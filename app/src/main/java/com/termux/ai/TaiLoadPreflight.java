package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public final class TaiLoadPreflight {
    private static final long BYTES_PER_GIB = 1024L * 1024L * 1024L;
    private static final long LOW_AVAILABLE_MEMORY_BYTES = 512L * 1024L * 1024L;

    private TaiLoadPreflight() {
    }

    @NonNull
    public static Result evaluate(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiRuntimeOptions options,
        boolean autoLoad
    ) throws JSONException {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(context);
        TaiModelProfile profile = TaiModelProfile.forModel(model);
        Builder builder = new Builder(context, model, options, device, profile, autoLoad);
        builder.checkBackendFormat();
        builder.checkModelFile();
        builder.checkMemory();
        builder.checkAcceleratorPolicy();
        builder.checkFailureHistory();
        return builder.build();
    }

    @Nullable
    public static String requestedAccelerator(@NonNull TaiRuntimeOptions options) {
        return normalizeAccelerator(options.accelerator);
    }

    @NonNull
    public static String effectiveAccelerator(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiDeviceCapabilities device,
        @NonNull TaiModelProfile profile
    ) {
        String requested = requestedAccelerator(options);
        if (requested != null) return requested;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)) return "cpu";
        List<String> ordered = autoAccelerators(context, model, device, profile);
        if (!ordered.isEmpty()) return ordered.get(0);
        return "none";
    }

    /**
     * Returns the model-declared accelerator order, demoting only accelerators with a recorded
     * failure on this exact model/device. A new GPU is therefore tried on its first automatic load,
     * matching Gallery's ordered accelerator configuration instead of requiring a circular
     * "successful GPU history" prerequisite.
     */
    @NonNull
    static List<String> autoAccelerators(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiDeviceCapabilities device,
        @NonNull TaiModelProfile profile
    ) {
        ArrayList<String> preferred = new ArrayList<>();
        ArrayList<String> failed = new ArrayList<>();
        for (String accelerator : profile.compatibleAccelerators) {
            if (!device.supportsAccelerator(accelerator)) continue;
            if (TaiRuntimeHistory.failedEntry(context, model, device, accelerator) == null) {
                preferred.add(accelerator);
            } else {
                failed.add(accelerator);
            }
        }
        preferred.addAll(failed);
        return preferred;
    }

    @Nullable
    public static String normalizeAccelerator(@Nullable String accelerator) {
        if (accelerator == null) return null;
        String value = accelerator.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "auto".equals(value)) return null;
        if ("opencl".equals(value)) return "gpu";
        if ("gpu".equals(value) || "cpu".equals(value)) return value;
        return value;
    }

    public static final class Result {
        @NonNull public final TaiDeviceCapabilities device;
        @NonNull public final TaiModelProfile profile;
        @NonNull public final String requestedAccelerator;
        @NonNull public final String effectiveAccelerator;
        @NonNull public final JSONArray checks;
        @NonNull public final JSONArray warnings;
        public final boolean autoLoad;
        public final boolean blocked;
        @NonNull public final String errorCode;
        @NonNull public final String message;

        private Result(
            @NonNull TaiDeviceCapabilities device,
            @NonNull TaiModelProfile profile,
            @NonNull String requestedAccelerator,
            @NonNull String effectiveAccelerator,
            @NonNull JSONArray checks,
            @NonNull JSONArray warnings,
            boolean autoLoad,
            boolean blocked,
            @NonNull String errorCode,
            @NonNull String message
        ) {
            this.device = device;
            this.profile = profile;
            this.requestedAccelerator = requestedAccelerator;
            this.effectiveAccelerator = effectiveAccelerator;
            this.checks = checks;
            this.warnings = warnings;
            this.autoLoad = autoLoad;
            this.blocked = blocked;
            this.errorCode = errorCode;
            this.message = message;
        }

        public boolean clean() {
            return !blocked && warnings.length() == 0;
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("ok", !blocked);
            json.put("clean", clean());
            json.put("blocked", blocked);
            json.put("error", errorCode.isEmpty() ? JSONObject.NULL : errorCode);
            json.put("message", message.isEmpty() ? JSONObject.NULL : message);
            json.put("autoLoad", autoLoad);
            json.put("requestedAccelerator", requestedAccelerator);
            json.put("effectiveAccelerator", effectiveAccelerator);
            json.put("checks", checks);
            json.put("warnings", warnings);
            json.put("device", device.toJson());
            json.put("modelProfile", profile.toJson());
            return json;
        }

        @NonNull
        public JSONObject blockingError(int statusCode) throws JSONException {
            JSONObject json = new JSONObject();
            json.put("ok", false);
            json.put("error", errorCode.isEmpty() ? "preflight_failed" : errorCode);
            json.put("message", message.isEmpty() ? "TAI model preflight failed." : message);
            json.put("_statusCode", statusCode);
            json.put("preflight", toJson());
            return json;
        }
    }

    private static final class Builder {
        @NonNull private final Context context;
        @NonNull private final TaiModelSpec model;
        @NonNull private final TaiRuntimeOptions options;
        @NonNull private final TaiDeviceCapabilities device;
        @NonNull private final TaiModelProfile profile;
        private final boolean autoLoad;
        @NonNull private final JSONArray checks = new JSONArray();
        @NonNull private final JSONArray warnings = new JSONArray();
        @NonNull private final String requestedAccelerator;
        @NonNull private final String effectiveAccelerator;
        private boolean blocked;
        @NonNull private String errorCode = "";
        @NonNull private String message = "";

        Builder(
            @NonNull Context context,
            @NonNull TaiModelSpec model,
            @NonNull TaiRuntimeOptions options,
            @NonNull TaiDeviceCapabilities device,
            @NonNull TaiModelProfile profile,
            boolean autoLoad
        ) {
            this.context = context.getApplicationContext();
            this.model = model;
            this.options = options;
            this.device = device;
            this.profile = profile;
            this.autoLoad = autoLoad;
            String requested = requestedAccelerator(options);
            this.requestedAccelerator = requested == null ? "auto" : requested;
            this.effectiveAccelerator = effectiveAccelerator(context, model, options, device, profile);
        }

        void checkBackendFormat() throws JSONException {
            boolean formatSupported = TaiModelSpec.isSupportedBackendFormat(model.backend, model.format);
            check("backend_format", formatSupported, "Model backend and format are compatible.",
                "Model backend " + model.backend + " cannot load format " + model.format + ".");
            if (!formatSupported) block("unsupported_backend_format",
                "Model backend " + model.backend + " cannot load format " + model.format + ".");

            if (TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend)) {
                check("android_api_level", true, "Android API level " + device.sdkInt + " is allowed for LiteRT-LM.", "");
                check("litert_abi", device.liteRtLmAbiSupported,
                    "Device ABI can load LiteRT-LM native libraries.",
                    "LiteRT-LM 0.12.0 ships native libraries for arm64-v8a and x86_64 only.");
                if (!device.liteRtLmAbiSupported) block("litert_lm_unsupported_abi",
                    "LiteRT-LM 0.12.0 ships native libraries for arm64-v8a and x86_64 only.");
                check("litert_native_libraries", device.liteRtLmNativeLibrariesAvailable,
                    "LiteRT-LM native libraries are bundled in this APK.",
                    "LiteRT-LM native libraries are not available in this APK.");
                if (!device.liteRtLmNativeLibrariesAvailable) block("litert_lm_native_unavailable",
                    "LiteRT-LM native libraries are not available in this APK.");
            } else if (TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)) {
                check("android_api_level", device.sdkInt >= TaiDeviceCapabilities.MNN_SDK_MINIMUM,
                    "Android API level satisfies MNN requirements.",
                    "MNN requires Android 7.0 (API " + TaiDeviceCapabilities.MNN_SDK_MINIMUM + ") or higher.");
                check("mnn_runtime", device.mnnSupported,
                    "MNN native runtime is available for this APK and ABI.",
                    device.mnnUnsupportedReason == null ? "MNN runtime is not supported on this device." : device.mnnUnsupportedReason);
                if (!device.mnnSupported) block("mnn_native_unavailable",
                    device.mnnUnsupportedReason == null ? "MNN runtime is not supported on this device." : device.mnnUnsupportedReason);
            }
        }

        void checkModelFile() throws JSONException {
            if (model.localPath == null || model.localPath.trim().isEmpty()) {
                check("model_file", false, "", "Download or import this model before loading it.");
                block("model_file_missing", "Download or import this model before loading it.");
                return;
            }
            File file = new File(model.localPath);
            boolean readable = file.isFile() && file.canRead();
            check("model_file_readable", readable, "Model file is readable.", "Model file is missing or unreadable.");
            if (!readable) {
                block("model_file_not_readable", "Model file is missing or unreadable.");
                return;
            }
            String lowerName = file.getName().toLowerCase(Locale.ROOT);
            if (TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend)) {
                boolean formatLooksRight = lowerName.endsWith(".litertlm") || lowerName.endsWith(".task") || lowerName.endsWith(".tflite");
                check("model_format", formatLooksRight, "LiteRT-LM model package has an expected extension.",
                    "LiteRT-LM models must be .litertlm, .task, or .tflite packages.");
                if (!formatLooksRight) block("model_file_wrong_format",
                    "LiteRT-LM models must be .litertlm, .task, or .tflite packages.");
            } else if (TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)) {
                boolean configJson = "config.json".equals(lowerName);
                check("model_format", configJson, "MNN model points at config.json.",
                    "MNN models must point at a readable config.json file.");
                if (!configJson) {
                    block("model_file_wrong_format", "MNN models must point at a readable config.json file.");
                    return;
                }
                checkMnnSidecars(file);
            }
        }

        void checkMemory() throws JSONException {
            int requiredGb = model.recommendedRamGb > 0
                ? model.recommendedRamGb
                : (profile.minDeviceMemoryInGb == null ? 0 : profile.minDeviceMemoryInGb);
            if (requiredGb > 0 && device.memoryBytes > 0L) {
                long requiredBytes = requiredGb * BYTES_PER_GIB;
                boolean hasRecommendedRam = device.memoryBytes >= requiredBytes;
                check("recommended_ram", hasRecommendedRam,
                    "Advertised device memory meets the model recommendation.",
                    "Device memory is below this model's recommended " + requiredGb + " GiB.");
                if (!hasRecommendedRam) warning("recommended_ram",
                    "Device memory is below this model's recommended " + requiredGb + " GiB.");
            }
            if (device.availableMemoryBytes > 0L) {
                boolean enoughAvailable = device.availableMemoryBytes >= LOW_AVAILABLE_MEMORY_BYTES;
                check("available_memory", enoughAvailable,
                    "Available memory is above the hard guard threshold.",
                    "Available memory is below 512 MiB.");
                if (!enoughAvailable || device.lowMemory) {
                    block("low_available_memory", "Available memory is too low to start a local AI runtime safely.");
                }
            } else {
                warning("available_memory_unknown", "Available memory could not be measured before load.");
            }
        }

        void checkAcceleratorPolicy() throws JSONException {
            if (TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)) {
                check("accelerator_policy", true, "MNN auto defaults to CPU unless OpenCL is explicitly requested.", "");
                return;
            }
            if ("none".equals(effectiveAccelerator)) {
                block("no_compatible_accelerator", "No accelerator is compatible with both this model and device.");
                check("accelerator_policy", false, "", "No accelerator is compatible with both this model and device.");
                return;
            }
            if (!"auto".equals(requestedAccelerator)) {
                boolean modelSupports = profile.supports(effectiveAccelerator);
                boolean deviceSupports = device.supportsAccelerator(effectiveAccelerator);
                check("accelerator_model_support", modelSupports,
                    "Model profile supports " + effectiveAccelerator + ".",
                    "The model profile does not support " + effectiveAccelerator + ".");
                check("accelerator_device_support", deviceSupports,
                    "Device supports " + effectiveAccelerator + " for LiteRT-LM.",
                    effectiveAccelerator.toUpperCase(Locale.ROOT) + " is disabled or unsupported on this device.");
                if (!modelSupports) block("accelerator_not_supported_by_model",
                    "The model profile does not support " + effectiveAccelerator + ".");
                if (!deviceSupports) block("accelerator_not_supported_by_device",
                    effectiveAccelerator.toUpperCase(Locale.ROOT) + " is disabled or unsupported on this device.");
                return;
            }
            check("accelerator_policy", !blocked,
                "Accelerator policy selected " + effectiveAccelerator + ".",
                message);
        }

        void checkFailureHistory() throws JSONException {
            JSONObject failed = TaiRuntimeHistory.failedEntry(context, model, device, effectiveAccelerator);
            if (failed == null) return;
            if ("cpu".equals(effectiveAccelerator)) {
                warning("previous_cpu_failure", "This model/backend failed previously on CPU: " + failed.optString("reason", "unknown"));
                return;
            }
            block("known_failed_accelerator",
                "This model/backend previously failed on " + effectiveAccelerator + ": " + failed.optString("reason", "unknown"));
        }

        @NonNull
        Result build() {
            if (autoLoad && !blocked && warnings.length() > 0) {
                blocked = true;
                if (errorCode.isEmpty()) errorCode = "autoload_preflight_not_clean";
                if (message.isEmpty()) message = "Automatic model loading requires a clean compatibility preflight.";
            }
            return new Result(device, profile, requestedAccelerator, effectiveAccelerator,
                checks, warnings, autoLoad, blocked, errorCode, message);
        }

        private void checkMnnSidecars(@NonNull File config) throws JSONException {
            File modelDir = config.getParentFile();
            if (modelDir == null) {
                block("model_file_not_readable", "MNN model config has no parent directory.");
                return;
            }
            JSONObject json;
            try {
                json = readJsonFile(config);
            } catch (JSONException e) {
                block("model_file_not_readable", e.getMessage() == null ? "Could not read MNN config." : e.getMessage());
                return;
            }
            checkMnnSidecar(modelDir, json, "llm_model", "llm.mnn");
            checkMnnSidecar(modelDir, json, "llm_weight", "llm.mnn.weight");
            // Qwen3-VL/eagle packages omit tokenizer_file and ship the conventional tokenizer.txt.
            String tokenizer = TaiModelStore.mnnTokenizerFile(modelDir, json);
            if (tokenizer.trim().isEmpty()) {
                block("model_file_not_readable", "MNN config is missing tokenizer_file.");
                check("mnn_tokenizer", false, "", "MNN config is missing tokenizer_file.");
            } else {
                checkMnnSidecar(modelDir, json, "tokenizer_file", tokenizer);
            }
        }

        private void checkMnnSidecar(
            @NonNull File modelDir,
            @NonNull JSONObject config,
            @NonNull String key,
            @NonNull String fallback
        ) throws JSONException {
            String fileName = config.optString(key, fallback);
            if (fileName == null || fileName.trim().isEmpty()) fileName = fallback;
            File file = new File(modelDir, fileName);
            boolean readable = file.isFile() && file.canRead();
            check("mnn_sidecar_" + key, readable,
                "MNN sidecar is readable: " + fileName,
                "MNN package sidecar is missing or unreadable: " + fileName);
            if (!readable) block("model_file_not_readable",
                "MNN package sidecar is missing or unreadable: " + fileName);
        }

        private void check(
            @NonNull String id,
            boolean ok,
            @NonNull String okMessage,
            @NonNull String failMessage
        ) throws JSONException {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("ok", ok);
            item.put("message", ok ? okMessage : failMessage);
            checks.put(item);
        }

        private void warning(@NonNull String id, @NonNull String warning) throws JSONException {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("message", warning);
            warnings.put(item);
        }

        private void block(@NonNull String code, @NonNull String reason) {
            if (!blocked) {
                blocked = true;
                errorCode = code;
                message = reason;
            }
        }
    }

    @NonNull
    private static JSONObject readJsonFile(@NonNull File file) throws JSONException {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            String message = e.getMessage();
            throw new JSONException("Could not read MNN config: " + (message == null ? e.getClass().getSimpleName() : message));
        }
    }
}
