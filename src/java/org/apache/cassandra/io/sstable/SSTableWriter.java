/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.io.sstable;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;

import com.google.common.collect.Sets;

import org.apache.cassandra.config.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.compaction.*;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.io.IColumnSerializer;
import org.apache.cassandra.io.compress.CompressedSequentialWriter;
import org.apache.cassandra.io.util.*;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.*;
import org.apache.cassandra.flecs.*;
import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.CassandraServer;
import org.apache.cassandra.thrift.CfDef;
import org.apache.cassandra.thrift.KsDef;

public class SSTableWriter extends SSTable
{
    private static final Logger logger = LoggerFactory.getLogger(SSTableWriter.class);

    private IndexWriter iwriter;
    private SegmentedFile.Builder dbuilder;
    private final SequentialWriter dataFile;
    private DecoratedKey lastWrittenKey;
    private FileMark dataMark;
    private final SSTableMetadata.Collector sstableMetadataCollector;
    
    public int test;
    
    

    public SSTableWriter(String filename, long keyCount) throws IOException
    {
        this(filename,
             keyCount,
             Schema.instance.getCFMetaData(Descriptor.fromFilename(filename)),
             StorageService.getPartitioner(),
             SSTableMetadata.createCollector());
    }

    private static Set<Component> components(CFMetaData metadata)throws IOException
    {
        Set<Component> components = new HashSet<Component>(Arrays.asList(Component.DATA,
                                                                         Component.FILTER,
                                                                         Component.PRIMARY_INDEX,
                                                                         Component.STATS,
                                                                         Component.SUMMARY));

        if (metadata.compressionParameters().sstableCompressor != null)
            components.add(Component.COMPRESSION_INFO);
        else
            // it would feel safer to actually add this component later in maybeWriteDigest(),
            // but the components are unmodifiable after construction
            components.add(Component.DIGEST);
        return components;
    }

    public SSTableWriter(String filename,
                         long keyCount,
                         CFMetaData metadata,
                         IPartitioner<?> partitioner,
                         SSTableMetadata.Collector sstableMetadataCollector) throws IOException
    {
        super(Descriptor.fromFilename(filename),
              components(metadata),
              metadata,
              partitioner);
        iwriter = new IndexWriter(keyCount);

        if (compression)
        {
            dbuilder = SegmentedFile.getCompressedBuilder();
            dataFile = CompressedSequentialWriter.open(getFilename(),
                                                       descriptor.filenameFor(Component.COMPRESSION_INFO),
                                                       !DatabaseDescriptor.populateIOCacheOnFlush(),
                                                       metadata.compressionParameters(),
                                                       sstableMetadataCollector);
        }
        else
        {
            dbuilder = SegmentedFile.getBuilder(DatabaseDescriptor.getDiskAccessMode());
            dataFile = SequentialWriter.open(new File(getFilename()), 
			                      !DatabaseDescriptor.populateIOCacheOnFlush());
            dataFile.setComputeDigest();
        }

        this.sstableMetadataCollector = sstableMetadataCollector;
    }

    public void mark()
    {
        dataMark = dataFile.mark();
        iwriter.mark();
    }

