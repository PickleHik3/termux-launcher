package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What this phone has measured about its models, as the {@link TaiFeaturePlan} reads it: read-only,
 * one typed reader in front of the bench results, the runtime history's failure records and the GPU
 * verdict. {@link TaiEvidenceFiles} reads the real files; {@link InMemory} is the tests' table.
 *
 * <p>Every result is matched on the backend as well as the model id: an MNN and a LiteRT-LM file
 * of the same model are different files on different runtimes.
 *
 * <p>Step 2 of the feature load plan (the feature check) plugs its results in at
 * {@link #featureChecks}: until it ships every adapter answers an empty list there, and launcher
 * features run on their defaults (spec decision 11).
 */
public interface TaiEvidence {

    /** One leaderboard row of the generic chat bench, reduced to what the plan decides on. */
    final class ChatResult {
        @NonNull public final String modelId;
        @NonNull public final String backend;
        /** {@code gpu} or {@code cpu}. */
        @NonNull public final String accelerator;
        public final boolean speculative;
        /** Median decode speed, tokens per second; {@code 0} when not measured. */
        public final double decodeTps;
        /** The answers were right, and the entry neither crashed nor was graded broken. */
        public final boolean passed;

        public ChatResult(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator,
                          boolean speculative, double decodeTps, boolean passed) {
            this.modelId = modelId;
            this.backend = backend;
            this.accelerator = accelerator.toLowerCase(Locale.ROOT);
            this.speculative = speculative;
            this.decodeTps = decodeTps;
            this.passed = passed;
        }
    }

    /**
     * One feature check of one setup (step 2): the feature's own workload, so it may change that
     * feature's plan. {@link #speed} is the check's figure with larger meaning faster, whatever unit the
     * feature measures in; only results of one feature are ever compared.
     */
    final class FeatureResult {
        @NonNull public final TaiFunction feature;
        @NonNull public final String modelId;
        @NonNull public final String backend;
        @NonNull public final String accelerator;
        public final boolean speculative;
        public final double speed;
        public final boolean passed;
        /** Speculative decoding was asked for and the runtime said it ran; {@code null} when not asked or not said. */
        @Nullable public final Boolean speculativeRan;

        public FeatureResult(@NonNull TaiFunction feature, @NonNull String modelId, @NonNull String backend,
                             @NonNull String accelerator, boolean speculative, double speed, boolean passed,
                             @Nullable Boolean speculativeRan) {
            this.feature = feature;
            this.modelId = modelId;
            this.backend = backend;
            this.accelerator = accelerator.toLowerCase(Locale.ROOT);
            this.speculative = speculative;
            this.speed = speed;
            this.passed = passed;
            this.speculativeRan = speculativeRan;
        }
    }

    /**
     * Whether {@code modelId} on {@code backend} has an unexpired load failure or crash on
     * {@code accelerator} ({@code gpu} or {@code cpu}). These always count, for every feature.
     */
    boolean failed(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator);

    /** The GPU verdict: {@code UNKNOWN} until the self-test or a check settles it. */
    @NonNull
    TaiGpuVerdict.State gpuVerdict();

    /** The generic chat bench's results for this model file; the plan lets them steer only chat features. */
    @NonNull
    List<ChatResult> chatBench(@NonNull String modelId, @NonNull String backend);

    /** The feature check's results for this feature and model file; empty until step 2 ships. */
    @NonNull
    List<FeatureResult> featureChecks(@NonNull TaiFunction feature, @NonNull String modelId, @NonNull String backend);

    /** No measurements at all: every plan runs on its defaults. */
    TaiEvidence NONE = new InMemory();

    /**
     * The chat bench rows of {@code TaiManager.benchmarks()}' JSON: the ranked leaderboard (the latest
     * clean result of each entry) and the broken rows (wrong answers, crashes), the latter as not passed.
     */
    @NonNull
    static List<ChatResult> chatResultsFrom(@Nullable JSONObject benchmarks) {
        List<ChatResult> results = new ArrayList<>();
        JSONObject leaderboard = benchmarks == null ? null : benchmarks.optJSONObject("leaderboard");
        if (leaderboard == null) return results;
        for (String list : new String[] {"ranked", "broken"}) {
            JSONArray rows = leaderboard.optJSONArray(list);
            if (rows == null) continue;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String verdict = row.optString("verdict", "");
                boolean passed = row.optBoolean("checkPassed", false)
                    && !TaiBenchStats.VERDICT_BROKEN.equals(verdict) && !TaiBenchStats.VERDICT_CRASHED.equals(verdict);
                results.add(new ChatResult(row.optString("modelId", ""), row.optString("backend", ""),
                    row.optString("accelerator", ""), row.optBoolean("speculative", false),
                    row.optDouble("decodeTps", 0.0), passed));
            }
        }
        return results;
    }

    /** A table of evidence for tests: add what the phone "measured", then plan against it. */
    final class InMemory implements TaiEvidence {
        private final Set<String> failures = new HashSet<>();
        private final List<ChatResult> chat = new ArrayList<>();
        private final Map<TaiFunction, List<FeatureResult>> checks = new EnumMap<>(TaiFunction.class);
        @NonNull private TaiGpuVerdict.State verdict = TaiGpuVerdict.State.UNKNOWN;

        @NonNull
        public InMemory failure(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator) {
            failures.add(key(modelId, backend, accelerator));
            return this;
        }

        @NonNull
        public InMemory verdict(@NonNull TaiGpuVerdict.State state) {
            verdict = state;
            return this;
        }

        @NonNull
        public InMemory chat(@NonNull ChatResult result) {
            chat.add(result);
            return this;
        }

        @NonNull
        public InMemory check(@NonNull FeatureResult result) {
            List<FeatureResult> list = checks.get(result.feature);
            if (list == null) checks.put(result.feature, list = new ArrayList<>());
            list.add(result);
            return this;
        }

        @Override
        public boolean failed(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator) {
            return failures.contains(key(modelId, backend, accelerator));
        }

        @NonNull
        @Override
        public TaiGpuVerdict.State gpuVerdict() {
            return verdict;
        }

        @NonNull
        @Override
        public List<ChatResult> chatBench(@NonNull String modelId, @NonNull String backend) {
            List<ChatResult> out = new ArrayList<>();
            for (ChatResult result : chat) {
                if (result.modelId.equals(modelId) && result.backend.equals(backend)) out.add(result);
            }
            return out;
        }

        @NonNull
        @Override
        public List<FeatureResult> featureChecks(@NonNull TaiFunction feature, @NonNull String modelId,
                                                 @NonNull String backend) {
            List<FeatureResult> list = checks.get(feature);
            if (list == null) return Collections.emptyList();
            List<FeatureResult> out = new ArrayList<>();
            for (FeatureResult result : list) {
                if (result.modelId.equals(modelId) && result.backend.equals(backend)) out.add(result);
            }
            return out;
        }

        @NonNull
        private static String key(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator) {
            return modelId + "|" + backend + "|" + accelerator.toLowerCase(Locale.ROOT);
        }
    }
}
