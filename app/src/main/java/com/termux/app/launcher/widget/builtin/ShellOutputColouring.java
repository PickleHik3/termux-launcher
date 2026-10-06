package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The light colouring the Command widget's 4×2 output block gets: a {@code git status -sb}
 * branch line in the "done" green with its {@code [ahead 2]} in the warm accent, a changed file's
 * two-letter status code in the error red, and an untracked {@code ??} in primary. Anything else
 * stays plain. Pure: it returns ranges and roles, the view turns them into spans.
 */
public final class ShellOutputColouring {
    /** Which style role a range is drawn in. */
    public enum Role { DONE, ERROR, PRIMARY, WARM }

    /** {@code [start, end)} of one line, drawn in {@link #role}. */
    public static final class Range {
        public final int start;
        public final int end;
        @NonNull public final Role role;

        public Range(int start, int end, @NonNull Role role) {
            this.start = start; this.end = end; this.role = role;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Range)) return false;
            Range that = (Range) other;
            return start == that.start && end == that.end && role == that.role;
        }

        @Override public int hashCode() { return (start * 31 + end) * 31 + role.hashCode(); }

        @NonNull @Override public String toString() { return role + "[" + start + "," + end + ")"; }
    }

    private static final String STATUS_CODES = " MADRCU";

    private ShellOutputColouring() { }

    /** The coloured ranges of one output line, in order; empty for a plain line. */
    @NonNull
    public static List<Range> line(@NonNull String line) {
        List<Range> ranges = new ArrayList<>();
        if (line.startsWith("##")) {
            int bracket = line.lastIndexOf(" [");
            if (bracket > 2 && line.endsWith("]")) {
                ranges.add(new Range(0, bracket, Role.DONE));
                ranges.add(new Range(bracket + 1, line.length(), Role.WARM));
            } else {
                ranges.add(new Range(0, line.length(), Role.DONE));
            }
            return ranges;
        }
        if (line.length() < 3 || line.charAt(2) != ' ') return ranges;
        char x = line.charAt(0), y = line.charAt(1);
        if (x == '?' && y == '?') {
            ranges.add(new Range(0, 2, Role.PRIMARY));
        } else if (STATUS_CODES.indexOf(x) >= 0 && STATUS_CODES.indexOf(y) >= 0 && (x != ' ' || y != ' ')) {
            ranges.add(new Range(0, 2, Role.ERROR));
        }
        return ranges;
    }
}
