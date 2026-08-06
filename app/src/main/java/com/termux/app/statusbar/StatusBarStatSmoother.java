package com.termux.app.statusbar;

/**
 * Turns one raw stat sample stream into a calm reading for a compact status-bar widget.
 *
 * <p>{@link SystemStatsController} samples for two surfaces at once: the mini-btop card, where a
 * live-moving number is the point, and the two-or-three character CPU/RAM readings in the status
 * bar, where it is not. A percentage that repaints with a different digit every few seconds in the
 * corner of the screen reads as flicker, not as information — the eye is pulled to it constantly and
 * nothing it says is worth the interruption. So the card keeps the raw snapshot at the controller's
 * own cadence and the bar goes through here, which does four things in order:
 *
 * <ol>
 *   <li><b>EMA</b> — the displayed value glides towards each sample instead of teleporting to it, so
 *       a single unlucky scheduler window cannot move the reading much.</li>
 *   <li><b>Quantization</b> — the glided value is snapped to a step, because the bar has room for a
 *       coarse reading and no room for a precise one.</li>
 *   <li><b>Hysteresis</b> — the snapped value is only allowed to move once the smoothed value has
 *       left a deadband around what is already on screen, so a metric parked on a step boundary
 *       cannot ping-pong between two readings.</li>
 *   <li><b>A minimum publish interval</b> — even a genuinely swinging metric repaints on a fixed,
 *       calm cadence rather than on every sample.</li>
 * </ol>
 *
 * <p>The last published text is cached, so an unchanged reading costs the caller nothing: {@link
 * #offer} returns false and no view is touched at all — no {@code setText}, no invalidate, no
 * relayout. The percent strings themselves are interned in a small table, so even a reading that
 * does change allocates nothing after warm-up.
 *
 * <p>Free of Android imports, and the clock is a parameter rather than something read in here, so
 * every rule above is unit-testable without sleeping and the host can drive all its widgets from one
 * timestamp.
 */
public final class StatusBarStatSmoother {

    /** What an unavailable reading shows. Blanking it would be indistinguishable from a zero. */
    public static final String UNKNOWN_TEXT = "--";

    /** Sentinel for "no reading", matching {@link SystemStatsController.Stats}' own convention. */
    public static final int UNKNOWN = -1;

    /**
     * Weight given to a fresh CPU sample, i.e. the same 0.68/0.32 blend {@link SystemStatsController}
     * already applies to per-process CPU in the card. Deliberately reused rather than re-tuned: CPU is
     * the noisiest thing on the bar, this fork already decided how hard to damp CPU, and having one
     * constant means the bar and the card settle at the same rate instead of at two rates that look
     * like a bug when both are on screen.
     */
    public static final double CPU_SAMPLE_WEIGHT = .32d;

    /**
     * Memory drifts rather than spikes — it is allocation and reclaim, not a scheduler window — so it
     * needs far less damping than CPU, and over-damping it would hide the one thing people watch
     * memory for: a slow climb. Half weight absorbs the sampling noise and still tracks a real
     * allocation within a couple of samples.
     */
    public static final double MEMORY_SAMPLE_WEIGHT = .5d;

    /**
     * CPU is shown in two-point steps, which is the finest grid that still swallows the ±1 wobble of
     * an idle device.
     *
     * <p>A coarser grid was tried and rejected. Tapping the CPU reading opens the card directly under
     * it, so the two numbers are read together, and a bar sitting a whole five-point step away from
     * the card's whole-percent reading does not look calm — it looks like one of the two is wrong. The
     * EMA and the publish throttle are what make the bar calm; the grid only has to keep it from
     * disagreeing with the card. Two points plus the hysteresis band keeps the steady-state gap under
     * three points. See the disagreement budget asserted in the tests.
     */
    public static final int CPU_STEP_PERCENT = 2;

    /**
     * Memory is shown in whole percent. It moves slowly and roughly monotonically, so a coarse grid
     * here would suppress the real signal rather than noise.
     */
    public static final int MEMORY_STEP_PERCENT = 1;

    /**
     * Smallest gap between two repaints of a bar widget. Chosen against the controller's own
     * cadences: it sits under the 6 s resting interval so the ordinary rhythm is never skipped, and
     * throttles the 1.5 s interval used while the card is open down to the rate the bar runs at
     * anyway — the bar behind an open card has no business repainting faster than the bar ever does on
     * its own.
     */
    public static final long MIN_PUBLISH_INTERVAL_MS = 3000L;

    /**
     * How stale the EMA may be before the next sample is adopted outright. Deliberately longer than
     * three resting samples (3 × 6 s), so a single slow or delayed tick is still treated as an
     * ordinary sample: a gap bigger than this means sampling actually stopped — the activity went
     * away, or the widgets were switched off — and gliding from history that old would display a value
     * that was never true and then visibly climb out of it. Fading in from a stale reading looks
     * broken; showing the truth does not.
     */
    public static final long STALE_HISTORY_MS = 20_000L;

    /**
     * Interned "N%" strings. The bar only ever shows whole percents in 0..100, so after warm-up the
     * publish path allocates nothing whatsoever — the point of the exercise being that a status bar
     * updating itself should not be measurable.
     */
    private static final String[] PERCENT_TEXT = new String[101];

    private final double mSampleWeight;
    private final int mStepPercent;
    private final double mHysteresisPercent;
    private final long mMinPublishIntervalMs;

    /** The glided value. Only meaningful while {@link #mHasHistory}. */
    private double mSmoothed;
    private boolean mHasHistory;
    private boolean mHasPublished;
    private int mPublishedPercent = UNKNOWN;
    private String mText = UNKNOWN_TEXT;
    private long mLastSampleMs;
    private long mLastPublishMs;

