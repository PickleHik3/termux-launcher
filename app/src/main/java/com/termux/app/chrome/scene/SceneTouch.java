package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;

/**
 * The pointer as a shader sees it, with Shadertoy's {@code iMouse} semantics. Pure; scene thread.
 *
 * <p>{@code iMouse.xy}: the pointer while it is down, where it last was after it lifts.
 * {@code iMouse.zw}: where it went down; {@code z} is positive while it is down and negative after
 * it lifts, {@code w} is positive only on the first frame after it went down. All zero until the
 * first touch. Positions are kept in view pixels (y down) and turned into the shader's pixels (y up,
 * at the render scale) by {@link #fill}.</p>
 */
public final class SceneTouch {

    private float mX, mY, mClickX, mClickY;
    private boolean mDown;
    private boolean mClickFresh;
    private boolean mEver;

    public void down(float x, float y) {
        mX = x;
        mY = y;
        mClickX = x;
        mClickY = y;
        mDown = true;
        mClickFresh = true;
        mEver = true;
    }

    public void move(float x, float y) {
        if (!mDown) return;
        mX = x;
        mY = y;
    }

    public void up(float x, float y) {
        if (!mDown) return;
        mX = x;
        mY = y;
        mDown = false;
    }

    public boolean isDown() {
        return mDown;
    }

    /**
     * Writes {@code iMouse} for a frame of a view {@code viewHeight} tall rendered at
     * {@code scale} render pixels per view pixel. {@link #frameDrawn} then ends the click's first frame.
     */
    public void fill(@NonNull float[] out, float viewHeight, float scale) {
        fill(out, viewHeight, scale, scale);
    }

    /**
     * Writes {@code iMouse} in the render target's pixels: view x times {@code scaleX}, and y
     * measured from the bottom ({@code viewHeight - y}) times {@code scaleY}, so it shares
     * {@code iResolution}'s space at any quality.
     */
    public void fill(@NonNull float[] out, float viewHeight, float scaleX, float scaleY) {
        if (!mEver) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        out[0] = mX * scaleX;
        out[1] = (viewHeight - mY) * scaleY;
        float cx = mClickX * scaleX, cy = (viewHeight - mClickY) * scaleY;
        out[2] = mDown ? cx : -cx;
        out[3] = mClickFresh ? cy : -cy;
    }

    /** ShaderEditor's {@code touch}: the pointer in render pixels, y up (zero before the first touch). */
    public void fillTouch(@NonNull float[] out, float viewHeight, float scaleX, float scaleY) {
        if (!mEver) {
            out[0] = out[1] = 0f;
            return;
        }
        out[0] = mX * scaleX;
        out[1] = (viewHeight - mY) * scaleY;
    }

    public void frameDrawn() {
        mClickFresh = false;
    }
}
