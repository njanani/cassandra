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
// Generated from file `_C2SDisp.java'
//
// Warning: do not edit this file.
//
// </auto-generated>
//

package org.apache.cassandra.ice;

public abstract class _C2SDisp extends Ice.ObjectImpl implements C2S
{
    protected void
    ice_copyStateFrom(Ice.Object __obj)
        throws java.lang.CloneNotSupportedException
    {
        throw new java.lang.CloneNotSupportedException();
    }

    public static final String[] __ids =
    {
        "::FleCS::C2S",
        "::Ice::Object"
    };

    public boolean
    ice_isA(String s)
    {
        return java.util.Arrays.binarySearch(__ids, s) >= 0;
    }

    public boolean
    ice_isA(String s, Ice.Current __current)
    {
        return java.util.Arrays.binarySearch(__ids, s) >= 0;
    }

    public String[]
    ice_ids()
    {
        return __ids;
    }

    public String[]
    ice_ids(Ice.Current __current)
    {
        return __ids;
    }

    public String
    ice_id()
    {
        return __ids[0];
    }

    public String
    ice_id(Ice.Current __current)
    {
        return __ids[0];
    }

    public static String
    ice_staticId()
    {
        return __ids[0];
    }

    public final void
    Append(String bucketID, String objID, byte[] content)
    {
        Append(bucketID, objID, content, null);
    }

    public final void
    Delete(String bucketID, String objID)
    {
        Delete(bucketID, objID, null);
    }

    public final void
    Get(String bucketID, String objID, ByteSeqHolder content)
    {
        Get(bucketID, objID, content, null);
    }

    public final void
    Process(String bucketID, String objID)
    {
        Process(bucketID, objID, null);
    }

    public final void
    Put(String bucketID, String objID, byte[] content)
    {
        Put(bucketID, objID, content, null);
    }

    public final long
    Size(String bucketID, String objID)
    {
        return Size(bucketID, objID, null);
    }

    public static Ice.DispatchStatus
    ___Get(C2S __obj, IceInternal.Incoming __inS, Ice.Current __current)
    {
        __checkMode(Ice.OperationMode.Normal, __current.mode);
        IceInternal.BasicStream __is = __inS.is();
        __is.startReadEncaps();
        String bucketID;
        bucketID = __is.readString();
        String objID;
        objID = __is.readString();
        __is.endReadEncaps();
        ByteSeqHolder content = new ByteSeqHolder();
        IceInternal.BasicStream __os = __inS.os();
        __obj.Get(bucketID, objID, content, __current);
        ByteSeqHelper.write(__os, content.value);
        return Ice.DispatchStatus.DispatchOK;
    }

    public static Ice.DispatchStatus
    ___Put(C2S __obj, IceInternal.Incoming __inS, Ice.Current __current)
    {
        __checkMode(Ice.OperationMode.Normal, __current.mode);
        IceInternal.BasicStream __is = __inS.is();
        __is.startReadEncaps();
        String bucketID;
        bucketID = __is.readString();
        String objID;
        objID = __is.readString();
        byte[] content;
        content = ByteSeqHelper.read(__is);
        __is.endReadEncaps();
        __obj.Put(bucketID, objID, content, __current);
        return Ice.DispatchStatus.DispatchOK;
    }

    public static Ice.DispatchStatus
    ___Append(C2S __obj, IceInternal.Incoming __inS, Ice.Current __current)
    {
        __checkMode(Ice.OperationMode.Normal, __current.mode);
        IceInternal.BasicStream __is = __inS.is();
        __is.startReadEncaps();
        String bucketID;
        bucketID = __is.readString();
        String objID;
        objID = __is.readString();
        byte[] content;
        content = ByteSeqHelper.read(__is);
        __is.endReadEncaps();
        __obj.Append(bucketID, objID, content, __current);
        return Ice.DispatchStatus.DispatchOK;
    }

