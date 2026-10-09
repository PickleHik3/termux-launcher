package com.termux.app.tour;

import androidx.annotation.NonNull;

/**
 * The run's finger demonstration as numbers: where one pass of the loop is at a moment, and how
 * many passes it plays before it rests.
 *
 * <p>A swipe pass (1.9 s) lands the finger, presses it, carries it along the gesture while a trail
 * draws behind it, then lifts it away. A hold pass (1.8 s) lands the finger, presses it down and
 * sends a ring out from under it. The zone around the control breathes in step with the swipe.
 *
 * <p>The launcher draws nothing that repaints for ever, so the loop plays {@link #PASSES} times
 * and then settles on the still outline of the zone; a new card or stage plays it again.
 *
 * <p>Pure, and every curve below is a cubic Bézier given the way CSS gives one, so a frame of the
 * demonstration is a unit test.
 */
public final class TourDemoLoop {

    /** How many passes play before the zone rests. */
    public static final int PASSES = 3;
    /** One swipe pass, and one breath of the zone's glow. */
    public static final long SWIPE_PASS_MS = 1900L;
    /** One hold pass. */
    public static final long HOLD_PASS_MS = 1800L;
    /** The faintest the zone's glow gets while it breathes. */
    public static final float GLOW_MIN = 0.35f;
    /** The finger: a disc this wide, with a halo this thick around it. */
    public static final float FINGER_DIAMETER_DP = 34f;
    public static final float FINGER_HALO_DP = 6f;
    /** The scale the swipe's finger lands from, and the hold's. */
    private static final float LAND_SCALE = 1.35f;
    /** The swipe's pressed finger, and the scale it lifts away at. */
    private static final float SWIPE_PRESS_SCALE = 0.92f;
    private static final float SWIPE_LIFT_SCALE = 0.85f;
    /** The hold's pressed finger. */
    private static final float HOLD_PRESS_SCALE = 0.86f;
    /** The hold's ring, from under the finger to its widest. */
    private static final float RING_FROM_SCALE = 0.7f;
    private static final float RING_TO_SCALE = 2.3f;
    private static final float RING_PEAK_ALPHA = 0.9f;

    /** The swipe's curve, cubic-bezier(.5, 0, .3, 1). */
    private static final float[] SWIPE_CURVE = {0.5f, 0f, 0.3f, 1f};
    /** CSS ease-in-out, the hold's and the glow's. */
    private static final float[] EASE_IN_OUT = {0.42f, 0f, 0.58f, 1f};
    /** CSS ease-out, the ring's. */
    private static final float[] EASE_OUT = {0f, 0f, 0.58f, 1f};

    /** One frame of a pass. */
    public static final class Frame {
        /** The finger's opacity, 0..1. */
        public float fingerAlpha;
        /** The finger's scale against its resting size. */
        public float fingerScale;
        /** How far along the gesture the finger is, 0 at the control and 1 at the end. */
        public float travel;
        /** How much of the trail is drawn, 0..1, and how strongly. */
        public float trailLength;
        public float trailAlpha;
        /** The hold's ring, against the finger's size, and its opacity. */
        public float ringScale;
        public float ringAlpha;
    }

    /** Whether a gesture plays the hold pass rather than the swipe pass. */
    public static boolean isHold(@NonNull TourGesture gesture) {
        return gesture == TourGesture.HOLD || gesture == TourGesture.TAP;
    }

    /** How long one pass of {@code gesture} takes. */
    public static long passMs(@NonNull TourGesture gesture) {
        return isHold(gesture) ? HOLD_PASS_MS : SWIPE_PASS_MS;
    }

    /**
     * Fills {@code out} for {@code progress}, 0..1 through one pass.
     */
    public static void frame(@NonNull TourGesture gesture, float progress, @NonNull Frame out) {
        float t = clamp01(progress);
        if (isHold(gesture)) holdFrame(t, out);
        else swipeFrame(t, out);
    }

    /**
     * How far the demonstration of {@code gesture} reaches past its control on one side, in dp:
     * the travel when the finger moves that way, and otherwise the widest the finger itself draws —
     * its halo, or the hold's ring. A card standing closer than this would sit on the finger.
     */
    public static float reachDp(@NonNull TourGesture gesture, boolean below) {
        float radius = FINGER_DIAMETER_DP / 2f;
        float still = isHold(gesture) ? radius * RING_TO_SCALE : radius + FINGER_HALO_DP;
        boolean travelsDown = gesture == TourGesture.DRAG_DOWN || gesture == TourGesture.DRAG_UP
            || gesture == TourGesture.SWIPE_DOWN_LEFT;
        boolean travelsUp = gesture == TourGesture.SWIPE_UP || gesture == TourGesture.SWIPE_UP_LEFT;
        if (below ? travelsDown : travelsUp) return TourFingerTrace.TRAVEL_DP + still;
        return still;
    }

    /** The zone's glow, 0..1, {@code elapsedMs} into the loop: it breathes once per swipe pass. */
    public static float glowAlpha(long elapsedMs) {
        float phase = (Math.max(0L, elapsedMs) % SWIPE_PASS_MS) / (float) SWIPE_PASS_MS;
        // 0% and 100% are the faintest, 50% the brightest.
        float half = phase < 0.5f ? phase / 0.5f : (1f - phase) / 0.5f;
        return GLOW_MIN + ((1f - GLOW_MIN) * bezier(EASE_IN_OUT, half));
    }

