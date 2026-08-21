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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves a parsed font config without allowing one bad optional font to disable the terminal. */
public final class TerminalFontLoader {

    private static final long MAX_FONT_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_SYMBOL_FONTS = 64;
    private static final int MAX_FALLBACK_FONTS = 8;

    public static final class Faces {
        @NonNull public final Typeface regular;
        @Nullable public final Typeface bold;
        @Nullable public final Typeface italic;
        @Nullable public final Typeface boldItalic;
        @NonNull public final TerminalRenderer.SymbolMap[] symbolMaps;
        /** {@code fallback_font} faces in config order, tried after the primary faces. */
        @NonNull public final List<Typeface> fallbackFonts;
        @NonNull public final TerminalRenderer.LigaturePolicy ligaturePolicy;
        @NonNull public final TerminalRenderer.FontFeatures fontFeatures;
        @NonNull public final TerminalRenderer.FontVariations fontVariations;
        @NonNull public final TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments;
        /** {@code box_drawing}, {@code box_drawing_scale} and {@code powerline_symbols}. */
        @NonNull public final TerminalRenderer.BoxDrawingPolicy boxDrawingPolicy;
        /** {@code narrow_symbols}: per-code-point ceilings on symbol expansion. */
        @NonNull public final TerminalRenderer.SymbolExpansion symbolExpansion;
        @NonNull public final List<String> errors;

        private Faces(@NonNull Typeface regular, @Nullable Typeface bold,
                      @Nullable Typeface italic, @Nullable Typeface boldItalic,
                      @NonNull TerminalRenderer.SymbolMap[] symbolMaps,
                      @NonNull List<Typeface> fallbackFonts,
                      @NonNull TerminalRenderer.LigaturePolicy ligaturePolicy,
                      @NonNull TerminalRenderer.FontFeatures fontFeatures,
                      @NonNull TerminalRenderer.FontVariations fontVariations,
                      @NonNull TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments,
                      @NonNull TerminalRenderer.BoxDrawingPolicy boxDrawingPolicy,
                      @NonNull TerminalRenderer.SymbolExpansion symbolExpansion,
                      @NonNull List<String> errors) {
            this.regular = regular;
            this.bold = bold;
            this.italic = italic;
            this.boldItalic = boldItalic;
            this.symbolMaps = symbolMaps.clone();
            this.fallbackFonts = Collections.unmodifiableList(new ArrayList<>(fallbackFonts));
            this.ligaturePolicy = ligaturePolicy;
            this.fontFeatures = fontFeatures;
            this.fontVariations = fontVariations;
            this.fontMetricsAdjustments = fontMetricsAdjustments;
            this.boxDrawingPolicy = boxDrawingPolicy;
            this.symbolExpansion = symbolExpansion;
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
        // One memo for every (face, axes) check the symbol path makes, so a setting inherited by
        // several maps from the shared symbols target is validated — and reported — once.
        Map<String, Boolean> variationChecks = new HashMap<>();
        String sharedSymbolVariations = config.variations(TerminalFontConfig.FontTarget.SYMBOLS);
        TerminalRenderer.SymbolMap[] symbolMaps = loadSymbolMaps(config.symbolMaps,
            sharedSymbolVariations, errors, variationChecks);
        List<Typeface> fallbackFonts = loadFallbackFonts(config.fallbackFonts, errors);
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
        String symbolVariations = validateSymbolVariations(symbolMaps, sharedSymbolVariations,
            errors, variationChecks);
        TerminalRenderer.FontVariations fontVariations = new TerminalRenderer.FontVariations(
            regularVariations, boldVariations, italicVariations, boldItalicVariations,
            symbolVariations);
        TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments =
            new TerminalRenderer.FontMetricsAdjustments(
                metric(config, TerminalFontConfig.Metric.CELL_WIDTH),
                metric(config, TerminalFontConfig.Metric.CELL_HEIGHT),
                metric(config, TerminalFontConfig.Metric.BASELINE),
                metric(config, TerminalFontConfig.Metric.UNDERLINE_POSITION),
                metric(config, TerminalFontConfig.Metric.UNDERLINE_THICKNESS),
                metric(config, TerminalFontConfig.Metric.STRIKETHROUGH_POSITION),
                metric(config, TerminalFontConfig.Metric.STRIKETHROUGH_THICKNESS));
        return new Faces(regular, bold, italic, boldItalic, symbolMaps, fallbackFonts,
            ligaturePolicy, fontFeatures, fontVariations, fontMetricsAdjustments,
            boxDrawingPolicy(config), symbolExpansion(config), errors);
    }

    /** Translates the box-drawing directives into the renderer's own policy type. */
    @NonNull
    private static TerminalRenderer.BoxDrawingPolicy boxDrawingPolicy(
        @NonNull TerminalFontConfig.Result config) {
        TerminalFontConfig.BoxDrawingScale scale = config.boxDrawingScale;
        return new TerminalRenderer.BoxDrawingPolicy(
            config.boxDrawing == TerminalFontConfig.BoxDrawingMode.FONT
                ? TerminalRenderer.BoxDrawingPolicy.Mode.FONT
                : TerminalRenderer.BoxDrawingPolicy.Mode.SYNTHESIZE,
            new float[] {(float) scale.thin, (float) scale.light, (float) scale.heavy,
                (float) scale.veryHeavy},
            config.powerlineSymbols == TerminalFontConfig.PowerlineMode.SYNTHESIZE);
    }

