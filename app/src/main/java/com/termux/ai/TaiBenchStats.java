package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The arithmetic behind a benchmark record: the token-rate formulas, median/min/max over runs,
 * the peak of the memory samples, and the verdict thresholds. Pure, so the formulas are pinned by
 * JVM tests and both backends are
 * measured by the same code.
 */
public final class TaiBenchStats {
    public static final String VERDICT_SMOOTH = "smooth";
    public static final String VERDICT_USABLE = "usable";
    public static final String VERDICT_SLOW = "slow";
    public static final String VERDICT_BROKEN = "broken";

    // The verdict's thresholds. Each figure is what a person notices in ordinary use, so they are
    // constants to argue with here, not numbers scattered through the harness and the screens.

    /** Smooth: a reply is written at about reading speed (a fast reader takes 5-7 words a second). */
    public static final double SMOOTH_DECODE_TPS = 12.0;
    /** Smooth: the reply begins before the person wonders whether the phone heard them. */
    public static final long SMOOTH_TTFT_MS = 1_500L;
    /** Smooth: a pasted page is read in about the time it takes to look at it. */
    public static final long SMOOTH_READ_MS = 8_000L;
    /** Usable: below this a reply is a wait, not a conversation (slow reading speed). */
    public static final double USABLE_DECODE_TPS = 6.0;
    /** Usable: still inside the pause people put up with before asking again. */
    public static final long USABLE_TTFT_MS = 3_000L;
    /** Usable: a pasted page is a short wait, not a break. */
    public static final long USABLE_READ_MS = 20_000L;

    private TaiBenchStats() {
    }

    /**
     * Prompt tokens over the wait for the first token. {@code 0} when nothing was measured: no
     * prompt tokens, or a first token that did not arrive after the submit.
     */
    static double prefillTps(int promptTokens, long ttftMs) {
        if (promptTokens <= 0 || ttftMs <= 0L) return 0.0;
        return promptTokens * 1000.0 / ttftMs;
    }

    /**
     * Tokens after the first, over the time from the first token to the last. The first token's
     * own wait is the prefill and belongs to TTFT, which is why it is not counted here. One token
     * has no interval and yields {@code 0}, as does a last token stamped no later than the first.
     */
    static double decodeTps(int generatedTokens, long firstTokenMs, long lastTokenMs) {
        if (generatedTokens <= 1) return 0.0;
        long intervalMs = lastTokenMs - firstTokenMs;
        if (intervalMs <= 0L) return 0.0;
        return (generatedTokens - 1) * 1000.0 / intervalMs;
    }

    /**
     * One word per model and processor. Broken when the sanity checks failed. Otherwise Smooth
     * when decode is at least {@link #SMOOTH_DECODE_TPS} and the chat's first token comes within
     * {@link #SMOOTH_TTFT_MS} and the long page is read within {@link #SMOOTH_READ_MS}; Usable
     * when all three clear the Usable line; Slow otherwise. Every bound is inclusive, and a
     * figure that was not measured ({@code <= 0}) clears no line.
     */
    @NonNull
    public static String verdict(double decodeTps, long chatTtftMs, long readMs, boolean checkPassed) {
        if (!checkPassed) return VERDICT_BROKEN;
        if (decodeTps >= SMOOTH_DECODE_TPS && within(chatTtftMs, SMOOTH_TTFT_MS) && within(readMs, SMOOTH_READ_MS)) {
            return VERDICT_SMOOTH;
        }
        if (decodeTps >= USABLE_DECODE_TPS && within(chatTtftMs, USABLE_TTFT_MS) && within(readMs, USABLE_READ_MS)) {
            return VERDICT_USABLE;
        }
        return VERDICT_SLOW;
    }

    private static boolean within(long measuredMs, long limitMs) {
        return measuredMs > 0L && measuredMs <= limitMs;
    }

    /** Smooth 3, Usable 2, Slow 1, anything else (Broken, none) 0: the leaderboard's first sort key, higher first. */
    public static int verdictOrder(@Nullable String verdict) {
        if (VERDICT_SMOOTH.equals(verdict)) return 3;
        if (VERDICT_USABLE.equals(verdict)) return 2;
        if (VERDICT_SLOW.equals(verdict)) return 1;
        return 0;
    }

    /** The largest of the samples added; the memory sampler feeds it while the long input is read. */
    static final class Peak {
        private long max = -1L;

        /** A sample at or below zero is a failed read and is ignored. */
        synchronized void add(long value) {
            if (value > max) max = value;
        }

        /** The largest sample, or {@code -1} when there was none. */
        synchronized long value() {
            return max;
        }
    }

    /** Median, min and max over the runs of one phase; the median is the ranked figure. */
    static final class Series {
        private final List<Double> values = new ArrayList<>();

        void add(double value) {
            values.add(value);
        }

        int count() {
            return values.size();
        }

        boolean isEmpty() {
            return values.isEmpty();
        }

        /** The middle value, or the mean of the two middle values for an even count; {@code 0} when empty. */
        double median() {
            if (values.isEmpty()) return 0.0;
            List<Double> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            int middle = sorted.size() / 2;
            if (sorted.size() % 2 == 1) return sorted.get(middle);
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }

        double min() {
            if (values.isEmpty()) return 0.0;
            return Collections.min(values);
        }

        double max() {
            if (values.isEmpty()) return 0.0;
            return Collections.max(values);
        }

        /** {@code {med, min, max, runs}}, or {@code null} when nothing was measured. */
        @Nullable
        JSONObject toJson() throws JSONException {
            if (values.isEmpty()) return null;
            return new JSONObject()
                .put("med", median())
                .put("min", min())
                .put("max", max())
                .put("runs", values.size());
        }
    }
}
