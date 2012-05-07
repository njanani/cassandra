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
// Generated from file `Callback_C2S_Get.java'
//
// Warning: do not edit this file.
//
// </auto-generated>
//

package org.apache.cassandra.ice;

public abstract class Callback_C2S_Get extends Ice.TwowayCallback
{
    public abstract void response(byte[] content);

    public final void __completed(Ice.AsyncResult __result)
    {
        C2SPrx __proxy = (C2SPrx)__result.getProxy();
        ByteSeqHolder content = new ByteSeqHolder();
        try
        {
            __proxy.end_Get(content, __result);
        }
        catch(Ice.LocalException __ex)
        {
            exception(__ex);
            return;
        }
        response(content.value);
    }
}