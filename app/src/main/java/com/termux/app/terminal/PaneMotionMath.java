package com.termux.app.terminal;

/**
 * The arithmetic behind the pane layer's motion, kept free of Android types so it can be tested.
 *
 * <p>Two things live here. The first is the predicate every animation has to pass: kitty and niri
 * both refuse to animate what they cannot see or have not measured — niri skips a close with no
 * snapshot, kitty snaps its trail during a live resize — and the launcher's ghost-over-hidden-pane
 * and cursor-flight-from-a-detached-view faults were both that rule missing.
 *
 * <p>The second is kitty's cursor-trail law. It is first order: each corner moves a fraction of the
 * remaining distance per frame, with the fraction derived from a decay time. No velocity is carried,
 * so a dropped frame cannot make it overshoot — the property that makes it a better fit for a
 * {@code Canvas} port than neovide's per-corner springs.
 */
public final class PaneMotionMath {

    /** Corner decay times, seconds: the leading corners snap, the trailing ones drag. */
    public static final float DECAY_FAST = 0.10f;
    public static final float DECAY_SLOW = 0.40f;
    /** Travel below this many cells is a nudge, not a journey; kitty's own threshold. */
    public static final float MIN_TRAVEL_CELLS = 2f;
    /** A corner within this fraction of a cell of its target has arrived. */
    public static final float SETTLE_CELLS = 0.5f;

    private PaneMotionMath() {}

    /**
     * Whether a view may be animated at all: laid out, attached, and on screen.
     *
     * <p>Size alone is not enough, which is the trap this exists for — a detached view keeps its
     * last measured width and height, so a size check passes while {@code getLocationOnScreen}
     * reports {@code 0,0} and every derived rect lands at the screen origin.
     */
    public static boolean canAnimate(boolean attached, boolean shown, int width, int height) {
        return attached && shown && width > 0 && height > 0;
    }

    /** Whether a flight over {@code distancePx} is worth drawing, given the cell size. */
    public static boolean isTravelWorthAnimating(float distancePx, float cellWidthPx) {
        return cellWidthPx > 0f && distancePx >= MIN_TRAVEL_CELLS * cellWidthPx;
    }

    /**
     * kitty's per-frame step: the fraction of the remaining distance a corner covers in {@code dt}
     * seconds at this decay time. Frame-rate independent by construction — halving the frame rate
     * doubles {@code dt} and lands on the same curve.
     */
    public static float step(float dtSeconds, float decaySeconds) {
        if (dtSeconds <= 0f || decaySeconds <= 0f) return 1f;
        double fraction = 1d - Math.pow(2d, -10d * dtSeconds / decaySeconds);
        return (float) Math.max(0d, Math.min(1d, fraction));
    }

    /**
     * The decay time for one corner, from how much that corner leads the travel direction.
     *
     * @param normalisedAlignment 0 for the most trailing corner, 1 for the most leading one
     */
    public static float cornerDecay(float normalisedAlignment) {
        float clamped = normalisedAlignment < 0f ? 0f : (normalisedAlignment > 1f ? 1f : normalisedAlignment);
        return DECAY_SLOW + (DECAY_FAST - DECAY_SLOW) * clamped;
    }

    /**
     * Min-max normalisation of the four corners' alignment with the travel direction, in place.
     *
     * <p>Absolute dot products would make a short travel barely differentiate its corners, so the
     * spread is normalised across the four: whichever corner leads becomes 1, whichever trails
     * becomes 0, and the deformation is the same shape at any distance. A travel with no direction
     * (all four equal) leaves every corner leading, which collapses the smear to a plain move.
     */
    public static void normaliseAlignments(float[] alignments) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : alignments) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        float span = max - min;
        for (int i = 0; i < alignments.length; i++) {
            alignments[i] = span <= 1e-4f ? 1f : (alignments[i] - min) / span;
        }
    }

    /** True once every corner offset is inside half a cell, i.e. the trail has caught up. */
    public static boolean hasSettled(float[] offsets, float cellHeightPx) {
        float tolerance = SETTLE_CELLS * Math.max(1f, cellHeightPx);
        for (float offset : offsets) {
            if (Math.abs(offset) > tolerance) return false;
        }
        return true;
    }
}
