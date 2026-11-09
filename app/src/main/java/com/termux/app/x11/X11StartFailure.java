package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Reads why a server that was started from the Display window died before a display connected,
 * out of what it wrote to stderr. Pure: the caller hands the captured text in.
 */
public final class X11StartFailure {

    private static final Pattern ERROR_WORDS =
        Pattern.compile("(?i)error|exception|fail|cannot|can't|unable|denied|not found");
    private static final int MAX_LENGTH = 200;

    private X11StartFailure() {}

    /**
     * @param stderr  what the start command wrote to stderr, or null
     * @param generic the line to give when there is no error line in it
     * @return the last non-empty line that reads as an error (stack frames skipped), trimmed and
     *         capped, else {@code generic}
     */
    @NonNull
    public static String reason(@Nullable CharSequence stderr, @NonNull String generic) {
        if (stderr == null) return generic;
        String[] lines = stderr.toString().split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            // Xorg brackets its fatal lines with "(EE)"; the user reads the sentence, not the tag.
            String line = lines[i].replace("(EE)", " ").trim();
            if (line.isEmpty() || line.startsWith("at ") || line.startsWith("...")) continue;
            if (!ERROR_WORDS.matcher(line).find()) continue;
            return line.length() <= MAX_LENGTH ? line : line.substring(0, MAX_LENGTH - 1) + "…";
        }
        return generic;
    }
}
