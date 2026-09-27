package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class TaiCliFormatter {
    private TaiCliFormatter() {
    }

    @NonNull
    public static String format(@NonNull String command, @NonNull JSONObject data) {
        try {
            if ("preflight".equals(command)) return formatPreflight(data);
            if (!data.optBoolean("ok", true) || data.has("error")) {
                return formatError(data);
            }
            switch (command) {
                case "status":
                    return formatStatus(data);
                case "runtime":
                    return formatRuntime(data);
                case "models":
                    return formatModels(data);
                case "downloads":
                    return formatDownloads(data);
                case "import":
                    return formatImport(data);
                case "download":
                    return formatDownloadStarted(data);
                case "preflight":
                    return formatPreflight(data);
                case "delete":
                    return formatDelete(data);
                case "load":
                    return formatLoad(data);
                case "unload":
                    return formatUnload(data);
                case "keep-warm":
                    return formatKeepWarm(data);
                case "cancel":
                    return formatCancel(data);
                case "launcher-status":
                    return formatLauncherStatus(data);
                case "speak":
                    return formatSpeak(data);
                case "speak-stop":
                    return data.optBoolean("stopped", false) ? "Stopped.\n" : "Nothing was speaking.\n";
                default:
                    return formatGeneric(data);
            }
        } catch (Exception e) {
            return data.toString() + "\n";
        }
    }

    /** "Spoke 3 sentences (4.2 s) as Jasper; first sound after 0.9 s." or "Stopped after 1 sentence." */
    @NonNull
    private static String formatSpeak(@NonNull JSONObject data) {
        int sentences = data.optInt("sentences", 0);
        String counted = sentences + (sentences == 1 ? " sentence" : " sentences");
        if (data.optBoolean("stopped", false) || data.optBoolean("cancelled", false)) {
            return "Stopped after " + counted + ".\n";
        }
        StringBuilder out = new StringBuilder();
        out.append("Spoke ").append(counted)
            .append(String.format(Locale.US, " (%.1f s)", data.optDouble("audioSeconds", 0.0)));
        String voice = data.optString("voice", "");
        if (!voice.isEmpty()) out.append(" as ").append(voice);
        long firstSound = data.optLong("firstSoundMs", -1L);
        if (firstSound >= 0L) out.append(String.format(Locale.US, "; first sound after %.1f s", firstSound / 1000.0));
        return out.append(".\n").toString();
    }

    @NonNull
    private static String formatError(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI error");
        String message = clean(data.optString("message", ""));
        if (!message.isEmpty()) out.append(": ").append(message);
        out.append('\n');
        appendValue(out, "Code", clean(data.optString("error", "")));
        appendValue(out, "Runtime", clean(data.optString("runtime", "")));
        appendValue(out, "Provider page", clean(data.optString("providerPageUrl", "")));
        appendValue(out, "Download URL", clean(data.optString("downloadUrl", "")));
        return out.toString();
    }

    @NonNull
    private static String formatStatus(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI status\n");
        JSONObject runtime = data.optJSONObject("runtime");
        if (runtime != null) {
            appendValue(out, "Runtime", runtime.optString("runtimeName", ""));
            appendValue(out, "Loaded model", nullable(runtime, "loadedModelId", "none"));
            appendValue(out, "Lifecycle", runtime.optString("state", ""));
            appendValue(out, "Backend", runtime.optString("backend", ""));
            appendValue(out, "Fallback", nullable(runtime, "backendFallbackReason", ""));
            appendValue(out, "Active generation", runtime.optBoolean("activeGeneration", false) ? "yes" : "no");
            appendValue(out, "Keep warm", runtime.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("keepWarmRemainingMs", 0L)) : "off");
            appendValue(out, "Idle unload", runtime.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("idleUnloadRemainingMs", 0L)) : "off");
            appendValue(out, "State", runtime.optString("status", ""));
        }
        appendCompatibility(out, data);

        JSONObject settings = data.optJSONObject("settings");
        if (settings != null) {
            JSONObject roles = settings.optJSONObject("roles");
            if (roles != null) {
                out.append("\nRoles\n");
                appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
            }
            out.append("\nSettings\n");
            appendValue(out, "Idle unload", settings.optInt("idleUnloadMinutes", 0) + " min");
            appendValue(out, "Hugging Face token", settings.optBoolean("huggingFaceTokenConfigured", false) ? "configured" : "not configured");
            JSONObject options = settings.optJSONObject("runtimeOptions");
            if (options != null) {
                appendValue(out, "Accelerator", nullable(options, "accelerator", "Auto / model profile"));
                appendValue(out, "Max tokens", nullable(options, "maxTokens", "Auto / Gallery default"));
                appendValue(out, "Temperature", nullable(options, "temperature", "Auto / Gallery default"));
            }
        }

        JSONArray limitations = data.optJSONArray("limitations");
        if (limitations != null && limitations.length() > 0) {
            out.append("\nLimitations\n");
            appendBullets(out, limitations);
        }
        return out.toString();
    }

    @NonNull
    private static String formatRuntime(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI runtime\n");
        JSONObject runtime = data.optJSONObject("runtime");
        if (runtime != null) appendRuntimeState(out, runtime);
        appendCompatibility(out, data);
        JSONObject settings = data.optJSONObject("settings");
        if (settings != null) {
            JSONObject options = settings.optJSONObject("runtimeOptions");
            if (options != null) {
                out.append("\nDefaults\n");
                appendValue(out, "Accelerator", nullable(options, "accelerator", "Auto / model profile"));
                appendValue(out, "Max tokens", nullable(options, "maxTokens", "Auto / Gallery default"));
                appendValue(out, "TopK", nullable(options, "topK", "Auto / Gallery default"));
                appendValue(out, "TopP", nullable(options, "topP", "Auto / Gallery default"));
                appendValue(out, "Temperature", nullable(options, "temperature", "Auto / Gallery default"));
                appendValue(out, "Speculative decoding", nullable(options, "speculativeDecodingEnabled", "Auto / Gallery default"));
            }
        }
        return out.toString();
    }

    @NonNull
    private static String formatModels(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI models\n");
        appendValue(out, "Storage", data.optString("storageDirectory", ""));
        appendValue(out, "Bundled model files", data.optBoolean("bundledModelFiles", false) ? "yes" : "no");

        JSONObject roles = data.optJSONObject("roles");
        if (roles != null) {
            out.append("\nRoles\n");
            appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
        }

        JSONArray models = data.optJSONArray("models");
        out.append("\nAvailable models\n");
        if (models == null || models.length() == 0) {
            out.append("  none\n");
        } else {
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) continue;
                out.append("  ").append(model.optString("id", "unknown"));
                if (!model.isNull("localPath")) {
                    out.append(" [downloaded/imported]");
                } else {
                    out.append(" [catalog]");
                }
                out.append('\n');
                appendValue(out, "    Role", model.optString("roleHint", ""));
                appendValue(out, "    Source", model.optString("source", ""));
                appendValue(out, "    Backend", model.optString("backend", "") + " / " + model.optString("format", ""));
                appendValue(out, "    Quantization", nullable(model, "quantization", "not specified"));
                appendValue(out, "    Recommended memory", model.optInt("recommendedRamGb", 0) > 0
                    ? model.optInt("recommendedRamGb") + " GiB" : "not specified");
                appendValue(out, "    Size", model.optLong("sizeBytes", 0L) > 0 ? formatBytes(model.optLong("sizeBytes")) : "not downloaded");
                appendValue(out, "    Capabilities", join(model.optJSONArray("capabilities")));
                JSONObject profile = model.optJSONObject("runtimeProfile");
                if (profile != null) {
                    appendValue(out, "    Accelerators", join(profile.optJSONArray("compatibleAccelerators")));
                    appendValue(out, "    Minimum memory", profile.isNull("minDeviceMemoryInGb")
                        ? "not specified" : profile.optInt("minDeviceMemoryInGb") + " GiB");
                    appendValue(out, "    Defaults", profile.optInt("defaultMaxTokens") + " tokens, temperature "
                        + profile.optDouble("defaultTemperature"));
                }
            }
        }

        JSONArray downloads = data.optJSONArray("downloads");
        if (downloads != null && downloads.length() > 0) {
            out.append("\nDownloads\n");
            appendDownloads(out, downloads);
        }
        return out.toString();
    }

    @NonNull
    private static String formatDownloads(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI downloads\n");
        JSONArray downloads = data.optJSONArray("downloads");
        if (downloads == null || downloads.length() == 0) {
            out.append("  none\n");
        } else {
            appendDownloads(out, downloads);
        }
        return out.toString();
    }

    @NonNull
    private static String formatImport(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Model imported\n");
        JSONObject model = data.optJSONObject("model");
        if (model != null) {
            appendValue(out, "Model", model.optString("id", ""));
            appendValue(out, "Path", nullable(model, "localPath", ""));
            appendValue(out, "Capabilities", join(model.optJSONArray("capabilities")));
        }
        appendValue(out, "Note", data.optString("message", ""));
        return out.toString();
    }

    @NonNull
    private static String formatDownloadStarted(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("started", false) ? "Download started\n" : "Download request\n");
        JSONObject transfer = data.optJSONObject("transfer");
        if (transfer != null) appendTransfer(out, transfer);
        appendValue(out, "Note", data.optString("message", ""));
        return out.toString();
    }

    @NonNull
    private static String formatDelete(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("deleted", false) ? "Model deleted\n" : "No matching model was deleted\n");
        appendValue(out, "Model", data.optString("modelId", ""));
        return out.toString();
    }

    @NonNull
    private static String formatLoad(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Model loaded\n");
        appendValue(out, "Model", data.optString("loadedModelId", ""));
        appendValue(out, "Runtime", data.optString("runtime", ""));
        appendValue(out, "Backend", data.optString("backend", ""));
        appendValue(out, "Fallback", nullable(data, "backendFallbackReason", ""));
        appendValue(out, "Path", data.optString("modelPath", ""));
        JSONObject state = data.optJSONObject("state");
        if (state != null) {
            appendValue(out, "Lifecycle", state.optString("state", ""));
            appendValue(out, "Keep warm", state.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(state.optLong("keepWarmRemainingMs", 0L)) : "off");
            appendValue(out, "Idle unload", state.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(state.optLong("idleUnloadRemainingMs", 0L)) : "off");
        }
        appendCompatibility(out, data);
        return out.toString();
    }

    @NonNull
    private static String formatPreflight(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("ok", false) ? "Preflight passed\n" : "Preflight blocked\n");
        JSONObject model = data.optJSONObject("model");
        if (model != null) {
            appendValue(out, "Model", model.optString("id", ""));
            appendValue(out, "Backend", model.optString("backend", "") + " / " + model.optString("format", ""));
            appendValue(out, "Recommended memory", model.optInt("recommendedRamGb", 0) > 0
                ? model.optInt("recommendedRamGb") + " GiB" : "not specified");
        }
        appendValue(out, "Requested accelerator", data.optString("requestedAccelerator", ""));
        appendValue(out, "Effective accelerator", data.optString("effectiveAccelerator", ""));
        appendValue(out, "Message", data.optString("message", ""));
        appendCompatibility(out, data);

        JSONArray checks = data.optJSONArray("checks");
        if (checks != null && checks.length() > 0) {
            out.append("\nChecks\n");
            for (int i = 0; i < checks.length(); i++) {
                JSONObject check = checks.optJSONObject(i);
                if (check == null) continue;
                out.append("  ").append(check.optBoolean("ok", false) ? "OK " : "FAIL ")
                    .append(check.optString("id", "check"));
                String message = clean(check.optString("message", ""));
                if (!message.isEmpty()) out.append(": ").append(message);
                out.append('\n');
            }
        }

        JSONArray warnings = data.optJSONArray("warnings");
        if (warnings != null && warnings.length() > 0) {
            out.append("\nWarnings\n");
            for (int i = 0; i < warnings.length(); i++) {
                JSONObject warning = warnings.optJSONObject(i);
                if (warning == null) continue;
                String message = clean(warning.optString("message", ""));
                if (!message.isEmpty()) out.append("  - ").append(message).append('\n');
            }
        }
        return out.toString();
    }

    @NonNull
    private static String formatUnload(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("loadCancellationRequested", false)
            ? "Model load cancellation requested\n"
            : "Model unloaded\n");
        appendValue(out, "Previous model", nullable(data, "unloadedModelId", "none"));
        appendValue(out, "Loading model", nullable(data, "loadingModelId", ""));
        appendValue(out, "Runtime", data.optString("runtime", ""));
        return out.toString();
    }

    @NonNull
    private static String formatKeepWarm(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Runtime keep-warm enabled\n");
        appendValue(out, "Minutes", data.optInt("keepWarmMinutes", 0) + "");
        JSONObject state = data.optJSONObject("state");
        if (state != null) appendRuntimeState(out, state);
        return out.toString();
    }

    @NonNull
    private static String formatCancel(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("loadCancellationRequested", false)
            ? "Model load cancellation requested\n"
            : (data.optBoolean("cancelled", false) ? "Generation cancel requested\n" : "No active generation\n"));
        appendValue(out, "Message", data.optString("message", ""));
        JSONObject state = data.optJSONObject("state");
        if (state != null) appendRuntimeState(out, state);
        return out.toString();
    }

    @NonNull
    private static String formatLauncherStatus(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("LauncherCtl status\n");
        appendValue(out, "API", data.optString("apiVersion", ""));
        appendValue(out, "Backend", data.optString("backendType", "") + " / " + data.optString("backendState", ""));
        appendValue(out, "Status", data.optString("statusMessage", ""));
        appendValue(out, "Privileged available", data.optBoolean("isPrivilegedAvailable", false) ? "yes" : "no");
        appendValue(out, "Notification listener", data.optBoolean("notificationListenerConnected", false) ? "connected" : "not connected");
        return out.toString();
    }

    @NonNull
    private static String formatGeneric(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        JSONArray names = data.names();
        if (names == null || names.length() == 0) return "{}\n";
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i, "");
            Object value = data.opt(key);
            appendValue(out, key, value == null || JSONObject.NULL.equals(value) ? "" : String.valueOf(value));
        }
        return out.toString();
    }

    private static void appendDownloads(@NonNull StringBuilder out, @NonNull JSONArray downloads) {
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject transfer = downloads.optJSONObject(i);
            if (transfer == null) continue;
            out.append("  ").append(i + 1).append(". ");
            out.append(transfer.optString("modelId", transfer.optString("id", "unknown")));
            out.append(" - ").append(transfer.optString("status", "unknown"));
            String progress = progress(transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", 0L));
            if (!progress.isEmpty()) out.append(" (").append(progress).append(")");
            out.append('\n');
            appendValue(out, "     Path", transfer.optString("path", ""));
            appendValue(out, "     Error", transfer.optString("error", ""));
        }
    }

    private static void appendRuntimeState(@NonNull StringBuilder out, @NonNull JSONObject runtime) {
        appendValue(out, "Runtime", runtime.optString("runtimeName", ""));
        appendValue(out, "Lifecycle", runtime.optString("state", ""));
        appendValue(out, "Loaded model", nullable(runtime, "loadedModelId", "none"));
        appendValue(out, "Backend", runtime.optString("backend", ""));
        appendValue(out, "Fallback", nullable(runtime, "backendFallbackReason", ""));
        appendValue(out, "Active generation", runtime.optBoolean("activeGeneration", false) ? "yes" : "no");
        appendValue(out, "Keep warm", runtime.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("keepWarmRemainingMs", 0L)) : "off");
        appendValue(out, "Idle unload", runtime.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("idleUnloadRemainingMs", 0L)) : "off");
        appendValue(out, "Status", runtime.optString("status", ""));
    }

    private static void appendCompatibility(@NonNull StringBuilder out, @NonNull JSONObject data) {
        JSONObject profile = data.optJSONObject("modelProfile");
        if (profile != null) {
            out.append("\nModel profile\n");
            appendValue(out, "Compatible accelerators", join(profile.optJSONArray("compatibleAccelerators")));
            appendValue(out, "Minimum memory", profile.isNull("minDeviceMemoryInGb")
                ? "not specified" : profile.optInt("minDeviceMemoryInGb") + " GiB");
            appendValue(out, "Generation defaults", profile.optInt("defaultMaxTokens") + " tokens, TopK "
                + profile.optInt("defaultTopK") + ", TopP " + profile.optDouble("defaultTopP")
                + ", temperature " + profile.optDouble("defaultTemperature"));
            appendValue(out, "Profile source", profile.optString("source", ""));
        }
        JSONObject device = data.optJSONObject("device");
        if (device != null) {
            out.append("\nDevice\n");
            appendValue(out, "Model", device.optString("manufacturer", "") + " " + device.optString("model", ""));
            appendValue(out, "SoC", device.optString("socModel", ""));
            appendValue(out, "Android API", String.valueOf(device.optInt("sdkInt", 0)));
            appendValue(out, "Memory", device.isNull("memoryGiB") ? "unknown"
                : String.format(Locale.US, "%.1f GiB (%s)", device.optDouble("memoryGiB"), device.optString("memorySource", "")));
            appendValue(out, "LiteRT-LM ABI", device.optBoolean("liteRtLmAbiSupported", false) ? "supported" : "unsupported");
            appendValue(out, "Phase 1 accelerators", join(device.optJSONArray("phase1Accelerators")));
            appendValue(out, "GPU policy", device.optString("gpuPolicy", ""));
        }
        JSONArray warnings = data.optJSONArray("compatibilityWarnings");
        if (warnings != null && warnings.length() > 0) {
            out.append("\nCompatibility warnings\n");
            appendBullets(out, warnings);
        }
    }

    private static void appendTransfer(@NonNull StringBuilder out, @NonNull JSONObject transfer) {
        appendValue(out, "Model", transfer.optString("modelId", ""));
        appendValue(out, "Status", transfer.optString("status", ""));
        appendValue(out, "Progress", progress(transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", 0L)));
        appendValue(out, "Path", transfer.optString("path", ""));
        appendValue(out, "Error", transfer.optString("error", ""));
    }

    private static void appendBullets(@NonNull StringBuilder out, @NonNull JSONArray values) {
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) out.append("  - ").append(value).append('\n');
        }
    }

    private static void appendValue(@NonNull StringBuilder out, @NonNull String label, @NonNull String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return;
        out.append(label).append(": ").append(clean).append('\n');
    }

    @NonNull
    private static String nullable(@NonNull JSONObject object, @NonNull String key, @NonNull String fallback) {
        if (!object.has(key) || object.isNull(key)) return fallback;
        return clean(object.optString(key, fallback));
    }

    @NonNull
    private static String join(JSONArray values) {
        if (values == null || values.length() == 0) return "";
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (value.isEmpty()) continue;
            if (joined.length() > 0) joined.append(", ");
            joined.append(value);
        }
        return joined.toString();
    }

    @NonNull
    private static String progress(long bytesRead, long totalBytes) {
        if (bytesRead <= 0L && totalBytes <= 0L) return "";
        if (totalBytes <= 0L) return formatBytes(bytesRead);
        double percent = Math.max(0d, Math.min(100d, (bytesRead * 100d) / totalBytes));
        return formatBytes(bytesRead) + " / " + formatBytes(totalBytes) + " (" + String.format(Locale.US, "%.1f", percent) + "%)";
    }

    @NonNull
    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    @NonNull
    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes > 0L) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    @NonNull
    private static String clean(@NonNull String value) {
        String trimmed = value.trim();
        if ("null".equals(trimmed)) return "";
        return trimmed;
    }
}
