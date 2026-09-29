package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Card-sourced defaults for the litert-community families the importer knows by name, so an
 * imported SmolLM3, Qwen3.5, MiniCPM5, MedGemma, FunctionGemma or EmbeddingGemma lands with the
 * processor, window, sampling, thinking switch and capabilities its publisher documents instead
 * of the generic CPU/1024 import default.
 *
 * <p>Every family is matched against an identity string: a Hugging Face link, a repository id, a
 * file name, or all of them joined, lower-cased. The repository name decides the family and the
 * file name decides the build (text or vision, GPU or CPU, context size). A value no card or
 * config gives is the importer's existing Gemma-style default (top-k 64, top-p 0.95,
 * temperature 1.0), and each such place says so.
 *
 * <p>Sources were read on 2026-09-27 from the Hugging Face API and each repository's files;
 * the chat templates were read out of the first 256 KB of each {@code .litertlm} (the
 * LlmMetadata section sits at the front of the bundle).
 */
public final class TaiImportProfiles {
    public static final String FAMILY_SMOLLM3 = "smollm3";
    public static final String FAMILY_QWEN35 = "qwen3.5";
    public static final String FAMILY_MINICPM5 = "minicpm5";
    public static final String FAMILY_MEDGEMMA = "medgemma";
    public static final String FAMILY_FUNCTIONGEMMA = "functiongemma";
    public static final String FAMILY_EMBEDDINGGEMMA = "embeddinggemma";

    static final String SOURCE = "litert-community-family-table";

    /** A family's defaults for one build: the stored profile, capabilities and runtime floor. */
    public static final class Match {
        @NonNull public final String family;
        @NonNull public final TaiModelProfile profile;
        @NonNull public final LinkedHashSet<String> capabilities;
        /** The LiteRT-LM version the card says the file needs, or {@code null} when it names none. */
        @Nullable public final String minimumRuntimeVersion;

        Match(@NonNull String family, @NonNull TaiModelProfile profile, @NonNull LinkedHashSet<String> capabilities,
                @Nullable String minimumRuntimeVersion) {
            this.family = family;
            this.profile = profile;
            this.capabilities = capabilities;
            this.minimumRuntimeVersion = minimumRuntimeVersion;
        }
    }

    private TaiImportProfiles() {
    }

    /** The family defaults for {@code repository} and {@code fileName}, either of which may be absent. */
    @Nullable
    public static Match match(@Nullable String repository, @Nullable String fileName) {
        return match((repository == null ? "" : repository) + " " + (fileName == null ? "" : fileName));
    }

    /** The family defaults for a free-form identity (link, repository and/or file name), or {@code null}. */
    @Nullable
    public static Match match(@Nullable String identity) {
        String value = identity == null ? "" : identity.toLowerCase(Locale.ROOT);
        if (value.trim().isEmpty()) return null;
        if (value.contains("smollm3-3b")) return smolLm3(value);
        if (value.contains("qwen3.5-0.8b") || value.contains("qwen3.5-2b") || value.contains("qwen3.5-4b")) {
            return qwen35(value);
        }
        // The two CPU-only MiniCPM5-2B files and the three MiniCPM5-1B files are published as
        // "minicpm_w..." without the model's name; no other litert-community repository uses that
        // prefix (MiniCPM-V-4 ships "MiniCPM-V-4-int8.litertlm").
        if (value.contains("minicpm5-") || value.contains("minicpm_w")) return miniCpm5(value);
        if (value.contains("medgemma-1.5-4b-it")) return medGemma(value);
        if (value.contains("functiongemma") || value.contains("mobile_actions_q8_ekv")
            || value.contains("mobile-actions_q8_ekv") || value.contains("tiny_garden_q8_ekv")) {
            return functionGemma(value);
        }
        if (value.contains("embeddinggemma-300m")) return embeddingGemma(value);
        return null;
    }

    // ---- SmolLM3 ----

