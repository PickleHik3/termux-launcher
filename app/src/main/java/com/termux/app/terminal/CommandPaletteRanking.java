package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One ranked list for a palette search: actions, apps and contacts placed on
 * {@link CommandPaletteFilter}'s score scale and sorted together, so an exact app name is never
 * buried under a loose action match just because apps used to be appended after the actions.
 *
 * <p>Order, strongest first:
 *
 * <ol>
 *   <li>the row picked most for exactly this query, its picks decayed like usage and recency
 *       breaking a tie ({@link CommandPaletteQueryMemory});
 *   <li>match quality, whichever source the row came from;
 *   <li>decayed usage, and only between rows of equal match quality, so a favourite can never
 *       jump a better match;
 *   <li>source: apps, then actions, then contacts;
 *   <li>enabled before disabled, the shorter title, then the order supplied.
 * </ol>
 *
 * <p>Free of Android types, like the filter, so the whole order is unit-testable. The controller
 * only gathers the candidates and the usage snapshots.
 */
public final class CommandPaletteRanking {

    /** First non-space character that limits a list search to launcher actions. */
    static final char ACTIONS_ONLY_PREFIX = '>';

    /** Where a row came from. Declaration order is the tie-break when nothing else decides. */
    enum Source { APP, ACTION, CONTACT }

    /** A row that matched the query, with its place on the shared score scale. */
    static final class Candidate {
        final CommandPaletteFilter.Entry entry;
        final Source source;
        final int score;

        Candidate(@NonNull CommandPaletteFilter.Entry entry, @NonNull Source source, int score) {
            this.entry = entry;
            this.source = source;
            this.score = score;
        }
    }

    /**
     * What the top-level query line asks the list for: the text to match, and whether a leading
     * {@code >} limits the answer to launcher actions. Only the list search reads this; argument
     * entry, choice lists and capture take the line as typed.
     */
    static final class Query {
        /** Trimmed, case kept; empty for a bare {@code >}. */
        @NonNull final String text;
        final boolean actionsOnly;

        private Query(@NonNull String text, boolean actionsOnly) {
            this.text = text;
            this.actionsOnly = actionsOnly;
        }

        @NonNull
        static Query parse(@Nullable String raw) {
            String trimmed = raw == null ? "" : raw.trim();
            if (!trimmed.isEmpty() && trimmed.charAt(0) == ACTIONS_ONLY_PREFIX)
                return new Query(trimmed.substring(1).trim(), true);
            return new Query(trimmed, false);
        }

        /** The text as query memory keys it: lower case, the {@code >} already gone. */
        @NonNull
        String normalized() {
            return text.toLowerCase(Locale.US);
        }
    }

    private CommandPaletteRanking() {
    }

    /** The action rows matching {@code text}, scored as {@link CommandPaletteFilter} scores them. */
    @NonNull
    static List<Candidate> actionCandidates(@NonNull List<CommandPaletteFilter.Entry> entries,
                                            @NonNull String text) {
        String needle = text.trim().toLowerCase(Locale.US);
        List<Candidate> out = new ArrayList<>();
        if (needle.isEmpty()) return out;
        for (CommandPaletteFilter.Entry entry : entries) {
            int score = CommandPaletteFilter.score(entry, needle);
            if (score > CommandPaletteFilter.SCORE_NONE)
                out.add(new Candidate(entry, Source.ACTION, score));
        }
        return out;
    }

    /**
     * A row's decayed usage. An app row counts launches from anywhere ({@code appUsage}, by stable
     * id) and its own palette runs ({@code rowUsage}, by row key); a palette launch lands in both,
     * so the larger of the two is taken rather than the sum.
     */
    static double usage(@NonNull Candidate candidate, @NonNull Map<String, Double> appUsage,
                        @NonNull Map<String, Double> rowUsage) {
        double row = orZero(rowUsage.get(CommandPaletteActionStats.keyFor(candidate.entry)));
        if (candidate.source != Source.APP || candidate.entry.iconKey == null) return row;
        return Math.max(row, orZero(appUsage.get(candidate.entry.iconKey)));
    }

    /**
     * Sorts the candidates into the list a search shows.
     *
     * @param appUsage   decayed launches by app stable id
     * @param rowUsage   decayed palette runs by {@link CommandPaletteActionStats#keyFor row key}
     * @param remembered rows picked before for this exact query, row key to its pick weight; the
     *                   heaviest one present (and enabled) goes first, the more recent on a tie,
     *                   and the rest keep their places below
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> rank(
        @NonNull List<Candidate> candidates,
        @NonNull Map<String, Double> appUsage,
        @NonNull Map<String, Double> rowUsage,
        @NonNull Map<String, CommandPaletteQueryMemory.Remembered> remembered) {
        List<Ranked> ranked = new ArrayList<>(candidates.size());
        Ranked pinned = null;
        CommandPaletteQueryMemory.Remembered pinnedWeight = null;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            Ranked item = new Ranked(candidate, usage(candidate, appUsage, rowUsage), i);
            ranked.add(item);
            // A disabled row would only greet the user with its reason, so it is not pinned.
            CommandPaletteQueryMemory.Remembered weight =
                remembered.isEmpty() || !candidate.entry.enabled ? null
                    : remembered.get(CommandPaletteActionStats.keyFor(candidate.entry));
            if (weight != null && (pinnedWeight == null || weight.outranks(pinnedWeight))) {
                pinned = item;
                pinnedWeight = weight;
            }
        }
        if (pinned != null) pinned.pinned = true;

        Collections.sort(ranked, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            int byScore = Integer.compare(b.candidate.score, a.candidate.score);
            if (byScore != 0) return byScore;
            int byUsage = Double.compare(b.usage, a.usage);
            if (byUsage != 0) return byUsage;
            if (a.candidate.source != b.candidate.source)
                return a.candidate.source.ordinal() - b.candidate.source.ordinal();
            CommandPaletteFilter.Entry ea = a.candidate.entry;
            CommandPaletteFilter.Entry eb = b.candidate.entry;
            if (ea.enabled != eb.enabled) return ea.enabled ? -1 : 1;
            int lengthDelta = ea.title.length() - eb.title.length();
            if (lengthDelta != 0) return lengthDelta;
            return a.index - b.index;
        });

        List<CommandPaletteFilter.Entry> out = new ArrayList<>(ranked.size());
        for (Ranked item : ranked) out.add(item.candidate.entry);
        return out;
    }

    private static double orZero(@Nullable Double value) {
        return value == null ? 0.0 : value;
    }

    private static final class Ranked {
        final Candidate candidate;
        final double usage;
        final int index;
        boolean pinned;

        Ranked(Candidate candidate, double usage, int index) {
            this.candidate = candidate;
            this.usage = usage;
            this.index = index;
        }
    }
}
