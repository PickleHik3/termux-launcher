package com.termux.app.chrome.scene;

/**
 * The scene's reactions as a fixed set of slots with bounded decay. Nothing allocates per event:
 * a new reaction overwrites the oldest (or an expired) slot, and {@link #fill} writes the live
 * amplitudes straight into the shader's uniform arrays. Pure Java, main thread only.
 *
 * <ul>
 *   <li>hill: touch-down raises a hill under the finger ({@link #HILL_RISE_MS}) that relaxes to
 *   nothing over {@link #HILL_MS};</li>
 *   <li>spark: a committed key lights the nearest contour from the cursor, travelling outward
 *   for {@link #SPARK_MS};</li>
 *   <li>pulse: the bell sends a ring outward from a pane's rect for {@link #PULSE_MS};</li>
 *   <li>focus: another window took focus (read by the Filament scene only): one slot, a cathode
 *   cycle of {@link FilamentLayout#CYCLE_MS}, not restarted by a change within
 *   {@link FilamentLayout#CYCLE_MIN_GAP_MS} of the last;</li>
 *   <li>basin: an open pane presses a shallow basin under its rect (steady, set by the layer).</li>
 * </ul>
 */
public final class SceneEvents {

    public static final int MAX_HILLS = 3;
    public static final int MAX_SPARKS = 4;
    public static final int MAX_PULSES = 2;
    public static final int MAX_BASINS = 4;

    public static final long HILL_RISE_MS = 90L;
    public static final long HILL_MS = 1000L;
    public static final long SPARK_MS = 800L;
    public static final long PULSE_MS = 1400L;

    /** Hill peak, in height units (the scene's heights span about 0..1; levels are 1/12 apart). */
    static final float HILL_PEAK = 0.55f;
    /** Hill footprint radius in dp. */
    static final float HILL_RADIUS_DP = 150f;
    /** How far a spark travels, dp per second. */
    static final float SPARK_SPEED_DP = 420f;
    static final float PULSE_SPEED_DP = 520f;

    private static final long NONE = Long.MIN_VALUE;

    private final long[] mHillStart = new long[MAX_HILLS];
    private final float[] mHillXY = new float[MAX_HILLS * 2];
    private final long[] mSparkStart = new long[MAX_SPARKS];
    private final float[] mSparkXY = new float[MAX_SPARKS * 2];
    private final long[] mPulseStart = new long[MAX_PULSES];
    /** Per pulse: centre x, centre y, half width, half height, px. */
    private final float[] mPulseRect = new float[MAX_PULSES * 4];
    private long mFocusStart = NONE;
    private int mBasinCount;
    private final float[] mBasins = new float[MAX_BASINS * 4];

    public SceneEvents() {
        reset();
    }

    public void reset() {
        for (int i = 0; i < MAX_HILLS; i++) mHillStart[i] = NONE;
        for (int i = 0; i < MAX_SPARKS; i++) mSparkStart[i] = NONE;
        for (int i = 0; i < MAX_PULSES; i++) mPulseStart[i] = NONE;
        mFocusStart = NONE;
        mBasinCount = 0;
    }

    /** Another window took focus at {@code nowMs}. */
    public void addFocus(long nowMs) {
        if (live(mFocusStart, nowMs, FilamentLayout.CYCLE_MIN_GAP_MS)) return;
        mFocusStart = nowMs;
    }

    /** Ms since the focus change while its cycle runs, else -1. */
    public long focusAgeMs(long nowMs) {
        return live(mFocusStart, nowMs, FilamentLayout.CYCLE_MS) ? nowMs - mFocusStart : -1L;
    }

    /** Drops the focus cycle: with motion off a change shows its result at once. */
    public void clearFocus() {
        mFocusStart = NONE;
    }

    public void addHill(float x, float y, long nowMs) {
        int i = slot(mHillStart, nowMs, HILL_MS);
        mHillStart[i] = nowMs;
        mHillXY[i * 2] = x;
        mHillXY[i * 2 + 1] = y;
    }

    public void addSpark(float x, float y, long nowMs) {
        int i = slot(mSparkStart, nowMs, SPARK_MS);
        mSparkStart[i] = nowMs;
        mSparkXY[i * 2] = x;
        mSparkXY[i * 2 + 1] = y;
    }

    /** A bell from the pane whose rect is centred at (cx, cy) with half extents (hw, hh). */
    public void addPulse(float cx, float cy, float hw, float hh, long nowMs) {
        int i = slot(mPulseStart, nowMs, PULSE_MS);
        mPulseStart[i] = nowMs;
        mPulseRect[i * 4] = cx;
        mPulseRect[i * 4 + 1] = cy;
        mPulseRect[i * 4 + 2] = hw;
        mPulseRect[i * 4 + 3] = hh;
    }

