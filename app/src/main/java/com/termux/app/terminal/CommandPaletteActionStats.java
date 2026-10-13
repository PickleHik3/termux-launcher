package com.termux.app.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Frequency and recency of palette row runs (actions, apps and contacts), used to fill the keycap
 * strip ({@link CommandPaletteStrip}) and to break ties between equally good matches in a search.
 *
 * <p>Mirrors {@link com.termux.app.launcher.data.LauncherUsageStatsStore}: one debounced
 * SharedPreferences blob, counts kept per stable key. The key here is the tool name plus its
 * row arguments, so an app row and a bare tool row rank independently.
 *
 * <p>Both readers take {@link #decayedScores} once when the palette opens, so neither the strip
 * nor a search reshuffles while it is up.
 */
public final class CommandPaletteActionStats {

    private static final String PREFS_KEY = "command_palette_action_stats_v1";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LAST = "last";
    private static final long PERSIST_DEBOUNCE_MS = 750L;

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

    /**
     * Decayed usage per row key, by the launcher's own formula
     * ({@link com.termux.app.launcher.data.LauncherUsageStatsStore#decayedScore}): what ranks the
     * keycap strip and breaks ties between equal matches in a search.
     */
    @NonNull
    public synchronized Map<String, Double> decayedScores(long nowEpochMs) {
        ensureLoaded();
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, Stat> entry : statsByKey.entrySet()) {
            Stat stat = entry.getValue();
            if (stat == null || stat.count <= 0) continue;
            scores.put(entry.getKey(), com.termux.app.launcher.data.LauncherUsageStatsStore
                .decayedScore(stat.count, stat.lastRunEpochMs, nowEpochMs));
        }
        return scores;
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
}
