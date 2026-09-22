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
 * Bounded parser for the terminal's font configuration, read from three places in one pass.
 *
 * <p>In load order: {@code ~/.config/kitty/kitty.conf}, then {@code ~/.termux/fonts.d/*.conf} in
 * ascending filename order, then {@code ~/.termux/fonts.conf}. One accumulator carries the whole
 * load, so the existing last-duplicate-wins rule makes the user's own files beat everything a
 * kitty-shaped tool wrote — and beat the app's own managed drop-in as before.
 *
 * <p>kitty.conf is a whole terminal's configuration, only a handful of whose directives are about
 * fonts, so it is read leniently: a directive this parser does not know is skipped in silence
 * there and reported everywhere else. A malformed <em>font</em> directive is reported wherever it
 * is written.
 */
public final class TerminalFontConfig {

    public static final String FILE_NAME = "fonts.conf";
    public static final String FILE_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + FILE_NAME;
    public static final String DROP_IN_DIR_NAME = "fonts.d";
    public static final String DROP_IN_DIR_PATH =
        TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + DROP_IN_DIR_NAME;
    /** kitty's own config directory, as kitty itself resolves it without an environment. */
    public static final String KITTY_DIR_PATH =
        TermuxConstants.TERMUX_HOME_DIR_PATH + "/.config/kitty";
    public static final String KITTY_FILE_NAME = "kitty.conf";
    public static final String KITTY_FILE_PATH = KITTY_DIR_PATH + "/" + KITTY_FILE_NAME;
    private static final String DROP_IN_SUFFIX = ".conf";
    private static final long MAX_FILE_BYTES = 64 * 1024;
    private static final int MAX_DROP_IN_FILES = 32;
    /** Aggregate budget for the drop-ins only: the user's own fonts.conf is never squeezed out. */
    private static final long MAX_DROP_IN_TOTAL_BYTES = 256 * 1024;
    /** Aggregate budget for everything one kitty.conf pulls in with {@code include}. */
    private static final long MAX_INCLUDE_TOTAL_BYTES = 256 * 1024;
    private static final int MAX_INCLUDE_FILES = 16;
    private static final int MAX_LINES = 512;
    /**
     * kitty.conf is a whole terminal's configuration and kitty's own generated sample runs to
     * thousands of lines, so it is bounded by its byte allowance rather than by the line count a
     * file of font directives needs.
     */
    private static final int MAX_KITTY_LINES = 8192;
    private static final int MAX_LINE_CHARS = 4096;
    private static final int MAX_FAMILY_CHARS = 128;
    private static final int MAX_SYMBOL_MAPS = 256;
    private static final int MAX_SYMBOL_RANGES = 1024;
    private static final int MAX_SYMBOL_MAP_NAME_CHARS = 32;
    private static final int MAX_NARROW_SYMBOL_RULES = 64;
    /** One cell plus kitty's {@code MAX_NUM_EXTRA_GLYPHS_PUA}. */
    private static final int MAX_NARROW_SYMBOL_CELLS = 5;
    private static final int MAX_NAMED_TARGETS = 256;
    private static final int MAX_FALLBACK_FONTS = 8;
    private static final int MAX_FEATURES_PER_TARGET = 32;
    private static final int MAX_VARIATIONS_PER_TARGET = 16;
    private static final int BOX_DRAWING_SCALE_VALUES = 4;
    private static final int MAX_BOX_DRAWING_SCALE = 8;
    private static final String NAME_PREFIX = "name=";
    private static final String PATH_PREFIX = "path=";
    private static final String FAMILY_PREFIX = "family=";
    /** kitty's four include forms; only the plain one can be honoured without a shell. */
    private static final List<String> INCLUDE_DIRECTIVES =
        Arrays.asList("include", "globinclude", "envinclude", "geninclude");

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

    /**
     * One {@code narrow_symbols} line: the most cells a private-use symbol in these ranges may be
     * drawn across. Later lines win over earlier ones for an overlapping code point.
     */
    public static final class NarrowSymbolsSpec {
        @NonNull public final List<CodePointRange> ranges;
        public final int cells;

        private NarrowSymbolsSpec(@NonNull List<CodePointRange> ranges, int cells) {
            this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
            this.cells = cells;
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
        /** {@code narrow_symbols} ceilings, in declaration order. */
        @NonNull public final List<NarrowSymbolsSpec> narrowSymbols;
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
                       @NonNull List<NarrowSymbolsSpec> narrowSymbols,
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
            this.narrowSymbols =
                Collections.unmodifiableList(new ArrayList<>(narrowSymbols));
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

    /** One {@code font_features}/{@code font_variations} line whose target is not a face. */
    private static final class NamedSetting {
        @NonNull final String name;
        @NonNull final String settings;
        @NonNull final String where;
        /** A target that matches nothing is only worth reporting outside kitty.conf. */
        final boolean lenient;

        NamedSetting(@NonNull String name, @NonNull String settings, @NonNull String where,
                     boolean lenient) {
            this.name = name;
            this.settings = settings;
            this.where = where;
            this.lenient = lenient;
        }
    }

    /**
     * One file of a load: how it names itself in errors, how strict it is, and where its
     * {@code include} lines resolve.
     *
     * <p>{@code includeDir} is null once includes are spent, which is what keeps kitty's
     * {@code include} one level deep.
     */
    private static final class Source {
        @NonNull final String prefix;
        final boolean lenient;
        @Nullable final File includeDir;

        Source(@NonNull String prefix, boolean lenient, @Nullable File includeDir) {
            this.prefix = prefix;
            this.lenient = lenient;
            this.includeDir = includeDir;
        }
    }

    /** The strict, include-less source every {@code ~/.termux} file is parsed as. */
    @NonNull
    private static Source strict(@Nullable String prefix) {
        return new Source(prefix == null ? "" : prefix, false, null);
    }

    /** Mutable state shared by every file of one load, so later files override earlier ones. */
    private static final class Accumulator {
        final EnumMap<Face, FaceSpec> faces = new EnumMap<>(Face.class);
        final List<SymbolMapSpec> symbolMaps = new ArrayList<>();
        final List<NarrowSymbolsSpec> narrowSymbols = new ArrayList<>();
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
        long includeBudget = MAX_INCLUDE_TOTAL_BYTES;
        int includeCount;
    }

    private TerminalFontConfig() {}

    @NonNull
    public static Result load() {
        return load(new File(KITTY_FILE_PATH), new File(DROP_IN_DIR_PATH), new File(FILE_PATH));
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
        return load(null, dropInDir, file);
    }

    /**
     * Reads kitty.conf, then the drop-ins, then {@code fonts.conf} into one accumulator.
     *
     * <p>kitty.conf is read outside the drop-in budget, on its own allowance, exactly as
     * {@code fonts.conf} is: neither the app's fragments nor a pile of third-party drop-ins can
     * push another source out of the load.
     */
    @NonNull
    static Result load(@Nullable File kittyConf, @NonNull File dropInDir, @NonNull File file) {
        Accumulator accumulator = new Accumulator();
        if (kittyConf != null && kittyConf.exists()) {
            String prefix = KITTY_FILE_NAME + ": ";
            String kitty = read(kittyConf, prefix, MAX_KITTY_LINES, accumulator.errors);
            if (kitty != null) {
                // A kitty.conf alone is an active configuration for the same reason a drop-in is:
                // the loader still falls back for every face it leaves unset.
                accumulator.filePresent = true;
                File parent = kittyConf.getAbsoluteFile().getParentFile();
                parse(accumulator, kitty, new Source(prefix, true, parent));
            }
        }
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
            parse(accumulator, content, strict(prefix));
        }
        // The user's own file is read outside that budget and keeps its own 64 KiB allowance, so
        // no set of drop-ins can push ~/.termux/fonts.conf out of the load.
        if (!file.exists()) return finish(accumulator);
        accumulator.filePresent = true;
        String content = read(file, "", accumulator.errors);
        if (content != null) parse(accumulator, content, strict(null));
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

    @Nullable
    private static String read(@NonNull File file, @NonNull String prefix,
                               @NonNull List<String> errors) {
        return read(file, prefix, MAX_LINES, errors);
    }

    /** Reads one bounded config file; null means the file was skipped and errors explains why. */
    @Nullable
    private static String read(@NonNull File file, @NonNull String prefix, int maxLines,
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
                if (++count > maxLines) {
                    errors.add(prefix + "font config exceeds " + maxLines + " lines");
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
        parse(accumulator, content, strict(null));
        return finish(accumulator);
    }

    /** Parses one file into the shared accumulator; the source names it in every error. */
    private static void parse(@NonNull Accumulator accumulator, @NonNull String content,
                              @NonNull Source source) {
        List<String> errors = accumulator.errors;
        String[] lines = content.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final int firstLine = i + 1;
            // kitty's line syntax: a line is a comment only when its first non-blank character is
            // '#', and a following line that starts with '\' continues this one.
            String line = lines[i].trim();
            while (i + 1 < lines.length) {
                String next = lines[i + 1];
                int at = 0;
                while (at < next.length() && Character.isWhitespace(next.charAt(at))) at++;
                if (at >= next.length() || next.charAt(at) != '\\') break;
                line = line + next.substring(at + 1);
                i++;
            }
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String where = source.prefix + "line " + firstLine;
            List<String> words;
            try {
                words = words(line);
            } catch (IllegalArgumentException e) {
                errors.add(where + ": " + e.getMessage());
                continue;
            }
            if (words.isEmpty()) continue;
            String directive = words.get(0).toLowerCase(Locale.US);
            if (INCLUDE_DIRECTIVES.contains(directive)) {
                // Only a plain include can be honoured: a glob, an environment variable or a
                // generated block all need a shell this parser does not have at load time.
                if ("include".equals(directive) && source.includeDir != null)
                    include(accumulator, source, words, where);
                else if (!source.lenient)
                    errors.add(where + ": unknown directive '" + words.get(0) + "'");
                continue;
            }
            if ("modify_font".equals(directive)) {
                // kitty's modify_font size scales one named font rather than the cell, which has
                // no meaning for a terminal that draws every face at the one cell size.
                if (words.size() >= 3 && "size".equalsIgnoreCase(words.get(1))) {
                    if (!source.lenient)
                        errors.add(where + ": modify_font size is not supported");
                    continue;
                }
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
            if ("font_variations".equals(directive)) {
                if (words.size() < 3) {
                    errors.add(where + ": expected font_variations target and one or more axes");
                    continue;
                }
                FontTarget target = fontTarget(words.get(1));
                String name = target == null ? words.get(1) : null;
                if (name != null && !isTargetName(name)) {
                    errors.add(where + ": variation target must be regular, bold, italic,"
                        + " bold_italic, symbols, a symbol_map name or a configured family");
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
                else putNamed(accumulator.namedVariations, name, settings, where, source, errors);
                continue;
            }
            if ("font_features".equals(directive)) {
                // kitty's own default value: the directive exists but names nothing.
                if (words.size() == 2 && "none".equalsIgnoreCase(words.get(1))) continue;
                if (words.size() < 3) {
                    errors.add(where + ": expected font_features target and one or more features");
                    continue;
                }
                FontTarget target = fontTarget(words.get(1));
                String name = target == null ? words.get(1) : null;
                if (name != null && !isTargetName(name)) {
                    errors.add(where + ": feature target must be regular, bold, italic,"
                        + " bold_italic, symbols, a symbol_map name or a configured family");
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
                else putNamed(accumulator.namedFeatures, name, settings, where, source, errors);
                continue;
            }
            if ("disable_ligatures".equals(directive)) {
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
            if ("box_drawing".equals(directive)) {
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
            if ("box_drawing_scale".equals(directive)) {
                BoxDrawingScale scale = parseBoxDrawingScale(words, 1, where, errors);
                if (scale != null) accumulator.boxDrawingScale = scale;
                continue;
            }
            if ("powerline_symbols".equals(directive)) {
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
            if ("narrow_symbols".equals(directive)) {
                // narrow_symbols <ranges> [cells], kitty's own syntax. Without a count the ranges
                // are pinned to a single cell, which is the point of the directive.
                if (words.size() < 2 || words.size() > 3) {
                    errors.add(where + ": expected narrow_symbols ranges and an optional cell count");
                    continue;
                }
                if (accumulator.narrowSymbols.size() >= MAX_NARROW_SYMBOL_RULES) {
                    errors.add(where + ": narrow_symbols count exceeds " + MAX_NARROW_SYMBOL_RULES);
                    continue;
                }
                int cells = 1;
                if (words.size() == 3) {
                    try {
                        cells = Integer.parseInt(words.get(2));
                    } catch (NumberFormatException e) {
                        cells = -1;
                    }
                    if (cells < 1 || cells > MAX_NARROW_SYMBOL_CELLS) {
                        errors.add(where + ": narrow_symbols cell count must be 1 to "
                            + MAX_NARROW_SYMBOL_CELLS);
                        continue;
                    }
                }
                List<CodePointRange> narrowRanges = parseRanges(words.get(1), where, errors);
                if (narrowRanges == null) continue;
                if (accumulator.symbolRangeCount + narrowRanges.size() > MAX_SYMBOL_RANGES) {
                    errors.add(where + ": symbol range count exceeds " + MAX_SYMBOL_RANGES);
                    continue;
                }
                accumulator.symbolRangeCount += narrowRanges.size();
                accumulator.narrowSymbols.add(new NarrowSymbolsSpec(narrowRanges, cells));
                continue;
            }
            if ("symbol_map".equals(directive)) {
                String name = null;
                int names = 0;
                boolean prefixed = false;
                List<String> arguments = new ArrayList<>();
                for (int word = 1; word < words.size(); word++) {
                    String value = words.get(word);
                    if (value.startsWith(NAME_PREFIX)) {
                        name = value.substring(NAME_PREFIX.length());
                        names++;
                        prefixed = true;
                    } else {
                        if (value.startsWith(PATH_PREFIX) || value.startsWith(FAMILY_PREFIX))
                            prefixed = true;
                        arguments.add(value);
                    }
                }
                if (names > 1) {
                    errors.add(where + ": symbol_map accepts one name= value");
                    continue;
                }
                // kitty writes the family as bare trailing words; our own path=/family=/name=
                // prefixes, when any of them is present, keep the two-argument shape they had.
                if (prefixed ? arguments.size() != 2 : arguments.size() < 2) {
                    errors.add(where + (prefixed
                        ? ": expected symbol_map ranges and one path= or family= value"
                        : ": expected symbol_map ranges and a font family"));
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
                // kitty: ' '.join(parts[1:]) — the rest of the line is one family name.
                FaceSpec font = prefixed ? parseSource(arguments.get(1), where, errors)
                    : parseFamily(join(arguments, 1), where, errors);
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
            if ("fallback_font".equals(directive)) {
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
            Face face = face(directive);
            if (face != null) {
                parseFace(accumulator, face, words, where);
                continue;
            }
            // kitty.conf is hundreds of directives about everything but fonts; only a file that
            // exists purely to configure them can call an unknown one a mistake.
            if (!source.lenient)
                errors.add(where + ": unknown directive '" + words.get(0) + "'");
        }
    }

    /**
     * One {@code font_family} / {@code bold_font} / {@code italic_font} / {@code bold_italic_font}
     * line, in any of the three shapes kitty accepts plus our {@code path=} extension.
     *
     * <p>{@code auto} leaves the slot unset, which is exactly kitty's "let the terminal choose":
     * the loader then falls back to {@code font.ttf} and finally to the platform monospace face. A
     * value whose first word carries no {@code =} is a bare family name, spaces and all. Otherwise
     * every word is a {@code key=value} pair: {@code family} and our {@code path} name the face,
     * {@code postscript_name} and {@code full_name} are read as a family name because a family is
     * the only thing Android can be asked for, {@code style} is already carried by the slot the
     * directive picked, {@code features} is this face's {@code font_features} and any remaining
     * {@code tag=number} is one of its {@code font_variations} axes.
     */
    private static void parseFace(@NonNull Accumulator accumulator, @NonNull Face face,
                                  @NonNull List<String> words, @NonNull String where) {
        List<String> errors = accumulator.errors;
        if (words.size() < 2) {
            errors.add(where + ": expected a face and one path= or family= value");
            return;
        }
        if (words.size() == 2 && "auto".equalsIgnoreCase(words.get(1))) {
            accumulator.faces.remove(face);
            return;
        }
        if (words.get(1).indexOf('=') < 0) {
            FaceSpec bare = parseFamily(join(words, 1), where, errors);
            if (bare != null) accumulator.faces.put(face, bare);
            return;
        }
        FaceSpec named = null;
        FaceSpec described = null;
        String featureText = null;
        LinkedHashMap<String, String> axes = new LinkedHashMap<>();
        for (int word = 1; word < words.size(); word++) {
            String item = words.get(word);
            int equals = item.indexOf('=');
            if (equals <= 0) {
                errors.add(where + ": expected key=value in the font spec, not '" + item + "'");
                return;
            }
            String key = item.substring(0, equals).toLowerCase(Locale.US);
            String value = item.substring(equals + 1);
            if ("path".equals(key) || "family".equals(key)) {
                named = parseSource(item, where, errors);
                if (named == null) return;
            } else if ("postscript_name".equals(key) || "full_name".equals(key)) {
                described = parseFamily(value, where, errors);
                if (described == null) return;
            } else if ("style".equals(key) || "variable_name".equals(key)) {
                continue;
            } else if ("features".equals(key)) {
                featureText = value;
            } else if (isFeatureTag(key) && isNumber(value)) {
                axes.remove(key);
                axes.put(key, value);
            } else {
                errors.add(where + ": unknown font spec key '" + key + "'");
                return;
            }
        }
        FaceSpec resolved = named != null ? named : described;
        if (resolved == null) {
            errors.add(where + ": font source must start with path= or family=");
            return;
        }
        accumulator.faces.put(face, resolved);
        FontTarget target = FontTarget.valueOf(face.name());
        if (featureText != null) {
            String settings = parseFeatureSettings(whitespaceWords(featureText), 0, where, errors);
            if (settings != null) accumulator.fontFeatures.put(target, settings);
        }
        if (!axes.isEmpty()) {
            List<String> pairs = new ArrayList<>(axes.size());
            for (Map.Entry<String, String> axis : axes.entrySet())
                pairs.add(axis.getKey() + "=" + axis.getValue());
            String settings = parseVariationSettings(pairs, 0, where, errors);
            if (settings != null) accumulator.fontVariations.put(target, settings);
        }
    }

    /** Reads one {@code include}, resolved against the kitty config directory and bounded. */
    private static void include(@NonNull Accumulator accumulator, @NonNull Source source,
                                @NonNull List<String> words, @NonNull String where) {
        List<String> errors = accumulator.errors;
        if (words.size() != 2 || source.includeDir == null) {
            errors.add(where + ": expected include and one path");
            return;
        }
        if (accumulator.includeCount >= MAX_INCLUDE_FILES) {
            errors.add(where + ": include count exceeds " + MAX_INCLUDE_FILES);
            return;
        }
        File target = new File(expandPath(words.get(1)));
        if (!target.isAbsolute()) target = new File(source.includeDir, words.get(1));
        if (!target.exists()) {
            errors.add(where + ": include " + words.get(1) + " does not exist");
            return;
        }
        // The rule fonts.d already follows: a config file may not be a link out of its directory.
        try {
            if (!isInside(target.getCanonicalFile(), source.includeDir.getCanonicalFile())) {
                errors.add(where + ": include " + words.get(1)
                    + " resolves outside the kitty config directory");
                return;
            }
        } catch (IOException e) {
            errors.add(where + ": cannot resolve include " + words.get(1) + ": " + e.getMessage());
            return;
        }
        if (target.length() > accumulator.includeBudget) {
            errors.add(where + ": include set exceeds " + MAX_INCLUDE_TOTAL_BYTES + " bytes");
            return;
        }
        String prefix = source.prefix + words.get(1) + ": ";
        String content = read(target, prefix, MAX_KITTY_LINES, errors);
        if (content == null) return;
        accumulator.includeBudget -= target.length();
        accumulator.includeCount++;
        // A null include directory is what stops this at one level; kitty nests without limit.
        parse(accumulator, content, new Source(prefix, source.lenient, null));
    }

    private static boolean isInside(@NonNull File file, @NonNull File root) {
        for (File at = file.getParentFile(); at != null; at = at.getParentFile())
            if (at.equals(root)) return true;
        return false;
    }

    /** Records a font_features/font_variations line whose target is not a face, last one winning. */
    private static void putNamed(@NonNull Map<String, NamedSetting> settings,
                                 @NonNull String name, @NonNull String value,
                                 @NonNull String where, @NonNull Source source,
                                 @NonNull List<String> errors) {
        String key = name.toLowerCase(Locale.US);
        if (settings.size() >= MAX_NAMED_TARGETS && !settings.containsKey(key)) {
            errors.add(where + ": named font target count exceeds " + MAX_NAMED_TARGETS);
            return;
        }
        settings.remove(key);
        settings.put(key, new NamedSetting(name, value, where, source.lenient));
    }

    @NonNull
    private static Result finish(@NonNull Accumulator accumulator) {
        // A map or a face may be declared in a file loaded after the font_features line that names
        // it, so a target can only be matched once every file of the load has been parsed.
        Map<String, Face> faceFamilies = new LinkedHashMap<>();
        for (Map.Entry<Face, FaceSpec> entry : accumulator.faces.entrySet())
            if (entry.getValue().type == SourceType.FAMILY)
                faceFamilies.put(entry.getValue().value.toLowerCase(Locale.US), entry.getKey());
        Map<String, String> symbolFamilies = new LinkedHashMap<>();
        for (SymbolMapSpec map : accumulator.symbolMaps)
            if (map.font.type == SourceType.FAMILY)
                symbolFamilies.put(map.font.value.toLowerCase(Locale.US), map.font.value);
        LinkedHashMap<String, String> familyFeatures = new LinkedHashMap<>();
        LinkedHashMap<String, String> familyVariations = new LinkedHashMap<>();
        resolveNamed(accumulator.namedFeatures, accumulator.symbolMapNames, faceFamilies,
            symbolFamilies, accumulator.fontFeatures, familyFeatures, "font_features",
            accumulator.errors);
        resolveNamed(accumulator.namedVariations, accumulator.symbolMapNames, faceFamilies,
            symbolFamilies, accumulator.fontVariations, familyVariations, "font_variations",
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
            String familyKey = map.font.type == SourceType.FAMILY
                ? map.font.value.toLowerCase(Locale.US) : null;
            String features = key == null ? null : namedFeatures.get(key);
            if (features == null && familyKey != null) features = familyFeatures.get(familyKey);
            String variations = key == null ? null : namedVariations.get(key);
            if (variations == null && familyKey != null)
                variations = familyVariations.get(familyKey);
            symbolMaps.add(new SymbolMapSpec(map.name, map.ranges, map.font,
                features == null ? sharedFeatures : features,
                variations == null ? sharedVariations : variations));
        }
        return new Result(accumulator.filePresent, accumulator.faces, symbolMaps,
            accumulator.narrowSymbols,
            accumulator.fallbackFonts, accumulator.ligaturePolicy, accumulator.fontFeatures,
            accumulator.fontVariations, namedFeatures, namedVariations, accumulator.metrics,
            accumulator.boxDrawing, accumulator.boxDrawingScale, accumulator.powerlineSymbols,
            accumulator.errors);
    }

    /**
     * Settles every target that is not a face: a {@code symbol_map name=} keeps its own entry, and
     * anything else gets one more chance against the family names this load configured.
     *
     * <p>kitty's target is a PostScript name, which is the closest thing a kitty user has to "this
     * font", so a target that matches a configured family is applied to the face or the symbol
     * maps that use it. An explicit face target always outranks a family match. What matches
     * nothing is a mistake worth reporting outside kitty.conf and noise inside it.
     */
    private static void resolveNamed(@NonNull Map<String, NamedSetting> settings,
                                     @NonNull Map<String, String> declared,
                                     @NonNull Map<String, Face> faceFamilies,
                                     @NonNull Map<String, String> symbolFamilies,
                                     @NonNull Map<FontTarget, String> faceTargets,
                                     @NonNull Map<String, String> familyTargets,
                                     @NonNull String directive, @NonNull List<String> errors) {
        Iterator<Map.Entry<String, NamedSetting>> entries = settings.entrySet().iterator();
        while (entries.hasNext()) {
            NamedSetting setting = entries.next().getValue();
            String key = setting.name.toLowerCase(Locale.US);
            if (declared.containsKey(key)) continue;
            boolean matched = false;
            Face face = faceFamilies.get(key);
            if (face != null) {
                FontTarget target = FontTarget.valueOf(face.name());
                if (!faceTargets.containsKey(target)) faceTargets.put(target, setting.settings);
                matched = true;
            }
            if (symbolFamilies.containsKey(key)) {
                familyTargets.put(key, setting.settings);
                matched = true;
            }
            if (!matched && !setting.lenient)
                errors.add(setting.where + ": " + directive + " target '" + setting.name
                    + "' matches no symbol map or configured family");
            entries.remove();
        }
    }

    /** A font_features/font_variations target may be a symbol map name or a family name. */
    private static boolean isTargetName(@NonNull String name) {
        return !name.isEmpty() && name.length() <= MAX_FAMILY_CHARS;
    }

    private static boolean isNumber(@NonNull String value) {
        if (value.isEmpty()) return false;
        try {
            double parsed = Double.parseDouble(value);
            return !Double.isNaN(parsed) && !Double.isInfinite(parsed);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** kitty joins the rest of the line with single spaces; so does this. */
    @NonNull
    private static String join(@NonNull List<String> words, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < words.size(); i++) {
            if (result.length() > 0) result.append(' ');
            result.append(words.get(i));
        }
        return result.toString();
    }

    @NonNull
    private static List<String> whitespaceWords(@NonNull String value) {
        List<String> result = new ArrayList<>();
        for (String item : value.trim().split("\\s+")) if (!item.isEmpty()) result.add(item);
        return result;
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
        if (source.startsWith(FAMILY_PREFIX))
            return parseFamily(source.substring(FAMILY_PREFIX.length()), where, errors);
        if (!source.startsWith(PATH_PREFIX)) {
            errors.add(where + ": font source must start with path= or family=");
            return null;
        }
        String value = source.substring(PATH_PREFIX.length());
        if (!(value.startsWith("~/") || value.startsWith("/"))) {
            errors.add(where + ": font paths must be absolute or start with ~/");
            return null;
        }
        if (value.isEmpty()) {
            errors.add(where + ": font source is empty");
            return null;
        }
        return new FaceSpec(SourceType.PATH, value);
    }

    /** A fontconfig family name, however it was written: bare, {@code family=} or a font spec. */
    @Nullable
    private static FaceSpec parseFamily(@NonNull String value, @NonNull String where,
                                        @NonNull List<String> errors) {
        String family = value.trim();
        if (family.length() > MAX_FAMILY_CHARS) {
            errors.add(where + ": family name exceeds " + MAX_FAMILY_CHARS + " characters");
            return null;
        }
        if (family.isEmpty()) {
            errors.add(where + ": font source is empty");
            return null;
        }
        return new FaceSpec(SourceType.FAMILY, family);
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

    /**
     * Splits one config line into words, allowing quotes and backslash escapes.
     *
     * <p>A {@code #} is an ordinary character here: kitty only treats a line as a comment when its
     * first non-blank character is {@code #}, which the caller has already decided, and a family
     * name is entitled to contain one.
     */
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
