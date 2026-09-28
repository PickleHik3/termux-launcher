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
 * and the verdict thresholds. Pure, so the formulas are pinned by JVM tests and both backends are
 * measured by the same code.
 */
final class TaiBenchStats {
    static final String VERDICT_SMOOTH = "smooth";
    static final String VERDICT_USABLE = "usable";
    static final String VERDICT_SLOW = "slow";
    static final String VERDICT_BROKEN = "broken";
    /** Decode tok/s at or above which a reply reads as it is written. */
    static final double SMOOTH_TPS = 15.0;
    /** Below this a reply is a wait, not a conversation. */
    static final double USABLE_TPS = 7.0;

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

    /** Smooth ≥ 15 tok/s, Usable 7–15, Slow below 7; Broken whenever the check failed. */
    @NonNull
    static String verdict(double writingTpsMedian, boolean checkPassed) {
        if (!checkPassed) return VERDICT_BROKEN;
        if (writingTpsMedian >= SMOOTH_TPS) return VERDICT_SMOOTH;
        if (writingTpsMedian >= USABLE_TPS) return VERDICT_USABLE;
        return VERDICT_SLOW;
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