    /**
     * Replaces the basin set: {@code count} rects of (cx, cy, hw, hh) in {@code rects}. True when
     * the set differs from the one held (a pane moved, opened or closed).
     */
    public boolean setBasins(float[] rects, int count) {
        int n = Math.max(0, Math.min(count, MAX_BASINS));
        boolean changed = n != mBasinCount;
        for (int i = 0; i < n * 4 && !changed; i++) {
            if (Math.abs(mBasins[i] - rects[i]) > 0.5f) changed = true;
        }
        if (changed) System.arraycopy(rects, 0, mBasins, 0, n * 4);
        mBasinCount = n;
        return changed;
    }

    public int basinCount() {
        return mBasinCount;
    }

    /** True while any hill, spark, pulse or focus cycle is still animating. */
    public boolean active(long nowMs) {
        if (live(mFocusStart, nowMs, FilamentLayout.CYCLE_MS)) return true;
        for (int i = 0; i < MAX_HILLS; i++) if (live(mHillStart[i], nowMs, HILL_MS)) return true;
        for (int i = 0; i < MAX_SPARKS; i++) if (live(mSparkStart[i], nowMs, SPARK_MS)) return true;
        for (int i = 0; i < MAX_PULSES; i++) if (live(mPulseStart[i], nowMs, PULSE_MS)) return true;
        return false;
    }

    /** A hill's amplitude 0..1 at {@code ageMs}: a quick ease up, then a squared fall to zero. */
    public static float hillAmp(long ageMs) {
        if (ageMs < 0L || ageMs >= HILL_MS) return 0f;
        if (ageMs < HILL_RISE_MS) {
            float t = ageMs / (float) HILL_RISE_MS;
            return t * t * (3f - 2f * t);
        }
        float f = 1f - (ageMs - HILL_RISE_MS) / (float) (HILL_MS - HILL_RISE_MS);
        return f * f;
    }

    /** A spark's or pulse's strength 0..1 at {@code ageMs} of {@code lifeMs}: linear fade out. */
    public static float fade(long ageMs, long lifeMs) {
        if (ageMs < 0L || ageMs >= lifeMs) return 0f;
        return 1f - ageMs / (float) lifeMs;
    }

    /**
     * Writes the shader's reaction uniforms and returns the packed counts via the arrays:
     * {@code hills} (x, y, amp, radiusPx) x3, {@code sparks} (x, y, travelPx, strength) x4,
     * {@code pulseRect} (cx, cy, hw, hh) x2 and {@code pulseT} (travelPx, strength) x2,
     * {@code basins} (cx, cy, hw, hh) x4 with hw 0 for an unused slot. Expired slots are written
     * as zero strength so the shader skips them.
     */
    public void fill(long nowMs, float density, float[] hills, float[] sparks,
                     float[] pulseRect, float[] pulseT, float[] basins) {
        for (int i = 0; i < MAX_HILLS; i++) {
            float amp = live(mHillStart[i], nowMs, HILL_MS)
                ? HILL_PEAK * hillAmp(nowMs - mHillStart[i]) : 0f;
            hills[i * 4] = mHillXY[i * 2];
            hills[i * 4 + 1] = mHillXY[i * 2 + 1];
            hills[i * 4 + 2] = amp;
            hills[i * 4 + 3] = HILL_RADIUS_DP * density;
        }
        for (int i = 0; i < MAX_SPARKS; i++) {
            long age = nowMs - mSparkStart[i];
            boolean on = live(mSparkStart[i], nowMs, SPARK_MS);
            sparks[i * 4] = mSparkXY[i * 2];
            sparks[i * 4 + 1] = mSparkXY[i * 2 + 1];
            sparks[i * 4 + 2] = on ? SPARK_SPEED_DP * density * age / 1000f : 0f;
            sparks[i * 4 + 3] = on ? fade(age, SPARK_MS) : 0f;
        }
        for (int i = 0; i < MAX_PULSES; i++) {
            long age = nowMs - mPulseStart[i];
            boolean on = live(mPulseStart[i], nowMs, PULSE_MS);
            System.arraycopy(mPulseRect, i * 4, pulseRect, i * 4, 4);
            pulseT[i * 2] = on ? PULSE_SPEED_DP * density * age / 1000f : 0f;
            pulseT[i * 2 + 1] = on ? fade(age, PULSE_MS) : 0f;
        }
        for (int i = 0; i < MAX_BASINS; i++) {
            if (i < mBasinCount) {
                System.arraycopy(mBasins, i * 4, basins, i * 4, 4);
            } else {
                basins[i * 4] = 0f;
                basins[i * 4 + 1] = 0f;
                basins[i * 4 + 2] = 0f;
                basins[i * 4 + 3] = 0f;
            }
        }
    }

    private static boolean live(long start, long nowMs, long lifeMs) {
        return start != NONE && nowMs - start >= 0L && nowMs - start < lifeMs;
    }

    /** An expired slot if one exists, else the oldest. */
    private static int slot(long[] starts, long nowMs, long lifeMs) {
        int oldest = 0;
        for (int i = 0; i < starts.length; i++) {
            if (!live(starts[i], nowMs, lifeMs)) return i;
            if (starts[i] < starts[oldest]) oldest = i;
        }
        return oldest;
    }
}
