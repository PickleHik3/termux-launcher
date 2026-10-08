package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What this device has learned about its models: which accelerators failed, what loads measured,
 * which models reject a system role.
 *
 * <p>The history lives in its own file, {@code files/tai/runtime-history.json}, read from disk on
 * every access and rewritten atomically under an exclusive file lock. It used to be one string in
 * the shared {@code termux_ai} preferences, and two processes write to that file: the app process
 * (settings, the API server, {@code clearRuntimeHistory}) and the {@code :tai_runtime} process
 * (every load and every runtime outcome). SharedPreferences caches the whole file per process and
 * writes the whole map back on every {@code apply()}, so a process holding a stale copy overwrote
 * the other's change. On 2026-10-04 {@code tai runtime --clear-history} removed 61 entries in the
 * app process, and the runtime process's next record wrote its stale 61 entries straight back,
 * so a GPU failure we had cleared returned and kept demoting the GPU. The same clobber could also
 * revert any TAI setting changed in the app process. With a file of its own, nothing is cached,
 * the lock is held across each read-modify-write, and both processes see one truth.
 *
 * <p>The old preferences key is read once, to migrate an existing history into the file, and
 * removed; after that this class never touches SharedPreferences.
 */
public final class TaiRuntimeHistory {
    /** The legacy preferences key, kept only so an existing history can be migrated out of it. */
    private static final String KEY_HISTORY = "tai_runtime_history_json";
    /** Entries are never otherwise pruned; past this many, the oldest {@code updatedAtMs} go first. */
    static final int MAX_ENTRIES = 200;
    private static final String FILE_NAME = "runtime-history.json";
    private static final String LOCK_NAME = "runtime-history.lock";

    private TaiRuntimeHistory() {
    }

    public static void recordSuccess(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator
    ) {
        record(context, model, device, backend, accelerator, true, "");
    }

    public static void recordFailure(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator,
        @NonNull String reason
    ) {
        record(context, model, device, backend, accelerator, false, reason);
    }

    public static boolean hasSuccessfulGpu(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device
    ) {
        JSONObject entry = entry(context, model, device, "gpu");
        return entry != null && entry.optBoolean("success", false);
    }

    public static void recordAudioInputOutcome(
        @NonNull Context context,
        @NonNull String modelId,
        @NonNull TaiDeviceCapabilities device,
        boolean success
    ) {
        withLock(context, history -> {
            try {
                JSONObject entry = new JSONObject();
                entry.put("modelId", modelId);
                entry.put("device", deviceKey(device));
                entry.put("feature", "audio_input");
                entry.put("success", success);
                entry.put("updatedAtMs", System.currentTimeMillis());
                history.put("audio_input|" + modelId + "|" + deviceKey(device), entry);
                return true;
            } catch (JSONException ignored) {
                return false;
            }
        });
    }

    @Nullable
    public static JSONObject audioInputEntry(
        @NonNull Context context,
        @NonNull String modelId,
        @NonNull TaiDeviceCapabilities device
    ) {
        return history(context).optJSONObject("audio_input|" + modelId + "|" + deviceKey(device));
    }

    public static boolean hasFailedAudioInput(
        @NonNull Context context,
        @NonNull String modelId,
        @NonNull TaiDeviceCapabilities device
    ) {
        JSONObject entry = audioInputEntry(context, modelId, device);
        return entry != null && !entry.optBoolean("success", false);
    }

    /**
     * Records that this model's chat template rejects a system-role message (codegemma among
     * them) — {@link TaiSystemPromptFolding} folds the system text into the first user turn
     * instead, and this verdict makes later conversations with the model skip straight to the
     * folded form rather than paying for the failed attempt every time.
     */
    public static void recordSystemRoleUnsupported(@NonNull Context context, @NonNull String modelId) {
        withLock(context, history -> {
            try {
                JSONObject entry = new JSONObject();
                entry.put("modelId", modelId);
                entry.put("feature", "system_role");
                entry.put("success", false);
                entry.put("updatedAtMs", System.currentTimeMillis());
                history.put(systemRoleKey(modelId), entry);
                return true;
            } catch (JSONException ignored) {
                return false;
            }
        });
    }

