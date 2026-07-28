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

    public enum Face { REGULAR, BOLD, ITALIC, BOLD_ITALIC }

    public enum SourceType { PATH, FAMILY }

    public static final class FaceSpec {
        @NonNull public final SourceType type;
        @NonNull public final String value;

        private FaceSpec(@NonNull SourceType type, @NonNull String value) {
            this.type = type;
            this.value = value;
        }
    }

    public static final class Result {
        public final boolean filePresent;
        @NonNull public final Map<Face, FaceSpec> faces;
        @NonNull public final List<String> errors;

        private Result(boolean filePresent, @NonNull Map<Face, FaceSpec> faces,
                       @NonNull List<String> errors) {
            this.filePresent = filePresent;
            EnumMap<Face, FaceSpec> faceCopy = new EnumMap<>(Face.class);
            faceCopy.putAll(faces);
            this.faces = Collections.unmodifiableMap(faceCopy);
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
        List<String> errors = new ArrayList<>();
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
            if (words.size() != 2) {
                errors.add("line " + (i + 1) + ": expected a face and one path= or family= value");
                continue;
            }
            Face face = face(words.get(0));
            if (face == null) {
                errors.add("line " + (i + 1) + ": unknown directive '" + words.get(0) + "'");
                continue;
            }
            String source = words.get(1);
            SourceType type;
            String value;
            if (source.startsWith("path=")) {
                type = SourceType.PATH;
                value = source.substring(5);
                if (!(value.startsWith("~/") || value.startsWith("/"))) {
                    errors.add("line " + (i + 1) + ": font paths must be absolute or start with ~/");
                    continue;
                }
            } else if (source.startsWith("family=")) {
                type = SourceType.FAMILY;
                value = source.substring(7).trim();
                if (value.length() > MAX_FAMILY_CHARS) {
                    errors.add("line " + (i + 1) + ": family name exceeds " + MAX_FAMILY_CHARS + " characters");
                    continue;
                }
            } else {
                errors.add("line " + (i + 1) + ": font source must start with path= or family=");
                continue;
            }
            if (value.isEmpty()) {
                errors.add("line " + (i + 1) + ": font source is empty");
                continue;
            }
            faces.put(face, new FaceSpec(type, value));
        }
        return new Result(filePresent, faces, errors);
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
        return new Result(present, Collections.emptyMap(), errors);
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
