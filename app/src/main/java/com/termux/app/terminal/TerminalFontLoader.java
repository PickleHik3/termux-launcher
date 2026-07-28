package com.termux.app.terminal;

import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;
import com.termux.view.TerminalRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves a parsed font config without allowing one bad optional font to disable the terminal. */
public final class TerminalFontLoader {

    private static final long MAX_FONT_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_SYMBOL_FONTS = 64;

    public static final class Faces {
        @NonNull public final Typeface regular;
        @Nullable public final Typeface bold;
        @Nullable public final Typeface italic;
        @Nullable public final Typeface boldItalic;
        @NonNull public final TerminalRenderer.SymbolMap[] symbolMaps;
        @NonNull public final TerminalRenderer.LigaturePolicy ligaturePolicy;
        @NonNull public final TerminalRenderer.FontFeatures fontFeatures;
        @NonNull public final TerminalRenderer.FontVariations fontVariations;
        @NonNull public final List<String> errors;

        private Faces(@NonNull Typeface regular, @Nullable Typeface bold,
                      @Nullable Typeface italic, @Nullable Typeface boldItalic,
                      @NonNull TerminalRenderer.SymbolMap[] symbolMaps,
                      @NonNull TerminalRenderer.LigaturePolicy ligaturePolicy,
                      @NonNull TerminalRenderer.FontFeatures fontFeatures,
                      @NonNull TerminalRenderer.FontVariations fontVariations,
                      @NonNull List<String> errors) {
            this.regular = regular;
            this.bold = bold;
            this.italic = italic;
            this.boldItalic = boldItalic;
            this.symbolMaps = symbolMaps.clone();
            this.ligaturePolicy = ligaturePolicy;
            this.fontFeatures = fontFeatures;
            this.fontVariations = fontVariations;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }
    }

    private TerminalFontLoader() {}

    @NonNull
    public static Faces load(@NonNull TerminalFontConfig.Result config) {
        List<String> errors = new ArrayList<>(config.errors);
        Typeface regular = loadConfigured(config.face(TerminalFontConfig.Face.REGULAR),
            Typeface.NORMAL, "font_family", errors);
        if (regular == null)
            regular = loadLegacy(TermuxConstants.TERMUX_FONT_FILE, Typeface.MONOSPACE,
                "font.ttf", errors);

        Typeface bold = loadConfigured(config.face(TerminalFontConfig.Face.BOLD),
            Typeface.BOLD, "bold_font", errors);
        Typeface italic = loadConfigured(config.face(TerminalFontConfig.Face.ITALIC),
            Typeface.ITALIC, "italic_font", errors);
        if (italic == null && TermuxConstants.TERMUX_ITALIC_FONT_FILE.isFile())
            italic = loadPath(TermuxConstants.TERMUX_ITALIC_FONT_FILE,
                "font-italic.ttf", errors);
        Typeface boldItalic = loadConfigured(config.face(TerminalFontConfig.Face.BOLD_ITALIC),
            Typeface.BOLD_ITALIC, "bold_italic_font", errors);
        TerminalRenderer.SymbolMap[] symbolMaps = loadSymbolMaps(config.symbolMaps, errors);
        TerminalRenderer.LigaturePolicy ligaturePolicy = TerminalRenderer.LigaturePolicy.valueOf(
            config.ligaturePolicy.name());
        TerminalRenderer.FontFeatures fontFeatures = new TerminalRenderer.FontFeatures(
            config.features(TerminalFontConfig.FontTarget.REGULAR),
            config.features(TerminalFontConfig.FontTarget.BOLD),
            config.features(TerminalFontConfig.FontTarget.ITALIC),
            config.features(TerminalFontConfig.FontTarget.BOLD_ITALIC),
            config.features(TerminalFontConfig.FontTarget.SYMBOLS));
        Typeface boldResolved = bold == null ? regular : bold;
        Typeface italicResolved = italic == null ? regular : italic;
        Typeface boldItalicResolved = boldItalic != null ? boldItalic
            : italic != null ? italic : bold != null ? bold : regular;
        String regularVariations = validateVariations(regular,
            config.variations(TerminalFontConfig.FontTarget.REGULAR), "regular", errors);
        String boldVariations = validateVariations(boldResolved,
            config.variations(TerminalFontConfig.FontTarget.BOLD), "bold", errors);
        String italicVariations = validateVariations(italicResolved,
            config.variations(TerminalFontConfig.FontTarget.ITALIC), "italic", errors);
        String boldItalicVariations = validateVariations(boldItalicResolved,
            config.variations(TerminalFontConfig.FontTarget.BOLD_ITALIC), "bold_italic", errors);
        String symbolVariations = validateSymbolVariations(symbolMaps,
            config.variations(TerminalFontConfig.FontTarget.SYMBOLS), errors);
        TerminalRenderer.FontVariations fontVariations = new TerminalRenderer.FontVariations(
            regularVariations, boldVariations, italicVariations, boldItalicVariations,
            symbolVariations);
        return new Faces(regular, bold, italic, boldItalic, symbolMaps, ligaturePolicy,
            fontFeatures, fontVariations, errors);
    }

