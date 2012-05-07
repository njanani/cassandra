// **********************************************************************
//
// Copyright (c) 2003-2011 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************
//
// Ice version 3.4.2
//
// <auto-generated>
//
// Generated from file `_C2SOperations.java'
//
// Warning: do not edit this file.
//
// </auto-generated>
//

package org.apache.cassandra.ice;

public interface _C2SOperations
{
    void Get(String bucketID, String objID, ByteSeqHolder content, Ice.Current __current);

    void Put(String bucketID, String objID, byte[] content, Ice.Current __current);

    void Append(String bucketID, String objID, byte[] content, Ice.Current __current);

    void Delete(String bucketID, String objID, Ice.Current __current);

    long Size(String bucketID, String objID, Ice.Current __current);

    void Process(String bucketID, String objID, Ice.Current __current);
}
