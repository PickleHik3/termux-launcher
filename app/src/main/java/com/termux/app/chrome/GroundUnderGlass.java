package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * Prototype #283 ({@link GlassGround}): the ground of the page beneath a glass, drawn into that
 * glass over its frame. A bar's glass draws it between its frost and its tint; the dock's backdrop
 * wears it in its overlay, so its frost filter leaves it alone and its refraction bends it with the
 * frost. A glass whose picture already holds the strip (the dock's Live picture records what is
 * beneath it) is told so ({@link #setCarried}) and draws nothing more.
 *
 * <p>Every draw reads the strip's bands where the glass stands now; the strip redraws its riders
 * when the bands move ({@link GroundStripView#addRider}). No allocation in {@link #draw} but the
 * diagnostic's, which runs only with the motion log on.</p>
 */
public final class GroundUnderGlass extends Drawable {

    @NonNull private final View mHost;
    @NonNull private final String mTag;
    @Nullable private GroundStripView mStrip;
    private boolean mCarried;
    private int mLoggedLeft;
    private int mLoggedMid;
    private int mLoggedRight;
    private int mLoggedCount;

    /** @param tag the glass's name in the diagnostic */
    public GroundUnderGlass(@NonNull View host, @NonNull String tag) {
        mHost = host;
        mTag = tag;
    }

    /**
     * Finds the window's ground strip from the host and rides it; call on attach. Nothing when the
     * gate is off or the window has no strip.
     */
    public void attach() {
        if (!GlassGround.ON) return;
        if (mStrip == null) {
            View found = mHost.getRootView().findViewById(R.id.ground_strip);
            if (found instanceof GroundStripView) mStrip = (GroundStripView) found;
        }
        if (mStrip != null) mStrip.addRider(mHost);
    }

    public void detach() {
        if (mStrip != null) mStrip.removeRider(mHost);
    }

    /** The glass's picture already holds the strip (a Live recording): draw nothing more. */
    public void setCarried(boolean carried) {
        if (carried == mCarried) return;
        mCarried = carried;
        mHost.invalidate();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        GroundStripView strip = mStrip;
        if (!GlassGround.ON || mCarried || strip == null) return;
        float leftInWall = strip.drawUnder(canvas, mHost);
        // DIAG(283): the ground drawn under this glass at its left, middle and right, on change
        // (each frame of a crossing whose seam is in view); revert before merge.
        if (MotionLog.enabled && !Float.isNaN(leftInWall)) {
            float width = mHost.getWidth();
            int left = strip.groundAtWallX(leftInWall + 1f);
            int mid = strip.groundAtWallX(leftInWall + width / 2f);
            int right = strip.groundAtWallX(leftInWall + width - 1f);
            int count = strip.bandCount();
            if (count > 1 || left != mLoggedLeft || mid != mLoggedMid || right != mLoggedRight
                    || count != mLoggedCount) {
                mLoggedLeft = left;
                mLoggedMid = mid;
                mLoggedRight = right;
                mLoggedCount = count;
                MotionLog.log("glass-ground", "glass=" + mTag + " bands=" + count
                    + " x=" + Math.round(leftInWall) + " w=" + Math.round(width)
                    + " dimL=" + (left >>> 24) + " dimM=" + (mid >>> 24)
                    + " dimR=" + (right >>> 24)
                    + " argbM=" + Integer.toHexString(mid));
            }
        }
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
