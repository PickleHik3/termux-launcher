package com.termux.app.controlbar;

import androidx.annotation.NonNull;

/**
 * The full-screen handle's geometry (#426), pure: a thin glass pill inside a taller, wider touch
 * band, standing where the control bar's bottom was, above Android's gesture line. A slide up on
 * it, or a tap, raises the bar.
 */
public final class FullScreenHandle {

    /** The pill's visible height, in dp. */
    public static final float PILL_HEIGHT_DP = 6f;
    /** The pill's visible width, in dp. */
    public static final float PILL_WIDTH_DP = 64f;
    /** The touch band's height and width, in dp. */
    public static final float BAND_HEIGHT_DP = 28f;
    public static final float BAND_WIDTH_DP = 160f;
    /** The pill's distance from the band's foot, in dp. */
    public static final float PILL_FOOT_DP = 4f;
    /** How far a slide goes up to raise the bar, in dp. */
    public static final float SLIDE_DP = 24f;

    private FullScreenHandle() {}

    /**
     * The pill {@code {left, top, right, bottom}} in a band {@code bandWidthPx} by {@code bandHeightPx}:
     * centred, at the foot, kept inside the band.
     */
    @NonNull
    public static float[] pillRect(float bandWidthPx, float bandHeightPx, float density) {
        float width = Math.min(PILL_WIDTH_DP * density, Math.max(0f, bandWidthPx));
        float left = (bandWidthPx - width) / 2f;
        float bottom = Math.max(0f, bandHeightPx - PILL_FOOT_DP * density);
        float top = Math.max(0f, bottom - PILL_HEIGHT_DP * density);
        return new float[]{left, top, left + width, bottom};
    }

    /**
     * How far the handle's foot stands above the screen's bottom: the bar's own bottom number
     * ({@link ControlBarPlacement#bottomAbovePx}), so the handle clears the gesture line the bar did.
     */
    public static float bottomAbovePx(int navigationPx, float drawnStackPx, int gapPx) {
        return ControlBarPlacement.bottomAbovePx(navigationPx, drawnStackPx, gapPx);
    }

    /**
     * The handle's foot above the screen's bottom: the bar's ({@code barBottomPx}), or higher where
     * Android's own bottom gesture reaches ({@code gestureInsetPx}, its mandatory gesture inset),
     * which no app can claim, so a slide from the handle is the handle's and never Android's
     * (round 3: only Back brought the bar back).
     */
    public static int bottomClearOfGesturesPx(int barBottomPx, int gestureInsetPx) {
        return Math.max(barBottomPx, Math.max(0, gestureInsetPx));
    }

    /** Whether a touch that moved ({@code dxPx}, {@code dyPx}) and lifted is a tap on the handle. */
    public static boolean isTap(float dyPx, float dxPx, float slopPx) {
        return Math.hypot(dxPx, dyPx) <= slopPx;
    }

    /** Whether a touch moved ({@code dxPx}, {@code dyPx}) from its start is a slide up. */
    public static boolean isSlideUp(float dyPx, float dxPx, float density) {
        return dyPx <= -SLIDE_DP * density && Math.abs(dyPx) > 2f * Math.abs(dxPx);
    }

    /** Whether ({@code x}, {@code y}) lies in a band {@code width} by {@code height}. */
    public static boolean bandContains(float x, float y, float width, float height) {
        return x >= 0f && x <= width && y >= 0f && y <= height;
    }

    /**
     * How far below its place the raising bar stands: its whole height at the start ({@code progress}
     * 0) to 0 at the end (1); with reduced motion it is in place at once.
     */
    public static float raiseOffsetPx(float barHeightPx, float progress, boolean reducedMotion) {
        if (reducedMotion) return 0f;
        return Math.max(0f, barHeightPx) * (1f - Math.max(0f, Math.min(1f, progress)));
    }
}
