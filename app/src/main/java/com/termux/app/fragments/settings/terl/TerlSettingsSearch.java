package com.termux.app.fragments.settings.terl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Search over Terl's Settings: every group, every setting row (found by its title, its summary and
 * the words people use for it), every link, and the rows of the v1 pages the groups open. Ranked
 * as v1's Settings search ranks ({@code SettingsSearchIndex}): a title that is the query, then one
 * that starts with it, then a word start, then anywhere in the title, then summary words. Within a
 * rank Terl's own rows come before rows of v1's pages, then tree order.
 */
public final class TerlSettingsSearch {

    /** What a hit opens. */
    public enum Kind {
        /** A group's page. */
        GROUP,
        /** A group's page, landed on a setting's row. */
        SETTING,
        /** A v1 page a group links. */
        LINK,
        /** A row of a v1 page a group opens, landed on that row. */
        PAGE_ROW,
        /** Edit mode, opened on the card holding a control ({@link #card}); no Settings page. */
        EDIT_MODE
    }

    /** One searchable thing. */
    public static final class Entry {
        @NonNull public final Kind kind;
        @NonNull public final String title;
        /** Where it lives, from the group down ("Workspace › Clipboard"). */
        @NonNull public final String breadcrumb;
        /** The group a hit opens in; null for an EDIT_MODE hit, which opens no Settings page. */
        @Nullable public final TerlSettingsTree.Group group;
        /** An EDIT_MODE hit's {@code EditModeDoor} extra: the card to open, or "rest". */
        @Nullable public final String card;
        /** A setting's id; null otherwise. */
        @Nullable public final String id;
        /** The v1 page a LINK or PAGE_ROW opens; null otherwise. */
        @Nullable public final String page;
        /** The v1 row a PAGE_ROW lands on; null otherwise. */
        @Nullable public final String key;
        @NonNull final String titleLower;
        @NonNull final String wordsLower;

        public Entry(@NonNull Kind kind, @NonNull String title, @Nullable String words,
                     @NonNull String breadcrumb, @Nullable TerlSettingsTree.Group group,
                     @Nullable String id, @Nullable String page, @Nullable String key) {
            this(kind, title, words, breadcrumb, group, id, page, key, null);
        }

        public Entry(@NonNull Kind kind, @NonNull String title, @Nullable String words,
                     @NonNull String breadcrumb, @Nullable TerlSettingsTree.Group group,
                     @Nullable String id, @Nullable String page, @Nullable String key,
                     @Nullable String card) {
            this.kind = kind;
            this.card = card;
            this.title = title;
            this.breadcrumb = breadcrumb;
            this.group = group;
            this.id = id;
            this.page = page;
            this.key = key;
            this.titleLower = title.toLowerCase(Locale.ROOT);
            this.wordsLower = words == null ? "" : words.toLowerCase(Locale.ROOT);
        }
    }

    @NonNull private final List<Entry> mEntries;

    public TerlSettingsSearch(@NonNull List<Entry> entries) {
        mEntries = new ArrayList<>(entries);
    }

    @NonNull
    public List<Entry> entries() {
        return Collections.unmodifiableList(mEntries);
    }

    /** The entries matching {@code query}, best first, at most {@code limit}. */
    @NonNull
    public List<Entry> search(@NonNull String query, int limit) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> hits = new ArrayList<>();
        if (needle.isEmpty()) return hits;
        String[] words = needle.split("\\s+");
        List<int[]> ranks = new ArrayList<>();
        for (int i = 0; i < mEntries.size(); i++) {
            Entry entry = mEntries.get(i);
            int rank = rank(entry, needle, words);
            if (rank < 0) continue;
            int v1 = entry.kind == Kind.PAGE_ROW ? 1 : 0;
            ranks.add(new int[] {rank, v1, i});
        }
        Collections.sort(ranks, (a, b) -> {
            for (int k = 0; k < 3; k++) if (a[k] != b[k]) return Integer.compare(a[k], b[k]);
            return 0;
        });
        for (int[] r : ranks) {
            if (hits.size() >= limit) break;
            Entry entry = mEntries.get(r[2]);
            if (isDuplicate(hits, entry)) continue;
            hits.add(entry);
        }
        return hits;
    }

    /** 0 best .. 4 summary words only; -1 no match. */
    private static int rank(@NonNull Entry entry, @NonNull String needle, @NonNull String[] words) {
        if (entry.titleLower.equals(needle)) return 0;
        if (entry.titleLower.startsWith(needle)) return 1;
        if (entry.titleLower.contains(" " + needle)) return 2;
        if (entry.titleLower.contains(needle)) return 3;
        for (String word : words) {
            if (!entry.titleLower.contains(word) && !entry.wordsLower.contains(word)) return -1;
        }
        return 4;
    }

    /** A link and the v1 page's own entry for the same page show once. */
    private static boolean isDuplicate(@NonNull List<Entry> hits, @NonNull Entry entry) {
        for (Entry hit : hits) {
            if (hit.title.equals(entry.title) && eq(hit.page, entry.page) && eq(hit.key, entry.key)
                && eq(hit.id, entry.id)) return true;
        }
        return false;
    }

    private static boolean eq(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