    /** Flattens the {@code narrow_symbols} rules into the renderer's parallel-array form. */
    @NonNull
    private static TerminalRenderer.SymbolExpansion symbolExpansion(
        @NonNull TerminalFontConfig.Result config) {
        int count = 0;
        for (TerminalFontConfig.NarrowSymbolsSpec spec : config.narrowSymbols)
            count += spec.ranges.size();
        if (count == 0) return TerminalRenderer.SymbolExpansion.DEFAULT;
        int[] first = new int[count];
        int[] last = new int[count];
        int[] cells = new int[count];
        int at = 0;
        for (TerminalFontConfig.NarrowSymbolsSpec spec : config.narrowSymbols) {
            for (TerminalFontConfig.CodePointRange range : spec.ranges) {
                first[at] = range.first;
                last[at] = range.last;
                cells[at] = spec.cells;
                at++;
            }
        }
        return new TerminalRenderer.SymbolExpansion(first, last, cells);
    }

    /** Resolves the fallback chain in order, dropping only the entries Android cannot load. */
    @NonNull
    private static List<Typeface> loadFallbackFonts(
        @NonNull List<TerminalFontConfig.FaceSpec> specs, @NonNull List<String> errors) {
        List<Typeface> result = new ArrayList<>();
        for (TerminalFontConfig.FaceSpec spec : specs) {
            if (result.size() >= MAX_FALLBACK_FONTS) {
                errors.add("fallback_font: font count exceeds " + MAX_FALLBACK_FONTS);
                break;
            }
            Typeface typeface = loadConfigured(spec, Typeface.NORMAL,
                "fallback_font " + sourceDescription(spec), errors);
            if (typeface != null) result.add(typeface);
        }
        return result;
    }

    @Nullable
    private static TerminalRenderer.MetricAdjustment metric(
        @NonNull TerminalFontConfig.Result config, @NonNull TerminalFontConfig.Metric metric) {
        TerminalFontConfig.MetricAdjustment adjustment = config.metric(metric);
        if (adjustment == null) return null;
        return new TerminalRenderer.MetricAdjustment((float) adjustment.value,
            adjustment.unit == TerminalFontConfig.MetricUnit.PERCENT);
    }

    @Nullable
    private static String validateVariations(@NonNull Typeface typeface,
                                             @Nullable String settings,
                                             @NonNull String label,
                                             @NonNull List<String> errors) {
        return validateVariations(typeface, settings, label, errors, null);
    }

    /**
     * The axes to hand the renderer, or null once the rejection has been reported as an error.
     *
     * <p>{@code checks} memoizes one (face, label, axes) verdict so the same check cannot report
     * the same error twice; pass null for a check that is only made once.
     */
    @Nullable
    private static String validateVariations(@NonNull Typeface typeface,
                                             @Nullable String settings,
                                             @NonNull String label,
                                             @NonNull List<String> errors,
                                             @Nullable Map<String, Boolean> checks) {
        if (settings == null) return null;
        String key = checks == null ? null
            : System.identityHashCode(typeface) + "\n" + label + "\n" + settings;
        if (key != null) {
            Boolean cached = checks.get(key);
            if (cached != null) return cached ? settings : null;
        }
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        try {
            if (paint.setFontVariationSettings(settings)) {
                if (key != null) checks.put(key, Boolean.TRUE);
                return settings;
            }
            errors.add("font_variations " + label + ": Android rejected the requested axes");
        } catch (RuntimeException e) {
            errors.add("font_variations " + label + ": Android rejected the requested axes: "
                + safeMessage(e));
        }
        if (key != null) checks.put(key, Boolean.FALSE);
        return null;
    }

    @Nullable
    private static String validateSymbolVariations(
        @NonNull TerminalRenderer.SymbolMap[] symbolMaps, @Nullable String settings,
        @NonNull List<String> errors, @NonNull Map<String, Boolean> checks) {
        if (settings == null || symbolMaps.length == 0) return settings;
        Set<Typeface> checked = new HashSet<>();
        for (TerminalRenderer.SymbolMap map : symbolMaps) {
            if (checked.add(map.typeface)
                && validateVariations(map.typeface, settings, "symbols", errors, checks) == null)
                return null;
        }
        return settings;
    }

    /**
     * Resolves every {@code symbol_map} range, carrying the settings its map resolved to.
     *
     * <p>A named map's own {@code font_features}/{@code font_variations} reach the renderer on the
     * map itself; an unnamed map carries the shared {@code symbols} settings the parser already
     * resolved for it. Features arrive translated and bounded by the parser, exactly as the face
     * targets' do, so only the axes need a face to be checked against here.
     */
    @NonNull
    private static TerminalRenderer.SymbolMap[] loadSymbolMaps(
        @NonNull List<TerminalFontConfig.SymbolMapSpec> specs,
        @Nullable String sharedVariations,
        @NonNull List<String> errors,
        @NonNull Map<String, Boolean> variationChecks) {
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
            // Axes inherited from the shared target are reported under that target's label, so the
            // user reads one message about the line they actually wrote.
            String label = spec.name != null && !sameSettings(spec.variations, sharedVariations)
                ? spec.name : "symbols";
            String variations = validateVariations(typeface, spec.variations, label, errors,
                variationChecks);
            for (TerminalFontConfig.CodePointRange range : spec.ranges)
                result.add(new TerminalRenderer.SymbolMap(range.first, range.last, typeface,
                    spec.features, variations));
        }
        return result.toArray(new TerminalRenderer.SymbolMap[0]);
    }

    private static boolean sameSettings(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : first.equals(second);
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
