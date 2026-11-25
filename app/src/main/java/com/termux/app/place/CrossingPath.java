package com.termux.app.place;

import androidx.annotation.NonNull;

/**
 * Stay, Glide or Ride: the one crossing rule
 * ({@code project-docs/active/crossing-transitions-stay-glide-ride-2026-11-25.md}, rule C). When a
 * crossing begins, each element's rest at each end comes from the one per-rest answer
 * ({@link RestCards#rows()} for the bottom rows, the card table for the keyboard), and this pure
 * function gives each element one verb:
 *
 * <ul>
 *   <li>{@link Verb#STAY}: the same rect at both ends. Nothing is written.</li>
 *   <li>{@link Verb#GLIDE}: the same size on the same edge (and the same side of the keyboard),
 *       the order among the rows both ends show kept. The view is laid out once and each rest is
 *       an offset from that layout; the frame is the blend of the two offsets on the material
 *       curve ({@link #glideAt}), along the edge's normal only.</li>
 *   <li>{@link Verb#RIDE}: anything else (size, edge, order, one end only). Each end's instance
 *       slides with its page; nothing is re-sized (ADR 0032 section 1).</li>
 * </ul>
 *
 * <p>Content that differs at the two ends of a STAY or GLIDE row crossfades ({@link #crossfades}).
 * The verb depends on the two rests alone, so it is symmetric ({@code verb(a, b) == verb(b, a)}),
 * a turned-back swipe runs p backward along the same frames, and p = 0 and p = 1 are the rests.
 * Nothing here allocates; the scene ({@link StackScene#at}) reads it on every frame.
 */
public final class CrossingPath {

    private CrossingPath() {}

    /** What an element does across a crossing. */
    public enum Verb {
        /** Neither end shows it. */
        NONE,
        STAY,
        GLIDE,
        RIDE
    }

    private static final Verb[] VERBS = Verb.values();

    /** The verb with ordinal {@code ordinal}: the scene's frame keeps verbs as ints. */
    @NonNull
    public static Verb of(int ordinal) {
        return VERBS[ordinal];
    }

    /** The bottom rows by index, in {@link StackLanes#ROWS}' order. */
    public static final int APPS = 0;
    public static final int AZ = 1;
    public static final int EXTRA_KEYS = 2;
    public static final int ROW_COUNT = 3;

    /** {@link Rows#where}: the row is not in the bottom bands at this rest. */
    public static final int NOWHERE = 0;
    /** {@link Rows#where}: over the keyboard, in the rows' band. */
    public static final int OVER = 1;
    /** {@link Rows#where}: under the keyboard, in the bands under it. */
    public static final int UNDER = 2;

    /**
     * One rest's bottom rows, the per-rest answer's part the rule reads: for each row which band it
     * stands in, its top measured from that band's bottom (so negative, and the band's own
     * motion is the stack's transforms'), its height, and a key of what it shows (equal keys draw
     * the same content). Mutable and reused; filled at rest only.
     */
    public static final class Rows {
        public final int[] where = new int[ROW_COUNT];
        public final float[] topInBandPx = new float[ROW_COUNT];
        public final float[] heightPx = new float[ROW_COUNT];
        public final int[] contentKey = new int[ROW_COUNT];

        public Rows() {
            clear();
        }

        public void clear() {
            for (int i = 0; i < ROW_COUNT; i++) {
                where[i] = NOWHERE;
                topInBandPx[i] = 0f;
                heightPx[i] = 0f;
                contentKey[i] = 0;
            }
        }

        /** Row {@code i} stands in {@code band} ({@link #OVER} or {@link #UNDER}). */
        public void set(int i, int band, float topInBandPx, float heightPx) {
            where[i] = band;
            this.topInBandPx[i] = topInBandPx;
            this.heightPx[i] = heightPx;
        }

        public void set(@NonNull Rows o) {
            for (int i = 0; i < ROW_COUNT; i++) {
                where[i] = o.where[i];
                topInBandPx[i] = o.topInBandPx[i];
                heightPx[i] = o.heightPx[i];
                contentKey[i] = o.contentKey[i];
            }
        }

        public boolean shown(int i) {
            return where[i] != NOWHERE;
        }
    }

    /** The index of {@code element} among the rows, -1 for one that is not a bottom row. */
    public static int rowOf(@NonNull Element element) {
        switch (element) {
            case APPS: return APPS;
            case AZ: return AZ;
            case EXTRA_KEYS: return EXTRA_KEYS;
            default: return -1;
        }
    }

    /** Row {@code i}'s verb from rest {@code a} to rest {@code b}; the same from b to a. */
    @NonNull
    public static Verb verb(@NonNull Rows a, @NonNull Rows b, int i) {
        boolean inA = a.shown(i);
        boolean inB = b.shown(i);
        if (!inA && !inB) return Verb.NONE;
        if (inA != inB) return Verb.RIDE;
        if (a.where[i] != b.where[i] || !same(a.heightPx[i], b.heightPx[i])) return Verb.RIDE;
        if (!orderKept(a, b, i)) return Verb.RIDE;
        return same(a.topInBandPx[i], b.topInBandPx[i]) ? Verb.STAY : Verb.GLIDE;
    }

