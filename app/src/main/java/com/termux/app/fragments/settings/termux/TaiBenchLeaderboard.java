package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiBenchGuardRules;
import com.termux.ai.TaiBenchStore;
import com.termux.ai.TaiModelSpec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Home screen's view of {@code GET /v1/ai/benchmarks}: the leaderboard rows with their marks
 * (charging, ran warm, low battery, battery saver, screen off, older app or runtime version, not installed), the
 * one ranked list, and one entry's history for the Result screen's chart. Pure
 * over the JSON {@code TaiManager.benchmarks()} returns, so {@code TaiBenchLeaderboardTest}
 * drives it without a device.
 */
final class TaiBenchLeaderboard {
    /** The app and runtime versions this build is: anything else on a record is "older". */
    static final class Versions {
        @NonNull final String appVersion;
        @NonNull final String liteRtVersion;
        @NonNull final String mnnVersion;

        Versions(@NonNull String appVersion, @NonNull String liteRtVersion, @NonNull String mnnVersion) {
            this.appVersion = appVersion;
            this.liteRtVersion = liteRtVersion;
            this.mnnVersion = mnnVersion;
        }

        @NonNull
        String runtimeVersionFor(@NonNull String backend) {
            return TaiModelSpec.BACKEND_MNN_LLM.equals(backend) ? mnnVersion : liteRtVersion;
        }
    }

    /**
     * The {@code X.Y.Z} core of an app version: drops {@code +dev.<sha>} build metadata and any
     * legacy {@code -nix} / {@code -vaj} / {@code -a} suffix, so nightlies of one release group together.
     */
    @NonNull
    static String versionCore(@NonNull String version) {
        int cut = version.length();
        int plus = version.indexOf('+');
        if (plus >= 0) cut = plus;
        int dash = version.indexOf('-');
        if (dash >= 0 && dash < cut) cut = dash;
        return version.substring(0, cut);
    }

    /** One leaderboard entry as Home shows it. */
    static final class Row {
        /** The place in the one list (verdict, then decode speed, then first token); {@code 0} for a broken entry. */
        final int rank;
        @NonNull final String key;
        @NonNull final String recordId;
        @NonNull final String modelId;
        @NonNull final String displayName;
        @NonNull final String backend;
        @NonNull final String accelerator;
        final boolean speculative;
        final long timestamp;
        @NonNull final String preset;
        final double decodeTps;
        /** {@code NaN} when the test never ran. */
        final double ttftMs;
        final double readMs;
        /** The long log was cut from the top to fit the loaded window. */
        final boolean truncated;
        final long loadMs;
        final long memBytes;
        final boolean checkPassed;
        @NonNull final String verdict;
        @NonNull final String runtimeVersion;
        @NonNull final String appVersion;
        final boolean charging;
        /** Started warm, or the phone heated up during the entry: kept, marked, never the best. */
        final boolean warmStart;
        final boolean lowBattery;
        final boolean powerSave;
        final boolean screenOff;
        /** The entry's runtime died; there are no figures. */
        final boolean crashed;
        final boolean olderVersion;
        final boolean installed;

        Row(@NonNull JSONObject row, @NonNull Versions versions, boolean installed) {
            rank = row.optInt("rank", 0);
            key = row.optString("key", "");
            recordId = row.optString("recordId", "");
            modelId = row.optString("modelId", "");
            displayName = row.optString("displayName", modelId);
            backend = row.optString("backend", "");
            accelerator = row.optString("accelerator", "");
            speculative = row.optBoolean("speculative", false);
            timestamp = row.optLong("timestamp", 0L);
            preset = row.optString("preset", "");
            decodeTps = row.optDouble("decodeTps", 0.0);
            ttftMs = row.isNull("ttftMs") ? Double.NaN : row.optDouble("ttftMs", Double.NaN);
            readMs = row.isNull("readMs") ? Double.NaN : row.optDouble("readMs", Double.NaN);
            truncated = row.optBoolean("truncated", false);
            loadMs = row.isNull("loadMs") ? -1L : row.optLong("loadMs", -1L);
            memBytes = row.isNull("memBytes") ? -1L : row.optLong("memBytes", -1L);
            checkPassed = row.optBoolean("checkPassed", false);
            verdict = row.optString("verdict", "");
            runtimeVersion = row.optString("runtimeVersion", "");
            appVersion = row.optString("appVersion", "");
            JSONObject conditions = row.optJSONObject("conditions");
            charging = conditions != null && conditions.optBoolean("charging", false);
            warmStart = conditions != null
                && (conditions.optBoolean("warmStart", false) || conditions.optBoolean("thermalRose", false));
            powerSave = conditions != null && conditions.optBoolean("powerSave", false);
            screenOff = conditions != null && conditions.optBoolean("screenOff", false);
            crashed = TaiBenchStore.STATUS_CRASHED.equals(row.optString("status", ""));
            int batteryStart = conditions == null || conditions.isNull("batteryStart") ? -1 : conditions.optInt("batteryStart", -1);
            lowBattery = batteryStart >= 0 && batteryStart < TaiBenchGuardRules.START_BATTERY_MIN_PERCENT && !charging;
            olderVersion = (!appVersion.isEmpty() && !versionCore(appVersion).equals(versionCore(versions.appVersion)))
                || (!runtimeVersion.isEmpty() && !runtimeVersion.equals(versions.runtimeVersionFor(backend)));
            this.installed = installed;
        }
    }

