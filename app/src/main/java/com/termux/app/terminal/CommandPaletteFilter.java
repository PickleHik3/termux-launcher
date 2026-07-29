package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Search and ranking for the command palette.
 *
 * <p>Deliberately free of Android types so the ranking is unit-testable: the
 * caller resolves localized strings and availability, this class only orders the
 * result. The tiers mirror the launcher's own app-search behavior (exact, prefix,
 * word prefix, substring, then a fuzzy subsequence pass) rather than reusing
 * {@code LauncherRankingEngine}, which is typed to app entries.
 */
public final class CommandPaletteFilter {

    /**
     * Score tiers, highest first. Every title tier — including the subsequence pass — outranks
     * every non-title tier, so what the row actually says decides the order before its id,
     * category, description or shortcut get a vote.
     */
    private static final int SCORE_EXACT_TITLE = 100;
    private static final int SCORE_TITLE_PREFIX = 90;
    private static final int SCORE_WORD_PREFIX = 80;
    private static final int SCORE_TITLE_SUBSTRING = 70;
    private static final int SCORE_TITLE_SUBSEQUENCE = 65;
    private static final int SCORE_ID_SUBSTRING = 60;
    private static final int SCORE_CATEGORY_MATCH = 50;
    private static final int SCORE_TEXT_MATCH = 45;
    private static final int SCORE_NONE = 0;

    /** Fuzzy subsequence matching needs at least this many characters to be useful. */
    private static final int FUZZY_MIN_QUERY_LENGTH = 2;

    /** One palette row. Strings arrive already localized. */
    public static final class Entry {
        public final String toolName;
        public final String title;
        public final String subtitle;
        public final String category;
        public final List<String> bindings;
        public final boolean enabled;
        @Nullable public final String disabledReason;
        public final boolean requiresConfirmation;
        public final LauncherToolRegistry.ToolRisk risk;
        /**
         * Arguments this row supplies to its tool, or {@code null} for the
         * argument-free tool rows. This is what lets one tool back many rows —
         * every app row runs {@code app.launch} with its own query.
         */
        @Nullable public final JSONObject arguments;
        /**
         * Schema property this row still has to collect before it can run, or {@code null}
         * when the row is ready to go. A free-text property puts the palette in argument
         * mode; one with {@link #argumentChoices} pushes a submenu instead.
         */
        @Nullable public final String argumentName;
        /** Allowed values for {@link #argumentName}, or {@code null} for free text. */
        @Nullable public final List<String> argumentChoices;

        public Entry(
            @NonNull String toolName,
            @NonNull String title,
            @NonNull String subtitle,
            @NonNull String category,
            @NonNull List<String> bindings,
            boolean enabled,
            @Nullable String disabledReason,
            boolean requiresConfirmation,
            @NonNull LauncherToolRegistry.ToolRisk risk
        ) {
            this(toolName, title, subtitle, category, bindings, enabled, disabledReason,
                requiresConfirmation, risk, null);
        }

        public Entry(
            @NonNull String toolName,
            @NonNull String title,
            @NonNull String subtitle,
            @NonNull String category,
            @NonNull List<String> bindings,
            boolean enabled,
            @Nullable String disabledReason,
            boolean requiresConfirmation,
            @NonNull LauncherToolRegistry.ToolRisk risk,
            @Nullable JSONObject arguments
        ) {
            this(toolName, title, subtitle, category, bindings, enabled, disabledReason,
                requiresConfirmation, risk, arguments, null, null);
        }

        public Entry(
            @NonNull String toolName,
            @NonNull String title,
            @NonNull String subtitle,
            @NonNull String category,
            @NonNull List<String> bindings,
            boolean enabled,
            @Nullable String disabledReason,
            boolean requiresConfirmation,
            @NonNull LauncherToolRegistry.ToolRisk risk,
            @Nullable JSONObject arguments,
            @Nullable String argumentName,
            @Nullable List<String> argumentChoices
        ) {
            this.arguments = arguments;
            this.toolName = toolName;
            this.title = title;
            this.subtitle = subtitle;
            this.category = category;
            this.bindings = Collections.unmodifiableList(new ArrayList<>(bindings));
            this.enabled = enabled;
            this.disabledReason = disabledReason;
            this.requiresConfirmation = requiresConfirmation;
            this.risk = risk;
            this.argumentName = argumentName;
            this.argumentChoices = argumentChoices == null
                ? null : Collections.unmodifiableList(new ArrayList<>(argumentChoices));
        }

        /** True when this action is destructive enough to confirm before running. */
        public boolean isDestructive() {
            return risk == LauncherToolRegistry.ToolRisk.HIGH
                || risk == LauncherToolRegistry.ToolRisk.CRITICAL;
        }

