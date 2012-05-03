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
package org.apache.cassandra.io.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.io.sstable.SSTable;
import org.apache.cassandra.flecs.FleCSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MmappedSegmentedFile extends SegmentedFile
{
    private static final Logger logger = LoggerFactory.getLogger(MmappedSegmentedFile.class);

    // in a perfect world, MAX_SEGMENT_SIZE would be final, but we need to test with a smaller size to stay sane.
    public static long MAX_SEGMENT_SIZE = Integer.MAX_VALUE;

    private static Method cleanerMethod = null;

    /**
     * Sorted array of segment offsets and MappedByteBuffers for segments. If mmap is completely disabled, or if the
     * segment would be too long to mmap, the value for an offset will be null, indicating that we need to fall back
     * to a RandomAccessFile.
     */
    private final Segment[] segments;

    public MmappedSegmentedFile(String path, long length, Segment[] segments)
    {
        super(path, length);
        this.segments = segments;
    }

    /**
     * @return The segment entry for the given position.
     */
    private Segment floor(long position)
    {
        assert 0 <= position && position < length: position + " vs " + length;
        Segment seg = new Segment(position, null);
        int idx = Arrays.binarySearch(segments, seg);
        assert idx != -1 : "Bad position " + position + " in segments " + Arrays.toString(segments);
        if (idx < 0)
            // round down to entry at insertion point
            idx = -(idx + 2);
        return segments[idx];
    }

    /**
     * @return The segment containing the given position: must be closed after use.
     */
    public FileDataInput getSegment(long position)
    {
        Segment segment = floor(position);
        if (segment.right != null)
        {
            // segment is mmap'd
            return new MappedFileDataInput(segment.right, path, segment.left, (int) (position - segment.left));
        }

        // not mmap'd: open a braf covering the segment
        try
        {
            // FIXME: brafs are unbounded, so this segment will cover the rest of the file, rather than just the row
            RandomAccessReader file = RandomAccessReader.open(new File(path));
            file.seek(position);
            return file;
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    public static void initCleaner()
    {
        try
        {
            cleanerMethod = Class.forName("sun.nio.ch.DirectBuffer").getMethod("cleaner");
        }
        catch (Exception e)
        {
            // Perhaps a non-sun-derived JVM - contributions welcome
            logger.info("Cannot initialize un-mmaper.  (Are you using a non-SUN JVM?)  Compacted data files will not be removed promptly.  Consider using a SUN JVM or using standard disk access mode");
        }
    }

    public static boolean isCleanerAvailable()
    {
        return cleanerMethod != null;
    }

    public void cleanup()
    {
        if (cleanerMethod == null)
            return;

        /*
         * Try forcing the unmapping of segments using undocumented unsafe sun APIs.
         * If this fails (non Sun JVM), we'll have to wait for the GC to finalize the mapping.
         * If this works and a thread tries to access any segment, hell will unleash on earth.
         */
        try
        {
            for (Segment segment : segments)
            {
                if (segment.right == null)
                    continue;

                Object cleaner = cleanerMethod.invoke(segment.right);
                cleaner.getClass().getMethod("clean").invoke(cleaner);
            }
            logger.debug("All segments have been unmapped successfully");
        }
        catch (Exception e)
        {
            // This is not supposed to happen
            logger.error("Error while unmapping segments", e);
        }
    }

    /**
     * Overrides the default behaviour to create segments of a maximum size.
     */
    static class Builder extends SegmentedFile.Builder
    {
        // planned segment boundaries
        private List<Long> boundaries;

        // offset of the open segment (first segment begins at 0).
        private long currentStart = 0;

        // current length of the open segment.
        // used to allow merging multiple too-large-to-mmap segments, into a single buffered segment.
        private long currentSize = 0;

        public Builder()
        {
            super();
            boundaries = new ArrayList<Long>();
            boundaries.add(0L);
        }

        public void addPotentialBoundary(long boundary)
        {
            if (boundary - currentStart <= MAX_SEGMENT_SIZE)
            {
                // boundary fits into current segment: expand it
                currentSize = boundary - currentStart;
                return;
            }

            // close the current segment to try and make room for the boundary
            if (currentSize > 0)
            {
                currentStart += currentSize;
                boundaries.add(currentStart);
            }
            currentSize = boundary - currentStart;

            // if we couldn't make room, the boundary needs its own segment
            if (currentSize > MAX_SEGMENT_SIZE)
            {
                currentStart = boundary;
                boundaries.add(currentStart);
                currentSize = 0;
            }
        }

        public SegmentedFile complete(String path)
        {
            long length;
            if(!path.contains("/system/") && path.endsWith("-Data.db")) {
            	String[] indexFileName = path.split("/");
                String[] indexFile = indexFileName[indexFileName.length - 1].split("-");
                String ksname = indexFile[0];
                String cfname = indexFile[1];
    	        //Get request to Flecs - only the data file is added to Flecs           
    	        FleCSClient fcsclient = new FleCSClient();
    	        fcsclient.init();
    	        CFMetaData cfm = Schema.instance.getCFMetaData(ksname,cfname);
    	        length = fcsclient.Size(SSTable.flecsContainers.get(cfm.getPrivacy()), path);
    	        fcsclient.cleanup();    	        
            }
            else 
            {
            	length = new File(path).length();
            }
            // add a sentinel value == length
            if (length != boundaries.get(boundaries.size() - 1))
                boundaries.add(length);
            // create the segments
            return new MmappedSegmentedFile(path, length, createSegments(path));
        }

        private Segment[] createSegments(String path)
        {
            int segcount = boundaries.size() - 1;
            Segment[] segments = new Segment[segcount];
            RandomAccessFile raf = null;
            ByteArrayInputStream bais = null;
            //MappedByteBuffer segment = null;
            ByteBuffer segment = null;
            ByteBuffer bb = null;
            byte[] filecontent = null;
            try
            {
            	if(!path.contains("/system/") && path.endsWith("-Data.db")) {
            		String[] indexFileName = path.split("/");
                    String[] indexFile = indexFileName[indexFileName.length - 1].split("-");
                    String ksname = indexFile[0];
                    String cfname = indexFile[1];
            		//Get request to Flecs - only the data file is added to Flecs           
        	        FleCSClient fcsclient = new FleCSClient();
        	        fcsclient.init();
        	        CFMetaData cfm = Schema.instance.getCFMetaData(ksname,cfname);
        	        filecontent = fcsclient.Get(SSTable.flecsContainers.get(cfm.getPrivacy()), path);
        	        //bais = new ByteArrayInputStream(filecontent);
        	        bb.wrap(filecontent);
        	        fcsclient.cleanup(); 
            	}
            	else
            	{
            		raf = new RandomAccessFile(path, "r");
            	}
                for (int i = 0; i < segcount; i++)
                {                	
                    long start = boundaries.get(i);
                    long size = boundaries.get(i + 1) - start;
                    int a,b;
                    if(!path.contains("/system/") && path.endsWith("-Data.db")) {
                    	segment = size <= MAX_SEGMENT_SIZE
                                ? bb.get(filecontent,(int)start,(int)size)
                                : null;                                
                    }
                    else 
                    {
                    	 segment = size <= MAX_SEGMENT_SIZE
                                 ? raf.getChannel().map(FileChannel.MapMode.READ_ONLY, start, size)
                                 : null;                                 
                    }                                
                    segments[i] = new Segment(start, segment);
                }
            }
            catch (IOException e)
            {
                throw new IOError(e);
            }
            finally
            {
                FileUtils.closeQuietly(raf);
            }
            return segments;
        }

        @Override
        public void serializeBounds(DataOutput dos) throws IOException
        {
            super.serializeBounds(dos);
            dos.writeInt(boundaries.size());
            for (long position: boundaries)
                dos.writeLong(position);
        }

        @Override
        public void deserializeBounds(DataInput dis) throws IOException
        {
            super.deserializeBounds(dis);
            List<Long> temp = new ArrayList<Long>();

            int size = dis.readInt();
            for (int i = 0; i < size; i++)
                temp.add(dis.readLong());

            boundaries = temp;
        }
    }
}