    public static Ice.DispatchStatus
    ___Delete(C2S __obj, IceInternal.Incoming __inS, Ice.Current __current)
    {
        __checkMode(Ice.OperationMode.Normal, __current.mode);
        IceInternal.BasicStream __is = __inS.is();
        __is.startReadEncaps();
        String bucketID;
        bucketID = __is.readString();
        String objID;
        objID = __is.readString();
        __is.endReadEncaps();
        __obj.Delete(bucketID, objID, __current);
        return Ice.DispatchStatus.DispatchOK;
    }

    public static Ice.DispatchStatus
    ___Size(C2S __obj, IceInternal.Incoming __inS, Ice.Current __current)
    {
        __checkMode(Ice.OperationMode.Normal, __current.mode);
        IceInternal.BasicStream __is = __inS.is();
        __is.startReadEncaps();
        String bucketID;
        bucketID = __is.readString();
        String objID;
        objID = __is.readString();
        __is.endReadEncaps();
        IceInternal.BasicStream __os = __inS.os();
        long __ret = __obj.Size(bucketID, objID, __current);
        __os.writeLong(__ret);
        return Ice.DispatchStatus.DispatchOK;
    }

    public static Ice.DispatchStatus
    ___Process(C2S __obj, IceInternal.Incoming __inS, Ice.Current __current)
    {
        __checkMode(Ice.OperationMode.Normal, __current.mode);
        IceInternal.BasicStream __is = __inS.is();
        __is.startReadEncaps();
        String bucketID;
        bucketID = __is.readString();
        String objID;
        objID = __is.readString();
        __is.endReadEncaps();
        __obj.Process(bucketID, objID, __current);
        return Ice.DispatchStatus.DispatchOK;
    }

    private final static String[] __all =
    {
        "Append",
        "Delete",
        "Get",
        "Process",
        "Put",
        "Size",
        "ice_id",
        "ice_ids",
        "ice_isA",
        "ice_ping"
    };

    public Ice.DispatchStatus
    __dispatch(IceInternal.Incoming in, Ice.Current __current)
    {
        int pos = java.util.Arrays.binarySearch(__all, __current.operation);
        if(pos < 0)
        {
            throw new Ice.OperationNotExistException(__current.id, __current.facet, __current.operation);
        }

        switch(pos)
        {
            case 0:
            {
                return ___Append(this, in, __current);
            }
            case 1:
            {
                return ___Delete(this, in, __current);
            }
            case 2:
            {
                return ___Get(this, in, __current);
            }
            case 3:
            {
                return ___Process(this, in, __current);
            }
            case 4:
            {
                return ___Put(this, in, __current);
            }
            case 5:
            {
                return ___Size(this, in, __current);
            }
            case 6:
            {
                return ___ice_id(this, in, __current);
            }
            case 7:
            {
                return ___ice_ids(this, in, __current);
            }
            case 8:
            {
                return ___ice_isA(this, in, __current);
            }
            case 9:
            {
                return ___ice_ping(this, in, __current);
            }
        }

        assert(false);
        throw new Ice.OperationNotExistException(__current.id, __current.facet, __current.operation);
    }

    public void
    __write(IceInternal.BasicStream __os)
    {
        __os.writeTypeId(ice_staticId());
        __os.startWriteSlice();
        __os.endWriteSlice();
        super.__write(__os);
    }

    public void
    __read(IceInternal.BasicStream __is, boolean __rid)
    {
        if(__rid)
        {
            __is.readTypeId();
        }
        __is.startReadSlice();
        __is.endReadSlice();
        super.__read(__is, true);
    }

    public void
    __write(Ice.OutputStream __outS)
    {
        Ice.MarshalException ex = new Ice.MarshalException();
        ex.reason = "type FleCS::C2S was not generated with stream support";
        throw ex;
    }

    public void
    __read(Ice.InputStream __inS, boolean __rid)
    {
        Ice.MarshalException ex = new Ice.MarshalException();
        ex.reason = "type FleCS::C2S was not generated with stream support";
        throw ex;
    }
}
