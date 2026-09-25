package com.termux.ai;

import android.content.Context;
import android.net.Uri;
import android.os.Process;
import java.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.BenchmarkInfo;
import com.google.ai.edge.litertlm.BenchmarkKt;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.OpenApiTool;
import com.google.ai.edge.litertlm.ToolCall;
import com.google.ai.edge.litertlm.ToolKt;
import com.google.ai.edge.litertlm.ToolProvider;
import com.termux.shared.android.ProcessUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class TaiManager {
    private static TaiManager instance;
    private static final int MAX_MEDIA_BYTES = 25 * 1024 * 1024;
    private static final String INTERNAL_MODEL_SPEC = "_taiModelSpec";
    private static final String INTERNAL_RUNTIME_OPTIONS = "_taiRuntimeOptions";
    /** The STT idle-unload setting, carried to the runtime process with every speech request. */
    private static final String INTERNAL_STT_IDLE_MINUTES = "_taiSttIdleUnloadMinutes";
    /** Where the app process puts audio for the runtime process; files here are deleted once transcribed. */
    public static final String STT_IPC_DIR = "tai-ipc";

    private final Context appContext;
    private final TaiSettings settings;
    private final TaiModelRegistry registry;
    private final TaiModelStore modelStore;
    private final TaiModelDownloader modelDownloader;
    @Nullable private TaiRuntime runtime;
    @Nullable private final TaiRuntimeServiceClient runtimeClient;
    private final boolean runtimeProcess;
    /** Total device RAM, detected once; {@code -1} until first use, {@code 0} when unknown. */
    private volatile long deviceMemoryBytes = -1L;
    /**
     * In the runtime process: how long an idle Whisper model is kept, as the app process's settings
     * last said ({@link TaiSettings#getSttIdleUnloadMinutes}); the plan's default until told.
     */
    private volatile long sttIdleLimitMs = TaiPressureWatch.STT_IDLE_MS;

    public interface OpenAiStreamSink {
        void onEvent(@NonNull JSONObject event) throws IOException;
        void onDone() throws IOException;
    }

    private TaiManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        runtimeProcess = isTaiRuntimeProcess(appContext);
        if (!runtimeProcess) {
            TaiRemoteCatalog.loadCached(appContext);
            Thread catalogRefresh = new Thread(() -> TaiRemoteCatalog.refresh(appContext), "tai-catalog-refresh");
            catalogRefresh.setDaemon(true);
            catalogRefresh.start();
        }
        settings = new TaiSettings(appContext);
        registry = new TaiModelRegistry();
        modelStore = new TaiModelStore(appContext);
        modelDownloader = new TaiModelDownloader(appContext, modelStore);
        runtime = runtimeProcess ? new MultiBackendTaiRuntime(appContext) : null;
        runtimeClient = runtimeProcess ? null : new TaiRuntimeServiceClient(appContext);
    }

    @NonNull
    public static synchronized TaiManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new TaiManager(context);
        }
        return instance;
    }

    @NonNull
    static synchronized TaiManager getRuntimeProcessInstance(@NonNull Context context) {
        return getInstance(context);
    }

    /**
     * Registers an extra path root that {@code importModel} accepts, for tests only: unit tests run
     * on a JVM where {@link com.termux.shared.termux.TermuxConstants#TERMUX_HOME_DIR_PATH} is not a
     * real, writable directory, so fixtures need somewhere else to put their fake model files.
     */
    public static void setImportPathAllowedRootForTesting(@Nullable String root) {
        TaiMediaAccess.setExtraAllowedRootForTesting(root);
    }

    private boolean shouldDelegateRuntime() {
        return !runtimeProcess && runtime == null;
    }

    private boolean hasInjectedRuntimeOverride() {
        return !runtimeProcess && runtime != null;
    }

    /** Status queries should return quickly; cap them so a busy/hung runtime can't stall callers. */
    private static final long RUNTIME_STATUS_TIMEOUT_MS = 8_000L;

    /** {@link TaiRuntimeServiceClient}'s own flat default, kept here for callers that don't pass one. */
    static final long DEFAULT_TRANSCRIBE_TIMEOUT_MS = 120_000L;
    static final long DEFAULT_CHAT_TIMEOUT_MS = 120_000L;

    @NonNull
    private JSONObject runtimeRequest(@NonNull String operation, @Nullable String body) throws JSONException {
        if (runtimeClient == null) return error(500, "runtime_client_unavailable", "TAI runtime service client is unavailable.");
        return runtimeClient.request(operation, body == null ? "{}" : body);
    }

    @NonNull
    private JSONObject runtimeRequest(@NonNull String operation, @Nullable String body, long timeoutMs) throws JSONException {
        if (runtimeClient == null) return error(500, "runtime_client_unavailable", "TAI runtime service client is unavailable.");
        return runtimeClient.request(operation, body == null ? "{}" : body, timeoutMs);
    }

    /** Supplies the isolated runtime with the model definition resolved by the authoritative app process. */
    @NonNull
    private String delegatedRuntimeBody(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        request.remove(INTERNAL_MODEL_SPEC);
        request.remove(INTERNAL_RUNTIME_OPTIONS);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(modelId);
        if (spec != null) {
            request.put("model", spec.id);
            request.put(INTERNAL_MODEL_SPEC, spec.toJson());
            request.put(INTERNAL_RUNTIME_OPTIONS, settings.getRuntimeOptions(spec).toJson());
        }
        return request.toString();
    }

    @NonNull
    private TaiRuntime localRuntime() {
        if (runtime == null) throw new IllegalStateException("TAI native runtime is only available in " + TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
        return runtime;
    }

    private static boolean isTaiRuntimeProcess(@NonNull Context context) {
        String processName = ProcessUtils.getAppProcessNameForPid(context, Process.myPid());
        return processName != null && processName.endsWith(TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
    }

    @NonNull
    public JSONObject status() throws JSONException {
        TaiRuntimeState state = getRuntimeState();
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("name", "TAI");
        data.put("displayName", "Termux AI");
        data.put("runtime", state.toJson());
        data.put("settings", settings.toJson());
        data.put("appProcessRuntime", false);
        data.put("runtimeProcess", TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
        data.put("modelsBundledInApk", false);
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("limitations", currentLimitations());
        appendCrashMarker(data);
        appendDeviceCompatibility(data, state);
        JSONArray endpoints = new JSONArray();
        endpoints.put("/v1/models");
        endpoints.put("/v1/chat/completions");
        endpoints.put("/v1/responses");
        endpoints.put("/v1/completions");
        endpoints.put("/v1/embeddings");
        endpoints.put("/v1/audio/transcriptions");
        data.put("openAiCompatibleEndpoints", endpoints);
        data.put("ollamaCompatibleEndpoints", new JSONArray()
            .put("/api/version").put("/api/tags").put("/api/show").put("/api/chat")
            .put("/api/generate").put("/api/ps").put("/api/embed"));
        return data;
    }

    @NonNull
    public JSONObject runtimeStatus() throws JSONException {
        // One round trip to the runtime process answers both the state and the resident table.
        JSONObject remote = shouldDelegateRuntime() ? remoteRuntimeStatus() : null;
        TaiRuntimeState state = shouldDelegateRuntime() ? remoteRuntimeState(remote) : localRuntime().getState();
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("runtime", state.toJson());
        data.put("residents", residentsJson(remote));
        data.put("settings", settings.toJson());
        data.put("appProcessRuntime", false);
        data.put("runtimeProcess", TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
        data.put("backendPolicy", "TAI loads native LiteRT-LM/MNN backends in an isolated Android process after preflight checks.");
        data.put("runtimeHistory", TaiRuntimeHistory.summary(appContext));
        appendCrashMarker(data);
        appendDeviceCompatibility(data, state);
        return data;
    }

    /** The resident table: the runtime process's own, or the one its status reply carried. */
    @NonNull
    private JSONArray residentsJson(@Nullable JSONObject remoteStatus) throws JSONException {
        if (shouldDelegateRuntime()) {
            JSONArray residents = remoteStatus == null ? null : remoteStatus.optJSONArray("residents");
            return residents == null ? new JSONArray() : residents;
        }
        if (!(runtime instanceof MultiBackendTaiRuntime)) return new JSONArray();
        return ((MultiBackendTaiRuntime) runtime).residency().toJson();
    }

    @NonNull
    public JSONObject models() throws JSONException {
        JSONObject data = registry.toJson(settings, modelStore.getUserModels());
        data.put("storageDirectory", modelStore.getModelsDirectory().getAbsolutePath());
        data.put("downloads", modelStore.getDownloads());
        data.put("catalog", catalogJson());
        TaiRuntimeState state = getRuntimeState();
        data.put("runtime", state.toJson());
        appendDeviceCompatibility(data, state);
        return data;
    }

    @NonNull
    public JSONObject importModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String path = request.optString("path", "").trim();
        if (path.isEmpty()) return error(400, "bad_request", "Missing model path");
        String resolvedPath;
        try {
            // Import runs over the same network-reachable API as chat, so a path here is a read
            // primitive too; scope it the same way TaiMediaAccess scopes chat media references.
            resolvedPath = TaiMediaAccess.resolveLocalPath(path);
        } catch (JSONException e) {
            String message = e.getMessage() == null ? "Media path is not readable through this endpoint" : e.getMessage();
            int colon = message.indexOf(':');
            String code = colon > 0 ? message.substring(0, colon) : "media_access_denied";
            String detail = colon > 0 ? message.substring(colon + 1) : message;
            JSONObject denied = error(403, code, detail);
            denied.put("path", path);
            return denied;
        }
        File modelFile = new File(resolvedPath);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            JSONObject error = error(404, "model_file_not_readable", "Model file does not exist or is not readable by the app process");
            error.put("path", path);
            return error;
        }

        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", modelFile.getName())));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        TaiModelSpec baseSpec;
        try {
            baseSpec = new TaiModelSpec(
                modelId,
                request.optString("displayName", modelId),
                request.optString("roleHint", "Imported local model"),
                "imported",
                modelFile.getAbsolutePath(),
                request.optString("license", "User-provided model; license accepted externally"),
                modelFile.length(),
                capabilitiesFromRequest(request, modelId, modelFile.getName()),
                false
            );
        } catch (IllegalArgumentException e) {
            return error(400, "unsupported_model_format",
                "TAI can import LiteRT-LM packages and MNN config packages only. GGUF/raw weights require a backend this APK does not include.");
        }
        TaiModelProfile runtimeProfile = TaiModelProfile.fromRequest(request, TaiModelProfile.forModel(baseSpec));
        TaiModelSpec spec = new TaiModelSpec(
            baseSpec.id,
            baseSpec.displayName,
            baseSpec.roleHint,
            baseSpec.source,
            baseSpec.localPath,
            baseSpec.license,
            baseSpec.sizeBytes,
            baseSpec.sourceCapabilities,
            false,
            runtimeProfile,
            baseSpec.backend,
            baseSpec.format,
            baseSpec.architecture,
            baseSpec.quantization,
            baseSpec.endpointContextWindow,
            baseSpec.sourceContextWindow,
            baseSpec.defaultMaxOutputTokens,
            baseSpec.recommendedRamGb,
            baseSpec.sha256,
            baseSpec.endpointCapabilities,
            baseSpec.toolMode
        );
        modelStore.upsertUserModel(spec);

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("imported", true);
        data.put("model", spec.toJson());
        data.put("requiresUserApprovedPath", true);
        data.put("copiedIntoAppPrivateStorage", false);
        data.put("message", "Model path registered. Load it with TAI to run through the isolated Android LiteRT-LM runtime when preflight passes.");
        return data;
    }

    @NonNull
    public JSONObject importModelDocument(@NonNull Uri uri, @Nullable String modelId) throws JSONException {
        return new TaiModelImporter(appContext, modelStore).importDocument(uri, modelId);
    }

    @NonNull
    public TaiModelImporter.DocumentMetadata modelDocumentMetadata(@NonNull Uri uri) {
        return new TaiModelImporter(appContext, modelStore).readMetadata(uri);
    }

    @NonNull
    public JSONObject downloadModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        boolean acceptedTerms = request.optBoolean("acceptedTerms", false);
        if (!acceptedTerms) {
            JSONObject error = error(403, "terms_not_accepted", "Model downloads require acceptedTerms=true after reviewing provider license/terms");
            error.put("downloadsRequireExplicitUserAction", true);
            error.put("huggingFaceTokenBundled", false);
            return error;
        }
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        String url = request.optString("url", "").trim();
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (url.isEmpty()) return error(400, "bad_request", "Missing download URL");
        String token = request.optString("huggingFaceToken", settings.getHuggingFaceToken());
        JSONObject selectedArtifact = null;
        // Accept a bare repo URL: auto-detect the backend and resolve to the package entry file
        // (config.json / .litertlm) so the user never picks a backend or hunts the HF file list.
        if (TaiHuggingFace.parse(url) != null) {
            TaiModelDownloader.HfResolve resolved = modelDownloader.resolveHuggingFaceEntry(url, token);
            if (resolved.authRequired) {
                JSONObject gated = error(403, "gated_model_requires_auth",
                    "This Hugging Face repo is gated or private. Save your Hugging Face access token "
                    + "(after accepting the model's terms on huggingface.co) and try again.");
                gated.put("huggingFaceTokenBundled", false);
                return gated;
            }
            if (resolved.candidates.length() > 1 || request.optBoolean("previewOnly", false)
                && resolved.candidates.length() > 0) {
                JSONObject choices = error(409, "artifact_selection_required", "Choose a model file to download.");
                choices.put("candidates", resolved.candidates);
                return choices;
            }
            if (resolved.url.isEmpty()) {
                return error(400, "hf_resolve_failed", "Could not find a downloadable model file in that Hugging Face repo. "
                    + "Paste the repo URL (e.g. https://huggingface.co/taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN) or a direct .../resolve/main/<file> URL.");
            }
            url = resolved.url;
            selectedArtifact = resolved.candidates.optJSONObject(0);
        }
        if (selectedArtifact != null) {
            String required = selectedArtifact.optString("minimumRuntimeVersion", "");
            if (!required.isEmpty() && !TaiArtifactCompatibility.versionAtLeast(com.termux.BuildConfig.LITERT_LM_VERSION, required))
                return error(400, "runtime_update_required", "This model file needs LiteRT-LM " + required
                    + " or later. This app includes " + com.termux.BuildConfig.LITERT_LM_VERSION + ".");
        }
        LinkedHashSet<String> capabilities = capabilitiesFromRequest(request, modelId, url);
        TaiModelProfile runtimeProfile = null;
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(TaiModelSpec.inferBackend(url))) {
            TaiModelSpec importSpec = new TaiModelSpec(modelId, request.optString("displayName", modelId),
                "Downloaded model", "downloaded", url,
                request.optString("license", "User accepted provider terms externally"), 0L,
                capabilities, false);
            runtimeProfile = TaiModelProfile.fromRequest(request, TaiModelProfile.forModel(importSpec));
        }
        JSONObject data = modelDownloader.startDownload(
            modelId,
            url,
            request.optString("displayName", modelId),
            request.optString("license", "User accepted provider terms externally"),
            capabilities,
            token,
            runtimeProfile,
            selectedArtifact
        );
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("huggingFaceTokenBundled", false);
        return data;
    }

    @NonNull
    public JSONObject downloadCatalogModel(@NonNull String modelId) throws JSONException {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return error(404, "model_not_found", "Unknown catalog model: " + modelId);
        if (!entry.downloadAvailable) {
            JSONObject error = error(409, "catalog_download_unavailable", entry.unavailableReason);
            error.put("providerPageUrl", entry.providerPageUrl);
            error.put("downloadAvailable", false);
            error.put("unavailableReason", entry.unavailableReason);
            return error;
        }
        if (entry.gated) {
            String token = settings.getHuggingFaceToken();
            if (token.trim().isEmpty()) {
                JSONObject error = error(403, "gated_model_requires_auth", "This model is gated on Hugging Face. Save a Hugging Face token after accepting the model terms.");
                error.put("providerPageUrl", entry.providerPageUrl);
                error.put("downloadUrl", entry.downloadUrl);
                error.put("huggingFaceTokenBundled", false);
                return error;
            }
        }
        return modelDownloader.startCatalogDownload(entry, settings.getHuggingFaceToken());
    }

    /** Downloads a speech-to-text catalog entry with the given window's graph (5 or 10 seconds).
     *  An unknown window (or a non-speech/window-less entry) downloads the entry's default artifact. */
    @NonNull
    public JSONObject downloadSpeechModel(@NonNull String modelId, int windowSeconds) throws JSONException {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return error(404, "model_not_found", "Unknown catalog model: " + modelId);
        if (!entry.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) {
            return error(400, "not_a_speech_model", "Model " + modelId + " is not a speech-to-text model.");
        }
        return modelDownloader.startCatalogDownload(entry.withWindow(windowSeconds), settings.getHuggingFaceToken());
    }

    @NonNull
    public JSONObject deleteModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        TaiRuntimeState state = getRuntimeState();
        boolean activeModelLoaded = state.loadedModelId != null && state.loadedModelId.equals(modelId);
        TaiModelStore.DeleteResult deleteResult = modelStore.deleteUserModel(modelId,
            activeModelLoaded, request.optBoolean("confirm", false));
        if (!deleteResult.ok) return error(409, deleteResult.errorCode, deleteResult.message);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("deleted", deleteResult.deleted);
        data.put("modelId", modelId);
        return data;
    }

    @NonNull
    public JSONObject downloads() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("downloads", modelStore.getDownloads());
        return data;
    }

    @NonNull
    public JSONObject cancelDownload(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        TaiModelDownloadService.requestCancel(modelId);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("modelId", modelId);
        data.put("cancellationRequested", true);
        return data;
    }

    @NonNull
    public JSONObject loadModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        if (spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
                && !spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            return error(400, "embedding_model_not_loadable",
                "Model " + modelId + " is an embedding model. It is served on demand via /v1/embeddings and "
                    + "/api/embed and does not need to be loaded into the generation runtime.");
        }
        if (spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) {
            // Speech models never enter the chat runtime: WhisperSttRuntime loads them on demand
            // behind /v1/audio/transcriptions and tai transcribe (or ahead of time via sttWarm).
            return error(400, "speech_model_not_loadable",
                "Model " + modelId + " is a speech-to-text model. It is served on demand via /v1/audio/transcriptions "
                    + "and tai transcribe and does not load into the chat runtime.");
        }
        String requestedBackend = request.optString("backend", "").trim();
        if (!requestedBackend.isEmpty() && !requestedBackend.equalsIgnoreCase(spec.backend)) {
            return error(409, "backend_mismatch", "Model " + modelId + " requires backend " + spec.backend + ".");
        }
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_LOAD_MODEL, delegatedRuntimeBody(body));
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        if (hasInjectedRuntimeOverride()) {
            JSONObject result = localRuntime().load(spec, options);
            return result;
        }
        TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options, false);
        if (preflight.blocked) {
            // Free memory running short is a moment, not a verdict on the accelerator: recording it
            // would demote the GPU for good over one crowded afternoon.
            if (!preflight.errorCode.startsWith("low_available_memory")) {
                TaiRuntimeHistory.recordFailure(appContext, spec, preflight.device, spec.backend,
                    preflight.effectiveAccelerator, preflight.message);
            }
            return preflight.blockingError(preflightStatusCode(preflight));
        }
        LoadDecision decision = decideLoad(spec, options, preflight);
        if (decision.refusal != null) return decision.refusal;
        JSONObject result = localRuntime().load(spec, decision.options);
        result.put("preflight", preflight.toJson());
        decision.describe(result);
        recordRuntimeResult(spec, preflight, result);
        return result;
    }

    @NonNull
    public JSONObject unloadModel() throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_UNLOAD_MODEL, "{}");
        return localRuntime().unload();
    }

    @NonNull
    public JSONObject keepWarmRuntime(@NonNull String body) throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_KEEP_WARM, delegatedRuntimeBody(body));
        JSONObject request = parseBody(body);
        TaiRuntimeState state = localRuntime().getState();
        String fallbackModel = state.loadedModelId != null ? state.loadedModelId : settings.getDefaultAssistantModel();
        String modelId = requestedModelId(request, fallbackModel);
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        int minutes = request.optInt("minutes", request.optInt("keepWarmMinutes", 0));
        if (minutes <= 0) minutes = settings.getIdleUnloadMinutes() > 0 ? settings.getIdleUnloadMinutes() : 30;
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options, false);
        if (preflight.blocked) return preflight.blockingError(preflightStatusCode(preflight));
        TaiRuntimeOptions warmOptions = optionsForPreflight(spec, options, preflight);
        LoadDecision decision = null;
        if (!localRuntime().isModelLoaded(spec.id)) {
            decision = decideLoad(spec, options, preflight);
            if (decision.refusal != null) return decision.refusal;
            warmOptions = decision.options;
        }
        JSONObject result = localRuntime().keepWarm(spec, warmOptions, minutes);
        result.put("preflight", preflight.toJson());
        if (decision != null) decision.describe(result);
        recordRuntimeResult(spec, preflight, result);
        return result;
    }

    @NonNull
    public JSONObject cancelRuntime() throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_CANCEL, "{}");
        return localRuntime().cancel();
    }

    /** Runs can each spend tens of seconds; the whole {@code tai benchmark} call may take minutes. */
    private static final long BENCHMARK_TIMEOUT_MS = 20 * 60_000L;
    private static final String BENCHMARK_PROMPT = "How are you";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /**
     * Runs LiteRT-LM's own {@code com.google.ai.edge.litertlm.benchmark()} in the runtime process, so
     * TAI's numbers are comparable 1:1 with Google AI Edge Gallery's Benchmark screen: same API, same
     * default prefill/decode token counts, its own throwaway engine and cache directory. That engine
     * is independent of the one {@link MultiBackendTaiRuntime} tracks, so it is refused while a chat
     * model is loaded or loading rather than silently doubling the memory a resident model already
     * holds — the caller unloads first, or passes {@code force} to accept that risk instead of the
     * memory-budget refusal below.
     */
    @NonNull
    public JSONObject benchmark(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            return error(400, "capability_not_supported", "Model " + modelId + " is not a chat model.");
        }
        if (!TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend)) {
            return error(400, "backend_not_supported",
                "tai benchmark calls com.google.ai.edge.litertlm.benchmark(), which is LiteRT-LM only.");
        }
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(request);
        if (params == null) return error(400, "bad_request", "accelerator must be gpu or cpu");
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_BENCHMARK, delegatedRuntimeBody(body), BENCHMARK_TIMEOUT_MS);

        TaiRuntimeState state = localRuntime().getState();
        if (state.loaded || state.activeGeneration || "loading".equals(state.state)) {
            JSONObject busy = error(409, "model_loaded", "Unload the loaded model first: tai benchmark runs its "
                + "own LiteRT-LM engine and cannot share memory with a resident model.");
            if (state.loadedModelId != null) busy.put("loadedModelId", state.loadedModelId);
            return busy;
        }

        File modelFile = spec.localPath == null ? null : new File(spec.localPath);
        if (modelFile == null || !modelFile.isFile() || !modelFile.canRead()) {
            return error(404, "model_file_not_readable", "Model file does not exist or is not readable: " + modelId);
        }

        if (!params.force) {
            TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
            long fileBytes = TaiResidency.fileBytes(spec);
            TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(spec.backend, fileBytes, false,
                device.physicalMemoryBytes, device.availableMemoryBytes,
                Collections.singletonList(params.accelerator), params.prefillTokens + params.decodeTokens, null, 0));
            if (!plan.fits) {
                JSONObject refusal = insufficientMemory(spec.displayName, plan);
                refusal.put("hint", "Pass force=true to skip this budget check. Google AI Edge Gallery's Benchmark "
                    + "screen has no memory budget, and force is how tai benchmark stays comparable to it.");
                return refusal;
            }
        }

        File cacheDir = new File(appContext.getCacheDir(), "tai-benchmark-" + System.nanoTime());
        if (!cacheDir.mkdirs()) {
            return error(500, "benchmark_cache_dir_failed",
                "Could not create a benchmark cache directory under " + appContext.getCacheDir());
        }
        try {
            return runBenchmark(spec, modelFile, params, cacheDir);
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    @NonNull
    private JSONObject runBenchmark(@NonNull TaiModelSpec spec, @NonNull File modelFile,
                                     @NonNull TaiBenchmarkParams params, @NonNull File cacheDir) throws JSONException {
        Backend backend = TaiBenchmarkParams.ACCELERATOR_CPU.equals(params.accelerator)
            ? new Backend.CPU() : new Backend.GPU();
        JSONArray runsJson = new JSONArray();
        ValueSeries initMsSeries = new ValueSeries();
        ValueSeries ttftMsSeries = new ValueSeries();
        ValueSeries prefillTpsSeries = new ValueSeries();
        ValueSeries decodeTpsSeries = new ValueSeries();
        Double firstInitMs = null;
        for (int i = 0; i < params.runs; i++) {
            TaiLoadMeter sampler = TaiLoadMeter.start(appContext);
            BenchmarkInfo info;
            try {
                info = BenchmarkKt.benchmark(modelFile.getAbsolutePath(), backend, params.prefillTokens,
                    params.decodeTokens, cacheDir.getAbsolutePath(), BENCHMARK_PROMPT);
            } catch (Throwable t) {
                sampler.stop();
                String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                JSONObject failure = error(500, "benchmark_failed", "Benchmark run " + (i + 1) + " failed: " + message);
                failure.put("run", i + 1);
                if (runsJson.length() > 0) failure.put("runs", runsJson);
                return failure;
            }
            sampler.stop();
            double initMs = info.getInitTimeInSecond() * 1000.0;
            double ttftMs = info.getTimeToFirstTokenInSecond() * 1000.0;
            double prefillTps = info.getLastPrefillTokensPerSecond();
            double decodeTps = info.getLastDecodeTokensPerSecond();
            // The first init reads the package off disk cold; later ones are warm. Averaging them
            // together would hide exactly the number Gallery's screen calls out separately.
            if (i == 0) firstInitMs = initMs; else initMsSeries.add(initMs);
            ttftMsSeries.add(ttftMs);
            prefillTpsSeries.add(prefillTps);
            decodeTpsSeries.add(decodeTps);
            JSONObject run = new JSONObject();
            run.put("initMs", initMs);
            run.put("ttftMs", ttftMs);
            run.put("prefillTps", prefillTps);
            run.put("decodeTps", decodeTps);
            run.put("prefillTokenCount", info.getLastPrefillTokenCount());
            run.put("decodeTokenCount", info.getLastDecodeTokenCount());
            run.put("availBeforeMb", sampler.beforeBytes() / BYTES_PER_MB);
            run.put("availMinMb", sampler.minBytes() / BYTES_PER_MB);
            runsJson.put(run);
        }
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("model", spec.id);
        data.put("accelerator", params.accelerator);
        data.put("prefillTokens", params.prefillTokens);
        data.put("decodeTokens", params.decodeTokens);
        data.put("runs", runsJson);
        JSONObject initSummary = new JSONObject();
        if (firstInitMs != null) initSummary.put("firstMs", firstInitMs);
        if (!initMsSeries.isEmpty()) {
            initSummary.put("laterAvgMs", initMsSeries.avg());
            initSummary.put("laterMinMs", initMsSeries.min());
            initSummary.put("laterMaxMs", initMsSeries.max());
        }
        JSONObject summary = new JSONObject();
        summary.put("initMs", initSummary);
        summary.put("ttftMs", ttftMsSeries.toJson());
        summary.put("prefillTps", prefillTpsSeries.toJson());
        summary.put("decodeTps", decodeTpsSeries.toJson());
        data.put("summary", summary);
        data.put("litertLmVersion", com.termux.BuildConfig.LITERT_LM_VERSION);
        return data;
    }

    /** avg/min/max/count over a run of values, the shape Gallery's own ValueSeries reports. */
    private static final class ValueSeries {
        private double sum;
        private double minValue = Double.NaN;
        private double maxValue = Double.NaN;
        private int count;

        void add(double value) {
            minValue = count == 0 ? value : Math.min(minValue, value);
            maxValue = count == 0 ? value : Math.max(maxValue, value);
            sum += value;
            count++;
        }

        boolean isEmpty() {
            return count == 0;
        }

        double avg() {
            return count == 0 ? 0.0 : sum / count;
        }

        double min() {
            return count == 0 ? 0.0 : minValue;
        }

        double max() {
            return count == 0 ? 0.0 : maxValue;
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("avg", avg());
            json.put("min", min());
            json.put("max", max());
            json.put("count", count);
            return json;
        }
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /**
     * Returns whether the model is known and has a readable local path registered.
     * Does not load the model.
     */
    public boolean isModelAvailable(@NonNull String modelId) {
        return modelStore.getInstalledUserModels().containsKey(TaiSettings.migrateBuiltInModelId(modelId));
    }

    @NonNull
    public TaiRuntimeState getRuntimeState() {
        if (!shouldDelegateRuntime()) return localRuntime().getState();
        return remoteRuntimeState(remoteRuntimeStatus());
    }

    /** The runtime process's full status reply, or {@code null} when it could not be reached. */
    @Nullable
    private JSONObject remoteRuntimeStatus() {
        try {
            return runtimeRequest(TaiRuntimeIpc.OP_RUNTIME_STATUS, "{}", RUNTIME_STATUS_TIMEOUT_MS);
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    private static TaiRuntimeState remoteRuntimeState(@Nullable JSONObject status) {
        if (status == null) return TaiRuntimeState.fromJson(null);
        JSONObject runtimeJson = status.optJSONObject("runtime");
        if (runtimeJson == null) runtimeJson = status.optJSONObject("state");
        return TaiRuntimeState.fromJson(runtimeJson);
    }

    @NonNull
    public JSONObject openAiChatCompletions(@NonNull String body) throws JSONException {
        return openAiChatCompletions(body, DEFAULT_CHAT_TIMEOUT_MS);
    }

    /**
     * As {@link #openAiChatCompletions(String)}, with the IPC deadline the caller wants instead of
     * the flat default — a voice-input rewrite gives itself a few seconds, so a slow answer costs
     * that one phrase its polish instead of stalling the session for two minutes.
     */
    @NonNull
    public JSONObject openAiChatCompletions(@NonNull String body, long timeoutMs) throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_OPENAI_CHAT, delegatedRuntimeBody(body), timeoutMs);
        JSONObject request = parseBody(body);
        JSONArray messages = request.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            return openAiError(error(400, "bad_request", "Missing messages"));
        }
        List<String> stopSequences;
        try {
            stopSequences = OpenAiStopSequences.fromRequest(request);
        } catch (JSONException e) {
            return openAiError(error(400, "invalid_stop", e.getMessage()));
        }

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return openAiError(error(404, "model_not_found", "Unknown TAI model: " + modelId));
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            return openAiError(generationCapabilityError(spec));
        }
        omitAutomaticToolsForCompatibility(request, spec);
        if (!modelSupportsRequestedTools(request, spec)) {
            return openAiError(error(400, "capability_not_supported",
                "Model " + spec.id + " does not support tool use through this endpoint."));
        }
        JSONObject audioOutputError = unsupportedAudioOutputRequest(request);
        if (audioOutputError != null) return openAiError(audioOutputError);
        OpenAiChatRequest chatRequest;
        try {
            chatRequest = openAiChatRequest(request, messages, spec);
        } catch (JSONException e) {
            return openAiError(chatRequestError(e));
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        JSONObject loadError = ensureModelLoadedForGeneration(spec, options);
        if (loadError != null) return openAiError(loadError);

        JSONObject chat = localRuntime().chat(modelId, chatRequest.request, options);
        if (!chat.optBoolean("ok", false)) {
            return openAiError(chat);
        }

        JSONObject response = new JSONObject();
        response.put("id", "tai-" + System.currentTimeMillis());
        response.put("object", "chat.completion");
        response.put("model", chat.optString("model", settings.getDefaultAssistantModel()));
        response.put("created", System.currentTimeMillis() / 1000L);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        String rawResponse = chat.optString("response", "");
        populateOpenAiChatChoice(choice, chat, stopSequences);
        choices.put(choice);
        response.put("choices", choices);
        response.put("usage", openAiUsage(chat, messages.toString(), rawResponse));
        response.put("tai", chat);
        return response;
    }

    static void populateOpenAiChatChoice(
        @NonNull JSONObject choice,
        @NonNull JSONObject chat,
        @NonNull List<String> stopSequences
    ) throws JSONException {
        JSONArray toolCalls = chat.optJSONArray("toolCalls");
        OpenAiStopSequences.Match stopMatch = OpenAiStopSequences.truncate(
            chat.optString("response", ""), stopSequences);
        boolean hasToolCalls = !stopMatch.stopped && toolCalls != null && toolCalls.length() > 0;
        choice.put("finish_reason", stopMatch.stopped ? "stop" : chat.optString("finishReason",
            hasToolCalls ? "tool_calls" : "stop"));
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", hasToolCalls && stopMatch.text.isEmpty()
            ? JSONObject.NULL : stopMatch.text);
        String reasoning = chat.optString("reasoning_content", "");
        if (!reasoning.isEmpty()) message.put("reasoning_content", reasoning);
        if (hasToolCalls) message.put("tool_calls", toolCalls);
        choice.put("message", message);
    }

    @NonNull
    public JSONObject openAiCompletions(@NonNull String body) throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_OPENAI_COMPLETION, delegatedRuntimeBody(body));
        JSONObject request = parseBody(body);
        String prompt = promptFromCompletionRequest(request);
        if (prompt.trim().isEmpty()) return openAiError(error(400, "bad_request", "Missing prompt"));
        List<String> stopSequences;
        try {
            stopSequences = OpenAiStopSequences.fromRequest(request);
        } catch (JSONException e) {
            return openAiError(error(400, "invalid_stop", e.getMessage()));
        }

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return openAiError(error(404, "model_not_found", "Unknown TAI model: " + modelId));
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            return openAiError(generationCapabilityError(spec));
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        JSONObject loadError = ensureModelLoadedForGeneration(spec, options);
        if (loadError != null) return openAiError(loadError);

        JSONObject completion = localRuntime().complete(modelId, prompt, options);
        if (!completion.optBoolean("ok", false)) {
            return openAiError(completion);
        }

        JSONObject response = new JSONObject();
        response.put("id", "tai-cmpl-" + System.currentTimeMillis());
        response.put("object", "text_completion");
        response.put("model", completion.optString("model", modelId));
        response.put("created", System.currentTimeMillis() / 1000L);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        String rawResponse = completion.optString("response", "");
        OpenAiStopSequences.Match stopMatch = OpenAiStopSequences.truncate(rawResponse, stopSequences);
        choice.put("text", stopMatch.text);
        choice.put("index", 0);
        choice.put("finish_reason", stopMatch.stopped
            ? "stop" : completion.optString("finishReason", "stop"));
        choices.put(choice);
        response.put("choices", choices);
        response.put("usage", openAiUsage(completion, prompt, rawResponse));
        response.put("tai", completion);
        return response;
    }

    @NonNull
    public JSONObject openAiAudioSpeech(@NonNull String body) throws JSONException {
        parseBody(body);
        return openAiError(error(501, "unsupported_audio_output",
            "Audio output is not available from the local LiteRT-LM or MNN runners. "
                + "Use text responses or a separate text-to-speech backend."));
    }

    public boolean isStreamRequest(@NonNull String body) {
        try {
            return parseBody(body).optBoolean("stream", false);
        } catch (JSONException e) {
            return false;
        }
    }

    public void openAiChatCompletionsStream(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        if (shouldDelegateRuntime()) {
            if (runtimeClient == null) {
                emitOpenAiError(sink, error(503, "tai_runtime_unavailable", "TAI runtime service client is unavailable."));
                return;
            }
            runtimeClient.stream(TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM, delegatedRuntimeBody(body), sink);
            return;
        }
        JSONObject request = parseBody(body);
        JSONArray messages = request.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            emitOpenAiError(sink, error(400, "bad_request", "Missing messages"));
            return;
        }
        List<String> stopSequences;
        try {
            stopSequences = OpenAiStopSequences.fromRequest(request);
        } catch (JSONException e) {
            emitOpenAiError(sink, error(400, "invalid_stop", e.getMessage()));
            return;
        }
        boolean includeUsage = includeStreamUsage(request);

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) {
            emitOpenAiError(sink, error(404, "model_not_found", "Unknown TAI model: " + modelId));
            return;
        }
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            emitOpenAiError(sink, generationCapabilityError(spec));
            return;
        }
        omitAutomaticToolsForCompatibility(request, spec);
        if (!modelSupportsRequestedTools(request, spec)) {
            emitOpenAiError(sink, error(400, "capability_not_supported",
                "Model " + spec.id + " does not support tool use through this endpoint."));
            return;
        }
        JSONObject audioOutputError = unsupportedAudioOutputRequest(request);
        if (audioOutputError != null) {
            emitOpenAiError(sink, audioOutputError);
            return;
        }
        OpenAiChatRequest chatRequest;
        try {
            chatRequest = openAiChatRequest(request, messages, spec);
        } catch (JSONException e) {
            emitOpenAiError(sink, chatRequestError(e));
            return;
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        JSONObject loadError = ensureModelLoadedForGeneration(spec, options);
        if (loadError != null) {
            emitOpenAiError(sink, loadError);
            return;
        }
        String id = "tai-chatcmpl-" + System.currentTimeMillis();
        long created = System.currentTimeMillis() / 1000L;
        emitChatChunk(sink, id, created, modelId, "", "assistant", null);

        AtomicReference<IOException> ioError = new AtomicReference<>();
        AtomicReference<Boolean> emittedToolCalls = new AtomicReference<>(false);
        OpenAiStopSequences.StreamMatcher stopMatcher = new OpenAiStopSequences.StreamMatcher(stopSequences);
        JSONObject chat = localRuntime().chat(modelId, chatRequest.request, options, new TaiGenerationCallback() {
            @Override
            public void onToken(@NonNull String text) {
                if (text.isEmpty() || ioError.get() != null) return;
                try {
                    String safeText = stopMatcher.append(text);
                    if (!safeText.isEmpty()) emitChatChunk(sink, id, created, modelId, safeText, null, null);
                } catch (IOException e) {
                    ioError.set(e);
                    try {
                        localRuntime().cancel();
                    } catch (JSONException ignored) {
                    }
                } catch (JSONException e) {
                    ioError.set(new IOException(e));
                    try {
                        localRuntime().cancel();
                    } catch (JSONException ignored) {
                    }
                }
            }

            @Override
            public void onThinkingToken(@NonNull String text) {
                if (text.isEmpty() || ioError.get() != null) return;
                try {
                    emitChatThinkingChunk(sink, id, created, modelId, text);
                } catch (IOException | JSONException e) {
                    ioError.set(e instanceof IOException ? (IOException) e : new IOException(e));
                    try {
                        localRuntime().cancel();
                    } catch (JSONException ignored) {
                    }
                }
            }

            @Override
            public boolean shouldCancelGeneration() {
                return stopMatcher.isStopped();
            }

            @Override
            public void onToolCalls(@NonNull JSONArray toolCalls) {
                if (toolCalls.length() == 0 || ioError.get() != null || stopMatcher.isStopped()) return;
                try {
                    emitToolCallChunk(sink, id, created, modelId, toolCalls);
                    emittedToolCalls.set(true);
                } catch (IOException | JSONException e) {
                    ioError.set(e instanceof IOException ? (IOException) e : new IOException(e));
                    try {
                        localRuntime().cancel();
                    } catch (JSONException ignored) {
                    }
                }
            }

            @Override
            public void onComplete(@NonNull String fullText) {
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
            }
        });
        if (ioError.get() != null) throw ioError.get();
        if (!chat.optBoolean("ok", false)) {
            emitOpenAiError(sink, chat);
            return;
        }
        String remainingText = stopMatcher.finish();
        if (!remainingText.isEmpty()) emitChatChunk(sink, id, created, modelId, remainingText, null, null);
        JSONArray toolCalls = chat.optJSONArray("toolCalls");
        boolean hasToolCalls = !stopMatcher.isStopped() && toolCalls != null && toolCalls.length() > 0;
        if (hasToolCalls && !emittedToolCalls.get()) {
            emitToolCallChunk(sink, id, created, modelId, toolCalls);
        }
        String finishReason = stopMatcher.isStopped() ? "stop"
            : chat.optString("finishReason", hasToolCalls ? "tool_calls" : "stop");
        emitChatChunk(sink, id, created, modelId, "", null, finishReason);
        if (includeUsage) {
            emitUsageChunk(sink, id, created, modelId, "chat.completion.chunk",
                openAiUsage(chat, messages.toString(), chat.optString("response", "")));
        }
        sink.onDone();
    }

    public void openAiCompletionsStream(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        if (shouldDelegateRuntime()) {
            if (runtimeClient == null) {
                emitOpenAiError(sink, error(503, "tai_runtime_unavailable", "TAI runtime service client is unavailable."));
                return;
            }
            runtimeClient.stream(TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM, delegatedRuntimeBody(body), sink);
            return;
        }
        JSONObject request = parseBody(body);
        String prompt = promptFromCompletionRequest(request);
        if (prompt.trim().isEmpty()) {
            emitOpenAiError(sink, error(400, "bad_request", "Missing prompt"));
            return;
        }
        List<String> stopSequences;
        try {
            stopSequences = OpenAiStopSequences.fromRequest(request);
        } catch (JSONException e) {
            emitOpenAiError(sink, error(400, "invalid_stop", e.getMessage()));
            return;
        }
        boolean includeUsage = includeStreamUsage(request);

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) {
            emitOpenAiError(sink, error(404, "model_not_found", "Unknown TAI model: " + modelId));
            return;
        }
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            emitOpenAiError(sink, generationCapabilityError(spec));
            return;
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        JSONObject loadError = ensureModelLoadedForGeneration(spec, options);
        if (loadError != null) {
            emitOpenAiError(sink, loadError);
            return;
        }

        String id = "tai-cmpl-" + System.currentTimeMillis();
        long created = System.currentTimeMillis() / 1000L;
        AtomicReference<IOException> ioError = new AtomicReference<>();
        OpenAiStopSequences.StreamMatcher stopMatcher = new OpenAiStopSequences.StreamMatcher(stopSequences);
        JSONObject completion = localRuntime().complete(modelId, prompt, options, new TaiGenerationCallback() {
            @Override
            public void onToken(@NonNull String text) {
                if (text.isEmpty() || ioError.get() != null) return;
                try {
                    String safeText = stopMatcher.append(text);
                    if (!safeText.isEmpty()) emitCompletionChunk(sink, id, created, modelId, safeText, null);
                } catch (IOException e) {
                    ioError.set(e);
                    try {
                        localRuntime().cancel();
                    } catch (JSONException ignored) {
                    }
                } catch (JSONException e) {
                    ioError.set(new IOException(e));
                    try {
                        localRuntime().cancel();
                    } catch (JSONException ignored) {
                    }
                }
            }

            @Override
            public boolean shouldCancelGeneration() {
                return stopMatcher.isStopped();
            }

            @Override
            public void onComplete(@NonNull String fullText) {
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
            }
        });
        if (ioError.get() != null) throw ioError.get();
        if (!completion.optBoolean("ok", false)) {
            emitOpenAiError(sink, completion);
            return;
        }
        String remainingText = stopMatcher.finish();
        if (!remainingText.isEmpty()) emitCompletionChunk(sink, id, created, modelId, remainingText, null);
        String finishReason = stopMatcher.isStopped()
            ? "stop" : completion.optString("finishReason", "stop");
        emitCompletionChunk(sink, id, created, modelId, "", finishReason);
        if (includeUsage) {
            emitUsageChunk(sink, id, created, modelId, "text_completion",
                openAiUsage(completion, prompt, completion.optString("response", "")));
        }
        sink.onDone();
    }

    @NonNull
    public JSONObject openAiModels() throws JSONException {
        JSONObject installed = new JSONObject();
        JSONArray models = new JSONArray();
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.read(appContext);
        boolean mnnSupported = device.mnnSupported;
        LinkedHashMap<String, TaiModelSpec> availableModels = new LinkedHashMap<>();
        availableModels.putAll(modelStore.getDownloadedReadableModels());
        availableModels.putAll(modelStore.getInstalledUserModels());
        for (TaiModelSpec stored : availableModels.values()) {
            if (TaiModelSpec.BACKEND_MNN_LLM.equals(stored.backend) && !mnnSupported) continue;
            // Management can retain imported packages whose backend is not executable yet, but
            // generation discovery must publish only models with at least one runnable endpoint.
            if (stored.endpointCapabilities.isEmpty()) continue;
            // Speech-to-text models aren't chat models: MultiBackendTaiRuntime doesn't route
            // speech_to_text yet (phase 2), and they're never a valid /v1/chat/completions target.
            if (stored.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) continue;
            Integer userContext = settings.getRuntimeOptions(stored).contextWindow;
            TaiModelSpec spec = advertisedContextWindow(TaiContextWindowPolicy.apply(stored, device.memoryBytes,
                userContext), device, presence, userContext != null);
            // Advertise multimodal LiteRT models as separate modality-scoped ids (chat / -vision /
            // -audio), matching Edge Gallery's per-task loading. See TaiModelVariants.
            for (TaiModelSpec variant : TaiModelVariants.expand(spec,
                    TaiModelVariants.Exposure.fromValue(modelStore.getExposure(spec.id)))) {
                models.put(variant.toJson());
            }
        }
        installed.put("models", models);
        return applyAudioHistoryGates(openAiModelsFromTaiModels(installed), device);
    }

    @NonNull
    private JSONObject applyAudioHistoryGates(@NonNull JSONObject response, @NonNull TaiDeviceCapabilities device) throws JSONException {
        JSONArray data = response.optJSONArray("data");
        if (data == null) return response;
        LinkedHashSet<String> failedAudioIds = new LinkedHashSet<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            if (!TaiModelSpec.BACKEND_LITERT_LM.equals(item.optString("_backend", ""))) continue;
            String id = item.optString("id", "");
            if (id.isEmpty()) continue;
            if (TaiRuntimeHistory.hasFailedAudioInput(appContext, id, device)) failedAudioIds.add(id);
        }
        return pruneAudioInputFromResponse(response, failedAudioIds);
    }

    @NonNull
    static JSONObject pruneAudioInputFromResponse(@NonNull JSONObject response, @NonNull Set<String> failedAudioModelIds) throws JSONException {
        if (failedAudioModelIds.isEmpty()) return response;
        JSONArray data = response.optJSONArray("data");
        if (data == null) return response;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            if (!failedAudioModelIds.contains(item.optString("id", ""))) continue;
            item.put("_capabilities", stripCapability(item.optJSONArray("_capabilities"), TaiModelSpec.CAPABILITY_AUDIO_INPUT));
            item.put("_endpoint_capabilities", stripCapability(item.optJSONArray("_endpoint_capabilities"), TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        }
        return response;
    }

    @NonNull
    private static JSONArray stripCapability(@Nullable JSONArray capabilities, @NonNull String capability) {
        JSONArray filtered = new JSONArray();
        if (capabilities == null) return filtered;
        for (int i = 0; i < capabilities.length(); i++) {
            String value = capabilities.optString(i, "");
            if (!capability.equals(value)) filtered.put(value);
        }
        return filtered;
    }

    @NonNull
    static JSONObject openAiModelsFromTaiModels(@NonNull JSONObject source) throws JSONException {
        JSONArray models = source.optJSONArray("models");
        Map<String, JSONObject> dedupedModels = new LinkedHashMap<>();
        if (models != null) {
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) continue;
                String id = model.optString("id", "");
                if (id.isEmpty()) continue;
                JSONObject existing = dedupedModels.get(id);
                dedupedModels.put(id, existing == null ? model : mergeModelMetadata(existing, model));
            }
        }
        JSONArray data = new JSONArray();
        for (JSONObject model : dedupedModels.values()) {
            JSONObject item = new JSONObject();
            item.put("id", model.optString("id", ""));
            item.put("object", "model");
            item.put("created", 0);
            item.put("owned_by", "termux-launcher");
            String backend = model.optString("backend", TaiModelSpec.BACKEND_LITERT_LM);
            item.put("_backend", backend);
            item.put("_format", model.optString("format", ""));
            item.put("_display_name", model.optString("displayName", model.optString("id", "")));
            item.put("_architecture", model.isNull("architecture") ? "" : model.optString("architecture", ""));
            item.put("_quantization", model.isNull("quantization") ? "" : model.optString("quantization", ""));
            item.put("_size", model.optLong("sizeBytes", 0L));
            item.put("_sha256", model.isNull("sha256") ? "" : model.optString("sha256", ""));
            item.put("_license", model.optString("license", ""));
            boolean capabilitiesVerified = model.optBoolean("capabilitiesVerified", false);
            item.put("_capabilities_verified", capabilitiesVerified);
            item.put("_capability_source", capabilitiesVerified ? "catalog"
                : model.optString("capabilitySource", "import_or_user_metadata"));
            // Declared by the publisher/importer until a device probe exists; never implied by provenance.
            item.put("_capability_verification", model.optString("capabilityVerification", "declared"));
            JSONArray sourceCapabilities = model.optJSONArray("sourceCapabilities");
            JSONArray declaredEndpointCapabilities = model.optJSONArray("endpointCapabilities");
            JSONArray capabilities = declaredEndpointCapabilities == null ? model.optJSONArray("capabilities") : declaredEndpointCapabilities;
            if (capabilities == null) capabilities = new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_CHAT);
            String defaultFormat = TaiModelSpec.BACKEND_MNN_LLM.equals(backend)
                ? TaiModelSpec.FORMAT_MNN : TaiModelSpec.FORMAT_LITERTLM;
            JSONArray endpointCapabilities = declaredEndpointCapabilities == null
                ? openAiEndpointCapabilities(model.optString("id", ""), capabilities, backend,
                    model.optString("format", defaultFormat))
                : capabilities;
            item.put("_capabilities", endpointCapabilities);
            item.put("_endpoint_capabilities", endpointCapabilities);
            item.put("_source_capabilities", sourceCapabilities == null ? capabilities : sourceCapabilities);
            item.put("_default_max_output_tokens", model.optInt("defaultMaxOutputTokens", TaiModelSpec.defaultMaxOutputTokensFor(model.optString("id", ""), backend)));
            item.put("_endpoint_context_window", model.optInt("endpointContextWindow", model.optInt("contextWindow", TaiModelSpec.defaultEndpointContextWindowFor(model.optString("id", ""), backend))));
            item.put("_source_context_window", model.optInt("sourceContextWindow", model.optInt("contextWindow", TaiModelSpec.defaultEndpointContextWindowFor(model.optString("id", ""), backend))));
            String toolMode = model.optString("toolMode", "");
            if (toolMode.isEmpty()) {
                LinkedHashSet<String> endpointSet = new LinkedHashSet<>();
                appendArrayValues(endpointSet, endpointCapabilities);
                String inferredToolMode = TaiModelSpec.toolModeFor(backend, endpointSet);
                toolMode = inferredToolMode == null ? "" : inferredToolMode;
            }
            if (!toolMode.isEmpty()) item.put("_tool_mode", toolMode);
            data.put(item);
        }

        JSONObject response = new JSONObject();
        response.put("object", "list");
        response.put("data", data);
        response.put("models", codexModels(data));
        return response;
    }

    @NonNull
    private static JSONArray codexModels(@NonNull JSONArray data) throws JSONException {
        JSONArray models = new JSONArray();
        for (int i = 0; i < data.length(); i++) {
            JSONObject source = data.optJSONObject(i);
            JSONArray capabilities = source == null ? null : source.optJSONArray("_capabilities");
            int context = source == null ? 0 : source.optInt("_endpoint_context_window", 4096);
            if (source == null
                || !contains(capabilities, TaiModelSpec.CAPABILITY_TEXT_CHAT)
                || !contains(capabilities, TaiModelSpec.CAPABILITY_TOOL_USE)
                || context < 16_384) continue;
            boolean image = contains(capabilities, TaiModelSpec.CAPABILITY_IMAGE_INPUT);
            boolean code = contains(capabilities, TaiModelSpec.CAPABILITY_CODE);
            JSONObject model = new JSONObject();
            model.put("slug", source.optString("id", ""));
            model.put("display_name", source.optString("_display_name", source.optString("id", "")));
            model.put("description", "On-device " + (code ? "coding " : "tool-capable ")
                + source.optString("_backend", "") + " model served by Termux Launcher");
            model.put("default_reasoning_level", "none");
            model.put("supported_reasoning_levels", new JSONArray());
            model.put("shell_type", "local");
            model.put("visibility", "list");
            model.put("supported_in_api", true);
            model.put("priority", i);
            model.put("availability_nux", JSONObject.NULL);
            model.put("upgrade", JSONObject.NULL);
            model.put("base_instructions", code
                ? "You are an on-device coding assistant. Use the provided tools when needed. Keep changes scoped and verify your work."
                : "You are an on-device assistant. Use the provided tools when needed and report tool results accurately.");
            model.put("supports_reasoning_summaries", false);
            model.put("support_verbosity", false);
            model.put("default_verbosity", JSONObject.NULL);
            model.put("apply_patch_tool_type", JSONObject.NULL);
            model.put("truncation_policy", new JSONObject().put("mode", "tokens").put("limit", Math.max(1024, context / 4)));
            model.put("supports_parallel_tool_calls", false);
            model.put("context_window", context);
            model.put("max_context_window", context);
            model.put("effective_context_window_percent", 90);
            model.put("experimental_supported_tools", new JSONArray());
            model.put("input_modalities", image ? new JSONArray().put("text").put("image") : new JSONArray().put("text"));
            models.put(model);
        }
        return models;
    }

    @NonNull
    private static JSONObject mergeModelMetadata(@NonNull JSONObject existing, @NonNull JSONObject next) throws JSONException {
        JSONObject merged = new JSONObject(next.toString());
        mergeArrayField(merged, existing, next, "capabilities");
        mergeArrayField(merged, existing, next, "endpointCapabilities");
        mergeArrayField(merged, existing, next, "sourceCapabilities");
        return merged;
    }

    private static void mergeArrayField(@NonNull JSONObject output, @NonNull JSONObject existing,
                                        @NonNull JSONObject next, @NonNull String field) throws JSONException {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        appendArrayValues(values, existing.optJSONArray(field));
        appendArrayValues(values, next.optJSONArray(field));
        if (values.isEmpty()) return;
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        output.put(field, array);
    }

    private static void appendArrayValues(@NonNull LinkedHashSet<String> output, @Nullable JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) output.add(value);
        }
    }

    private static boolean contains(@Nullable JSONArray values, @NonNull String expected) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) {
            if (expected.equals(values.optString(i, ""))) return true;
        }
        return false;
    }

    @NonNull
    public JSONObject embeddings(@NonNull String body) throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_EMBEDDINGS, delegatedRuntimeBody(body));
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        String encodingFormat = request.optString("encoding_format", "float");
        if (!encodingFormat.isEmpty() && !"float".equalsIgnoreCase(encodingFormat)) {
            return openAiRequestError(400, "unsupported_encoding_format",
                "Only encoding_format:\"float\" is supported for local embeddings.", "encoding_format");
        }
        boolean hasDimensions = request.has("dimensions");
        int dimensions = hasDimensions ? request.optInt("dimensions", -1) : 0;
        if (hasDimensions && dimensions <= 0) {
            return openAiRequestError(400, "invalid_dimensions", "Embedding dimensions must be positive.", "dimensions");
        }
        List<String> inputs = embeddingInputs(request);
        if (inputs == null) {
            return openAiRequestError(400, "unsupported_embedding_input",
                "Embeddings input must be a string or an array of strings.", "input");
        }
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) {
            return openAiRequestError(404, "model_not_found", "Unknown TAI model: " + modelId, "model");
        }
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)) {
            return openAiRequestError(501, "capability_not_supported",
                "Embeddings are not supported for model '" + modelId + "'.", "model");
        }
        MultiBackendTaiRuntime local = (MultiBackendTaiRuntime) localRuntime();
        if (!local.residency().isResident(TaiResidency.Kind.EMBEDDING, spec.id)) {
            JSONObject refusal = decideEmbeddingLoad(spec, runtimeOptionsFromRequest(request, spec));
            if (refusal != null) return refusal;
        }
        return local.embed(spec, inputs, dimensions);
    }

    @Nullable
    private List<String> embeddingInputs(@NonNull JSONObject request) {
        ArrayList<String> inputs = new ArrayList<>();
        Object raw = request.opt("input");
        if (raw == null || raw == JSONObject.NULL) {
            inputs.add("");
            return inputs;
        }
        if (raw instanceof String) {
            inputs.add((String) raw);
            return inputs;
        }
        if (!(raw instanceof JSONArray)) return null;
        JSONArray array = (JSONArray) raw;
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (!(item instanceof String)) return null;
            inputs.add((String) item);
        }
        return inputs;
    }

    /**
     * OpenAI-style speech-to-text: {@code file} (a path — the API server writes uploads to
     * {@code cacheDir/tai-ipc}, and a path under Termux home or shared storage is accepted from
     * scripts), {@code model} (default: the installed speech model from settings), {@code language}
     * (ISO 639-1, multilingual Whisper graphs only; Parakeet detects it) and {@code prompt} (a
     * vocabulary line Whisper biases towards; Parakeet has no prompt). The app process resolves the
     * model and forwards to the runtime process, whose STT lane runs the engine's interpreter.
     * Answers {@code {text, language, duration, segments, ...}}.
     */
    @NonNull
    public JSONObject transcribe(@NonNull String body) throws JSONException {
        return transcribe(body, DEFAULT_TRANSCRIBE_TIMEOUT_MS);
    }

    /**
     * As {@link #transcribe(String)}, with the IPC deadline the caller wants instead of the flat
     * default — a voice-input segment gives itself a much shorter one, so a hung runtime fails
     * that one segment instead of stalling for the full timeout.
     */
    @NonNull
    public JSONObject transcribe(@NonNull String body, long timeoutMs) throws JSONException {
        JSONObject request = parseBody(body);
        TaiModelSpec spec = resolveSpeechModel(request);
        if (spec == null) return speechModelError(request);
        String path = request.optString("file", "").trim();
        if (path.isEmpty()) return openAiRequestError(400, "stt_audio_missing", "Provide the audio as multipart 'file' or a 'file' path.", "file");
        if (shouldDelegateRuntime()) {
            File audio = new File(path);
            if (!isSttIpcFile(audio)) {
                // A path from a script: the same roots /v1/chat/completions accepts local media from.
                try {
                    path = TaiMediaAccess.resolveLocalPath(path);
                } catch (JSONException e) {
                    return openAiRequestError(400, "stt_audio_unreadable", mediaAccessMessage(e), "file");
                }
            }
            if (!new File(path).isFile()) return openAiRequestError(400, "stt_audio_missing", "Audio file not found: " + path, "file");
            request.put("file", path);
            return runtimeRequest(TaiRuntimeIpc.OP_TRANSCRIBE, delegatedSpeechBody(request, spec), timeoutMs);
        }
        rememberSttIdleLimit(request);
        File audio = new File(path);
        if (!audio.isFile()) return openAiRequestError(400, "stt_audio_missing", "Audio file not found: " + path, "file");
        try {
            TaiRuntime local = localRuntime();
            if (!(local instanceof MultiBackendTaiRuntime)) {
                return openAiRequestError(501, "capability_not_supported", "Speech-to-text needs the multi-backend runtime.", "model");
            }
            MultiBackendTaiRuntime router = (MultiBackendTaiRuntime) local;
            if (!router.residency().isResident(TaiResidency.Kind.STT, spec.id)) {
                JSONObject refusal = decideSttLoad(spec);
                if (refusal != null) return refusal;
            }
            String language = stringOverride(request, "language");
            return router.transcribe(spec, audio, language, biasPromptFor(request));
        } finally {
            // The upload was written for this one request; scripts' own files are left alone.
            if (isSttIpcFile(audio)) {
                //noinspection ResultOfMethodCallIgnored
                audio.delete();
            }
        }
    }

    /** Loads the speech model ahead of its first request, so the load overlaps the first words spoken. */
    @NonNull
    public JSONObject sttWarm(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        TaiModelSpec spec = resolveSpeechModel(request);
        if (spec == null) return speechModelError(request);
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_STT_WARM, delegatedSpeechBody(request, spec));
        rememberSttIdleLimit(request);
        TaiRuntime local = localRuntime();
        if (!(local instanceof MultiBackendTaiRuntime)) {
            return openAiRequestError(501, "capability_not_supported", "Speech-to-text needs the multi-backend runtime.", "model");
        }
        MultiBackendTaiRuntime router = (MultiBackendTaiRuntime) local;
        if (!router.residency().isResident(TaiResidency.Kind.STT, spec.id)) {
            JSONObject refusal = decideSttLoad(spec);
            if (refusal != null) return refusal;
        }
        return router.sttWarm(spec);
    }

    /** The idle limit for a resident speech model, for the runtime process's memory watch. */
    public long sttIdleLimitMs() {
        return sttIdleLimitMs;
    }

    /**
     * The vocabulary line behind {@code <|startofprev|>}: the request's OpenAI {@code prompt},
     * trimmed, or none (plain dictation). There is no built-in vocabulary any more: the shell
     * bias hurt prose (dropped words, noise read as "ok"), and voice input is dictation only.
     */
    @Nullable
    static String biasPromptFor(@NonNull JSONObject request) {
        String prompt = request.optString("prompt", "").trim();
        return prompt.isEmpty() ? null : prompt;
    }

    /** The speech model a request names, or the one voice input uses; {@code null} when neither resolves. */
    @Nullable
    private TaiModelSpec resolveSpeechModel(@NonNull JSONObject request) {
        String modelId = speechModelIdFor(request);
        if (modelId.isEmpty()) return null;
        TaiModelSpec spec = resolveModel(request, modelId);
        return spec != null && spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT) ? spec : null;
    }

    /** The request's model, else the installed speech model the settings choose — a stored id whose
     *  files are gone counts as unset and another installed speech model stands in for it. */
    @NonNull
    private String speechModelIdFor(@NonNull JSONObject request) {
        String requested = requestedModelId(request, "");
        return requested.isEmpty() ? TaiSpeechModels.activeModelId(settings, modelStore) : requested;
    }

    @NonNull
    private JSONObject speechModelError(@NonNull JSONObject request) throws JSONException {
        String modelId = speechModelIdFor(request);
        if (modelId.isEmpty()) {
            return openAiRequestError(400, "stt_model_not_configured",
                "No speech-to-text model is installed. Download one under Keyboard settings > Voice input > Speech model.", "model");
        }
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return openAiRequestError(404, "model_not_found", "Unknown TAI model: " + modelId, "model");
        return openAiRequestError(400, "not_a_speech_model", "Model '" + modelId + "' is not a speech-to-text model.", "model");
    }

    /** The runtime-process body for a speech request: the resolved spec and the STT idle setting ride along. */
    @NonNull
    private String delegatedSpeechBody(@NonNull JSONObject request, @NonNull TaiModelSpec spec) throws JSONException {
        request.remove(INTERNAL_RUNTIME_OPTIONS);
        request.put("model", spec.id);
        request.put(INTERNAL_MODEL_SPEC, spec.toJson());
        request.put(INTERNAL_STT_IDLE_MINUTES, settings.getSttIdleUnloadMinutes());
        return request.toString();
    }

    private void rememberSttIdleLimit(@NonNull JSONObject request) {
        if (!runtimeProcess || !request.has(INTERNAL_STT_IDLE_MINUTES)) return;
        sttIdleLimitMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(Math.max(0, request.optInt(INTERNAL_STT_IDLE_MINUTES, 0)));
    }

    /** Whether {@code file} is one of the uploads the API server wrote under {@code cacheDir/tai-ipc}. */
    private boolean isSttIpcFile(@NonNull File file) {
        try {
            File dir = new File(appContext.getCacheDir(), STT_IPC_DIR).getCanonicalFile();
            return file.getCanonicalFile().getParentFile() != null && dir.equals(file.getCanonicalFile().getParentFile());
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    private static String mediaAccessMessage(@NonNull JSONException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        int colon = message.indexOf(':');
        return colon > 0 ? message.substring(colon + 1) : message;
    }

    @NonNull
    private JSONObject openAiRequestError(int statusCode, @NonNull String code,
                                          @NonNull String message, @Nullable String param) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", "invalid_request_error");
        if (param != null) error.put("param", param);
        error.put("code", code);
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", statusCode);
        return response;
    }

    @NonNull
    public JSONObject preflight(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        String requestedBackend = request.optString("backend", "").trim();
        if (!requestedBackend.isEmpty() && !requestedBackend.equalsIgnoreCase(spec.backend)) {
            return error(409, "backend_mismatch", "Model " + modelId + " requires backend " + spec.backend + ".");
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options,
            request.optBoolean("autoLoad", request.optBoolean("auto_load", false)));
        JSONObject data = preflight.toJson();
        data.put("model", spec.toJson());
        data.put("_statusCode", preflight.blocked ? preflightStatusCode(preflight) : 200);
        return data;
    }

    @NonNull
    private JSONObject parseBody(@NonNull String body) throws JSONException {
        if (body.trim().isEmpty()) return new JSONObject();
        return new JSONObject(body);
    }

    @Nullable
    private JSONObject ensureModelLoadedForGeneration(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) throws JSONException {
        String modelId = spec.id;
        if (localRuntime().isModelLoaded(modelId)) return null;
        if (hasInjectedRuntimeOverride()) {
            JSONObject load = localRuntime().load(spec, options);
            if (!load.optBoolean("ok", false)) return load;
            return null;
        }
        if (!settings.isOpenAiAutoLoadEnabled()) {
            JSONObject data = error(409, "model_not_loaded",
                "Model is not loaded. Load it explicitly with tai load or from TAI settings.");
            data.put("autoLoadEnabled", false);
            return data;
        }
        TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options, true);
        if (preflight.blocked) {
            return preflight.blockingError(preflightStatusCode(preflight));
        }
        LoadDecision decision = decideLoad(spec, options, preflight);
        if (decision.refusal != null) return decision.refusal;
        JSONObject load = localRuntime().load(spec, decision.options);
        load.put("preflight", preflight.toJson());
        decision.describe(load);
        recordRuntimeResult(spec, preflight, load);
        if (!load.optBoolean("ok", false)) return load;
        return null;
    }

    /** The options a load goes ahead with, or the refusal the memory budget answered instead. */
    private static final class LoadDecision {
        @Nullable final TaiRuntimeOptions options;
        @Nullable final JSONObject refusal;
        @NonNull final TaiLoadBudget.Plan plan;
        /** Residents closed to make room, in order; empty when the load fit as is. */
        @NonNull final List<String> evicted;

        LoadDecision(@Nullable TaiRuntimeOptions options, @Nullable JSONObject refusal,
                     @NonNull TaiLoadBudget.Plan plan, @NonNull List<String> evicted) {
            this.options = options;
            this.refusal = refusal;
            this.plan = plan;
            this.evicted = evicted;
        }

        /** Stamps the load result with the budget and what was evicted for it. */
        void describe(@NonNull JSONObject result) throws JSONException {
            result.put("memoryBudget", planJson(plan));
            result.put("evicted", new JSONArray(evicted));
        }
    }

    /**
     * Settles accelerator and context window for a load that passed preflight, from the memory free
     * right now (see {@link TaiLoadBudget}). Every load path goes through here: explicit loads,
     * keep-warm and the automatic load behind a chat request.
     */
    @NonNull
    private LoadDecision decideLoad(
        @NonNull TaiModelSpec spec,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiLoadPreflight.Result preflight
    ) throws JSONException {
        TaiDeviceCapabilities device = preflight.device;
        List<String> accelerators;
        if (!"auto".equals(preflight.requestedAccelerator) || TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)) {
            accelerators = Collections.singletonList(preflight.effectiveAccelerator);
        } else {
            accelerators = TaiLoadPreflight.autoAccelerators(appContext, spec, device, preflight.profile);
            if (accelerators.isEmpty()) accelerators = Collections.singletonList(preflight.effectiveAccelerator);
        }
        int cap = TaiContextWindowPolicy.effectiveEndpointContextWindow(spec, device.memoryBytes, options.contextWindow);
        long fileBytes = TaiResidency.fileBytes(spec);
        boolean encoders = spec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)
            || spec.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        String crashedAccelerator = null;
        int crashedContext = 0;
        JSONObject marker = TaiRuntimeCrashMarker.read(appContext);
        if (marker != null && spec.id.equals(marker.optString("modelId"))) {
            crashedAccelerator = TaiLoadPreflight.normalizeAccelerator(marker.optString("accelerator", null));
            crashedContext = marker.optInt("contextWindow", 0);
        }
        // The resident chat model is closed before this one initializes, so its bytes are this
        // load's to spend; every other resident stays and is already missing from availMem — the
        // idle ones among them are what the plan may evict when it does not fit as is.
        List<TaiResidency.Entry> residents = residency().snapshot();
        long available = TaiResidency.creditedAvailable(device.availableMemoryBytes, residents,
            TaiResidency.Kind.CHAT, spec.backend);
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(spec.backend, fileBytes, encoders,
            device.physicalMemoryBytes, available, accelerators, cap,
            crashedAccelerator, crashedContext, options.contextWindow != null, device.memoryThresholdBytes,
            measuredHistory(spec, device), TaiResidency.evictionCandidates(residents, TaiResidency.Kind.CHAT, spec.backend)));
        if (!plan.fits) return new LoadDecision(null, insufficientMemory(spec.displayName, plan), plan, Collections.<String>emptyList());
        List<String> evicted = evict(plan);
        TaiRuntimeOptions loadOptions = optionsForPreflight(spec, options, preflight);
        if (!TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend) && plan.accelerator != null
                && !plan.accelerator.equals(preflight.effectiveAccelerator)) {
            loadOptions = loadOptions.withAccelerator(plan.accelerator);
        }
        return new LoadDecision(loadOptions.withContextWindow(plan.contextWindow), null, plan, evicted);
    }

    /**
     * The one budget for an embedding model that is not resident yet: the same preflight a chat
     * load passes, then the measured or file-size estimate against what is free, idle residents
     * evicted if that is what it takes. Checked only when the runtime does not already hold this
     * model, never per batch. {@code null} means go ahead.
     */
    @Nullable
    private JSONObject decideEmbeddingLoad(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) throws JSONException {
        // Memory only: the chat preflight's ABI, native-library and sidecar checks do not describe an
        // embedding package, and the embedding runtimes report their own load errors.
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        // This backend's embedding runtime replaces the model it holds; that one is credited back.
        List<TaiResidency.Entry> residents = residency().snapshot();
        long available = TaiResidency.creditedAvailable(device.availableMemoryBytes, residents,
            TaiResidency.Kind.EMBEDDING, spec.backend);
        long worst = TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device, spec.backend, "cpu", 0);
        TaiLoadBudget.Estimate estimate = worst > 0L ? TaiLoadBudget.Estimate.measured(worst)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.embeddingEstimateBytes(spec), 0L);
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            device.memoryThresholdBytes, TaiResidency.evictionCandidates(residents, TaiResidency.Kind.EMBEDDING, spec.backend));
        if (!plan.fits) return openAiError(insufficientMemory(spec.displayName, plan));
        evict(plan);
        return null;
    }

    /**
     * The budget for a speech model that is not resident yet, the embedding decision's twin: the
     * measured or file-size estimate (× 1.9, see {@link TaiResidency#STT_FACTOR_TENTHS}) against
     * what is free plus the STT resident it replaces, idle embeddings — and, for STT alone, idle
     * chat — evicted if that is what it takes. {@code null} means go ahead.
     */
    @Nullable
    private JSONObject decideSttLoad(@NonNull TaiModelSpec spec) throws JSONException {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        List<TaiResidency.Entry> residents = residency().snapshot();
        long available = TaiResidency.creditedAvailable(device.availableMemoryBytes, residents,
            TaiResidency.Kind.STT, spec.backend);
        long worst = TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0);
        TaiLoadBudget.Estimate estimate = worst > 0L ? TaiLoadBudget.Estimate.measured(worst)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.sttEstimateBytes(spec), 0L);
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            device.memoryThresholdBytes, TaiResidency.evictionCandidates(residents, TaiResidency.Kind.STT, spec.backend));
        if (!plan.fits) return openAiError(insufficientMemory(spec.displayName, plan));
        evict(plan);
        return null;
    }

    /** The measured load costs of this model on this device, as the budget asks for them. */
    @NonNull
    private TaiLoadBudget.History measuredHistory(@NonNull TaiModelSpec spec, @NonNull TaiDeviceCapabilities device) {
        return (accelerator, contextTokens) -> TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device,
            spec.backend, accelerator, contextTokens);
    }

    /** Carries out a plan's evictions through the router; the ids actually closed, in order. */
    @NonNull
    private List<String> evict(@NonNull TaiLoadBudget.Plan plan) throws JSONException {
        if (plan.evicted.isEmpty()) return Collections.emptyList();
        TaiRuntime local = localRuntime();
        if (!(local instanceof MultiBackendTaiRuntime)) return Collections.emptyList();
        return ((MultiBackendTaiRuntime) local).evict(plan.evicted);
    }

    /** The refusal every load path answers when the budget says no, chat and embedding alike. */
    @NonNull
    static JSONObject insufficientMemory(@NonNull String displayName, @NonNull TaiLoadBudget.Plan plan) throws JSONException {
        JSONObject refusal = new JSONObject();
        refusal.put("ok", false);
        refusal.put("error", "insufficient_memory");
        refusal.put("message", "Not enough free memory to load " + displayName + ". Close some apps and try again.");
        refusal.put("_statusCode", 409);
        refusal.put("memoryBudget", planJson(plan));
        return refusal;
    }

    /** The registry of resident models; an empty one when a test has injected a bare runtime. */
    @NonNull
    private TaiResidency residency() {
        TaiRuntime local = localRuntime();
        return local instanceof MultiBackendTaiRuntime ? ((MultiBackendTaiRuntime) local).residency() : new TaiResidency();
    }

    /** What the resident chat model holds by the registry's estimate; published for the app process. */
    public long residentChatBytes() {
        if (!(runtime instanceof MultiBackendTaiRuntime)) return 0L;
        return ((MultiBackendTaiRuntime) runtime).residency().bytes(TaiResidency.Kind.CHAT, null);
    }

    @NonNull
    static JSONObject planJson(@NonNull TaiLoadBudget.Plan plan) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("fits", plan.fits);
        json.put("accelerator", plan.accelerator == null ? JSONObject.NULL : plan.accelerator);
        json.put("contextWindow", plan.contextWindow);
        json.put("estimatedBytes", plan.estimatedBytes);
        json.put("estimateSource", plan.estimateSource);
        json.put("reclaimableBytes", plan.reclaimableBytes);
        json.put("marginBytes", plan.marginBytes);
        json.put("availableBytes", plan.availableBytes);
        json.put("reserveBytes", plan.reserveBytes);
        json.put("neededFreeBytes", plan.neededFreeBytes());
        json.put("measured", plan.measured);
        JSONArray evicted = new JSONArray();
        for (TaiResidency.Entry entry : plan.evicted) evicted.put(entry.modelId);
        json.put("evicted", evicted);
        return json;
    }

    @NonNull
    private TaiRuntimeOptions optionsForPreflight(
        @NonNull TaiModelSpec spec,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiLoadPreflight.Result preflight
    ) {
        if (!"auto".equals(preflight.requestedAccelerator)) return options;
        if (!"cpu".equals(preflight.effectiveAccelerator) && !"gpu".equals(preflight.effectiveAccelerator)) return options;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend) && "gpu".equals(preflight.effectiveAccelerator)) {
            return options.withAccelerator("opencl");
        }
        return options.withAccelerator(preflight.effectiveAccelerator);
    }

    private int preflightStatusCode(@NonNull TaiLoadPreflight.Result preflight) {
        String code = preflight.errorCode;
        if (code.startsWith("model_file_missing") || code.startsWith("model_file_not_readable")) return 404;
        if (code.contains("native_unavailable") || code.contains("unsupported_abi")) return 501;
        if (code.contains("low_available_memory") || code.contains("known_failed") || code.contains("requires_explicit")) return 409;
        return 400;
    }

    private void recordRuntimeResult(
        @NonNull TaiModelSpec spec,
        @NonNull TaiLoadPreflight.Result preflight,
        @NonNull JSONObject result
    ) {
        String accelerator = acceleratorFromRuntimeResult(result, preflight);
        if (result.optBoolean("ok", false)) {
            TaiRuntimeHistory.recordSuccess(appContext, spec, preflight.device, spec.backend, accelerator);
            return;
        }
        TaiRuntimeHistory.recordFailure(appContext, spec, preflight.device, spec.backend, accelerator,
            result.optString("message", result.optString("error", "load_failed")));
    }

    @NonNull
    private String acceleratorFromRuntimeResult(@NonNull JSONObject result, @NonNull TaiLoadPreflight.Result preflight) {
        String backend = result.optString("backend", preflight.effectiveAccelerator);
        String normalized = backend == null ? "" : backend.toLowerCase(Locale.ROOT);
        if (normalized.contains("gpu") || normalized.contains("opencl")) return "gpu";
        if (normalized.contains("cpu")) return "cpu";
        return preflight.effectiveAccelerator;
    }

    @NonNull
    private TaiRuntimeOptions runtimeOptionsFromRequest(@NonNull JSONObject request, @NonNull TaiModelSpec spec) {
        JSONObject suppliedOptions = runtimeProcess ? request.optJSONObject(INTERNAL_RUNTIME_OPTIONS) : null;
        TaiRuntimeOptions options = suppliedOptions == null
            ? settings.getRuntimeOptions(spec)
            : TaiRuntimeOptions.fromJson(suppliedOptions);
        Integer maxTokens = integerOverride(request, "max_tokens", integerOverride(request, "max_completion_tokens", null));
        Integer topK = integerOverride(request, "top_k", null);
        Double topP = doubleOverride(request, "top_p", null);
        Double temperature = doubleOverride(request, "temperature", null);
        Integer contextWindow = integerOverride(request, "context_window", null);
        Integer threadCount = integerOverride(request, "thread_count", null);
        String precision = stringOverride(request, "precision");
        String memoryMode = stringOverride(request, "memory_mode");
        String accelerator = null;
        if (request.has("accelerator") && !request.isNull("accelerator")) {
            String value = request.optString("accelerator", "").trim();
            accelerator = value.isEmpty() || "auto".equalsIgnoreCase(value) ? "auto" : value;
        }
        Boolean thinking = booleanOverride(request, "thinking");
        Boolean speculative = booleanOverride(request, "speculative_decoding");
        return options.withGenerationOverrides(maxTokens, topK, topP, temperature, accelerator,
            contextWindow, threadCount, precision, memoryMode, thinking, speculative);
    }

    @Nullable
    private Integer integerOverride(@NonNull JSONObject request, @NonNull String key, @Nullable Integer fallback) {
        if (!request.has(key) || request.isNull(key)) return fallback;
        try {
            return request.getInt(key);
        } catch (JSONException e) {
            return fallback;
        }
    }

    @Nullable
    private Double doubleOverride(@NonNull JSONObject request, @NonNull String key, @Nullable Double fallback) {
        if (!request.has(key) || request.isNull(key)) return fallback;
        try {
            return request.getDouble(key);
        } catch (JSONException e) {
            return fallback;
        }
    }

    @Nullable
    private Boolean booleanOverride(@NonNull JSONObject request, @NonNull String key) {
        if (!request.has(key) || request.isNull(key)) return null;
        return request.optBoolean(key);
    }

    @Nullable
    private String stringOverride(@NonNull JSONObject request, @NonNull String key) {
        if (!request.has(key) || request.isNull(key)) return null;
        String value = request.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    @NonNull
    private String requestedModelId(@NonNull JSONObject request, @NonNull String fallback) {
        String model = request.optString("model", request.optString("modelId", fallback));
        String resolved = model == null || model.trim().isEmpty() ? fallback : model.trim();
        return TaiSettings.migrateBuiltInModelId(resolved);
    }

    @Nullable
    private TaiModelSpec resolveModel(@Nullable String modelId) {
        String migratedId = modelId == null ? null : TaiSettings.migrateBuiltInModelId(modelId);
        TaiModelSpec direct = lookupBaseModel(migratedId);
        // Split exposure: the canonical id loads text-only (Gallery's chat task) and the encoders
        // are reached through "-vision"/"-audio". Combined/Both: the canonical id loads every
        // enabled modality at once.
        if (direct != null) {
            return withDeviceContextWindow(TaiModelStore.EXPOSURE_SPLIT.equals(modelStore.getExposure(direct.id))
                ? TaiModelVariants.chatScopedOrSelf(direct)
                : direct);
        }
        return withDeviceContextWindow(TaiModelVariants.resolve(migratedId, this::lookupBaseModel));
    }

    /**
     * What a client is told a chat model's window is: the loaded engine's when it is resident,
     * otherwise what a load would be given with the memory free now. Advertising the RAM tier
     * instead promised 32k to a phone that could only load 4k, and clients sized their prompts to
     * the promise.
     */
    @NonNull
    private TaiModelSpec advertisedContextWindow(@NonNull TaiModelSpec spec, @NonNull TaiDeviceCapabilities device,
                                                 @NonNull TaiRuntimePresence.Snapshot presence, boolean explicitContext) {
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) return spec;
        if (presence.loaded && spec.id.equals(presence.modelId) && presence.contextWindow > 0) {
            return spec.withEndpointContextWindow(Math.min(spec.endpointContextWindow, presence.contextWindow));
        }
        // The same credit decideLoad takes from the registry, published by the runtime process so
        // this process advertises the window the load would actually be given — the same floor,
        // measured history and GPU cap too. Evictions are not modelled here: they do not change
        // the window, only whether the load goes ahead.
        long available = device.availableMemoryBytes;
        if (available > 0L && presence.loaded) available += presence.residentChatBytes;
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(spec.backend, TaiResidency.fileBytes(spec),
            false, device.physicalMemoryBytes, available,
            TaiLoadPreflight.autoAccelerators(appContext, spec, device, TaiModelProfile.forModel(spec)),
            spec.endpointContextWindow, null, 0, explicitContext, device.memoryThresholdBytes,
            measuredHistory(spec, device), Collections.<TaiResidency.Entry>emptyList()));
        return spec.withEndpointContextWindow(Math.min(spec.endpointContextWindow, plan.contextWindow));
    }

    /**
     * Stored specs carry the catalog's conservative context floor. Everything that loads or
     * advertises a model goes through here so the runtime budget, the tool-compatibility gate and
     * {@code /v1/models} all see the same device-sized window (see {@link TaiContextWindowPolicy}).
     */
    @Nullable
    private TaiModelSpec withDeviceContextWindow(@Nullable TaiModelSpec spec) {
        if (spec == null) return null;
        return TaiContextWindowPolicy.apply(spec, deviceMemoryBytes(), settings.getRuntimeOptions(spec).contextWindow);
    }

    private long deviceMemoryBytes() {
        long cached = deviceMemoryBytes;
        if (cached >= 0L) return cached;
        long detected = TaiDeviceCapabilities.detect(appContext).memoryBytes;
        deviceMemoryBytes = Math.max(0L, detected);
        return deviceMemoryBytes;
    }

    @Nullable
    private TaiModelSpec resolveModel(@NonNull JSONObject request, @Nullable String modelId) {
        JSONObject serialized = runtimeProcess ? request.optJSONObject(INTERNAL_MODEL_SPEC) : null;
        if (serialized != null) {
            try {
                TaiModelSpec supplied = TaiModelSpec.fromJson(serialized);
                String requested = modelId == null ? "" : TaiSettings.migrateBuiltInModelId(modelId);
                if (supplied.id.equals(requested)) return supplied;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return resolveModel(modelId);
    }

    @Nullable
    private TaiModelSpec lookupBaseModel(@Nullable String modelId) {
        if (modelId == null) return null;
        TaiModelSpec spec = modelStore.getUserModel(modelId);
        if (spec != null && spec.localPath != null && !spec.localPath.trim().isEmpty()) return spec;
        // Self-heal: a catalog model whose package is on disk but isn't registered (e.g. an
        // interrupted download) is still loadable. Prefer it over an unusable/empty registration.
        // onDisk/registry specs come from the catalog, so re-apply any user capability override
        // (e.g. vision enabled on a built-in model) — otherwise generation ignores what /v1/models
        // advertises and the endpoint media gate rejects declared modalities.
        TaiModelSpec onDisk = modelStore.onDiskModelSpec(modelId);
        if (onDisk != null) return modelStore.withCapabilityOverride(onDisk);
        // A URL/imported download that registered only in the downloads list (not user-models, and
        // not a catalog entry) is advertised by /v1/models via getDownloadedReadableModels; resolve
        // it here too so it can actually be loaded and not just listed.
        TaiModelSpec downloaded = modelStore.getDownloadedReadableModels().get(modelId);
        if (downloaded != null) return modelStore.withCapabilityOverride(downloaded);
        if (spec != null) return spec;
        TaiModelSpec registryModel = registry.getModel(modelId);
        return registryModel == null ? null : modelStore.withCapabilityOverride(registryModel);
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", statusCode);
        return data;
    }

    @NonNull
    static JSONObject openAiError(@NonNull JSONObject source) throws JSONException {
        JSONObject nestedSourceError = source.optJSONObject("error");
        String code = nestedSourceError == null
            ? source.optString("error", source.optString("code", "tai_error"))
            : nestedSourceError.optString("code", source.optString("code", "tai_error"));
        String message = source.optString("message", "TAI request failed");
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", "invalid_request_error");
        error.put("code", code);

        JSONObject response = new JSONObject();
        response.put("ok", false);
        response.put("message", message);
        response.put("code", code);
        response.put("error_code", code);
        response.put("error", error);
        response.put("tai", source);
        response.put("_statusCode", source.optInt("_statusCode", 500));
        return response;
    }

    static boolean includeStreamUsage(@NonNull JSONObject request) {
        JSONObject streamOptions = request.optJSONObject("stream_options");
        return streamOptions != null && streamOptions.optBoolean("include_usage", false);
    }

    @NonNull
    static JSONObject openAiUsage(
        @NonNull JSONObject runtimeResult,
        @Nullable String promptFallback,
        @Nullable String completionFallback
    ) throws JSONException {
        JSONObject runtimeUsage = runtimeResult.optJSONObject("usage");
        boolean hasPromptTokens = runtimeUsage != null && runtimeUsage.has("prompt_tokens");
        boolean hasCompletionTokens = runtimeUsage != null && runtimeUsage.has("completion_tokens");
        int promptTokens = hasPromptTokens
            ? Math.max(0, runtimeUsage.optInt("prompt_tokens", 0))
            : approximateTokenCountFromCharacters(promptFallback);
        int completionTokens = hasCompletionTokens
            ? Math.max(0, runtimeUsage.optInt("completion_tokens", 0))
            : approximateTokenCountFromCharacters(completionFallback);
        if (!hasPromptTokens || !hasCompletionTokens) {
            runtimeResult.put("usageEstimated", true);
            runtimeResult.put("usageSource", "characters_divided_by_4");
        }
        JSONObject usage = new JSONObject();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);
        return usage;
    }

    private static int approximateTokenCountFromCharacters(@Nullable String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, (text.length() + 3) / 4);
    }

    @NonNull
    private JSONObject chatRequestError(@NonNull JSONException e) throws JSONException {
        String message = e.getMessage() == null ? "Invalid chat request" : e.getMessage();
        if (message.startsWith("unsupported_content_part:")) {
            String type = message.substring("unsupported_content_part:".length());
            return error(400, "unsupported_content_part", "Unsupported OpenAI content part: " + type);
        }
        if (message.startsWith("capability_not_supported:")) {
            String detail = message.substring("capability_not_supported:".length());
            return error(400, "capability_not_supported", detail);
        }
        if (message.startsWith("media_fetch_failed:")) {
            String detail = message.substring("media_fetch_failed:".length());
            return error(400, "media_fetch_failed", detail);
        }
        return error(400, "invalid_chat_request", message);
    }

    @Nullable
    private JSONObject unsupportedAudioOutputRequest(@NonNull JSONObject request) throws JSONException {
        JSONArray modalities = request.optJSONArray("modalities");
        if (modalities != null) {
            for (int i = 0; i < modalities.length(); i++) {
                if ("audio".equalsIgnoreCase(modalities.optString(i, ""))) {
                    return error(501, "unsupported_audio_output",
                        "Audio output is not available from the local LiteRT/MNN chat runtimes.");
                }
            }
        }
        if (request.has("audio") && !request.isNull("audio")) {
            return error(501, "unsupported_audio_output",
                "Audio output is not available from the local LiteRT/MNN chat runtimes.");
        }
        return null;
    }

    private void emitOpenAiError(@NonNull OpenAiStreamSink sink, @NonNull JSONObject source) throws JSONException, IOException {
        sink.onEvent(openAiError(source));
        sink.onDone();
    }

    private boolean modelSupportsRequestedTools(@NonNull JSONObject request, @NonNull TaiModelSpec spec) {
        JSONArray tools = request.optJSONArray("tools");
        if (tools == null || tools.length() == 0 || "none".equals(String.valueOf(request.opt("tool_choice")))) return true;
        return spec.capabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE);
    }

    /**
     * Agent TUIs attach their complete tool catalogue to even a casual text prompt. For local
     * models that cannot use tools, short-context models where that catalogue cannot fit, and MNN
     * prompt-fallback models that are not reliable under automatic tool choice, degrade only the
     * automatic request to ordinary text chat. Mobile-actions specialists are exempt from the
     * short-context degrade because making tool calls is their purpose; stripping tools makes them
     * useless, so any overflow instead surfaces as a normal context error. Explicit required/named
     * tool choices still fail closed through
     * {@link #modelSupportsRequestedTools(JSONObject, TaiModelSpec)}.
     */
    static boolean omitAutomaticToolsForCompatibility(
        @NonNull JSONObject request,
        @NonNull TaiModelSpec spec
    ) throws JSONException {
        JSONArray tools = request.optJSONArray("tools");
        if (tools == null || tools.length() == 0) return false;
        Object choice = request.opt("tool_choice");
        boolean automatic = choice == null || JSONObject.NULL.equals(choice) || "auto".equals(String.valueOf(choice));
        if (!automatic) return false;
        boolean reliableAutomaticTools = spec.capabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE)
            && !TaiModelSpec.TOOL_MODE_PROMPT_FALLBACK.equals(spec.toolMode)
            && (spec.endpointContextWindow >= 16_384
                || spec.capabilities.contains(TaiModelSpec.CAPABILITY_MOBILE_ACTIONS));
        if (reliableAutomaticTools) return false;
        request.remove("tools");
        request.put("tool_choice", "none");
        return true;
    }

    @NonNull
    private JSONObject generationCapabilityError(@NonNull TaiModelSpec spec) throws JSONException {
        return error(400, "capability_not_supported",
            "Model " + spec.id + " does not support chat or text generation. Use an embeddings endpoint for embedding-only models.");
    }

    private void emitChatChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull String content,
        @Nullable String role,
        @Nullable String finishReason
    ) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", "chat.completion.chunk");
        response.put("created", created);
        response.put("model", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        JSONObject delta = new JSONObject();
        if (role != null) delta.put("role", role);
        if (!content.isEmpty()) delta.put("content", content);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason == null ? JSONObject.NULL : finishReason);
        choices.put(choice);
        response.put("choices", choices);
        sink.onEvent(response);
    }

    private void emitChatThinkingChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull String reasoning
    ) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", "chat.completion.chunk");
        response.put("created", created);
        response.put("model", model);
        JSONObject delta = new JSONObject().put("reasoning_content", reasoning);
        JSONObject choice = new JSONObject()
            .put("index", 0)
            .put("delta", delta)
            .put("finish_reason", JSONObject.NULL);
        response.put("choices", new JSONArray().put(choice));
        sink.onEvent(response);
    }

    private void emitUsageChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull String object,
        @NonNull JSONObject usage
    ) throws JSONException, IOException {
        sink.onEvent(openAiUsageChunk(id, created, model, object, usage));
    }

    @NonNull
    static JSONObject openAiUsageChunk(
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull String object,
        @NonNull JSONObject usage
    ) throws JSONException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", object);
        response.put("created", created);
        response.put("model", model);
        response.put("choices", new JSONArray());
        response.put("usage", usage);
        return response;
    }

    private void emitCompletionChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull String text,
        @Nullable String finishReason
    ) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", "text_completion");
        response.put("created", created);
        response.put("model", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("text", text);
        choice.put("index", 0);
        choice.put("finish_reason", finishReason == null ? JSONObject.NULL : finishReason);
        choices.put(choice);
        response.put("choices", choices);
        sink.onEvent(response);
    }

    private void emitToolCallChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull JSONArray toolCalls
    ) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", "chat.completion.chunk");
        response.put("created", created);
        response.put("model", model);
        JSONObject delta = new JSONObject();
        JSONArray deltaCalls = new JSONArray();
        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject source = toolCalls.optJSONObject(i);
            if (source == null) continue;
            JSONObject call = new JSONObject(source.toString());
            call.put("index", i);
            deltaCalls.put(call);
        }
        delta.put("tool_calls", deltaCalls);
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", JSONObject.NULL);
        response.put("choices", new JSONArray().put(choice));
        sink.onEvent(response);
    }

    @NonNull
    private JSONArray currentLimitations() {
        JSONArray limitations = new JSONArray();
        limitations.put("Native LiteRT-LM and MNN inference runs in the isolated :tai_runtime Android process.");
        limitations.put("Model loads run preflight checks for ABI, API level, bundled native libraries, model files, memory pressure, accelerator policy, and known failure history.");
        limitations.put("Auto defaults to CPU on unknown devices; GPU is used automatically only after a successful model/device history.");
        limitations.put("Streaming text responses, cancellation, and keep-warm lifecycle controls are available through the localhost API.");
        limitations.put("LiteRT-LM image and audio input are accepted for models that declare those capabilities.");
        limitations.put("/v1/models lists endpoint capabilities for loadable LiteRT-LM/MNN models only; source model-card capabilities are informational.");
        limitations.put("GGUF/raw weight files are not supported because this APK does not include a GGUF/llama.cpp backend.");
        limitations.put("Audio output is not available from the local LiteRT-LM or MNN runners.");
        limitations.put("OpenAI function tools are returned for client-side execution; TAI does not automatically execute shell commands or device actions.");
        return limitations;
    }

    @NonNull
    private OpenAiChatRequest openAiChatRequest(
        @NonNull JSONObject request,
        @NonNull JSONArray messages,
        @NonNull TaiModelSpec spec
    ) throws JSONException {
        StringBuilder clientSystemPrompt = new StringBuilder();
        List<Message> conversationMessages = new ArrayList<>();
        Map<String, String> toolNamesByCallId = new LinkedHashMap<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role", "user");
            if ("system".equals(role) || "developer".equals(role)) {
                String content = messageContentToText(message.opt("content"));
                if (!content.isEmpty()) {
                    if (clientSystemPrompt.length() > 0) clientSystemPrompt.append('\n');
                    clientSystemPrompt.append(content);
                }
                continue;
            }
            if ("assistant".equals(role)) {
                String content = messageContentToText(message.opt("content"));
                List<ToolCall> calls = toolCallsFromAssistant(message, toolNamesByCallId);
                conversationMessages.add(Message.Companion.model(
                    Contents.Companion.of(content), calls, Collections.emptyMap()));
            } else if ("tool".equals(role)) {
                String callId = message.optString("tool_call_id", "");
                String toolName = message.optString("name", toolNamesByCallId.get(callId));
                if (toolName == null || toolName.isEmpty()) toolName = "tool";
                Object response = jsonCompatibleValue(message.opt("content"));
                conversationMessages.add(Message.Companion.tool(
                    Contents.Companion.of(new Content.ToolResponse(toolName, response))));
            } else {
                conversationMessages.add(Message.Companion.user(messageContentToContents(message.opt("content"), spec)));
            }
        }
        if (conversationMessages.isEmpty()) {
            throw new JSONException("Chat request has no user, assistant, or tool messages");
        }

        String systemPrompt = clientSystemPrompt.length() > 0
            ? clientSystemPrompt.toString() : settings.getSystemPrompt(spec.id);
        JSONArray toolsJson = request.optJSONArray("tools");
        List<ToolProvider> tools = toolProviders(toolsJson, request.opt("tool_choice"));
        systemPrompt = applyToolChoiceInstruction(systemPrompt, request.opt("tool_choice"), toolsJson);

        Message finalMessage = conversationMessages.remove(conversationMessages.size() - 1);
        if (finalMessage.getRole() != com.google.ai.edge.litertlm.Role.USER
            && finalMessage.getRole() != com.google.ai.edge.litertlm.Role.TOOL) {
            throw new JSONException("The final chat message must have role user or tool");
        }
        // Reusable: the runtime keeps the conversation alive across stateless OpenAI requests when
        // the new transcript continues the previous one, so a chat client's next turn prefills
        // only the new message instead of the whole history (see TaiConversationTranscript).
        return new OpenAiChatRequest(new TaiChatRequest(
            systemPrompt, conversationMessages, finalMessage, tools, true,
            messages, toolsJson, request.opt("tool_choice"), OpenAiStopSequences.fromRequest(request)));
    }

    @NonNull
    static List<ToolProvider> toolProviders(@Nullable JSONArray tools, @Nullable Object toolChoice) throws JSONException {
        if (tools == null || tools.length() == 0 || "none".equals(String.valueOf(toolChoice))) {
            return Collections.emptyList();
        }
        List<ToolProvider> providers = new ArrayList<>();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null || !"function".equals(tool.optString("type", ""))) {
                throw new JSONException("Only OpenAI function tools are supported");
            }
            JSONObject function = tool.optJSONObject("function");
            if (function == null || function.optString("name", "").isEmpty()) {
                throw new JSONException("Each function tool requires a name");
            }
            JSONObject liteRtDescription = new JSONObject();
            liteRtDescription.put("name", function.getString("name"));
            if (function.has("description")) liteRtDescription.put("description", function.optString("description", ""));
            if (function.has("parameters")) liteRtDescription.put("parameters", function.opt("parameters"));
            String description = liteRtDescription.toString();
            providers.add(ToolKt.tool(new OpenApiTool() {
                @NonNull
                @Override
                public String getToolDescriptionJsonString() {
                    return description;
                }

                @NonNull
                @Override
                public String execute(@NonNull String paramsJsonString) {
                    throw new UnsupportedOperationException("TAI uses client-side tool execution.");
                }
            }));
        }
        if (providers.isEmpty()) throw new JSONException("No valid function tools were provided");
        return providers;
    }

    @NonNull
    static List<ToolCall> toolCallsFromAssistant(
        @NonNull JSONObject message,
        @NonNull Map<String, String> toolNamesByCallId
    ) throws JSONException {
        JSONArray calls = message.optJSONArray("tool_calls");
        if (calls == null) return Collections.emptyList();
        List<ToolCall> output = new ArrayList<>();
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.optJSONObject(i);
            JSONObject function = call == null ? null : call.optJSONObject("function");
            if (function == null) continue;
            String name = function.optString("name", "");
            if (name.isEmpty()) continue;
            String callId = call.optString("id", "");
            if (!callId.isEmpty()) toolNamesByCallId.put(callId, name);
            Object argumentsValue = function.opt("arguments");
            JSONObject arguments;
            if (argumentsValue instanceof JSONObject) {
                arguments = (JSONObject) argumentsValue;
            } else {
                String argumentsText = argumentsValue == null ? "{}" : String.valueOf(argumentsValue);
                arguments = argumentsText.trim().isEmpty() ? new JSONObject() : new JSONObject(argumentsText);
            }
            output.add(new ToolCall(name, jsonObjectToMap(arguments)));
        }
        return output;
    }

    @NonNull
    static String applyToolChoiceInstruction(
        @NonNull String systemPrompt,
        @Nullable Object toolChoice,
        @Nullable JSONArray tools
    ) {
        if (toolChoice == null || JSONObject.NULL.equals(toolChoice) || tools == null || tools.length() == 0) {
            return systemPrompt;
        }
        if ("required".equals(String.valueOf(toolChoice))) {
            return systemPrompt + "\nYou must call one of the provided tools for this response.";
        }
        if (toolChoice instanceof JSONObject) {
            JSONObject function = ((JSONObject) toolChoice).optJSONObject("function");
            String name = function == null ? "" : function.optString("name", "");
            if (!name.isEmpty()) return systemPrompt + "\nYou must call the provided tool named " + name + ".";
        }
        return systemPrompt;
    }

    @NonNull
    static Map<String, Object> jsonObjectToMap(@NonNull JSONObject object) {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonCompatibleValue(object.opt(key)));
        }
        return map;
    }

    @Nullable
    static Object jsonCompatibleValue(@Nullable Object value) {
        if (value == null || JSONObject.NULL.equals(value)) return null;
        if (value instanceof JSONObject) return jsonObjectToMap((JSONObject) value);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) list.add(jsonCompatibleValue(array.opt(i)));
            return list;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                try {
                    return text.startsWith("{")
                        ? jsonObjectToMap(new JSONObject(text))
                        : jsonCompatibleValue(new JSONArray(text));
                } catch (JSONException ignored) {
                }
            }
        }
        return value;
    }

    @NonNull
    private String promptFromCompletionRequest(@NonNull JSONObject request) {
        Object prompt = request.opt("prompt");
        if (prompt == null || JSONObject.NULL.equals(prompt)) return "";
        if (prompt instanceof JSONArray) {
            JSONArray array = (JSONArray) prompt;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(String.valueOf(array.opt(i)));
            }
            return builder.toString();
        }
        return String.valueOf(prompt);
    }

    @NonNull
    private String messageContentToText(@Nullable Object content) throws JSONException {
        if (content == null || JSONObject.NULL.equals(content)) return "";
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    String type = object.optString("type", "");
                    if ("text".equals(type)) {
                        builder.append(object.optString("text", ""));
                    } else if ("image_url".equals(type) || "input_image".equals(type)
                        || "audio".equals(type) || "input_audio".equals(type)) {
                        throw new JSONException("unsupported_content_part:" + type);
                    } else if (!type.isEmpty()) {
                        throw new JSONException("unsupported_content_part:" + type);
                    }
                } else if (item != null && !JSONObject.NULL.equals(item)) {
                    builder.append(String.valueOf(item));
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    @NonNull
    static Contents messageContentToContents(@Nullable Object content, @NonNull TaiModelSpec spec) throws JSONException {
        if (content == null || JSONObject.NULL.equals(content)) return Contents.Companion.of("");
        if (!(content instanceof JSONArray)) return Contents.Companion.of(String.valueOf(content));
        JSONArray array = (JSONArray) content;
        ArrayList<Content> contents = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                String type = object.optString("type", "");
                if ("text".equals(type)) {
                    contents.add(new Content.Text(object.optString("text", "")));
                } else if ("image_url".equals(type) || "input_image".equals(type)) {
                    // Image is real on LiteRT and best-effort on MNN VL models; the endpoint
                    // capability gate (set in endpointCapabilitiesFor) is the single source of truth.
                    if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)) {
                        throw new JSONException("capability_not_supported:Model " + spec.id + " does not support image input through this endpoint.");
                    }
                    contents.add(imageContent(object));
                } else if ("input_audio".equals(type) || "audio".equals(type)) {
                    if (!TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend)
                        || !spec.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT)) {
                        throw new JSONException("capability_not_supported:Model " + spec.id + " does not support audio input through this endpoint.");
                    }
                    contents.add(audioContent(object));
                } else if (!type.isEmpty()) {
                    throw new JSONException("unsupported_content_part:" + type);
                }
            } else if (item != null && !JSONObject.NULL.equals(item)) {
                contents.add(new Content.Text(String.valueOf(item)));
            }
        }
        if (contents.isEmpty()) contents.add(new Content.Text(""));
        return Contents.Companion.of(contents);
    }

    @NonNull
    private static Content imageContent(@NonNull JSONObject part) throws JSONException {
        Object imageUrlValue = part.opt("image_url");
        String url = "";
        if (imageUrlValue instanceof JSONObject) {
            url = ((JSONObject) imageUrlValue).optString("url", "");
        } else if (imageUrlValue != null && !JSONObject.NULL.equals(imageUrlValue)) {
            url = String.valueOf(imageUrlValue);
        }
        if (url.trim().isEmpty()) url = part.optString("image_url", "");
        if (url.trim().isEmpty()) throw new JSONException("unsupported_content_part:image_url");
        return contentFromUrl(url, true);
    }

    @NonNull
    private static Content audioContent(@NonNull JSONObject part) throws JSONException {
        JSONObject inputAudio = part.optJSONObject("input_audio");
        if (inputAudio == null) inputAudio = part.optJSONObject("audio");
        if (inputAudio != null) {
            String data = inputAudio.optString("data", "");
            if (!data.trim().isEmpty()) {
                return new Content.AudioBytes(decodeBase64(data, "input_audio"));
            }
            String url = inputAudio.optString("url", "");
            if (!url.trim().isEmpty()) return contentFromUrl(url, false);
        }
        String url = part.optString("audio_url", "");
        if (!url.trim().isEmpty()) return contentFromUrl(url, false);
        throw new JSONException("unsupported_content_part:input_audio");
    }

    @NonNull
    private static Content contentFromUrl(@NonNull String rawUrl, boolean image) throws JSONException {
        String url = rawUrl.trim();
        if (url.startsWith("data:")) {
            return image
                ? new Content.ImageBytes(decodeDataUrl(url, "image_url"))
                : new Content.AudioBytes(decodeDataUrl(url, "input_audio"));
        }
        if (url.startsWith("file://")) {
            String path = Uri.parse(url).getPath();
            if (path == null || path.trim().isEmpty()) throw new JSONException("media_fetch_failed:Empty file URL");
            String resolved = TaiMediaAccess.resolveLocalPath(path);
            return image ? new Content.ImageFile(resolved) : new Content.AudioFile(resolved);
        }
        if (url.startsWith("/")) {
            String resolved = TaiMediaAccess.resolveLocalPath(url);
            return image ? new Content.ImageFile(resolved) : new Content.AudioFile(resolved);
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            byte[] bytes = fetchMedia(url);
            return image ? new Content.ImageBytes(bytes) : new Content.AudioBytes(bytes);
        }
        throw new JSONException("media_fetch_failed:Unsupported media URL scheme");
    }

    @NonNull
    private static byte[] decodeDataUrl(@NonNull String dataUrl, @NonNull String partName) throws JSONException {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) throw new JSONException("media_fetch_failed:Malformed data URL for " + partName);
        String metadata = dataUrl.substring(0, comma).toLowerCase(Locale.ROOT);
        if (!metadata.contains(";base64")) {
            throw new JSONException("media_fetch_failed:Only base64 data URLs are supported for " + partName);
        }
        return decodeBase64(dataUrl.substring(comma + 1), partName);
    }

    @NonNull
    private static byte[] decodeBase64(@NonNull String value, @NonNull String partName) throws JSONException {
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(value);
            if (bytes.length > MAX_MEDIA_BYTES) throw new JSONException("media_fetch_failed:" + partName + " exceeds 25 MB");
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new JSONException("media_fetch_failed:Invalid base64 for " + partName);
        }
    }

    @NonNull
    private static byte[] fetchMedia(@NonNull String url) throws JSONException {
        return TaiMediaAccess.fetch(url, MAX_MEDIA_BYTES);
    }

    @NonNull
    private static JSONArray openAiEndpointCapabilities(
        @NonNull String id,
        @NonNull JSONArray capabilities,
        @NonNull String backend,
        @NonNull String format
    ) {
        LinkedHashSet<String> source = new LinkedHashSet<>();
        for (int i = 0; i < capabilities.length(); i++) {
            String capability = capabilities.optString(i, "");
            if (!capability.isEmpty()) source.add(capability);
        }
        LinkedHashSet<String> endpoint = TaiModelSpec.endpointCapabilitiesFor(id, backend, format, source, null);
        JSONArray filtered = new JSONArray();
        for (String capability : endpoint) filtered.put(capability);
        return filtered;
    }

    @NonNull
    private LinkedHashSet<String> capabilitiesFromRequest(@NonNull JSONObject request, @NonNull String modelId,
                                                           @Nullable String artifactHint) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        JSONArray array = request.optJSONArray("capabilities");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String capability = array.optString(i, "");
                if (!capability.isEmpty()) capabilities.add(capability);
            }
        }
        if (capabilities.isEmpty()) {
            String normalized = (modelId + " " + (artifactHint == null ? "" : artifactHint)).toLowerCase(Locale.ROOT);
            // Strip any URL query/fragment (e.g. "...tflite?download=true") before matching the extension,
            // otherwise a downloaded embedding model is misclassified as a chat model and never gets its
            // SentencePiece tokenizer sidecar.
            int cut = normalized.indexOf('?');
            if (cut >= 0) normalized = normalized.substring(0, cut);
            cut = normalized.indexOf('#');
            if (cut >= 0) normalized = normalized.substring(0, cut);
            if (normalized.endsWith(".tflite")) {
                capabilities.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
            } else {
                capabilities.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
            }
            String identity = normalized.replaceAll("[^a-z0-9]", "");
            if (identity.contains("qwen34bthinking2507")) {
                capabilities.add("reasoning");
                capabilities.add(TaiModelSpec.CAPABILITY_LLM_THINKING);
            }
        }
        return capabilities;
    }

    @NonNull
    private String sanitizeModelId(@NonNull String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "-");
    }

    @NonNull
    private JSONArray catalogJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            JSONObject json = new JSONObject();
            json.put("modelId", entry.modelId);
            json.put("displayName", entry.displayName);
            json.put("roleHint", entry.roleHint);
            json.put("jobGroup", entry.jobGroup);
            json.put("priority", entry.priority);
            json.put("providerPageUrl", entry.providerPageUrl);
            json.put("downloadUrl", entry.downloadUrl == null ? JSONObject.NULL : entry.downloadUrl);
            json.put("downloadAvailable", entry.downloadAvailable);
            json.put("unavailableReason", entry.unavailableReason);
            json.put("license", entry.license);
            json.put("sizeBytes", entry.sizeBytes);
            json.put("sizeEstimate", entry.sizeEstimate);
            json.put("ramTier", entry.ramTier);
            json.put("recommended", entry.recommended);
            json.put("gated", entry.gated);
            json.put("backend", entry.backend);
            json.put("format", entry.format);
            json.put("architecture", entry.architecture);
            json.put("quantization", entry.quantization == null ? JSONObject.NULL : entry.quantization);
            json.put("contextWindow", entry.contextWindow);
            json.put("endpointContextWindow", entry.endpointContextWindow);
            json.put("sourceContextWindow", entry.sourceContextWindow);
            json.put("defaultMaxOutputTokens", entry.defaultMaxOutputTokens);
            json.put("recommendedRamGb", entry.recommendedRamGb);
            json.put("revision", entry.revision);
            json.put("sha256", entry.sha256 == null ? JSONObject.NULL : entry.sha256);
            json.put("toolMode", entry.toolMode == null ? JSONObject.NULL : entry.toolMode);
            TaiModelSpec catalogSpec = registry.getModel(entry.modelId);
            if (catalogSpec != null) json.put("runtimeProfile", TaiModelProfile.forModel(catalogSpec).toJson());
            JSONArray capabilities = new JSONArray();
            for (String capability : entry.capabilities) capabilities.put(capability);
            json.put("capabilities", capabilities);
            JSONArray endpointCapabilities = new JSONArray();
            for (String capability : entry.endpointCapabilities) endpointCapabilities.put(capability);
            json.put("endpointCapabilities", endpointCapabilities);
            JSONArray sourceCapabilities = new JSONArray();
            for (String capability : entry.sourceCapabilities) sourceCapabilities.put(capability);
            json.put("sourceCapabilities", sourceCapabilities);
            JSONArray displayCapabilityTags = new JSONArray();
            for (String tag : entry.displayCapabilityTags) displayCapabilityTags.put(tag);
            json.put("displayCapabilityTags", displayCapabilityTags);
            array.put(json);
        }
        return array;
    }

    private void appendDeviceCompatibility(@NonNull JSONObject data, @NonNull TaiRuntimeState state) throws JSONException {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        data.put("device", device.toJson());
        if (state.loadedModelId == null) return;
        TaiModelSpec model = resolveModel(state.loadedModelId);
        if (model == null) return;
        TaiModelProfile profile = TaiModelProfile.forModel(model);
        data.put("modelProfile", profile.toJson());
        JSONArray warnings = new JSONArray();
        String memoryWarning = device.memoryWarning(profile);
        if (memoryWarning != null) warnings.put(memoryWarning);
        if (model.recommendedRamGb > 0 && device.memoryBytes > 0L
            && device.memoryBytes < model.recommendedRamGb * 1024L * 1024L * 1024L) {
            warnings.put("Device memory is below this model's recommendation of " + model.recommendedRamGb + " GiB.");
        }
        data.put("compatibilityWarnings", warnings);
    }

    private void appendCrashMarker(@NonNull JSONObject data) throws JSONException {
        JSONObject marker = TaiRuntimeCrashMarker.read(appContext);
        if (marker != null) data.put("lastRuntimeCrash", marker);
    }

    private static final class OpenAiChatRequest {
        final TaiChatRequest request;

        OpenAiChatRequest(@NonNull TaiChatRequest request) {
            this.request = request;
        }
    }
}
