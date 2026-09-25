package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.ai.TaiHuggingFace;
import com.termux.ai.TaiModelImporter;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The names the import flow pre-fills from what the user gave it: a link or a file name becomes
 * "Gemma 3 1B IT" for the card and {@code gemma-3-1b-it} for the store. Pure string work, no
 * Android, so the guesses are unit-tested.
 */
final class TaiImportNames {
    /** Suffixes a published file name carries after the model's name: quantization, cache size, runtime. */
    private static final String[] VARIANT_MARKERS = {
        "_multi-prefill", "_q4", "_q8", "_int4", "_int8", "_fp16", "_bf16", "_f16", "_seq", "_ekv",
        "_mixed-precision", "-q4", "-q8", "-int4", "-int8", "-litert-lm", "-litertlm", "-mnn", "_mnn",
        "-litert", "_litert"
    };

    private TaiImportNames() {
    }

    /** The name shown on the card and in the model list, from a link or a file name. */
    @NonNull
    static String displayName(@Nullable String source) {
        return humanize(base(source));
    }

    /** The store id for the same source; the display name's raw form, sanitised. */
    @NonNull
    static String modelId(@Nullable String source) {
        return TaiModelImporter.sanitizeModelId(base(source)).toLowerCase(Locale.ROOT);
    }

    /**
     * What a repository publishes as a file name, in words: "gemma-3-1b-it_q4_ekv2048.litertlm"
     * reads "Gemma 3 1B IT", and the part after the name says which build it is.
     */
    @NonNull
    static String humanize(@NonNull String raw) {
        String[] words = raw.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            String lower = word.toLowerCase(Locale.ROOT);
            if (lower.matches("\\d+(\\.\\d+)?[bm]") || lower.matches("e\\d+b") || lower.equals("it")
                || lower.equals("vl") || lower.equals("sft") || lower.equals("moe")) {
                out.append(word.toUpperCase(Locale.ROOT));
            } else {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /**
     * What one of a repository's several files is, in plain words, or {@code 0} when its name
     * does not say: {@code f32} builds are full size (slowest, most memory), {@code fp16} half
     * size, {@code int8}/{@code q8} compact, {@code int4}/{@code q4} the smallest and a bit less
     * accurate, {@code web} builds are for browsers. Absolute, not "smaller than the other one":
     * a q8 file is the small one next to f32 and the large one next to q4.
     */
    static int variantHint(@Nullable String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (WEB.matcher(lower).find()) return R.string.termux_ai_import_variant_web;
        if (SMALLEST.matcher(lower).find()) return R.string.termux_ai_import_variant_smallest;
        if (COMPACT.matcher(lower).find()) return R.string.termux_ai_import_variant_compact;
        if (HALF.matcher(lower).find()) return R.string.termux_ai_import_variant_half;
        if (FULL.matcher(lower).find()) return R.string.termux_ai_import_variant_full;
        return 0;
    }

    /** A full-precision build: several times the memory of a compact one, for little gain on a phone. */
    static boolean isFullPrecision(@Nullable String fileName) {
        return fileName != null && FULL.matcher(fileName.toLowerCase(Locale.ROOT)).find();
    }

    // Build tokens sit between separators; "seq4096" must not read as a q4 build.
    private static final Pattern WEB = Pattern.compile("(^|[-_.])web(?=[-_.]|$)");
    private static final Pattern SMALLEST = Pattern.compile("(^|[-_.])(q4|int4|4bit)(?=[-_.]|$)");
    private static final Pattern COMPACT = Pattern.compile("(^|[-_.])(q8|int8|8bit|i8)(?=[-_.]|$)");
    private static final Pattern HALF = Pattern.compile("(^|[-_.])(fp16|f16|bf16)(?=[-_.]|$)");
    private static final Pattern FULL = Pattern.compile("(^|[-_.])(f32|fp32|float32)(?=[-_.]|$)");

    /** The web page for a Hugging Face link, so a gated model's terms are one tap away; else the link itself. */
    @NonNull
    static String modelPageUrl(@NonNull String url) {
        TaiHuggingFace parsed = TaiHuggingFace.parse(url);
        return parsed == null ? url : "https://huggingface.co/" + parsed.repository;
    }

    /**
     * The identity behind a source: a Hugging Face link's repository name (never its file, which
     * for MNN is always {@code config.json}), another link's last path segment, or a file name —
     * without extension or build suffix.
     */
    @NonNull
    private static String base(@Nullable String source) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) return "";
        TaiHuggingFace parsed = value.startsWith("https://") ? TaiHuggingFace.parse(value) : null;
        if (parsed != null) {
            int slash = parsed.repository.indexOf('/');
            value = slash >= 0 ? parsed.repository.substring(slash + 1) : parsed.repository;
        } else if (value.startsWith("https://") || value.startsWith("http://")) {
            int query = value.indexOf('?');
            if (query >= 0) value = value.substring(0, query);
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            value = value.substring(value.lastIndexOf('/') + 1);
        }
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        value = TaiModelImporter.stripModelExtension(value);
        if (value.toLowerCase(Locale.ROOT).equals("config.json")) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        int cut = value.length();
        for (String marker : VARIANT_MARKERS) {
            int at = lower.indexOf(marker);
            if (at > 0 && at < cut) cut = at;
        }
        return value.substring(0, cut);
    }
}
