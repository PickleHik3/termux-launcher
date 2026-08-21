package com.termux.app.terminal;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Literal, case-insensitive search over a row-addressable terminal snapshot. */
final class TerminalScrollbackSearchModel {

    static final int MAX_RESULTS = 200;

    static final class Line {
        final int row;
        final String text;

        Line(int row, @NonNull String text) {
            this.row = row;
            this.text = text;
        }
    }

    static final class Match {
        final int row;
        final int start;
        final String line;
        final String snippet;

        Match(int row, int start, String line, String snippet) {
            this.row = row;
            this.start = start;
            this.line = line;
            this.snippet = snippet;
        }
    }

    private TerminalScrollbackSearchModel() {}

    @NonNull
    static List<Match> search(@NonNull List<Line> lines, @NonNull String rawQuery) {
        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return Collections.emptyList();
        List<Match> result = new ArrayList<>();
        // Newest rows first, which is normally where the user is looking.
        for (int i = lines.size() - 1; i >= 0 && result.size() < MAX_RESULTS; i--) {
            Line line = lines.get(i);
            String lower = line.text.toLowerCase(Locale.ROOT);
            int from = 0;
            while (from <= lower.length() - query.length() && result.size() < MAX_RESULTS) {
                int index = lower.indexOf(query, from);
                if (index < 0) break;
                result.add(new Match(line.row, index, line.text, snippet(line.text, index, query.length())));
                from = Math.max(index + query.length(), index + 1);
            }
        }
        return result;
    }

    /**
     * Where an arrow leaves the highlighted result.
     *
     * <p>Clamped rather than wrapped: an arrow held down at the end of the list should stop there, not
     * jump back to the far end, because the user is walking the list to read it. A page key arrives
     * here as a larger delta and lands on the edge when it overshoots.
     *
     * @param size number of results; zero leaves the highlight at zero.
     */
    public static int moveHighlight(int current, int delta, int size) {
        if (size <= 0) return 0;
        int moved = current + delta;
        if (moved < 0) return 0;
        return Math.min(moved, size - 1);
    }

    private static String snippet(String line, int start, int length) {
        int left = Math.max(0, start - 36);
        int right = Math.min(line.length(), start + length + 56);
        String value = line.substring(left, right).trim();
        return (left > 0 ? "…" : "") + value + (right < line.length() ? "…" : "");
    }
}
