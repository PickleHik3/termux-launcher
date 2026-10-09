package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.ai.TaiImportProfiles;
import com.termux.ai.TaiModelSpec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The words the import flow shows about a model, built only from things it can point at: the
 * file name, the repository's Hugging Face metadata, the card-sourced family table
 * ({@link TaiImportProfiles}), and measurements of this phone. Nothing here judges a build as
 * "compact" or "a good balance": a file named {@code granite-4.0-h-350m_int8_gpu.litertlm}
 * reads "Granite 4.0 H 350M · int8 · GPU build" because those are the words in its name.
 * Comparative words come only from the model card, through {@link TaiImportCard}, quoted.
 *
 * <p>Pure string work behind a {@link Words} lookup, so it is unit-tested without Android.
 */
final class TaiImportFacts {
    /** A string resource lookup; {@code Context::getString} in the app, a table in tests. */
    interface Words {
        @NonNull String get(int resId, Object... args);
    }

    private static final double GIB = 1024d * 1024d * 1024d;

    private TaiImportFacts() {
    }

    // ---- the file name, read token by token ----

    /** What a published file name says about the build, and nothing more. */
    static final class FileFacts {
        /** The model's name words from the file, humanized ("Qwen3.5 2B VL"); "" when the name has none. */
        @NonNull final String name;
        /**
         * The precision and packing words exactly as the file spells them, one entry per run of
         * such words ("int8", "q4_block32", "mixed-precision", "wi4c_wi8_afp32").
         */
        @NonNull final List<String> builds;
        /** Words after the build that the reader does not know; shown as the file spells them. */
        @NonNull final List<String> others;
        final boolean gpu;
        final boolean web;
        /** A {@code vl} or {@code vision} word anywhere in the file name. */
        final boolean vision;
        /** Whether that vision word sits after the model's name (MedGemma's {@code _vision_}), so the title says it. */
        final boolean visionInBuild;
        /** An {@code embedding} word in the file name. */
        final boolean embedding;
        /** The chip a build was compiled for, as the file spells it ("Google Tensor G5"), or {@code null}. */
        @Nullable final String chip;
        /** The KV cache the file was exported with ({@code ekvNNNN}); {@code 0} when it says none. */
        final int kvTokens;
        /** The input length an encoder was exported with ({@code seqNNNN}); {@code 0} when it says none. */
        final int seqTokens;
        /** The lower-case extension ("litertlm", "task", "tflite", "json"), or "". */
        @NonNull final String extension;
        /** The file name from its first build word on, lower-case ("int8_gpu"), for matching card text. */
        @NonNull final String buildSuffix;

        FileFacts(@NonNull String name, @NonNull List<String> builds, @NonNull List<String> others, boolean gpu,
                  boolean web, boolean vision, boolean visionInBuild, boolean embedding, @Nullable String chip,
                  int kvTokens, int seqTokens, @NonNull String extension, @NonNull String buildSuffix) {
            this.name = name;
            this.builds = builds;
            this.others = others;
            this.gpu = gpu;
            this.web = web;
            this.vision = vision;
            this.visionInBuild = visionInBuild;
            this.embedding = embedding;
            this.chip = chip;
            this.kvTokens = kvTokens;
            this.seqTokens = seqTokens;
            this.extension = extension;
            this.buildSuffix = buildSuffix;
        }
    }

    // Precision and packing words: q4, int8, i8, fp16, f32, bf16, float32, 4bit, block32, and the
    // MiniCPM weight/activation spellings wi4c, wi8, wi4b32, afp32.
    private static final Pattern BUILD_WORD = Pattern.compile(
        "(q|int|i)\\d+|fp\\d+|f\\d+|bf16|float\\d+|\\d+bit|block\\d+|wi\\d+[a-z]*\\d*|afp\\d+|mixed|precision|dynamic");
    private static final Pattern EKV = Pattern.compile("ekv(\\d+)");
    private static final Pattern SEQ = Pattern.compile("seq(\\d+)");
    private static final Pattern CHIP_ID = Pattern.compile("sm\\d{4}|mt\\d{4}|qcs\\d+|tensor");
    private static final Pattern CHIP_VENDOR = Pattern.compile("google|qualcomm|mediatek|samsung|exynos|intel");
    // Words that name the container or the export pipeline, not the build: never shown.
    private static final Pattern SILENT = Pattern.compile("litert|lm|litertlm|mnn|multi|prefill|seq|opt");
    private static final Pattern TOKEN = Pattern.compile("[^-_]+");
    private static final String[] EXTENSIONS = {".litertlm", ".task", ".tflite", ".json"};

