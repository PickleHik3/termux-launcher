package com.termux.app.chrome;

/**
 * The wallpaper's live x-offset, shared by every surface that samples the pre-blurred frame.
 *
 * <p>One object, written by the activity on every frame the wall moves and read by each glass
 * surface in its own draw: the self-drawn backdrop, the pane slabs, the dock and keyboard crops,
 * the status and window-bar frost. Everything reads the same number at the same moment, so all
 * the frost stays aligned with the moving wallpaper mid-slide, and none of it has to be handed a
 * new value one surface at a time.</p>
 *
 * <p>The offset is how far into the wide frame the screen's left edge lies, in screen pixels: a
 * surface at screen x samples the frame at x + offset. Zero while nothing pans — a still wallpaper,
 * landscape, reduce motion, the switch off — which leaves every surface drawing exactly as it did
 * before the wallpaper could move.</p>
 */
public final class WallpaperParallax {

    private float mOffsetPx;

    /** How far into the frame the screen's left edge lies, in px. */
    public float offsetPx() {
        return mOffsetPx;
    }

    /** Sets the offset; true when it actually moved, which is when the readers need a redraw. */
    public boolean setOffsetPx(float offsetPx) {
        if (mOffsetPx == offsetPx) return false;
        mOffsetPx = offsetPx;
        return true;
    }
}
