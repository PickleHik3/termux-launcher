package com.termux.app.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Frequency and recency of palette action runs, used to fill the frequent-action keycap strip.
 *
 * <p>Mirrors {@link com.termux.app.launcher.data.LauncherUsageStatsStore}: one debounced
 * SharedPreferences blob, counts kept per stable key. The key here is the tool name plus its
 * row arguments, so an app row and a bare tool row rank independently.
 *
 * <p>Ranking is frequency first with a recency tie-break rather than a decayed score, so a
 * strip does not reshuffle under the user between two runs of the same action.
 */
public final class CommandPaletteActionStats {

    private static final String PREFS_KEY = "command_palette_action_stats_v1";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LAST = "last";
    private static final long PERSIST_DEBOUNCE_MS = 750L;

    private static final Comparator<Ranked> RANKING = (a, b) -> {
        int byCount = Integer.compare(b.count, a.count);
        if (byCount != 0) return byCount;
        return Long.compare(b.lastRunEpochMs, a.lastRunEpochMs);
    };

    private final SharedPreferences sharedPreferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Stat> statsByKey = new HashMap<>();
    private final Runnable persistRunnable = this::persist;
    private boolean loaded;

    public CommandPaletteActionStats(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
    }

    public synchronized void recordRun(@NonNull String key) {
        ensureLoaded();
        Stat stat = statsByKey.get(key);
        if (stat == null) {
            stat = new Stat();
            statsByKey.put(key, stat);
        }
        stat.count = Math.max(0, stat.count) + 1;
        stat.lastRunEpochMs = System.currentTimeMillis();
        mainHandler.removeCallbacks(persistRunnable);
        mainHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS);
    }

    /** True when nothing has ever been run, so the caller shows its default strip instead. */
    public synchronized boolean isEmpty() {
        ensureLoaded();
        return statsByKey.isEmpty();
    }

    /**
     * The {@code limit} most-used entries of {@code entries}, best first. Entries with no
     * history are dropped, so a short history yields a short strip rather than a padded one.
     */
    @NonNull
    public synchronized List<CommandPaletteFilter.Entry> rank(
        @NonNull List<CommandPaletteFilter.Entry> entries, int limit) {
        ensureLoaded();
        List<Ranked> ranked = new ArrayList<>();
        for (CommandPaletteFilter.Entry entry : entries) {
            Stat stat = statsByKey.get(keyFor(entry));
            if (stat == null || stat.count <= 0) continue;
            ranked.add(new Ranked(entry, stat.count, stat.lastRunEpochMs));
        }
        Collections.sort(ranked, RANKING);
        List<CommandPaletteFilter.Entry> out = new ArrayList<>(Math.min(limit, ranked.size()));
        for (int i = 0; i < ranked.size() && out.size() < limit; i++) out.add(ranked.get(i).entry);
        return out;
    }

    /** Stable identity of a row: the tool plus the arguments that row supplies. */
    @NonNull
    public static String keyFor(@NonNull CommandPaletteFilter.Entry entry) {
        if (entry.arguments == null || entry.arguments.length() == 0) return entry.toolName;
        return entry.toolName + " " + entry.arguments.toString();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        String raw = sharedPreferences.getString(PREFS_KEY, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject json = root.optJSONObject(key);
                if (json == null) continue;
                Stat stat = new Stat();
                stat.count = Math.max(0, json.optInt(FIELD_COUNT, 0));
                stat.lastRunEpochMs = Math.max(0L, json.optLong(FIELD_LAST, 0L));
                statsByKey.put(key, stat);
            }
        } catch (JSONException ignored) {
        }
    }

    private synchronized void persist() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, Stat> entry : statsByKey.entrySet()) {
                Stat stat = entry.getValue();
                if (stat == null || stat.count <= 0) continue;
                JSONObject json = new JSONObject();
                json.put(FIELD_COUNT, stat.count);
                json.put(FIELD_LAST, stat.lastRunEpochMs);
                root.put(entry.getKey(), json);
            }
        } catch (JSONException ignored) {
        }
        sharedPreferences.edit().putString(PREFS_KEY, root.toString()).apply();
    }

    private static final class Stat {
        int count;
        long lastRunEpochMs;
    }

    private static final class Ranked {
        final CommandPaletteFilter.Entry entry;
        final int count;
        final long lastRunEpochMs;

        Ranked(CommandPaletteFilter.Entry entry, int count, long lastRunEpochMs) {
            this.entry = entry;
            this.count = count;
            this.lastRunEpochMs = lastRunEpochMs;
        }
    }
}
