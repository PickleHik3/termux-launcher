package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every screen and row Settings can show, searchable from any screen (F1). Built from
 * {@link SettingsTree}: each screen is an entry of its own (searching "Linux apps" finds the page),
 * and each titled row of a screen's XML is an entry that lands on that page, scrolled to the row.
 *
 * <p>Inflation uses a fresh {@link PreferenceManager} per XML, so nothing is persisted and keys
 * cannot collide with a live screen; one XML that fails to inflate is skipped, never fatal.
 */
public final class SettingsSearchIndex {

    /** The separator between the titles of a result's path, as in "Terminal › Text & display". */
    public static final String PATH_SEPARATOR = " › ";

    /** One searchable thing: a page, or a row on a page. */
    public static final class Entry {
        @NonNull public final String title;
        /** Where it lives, as the path of page titles from its section down. */
        @NonNull public final String breadcrumb;
        /** The page to open. */
        @NonNull public final String screenClass;
        /** The row to scroll to and ring, or null for the page itself. */
        @Nullable public final String key;
        @NonNull public final SettingsTree.Section section;
        @NonNull final String titleLower;
        @NonNull final String summaryLower;

        public Entry(@NonNull String title, @Nullable String summary, @NonNull String breadcrumb,
                     @NonNull String screenClass, @Nullable String key, @NonNull SettingsTree.Section section) {
            this.title = title;
            this.breadcrumb = breadcrumb;
            this.screenClass = screenClass;
            this.key = key;
            this.section = section;
            this.titleLower = title.toLowerCase(Locale.ROOT);
            this.summaryLower = summary == null ? "" : summary.toLowerCase(Locale.ROOT);
        }
    }

    @NonNull private final List<Entry> mEntries;

    @VisibleForTesting
    public SettingsSearchIndex(@NonNull List<Entry> entries) {
        mEntries = entries;
    }

    @NonNull
    public List<Entry> entries() {
        return Collections.unmodifiableList(mEntries);
    }

    /** The index built once per process (and again for another language), shared by every box. */
    @Nullable private static SettingsSearchIndex sCached;
    @Nullable private static java.util.Locale sCachedLocale;

    /**
     * The index, built on the first query and kept: every search box of the process shares it, so
     * a keystroke never inflates anything. Built on the main thread, where the rows' own
     * constructors expect to run; a language change builds it anew.
     */
    @NonNull
    public static synchronized SettingsSearchIndex get(@NonNull Context context) {
        java.util.Locale locale = context.getResources().getConfiguration().locale;
        if (sCached == null || !locale.equals(sCachedLocale)) {
            sCached = build(context);
            sCachedLocale = locale;
        }
        return sCached;
    }

    /** Builds the index of every screen in the tree. A screen the build leaves out is skipped. */
    @NonNull
    public static SettingsSearchIndex build(@NonNull Context context) {
        List<Entry> entries = new ArrayList<>();
        for (SettingsTree.Screen screen : SettingsTree.screens()) {
            if (!availableInThisBuild(screen)) continue;
            List<SettingsTree.Screen> path = SettingsTree.path(screen.className);
            StringBuilder trail = new StringBuilder();
            for (int i = 0; i < path.size() - 1; i++) {
                if (trail.length() > 0) trail.append(PATH_SEPARATOR);
                trail.append(context.getString(path.get(i).titleRes));
            }
            String pageTitle = context.getString(screen.titleRes);
            String parentTrail = trail.length() == 0 ? context.getString(R.string.title_activity_termux_settings) : trail.toString();
            entries.add(new Entry(pageTitle, null, parentTrail, screen.className, null, screen.section));
            if (screen.xmlRes == 0) continue;
            String rowTrail = trail.length() == 0 ? pageTitle : trail + PATH_SEPARATOR + pageTitle;
            try {
                // A store that writes nothing: attaching the rows sets their defaults, which must
                // not land in the real preferences just because someone searched.
                PreferenceManager manager = new PreferenceManager(context);
                manager.setPreferenceDataStore(NO_WRITES);
                PreferenceScreen inflated = manager.inflateFromResource(context, screen.xmlRes, null);
                if (inflated != null) collect(inflated, rowTrail, screen, entries);
            } catch (Exception e) {
                // One XML that cannot inflate here costs its rows, not the whole search.
            }
        }
        return new SettingsSearchIndex(entries);
    }

    /** Rows a page hides on a phone that lacks their feature, so a result never points at nothing. */
    private static boolean hiddenOnThisPhone(@Nullable String key) {
        return "terminal_retro_effect".equals(key) && !com.termux.app.terminal.PaneRetroEffect.available();
    }

    /** Reads give the default; writes go nowhere. */
    private static final androidx.preference.PreferenceDataStore NO_WRITES = new androidx.preference.PreferenceDataStore() {
        @Override public void putString(String key, @Nullable String value) {}
        @Override public void putStringSet(String key, @Nullable java.util.Set<String> values) {}
        @Override public void putInt(String key, int value) {}
        @Override public void putLong(String key, long value) {}
        @Override public void putFloat(String key, float value) {}
        @Override public void putBoolean(String key, boolean value) {}
        // With a store every row reads its initial value, default or not; a multi-select without
        // one would be handed null.
        @Nullable
        @Override public java.util.Set<String> getStringSet(String key, @Nullable java.util.Set<String> defValues) {
            return defValues != null ? defValues : new java.util.HashSet<>();
        }
    };