        /** True when ⏎ opens a choice list rather than running the action. */
        public boolean isSubmenu() {
            return argumentName != null && argumentChoices != null && !argumentChoices.isEmpty();
        }

        /** True when ⏎ switches the palette into argument mode. */
        public boolean isArgumentPrompt() {
            return argumentName != null && !isSubmenu();
        }

        /**
         * Right-hand column of the row: the compacted stroke that applies, else the marker
         * for what ⏎ will do with the row.
         */
        @NonNull
        public String shortcutLabel() {
            if (isSubmenu()) return "›";
            if (isArgumentPrompt()) return "args";
            for (String binding : bindings) {
                // Gesture strokes ("kbd:space:swipe-north") do not compact into the column.
                if (!binding.startsWith("kbd:")) return compactStroke(binding);
            }
            if (LauncherToolRegistry.CATEGORY_APPS.equals(category)) return "app";
            return "";
        }
    }

    /**
     * Compacts a binding into the ledger's shorthand: {@code ctrl+alt+k} reads {@code C-A-k},
     * matching the emacs-style notation the mock uses in the shortcut column.
     */
    @NonNull
    static String compactStroke(@NonNull String stroke) {
        int condition = stroke.indexOf(" (");
        String bare = condition < 0 ? stroke : stroke.substring(0, condition);
        StringBuilder out = new StringBuilder();
        for (String part : bare.split("\\+")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append('-');
            String lower = part.toLowerCase(Locale.US);
            switch (lower) {
                case "ctrl": out.append('C'); break;
                case "alt": out.append('A'); break;
                case "shift": out.append('S'); break;
                case "meta": out.append('M'); break;
                default: out.append(part); break;
            }
        }
        return out.toString();
    }

    private CommandPaletteFilter() {
    }

    /**
     * Filters and ranks entries for a query.
     *
     * <p>An empty query returns every entry in the order supplied, so the caller's
     * category grouping survives. A non-empty query returns only matches, best
     * first; ties keep the supplied order, and enabled entries outrank disabled
     * ones at equal score.
     */
    @NonNull
    public static List<Entry> filterAndRank(@NonNull List<Entry> entries, @Nullable String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int score = score(entry, normalized);
            if (score > SCORE_NONE) {
                scored.add(new Scored(entry, score, i));
            }
        }

        Collections.sort(scored, new Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored b) {
                if (a.score != b.score) return b.score - a.score;
                if (a.entry.enabled != b.entry.enabled) return a.entry.enabled ? -1 : 1;
                int lengthDelta = a.entry.title.length() - b.entry.title.length();
                if (lengthDelta != 0) return lengthDelta;
                return a.index - b.index;
            }
        });

        List<Entry> result = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            result.add(s.entry);
        }
        return Collections.unmodifiableList(result);
    }

    static int score(@NonNull Entry entry, @NonNull String query) {
        String title = entry.title.toLowerCase(Locale.US);
        if (title.equals(query)) return SCORE_EXACT_TITLE;
        if (title.startsWith(query)) return SCORE_TITLE_PREFIX;

        for (String word : title.split("[\\s/\\-]+")) {
            if (!word.isEmpty() && word.startsWith(query)) return SCORE_WORD_PREFIX;
        }
        if (title.contains(query)) return SCORE_TITLE_SUBSTRING;
        if (query.length() >= FUZZY_MIN_QUERY_LENGTH && isSubsequence(query, title)) {
            return SCORE_TITLE_SUBSEQUENCE;
        }

        // "split pane" should find pane.split_vertical, so match the id with its
        // separators treated as spaces.
        String id = entry.toolName.toLowerCase(Locale.US).replace('.', ' ').replace('_', ' ');
        if (id.contains(query)) return SCORE_ID_SUBSTRING;

        String category = entry.category.toLowerCase(Locale.US);
        if (category.startsWith(query) || category.contains(query)) return SCORE_CATEGORY_MATCH;

        // Bindings are searchable so "ctrl+alt+v" finds its action.
        for (String binding : entry.bindings) {
            if (binding.toLowerCase(Locale.US).contains(query)) return SCORE_TEXT_MATCH;
        }
        if (entry.subtitle.toLowerCase(Locale.US).contains(query)) return SCORE_TEXT_MATCH;
        return SCORE_NONE;
    }

    /** True when every character of {@code query} appears in order within {@code candidate}. */
    static boolean isSubsequence(@NonNull String query, @NonNull String candidate) {
        int q = 0;
        for (int c = 0; c < candidate.length() && q < query.length(); c++) {
            if (candidate.charAt(c) == query.charAt(q)) q++;
        }
        return q == query.length();
    }

    private static final class Scored {
        final Entry entry;
        final int score;
        final int index;

        Scored(Entry entry, int score, int index) {
            this.entry = entry;
            this.score = score;
            this.index = index;
        }
    }
}
