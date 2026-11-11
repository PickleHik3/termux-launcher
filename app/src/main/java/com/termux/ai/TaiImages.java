package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.ai.TaiManager.OpenAiStreamSink;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Image generation (MNN diffusion): check, generate, stream, cancel. Moved out of TaiManager. */
public final class TaiImages {
    private final TaiManager manager;

    TaiImages(@NonNull TaiManager manager) {
        this.manager = manager;
    }

    /** Receives 0-100 progress; the engine cannot be interrupted, so a throw only ends the wait and discards the image. */
    public interface ImageProgress {
        void onProgress(int percent) throws IOException;
    }

    /** A request that passed every check, or the refusal that stopped it. */
    private static final class PreparedImage {
        @Nullable final JSONObject refusal;
        @Nullable final TaiImageRequest request;
        @Nullable final TaiModelSpec spec;
        @Nullable final String modelDir;
        @Nullable final TaiDiffusionPackage.Result pkg;

        PreparedImage(@Nullable JSONObject refusal, @Nullable TaiImageRequest request, @Nullable TaiModelSpec spec,
                      @Nullable String modelDir, @Nullable TaiDiffusionPackage.Result pkg) {
            this.refusal = refusal;
            this.request = request;
            this.spec = spec;
            this.modelDir = modelDir;
            this.pkg = pkg;
        }
    }

    /**
     * Checks an image request without running it: the refusal (OpenAI error shape with
     * {@code _statusCode}) or {@code null} when it would run. The API calls this before it commits
     * to a 200 and a streamed body.
     */
    @Nullable
    public JSONObject checkImageRequest(@NonNull String body) throws JSONException {
        return prepareImage(manager.parseBody(body)).refusal;
    }