    /** Both lists Home shows: the ranked rows in rank order, and the broken ones. */
    static final class Board {
        @NonNull final List<Row> ranked;
        @NonNull final List<Row> broken;
        /** Records of another bench version: kept, shown as a count, never ranked. */
        final int otherVersionRecords;
        /** The newest record's timestamp, or {@code 0} with none. */
        final long lastRunMs;

        Board(@NonNull List<Row> ranked, @NonNull List<Row> broken, int otherVersionRecords, long lastRunMs) {
            this.ranked = ranked;
            this.broken = broken;
            this.otherVersionRecords = otherVersionRecords;
            this.lastRunMs = lastRunMs;
        }

        boolean empty() {
            return ranked.isEmpty() && broken.isEmpty();
        }

        /** The fastest decode speed of any clean ranked entry, or {@code 0} with none: what a "New best" must beat. */
        double bestTps() {
            double best = 0.0;
            for (Row row : ranked) if (!row.warmStart) best = Math.max(best, row.decodeTps);
            return best;
        }
    }

    /** One record of one entry, for the history chart. */
    static final class Point {
        final long timestamp;
        final double decodeTps;
        @NonNull final String appVersion;
        @NonNull final String runtimeVersion;
        @NonNull final String status;
        final boolean checkPassed;

        Point(long timestamp, double decodeTps, @NonNull String appVersion, @NonNull String runtimeVersion,
              @NonNull String status, boolean checkPassed) {
            this.timestamp = timestamp;
            this.decodeTps = decodeTps;
            this.appVersion = appVersion;
            this.runtimeVersion = runtimeVersion;
            this.status = status;
            this.checkPassed = checkPassed;
        }
    }

    private TaiBenchLeaderboard() {
    }

