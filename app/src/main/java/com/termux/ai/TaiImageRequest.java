package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * One image-generation request as the API and the CLI send it, checked and normalised in one place
 * (the speech request's twin). Two steps, both pure: {@link #parse} checks the shape and resolves the
 * paths it was given through a {@link PathResolver} (the allowed-roots policy in production), and
 * {@link #validateForPackage} applies the rules that depend on the model's type once the package
 * has been recognised: Stable Diffusion and Taiyi are fixed at 512x512 by the engine, Sana takes
 * any size that is a multiple of 32 between 256 and 2048 and is the only family with image input.
 *
 * <p>Body fields: {@code model} (a registered id) or {@code model_path} (a local directory, the
 * pre-importer test path), {@code model_type} (sd15 | taiyi | sana; needed for Taiyi, which shares
 * Stable Diffusion's file layout), {@code prompt}, {@code size} ("WxH"), {@code n} (only 1),
 * {@code steps}, {@code seed} (-1 random), {@code cfg_scale} (Sana; 0 turns guidance off),
 * {@code image} (input path, Sana), {@code backend} (opencl | cpu), {@code memory_mode} (0 | 1 | 2),
 * {@code output} (a PNG path to write instead of returning base64) and {@code response_format}
 * ({@code b64_json}, the only one supported).
 */
final class TaiImageRequest {
    /** Resolves a local path against the allowed roots; throws {@code JSONException("code:message")}. */
    interface PathResolver {
        @NonNull String resolve(@NonNull String rawPath) throws JSONException;
    }

    static final int SD_SIZE = 512;
    static final int SANA_MIN = 256;
    static final int SANA_MAX = 2048;
    static final int SANA_MULTIPLE = 32;
    static final int DEFAULT_STEPS = 20;
    static final int MAX_STEPS = 100;
    static final int MAX_PROMPT_CHARS = 2000;
    static final float DEFAULT_SANA_CFG = 4.5f;
    static final String BACKEND_OPENCL = "opencl";
    static final String BACKEND_CPU = "cpu";
    /** {@link #memoryMode} when the caller did not choose; admission picks from the device's memory. */
    static final int MEMORY_MODE_AUTO = -1;

    @NonNull final String prompt;
    @Nullable final String modelId;
    /** A resolved local directory when the request named {@code model_path}. */
    @Nullable final String modelPath;
    /** {@link TaiDiffusionPackage#TYPE_AUTO} when the request did not say. */
    final int typeHint;
    /** -1 when the request gave no size; filled with the type's default at validation. */
    final int width;
    final int height;
    /** 0 when the request gave none. */
    final int steps;
    final int seed;
    /** {@code null} when the request did not choose; Sana defaults to {@link #DEFAULT_SANA_CFG}. */
    @Nullable final Float cfgScale;
    @Nullable final String inputImage;
    @NonNull final String backend;
    final int memoryMode;
    /** A resolved PNG path, or {@code null} to return the image inline. */
    @Nullable final String outputPath;
    final boolean stream;

    @Nullable final String errorCode;
    @Nullable final String errorMessage;
    @Nullable final String errorParam;
    final int statusCode;

    private TaiImageRequest(@NonNull String prompt, @Nullable String modelId, @Nullable String modelPath, int typeHint,
                            int width, int height, int steps, int seed, @Nullable Float cfgScale,
                            @Nullable String inputImage, @NonNull String backend, int memoryMode,
                            @Nullable String outputPath, boolean stream, @Nullable String errorCode,
                            @Nullable String errorMessage, @Nullable String errorParam, int statusCode) {
        this.prompt = prompt;
        this.modelId = modelId;
        this.modelPath = modelPath;
        this.typeHint = typeHint;
        this.width = width;
        this.height = height;
        this.steps = steps;
        this.seed = seed;
        this.cfgScale = cfgScale;
        this.inputImage = inputImage;
        this.backend = backend;
        this.memoryMode = memoryMode;
        this.outputPath = outputPath;
        this.stream = stream;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorParam = errorParam;
        this.statusCode = statusCode;
    }

    boolean isValid() {
        return errorCode == null;
    }

    @NonNull
    private static TaiImageRequest refuse(int status, @NonNull String code, @NonNull String message, @Nullable String param) {
        return new TaiImageRequest("", null, null, TaiDiffusionPackage.TYPE_AUTO, -1, -1, 0, -1, null, null,
            BACKEND_OPENCL, MEMORY_MODE_AUTO, null, false, code, message, param, status);
    }

    @NonNull
    static TaiImageRequest parse(@NonNull JSONObject request, @NonNull PathResolver paths) {
        String prompt = request.optString("prompt", "").trim();
        if (prompt.isEmpty()) return refuse(400, "missing_prompt", "An image prompt is required.", "prompt");
        if (prompt.length() > MAX_PROMPT_CHARS) {
            return refuse(400, "prompt_too_long", "The image prompt is longer than " + MAX_PROMPT_CHARS + " characters.", "prompt");
        }
        if (request.has("n") && request.optInt("n", 1) != 1) {
            return refuse(400, "unsupported_n", "Only one image can be generated per request.", "n");
        }
        String format = request.optString("response_format", "b64_json").trim().toLowerCase(Locale.ROOT);
        if (!format.isEmpty() && !"b64_json".equals(format)) {
            return refuse(400, "unsupported_response_format",
                "Images are returned as b64_json, or written to a file with output.", "response_format");
        }
        String modelId = request.optString("model", "").trim();
        String modelPathRaw = request.optString("model_path", "").trim();
        if (!modelId.isEmpty() && !modelPathRaw.isEmpty()) {
            return refuse(400, "ambiguous_model", "Give either model or model_path, not both.", "model");
        }
        if (modelId.isEmpty() && modelPathRaw.isEmpty()) {
            return refuse(400, "missing_model", "Name an installed image model with model, or a folder with model_path.", "model");
        }
        String modelPath = null;
        if (!modelPathRaw.isEmpty()) {
            try {
                modelPath = paths.resolve(modelPathRaw);
            } catch (JSONException e) {
                return pathRefusal(e, "model_path");
            }
        }
        int typeHint = TaiDiffusionPackage.TYPE_AUTO;
        if (request.has("model_type")) {
            typeHint = TaiDiffusionPackage.parseType(request.optString("model_type", ""));
            if (typeHint == -2) {
                return refuse(400, "invalid_model_type", "model_type must be sd15, taiyi or sana.", "model_type");
            }
        }
        int width = -1;
        int height = -1;
        if (request.has("size")) {
            String size = request.optString("size", "").trim().toLowerCase(Locale.ROOT);
            int x = size.indexOf('x');
            try {
                if (x <= 0 || x == size.length() - 1) throw new NumberFormatException();
                width = Integer.parseInt(size.substring(0, x));
                height = Integer.parseInt(size.substring(x + 1));
            } catch (NumberFormatException e) {
                return refuse(400, "invalid_size", "size must look like 512x512.", "size");
            }
            if (width <= 0 || height <= 0) return refuse(400, "invalid_size", "size must look like 512x512.", "size");
        }
        int steps = 0;
        if (request.has("steps")) {
            steps = request.optInt("steps", 0);
            if (steps < 1 || steps > MAX_STEPS) {
                return refuse(400, "invalid_steps", "steps must be between 1 and " + MAX_STEPS + ".", "steps");
            }
        }
        int seed = request.has("seed") ? request.optInt("seed", -1) : -1;
        Float cfg = null;
        if (request.has("cfg_scale")) {
            double value = request.optDouble("cfg_scale", Double.NaN);
            if (Double.isNaN(value) || value < 0.0 || value > 30.0) {
                return refuse(400, "invalid_cfg_scale", "cfg_scale must be between 0 and 30.", "cfg_scale");
            }
            cfg = (float) value;
        }
        String backend = request.optString("backend", BACKEND_OPENCL).trim().toLowerCase(Locale.ROOT);
        if (backend.isEmpty()) backend = BACKEND_OPENCL;
        if (!BACKEND_OPENCL.equals(backend) && !BACKEND_CPU.equals(backend)) {
            return refuse(400, "invalid_backend", "backend must be opencl or cpu.", "backend");
        }
        int memoryMode = MEMORY_MODE_AUTO;
        if (request.has("memory_mode")) {
            memoryMode = request.optInt("memory_mode", -2);
            if (memoryMode < 0 || memoryMode > 2) {
                return refuse(400, "invalid_memory_mode", "memory_mode must be 0, 1 or 2.", "memory_mode");
            }
        }
        String inputImage = null;
        String imageRaw = request.optString("image", "").trim();
        if (!imageRaw.isEmpty()) {
            try {
                inputImage = paths.resolve(imageRaw);
            } catch (JSONException e) {
                return pathRefusal(e, "image");
            }
        }
        String outputPath = null;
        String outputRaw = request.optString("output", "").trim();
        if (!outputRaw.isEmpty()) {
            try {
                outputPath = paths.resolve(outputRaw);
            } catch (JSONException e) {
                return pathRefusal(e, "output");
            }
            if (!outputPath.toLowerCase(Locale.ROOT).endsWith(".png")) {
                return refuse(400, "invalid_output", "output must be a .png file.", "output");
            }
        }
        return new TaiImageRequest(prompt, modelId.isEmpty() ? null : modelId, modelPath, typeHint, width, height,
            steps, seed, cfg, inputImage, backend, memoryMode, outputPath, request.optBoolean("stream", false),
            null, null, null, 200);
    }

    @NonNull
    private static TaiImageRequest pathRefusal(@NonNull JSONException e, @NonNull String param) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        int colon = raw.indexOf(':');
        String code = colon > 0 ? raw.substring(0, colon) : "invalid_path";
        String message = colon > 0 ? raw.substring(colon + 1) : "The path is not usable.";
        return refuse(code.endsWith("denied") ? 403 : 400, code, message, param);
    }

    /** Fixed-size and capability rules for the recognised package; {@code null} when the request fits. */
    @Nullable
    static Refusal validateForPackage(@NonNull TaiImageRequest request, int type, boolean supportsImageInput) {
        int width = request.width;
        int height = request.height;
        if (type == TaiDiffusionPackage.TYPE_SANA) {
            if (width > 0 || height > 0) {
                if (width % SANA_MULTIPLE != 0 || height % SANA_MULTIPLE != 0) {
                    return new Refusal(400, "invalid_size", "Sana sizes must be multiples of 32.", "size");
                }
                if (width < SANA_MIN || width > SANA_MAX || height < SANA_MIN || height > SANA_MAX) {
                    return new Refusal(400, "invalid_size", "Sana sizes must be between 256 and 2048.", "size");
                }
            }
            if (request.inputImage != null && !supportsImageInput) {
                return new Refusal(400, "image_input_unavailable",
                    "This Sana package has no image encoder, so it cannot edit an input image.", "image");
            }
        } else {
            if ((width > 0 && width != SD_SIZE) || (height > 0 && height != SD_SIZE)) {
                return new Refusal(400, "invalid_size", "This model only makes 512x512 images.", "size");
            }
            if (request.inputImage != null) {
                return new Refusal(400, "image_input_unavailable", "Only Sana models can edit an input image.", "image");
            }
        }
        return null;
    }

    int widthFor(int type) {
        return width > 0 ? width : SD_SIZE;
    }

    int heightFor(int type) {
        return height > 0 ? height : SD_SIZE;
    }

    int stepsFor(int type) {
        return steps > 0 ? steps : DEFAULT_STEPS;
    }

    /** Sana's classifier-free guidance scale; 0 means guidance is off. SD/Taiyi ignore it. */
    float cfgScaleFor(int type) {
        if (type != TaiDiffusionPackage.TYPE_SANA) return 0f;
        return cfgScale != null ? cfgScale : DEFAULT_SANA_CFG;
    }

    /** A refusal in the OpenAI error fields, for the type-dependent rules. */
    static final class Refusal {
        final int statusCode;
        @NonNull final String code;
        @NonNull final String message;
        @NonNull final String param;

        Refusal(int statusCode, @NonNull String code, @NonNull String message, @NonNull String param) {
            this.statusCode = statusCode;
            this.code = code;
            this.message = message;
            this.param = param;
        }
    }
}
