package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded parser for the optional {@code ~/.termux/fonts.conf} file and the optional
 * {@code ~/.termux/fonts.d/*.conf} drop-in directory. The drop-ins are read first in ascending
 * filename order and {@code fonts.conf} last, so the user's own file always wins the existing
 * last-duplicate-wins rule.
 */
public final class TerminalFontConfig {

    public static final String FILE_NAME = "fonts.conf";
    public static final String FILE_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + FILE_NAME;
    public static final String DROP_IN_DIR_NAME = "fonts.d";
    public static final String DROP_IN_DIR_PATH =
        TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + DROP_IN_DIR_NAME;
    private static final String DROP_IN_SUFFIX = ".conf";
    private static final long MAX_FILE_BYTES = 64 * 1024;
    private static final int MAX_DROP_IN_FILES = 32;
    /** Aggregate budget for the drop-ins only: the user's own fonts.conf is never squeezed out. */
    private static final long MAX_DROP_IN_TOTAL_BYTES = 256 * 1024;
    private static final int MAX_LINES = 512;
    private static final int MAX_LINE_CHARS = 4096;
    private static final int MAX_FAMILY_CHARS = 128;
    private static final int MAX_SYMBOL_MAPS = 256;
    private static final int MAX_SYMBOL_RANGES = 1024;
    private static final int MAX_SYMBOL_MAP_NAME_CHARS = 32;
    private static final int MAX_NAMED_TARGETS = 256;
    private static final int MAX_FALLBACK_FONTS = 8;
    private static final int MAX_FEATURES_PER_TARGET = 32;
    private static final int MAX_VARIATIONS_PER_TARGET = 16;
    private static final int BOX_DRAWING_SCALE_VALUES = 4;
    private static final int MAX_BOX_DRAWING_SCALE = 8;
    private static final String NAME_PREFIX = "name=";

    public enum Face { REGULAR, BOLD, ITALIC, BOLD_ITALIC }

    public enum SourceType { PATH, FAMILY }

    public enum LigaturePolicy { NEVER, CURSOR, ALWAYS }

    public enum FontTarget { REGULAR, BOLD, ITALIC, BOLD_ITALIC, SYMBOLS }

    public enum BoxDrawingMode { SYNTHESIZE, FONT }

    public enum PowerlineMode { FONT, SYNTHESIZE }

    public enum Metric {
        CELL_WIDTH, CELL_HEIGHT, BASELINE, UNDERLINE_POSITION, UNDERLINE_THICKNESS,
        STRIKETHROUGH_POSITION, STRIKETHROUGH_THICKNESS
    }

    public enum MetricUnit { PIXEL, PERCENT }

    public static final class MetricAdjustment {
        public final double value;
        @NonNull public final MetricUnit unit;

        private MetricAdjustment(double value, @NonNull MetricUnit unit) {
            this.value = value;
            this.unit = unit;
        }
    }

    /** Stroke widths, in pixels at the default font size, of the four box drawing weights. */
    public static final class BoxDrawingScale {
        public final double thin;
        public final double light;
        public final double heavy;
        public final double veryHeavy;

        private BoxDrawingScale(double thin, double light, double heavy, double veryHeavy) {
            this.thin = thin;
            this.light = light;
            this.heavy = heavy;
            this.veryHeavy = veryHeavy;
        }
    }

    private static final BoxDrawingScale DEFAULT_BOX_DRAWING_SCALE =
        new BoxDrawingScale(0.001d, 1d, 1.5d, 2d);

    public static final class FaceSpec {
        @NonNull public final SourceType type;
        @NonNull public final String value;

        private FaceSpec(@NonNull SourceType type, @NonNull String value) {
            this.type = type;
            this.value = value;
        }
    }

    public static final class CodePointRange {
        public final int first;
        public final int last;

        private CodePointRange(int first, int last) {
            this.first = first;
            this.last = last;
        }
    }

    public static final class SymbolMapSpec {
        @NonNull public final List<CodePointRange> ranges;
        @NonNull public final FaceSpec font;
        /** The optional {@code name=} of the map, as written; null for an unnamed map. */
        @Nullable public final String name;
        /** Already resolved: the map's own settings if named, else the shared symbols ones. */
        @Nullable public final String features;
        @Nullable public final String variations;

        private SymbolMapSpec(@Nullable String name, @NonNull List<CodePointRange> ranges,
                              @NonNull FaceSpec font, @Nullable String features,
                              @Nullable String variations) {
            this.name = name;
            this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
            this.font = font;
            this.features = features;
            this.variations = variations;
        }
    }

    public static final class Result {
        /**
         * True when the user has some active font configuration, which means either
         * {@code fonts.conf} exists or at least one {@code fonts.d} drop-in was loaded.
         */
        public final boolean filePresent;
        @NonNull public final Map<Face, FaceSpec> faces;
        @NonNull public final List<SymbolMapSpec> symbolMaps;
        @NonNull public final List<FaceSpec> fallbackFonts;
        @NonNull public final LigaturePolicy ligaturePolicy;
        @NonNull public final Map<FontTarget, String> fontFeatures;
        @NonNull public final Map<FontTarget, String> fontVariations;
        @NonNull public final Map<String, String> namedFontFeatures;
        @NonNull public final Map<String, String> namedFontVariations;
        @NonNull public final Map<Metric, MetricAdjustment> metrics;
        @NonNull public final BoxDrawingMode boxDrawing;
        @NonNull public final BoxDrawingScale boxDrawingScale;
        @NonNull public final PowerlineMode powerlineSymbols;
        @NonNull public final List<String> errors;

        private Result(boolean filePresent, @NonNull Map<Face, FaceSpec> faces,
                       @NonNull List<SymbolMapSpec> symbolMaps,
                       @NonNull List<FaceSpec> fallbackFonts,
                       @NonNull LigaturePolicy ligaturePolicy,
                       @NonNull Map<FontTarget, String> fontFeatures,
                       @NonNull Map<FontTarget, String> fontVariations,
                       @NonNull Map<String, String> namedFontFeatures,
                       @NonNull Map<String, String> namedFontVariations,
                       @NonNull Map<Metric, MetricAdjustment> metrics,
                       @NonNull BoxDrawingMode boxDrawing,
                       @NonNull BoxDrawingScale boxDrawingScale,
                       @NonNull PowerlineMode powerlineSymbols,
                       @NonNull List<String> errors) {
            this.filePresent = filePresent;
            EnumMap<Face, FaceSpec> faceCopy = new EnumMap<>(Face.class);
            faceCopy.putAll(faces);
            this.faces = Collections.unmodifiableMap(faceCopy);
            this.symbolMaps = Collections.unmodifiableList(new ArrayList<>(symbolMaps));
            this.fallbackFonts = Collections.unmodifiableList(new ArrayList<>(fallbackFonts));
            this.ligaturePolicy = ligaturePolicy;
            EnumMap<FontTarget, String> featureCopy = new EnumMap<>(FontTarget.class);
            featureCopy.putAll(fontFeatures);
            this.fontFeatures = Collections.unmodifiableMap(featureCopy);
            EnumMap<FontTarget, String> variationCopy = new EnumMap<>(FontTarget.class);
            variationCopy.putAll(fontVariations);
            this.fontVariations = Collections.unmodifiableMap(variationCopy);
            this.namedFontFeatures =
                Collections.unmodifiableMap(new LinkedHashMap<>(namedFontFeatures));
            this.namedFontVariations =
                Collections.unmodifiableMap(new LinkedHashMap<>(namedFontVariations));
            EnumMap<Metric, MetricAdjustment> metricCopy = new EnumMap<>(Metric.class);
            metricCopy.putAll(metrics);
            this.metrics = Collections.unmodifiableMap(metricCopy);
            this.boxDrawing = boxDrawing;
            this.boxDrawingScale = boxDrawingScale;
            this.powerlineSymbols = powerlineSymbols;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        @Nullable public FaceSpec face(@NonNull Face face) {
            return faces.get(face);
        }

        @Nullable public String features(@NonNull FontTarget target) {
            return fontFeatures.get(target);
        }

        @Nullable public String variations(@NonNull FontTarget target) {
            return fontVariations.get(target);
        }

        /** Features declared for a {@code symbol_map name=}, matched case-insensitively. */
        @Nullable public String namedFeatures(@NonNull String name) {
            return namedFontFeatures.get(name.toLowerCase(Locale.US));
        }

        @Nullable public String namedVariations(@NonNull String name) {
            return namedFontVariations.get(name.toLowerCase(Locale.US));
        }

        @Nullable public MetricAdjustment metric(@NonNull Metric metric) {
            return metrics.get(metric);
        }
    }

    /** One {@code font_features}/{@code font_variations} line that named a symbol map. */
    private static final class NamedSetting {
        @NonNull final String name;
        @NonNull final String settings;
        @NonNull final String where;

        NamedSetting(@NonNull String name, @NonNull String settings, @NonNull String where) {
            this.name = name;
            this.settings = settings;
            this.where = where;
        }
    }

    /** Mutable state shared by every file of one load, so later files override earlier ones. */
    private static final class Accumulator {
        final EnumMap<Face, FaceSpec> faces = new EnumMap<>(Face.class);
        final List<SymbolMapSpec> symbolMaps = new ArrayList<>();
        final List<FaceSpec> fallbackFonts = new ArrayList<>();
        final EnumMap<FontTarget, String> fontFeatures = new EnumMap<>(FontTarget.class);
        final EnumMap<FontTarget, String> fontVariations = new EnumMap<>(FontTarget.class);
        final LinkedHashMap<String, NamedSetting> namedFeatures = new LinkedHashMap<>();
        final LinkedHashMap<String, NamedSetting> namedVariations = new LinkedHashMap<>();
        final LinkedHashMap<String, String> symbolMapNames = new LinkedHashMap<>();
        final EnumMap<Metric, MetricAdjustment> metrics = new EnumMap<>(Metric.class);
        final List<String> errors = new ArrayList<>();
        LigaturePolicy ligaturePolicy = LigaturePolicy.NEVER;
        BoxDrawingMode boxDrawing = BoxDrawingMode.SYNTHESIZE;
        BoxDrawingScale boxDrawingScale = DEFAULT_BOX_DRAWING_SCALE;
        // Kitty renders the Powerline separators itself, and geometry is the only way their edges
        // sit flush with the cell-aligned background rectangles; a font glyph never fills the cell.
        PowerlineMode powerlineSymbols = PowerlineMode.SYNTHESIZE;
        int symbolRangeCount;
        boolean filePresent;
    }

    private TerminalFontConfig() {}

    @NonNull
    public static Result load() {
        return load(new File(DROP_IN_DIR_PATH), new File(FILE_PATH));
    }

    /** Loads the {@code fonts.d} directory sitting next to the given {@code fonts.conf}. */
    @NonNull
    static Result load(@NonNull File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        return load(parent == null ? new File(DROP_IN_DIR_PATH)
            : new File(parent, DROP_IN_DIR_NAME), file);
    }

    @NonNull
    static Result load(@NonNull File dropInDir, @NonNull File file) {
        Accumulator accumulator = new Accumulator();
        long budget = MAX_DROP_IN_TOTAL_BYTES;
        for (File dropIn : dropInFiles(dropInDir, accumulator.errors)) {
            String prefix = DROP_IN_DIR_NAME + "/" + dropIn.getName() + ": ";
            if (dropIn.length() > budget) {
                accumulator.errors.add(DROP_IN_DIR_NAME + ": drop-in set exceeds "
                    + MAX_DROP_IN_TOTAL_BYTES + " bytes; remaining " + DROP_IN_DIR_NAME
                    + " files skipped");
                break;
            }
            String content = read(dropIn, prefix, accumulator.errors);
            if (content == null) continue;
            budget -= dropIn.length();
            // A drop-in alone counts as an active configuration; the loader still falls back to
            // font.ttf and monospace whenever a face is left unset, exactly as with no files.
            accumulator.filePresent = true;
            parse(accumulator, content, prefix);
        }
        // The user's own file is read outside that budget and keeps its own 64 KiB allowance, so
        // no set of drop-ins can push ~/.termux/fonts.conf out of the load.
        if (!file.exists()) return finish(accumulator);
        accumulator.filePresent = true;
        String content = read(file, "", accumulator.errors);
        if (content != null) parse(accumulator, content, null);
        return finish(accumulator);
    }

    /** The {@code *.conf} files of one {@code fonts.d}, in ascending byte-wise filename order. */
    @NonNull
    private static List<File> dropInFiles(@NonNull File dir, @NonNull List<String> errors) {
        if (!dir.isDirectory()) return Collections.emptyList();
        File[] entries = dir.listFiles();
        if (entries == null) {
            errors.add(DROP_IN_DIR_NAME + ": cannot list the directory");
            return Collections.emptyList();
        }
        File canonicalDir;
        try {
            canonicalDir = dir.getCanonicalFile();
        } catch (IOException e) {
            errors.add(DROP_IN_DIR_NAME + ": cannot resolve the directory: " + e.getMessage());
            return Collections.emptyList();
        }
        Arrays.sort(entries, (first, second) -> compareBytes(first.getName(), second.getName()));
        List<File> result = new ArrayList<>();
        for (File entry : entries) {
            if (!entry.getName().toLowerCase(Locale.US).endsWith(DROP_IN_SUFFIX)) continue;
            if (!entry.isFile() || !entry.canRead()) continue;
            // Never follow a link out of fonts.d, so a dropped-in symlink cannot pull in a file
            // the user did not mean to hand to the terminal.
            try {
                File resolvedParent = entry.getCanonicalFile().getParentFile();
                if (resolvedParent == null || !resolvedParent.equals(canonicalDir)) continue;
            } catch (IOException e) {
                continue;
            }
            if (result.size() >= MAX_DROP_IN_FILES) {
                errors.add(DROP_IN_DIR_NAME + ": file count exceeds " + MAX_DROP_IN_FILES);
                break;
            }
            result.add(entry);
        }
        return result;
    }

    private static int compareBytes(@NonNull String first, @NonNull String second) {
        byte[] a = first.getBytes(StandardCharsets.UTF_8);
        byte[] b = second.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < a.length && i < b.length; i++) {
            int difference = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (difference != 0) return difference;
        }
        return a.length - b.length;
    }

    /** Reads one bounded config file; null means the file was skipped and errors explains why. */
    @Nullable
    private static String read(@NonNull File file, @NonNull String prefix,
                               @NonNull List<String> errors) {
        if (!file.isFile()) {
            errors.add(prefix + file.getPath() + " is not a regular file");
            return null;
        }
        if (file.length() > MAX_FILE_BYTES) {
            errors.add(prefix + "font config exceeds " + MAX_FILE_BYTES + " bytes");
            return null;
        }
        StringBuilder content = new StringBuilder((int) Math.min(file.length(), MAX_FILE_BYTES));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (++count > MAX_LINES) {
                    errors.add(prefix + "font config exceeds " + MAX_LINES + " lines");
                    return null;
                }
                if (line.length() > MAX_LINE_CHARS) {
                    errors.add(prefix + "line " + count + " exceeds " + MAX_LINE_CHARS
                        + " characters");
                    return null;
                }
                content.append(line).append('\n');
            }
        } catch (IOException e) {
            errors.add(prefix + "cannot read font config: " + e.getMessage());
            return null;
        }
        return content.toString();
    }

    @NonNull
    static Result parse(@NonNull String content, boolean filePresent) {
        Accumulator accumulator = new Accumulator();
        accumulator.filePresent = filePresent;
        parse(accumulator, content, null);
        return finish(accumulator);
    }

    /** Parses one file into the shared accumulator; prefix names the file in every error. */
    private static void parse(@NonNull Accumulator accumulator, @NonNull String content,
                              @Nullable String prefix) {
        List<String> errors = accumulator.errors;
        String[] lines = content.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String where = (prefix == null ? "" : prefix) + "line " + (i + 1);
            List<String> words;
            try {
                words = words(lines[i]);
            } catch (IllegalArgumentException e) {
                errors.add(where + ": " + e.getMessage());
                continue;
            }
            if (words.isEmpty()) continue;
            if ("modify_font".equalsIgnoreCase(words.get(0))) {
                if (words.size() != 3) {
                    errors.add(where + ": expected modify_font metric value");
                    continue;
                }
                Metric metric = metric(words.get(1));
                if (metric == null) {
                    errors.add(where + ": unknown font metric '" + words.get(1) + "'");
                    continue;
                }
                if ("none".equalsIgnoreCase(words.get(2))) {
                    accumulator.metrics.remove(metric);
                    continue;
                }
                MetricAdjustment adjustment = parseMetricAdjustment(words.get(2), where, errors);
                if (adjustment != null) accumulator.metrics.put(metric, adjustment);
                continue;
            }
            if ("font_variations".equalsIgnoreCase(words.get(0))) {
                if (words.size() < 3) {
                    errors.add(where + ": expected font_variations target and one or more axes");
                    continue;
                }
                FontTarget target = fontTarget(words.get(1));
                String name = target == null ? words.get(1) : null;
                if (name != null && !isSymbolMapName(name)) {
                    errors.add(where + ": variation target must be regular, bold, italic,"
                        + " bold_italic, symbols, or a symbol_map name");
                    continue;
                }
                if (words.size() == 3 && "none".equalsIgnoreCase(words.get(2))) {
                    if (target != null) accumulator.fontVariations.remove(target);
                    else accumulator.namedVariations.remove(name.toLowerCase(Locale.US));
                    continue;
                }
                String settings = parseVariationSettings(words, 2, where, errors);
                if (settings == null) continue;
                if (target != null) accumulator.fontVariations.put(target, settings);
                else putNamed(accumulator.namedVariations, name, settings, where, errors);
                continue;
            }
            if ("font_features".equalsIgnoreCase(words.get(0))) {
                if (words.size() < 3) {
                    errors.add(where + ": expected font_features target and one or more features");
                    continue;
                }
                FontTarget target = fontTarget(words.get(1));
                String name = target == null ? words.get(1) : null;
                if (name != null && !isSymbolMapName(name)) {
                    errors.add(where + ": feature target must be regular, bold, italic,"
                        + " bold_italic, symbols, or a symbol_map name");
                    continue;
                }
                if (words.size() == 3 && "none".equalsIgnoreCase(words.get(2))) {
                    if (target != null) accumulator.fontFeatures.remove(target);
                    else accumulator.namedFeatures.remove(name.toLowerCase(Locale.US));
                    continue;
                }
                String settings = parseFeatureSettings(words, 2, where, errors);
                if (settings == null) continue;
                if (target != null) accumulator.fontFeatures.put(target, settings);
                else putNamed(accumulator.namedFeatures, name, settings, where, errors);
                continue;
            }
            if ("disable_ligatures".equalsIgnoreCase(words.get(0))) {
                if (words.size() != 2) {
                    errors.add(where + ": expected disable_ligatures never, cursor, or always");
                    continue;
                }
                try {
                    accumulator.ligaturePolicy =
                        LigaturePolicy.valueOf(words.get(1).toUpperCase(Locale.US));
                } catch (IllegalArgumentException e) {
                    errors.add(where + ": disable_ligatures must be never, cursor, or always");
                }
                continue;
            }
            if ("box_drawing".equalsIgnoreCase(words.get(0))) {
                if (words.size() != 2) {
                    errors.add(where + ": expected box_drawing synthesize or font");
                    continue;
                }
                try {
                    accumulator.boxDrawing =
                        BoxDrawingMode.valueOf(words.get(1).toUpperCase(Locale.US));
                } catch (IllegalArgumentException e) {
                    errors.add(where + ": box_drawing must be synthesize or font");
                }
                continue;
            }
            if ("box_drawing_scale".equalsIgnoreCase(words.get(0))) {
                BoxDrawingScale scale = parseBoxDrawingScale(words, 1, where, errors);
                if (scale != null) accumulator.boxDrawingScale = scale;
                continue;
            }
            if ("powerline_symbols".equalsIgnoreCase(words.get(0))) {
                if (words.size() != 2) {
                    errors.add(where + ": expected powerline_symbols font or synthesize");
                    continue;
                }
                try {
                    accumulator.powerlineSymbols =
                        PowerlineMode.valueOf(words.get(1).toUpperCase(Locale.US));
                } catch (IllegalArgumentException e) {
                    errors.add(where + ": powerline_symbols must be font or synthesize");
                }
                continue;
            }
            if ("symbol_map".equalsIgnoreCase(words.get(0))) {
                String name = null;
                int names = 0;
                List<String> arguments = new ArrayList<>();
                for (int word = 1; word < words.size(); word++) {
                    if (words.get(word).startsWith(NAME_PREFIX)) {
                        name = words.get(word).substring(NAME_PREFIX.length());
                        names++;
                    } else {
                        arguments.add(words.get(word));
                    }
                }
                if (names > 1) {
                    errors.add(where + ": symbol_map accepts one name= value");
                    continue;
                }
                if (arguments.size() != 2) {
                    errors.add(where
                        + ": expected symbol_map ranges and one path= or family= value");
                    continue;
                }
                if (name != null && !isSymbolMapName(name)) {
                    errors.add(where + ": symbol map names must be 1 to "
                        + MAX_SYMBOL_MAP_NAME_CHARS + " characters of A-Z a-z 0-9 _ -");
                    continue;
                }
                if (name != null && fontTarget(name) != null) {
                    errors.add(where + ": symbol map name '" + name
                        + "' is a reserved font target");
                    continue;
                }
                if (accumulator.symbolMaps.size() >= MAX_SYMBOL_MAPS) {
                    errors.add(where + ": symbol_map count exceeds " + MAX_SYMBOL_MAPS);
                    continue;
                }
                List<CodePointRange> ranges = parseRanges(arguments.get(0), where, errors);
                FaceSpec font = parseSource(arguments.get(1), where, errors);
                if (ranges == null || font == null) continue;
                if (accumulator.symbolRangeCount + ranges.size() > MAX_SYMBOL_RANGES) {
                    errors.add(where + ": symbol range count exceeds " + MAX_SYMBOL_RANGES);
                    continue;
                }
                accumulator.symbolMaps.add(new SymbolMapSpec(name, ranges, font, null, null));
                accumulator.symbolRangeCount += ranges.size();
                // MAX_SYMBOL_MAPS already bounds how many distinct names can be declared.
                if (name != null) accumulator.symbolMapNames.put(name.toLowerCase(Locale.US), name);
                continue;
            }
            if ("fallback_font".equalsIgnoreCase(words.get(0))) {
                if (words.size() != 2) {
                    errors.add(where + ": expected fallback_font and one path= or family= value");
                    continue;
                }
                if (accumulator.fallbackFonts.size() >= MAX_FALLBACK_FONTS) {
                    errors.add(where + ": fallback_font count exceeds " + MAX_FALLBACK_FONTS);
                    continue;
                }
                FaceSpec fallback = parseSource(words.get(1), where, errors);
                if (fallback != null) accumulator.fallbackFonts.add(fallback);
                continue;
            }
            if (words.size() != 2) {
                errors.add(where + ": expected a face and one path= or family= value");
                continue;
            }
            Face face = face(words.get(0));
            if (face == null) {
                errors.add(where + ": unknown directive '" + words.get(0) + "'");
                continue;
            }
            FaceSpec source = parseSource(words.get(1), where, errors);
            if (source != null) accumulator.faces.put(face, source);
        }
    }

    /** Records a font_features/font_variations line that named a map, last duplicate winning. */
    private static void putNamed(@NonNull Map<String, NamedSetting> settings,
                                 @NonNull String name, @NonNull String value,
                                 @NonNull String where, @NonNull List<String> errors) {
        String key = name.toLowerCase(Locale.US);
        if (settings.size() >= MAX_NAMED_TARGETS && !settings.containsKey(key)) {
            errors.add(where + ": named font target count exceeds " + MAX_NAMED_TARGETS);
            return;
        }
        settings.remove(key);
        settings.put(key, new NamedSetting(name, value, where));
    }

    @NonNull
    private static Result finish(@NonNull Accumulator accumulator) {
        // A map may be declared in a file loaded after the font_features line that names it, so
        // undeclared names can only be reported once every file of the load has been parsed.
        resolveNamed(accumulator.namedFeatures, accumulator.symbolMapNames, "font_features",
            accumulator.errors);
        resolveNamed(accumulator.namedVariations, accumulator.symbolMapNames, "font_variations",
            accumulator.errors);
        String sharedFeatures = accumulator.fontFeatures.get(FontTarget.SYMBOLS);
        String sharedVariations = accumulator.fontVariations.get(FontTarget.SYMBOLS);
        List<SymbolMapSpec> symbolMaps = new ArrayList<>(accumulator.symbolMaps.size());
        LinkedHashMap<String, String> namedFeatures = new LinkedHashMap<>();
        LinkedHashMap<String, String> namedVariations = new LinkedHashMap<>();
        for (Map.Entry<String, NamedSetting> entry : accumulator.namedFeatures.entrySet())
            namedFeatures.put(entry.getKey(), entry.getValue().settings);
        for (Map.Entry<String, NamedSetting> entry : accumulator.namedVariations.entrySet())
            namedVariations.put(entry.getKey(), entry.getValue().settings);
        for (SymbolMapSpec map : accumulator.symbolMaps) {
            String key = map.name == null ? null : map.name.toLowerCase(Locale.US);
            String features = key == null ? null : namedFeatures.get(key);
            String variations = key == null ? null : namedVariations.get(key);
            symbolMaps.add(new SymbolMapSpec(map.name, map.ranges, map.font,
                features == null ? sharedFeatures : features,
                variations == null ? sharedVariations : variations));
        }
        return new Result(accumulator.filePresent, accumulator.faces, symbolMaps,
            accumulator.fallbackFonts, accumulator.ligaturePolicy, accumulator.fontFeatures,
            accumulator.fontVariations, namedFeatures, namedVariations, accumulator.metrics,
            accumulator.boxDrawing, accumulator.boxDrawingScale, accumulator.powerlineSymbols,
            accumulator.errors);
    }

    private static void resolveNamed(@NonNull Map<String, NamedSetting> settings,
                                     @NonNull Map<String, String> declared,
                                     @NonNull String directive, @NonNull List<String> errors) {
        Iterator<Map.Entry<String, NamedSetting>> entries = settings.entrySet().iterator();
        while (entries.hasNext()) {
            NamedSetting setting = entries.next().getValue();
            if (declared.containsKey(setting.name.toLowerCase(Locale.US))) continue;
            errors.add(setting.where + ": " + directive + " names undeclared symbol map '"
                + setting.name + "'");
            entries.remove();
        }
    }

    private static boolean isSymbolMapName(@NonNull String name) {
        if (name.isEmpty() || name.length() > MAX_SYMBOL_MAP_NAME_CHARS) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-')) return false;
        }
        return true;
    }

    @Nullable
    private static Metric metric(@NonNull String value) {
        try {
            return Metric.valueOf(value.toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static BoxDrawingScale parseBoxDrawingScale(@NonNull List<String> words, int start,
                                                        @NonNull String where,
                                                        @NonNull List<String> errors) {
        List<Double> values = new ArrayList<>();
        for (int i = start; i < words.size(); i++) {
            for (String item : words.get(i).split(",", -1)) {
                if (item.isEmpty()) continue;
                if (values.size() >= BOX_DRAWING_SCALE_VALUES) {
                    errors.add(where + ": expected box_drawing_scale with "
                        + BOX_DRAWING_SCALE_VALUES + " comma or space separated values");
                    return null;
                }
                double value;
                try {
                    value = Double.parseDouble(item);
                } catch (NumberFormatException e) {
                    value = Double.NaN;
                }
                if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0d
                    || value > MAX_BOX_DRAWING_SCALE) {
                    errors.add(where + ": box_drawing_scale values must be greater than 0 and at"
                        + " most " + MAX_BOX_DRAWING_SCALE);
                    return null;
                }
                values.add(value);
            }
        }
        if (values.size() != BOX_DRAWING_SCALE_VALUES) {
            errors.add(where + ": expected box_drawing_scale with " + BOX_DRAWING_SCALE_VALUES
                + " comma or space separated values");
            return null;
        }
        return new BoxDrawingScale(values.get(0), values.get(1), values.get(2), values.get(3));
    }

    @Nullable
    private static MetricAdjustment parseMetricAdjustment(@NonNull String text,
                                                          @NonNull String where,
                                                          @NonNull List<String> errors) {
        MetricUnit unit = MetricUnit.PIXEL;
        String number = text;
        if (text.endsWith("%")) {
            unit = MetricUnit.PERCENT;
            number = text.substring(0, text.length() - 1);
        } else if (text.toLowerCase(Locale.US).endsWith("px")) {
            number = text.substring(0, text.length() - 2);
        }
        double value;
        try {
            value = Double.parseDouble(number);
        } catch (NumberFormatException e) {
            value = Double.NaN;
        }
        boolean valid = !Double.isNaN(value) && !Double.isInfinite(value)
            && (unit == MetricUnit.PERCENT ? value >= 10d && value <= 500d
                : value >= -256d && value <= 256d);
        if (!valid) {
            errors.add(where + ": metric must be -256..256 pixels or 10%..500%");
            return null;
        }
        return new MetricAdjustment(value, unit);
    }

    @Nullable
    private static FontTarget fontTarget(@NonNull String value) {
        try {
            return FontTarget.valueOf(value.toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static String parseVariationSettings(@NonNull List<String> words, int start,
                                                 @NonNull String where,
                                                 @NonNull List<String> errors) {
        LinkedHashMap<String, Double> axes = new LinkedHashMap<>();
        for (int i = start; i < words.size(); i++) {
            for (String item : words.get(i).split(",", -1)) {
                int equals = item.indexOf('=');
                String tag = equals < 0 ? item : item.substring(0, equals);
                if (equals < 0 || !isFeatureTag(tag) || equals == item.length() - 1
                    || "none".equalsIgnoreCase(item)) {
                    errors.add(where + ": axes must use a four-character tag=value form");
                    return null;
                }
                double value;
                try {
                    value = Double.parseDouble(item.substring(equals + 1));
                } catch (NumberFormatException e) {
                    value = Double.NaN;
                }
                if (Double.isNaN(value) || Double.isInfinite(value)
                    || value < -1_000_000d || value > 1_000_000d) {
                    errors.add(where
                        + ": axis values must be finite and between -1000000 and 1000000");
                    return null;
                }
                if (axes.containsKey(tag)) axes.remove(tag);
                axes.put(tag, value);
                if (axes.size() > MAX_VARIATIONS_PER_TARGET) {
                    errors.add(where + ": variation axis count exceeds "
                        + MAX_VARIATIONS_PER_TARGET);
                    return null;
                }
            }
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Double> axis : axes.entrySet()) {
            if (result.length() > 0) result.append(", ");
            String value = BigDecimal.valueOf(axis.getValue()).stripTrailingZeros().toPlainString();
            result.append('\'').append(axis.getKey()).append("' ").append(value);
        }
        return result.toString();
    }

    @Nullable
    private static String parseFeatureSettings(@NonNull List<String> words, int start,
                                               @NonNull String where,
                                               @NonNull List<String> errors) {
        LinkedHashMap<String, Integer> features = new LinkedHashMap<>();
        for (int i = start; i < words.size(); i++) {
            for (String item : words.get(i).split(",", -1)) {
                if (item.isEmpty() || "none".equalsIgnoreCase(item)) {
                    errors.add(where + ": invalid OpenType feature '" + item + "'");
                    return null;
                }
                boolean disabled = item.charAt(0) == '-';
                int tagStart = item.charAt(0) == '+' || disabled ? 1 : 0;
                int equals = item.indexOf('=', tagStart);
                String tag = equals < 0 ? item.substring(tagStart) : item.substring(tagStart, equals);
                if (!isFeatureTag(tag)) {
                    errors.add(where + ": feature tags must be four ASCII letters or digits");
                    return null;
                }
                int value = disabled ? 0 : 1;
                if (equals >= 0) {
                    if (tagStart != 0) {
                        errors.add(where + ": feature values cannot also use + or -");
                        return null;
                    }
                    try {
                        value = Integer.parseInt(item.substring(equals + 1));
                    } catch (NumberFormatException e) {
                        value = -1;
                    }
                    if (value < 0 || value > 65535) {
                        errors.add(where + ": feature values must be between 0 and 65535");
                        return null;
                    }
                }
                if (features.containsKey(tag)) features.remove(tag);
                features.put(tag, value);
                if (features.size() > MAX_FEATURES_PER_TARGET) {
                    errors.add(where + ": feature count exceeds " + MAX_FEATURES_PER_TARGET);
                    return null;
                }
            }
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Integer> feature : features.entrySet()) {
            if (result.length() > 0) result.append(", ");
            result.append('\'').append(feature.getKey()).append("' ").append(feature.getValue());
        }
        return result.toString();
    }

    private static boolean isFeatureTag(@NonNull String tag) {
        if (tag.length() != 4) return false;
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    @Nullable
    private static FaceSpec parseSource(@NonNull String source, @NonNull String where,
                                        @NonNull List<String> errors) {
        SourceType type;
        String value;
        if (source.startsWith("path=")) {
            type = SourceType.PATH;
            value = source.substring(5);
            if (!(value.startsWith("~/") || value.startsWith("/"))) {
                errors.add(where + ": font paths must be absolute or start with ~/");
                return null;
            }
        } else if (source.startsWith("family=")) {
            type = SourceType.FAMILY;
            value = source.substring(7).trim();
            if (value.length() > MAX_FAMILY_CHARS) {
                errors.add(where + ": family name exceeds " + MAX_FAMILY_CHARS + " characters");
                return null;
            }
        } else {
            errors.add(where + ": font source must start with path= or family=");
            return null;
        }
        if (value.isEmpty()) {
            errors.add(where + ": font source is empty");
            return null;
        }
        return new FaceSpec(type, value);
    }

    @Nullable
    private static List<CodePointRange> parseRanges(@NonNull String value, @NonNull String where,
                                                     @NonNull List<String> errors) {
        List<CodePointRange> result = new ArrayList<>();
        for (String item : value.replace('\u2013', '-').split(",", -1)) {
            int separator = item.indexOf('-', 2);
            String firstText = separator < 0 ? item : item.substring(0, separator);
            String lastText = separator < 0 ? item : item.substring(separator + 1);
            int first = parseCodePoint(firstText);
            int last = parseCodePoint(lastText);
            if (first < 1 || last < first || last > Character.MAX_CODE_POINT
                || (first <= Character.MAX_SURROGATE && last >= Character.MIN_SURROGATE)) {
                errors.add(where + ": invalid Unicode range '" + item + "'");
                return null;
            }
            result.add(new CodePointRange(first, last));
        }
        return result;
    }

    private static int parseCodePoint(@NonNull String value) {
        if (value.length() < 3 || !(value.startsWith("U+") || value.startsWith("u+"))) return -1;
        try {
            return Integer.parseInt(value.substring(2), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @NonNull
    static String expandPath(@NonNull String path) {
        if (path.startsWith("~/")) return TermuxConstants.TERMUX_HOME_DIR_PATH + path.substring(1);
        return path;
    }

    @Nullable
    private static Face face(@NonNull String directive) {
        switch (directive.toLowerCase(Locale.US)) {
            case "font_family": return Face.REGULAR;
            case "bold_font": return Face.BOLD;
            case "italic_font": return Face.ITALIC;
            case "bold_italic_font": return Face.BOLD_ITALIC;
            default: return null;
        }
    }

    /** Split one config line, allowing quotes and backslash escapes; # starts a comment. */
    @NonNull
    private static List<String> words(@NonNull String line) {
        List<String> result = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        boolean started = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaping) {
                word.append(c);
                escaping = false;
                started = true;
            } else if (c == '\\') {
                escaping = true;
                started = true;
            } else if (quote != 0) {
                if (c == quote) quote = 0;
                else word.append(c);
                started = true;
            } else if (c == '\'' || c == '"') {
                quote = c;
                started = true;
            } else if (c == '#') {
                break;
            } else if (Character.isWhitespace(c)) {
                if (started) {
                    result.add(word.toString());
                    word.setLength(0);
                    started = false;
                }
            } else {
                word.append(c);
                started = true;
            }
        }
        if (escaping) throw new IllegalArgumentException("trailing escape");
        if (quote != 0) throw new IllegalArgumentException("unterminated quote");
        if (started) result.add(word.toString());
        return result;
    }
}