    /** The bar's CPU reading: heavily damped, five-point steps. */
    public static StatusBarStatSmoother forCpuPercent() {
        return new StatusBarStatSmoother(CPU_SAMPLE_WEIGHT, CPU_STEP_PERCENT,
            MIN_PUBLISH_INTERVAL_MS);
    }

    /** The bar's memory reading: lightly damped, whole percent. */
    public static StatusBarStatSmoother forMemoryPercent() {
        return new StatusBarStatSmoother(MEMORY_SAMPLE_WEIGHT, MEMORY_STEP_PERCENT,
            MIN_PUBLISH_INTERVAL_MS);
    }

    StatusBarStatSmoother(double sampleWeight, int stepPercent, long minPublishIntervalMs) {
        mSampleWeight = sampleWeight;
        mStepPercent = Math.max(1, stepPercent);
        mHysteresisPercent = hysteresisFor(mStepPercent);
        mMinPublishIntervalMs = Math.max(0L, minPublishIntervalMs);
    }

    /**
     * The deadband around the value on screen, as a percentage.
     *
     * <p>Half a step is what quantization alone gives, and it is not enough: a smoothed value sitting
     * on a boundary crosses it repeatedly and the text flips between two readings. So the band is half
     * a step plus a margin, which is what actually stops the flip — after a move of one step, coming
     * straight back needs {@code margin} more movement than the crossing that caused it.
     *
     * <p>The margin is deliberately a fraction of the step rather than a constant. It has to stay
     * below one step, or a real one-step change could never be published at all: an EMA approaches a
     * new level asymptotically, so a band of a whole step is a band the value never quite leaves. That
     * bug — a reading frozen one step below the truth forever — is the reason this is a named method
     * with an explanation rather than a literal.
     */
    static double hysteresisFor(int stepPercent) {
        return stepPercent * .5d + stepPercent * .3d;
    }

    /**
     * Feed one sample and learn whether the widget needs touching.
     *
     * @param samplePercent the raw reading in 0..100, or {@link #UNKNOWN} when unavailable
     * @param nowMs         a monotonic clock, e.g. {@code SystemClock.uptimeMillis()}
     * @return true when {@link #text()} differs from what was last handed out, and only then does the
     *     caller have any view work to do
     */
    public boolean offer(int samplePercent, long nowMs) {
        if (samplePercent < 0) {
            // Never smooth towards zero on the way to "unavailable": a metric that stopped reporting
            // is not a metric that fell. Drop the history so the next real sample is adopted outright.
            mHasHistory = false;
            mLastSampleMs = nowMs;
            return publish(UNKNOWN, nowMs);
        }
        int sample = Math.max(0, Math.min(100, samplePercent));
        boolean fresh = !mHasHistory
            || nowMs < mLastSampleMs
            || nowMs - mLastSampleMs > STALE_HISTORY_MS;
        mLastSampleMs = nowMs;
        if (fresh) {
            // First sample after a start, a resume, or a gap: show the real value now. A smoother
            // that fades in from nothing reads as a broken widget, not as a calm one.
            mSmoothed = sample;
            mHasHistory = true;
            return publish(quantize(mSmoothed, mStepPercent), nowMs);
        }
        // The sample always folds into the EMA, even when the result will not be published: throwing
        // samples away during the quiet period would make the reading depend on which ticks happened
        // to land on a publish boundary.
        mSmoothed = mSmoothed * (1d - mSampleWeight) + sample * mSampleWeight;
        if (!mHasPublished || mPublishedPercent < 0) {
            return publish(quantize(mSmoothed, mStepPercent), nowMs);
        }
        if (Math.abs(mSmoothed - mPublishedPercent) < mHysteresisPercent) return false;
        if (nowMs - mLastPublishMs < mMinPublishIntervalMs) return false;
        return publish(quantize(mSmoothed, mStepPercent), nowMs);
    }

    /** The cached reading to show, e.g. {@code "42%"} or {@link #UNKNOWN_TEXT}. */
    public String text() {
        return mText;
    }

    /** The published, quantized reading, or {@link #UNKNOWN}. */
    public int publishedPercent() {
        return mPublishedPercent;
    }

    /**
     * Forget everything, so the next sample is published immediately and exactly. For the host to
     * call when sampling stops: on resume the widget has to show what is true now, not glide out of
     * whatever was true when the user left.
     */
    public void reset() {
        mHasHistory = false;
        mHasPublished = false;
        mPublishedPercent = UNKNOWN;
        mText = UNKNOWN_TEXT;
        mSmoothed = 0d;
        mLastSampleMs = 0L;
        mLastPublishMs = 0L;
    }

    /** Nearest multiple of {@code stepPercent}, clamped to 0..100. Half a step rounds up. */
    static int quantize(double percent, int stepPercent) {
        int step = Math.max(1, stepPercent);
        long steps = Math.round(percent / step);
        return (int) Math.max(0L, Math.min(100L, steps * step));
    }

    /** Interned {@code "N%"}, so the common paths allocate nothing. */
    static String percentText(int percent) {
        if (percent < 0 || percent > 100) return UNKNOWN_TEXT;
        String cached = PERCENT_TEXT[percent];
        if (cached == null) {
            cached = percent + "%";
            PERCENT_TEXT[percent] = cached;
        }
        return cached;
    }

    private boolean publish(int percent, long nowMs) {
        boolean changed = !mHasPublished || percent != mPublishedPercent;
        mHasPublished = true;
        mPublishedPercent = percent;
        mLastPublishMs = nowMs;
        if (changed) mText = percentText(percent);
        return changed;
    }
}
