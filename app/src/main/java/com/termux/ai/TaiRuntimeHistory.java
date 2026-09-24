package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public final class TaiRuntimeHistory {
    private static final String KEY_HISTORY = "tai_runtime_history_json";

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
            prefs(context).edit().putString(KEY_HISTORY, history.toString()).apply();
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

    @Nullable
    public static JSONObject failedEntry(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull String accelerator
    ) {
        JSONObject entry = entry(context, model, device, accelerator);
        if (entry == null || entry.optBoolean("success", false)) return null;
        return entry;
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
            prefs(context).edit().putString(KEY_HISTORY, history.toString()).apply();
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
            history.put(key(model, device, accelerator), entry);
            prefs(context).edit().putString(KEY_HISTORY, history.toString()).apply();
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
