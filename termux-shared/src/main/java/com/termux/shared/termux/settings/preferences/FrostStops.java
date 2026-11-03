package com.termux.shared.termux.settings.preferences;

/**
 * Frost's eight stops (ADR 0017 §4) and the glass curve they sit on, the one place either lives.
 *
 * <p>The curve runs over the old 0 to 100 Glass scale: {@code blur = R(4 + 32 t^1.5)} dp and
 * {@code grain = R(2 + 6 t)} percent with {@code t = position / 100} and {@code R} rounding half
 * up; position 0 is no frost at all, blur 0 and grain 0. The stops are the positions 0, 10, 20,
 * 40, 55, 75, 90 and 100, chosen so that every shipped Look lands on one (Solid 0, Clear 20,
 * Tint 40, Mist 75): eight distinct blur radii, which is what the blur cache is sized for.</p>
 */
public final class FrostStops {

    private FrostStops() {}

    /** The curve's range, the old Glass scale. */
    public static final int POSITION_MIN = 0;
    public static final int POSITION_MAX = 100;

    /** Each stop's position on the curve. */
    private static final int[] POSITIONS = {0, 10, 20, 40, 55, 75, 90, 100};

    public static final int COUNT = POSITIONS.length;
    public static final int LAST = COUNT - 1;

    /** The shipped Looks' stops. */
    public static final int SOLID = 0;
    public static final int CLEAR = 2;
    public static final int TINT = 3;
    public static final int MIST = 5;

    /** A stop's position on the old Glass scale. */
    public static int positionOf(int stop) {
        return POSITIONS[clampStop(stop)];
    }

    /** The blur a curve position writes, in dp: {@code R(4 + 32 t^1.5)}, 4 to 36; 0 at 0. */
    public static int blurDpAt(int position) {
        int p = clampPosition(position);
        if (p == POSITION_MIN) return 0;
        double t = p / 100d;
        return (int) Math.round(4d + 32d * Math.pow(t, 1.5d));
    }

    /** The grain a curve position writes, in percent: {@code R(2 + 6 t)}, 2 to 8; 0 at 0. */
    public static int grainPercentAt(int position) {
        int p = clampPosition(position);
        if (p == POSITION_MIN) return 0;
        double t = p / 100d;
        return (int) Math.round(2d + 6d * t);
    }

    /** A stop's blur in dp. */
    public static int blurDpFor(int stop) {
        return blurDpAt(positionOf(stop));
    }

    /** A stop's grain in percent. */
    public static int grainPercentFor(int stop) {
        return grainPercentAt(positionOf(stop));
    }

    /** The stop nearest a curve position, the lower one on a tie. */
    public static int nearestStop(int position) {
        int p = clampPosition(position);
        int best = 0;
        for (int stop = 1; stop < COUNT; stop++) {
            if (Math.abs(POSITIONS[stop] - p) < Math.abs(POSITIONS[best] - p)) best = stop;
        }
        return best;
    }

    /**
     * The stop whose pair is nearest a stored blur and grain: squared differences summed, the
     * lower stop on a tie.
     */
    public static int nearestStop(int blurDp, int grainPercent) {
        int best = 0;
        long bestError = Long.MAX_VALUE;
        for (int stop = 0; stop < COUNT; stop++) {
            long db = blurDpFor(stop) - blurDp;
            long dg = grainPercentFor(stop) - grainPercent;
            long error = db * db + dg * dg;
            if (error < bestError) {
                bestError = error;
                best = stop;
            }
        }
        return best;
    }

    /** The stop whose blur is nearest {@code blurDp}, the lower one on a tie. */
    public static int nearestStopByBlur(int blurDp) {
        int best = 0;
        for (int stop = 1; stop < COUNT; stop++) {
            if (Math.abs(blurDpFor(stop) - blurDp) < Math.abs(blurDpFor(best) - blurDp)) best = stop;
        }
        return best;
    }

    public static int clampStop(int stop) {
        return Math.max(0, Math.min(LAST, stop));
    }

    public static int clampPosition(int position) {
        return Math.max(POSITION_MIN, Math.min(POSITION_MAX, position));
    }
}
