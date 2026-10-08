package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The feature check's results file, {@code files/tai/feature-checks.json}, beside the bench's
 * ({@link TaiBenchStore}) and written the same way: one writer (the app process), every write through
 * {@link TaiBenchStore#writeAtomically}, a file that does not parse read as empty. Records are
 * {@link TaiFeatureCheck#record}'s; the last {@link #KEEP_PER_KEY} of each run's key are kept.
 *
 * <p>Crash recovery: the run in progress is marked on disk before its load ({@link #markInProgress}); a
 * marker still there when the next check starts means the app died during that run, and
 * {@link #recoverStaleMarker} writes it down as crashed. A death of the runtime process alone reaches the
 * runner as an error and is recorded there.
 */
public final class TaiFeatureCheckStore {
    static final String FILE_NAME = "feature-checks.json";
    static final String MARKER_SUFFIX = ".inprogress";
    /** Runs kept per key (feature, model file, accelerator, speculative decoding); only the latest is read. */
    static final int KEEP_PER_KEY = 3;
    /** Records kept overall, whatever their keys. */
    static final int KEEP_TOTAL = 200;
    private static final int FORMAT_VERSION = 1;

    @NonNull private final File file;

    TaiFeatureCheckStore(@NonNull File file) {
        this.file = file;
    }

    /** The store under {@code filesDir/tai/}. */
    @NonNull
    public static TaiFeatureCheckStore in(@NonNull File filesDir) {
        return new TaiFeatureCheckStore(new File(new File(filesDir, "tai"), FILE_NAME));
    }

    @NonNull
    File file() {
        return file;
    }

    /** Appends one record and trims the file. */
    public synchronized void append(@NonNull JSONObject record) throws IOException {
        List<JSONObject> records = readRecords();
        records.add(record);
        write(trim(records));
    }

    /** Every record, oldest first. */
    @NonNull
    public synchronized List<JSONObject> records() {
        return readRecords();
    }

    /** The newest record of each key, in the order each key was last written. */
    @NonNull
    public static List<JSONObject> latest(@NonNull List<JSONObject> records) {
        Map<String, JSONObject> latest = new LinkedHashMap<>();
        for (JSONObject record : records) {
            String key = TaiFeatureCheck.keyOf(record);
            latest.remove(key);
            latest.put(key, record);
        }
        return new ArrayList<>(latest.values());
    }

    // ---- staleness ---------------------------------------------------------------------------

    /**
     * The version of the runtime that runs {@code backend}'s models: MNN's and LiteRT-LM's own, and for the
     * speech and embedding runtimes, which ship inside the app, the app's release ({@code X.Y.Z}, without a
     * nightly's build suffix).
     */
    @NonNull
    public static String runtimeVersion(@NonNull String backend) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) return "mnn " + MnnTaiRuntime.RUNTIME_VERSION;
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(backend)) return "litert-lm " + com.termux.BuildConfig.LITERT_LM_VERSION;
        String version = com.termux.BuildConfig.VERSION_NAME;
        int build = version.indexOf('+');
        return "app " + (build >= 0 ? version.substring(0, build) : version);
    }

    /** {@code spec}'s staleness key now: its file ({@link TaiFeatureCheck#fileKey}) and its runtime's version. Stats the file. */
    @NonNull
    public static String stalenessKey(@NonNull TaiModelSpec spec) {
        File path = spec.localPath == null || spec.localPath.trim().isEmpty() ? null : new File(spec.localPath);
        long modified = path == null ? 0L : path.lastModified();
        return TaiFeatureCheck.stalenessKey(TaiFeatureCheck.fileKey(spec.sha256, TaiResidency.fileBytes(spec), modified),
            runtimeVersion(spec.backend));
    }

    // ---- the in-progress marker --------------------------------------------------------------

    @NonNull
    private File markerFile() {
        return new File(file.getParentFile(), file.getName() + MARKER_SUFFIX);
    }

    /** Writes down the run about to load ({@link TaiFeatureCheck#record} of what is known so far). */
    public synchronized void markInProgress(@NonNull JSONObject record) throws IOException {
        TaiBenchStore.writeAtomically(markerFile(), record.toString());
    }

    public synchronized void clearInProgress() {
        //noinspection ResultOfMethodCallIgnored
        markerFile().delete();
    }

    /**
     * A marker left on disk by a run the app died in: stored as a crashed record with {@code reason}, and
     * returned; {@code null} when there was none.
     */
    @Nullable
    public synchronized JSONObject recoverStaleMarker(@NonNull String reason) throws IOException {
        File marker = markerFile();
        if (!marker.isFile()) return null;
        JSONObject record;
        try {
            record = new JSONObject(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
            record.put("status", TaiFeatureCheck.STATUS_CRASHED).put("passed", false).put("reason", reason)
                .put("verdict", JSONObject.NULL).put("figure", 0.0);
        } catch (IOException | JSONException | RuntimeException e) {
            clearInProgress();
            return null;
        }
        append(record);
        clearInProgress();
        return record;
    }

    // ---- the file ----------------------------------------------------------------------------

    /** Keeps the newest {@link #KEEP_PER_KEY} of each key, in the file's order, then the newest {@link #KEEP_TOTAL}. */
    @NonNull
    static List<JSONObject> trim(@NonNull List<JSONObject> records) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JSONObject record : records) {
            String key = TaiFeatureCheck.keyOf(record);
            Integer count = counts.get(key);
            counts.put(key, count == null ? 1 : count + 1);
        }
        List<JSONObject> kept = new ArrayList<>(records.size());
        for (JSONObject record : records) {
            String key = TaiFeatureCheck.keyOf(record);
            if (counts.get(key) > KEEP_PER_KEY) {
                // Oldest first in the file: drop this one and count it off.
                counts.put(key, counts.get(key) - 1);
                continue;
            }
            kept.add(record);
        }
        if (kept.size() > KEEP_TOTAL) kept = new ArrayList<>(kept.subList(kept.size() - KEEP_TOTAL, kept.size()));
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
            return new ArrayList<>(Collections.<JSONObject>emptyList());
        }
        return records;
    }

    private void write(@NonNull List<JSONObject> records) throws IOException {
        JSONArray array = new JSONArray();
        for (JSONObject record : records) array.put(record);
        try {
            TaiBenchStore.writeAtomically(file, new JSONObject().put("version", FORMAT_VERSION).put("records", array).toString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
