package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Paths for the file-backed widgets (Tasks, Scratchpad): what the user types in settings
 * ({@code ~/notes/tasks.md}), the absolute file it names, the short forms the cards show, and the
 * command that opens it in the user's editor.
 */
public final class FilesWidgetPaths {
    private FilesWidgetPaths() { }

    /** The user's home: {@code $HOME} of every terminal session of this edition. */
    @NonNull public static String home() { return TermuxConstants.TERMUX_HOME_DIR_PATH; }

    /**
     * {@code raw} as an absolute path: {@code ~} and {@code ~/…} expand to {@code home}, an
     * absolute path stays, and a relative one is read from {@code home}.
     */
    @NonNull public static String expand(@NonNull String raw, @NonNull String home) {
        String path = raw.trim();
        String base = home.endsWith("/") && home.length() > 1 ? home.substring(0, home.length() - 1) : home;
        if (path.isEmpty() || path.equals("~")) return base;
        if (path.startsWith("~/")) return base + path.substring(1);
        if (path.startsWith("/")) return path;
        return base + "/" + path;
    }

    /** {@code path} with the home folder written as {@code ~}, the way the user typed it. */
    @NonNull public static String display(@NonNull String path, @NonNull String home) {
        if (path.equals(home)) return "~";
        if (path.startsWith(home + "/")) return "~" + path.substring(home.length());
        return path;
    }

    /** The last segment of {@code path}: {@code scratch.md}. */
    @NonNull public static String fileName(@NonNull String path) {
        String trimmed = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }

    /** The file name without its extension: {@code tasks} for {@code tasks.md}. */
    @NonNull public static String baseName(@NonNull String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** {@code value} as one single-quoted shell word. */
    @NonNull public static String shellQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** The command that opens {@code path} in the user's {@code $EDITOR}, or nano without one. */
    @NonNull public static List<String> editorCommand(@NonNull String path) {
        return Arrays.asList("bash", "-lc", "\"${EDITOR:-nano}\" " + shellQuote(path));
    }

    /** The comma-separated entries of {@code csv}, trimmed, blanks dropped. */
    @NonNull public static List<String> splitList(@NonNull String csv) {
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String entry = part.trim();
            if (!entry.isEmpty()) out.add(entry);
        }
        return out;
    }
}
