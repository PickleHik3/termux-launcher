package com.termux.app.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.LauncherAppEntry;
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
 * Persists app usage stats and provides stable ranking for AZ workflow.
 */
public final class LauncherUsageStatsStore {

    private static final String PREFS_KEY_USAGE_STATS_V1 = "app_launcher_az_usage_stats_v1";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LAST = "last";
    private static final long PERSIST_DEBOUNCE_MS = 750L;
    @Nullable private static LauncherUsageStatsStore processInstance;
    private static final Comparator<RankedEntry> USAGE_RANKING_COMPARATOR = (a, b) -> {
        int countComparison = Integer.compare(b.count, a.count);
        if (countComparison != 0) return countComparison;
        int labelComparison = safeLabel(a.entry).compareToIgnoreCase(safeLabel(b.entry));
        if (labelComparison != 0) return labelComparison;
        return 0; // Collections.sort is stable, so identical labels retain source order.
    };
    private static final Comparator<RankedEntry> SUGGESTION_RANKING_COMPARATOR = (a, b) -> {
        int scoreComparison = Double.compare(b.decayedScore, a.decayedScore);
        if (scoreComparison != 0) return scoreComparison;
        int countComparison = Integer.compare(b.count, a.count);
        if (countComparison != 0) return countComparison;
        int lastComparison = Long.compare(b.lastLaunchEpochMs, a.lastLaunchEpochMs);
        if (lastComparison != 0) return lastComparison;
        int labelComparison = safeLabel(a.entry).compareToIgnoreCase(safeLabel(b.entry));
        if (labelComparison != 0) return labelComparison;
        return a.entry.appRef.stableId().compareTo(b.entry.appRef.stableId());
    };
    /** Half-life of a launch's suggestion weight; two weeks idle costs half the count. */
    private static final double DECAY_HALF_LIFE_DAYS = 14.0;
    private static final double MS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0;

    private final SharedPreferences sharedPreferences;
    @NonNull private final Context applicationContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, UsageStat> usageByStableId = new HashMap<>();
    private final Runnable persistRunnable = this::persist;
    private boolean loaded;

