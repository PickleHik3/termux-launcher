package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONObject;

/**
 * Recognises an MNN text-to-image package directory (Stable Diffusion 1.5, Taiyi, Sana) from the
 * files MNN 3.6.1's Diffusion engine actually opens, and validates that every required file is
 * present and non-empty. Pure file inspection: nothing is loaded.
 *
 * <p>File names come from the engine source: {@code StableDiffusion::load} opens
 * {@code text_encoder.mnn}, {@code unet.mnn}, {@code vae_decoder.mnn} and {@code tokenizer.mtok};
 * {@code SanaDiffusion::load} opens {@code connector.mnn}, {@code projector.mnn},
 * {@code transformer.mnn} and {@code vae_decoder.mnn} ({@code vae_encoder.mnn} lazily, for
 * image-to-image only), and {@code SanaLlm} reads {@code llm/config.json} and
 * {@code llm/meta_queries.mnn} beside the LLM graph. Stable Diffusion and Taiyi share one file
 * layout (only the tokenizer contents and special-token ids differ), so the two cannot be told
 * apart from files: callers pass a type hint and the default is Stable Diffusion 1.5.
 */
final class TaiDiffusionPackage {
    static final int TYPE_SD15 = 0;
    static final int TYPE_TAIYI = 1;
    static final int TYPE_SANA = 2;
    /** No caller preference; the files decide (Sana) or Stable Diffusion 1.5 is assumed. */
    static final int TYPE_AUTO = -1;

    static final String FAIL_NOT_A_DIRECTORY = "not_a_directory";
    static final String FAIL_UNRECOGNISED = "unrecognised_package";
    static final String FAIL_TYPE_MISMATCH = "model_type_mismatch";
    static final String FAIL_MISSING_FILE = "missing_file";
    static final String FAIL_TOKENIZER_MTOK_MISSING = "tokenizer_mtok_missing";

    /** Outcome of {@link #inspect}; {@code failure} is null when the package is usable. */
    static final class Result {
        final int type;
        final long totalBytes;
        /**
         * The largest set of graphs alive at once: everything for Stable Diffusion/Taiyi; for Sana the
         * larger of the prompt LLM and the diffusion graphs, because the engine frees one before loading
         * the other. What the admission budget multiplies.
         */
        final long peakBytes;
        /** Sana only: image-to-image needs {@code vae_encoder.mnn}. Always false for SD/Taiyi. */
        final boolean supportsImageInput;
        @Nullable final String failure;
        @NonNull final String message;
        @NonNull final List<String> missing;

        Result(int type, long totalBytes, long peakBytes, boolean supportsImageInput, @Nullable String failure,
               @NonNull String message, @NonNull List<String> missing) {
            this.type = type;
            this.totalBytes = totalBytes;
            this.peakBytes = peakBytes;
            this.supportsImageInput = supportsImageInput;
            this.failure = failure;
            this.message = message;
            this.missing = missing;
        }

        boolean ok() {
            return failure == null;
        }
    }

    private TaiDiffusionPackage() {}

