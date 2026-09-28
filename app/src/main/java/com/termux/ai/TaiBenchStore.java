package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The benchmark results file, {@code files/tai/benchmarks.json}: every record the harness has
 * produced, the last {@link #KEEP_PER_KEY} per leaderboard entry, and the leaderboard query over
 * them. One writer, the app (UI) process, which appends each record as its {@code entry_done}
 * event arrives; the runtime process never touches the file, so a runtime crash mid-bench loses
 * at most the entry it was on.
 *
 * <p>Plain Java over a {@link File} so the JVM tests run it against a temp directory. Every write
 * goes to a sibling temp file and is renamed over the old one, so a reader never sees half a file
 * and a crash mid-write leaves the previous file intact. A file that does not parse is treated as
 * empty and overwritten by the next append; results are measurements, not user data, and a run
 * makes new ones.
 *
 * <p>Record layout (bench v1): {@code id, benchVersion, preset, timestamp, modelId, displayName,
 * sizeBytes, sha256, backend, accelerator, speculative, runtimeVersion, appVersion, device{soc,
 * ramClassGb}, conditions{batteryStart, batteryEnd, charging, thermalStart, thermalEnd,
 * headroomStart, headroomEnd, warmStart},
 * phases{load{ms, memBytes}, reading{med,min,max,runs}, firstWord{…}, writing{…, tokens},
 * sustained{startTps, endTps, dropPct}|null}, check{passed, total, details}, status, verdict}.
 * The entry key is {@code modelId|backend|accelerator|speculative}.
 */
final class TaiBenchStore {
    static final String FILE_NAME = "benchmarks.json";
    /** Runs kept per leaderboard entry; the oldest go first. */
    static final int KEEP_PER_KEY = 20;
    static final String STATUS_COMPLETE = "complete";
    private static final int FORMAT_VERSION = 1;

    @NonNull private final File file;

    TaiBenchStore(@NonNull File file) {
        this.file = file;
    }

    /** The store under {@code filesDir/tai/}. */
    @NonNull
    static TaiBenchStore in(@NonNull File filesDir) {
        return new TaiBenchStore(new File(new File(filesDir, "tai"), FILE_NAME));
    }

    @NonNull
    File file() {
        return file;
    }

    /** {@code modelId|backend|accelerator|speculative}, the leaderboard entry a record belongs to. */
    @NonNull
    static String keyOf(@NonNull JSONObject record) {
        return TaiBenchSuite.EntryPlan.key(record.optString("modelId", ""), record.optString("backend", ""),
            record.optString("accelerator", ""), record.optBoolean("speculative", false));
    }

    /** Appends one record, trims its entry to the last {@link #KEEP_PER_KEY}, and writes the file. */
    synchronized void append(@NonNull JSONObject record) throws IOException {
        List<JSONObject> records = readRecords();
        records.add(record);
        write(trim(records));
    }

    /** Every record, oldest first. */
    @NonNull
    synchronized JSONArray records() {
        JSONArray array = new JSONArray();
        for (JSONObject record : readRecords()) array.put(record);
        return array;
    }

    /** Removes every record, or only {@code modelId}'s; returns how many went. */
    synchronized int clear(@Nullable String modelId) throws IOException {
        List<JSONObject> records = readRecords();
        List<JSONObject> kept = new ArrayList<>();
        for (JSONObject record : records) {
            if (modelId != null && !modelId.isEmpty() && !modelId.equals(record.optString("modelId", ""))) kept.add(record);
        }
        int removed = records.size() - kept.size();
        if (removed > 0) write(kept);
        return removed;
    }

    /** {@code records}, the leaderboard for {@code benchVersion}, and every version on file. */
    @NonNull
    synchronized JSONObject toJson(@NonNull String benchVersion) throws JSONException {
        JSONArray records = records();
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("file", file.getAbsolutePath());
        json.put("benchVersion", benchVersion);
        json.put("records", records);
        json.put("leaderboard", leaderboard(records, benchVersion));
        JSONArray versions = new JSONArray();
        for (String version : benchVersions(records)) versions.put(version);
        json.put("benchVersions", versions);
        return json;
    }

    /** The distinct bench versions in {@code records}, in first-seen order. */
    @NonNull
    static List<String> benchVersions(@NonNull JSONArray records) {
        Set<String> versions = new LinkedHashSet<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null) versions.add(record.optString("benchVersion", ""));
        }
        return new ArrayList<>(versions);
    }

    /**
     * The leaderboard for one bench version: the latest complete record per entry, ranked by the
     * median writing speed, ties to the faster first word. Entries whose check failed are
     * {@code broken}: returned, not ranked. Records of other bench versions are not looked at, so
     * two versions never share a ranking; stopped, skipped and timed-out records never rank
     * either, though an older complete record of the same entry does.
     */
    @NonNull
    static JSONObject leaderboard(@NonNull JSONArray records, @NonNull String benchVersion) throws JSONException {
        Map<String, JSONObject> latestComplete = new LinkedHashMap<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            if (!benchVersion.equals(record.optString("benchVersion", ""))) continue;
            if (!STATUS_COMPLETE.equals(record.optString("status", ""))) continue;
            latestComplete.put(keyOf(record), record);
        }
        List<JSONObject> ranked = new ArrayList<>();
        List<JSONObject> broken = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : latestComplete.entrySet()) {
            JSONObject row = row(entry.getKey(), entry.getValue());
            (row.optBoolean("checkPassed", false) ? ranked : broken).add(row);
        }
        Collections.sort(ranked, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                int bySpeed = Double.compare(b.optDouble("writingTps", 0.0), a.optDouble("writingTps", 0.0));
                if (bySpeed != 0) return bySpeed;
                return Double.compare(firstWordForSort(a), firstWordForSort(b));
            }
        });
        JSONArray rankedJson = new JSONArray();
        for (int i = 0; i < ranked.size(); i++) {
            JSONObject row = ranked.get(i);
            row.put("rank", i + 1);
            rankedJson.put(row);
        }
        JSONArray brokenJson = new JSONArray();
        for (JSONObject row : broken) brokenJson.put(row);
        return new JSONObject()
            .put("benchVersion", benchVersion)
            .put("ranked", rankedJson)
            .put("broken", brokenJson);
    }

    /** A missing first-word figure sorts after every measured one. */
    private static double firstWordForSort(@NonNull JSONObject row) {
        double value = row.optDouble("firstWordMs", Double.NaN);
        return Double.isNaN(value) ? Double.MAX_VALUE : value;
    }

    /** The leaderboard's view of one record: the headline figures, not the runs. */
    @NonNull
    private static JSONObject row(@NonNull String key, @NonNull JSONObject record) throws JSONException {
        JSONObject phases = record.optJSONObject("phases");
        JSONObject check = record.optJSONObject("check");
        JSONObject writing = phases == null ? null : phases.optJSONObject("writing");
        JSONObject firstWord = phases == null ? null : phases.optJSONObject("firstWord");
        JSONObject reading = phases == null ? null : phases.optJSONObject("reading");
        JSONObject load = phases == null ? null : phases.optJSONObject("load");
        boolean checkPassed = check != null && check.optInt("total", 0) > 0
            && check.optInt("passed", 0) >= check.optInt("total", 0);
        double writingTps = writing == null ? 0.0 : writing.optDouble("med", 0.0);
        JSONObject row = new JSONObject();
        row.put("key", key);
        row.put("recordId", record.optString("id", ""));
        row.put("modelId", record.optString("modelId", ""));
        row.put("displayName", record.optString("displayName", record.optString("modelId", "")));
        row.put("backend", record.optString("backend", ""));
        row.put("accelerator", record.optString("accelerator", ""));
        row.put("speculative", record.optBoolean("speculative", false));
        row.put("timestamp", record.optLong("timestamp", 0L));
        row.put("preset", record.optString("preset", ""));
        row.put("writingTps", writingTps);
        row.put("firstWordMs", firstWord == null ? JSONObject.NULL : firstWord.optDouble("med", 0.0));
        row.put("readingTps", reading == null ? JSONObject.NULL : reading.optDouble("med", 0.0));
        row.put("loadMs", load == null ? JSONObject.NULL : load.optLong("ms", 0L));
        row.put("memBytes", load == null ? JSONObject.NULL : load.optLong("memBytes", -1L));
        row.put("checkPassed", checkPassed);
        row.put("verdict", record.optString("verdict", TaiBenchStats.verdict(writingTps, checkPassed)));
        row.put("conditions", record.opt("conditions") == null ? JSONObject.NULL : record.opt("conditions"));
        row.put("runtimeVersion", record.optString("runtimeVersion", ""));
        row.put("appVersion", record.optString("appVersion", ""));
        return row;
    }

    /** Keeps the newest {@link #KEEP_PER_KEY} of each entry, in the file's order. */
    @NonNull
    private static List<JSONObject> trim(@NonNull List<JSONObject> records) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JSONObject record : records) {
            String key = keyOf(record);
            Integer count = counts.get(key);
            counts.put(key, count == null ? 1 : count + 1);
        }
        List<JSONObject> kept = new ArrayList<>(records.size());
        for (JSONObject record : records) {
            String key = keyOf(record);
            int surplus = counts.get(key) - KEEP_PER_KEY;
            if (surplus > 0) {
                // Oldest first in the file, so the first `surplus` of this key are the ones to drop.
                counts.put(key, counts.get(key) - 1);
                continue;
            }
            kept.add(record);
        }
        return kept;
    }

    @NonNull
    private List<JSONObject> readRecords() {
        List<JSONObject> records = new ArrayList<>();
        if (!file.isFile()) return records;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONArray array = new JSONObject(text).optJSONArray("records");
            if (array == null) return records;
            for (int i = 0; i < array.length(); i++) {
                JSONObject record = array.optJSONObject(i);
                if (record != null) records.add(record);
            }
        } catch (IOException | JSONException | RuntimeException ignored) {
            // Unreadable or not ours: start over on the next write.
            records.clear();
        }
        return records;
    }

    private void write(@NonNull List<JSONObject> records) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent);
        }
        JSONArray array = new JSONArray();
        for (JSONObject record : records) array.put(record);
        String payload;
        try {
            payload = new JSONObject().put("version", FORMAT_VERSION).put("records", array).toString();
        } catch (JSONException e) {
            throw new IOException(e);
        }
        File temp = new File(parent, file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(payload.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | UnsupportedOperationException atomicUnsupported) {
            if (!temp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                throw new IOException("Could not replace " + file);
            }
        }
    }
}
