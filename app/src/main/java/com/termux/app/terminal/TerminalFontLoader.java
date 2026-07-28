package com.termux.app.terminal;

import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves a parsed font config without allowing one bad optional font to disable the terminal. */
public final class TerminalFontLoader {

    private static final long MAX_FONT_FILE_BYTES = 64L * 1024L * 1024L;

    public static final class Faces {
        @NonNull public final Typeface regular;
        @Nullable public final Typeface bold;
        @Nullable public final Typeface italic;
        @Nullable public final Typeface boldItalic;
        @NonNull public final List<String> errors;

        private Faces(@NonNull Typeface regular, @Nullable Typeface bold,
                      @Nullable Typeface italic, @Nullable Typeface boldItalic,
                      @NonNull List<String> errors) {
            this.regular = regular;
            this.bold = bold;
            this.italic = italic;
            this.boldItalic = boldItalic;
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
        return new Faces(regular, bold, italic, boldItalic, errors);
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