    @Nullable
    private static String validateVariations(@NonNull Typeface typeface,
                                             @Nullable String settings,
                                             @NonNull String label,
                                             @NonNull List<String> errors) {
        if (settings == null) return null;
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        try {
            if (paint.setFontVariationSettings(settings)) return settings;
            errors.add("font_variations " + label + ": Android rejected the requested axes");
        } catch (RuntimeException e) {
            errors.add("font_variations " + label + ": Android rejected the requested axes: "
                + safeMessage(e));
        }
        return null;
    }

    @Nullable
    private static String validateSymbolVariations(
        @NonNull TerminalRenderer.SymbolMap[] symbolMaps, @Nullable String settings,
        @NonNull List<String> errors) {
        if (settings == null || symbolMaps.length == 0) return settings;
        Set<Typeface> checked = new HashSet<>();
        for (TerminalRenderer.SymbolMap map : symbolMaps) {
            if (checked.add(map.typeface)
                && validateVariations(map.typeface, settings, "symbols", errors) == null)
                return null;
        }
        return settings;
    }

    @NonNull
    private static TerminalRenderer.SymbolMap[] loadSymbolMaps(
        @NonNull List<TerminalFontConfig.SymbolMapSpec> specs,
        @NonNull List<String> errors) {
        List<TerminalRenderer.SymbolMap> result = new ArrayList<>();
        Map<String, Typeface> loaded = new LinkedHashMap<>();
        Set<String> failed = new HashSet<>();
        for (TerminalFontConfig.SymbolMapSpec spec : specs) {
            String key = spec.font.type.name() + '\n' + spec.font.value;
            Typeface typeface = loaded.get(key);
            if (typeface == null && !failed.contains(key)) {
                if (loaded.size() + failed.size() >= MAX_SYMBOL_FONTS) {
                    errors.add("symbol_map: distinct font count exceeds " + MAX_SYMBOL_FONTS);
                    break;
                }
                typeface = loadConfigured(spec.font, Typeface.NORMAL,
                    "symbol_map " + sourceDescription(spec.font), errors);
                if (typeface == null) failed.add(key);
                else loaded.put(key, typeface);
            }
            if (typeface == null) continue;
            for (TerminalFontConfig.CodePointRange range : spec.ranges)
                result.add(new TerminalRenderer.SymbolMap(range.first, range.last, typeface));
        }
        return result.toArray(new TerminalRenderer.SymbolMap[0]);
    }

    @NonNull
    private static String sourceDescription(@NonNull TerminalFontConfig.FaceSpec spec) {
        return spec.type == TerminalFontConfig.SourceType.PATH
            ? "path=" + spec.value : "family='" + spec.value + "'";
    }

    @Nullable
    private static Typeface loadConfigured(@Nullable TerminalFontConfig.FaceSpec spec, int style,
                                           @NonNull String label,
                                           @NonNull List<String> errors) {
        if (spec == null) return null;
        if (spec.type == TerminalFontConfig.SourceType.FAMILY) {
            try {
                return Typeface.create(spec.value, style);
            } catch (RuntimeException e) {
                errors.add(label + ": cannot resolve family '" + spec.value + "': "
                    + safeMessage(e));
                return null;
            }
        }
        return loadPath(new File(TerminalFontConfig.expandPath(spec.value)), label, errors);
    }

    @NonNull
    private static Typeface loadLegacy(@NonNull File file, @NonNull Typeface fallback,
                                       @NonNull String label, @NonNull List<String> errors) {
        if (!file.isFile() || file.length() <= 0) return fallback;
        Typeface loaded = loadPath(file, label, errors);
        return loaded == null ? fallback : loaded;
    }

    @Nullable
    private static Typeface loadPath(@NonNull File file, @NonNull String label,
                                     @NonNull List<String> errors) {
        if (!file.isFile() || !file.canRead() || file.length() <= 0) {
            errors.add(label + ": font path is not a readable non-empty file");
            return null;
        }
        if (file.length() > MAX_FONT_FILE_BYTES) {
            errors.add(label + ": font file exceeds " + MAX_FONT_FILE_BYTES + " bytes");
            return null;
        }
        try {
            return Typeface.createFromFile(file);
        } catch (RuntimeException e) {
            errors.add(label + ": Android rejected the font: " + safeMessage(e));
            return null;
        }
    }

    @NonNull
    private static String safeMessage(@NonNull RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? error.getClass().getSimpleName() : message;
    }
}
