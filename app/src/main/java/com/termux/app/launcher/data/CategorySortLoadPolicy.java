package com.termux.app.launcher.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiBenchStats;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiTierPolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * How a category sort loads its model: the smallest window it needs, and the accelerator and
 * speculative decoding the user's own speed test found fastest for this model, or a safe default
 * when there is no test. Pure except for {@link #forContext}, the one place that reads the device.
 *
 * <p>A sort asks one question per app and takes one word back, so what it costs is the time to read
 * the prompt (first token), not decode speed. Speculative decoding speeds up long answers; for one
 * word it only adds load time and memory (pong, 2026-10-05, {@code daytoday-settings-bench}), so it is
 * on only when a speed test measured it winning on the accelerator in use.
 */
public final class CategorySortLoadPolicy {
    /** The smallest window the runtime accepts; the prompt and its 24-token answer need about a third of it. */
    static final int MIN_CONTEXT_WINDOW = 1024;
    static final int MAX_CONTEXT_WINDOW = 4096;
    private static final int WINDOW_STEP = 256;
    /** Characters per token, deliberately low (denser text means more tokens) so the window errs large. */
    private static final int CHARS_PER_TOKEN = 3;
    private static final int ANSWER_AND_SLACK_TOKENS = 24 + 64;

    private CategorySortLoadPolicy() {
    }

    /** One speed-test result for a model, reduced to what the sort decides on. */
    public static final class Entry {
        @NonNull final String modelId;
        @NonNull final String accelerator;
        final boolean speculative;
        /** Median time to first token in ms, {@code NaN} when the test did not measure it. */
        final double ttftMs;
        /** Load time in ms, or -1 when unknown. */
        final long loadMs;
        /** The test completed with correct answers and the entry did not crash. */
        final boolean passed;

        public Entry(@NonNull String modelId, @NonNull String accelerator, boolean speculative,
                     double ttftMs, long loadMs, boolean passed) {
            this.modelId = modelId;
            this.accelerator = accelerator;
            this.speculative = speculative;
            this.ttftMs = ttftMs;
            this.loadMs = loadMs;
            this.passed = passed;
        }
    }

    /** What the sort loads with. */
    public static final class Decision {
        @NonNull public final String accelerator;
        public final boolean speculative;
        public final int contextWindow;
        /** A passing speed-test result for this model decided the accelerator. */
        public final boolean benchmarked;

        Decision(@NonNull String accelerator, boolean speculative, int contextWindow, boolean benchmarked) {
            this.accelerator = accelerator;
            this.speculative = speculative;
            this.contextWindow = contextWindow;
            this.benchmarked = benchmarked;
        }
    }

    /**
     * The window a sort needs: its longest prompt plus the answer, rounded up to a step, never below
     * what the runtime accepts and capped at the usual default window.
     */
    public static int contextWindowFor(int longestPromptChars) {
        int tokens = (Math.max(0, longestPromptChars) + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN + ANSWER_AND_SLACK_TOKENS;
        int rounded = (tokens + WINDOW_STEP - 1) / WINDOW_STEP * WINDOW_STEP;
        return Math.min(MAX_CONTEXT_WINDOW, Math.max(MIN_CONTEXT_WINDOW, rounded));
    }

    /**
     * @param tierAccelerator what the tier policy would pick; breaks an exact tie between two results
     * @param pickedAccelerator the accelerator the user chose for this function, or null; it wins
     * @param gpuOffered the device offers a GPU at all
     * @param gpuTrusted {@code gpuOffered} and the GPU's canary has not failed; a speed-test result alone
     *     never sends a sort to a GPU that answers wrongly
     */
    @NonNull
    public static Decision decide(@NonNull String modelId, @Nullable String tierAccelerator,
                                  @Nullable String pickedAccelerator, boolean gpuOffered, boolean gpuTrusted,
                                  @NonNull List<Entry> entries, int longestPromptChars) {
        int window = contextWindowFor(longestPromptChars);
        List<Entry> mine = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.passed && entry.modelId.equals(modelId)) mine.add(entry);
        }

        String accelerator;
        if (pickedAccelerator != null && !pickedAccelerator.isEmpty()) {
            accelerator = TaiTierPolicy.ACCEL_GPU.equals(pickedAccelerator) && !gpuOffered
                ? TaiTierPolicy.ACCEL_CPU : pickedAccelerator;
        } else {
            Entry best = null;
            for (Entry entry : mine) {
                if (entry.speculative) continue; // a speculative result only toggles, it never picks the accelerator
                if (TaiTierPolicy.ACCEL_GPU.equals(entry.accelerator) && !gpuTrusted) continue;
                if (!TaiTierPolicy.ACCEL_GPU.equals(entry.accelerator) && !TaiTierPolicy.ACCEL_CPU.equals(entry.accelerator)) continue;
                if (best == null || cheaper(entry, best, tierAccelerator)) best = entry;
            }
            if (best == null) return new Decision(TaiTierPolicy.ACCEL_CPU, false, window, false);
            accelerator = best.accelerator;
        }

        Entry plain = null;
        Entry speculative = null;
        for (Entry entry : mine) {
            if (!accelerator.equals(entry.accelerator)) continue;
            if (entry.speculative) {
                if (speculative == null || cheaper(entry, speculative, tierAccelerator)) speculative = entry;
            } else if (plain == null || cheaper(entry, plain, tierAccelerator)) {
                plain = entry;
            }
        }
        if (plain == null) return new Decision(accelerator, false, window, false);
        boolean useSpeculative = speculative != null && strictlyFaster(speculative, plain);
        return new Decision(accelerator, useSpeculative, window, true);
    }

    /** First token first, load time as the tie-break, the tier's own accelerator as the last. */
    private static boolean cheaper(@NonNull Entry a, @NonNull Entry b, @Nullable String tierAccelerator) {
        int byTtft = Double.compare(ttftKey(a), ttftKey(b));
        if (byTtft != 0) return byTtft < 0;
        int byLoad = Long.compare(loadKey(a), loadKey(b));
        if (byLoad != 0) return byLoad < 0;
        return tierAccelerator != null && tierAccelerator.equals(a.accelerator) && !tierAccelerator.equals(b.accelerator);
    }

    /** Speculative decoding must win on first token; equal is not a win because it loads slower and holds more memory. */
    private static boolean strictlyFaster(@NonNull Entry speculative, @NonNull Entry plain) {
        return !Double.isNaN(speculative.ttftMs) && ttftKey(speculative) < ttftKey(plain);
    }

    private static double ttftKey(@NonNull Entry entry) {
        return Double.isNaN(entry.ttftMs) ? Double.MAX_VALUE : entry.ttftMs;
    }

    private static long loadKey(@NonNull Entry entry) {
        return entry.loadMs < 0 ? Long.MAX_VALUE : entry.loadMs;
    }

    /**
     * Reads the entries from {@code TaiManager.benchmarks()}' JSON: the ranked leaderboard rows (the
     * latest result of each entry). A row passes when its answers were right and it neither crashed
     * nor was graded broken.
     */
    @NonNull
    public static List<Entry> entriesFrom(@Nullable JSONObject benchmarks) {
        List<Entry> entries = new ArrayList<>();
        JSONObject leaderboard = benchmarks == null ? null : benchmarks.optJSONObject("leaderboard");
        JSONArray ranked = leaderboard == null ? null : leaderboard.optJSONArray("ranked");
        if (ranked == null) return entries;
        for (int i = 0; i < ranked.length(); i++) {
            JSONObject row = ranked.optJSONObject(i);
            if (row == null) continue;
            String verdict = row.optString("verdict", "");
            boolean passed = row.optBoolean("checkPassed", false)
                && !TaiBenchStats.VERDICT_BROKEN.equals(verdict) && !TaiBenchStats.VERDICT_CRASHED.equals(verdict);
            entries.add(new Entry(row.optString("modelId", ""), row.optString("accelerator", "").toLowerCase(java.util.Locale.ROOT),
                row.optBoolean("speculative", false), row.optDouble("ttftMs", Double.NaN),
                row.optLong("loadMs", -1L), passed));
        }
        return entries;
    }

    /**
     * The decision for a sort on this device: the user's speed-test results, the GPU's standing, and
     * the accelerator the user picked for the function (only an explicit pick of this model counts).
     * Reads a small file; call it off the main thread when convenient.
     */
    @NonNull
    public static Decision forContext(@NonNull Context context, @NonNull String modelId,
                                      @Nullable String tierAccelerator, int longestPromptChars) {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(context);
        boolean gpuOffered = device.supportsAccelerator(TaiTierPolicy.ACCEL_GPU);
        TaiPlatformCaps.GpuPath path = TaiPlatformCaps.cached(context).gpuPath;
        boolean gpuTrusted = gpuOffered && path != TaiPlatformCaps.GpuPath.NO && path != TaiPlatformCaps.GpuPath.CPU_FIRST;
        TaiFunctionModels models = TaiFunctionModels.forContext(context);
        String pick = models.pick(TaiFunction.APP_CATEGORIES);
        String picked = pick.equals(modelId) ? models.acceleratorPick(TaiFunction.APP_CATEGORIES) : "";
        List<Entry> entries;
        try {
            entries = entriesFrom(TaiManager.getInstance(context).benchmarks());
        } catch (Exception e) {
            entries = new ArrayList<>();
        }
        return decide(modelId, tierAccelerator, picked, gpuOffered, gpuTrusted, entries, longestPromptChars);
    }
}
