package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GL compile or link log read back onto the user's text. Pure.
 *
 * <p>Drivers write a location as {@code <string>:<line>}: Adreno and PowerVR
 * {@code ERROR: 0:12: 'x' : undeclared identifier}, Mali {@code 0:12: L0002: Undeclared variable}
 * (or with the {@code ERROR:} prefix), Mesa and the emulator's translator
 * {@code 0:12(5): error: ...}. The line is mapped through {@link ShaderPrelude.Built#userLine};
 * warnings are dropped, and a log with no located error keeps its lines as messages without a
 * line (a link error, an out-of-memory).</p>
 */
public final class ShaderErrors {

    /** One error: the user's line (or -1 when it has none) and the driver's message. */
    public static final class Error {
        public final int line;
        @NonNull public final String message;

        public Error(int line, @NonNull String message) {
            this.line = line;
            this.message = message;
        }

        /** "Line 12: 'x' : undeclared identifier", or the message alone. */
        @NonNull
        @Override
        public String toString() {
            return line > 0 ? "Line " + line + ": " + message : message;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Error)) return false;
            Error e = (Error) o;
            return e.line == line && e.message.equals(message);
        }

        @Override
        public int hashCode() {
            return line * 31 + message.hashCode();
        }
    }

    private static final Pattern LOCATED = Pattern.compile(
        "^\\s*(ERROR|WARNING)?:?\\s*\\d+:(\\d+)(?:\\(\\d+\\))?\\s*:\\s*(.*)$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY = Pattern.compile(
        "(?i)^\\s*(ERROR:\\s*)?\\d+\\s+compilation errors?.*$");

    private ShaderErrors() {}

    @NonNull
    public static List<Error> parse(@Nullable String log, @NonNull ShaderPrelude.Built built) {
        if (log == null || log.trim().isEmpty()) return Collections.emptyList();
        List<Error> located = new ArrayList<>();
        List<Error> loose = new ArrayList<>();
        for (String raw : log.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || SUMMARY.matcher(line).matches()) continue;
            Matcher m = LOCATED.matcher(line);
            if (m.matches()) {
                String prefix = m.group(1);
                String message = m.group(3).trim();
                boolean warning = "WARNING".equalsIgnoreCase(prefix)
                    || (prefix == null && message.regionMatches(true, 0, "warning", 0, 7));
                if (warning) continue;
                if (message.regionMatches(true, 0, "error:", 0, 6)) message = message.substring(6).trim();
                int compiled = parseInt(m.group(2));
                located.add(new Error(built.userLine(compiled), message));
            } else if (!line.regionMatches(true, 0, "warning", 0, 7)) {
                loose.add(new Error(-1, line));
            }
        }
        return located.isEmpty() ? loose : located;
    }

    /** The errors one per line, as the editor's error strip shows them. */
    @NonNull
    public static String format(@NonNull List<Error> errors) {
        StringBuilder b = new StringBuilder();
        for (Error e : errors) {
            if (b.length() > 0) b.append('\n');
            b.append(e);
        }
        return b.toString();
    }

    private static int parseInt(@NonNull String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
