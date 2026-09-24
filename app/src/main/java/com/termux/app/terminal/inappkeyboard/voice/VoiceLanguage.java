package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which language token a multilingual Whisper graph is forced to. The setting's own value wins;
 * {@code auto} takes the in-app keyboard's current text layout when its id names a language
 * ({@code latn_qwertz_de}, {@code cyrl_jcuken_uk}, {@code arab_pc}), then the first system locale;
 * a language Whisper does not know falls back to English, flagged so the caller can say so once.
 * Codes are the ISO 639-1 (a few 639-2) names of Whisper's {@code <|xx|>} tokens.
 */
public final class VoiceLanguage {

    public static final String AUTO = "auto";
    public static final String FALLBACK = "en";

    /** The 99 languages Whisper carries a token for, by the code its tokenizer spells them with. */
    static final Set<String> WHISPER_LANGUAGES = new HashSet<>(Arrays.asList(
        "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv",
        "it", "id", "hi", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no",
        "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn", "sr",
        "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw",
        "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc", "ka", "be", "tg", "sd", "gu",
        "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl",
        "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue"));

    /**
     * Layout-id tokens that are countries or variants, not languages, and what they speak. "be"
     * (Belgium, Belarusian) and "ch" are ambiguous and deliberately absent: they fall through.
     */
    private static final Map<String, String> LAYOUT_TOKEN_LANGUAGE = new HashMap<>();
    /** Scripts that name one language well enough when the id carries no language token. */
    private static final Map<String, String> SCRIPT_LANGUAGE = new HashMap<>();

    static {
        LAYOUT_TOKEN_LANGUAGE.put("us", "en");
        LAYOUT_TOKEN_LANGUAGE.put("gb", "en");
        LAYOUT_TOKEN_LANGUAGE.put("br", "pt");
        LAYOUT_TOKEN_LANGUAGE.put("jp", "ja");
        LAYOUT_TOKEN_LANGUAGE.put("kr", "ko");
        LAYOUT_TOKEN_LANGUAGE.put("in", "hi");
        LAYOUT_TOKEN_LANGUAGE.put("ir", "fa");
        LAYOUT_TOKEN_LANGUAGE.put("il", "he");
        LAYOUT_TOKEN_LANGUAGE.put("se", "sv");
        LAYOUT_TOKEN_LANGUAGE.put("cz", "cs");
        LAYOUT_TOKEN_LANGUAGE.put("tj", "tg");
        SCRIPT_LANGUAGE.put("arab", "ar");
        SCRIPT_LANGUAGE.put("grek", "el");
        SCRIPT_LANGUAGE.put("hebr", "he");
        SCRIPT_LANGUAGE.put("hang", "ko");
        SCRIPT_LANGUAGE.put("deva", "hi");
        SCRIPT_LANGUAGE.put("beng", "bn");
        SCRIPT_LANGUAGE.put("armn", "hy");
        SCRIPT_LANGUAGE.put("georgian", "ka");
        SCRIPT_LANGUAGE.put("guj", "gu");
        SCRIPT_LANGUAGE.put("kann", "kn");
        SCRIPT_LANGUAGE.put("tamil", "ta");
        SCRIPT_LANGUAGE.put("sinhala", "si");
        SCRIPT_LANGUAGE.put("urdu", "ur");
        SCRIPT_LANGUAGE.put("thai", "th");
        SCRIPT_LANGUAGE.put("shaw", "en");
    }

    /** Where the language came from, so a fallback can be told once. */
    public static final class Resolution {
        @NonNull public final String code;
        /** Nothing named a language Whisper knows, so {@link #FALLBACK} is being used. */
        public final boolean fallback;

        Resolution(@NonNull String code, boolean fallback) {
            this.code = code;
            this.fallback = fallback;
        }
    }

    private VoiceLanguage() {
    }

    /**
     * @param setting  the "Voice language" preference: {@link #AUTO} or a code
     * @param layoutId the in-app keyboard's active text layout id, or {@code null}
     * @param system   the first system locale, or {@code null}
     */
    @NonNull
    public static Resolution resolve(@Nullable String setting, @Nullable String layoutId,
                                     @Nullable Locale system) {
        String explicit = setting == null ? "" : setting.trim().toLowerCase(Locale.ROOT);
        if (!explicit.isEmpty() && !AUTO.equals(explicit)) {
            return isKnown(explicit) ? new Resolution(explicit, false) : new Resolution(FALLBACK, true);
        }
        String fromLayout = fromLayoutId(layoutId);
        if (fromLayout != null) return new Resolution(fromLayout, false);
        String fromLocale = fromLocale(system);
        if (fromLocale != null) return new Resolution(fromLocale, false);
        return new Resolution(FALLBACK, true);
    }

    public static boolean isKnown(@Nullable String code) {
        return code != null && WHISPER_LANGUAGES.contains(code);
    }

    /**
     * The language a layout id names: its trailing two- or three-letter tokens, last first, read
     * as a country ({@code us}, {@code br}) or a language ({@code de}, {@code haw}); "uk" under a
     * Latin script is Britain, under Cyrillic it is Ukrainian. The script alone decides for the
     * scripts that have one common language; {@code null} for the rest ({@code cyrl_jiuken},
     * {@code latn_dvorak}).
     */
    @Nullable
    static String fromLayoutId(@Nullable String layoutId) {
        if (layoutId == null || layoutId.isEmpty()) return null;
        String[] tokens = layoutId.toLowerCase(Locale.ROOT).split("_");
        String script = tokens[0];
        for (int i = tokens.length - 1; i >= 1; i--) {
            String token = tokens[i];
            if (token.length() < 2 || token.length() > 3) continue;
            if ("uk".equals(token)) return "latn".equals(script) ? "en" : "uk";
            String country = LAYOUT_TOKEN_LANGUAGE.get(token);
            if (country != null) return country;
            if ("be".equals(token) || "ch".equals(token)) continue;
            if (WHISPER_LANGUAGES.contains(token)) return token;
        }
        return SCRIPT_LANGUAGE.get(script);
    }

    /** The locale's language as a Whisper code, mapping Java's legacy codes; {@code null} when unknown. */
    @Nullable
    static String fromLocale(@Nullable Locale locale) {
        if (locale == null) return null;
        String language = locale.getLanguage();
        if (language == null || language.isEmpty()) return null;
        switch (language) {
            case "iw": language = "he"; break;
            case "in": language = "id"; break;
            case "ji": language = "yi"; break;
            case "nb": language = "no"; break;
            case "fil": language = "tl"; break;
            default: break;
        }
        return WHISPER_LANGUAGES.contains(language) ? language : null;
    }
}
