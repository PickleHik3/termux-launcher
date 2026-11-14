package com.termux.app.terminal.extrakeys;

import com.termux.app.terminal.extrakeys.HomePillSpot.Rect;

/**
 * The maths of the Home pill's one-shot flights: where the flying object is at {@code t}, what
 * fades while it goes, and the pose the keyboard drags it through. Pure, draw-only, and
 * allocation-free per frame (it writes into the caller's array).
 */
public final class HomePillFlight {

    public static final long DURATION_MS = 280L;
    /** The cross-fade runs over this span of the flight. */
    public static final float FADE_START = 0.25f;
    public static final float FADE_END = 0.75f;

    private HomePillFlight() {}

    /**
     * The transform of the object at {@code t} (already interpolated, 0 at {@code from}, 1 at
     * {@code to}) for a view laid out at {@code base} (the from rect or the to rect): {@code out} is
     * translationX, translationY, scaleX, scaleY, with the pivot at the view's centre (the
     * default).
     */
    public static void transform(Rect from, Rect to, float t, Rect base, float[] out) {
        float cx = lerp(from.centerX(), to.centerX(), t);
        float cy = lerp(from.centerY(), to.centerY(), t);
        float w = lerp(from.width(), to.width(), t);
        float h = lerp(from.height(), to.height(), t);
        out[0] = cx - base.centerX();
        out[1] = cy - base.centerY();
        out[2] = base.width() <= 0f ? 1f : w / base.width();
        out[3] = base.height() <= 0f ? 1f : h / base.height();
    }

    /** The cross-fade position: 0 before {@link #FADE_START}, 1 after {@link #FADE_END}. */
    public static float crossFade(float t) {
        float c = (t - FADE_START) / (FADE_END - FADE_START);
        return Math.max(0f, Math.min(1f, c));
    }

    /** The keys leaving. */
    public static float outgoingAlpha(float t) {
        return 1f - crossFade(t);
    }

    /** The keys arriving. */
    public static float incomingAlpha(float t) {
        return crossFade(t);
    }

    /**
     * The pill's rect while the keyboard docks it: {@code p} 0 is the floating pill, 1 the docked
     * pose. {@code out} is left, top, right, bottom; the caller adds the stack's own translation.
     */
    public static void keyboardPose(Rect pill, Rect docked, float p, float[] out) {
        float q = Math.max(0f, Math.min(1f, p));
        out[0] = lerp(pill.left, docked.left, q);
        out[1] = lerp(pill.top, docked.top, q);
        out[2] = lerp(pill.right, docked.right, q);
        out[3] = lerp(pill.bottom, docked.bottom, q);
    }

    /**
     * The same pose from plain arrays (left, top, right, bottom), for the per-frame path, which
     * builds no {@link Rect}. {@code out} may be {@code pill}.
     */
    public static void keyboardPose(float[] pill, float[] docked, float p, float[] out) {
        float q = Math.max(0f, Math.min(1f, p));
        for (int i = 0; i < 4; i++) out[i] = lerp(pill[i], docked[i], q);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
