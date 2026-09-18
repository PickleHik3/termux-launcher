package com.termux.app.help;

import android.content.Context;

import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One search over everything help knows: every topic, every fix and every glossary term, whatever
 * place the reader is on and whether or not the control is on screen.
 *
 * <p>Exact title or term matches come first, then curated aliases, then the body. The current place
 * only breaks ties — it never hides a result. Nothing here reads the terminal, the user's apps or
 * their files.
 *
 * <p>Pure: strings come through {@link HelpTopics.Text}, so a plain JUnit test can feed its own.
 */
public final class HelpSearch {
    private HelpSearch() {}

    /** What a result is: a guide topic, a glossary term, or a recovery symptom. */
    public enum Kind { GUIDE, TERM, FIX }

    /** One row of results. */
    public static final class Result {
        public final Kind kind;
        /** A topic id for {@link Kind#GUIDE} and {@link Kind#FIX}, a term id for {@link Kind#TERM}. */
        public final String id;
        public final String title;
        /** One line under the title: the topic's summary, or the term's definition. */
        public final String excerpt;
        /** The section the result lives in, or null for a term. */
        public final HelpTopics.Group group;
        /** How well it matched; higher is better. Exposed so a test can reason about order. */
        public final int score;

        Result(Kind kind, String id, String title, String excerpt, HelpTopics.Group group, int score) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.excerpt = excerpt;
            this.group = group;
            this.score = score;
        }

        @Override public String toString() { return kind + ":" + id + "(" + score + ")"; }
    }

    private static final int EXACT = 100;
    private static final int TITLE_STARTS = 90;
    private static final int TITLE_CONTAINS = 80;
    private static final int ALIAS_EXACT = 70;
    private static final int ALIAS_CONTAINS = 60;
    private static final int BODY = 40;

    /** Resolves resources through the given context. */
    public static HelpTopics.Text text(final Context context) {
        return new HelpTopics.Text() {
            @Override public String get(int res) { return context.getString(res); }
        };
    }

    /** The convenience the views use. */
    public static List<Result> search(String query, PaneWallPage place, Context context) {
        return search(query, place, text(context));
    }

    /**
     * Results best first. A blank query returns nothing — help home shows suggestions instead of
     * pretending everything matched.
     */
    public static List<Result> search(String query, PaneWallPage place, HelpTopics.Text text) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return Collections.emptyList();
        List<Result> found = new ArrayList<>();
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            int score = scoreTopic(entry, needle, text);
            if (score <= 0) continue;
            if (place != null && entry.onPlace(place)) score++;
            found.add(new Result(entry.kind == HelpTopics.Kind.FIX ? Kind.FIX : Kind.GUIDE,
                entry.id, text.get(entry.titleRes), text.get(entry.summaryRes), entry.group, score));
        }
        for (HelpGlossary.Term term : HelpGlossary.all()) {
            int score = scoreTerm(term, needle, text);
            if (score <= 0) continue;
            found.add(new Result(Kind.TERM, term.id, text.get(term.titleRes),
                text.get(term.definitionRes), null, score));
        }
        // Stable: equal scores keep catalogue order, and no topic is listed twice.
        Collections.sort(found, new Comparator<Result>() {
            @Override public int compare(Result a, Result b) { return b.score - a.score; }
        });
        return Collections.unmodifiableList(found);
    }

    private static int scoreTopic(HelpTopics.Entry entry, String needle, HelpTopics.Text text) {
        int score = title(text.get(entry.titleRes), needle);
        if (score == 0) score = aliases(entry.aliasesRes, needle, text);
        if (score == 0) score = body(entry, needle, text);
        return score;
    }

    private static int scoreTerm(HelpGlossary.Term term, String needle, HelpTopics.Text text) {
        int score = title(text.get(term.titleRes), needle);
        if (score == 0 && contains(text.get(term.definitionRes), needle)) score = BODY;
        return score;
    }

    private static int title(String title, String needle) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.equals(needle)) return EXACT;
        if (lower.startsWith(needle)) return TITLE_STARTS;
        if (lower.contains(needle)) return TITLE_CONTAINS;
        return 0;
    }

    private static int aliases(int aliasesRes, String needle, HelpTopics.Text text) {
        if (aliasesRes == 0) return 0;
        for (String alias : text.get(aliasesRes).split(",")) {
            String one = alias.trim().toLowerCase(Locale.ROOT);
            if (one.isEmpty()) continue;
            if (one.equals(needle)) return ALIAS_EXACT;
            if (one.contains(needle) || needle.contains(one)) return ALIAS_CONTAINS;
        }
        return 0;
    }

    private static int body(HelpTopics.Entry entry, String needle, HelpTopics.Text text) {
        if (contains(text.get(entry.summaryRes), needle)) return BODY;
        if (contains(text.get(entry.actionRes), needle)) return BODY;
        for (int step : entry.stepsRes) if (contains(text.get(step), needle)) return BODY;
        if (entry.wayBackRes != 0 && contains(text.get(entry.wayBackRes), needle)) return BODY;
        if (entry.revealRes != 0 && contains(text.get(entry.revealRes), needle)) return BODY;
        return 0;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
