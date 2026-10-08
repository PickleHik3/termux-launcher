package com.termux.ai;

import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class TaiManager {
    private static TaiManager instance;
    private static final int MAX_MEDIA_BYTES = 25 * 1024 * 1024;
    private static final String INTERNAL_MODEL_SPEC = "_taiModelSpec";
    private static final String INTERNAL_RUNTIME_OPTIONS = "_taiRuntimeOptions";
    /** The feature load plan the app process resolved for a request that names its feature. */
    private static final String INTERNAL_FEATURE_PLAN = "_taiFeaturePlan";
    /** The STT idle-unload setting, carried to the runtime process with every speech request. */
    private static final String INTERNAL_STT_IDLE_MINUTES = "_taiSttIdleUnloadMinutes";
    /** Where the app process puts audio for the runtime process; files here are deleted once transcribed. */
    public static final String STT_IPC_DIR = "tai-ipc";

    private final Context appContext;
    private final TaiSettings settings;
    private final TaiModelRegistry registry;
    private final TaiModelStore modelStore;
    private final TaiModelDownloader modelDownloader;
    /** The download queue; null in the runtime process, which never downloads. */
    @Nullable private final TaiDownloadEngine downloadEngine;
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
    /** In the runtime process: the benchmark in progress, which cancel and unload stop first. */
    @Nullable private volatile TaiBenchHarness activeBench;
    /** In the app process: a benchmark stream in flight; a second request is refused. */
    private final AtomicBoolean benchStreamActive = new AtomicBoolean();

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
        // Building the engine reconciles: a download the last process died under reads as paused
        // from the first status call, never as a "downloading" nothing is doing.
        downloadEngine = runtimeProcess ? null : TaiDownloadEngine.getInstance(appContext);
        runtime = runtimeProcess ? new MultiBackendTaiRuntime(appContext) : null;
        runtimeClient = runtimeProcess ? null : new TaiRuntimeServiceClient(appContext);
        if (!runtimeProcess) {
            // The wallpaper vision models are gone; delete any left on the phone, once, off the main thread.
            Thread visionCleanup = new Thread(() -> TaiVisionLeftovers.cleanOnce(appContext), "tai-vision-cleanup");
            visionCleanup.setDaemon(true);
            visionCleanup.start();
        }
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
        if (runtimeClient == null) return error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
        return runtimeClient.request(operation, body == null ? "{}" : body);
    }

    @NonNull
    private JSONObject runtimeRequest(@NonNull String operation, @Nullable String body, long timeoutMs) throws JSONException {
        if (runtimeClient == null) return error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
        return runtimeClient.request(operation, body == null ? "{}" : body, timeoutMs);
    }

    /** Supplies the isolated runtime with the model definition resolved by the authoritative app process. */
    @NonNull
    private String delegatedRuntimeBody(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        request.remove(INTERNAL_MODEL_SPEC);
        request.remove(INTERNAL_RUNTIME_OPTIONS);
        request.remove(INTERNAL_FEATURE_PLAN);
        // A request that names its feature loads by that feature's plan, resolved here where the picks live.
        TaiFunction feature = TaiCallerRequests.featureOf(request);
        TaiFeaturePlan plan = feature == null ? null : TaiFeaturePlans.forContext(appContext).plan(feature);
        if (plan != null && plan.where == TaiFeaturePlan.Where.ON_DEVICE && !namesModel(request)) {
            request.put("model", plan.requestModel());
        }
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(modelId);
        if (spec != null) {
            request.put("model", spec.id);
            request.put(INTERNAL_MODEL_SPEC, spec.toJson());
            request.put(INTERNAL_RUNTIME_OPTIONS, settings.getRuntimeOptions(spec).toJson());
        }
        if (plan != null && plan.where == TaiFeaturePlan.Where.ON_DEVICE) request.put(INTERNAL_FEATURE_PLAN, plan.toJson());
        return request.toString();
    }

    /**
     * App process: a request that names its feature and no model asks for the plan's model, local or
     * {@code remote/<id>}, so the remote routing that follows sees it. Anything else is left as it is.
     */
    @NonNull
    private String withFeatureModel(@NonNull String body) {
        if (runtimeProcess || !body.contains("\"" + TaiCallerRequests.FUNCTION + "\"")) return body;
        try {
            JSONObject request = parseBody(body);
            TaiFunction feature = TaiCallerRequests.featureOf(request);
            if (feature == null || namesModel(request)) return body;
            String model = TaiFeaturePlans.forContext(appContext).plan(feature).requestModel();
            return model == null ? body : request.put("model", model).toString();
        } catch (JSONException | RuntimeException e) {
            return body;
        }
    }

    private static boolean namesModel(@NonNull JSONObject request) {
        return !request.optString("model", request.optString("modelId", "")).trim().isEmpty();
    }

    @NonNull
    private TaiRuntime localRuntime() {
        if (runtime == null) throw new IllegalStateException("On-device AI native runtime is only available in " + TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
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
        data.put("displayName", "On-device AI");
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
        endpoints.put("/v1/tokenize");
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
        JSONArray residents = residentsJson(remote);
        data.put("residents", residents);
        data.put("embedder", embedderStateJson(residents));
        data.put("settings", settings.toJson());
        data.put("appProcessRuntime", false);
        data.put("runtimeProcess", TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
        data.put("backendPolicy", "On-device AI loads native LiteRT-LM/MNN backends in an isolated Android process after preflight checks.");
        data.put("runtimeHistory", TaiRuntimeHistory.summary(appContext));
        appendCrashMarker(data);
        appendDeviceCompatibility(data, state);
        return data;
    }

    /**
     * Dawn brief item 6: whether an embedder is resident right now, next to the chat model's own
     * {@code runtime.loaded} — read off the resident table so it works identically whether this
     * call answers locally or through {@link #shouldDelegateRuntime}'s round trip. Dawn runs quiet
     * background indexing only while this is cheap, i.e. the embedder is already resident.
     */
    @NonNull
    private static JSONObject embedderStateJson(@NonNull JSONArray residents) throws JSONException {
        JSONObject embedder = new JSONObject();
        JSONObject embedding = null;
        for (int i = 0; i < residents.length(); i++) {
            JSONObject entry = residents.optJSONObject(i);
            if (entry != null && "embedding".equals(entry.optString("kind", ""))) {
                embedding = entry;
                break;
            }
        }
        embedder.put("loaded", embedding != null);
        if (embedding != null) {
            embedder.put("modelId", embedding.optString("id", ""));
            embedder.put("backend", embedding.optString("backend", ""));
            // The size the model takes loaded, from its file; the load meter reads far less for an
            // mmapped graph (26 MB for EmbeddingGemma's 183 MB file on pong), so it is reported apart.
            long estimated = embedding.optLong("estimatedBytes", 0L);
            long measured = embedding.isNull("measuredBytes") ? 0L : embedding.optLong("measuredBytes", 0L);
            embedder.put("residentMb", Math.max(estimated, measured) / (1024L * 1024L));
            if (measured > 0L) embedder.put("measuredLoadMb", measured / (1024L * 1024L));
            embedder.put("busy", embedding.optBoolean("busy", false));
        }
        return embedder;
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
        File imageDirectory = diffusionImportDirectory(modelFile);
        if (imageDirectory != null) return importDiffusionPackage(request, imageDirectory, path);
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
                "On-device AI can import LiteRT-LM packages and MNN config packages only. GGUF/raw weights require a backend this APK does not include.");
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
        data.put("message", "Model path registered. Load it with On-device AI to run through the isolated Android LiteRT-LM runtime when preflight passes.");
        return data;
    }

    /**
     * The text-to-image package folder {@code target} is or sits in (Stable Diffusion, Taiyi, Sana), or
     * null for anything else, so {@code tai import} takes a package by its folder or by any file in it.
     */
    @Nullable
    private static File diffusionImportDirectory(@NonNull File target) {
        File directory = target.isDirectory() ? target : target.isFile() ? target.getParentFile() : null;
        // Sana's llm/ holds its own config.json and graphs; a file in it belongs to the package above.
        if (directory != null && directory.getName().equals("llm") && directory.getParentFile() != null
            && TaiDiffusionImport.detectLayout(rootNames(directory.getParentFile())) != TaiDiffusionPackage.TYPE_AUTO) {
            directory = directory.getParentFile();
        }
        if (directory == null) return null;
        return TaiDiffusionImport.detectLayout(rootNames(directory)) == TaiDiffusionPackage.TYPE_AUTO ? null : directory;
    }

    @NonNull
    private static List<String> rootNames(@NonNull File directory) {
        List<String> names = new ArrayList<>();
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) if (child.isFile()) names.add(child.getName());
        return names;
    }

    /**
     * Registers a text-to-image folder where it is (as {@code tai import} does for any model). A
     * Stable Diffusion folder that ships the raw CLIP tokenizer gets the app's converted
     * {@code tokenizer.mtok} written beside it.
     */
    @NonNull
    private JSONObject importDiffusionPackage(@NonNull JSONObject request, @NonNull File directory,
                                              @NonNull String requestedPath) throws JSONException {
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", directory.getName())));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        int layout = TaiDiffusionImport.detectLayout(rootNames(directory));
        int hinted = TaiDiffusionPackage.parseType(request.optString("modelType", request.optString("type", "")));
        int type = hinted >= 0 && (layout == TaiDiffusionPackage.TYPE_SANA) == (hinted == TaiDiffusionPackage.TYPE_SANA)
            ? hinted : TaiDiffusionImport.typeFor(layout, directory.getName());
        if (type != TaiDiffusionPackage.TYPE_SANA) {
            TaiDiffusionTokenizer.Result tokenizer = TaiDiffusionTokenizer.ensure(directory, appContext);
            if (!tokenizer.proceed()) {
                JSONObject refused = error(400, "unsupported_image_model", tokenizer.message);
                refused.put("path", requestedPath);
                return refused;
            }
        }
        TaiDiffusionPackage.Result checked = TaiDiffusionPackage.inspect(directory, type);
        if (!checked.ok()) {
            JSONObject refused = error(400, "unsupported_image_model", checked.message);
            refused.put("path", requestedPath);
            return refused;
        }
        TaiModelSpec spec = TaiDiffusionImport.spec(modelId, request.optString("displayName", modelId), "imported",
            request.optString("license", "User-provided model; license accepted externally"),
            directory.getAbsolutePath(), checked.type, checked.totalBytes);
        modelStore.upsertUserModel(spec);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("imported", true);
        data.put("model", spec.toJson());
        data.put("requiresUserApprovedPath", true);
        data.put("copiedIntoAppPrivateStorage", false);
        data.put("message", "Image model registered. Generate with tai image --model " + modelId + ".");
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

    /** The image model type ({@code sd15}, {@code taiyi}, {@code sana}) a picked folder holds, or "". */
    @NonNull
    public String diffusionTypeOfFolder(@NonNull Uri tree) {
        return new TaiModelImporter(appContext, modelStore).diffusionTypeOfFolder(tree);
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
            boolean previewOnly = request.optBoolean("previewOnly", false);
            // The card is only read for a preview: it feeds the import flow's picker and summary,
            // and a real download (or an API client) has no use for it.
            TaiModelDownloader.HfResolve resolved = modelDownloader.resolveHuggingFaceEntry(url, token, previewOnly);
            if (resolved.authRequired) {
                JSONObject gated = error(403, "gated_model_requires_auth",
                    "This Hugging Face repo is gated or private. Save your Hugging Face access token "
                    + "(after accepting the model's terms on huggingface.co) and try again.");
                gated.put("huggingFaceTokenBundled", false);
                return gated;
            }
            if (resolved.candidates.length() > 1 || previewOnly && resolved.candidates.length() > 0) {
                JSONObject choices = error(409, "artifact_selection_required", "Choose a model file to download.");
                choices.put("candidates", resolved.candidates);
                choices.put("modelFacts", resolved.facts);
                if (!resolved.readme.isEmpty()) choices.put("modelCard", resolved.readme);
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
        // A transfer still running for this model would re-create its record and files under the
        // delete; stop it first (its partial files go with it).
        if (downloadEngine != null && request.optBoolean("confirm", false)) downloadEngine.cancel(modelId);
        TaiModelStore.DeleteResult deleteResult = modelStore.deleteUserModel(modelId,
            activeModelLoaded, request.optBoolean("confirm", false));
        if (!deleteResult.ok) return error(409, deleteResult.errorCode, deleteResult.message);
        // The model's runtime history and bench records go with it: they are keyed by its id and
        // nothing prunes them otherwise. (Bench results used to be kept after a delete.)
        pruneModelRecords(modelId);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("deleted", deleteResult.deleted);
        data.put("modelId", modelId);
        return data;
    }

    /** Drops a deleted model's runtime-history entries and benchmark records; failures only cost disk. */
    private void pruneModelRecords(@NonNull String modelId) {
        TaiRuntimeHistory.removeModel(appContext, modelId);
        try {
            benchStore().removeModel(modelId);
        } catch (IOException ignored) {
        }
    }

    @NonNull
    public JSONObject downloads() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("downloads", modelStore.getDownloads());
        data.put("parallel", settings.getDownloadParallel());
        JSONArray running = new JSONArray();
        if (downloadEngine != null) for (String id : downloadEngine.runningTransferIds()) running.put(id);
        data.put("running", running);
        return data;
    }

    /** Stops a download and deletes its partial files. */
    @NonNull
    public JSONObject cancelDownload(@NonNull String body) throws JSONException {
        return downloadAction(body, "cancel");
    }

    /** Stops a download and keeps its partial file; {@link #resumeDownload} continues it. */
    @NonNull
    public JSONObject pauseDownload(@NonNull String body) throws JSONException {
        return downloadAction(body, "pause");
    }

    /** Re-queues a paused, failed or cancelled download; it continues from the bytes it has. */
    @NonNull
    public JSONObject resumeDownload(@NonNull String body) throws JSONException {
        return downloadAction(body, "resume");
    }

    /** "Start now": moves a queued download to the front, swapping out the oldest running one. */
    @NonNull
    public JSONObject prioritizeDownload(@NonNull String body) throws JSONException {
        return downloadAction(body, "prioritize");
    }

    @NonNull
    private JSONObject downloadAction(@NonNull String body, @NonNull String action) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (downloadEngine == null) return error(500, "downloads_unavailable", "Downloads run in the app process only.");
        boolean applied;
        switch (action) {
            case "pause": applied = downloadEngine.pause(modelId, TaiModelStore.PAUSED_USER); break;
            case "resume": applied = downloadEngine.resume(modelId); break;
            case "prioritize": applied = downloadEngine.prioritize(modelId); break;
            default: applied = downloadEngine.cancel(modelId); break;
        }
        JSONObject record = modelStore.findDownloadForModel(modelId);
        if (!applied && record == null) return error(404, "download_not_found", "No download for model " + modelId);
        if (!applied) {
            JSONObject conflict = error(409, "download_state_conflict",
                "Download for " + modelId + " is " + record.optString("status", "unknown") + "; cannot " + action + " it.");
            conflict.put("transfer", record);
            return conflict;
        }
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("modelId", modelId);
        data.put("action", action);
        // The old field name, for the CLI and scripts that read it.
        if ("cancel".equals(action)) data.put("cancellationRequested", true);
        if (record != null) data.put("transfer", record);
        return data;
    }

    @NonNull
    public JSONObject loadModel(@NonNull String body) throws JSONException {
        body = withFeatureModel(body);
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown model: " + modelId);
        if (spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
                && !spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            return error(400, "embedding_model_not_loadable",
                "Model " + modelId + " is an embedding model. It is served on demand via /v1/embeddings and "
                    + "/api/embed and does not need to be loaded into the generation runtime.");
        }
        if (spec.isImageGeneration()) {
            // Image models never enter the chat runtime: MnnDiffusionRuntime loads them on demand.
            return error(400, "image_model_not_loadable",
                "Model " + modelId + " is an image model. It is served on demand via /v1/ai/images/generations "
                    + "and tai image and does not load into the chat runtime.");
        }
        if (spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) {
            // Speech models never enter the chat runtime: WhisperSttRuntime loads them on demand
            // behind /v1/audio/transcriptions and tai transcribe (or ahead of time via sttWarm).
            return error(400, "speech_model_not_loadable",
                "Model " + modelId + " is a speech-to-text model. It is served on demand via /v1/audio/transcriptions "
                    + "and tai transcribe and does not load into the chat runtime.");
        }
        if (spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)) {
            // Voice models load on demand behind /v1/audio/speech, tai speak and Read aloud.
            return error(400, "tts_model_not_loadable",
                "Model " + modelId + " is a voice model. It is served on demand via /v1/audio/speech "
                    + "and tai speak and does not load into the chat runtime.");
        }
        if (spec.isVisionTool()) {
            // Leftover vision tool models are never a chat target.
            return error(400, "vision_model_not_loadable",
                "Model " + modelId + " is a vision tool model and does not load into the chat runtime.");
        }
        String requestedBackend = request.optString("backend", "").trim();
        if (!requestedBackend.isEmpty() && !requestedBackend.equalsIgnoreCase(spec.backend)) {
            return error(409, "backend_mismatch", "Model " + modelId + " requires backend " + spec.backend + ".");
        }
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_LOAD_MODEL, delegatedRuntimeBody(body));
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        // A feature's load reuses its model when the runtime holds it with a window as large as the plan's.
        JSONObject reused = reuseForFeature(spec, options);
        if (reused != null) return reused;
        // The reset hook (tai load --fresh, or clearCache on the load routes directly): thrown away
        // before the load itself picks the fingerprinted directory, so the model always rebuilds
        // its converted-weight cache from scratch instead of trusting whatever is on disk.
        if (request.optBoolean("clearCache", false) && TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)) {
            MnnTaiRuntime.clearMmapCache(appContext, spec.id);
        }
        if (hasInjectedRuntimeOverride()) {
            JSONObject result = localRuntime().load(spec, options);
            return result;
        }
        TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options, false);
        if (preflight.blocked) {
            // Only a refusal that says something about the accelerator belongs in its history. Free
            // memory running short is a moment, a missing or unreadable file is the download not
            // being done yet, and a known failure is the record itself: writing any of them down
            // locked the GPU out for good (a load tried mid-download left "failed on gpu: Download
            // or import this model" behind, and automatic loads were refused from then on).
            if (TaiRuntimeHistory.isAcceleratorVerdict(preflight.errorCode)) {
                TaiRuntimeHistory.recordFailure(appContext, spec, preflight.device, spec.backend,
                    preflight.effectiveAccelerator, preflight.message);
            }
            return preflight.blockingError(preflightStatusCode(preflight));
        }
        LoadDecision decision = decideLoad(spec, options, preflight);
        if (decision.refusal != null) return decision.refusal;
        JSONObject result = loadWithCanary(spec, decision.options);
        result.put("preflight", preflight.toJson());
        decision.describe(result);
        recordRuntimeResult(spec, preflight, result);
        if (result.optBoolean("ok", false)) markUsedByFeature(spec, options);
        return result;
    }

    @NonNull
    public JSONObject unloadModel() throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_UNLOAD_MODEL, "{}");
        // An unload under a running benchmark ends the benchmark: its entry is recorded as
        // stopped and the phases that finished are kept.
        TaiBenchHarness bench = activeBench;
        if (bench != null) bench.requestStop("unloaded");
        return localRuntime().unload();
    }

    @NonNull
    public JSONObject keepWarmRuntime(@NonNull String body) throws JSONException {
        body = withFeatureModel(body);
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_KEEP_WARM, delegatedRuntimeBody(body));
        JSONObject request = parseBody(body);
        TaiRuntimeState state = localRuntime().getState();
        String fallbackModel = state.loadedModelId != null ? state.loadedModelId : settings.getDefaultAssistantModel();
        String modelId = requestedModelId(request, fallbackModel);
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown model: " + modelId);
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
        return cancelRuntime(TaiBenchHarness.STOP_CANCELLED);
    }

    /**
     * {@link #cancelRuntime()} with the reason a running benchmark's record gives for stopping:
     * {@code cancelled} is the user, {@code memory_pressure} is the runtime giving everything back
     * when the phone ran out. Only the runtime process itself passes anything else; a request over
     * IPC is always the user's.
     */
    @NonNull
    public JSONObject cancelRuntime(@NonNull String stopReason) throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_CANCEL, "{}");
        // tai cancel is how a running benchmark is stopped from outside; see TaiRuntimeService's
        // busy rule. The harness cancels the generation itself and records the entry as stopped.
        TaiBenchHarness bench = activeBench;
        if (bench != null) bench.requestStop(stopReason);
        return localRuntime().cancel();
    }

    /**
     * "Skip the wait" (spec Screen 5): ends the active bench's current or next cool-down wait at
     * once, and that entry's record is marked {@code warmStart}. A no-op, not an error, when
     * nothing is running.
     */
    @NonNull
    public JSONObject skipBenchCooldown() throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_BENCH_SKIP_WAIT, "{}");
        TaiBenchHarness bench = activeBench;
        if (bench != null) bench.skipCooldown();
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("skipped", bench != null);
        return result;
    }

    /**
     * "Leaving pauses at the end of the current step; Resume on return" (spec Safety table,
     * Screen/app row): holds or releases the active bench's guard. The in-app run screen's only
     * lever — a {@code tai benchmark} from the terminal is never held. A no-op, not an error, when
     * nothing is running.
     */
    @NonNull
    public JSONObject holdBench(@NonNull String body) throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_BENCH_HOLD, delegatedRuntimeBody(body));
        JSONObject request = parseBody(body);
        boolean held = request.optBoolean("held", false);
        TaiBenchHarness bench = activeBench;
        if (bench != null) bench.setHeld(held);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("held", held);
        result.put("active", bench != null);
        return result;
    }

    // ---- Benchmark (bench v2) ------------------------------------------------------------------

    /** The results file, {@code files/tai/benchmarks.json}, written by this (the app) process only. */
    @NonNull
    private TaiBenchStore benchStore() {
        return TaiBenchStore.in(appContext.getFilesDir());
    }

    /**
     * {@code GET /v1/ai/benchmarks}: every record and the leaderboard for the current bench
     * version. Deleting a model removes its results (see {@link #pruneModelRecords}); each record
     * still says whether its model is installed so a reader can mark it.
     */
    @NonNull
    public JSONObject benchmarks() throws JSONException {
        JSONObject data = benchStore().toJson(TaiBenchSuite.BENCH_VERSION);
        JSONArray records = data.optJSONArray("records");
        if (records != null) {
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record != null) record.put("installed", resolveModel(record.optString("modelId", "")) != null);
            }
        }
        return data;
    }

    /** {@code GET /v1/ai/logs}: the last {@code lines} lines of the AI event log (both files), oldest first. */
    @NonNull
    public JSONObject eventLogs(int lines) throws JSONException {
        List<String> tail = TaiEventLog.in(appContext.getFilesDir()).tail(lines > 0 ? lines : TaiEventLog.DEFAULT_TAIL_LINES);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("count", tail.size());
        data.put("lines", new JSONArray(tail));
        return data;
    }

    /** {@code DELETE /v1/ai/logs}: deletes the AI event log files. */
    @NonNull
    public JSONObject clearEventLogs() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("cleared", TaiEventLog.in(appContext.getFilesDir()).clear());
        return data;
    }

    /** {@code DELETE /v1/ai/runtime/history}: wipes the recorded load/failure/memory history. */
    @NonNull
    public JSONObject clearRuntimeHistory() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("removed", TaiRuntimeHistory.clear(appContext));
        return data;
    }

    /** {@code DELETE /v1/ai/benchmarks}: clears every record, or {@code modelId}'s only. */
    @NonNull
    public JSONObject clearBenchmarks(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = request.optString("modelId", request.optString("model", "")).trim();
        try {
            int removed = benchStore().clear(modelId.isEmpty() ? null : modelId);
            JSONObject data = new JSONObject();
            data.put("ok", true);
            data.put("removed", removed);
            data.put("modelId", modelId.isEmpty() ? JSONObject.NULL : modelId);
            return data;
        } catch (IOException e) {
            return error(500, "benchmarks_clear_failed", "Could not rewrite the benchmark file: " + message(e));
        }
    }

    /**
     * {@code POST /v1/ai/benchmarks/run} without {@code stream}: runs the whole bench and answers
     * once with the {@code done} summary plus the leaderboard the records landed on.
     */
    @NonNull
    public JSONObject benchRunCollect(@NonNull String body) throws JSONException {
        AtomicReference<JSONObject> done = new AtomicReference<>();
        AtomicReference<JSONObject> failure = new AtomicReference<>();
        try {
            benchRun(body, new OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) {
                    String name = event.optString("event", "");
                    if ("done".equals(name)) done.set(event);
                    // An error outside any entry is the run failing to start; entry errors are in the records.
                    else if ("error".equals(name) && !event.has("entry")) failure.compareAndSet(null, event);
                }

                @Override
                public void onDone() {
                }
            });
        } catch (IOException e) {
            return error(500, "benchmark_failed", message(e));
        }
        if (done.get() == null) {
            JSONObject event = failure.get();
            if (event == null) return error(500, "benchmark_failed", "The benchmark ended without a result.");
            return error(event.optInt("status", 500), event.optString("code", "benchmark_failed"),
                event.optString("message", "The benchmark could not start."));
        }
        JSONObject result = new JSONObject(done.get().toString());
        result.remove("event");
        result.remove("at");
        result.put("leaderboard", benchStore().toJson(TaiBenchSuite.BENCH_VERSION).opt("leaderboard"));
        return result;
    }

    /** An exception's message, or its class name when it has none (an IOException often does not). */
    @NonNull
    private static String message(@NonNull Exception e) {
        String text = e.getMessage();
        return text == null || text.isEmpty() ? e.getClass().getSimpleName() : text;
    }

    /**
     * Runs bench v2 over {@code models} and streams {@link TaiBenchHarness} events into
     * {@code sink}. Request: {@code {models: [id…] | model, preset: quick|standard,
     * compare: bool? (both processors where supported), processors: [cpu|gpu…]?, eagle: bool?}}. In the app process the request is resolved
     * (model specs and the settings' runtime options travel with it, as every runtime request's
     * do), forwarded to {@code :tai_runtime} as {@link TaiRuntimeIpc#OP_BENCH_RUN}, and each
     * {@code entry_done} record is appended to the store as it arrives. In the runtime process
     * the harness runs here, against the local router.
     */
    public void benchRun(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        long startedMs = android.os.SystemClock.elapsedRealtime();
        TaiEventLog.log(appContext, TaiEventLog.BENCH_START, "benchmark run");
        String outcome = "finished";
        try {
            if (shouldDelegateRuntime()) {
                benchRunDelegated(body, sink);
            } else {
                benchRunLocal(body, sink);
            }
        } catch (JSONException | IOException | RuntimeException e) {
            outcome = "failed: " + e.getClass().getSimpleName();
            throw e;
        } finally {
            TaiEventLog.log(appContext, TaiEventLog.BENCH_DONE, null, null, null, 0,
                android.os.SystemClock.elapsedRealtime() - startedMs, 0L, outcome);
        }
    }

    private void benchRunDelegated(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        if (runtimeClient == null) {
            emitBenchError(sink, error(503, "tai_runtime_unavailable", "On-device AI runtime service client is unavailable."));
            return;
        }
        JSONObject prepared = prepareBenchRequest(parseBody(body));
        if (!prepared.optBoolean("ok", false)) {
            emitBenchError(sink, prepared);
            return;
        }
        prepared.remove("ok");
        if (!benchStreamActive.compareAndSet(false, true)) {
            emitBenchError(sink, error(409, "benchmark_running", "A benchmark is already running; wait for it or stop it with tai cancel."));
            return;
        }
        try {
            // The runtime can die under a run; the recovery writes what the harness could not and
            // asks for the rest of the entries again (see TaiBenchCrashRecovery).
            new TaiBenchCrashRecovery(benchStore(), com.termux.BuildConfig.VERSION_NAME,
                TaiBenchCrashRecovery.DEFAULT_RESTART_PAUSE_MS).run(prepared, benchErrorMapped(sink),
                (request, attemptSink) -> runtimeClient.stream(TaiRuntimeIpc.OP_BENCH_RUN, request.toString(), attemptSink));
        } finally {
            benchStreamActive.set(false);
        }
    }

    /**
     * {@code sink} with the service client's error shape ({@code {error, tai}}) turned into the
     * bench's own {@code error} event, for errors the recovery does not handle itself.
     */
    @NonNull
    private static OpenAiStreamSink benchErrorMapped(@NonNull OpenAiStreamSink sink) {
        return new OpenAiStreamSink() {
            @Override
            public void onEvent(@NonNull JSONObject event) throws IOException {
                if (!event.has("event") && event.has("error")) {
                    try {
                        sink.onEvent(benchErrorEvent(event.optJSONObject("tai") == null ? event : event.getJSONObject("tai")));
                    } catch (JSONException e) {
                        throw new IOException(e);
                    }
                    return;
                }
                sink.onEvent(event);
            }

            @Override
            public void onDone() throws IOException {
                sink.onDone();
            }
        };
    }

    /**
     * The app-process half of a bench request: every model resolved and checked (a chat model,
     * installed), the preset and processors validated, the specs and runtime options attached the
     * way {@link #delegatedRuntimeBody} attaches them for one model. {@code ok:false} is the error
     * to answer with.
     */
    @NonNull
    private JSONObject prepareBenchRequest(@NonNull JSONObject request) throws JSONException {
        List<String> ids = new ArrayList<>();
        JSONArray models = request.optJSONArray("models");
        if (models != null) {
            for (int i = 0; i < models.length(); i++) {
                Object item = models.opt(i);
                String id = item instanceof JSONObject ? ((JSONObject) item).optString("model", "") : String.valueOf(item);
                if (!id.trim().isEmpty() && !ids.contains(id.trim())) ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            String single = request.optString("model", "").trim();
            ids.add(single.isEmpty() ? settings.getDefaultAssistantModel() : single);
        }
        TaiBenchSuite.Preset preset = TaiBenchSuite.Preset.fromId(request.optString("preset", null));
        if (preset == null) return error(400, "bad_preset", "preset must be quick or standard.");
        JSONArray prepared = new JSONArray();
        for (String id : ids) {
            TaiModelSpec spec = resolveModel(id);
            if (spec == null) return error(404, "model_not_found", "Unknown model: " + id);
            if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
                return error(400, "capability_not_supported", "Model " + id + " is not a chat model.");
            }
            if (spec.localPath == null || !new File(spec.localPath).exists()) {
                return error(404, "model_file_missing", "Download or import " + id + " before benchmarking it.");
            }
            JSONObject entry = new JSONObject();
            entry.put("model", spec.id);
            entry.put(INTERNAL_MODEL_SPEC, spec.toJson());
            entry.put(INTERNAL_RUNTIME_OPTIONS, settings.getRuntimeOptions(spec).toJson());
            prepared.put(entry);
        }
        JSONArray processors = null;
        Object requestedProcessors = request.opt("processors");
        if (requestedProcessors instanceof JSONArray) {
            processors = (JSONArray) requestedProcessors;
        } else if (requestedProcessors instanceof String && !((String) requestedProcessors).trim().isEmpty()) {
            processors = new JSONArray().put(((String) requestedProcessors).trim());
        }
        JSONObject body = new JSONObject();
        body.put("ok", true);
        body.put("models", prepared);
        body.put("preset", preset.id);
        body.put("processors", processors == null ? JSONObject.NULL : processors);
        body.put("compare", request.optBoolean("compare", false));
        body.put("eagle", request.optBoolean("eagle", false));
        body.put("force", request.optBoolean("force", false));
        return body;
    }

    /** The runtime-process half: the harness against the local router. */
    private void benchRunLocal(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        JSONObject request = parseBody(body);
        TaiRuntimeState state = localRuntime().getState();
        if (state.activeGeneration || "loading".equals(state.state)) {
            JSONObject busy = error(409, "runtime_busy", "The runtime is " + ("loading".equals(state.state) ? "loading" : "generating")
                + "; wait for it to finish or cancel it before benchmarking.");
            if (state.loadedModelId != null) busy.put("loadedModelId", state.loadedModelId);
            emitBenchError(sink, busy);
            return;
        }
        if (activeBench != null) {
            emitBenchError(sink, error(409, "benchmark_running", "A benchmark is already running."));
            return;
        }
        TaiBenchSuite.Preset preset = TaiBenchSuite.Preset.fromId(request.optString("preset", null));
        if (preset == null) {
            emitBenchError(sink, error(400, "bad_preset", "preset must be quick or standard."));
            return;
        }
        JSONArray models = request.optJSONArray("models");
        if (models == null || models.length() == 0) {
            emitBenchError(sink, error(400, "no_models", "No models to benchmark."));
            return;
        }
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        Map<String, TaiModelSpec> specs = new LinkedHashMap<>();
        Map<String, TaiRuntimeOptions> baseOptions = new LinkedHashMap<>();
        List<TaiBenchSuite.ModelInput> inputs = new ArrayList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) continue;
            String id = model.optString("model", "");
            TaiModelSpec spec = resolveModel(model, id);
            if (spec == null) {
                emitBenchError(sink, error(404, "model_not_found", "Unknown model: " + id));
                return;
            }
            TaiRuntimeOptions options = runtimeOptionsFromRequest(model, spec);
            // The processor an automatic load would take is the default pick; the GPU is compared
            // only where the phone and this model's preflight allow it. An explicit --gpu is
            // planned regardless and refused, with the reason, by the load.
            boolean gpuSupported = device.supportsAccelerator("gpu")
                && !TaiLoadPreflight.evaluate(appContext, spec, options.withAccelerator("gpu"), false).blocked;
            String best = TaiLoadPreflight.evaluate(appContext, spec, options.withAccelerator("auto"), false).effectiveAccelerator;
            best = "gpu".equalsIgnoreCase(best) ? TaiBenchSuite.ACCELERATOR_GPU : TaiBenchSuite.ACCELERATOR_CPU;
            // A GPU-only file (the Gemma 4 -gpu/-web bundles) has no CPU graph; the comparison skips it.
            boolean cpuSupported = TaiModelProfile.forModel(spec).supports("cpu");
            boolean speculativeCapable = spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING);
            specs.put(spec.id, spec);
            baseOptions.put(spec.id, options);
            inputs.add(new TaiBenchSuite.ModelInput(spec.id, spec.backend, best, cpuSupported, gpuSupported, speculativeCapable));
        }
        List<String> processors = null;
        JSONArray requestedProcessors = request.optJSONArray("processors");
        if (requestedProcessors != null) {
            processors = new ArrayList<>();
            for (int i = 0; i < requestedProcessors.length(); i++) processors.add(requestedProcessors.optString(i, ""));
        }
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(inputs, request.optBoolean("compare", false), processors, request.optBoolean("eagle", false));
        // A restart after the runtime crashed asks only for the entries not yet finished.
        JSONArray skip = request.optJSONArray("skip");
        if (skip != null && skip.length() > 0) {
            Set<String> finished = new LinkedHashSet<>();
            for (int i = 0; i < skip.length(); i++) finished.add(skip.optString(i, ""));
            List<TaiBenchSuite.EntryPlan> remaining = new ArrayList<>();
            for (TaiBenchSuite.EntryPlan planned : entries) if (!finished.contains(planned.key())) remaining.add(planned);
            entries = remaining;
        }
        if (entries.isEmpty()) {
            emitBenchError(sink, error(400, "no_entries", "Nothing to run: no model and processor pair to benchmark."));
            return;
        }
        JSONObject deviceJson = new JSONObject();
        deviceJson.put("soc", device.socModel == null ? JSONObject.NULL : device.socModel);
        deviceJson.put("ramClassGb", device.memoryBytes > 0L ? Math.round(device.memoryBytes / (double) (1024L * 1024L * 1024L)) : JSONObject.NULL);

        TaiDeviceConditions conditionsReader = new TaiDeviceConditions(appContext);
        boolean force = request.optBoolean("force", false);
        if (!force) {
            TaiBenchGuardRules.Snapshot startSnapshot = conditionsReader.snapshot();
            String reason = TaiBenchGuardRules.startCheck(startSnapshot);
            if (reason != null) {
                JSONObject refusal = error(409, "conditions_not_met", conditionsMessage(reason));
                refusal.put("reason", reason);
                refusal.put("batteryPercent", startSnapshot.batteryPercent >= 0 ? startSnapshot.batteryPercent : JSONObject.NULL);
                refusal.put("charging", startSnapshot.charging);
                refusal.put("thermalStatus", startSnapshot.thermalStatus >= 0 ? startSnapshot.thermalStatus : JSONObject.NULL);
                emitBenchError(sink, refusal);
                return;
            }
        }

        TaiBenchGuard guard = new TaiBenchConditionsGuard(conditionsReader::snapshot, System::currentTimeMillis);
        TaiBenchHarness harness = new TaiBenchHarness(preset, entries, new BenchHost(specs, baseOptions),
            guard, sink::onEvent, com.termux.BuildConfig.VERSION_NAME, deviceJson);
        activeBench = harness;
        conditionsReader.startThermalListener(() -> harness.requestStop("thermal"));
        try {
            harness.run();
        } finally {
            conditionsReader.stopThermalListener();
            activeBench = null;
        }
        sink.onDone();
    }

    /** The human message for a {@code conditions_not_met} refusal, by {@link TaiBenchGuardRules#startCheck} reason. */
    @NonNull
    private static String conditionsMessage(@NonNull String reason) {
        switch (reason) {
            case "battery_low":
                return "Battery is below " + TaiBenchGuardRules.START_BATTERY_MIN_PERCENT
                    + "%; plug in or charge before benchmarking, or pass force=true to run anyway.";
            case "too_hot":
                return "The phone is already warm; let it cool before benchmarking, or pass force=true to run anyway.";
            default:
                return "The phone is not in a state to start a benchmark.";
        }
    }

    /** The harness's window on this process: the router, the meter, the log, the stamps. */
    private final class BenchHost implements TaiBenchHarness.Host {
        /** Long enough for the {@code done} event to reach the app over the binder before the process goes. */
        private static final long ABANDON_KILL_DELAY_MS = 1_500L;

        @NonNull private final Map<String, TaiModelSpec> specs;
        @NonNull private final Map<String, TaiRuntimeOptions> baseOptions;
        @Nullable private String log;

        BenchHost(@NonNull Map<String, TaiModelSpec> specs, @NonNull Map<String, TaiRuntimeOptions> baseOptions) {
            this.specs = specs;
            this.baseOptions = baseOptions;
        }

        @NonNull
        private TaiModelSpec spec(@NonNull TaiBenchSuite.EntryPlan entry) {
            TaiModelSpec spec = specs.get(entry.modelId);
            if (spec == null) throw new IllegalStateException("No spec for " + entry.modelId);
            return spec;
        }

        /** The entry's processor and draft-model choice on top of the settings' options; thinking off. */
        @NonNull
        private TaiRuntimeOptions options(@NonNull TaiBenchSuite.EntryPlan entry, @Nullable Integer maxTokens, boolean greedy) {
            TaiRuntimeOptions base = baseOptions.get(entry.modelId);
            if (base == null) base = settings.getRuntimeOptions(spec(entry));
            return base.withGenerationOverrides(maxTokens, greedy ? Integer.valueOf(1) : null, null,
                greedy ? Double.valueOf(0.0) : null, entry.accelerator, null, null, null, null,
                Boolean.FALSE, entry.speculative);
        }

        /** The same preflight, budget and history as {@code tai load}; a refusal skips the entry. */
        @NonNull
        @Override
        public JSONObject load(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            TaiModelSpec spec = spec(entry);
            TaiRuntimeOptions options = options(entry, null, false);
            TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options, false);
            if (preflight.blocked) {
                if (TaiRuntimeHistory.isAcceleratorVerdict(preflight.errorCode)) {
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
        @Override
        public JSONObject unload() throws JSONException {
            return localRuntime().unload();
        }

        /** The chat model resident now, with the processor it runs on and what is left of its keep-warm. */
        @Nullable
        @Override
        public JSONObject residentChat() throws JSONException {
            TaiRuntimeState state = localRuntime().getState();
            if (!state.loaded || state.loadedModelId == null) return null;
            JSONObject resident = new JSONObject().put("modelId", state.loadedModelId);
            MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
            TaiResidency.Entry entry = router == null ? null : router.residency().find(TaiResidency.Kind.CHAT, state.loadedModelId);
            if (entry != null) resident.put("accelerator", entry.accelerator);
            long warmLeftMs = state.keepWarmUntilMs - System.currentTimeMillis();
            if (state.keepWarmUntilMs > 0L && warmLeftMs > 0L) {
                resident.put("keepWarmMinutes", Math.max(1L, (warmLeftMs + 59_999L) / 60_000L));
            }
            return resident;
        }

        /** Loads it back the way {@code tai load} does (or {@code tai keep-warm}, when it was being kept warm). */
        @Override
        public void restoreChat(@NonNull JSONObject resident) throws JSONException {
            JSONObject request = new JSONObject().put("model", resident.optString("modelId", ""));
            String accelerator = resident.optString("accelerator", "");
            if (!accelerator.isEmpty()) request.put("accelerator", accelerator);
            int minutes = resident.optInt("keepWarmMinutes", 0);
            if (minutes > 0) {
                request.put("minutes", minutes);
                keepWarmRuntime(request.toString());
            } else {
                loadModel(request.toString());
            }
        }

        /**
         * A load that never returned is still running on a thread the runtime cannot stop, and the
         * weights it mapped stay mapped for as long as the process lives. Ending the process is the
         * only way to get them back, so it goes a moment after the run's {@code done} has been
         * sent. The app's client sees no request in flight, does not count it a crash, and binds a
         * fresh process on the next request.
         */
        @Override
        public void abandonRuntime() {
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(ABANDON_KILL_DELAY_MS);
                } catch (InterruptedException ignored) {
                }
                Process.killProcess(Process.myPid());
            }, "tai-bench-abandon");
            killer.setDaemon(true);
            killer.start();
        }

        @NonNull
        @Override
        public JSONObject chat(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull String userPrompt, int maxTokens,
                               @NonNull TaiGenerationCallback callback) throws JSONException {
            // Greedy (top_k 1, temperature 0) for every phase: the numbers should not move with
            // the dice, and the check questions have one right answer. One-shot: every prompt gets
            // a fresh conversation, or LiteRT-LM would answer each one with the earlier phases'
            // passages and replies still in context (a check answered as the previous question).
            return localRuntime().chat(spec(entry).id, TaiChatRequest.oneShot(TaiBenchSuite.SYSTEM_PROMPT, userPrompt),
                options(entry, maxTokens, true), callback);
        }

        @Override
        public void cancel() {
            try {
                localRuntime().cancel();
            } catch (JSONException | RuntimeException ignored) {
            }
        }

        @NonNull
        @Override
        public TaiLoadMeter startMeter() {
            return TaiLoadMeter.start(appContext);
        }

        @NonNull
        @Override
        public String longInputLog() throws IOException {
            if (log != null) return log;
            try (InputStream input = appContext.getAssets().open(TaiBenchSuite.LONG_INPUT_ASSET);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                log = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            return log;
        }

        @Override
        public void clearMmapCache(@NonNull TaiBenchSuite.EntryPlan entry) {
            if (!TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) return;
            MnnTaiRuntime.clearMmapCache(appContext, spec(entry).id);
        }

        /** {@code :tai_runtime}'s own PSS (this process, where the model is resident). */
        @Override
        public long processPssBytes() {
            try {
                long kb = android.os.Debug.getPss();
                return kb > 0L ? kb * 1024L : -1L;
            } catch (RuntimeException e) {
                return -1L;
            }
        }

        @NonNull
        @Override
        public JSONObject describe(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            TaiModelSpec spec = spec(entry);
            JSONObject json = new JSONObject();
            json.put("displayName", spec.displayName);
            // The size the spec knows or the file's; nothing is hashed here — a multi-GB sha256
            // has no place inside a timed run, so the hash is whatever the download recorded.
            long bytes = TaiResidency.fileBytes(spec);
            json.put("sizeBytes", bytes > 0L ? bytes : spec.sizeBytes);
            json.put("sha256", spec.sha256 == null ? JSONObject.NULL : spec.sha256);
            json.put("runtimeVersion", TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)
                ? MnnTaiRuntime.RUNTIME_VERSION : com.termux.BuildConfig.LITERT_LM_VERSION);
            return json;
        }
    }

    /** An {@code error} event in the harness's shape from a manager error object. */
    @NonNull
    private static JSONObject benchErrorEvent(@NonNull JSONObject source) throws JSONException {
        JSONObject event = new JSONObject();
        event.put("event", "error");
        event.put("at", System.currentTimeMillis());
        event.put("code", source.optString("error", "benchmark_failed"));
        event.put("message", source.optString("message", "The benchmark could not start."));
        event.put("status", source.optInt("_statusCode", 500));
        if (source.has("loadedModelId")) event.put("loadedModelId", source.opt("loadedModelId"));
        // A conditions_not_met refusal attaches reason/batteryPercent/charging/thermalStatus; any
        // field beyond the standard shape travels through to the caller unchanged.
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("ok".equals(key) || "error".equals(key) || "message".equals(key)
                || "_statusCode".equals(key) || "loadedModelId".equals(key)) continue;
            event.put(key, source.get(key));
        }
        return event;
    }

    private void emitBenchError(@NonNull OpenAiStreamSink sink, @NonNull JSONObject source) throws JSONException, IOException {
        sink.onEvent(benchErrorEvent(source));
        sink.onDone();
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
        if (spec == null) return error(404, "model_not_found", "Unknown model: " + modelId);
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
                device.physicalMemoryBytes, availableMemory(device),
                Collections.singletonList(params.accelerator), params.prefillTokens + params.decodeTokens, null, 0)
                .withConditions(gateConditions()));
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
        body = withFeatureModel(body);
        // A remote/<id> model goes to the remote provider here, in the app process: the :tai_runtime
        // process is never woken for it (remote provider design, section 4.2).
        if (!runtimeProcess && TaiCallerRequests.isRemoteRequest(body)) {
            return new TaiRemoteProvider(appContext).chatCompletions(
                TaiCallerRequests.remoteBody(parseBody(body)), timeoutMs);
        }
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
        if (spec == null) return openAiError(error(404, "model_not_found", "Unknown model: " + modelId));
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
        if (spec == null) return openAiError(error(404, "model_not_found", "Unknown model: " + modelId));
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

    public boolean isStreamRequest(@NonNull String body) {
        try {
            return parseBody(body).optBoolean("stream", false);
        } catch (JSONException e) {
            return false;
        }
    }

    public void openAiChatCompletionsStream(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        body = withFeatureModel(body);
        if (!runtimeProcess && TaiCallerRequests.isRemoteRequest(body)) {
            new TaiRemoteProvider(appContext).stream(
                TaiCallerRequests.remoteBody(parseBody(body)), DEFAULT_CHAT_TIMEOUT_MS, sink);
            return;
        }
        if (shouldDelegateRuntime()) {
            if (runtimeClient == null) {
                emitOpenAiError(sink, error(503, "tai_runtime_unavailable", "On-device AI runtime service client is unavailable."));
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
            emitOpenAiError(sink, error(404, "model_not_found", "Unknown model: " + modelId));
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
                emitOpenAiError(sink, error(503, "tai_runtime_unavailable", "On-device AI runtime service client is unavailable."));
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
            emitOpenAiError(sink, error(404, "model_not_found", "Unknown model: " + modelId));
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
            if ((TaiModelSpec.BACKEND_MNN_LLM.equals(stored.backend)
                || TaiModelSpec.BACKEND_MNN_DIFFUSION.equals(stored.backend)) && !mnnSupported) continue;
            // Image models are not chat models either; they are addressed by the image route.
            if (stored.isImageGeneration()) continue;
            // Management can retain imported packages whose backend is not executable yet, but
            // generation discovery must publish only models with at least one runnable endpoint.
            if (stored.endpointCapabilities.isEmpty()) continue;
            // Speech-to-text models aren't chat models: MultiBackendTaiRuntime doesn't route
            // speech_to_text yet (phase 2), and they're never a valid /v1/chat/completions target.
            if (stored.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) continue;
            if (stored.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)) continue;
            // Vision tool models are never a chat target.
            if (stored.isVisionTool()) continue;
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
        JSONObject response = applyAudioHistoryGates(openAiModelsFromTaiModels(installed), device);
        return response;
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
            if (contains(endpointCapabilities, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)) {
                putEmbedderFields(item, model);
            }
            data.put(item);
        }

        JSONObject response = new JSONObject();
        response.put("object", "list");
        response.put("data", data);
        response.put("models", codexModels(data));
        return response;
    }

    /**
     * The embedder-specific fields dawn's brief asks {@code /v1/models} for (item 3): output
     * dimensions, the Matryoshka sizes {@code dimensions} may truncate to, a stable revision so
     * dawn knows when to rebuild its index, that vectors are L2-normalised, and the largest batch
     * {@code /v1/embeddings} accepts (item 4). Context window is already covered by the existing
     * {@code _endpoint_context_window}, overridden here by the largest installed window graph
     * because a catalogue install records the catalogue's window rather than the graph's; the
     * window-routing brief (item 7) also adds {@code _endpoint_windows}, every installed window
     * sorted ascending, when more than the primary graph is on disk.
     */
    private static void putEmbedderFields(@NonNull JSONObject item, @NonNull JSONObject model) throws JSONException {
        String id = model.optString("id", "");
        String localPath = model.isNull("localPath") ? null : model.optString("localPath", null);
        // Window-routing brief item 7: several installed graphs beside one another, keyed by their
        // own seqNNNN, override the single-file window a catalogue install otherwise records.
        int[] windows = localPath == null ? new int[0] : TaiModelSpec.windowsFor(localPath);
        if (windows.length > 0) {
            item.put("_endpoint_context_window", windows[windows.length - 1]);
            JSONArray windowsArray = new JSONArray();
            for (int window : windows) windowsArray.put(window);
            item.put("_endpoint_windows", windowsArray);
        } else if (localPath != null && localPath.toLowerCase(Locale.ROOT).endsWith(".litertlm")) {
            // A .litertlm embedder (EmbeddingGemma 2) is one bundle with no seqNNNN in its name;
            // its window is the input cap the LiteRT-LM embedding engine is built with.
            item.put("_endpoint_context_window", LiteRtLmEmbeddingRuntime.MAX_INPUT_TOKENS);
        }
        int dimensions = TaiModelSpec.embeddingDimensionsFor(id, localPath);
        if (dimensions > 0) item.put("_endpoint_dimensions", dimensions);
        int[] matryoshka = TaiModelSpec.embeddingMatryoshkaDimsFor(id, localPath);
        if (matryoshka.length > 0) {
            JSONArray sizes = new JSONArray();
            for (int size : matryoshka) sizes.put(size);
            item.put("_endpoint_matryoshka_dims", sizes);
        }
        String revision = TaiModelSpec.revisionFor(localPath);
        if (revision != null) item.put("_revision", revision);
        item.put("_endpoint_normalized", true);
        item.put("_endpoint_max_batch", EMBEDDINGS_MAX_BATCH);
        // Item 5: the policy, not the live state. While /v1/ai/runtime reports
        // runtime.activeGeneration, embedding runs at background thread priority.
        item.put("_endpoint_throttle_while_generating", "priority");
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

    /** {@code /v1/embeddings} accepts at most this many inputs per request (dawn brief item 4);
     *  a larger batch is refused with 413, not silently truncated or dropped. Dawn is told the
     *  number through {@code _endpoint_max_batch} on the embedder's {@code /v1/models} entry. */
    static final int EMBEDDINGS_MAX_BATCH = 64;

    /**
     * {@code body} with the embedder the EMBEDDINGS function resolves to as its {@code model} when it
     * names none; unchanged when it names one, is not JSON, or nothing resolves.
     */
    @NonNull
    private String withEmbeddingModel(@NonNull String body) {
        if (runtimeProcess) return body;
        try {
            JSONObject request = new JSONObject(body);
            if (!request.optString("model", "").trim().isEmpty()) return body;
            String id = TaiFunctionModels.forContext(appContext).resolve(TaiFunction.EMBEDDINGS).modelId;
            if (id == null || id.isEmpty()) return body;
            return request.put("model", id).toString();
        } catch (JSONException | RuntimeException e) {
            return body;
        }
    }

    @NonNull
    public JSONObject embeddings(@NonNull String body) throws JSONException {
        // No model in the request: the embedder the EMBEDDINGS function resolves to, not the chat
        // assistant. Resolved before the runtime hand-off so both processes see the same model.
        body = withEmbeddingModel(body);
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_EMBEDDINGS, delegatedRuntimeBody(body));
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        String encodingFormat = request.optString("encoding_format", "float");
        boolean base64 = "base64".equalsIgnoreCase(encodingFormat);
        if (!encodingFormat.isEmpty() && !"float".equalsIgnoreCase(encodingFormat) && !base64) {
            return openAiRequestError(400, "unsupported_encoding_format",
                "Only encoding_format:\"float\" or \"base64\" is supported for local embeddings.", "encoding_format");
        }
        boolean hasDimensions = request.has("dimensions");
        int dimensions = hasDimensions ? request.optInt("dimensions", -1) : 0;
        if (hasDimensions && dimensions <= 0) {
            return openAiRequestError(400, "invalid_dimensions", "Embedding dimensions must be positive.", "dimensions");
        }
        String inputType = request.optString("input_type", LiteRtEmbeddingRuntime.INPUT_TYPE_DOCUMENT);
        if (!LiteRtEmbeddingRuntime.INPUT_TYPE_QUERY.equals(inputType)
                && !LiteRtEmbeddingRuntime.INPUT_TYPE_DOCUMENT.equals(inputType)) {
            return openAiRequestError(400, "invalid_input_type",
                "input_type must be \"query\" or \"document\".", "input_type");
        }
        String title = request.has("title") && !request.isNull("title") ? request.optString("title", null) : null;
        List<String> inputs = embeddingInputs(request);
        if (inputs == null) {
            return openAiRequestError(400, "unsupported_embedding_input",
                "Embeddings input must be a string or an array of strings.", "input");
        }
        if (inputs.size() > EMBEDDINGS_MAX_BATCH) {
            return openAiRequestError(413, "batch_too_large",
                "At most " + EMBEDDINGS_MAX_BATCH + " inputs are accepted per /v1/embeddings request.", "input");
        }
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) {
            return openAiRequestError(404, "model_not_found", "Unknown model: " + modelId, "model");
        }
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)) {
            return openAiRequestError(501, "capability_not_supported",
                "Embeddings are not supported for model '" + modelId + "'.", "model");
        }
        // A catalogue built-in that was never downloaded has no graph on disk: say so plainly
        // instead of failing deep in the runtime.
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
            return openAiRequestError(404, "model_not_installed",
                "Model '" + modelId + "' is not downloaded. Download it in the Model centre first.", "model");
        }
        // Matryoshka quality holds only at the trained sizes, so a model that lists them takes no other.
        int[] matryoshka = TaiModelSpec.embeddingMatryoshkaDimsFor(spec.id, spec.localPath);
        if (dimensions > 0 && matryoshka.length > 0 && !contains(matryoshka, dimensions)) {
            return openAiRequestError(400, "invalid_dimensions",
                "dimensions must be one of " + java.util.Arrays.toString(matryoshka) + " for this model.", "dimensions");
        }
        MultiBackendTaiRuntime local = (MultiBackendTaiRuntime) localRuntime();
        if (!local.residency().isResident(TaiResidency.Kind.EMBEDDING, spec.id)) {
            JSONObject refusal = decideEmbeddingLoad(spec, runtimeOptionsFromRequest(request, spec));
            if (refusal != null) return refusal;
        }
        JSONObject result = local.embed(spec, inputs, dimensions, inputType, title);
        if (base64 && result.optInt("_statusCode", 200) < 400) applyBase64Encoding(result);
        return result;
    }

    /** In place: swaps every {@code data[i].embedding} float array for the little-endian float32
     *  base64 string OpenAI's {@code encoding_format: "base64"} sends (dawn brief, "nice to have"). */
    private static void applyBase64Encoding(@NonNull JSONObject result) throws JSONException {
        JSONArray data = result.optJSONArray("data");
        if (data == null) return;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            JSONArray vector = item == null ? null : item.optJSONArray("embedding");
            if (item == null || vector == null) continue;
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(vector.length() * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int j = 0; j < vector.length(); j++) buffer.putFloat((float) vector.optDouble(j, 0.0));
            item.put("embedding", Base64.getEncoder().encodeToString(buffer.array()));
        }
    }

    /**
     * {@code POST /v1/tokenize}: {@code {model, input}} in, {@code {tokens: n}} out (dawn brief,
     * "nice to have") — the installed embedding model's own tokenizer, so dawn can split notes on
     * real token counts instead of estimating from characters.
     */
    @NonNull
    public JSONObject tokenize(@NonNull String body) throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_TOKENIZE, delegatedRuntimeBody(body));
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        String input = request.optString("input", "");
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) {
            return openAiRequestError(404, "model_not_found", "Unknown model: " + modelId, "model");
        }
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)) {
            return openAiRequestError(501, "capability_not_supported",
                "Tokenize is only supported for embedding models today.", "model");
        }
        MultiBackendTaiRuntime local = (MultiBackendTaiRuntime) localRuntime();
        return local.tokenize(spec, input);
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

    // ---- speech output ----

    /** One sentence of speech for {@link #synthesizeSpeech}: PCM16 little-endian mono at {@code sampleRate}. */
    public interface SpeechAudioSink {
        void onAudio(@NonNull byte[] pcm16, int sampleRate) throws IOException;
    }

    /**
     * The IPC deadline for {@link #speak}: the call returns only once the text has been heard, so
     * the deadline covers synthesis and playback of the whole text, generously (a slow phone
     * synthesising slower than it speaks, and the model load on first use). Time the runtime spent
     * paused ({@link #pauseSpeaking}) does not count against it; see {@link #speechPauses}.
     */
    static long speakTimeoutMs(int chars) {
        return Math.min(60L * 60_000L, 60_000L + chars * 200L);
    }

    /**
     * The app process's note of how long the phone's speech has been paused, opened when the
     * runtime confirms a pause and closed on resume or stop, so a {@link #speak} waiting on the
     * runtime moves its deadline out for as long as the user keeps it paused. The runtime keeps its
     * own ({@link TaiTtsPlayer#awaitDone}).
     */
    private final TaiPauseClock speechPauses = new TaiPauseClock();

    /**
     * Speaks {@code input} through the phone's speaker with the speech-output model and returns
     * when it has been heard or stopped: {@code tai speak}, Read aloud and the settings sample.
     * Body: {@code input} (or {@code text}), {@code voice}, {@code speed}, {@code model}; voice and
     * speed default to the settings'. Answers {@code ok, model, voice, speed, sentences,
     * audioSeconds, firstSoundMs, played, stopped, timings}.
     */
    @NonNull
    public JSONObject speak(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, TaiSpeechRequest.MAX_SPEAK_CHARS);
        if (!parsed.isValid()) return speechRefusal(parsed);
        TaiModelSpec spec = resolveTtsModel(request, parsed);
        if (spec == null) return ttsModelError(request, parsed);
        if (shouldDelegateRuntime()) {
            if (runtimeClient == null) return error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
            return runtimeClient.request(TaiRuntimeIpc.OP_TTS_SPEAK, delegatedTtsBody(parsed, spec),
                speakTimeoutMs(parsed.text.length()), speechPauses);
        }
        MultiBackendTaiRuntime router = ttsRouter();
        if (router == null) return noTtsRuntime();
        JSONObject refusal = decideTtsLoad(router, spec);
        if (refusal != null) return refusal;
        return TaiSpeechOutput.speak(appContext, router, spec, parsed);
    }

    /** Stops whatever the phone is saying; answers {@code {ok, stopped}}. Never waits for synthesis. */
    @NonNull
    public JSONObject stopSpeaking() throws JSONException {
        if (shouldDelegateRuntime()) {
            speechPauses.resume(SystemClock.elapsedRealtime());
            return runtimeRequest(TaiRuntimeIpc.OP_TTS_STOP, "{}", RUNTIME_STATUS_TIMEOUT_MS);
        }
        return TaiSpeechOutput.stop(ttsRouter());
    }

    /**
     * Pauses the phone's speech where it is, mid-word; the speak call stays open until it is
     * resumed or stopped. Answers {@code {ok, paused}}, {@code paused} false when nothing was
     * playing (between two of Read aloud's sentences, say). Never waits for synthesis.
     */
    @NonNull
    public JSONObject pauseSpeaking() throws JSONException {
        if (shouldDelegateRuntime()) {
            JSONObject result = runtimeRequest(TaiRuntimeIpc.OP_TTS_PAUSE, "{}", RUNTIME_STATUS_TIMEOUT_MS);
            if (result.optBoolean("paused", false)) speechPauses.pause(SystemClock.elapsedRealtime());
            return result;
        }
        return TaiSpeechOutput.pause();
    }

    /** Carries paused speech on from where it stopped; answers {@code {ok, resumed}}. */
    @NonNull
    public JSONObject resumeSpeaking() throws JSONException {
        if (shouldDelegateRuntime()) {
            speechPauses.resume(SystemClock.elapsedRealtime());
            return runtimeRequest(TaiRuntimeIpc.OP_TTS_RESUME, "{}", RUNTIME_STATUS_TIMEOUT_MS);
        }
        return TaiSpeechOutput.resume();
    }

    /** Answers {@code {ok, speaking, sounding, paused}} for the phone's speech; see {@link TaiSpeechOutput#state}. */
    @NonNull
    public JSONObject speechState() throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_TTS_STATE, "{}", RUNTIME_STATUS_TIMEOUT_MS);
        return TaiSpeechOutput.state();
    }

    /** Loads the speech-output model ahead of its first sentence. */
    @NonNull
    public JSONObject ttsWarm(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        TaiSpeechRequest parsed = TaiSpeechRequest.parse(new JSONObject(request.toString()).put("input", "."),
            settings.getTtsVoice(), settings.getTtsSpeed(), TaiSpeechRequest.MAX_SPEAK_CHARS);
        TaiModelSpec spec = resolveTtsModel(request, parsed);
        if (spec == null) return ttsModelError(request, parsed);
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_TTS_WARM, delegatedTtsBody(parsed, spec));
        MultiBackendTaiRuntime router = ttsRouter();
        if (router == null) return noTtsRuntime();
        JSONObject refusal = decideTtsLoad(router, spec);
        if (refusal != null) return refusal;
        return router.ttsWarm(spec);
    }

    /**
     * Checks a speech request without running it: the refusal (OpenAI error shape with
     * {@code _statusCode}) or {@code null} when it would run. The API calls this before it commits
     * to a 200 and a streamed body.
     */
    @Nullable
    public JSONObject checkSpeechRequest(@NonNull String body, int maxChars) throws JSONException {
        JSONObject request = parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, maxChars);
        if (!parsed.isValid()) return speechRefusal(parsed);
        return resolveTtsModel(request, parsed) == null ? ttsModelError(request, parsed) : null;
    }

    /** {@code wav} or {@code pcm} for a request that {@link #checkSpeechRequest} accepted. */
    @NonNull
    public static String speechResponseFormat(@NonNull String body) {
        try {
            return TaiSpeechRequest.parse(new JSONObject(body.trim().isEmpty() ? "{}" : body), TaiTtsVoices.DEFAULT_VOICE,
                TaiTtsVoices.DEFAULT_SPEED, Integer.MAX_VALUE).format;
        } catch (JSONException e) {
            return TaiSpeechRequest.FORMAT_WAV;
        }
    }

    /** The 44-byte RIFF header for {@code dataBytes} of mono PCM16, for the API's {@code wav} answer. */
    @NonNull
    public static byte[] wavHeader(int dataBytes, int sampleRate) {
        return TaiWav.header(dataBytes, sampleRate);
    }

    /** The limit {@code /v1/audio/speech} applies, OpenAI's; {@code tai speak} gets {@link #speakCharacterLimit}. */
    public static int apiSpeechCharacterLimit() {
        return TaiSpeechRequest.MAX_API_CHARS;
    }

    public static int speakCharacterLimit() {
        return TaiSpeechRequest.MAX_SPEAK_CHARS;
    }

    /**
     * Synthesises without playing, handing each sentence to {@code sink} as it is ready: the
     * OpenAI {@code /v1/audio/speech} route and {@code tai speak --out}. The runtime process writes
     * each sentence as a PCM16 file under {@code cacheDir/tai-ipc} and says so; this side reads
     * it, deletes it and passes the bytes on, so audio never crosses the Messenger inline. Returns
     * the runtime's summary, or a refusal or failure in the OpenAI error shape. When {@code sink}
     * throws (the HTTP client went away) synthesis is stopped and the exception rethrown.
     */
    @NonNull
    public JSONObject synthesizeSpeech(@NonNull String body, int maxChars, @NonNull SpeechAudioSink sink)
            throws JSONException, IOException {
        JSONObject request = parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, maxChars);
        if (!parsed.isValid()) return speechRefusal(parsed);
        TaiModelSpec spec = resolveTtsModel(request, parsed);
        if (spec == null) return ttsModelError(request, parsed);
        if (!shouldDelegateRuntime()) {
            // In-process (a runtime override): synthesise straight into the sink.
            MultiBackendTaiRuntime router = ttsRouter();
            if (router == null) return noTtsRuntime();
            final IOException[] failure = new IOException[1];
            JSONObject result = router.synthesizeSpeech(spec, parsed.text, parsed.voice, parsed.speed, (samples, count, index) -> {
                byte[] pcm = new byte[count * 2];
                TaiWav.floatToPcm16(samples, 0, count, pcm, 0);
                try {
                    sink.onAudio(pcm, router.ttsSampleRate());
                    return true;
                } catch (IOException e) {
                    failure[0] = e;
                    return false;
                }
            }, () -> failure[0] != null);
            if (failure[0] != null) throw failure[0];
            return result;
        }
        if (runtimeClient == null) return error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
        final JSONObject[] summary = new JSONObject[1];
        try {
            runtimeClient.stream(TaiRuntimeIpc.OP_TTS_SYNTHESIZE, delegatedTtsBody(parsed, spec), new OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) throws IOException {
                    String file = event.optString("file", "");
                    if (!file.isEmpty()) {
                        File pcm = new File(file);
                        try {
                            sink.onAudio(readAllBytes(pcm), event.optInt("sampleRate", KittenTtsRuntime.SAMPLE_RATE));
                        } finally {
                            //noinspection ResultOfMethodCallIgnored
                            pcm.delete();
                        }
                    } else if (event.has("result")) {
                        summary[0] = event.optJSONObject("result");
                    } else if (event.has("error")) {
                        summary[0] = event;
                    }
                }

                @Override
                public void onDone() {
                }
            });
        } catch (IOException | RuntimeException e) {
            // The client went away mid-stream: stop synthesising audio nobody will hear.
            try {
                stopSpeaking();
            } catch (JSONException ignored) {
            }
            throw e;
        }
        if (summary[0] == null) return error(500, "tts_stream_incomplete", "The speech runtime ended without a result.");
        return summary[0];
    }

    /** The runtime-process half of {@link #synthesizeSpeech}: files out, one event per sentence, then the summary. */
    void synthesizeSpeechToEvents(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        JSONObject request = parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, TaiSpeechRequest.MAX_SPEAK_CHARS);
        TaiModelSpec spec = parsed.isValid() ? resolveTtsModel(request, parsed) : null;
        MultiBackendTaiRuntime router = ttsRouter();
        JSONObject result;
        if (!parsed.isValid()) {
            result = speechRefusal(parsed);
        } else if (spec == null) {
            result = ttsModelError(request, parsed);
        } else if (router == null) {
            result = noTtsRuntime();
        } else {
            JSONObject refusal = decideTtsLoad(router, spec);
            if (refusal != null) {
                result = refusal;
            } else {
                final IOException[] failure = new IOException[1];
                result = TaiSpeechOutput.synthesizeToFiles(appContext, router, spec, parsed, (file, samples, sampleRate, index) -> {
                    try {
                        JSONObject event = new JSONObject();
                        event.put("file", file.getAbsolutePath());
                        event.put("samples", samples);
                        event.put("sampleRate", sampleRate);
                        event.put("index", index);
                        sink.onEvent(event);
                        return true;
                    } catch (IOException | JSONException e) {
                        failure[0] = e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        return false;
                    }
                });
                if (failure[0] != null) throw failure[0];
            }
        }
        if (result.has("error") && !result.optBoolean("ok", true)) {
            sink.onEvent(result);
        } else {
            sink.onEvent(new JSONObject().put("result", result));
        }
        sink.onDone();
    }

    @NonNull
    private TaiSpeechRequest parseSpeechRequest(@NonNull JSONObject request, int maxChars) {
        return TaiSpeechRequest.parse(request, settings.getTtsVoice(), settings.getTtsSpeed(), maxChars);
    }

    @NonNull
    private JSONObject speechRefusal(@NonNull TaiSpeechRequest parsed) throws JSONException {
        return openAiRequestError(parsed.statusCode, parsed.errorCode == null ? "bad_request" : parsed.errorCode,
            parsed.errorMessage == null ? "Bad speech request." : parsed.errorMessage, parsed.errorParam);
    }

    @NonNull
    private JSONObject noTtsRuntime() throws JSONException {
        return openAiRequestError(501, "capability_not_supported", "Speech output needs the multi-backend runtime.", "model");
    }

    /**
     * The speech-output model a request names, or the installed one when it names none, one of
     * OpenAI's speech model names, or an id this phone has never heard of (OpenAI-shaped clients
     * send a model field they may not know how to leave out); {@code null} when that does not
     * resolve to a {@code text_to_speech} model. In the runtime process the app process's
     * resolved spec rides along in the body.
     */
    @Nullable
    private TaiModelSpec resolveTtsModel(@NonNull JSONObject request, @NonNull TaiSpeechRequest parsed) {
        String modelId = parsed.model;
        if (runtimeProcess) {
            TaiModelSpec supplied = resolveModel(request, modelId);
            return supplied != null && TaiTtsModels.isTtsModel(supplied) ? supplied : null;
        }
        if (TaiSpeechRequest.isDefaultModelAlias(modelId)) return TaiTtsModels.resolveActive(appContext, modelStore);
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null && TaiModelCatalog.get(modelId) == null) return TaiTtsModels.resolveActive(appContext, modelStore);
        return spec != null && TaiTtsModels.isTtsModel(spec) ? spec : null;
    }

    @NonNull
    private JSONObject ttsModelError(@NonNull JSONObject request, @NonNull TaiSpeechRequest parsed) throws JSONException {
        String modelId = parsed.model;
        TaiModelSpec spec = TaiSpeechRequest.isDefaultModelAlias(modelId) ? null : resolveModel(request, modelId);
        if (spec != null && !TaiTtsModels.isTtsModel(spec)) {
            return openAiRequestError(400, "not_a_tts_model", "Model '" + modelId + "' is not a voice model.", "model");
        }
        if (!TaiSpeechRequest.isDefaultModelAlias(modelId) && TaiModelCatalog.get(modelId) != null) {
            return openAiRequestError(400, "tts_model_not_installed", "Model '" + modelId + "' is not installed.", "model");
        }
        return openAiRequestError(400, "tts_model_not_installed",
            "No voice model is installed. Get one in On-device AI settings > Model centre > Speech > Voice output.", "model");
    }

    /** The runtime-process body: the parsed request with the resolved spec riding along. */
    @NonNull
    private String delegatedTtsBody(@NonNull TaiSpeechRequest parsed, @NonNull TaiModelSpec spec) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("input", parsed.text);
        body.put("voice", parsed.voice);
        body.put("speed", (double) parsed.speed);
        body.put("response_format", parsed.format);
        body.put("model", spec.id);
        body.put(INTERNAL_MODEL_SPEC, spec.toJson());
        return body.toString();
    }

    @Nullable
    private MultiBackendTaiRuntime ttsRouter() {
        TaiRuntime local = runtime;
        return local instanceof MultiBackendTaiRuntime ? (MultiBackendTaiRuntime) local : null;
    }

    /**
     * The budget for a speech-output model that is not resident yet: the measured or estimated
     * cost against what is free, with idle embeddings evicted if that is what it takes. Speech
     * output never evicts speech-to-text or chat: dictating and chatting matter more than a voice
     * that reloads in a second. {@code null} means go ahead.
     */
    @Nullable
    private JSONObject decideTtsLoad(@NonNull MultiBackendTaiRuntime router, @NonNull TaiModelSpec spec) throws JSONException {
        if (router.residency().isResident(TaiResidency.Kind.TTS, spec.id)) return null;
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        List<TaiResidency.Entry> residents = router.residency().snapshot();
        long available = TaiResidency.creditedAvailable(availableMemory(device), residents, TaiResidency.Kind.TTS, spec.backend);
        long worst = TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0);
        TaiLoadBudget.Estimate estimate = worst > 0L ? TaiLoadBudget.Estimate.measured(worst)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.ttsEstimateBytes(spec), 0L);
        List<TaiResidency.Entry> candidates = new ArrayList<>();
        for (TaiResidency.Entry entry : TaiResidency.evictionCandidates(residents, TaiResidency.Kind.TTS, spec.backend)) {
            if (entry.kind == TaiResidency.Kind.EMBEDDING) candidates.add(entry);
        }
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            candidates, gateConditions());
        if (!plan.fits) return openAiError(insufficientMemory(spec.displayName, plan));
        evict(plan);
        return null;
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull File file) throws IOException {
        long length = file.length();
        if (length > Integer.MAX_VALUE) throw new IOException("Audio file too large");
        byte[] out = new byte[(int) length];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int done = 0;
            while (done < out.length) {
                int read = in.read(out, done, out.length - done);
                if (read < 0) break;
                done += read;
            }
            return done == out.length ? out : java.util.Arrays.copyOf(out, done);
        }
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
        return requested.isEmpty() ? TaiSpeechModels.activeModelId(appContext) : requested;
    }

    @NonNull
    private JSONObject speechModelError(@NonNull JSONObject request) throws JSONException {
        String modelId = speechModelIdFor(request);
        if (modelId.isEmpty()) {
            return openAiRequestError(400, "stt_model_not_configured",
                "No speech-to-text model is installed. Get one in On-device AI settings > Model centre > Speech.", "model");
        }
        TaiModelSpec spec = resolveModel(request, modelId);
        if (spec == null) return openAiRequestError(404, "model_not_found", "Unknown model: " + modelId, "model");
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
        if (spec == null) return error(404, "model_not_found", "Unknown model: " + modelId);
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
        // Resident: used as it is, unless a feature asks for a larger window than it was loaded with
        // (decision 5) - then it reloads once at the feature's window, and stays if that cannot happen.
        boolean resident = localRuntime().isModelLoaded(modelId);
        if (resident && residentServes(spec, options)) {
            markUsedByFeature(spec, options);
            return null;
        }
        if (hasInjectedRuntimeOverride()) {
            JSONObject load = localRuntime().load(spec, options);
            if (!load.optBoolean("ok", false)) return load;
            return null;
        }
        if (!settings.isOpenAiAutoLoadEnabled()) {
            if (resident) return null;
            JSONObject data = error(409, "model_not_loaded",
                "Model is not loaded. Load it explicitly with tai load or from On-device AI settings.");
            data.put("autoLoadEnabled", false);
            return data;
        }
        TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options, true);
        if (preflight.blocked) {
            return resident ? null : preflight.blockingError(preflightStatusCode(preflight));
        }
        LoadDecision decision = decideLoad(spec, options, preflight);
        if (decision.refusal != null) return resident ? null : decision.refusal;
        JSONObject load = loadWithCanary(spec, decision.options);
        load.put("preflight", preflight.toJson());
        decision.describe(load);
        recordRuntimeResult(spec, preflight, load);
        if (!load.optBoolean("ok", false)) return load;
        markUsedByFeature(spec, options);
        return null;
    }

    /** The canary runs at most once per process, whatever it finds. */
    private static final java.util.concurrent.atomic.AtomicBoolean GPU_CANARY_RAN =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * A chat load, with the GPU check of spec section 2.3 after it. On the first GPU load of a Gemma 4
     * file on a phone whose GPU path is unconfirmed, a fixed prompt is answered greedily before the
     * caller's request; a wrong answer (or a crash, which {@link TaiGpuVerdict} reads on the next
     * start) stores Failed, and this load is redone on the CPU with the reason stamped on it.
     */
    @NonNull
    private JSONObject loadWithCanary(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) throws JSONException {
        JSONObject result = localRuntime().load(spec, options);
        if (!result.optBoolean("ok", false)) return result;
        TaiPlatformCaps.GpuPath path = TaiPlatformCaps.cached(appContext).gpuPath;
        if (!TaiGpuVerdict.shouldRunCanary(path, spec.id, options.accelerator, GPU_CANARY_RAN.get())) return result;
        if (!GPU_CANARY_RAN.compareAndSet(false, true)) return result;
        boolean passed;
        TaiGpuVerdict.beginCanary(appContext);
        try {
            TaiRuntimeOptions canary = options.withGenerationOverrides(TaiGpuVerdict.CANARY_MAX_TOKENS, null, null, 0.0,
                null, null, null, null, null, Boolean.FALSE, Boolean.FALSE);
            JSONObject reply = localRuntime().chat(spec.id, "", TaiGpuVerdict.CANARY_PROMPT, canary);
            passed = reply.optBoolean("ok", false) && TaiGpuVerdict.canaryPasses(reply.optString("response", ""));
        } catch (JSONException | RuntimeException e) {
            passed = false;
        }
        TaiGpuVerdict.finishCanary(appContext, passed);
        if (passed) return result;
        TaiEventLog.log(appContext, TaiEventLog.ACCEL_FALLBACK, spec.id, spec.backend, TaiTierPolicy.ACCEL_GPU,
            0, 0L, 0L, TaiGpuVerdict.REASON_FAILED);
        TaiAcceleratorFallback.set(spec.id, TaiGpuVerdict.REASON_FAILED);
        JSONObject cpu = localRuntime().load(spec, options.withAccelerator(TaiTierPolicy.ACCEL_CPU));
        if (cpu.optBoolean("ok", false) && cpu.isNull("backendFallbackReason")) {
            cpu.put("backendFallbackReason", TaiGpuVerdict.REASON_FAILED);
        }
        return cpu;
    }

    /** The options a load goes ahead with, or the refusal the memory budget answered instead. */
    private static final class LoadDecision {
        @Nullable final TaiRuntimeOptions options;
        @Nullable final JSONObject refusal;
        @NonNull final TaiLoadBudget.Plan plan;
        /** Residents closed to make room, in order; empty when the load fit as is. */
        @NonNull final List<String> evicted;
        /** Why the plan's accelerator is not the model's first choice; empty when it is. */
        @NonNull final String acceleratorFallbackReason;

        LoadDecision(@Nullable TaiRuntimeOptions options, @Nullable JSONObject refusal,
                     @NonNull TaiLoadBudget.Plan plan, @NonNull List<String> evicted) {
            this(options, refusal, plan, evicted, "");
        }

        LoadDecision(@Nullable TaiRuntimeOptions options, @Nullable JSONObject refusal,
                     @NonNull TaiLoadBudget.Plan plan, @NonNull List<String> evicted,
                     @NonNull String acceleratorFallbackReason) {
            this.options = options;
            this.refusal = refusal;
            this.plan = plan;
            this.evicted = evicted;
            this.acceleratorFallbackReason = acceleratorFallbackReason;
        }

        /** Stamps the load result with the budget and what was evicted for it. */
        void describe(@NonNull JSONObject result) throws JSONException {
            result.put("memoryBudget", planJson(plan));
            result.put("evicted", new JSONArray(evicted));
            if (!acceleratorFallbackReason.isEmpty()) {
                result.put("acceleratorFallbackReason", acceleratorFallbackReason);
                if (result.isNull("backendFallbackReason")) result.put("backendFallbackReason", acceleratorFallbackReason);
            }
        }
    }

    /**
     * A dry run of the memory budget for a momentary load of {@code modelId} (a model the caller
     * unloads within a minute, such as a background job) as it would be decided now: the
     * same request {@link #decideLoad} builds, but nothing is evicted, logged or loaded. Null when
     * it cannot be worked out (unknown model, a blocked preflight, an error): the caller then goes
     * ahead and lets the real load decide. Not for the main thread.
     */
    @Nullable
    public TaiLoadBudget.Plan previewMomentaryLoad(@NonNull String modelId, @Nullable String accelerator,
                                                   int contextWindow) {
        try {
            TaiModelSpec spec = resolveModel(modelId);
            if (spec == null) return null;
            JSONObject request = new JSONObject();
            request.put("load_class", "momentary");
            request.put("context_window", contextWindow);
            if (accelerator != null) request.put("accelerator", accelerator);
            TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
            TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(appContext, spec, options, true);
            if (preflight.blocked) return null;
            TaiDeviceCapabilities device = preflight.device;
            List<String> accelerators;
            if (!"auto".equals(preflight.requestedAccelerator) || TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)) {
                accelerators = Collections.singletonList(preflight.effectiveAccelerator);
            } else {
                accelerators = TaiLoadPreflight.autoAccelerators(appContext, spec, device, preflight.profile,
                    TaiLoadPreflight.normalizeAccelerator(options.preferredAccelerator));
                if (accelerators.isEmpty()) accelerators = Collections.singletonList(preflight.effectiveAccelerator);
            }
            int cap = TaiContextWindowPolicy.effectiveEndpointContextWindow(spec, device.memoryBytes, options.contextWindow);
            boolean encoders = spec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)
                || spec.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
            List<TaiResidency.Entry> residents = residency().snapshot();
            TaiMemInfo.Reading memory = TaiMemInfo.read(appContext);
            long available = TaiResidency.creditedAvailable(availableMemory(memory, device), residents,
                TaiResidency.Kind.CHAT, spec.backend);
            return TaiLoadBudget.plan(new TaiLoadBudget.Request(spec.backend, TaiResidency.fileBytes(spec), encoders,
                device.physicalMemoryBytes, available, accelerators, cap,
                null, 0, options.contextWindow != null,
                measuredHistory(spec, device), TaiResidency.evictionCandidates(residents, TaiResidency.Kind.CHAT, spec.backend),
                true)
                .withConditions(gateConditions())
                .withKvBytesPerToken(kvBytesPerToken(spec))
                .withGpuless(!device.supportsAccelerator("gpu")));
        } catch (Exception | LinkageError e) {
            return null;
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
            accelerators = TaiLoadPreflight.autoAccelerators(appContext, spec, device, preflight.profile,
                TaiLoadPreflight.normalizeAccelerator(options.preferredAccelerator));
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
        TaiMemInfo.Reading memory = TaiMemInfo.read(appContext);
        long available = TaiResidency.creditedAvailable(availableMemory(memory, device), residents,
            TaiResidency.Kind.CHAT, spec.backend);
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(spec.backend, fileBytes, encoders,
            device.physicalMemoryBytes, available, accelerators, cap,
            crashedAccelerator, crashedContext, options.contextWindow != null,
            measuredHistory(spec, device), TaiResidency.evictionCandidates(residents, TaiResidency.Kind.CHAT, spec.backend),
            options.momentary == Boolean.TRUE)
            .withConditions(gateConditions())
            .withKvBytesPerToken(kvBytesPerToken(spec))
            .withGpuless(!device.supportsAccelerator("gpu")));
        if (!plan.fits) {
            TaiEventLog.log(appContext, TaiEventLog.OOM_GUARD, spec.id, spec.backend, plan.accelerator,
                plan.contextWindow, 0L, plan.neededFreeBytes(),
                "load refused: needs " + plan.neededFreeBytes() / (1024L * 1024L) + " MB free, "
                    + plan.availableBytes / (1024L * 1024L) + " MB available");
            return new LoadDecision(null, insufficientMemory(spec.displayName, plan), plan, Collections.<String>emptyList());
        }
        if (plan.overcommitted) {
            TaiEventLog.log(appContext, TaiEventLog.OOM_GUARD, spec.id, spec.backend, plan.accelerator,
                plan.contextWindow, 0L, plan.neededFreeBytes(),
                "load past the budget (unrestricted): needs " + plan.neededFreeBytes() / (1024L * 1024L) + " MB free, "
                    + plan.availableBytes / (1024L * 1024L) + " MB available");
        }
        String fallbackReason = acceleratorFallbackReason(spec, device, preflight, plan, fileBytes, encoders, available,
            options.momentary == Boolean.TRUE, TaiLoadPreflight.normalizeAccelerator(options.preferredAccelerator));
        TaiAcceleratorFallback.set(spec.id, fallbackReason);
        if (!fallbackReason.isEmpty()) {
            TaiEventLog.log(appContext, TaiEventLog.ACCEL_FALLBACK, spec.id, spec.backend, plan.accelerator,
                plan.contextWindow, 0L, 0L, fallbackReason);
        }
        List<String> evicted = evict(plan);
        TaiRuntimeOptions loadOptions = optionsForPreflight(spec, options, preflight);
        if (!TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend) && plan.accelerator != null
                && !plan.accelerator.equals(preflight.effectiveAccelerator)) {
            loadOptions = loadOptions.withAccelerator(plan.accelerator);
        }
        return new LoadDecision(loadOptions.withContextWindow(plan.contextWindow), null, plan, evicted, fallbackReason);
    }

    /**
     * Why a load goes ahead on other than the model's first compatible accelerator, or an empty
     * string when it does not: either the first one carries an unexpired failure record, or the
     * memory budget could not afford it at the floor window. Only automatic loads of LiteRT models
     * choose; an explicit accelerator is the caller's.
     */
    @NonNull
    private String acceleratorFallbackReason(
        @NonNull TaiModelSpec spec,
        @NonNull TaiDeviceCapabilities device,
        @NonNull TaiLoadPreflight.Result preflight,
        @NonNull TaiLoadBudget.Plan plan,
        long fileBytes,
        boolean encoders,
        long available,
        boolean momentary,
        @Nullable String preferred
    ) {
        if (!"auto".equals(preflight.requestedAccelerator) || TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)) return "";
        if (plan.accelerator == null) return "";
        // The feature's plan, when it names one the model and device take, is the first choice.
        String first = preferred != null && preflight.profile.supports(preferred) && device.supportsAccelerator(preferred)
            ? preferred : null;
        for (String accelerator : preflight.profile.compatibleAccelerators) {
            if (first != null) break;
            if (device.supportsAccelerator(accelerator)) first = accelerator;
        }
        if (first == null || first.equals(plan.accelerator)) return "";
        JSONObject failed = TaiRuntimeHistory.failedEntry(appContext, spec, device, first);
        if (failed != null) {
            return "history_failure: " + failed.optString("reason", "load failed");
        }
        // Quoted at the window the plan actually tried for this accelerator (the director's 2048,
        // not the chat floor of 4096): the figure in the event must be the one that was refused.
        TaiLoadBudget.Estimate estimate = TaiLoadBudget.estimate(spec.backend, first, fileBytes, encoders,
            plan.contextWindow, measuredHistory(spec, device), kvBytesPerToken(spec));
        long needed = estimate.nonReclaimableBytes + plan.reserveBytes;
        return (momentary ? "budget(momentary): needs " : "budget: needs ") + needed / (1024L * 1024L) + " MB free, "
            + available / (1024L * 1024L) + " MB available";
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
        long available = TaiResidency.creditedAvailable(availableMemory(device), residents,
            TaiResidency.Kind.EMBEDDING, spec.backend);
        long worst = TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device, spec.backend, "cpu", 0);
        TaiLoadBudget.Estimate estimate = worst > 0L ? TaiLoadBudget.Estimate.measured(worst)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.embeddingEstimateBytes(spec), 0L);
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.EMBEDDING, spec.backend), gateConditions());
        if (!plan.fits) {
            JSONObject envelope = openAiError(embeddingMemoryRefusal(spec.displayName, plan));
            envelope.put("_retryAfterSeconds", EMBEDDING_MEMORY_RETRY_AFTER_SECONDS);
            return envelope;
        }
        evict(plan);
        return null;
    }

    /**
     * Insufficient memory for an embedding load, dawn brief item 5's shape: {@code 503}, a
     * {@code Retry-After} the server layer turns into a header (see {@code _retryAfterSeconds} in
     * {@link com.termux.launcherctl.LauncherCtlApiServer#jsonResponse}), and the stable
     * {@code embedding_memory} code so dawn can back off without guessing at a message string.
     */
    private static boolean contains(@NonNull int[] values, int value) {
        for (int v : values) if (v == value) return true;
        return false;
    }

    @NonNull
    static JSONObject embeddingMemoryRefusal(@NonNull String displayName, @NonNull TaiLoadBudget.Plan plan) throws JSONException {
        JSONObject refusal = insufficientMemory(displayName, plan);
        refusal.put("error", "embedding_memory");
        refusal.put("_statusCode", 503);
        refusal.put("_retryAfterSeconds", EMBEDDING_MEMORY_RETRY_AFTER_SECONDS);
        return refusal;
    }

    // ---- Image generation (MNN diffusion) -------------------------------------------------------

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
        return prepareImage(parseBody(body)).refusal;
    }

    @NonNull
    private PreparedImage prepareImage(@NonNull JSONObject request) throws JSONException {
        TaiImageRequest parsed = TaiImageRequest.parse(request, TaiMediaAccess::resolveLocalPath);
        if (!parsed.isValid()) {
            return new PreparedImage(openAiRequestError(parsed.statusCode,
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
            spec = resolveModel(request, modelId);
            if (spec == null) {
                return new PreparedImage(openAiRequestError(404, "model_not_found", "Unknown model: " + modelId, "model"),
                    null, null, null, null);
            }
            if (!spec.isImageGeneration()) {
                return new PreparedImage(openAiRequestError(400, "not_an_image_model",
                    "Model '" + modelId + "' is not an image model.", "model"), null, null, null, null);
            }
            if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
                return new PreparedImage(openAiRequestError(404, "model_file_missing",
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
            return new PreparedImage(openAiRequestError(status, pkg.failure == null ? "image_model_invalid" : pkg.failure,
                pkg.message, param), null, null, null, null);
        }
        TaiImageRequest.Refusal typeRefusal = TaiImageRequest.validateForPackage(parsed, pkg.type, pkg.supportsImageInput);
        if (typeRefusal != null) {
            return new PreparedImage(openAiRequestError(typeRefusal.statusCode, typeRefusal.code, typeRefusal.message,
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
        JSONObject request = parseBody(body);
        PreparedImage prepared = prepareImage(request);
        if (prepared.refusal != null) return prepared.refusal;
        TaiImageRequest parsed = prepared.request;
        if (parsed == null) return error(500, "image_request_invalid", "The image request could not be read.");
        JSONObject summary;
        if (!shouldDelegateRuntime()) {
            // In-process (a runtime override): run straight through the router.
            final IOException[] failure = new IOException[1];
            summary = runPreparedImage(prepared, percent -> {
                try {
                    progress.onProgress(percent);
                } catch (IOException e) {
                    failure[0] = e;
                    MultiBackendTaiRuntime router = ttsRouter();
                    if (router != null) router.cancelImage();
                }
            });
            if (failure[0] != null) {
                discardImageFile(summary);
                throw failure[0];
            }
        } else {
            if (runtimeClient == null) return error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
            if (prepared.spec != null && parsed.modelPath == null) request.put(INTERNAL_MODEL_SPEC, prepared.spec.toJson());
            final JSONObject[] holder = new JSONObject[1];
            try {
                runtimeClient.stream(TaiRuntimeIpc.OP_IMAGE_GENERATE, request.toString(), new OpenAiStreamSink() {
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
            if (summary == null) return error(500, "image_stream_incomplete", "The image runtime ended without a result.");
        }
        if (summary.has("error") && !summary.has("file")) return summary;
        return finishImage(summary, parsed, prepared);
    }

    @NonNull
    private JSONObject finishImage(@NonNull JSONObject summary, @NonNull TaiImageRequest parsed,
                                   @NonNull PreparedImage prepared) throws JSONException, IOException {
        File file = new File(summary.optString("file", ""));
        if (!file.isFile()) return error(500, "image_file_missing", "The generated image could not be found.");
        JSONObject item = new JSONObject();
        try {
            if (parsed.outputPath != null) {
                File target = new File(parsed.outputPath);
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    return openAiRequestError(400, "invalid_output", "The output folder could not be created.", "output");
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
                item.put("b64_json", Base64.getEncoder().encodeToString(readAllBytes(file)));
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
        PreparedImage prepared = prepareImage(parseBody(body));
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
        MultiBackendTaiRuntime router = ttsRouter();
        TaiImageRequest parsed = prepared.request;
        TaiModelSpec spec = prepared.spec;
        TaiDiffusionPackage.Result pkg = prepared.pkg;
        if (router == null || parsed == null || spec == null || pkg == null || prepared.modelDir == null) {
            return openAiRequestError(501, "capability_not_supported", "Image generation needs the multi-backend runtime.", "model");
        }
        ImageLoadDecision decision = decideImageLoad(router, spec, pkg, parsed);
        if (decision.refusal != null) return decision.refusal;
        File dir = new File(appContext.getCacheDir(), STT_IPC_DIR);
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
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        List<TaiResidency.Entry> residents = router.residency().snapshot();
        TaiResidency.Entry resident = router.residency().find(TaiResidency.Kind.IMAGE, spec.id);
        if (resident != null && (request.memoryMode < 0 || request.memoryMode == resident.window)) {
            return new ImageLoadDecision(null, resident.window, Collections.<String>emptyList());
        }
        long available = TaiResidency.creditedAvailable(availableMemory(device), residents, TaiResidency.Kind.IMAGE,
            TaiModelSpec.BACKEND_MNN_DIFFUSION);
        TaiImageAdmission.Decision decision = TaiImageAdmission.decide(pkg.peakBytes, request.memoryMode, request.backend,
            device.physicalMemoryBytes, available,
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.IMAGE, TaiModelSpec.BACKEND_MNN_DIFFUSION),
            mode -> TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device, TaiModelSpec.BACKEND_MNN_DIFFUSION,
                request.backend, mode + 1), gateConditions());
        if (!decision.fits) return new ImageLoadDecision(openAiError(insufficientMemory(spec.displayName, decision.plan)), decision.memoryMode,
            Collections.<String>emptyList());
        return new ImageLoadDecision(null, decision.memoryMode, evict(decision.plan));
    }

    /** Discards the image generation in flight; answers {@code {ok, cancelled}}. Never waits for the engine. */
    @NonNull
    public JSONObject cancelImage() throws JSONException {
        if (shouldDelegateRuntime()) return runtimeRequest(TaiRuntimeIpc.OP_IMAGE_CANCEL, "{}", RUNTIME_STATUS_TIMEOUT_MS);
        MultiBackendTaiRuntime router = ttsRouter();
        boolean cancelled = router != null && router.cancelImage();
        return new JSONObject().put("ok", true).put("cancelled", cancelled);
    }

    /** How long dawn should wait before retrying an embedding request refused for memory. */
    private static final int EMBEDDING_MEMORY_RETRY_AFTER_SECONDS = 20;

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
        long available = TaiResidency.creditedAvailable(availableMemory(device), residents,
            TaiResidency.Kind.STT, spec.backend);
        long worst = TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0);
        TaiLoadBudget.Estimate estimate = worst > 0L ? TaiLoadBudget.Estimate.measured(worst)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.sttEstimateBytes(spec), 0L);
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.STT, spec.backend), gateConditions());
        if (!plan.fits) return openAiError(insufficientMemory(spec.displayName, plan));
        evict(plan);
        return null;
    }

    /**
     * The measured load costs of this model on this device, as the budget asks for them; with none
     * of its own yet, the shipped {@link TaiLoadPriors} for the bundled Gemma files, so the first
     * load is planned on a measurement rather than the seed that refused it.
     */
    @NonNull
    private TaiLoadBudget.History measuredHistory(@NonNull TaiModelSpec spec, @NonNull TaiDeviceCapabilities device) {
        long fileBytes = TaiResidency.fileBytes(spec);
        // What lies above the measured windows is carried up by the seed slope, and a vision key with
        // no samples of its own borrows the text key's plus the encoders' share of the file.
        long slope = TaiLoadBudget.seedSlopeBytes(spec.backend, fileBytes, kvBytesPerToken(spec));
        long encoderDelta = fileBytes / 10L;
        return (accelerator, contextTokens) -> {
            long measured = TaiRuntimeHistory.measuredLoadBytes(appContext, spec, device,
                spec.backend, accelerator, contextTokens, slope, encoderDelta);
            return measured > 0L ? measured : TaiLoadPriors.bytes(spec, accelerator, contextTokens, slope);
        };
    }

    /** The architecture's KV bytes per token for an MNN model whose config says so; {@code 0} keeps the file-size slope. */
    private static long kvBytesPerToken(@NonNull TaiModelSpec spec) {
        return TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend) ? TaiLoadBudget.mnnKvBytesPerToken(spec.localPath) : 0L;
    }

    /**
     * Free memory for the gate: {@link TaiMemInfo}'s reading (MemAvailable on every release), or
     * the device snapshot's {@code availMem} when the reading has nothing.
     */
    private static long availableMemory(@NonNull TaiMemInfo.Reading memory, @NonNull TaiDeviceCapabilities device) {
        return memory.availBytes > 0L ? memory.availBytes : device.availableMemoryBytes;
    }

    private long availableMemory(@NonNull TaiDeviceCapabilities device) {
        return availableMemory(TaiMemInfo.read(appContext), device);
    }

    /** The memory limits every plan in this process is made under; the runtime's watch reads the same file. */
    @NonNull
    private TaiLoadBudget.Conditions gateConditions() {
        return TaiMemInfo.conditions(appContext);
    }

    /** Carries out a plan's evictions through the router; the ids actually closed, in order. */
    @NonNull
    private List<String> evict(@NonNull TaiLoadBudget.Plan plan) throws JSONException {
        if (plan.evicted.isEmpty()) return Collections.emptyList();
        TaiRuntime local = localRuntime();
        if (!(local instanceof MultiBackendTaiRuntime)) return Collections.emptyList();
        List<String> evicted = ((MultiBackendTaiRuntime) local).evict(plan.evicted);
        for (TaiResidency.Entry victim : plan.evicted) {
            if (!evicted.contains(victim.modelId)) continue;
            TaiEventLog.log(appContext, TaiEventLog.EVICT, victim.modelId, victim.backend, victim.accelerator,
                victim.window, 0L, victim.bytes(), "load plan, " + victim.kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        return evicted;
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
        json.put("availableBytes", plan.availableBytes);
        json.put("reserveBytes", plan.reserveBytes);
        json.put("neededFreeBytes", plan.neededFreeBytes());
        json.put("measured", plan.measured);
        json.put("overcommitted", plan.overcommitted);
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
        if (!shouldRecordFailure(result)) return;
        TaiRuntimeHistory.recordFailure(appContext, spec, preflight.device, spec.backend, accelerator,
            result.optString("message", result.optString("error", "load_failed")));
    }

    /**
     * Whether a non-ok load result is a verdict on its accelerator. A cancelled load once wrote
     * "Model load cancelled." as a GPU failure and demoted the GPU for good; see
     * {@link TaiRuntimeHistory#isRuntimeVerdict}.
     */
    static boolean shouldRecordFailure(@NonNull JSONObject result) {
        return !result.optBoolean("ok", false) && TaiRuntimeHistory.isRuntimeVerdict(result.optString("error", ""));
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
        // The feature's plan over the settings; the explicit fields below still win, as a pick for this request.
        TaiFeaturePlan plan = featurePlan(request);
        if (plan != null) options = plan.applyTo(options, spec.id);
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
        // "load_class": "momentary" declares a load the caller will unload within a minute, such as
        // a background job; the budget then keeps the lower peak floor instead of the hold
        // floor, and the runtime process itself unloads the model three minutes after the load
        // starts (TaiRuntimeService). The field rides in the request body to the runtime process,
        // which resolves it here again, so the decision made there sees the flag. Any other value
        // leaves the load ordinary.
        TaiRuntimeOptions overridden = options.withGenerationOverrides(maxTokens, topK, topP, temperature,
            accelerator, contextWindow, threadCount, precision, memoryMode, thinking, speculative);
        if (request.has("load_class") && "momentary".equals(request.optString("load_class", "").trim())) {
            overridden = overridden.withMomentary(Boolean.TRUE);
        }
        return overridden;
    }

    /**
     * The plan of the feature a request names: the one the app process resolved, in the runtime
     * process; resolved here otherwise. {@code null} for a request that names no feature.
     */
    @Nullable
    private TaiFeaturePlan featurePlan(@NonNull JSONObject request) {
        if (runtimeProcess) return TaiFeaturePlan.fromJson(request.optJSONObject(INTERNAL_FEATURE_PLAN));
        TaiFunction feature = TaiCallerRequests.featureOf(request);
        return feature == null ? null : TaiFeaturePlans.forContext(appContext).plan(feature);
    }

    /**
     * Whether the resident {@code spec} can serve a feature's request as it is (decision 5): its window is
     * at least the one the feature asks, or the load that put it there asked as much and the memory budget
     * gave less, which a reload would only repeat. A request that names no feature always reuses.
     */
    private boolean residentServes(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) {
        if (options.feature == null) return true;
        TaiResidency.Entry resident = residency().find(TaiResidency.Kind.CHAT, spec.id);
        if (resident == null || resident.window <= 0) return true;
        long memory = TaiDeviceCapabilities.detect(appContext).memoryBytes;
        int wanted = TaiContextWindowPolicy.effectiveEndpointContextWindow(spec, memory, options.contextWindow);
        if (resident.window >= wanted) return true;
        if (resident.feature == null) return false;
        int asked = TaiFeaturePlan.windowFor(resident.feature);
        return TaiContextWindowPolicy.effectiveEndpointContextWindow(spec, memory, asked > 0 ? asked : null) >= wanted;
    }

    /** Records which feature used the resident chat model, for its groups and the next window check. */
    private void markUsedByFeature(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) {
        residency().markUsedBy(TaiResidency.Kind.CHAT, spec.id, TaiFunction.fromId(options.feature));
    }

    /**
     * An explicit load for a feature whose model the runtime already holds well enough: the state as it
     * is, marked {@code reused}, instead of a reload that costs seconds and a second memory peak.
     * {@code null} when the load should go ahead.
     */
    @Nullable
    private JSONObject reuseForFeature(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) throws JSONException {
        if (options.feature == null || !localRuntime().isModelLoaded(spec.id) || !residentServes(spec, options)) return null;
        markUsedByFeature(spec, options);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("reused", true);
        result.put("modelId", spec.id);
        result.put("runtime", localRuntime().getState().toJson());
        return result;
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
        long available = availableMemory(device);
        if (available > 0L && presence.loaded) available += presence.residentChatBytes;
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(spec.backend, TaiResidency.fileBytes(spec),
            false, device.physicalMemoryBytes, available,
            TaiLoadPreflight.autoAccelerators(appContext, spec, device, TaiModelProfile.forModel(spec)),
            spec.endpointContextWindow, null, 0, explicitContext,
            measuredHistory(spec, device), Collections.<TaiResidency.Entry>emptyList())
            .withConditions(gateConditions()));
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
        String message = source.optString("message", "On-device AI request failed");
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
        limitations.put("OpenAI function tools are returned for client-side execution; On-device AI does not automatically execute shell commands or device actions.");
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

        // _tai_no_system_prompt (the category sort): the user's own TAI system prompt is not injected.
        // A system message the client sent still counts. The flag is read here and goes no further.
        String systemPrompt = clientSystemPrompt.length() > 0
            ? clientSystemPrompt.toString()
            : TaiCallerRequests.wantsNoSystemPrompt(request) ? "" : settings.getSystemPrompt(spec.id);
        TaiCallerRequests.stripPrivateFlags(request);
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
                    throw new UnsupportedOperationException("On-device AI uses client-side tool execution.");
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
            String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
            if (normalized.endsWith(".tflite")) {
                capabilities.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
            } else if (normalized.endsWith(".litertlm") && fileName.contains("embeddinggemma")) {
                // EmbeddingGemma 2 ships as a .litertlm bundle served by LiteRtLmEmbeddingRuntime.
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