    private static boolean availableInThisBuild(@NonNull SettingsTree.Screen screen) {
        if (com.termux.BuildConfig.X11_SERVER) return true;
        for (SettingsTree.Screen step : SettingsTree.path(screen.className)) {
            if (step.className.endsWith(".X11DisplayPreferencesFragment")) return false;
        }
        return true;
    }

    private static void collect(@NonNull PreferenceGroup group, @NonNull String breadcrumb,
                                @NonNull SettingsTree.Screen screen, @NonNull List<Entry> out) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference row = group.getPreference(i);
            if (row instanceof PreferenceGroup) {
                // A section hidden in the XML is not offered, so neither are its rows.
                if (!row.isVisible()) continue;
                // A section header is not a destination; its rows are.
                collect((PreferenceGroup) row, breadcrumb, screen, out);
                continue;
            }
            if (row instanceof SettingsSearchPreference) continue;
            // A row that opens another page of the tree is found as that page.
            if (row.getFragment() != null && SettingsTree.find(row.getFragment()) != null) continue;
            CharSequence title = row.getTitle();
            if (title == null || title.length() == 0 || !row.isVisible()) continue;
            if (hiddenOnThisPhone(row.getKey())) continue;
            CharSequence summary = row.getSummary();
            // An <extra android:name="search_terms"> in the XML: words a row is found by without
            // showing them, such as a feature's old name. Matched as summary words.
            String terms = row.peekExtras() == null ? null : row.peekExtras().getString("search_terms");
            String searchable = summary == null ? terms
                : terms == null ? summary.toString() : summary + " " + terms;
            out.add(new Entry(title.toString(), searchable, breadcrumb,
                screen.className, row.getKey(), screen.section));
        }
    }

    /**
     * The entries matching {@code query}, best first: a title that is the query, then one that
     * starts with it, then a title holding it as a word start, then anywhere in the title, then
     * in the row's summary.
     * Within a rank, hits in {@code scope} (the section the reader searched from) come first, then
     * pages before rows, then tree order. Every word of a multi-word query must match.
     */
    @NonNull
    public List<Entry> search(@NonNull String query, @Nullable SettingsTree.Section scope, int limit) {
        return search(query, scope, limit, null);
    }

    /**
     * {@link #search}, less every hit in {@code hidden}: Home's section under Use as Terminal
     * only, whose pages a hit would open.
     */
    @NonNull
    public List<Entry> search(@NonNull String query, @Nullable SettingsTree.Section scope, int limit,
                              @Nullable SettingsTree.Section hidden) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> hits = new ArrayList<>();
        if (needle.isEmpty()) return hits;
        String[] words = needle.split("\\s+");
        List<int[]> ranks = new ArrayList<>();
        for (int i = 0; i < mEntries.size(); i++) {
            Entry entry = mEntries.get(i);
            if (hidden != null && entry.section == hidden) continue;
            int rank = rank(entry, needle, words);
            if (rank < 0) continue;
            int scoped = scope != null && entry.section == scope ? 0 : 1;
            int kind = entry.key == null ? 0 : 1;
            ranks.add(new int[] {rank, scoped, kind, i});
        }
        Collections.sort(ranks, (a, b) -> {
            for (int k = 0; k < 4; k++) if (a[k] != b[k]) return Integer.compare(a[k], b[k]);
            return 0;
        });
        for (int[] r : ranks) {
            if (hits.size() >= limit) break;
            Entry entry = mEntries.get(r[3]);
            if (isDuplicate(hits, entry)) continue;
            hits.add(entry);
        }
        return hits;
    }

    /**
     * 0 best .. 4 summary only; -1 no match. A title that is the query itself comes first, so
     * "start in" finds Start in before "Start in the lower half".
     */
    private static int rank(@NonNull Entry entry, @NonNull String needle, @NonNull String[] words) {
        if (entry.titleLower.equals(needle)) return 0;
        if (entry.titleLower.startsWith(needle)) return 1;
        if (entry.titleLower.contains(" " + needle)) return 2;
        if (entry.titleLower.contains(needle)) return 3;
        boolean all = true;
        for (String word : words) {
            if (!entry.titleLower.contains(word) && !entry.summaryLower.contains(word)) {
                all = false;
                break;
            }
        }
        return all ? 4 : -1;
    }

    /** The same row reached twice (a row on a page that is also indexed as itself) shows once. */
    private static boolean isDuplicate(@NonNull List<Entry> hits, @NonNull Entry entry) {
        for (Entry hit : hits) {
            if (hit.screenClass.equals(entry.screenClass) && hit.title.equals(entry.title)
                && (hit.key == null ? entry.key == null : hit.key.equals(entry.key))) return true;
        }
        return false;
    }
}
