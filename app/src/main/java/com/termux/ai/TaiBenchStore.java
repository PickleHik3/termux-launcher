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
 * <p>Record layout (bench v2): {@code id, benchVersion, preset, timestamp, durationMs, modelId,
 * displayName, sizeBytes, sha256, backend, accelerator, speculative, runtimeVersion, appVersion,
 * device{soc, ramClassGb}, conditions{batteryStart, batteryEnd, charging, thermalStart,
 * thermalEnd, headroomStart, headroomEnd, warmStart, thermalRose, thermalPeak, powerSave,
 * screenOff},
 * phases{load{ms, memBytes}, chat{ttftMs{med,min,max,runs}, decodeTps{…}, tokens, reply,
 * finishReason, reasoningTokens, tokenLimit}, longInput{readMs{…}, promptTps{…}, promptTokens,
 * contextWindow, truncated, keptChars, totalChars, peakPssBytes, finishReason, …}},
 * check{passed, total, details}, status, verdict}. The entry key is
 * {@code modelId|backend|accelerator|speculative}. Older records stay in the file, whatever their
 * layout, and are only ever read for their key and version. {@link #memoryBytes} is the memory
 * figure of a record's phases: the peak PSS sampled during the long input, else the older
 * MemAvailable difference of the load.
 */
public final class TaiBenchStore {
    static final String FILE_NAME = "benchmarks.json";
    /** Runs kept per leaderboard entry; the oldest go first. */
    static final int KEEP_PER_KEY = 20;
    public static final String STATUS_COMPLETE = "complete";
    /**
     * The runtime process died (or the app did) while this entry was loading or running. Like
     * every status but {@link #STATUS_COMPLETE} it never ranks; the Choose screen reads it to keep
     * the model out of the default selection.
     */
    public static final String STATUS_CRASHED = "crashed";
    static final String MARKER_SUFFIX = ".inprogress";
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
    public static String keyOf(@NonNull JSONObject record) {
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

    // ---- the in-progress marker --------------------------------------------------------------

    @NonNull
    private File markerFile() {
        return new File(file.getParentFile(), file.getName() + MARKER_SUFFIX);
    }

    /**
     * Writes down which entry is about to load, before it does, so a death of the runtime or the
     * app mid-load can still be recorded ({@link #recoverStaleMarker}). One marker at a time; the
     * next entry overwrites it. {@code marker} is {@code {modelId, backend, accelerator,
     * speculative, preset, benchVersion, timestamp, appVersion, device}}.
     */
    synchronized void markInProgress(@NonNull JSONObject marker) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent);
        }
        File target = markerFile();
        File temp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(marker.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | UnsupportedOperationException atomicUnsupported) {
            if (!temp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                throw new IOException("Could not replace " + target);
            }
        }
    }

    /** The entry marked in progress, or {@code null} when none is (or the marker does not parse). */
    @Nullable
    synchronized JSONObject inProgress() {
        File target = markerFile();
        if (!target.isFile()) return null;
        try {
            return new JSONObject(new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | JSONException | RuntimeException e) {
            return null;
        }
    }

    synchronized void clearInProgress() {
        //noinspection ResultOfMethodCallIgnored
        markerFile().delete();
    }

    /**
     * A marker still on disk when a run starts means the app or the runtime died mid-load last
     * time: writes the {@code crashed} record for it and clears the marker. Returns the record, or
     * {@code null} when there was nothing to recover.
     */
    @Nullable
    synchronized JSONObject recoverStaleMarker(@NonNull String reason) throws IOException {
        JSONObject marker = inProgress();
        if (marker == null) {
            clearInProgress();
            return null;
        }
        JSONObject record = crashedRecord(marker, reason);
        append(record);
        clearInProgress();
        return record;
    }

    /**
     * The record for an entry that died: no figures, {@code status: crashed}, and the reason in
     * {@code skipReason} (which the run screen shows as the entry's note).
     */
    @NonNull
    public static JSONObject crashedRecord(@NonNull JSONObject marker, @NonNull String reason) {
        try {
            long started = marker.optLong("timestamp", System.currentTimeMillis());
            JSONObject phases = new JSONObject().put("load", JSONObject.NULL).put("chat", JSONObject.NULL)
                .put("longInput", JSONObject.NULL);
            JSONObject check = new JSONObject().put("passed", 0).put("total", TaiBenchSuite.CHECKS.size())
                .put("details", new JSONArray());
            JSONObject device = marker.optJSONObject("device");
            return new JSONObject()
                .put("id", java.util.UUID.randomUUID().toString())
                .put("benchVersion", marker.optString("benchVersion", TaiBenchSuite.BENCH_VERSION))
                .put("preset", marker.optString("preset", ""))
                .put("timestamp", started)
                .put("durationMs", Math.max(0L, System.currentTimeMillis() - started))
                .put("modelId", marker.optString("modelId", ""))
                .put("displayName", marker.optString("displayName", marker.optString("modelId", "")))
                .put("sizeBytes", marker.optLong("sizeBytes", 0L))
                .put("sha256", JSONObject.NULL)
                .put("backend", marker.optString("backend", ""))
                .put("accelerator", marker.optString("accelerator", ""))
                .put("speculative", marker.optBoolean("speculative", false))
                .put("runtimeVersion", "")
                .put("appVersion", marker.optString("appVersion", ""))
                .put("device", device == null ? new JSONObject() : device)
                .put("conditions", new JSONObject())
                .put("phases", phases)
                .put("check", check)
                .put("status", STATUS_CRASHED)
                .put("verdict", JSONObject.NULL)
                .put("skipReason", reason);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
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
     * The leaderboard for one bench version: one complete record per entry, ranked by
     * verdict (Smooth, Usable, Slow), then decode speed, then the chat's first token. The record is
     * the latest clean one; a record that {@link #ranWarm ran warm} stands in only for an entry
     * with no clean one, so a hot-phone run never outranks (or replaces) a clean run of the same
     * model on the same processor. Entries whose sanity check failed are {@code broken}: returned,
     * not ranked, and so is an entry whose latest record is a crash with no complete record after
     * it. Records of other bench versions are not looked at, so two versions never share a
     * ranking; stopped, skipped and timed-out records never rank either, though an older complete
     * record of the same entry does. The verdict is worked out from the record's figures, so a
     * change to the thresholds re-grades the records already on file.
     */
    @NonNull
    public static JSONObject leaderboard(@NonNull JSONArray records, @NonNull String benchVersion) throws JSONException {
        Map<String, JSONObject> latestClean = new LinkedHashMap<>();
        Map<String, JSONObject> latestWarm = new LinkedHashMap<>();
        Map<String, JSONObject> crashed = new LinkedHashMap<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            if (!benchVersion.equals(record.optString("benchVersion", ""))) continue;
            String status = record.optString("status", "");
            String key = keyOf(record);
            if (STATUS_CRASHED.equals(status)) {
                crashed.put(key, record);
                continue;
            }
            if (!STATUS_COMPLETE.equals(status)) continue;
            // A complete record after a crash means the entry runs again.
            crashed.remove(key);
            (ranWarm(record) ? latestWarm : latestClean).put(key, record);
        }
        Map<String, JSONObject> latestComplete = new LinkedHashMap<>(latestClean);
        for (Map.Entry<String, JSONObject> entry : latestWarm.entrySet()) {
            if (!latestComplete.containsKey(entry.getKey())) latestComplete.put(entry.getKey(), entry.getValue());
        }
        List<JSONObject> ranked = new ArrayList<>();
        List<JSONObject> broken = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : latestComplete.entrySet()) {
            JSONObject row = row(entry.getKey(), entry.getValue());
            (row.optBoolean("checkPassed", false) ? ranked : broken).add(row);
        }
        for (Map.Entry<String, JSONObject> entry : crashed.entrySet()) {
            broken.add(row(entry.getKey(), entry.getValue()));
        }
        Collections.sort(ranked, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                int byVerdict = Integer.compare(TaiBenchStats.verdictOrder(b.optString("verdict", "")),
                    TaiBenchStats.verdictOrder(a.optString("verdict", "")));
                if (byVerdict != 0) return byVerdict;
                int bySpeed = Double.compare(b.optDouble("decodeTps", 0.0), a.optDouble("decodeTps", 0.0));
                if (bySpeed != 0) return bySpeed;
                return Double.compare(ttftForSort(a), ttftForSort(b));
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

    /**
     * The memory figure of a record's {@code phases}: the runtime process's peak PSS sampled
     * while the long input was read ({@code longInput.peakPssBytes}) when it was measured, else
     * the MemAvailable difference of the load ({@code load.memBytes}), which says little about an
     * mmap'd MNN package (the same model has read 4 MB and 660 MB) and is only the fallback;
     * {@code -1} when neither was measured.
     */
    public static long memoryBytes(@Nullable JSONObject phases) {
        JSONObject longInput = phases == null ? null : phases.optJSONObject("longInput");
        long peak = longInput == null ? -1L : longInput.optLong("peakPssBytes", -1L);
        if (peak > 0L) return peak;
        JSONObject load = phases == null ? null : phases.optJSONObject("load");
        return load == null ? -1L : load.optLong("memBytes", -1L);
    }

    /** {@code parent.key.med}, or {@code fallback} when the figure is missing. */
    public static double median(@Nullable JSONObject parent, @NonNull String key, double fallback) {
        JSONObject figure = parent == null ? null : parent.optJSONObject(key);
        return figure == null ? fallback : figure.optDouble("med", fallback);
    }

    /**
     * Whether a record was measured on a hot phone: it started after a cool-down that was skipped
     * or ran out ({@code warmStart}), or the thermal status rose above the run's start during the
     * entry ({@code thermalRose}).
     */
    public static boolean ranWarm(@NonNull JSONObject record) {
        JSONObject conditions = record.optJSONObject("conditions");
        return conditions != null && (conditions.optBoolean("warmStart", false) || conditions.optBoolean("thermalRose", false));
    }

    /** A missing first-token figure sorts after every measured one. */
    private static double ttftForSort(@NonNull JSONObject row) {
        double value = row.optDouble("ttftMs", Double.NaN);
        return Double.isNaN(value) || value <= 0.0 ? Double.MAX_VALUE : value;
    }

    /** The leaderboard's view of one record: the headline figures, not the runs. */
    @NonNull
    private static JSONObject row(@NonNull String key, @NonNull JSONObject record) throws JSONException {
        JSONObject phases = record.optJSONObject("phases");
        JSONObject check = record.optJSONObject("check");
        JSONObject chat = phases == null ? null : phases.optJSONObject("chat");
        JSONObject longInput = phases == null ? null : phases.optJSONObject("longInput");
        JSONObject load = phases == null ? null : phases.optJSONObject("load");
        boolean checkPassed = check != null && check.optInt("total", 0) > 0
            && check.optInt("passed", 0) >= check.optInt("total", 0);
        double decodeTps = median(chat, "decodeTps", 0.0);
        double ttftMs = median(chat, "ttftMs", 0.0);
        double readMs = median(longInput, "readMs", 0.0);
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
        row.put("decodeTps", decodeTps);
        row.put("ttftMs", chat == null || chat.optJSONObject("ttftMs") == null ? JSONObject.NULL : ttftMs);
        row.put("readMs", longInput == null || longInput.optJSONObject("readMs") == null ? JSONObject.NULL : readMs);
        row.put("truncated", longInput != null && longInput.optBoolean("truncated", false));
        row.put("loadMs", load == null ? JSONObject.NULL : load.optLong("ms", 0L));
        row.put("memBytes", memoryBytes(phases));
        row.put("checkPassed", checkPassed);
        row.put("status", record.optString("status", ""));
        row.put("verdict", STATUS_CRASHED.equals(record.optString("status", "")) ? TaiBenchStats.VERDICT_CRASHED
            : TaiBenchStats.verdict(decodeTps, Math.round(ttftMs), Math.round(readMs), checkPassed));
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
