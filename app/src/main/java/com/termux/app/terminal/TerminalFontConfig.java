package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded parser for the optional {@code ~/.termux/fonts.conf} file. */
public final class TerminalFontConfig {

    public static final String FILE_NAME = "fonts.conf";
    public static final String FILE_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + FILE_NAME;
    private static final long MAX_FILE_BYTES = 64 * 1024;
    private static final int MAX_LINES = 512;
    private static final int MAX_LINE_CHARS = 4096;
    private static final int MAX_FAMILY_CHARS = 128;
    private static final int MAX_SYMBOL_MAPS = 256;
    private static final int MAX_SYMBOL_RANGES = 1024;

    public enum Face { REGULAR, BOLD, ITALIC, BOLD_ITALIC }

    public enum SourceType { PATH, FAMILY }

    public enum LigaturePolicy { NEVER, CURSOR, ALWAYS }

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

        private SymbolMapSpec(@NonNull List<CodePointRange> ranges, @NonNull FaceSpec font) {
            this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
            this.font = font;
        }
    }

    public static final class Result {
        public final boolean filePresent;
        @NonNull public final Map<Face, FaceSpec> faces;
        @NonNull public final List<SymbolMapSpec> symbolMaps;
        @NonNull public final LigaturePolicy ligaturePolicy;
        @NonNull public final List<String> errors;

        private Result(boolean filePresent, @NonNull Map<Face, FaceSpec> faces,
                       @NonNull List<SymbolMapSpec> symbolMaps,
                       @NonNull LigaturePolicy ligaturePolicy, @NonNull List<String> errors) {
            this.filePresent = filePresent;
            EnumMap<Face, FaceSpec> faceCopy = new EnumMap<>(Face.class);
            faceCopy.putAll(faces);
            this.faces = Collections.unmodifiableMap(faceCopy);
            this.symbolMaps = Collections.unmodifiableList(new ArrayList<>(symbolMaps));
            this.ligaturePolicy = ligaturePolicy;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        @Nullable public FaceSpec face(@NonNull Face face) {
            return faces.get(face);
        }
    }

    private TerminalFontConfig() {}

    @NonNull
    public static Result load() {
        return load(new File(FILE_PATH));
    }

    @NonNull
    static Result load(@NonNull File file) {
        if (!file.exists()) return empty(false, null);
        if (!file.isFile()) return empty(true, file.getPath() + " is not a regular file");
        if (file.length() > MAX_FILE_BYTES)
            return empty(true, "font config exceeds " + MAX_FILE_BYTES + " bytes");
        StringBuilder content = new StringBuilder((int) Math.min(file.length(), MAX_FILE_BYTES));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (++count > MAX_LINES)
                    return empty(true, "font config exceeds " + MAX_LINES + " lines");
                if (line.length() > MAX_LINE_CHARS)
                    return empty(true, "line " + count + " exceeds " + MAX_LINE_CHARS + " characters");
                content.append(line).append('\n');
            }
        } catch (IOException e) {
            return empty(true, "cannot read font config: " + e.getMessage());
        }
        return parse(content.toString(), true);
    }

    @NonNull
    static Result parse(@NonNull String content, boolean filePresent) {
        EnumMap<Face, FaceSpec> faces = new EnumMap<>(Face.class);
        List<SymbolMapSpec> symbolMaps = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        LigaturePolicy ligaturePolicy = LigaturePolicy.NEVER;
        int symbolRangeCount = 0;
        String[] lines = content.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            List<String> words;
            try {
                words = words(lines[i]);
            } catch (IllegalArgumentException e) {
                errors.add("line " + (i + 1) + ": " + e.getMessage());
                continue;
            }
            if (words.isEmpty()) continue;
            if ("disable_ligatures".equalsIgnoreCase(words.get(0))) {
                if (words.size() != 2) {
                    errors.add("line " + (i + 1)
                        + ": expected disable_ligatures never, cursor, or always");
                    continue;
                }
                try {
                    ligaturePolicy = LigaturePolicy.valueOf(words.get(1).toUpperCase(Locale.US));
                } catch (IllegalArgumentException e) {
                    errors.add("line " + (i + 1)
                        + ": disable_ligatures must be never, cursor, or always");
                }
                continue;
            }
            if ("symbol_map".equalsIgnoreCase(words.get(0))) {
                if (words.size() != 3) {
                    errors.add("line " + (i + 1)
                        + ": expected symbol_map ranges and one path= or family= value");
                    continue;
                }
                if (symbolMaps.size() >= MAX_SYMBOL_MAPS) {
                    errors.add("line " + (i + 1) + ": symbol_map count exceeds " + MAX_SYMBOL_MAPS);
                    continue;
                }
                List<CodePointRange> ranges = parseRanges(words.get(1), i + 1, errors);
                FaceSpec font = parseSource(words.get(2), i + 1, errors);
                if (ranges == null || font == null) continue;
                if (symbolRangeCount + ranges.size() > MAX_SYMBOL_RANGES) {
                    errors.add("line " + (i + 1) + ": symbol range count exceeds "
                        + MAX_SYMBOL_RANGES);
                    continue;
                }
                symbolMaps.add(new SymbolMapSpec(ranges, font));
                symbolRangeCount += ranges.size();
                continue;
            }
            if (words.size() != 2) {
                errors.add("line " + (i + 1) + ": expected a face and one path= or family= value");
                continue;
            }
            Face face = face(words.get(0));
            if (face == null) {
                errors.add("line " + (i + 1) + ": unknown directive '" + words.get(0) + "'");
                continue;
            }
            FaceSpec source = parseSource(words.get(1), i + 1, errors);
            if (source != null) faces.put(face, source);
        }
        return new Result(filePresent, faces, symbolMaps, ligaturePolicy, errors);
    }

    @Nullable
    private static FaceSpec parseSource(@NonNull String source, int line, @NonNull List<String> errors) {
        SourceType type;
        String value;
        if (source.startsWith("path=")) {
            type = SourceType.PATH;
            value = source.substring(5);
            if (!(value.startsWith("~/") || value.startsWith("/"))) {
                errors.add("line " + line + ": font paths must be absolute or start with ~/");
                return null;
            }
        } else if (source.startsWith("family=")) {
            type = SourceType.FAMILY;
            value = source.substring(7).trim();
            if (value.length() > MAX_FAMILY_CHARS) {
                errors.add("line " + line + ": family name exceeds " + MAX_FAMILY_CHARS + " characters");
                return null;
            }
        } else {
            errors.add("line " + line + ": font source must start with path= or family=");
            return null;
        }
        if (value.isEmpty()) {
            errors.add("line " + line + ": font source is empty");
            return null;
        }
        return new FaceSpec(type, value);
    }

    @Nullable
    private static List<CodePointRange> parseRanges(@NonNull String value, int line,
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
                errors.add("line " + line + ": invalid Unicode range '" + item + "'");
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

    @NonNull
    private static Result empty(boolean present, @Nullable String error) {
        List<String> errors = error == null ? Collections.emptyList() : Collections.singletonList(error);
        return new Result(present, Collections.emptyMap(), Collections.emptyList(),
            LigaturePolicy.NEVER, errors);
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
