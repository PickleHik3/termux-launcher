package com.termux.app.terminal.extrakeys;

/** The numbers behind the bar's page drag and the swipe-up bubble. Pure; the view applies them. */
public final class ExtraKeyGesturePolicy {

    public static final float FLIP_FRACTION = 0.30f;
    public static final int SLIDE_MS = 160;
    public static final float END_RESISTANCE = 0.25f;
    public static final float BUBBLE_MAX_LIFT_DP = 60f;

    private ExtraKeyGesturePolicy() {}

    public static float swipeProgress(float upPx, float fireDistancePx) {
        if (fireDistancePx <= 0f) return upPx > 0f ? 1f : 0f;
        float p = upPx / fireDistancePx;
        return p < 0f ? 0f : Math.min(p, 1f);
    }

    public static float bubbleAlpha(float p) {
        return Math.min(1f, 1.6f * p);
    }

    public static float bubbleScale(float p) {
        return 0.5f + 0.5f * p;
    }

    public static float bubbleLiftPx(float upPx, float maxLiftPx) {
        return Math.min(Math.max(upPx, 0f), maxLiftPx) / 2f;
    }

    /** dx below zero drags toward the next page. */
    public static int landingPage(int current, int pageCount, float dxPx, float barWidthPx) {
        if (Math.abs(dxPx) <= FLIP_FRACTION * barWidthPx) return current;
        int target = dxPx < 0f ? current + 1 : current - 1;
        return target >= 0 && target < pageCount ? target : current;
    }

    public static float dragOffsetPx(int current, int pageCount, float dxPx) {
        boolean pastFirst = current <= 0 && dxPx > 0f;
        boolean pastLast = current >= pageCount - 1 && dxPx < 0f;
        return pastFirst || pastLast ? dxPx * END_RESISTANCE : dxPx;
    }
}