    public void resetAndTruncate()
    {
        try
        {
            dataFile.resetAndTruncate(dataMark);
            iwriter.resetAndTruncate();
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    /**
     * Perform sanity checks on @param decoratedKey and @return the position in the data file before any data is written
     */
    private long beforeAppend(DecoratedKey decoratedKey) throws IOException
    {
        assert decoratedKey != null : "Keys must not be null";
        assert lastWrittenKey == null || lastWrittenKey.compareTo(decoratedKey) < 0
               : "Last written key " + lastWrittenKey + " >= current key " + decoratedKey + " writing into " + getFilename();
        return (lastWrittenKey == null) ? 0 : dataFile.getFilePointer();
    }

    private RowIndexEntry afterAppend(DecoratedKey decoratedKey, long dataPosition, DeletionInfo delInfo, ColumnIndex index) throws IOException
    {
        lastWrittenKey = decoratedKey;
        this.last = lastWrittenKey;
        if(null == this.first)
            this.first = lastWrittenKey;

        if (logger.isTraceEnabled())
            logger.trace("wrote " + decoratedKey + " at " + dataPosition);
        RowIndexEntry entry = RowIndexEntry.create(dataPosition, delInfo, index);
        iwriter.append(decoratedKey, entry);
        dbuilder.addPotentialBoundary(dataPosition);
        return entry;
    }

    public RowIndexEntry append(AbstractCompactedRow row) throws IOException
    {
        long currentPosition = beforeAppend(row.key);
        ByteBufferUtil.writeWithShortLength(row.key.key, dataFile.stream);
        long dataStart = dataFile.getFilePointer();
        long dataSize = row.write(dataFile.stream);
        assert dataSize == dataFile.getFilePointer() - (dataStart + 8)
                : "incorrect row data size " + dataSize + " written to " + dataFile.getPath() + "; correct is " + (dataFile.getFilePointer() - (dataStart + 8));
        sstableMetadataCollector.update(dataFile.getFilePointer() - currentPosition, row.columnStats());
        return afterAppend(row.key, currentPosition, row.deletionInfo(), row.index());
    }

    public void append(DecoratedKey decoratedKey, ColumnFamily cf) throws IOException
    {
        long startPosition = beforeAppend(decoratedKey);
        ByteBufferUtil.writeWithShortLength(decoratedKey.key, dataFile.stream);

        // build column index
        // TODO: build and serialization could be merged
        ColumnIndex index = new ColumnIndex.Builder(cf.getComparator(), decoratedKey.key, cf.getColumnCount()).build(cf);

        // write out row size + data
        dataFile.stream.writeLong(cf.serializedSizeForSSTable());
        ColumnFamily.serializer().serializeForSSTable(cf, dataFile.stream);

        afterAppend(decoratedKey, startPosition, cf.deletionInfo(), index);

        sstableMetadataCollector.update(dataFile.getFilePointer() - startPosition, cf.getColumnStats());
    }

    public long appendFromStream(DecoratedKey key, CFMetaData metadata, long dataSize, DataInput in) throws IOException
    {
        long currentPosition = beforeAppend(key);
        ByteBufferUtil.writeWithShortLength(key.key, dataFile.stream);
        long dataStart = dataFile.getFilePointer();

        // write row size
        dataFile.stream.writeLong(dataSize);

        // cf data
        int lct = in.readInt();
        long mfda = in.readLong();
        DeletionInfo deletionInfo = new DeletionInfo(mfda, lct);
        dataFile.stream.writeInt(lct);
        dataFile.stream.writeLong(mfda);

        // column size
        int columnCount = in.readInt();
        dataFile.stream.writeInt(columnCount);

        // deserialize each column to obtain maxTimestamp and immediately serialize it.
        long maxTimestamp = Long.MIN_VALUE;
        StreamingHistogram tombstones = new StreamingHistogram(TOMBSTONE_HISTOGRAM_BIN_SIZE);
        ColumnFamily cf = ColumnFamily.create(metadata, ArrayBackedSortedColumns.factory());
        ColumnIndex.Builder columnIndexer = new ColumnIndex.Builder(cf.getComparator(), key.key, columnCount);
        IColumnSerializer columnSerializer = cf.getColumnSerializer();
        for (int i = 0; i < columnCount; i++)
        {
            // deserialize column with PRESERVE_SIZE because we've written the dataSize based on the
            // data size received, so we must reserialize the exact same data
            IColumn column = columnSerializer.deserialize(in, IColumnSerializer.Flag.PRESERVE_SIZE, Integer.MIN_VALUE);
            if (column instanceof CounterColumn)
            {
                column = ((CounterColumn) column).markDeltaToBeCleared();
            }
            else if (column instanceof SuperColumn)
            {
                SuperColumn sc = (SuperColumn) column;
                for (IColumn subColumn : sc.getSubColumns())
                {
                    if (subColumn instanceof CounterColumn)
                    {
                        IColumn marked = ((CounterColumn) subColumn).markDeltaToBeCleared();
                        sc.replace(subColumn, marked);
                    }
                }
            }

            int deletionTime = column.getLocalDeletionTime();
            if (deletionTime < Integer.MAX_VALUE)
            {
                tombstones.update(deletionTime);
            }
            maxTimestamp = Math.max(maxTimestamp, column.maxTimestamp());
            cf.getColumnSerializer().serialize(column, dataFile.stream);
            columnIndexer.add(column);
        }

        assert dataSize == dataFile.getFilePointer() - (dataStart + 8)
                : "incorrect row data size " + dataSize + " written to " + dataFile.getPath() + "; correct is " + (dataFile.getFilePointer() - (dataStart + 8));
        sstableMetadataCollector.updateMaxTimestamp(maxTimestamp);
        sstableMetadataCollector.addRowSize(dataFile.getFilePointer() - currentPosition);
        sstableMetadataCollector.addColumnCount(columnCount);
        sstableMetadataCollector.mergeTombstoneHistogram(tombstones);
        afterAppend(key, currentPosition, deletionInfo, columnIndexer.build());
        return currentPosition;
    }

    /**
     * After failure, attempt to close the index writer and data file before deleting all temp components for the sstable
     */
    public void abort()
    {
        assert descriptor.temporary;
        FileUtils.closeQuietly(iwriter);
        FileUtils.closeQuietly(dataFile);

        try
        {
            Set<Component> components = SSTable.componentsFor(descriptor);
            if (!components.isEmpty())
                SSTable.delete(descriptor, components);
        }
        catch (Exception e)
        {
            logger.error(String.format("Failed deleting temp components for %s", descriptor), e);
        }
    }

    public SSTableReader closeAndOpenReader() throws IOException
    {
        return closeAndOpenReader(System.currentTimeMillis());
    }

    public SSTableReader closeAndOpenReader(long maxDataAge) throws IOException
    {
        // index and filter
        iwriter.close();

        // main data, close will truncate if necessary
        dataFile.close();

        // write sstable statistics
        SSTableMetadata sstableMetadata = sstableMetadataCollector.finalizeMetadata(partitioner.getClass().getCanonicalName());
        writeMetadata(descriptor, sstableMetadata);
        maybeWriteDigest();

        // remove the 'tmp' marker from all components
        final Descriptor newdesc = rename(descriptor, components);

        // finalize in-memory state for the reader
        SegmentedFile ifile = iwriter.builder.complete(newdesc.filenameFor(SSTable.COMPONENT_INDEX));
        SegmentedFile dfile = dbuilder.complete(newdesc.filenameFor(SSTable.COMPONENT_DATA));
        SSTableReader sstable = SSTableReader.internalOpen(newdesc,
                                                           components,
                                                           metadata,
                                                           partitioner,
                                                           ifile,
                                                           dfile,
                                                           iwriter.summary,
                                                           iwriter.bf,
                                                           maxDataAge,
                                                           sstableMetadata);
        sstable.first = getMinimalKey(first);
        sstable.last = getMinimalKey(last);
        // try to save the summaries to disk
        SSTableReader.saveSummary(sstable, iwriter.builder, dbuilder);
        iwriter = null;
        dbuilder = null;
        return sstable;
    }

    private void maybeWriteDigest() throws IOException
    {
        byte[] digest = dataFile.digest();
        if (digest == null)
            return;

        SequentialWriter out = SequentialWriter.open(new File(descriptor.filenameFor(SSTable.COMPONENT_DIGEST)), true);
        // Writing output compatible with sha1sum
        Descriptor newdesc = descriptor.asTemporary(false);
        String[] tmp = newdesc.filenameFor(SSTable.COMPONENT_DATA).split(Pattern.quote(File.separator));
        String dataFileName = tmp[tmp.length - 1];
        out.write(String.format("%s  %s", Hex.bytesToHex(digest), dataFileName).getBytes());
        out.close();
    }

    private static void writeMetadata(Descriptor desc, SSTableMetadata sstableMetadata) throws IOException
    {
        SequentialWriter out = SequentialWriter.open(new File(desc.filenameFor(SSTable.COMPONENT_STATS)), true);
        SSTableMetadata.serializer.serialize(sstableMetadata, out.stream);
        out.close();
    }

    static Descriptor rename(Descriptor tmpdesc, Set<Component> components)
    {
        Descriptor newdesc = tmpdesc.asTemporary(false);
        rename(tmpdesc, newdesc, components);
        return newdesc;
    }

	public static byte[] readFileToByteArray(String fname)throws IOException
    {
    	RandomAccessFile f = new RandomAccessFile(fname, "r");
        byte[] b = new byte[(int)f.length()];
        f.read(b);
        return b;
    }

    public static CfDef getCfDef(KsDef keyspace, String columnFamilyName)
    {
        for (CfDef cfDef : keyspace.cf_defs)
        {
            if (cfDef.name.equals(columnFamilyName))
                return cfDef;
        }

        return null;
    }

    public static void rename(Descriptor tmpdesc, Descriptor newdesc, Set<Component> components)
    {
        try
        {
            // do -Data last because -Data present should mean the sstable was completely renamed before crash
            // don't rename -Summary component as it is not created yet and created when SSTable is loaded.
            for (Component component : Sets.difference(components, Sets.newHashSet(Component.DATA, Component.SUMMARY)))
                FBUtilities.renameWithConfirm(tmpdesc.filenameFor(component), newdesc.filenameFor(component));
            FBUtilities.renameWithConfirm(tmpdesc.filenameFor(Component.DATA), newdesc.filenameFor(Component.DATA));
            
			String filename = newdesc.filenameFor(Component.DATA);
            if (!filename.contains("/system/")) {        
	            //Put request to Flecs - only the data file is added to Flecs           
	            FleCSClient fcsclient = new FleCSClient();
	            fcsclient.init();
	            byte[] filecontent = readFileToByteArray(filename);	  
	            CFMetaData cfm = Schema.instance.getCFMetaData(newdesc.ksname,newdesc.cfname);
	            int status = fcsclient.Put(flecsContainers.get(cfm.getPrivacy()), filename, filecontent);
	            fcsclient.cleanup();
	            
	            File todelete = new File(filename);
	            if (!todelete.delete())
	            {
	                throw new IOException("Failed to delete " + todelete.getAbsolutePath());
	            }
	            
	            if (status == 1)
	            {
	                logger.error("Put failed " + newdesc.filenameFor(Component.DATA));	                
	                throw new IOException();
	            }
            }
 
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    public long getFilePointer()
    {
        return dataFile.getFilePointer();
    }

    /**
     * Encapsulates writing the index and filter for an SSTable. The state of this object is not valid until it has been closed.
     */
    class IndexWriter implements Closeable
    {
        private final SequentialWriter indexFile;
        public final SegmentedFile.Builder builder;
        public final IndexSummary summary;
        public final Filter bf;
        private FileMark mark;

        IndexWriter(long keyCount) throws IOException
        {
            indexFile = SequentialWriter.open(new File(descriptor.filenameFor(SSTable.COMPONENT_INDEX)),
                                              !DatabaseDescriptor.populateIOCacheOnFlush());
            builder = SegmentedFile.getBuilder(DatabaseDescriptor.getIndexAccessMode());
            summary = new IndexSummary(keyCount);

            Double fpChance = metadata.getBloomFilterFpChance();
            if (fpChance != null && fpChance == 0)
            {
                // paranoia -- we've had bugs in the thrift <-> avro <-> CfDef dance before, let's not let that break things
                logger.error("Bloom filter FP chance of zero isn't supposed to happen");
                fpChance = null;
            }
            bf = fpChance == null ? FilterFactory.getFilter(keyCount, 15)
                                  : FilterFactory.getFilter(keyCount, fpChance);
        }

        public void append(DecoratedKey key, RowIndexEntry indexEntry) throws IOException
        {
            bf.add(key.key);
            long indexPosition = indexFile.getFilePointer();
            ByteBufferUtil.writeWithShortLength(key.key, indexFile.stream);
            RowIndexEntry.serializer.serialize(indexEntry, indexFile.stream);
            if (logger.isTraceEnabled())
                logger.trace("wrote index entry: " + indexEntry + " at " + indexPosition);

            summary.maybeAddEntry(key, indexPosition);
            builder.addPotentialBoundary(indexPosition);
        }

        /**
         * Closes the index and bloomfilter, making the public state of this writer valid for consumption.
         */
        public void close() throws IOException
        {
            // bloom filter
            FileOutputStream fos = new FileOutputStream(descriptor.filenameFor(SSTable.COMPONENT_FILTER));
            DataOutputStream stream = new DataOutputStream(fos);
            FilterFactory.serialize(bf, stream, descriptor.filterType);
            stream.flush();
            fos.getFD().sync();
            stream.close();

            // index
            long position = indexFile.getFilePointer();
            indexFile.close(); // calls force
            FileUtils.truncate(indexFile.getPath(), position);

            // finalize in-memory index state
            summary.complete();
        }

        public void mark()
        {
            mark = indexFile.mark();
        }

        public void resetAndTruncate() throws IOException
        {
            // we can't un-set the bloom filter addition, but extra keys in there are harmless.
            // we can't reset dbuilder either, but that is the last thing called in afterappend so
            // we assume that if that worked then we won't be trying to reset.
            indexFile.resetAndTruncate(mark);
        }

        @Override
        public String toString()
        {
            return "IndexWriter(" + descriptor + ")";
        }
    }
}
