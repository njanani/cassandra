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
// Generated from file `_C2SDel.java'
//
// Warning: do not edit this file.
//
// </auto-generated>
//

package org.apache.cassandra.ice;

public interface _C2SDel extends Ice._ObjectDel
{
    void Get(String bucketID, String objID, ByteSeqHolder content, java.util.Map<String, String> __ctx)
        throws IceInternal.LocalExceptionWrapper;

    void Put(String bucketID, String objID, byte[] content, java.util.Map<String, String> __ctx)
        throws IceInternal.LocalExceptionWrapper;

    void Append(String bucketID, String objID, byte[] content, java.util.Map<String, String> __ctx)
        throws IceInternal.LocalExceptionWrapper;

    void Delete(String bucketID, String objID, java.util.Map<String, String> __ctx)
        throws IceInternal.LocalExceptionWrapper;

    long Size(String bucketID, String objID, java.util.Map<String, String> __ctx)
        throws IceInternal.LocalExceptionWrapper;

    void Process(String bucketID, String objID, java.util.Map<String, String> __ctx)
        throws IceInternal.LocalExceptionWrapper;
}
