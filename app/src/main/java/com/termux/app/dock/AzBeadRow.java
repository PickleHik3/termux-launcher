package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The A–Z row's letter beads under Lens beads ({@link LensBeadGeometry#azRowWearsBeads}): one
 * small round bead of the icons' glass under each letter of the row (the ☆ favourites key and
 * the # key included), the icons' bead geometry scaled to the letter row.
 *
 * <p>The letters stand on even slots across the row ({@code AzLetterTrack}), so the row is
 * described by its first centre, its pitch and its count; the glass program finds a pixel's bead
 * from those alone, with no array of centres (any number of letters costs the same). A bead
 * keeps {@link #GAP_SHARE} of its slot as air to its neighbour, as an icon's bead keeps
 * {@link LensBeadGeometry#MIN_GAP_DP} of a slot about ten times wider, and stays
 * {@link LensBeadGeometry#EDGE_CLEARANCE_DP} inside the row's own band.</p>
 *
 * <p>The lift: while a finger is down on the row each bead and its letter rise by a smooth
 * falloff centred on the finger ({@link #liftShare}): the bead under it by about
 * {@link #LIFT_PEAK_DP}, its neighbours less, nothing past {@link #LIFT_REACH_SLOTS} letters.
 * The finger's coming and going is a short spring ({@link #springShare}). Pure arithmetic: the
 * row view, the dock host's clip and the glass program all draw from these numbers.</p>
 */
public final class AzBeadRow {

    /** How far the bead under the finger rises, dp. */
    public static final float LIFT_PEAK_DP = 7f;
    /** How many letters to each side the lift reaches before it is nothing. */
    public static final float LIFT_REACH_SLOTS = 2.5f;
    /** The share of a letter's slot left as air between two beads. */
    public static final float GAP_SHARE = 0.12f;
    /** The least air between two beads, dp, on a crowded row. */
    public static final float MIN_GAP_DP = 1.5f;
    /** A bead narrower than this, dp, is no lens: the row keeps no letter beads. */
    public static final float MIN_DIAMETER_DP = 6f;
    /** How long the spring runs before it is held at its end, ms. */
    public static final long SPRING_MS = 320L;
    /** The spring's natural frequency, rad/s, and its damping ratio: a short, slight overshoot. */
    static final float SPRING_OMEGA = 28f;
    static final float SPRING_ZETA = 0.55f;

    /** The first letter's bead centre x, in the surface's coordinates. */
    public final float firstCenterX;
    /** From one bead's centre to the next. */
    public final float pitchPx;
    /** How many beads: one per letter shown. */
    public final int count;
    /** The row's centre line at rest. */
    public final float centerY;
    /** Every bead's radius. */
    public final float radiusPx;

    AzBeadRow(float firstCenterX, float pitchPx, int count, float centerY, float radiusPx) {
        this.firstCenterX = firstCenterX;
        this.pitchPx = pitchPx;
        this.count = count;
        this.centerY = centerY;
        this.radiusPx = radiusPx;
    }

    /**
     * The letter beads of a row whose {@code count} letters stand on slots {@code pitchPx} wide,
     * the first centred at {@code firstCenterX}, along a band {@code bandHeightPx} tall centred on
     * {@code centerY}; null where the row has no letters or a bead would be too small to read as
     * a lens.
     */
    @Nullable
    public static AzBeadRow compute(float firstCenterX, float pitchPx, int count, float centerY,
                                    float bandHeightPx, float density) {
        if (count <= 0 || !(pitchPx > 0f) || !(bandHeightPx > 0f) || Float.isNaN(firstCenterX)
            || Float.isNaN(centerY)) return null;
        float d = Math.max(0f, density);
        float gap = Math.max(MIN_GAP_DP * d, GAP_SHARE * pitchPx);
        float diameter = Math.min(pitchPx - gap,
            bandHeightPx - 2f * LensBeadGeometry.EDGE_CLEARANCE_DP * d);
        if (!(diameter >= MIN_DIAMETER_DP * d)) return null;
        return new AzBeadRow(firstCenterX, pitchPx, count, centerY, diameter * 0.5f);
    }

    /** Bead {@code index}'s centre x. */
    public float centerX(int index) {
        return firstCenterX + index * pitchPx;
    }

    /** The same beads in a surface whose origin sits {@code (-dx, -dy)} from this one's. */
    @NonNull
    public AzBeadRow offset(float dx, float dy) {
        return new AzBeadRow(firstCenterX + dx, pitchPx, count, centerY + dy, radiusPx);
    }

    /**
     * The share of the lift a bead {@code distanceSlots} letters from the finger takes: 1 under
     * it, falling on a raised cosine to 0 at {@link #LIFT_REACH_SLOTS}, nothing beyond.
     */
    public static float liftShare(float distanceSlots) {
        if (Float.isNaN(distanceSlots)) return 0f;
        float d = Math.abs(distanceSlots);
        if (d >= LIFT_REACH_SLOTS) return 0f;
        return 0.5f + 0.5f * (float) Math.cos(Math.PI * d / LIFT_REACH_SLOTS);
    }

    /**
     * How far a bead centred at {@code beadX} rises, px (up is positive), for a finger at
     * {@code fingerX} on slots {@code pitchPx} wide and a peak of {@code peakPx} (the lift at
     * its whole, {@link #LIFT_PEAK_DP} in px, times the spring's share now). NaN is no finger.
     */
    public static float liftPx(float beadX, float fingerX, float pitchPx, float peakPx) {
        if (Float.isNaN(fingerX) || peakPx == 0f || Float.isNaN(peakPx) || !(pitchPx > 0f))
            return 0f;
        return peakPx * liftShare((beadX - fingerX) / pitchPx);
    }

    /**
     * The spring's share of the way still to go, {@code elapsedMs} after it set off: 1 at the
     * start, 0 at rest, through a slight overshoot below 0 on the way (an underdamped spring at
     * {@link #SPRING_OMEGA}, {@link #SPRING_ZETA}); held at 0 from {@link #SPRING_MS}, where
     * what is left is under a hundredth.
     */
    public static float springShare(float elapsedMs) {
        if (Float.isNaN(elapsedMs) || elapsedMs <= 0f) return 1f;
        if (elapsedMs >= SPRING_MS) return 0f;
        double t = elapsedMs / 1000.0;
        double decay = SPRING_ZETA * SPRING_OMEGA;
        double damped = SPRING_OMEGA * Math.sqrt(1.0 - SPRING_ZETA * SPRING_ZETA);
        return (float) (Math.exp(-decay * t)
            * (Math.cos(damped * t) + (decay / damped) * Math.sin(damped * t)));
    }

    /** A spring from {@code from} to {@code to}, {@code elapsedMs} in. */
    public static float spring(float from, float to, float elapsedMs) {
        return to + (from - to) * springShare(elapsedMs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AzBeadRow)) return false;
        AzBeadRow that = (AzBeadRow) o;
        return count == that.count && Float.compare(firstCenterX, that.firstCenterX) == 0
            && Float.compare(pitchPx, that.pitchPx) == 0
            && Float.compare(centerY, that.centerY) == 0
            && Float.compare(radiusPx, that.radiusPx) == 0;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(firstCenterX);
        h = 31 * h + Float.floatToIntBits(pitchPx);
        h = 31 * h + count;
        h = 31 * h + Float.floatToIntBits(centerY);
        return 31 * h + Float.floatToIntBits(radiusPx);
    }

    @NonNull
    @Override
    public String toString() {
        return "AzBeadRow{x0=" + firstCenterX + " pitch=" + pitchPx + " n=" + count + " y="
            + centerY + " r=" + radiusPx + "}";
    }
}