    @NonNull
    static FileFacts parse(@Nullable String fileName) {
        String base = fileName == null ? "" : fileName.trim().replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String extension = "";
        String lowerBase = base.toLowerCase(Locale.ROOT);
        for (String candidate : EXTENSIONS) {
            if (lowerBase.endsWith(candidate)) {
                extension = candidate.substring(1);
                base = base.substring(0, base.length() - candidate.length());
                break;
            }
        }
        // An MNN package is named by its folder or repository; "config" is no model's name.
        if ("json".equals(extension) && base.equalsIgnoreCase("config")) base = "";
        // Chip suffixes are dotted in some files (".qualcomm.sm8650.tflite"); dots elsewhere are
        // part of the name ("Qwen3.5", "granite-4.0"), so only these become separators.
        base = base.replaceAll("(?i)\\.(google|qualcomm|mediatek|samsung|intel)\\.", "_$1_");

        List<String> tokens = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        java.util.regex.Matcher tokenMatcher = TOKEN.matcher(base);
        while (tokenMatcher.find()) {
            tokens.add(tokenMatcher.group());
            starts.add(tokenMatcher.start());
        }
        int buildStart = tokens.size();
        for (int i = 1; i < tokens.size(); i++) {
            if (isBuildToken(tokens.get(i).toLowerCase(Locale.ROOT))) {
                buildStart = i;
                break;
            }
        }
        boolean vision = false;
        boolean visionInBuild = false;
        boolean embedding = false;
        for (int i = 0; i < tokens.size(); i++) {
            String lower = tokens.get(i).toLowerCase(Locale.ROOT);
            if (lower.equals("vl") || lower.equals("vision")) {
                vision = true;
                if (i >= buildStart) visionInBuild = true;
            }
            if (lower.startsWith("embedding") || lower.equals("embed")) embedding = true;
        }
        StringBuilder nameRaw = new StringBuilder();
        for (int i = 0; i < buildStart; i++) {
            if (nameRaw.length() > 0) nameRaw.append('-');
            nameRaw.append(tokens.get(i));
        }

        List<String> builds = new ArrayList<>();
        List<String> others = new ArrayList<>();
        boolean gpu = false;
        boolean web = false;
        int kv = 0;
        int seq = 0;
        StringBuilder chip = null;
        int runStart = -1;
        int runEnd = -1;
        for (int i = buildStart; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String lower = token.toLowerCase(Locale.ROOT);
            boolean build = BUILD_WORD.matcher(lower).matches();
            if (build) {
                if (runStart < 0) runStart = starts.get(i);
                runEnd = starts.get(i) + token.length();
                continue;
            }
            if (runStart >= 0) {
                builds.add(base.substring(runStart, runEnd));
                runStart = -1;
            }
            if (chip != null && !EKV.matcher(lower).matches() && !SEQ.matcher(lower).matches()
                && !SILENT.matcher(lower).matches() && !lower.equals("gpu") && !lower.equals("web")) {
                chip.append(' ').append(chipWord(token));
                continue;
            }
            if (CHIP_VENDOR.matcher(lower).matches() || CHIP_ID.matcher(lower).matches()) {
                chip = new StringBuilder(chipWord(token));
            } else if (lower.equals("gpu")) {
                gpu = true;
            } else if (lower.equals("web")) {
                web = true;
            } else if (EKV.matcher(lower).matches()) {
                kv = number(lower.substring(3));
            } else if (SEQ.matcher(lower).matches()) {
                seq = number(lower.substring(3));
            } else if (lower.equals("vl") || lower.equals("vision") || SILENT.matcher(lower).matches()) {
                // The vision word is reported through visionInBuild; the others name no build.
            } else {
                others.add(token);
            }
        }
        if (runStart >= 0) builds.add(base.substring(runStart, runEnd));
        String suffix = buildStart < tokens.size() ? base.substring(starts.get(buildStart)).toLowerCase(Locale.ROOT) : "";
        return new FileFacts(TaiImportNames.humanize(nameRaw.toString()), builds, others, gpu, web, vision,
            visionInBuild, embedding, chip == null ? null : chip.toString(), kv, seq, extension, suffix);
    }

    private static boolean isBuildToken(@NonNull String lower) {
        return BUILD_WORD.matcher(lower).matches() || EKV.matcher(lower).matches() || SEQ.matcher(lower).matches()
            || CHIP_VENDOR.matcher(lower).matches() || CHIP_ID.matcher(lower).matches()
            || SILENT.matcher(lower).matches() || lower.equals("gpu") || lower.equals("web");
    }

