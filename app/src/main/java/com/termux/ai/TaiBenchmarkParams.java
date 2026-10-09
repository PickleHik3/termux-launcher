package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONObject;

/**
 * Parsed and defaulted parameters for {@code tai benchmark}, kept independent of Android so the
 * parsing/clamping rules are covered by a plain JVM unit test. Mirrors Google AI Edge Gallery's
 * Benchmark screen defaults (prefill/decode 256 tokens each) so the two are comparable 1:1.
 */
final class TaiBenchmarkParams {
    static final String ACCELERATOR_GPU = "gpu";
    static final String ACCELERATOR_CPU = "cpu";
    static final int DEFAULT_PREFILL_TOKENS = 256;
    static final int DEFAULT_DECODE_TOKENS = 256;
    static final int DEFAULT_RUNS = 3;
    static final int MAX_RUNS = 10;

    @NonNull final String accelerator;
    final int prefillTokens;
    final int decodeTokens;
    final int runs;
    final boolean force;

    private TaiBenchmarkParams(@NonNull String accelerator, int prefillTokens, int decodeTokens, int runs, boolean force) {
        this.accelerator = accelerator;
        this.prefillTokens = prefillTokens;
        this.decodeTokens = decodeTokens;
        this.runs = runs;
        this.force = force;
    }

    /** {@code null} means the request named an accelerator this command does not support. */
    @androidx.annotation.Nullable
    static TaiBenchmarkParams fromRequest(@NonNull JSONObject request) {
        String accelerator = request.optString("accelerator", ACCELERATOR_GPU).trim().toLowerCase(java.util.Locale.ROOT);
        if (accelerator.isEmpty()) accelerator = ACCELERATOR_GPU;
        if (!ACCELERATOR_GPU.equals(accelerator) && !ACCELERATOR_CPU.equals(accelerator)) return null;
        int prefillTokens = positiveOrDefault(request.optInt("prefillTokens", DEFAULT_PREFILL_TOKENS), DEFAULT_PREFILL_TOKENS);
        int decodeTokens = positiveOrDefault(request.optInt("decodeTokens", DEFAULT_DECODE_TOKENS), DEFAULT_DECODE_TOKENS);
        int runs = clamp(positiveOrDefault(request.optInt("runs", DEFAULT_RUNS), DEFAULT_RUNS), 1, MAX_RUNS);
        boolean force = request.optBoolean("force", false);
        return new TaiBenchmarkParams(accelerator, prefillTokens, decodeTokens, runs, force);
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
