package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Bench v1: the fixed prompts, the check questions with their graders, and the presets, as data.
 * Plain Java on purpose, so the preset expansion and the graders are covered by JVM tests and the
 * harness ({@link TaiBenchHarness}) has nothing of its own to decide about what a run contains.
 *
 * <p>The reading passage is an asset ({@code tai/bench/reading.txt}, about 512 tokens) rather than
 * a constant here: it is prose, it is long, and the harness reads it once per run. Everything
 * that changes what a record means bumps {@link #BENCH_VERSION}; the store never ranks two
 * versions against each other.
 */
public final class TaiBenchSuite {
    public static final String BENCH_VERSION = "bench_v1";
    /** Where the harness finds the reading passage in the APK. */
    static final String READING_ASSET = "tai/bench/reading.txt";

    public static final String PHASE_LOAD = "load";
    public static final String PHASE_WARMUP = "warmup";
    public static final String PHASE_READING = "reading";
    public static final String PHASE_FIRST_WORD = "firstWord";
    public static final String PHASE_WRITING = "writing";
    public static final String PHASE_SUSTAINED = "sustained";
    public static final String PHASE_CHECK = "check";

    /** Nothing but the reply: a system line the chat templates of both backends accept. */
    static final String SYSTEM_PROMPT = "You are a helpful assistant. Answer directly.";
    /** About 30 tokens; only its first token is timed. */
    static final String FIRST_WORD_PROMPT =
        "In one short sentence, tell me what a good morning routine looks like for someone who works from home.";
    /** A prompt whose natural answer runs well past the 128-token cap, so the cap is what stops it. */
    static final String WRITING_PROMPT =
        "Write a detailed, step-by-step explanation of how rain forms, from evaporation over the sea "
            + "to droplets falling on a hillside. Use plain language and full paragraphs.";
    /** The warm-up asks for a word and throws it away; it only exists to fill the caches. */
    static final String WARMUP_PROMPT = "Say hello.";
    static final String READING_INSTRUCTION = "Summarise the passage above in one line.";

    static final int WRITING_MAX_TOKENS = 128;
    static final int READING_MAX_TOKENS = 32;
    static final int FIRST_WORD_MAX_TOKENS = 16;
    static final int WARMUP_MAX_TOKENS = 8;
    static final int CHECK_MAX_TOKENS = 32;
    /** Sustained keeps writing until the harness stops it; the cap only has to be out of reach. */
    static final int SUSTAINED_MAX_TOKENS = 8192;
    static final int SUSTAINED_SECONDS = 90;

    /**
     * What a phase is expected to take on a slow phone, in seconds; a phase is stopped at three
     * times this ({@link #timeLimitMs}). The writing figure is 128 tokens at 3 tok/s; reading is
     * 512 prompt tokens at 30 tok/s plus a short reply; a load is a cold read of a few GB.
     */
    static final int EXPECTED_LOAD_SECONDS = 90;
    static final int EXPECTED_WARMUP_SECONDS = 30;
    static final int EXPECTED_READING_SECONDS = 40;
    static final int EXPECTED_FIRST_WORD_SECONDS = 20;
    static final int EXPECTED_WRITING_SECONDS = 45;
    static final int EXPECTED_CHECK_SECONDS = 20;
    static final int TIME_LIMIT_FACTOR = 3;

    public static final String ACCELERATOR_CPU = "cpu";
    public static final String ACCELERATOR_GPU = "gpu";

    private TaiBenchSuite() {
    }

    /** The reading prompt: the passage, a blank line, the instruction. */
    @NonNull
    static String readingPrompt(@NonNull String passage) {
        return passage.trim() + "\n\n" + READING_INSTRUCTION;
    }

    /** Screen 2's rough time per model: Quick about a minute in all, Standard two minutes per processor, Thorough four. */
    static final long ESTIMATE_QUICK_MS = 60_000L;
    static final long ESTIMATE_STANDARD_PER_PROCESSOR_MS = 2 * 60_000L;
    static final long ESTIMATE_THOROUGH_PER_PROCESSOR_MS = 4 * 60_000L;

    /**
     * How long one model is expected to take under {@code preset} on {@code processors}
     * processors (the spec's "rough time per model", without the cool-down). Quick runs one
     * processor whatever the count; a count under one reads as one.
     */
    public static long estimateMs(@NonNull Preset preset, int processors) {
        int count = Math.max(1, processors);
        switch (preset) {
            case QUICK: return ESTIMATE_QUICK_MS;
            case THOROUGH: return ESTIMATE_THOROUGH_PER_PROCESSOR_MS * count;
            default: return ESTIMATE_STANDARD_PER_PROCESSOR_MS * count;
        }
    }

    /** The limit for one run of {@code phase}, or for the whole sustained window. */
    static long timeLimitMs(@NonNull String phase) {
        int expected;
        switch (phase) {
            case PHASE_LOAD: expected = EXPECTED_LOAD_SECONDS; break;
            case PHASE_WARMUP: expected = EXPECTED_WARMUP_SECONDS; break;
            case PHASE_READING: expected = EXPECTED_READING_SECONDS; break;
            case PHASE_FIRST_WORD: expected = EXPECTED_FIRST_WORD_SECONDS; break;
            case PHASE_WRITING: expected = EXPECTED_WRITING_SECONDS; break;
            case PHASE_SUSTAINED: expected = SUSTAINED_SECONDS; break;
            case PHASE_CHECK: expected = EXPECTED_CHECK_SECONDS; break;
            default: expected = EXPECTED_WRITING_SECONDS; break;
        }
        return expected * TIME_LIMIT_FACTOR * 1000L;
    }

    // ---- Presets ----------------------------------------------------------------------------

    /** How many runs of each timed phase a preset does, and which processors it covers. */
    public enum Preset {
        QUICK("quick", 1, 1, 1, false, false),
        STANDARD("standard", 3, 3, 3, false, true),
        THOROUGH("thorough", 3, 3, 5, true, true);

        public final String id;
        public final int readingRuns;
        public final int firstWordRuns;
        public final int writingRuns;
        public final boolean sustained;
        /** Every supported processor, or only the one an automatic load would pick. */
        public final boolean bothProcessors;

        Preset(String id, int readingRuns, int firstWordRuns, int writingRuns, boolean sustained, boolean bothProcessors) {
            this.id = id;
            this.readingRuns = readingRuns;
            this.firstWordRuns = firstWordRuns;
            this.writingRuns = writingRuns;
            this.sustained = sustained;
            this.bothProcessors = bothProcessors;
        }

        /** {@code null} for a name that is not a preset; an empty or missing name is Standard. */
        @Nullable
        public static Preset fromId(@Nullable String id) {
            if (id == null || id.trim().isEmpty()) return STANDARD;
            String wanted = id.trim().toLowerCase(Locale.ROOT);
            for (Preset preset : values()) {
                if (preset.id.equals(wanted)) return preset;
            }
            return null;
        }
    }

    /** What the expansion needs to know about one model; the harness fills it from the spec and device. */
    static final class ModelInput {
        @NonNull final String modelId;
        @NonNull final String backend;
        /** The processor an automatic load would choose on this phone (the Quick preset's pick). */
        @NonNull final String bestAccelerator;
        /** Whether the model and the phone allow a GPU entry at all. */
        final boolean gpuSupported;
        /** Whether the model ships a draft model (Eagle) that {@code eagle} can switch on. */
        final boolean speculativeCapable;

        ModelInput(@NonNull String modelId, @NonNull String backend, @NonNull String bestAccelerator,
                   boolean gpuSupported, boolean speculativeCapable) {
            this.modelId = modelId;
            this.backend = backend;
            this.bestAccelerator = bestAccelerator;
            this.gpuSupported = gpuSupported;
            this.speculativeCapable = speculativeCapable;
        }
    }

    /** One leaderboard entry to run: a model on one processor, with or without its draft model. */
    public static final class EntryPlan {
        @NonNull public final String modelId;
        @NonNull public final String backend;
        @NonNull public final String accelerator;
        public final boolean speculative;

        public EntryPlan(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator, boolean speculative) {
            this.modelId = modelId;
            this.backend = backend;
            this.accelerator = accelerator;
            this.speculative = speculative;
        }

        /** The store's entry key: {@code modelId|backend|accelerator|speculative}. */
        @NonNull
        public String key() {
            return key(modelId, backend, accelerator, speculative);
        }

        @NonNull
        public static String key(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator, boolean speculative) {
            return modelId + "|" + backend + "|" + accelerator + "|" + (speculative ? "on" : "off");
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            return new JSONObject()
                .put("modelId", modelId)
                .put("backend", backend)
                .put("accelerator", accelerator)
                .put("speculative", speculative)
                .put("key", key());
        }
    }

    /**
     * Expands models × processors into the entries a run does, in request order. With
     * {@code processors} given (the CLI's {@code --cpu}/{@code --gpu}) every model gets exactly
     * those, supported or not: the load's preflight then refuses the ones the phone cannot do,
     * and the run says why instead of silently leaving them out. Otherwise the preset decides:
     * Standard and Thorough take the CPU and the GPU where supported, Quick takes only the
     * processor an automatic load would. {@code eagle} turns the draft model on for every model
     * that has one; the others ignore it.
     */
    @NonNull
    static List<EntryPlan> expand(@NonNull List<ModelInput> models, @NonNull Preset preset,
                                  @Nullable List<String> processors, boolean eagle) {
        List<EntryPlan> entries = new ArrayList<>();
        for (ModelInput model : models) {
            List<String> accelerators = new ArrayList<>();
            if (processors != null && !processors.isEmpty()) {
                for (String processor : processors) {
                    String normalized = processor.trim().toLowerCase(Locale.ROOT);
                    if (!ACCELERATOR_CPU.equals(normalized) && !ACCELERATOR_GPU.equals(normalized)) continue;
                    if (!accelerators.contains(normalized)) accelerators.add(normalized);
                }
            } else if (preset.bothProcessors) {
                accelerators.add(ACCELERATOR_CPU);
                if (model.gpuSupported) accelerators.add(ACCELERATOR_GPU);
            } else {
                accelerators.add(model.gpuSupported ? model.bestAccelerator : ACCELERATOR_CPU);
            }
            for (String accelerator : accelerators) {
                entries.add(new EntryPlan(model.modelId, model.backend, accelerator, eagle && model.speculativeCapable));
            }
        }
        return Collections.unmodifiableList(entries);
    }

    // ---- Check questions ----------------------------------------------------------------------

    /** A fixed question with a known answer and a grader tolerant of the model's formatting. */
    static final class Check {
        @NonNull final String name;
        @NonNull final String prompt;

        private Check(@NonNull String name, @NonNull String prompt) {
            this.name = name;
            this.prompt = prompt;
        }

        boolean grade(@Nullable String reply) {
            String cleaned = normalize(reply);
            switch (name) {
                case "arithmetic":
                    return cleaned.contains("42");
                case "json":
                    return gradeJson(cleaned);
                case "repeat":
                    return cleaned.contains("pineapple");
                default:
                    return false;
            }
        }

        /** Case, surrounding whitespace and a trailing full stop never fail a check. */
        @NonNull
        static String normalize(@Nullable String reply) {
            if (reply == null) return "";
            String cleaned = reply.trim().toLowerCase(Locale.ROOT);
            while (cleaned.endsWith(".")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
            return cleaned;
        }

        /** The JSON object may sit inside a code fence or a sentence; the first {@code {…}} is graded. */
        private static boolean gradeJson(@NonNull String cleaned) {
            int open = cleaned.indexOf('{');
            int close = cleaned.lastIndexOf('}');
            if (open < 0 || close <= open) return false;
            try {
                JSONObject json = new JSONObject(cleaned.substring(open, close + 1));
                return "blue".equals(json.optString("color", "").trim());
            } catch (JSONException e) {
                return false;
            }
        }
    }

    static final List<Check> CHECKS = Collections.unmodifiableList(Arrays.asList(
        new Check("arithmetic", "What is 17 + 25? Answer with just the number."),
        new Check("json", "Reply with only this JSON and nothing else: {\"color\":\"blue\"}"),
        new Check("repeat", "Repeat exactly the word \"pineapple\" and nothing else.")
    ));
}
