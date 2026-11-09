package com.termux.app.chrome.scene;

/**
 * The pure rules that keep a user's shader from taking the home screen down (spec decision 8).
 *
 * <ul>
 *   <li>{@link HeavyFrames}: a draw whose wall time is over twice the period for
 *   {@link #HEAVY_FRAMES} frames in a row drops one quality step; at Quarter the shader holds
 *   at its still and the detail page says why.</li>
 *   <li>{@link ContextLoss}: a lost context is recreated once; a second loss within
 *   {@link #LOSS_WINDOW_MS} reverts to Photo.</li>
 *   <li>The pending marker written on apply is cleared after {@link #PENDING_CLEAR_MS} of
 *   drawing; one found when the process starts reverts to Photo before a surface exists.</li>
 * </ul>
 */
public final class ShaderSafety {

    public static final int HEAVY_FRAMES = 30;
    public static final long LOSS_WINDOW_MS = 10_000L;
    public static final long PENDING_CLEAR_MS = 3_000L;
    /** The period a heavy frame is judged against when the rate is every vsync. */
    static final long VSYNC_NS = 1_000_000_000L / 90L;

    /** The reason the detail page shows when a shader holds at its still for weight. */
    public static final String REASON_TOO_HEAVY = "too_heavy";

    private ShaderSafety() {}

    /** Render scale steps: Full, Half (default), Quarter. */
    public enum Quality {
        FULL(1f), HALF(0.5f), QUARTER(0.25f);

        public final float scale;

        Quality(float scale) {
            this.scale = scale;
        }

        /** {@code full}, {@code half} or {@code quarter}; anything else is Half. */
        public static Quality parse(String value) {
            if ("full".equals(value)) return FULL;
            if ("quarter".equals(value)) return QUARTER;
            return HALF;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** One step lower, or null at Quarter. */
        public Quality lower() {
            return this == FULL ? HALF : this == HALF ? QUARTER : null;
        }
    }

    /** Counts heavy frames in a row. */
    public static final class HeavyFrames {
        public static final int NONE = 0, STEP_DOWN = 1, HOLD = 2;
        private int mOver;

        /**
         * One drawn frame: its wall time and the period it was drawn at ({@code Long.MAX_VALUE}
         * when it was a one-off draw, which does not count; 0 for every vsync). Returns
         * {@link #STEP_DOWN} or {@link #HOLD} once the run reaches {@link #HEAVY_FRAMES}
         * (and starts counting again), {@link #NONE} otherwise.
         */
        public int onFrame(long wallNs, long periodNs, Quality quality) {
            if (periodNs == Long.MAX_VALUE) return NONE;
            long period = periodNs <= 0L ? VSYNC_NS : periodNs;
            if (wallNs > 2L * period) mOver++;
            else mOver = 0;
            if (mOver < HEAVY_FRAMES) return NONE;
            mOver = 0;
            return quality.lower() == null ? HOLD : STEP_DOWN;
        }

        public void reset() {
            mOver = 0;
        }
    }

    /** Context losses over time. */
    public static final class ContextLoss {
        public static final int RECREATE = 0, REVERT = 1;
        private long mLastMs = Long.MIN_VALUE;

        public int onLoss(long nowMs) {
            boolean recent = mLastMs != Long.MIN_VALUE && nowMs - mLastMs <= LOSS_WINDOW_MS;
            mLastMs = nowMs;
            return recent ? REVERT : RECREATE;
        }
    }

    /** Whether a marker may be cleared: the shader has drawn for {@link #PENDING_CLEAR_MS} since its first frame. */
    public static boolean pendingMayClear(long firstDrawMs, long nowMs) {
        return firstDrawMs != Long.MIN_VALUE && nowMs - firstDrawMs >= PENDING_CLEAR_MS;
    }
}