    /** A chip word as a person writes it: vendors capitalised, chip ids upper-case ("MediaTek MT6991"). */
    @NonNull
    private static String chipWord(@NonNull String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "google": return "Google";
            case "qualcomm": return "Qualcomm";
            case "mediatek": return "MediaTek";
            case "samsung": return "Samsung";
            case "exynos": return "Exynos";
            case "intel": return "Intel";
            case "tensor": return "Tensor";
            default:
                if (lower.matches("[a-z]+\\d+") || lower.matches("[a-z]{1,3}")) return token.toUpperCase(Locale.ROOT);
                return Character.toUpperCase(token.charAt(0)) + token.substring(1);
        }
    }

    private static int number(@NonNull String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- titles ----

    /**
     * The model's name for one file of a repository: the file's own name words when they say more
     * than the repository's (litert-community/Qwen3.5-2B publishes "Qwen3.5-2B-VL_int8"), else the
     * repository's name (FunctionGemma's "mobile_actions_q8_ekv1024" is in
     * functiongemma-270m-ft-mobile-actions, and MiniCPM5-2B's "minicpm_wi8_afp32" in MiniCPM5-2B).
     */
    @NonNull
    static String modelName(@Nullable String repositoryName, @Nullable String fileName) {
        String repo = repositoryName == null ? "" : repositoryName.trim();
        String file = parse(fileName).name;
        if (repo.isEmpty()) return file;
        if (file.isEmpty()) return repo;
        String r = alnum(repo);
        String f = alnum(file);
        if (f.contains(r)) return file;
        if (r.contains(f)) return repo;
        return file;
    }

    /**
     * One picker row's title: the model's name, then each fact the file name states, in the order
     * precision, target (GPU, web, chip), vision, window. "Granite 4.0 H 350M · int8 · GPU build",
     * "Gemma 4 E2B IT · for Google Tensor G5", "SmolLM3 3B · q4_block32 · 4096-token context".
     */
    @NonNull
    static String variantTitle(@NonNull Words words, @Nullable String repositoryName, @Nullable String fileName) {
        FileFacts facts = parse(fileName);
        List<String> parts = new ArrayList<>();
        String name = modelName(repositoryName, fileName);
        if (!name.isEmpty()) parts.add(name);
        parts.addAll(facts.builds);
        if (facts.gpu) parts.add(words.get(R.string.termux_ai_import_variant_gpu_build));
        if (facts.web) parts.add(words.get(R.string.termux_ai_import_variant_web_build));
        if (facts.chip != null) parts.add(words.get(R.string.termux_ai_import_variant_chip_build, facts.chip));
        if (facts.visionInBuild) parts.add("vision");
        parts.addAll(facts.others);
        if (facts.kvTokens > 0) parts.add(words.get(R.string.termux_ai_import_variant_context, facts.kvTokens));
        if (facts.seqTokens > 0) parts.add(words.get(R.string.termux_ai_import_variant_input, facts.seqTokens));
        if (parts.isEmpty()) parts.add(fileName == null ? "" : fileName);
        return join(parts);
    }

    // ---- this phone ----

    /**
     * The file against this phone, as measurements: "3.7 GB file · this phone has 12 GB RAM, about
     * 6.3 GB free now". {@code ramBytes} is the RAM class the phone is sold with; {@code freeBytes}
     * Android's available memory at the moment of asking.
     */
    @NonNull
    static String fitLine(@NonNull Words words, long sizeBytes, long ramBytes, long freeBytes) {
        String file = sizeBytes > 0L ? words.get(R.string.termux_ai_import_fit_file, TaiImportMessages.formatBytes(sizeBytes))
            : words.get(R.string.termux_ai_import_fit_file_unknown);
        String phone;
        if (ramBytes <= 0L) phone = words.get(R.string.termux_ai_import_fit_phone_unknown);
        else if (freeBytes > 0L) phone = words.get(R.string.termux_ai_import_fit_phone_free, ramGb(ramBytes),
            TaiImportMessages.formatBytes(freeBytes));
        else phone = words.get(R.string.termux_ai_import_fit_phone, ramGb(ramBytes));
        return file + " · " + phone;
    }

    /** The phone's RAM class in whole GB, as it is sold ("12 GB"). */
    static int ramGb(long ramBytes) {
        return ramBytes <= 0L ? 0 : (int) Math.round(ramBytes / GIB);
    }

    /** A file larger than all of this phone's RAM: the one size fact worth a warning on a picker row. */
    static boolean largerThanRam(long sizeBytes, long ramBytes) {
        return sizeBytes > 0L && ramBytes > 0L && sizeBytes > ramBytes;
    }

    // ---- what it can do ----

    /**
     * The capabilities something other than the model's name vouches for, each with the string
     * resource naming its source: the card-sourced family table, the file's format, a vision or
     * embedding word in the file name, and the repository's {@code pipeline_tag} and tags.
     * Repository-wide tags say what the repository holds, not what each file does
     * (litert-community/Qwen3.5-2B is tagged {@code image-text-to-text} and also publishes a
     * text-only file), so an image claim from them only counts for a repository's sole file.
     */
    @NonNull
    static LinkedHashMap<String, Integer> groundedCapabilities(@Nullable String identity, @Nullable String fileName,
                                                               @Nullable JSONObject modelFacts, boolean soleFile) {
        LinkedHashMap<String, Integer> grounded = new LinkedHashMap<>();
        TaiImportProfiles.Match family = TaiImportProfiles.match(identity);
        if (family != null) {
            for (String capability : family.capabilities) grounded.put(capability, R.string.termux_ai_import_source_card);
        }
        FileFacts facts = parse(fileName);
        if (facts.extension.equals("tflite")) {
            // The app serves a bare .tflite with its embedding runtime only; a fact about TAI, not a guess.
            putIfAbsent(grounded, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS, R.string.termux_ai_import_source_format);
            return grounded;
        }
        if (facts.embedding) putIfAbsent(grounded, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS, R.string.termux_ai_import_source_file_name);
        else if (facts.extension.equals("litertlm") || facts.extension.equals("task")) {
            // A LiteRT-LM bundle carries a language model and its chat template.
            putIfAbsent(grounded, TaiModelSpec.CAPABILITY_TEXT_CHAT, R.string.termux_ai_import_source_format);
        }
        if (facts.vision) putIfAbsent(grounded, TaiModelSpec.CAPABILITY_IMAGE_INPUT, R.string.termux_ai_import_source_file_name);
        if (modelFacts != null) {
            String pipeline = modelFacts.optString("pipelineTag", "");
            List<String> tags = new ArrayList<>();
            JSONArray tagArray = modelFacts.optJSONArray("tags");
            for (int i = 0; tagArray != null && i < tagArray.length(); i++) tags.add(tagArray.optString(i, ""));
            int source = R.string.termux_ai_import_source_tags;
            if (pipeline.equals("feature-extraction") || pipeline.equals("sentence-similarity")) {
                putIfAbsent(grounded, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS, source);
            } else if (pipeline.equals("text-generation") || pipeline.equals("text2text-generation")
                || pipeline.equals("image-text-to-text") || pipeline.equals("any-to-any")) {
                if (!grounded.containsKey(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)) {
                    putIfAbsent(grounded, TaiModelSpec.CAPABILITY_TEXT_CHAT, source);
                }
            }
            boolean imageTag = pipeline.equals("image-text-to-text") || tags.contains("image-text-to-text")
                || tags.contains("vlm");
            if (imageTag && soleFile) putIfAbsent(grounded, TaiModelSpec.CAPABILITY_IMAGE_INPUT, source);
            if (tags.contains("function-calling") || tags.contains("tool-use")) {
                putIfAbsent(grounded, TaiModelSpec.CAPABILITY_TOOL_USE, source);
            }
        }
        return grounded;
    }

    private static void putIfAbsent(@NonNull LinkedHashMap<String, Integer> map, @NonNull String key, int source) {
        if (!map.containsKey(key)) map.put(key, source);
    }

    /**
     * The context window a source states and which source it is: the file name's {@code ekvNNNN}
     * or {@code seqNNNN}, else the family table's card-sourced window. {@code null} when neither
     * says; the import default is not a fact about the model.
     */
    @Nullable
    static int[] contextWindow(@Nullable String identity, @Nullable String fileName) {
        FileFacts facts = parse(fileName);
        if (facts.kvTokens > 0) return new int[]{facts.kvTokens, R.string.termux_ai_import_source_file_name};
        if (facts.seqTokens > 0) return new int[]{facts.seqTokens, R.string.termux_ai_import_source_file_name};
        TaiImportProfiles.Match family = TaiImportProfiles.match(identity);
        if (family != null && family.profile.maxContextTokens > 0) {
            return new int[]{family.profile.maxContextTokens, R.string.termux_ai_import_source_card};
        }
        return null;
    }

    @NonNull
    private static String alnum(@NonNull String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @NonNull
    static String join(@NonNull List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (out.length() > 0) out.append(" · ");
            out.append(part);
        }
        return out.toString();
    }
}