    @NonNull
    private PreparedImage prepareImage(@NonNull JSONObject request) throws JSONException {
        TaiImageRequest parsed = TaiImageRequest.parse(request, TaiMediaAccess::resolveLocalPath);
        if (!parsed.isValid()) {
            return new PreparedImage(manager.openAiRequestError(parsed.statusCode,
                parsed.errorCode == null ? "bad_request" : parsed.errorCode,
                parsed.errorMessage == null ? "Bad image request." : parsed.errorMessage, parsed.errorParam),
                null, null, null, null);
        }
        TaiModelSpec spec = null;
        String dir;
        int typeHint = parsed.typeHint;
        String param = parsed.modelPath != null ? "model_path" : "model";
        if (parsed.modelPath != null) {
            dir = parsed.modelPath;
        } else {
            String modelId = parsed.modelId == null ? "" : parsed.modelId;
            spec = manager.resolveModel(request, modelId);
            if (spec == null) {
                return new PreparedImage(manager.openAiRequestError(404, "model_not_found", "Unknown model: " + modelId, "model"),
                    null, null, null, null);
            }
            if (!spec.isImageGeneration()) {
                return new PreparedImage(manager.openAiRequestError(400, "not_an_image_model",
                    "Model '" + modelId + "' is not an image model.", "model"), null, null, null, null);
            }
            if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
                return new PreparedImage(manager.openAiRequestError(404, "model_file_missing",
                    "Model '" + modelId + "' is not installed.", "model"), null, null, null, null);
            }
            dir = spec.localPath;
            if (typeHint == TaiDiffusionPackage.TYPE_AUTO) {
                int declared = TaiDiffusionPackage.parseType(spec.architecture);
                typeHint = declared == -2 ? TaiDiffusionPackage.TYPE_AUTO : declared;
            }
        }
        TaiDiffusionPackage.Result pkg = TaiDiffusionPackage.inspect(new File(dir), typeHint);
        if (!pkg.ok()) {
            int status = TaiDiffusionPackage.FAIL_NOT_A_DIRECTORY.equals(pkg.failure) ? 404 : 400;
            return new PreparedImage(manager.openAiRequestError(status, pkg.failure == null ? "image_model_invalid" : pkg.failure,
                pkg.message, param), null, null, null, null);
        }
        TaiImageRequest.Refusal typeRefusal = TaiImageRequest.validateForPackage(parsed, pkg.type, pkg.supportsImageInput);
        if (typeRefusal != null) {
            return new PreparedImage(manager.openAiRequestError(typeRefusal.statusCode, typeRefusal.code, typeRefusal.message,
                typeRefusal.param), null, null, null, null);
        }
        if (spec == null) spec = TaiDiffusionPackage.syntheticSpec(dir, pkg.type, pkg.totalBytes);
        return new PreparedImage(null, parsed, spec, dir, pkg);
    }

    /**
     * {@code POST /v1/ai/images/generations} from the app process: validates, hands the run to the
     * runtime process (which writes the PNG under {@code cacheDir/tai-ipc}), then returns the image
     * inline as base64 or moves it to the request's {@code output} path. Progress (0-100) is
     * reported as it arrives. Answers the OpenAI-shaped {@code {created, data, tai}} or an error
     * envelope. When {@code progress} throws (the HTTP client went away) the generation is asked to
     * discard its result and the exception is rethrown.
     */
    @NonNull
    public JSONObject generateImage(@NonNull String body, @NonNull ImageProgress progress) throws JSONException, IOException {
        JSONObject request = manager.parseBody(body);
        PreparedImage prepared = prepareImage(request);
        if (prepared.refusal != null) return prepared.refusal;
        TaiImageRequest parsed = prepared.request;
        if (parsed == null) return manager.error(500, "image_request_invalid", "The image request could not be read.");
        JSONObject summary;
        if (!manager.shouldDelegateRuntime()) {
            // In-process (a runtime override): run straight through the router.
            final IOException[] failure = new IOException[1];
            summary = runPreparedImage(prepared, percent -> {
                try {
                    progress.onProgress(percent);
                } catch (IOException e) {
                    failure[0] = e;
                    MultiBackendTaiRuntime router = manager.speech().ttsRouter();
                    if (router != null) router.cancelImage();
                }
            });
            if (failure[0] != null) {
                discardImageFile(summary);
                throw failure[0];
            }
        } else {
            if (manager.runtimeClient == null) return manager.error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
            if (prepared.spec != null && parsed.modelPath == null) request.put(TaiManager.INTERNAL_MODEL_SPEC, prepared.spec.toJson());
            final JSONObject[] holder = new JSONObject[1];
            try {
                manager.runtimeClient.stream(TaiRuntimeIpc.OP_IMAGE_GENERATE, request.toString(), new OpenAiStreamSink() {
                    @Override
                    public void onEvent(@NonNull JSONObject event) throws IOException {
                        if (event.has("progress")) {
                            progress.onProgress(event.optInt("progress", 0));
                        } else if (event.has("result")) {
                            holder[0] = event.optJSONObject("result");
                        } else if (event.has("error")) {
                            holder[0] = event;
                        }
                    }

                    @Override
                    public void onDone() {
                    }
                });
            } catch (IOException | RuntimeException e) {
                // The client went away mid-run: have the runtime throw the image away.
                try {
                    cancelImage();
                } catch (JSONException ignored) {
                }
                throw e;
            }
            summary = holder[0];
            if (summary == null) return manager.error(500, "image_stream_incomplete", "The image runtime ended without a result.");
        }
        if (summary.has("error") && !summary.has("file")) return summary;
        return finishImage(summary, parsed, prepared);
    }

    @NonNull
    private JSONObject finishImage(@NonNull JSONObject summary, @NonNull TaiImageRequest parsed,
                                   @NonNull PreparedImage prepared) throws JSONException, IOException {
        File file = new File(summary.optString("file", ""));
        if (!file.isFile()) return manager.error(500, "image_file_missing", "The generated image could not be found.");
        JSONObject item = new JSONObject();
        try {
            if (parsed.outputPath != null) {
                File target = new File(parsed.outputPath);
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    return manager.openAiRequestError(400, "invalid_output", "The output folder could not be created.", "output");
                }
                if (!file.renameTo(target)) {
                    try (java.io.InputStream in = new java.io.FileInputStream(file);
                         java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                    }
                }
                item.put("path", target.getAbsolutePath());
            } else {
                item.put("b64_json", Base64.getEncoder().encodeToString(manager.speech().readAllBytes(file)));
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        JSONObject tai = new JSONObject();
        for (String key : new String[] {"width", "height", "steps", "seed", "backend", "memoryMode", "modelType", "loadMs",
                "generateMs", "evicted"}) {
            if (summary.has(key)) tai.put(key, summary.get(key));
        }
        tai.put("model", prepared.spec == null ? "" : prepared.spec.id);
        JSONObject response = new JSONObject();
        response.put("created", System.currentTimeMillis() / 1000L);
        response.put("data", new JSONArray().put(item));
        response.put("tai", tai);
        return response;
    }

    private static void discardImageFile(@Nullable JSONObject summary) {
        if (summary == null) return;
        String file = summary.optString("file", "");
        if (!file.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            new File(file).delete();
        }
    }

    /** The runtime-process half of {@link #generateImage}: progress events, then the result or an error. */
    void generateImageToEvents(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        PreparedImage prepared = prepareImage(manager.parseBody(body));
        JSONObject result;
        final IOException[] failure = new IOException[1];
        if (prepared.refusal != null) {
            result = prepared.refusal;
        } else {
            result = runPreparedImage(prepared, percent -> {
                if (failure[0] != null) return;
                try {
                    sink.onEvent(new JSONObject().put("progress", percent));
                } catch (IOException | JSONException e) {
                    failure[0] = e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
                }
            });
        }
        if (failure[0] != null) {
            discardImageFile(result);
            throw failure[0];
        }
        if (result.has("error") && !result.has("file")) {
            sink.onEvent(result);
        } else {
            sink.onEvent(new JSONObject().put("result", result));
        }
        sink.onDone();
    }

    /** Admission, then the run through the router; the runtime summary (file and timings) or an error envelope. */
    @NonNull
    private JSONObject runPreparedImage(@NonNull PreparedImage prepared, @NonNull java.util.function.IntConsumer progress)
            throws JSONException {
        MultiBackendTaiRuntime router = manager.speech().ttsRouter();
        TaiImageRequest parsed = prepared.request;
        TaiModelSpec spec = prepared.spec;
        TaiDiffusionPackage.Result pkg = prepared.pkg;
        if (router == null || parsed == null || spec == null || pkg == null || prepared.modelDir == null) {
            return manager.openAiRequestError(501, "capability_not_supported", "Image generation needs the multi-backend runtime.", "model");
        }
        ImageLoadDecision decision = decideImageLoad(router, spec, pkg, parsed);
        if (decision.refusal != null) return decision.refusal;
        File dir = new File(manager.appContext.getCacheDir(), TaiManager.STT_IPC_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File output = new File(dir, "tai-image-" + System.nanoTime() + ".png");
        MnnDiffusionRuntime.Params params = new MnnDiffusionRuntime.Params(spec, prepared.modelDir, pkg.type,
            parsed.backend, decision.memoryMode, pkg.peakBytes, parsed.prompt, parsed.inputImage, output,
            parsed.widthFor(pkg.type), parsed.heightFor(pkg.type), parsed.stepsFor(pkg.type), parsed.seed,
            parsed.cfgScaleFor(pkg.type));
        // Sana reports no progress of its own before the end; say the run has started.
        progress.accept(0);
        JSONObject result = router.generateImage(params, progress::accept);
        if (!decision.evicted.isEmpty() && result.has("file")) {
            JSONArray evicted = new JSONArray();
            for (String id : decision.evicted) evicted.put(id);
            result.put("evicted", evicted);
        }
        return result;
    }

    private static final class ImageLoadDecision {
        @Nullable final JSONObject refusal;
        final int memoryMode;
        @NonNull final List<String> evicted;

        ImageLoadDecision(@Nullable JSONObject refusal, int memoryMode, @NonNull List<String> evicted) {
            this.refusal = refusal;
            this.memoryMode = memoryMode;
            this.evicted = evicted;
        }
    }

    /**
     * The budget for an image run: the fastest memory mode whose estimate fits (see
     * {@link TaiImageAdmission}), with idle embeddings, speech, other image models and chat closed
     * if that is what it takes. A resident image model in the requested shape needs no plan.
     */
    @NonNull
    private ImageLoadDecision decideImageLoad(@NonNull MultiBackendTaiRuntime router, @NonNull TaiModelSpec spec,
                                              @NonNull TaiDiffusionPackage.Result pkg, @NonNull TaiImageRequest request)
            throws JSONException {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(manager.appContext);
        List<TaiResidency.Entry> residents = router.residency().snapshot();
        TaiResidency.Entry resident = router.residency().find(TaiResidency.Kind.IMAGE, spec.id);
        if (resident != null && (request.memoryMode < 0 || request.memoryMode == resident.window)) {
            return new ImageLoadDecision(null, resident.window, Collections.<String>emptyList());
        }
        long available = TaiResidency.creditedAvailable(manager.availableMemory(device), residents, TaiResidency.Kind.IMAGE,
            TaiModelSpec.BACKEND_MNN_DIFFUSION);
        TaiImageAdmission.Decision decision = TaiImageAdmission.decide(pkg.peakBytes, request.memoryMode, request.backend,
            device.physicalMemoryBytes, available,
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.IMAGE, TaiModelSpec.BACKEND_MNN_DIFFUSION,
                null, System.currentTimeMillis()),
            mode -> TaiRuntimeHistory.measuredLoadBytes(manager.appContext, spec, device, TaiModelSpec.BACKEND_MNN_DIFFUSION,
                request.backend, mode + 1), manager.gateConditions());
        if (!decision.fits) return new ImageLoadDecision(TaiManager.openAiError(TaiManager.insufficientMemory(spec.displayName, decision.plan)), decision.memoryMode,
            Collections.<String>emptyList());
        return new ImageLoadDecision(null, decision.memoryMode, manager.evict(decision.plan));
    }

    /** Discards the image generation in flight; answers {@code {ok, cancelled}}. Never waits for the engine. */
    @NonNull
    public JSONObject cancelImage() throws JSONException {
        if (manager.shouldDelegateRuntime()) return manager.runtimeRequest(TaiRuntimeIpc.OP_IMAGE_CANCEL, "{}", TaiManager.RUNTIME_STATUS_TIMEOUT_MS);
        MultiBackendTaiRuntime router = manager.speech().ttsRouter();
        boolean cancelled = router != null && router.cancelImage();
        return new JSONObject().put("ok", true).put("cancelled", cancelled);
    }
}
