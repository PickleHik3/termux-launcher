package com.termux.x11;

import java.util.Objects;

/**
 * Lets a window change through only when it differs from the last one sent on this connection.
 * The X server resizes the screen, and logs it, for every change it is sent; {@link LorieView}
 * announces its size on every measure and every surface change, so one real resize used to reach
 * the server two or three times. A new connection has heard nothing: {@link #forget()} then.
 */
final class WindowChangeGate {

    private boolean mSent;
    private int mWidth, mHeight, mFramerate;
    private String mName;

    /** True when this change is new and is to be sent; it is then noted as the last one sent. */
    boolean shouldSend(int width, int height, int framerate, String name) {
        if (mSent && mWidth == width && mHeight == height && mFramerate == framerate
                && Objects.equals(mName, name))
            return false;
        mSent = true;
        mWidth = width;
        mHeight = height;
        mFramerate = framerate;
        mName = name;
        return true;
    }

    /** The connection is new or gone: whatever comes next is sent. */
    void forget() {
        mSent = false;
        mName = null;
    }
}