    /**
     * litert-community/SmolLM3-3B ({@code SmolLM3-3B_q4_block32_ekv4096.litertlm},
     * {@code SmolLM3-3B.litertlm}).
     * <ul>
     *   <li>Sampling: temperature 0.6, top-p 0.95 from
     *   https://huggingface.co/HuggingFaceTB/SmolLM3-3B/resolve/main/generation_config.json (the
     *   upstream card: "We recommend setting temperature=0.6 and top_p=0.95"). No top-k is given, so
     *   the importer's default 64 stays.</li>
     *   <li>Window 4096: the q4 file's {@code _ekv4096} and litertlm_manifest.json
     *   {@code context_length: 4096} for both files.</li>
     *   <li>Processor: the manifest's {@code default_backend} is gpu for the q4 file (verified on a
     *   Galaxy S26 and a Pixel 8a) and cpu for the undocumented full-size file.</li>
     *   <li>Thinking: both bundles declare the {@code thought} channel, so the profile names no
     *   markers and the bundle's own apply. Neither template reads {@code enable_thinking}: the
     *   full-size one switches on {@code /no_think} in the system message, the q4 one passes the
     *   system message through, and upstream trains on that flag, hence the system-flag switch.</li>
     * </ul>
     */
    @NonNull
    private static Match smolLm3(@NonNull String value) {
        boolean q4 = value.contains("_q4_block32");
        List<String> accelerators = q4 ? Arrays.asList("gpu", "cpu") : Arrays.asList("cpu", "gpu");
        int context = contextCap(value, 4096);
        TaiModelProfile profile = new TaiModelProfile(accelerators, TaiModelProfile.sensibleOutputCap(context), 64,
            0.95d, 0.6d, null, SOURCE, TaiModelProfile.THINKING_TOGGLEABLE, null, null, context,
            TaiModelProfile.THINKING_SWITCH_SYSTEM_FLAG);
        return new Match(FAMILY_SMOLLM3, profile,
            caps(TaiModelSpec.CAPABILITY_TEXT_CHAT, "reasoning", TaiModelSpec.CAPABILITY_LLM_THINKING, "multilingual"),
            null);
    }

    // ---- Qwen3.5 ----

    /**
     * litert-community/Qwen3.5-0.8B, -2B and -4B ({@code Qwen3.5-2B_int8.litertlm},
     * {@code Qwen3.5-2B-VL_int8.litertlm}, {@code Qwen3.5-4B_mixed_int4.litertlm} ...).
     * <ul>
     *   <li>Sampling: temperature 0.7, top-p 0.8, top-k 20, the non-thinking set the upstream cards
     *   give for vision tasks (https://huggingface.co/Qwen/Qwen3.5-2B, Best Practices) and for general
     *   tasks on the 4B (https://huggingface.co/Qwen/Qwen3.5-4B). The cards' text set (temperature
     *   1.0, top-p 1.0) leans on presence_penalty 2.0 against repetition, which LiteRT-LM's sampler
     *   has no knob for, so the set that needs no penalty is the one kept.</li>
     *   <li>Window 4096: litertlm_manifest.json {@code context_length: 4096} ("4096-token budget
     *   here" on the 2B card); the upstream 262,144 is not what the bundles carry.</li>
     *   <li>Processor: CPU first. The manifests recommend cpu on Android, the cards say GPU is not
     *   verified on Adreno and an 8 GB phone runs out of memory building the GPU engine (fp32
     *   activations), and both builds fell back to the CPU on pong.</li>
     *   <li>Thinking: none. Every bundle's template opens each assistant turn with an empty
     *   {@code <think>\n\n</think>} block, so thinking is off whatever is sent.</li>
     *   <li>Runtime: "Requires litert-lm >= 0.15" (cards; manifest {@code min_runtime_version}).</li>
     * </ul>
     */
    @NonNull
    private static Match qwen35(@NonNull String value) {
        int context = contextCap(value, 4096);
        TaiModelProfile profile = new TaiModelProfile(Arrays.asList("cpu", "gpu"),
            TaiModelProfile.sensibleOutputCap(context), 20, 0.8d, 0.7d, null, SOURCE,
            TaiModelProfile.THINKING_NONE, null, null, context);
        LinkedHashSet<String> capabilities = caps(TaiModelSpec.CAPABILITY_TEXT_CHAT, "multilingual");
        if (value.contains("-vl")) capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        return new Match(FAMILY_QWEN35, profile, capabilities, "0.15.0");
    }

