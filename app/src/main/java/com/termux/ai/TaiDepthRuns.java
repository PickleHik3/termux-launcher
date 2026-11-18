package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.ai.TaiManager.OpenAiStreamSink;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The photo wallpaper's depth image as the rest of the app asks for it: make one from a photo,
 * cancel it. The run itself is {@link DepthMaker} in the runtime process ({@link MultiBackendTaiRuntime#makeDepth});
 * this class is the request, the budget gate in front of it and the stream of stage events back.
 * Nothing from a run stays resident.
 */
public final class TaiDepthRuns {
    /** Refusal codes of this class, besides {@link DepthMaker}'s own; stable, for the caller to map to words. */
    public static final String CODE_MODEL_MISSING = "depth_model_missing";
    public static final String CODE_INSUFFICIENT_MEMORY = "insufficient_memory";
    /** A chat or image model is generating right now; the depth graph never loads beside it. */
    public static final String CODE_RUNTIME_BUSY = "runtime_busy";
    public static final String CODE_RUNTIME_UNAVAILABLE = "runtime_unavailable";
    public static final String CODE_BAD_REQUEST = "bad_request";
    public static final String CODE_INCOMPLETE = "depth_stream_incomplete";

    private final TaiManager manager;

    TaiDepthRuns(@NonNull TaiManager manager) {
        this.manager = manager;
    }

    /** Receives each stage with a 0-100 share of the whole run; called on the job's thread. */
    public interface StageSink {
        void onStage(@NonNull String stage, int percent);
    }

    /** How a run ended; {@code code} is a {@link DepthMaker} {@code CODE_} or one of this class's. */
    public static final class Outcome {
        public final boolean ok;
        public final boolean cancelled;
        @NonNull public final String code;
        @NonNull public final String message;

        Outcome(boolean ok, boolean cancelled, @NonNull String code, @NonNull String message) {
            this.ok = ok;
            this.cancelled = cancelled;
            this.code = code;
            this.message = message;
        }

        @NonNull
        static Outcome failure(@NonNull String code, @NonNull String message) {
            return new Outcome(false, DepthMaker.CODE_CANCELLED.equals(code), code, message);
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            return new JSONObject().put("ok", ok).put("cancelled", cancelled).put("code", code).put("message", message);
        }

        @NonNull
        static Outcome fromJson(@Nullable JSONObject json) {
            if (json == null) return failure(CODE_INCOMPLETE, "The depth run ended without a result.");
            boolean ok = json.optBoolean("ok", false);
            String code = json.optString("code", ok ? DepthMaker.CODE_OK : DepthMaker.CODE_RUN_FAILED);
            return new Outcome(ok, json.optBoolean("cancelled", false) || DepthMaker.CODE_CANCELLED.equals(code),
                code, json.optString("message", ""));
        }
    }

    /** The installed depth model {@code modelId} when it carries the capability and its file is readable, else {@code null}. */
    @Nullable
    TaiModelSpec resolveDepthModel(@Nullable JSONObject request, @Nullable String modelId) {
        if (modelId == null || !TaiDepthModels.isDepthModel(modelId)) return null;
        TaiModelSpec spec = request == null ? manager.resolveModel(modelId) : manager.resolveModel(request, modelId);
        if (spec == null || !spec.capabilities.contains(TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION)) return null;
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) return null;
        File file = new File(spec.localPath);
        return file.isFile() && file.canRead() ? spec : null;
    }

    /**
     * Makes the depth image of {@code photoPath} at {@code outPath} with the installed model
     * {@code modelId}, reporting each stage. Blocking: call off the main thread. Never throws for a
     * missing model, a bad photo or a refusal; those come back as a failed outcome.
     */
    @NonNull
    public Outcome make(@Nullable String photoPath, @Nullable String outPath, @Nullable String modelId,
                        @Nullable StageSink stages) {
        if (photoPath == null || photoPath.trim().isEmpty() || outPath == null || outPath.trim().isEmpty()) {
            return Outcome.failure(CODE_BAD_REQUEST, "A photo and an output path are required.");
        }
        String id = modelId == null ? "" : modelId.trim();
        try {
            TaiModelSpec spec = resolveDepthModel(null, id);
            if (spec == null) return Outcome.failure(CODE_MODEL_MISSING, "The depth model is not installed.");
            JSONObject body = new JSONObject();
            body.put("photoPath", photoPath);
            body.put("outPath", outPath);
            body.put("model", spec.id);
            body.put(TaiManager.INTERNAL_MODEL_SPEC, spec.toJson());
            final JSONObject[] holder = new JSONObject[1];
            OpenAiStreamSink sink = new OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) {
                    if (event.has("stage")) {
                        if (stages != null) stages.onStage(event.optString("stage", ""), event.optInt("percent", 0));
                    } else if (event.has("result")) {
                        holder[0] = event.optJSONObject("result");
                    }
                }

                @Override
                public void onDone() {
                }
            };
            if (!manager.shouldDelegateRuntime()) {
                makeToEvents(body.toString(), sink);
            } else {
                if (manager.runtimeClient == null) {
                    return Outcome.failure(CODE_RUNTIME_UNAVAILABLE, "The on-device runtime is unavailable.");
                }
                try {
                    manager.runtimeClient.stream(TaiRuntimeIpc.OP_DEPTH_MAKE, body.toString(), sink);
                } catch (IOException | RuntimeException e) {
                    // The caller's side broke mid-run: have the runtime stop rather than finish for nobody.
                    cancelQuietly();
                    throw e;
                }
            }
            return Outcome.fromJson(holder[0]);
        } catch (JSONException | IOException | RuntimeException e) {
            return Outcome.failure(CODE_RUNTIME_UNAVAILABLE,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void cancelQuietly() {
        try {
            cancel();
        } catch (JSONException | RuntimeException ignored) {
        }
    }

    /** Stops the run in flight; answers {@code {ok, cancelled}}. Call off the main thread. */
    @NonNull
    public JSONObject cancel() throws JSONException {
        if (manager.shouldDelegateRuntime()) {
            return manager.runtimeRequest(TaiRuntimeIpc.OP_DEPTH_CANCEL, "{}", TaiManager.RUNTIME_STATUS_TIMEOUT_MS);
        }
        MultiBackendTaiRuntime router = manager.speech().ttsRouter();
        boolean cancelled = router != null && router.cancelDepth();
        return new JSONObject().put("ok", true).put("cancelled", cancelled);
    }

    /**
     * The runtime-process half of {@link #make}: the gate, then the run through the router with a
     * {@code {stage, percent}} event per step, then {@code {result}}.
     */
    void makeToEvents(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        Outcome outcome = run(manager.parseBody(body), sink);
        sink.onEvent(new JSONObject().put("result", outcome.toJson()));
        sink.onDone();
    }

    @NonNull
    private Outcome run(@NonNull JSONObject request, @NonNull OpenAiStreamSink sink) throws JSONException {
        String photoPath = request.optString("photoPath", "").trim();
        String outPath = request.optString("outPath", "").trim();
        if (photoPath.isEmpty() || outPath.isEmpty()) {
            return Outcome.failure(CODE_BAD_REQUEST, "A photo and an output path are required.");
        }
        String modelId = request.optString("model", "").trim();
        TaiModelSpec spec = resolveDepthModel(request, modelId);
        if (spec == null) return Outcome.failure(CODE_MODEL_MISSING, "The depth model is not installed.");
        MultiBackendTaiRuntime router = manager.speech().ttsRouter();
        if (router == null) {
            return Outcome.failure(CODE_RUNTIME_UNAVAILABLE, "Depth needs the multi-backend runtime.");
        }
        if (router.isDepthActive()) {
            return Outcome.failure(MultiBackendTaiRuntime.CODE_DEPTH_BUSY, "A depth image is already being made.");
        }
        if (busyReason(router.residency().snapshot(), router.isImageActive()) != null) {
            return Outcome.failure(CODE_RUNTIME_BUSY, "A model is busy answering.");
        }
        Outcome refusal = decideLoad(router, spec);
        if (refusal != null) return refusal;
        boolean distance = TaiDepthModels.isDistance(spec.id, spec.architecture);
        final IOException[] failure = new IOException[1];
        DepthMaker.Result result = router.makeDepth(spec, new File(photoPath), new File(spec.localPath), distance,
            new File(outPath), (stage, percent) -> {
                if (failure[0] != null) return;
                try {
                    sink.onEvent(new JSONObject().put("stage", stage).put("percent", percent));
                } catch (IOException | JSONException e) {
                    // The client went away: stop rather than finish for nobody.
                    failure[0] = e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
                    router.cancelDepth();
                }
            });
        return new Outcome(result.ok, result.cancelled, result.code, result.message);
    }

    /**
     * Why a depth graph must not load now, or {@code null}: a chat model is generating, or an image
     * is being made. One vision graph at a time, never beside a busy model. Idle residents do not
     * block it. Pure: {@code residents} is a registry snapshot.
     */
    @Nullable
    static String busyReason(@NonNull List<TaiResidency.Entry> residents, boolean imageActive) {
        if (imageActive) return "image";
        for (TaiResidency.Entry entry : residents) {
            if (entry.busy && (entry.kind == TaiResidency.Kind.CHAT || entry.kind == TaiResidency.Kind.IMAGE)) {
                return entry.kind.name().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return null;
    }

    /**
     * The budget for one depth graph: its measured load when on record, else its file times
     * {@link TaiResidency#VISION_FACTOR_TENTHS}. Idle embeddings are closed if that is what it
     * takes; nothing else is. {@code null} means go ahead.
     */
    @Nullable
    private Outcome decideLoad(@NonNull MultiBackendTaiRuntime router, @NonNull TaiModelSpec spec) throws JSONException {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(manager.appContext);
        List<TaiResidency.Entry> residents = router.residency().snapshot();
        long available = TaiResidency.creditedAvailable(manager.availableMemory(device), residents,
            TaiResidency.Kind.VISION, TaiModelSpec.BACKEND_LITERT_LM);
        long measured = TaiRuntimeHistory.measuredLoadBytes(manager.appContext, spec, device,
            TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0);
        TaiLoadBudget.Estimate estimate = measured > 0L ? TaiLoadBudget.Estimate.measured(measured)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.visionEstimateBytes(spec), 0L);
        List<TaiResidency.Entry> candidates = new ArrayList<>();
        for (TaiResidency.Entry entry : TaiResidency.evictionCandidates(residents, TaiResidency.Kind.VISION,
                TaiModelSpec.BACKEND_LITERT_LM)) {
            if (entry.kind == TaiResidency.Kind.EMBEDDING) candidates.add(entry);
        }
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            candidates, manager.gateConditions());
        if (!plan.fits) {
            return Outcome.failure(CODE_INSUFFICIENT_MEMORY, "Not enough free memory to make the depth image.");
        }
        manager.evict(plan);
        return null;
    }
}
