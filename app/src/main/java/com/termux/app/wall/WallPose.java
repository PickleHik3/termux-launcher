package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * The wall's pose as state: how far it has slid, how much of the terminal's rims shows, how far
 * the Terminal page sank under a held border, and whether that page is wholly off screen. Written
 * only by {@link PaneWallController}, which tells its {@link Reader}s which part moved. Main
 * thread only; nothing here allocates.
 */
public final class WallPose {

    /** The slide's signed offset from the current page's rest changed. */
    public static final int OFFSET = 1;
    /** The terminal's rim alpha changed. */
    public static final int RIMS = 1 << 1;
    /** The Terminal page's sink scale changed. */
    public static final int SINK = 1 << 2;
    /** The Terminal page went fully off screen, or came back. */
    public static final int OFF_SCREEN = 1 << 3;

    /** Hears the pose change; {@code changed} holds the bits of what moved. */
    public interface Reader {
        void onWallPose(@NonNull WallPose pose, int changed);
    }

    private float mOffsetPx;
    private float mRimAlpha = 1f;
    private float mSinkScale = 1f;
    private boolean mOffScreen;

    /** Signed distance from the current page's rest, in px. */
    public float offsetPx() { return mOffsetPx; }

    /** How much of the terminal's rims the slide leaves showing; 1 at rest. */
    public float terminalRimAlpha() { return mRimAlpha; }

    /** The Terminal page's scale under a held border; 1 at rest. */
    public float terminalSinkScale() { return mSinkScale; }

    /** Whether the Terminal page is fully off screen. */
    public boolean terminalOffScreen() { return mOffScreen; }

    void setOffsetPx(float offsetPx) { mOffsetPx = offsetPx; }

    void setTerminalRimAlpha(float alpha) { mRimAlpha = alpha; }

    void setTerminalSinkScale(float scale) { mSinkScale = scale; }

    void setTerminalOffScreen(boolean offScreen) { mOffScreen = offScreen; }
}