    // ---- MiniCPM5 ----

    /**
     * litert-community/MiniCPM5-2B ({@code MiniCPM5-2B_int4.litertlm}, {@code MiniCPM5-2B_int8.litertlm},
     * {@code minicpm_wi4c_wi8_afp32.litertlm}, {@code minicpm_wi8_afp32.litertlm}) and
     * litert-community/MiniCPM5-1B ({@code MiniCPM5-1B_dynamic_wi8_afp32.litertlm},
     * {@code minicpm_wi4b32_wi8_afp32.litertlm}, {@code minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm}).
     * <ul>
     *   <li>Sampling: 2B temperature 1.0, top-p 0.95
     *   (https://huggingface.co/openbmb/MiniCPM5-2B/resolve/main/generation_config.json; the litert
     *   card: "OpenBMB recommends temperature 1.0, top_p 0.95"); 1B temperature 0.9, top-p 0.95
     *   (https://huggingface.co/openbmb/MiniCPM5-1B/resolve/main/generation_config.json). No top-k
     *   is given, so the importer's default 64 stays.</li>
     *   <li>Window: 2B 4096 ("4096-token KV budget", litert card and manifest). The 1B card states
     *   none, so TAI's memory tiers decide.</li>
     *   <li>Output cap 2048 on the 2B: the card says to "budget max output tokens >= 2048" for a
     *   thinking run. The 1B card says nothing, so it keeps the 1024 default.</li>
     *   <li>Processor: the 2B int4/int8 files are "CPU and GPU compatible" with gpu the manifest
     *   default; the two {@code minicpm_*} 2B files are "CPU-only models"; the 1B
     *   {@code _gpu_opt} file is "optimized for GPU execution" and the other two are not.</li>
     *   <li>Thinking: every bundle declares the {@code thought} channel, and the templates read
     *   {@code enable_thinking is false} / {@code is true} (or {@code | default(false)}), which a
     *   string never satisfies, so the switch sends a real boolean.</li>
     *   <li>Runtime: the 2B card says "Requires litert-lm >= 0.16"; the 1B card names none.</li>
     * </ul>
     */
    @NonNull
    private static Match miniCpm5(@NonNull String value) {
        // Only the 1B repository publishes the block-32 "minicpm_wi4b32" builds.
        boolean oneB = value.contains("minicpm5-1b") || value.contains("minicpm_wi4b32");
        boolean cpuOnly = !oneB && (value.contains("minicpm_wi4c_wi8_afp32") || value.contains("minicpm_wi8_afp32"));
        List<String> accelerators;
        if (cpuOnly) accelerators = Collections.singletonList("cpu");
        else if (oneB) accelerators = value.contains("_gpu_opt") ? Arrays.asList("gpu", "cpu") : Arrays.asList("cpu", "gpu");
        else accelerators = Arrays.asList("gpu", "cpu");
        int context = oneB ? contextCap(value, 0) : contextCap(value, 4096);
        TaiModelProfile profile = new TaiModelProfile(accelerators, oneB ? 1024 : 2048, 64, 0.95d,
            oneB ? 0.9d : 1.0d, null, SOURCE, TaiModelProfile.THINKING_TOGGLEABLE, null, null, context,
            TaiModelProfile.THINKING_SWITCH_TEMPLATE_BOOLEAN);
        return new Match(FAMILY_MINICPM5, profile,
            caps(TaiModelSpec.CAPABILITY_TEXT_CHAT, "reasoning", TaiModelSpec.CAPABILITY_LLM_THINKING, "multilingual"),
            oneB ? null : "0.16.0");
    }

    // ---- MedGemma ----