    private static void swipeFrame(float t, @NonNull Frame out) {
        out.ringScale = 0f;
        out.ringAlpha = 0f;
        // Lands (0-12%), presses (12-24%), travels (24-70%), lifts away (70-86%).
        if (t < 0.12f) {
            float k = bezier(SWIPE_CURVE, t / 0.12f);
            out.fingerAlpha = k;
            out.fingerScale = lerp(LAND_SCALE, 1f, k);
            out.travel = 0f;
        } else if (t < 0.24f) {
            float k = bezier(SWIPE_CURVE, (t - 0.12f) / 0.12f);
            out.fingerAlpha = 1f;
            out.fingerScale = lerp(1f, SWIPE_PRESS_SCALE, k);
            out.travel = 0f;
        } else if (t < 0.70f) {
            float k = bezier(SWIPE_CURVE, (t - 0.24f) / 0.46f);
            out.fingerAlpha = 1f;
            out.fingerScale = SWIPE_PRESS_SCALE;
            out.travel = k;
        } else if (t < 0.86f) {
            float k = bezier(SWIPE_CURVE, (t - 0.70f) / 0.16f);
            out.fingerAlpha = 1f - k;
            out.fingerScale = lerp(SWIPE_PRESS_SCALE, SWIPE_LIFT_SCALE, k);
            out.travel = 1f;
        } else {
            out.fingerAlpha = 0f;
            out.fingerScale = SWIPE_LIFT_SCALE;
            out.travel = 1f;
        }
        // The trail grows with the travel (24-70%), shows from 24-28% and fades from 70-90%.
        if (t < 0.24f) {
            out.trailLength = 0f;
            out.trailAlpha = 0f;
        } else if (t < 0.70f) {
            out.trailLength = bezier(SWIPE_CURVE, (t - 0.24f) / 0.46f);
            out.trailAlpha = t < 0.28f ? bezier(SWIPE_CURVE, (t - 0.24f) / 0.04f) : 1f;
        } else {
            out.trailLength = 1f;
            out.trailAlpha = t < 0.90f ? 1f - bezier(SWIPE_CURVE, (t - 0.70f) / 0.20f) : 0f;
        }
    }

    private static void holdFrame(float t, @NonNull Frame out) {
        out.travel = 0f;
        out.trailLength = 0f;
        out.trailAlpha = 0f;
        // Lands (0-14%), presses (14-30%), stays down (30-80%), lifts (80-100%).
        if (t < 0.14f) {
            float k = bezier(EASE_IN_OUT, t / 0.14f);
            out.fingerAlpha = k;
            out.fingerScale = lerp(LAND_SCALE, 1f, k);
        } else if (t < 0.30f) {
            float k = bezier(EASE_IN_OUT, (t - 0.14f) / 0.16f);
            out.fingerAlpha = 1f;
            out.fingerScale = lerp(1f, HOLD_PRESS_SCALE, k);
        } else if (t < 0.80f) {
            out.fingerAlpha = 1f;
            out.fingerScale = HOLD_PRESS_SCALE;
        } else {
            out.fingerAlpha = 1f - bezier(EASE_IN_OUT, (t - 0.80f) / 0.20f);
            out.fingerScale = HOLD_PRESS_SCALE;
        }
        // The ring waits under the finger (0-25%), shows (25-30%) and spreads out as it fades
        // (25-85%).
        if (t < 0.25f) {
            out.ringScale = RING_FROM_SCALE;
            out.ringAlpha = 0f;
        } else if (t < 0.85f) {
            out.ringScale = lerp(RING_FROM_SCALE, RING_TO_SCALE,
                bezier(EASE_OUT, (t - 0.25f) / 0.60f));
            out.ringAlpha = t < 0.30f
                ? RING_PEAK_ALPHA * bezier(EASE_OUT, (t - 0.25f) / 0.05f)
                : RING_PEAK_ALPHA * (1f - bezier(EASE_OUT, (t - 0.30f) / 0.55f));
        } else {
            out.ringScale = RING_TO_SCALE;
            out.ringAlpha = 0f;
        }
    }

    /**
     * A CSS cubic-bezier(x1, y1, x2, y2) at {@code x}: the curve's x is solved for its parameter,
     * then its y is read off. Newton's method first, bisection when the slope is too flat.
     */
    static float bezier(@NonNull float[] curve, float x) {
        float bounded = clamp01(x);
        float x1 = curve[0];
        float y1 = curve[1];
        float x2 = curve[2];
        float y2 = curve[3];
        float u = bounded;
        for (int i = 0; i < 8; i++) {
            float error = sample(x1, x2, u) - bounded;
            if (Math.abs(error) < 1e-5f) return sample(y1, y2, u);
            float slope = slope(x1, x2, u);
            if (Math.abs(slope) < 1e-6f) break;
            u -= error / slope;
        }
        float low = 0f;
        float high = 1f;
        u = bounded;
        for (int i = 0; i < 32; i++) {
            float value = sample(x1, x2, u);
            if (Math.abs(value - bounded) < 1e-5f) break;
            if (value < bounded) low = u;
            else high = u;
            u = (low + high) / 2f;
        }
        return sample(y1, y2, u);
    }

    /** One coordinate of the curve from (0,0) through its two handles to (1,1), at {@code u}. */
    private static float sample(float p1, float p2, float u) {
        float inverse = 1f - u;
        return (3f * inverse * inverse * u * p1) + (3f * inverse * u * u * p2) + (u * u * u);
    }

    private static float slope(float p1, float p2, float u) {
        float inverse = 1f - u;
        return (3f * inverse * inverse * p1) + (6f * inverse * u * (p2 - p1))
            + (3f * u * u * (1f - p2));
    }

    private static float lerp(float from, float to, float k) {
        return from + ((to - from) * k);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(1f, value);
    }

    private TourDemoLoop() {}
}
