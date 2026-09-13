package com.termux.app.tour;

/**
 * Where the finger glyph sits at a moment of a trace, derived from the target's bounds.
 *
 * <p>Pure geometry in pixels so the overlay only has to draw the answer, and so the paths can be
 * checked without a screen. Every trace starts and ends on the control it is teaching: a finger
 * that flies in from off-screen reads as decoration, not as an instruction.
 */
public final class TourFingerTrace {

    /** One pass of the trace. Long enough to read, short enough not to become an animation. */
    public static final long TRACE_MS = 1100L;

    /** How far a swipe or drag travels beyond the control, in dp. */
    private static final float TRAVEL_DP = 56f;

    /** The gap the finger keeps from the exact edge it is dragging from, in dp. */
    private static final float INSET_DP = 10f;

    /**
     * Fills {@code out} with the finger's x and y for {@code progress} in 0..1.
     *
     * @param density the display density, so travel reads the same on every phone
     */
    public static void pointAt(TourGesture gesture, float left, float top, float right,
                               float bottom, float density, float progress, float[] out) {
        float eased = ease(clamp01(progress));
        float centerX = (left + right) / 2f;
        float centerY = (top + bottom) / 2f;
        float travel = TRAVEL_DP * density;
        float inset = INSET_DP * density;
        float halfWidth = Math.max(0f, (right - left) / 2f - inset);
        float reach = Math.min(travel, Math.max(travel * 0.4f, halfWidth));
        switch (gesture) {
            case SWIPE_LEFT:
                out[0] = centerX + reach - (2f * reach * eased);
                out[1] = centerY;
                break;
            case SWIPE_RIGHT:
                out[0] = centerX - reach + (2f * reach * eased);
                out[1] = centerY;
                break;
            case SWIPE_UP:
                out[0] = centerX;
                out[1] = bottom - inset - (travel * eased);
                break;
            case DRAG_DOWN:
                out[0] = centerX;
                out[1] = centerY + (travel * eased);
                break;
            case DRAG_UP:
                out[0] = centerX;
                out[1] = centerY + travel - (travel * eased);
                break;
            case SCRUB:
                scrubPoint(left, right, centerY, inset, travel, eased, out);
                break;
            case TAP:
            case NONE:
            default:
                out[0] = centerX;
                out[1] = centerY;
                break;
        }
    }

    /**
     * The tap's ring, 0 at rest and 1 at its widest. Taps have nowhere to travel, so the pulse is
     * what carries them.
     */
    public static float tapPulse(float progress) {
        float bounded = clamp01(progress);
        if (bounded < 0.25f) return bounded / 0.25f;
        return Math.max(0f, 1f - ((bounded - 0.25f) / 0.75f));
    }

    /** Slide along the row for the first two thirds, then lift away from it. */
    private static void scrubPoint(float left, float right, float centerY, float inset,
                                   float travel, float eased, float[] out) {
        float slideEnd = 0.66f;
        float startX = left + inset;
        float endX = Math.max(startX, right - inset);
        if (eased <= slideEnd) {
            out[0] = startX + ((endX - startX) * (eased / slideEnd));
            out[1] = centerY;
        } else {
            float lift = (eased - slideEnd) / (1f - slideEnd);
            out[0] = endX;
            out[1] = centerY - (travel * lift);
        }
    }

    /** Smoothstep, so the finger leaves and arrives at rest rather than snapping. */
    private static float ease(float t) {
        return t * t * (3f - (2f * t));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(1f, value);
    }

    private TourFingerTrace() {}
}
