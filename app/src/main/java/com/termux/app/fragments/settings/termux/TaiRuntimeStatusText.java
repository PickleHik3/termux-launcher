package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiSettings;
import com.termux.launcherctl.LauncherCtlApiServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * What the On-device AI pages say about the runtime, read from {@code TaiManager.runtimeStatus()}.
 * The overview shows the {@link #headline} and the {@link #brief} body; the Runtime page shows the
 * headline and the {@link #full} body. Everything but {@link #full} is pure over the JSON.
 */
final class TaiRuntimeStatusText {

    private TaiRuntimeStatusText() {}

    /** The status card's dot and label. */
    static final class Headline {
        @StringRes final int label;
        final boolean active;

        Headline(@StringRes int label, boolean active) {
            this.label = label;
            this.active = active;
        }
    }

    @Nullable
    private static JSONObject runtime(@Nullable JSONObject status) {
        return status == null ? null : status.optJSONObject("runtime");
    }

    @NonNull
    static Headline headline(@Nullable JSONObject status) {
        JSONObject runtime = runtime(status);
        if (runtime == null) return new Headline(R.string.termux_ai_status_unavailable, false);
        String state = runtime.optString("state", "unloaded");
        if ("loading".equals(state)) return new Headline(R.string.termux_ai_status_loading, true);
        if (runtime.optBoolean("activeGeneration", false))
            return new Headline(R.string.termux_ai_status_generating, true);
        if ("stopping".equals(state)) return new Headline(R.string.termux_ai_status_stopping, true);
        if (runtime.optBoolean("loaded", false)) return new Headline(R.string.termux_ai_status_loaded, true);
        return new Headline(R.string.termux_ai_status_none, false);
    }

    /**
     * The overview's short body: what is loaded and where, when it will unload, the runtime's own
     * message and the last crash. Empty when none of that applies.
     */
    @NonNull
    static String brief(@Nullable JSONObject status) {
        JSONObject runtime = runtime(status);
        if (runtime == null) return "";
        StringBuilder body = new StringBuilder();
        if (runtime.optBoolean("loaded", false)) {
            StringBuilder line = new StringBuilder(nullable(runtime, "loadedModelId", "model"));
            String backend = nullable(runtime, "backend", "");
            if (!backend.isEmpty()) line.append(" · ").append(backend);
            String accelerator = nullable(runtime, "accelerator", "");
            if (accelerator.isEmpty()) accelerator = nullable(runtime, "ranOn", "");
            if (!accelerator.isEmpty()) line.append(" · ").append(accelerator);
            body.append(line).append('\n');
        }
        long keepWarmRemaining = runtime.optLong("keepWarmRemainingMs", 0L);
        long idleRemaining = runtime.optLong("idleUnloadRemainingMs", 0L);
        if (keepWarmRemaining > 0L) {
            body.append("Kept warm for ").append(formatDuration(keepWarmRemaining)).append('\n');
        } else if (idleRemaining > 0L) {
            body.append("Unloads in ").append(formatDuration(idleRemaining)).append('\n');
        }
        String message = runtime.optString("status", "");
        if (!message.isEmpty()) body.append(message).append('\n');
        JSONObject crash = status.optJSONObject("lastRuntimeCrash");
        if (crash != null) {
            body.append(crashLine(crash)).append('\n')
                .append(crash.optString("suggestedFallback", "Try CPU or a smaller model.")).append('\n');
        }
        return body.toString().trim();
    }

    /** Polling continues while something will change on its own: a reply, a loaded model, a countdown. */
    static boolean keepPolling(@Nullable JSONObject status) {
        JSONObject runtime = runtime(status);
        if (runtime == null) return false;
        return runtime.optBoolean("activeGeneration", false)
            || runtime.optBoolean("loaded", false)
            || runtime.optLong("keepWarmRemainingMs", 0L) > 0L
            || runtime.optLong("idleUnloadRemainingMs", 0L) > 0L;
    }

    /** A reply being written, or a load in progress, can be stopped. */
    static boolean stopEnabled(@Nullable JSONObject status) {
        JSONObject runtime = runtime(status);
        if (runtime == null) return false;
        return runtime.optBoolean("activeGeneration", false) || "loading".equals(runtime.optString("state", ""));
    }

    /** A loaded (or loading) model can be unloaded unless a reply or a stop is under way. */
    static boolean unloadEnabled(@Nullable JSONObject status) {
        JSONObject runtime = runtime(status);
        if (runtime == null) return false;
        String state = runtime.optString("state", "unloaded");
        boolean loaded = runtime.optBoolean("loaded", false);
        boolean loading = "loading".equals(state);
        boolean stopping = "stopping".equals(state);
        return (loaded || loading) && !runtime.optBoolean("activeGeneration", false) && !stopping;
    }

    /**
     * The Runtime page's full card body: device, engines, model, backend, timers, warnings, the
     * last crash and the local endpoint. Empty when the status carries no runtime object.
     */
    @NonNull
    static String full(@NonNull Context context, @Nullable JSONObject status) {
        JSONObject runtime = runtime(status);
        if (runtime == null) return "";
        StringBuilder body = new StringBuilder();
        JSONObject device = status.optJSONObject("device");
        if (device != null) {
            StringBuilder deviceLine = new StringBuilder(device.optString("model", "unknown"));
            if (!device.isNull("memoryGiB")) {
                deviceLine.append(" · ").append(String.format(Locale.US, "%.1f GiB", device.optDouble("memoryGiB")));
            }
            appendKv(body, "device", deviceLine.toString());
            appendKv(body, "accel", join(device.optJSONArray("phase1Accelerators")));
        }
        TaiDeviceCapabilities caps = TaiDeviceCapabilities.detect(context);
        boolean liteRtOk = caps.liteRtLmAbiSupported && caps.liteRtLmNativeLibrariesAvailable;
        StringBuilder engine = new StringBuilder("litert-lm ")
            .append(liteRtOk ? "ok" : "unavailable")
            .append(" · mnn-llm ")
            .append(caps.mnnSupported ? "ok" : "unavailable");
        if (!caps.mnnSupported && caps.mnnUnsupportedReason != null) {
            engine.append(" (").append(caps.mnnUnsupportedReason).append(')');
        }
        appendKv(body, "engine", engine.toString());
        appendKv(body, "model", nullable(runtime, "loadedModelId", "none"));
        appendKv(body, "backend", runtime.optString("backend", "none"));
        String fallback = nullable(runtime, "backendFallbackReason", "");
        if (!fallback.isEmpty()) appendKv(body, "fallback", fallback);
        if (runtime.optBoolean("activeGeneration", false)) appendKv(body, "generate", "active");
        String runtimeProcess = status.optString("runtimeProcess", "");
        if (!runtimeProcess.isEmpty()) appendKv(body, "process", runtimeProcess);
        long keepWarmRemaining = runtime.optLong("keepWarmRemainingMs", 0L);
        if (keepWarmRemaining > 0L) appendKv(body, "warm", formatDuration(keepWarmRemaining));
        long idleRemaining = runtime.optLong("idleUnloadRemainingMs", 0L);
        if (idleRemaining > 0L) appendKv(body, "idle", formatDuration(idleRemaining));
        String statusMessage = runtime.optString("status", "");
        if (!statusMessage.isEmpty()) appendKv(body, "status", statusMessage);
        JSONObject profile = status.optJSONObject("modelProfile");
        if (profile != null) {
            StringBuilder compat = new StringBuilder(join(profile.optJSONArray("compatibleAccelerators")));
            if (!profile.isNull("minDeviceMemoryInGb")) {
                compat.append(" · min ").append(profile.optInt("minDeviceMemoryInGb")).append(" GiB");
            }
            appendKv(body, "compat", compat.toString());
        }
        JSONArray warnings = status.optJSONArray("compatibilityWarnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                String warning = warnings.optString(i, "");
                if (!warning.isEmpty()) appendKv(body, "warning", warning);
            }
        }
        JSONObject crash = status.optJSONObject("lastRuntimeCrash");
        if (crash != null) {
            appendKv(body, "last", crashLine(crash));
            appendKv(body, "fallback", crash.optString("suggestedFallback", "Try CPU or a smaller model."));
        }
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            String baseUrl = endpoint.optString("openAiBaseUrl", "");
            String token = endpoint.optString("token", "");
            if (!baseUrl.isEmpty()) appendKv(body, "endpoint", baseUrl);
            if (!token.isEmpty()) appendKv(body, "token", TaiSettings.redactToken(token));
        } catch (JSONException ignored) {
        }
        return body.toString().trim();
    }

    @NonNull
    private static String crashLine(@NonNull JSONObject crash) {
        String model = crash.optString("modelId", "");
        String accelerator = crash.optString("accelerator", "");
        return "AI runtime crashed while loading " + (model.isEmpty() ? "a model" : model)
            + (accelerator.isEmpty() ? "" : " on " + accelerator);
    }

    static void appendKv(@NonNull StringBuilder builder, @NonNull String key, @NonNull String value) {
        builder.append(String.format(Locale.US, "%-9s", key)).append(value).append('\n');
    }

    @NonNull
    static String join(@Nullable JSONArray values) {
        if (values == null || values.length() == 0) return "none";
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            if (i > 0) joined.append(", ");
            joined.append(values.optString(i, ""));
        }
        return joined.toString();
    }

    @NonNull
    static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes > 0L) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    @NonNull
    static String nullable(@Nullable JSONObject object, @NonNull String key, @NonNull String fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) return fallback;
        return object.optString(key, fallback);
    }
}