    /** Whether this model is already known to reject a system-role message; see {@link #recordSystemRoleUnsupported}. */
    public static boolean isSystemRoleKnownUnsupported(@NonNull Context context, @Nullable String modelId) {
        if (modelId == null) return false;
        JSONObject entry = history(context).optJSONObject(systemRoleKey(modelId));
        return entry != null && !entry.optBoolean("success", true);
    }

    @NonNull
    private static String systemRoleKey(@NonNull String modelId) {
        // Keyed by the underlying model, not a per-modality virtual variant: whether a chat
        // template accepts a system role is a property of the model file, shared across every
        // request shape.
        return "system_role|" + TaiModelVariants.baseModelId(modelId);
    }

    /**
     * Whether a preflight refusal with this code is a verdict on the model/accelerator pair, and so
     * worth recording as its failure. See the caller in TaiManager for why the rest are not.
     */
    public static boolean isAcceleratorVerdict(@Nullable String errorCode) {
        if (errorCode == null || errorCode.isEmpty()) return false;
        return !errorCode.startsWith("low_available_memory")
            && !errorCode.startsWith("model_file_")
            && !"known_failed_accelerator".equals(errorCode);
    }

    /**
     * Whether a failed load result with this code says something about the accelerator, and so is
     * worth recording as its failure. A cancelled load is the incident: one GPU load of gemma-4-e4b
     * was cancelled, "Model load cancelled." was written down as a GPU failure, and from then on the
     * automatic order put the CPU first, so a background job ran a minute per request on
     * the CPU. Cancellations, timeouts, memory refusals, file problems, a load already in progress
     * and "known failed" echoes are about the moment, not the accelerator; native or GPU
     * initialisation failures, unsupported operations, corrupt output and crashes are verdicts. An
     * empty code is no verdict either, since nothing says what went wrong.
     */
    public static boolean isRuntimeVerdict(@Nullable String errorCode) {
        if (!isAcceleratorVerdict(errorCode)) return false;
        String code = errorCode.toLowerCase(Locale.ROOT);
        return !code.contains("cancel")
            && !code.contains("timeout")
            && !code.contains("timed_out")
            && !code.contains("insufficient_memory")
            && !code.startsWith("known_failed")
            && !"load_in_progress".equals(code)
            && !"generation_active".equals(code)
            && !"model_not_loaded".equals(code)
            && !"backend_mismatch".equals(code);
    }

    /**
     * Records written before {@link #isAcceleratorVerdict} existed, by a load tried while the model
     * file was still missing. They say nothing about the accelerator, so they are not treated as
     * failures; this is what un-sticks a phone that already has one.
     */
    static boolean isStaleFileMissingRecord(@NonNull JSONObject entry) {
        String reason = entry.optString("reason", "");
        return reason.startsWith("Download or import this model");
    }

    /**
     * How long a failure record demotes its accelerator. A failure is a snapshot of one build on one
     * day (a driver hiccup, a bug a later release fixed), so it must not decide the backend for
     * ever: after this long the accelerator is tried again, and a new verdict writes a new record.
     */
    static final long FAILURE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;

    @Nullable
    public static JSONObject failedEntry(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String accelerator
    ) {
        JSONObject entry = entry(context, model, device, accelerator);
        if (entry == null || entry.optBoolean("success", false)) return null;
        if (isStaleFileMissingRecord(entry)) return null;
        if (isExpired(entry, System.currentTimeMillis(), appVersionCode(context))) return null;
        return entry;
    }

    /**
     * {@link #failedEntry} by ids, for a model the caller knows only as installed (the evidence view):
     * the unexpired failure of {@code modelId}'s file on {@code accelerator}, written by {@code backend}.
     */
    public static boolean hasFailure(
        @NonNull Context context,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String modelId,
        @NonNull String backend,
        @NonNull String accelerator
    ) {
        return hasFailure(history(context), device, modelId, backend, accelerator,
            System.currentTimeMillis(), appVersionCode(context));
    }

