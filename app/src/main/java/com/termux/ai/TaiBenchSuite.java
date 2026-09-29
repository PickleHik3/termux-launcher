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
 * Bench v2: the fixed prompts, the check questions with their graders, and the presets, as data.
 * Plain Java on purpose, so the preset expansion, the long-input fitting and the graders are
 * covered by JVM tests and the harness ({@link TaiBenchHarness}) has nothing of its own to decide
 * about what a run contains.
 *
 * <p>Three tests stand for real use. <b>Chat</b> is an everyday question with a full answer: it
 * gives "starts replying in X s" (time to first token) and "writes N tok/s" (decode).
 * <b>Long input</b> pastes a build log of about 2000 tokens and asks for a short answer: it gives
 * "reads a long page in X s" (the wait for the first token). <b>Sanity</b> asks three questions
 * with known answers and only shows when they fail. The long log is an asset
 * ({@code tai/bench/build_log.txt}); everything that changes what a record means bumps
 * {@link #BENCH_VERSION}, and the store never ranks two versions against each other.
 */
public final class TaiBenchSuite {
    public static final String BENCH_VERSION = "bench_v2";
    /** Where the harness finds the long-input log in the APK. */
    static final String LONG_INPUT_ASSET = "tai/bench/build_log.txt";

    public static final String PHASE_LOAD = "load";
    public static final String PHASE_WARMUP = "warmup";
    public static final String PHASE_CHAT = "chat";
    public static final String PHASE_LONG_INPUT = "longInput";
    public static final String PHASE_CHECK = "check";

    /** Nothing but the reply: a system line the chat templates of both backends accept. */
    static final String SYSTEM_PROMPT = "You are a helpful assistant. Answer directly.";
    /** An everyday power-user question whose full answer runs to a few hundred tokens. */
    static final String CHAT_PROMPT = "Explain what a shell alias is and give two useful examples.";
    /** The question after the pasted log. */
    static final String LONG_INPUT_QUESTION = "What went wrong, in two sentences?";
    /** The warm-up asks for a word and throws it away; it only exists to fill the caches. */
    static final String WARMUP_PROMPT = "Say hello.";

    /** Most models finish the chat answer on their own inside this; the reply says when the cap stopped it. */
    static final int CHAT_MAX_TOKENS = 320;
    /** Two sentences fit well inside it. */
    static final int LONG_INPUT_MAX_TOKENS = 96;
    static final int WARMUP_MAX_TOKENS = 8;
    static final int CHECK_MAX_TOKENS = 32;

    /**
     * The long log is cut from the top when the loaded window cannot hold it. Log text (paths,
     * versions, hashes) splits into small tokens, so three characters a token is the safe side.
     */
    static final int CHARS_PER_TOKEN_ESTIMATE = 3;
    /** Room kept beside the log and the reply for the system line, the question and the chat template. */
    static final int PROMPT_OVERHEAD_TOKENS = 128;
    /** However small the window, this much of the log's tail is kept: the error is at the end. */
    static final int MIN_LOG_CHARS = 512;

    /**
     * What a phase is expected to take on a slow phone, in seconds; a phase is stopped at three
     * times this ({@link #timeLimitMs}). Chat is 320 tokens at 3.5 tok/s; long input is about 2700
     * prompt tokens at 30 tok/s plus a short reply at 3.5 tok/s; a load is a cold read of a few GB.
     */
    static final int EXPECTED_LOAD_SECONDS = 90;
    static final int EXPECTED_WARMUP_SECONDS = 30;
    static final int EXPECTED_CHAT_SECONDS = 100;
    static final int EXPECTED_LONG_INPUT_SECONDS = 120;
    static final int EXPECTED_CHECK_SECONDS = 20;
    static final int TIME_LIMIT_FACTOR = 3;

    public static final String ACCELERATOR_CPU = "cpu";
    public static final String ACCELERATOR_GPU = "gpu";

    private TaiBenchSuite() {
    }

    /** The long-input prompt as the harness sends it, and whether the log had to be cut to fit. */
    static final class LongInput {
        @NonNull final String prompt;
        final boolean truncated;
        /** Characters of the log that were kept, and the log's whole length. */
        final int keptChars;
        final int totalChars;

        LongInput(@NonNull String prompt, boolean truncated, int keptChars, int totalChars) {
            this.prompt = prompt;
            this.truncated = truncated;
            this.keptChars = keptChars;
            this.totalChars = totalChars;
        }
    }

    /**
     * The log, a blank line, the question. When {@code contextWindow} (tokens; {@code <= 0} for
     * unknown) cannot hold the log plus the reply cap and {@link #PROMPT_OVERHEAD_TOKENS}, the log
     * is cut from the top, at a line boundary, to what fits: the error is at the end of a log, so
     * the tail is what is kept.
     */
    @NonNull
    static LongInput longInput(@NonNull String log, int contextWindow) {
        String text = log.trim();
        int total = text.length();
        String kept = text;
        if (contextWindow > 0) {
            int budgetTokens = contextWindow - LONG_INPUT_MAX_TOKENS - PROMPT_OVERHEAD_TOKENS;
            int maxChars = Math.max(MIN_LOG_CHARS, budgetTokens * CHARS_PER_TOKEN_ESTIMATE);
            if (total > maxChars) {
                int start = total - maxChars;
                int lineBreak = text.indexOf('\n', start);
                if (lineBreak >= 0 && lineBreak + 1 < total) start = lineBreak + 1;
                kept = text.substring(start);
            }
        }
        return new LongInput(kept + "\n\n" + LONG_INPUT_QUESTION, kept.length() < total, kept.length(), total);
    }

    /** Screen 2's rough time per model and processor: Quick a minute and a half, Standard three minutes. */
    static final long ESTIMATE_QUICK_PER_PROCESSOR_MS = 90_000L;
    static final long ESTIMATE_STANDARD_PER_PROCESSOR_MS = 3 * 60_000L;

    /**
     * How long one model is expected to take under {@code preset} on {@code processors}
     * processors (the spec's "rough time per model", without the cool-down); a count under one
     * reads as one.
     */
    public static long estimateMs(@NonNull Preset preset, int processors) {
        long perProcessor = preset == Preset.QUICK ? ESTIMATE_QUICK_PER_PROCESSOR_MS : ESTIMATE_STANDARD_PER_PROCESSOR_MS;
        return perProcessor * Math.max(1, processors);
    }

    /** The limit for one run of {@code phase}. */
    static long timeLimitMs(@NonNull String phase) {
        int expected;
        switch (phase) {
            case PHASE_LOAD: expected = EXPECTED_LOAD_SECONDS; break;
            case PHASE_WARMUP: expected = EXPECTED_WARMUP_SECONDS; break;
            case PHASE_LONG_INPUT: expected = EXPECTED_LONG_INPUT_SECONDS; break;
            case PHASE_CHECK: expected = EXPECTED_CHECK_SECONDS; break;
            default: expected = EXPECTED_CHAT_SECONDS; break;
        }
        return expected * TIME_LIMIT_FACTOR * 1000L;
    }

    // ---- Presets ----------------------------------------------------------------------------

    /** How many runs of each test a preset does; the reported figure is their median. */
    public enum Preset {
        QUICK("quick", 1),
        STANDARD("standard", 2);

        public final String id;
        public final int runs;

        Preset(String id, int runs) {
            this.id = id;
            this.runs = runs;
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
        /** The processor an automatic load would choose on this phone (the default pick). */
        @NonNull final String bestAccelerator;
        /** Whether the model's file runs on the CPU at all (a GPU-only bundle does not). */
        final boolean cpuSupported;
        /** Whether the model and the phone allow a GPU entry at all. */
        final boolean gpuSupported;
        /** Whether the model ships a draft model (Eagle) that {@code eagle} can switch on. */
        final boolean speculativeCapable;

        ModelInput(@NonNull String modelId, @NonNull String backend, @NonNull String bestAccelerator,
                   boolean gpuSupported, boolean speculativeCapable) {
            this(modelId, backend, bestAccelerator, true, gpuSupported, speculativeCapable);
        }

        ModelInput(@NonNull String modelId, @NonNull String backend, @NonNull String bestAccelerator,
                   boolean cpuSupported, boolean gpuSupported, boolean speculativeCapable) {
            this.modelId = modelId;
            this.backend = backend;
            this.bestAccelerator = bestAccelerator;
            this.cpuSupported = cpuSupported;
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
     * Expands models x processors into the entries a run does, in request order. With
     * {@code processors} given (the CLI's {@code --cpu}/{@code --gpu}) every model gets exactly
     * those, supported or not: the load's preflight then refuses the ones the phone cannot do,
     * and the run says why instead of silently leaving them out. Otherwise {@code bothProcessors}
     * (the "Compare CPU and GPU" switch) takes the CPU and the GPU where supported, and the
     * default takes only the processor an automatic load would. A GPU-only model skips the CPU
     * there; with neither processor usable the CPU entry stays, so the run still says why it
     * failed. {@code eagle} turns the draft model on for every model that has one; the others
     * ignore it.
     */
    @NonNull
    static List<EntryPlan> expand(@NonNull List<ModelInput> models, boolean bothProcessors,
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
            } else if (bothProcessors) {
                if (model.cpuSupported || !model.gpuSupported) accelerators.add(ACCELERATOR_CPU);
                if (model.gpuSupported) accelerators.add(ACCELERATOR_GPU);
            } else if (model.gpuSupported && !model.cpuSupported) {
                accelerators.add(ACCELERATOR_GPU);
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