    public LauncherUsageStatsStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.applicationContext = appContext;
        this.sharedPreferences = appContext.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
    }

    /** One process-live cache shared by every launcher usage reader and writer. */
    @NonNull
    public static synchronized LauncherUsageStatsStore getInstance(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (processInstance == null || processInstance.applicationContext != appContext)
            processInstance = new LauncherUsageStatsStore(appContext);
        return processInstance;
    }

    public synchronized void recordLaunch(@NonNull String stableId) {
        ensureLoaded();
        UsageStat stat = usageByStableId.get(stableId);
        if (stat == null) {
            stat = new UsageStat();
            usageByStableId.put(stableId, stat);
        }
        stat.count = Math.max(0, stat.count) + 1;
        stat.lastLaunchEpochMs = System.currentTimeMillis();
        mainHandler.removeCallbacks(persistRunnable);
        mainHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS);
    }

    public synchronized void clear() {
        mainHandler.removeCallbacks(persistRunnable);
        usageByStableId.clear();
        loaded = true;
        sharedPreferences.edit().putString(PREFS_KEY_USAGE_STATS_V1, "").apply();
    }

    @NonNull
    public synchronized List<LauncherAppEntry> rankForAz(@NonNull List<LauncherAppEntry> entries) {
        ensureLoaded();
        if (entries.size() <= 1) {
            return new ArrayList<>(entries);
        }
        List<RankedEntry> ranked = new ArrayList<>(entries.size());
        for (LauncherAppEntry entry : entries) {
            UsageStat stat = usageByStableId.get(entry.appRef.stableId());
            ranked.add(new RankedEntry(entry, stat == null ? 0 : stat.count));
        }
        Collections.sort(ranked, USAGE_RANKING_COMPARATOR);
        List<LauncherAppEntry> sorted = new ArrayList<>(ranked.size());
        for (RankedEntry item : ranked) sorted.add(item.entry);
        return sorted;
    }

    /** Positive-use entries only, ordered by decayed score, count, recency, label, stable id. */
    @NonNull
    public synchronized List<LauncherAppEntry> rankForSuggestions(
        @NonNull List<LauncherAppEntry> entries) {
        return rankForSuggestions(entries, System.currentTimeMillis());
    }

    @NonNull
    public synchronized List<LauncherAppEntry> rankForSuggestions(
        @NonNull List<LauncherAppEntry> entries, long nowEpochMs) {
        ensureLoaded();
        List<RankedEntry> ranked = new ArrayList<>(entries.size());
        for (LauncherAppEntry entry : entries) {
            UsageStat stat = usageByStableId.get(entry.appRef.stableId());
            if (stat == null || stat.count <= 0) continue;
            ranked.add(new RankedEntry(entry, stat.count, stat.lastLaunchEpochMs,
                decayedScore(stat.count, stat.lastLaunchEpochMs, nowEpochMs)));
        }
        Collections.sort(ranked, SUGGESTION_RANKING_COMPARATOR);
        List<LauncherAppEntry> sorted = new ArrayList<>(ranked.size());
        for (RankedEntry item : ranked) sorted.add(item.entry);
        return sorted;
    }

    /** Per-stable-id decayed scores for the given entries; never-used entries are omitted. */
    @NonNull
    public synchronized Map<String, Double> decayedScores(@NonNull List<LauncherAppEntry> entries,
                                                          long nowEpochMs) {
        ensureLoaded();
        Map<String, Double> scores = new HashMap<>();
        for (LauncherAppEntry entry : entries) {
            UsageStat stat = usageByStableId.get(entry.appRef.stableId());
            if (stat == null || stat.count <= 0) continue;
            scores.put(entry.appRef.stableId(),
                decayedScore(stat.count, stat.lastLaunchEpochMs, nowEpochMs));
        }
        return scores;
    }

    /** {@code count * 0.5^(daysSinceLastLaunch / 14)}; future timestamps clamp to age zero. */
    public static double decayedScore(int count, long lastLaunchEpochMs, long nowEpochMs) {
        if (count <= 0) return 0.0;
        double days = Math.max(0L, nowEpochMs - lastLaunchEpochMs) / MS_PER_DAY;
        return count * Math.pow(0.5, days / DECAY_HALF_LIFE_DAYS);
    }

    private static String safeLabel(@NonNull LauncherAppEntry entry) {
        return entry.label == null ? "" : entry.label;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        usageByStableId.clear();
        String raw = sharedPreferences.getString(PREFS_KEY_USAGE_STATS_V1, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> keyIterator = root.keys();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                JSONObject statJson = root.optJSONObject(key);
                if (statJson == null) continue;
                UsageStat stat = new UsageStat();
                stat.count = Math.max(0, statJson.optInt(FIELD_COUNT, 0));
                stat.lastLaunchEpochMs = Math.max(0L, statJson.optLong(FIELD_LAST, 0L));
                usageByStableId.put(key, stat);
            }
        } catch (JSONException ignored) {
        }
    }

    private synchronized void persist() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, UsageStat> entry : usageByStableId.entrySet()) {
                UsageStat stat = entry.getValue();
                if (stat == null || stat.count <= 0) continue;
                JSONObject statJson = new JSONObject();
                statJson.put(FIELD_COUNT, stat.count);
                statJson.put(FIELD_LAST, stat.lastLaunchEpochMs);
                root.put(entry.getKey(), statJson);
            }
        } catch (JSONException ignored) {
        }
        sharedPreferences.edit().putString(PREFS_KEY_USAGE_STATS_V1, root.toString()).apply();
    }

    private static final class UsageStat {
        int count;
        long lastLaunchEpochMs;
    }

    private static final class RankedEntry {
        final LauncherAppEntry entry;
        final int count;
        final long lastLaunchEpochMs;
        final double decayedScore;

        RankedEntry(@NonNull LauncherAppEntry entry, int count) {
            this(entry, count, 0L, 0.0);
        }

        RankedEntry(@NonNull LauncherAppEntry entry, int count, long lastLaunchEpochMs,
                    double decayedScore) {
            this.entry = entry;
            this.count = count;
            this.lastLaunchEpochMs = lastLaunchEpochMs;
            this.decayedScore = decayedScore;
        }
    }
}