    /** The parsed history as of now, for a caller that asks many questions of it ({@link #hasFailure(JSONObject, TaiDeviceCapabilities, String, String, String, long, long)}); do not mutate it. */
    @NonNull
    public static JSONObject snapshot(@NonNull Context context) {
        return history(context);
    }

    /** {@link #hasFailure(Context, TaiDeviceCapabilities, String, String, String)} against a given history and clock. */
    public static boolean hasFailure(
        @NonNull JSONObject history,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String modelId,
        @NonNull String backend,
        @NonNull String accelerator,
        long nowMs,
        long currentVersionCode
    ) {
        JSONObject entry = history.optJSONObject(key(modelId, device, accelerator));
        if (entry == null || entry.optBoolean("success", false)) return false;
        if (!backend.equals(entry.optString("backend", backend))) return false;
        if (isStaleFileMissingRecord(entry)) return false;
        return !isExpired(entry, nowMs, currentVersionCode);
    }

    /**
     * Whether a failure record no longer counts: older than {@link #FAILURE_TTL_MS}, or written by a
     * different app version (a new build may well have fixed it). Records without an
     * {@code appVersionCode} field, and an unknown running version ({@code 0}), follow the time rule
     * only. The clock is a parameter for tests.
     */
    static boolean isExpired(@NonNull JSONObject entry, long nowMs, long currentVersionCode) {
        long updated = entry.optLong("updatedAtMs", 0L);
        if (updated > 0L && nowMs - updated > FAILURE_TTL_MS) return true;
        long recorded = entry.optLong("appVersionCode", 0L);
        return recorded > 0L && currentVersionCode > 0L && recorded != currentVersionCode;
    }

    /** The running app's version code, or {@code 0} when the package manager cannot say. */
    static long appVersionCode(@Nullable Context context) {
        if (context == null) return 0L;
        try {
            return PackageInfoCompat.getLongVersionCode(context.getApplicationContext().getPackageManager()
                .getPackageInfo(context.getPackageName(), 0));
        } catch (Exception e) {
            return 0L;
        }
    }

    // ---- Measured loads ---------------------------------------------------------------------------
    //
    // One record per (model file, device, app version, backend, accelerator, modality), holding a
    // ring of the last RING_SIZE samples. The window is part of each sample, not of the key, so a
    // 2048-token request can use what a 4096-token load measured (a larger window is an upper
    // bound) and a 6k request can be carried up from what 4k cost. The worst-case-forever ratchet
    // is gone: samples expire after SAMPLE_TTL_MS and with the app version, as failures do.

    /** Samples kept per key; the oldest leave first. */
    static final int RING_SIZE = 8;
    /** How long a measured sample counts. A sample is a snapshot of one build on one day. */
    static final long SAMPLE_TTL_MS = 30L * 24L * 60L * 60L * 1000L;
    /** The drop from just before native init to its end. */
    public static final String PHASE_LOAD = "load";
    /** The drop from just before native init to the first token of the first request after the load. */
    public static final String PHASE_FIRST_PREFILL = "first_prefill";
    static final String MODALITY_TEXT = "text";
    static final String MODALITY_VISION = "vision";
    static final String MODALITY_AUDIO = "audio";
    private static final String MEASURED_PREFIX = "load2|";
    /** Written before the window moved into the samples; ignored on read and dropped on the next write. */
    private static final String LEGACY_MEASURED_PREFIX = "load|";

    /** One measured drop: what the phone lost, at which window, for how long a prompt, and when. */
    static final class Sample {
        final int window;
        final long dropBytes;
        final int promptTokens;
        @NonNull final String phase;
        final long timestampMs;

        Sample(int window, long dropBytes, int promptTokens, @NonNull String phase, long timestampMs) {
            this.window = window;
            this.dropBytes = dropBytes;
            this.promptTokens = promptTokens;
            this.phase = phase;
            this.timestampMs = timestampMs;
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            return new JSONObject().put("w", window).put("d", dropBytes).put("p", promptTokens)
                .put("ph", phase).put("t", timestampMs);
        }
    }