    /** Maps a request/CLI/spec type name to a TYPE constant, or {@link #TYPE_AUTO} for blank; -2 if unknown. */
    static int parseType(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return TYPE_AUTO;
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "sd15": case "sd1.5": case "sd_1_5": case "stable-diffusion-1.5": return TYPE_SD15;
            case "taiyi": return TYPE_TAIYI;
            case "sana": return TYPE_SANA;
            default: return -2;
        }
    }

    @NonNull
    static String typeName(int type) {
        switch (type) {
            case TYPE_TAIYI: return "taiyi";
            case TYPE_SANA: return "sana";
            default: return "sd15";
        }
    }

    /** A spec for a package named by path rather than registered: the id and the load-history key. */
    @NonNull
    static TaiModelSpec syntheticSpec(@NonNull String dir, int type, long totalBytes) {
        String name = new File(dir).getName();
        java.util.LinkedHashSet<String> capabilities = new java.util.LinkedHashSet<>();
        capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_GENERATION);
        return new TaiModelSpec("local-image-" + name.replaceAll("[^A-Za-z0-9._-]", "_"), name,
            "image_generation", "local", dir, "unknown", totalBytes, capabilities, false, null,
            TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.FORMAT_MNN, typeName(type), null, 4096, 0, null);
    }

    @NonNull
    static Result inspect(@Nullable File dir, int typeHint) {
        if (dir == null || !dir.isDirectory()) {
            return fail(typeHint, FAIL_NOT_A_DIRECTORY, "The image model folder was not found.", new ArrayList<>());
        }
        boolean sanaShaped = new File(dir, "transformer.mnn").exists() || new File(dir, "connector.mnn").exists()
            || new File(dir, "llm").isDirectory();
        boolean sdShaped = new File(dir, "unet.mnn").exists() || new File(dir, "text_encoder.mnn").exists();
        if (typeHint == TYPE_SANA) {
            if (!sanaShaped) return fail(typeHint, FAIL_TYPE_MISMATCH, "This folder does not look like a Sana image model.", new ArrayList<>());
            return inspectSana(dir);
        }
        if (typeHint == TYPE_SD15 || typeHint == TYPE_TAIYI) {
            if (!sdShaped) return fail(typeHint, FAIL_TYPE_MISMATCH, "This folder does not look like a Stable Diffusion image model.", new ArrayList<>());
            return inspectSd(dir, typeHint);
        }
        if (sanaShaped && !sdShaped) return inspectSana(dir);
        if (sdShaped && !sanaShaped) return inspectSd(dir, TYPE_SD15);
        return fail(TYPE_AUTO, FAIL_UNRECOGNISED, "This folder is not a recognised image model.", new ArrayList<>());
    }

    @NonNull
    private static Result inspectSd(@NonNull File dir, int type) {
        List<String> missing = new ArrayList<>();
        long[] total = {0L};
        for (String name : new String[]{"text_encoder.mnn", "unet.mnn", "vae_decoder.mnn"}) {
            require(dir, name, total, missing);
        }
        // External-weights exports keep the tensors in a sibling file; count it when present.
        for (String name : new String[]{"text_encoder.mnn.weight", "unet.mnn.weight", "vae_decoder.mnn.weight"}) {
            optional(dir, name, total);
        }
        File mtok = new File(dir, "tokenizer.mtok");
        if (mtok.isFile() && mtok.length() > 0L) {
            total[0] += mtok.length();
        } else if (new File(dir, "vocab.json").isFile() && new File(dir, "merges.txt").isFile()) {
            // The published MNN packages ship the raw tokenizer; MNN 3.6.1 only loads tokenizer.mtok.
            return fail(type, FAIL_TOKENIZER_MTOK_MISSING,
                "This image model needs its tokenizer converted first. Re-export it with the MNN diffusion "
                    + "export tool so the folder contains tokenizer.mtok.", missing);
        } else {
            missing.add("tokenizer.mtok");
        }
        if (!missing.isEmpty()) return missingFiles(type, missing);
        return new Result(type, total[0], total[0], false, null, "", missing);
    }

    @NonNull
    private static Result inspectSana(@NonNull File dir) {
        List<String> missing = new ArrayList<>();
        long[] total = {0L};
        for (String name : new String[]{"connector.mnn", "projector.mnn", "transformer.mnn", "vae_decoder.mnn"}) {
            require(dir, name, total, missing);
            optional(dir, name + ".weight", total);
        }
        File llm = new File(dir, "llm");
        File llmConfig = new File(llm, "config.json");
        require(dir, "llm/config.json", total, missing);
        require(dir, "llm/meta_queries.mnn", total, missing);
        optional(dir, "llm/meta_queries.mnn.weight", total);
        String llmModel = "llm.mnn";
        String llmWeight = "llm.mnn.weight";
        String tokenizer = null;
        if (llmConfig.isFile() && llmConfig.length() > 0L && llmConfig.length() < 2L * 1024 * 1024) {
            try {
                JSONObject config = new JSONObject(new String(Files.readAllBytes(llmConfig.toPath()), StandardCharsets.UTF_8));
                llmModel = config.optString("llm_model", llmModel);
                llmWeight = config.optString("llm_weight", llmWeight);
                tokenizer = config.optString("tokenizer_file", "");
            } catch (Exception ignored) {
                // An unreadable config is reported by the engine; the defaults still size the package.
            }
        }
        if (!safeRelative(llmModel)) llmModel = "llm.mnn";
        if (!safeRelative(llmWeight)) llmWeight = "llm.mnn.weight";
        require(dir, "llm/" + llmModel, total, missing);
        require(dir, "llm/" + llmWeight, total, missing);
        if (tokenizer == null || tokenizer.isEmpty() || !safeRelative(tokenizer)) {
            tokenizer = new File(llm, "tokenizer.mtok").isFile() ? "tokenizer.mtok" : "tokenizer.txt";
        }
        require(dir, "llm/" + tokenizer, total, missing);
        for (String name : new String[]{"llm_config.json", "llm.mnn.json"}) optional(dir, "llm/" + name, total);
        File encoder = new File(dir, "vae_encoder.mnn");
        boolean image = encoder.isFile() && encoder.length() > 0L;
        if (image) {
            total[0] += encoder.length();
            optional(dir, "vae_encoder.mnn.weight", total);
        }
        if (!missing.isEmpty()) return missingFiles(TYPE_SANA, missing);
        long llmBytes = 0L;
        File[] llmFiles = llm.listFiles();
        if (llmFiles != null) {
            for (File file : llmFiles) if (file.isFile()) llmBytes += file.length();
        }
        long peak = Math.max(llmBytes, total[0] - llmBytes);
        return new Result(TYPE_SANA, total[0], peak, image, null, "", missing);
    }

    private static boolean safeRelative(@NonNull String path) {
        return !path.isEmpty() && !path.startsWith("/") && !path.contains("..") && !path.contains(":");
    }

    private static void require(@NonNull File dir, @NonNull String relative, @NonNull long[] total, @NonNull List<String> missing) {
        File file = new File(dir, relative);
        if (file.isFile() && file.length() > 0L) {
            total[0] += file.length();
        } else {
            missing.add(relative);
        }
    }

    private static void optional(@NonNull File dir, @NonNull String relative, @NonNull long[] total) {
        File file = new File(dir, relative);
        if (file.isFile()) total[0] += file.length();
    }

    @NonNull
    private static Result missingFiles(int type, @NonNull List<String> missing) {
        return fail(type, FAIL_MISSING_FILE, "This image model is incomplete. Missing: " + join(missing) + ".", missing);
    }

    @NonNull
    private static Result fail(int type, @NonNull String failure, @NonNull String message, @NonNull List<String> missing) {
        return new Result(type, 0L, 0L, false, failure, message, missing);
    }

    @NonNull
    private static String join(@NonNull List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        return out.toString();
    }
}