    /**
     * litert-community/MedGemma-1.5-4B-IT ({@code medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm},
     * {@code ..._q4_block32_vision_ekv2048.litertlm}, {@code ..._q8_gpu_ekv2048.litertlm}).
     * <ul>
     *   <li>Sampling: neither the litert card nor the base card
     *   (https://huggingface.co/google/medgemma-1.5-4b-it) gives any (its examples decode greedily),
     *   and the base generation_config.json is gated, so the Gemma-style defaults stay.</li>
     *   <li>Window 2048: every file's {@code _ekv2048} ("exported with cache_length=2048").</li>
     *   <li>Processor: "the text-only variant runs acceptably on CPU"; the undocumented {@code _gpu}
     *   file says GPU in its name.</li>
     *   <li>Thinking: none; Gemma 3 based and the cards describe no reasoning mode.</li>
     * </ul>
     */
    @NonNull
    private static Match medGemma(@NonNull String value) {
        boolean gpuBuild = gpuBuild(value);
        int context = contextCap(value, 2048);
        TaiModelProfile profile = new TaiModelProfile(
            gpuBuild ? Arrays.asList("gpu", "cpu") : Arrays.asList("cpu", "gpu"),
            TaiModelProfile.sensibleOutputCap(context), 64, 0.95d, 1.0d, null, SOURCE,
            TaiModelProfile.THINKING_NONE, null, null, context);
        LinkedHashSet<String> capabilities = caps(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        if (value.contains("_vision")) capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        return new Match(FAMILY_MEDGEMMA, profile, capabilities, null);
    }

    // ---- FunctionGemma ----

    /**
     * litert-community/functiongemma-270m-ft-mobile-actions ({@code mobile_actions_q8_ekv1024.litertlm}
     * and the Tensor G5/G6 builds), functiongemma-270m-ft-tiny-garden
     * ({@code tiny_garden_q8_ekv1024.litertlm}) and functiongemma-mobile-actions_q8_ekv1024.litertlm
     * ({@code mobile-actions_q8_ekv1024.litertlm}).
     * <ul>
     *   <li>Sampling and processor: the values Edge Gallery's allowlist gives the same
     *   MobileActions/TinyGarden files (see {@link TaiModelProfile#forModel}): CPU, temperature 0,
     *   top-k 64, top-p 0.95, 6 GB. The card benchmarks CPU only; top-k 64 and top-p 0.95 also match
     *   https://huggingface.co/litert-community/FunctionGemma_270M_Mobile_Actions/resolve/main/generation_config.json.</li>
     *   <li>Window 1024: {@code _ekv1024}, the card's "Context length 1024".</li>
     *   <li>Capabilities: chat plus tool use, which is the whole point of the family.</li>
     * </ul>
     */
    @NonNull
    private static Match functionGemma(@NonNull String value) {
        int context = contextCap(value, 1024);
        TaiModelProfile profile = new TaiModelProfile(Collections.singletonList("cpu"), context, 64, 0.95d, 0.0d, 6,
            SOURCE, TaiModelProfile.THINKING_NONE, null, null, context);
        return new Match(FAMILY_FUNCTIONGEMMA, profile,
            caps(TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_TOOL_USE), null);
    }

    // ---- EmbeddingGemma ----

    /**
     * litert-community/embeddinggemma-300m ({@code embeddinggemma-300M_seq256|512|1024|2048_mixed-precision.tflite}
     * plus SoC-compiled copies). Embeddings only: the embedding runtime serves it on demand and
     * reads the repository's {@code sentencepiece.model}, which the downloader fetches beside the
     * graph. The output is 768-dimensional, truncatable to 512/256/128 (Matryoshka), per
     * https://huggingface.co/google/embeddinggemma-300m. The window is the file's {@code seqNNNN};
     * sampling does not apply, so the generic defaults stay.
     */
    @NonNull
    private static Match embeddingGemma(@NonNull String value) {
        Integer seq = firstNumber(SEQ_TOKEN, value);
        TaiModelProfile profile = new TaiModelProfile(Collections.singletonList("cpu"), 1024, 64, 0.95d, 1.0d, null,
            SOURCE, TaiModelProfile.THINKING_NONE, null, null, seq == null ? 0 : seq);
        return new Match(FAMILY_EMBEDDINGGEMMA, profile, caps(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS), null);
    }

    /**
     * The {@code seqNNNN} window in an embedding graph's file name, or 0. The file fixes the
     * input length, so this beats whatever window a catalogue entry declared.
     */
    static int sequenceWindowOf(@Nullable String fileName) {
        if (fileName == null) return 0;
        Integer seq = firstNumber(SEQ_TOKEN, fileName.toLowerCase(java.util.Locale.ROOT));
        return seq == null ? 0 : seq;
    }

    /** Matches the {@code seqNNNN} token anywhere in a file name, for sibling-window comparisons. */
    private static final Pattern SEQ_ANY = Pattern.compile("seq\\d+");

    /**
     * {@code fileName} with its {@code seqNNNN} token replaced by a fixed placeholder, so two
     * files whose names are identical apart from the window number normalise to the same string.
     * Used only to compare siblings; {@link #sequenceWindowOf} still reads the real number.
     */
    @NonNull
    private static String seqNormalizedName(@NonNull String fileName) {
        return SEQ_ANY.matcher(fileName.toLowerCase(Locale.ROOT)).replaceFirst("seqn");
    }

    /**
     * The window graphs installed beside {@code primaryFile} (an EmbeddingGemma-style
     * {@code ..._seqNNNN_...} model): every file in the same directory whose name is identical to
     * {@code primaryFile}'s apart from the {@code seqNNNN} number — same {@code _mixed-precision}
     * marker, same chip variant suffix (or none on either side) — keyed by window, ascending. The
     * primary itself is always included when its own name carries a window. A chip-specific build
     * ({@code .google_tensor_g5.tflite}) never matches a portable one: their normalised names
     * differ by that suffix, so they are never mixed into the same routing table.
     */
    @NonNull
    static Map<Integer, File> siblingWindowGraphs(@NonNull File primaryFile) {
        TreeMap<Integer, File> result = new TreeMap<>();
        int primaryWindow = sequenceWindowOf(primaryFile.getName());
        if (primaryWindow > 0) result.put(primaryWindow, primaryFile);
        File dir = primaryFile.getParentFile();
        if (dir == null) return result;
        File[] candidates = dir.listFiles();
        if (candidates == null) return result;
        String primaryNormalized = seqNormalizedName(primaryFile.getName());
        for (File candidate : candidates) {
            if (!candidate.isFile() || candidate.equals(primaryFile)) continue;
            int window = sequenceWindowOf(candidate.getName());
            if (window <= 0) continue;
            if (!seqNormalizedName(candidate.getName()).equals(primaryNormalized)) continue;
            result.put(window, candidate);
        }
        return result;
    }

    // ---- builds tied to one chip ----

    // "google.tensor_g5", "_Google_Tensor_G5", "qualcomm.sm8650", "mediatek.mt6991": builds compiled
    // ahead of time for one phone chip's NPU. TAI runs LiteRT on the CPU and GPU only.
    private static final Pattern SOC_TOKEN =
        Pattern.compile("(?:^|[._-])(?:google[._-])?(tensor[._-]?g\\d+|sm\\d{4}|mt\\d{4})(?=[._-]|$)");

    /**
     * The chip a file was compiled for, normalised ("tensorg5", "sm8650", "mt6991"), or {@code null}
     * for a portable build.
     */
    @Nullable
    public static String socTarget(@Nullable String fileName) {
        if (fileName == null) return null;
        String name = fileName.toLowerCase(Locale.ROOT);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        Matcher matcher = SOC_TOKEN.matcher(name);
        return matcher.find() ? normalizeSoc(matcher.group(1)) : null;
    }

    /**
     * Whether a chip-specific build was made for this phone: {@code deviceSoc} is
     * {@code Build.SOC_MODEL} ("SM8650", "MT6991", "Tensor G5"); an unknown chip matches nothing.
     */
    public static boolean socMatches(@Nullable String target, @Nullable String deviceSoc) {
        if (target == null) return true;
        String device = normalizeSoc(deviceSoc);
        return !device.isEmpty() && device.contains(target);
    }

    /** A build named for the GPU ({@code _gpu_ekv2048}, {@code _gpu_opt}), which a CPU load should not get first. */
    public static boolean gpuBuild(@Nullable String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return GPU_TOKEN.matcher(name).find();
    }

    /**
     * A file the variant picker should not pre-select while a portable build exists: one tied to a
     * chip's NPU, one built for the GPU (on a phone whose GPU cannot take it, the load fails), or a
     * web (WebGPU) build.
     */
    public static boolean deprioritised(@Nullable String fileName) {
        return socTarget(fileName) != null || gpuBuild(fileName) || webBuild(fileName);
    }

    /** A build named for the web ({@code -web}): a WebGPU bundle, never the phone's default. */
    public static boolean webBuild(@Nullable String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return WEB_TOKEN.matcher(name).find();
    }

    /**
     * litert-community's Gemma 4 {@code -gpu} and {@code -web} files. Their headers (read 2026-09-27)
     * hold one {@code tf_lite_artisan_text_decoder} section with {@code backend_constraint=gpu_artisan}:
     * text-only, run on LiteRT-LM's hand-written GPU_ARTISAN path at FP16 activations, not the ML
     * Drift delegate the standard file uses. On an Adreno 730 both produced corrupted text, and
     * Google's AI Edge Gallery allowlist ships only the standard files, so the picker says so.
     */
    public static boolean artisanBundle(@Nullable String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.matches("gemma-4-e[24]b-it-(gpu|web)\\.litertlm");
    }

    /** litert-community's Granite 4.2 files, from any of a model id, a repository or a file name. */
    public static boolean granite42(@Nullable String identity) {
        return identity != null && identity.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains("granite42");
    }

    /**
     * Granite 4.2's chat template reads {@code {%- if enable_thinking | default(true) -%}}: with
     * no key it thinks, every token lands in the thought channel and the reply is empty. So its
     * profile switches thinking (off unless asked) with a real boolean, which sends
     * {@code enable_thinking=false} (see {@link TaiModelProfile#THINKING_SWITCH_TEMPLATE_BOOLEAN}).
     * Returns {@code profile} untouched for any other model, or when it already has a thinking mode.
     * Used for the profile an import stores and for the ones already installed (see
     * {@link TaiModelProfile#forModel}).
     */
    @NonNull
    public static TaiModelProfile withGraniteThinkingSwitch(@NonNull TaiModelProfile profile, @Nullable String identity) {
        if (!granite42(identity) || !TaiModelProfile.THINKING_NONE.equals(profile.thinkingMode)) return profile;
        return profile.withThinking(TaiModelProfile.THINKING_TOGGLEABLE, TaiModelProfile.THINKING_SWITCH_TEMPLATE_BOOLEAN);
    }

    private static final Pattern GPU_TOKEN = Pattern.compile("(^|[._-])gpu(?=[._-]|$)");
    private static final Pattern WEB_TOKEN = Pattern.compile("(^|[-_.])web(?=[-_.]|$)");
    private static final Pattern SEQ_TOKEN = Pattern.compile("(?:^|[._-])seq(\\d+)(?=[._-]|$)");

    @NonNull
    private static String normalizeSoc(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * The window a build may use: its file name's {@code ekvNNNN} when present (the KV cache the
     * file was exported with), capped by {@code cardContext} when the card gives one.
     */
    private static int contextCap(@NonNull String value, int cardContext) {
        Integer ekv = TaiModelProfile.extractEkvContext(value);
        if (ekv == null) return cardContext;
        return cardContext > 0 ? Math.min(ekv, cardContext) : ekv;
    }

    @Nullable
    private static Integer firstNumber(@NonNull Pattern pattern, @NonNull String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) return null;
        try {
            int number = Integer.parseInt(matcher.group(1));
            return number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static LinkedHashSet<String> caps(@NonNull String... capabilities) {
        return new LinkedHashSet<>(Arrays.asList(capabilities));
    }
}