    /**
     * Keeps one measured MemAvailable drop of this model on this device, backend and accelerator in
     * its ring, with the window it was taken at. Non-positive drops are not recorded; callers skip
     * cancelled and failed loads. Recorded as the {@link #PHASE_LOAD} phase.
     */
    public static void recordMeasuredLoad(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator,
        int contextWindow,
        long measuredBytes
    ) {
        recordMeasuredLoad(context, model, device, backend, accelerator, contextWindow, measuredBytes, PHASE_LOAD, 0);
    }

    /**
     * {@link #recordMeasuredLoad(Context, TaiModelSpec, TaiDeviceCapabilities, String, String, int, long)}
     * with the phase ({@link #PHASE_LOAD} or {@link #PHASE_FIRST_PREFILL}) and the prompt token
     * count of the request a first-prefill drop was taken over, image tokens included.
     */
    public static void recordMeasuredLoad(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator,
        int contextWindow,
        long measuredBytes,
        @NonNull String phase,
        int promptTokens
    ) {
        if (measuredBytes <= 0L) return;
        final long version = appVersionCode(context);
        final String key = loadKey(model, TaiResidency.fileBytes(model), device, version, backend, accelerator);
        withLock(context, history -> {
            try {
                long now = System.currentTimeMillis();
                pruneMeasured(history, now, version);
                JSONObject entry = history.optJSONObject(key);
                if (entry == null) entry = new JSONObject();
                entry.put("modelId", model.id);
                entry.put("device", deviceKey(device));
                entry.put("backend", backend);
                entry.put("accelerator", normalizeAccelerator(accelerator));
                entry.put("modality", modalityOf(model.id));
                appendSample(entry, new Sample(contextWindow, measuredBytes, Math.max(0, promptTokens), phase, now), now, version);
                history.put(key, entry);
                return true;
            } catch (JSONException ignored) {
                return false;
            }
        });
    }

    /**
     * The measured drop to plan a load of {@code contextWindow} tokens on, or {@code 0} when nothing
     * usable was measured; see {@link #lookup}. No seed slope is known here, so a window above every
     * sample is planned at the largest sample; the budget's history hook passes its own.
     */
    public static long measuredLoadBytes(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator,
        int contextWindow
    ) {
        return measuredLoadBytes(context, model, device, backend, accelerator, contextWindow, 0L, 0L);
    }

    /**
     * The measured drop for a load of {@code contextWindow} tokens: this key's samples through
     * {@link #lookup} with {@code seedSlopeBytes} for what lies above them; when a vision or audio
     * key has none, the text key's plus {@code encoderDeltaBytes}, never the reverse.
     */
    public static long measuredLoadBytes(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator,
        int contextWindow,
        long seedSlopeBytes,
        long encoderDeltaBytes
    ) {
        long version = appVersionCode(context);
        long fileBytes = TaiResidency.fileBytes(model);
        JSONObject history = history(context);
        long now = System.currentTimeMillis();
        String key = loadKey(model, fileBytes, device, version, backend, accelerator);
        return lookupWithVariantFallback(entrySamples(history.optJSONObject(key)),
            modalityOf(model.id), () -> entrySamples(history.optJSONObject(
                loadKey(TaiModelVariants.baseModelId(model.id), MODALITY_TEXT, fileBytes, device, version, backend, accelerator))),
            contextWindow, seedSlopeBytes, encoderDeltaBytes, now);
    }

    /** Supplies the samples of the text key when a vision or audio key has none. */
    interface TextSamples {
        @NonNull List<Sample> get();
    }

