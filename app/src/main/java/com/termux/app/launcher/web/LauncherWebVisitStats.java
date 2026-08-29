package com.termux.app.launcher.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How often, and how recently, each web address was opened from the launcher.
 *
 * <p>Android gives no app the browser's own history, so this is the only history there is: the
 * pages this launcher opened. That is enough for what it is for — the palette offering back the
 * handful of addresses the user actually goes to.
 *
 * <p>Mirrors {@link com.termux.app.terminal.CommandPaletteActionStats}: one debounced
 * SharedPreferences blob, frequency first with recency as the tie-break so the list does not
 * reshuffle between two visits to the same page.
 */
public final class LauncherWebVisitStats {

    private static final String PREFS_KEY = "web_visit_stats_v1";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LAST = "last";
    private static final String FIELD_TITLE = "title";
    private static final long PERSIST_DEBOUNCE_MS = 750L;

    /** Bounded because it grows forever otherwise, and because this is a suggestion list. */
    private static final int MAX_ENTRIES = 200;

    @Nullable
    private static volatile LauncherWebVisitStats sInstance;

    /** One visited address. */
    public static final class Visit {
        public final String url;
        public final String title;
        public final int count;
        public final long lastVisitEpochMs;

        Visit(@NonNull String url, @NonNull String title, int count, long lastVisitEpochMs) {
            this.url = url;
            this.title = title;
            this.count = count;
            this.lastVisitEpochMs = lastVisitEpochMs;
        }
    }

    private static final Comparator<Visit> RANKING = (a, b) -> {
        int byCount = Integer.compare(b.count, a.count);
        if (byCount != 0) return byCount;
        return Long.compare(b.lastVisitEpochMs, a.lastVisitEpochMs);
    };

    private final SharedPreferences sharedPreferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Visit> visitsByUrl = new HashMap<>();
    private final Runnable persistRunnable = this::persist;
    private boolean loaded;

    private LauncherWebVisitStats(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
    }

    @NonNull
    public static LauncherWebVisitStats getInstance(@NonNull Context context) {
        LauncherWebVisitStats instance = sInstance;
        if (instance != null) return instance;
        synchronized (LauncherWebVisitStats.class) {
            if (sInstance == null) sInstance = new LauncherWebVisitStats(context);
            return sInstance;
        }
    }

    /** Records one visit. {@code title} may be null, in which case the host names the row. */
    public synchronized void recordVisit(@NonNull String url, @Nullable String title) {
        ensureLoaded();
        Visit existing = visitsByUrl.get(url);
        String name = title != null && !title.trim().isEmpty()
            ? title.trim()
            : (existing != null ? existing.title : LauncherWebLinks.labelFor(url));
        visitsByUrl.put(url, new Visit(url, name,
            (existing == null ? 0 : existing.count) + 1, System.currentTimeMillis()));
        pruneIfNeeded();
        mainHandler.removeCallbacks(persistRunnable);
        mainHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS);
    }

    /**
     * The best {@code limit} matches for {@code query}, best first. An empty query ranks the
     * whole history, which is what a bare prefix in the palette shows.
     */
    @NonNull
    public synchronized List<Visit> rank(@NonNull String query, int limit) {
        ensureLoaded();
        if (limit <= 0 || visitsByUrl.isEmpty()) return Collections.emptyList();
        String needle = query.trim().toLowerCase(Locale.US);
        List<Visit> matches = new ArrayList<>();
        for (Visit visit : visitsByUrl.values()) {
            if (!needle.isEmpty()
                && !visit.url.toLowerCase(Locale.US).contains(needle)
                && !visit.title.toLowerCase(Locale.US).contains(needle)) continue;
            matches.add(visit);
        }
        Collections.sort(matches, RANKING);
        return matches.size() <= limit ? matches : new ArrayList<>(matches.subList(0, limit));
    }

    /** Drops every recorded visit, for a "clear history" control. */
    public synchronized void clear() {
        ensureLoaded();
        visitsByUrl.clear();
        mainHandler.removeCallbacks(persistRunnable);
        persist();
    }

    private void pruneIfNeeded() {
        if (visitsByUrl.size() <= MAX_ENTRIES) return;
        List<Visit> ranked = new ArrayList<>(visitsByUrl.values());
        Collections.sort(ranked, RANKING);
        for (int i = MAX_ENTRIES; i < ranked.size(); i++) {
            visitsByUrl.remove(ranked.get(i).url);
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        String stored = sharedPreferences.getString(PREFS_KEY, null);
        if (stored == null || stored.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(stored);
            for (Iterator<String> keys = root.keys(); keys.hasNext(); ) {
                String url = keys.next();
                JSONObject entry = root.optJSONObject(url);
                if (entry == null) continue;
                visitsByUrl.put(url, new Visit(url,
                    entry.optString(FIELD_TITLE, LauncherWebLinks.labelFor(url)),
                    Math.max(0, entry.optInt(FIELD_COUNT, 0)),
                    Math.max(0L, entry.optLong(FIELD_LAST, 0L))));
            }
        } catch (JSONException e) {
            // A blob we cannot read is a suggestion list we cannot offer, never a failure the
            // user should see: start over rather than throwing on the way into the palette.
            visitsByUrl.clear();
        }
    }

    private synchronized void persist() {
        JSONObject root = new JSONObject();
        try {
            for (Visit visit : visitsByUrl.values()) {
                JSONObject entry = new JSONObject();
                entry.put(FIELD_COUNT, visit.count);
                entry.put(FIELD_LAST, visit.lastVisitEpochMs);
                entry.put(FIELD_TITLE, visit.title);
                root.put(visit.url, entry);
            }
        } catch (JSONException e) {
            return;
        }
        sharedPreferences.edit().putString(PREFS_KEY, root.toString()).apply();
    }
}
