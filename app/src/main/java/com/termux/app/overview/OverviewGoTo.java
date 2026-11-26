package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Overview's Go to (spec overview-2026-11-24, "Go to"): which places match what was typed, and in
 * what order. Pure. A place is an {@link Entry}: a wall card by name, and a Workspace window also
 * by the command in front where the terminal already knows it. Sessions by name (#357) join as
 * entries of their own with the place they go to; the matching here does not care what an entry
 * stands for.
 *
 * <p>Order: a match on the name comes before one on the command; within each, an exact match, then
 * a prefix, then the start of a word, then any part of it; a query of several words matches when
 * every word is somewhere in the name or the command, after all of those. Ties keep wall order.
 */
public final class OverviewGoTo {

    private OverviewGoTo() { }

    /** One place Go to can reach. */
    public static final class Entry {
        /** The strip position of the card it goes to. */
        public final int card;
        /** What the glyph beside the match shows. */
        @NonNull public final PageKind kind;
        @NonNull public final String name;
        /** What runs in front, where the terminal knows it; null for none. */
        @Nullable public final String command;

        public Entry(int card, @NonNull PageKind kind, @NonNull String name,
                     @Nullable String command) {
            this.card = card;
            this.kind = kind;
            this.name = name;
            this.command = command == null || command.trim().isEmpty() ? null : command;
        }
    }

    /** One match: the place and whether the command, not the name, is what matched. */
    public static final class Match {
        @NonNull public final Entry entry;
        public final boolean viaCommand;

        Match(@NonNull Entry entry, boolean viaCommand) {
            this.entry = entry;
            this.viaCommand = viaCommand;
        }
    }

    private static final int NAME = 0;
    private static final int COMMAND = 4;
    private static final int ALL_WORDS = 8;
    private static final int NO_MATCH = Integer.MAX_VALUE;

    /**
     * The matches for {@code query}, best first; empty for a blank query (the strip is shown) and
     * when nothing matches.
     */
    @NonNull
    public static List<Match> match(@NonNull List<Entry> entries, @Nullable String query) {
        String q = normalise(query);
        if (q.isEmpty()) return Collections.emptyList();
        String[] words = q.split(" ");
        int n = entries.size();
        int[] rank = new int[n];
        boolean[] viaCommand = new boolean[n];
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            String name = normalise(e.name);
            String command = e.command == null ? "" : normalise(e.command);
            int byName = tier(name, q);
            int byCommand = command.isEmpty() ? NO_MATCH : tier(command, q);
            int best = byName != NO_MATCH ? NAME + byName
                : byCommand != NO_MATCH ? COMMAND + byCommand : NO_MATCH;
            boolean command_ = byName == NO_MATCH && byCommand != NO_MATCH;
            if (best == NO_MATCH && words.length > 1 && everyWord(words, name, command)) {
                best = ALL_WORDS;
                command_ = false;
            }
            if (best == NO_MATCH) continue;
            rank[i] = best;
            viaCommand[i] = command_;
            hits.add(i);
        }
        // Stable: equal ranks stay in the order the entries came in (wall order).
        Collections.sort(hits, (a, b) -> Integer.compare(rank[a], rank[b]));
        List<Match> out = new ArrayList<>(hits.size());
        for (int i : hits) out.add(new Match(entries.get(i), viaCommand[i]));
        return out;
    }

    /** 0 exact, 1 prefix, 2 word start, 3 anywhere; {@link #NO_MATCH} for none. */
    private static int tier(@NonNull String text, @NonNull String q) {
        if (text.equals(q)) return 0;
        if (text.startsWith(q)) return 1;
        int at = text.indexOf(q);
        if (at < 0) return NO_MATCH;
        for (; at >= 0; at = text.indexOf(q, at + 1)) {
            if (at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1))) return 2;
        }
        return 3;
    }

    private static boolean everyWord(@NonNull String[] words, @NonNull String name,
                                     @NonNull String command) {
        for (String w : words) {
            if (!name.contains(w) && !command.contains(w)) return false;
        }
        return true;
    }

    /** Lower case, trimmed, runs of spaces as one. */
    @NonNull
    static String normalise(@Nullable String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
