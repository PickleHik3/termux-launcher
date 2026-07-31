/*
 * Copyright 2016 Tu Yimin
 * Licensed under the Apache License, Version 2.0.
 * Modified by Termux Launcher in 2026 for the Termux:Monet-derived blur implementation.
 */
package com.github.mmin18.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class AndroidStockBlurImpl implements BlurImpl {

    private RenderScript mRenderScript;
    private ScriptIntrinsicBlur mBlurScript;
    private Allocation mBlurInput;
    private Allocation mBlurOutput;

    @Override
    public boolean prepare(Context context, Bitmap buffer, float radius) {
        if (mRenderScript == null) {
            try {
                mRenderScript = RenderScript.create(context);
                mBlurScript = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));
            } catch (android.renderscript.RSRuntimeException e) {
                if (isDebug(context)) {
                    throw e;
                }
                release();
                return false;
            }
        }

        mBlurScript.setRadius(radius);
        // prepare() runs again for every size or radius change, and a resizing surface — the
        // keyboard sliding in, the dock growing — does that on every animation frame. Allocation
        // holds native memory that only destroy() returns promptly, so overwriting these fields
        // without destroying the previous pair leaked a buffer per frame.
        if (mBlurInput != null) {
            mBlurInput.destroy();
            mBlurInput = null;
        }
        if (mBlurOutput != null) {
            mBlurOutput.destroy();
            mBlurOutput = null;
        }
        mBlurInput = Allocation.createFromBitmap(mRenderScript, buffer,
            Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        mBlurOutput = Allocation.createTyped(mRenderScript, mBlurInput.getType());
        return true;
    }

    @Override
    public void release() {
        if (mBlurInput != null) {
            mBlurInput.destroy();
            mBlurInput = null;
        }
        if (mBlurOutput != null) {
            mBlurOutput.destroy();
            mBlurOutput = null;
        }
        if (mBlurScript != null) {
            mBlurScript.destroy();
            mBlurScript = null;
        }
        if (mRenderScript != null) {
            mRenderScript.destroy();
            mRenderScript = null;
        }
    }

    @Override
    public void blur(Bitmap input, Bitmap output) {
        mBlurInput.copyFrom(input);
        mBlurScript.setInput(mBlurInput);
        mBlurScript.forEach(mBlurOutput);
        mBlurOutput.copyTo(output);
    }

    private static Boolean DEBUG;

    private static boolean isDebug(Context ctx) {
        if (DEBUG == null && ctx != null) {
            DEBUG = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        }
        return DEBUG == Boolean.TRUE;
    }
}