    /**
     * {@link #lookup} on the variant's own samples; with none and a non-text modality, the text
     * samples plus {@code encoderDeltaBytes}. The delta is added to the text figure, never the other
     * way round: a text drop read back for a vision load would be missing the encoder.
     */
    static long lookupWithVariantFallback(
        @NonNull List<Sample> own,
        @NonNull String modality,
        @NonNull TextSamples text,
        int window,
        long seedSlopeBytes,
        long encoderDeltaBytes,
        long nowMs
    ) {
        long measured = lookup(own, window, seedSlopeBytes, nowMs);
        if (measured > 0L || MODALITY_TEXT.equals(modality)) return measured;
        long base = lookup(text.get(), window, seedSlopeBytes, nowMs);
        return base > 0L ? base + Math.max(0L, encoderDeltaBytes) : 0L;
    }

    /**
     * The drop to plan on for a load of {@code window} tokens, from the live samples (younger than
     * {@link #SAMPLE_TTL_MS}); {@code 0} means none, and the caller uses its seed. Only
     * {@link #PHASE_FIRST_PREFILL} samples count when the key has any, since the first request is
     * where a load's real cost lands; otherwise the {@link #PHASE_LOAD} ones. In order:
     * <ol>
     *   <li>the largest sample at the smallest measured window at or above {@code window}: a larger
     *       window is an upper bound for a smaller one;</li>
     *   <li>with two or more windows, all below, a line fitted through each window's largest sample,
     *       evaluated at {@code window} (never below the largest sample);</li>
     *   <li>with one window, below: that sample plus {@code seedSlopeBytes} for each extra token;</li>
     * </ol>
     */
    static long lookup(@NonNull List<Sample> samples, int window, long seedSlopeBytes, long nowMs) {
        boolean prefill = false;
        for (Sample sample : samples) {
            if (nowMs - sample.timestampMs <= SAMPLE_TTL_MS && PHASE_FIRST_PREFILL.equals(sample.phase)) prefill = true;
        }
        String wanted = prefill ? PHASE_FIRST_PREFILL : PHASE_LOAD;
        TreeMap<Integer, Long> maxima = new TreeMap<>();
        for (Sample sample : samples) {
            if (nowMs - sample.timestampMs > SAMPLE_TTL_MS || !wanted.equals(sample.phase) || sample.dropBytes <= 0L) continue;
            Long best = maxima.get(sample.window);
            if (best == null || sample.dropBytes > best) maxima.put(sample.window, sample.dropBytes);
        }
        if (maxima.isEmpty()) return 0L;
        int w = Math.max(0, window);
        Integer above = maxima.ceilingKey(w);
        if (above != null) return maxima.get(above);
        Map.Entry<Integer, Long> top = maxima.lastEntry();
        long beyond = (long) (w - top.getKey());
        if (maxima.size() >= 2) {
            double n = maxima.size();
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            for (Map.Entry<Integer, Long> e : maxima.entrySet()) {
                double x = e.getKey();
                double y = e.getValue();
                sx += x;
                sy += y;
                sxx += x * x;
                sxy += x * y;
            }
            double slope = (n * sxy - sx * sy) / (n * sxx - sx * sx);
            double fixed = (sy - slope * sx) / n;
            if (slope > 0.0) return Math.max(top.getValue(), Math.round(fixed + slope * w));
        }
        return top.getValue() + Math.max(0L, seedSlopeBytes) * beyond;
    }

