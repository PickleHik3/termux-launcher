package com.termux.view;

import java.util.Arrays;

/**
 * The least contrast text keeps against what it actually stands on, by WCAG 2 relative luminance:
 * {@link #TEXT} for normal text and {@link #DIM_TEXT} for faint (SGR 2) text. A colour already at
 * its floor comes back unchanged, so a well-chosen theme is never touched; one below it is mixed
 * toward white or black — away from the backdrop where that reaches the floor — by the least amount
 * that does, which keeps its hue. Because faint text is floored lower than normal text, a nudge
 * never makes it read louder than the normal text beside it.
 *
 * <p>Pure: colours in, colours out, so it unit tests on the JVM. The terminal renderer and the
 * launcher's dock both hold their text to it. {@link Memo} remembers answers for one backdrop, so
 * a draw pass never runs the search per glyph.</p>
 */
public final class ContrastFloor {

    /** WCAG AA for body text. */
    public static final double TEXT = 4.5d;
    /** Faint text: WCAG's large-text floor, so it still recedes behind {@link #TEXT}. */
    public static final double DIM_TEXT = 3.0d;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    /** Steps the mix toward white or black is searched in. */
    private static final int MIX_STEPS = 1024;

    /** sRGB channel value to linear light, per WCAG 2. */
    private static final double[] LINEAR = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255d;
            LINEAR[i] = c <= 0.04045d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
        }
    }

    private ContrastFloor() {}

    /** WCAG 2 relative luminance of {@code color}'s RGB, 0 for black to 1 for white; alpha ignored. */
    public static double luminance(int color) {
        return 0.2126d * LINEAR[(color >> 16) & 0xFF] + 0.7152d * LINEAR[(color >> 8) & 0xFF]
            + 0.0722d * LINEAR[color & 0xFF];
    }

    /** WCAG 2 contrast ratio between two colours' RGB, 1 to 21; alpha ignored. */
    public static double contrast(int a, int b) {
        return ratio(luminance(a), luminance(b));
    }

    private static double ratio(double la, double lb) {
        return la >= lb ? (la + 0.05d) / (lb + 0.05d) : (lb + 0.05d) / (la + 0.05d);
    }

    /**
     * Faint text the way xterm and libvte derive it: two thirds of each channel, opaque. Darker,
     * which recedes on a dark backdrop; on a light one it reads darker, not fainter.
     */
    public static int dim(int color) {
        final int red = (0xFF & (color >> 16)) * 2 / 3;
        final int green = (0xFF & (color >> 8)) * 2 / 3;
        final int blue = (0xFF & color) * 2 / 3;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /** {@code over} laid at its own alpha on {@code under} taken as opaque; the result is opaque. */
    public static int composite(int over, int under) {
        final int alpha = over >>> 24;
        if (alpha == 0xFF) return over;
        if (alpha == 0) return under | 0xFF000000;
        return 0xFF000000
            | (blend((over >> 16) & 0xFF, (under >> 16) & 0xFF, alpha) << 16)
            | (blend((over >> 8) & 0xFF, (under >> 8) & 0xFF, alpha) << 8)
            | blend(over & 0xFF, under & 0xFF, alpha);
    }

    private static int blend(int over, int under, int alpha) {
        return (over * alpha + under * (255 - alpha) + 127) / 255;
    }

    /**
     * {@code color} held to {@code floor} against {@code backdrop}: itself where it already reads,
     * otherwise the least mix of it toward white or black that reaches the floor. The mix goes away
     * from the backdrop (lighter text lighter, darker text darker) unless only the other way can
     * get there; where neither can, the better of white and black. Hue is kept, since mixing with
     * white or black moves every channel by the same share. Alpha is kept and ignored, both colours
     * being judged as opaque.
     */
    public static int atLeast(int color, int backdrop, double floor) {
        final double ground = luminance(backdrop);
        final double own = luminance(color);
        if (ratio(own, ground) >= floor) return color;
        final int away = own >= ground ? WHITE : BLACK;
        final int toward = away == WHITE ? BLACK : WHITE;
        final double awayBest = ratio(luminance(away), ground);
        final int extreme;
        if (awayBest >= floor) {
            extreme = away;
        } else {
            final double towardBest = ratio(luminance(toward), ground);
            if (towardBest < floor) {
                return (color & 0xFF000000) | ((awayBest >= towardBest ? away : toward) & 0xFFFFFF);
            }
            extreme = toward;
        }
        // Luminance moves one way as the mix grows, so the floor is crossed once: search for it.
        int lo = 0;
        int hi = MIX_STEPS;
        while (hi - lo > 1) {
            final int mid = (lo + hi) >>> 1;
            if (ratio(luminance(mix(color, extreme, mid)), ground) >= floor) hi = mid;
            else lo = mid;
        }
        return (color & 0xFF000000) | (mix(color, extreme, hi) & 0xFFFFFF);
    }

    /** {@code from} moved {@code steps} of {@link #MIX_STEPS} of the way to {@code to}, per channel. */
    private static int mix(int from, int to, int steps) {
        return (mixChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, steps) << 16)
            | (mixChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, steps) << 8)
            | mixChannel(from & 0xFF, to & 0xFF, steps);
    }

    private static int mixChannel(int from, int to, int steps) {
        return from + Math.round((to - from) * steps / (float) MIX_STEPS);
    }

    /**
     * Normal and faint text held to their floors against one backdrop, remembered per colour so a
     * draw pass looks answers up instead of searching for them. A small direct-mapped table: a
     * screen uses few distinct colours, and a colour that loses its slot is only searched again.
     * Not thread-safe; one per renderer.
     */
    public static final class Memo {
        private static final int SLOTS = 128;
        /** Marks a filled slot, above the 32 colour bits and the kind bit. */
        private static final long FILLED = 1L << 40;
        private static final long DIM = 1L << 32;

        private final long[] mKeys = new long[SLOTS];
        private final int[] mValues = new int[SLOTS];
        private int mBackdrop;
        private boolean mHasBackdrop;

        /** The opaque colour text stands on; a different one forgets every answer. */
        public void setBackdrop(int backdrop) {
            final int opaque = backdrop | 0xFF000000;
            if (mHasBackdrop && opaque == mBackdrop) return;
            mBackdrop = opaque;
            mHasBackdrop = true;
            Arrays.fill(mKeys, 0L);
        }

        public int backdrop() {
            return mBackdrop;
        }

        /** {@code color} as normal text: held to {@link #TEXT}. */
        public int text(int color) {
            return lookup(color, false);
        }

        /** {@code color} as faint text: {@link #dim} of it, held to {@link #DIM_TEXT}. */
        public int dimText(int color) {
            return lookup(color, true);
        }

        private int lookup(int color, boolean dim) {
            final long key = FILLED | (dim ? DIM : 0L) | (color & 0xFFFFFFFFL);
            int hash = color * 0x9E3779B1;
            if (dim) hash ^= 0x5BD1E995;
            final int slot = (hash ^ (hash >>> 15)) & (SLOTS - 1);
            if (mKeys[slot] == key) return mValues[slot];
            final int answer = dim ? atLeast(ContrastFloor.dim(color), mBackdrop, DIM_TEXT)
                : atLeast(color, mBackdrop, TEXT);
            mKeys[slot] = key;
            mValues[slot] = answer;
            return answer;
        }
    }
}
