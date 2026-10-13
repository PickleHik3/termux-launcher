package com.termux.app.terminal;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the user ran for a query: type "term", run Termux, and the next "term" puts Termux first
 * whatever the ranking would have said ({@link CommandPaletteRanking}).
 *
 * <p>Queries are normalized the way {@link CommandPaletteRanking.Query} reads them (trimmed, lower
 * case, the actions-only {@code >} dropped) and point at rows by
 * {@link CommandPaletteActionStats#keyFor}. Each pick keeps a count and when it last happened, and
 * the row that leads is the one picked most for that query, its count decayed by
 * {@link com.termux.app.launcher.data.LauncherUsageStatsStore#decayedScore} the way usage is, so a
 * one-off pick of something else does not unseat a habit; recency only breaks ties. The least
 * recently picked queries are forgotten past {@link #MAX_QUERIES}.
 *
 * <p>The book itself is pure and unit-tested; {@link Store} persists it the way the action stats
 * do, as one debounced JSON blob in the default preferences file.
 */
public final class CommandPaletteQueryMemory {

    static final int MAX_QUERIES = 200;
    /** Rows remembered per query; past this the oldest pick goes. */
    static final int MAX_ROWS_PER_QUERY = 8;

    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LAST = "last";

    /** Query to its picks, in pick recency, oldest first, so eviction takes the head. */
    private final LinkedHashMap<String, LinkedHashMap<String, Pick>> mByQuery =
        new LinkedHashMap<>();

    /** The memory key for a query line, or empty when there is nothing to remember it by. */
    @NonNull
    static String normalize(@Nullable String query) {
        return CommandPaletteRanking.Query.parse(query).normalized();
    }

    /**
     * Remembers that {@code rowKey} was run from {@code query}.
     *
     * @return false, having remembered nothing, for an empty query or row key
     */
    boolean record(@Nullable String query, @NonNull String rowKey, long nowEpochMs) {
        String key = normalize(query);
        if (key.isEmpty() || rowKey.isEmpty()) return false;
        LinkedHashMap<String, Pick> picks = mByQuery.remove(key);
        if (picks == null) picks = new LinkedHashMap<>();
        mByQuery.put(key, picks);
        Pick pick = picks.remove(rowKey);
        if (pick == null) pick = new Pick();
        pick.count = Math.max(0, pick.count) + 1;
        pick.lastEpochMs = nowEpochMs;
        picks.put(rowKey, pick);
        trim(picks, MAX_ROWS_PER_QUERY);
        trim(mByQuery, MAX_QUERIES);
        return true;
    }

    /** Rows picked for exactly this query, row key to its weight as of {@code nowEpochMs}. */
    @NonNull
    Map<String, Remembered> picksFor(@Nullable String query, long nowEpochMs) {
        LinkedHashMap<String, Pick> picks = mByQuery.get(normalize(query));
        if (picks == null || picks.isEmpty()) return Collections.emptyMap();
        Map<String, Remembered> out = new HashMap<>(picks.size());
        for (Map.Entry<String, Pick> pick : picks.entrySet()) {
            Pick value = pick.getValue();
            out.put(pick.getKey(), new Remembered(com.termux.app.launcher.data
                .LauncherUsageStatsStore.decayedScore(value.count, value.lastEpochMs, nowEpochMs),
                value.lastEpochMs));
        }
        return out;
    }

    int size() {
        return mByQuery.size();
    }

    @NonNull
    String toJson() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, LinkedHashMap<String, Pick>> query : mByQuery.entrySet()) {
                JSONObject picks = new JSONObject();
                for (Map.Entry<String, Pick> pick : query.getValue().entrySet()) {
                    JSONObject json = new JSONObject();
                    json.put(FIELD_COUNT, pick.getValue().count);
                    json.put(FIELD_LAST, pick.getValue().lastEpochMs);
                    picks.put(pick.getKey(), json);
                }
                root.put(query.getKey(), picks);
            }
        } catch (JSONException ignored) {
        }
        return root.toString();
    }

    /**
     * Reads a book {@link #toJson} wrote. JSON objects carry no order, so recency is rebuilt from
     * the stored pick times. A malformed blob reads as an empty book.
     */
    @NonNull
    static CommandPaletteQueryMemory fromJson(@Nullable String raw) {
        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        if (raw == null || raw.trim().isEmpty()) return memory;
        List<Loaded> loaded = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> queries = root.keys();
            while (queries.hasNext()) {
                String query = queries.next();
                JSONObject picksJson = root.optJSONObject(query);
                if (query.isEmpty() || picksJson == null) continue;
                List<Map.Entry<String, Pick>> picks = new ArrayList<>();
                long newest = 0L;
                Iterator<String> rows = picksJson.keys();
                while (rows.hasNext()) {
                    String row = rows.next();
                    JSONObject json = picksJson.optJSONObject(row);
                    if (json == null) continue;
                    Pick pick = new Pick();
                    pick.count = Math.max(0, json.optInt(FIELD_COUNT, 0));
                    pick.lastEpochMs = Math.max(0L, json.optLong(FIELD_LAST, 0L));
                    if (pick.count <= 0) continue;
                    picks.add(new java.util.AbstractMap.SimpleEntry<>(row, pick));
                    newest = Math.max(newest, pick.lastEpochMs);
                }
                if (picks.isEmpty()) continue;
                picks.sort((a, b) ->
                    Long.compare(a.getValue().lastEpochMs, b.getValue().lastEpochMs));
                loaded.add(new Loaded(query, picks, newest));
            }
        } catch (JSONException ignored) {
        }
        loaded.sort((a, b) -> Long.compare(a.newest, b.newest));
        for (Loaded query : loaded) {
            LinkedHashMap<String, Pick> picks = new LinkedHashMap<>();
            for (Map.Entry<String, Pick> pick : query.picks)
                picks.put(pick.getKey(), pick.getValue());
            trim(picks, MAX_ROWS_PER_QUERY);
            memory.mByQuery.put(query.query, picks);
        }
        trim(memory.mByQuery, MAX_QUERIES);
        return memory;
    }

    /** Drops the oldest entries, the map's head, until it holds at most {@code max}. */
    private static void trim(@NonNull LinkedHashMap<String, ?> map, int max) {
        Iterator<String> keys = map.keySet().iterator();
        while (map.size() > max && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private static final class Pick {
        int count;
        long lastEpochMs;
    }

    /**
     * One remembered row's weight for a query: its decayed pick count, and when it was last picked
     * for the tie-break.
     */
    static final class Remembered {
        final double score;
        final long lastEpochMs;

        Remembered(double score, long lastEpochMs) {
            this.score = score;
            this.lastEpochMs = lastEpochMs;
        }

        /** Whether this row leads {@code other}: the heavier pick, then the more recent one. */
        boolean outranks(@NonNull Remembered other) {
            int byScore = Double.compare(score, other.score);
            return byScore != 0 ? byScore > 0 : lastEpochMs > other.lastEpochMs;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Remembered)) return false;
            Remembered other = (Remembered) o;
            return Double.compare(score, other.score) == 0 && lastEpochMs == other.lastEpochMs;
        }

        @Override
        public int hashCode() {
            return 31 * Double.hashCode(score) + Long.hashCode(lastEpochMs);
        }
    }

    private static final class Loaded {
        final String query;
        final List<Map.Entry<String, Pick>> picks;
        final long newest;

        Loaded(String query, List<Map.Entry<String, Pick>> picks, long newest) {
            this.query = query;
            this.picks = picks;
            this.newest = newest;
        }
    }

    /**
     * The persisted book: loaded on first use, written back debounced, in the same preferences
     * file as {@link CommandPaletteActionStats}.
     */
    static final class Store {

        private static final String PREFS_KEY = "command_palette_query_memory_v1";
        private static final long PERSIST_DEBOUNCE_MS = 750L;

        private final SharedPreferences mPreferences;
        private final Handler mMainHandler = new Handler(Looper.getMainLooper());
        private final Runnable mPersistRunnable = this::persist;
        @Nullable private CommandPaletteQueryMemory mMemory;

        Store(@NonNull Context context) {
            mPreferences = context.getApplicationContext().getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
                Context.MODE_PRIVATE);
        }

        synchronized void record(@Nullable String query, @NonNull String rowKey) {
            if (!memory().record(query, rowKey, System.currentTimeMillis())) return;
            mMainHandler.removeCallbacks(mPersistRunnable);
            mMainHandler.postDelayed(mPersistRunnable, PERSIST_DEBOUNCE_MS);
        }

        @NonNull
        synchronized Map<String, Remembered> picksFor(@Nullable String query, long nowEpochMs) {
            return memory().picksFor(query, nowEpochMs);
        }

        @NonNull
        private CommandPaletteQueryMemory memory() {
            if (mMemory == null) mMemory = fromJson(mPreferences.getString(PREFS_KEY, ""));
            return mMemory;
        }

        private synchronized void persist() {
            mPreferences.edit().putString(PREFS_KEY, memory().toJson()).apply();
        }
    }
}