    /**
     * Whether row {@code i} keeps its order among the rows both rests stand in its band: each
     * other row both show there is on the same side of it (above or below) at both rests. A row
     * whose order flips rides (the developer's decision Q2): two rows never glide through each other.
     */
    public static boolean orderKept(@NonNull Rows a, @NonNull Rows b, int i) {
        for (int j = 0; j < ROW_COUNT; j++) {
            if (j == i || a.where[j] != a.where[i] || b.where[j] != b.where[i]) continue;
            boolean aboveInA = a.topInBandPx[j] < a.topInBandPx[i];
            boolean aboveInB = b.topInBandPx[j] < b.topInBandPx[i];
            if (aboveInA != aboveInB) return false;
        }
        return true;
    }

    /** Whether row {@code i} shows two contents across the crossing: STAY or GLIDE with different keys. */
    public static boolean crossfades(@NonNull Rows a, @NonNull Rows b, int i) {
        Verb verb = verb(a, b, i);
        return (verb == Verb.STAY || verb == Verb.GLIDE) && a.contentKey[i] != b.contentKey[i];
    }

    /**
     * Row {@code i}'s translation along its edge's normal at progress {@code p} while it is laid out
     * at rest {@code from}: 0 at p = 0, the arriving rest's offset from that layout at p = 1, the
     * material curve between. 0 for any verb but GLIDE.
     */
    public static float glideTyPx(@NonNull Rows from, @NonNull Rows to, int i, float p) {
        if (verb(from, to, i) != Verb.GLIDE) return 0f;
        return glideAt(0f, to.topInBandPx[i] - from.topInBandPx[i], p);
    }

    /** A GLIDE's offset at progress {@code p}: {@code a} at 0, {@code b} at 1, on the material curve. */
    public static float glideAt(float a, float b, float p) {
        return glide(a, b, StackScene.materialShare(p));
    }

    /**
     * A GLIDE's offset at blend {@code w} (already on the material curve): {@code a} at 0, {@code b}
     * at 1. Written {@code a * (1 - w) + b * w} so the shared row's ease it replaced
     * ({@link SideCards#laneTyPx}) keeps its exact numbers.
     */
    public static float glide(float a, float b, float w) {
        float s = w < 0f ? 0f : w > 1f ? 1f : w;
        return a * (1f - s) + b * s;
    }

    // ---- The keyboard ----------------------------------------------------------------------

    /**
     * The keyboard's verb (decisions Q1 and Q6): up at both ends it is one keyboard that stays
     * (#307's shared column, the keys do not move); up at one end only it rides with that end's
     * page, sideways, at its rest height; down at both, nothing.
     */
    @NonNull
    public static Verb keyboard(boolean fromUp, boolean toUp) {
        if (fromUp && toUp) return Verb.STAY;
        return fromUp || toUp ? Verb.RIDE : Verb.NONE;
    }

    /**
     * The way the leaving page slides out ({@link KeyboardColumn#LEAVES_LEFT},
     * {@link KeyboardColumn#LEAVES_RIGHT}): the wall's own ({@code wallDirection}) when known,
     * else by side, Home standing to the left of the Terminal ({@link StackScene#sideSlide}).
     * Unknown for two ends of one side without the wall's.
     */
    public static int leavesDirection(int wallDirection, boolean fromHome, boolean toHome) {
        if (wallDirection != KeyboardColumn.LEAVES_UNKNOWN) return Integer.signum(wallDirection);
        if (fromHome == toHome) return KeyboardColumn.LEAVES_UNKNOWN;
        return fromHome ? KeyboardColumn.LEAVES_LEFT : KeyboardColumn.LEAVES_RIGHT;
    }

    /**
     * How far across the keyboard is drawn at progress {@code p} on a wall {@code widthPx} wide: 0
     * unless it rides. Up at the leaving end it slides out with the leaving page
     * ({@link KeyboardColumn#leavingTxPx}); up at the arriving end it slides in with the arriving
     * page from the other side. 0 at the end that has it up, a whole wall at the other.
     */
    public static float keyboardTxPx(boolean fromUp, boolean toUp, int direction, float p,
                                     int widthPx) {
        if (keyboard(fromUp, toUp) != Verb.RIDE || direction == KeyboardColumn.LEAVES_UNKNOWN)
            return 0f;
        float q = p < 0f ? 0f : p > 1f ? 1f : p;
        int w = Math.max(0, widthPx);
        if (fromUp) return KeyboardColumn.leavingTxPx(direction, q, w);
        return -Integer.signum(direction) * (1f - q) * w;
    }

    private static boolean same(float a, float b) {
        return Math.abs(a - b) < 0.5f;
    }
}
