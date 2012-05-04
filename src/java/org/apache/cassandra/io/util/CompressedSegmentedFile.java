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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io. ObjectInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import org.apache.cassandra.flecs.FleCSClient;
import org.apache.cassandra.io.compress.CompressedRandomAccessReader;
import org.apache.cassandra.io.compress.CompressionMetadata;
import org.apache.cassandra.io.sstable.SSTable;

public class CompressedSegmentedFile extends SegmentedFile
{
    public final CompressionMetadata metadata;

    public CompressedSegmentedFile(String path, CompressionMetadata metadata)
    {
        super(path, metadata.dataLength, metadata.compressedFileLength);
        this.metadata = metadata;
    }

    public static class Builder extends SegmentedFile.Builder
    {
        /**
         * Adds a position that would be a safe place for a segment boundary in the file. For a block/row based file
         * format, safe boundaries are block/row edges.
         * @param boundary The absolute position of the potential boundary in the file.
         */
        public void addPotentialBoundary(long boundary)
        {
            // only one segment in a standard-io file
        }

        /**
         * Called after all potential boundaries have been added to apply this Builder to a concrete file on disk.
         * @param path The file on disk.
         */
        public SegmentedFile complete(String path)
        {
            return new CompressedSegmentedFile(path, CompressionMetadata.create(path));
        }
    }

    public FileDataInput getSegment(long position)
    {
    	RandomAccessReader file = null;
        try
        {
        	if(!path.contains("/system/")) {
        		FleCSClient fcsclient = new FleCSClient();
    	        fcsclient.init();
    	        byte [] b = fcsclient.Get(SSTable.flecsContainers.get(MmappedSegmentedFile.getcfmData(path)), SSTable.modifyFilePath(path));
    	        fcsclient.cleanup();
    	        FileOutputStream fos = new FileOutputStream(new File(path));
    	        BufferedOutputStream bos = new BufferedOutputStream(fos);
    	        bos.write(b);
    	        bos.flush();
    	        bos.close();
    	        file = CompressedRandomAccessReader.open(path, metadata);        		                	
	            file.seek(position);	
        	}
        	else
        	{
	            file = CompressedRandomAccessReader.open(path, metadata);        		                	
	            file.seek(position);	           
        	}
            return file;
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    public void cleanup()
    {
        // nothing to do
    }
}