    /** Reads the {@code benchmarks()} JSON into rows with their marks. */
    @NonNull
    static Board read(@Nullable JSONObject benchmarks, @NonNull Versions versions) {
        if (benchmarks == null) return new Board(Collections.emptyList(), Collections.emptyList(), 0, 0L);
        Map<String, Boolean> installedById = new HashMap<>();
        int otherVersion = 0;
        long lastRun = 0L;
        String benchVersion = benchmarks.optString("benchVersion", "");
        JSONArray records = benchmarks.optJSONArray("records");
        if (records != null) {
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record == null) continue;
                installedById.put(record.optString("id", ""), record.optBoolean("installed", true));
                lastRun = Math.max(lastRun, record.optLong("timestamp", 0L));
                if (!benchVersion.isEmpty() && !benchVersion.equals(record.optString("benchVersion", ""))) otherVersion++;
            }
        }
        JSONObject leaderboard = benchmarks.optJSONObject("leaderboard");
        List<Row> ranked = rows(leaderboard == null ? null : leaderboard.optJSONArray("ranked"), versions, installedById);
        List<Row> broken = rows(leaderboard == null ? null : leaderboard.optJSONArray("broken"), versions, installedById);
        return new Board(ranked, broken, otherVersion, lastRun);
    }

    @NonNull
    private static List<Row> rows(@Nullable JSONArray array, @NonNull Versions versions, @NonNull Map<String, Boolean> installedById) {
        List<Row> rows = new ArrayList<>();
        if (array == null) return rows;
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            Boolean installed = installedById.get(row.optString("recordId", ""));
            rows.add(new Row(row, versions, installed == null || installed));
        }
        return rows;
    }

    /** The row for {@code key} on either list, or {@code null}. */
    @Nullable
    static Row find(@NonNull Board board, @NonNull String key) {
        for (Row row : board.ranked) if (row.key.equals(key)) return row;
        for (Row row : board.broken) if (row.key.equals(key)) return row;
        return null;
    }

    /**
     * Every record of {@code key} that measured a decode speed (complete or timed out), oldest
     * first: the Result screen's history. Skipped and stopped records have no speed and are left
     * out, and so are the records of an older bench version, which have no chat.
     */
    @NonNull
    static List<Point> history(@Nullable JSONObject benchmarks, @NonNull String key) {
        List<Point> points = new ArrayList<>();
        JSONArray records = benchmarks == null ? null : benchmarks.optJSONArray("records");
        if (records == null) return points;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || !key.equals(TaiBenchStore.keyOf(record))) continue;
            String status = record.optString("status", "");
            if (!TaiBenchStore.STATUS_COMPLETE.equals(status) && !"timeout".equals(status)) continue;
            JSONObject phases = record.optJSONObject("phases");
            JSONObject chat = phases == null ? null : phases.optJSONObject("chat");
            if (chat == null || chat.optJSONObject("decodeTps") == null) continue;
            JSONObject check = record.optJSONObject("check");
            boolean passed = check != null && check.optInt("total", 0) > 0 && check.optInt("passed", 0) >= check.optInt("total", 0);
            points.add(new Point(record.optLong("timestamp", 0L), TaiBenchStore.median(chat, "decodeTps", 0.0),
                record.optString("appVersion", ""), record.optString("runtimeVersion", ""), status, passed));
        }
        Collections.sort(points, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        return points;
    }

    /**
     * The indexes in {@code points} before which the chart draws a divider: where the app or
     * runtime version changed from the point before. Never index 0.
     */
    @NonNull
    static List<Integer> dividers(@NonNull List<Point> points) {
        List<Integer> dividers = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            Point before = points.get(i - 1);
            Point point = points.get(i);
            if (!before.appVersion.equals(point.appVersion) || !before.runtimeVersion.equals(point.runtimeVersion)) dividers.add(i);
        }
        return dividers;
    }

    /** The latest record of {@code key}, whatever its status, or {@code null}. */
    @Nullable
    static JSONObject latestRecord(@Nullable JSONObject benchmarks, @NonNull String key) {
        JSONArray records = benchmarks == null ? null : benchmarks.optJSONArray("records");
        if (records == null) return null;
        JSONObject latest = null;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || !key.equals(TaiBenchStore.keyOf(record))) continue;
            if (latest == null || record.optLong("timestamp", 0L) >= latest.optLong("timestamp", 0L)) latest = record;
        }
        return latest;
    }

    /** The latest complete (or timed-out, which still measured) record of {@code key}, or {@code null}. */
    @Nullable
    static JSONObject latestMeasured(@Nullable JSONObject benchmarks, @NonNull String key) {
        JSONArray records = benchmarks == null ? null : benchmarks.optJSONArray("records");
        if (records == null) return null;
        JSONObject latest = null;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || !key.equals(TaiBenchStore.keyOf(record))) continue;
            String status = record.optString("status", "");
            if (!TaiBenchStore.STATUS_COMPLETE.equals(status) && !"timeout".equals(status)) continue;
            if (latest == null || record.optLong("timestamp", 0L) >= latest.optLong("timestamp", 0L)) latest = record;
        }
        return latest;
    }

    /**
     * The Model centre's speed pill: the best (highest) decode speed the ranked leaderboard
     * shows per model, across every accelerator that model was measured on. A broken entry
     * carries no speed and is ignored. Pure over the same JSON {@link #read} takes, so the
     * Model centre can compute it off the main thread without building a full {@link Board}.
     */
    @NonNull
    static Map<String, Double> bestSpeedByModel(@Nullable JSONObject benchmarks) {
        Map<String, Double> best = new HashMap<>();
        JSONObject leaderboard = benchmarks == null ? null : benchmarks.optJSONObject("leaderboard");
        JSONArray ranked = leaderboard == null ? null : leaderboard.optJSONArray("ranked");
        if (ranked == null) return best;
        for (int i = 0; i < ranked.length(); i++) {
            JSONObject row = ranked.optJSONObject(i);
            if (row == null) continue;
            String modelId = row.optString("modelId", "");
            if (modelId.isEmpty()) continue;
            double decodeTps = row.optDouble("decodeTps", 0.0);
            if (decodeTps <= 0.0) continue;
            Double current = best.get(modelId);
            if (current == null || decodeTps > current) best.put(modelId, decodeTps);
        }
        return best;
    }

    /**
     * The quiet speed pill's number, without a unit: rounded to an integer at 10 and above, one
     * decimal place below it (so a slow model still reads as more than "0"). The caller wraps it
     * in {@code R.string.tai_bench_tps} for the unit, same as {@code TaiBenchViews.tps}.
     */
    @NonNull
    static String formatTpsValue(double decodeTps) {
        if (decodeTps >= 10.0) return String.valueOf(Math.round(decodeTps));
        double rounded = Math.round(decodeTps * 10.0) / 10.0;
        return rounded == Math.rint(rounded)
            ? String.format(java.util.Locale.US, "%.0f", rounded)
            : String.format(java.util.Locale.US, "%.1f", rounded);
    }
}
