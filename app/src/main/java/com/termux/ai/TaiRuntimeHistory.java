package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class TaiRuntimeHistory {
    private static final String KEY_HISTORY = "tai_runtime_history_json";
    /** Entries are never otherwise pruned; past this many, the oldest {@code updatedAtMs} go first. */
    static final int MAX_ENTRIES = 200;

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
        try {
            JSONObject history = history(context);
            JSONObject entry = new JSONObject();
            entry.put("modelId", modelId);
            entry.put("device", deviceKey(device));
            entry.put("feature", "audio_input");
            entry.put("success", success);
            entry.put("updatedAtMs", System.currentTimeMillis());
            history.put("audio_input|" + modelId + "|" + deviceKey(device), entry);
            save(context, history);
        } catch (JSONException ignored) {
        }
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
        try {
            JSONObject history = history(context);
            JSONObject entry = new JSONObject();
            entry.put("modelId", modelId);
            entry.put("feature", "system_role");
            entry.put("success", false);
            entry.put("updatedAtMs", System.currentTimeMillis());
            history.put(systemRoleKey(modelId), entry);
            save(context, history);
        } catch (JSONException ignored) {
        }
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
     * automatic order put the CPU first, so the living-wallpaper director ran a minute per photo on
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

    /**
     * Keeps the largest MemAvailable drop any load of this model has measured on this device,
     * backend and accelerator at this window bucket — the worst case is what the budget plans
     * against, since identical GPU loads spread over ±0.9 GB on pong. Non-positive drops are not
     * recorded; callers skip cancelled and failed loads.
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
        if (measuredBytes <= 0L) return;
        try {
            JSONObject history = history(context);
            String key = measuredKey(model, device, backend, accelerator, contextWindow);
            JSONObject entry = history.optJSONObject(key);
            long worst = entry == null ? 0L : entry.optLong("bytes", 0L);
            int samples = entry == null ? 0 : entry.optInt("samples", 0);
            if (entry == null) entry = new JSONObject();
            entry.put("modelId", model.id);
            entry.put("device", deviceKey(device));
            entry.put("backend", backend);
            entry.put("accelerator", normalizeAccelerator(accelerator));
            entry.put("contextBucket", contextBucket(contextWindow));
            entry.put("bytes", Math.max(worst, measuredBytes));
            entry.put("lastBytes", measuredBytes);
            entry.put("samples", samples + 1);
            entry.put("updatedAtMs", System.currentTimeMillis());
            history.put(key, entry);
            save(context, history);
        } catch (JSONException ignored) {
        }
    }

    /** The worst measured drop for this key, or {@code 0} when no load of it has been measured. */
    public static long measuredLoadBytes(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String backend,
        @NonNull String accelerator,
        int contextWindow
    ) {
        JSONObject entry = history(context).optJSONObject(measuredKey(model, device, backend, accelerator, contextWindow));
        return entry == null ? 0L : Math.max(0L, entry.optLong("bytes", 0L));
    }

    /**
     * Windows are bucketed to the power of two at or above them, so 4000 and 4096 share a record
     * and a 6k window plans against what 8k cost. {@code 0} (no window) stays {@code 0}.
     */
    static int contextBucket(int contextWindow) {
        if (contextWindow <= 0) return 0;
        int bucket = 1;
        while (bucket < contextWindow && bucket < (1 << 30)) bucket <<= 1;
        return bucket;
    }

    @NonNull
    private static String measuredKey(@NonNull TaiModelSpec model, @NonNull TaiDeviceCapabilities device,
                                      @NonNull String backend, @NonNull String accelerator, int contextWindow) {
        return "load|" + TaiModelVariants.baseModelId(model.id) + "|" + deviceKey(device) + "|" + backend + "|"
            + normalizeAccelerator(accelerator) + "|" + contextBucket(contextWindow);
    }

    /** Wipes every entry (the {@code tai runtime --clear-history} path); returns how many were dropped. */
    public static int clear(@NonNull Context context) {
        int count = history(context).length();
        prefs(context).edit().remove(KEY_HISTORY).apply();
        return count;
    }

    /**
     * Drops every entry recorded for this model (its load, failure, measured-load, audio-input and
     * system-role records, variants included), for when the model is deleted. Returns the number dropped.
     */
    public static int removeModel(@NonNull Context context, @NonNull String modelId) {
        JSONObject history = history(context);
        int removed = removeModel(history, modelId);
        if (removed > 0) save(context, history);
        return removed;
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

    private static void save(@NonNull Context context, @NonNull JSONObject history) {
        cap(history, MAX_ENTRIES);
        prefs(context).edit().putString(KEY_HISTORY, history.toString()).apply();
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
        try {
            JSONObject history = history(context);
            JSONObject entry = new JSONObject();
            entry.put("modelId", model.id);
            entry.put("device", deviceKey(device));
            entry.put("backend", backend);
            entry.put("accelerator", normalizeAccelerator(accelerator));
            entry.put("success", success);
            entry.put("reason", reason);
            entry.put("updatedAtMs", System.currentTimeMillis());
            if (!success) {
                long version = appVersionCode(context);
                if (version > 0L) entry.put("appVersionCode", version);
            }
            history.put(key(model, device, accelerator), entry);
            save(context, history);
        } catch (JSONException ignored) {
        }
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

    @NonNull
    private static JSONObject history(@NonNull Context context) {
        String value = prefs(context).getString(KEY_HISTORY, "{}");
        if (value == null || value.trim().isEmpty()) value = "{}";
        try {
            return new JSONObject(value);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @NonNull
    private static String key(@NonNull TaiModelSpec model, @NonNull TaiDeviceCapabilities device, @NonNull String accelerator) {
        // Key by the underlying model, not the per-modality virtual variant (…-vision/…-audio):
        // GPU load stability is a property of the model file + device and is shared across modalities.
        // Otherwise a vision request can never auto-load because the variant id has no GPU history,
        // even after the base model has loaded successfully on GPU.
        return TaiModelVariants.baseModelId(model.id) + "|" + deviceKey(device) + "|" + normalizeAccelerator(accelerator);
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
