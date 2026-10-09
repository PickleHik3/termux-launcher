package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The feature check's decisions, pure (tai-feature-load-plan spec, step 2): which runs a feature gets,
 * what its figure is and how it reads (Smooth, Usable, Slow), whether an answer is sane, whether a
 * result still describes the file and runtime on the phone, what a run says about the GPU verdict,
 * and the stored record that {@link TaiEvidence} reads back as a {@link TaiEvidence.FeatureResult}.
 * {@code TaiFeatureCheckRunner} runs the workloads; {@link TaiFeatureCheckStore} keeps the records.
 *
 * <p>Every result's {@code speed} is larger-is-faster in the feature's own unit, so the plan can
 * compare two setups of one feature; {@link #figure} turns it into the number a row prints.
 */
public final class TaiFeatureCheck {
    private TaiFeatureCheck() {
    }

    /**
     * The features the check measures, in the order a run takes them. Voice typing is not among them:
     * its workload is a recorded clip, and the app ships none yet.
     */
    public static final List<TaiFunction> FEATURES = Collections.unmodifiableList(Arrays.asList(
        TaiFunction.TIDY_DICTATION, TaiFunction.APP_CATEGORIES, TaiFunction.ASSISTANT, TaiFunction.DAWN_CHAT,
        TaiFunction.EMBEDDINGS, TaiFunction.READ_ALOUD));

    /** The record layout; a record of another version is ignored. */
    static final int RECORD_VERSION = 1;

    // ---------------------------------------------------------------------------------- runs

    /** Which axis a run flips against the plan's own setup. */
    public enum Axis { CURRENT, ACCELERATOR, SPECULATIVE }

    /** One run of a feature: its accelerator and whether it asks for speculative decoding. */
    public static final class Variant {
        @NonNull public final Axis axis;
        @NonNull public final String accelerator;
        public final boolean speculative;

        Variant(@NonNull Axis axis, @NonNull String accelerator, boolean speculative) {
            this.axis = axis;
            this.accelerator = accelerator;
            this.speculative = speculative;
        }
    }

    /** The runs of one feature, and whether the speculative run was left out because the file cannot do it. */
    public static final class Runs {
        @NonNull public final List<Variant> variants;
        /** The model file does not declare speculative decoding: the row says "not available for this model". */
        public final boolean speculativeUnavailable;

        Runs(@NonNull List<Variant> variants, boolean speculativeUnavailable) {
            this.variants = Collections.unmodifiableList(variants);
            this.speculativeUnavailable = speculativeUnavailable;
        }
    }

    /**
     * The runs for a feature (spec decision 3): the plan's setup, then the other accelerator, then
     * speculative decoding toggled; at most three. A CPU-only feature gets one run. The GPU run is left
     * out when the phone has no usable GPU ({@code gpuUsable}), and the speculative one when the file
     * does not declare support.
     */
    @NonNull
    public static Runs runs(@NonNull TaiFunction feature, @Nullable String planAccelerator, @Nullable Boolean planSpeculative,
                            boolean modelDeclaresSpeculative, boolean gpuUsable) {
        List<Variant> variants = new ArrayList<>();
        if (!feature.usesChatModel()) {
            variants.add(new Variant(Axis.CURRENT, TaiTierPolicy.ACCEL_CPU, false));
            return new Runs(variants, false);
        }
        String current = TaiTierPolicy.ACCEL_GPU.equals(planAccelerator) && gpuUsable
            ? TaiTierPolicy.ACCEL_GPU : TaiTierPolicy.ACCEL_CPU;
        boolean speculative = modelDeclaresSpeculative && Boolean.TRUE.equals(planSpeculative);
        variants.add(new Variant(Axis.CURRENT, current, speculative));
        String other = TaiTierPolicy.ACCEL_GPU.equals(current) ? TaiTierPolicy.ACCEL_CPU : TaiTierPolicy.ACCEL_GPU;
        if (gpuUsable || TaiTierPolicy.ACCEL_CPU.equals(other)) variants.add(new Variant(Axis.ACCELERATOR, other, speculative));
        if (modelDeclaresSpeculative) variants.add(new Variant(Axis.SPECULATIVE, current, !speculative));
        return new Runs(variants, !modelDeclaresSpeculative);
    }

    /**
     * The accelerator a load result's {@code backend} names ({@code GPU}, {@code opencl}, {@code cpu}…);
     * {@code asked} when it names neither.
     */
    @NonNull
    public static String acceleratorOf(@Nullable String backend, @NonNull String asked) {
        String value = backend == null ? "" : backend.toLowerCase(Locale.ROOT);
        if (value.contains("gpu") || value.contains("opencl") || value.contains("vulkan")) return TaiTierPolicy.ACCEL_GPU;
        if (value.contains("cpu")) return TaiTierPolicy.ACCEL_CPU;
        return asked.toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the check runs the GPU at all: the phone has one ({@code path} is not {@code NO}) and its
     * verdict is not that it answers wrongly. A Failed verdict stays until "Try GPU again" clears it.
     */
    public static boolean gpuUsable(@NonNull TaiPlatformCaps.GpuPath path, @NonNull TaiGpuVerdict.State verdict) {
        return path != TaiPlatformCaps.GpuPath.NO && verdict != TaiGpuVerdict.State.FAILED;
    }

    // ------------------------------------------------------------------------------- figures

    /** What a feature's figure counts. */
    public enum Unit {
        /** Cleanup: seconds to tidy a minute of speech ("a minute of speech tidied in 6 s"). */
        SECONDS_PER_MINUTE_OF_SPEECH,
        /** App sorting: seconds per app ("0.9 s per app"). */
        SECONDS_PER_APP,
        /** Assistant and Dawn chat: tokens written per second. */
        TOKENS_PER_SECOND,
        /** Dawn search: notes read per second. */
        NOTES_PER_SECOND,
        /** Read aloud: seconds to the first sound ("speaks in 0.3 s"). */
        SECONDS_TO_FIRST_SOUND
    }

    /** A minute of dictation: the pace the 150-word sample stands for. */
    public static final int WORDS_PER_MINUTE = 150;

    // The verdict lines, each in the unit's own direction. Cleanup and app sorting are from the pong runs
    // of 2026-10-05 (project-docs/reference/voice-ai/daytoday-settings-bench-2026-10-05.md); the chat
    // features reuse the bench's lines. Dawn search and read aloud were not measured there: their lines
    // are provisional, set from what a person notices, until a phone run gives them numbers.

    /** Cleanup Smooth: pong tidied 146 words in 3.7 s (E2B) to 7.6 s (E4B) with speculative decoding on. */
    static final double CLEANUP_SMOOTH_SECONDS = 8.0;
    /** Cleanup Usable: E4B without speculative decoding took 14.7 s; past 20 s the text arrives long after the speech. */
    static final double CLEANUP_USABLE_SECONDS = 20.0;
    /** App sorting Smooth: pong sorted an app in 0.69–0.76 s on E2B. */
    static final double SORTING_SMOOTH_SECONDS_PER_APP = 1.0;
    /** App sorting Usable: E4B took 1.32–1.42 s; at 3 s a drawer of a hundred apps takes five minutes. */
    static final double SORTING_USABLE_SECONDS_PER_APP = 3.0;
    /** Dawn search Smooth (provisional): a note set of a few hundred indexes while its screen opens. */
    static final double SEARCH_SMOOTH_NOTES_PER_SECOND = 16.0;
    /** Dawn search Usable (provisional): below this, indexing a note set is a wait the user sees. */
    static final double SEARCH_USABLE_NOTES_PER_SECOND = 4.0;
    /** Read aloud Smooth (provisional): speech starts inside the pause after a tap. */
    static final double READ_SMOOTH_SECONDS = 0.5;
    /** Read aloud Usable (provisional): a beat, not a wait. */
    static final double READ_USABLE_SECONDS = 1.5;

    @NonNull
    public static Unit unitOf(@NonNull TaiFunction feature) {
        switch (feature) {
            case TIDY_DICTATION: return Unit.SECONDS_PER_MINUTE_OF_SPEECH;
            case APP_CATEGORIES: return Unit.SECONDS_PER_APP;
            case EMBEDDINGS: return Unit.NOTES_PER_SECOND;
            case READ_ALOUD: return Unit.SECONDS_TO_FIRST_SOUND;
            default: return Unit.TOKENS_PER_SECOND;
        }
    }

    /** Cleanup's speed: seconds of speech tidied per second of waiting, for {@code words} dictated. */
    public static double cleanupSpeed(int words, long elapsedMs) {
        if (words <= 0 || elapsedMs <= 0L) return 0.0;
        double speechSeconds = words * 60.0 / WORDS_PER_MINUTE;
        return speechSeconds * 1000.0 / elapsedMs;
    }

    /** {@code count} items (apps, notes) over {@code elapsedMs}, per second. */
    public static double perSecond(int count, long elapsedMs) {
        if (count <= 0 || elapsedMs <= 0L) return 0.0;
        return count * 1000.0 / elapsedMs;
    }

    /** Read aloud's speed: the inverse of the wait for the first sound, so a shorter wait is faster. */
    public static double firstSoundSpeed(long firstSoundMs) {
        return firstSoundMs <= 0L ? 0.0 : 1000.0 / firstSoundMs;
    }

    /** The number a row prints, in {@link #unitOf}'s unit, from a result's speed; {@code 0} when not measured. */
    public static double figure(@NonNull TaiFunction feature, double speed) {
        if (speed <= 0.0) return 0.0;
        switch (unitOf(feature)) {
            case SECONDS_PER_MINUTE_OF_SPEECH: return 60.0 / speed;
            case SECONDS_PER_APP:
            case SECONDS_TO_FIRST_SOUND: return 1.0 / speed;
            default: return speed;
        }
    }

    /** Smooth, Usable or Slow ({@link TaiBenchStats}' words) for a result's speed; {@code null} when not measured. */
    @Nullable
    public static String verdict(@NonNull TaiFunction feature, double speed) {
        if (speed <= 0.0) return null;
        double figure = figure(feature, speed);
        switch (unitOf(feature)) {
            case SECONDS_PER_MINUTE_OF_SPEECH: return atMost(figure, CLEANUP_SMOOTH_SECONDS, CLEANUP_USABLE_SECONDS);
            case SECONDS_PER_APP: return atMost(figure, SORTING_SMOOTH_SECONDS_PER_APP, SORTING_USABLE_SECONDS_PER_APP);
            case SECONDS_TO_FIRST_SOUND: return atMost(figure, READ_SMOOTH_SECONDS, READ_USABLE_SECONDS);
            case NOTES_PER_SECOND: return atLeast(figure, SEARCH_SMOOTH_NOTES_PER_SECOND, SEARCH_USABLE_NOTES_PER_SECOND);
            default: return atLeast(figure, TaiBenchStats.SMOOTH_DECODE_TPS, TaiBenchStats.USABLE_DECODE_TPS);
        }
    }

    @NonNull
    private static String atMost(double value, double smooth, double usable) {
        if (value <= smooth) return TaiBenchStats.VERDICT_SMOOTH;
        return value <= usable ? TaiBenchStats.VERDICT_USABLE : TaiBenchStats.VERDICT_SLOW;
    }

    @NonNull
    private static String atLeast(double value, double smooth, double usable) {
        if (value >= smooth) return TaiBenchStats.VERDICT_SMOOTH;
        return value >= usable ? TaiBenchStats.VERDICT_USABLE : TaiBenchStats.VERDICT_SLOW;
    }

    // -------------------------------------------------------------------------------- sanity

    // "Correct", for the GPU verdict and a result's passed flag. A chat feature's answer is judged by its
    // feature's own rule; Dawn search and read aloud are correct when they return their output at all.

    /** Cleanup kept at least this share of the dictation's length; less means it dropped the text. */
    static final double CLEANUP_MIN_RATIO = 0.25;
    /** Cleanup wrote at most this many times the dictation's length; Polished's own acceptance cap. */
    static final double CLEANUP_MAX_RATIO = 3.0;

    private static final String[] THINKING_MARKS = {"<think", "</think", "<thinking", "</thinking", "<|channel", "<|start"};

    /**
     * App sorting's answer is right in shape when, stripped of case, spaces, quotes and end punctuation,
     * it is exactly one of the category ids ({@code slugs}): a well-behaved model answers with an id and
     * nothing else, and a broken GPU answers with noise.
     */
    public static boolean sortAnswerOk(@Nullable String reply, @NonNull Collection<String> slugs) {
        if (reply == null) return false;
        String value = reply.trim().toLowerCase(Locale.ROOT).replaceAll("^[\\s\"'`*]+|[\\s\"'`*.!]+$", "");
        return slugs.contains(value);
    }

    /**
     * Cleanup's answer is sane: not empty, no thinking tags, and its length within
     * [{@link #CLEANUP_MIN_RATIO}, {@link #CLEANUP_MAX_RATIO}] of the dictation's.
     */
    public static boolean cleanupOutputOk(@NonNull String dictation, @Nullable String output) {
        if (output == null || output.trim().isEmpty()) return false;
        String lower = output.toLowerCase(Locale.ROOT);
        for (String mark : THINKING_MARKS) {
            if (lower.contains(mark)) return false;
        }
        int input = dictation.trim().length();
        if (input == 0) return false;
        double ratio = output.trim().length() / (double) input;
        return ratio >= CLEANUP_MIN_RATIO && ratio <= CLEANUP_MAX_RATIO;
    }

    // ----------------------------------------------------------------------------- staleness

    /**
     * The model file as a result is tied to it (decision 7): its sha-256 when the store recorded one, else
     * its size and modification time.
     */
    @NonNull
    public static String fileKey(@Nullable String sha256, long sizeBytes, long modifiedMs) {
        if (sha256 != null && !sha256.trim().isEmpty()) return "sha256:" + sha256.trim().toLowerCase(Locale.ROOT);
        return "size:" + sizeBytes + ":mtime:" + modifiedMs;
    }

    /** A result's staleness key: the model file and the runtime version that measured it. */
    @NonNull
    public static String stalenessKey(@NonNull String fileKey, @NonNull String runtimeVersion) {
        return fileKey + "|" + runtimeVersion;
    }

    /** A result is stale when its key is not the file and runtime on the phone now, or either is unknown. */
    public static boolean isStale(@Nullable String recordKey, @Nullable String currentKey) {
        return recordKey == null || recordKey.isEmpty() || currentKey == null || !recordKey.equals(currentKey);
    }

    // ---------------------------------------------------------------------------- GPU verdict

    /** One run as the GPU verdict sees it. */
    public static final class Outcome {
        /** The accelerator the model actually came up on: a GPU load that fell back runs on the CPU. */
        @NonNull public final String ranOn;
        /** The runtime process died during the run. */
        public final boolean crashed;
        /** The answers passed the feature's sanity check. */
        public final boolean correct;
        /** The run's speculative-decoding setting: a GPU run is only compared with a CPU run that had the same one. */
        public final boolean speculative;

        public Outcome(@NonNull String ranOn, boolean crashed, boolean correct) {
            this(ranOn, crashed, correct, false);
        }

        public Outcome(@NonNull String ranOn, boolean crashed, boolean correct, boolean speculative) {
            this.ranOn = ranOn.toLowerCase(Locale.ROOT);
            this.crashed = crashed;
            this.correct = correct;
            this.speculative = speculative;
        }
    }

    /**
     * What one feature's runs say about the GPU (decision 8): {@code FAILED} when a GPU run crashed, or
     * gave wrong answers while a CPU run of the same model and speculative setting got them right (the model is fine, the GPU is
     * not); {@code VERIFIED} when a GPU run answered correctly and none failed; {@code UNKNOWN} when the
     * runs say nothing (no GPU run, or the model is wrong on every processor).
     */
    @NonNull
    public static TaiGpuVerdict.State gpuOutcome(@NonNull List<Outcome> outcomes) {
        boolean cpuCorrectSpeculative = false;
        boolean cpuCorrectPlain = false;
        for (Outcome outcome : outcomes) {
            if (TaiTierPolicy.ACCEL_CPU.equals(outcome.ranOn) && !outcome.crashed && outcome.correct) {
                if (outcome.speculative) cpuCorrectSpeculative = true;
                else cpuCorrectPlain = true;
            }
        }
        boolean verified = false;
        for (Outcome outcome : outcomes) {
            if (!TaiTierPolicy.ACCEL_GPU.equals(outcome.ranOn)) continue;
            boolean cpuCorrect = outcome.speculative ? cpuCorrectSpeculative : cpuCorrectPlain;
            if (outcome.crashed || (!outcome.correct && cpuCorrect)) return TaiGpuVerdict.State.FAILED;
            if (outcome.correct) verified = true;
        }
        return verified ? TaiGpuVerdict.State.VERIFIED : TaiGpuVerdict.State.UNKNOWN;
    }

    /** The phone-wide verdict after a feature's outcome: the latest result wins, and one that says nothing keeps it. */
    @NonNull
    public static TaiGpuVerdict.State nextVerdict(@NonNull TaiGpuVerdict.State current, @NonNull TaiGpuVerdict.State outcome) {
        return outcome == TaiGpuVerdict.State.VERIFIED || outcome == TaiGpuVerdict.State.FAILED ? outcome : current;
    }

    // ------------------------------------------------------------------------------- records

    public static final String STATUS_COMPLETE = TaiBenchStore.STATUS_COMPLETE;
    public static final String STATUS_CRASHED = TaiBenchStore.STATUS_CRASHED;

    /** What one run measured, before it becomes a record. */
    public static final class Measurement {
        @NonNull public TaiFunction feature = TaiFunction.ASSISTANT;
        @NonNull public String modelId = "";
        @NonNull public String backend = "";
        /** The accelerator the run asked for. */
        @NonNull public String accelerator = TaiTierPolicy.ACCEL_CPU;
        /** The accelerator the model came up on; empty when it never loaded. */
        @NonNull public String ranOn = "";
        public boolean speculative;
        @Nullable public Boolean speculativeRan;
        public double speed;
        /** Decode speed across the run's replies, tokens per second; {@code 0} where nothing was decoded. */
        public double decodeTps;
        public long loadMs = -1L;
        /** The load and every request were let in by the memory budget. */
        public boolean fits;
        public boolean passed;
        @NonNull public String status = STATUS_COMPLETE;
        @Nullable public String reason;
        @NonNull public String staleKey = "";
        @NonNull public String displayName = "";
        public long timestamp;
        @Nullable public JSONObject conditions;
    }

    /** A model id without its modality suffix ({@code -vision}): the file a result belongs to. */
    @NonNull
    public static String baseModelId(@NonNull String modelId) {
        return TaiModelVariants.baseModelId(modelId);
    }

    /** The store's key of a run: one feature on one setup of one model file. */
    @NonNull
    public static String keyOf(@NonNull TaiFunction feature, @NonNull String modelId, @NonNull String backend,
                               @NonNull String accelerator, boolean speculative) {
        return feature.id() + "|" + modelId + "|" + backend + "|" + accelerator.toLowerCase(Locale.ROOT)
            + "|" + (speculative ? "on" : "off");
    }

    /** {@link #keyOf} for a stored record; it uses the accelerator the run asked for, so each run has one key. */
    @NonNull
    public static String keyOf(@NonNull JSONObject record) {
        return record.optString("feature", "") + "|" + record.optString("modelId", "") + "|"
            + record.optString("backend", "") + "|" + record.optString("accelerator", "").toLowerCase(Locale.ROOT)
            + "|" + (record.optBoolean("speculative", false) ? "on" : "off");
    }

    /** The record the store keeps for {@code m}, with its figure and verdict worked out. */
    @NonNull
    public static JSONObject record(@NonNull Measurement m) throws JSONException {
        boolean complete = STATUS_COMPLETE.equals(m.status);
        String verdict = complete && m.passed ? verdict(m.feature, m.speed) : null;
        JSONObject json = new JSONObject()
            .put("version", RECORD_VERSION)
            .put("timestamp", m.timestamp)
            .put("feature", m.feature.id())
            .put("modelId", m.modelId)
            .put("displayName", m.displayName.isEmpty() ? m.modelId : m.displayName)
            .put("backend", m.backend)
            .put("accelerator", m.accelerator.toLowerCase(Locale.ROOT))
            .put("ranOn", m.ranOn.toLowerCase(Locale.ROOT))
            .put("speculative", m.speculative)
            .put("speculativeRan", m.speculativeRan == null ? JSONObject.NULL : m.speculativeRan)
            .put("speed", m.speed)
            .put("decodeTps", m.decodeTps)
            .put("loadMs", m.loadMs)
            .put("fits", m.fits)
            .put("passed", m.passed)
            .put("figure", complete ? figure(m.feature, m.speed) : 0.0)
            .put("verdict", verdict == null ? JSONObject.NULL : verdict)
            .put("status", m.status)
            .put("staleKey", m.staleKey);
        if (m.reason != null) json.put("reason", m.reason);
        if (m.conditions != null) json.put("conditions", m.conditions);
        return json;
    }

    /**
     * A stored record as the plan's evidence, judged against the file and runtime on the phone now
     * ({@code currentKey}, see {@link #stalenessKey}); {@code null} for a record of another version or one
     * that names no known feature. A result counts as passed only when the run completed, fitted and
     * answered correctly; it is measured on the accelerator the model came up on.
     */
    @Nullable
    public static TaiEvidence.FeatureResult resultOf(@NonNull JSONObject record, @Nullable String currentKey) {
        if (record.optInt("version", 0) != RECORD_VERSION) return null;
        TaiFunction feature = TaiFunction.fromId(record.optString("feature", ""));
        if (feature == null) return null;
        String ranOn = record.optString("ranOn", "");
        String accelerator = ranOn.isEmpty() ? record.optString("accelerator", "") : ranOn;
        boolean passed = STATUS_COMPLETE.equals(record.optString("status", ""))
            && record.optBoolean("passed", false) && record.optBoolean("fits", false);
        boolean speculative = record.optBoolean("speculative", false);
        Boolean ran = !speculative || record.isNull("speculativeRan") || !record.has("speculativeRan")
            ? null : Boolean.valueOf(record.optBoolean("speculativeRan"));
        boolean stale = isStale(record.optString("staleKey", ""), currentKey);
        return new TaiEvidence.FeatureResult(feature, record.optString("modelId", ""), record.optString("backend", ""),
            accelerator, speculative, record.optDouble("speed", 0.0), record.optDouble("decodeTps", 0.0), passed, ran, stale);
    }
}