    /** The samples a record holds, oldest first; unreadable or missing ones are skipped, never thrown. */
    @NonNull
    static List<Sample> entrySamples(@Nullable JSONObject entry) {
        List<Sample> samples = new ArrayList<>();
        JSONArray array = entry == null ? null : entry.optJSONArray("samples");
        if (array == null) return samples;
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            long drop = json.optLong("d", 0L);
            long at = json.optLong("t", 0L);
            if (drop <= 0L || at <= 0L) continue;
            samples.add(new Sample(json.optInt("w", 0), drop, json.optInt("p", 0), json.optString("ph", PHASE_LOAD), at));
        }
        return samples;
    }

    /**
     * Adds a sample to a record's ring: samples older than {@link #SAMPLE_TTL_MS}, and every sample
     * when the record was written by another app version, leave first; then only the last
     * {@link #RING_SIZE} stay.
     */
    static void appendSample(@NonNull JSONObject entry, @NonNull Sample sample, long nowMs, long versionCode)
        throws JSONException {
        List<Sample> kept = new ArrayList<>();
        long recorded = entry.optLong("appVersionCode", 0L);
        boolean otherVersion = recorded > 0L && versionCode > 0L && recorded != versionCode;
        if (!otherVersion) {
            for (Sample old : entrySamples(entry)) {
                if (nowMs - old.timestampMs <= SAMPLE_TTL_MS) kept.add(old);
            }
        }
        kept.add(sample);
        while (kept.size() > RING_SIZE) kept.remove(0);
        JSONArray array = new JSONArray();
        for (Sample s : kept) array.put(s.toJson());
        entry.put("samples", array);
        entry.put("appVersionCode", versionCode);
        entry.put("updatedAtMs", nowMs);
    }

    /**
     * Drops what can no longer be read: records in the old format (one bucketed worst case, no
     * window per sample), records of another app version, and records whose samples have all
     * expired. Returns how many were removed.
     */
    static int pruneMeasured(@NonNull JSONObject history, long nowMs, long versionCode) {
        List<String> doomed = new ArrayList<>();
        Iterator<String> keys = history.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(LEGACY_MEASURED_PREFIX)) {
                doomed.add(key);
            } else if (key.startsWith(MEASURED_PREFIX)) {
                JSONObject entry = history.optJSONObject(key);
                long recorded = entry == null ? 0L : entry.optLong("appVersionCode", 0L);
                boolean otherVersion = recorded > 0L && versionCode > 0L && recorded != versionCode;
                boolean live = false;
                for (Sample sample : entrySamples(entry)) {
                    if (nowMs - sample.timestampMs <= SAMPLE_TTL_MS) live = true;
                }
                if (otherVersion || !live) doomed.add(key);
            }
        }
        for (String key : doomed) history.remove(key);
        return doomed.size();
    }

    /** {@code text}, {@code vision} or {@code audio}, from the variant suffix of the model id. */
    @NonNull
    static String modalityOf(@NonNull String modelId) {
        if (modelId.endsWith(TaiModelVariants.SUFFIX_VISION)) return MODALITY_VISION;
        if (modelId.endsWith(TaiModelVariants.SUFFIX_AUDIO)) return MODALITY_AUDIO;
        return MODALITY_TEXT;
    }

    @NonNull
    private static String loadKey(@NonNull TaiModelSpec model, long fileBytes, @NonNull TaiDeviceCapabilities device,
                                  long versionCode, @NonNull String backend, @NonNull String accelerator) {
        return loadKey(TaiModelVariants.baseModelId(model.id), modalityOf(model.id), fileBytes, device, versionCode,
            backend, accelerator);
    }

    /**
     * {@code model base id + file size | device (its Android version included) | app version |
     * backend | accelerator | modality}. The file size tells a re-exported model of the same id
     * from the one measured; the app version is the runtime's, so a new LiteRT-LM or MNN starts
     * clean.
     */
    @NonNull
    static String loadKey(@NonNull String baseModelId, @NonNull String modality, long fileBytes,
                          @NonNull TaiDeviceCapabilities device, long versionCode, @NonNull String backend,
                          @NonNull String accelerator) {
        return MEASURED_PREFIX + baseModelId + "|" + fileBytes + "|" + deviceKey(device) + "|" + versionCode + "|"
            + backend + "|" + normalizeAccelerator(accelerator) + "|" + modality;
    }

    /** Wipes every entry (the {@code tai runtime --clear-history} path); returns how many were dropped. */
    public static int clear(@NonNull Context context) {
        final int[] count = {0};
        withLock(context, history -> {
            count[0] = history.length();
            List<String> all = new ArrayList<>();
            Iterator<String> keys = history.keys();
            while (keys.hasNext()) all.add(keys.next());
            for (String key : all) history.remove(key);
            return true;
        });
        return count[0];
    }

    /**
     * Drops every entry recorded for this model (its load, failure, measured-load, audio-input and
     * system-role records, variants included), for when the model is deleted. Returns the number dropped.
     */
    public static int removeModel(@NonNull Context context, @NonNull String modelId) {
        final int[] removed = {0};
        withLock(context, history -> {
            removed[0] = removeModel(history, modelId);
            return removed[0] > 0;
        });
        return removed[0];
    }

    static int removeModel(@NonNull JSONObject history, @NonNull String modelId) {
        String base = TaiModelVariants.baseModelId(modelId);
        List<String> doomed = new ArrayList<>();
        Iterator<String> keys = history.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entry = history.optJSONObject(key);
            String id = entry == null ? "" : entry.optString("modelId", "");
            if (id.isEmpty() || !(id.equals(modelId) || TaiModelVariants.baseModelId(id).equals(base))) continue;
            doomed.add(key);
        }
        for (String key : doomed) history.remove(key);
        return doomed.size();
    }

    /** Drops the oldest entries (by {@code updatedAtMs}) until at most {@code max} remain; returns the count dropped. */
    static int cap(@NonNull JSONObject history, int max) {
        int excess = history.length() - max;
        if (excess <= 0) return 0;
        List<String> keys = new ArrayList<>();
        Iterator<String> it = history.keys();
        while (it.hasNext()) keys.add(it.next());
        keys.sort((a, b) -> Long.compare(updatedAt(history, a), updatedAt(history, b)));
        for (int i = 0; i < excess; i++) history.remove(keys.get(i));
        return excess;
    }

    private static long updatedAt(@NonNull JSONObject history, @NonNull String key) {
        JSONObject entry = history.optJSONObject(key);
        return entry == null ? 0L : entry.optLong("updatedAtMs", 0L);
    }

    @NonNull
    public static JSONObject summary(@NonNull Context context) throws JSONException {
        JSONObject data = new JSONObject();
        JSONObject history = history(context);
        data.put("entries", history);
        data.put("count", history.length());
        return data;
    }

    private static void record(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator,
        boolean success,
        @NonNull String reason
    ) {
        final long version = success ? 0L : appVersionCode(context);
        withLock(context, history -> {
            try {
                JSONObject entry = new JSONObject();
                entry.put("modelId", model.id);
                entry.put("device", deviceKey(device));
                entry.put("backend", backend);
                entry.put("accelerator", normalizeAccelerator(accelerator));
                entry.put("success", success);
                entry.put("reason", reason);
                entry.put("updatedAtMs", System.currentTimeMillis());
                if (!success && version > 0L) entry.put("appVersionCode", version);
                history.put(key(model, device, accelerator), entry);
                return true;
            } catch (JSONException ignored) {
                return false;
            }
        });
    }

    @Nullable
    private static JSONObject entry(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String accelerator
    ) {
        return history(context).optJSONObject(key(model, device, accelerator));
    }

    /** A read-modify-write over the history; return {@code true} to have the changed history written back. */
    private interface Mutation {
        boolean apply(@NonNull JSONObject history);
    }

    /** Serialises threads of this process, since a {@link FileLock} is per JVM and overlapping requests throw. */
    private static final Object PROCESS_LOCK = new Object();

    @NonNull
    private static File directory(@NonNull Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "tai");
    }

    /**
     * Runs {@code mutation} on the freshly read history while holding the process lock and the
     * exclusive file lock, and writes the result back (capped, to a temporary file renamed over the
     * target) when it returns {@code true}. The lock spans the read and the write, so another
     * process cannot slip a change in between and have it lost. Returns the history as it stands.
     */
    @NonNull
    private static JSONObject withLock(@NonNull Context context, @NonNull Mutation mutation) {
        File dir = directory(context);
        synchronized (PROCESS_LOCK) {
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return new JSONObject();
            try (RandomAccessFile lockFile = new RandomAccessFile(new File(dir, LOCK_NAME), "rw");
                 FileChannel channel = lockFile.getChannel();
                 FileLock ignored = channel.lock()) {
                File file = new File(dir, FILE_NAME);
                JSONObject history = readFile(context, file, true);
                if (mutation.apply(history)) {
                    cap(history, MAX_ENTRIES);
                    write(file, history);
                }
                return history;
            } catch (IOException | RuntimeException e) {
                // The history is an optimisation; a failed write loses a record, never a load.
                return new JSONObject();
            }
        }
    }

    private static void write(@NonNull File file, @NonNull JSONObject history) throws IOException {
        File temp = new File(file.getParentFile(), FILE_NAME + ".tmp");
        Files.write(temp.toPath(), history.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Reads the history from disk every time, with no cache, because the other process may have
     * changed the file since the last look. Writers replace the file by rename, so a read without
     * the file lock always sees a whole file. The cases that must change something (a legacy value
     * to migrate, an unparseable file to set aside) go through the lock.
     */
    @NonNull
    private static JSONObject history(@NonNull Context context) {
        File file = new File(directory(context), FILE_NAME);
        if (file.isFile()) {
            JSONObject parsed = readFile(context, file, false);
            if (parsed != null) return parsed;
        } else if (!hasLegacyHistory(context)) {
            return new JSONObject();
        }
        return withLock(context, history -> false);
    }

    private static boolean hasLegacyHistory(@NonNull Context context) {
        try {
            return prefs(context).contains(KEY_HISTORY);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Parses the file. With {@code locked} set (the caller holds the file lock) a missing file is
     * filled from the legacy preferences value, which is then removed, and an unparseable file is
     * renamed to {@code .bad} and read as empty. Without it, an unparseable file yields {@code null}
     * so the caller can retry under the lock, and a missing file reads as empty.
     */
    @Nullable
    private static JSONObject readFile(@NonNull Context context, @NonNull File file, boolean locked) {
        if (!file.isFile()) return locked ? migrateLegacy(context, file) : new JSONObject();
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (text.trim().isEmpty()) return new JSONObject();
            return new JSONObject(text);
        } catch (JSONException e) {
            if (!locked) return null;
            try {
                Files.move(file.toPath(), new File(file.getParentFile(), FILE_NAME + ".bad").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            return new JSONObject();
        } catch (IOException e) {
            return new JSONObject();
        }
    }

    @NonNull
    private static JSONObject migrateLegacy(@NonNull Context context, @NonNull File file) {
        JSONObject history = new JSONObject();
        if (!hasLegacyHistory(context)) return history;
        try {
            SharedPreferences prefs = prefs(context);
            String value = prefs.getString(KEY_HISTORY, "");
            if (value != null && !value.trim().isEmpty()) {
                try {
                    history = new JSONObject(value);
                } catch (JSONException ignored) {
                    history = new JSONObject();
                }
            }
            write(file, history);
            prefs.edit().remove(KEY_HISTORY).apply();
        } catch (IOException | RuntimeException ignored) {
        }
        return history;
    }

    @NonNull
    private static String key(@NonNull TaiModelSpec model, @NonNull TaiDeviceCapabilities device, @NonNull String accelerator) {
        return key(model.id, device, accelerator);
    }

    @NonNull
    private static String key(@NonNull String modelId, @NonNull TaiDeviceCapabilities device, @NonNull String accelerator) {
        // Key by the underlying model, not the per-modality virtual variant (…-vision/…-audio):
        // GPU load stability is a property of the model file + device and is shared across modalities.
        // Otherwise a vision request can never auto-load because the variant id has no GPU history,
        // even after the base model has loaded successfully on GPU.
        return TaiModelVariants.baseModelId(modelId) + "|" + deviceKey(device) + "|" + normalizeAccelerator(accelerator);
    }

    @NonNull
    private static String deviceKey(@NonNull TaiDeviceCapabilities device) {
        return (device.manufacturer + "|" + device.model + "|" + device.socModel + "|" + device.sdkInt + "|" + device.supportedAbis)
            .toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static String normalizeAccelerator(@Nullable String accelerator) {
        if (accelerator == null || accelerator.trim().isEmpty()) return "auto";
        String value = accelerator.trim().toLowerCase(Locale.ROOT);
        if ("opencl".equals(value)) return "gpu";
        return value;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
