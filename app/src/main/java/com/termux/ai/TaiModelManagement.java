package com.termux.ai;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Model import, download and delete management. Moved out of TaiManager. */
public final class TaiModelManagement {
    private final TaiManager manager;

    TaiModelManagement(@NonNull TaiManager manager) {
        this.manager = manager;
    }

    @NonNull
    public JSONObject importModel(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        String path = request.optString("path", "").trim();
        if (path.isEmpty()) return manager.error(400, "bad_request", "Missing model path");
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
            JSONObject denied = manager.error(403, code, detail);
            denied.put("path", path);
            return denied;
        }
        File modelFile = new File(resolvedPath);
        File imageDirectory = diffusionImportDirectory(modelFile);
        if (imageDirectory != null) return importDiffusionPackage(request, imageDirectory, path);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            JSONObject error = manager.error(404, "model_file_not_readable", "Model file does not exist or is not readable by the app process");
            error.put("path", path);
            return error;
        }

        String modelId = manager.sanitizeModelId(request.optString("modelId", request.optString("model", modelFile.getName())));
        if (modelId.isEmpty()) return manager.error(400, "bad_request", "Missing model id");
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
                manager.capabilitiesFromRequest(request, modelId, modelFile.getName()),
                false
            );
        } catch (IllegalArgumentException e) {
            return manager.error(400, "unsupported_model_format",
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
        manager.modelStore.upsertUserModel(spec);

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
        String modelId = manager.sanitizeModelId(request.optString("modelId", request.optString("model", directory.getName())));
        if (modelId.isEmpty()) return manager.error(400, "bad_request", "Missing model id");
        int layout = TaiDiffusionImport.detectLayout(rootNames(directory));
        int hinted = TaiDiffusionPackage.parseType(request.optString("modelType", request.optString("type", "")));
        int type = hinted >= 0 && (layout == TaiDiffusionPackage.TYPE_SANA) == (hinted == TaiDiffusionPackage.TYPE_SANA)
            ? hinted : TaiDiffusionImport.typeFor(layout, directory.getName());
        if (type != TaiDiffusionPackage.TYPE_SANA) {
            TaiDiffusionTokenizer.Result tokenizer = TaiDiffusionTokenizer.ensure(directory, manager.appContext);
            if (!tokenizer.proceed()) {
                JSONObject refused = manager.error(400, "unsupported_image_model", tokenizer.message);
                refused.put("path", requestedPath);
                return refused;
            }
        }
        TaiDiffusionPackage.Result checked = TaiDiffusionPackage.inspect(directory, type);
        if (!checked.ok()) {
            JSONObject refused = manager.error(400, "unsupported_image_model", checked.message);
            refused.put("path", requestedPath);
            return refused;
        }
        TaiModelSpec spec = TaiDiffusionImport.spec(modelId, request.optString("displayName", modelId), "imported",
            request.optString("license", "User-provided model; license accepted externally"),
            directory.getAbsolutePath(), checked.type, checked.totalBytes);
        manager.modelStore.upsertUserModel(spec);
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
        return new TaiModelImporter(manager.appContext, manager.modelStore).importDocument(uri, modelId);
    }

    @NonNull
    public TaiModelImporter.DocumentMetadata modelDocumentMetadata(@NonNull Uri uri) {
        return new TaiModelImporter(manager.appContext, manager.modelStore).readMetadata(uri);
    }

    /** The image model type ({@code sd15}, {@code taiyi}, {@code sana}) a picked folder holds, or "". */
    @NonNull
    public String diffusionTypeOfFolder(@NonNull Uri tree) {
        return new TaiModelImporter(manager.appContext, manager.modelStore).diffusionTypeOfFolder(tree);
    }

    @NonNull
    public JSONObject downloadModel(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        boolean acceptedTerms = request.optBoolean("acceptedTerms", false);
        if (!acceptedTerms) {
            JSONObject error = manager.error(403, "terms_not_accepted", "Model downloads require acceptedTerms=true after reviewing provider license/terms");
            error.put("downloadsRequireExplicitUserAction", true);
            error.put("huggingFaceTokenBundled", false);
            return error;
        }
        String modelId = manager.sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        String url = request.optString("url", "").trim();
        if (modelId.isEmpty()) return manager.error(400, "bad_request", "Missing model id");
        if (url.isEmpty()) return manager.error(400, "bad_request", "Missing download URL");
        String token = request.optString("huggingFaceToken", manager.settings.getHuggingFaceToken());
        JSONObject selectedArtifact = null;
        // Accept a bare repo URL: auto-detect the backend and resolve to the package entry file
        // (config.json / .litertlm) so the user never picks a backend or hunts the HF file list.
        if (TaiHuggingFace.parse(url) != null) {
            boolean previewOnly = request.optBoolean("previewOnly", false);
            // The card is only read for a preview: it feeds the import flow's picker and summary,
            // and a real download (or an API client) has no use for it.
            TaiModelDownloader.HfResolve resolved = manager.modelDownloader.resolveHuggingFaceEntry(url, token, previewOnly);
            if (resolved.authRequired) {
                JSONObject gated = manager.error(403, "gated_model_requires_auth",
                    "This Hugging Face repo is gated or private. Save your Hugging Face access token "
                    + "(after accepting the model's terms on huggingface.co) and try again.");
                gated.put("huggingFaceTokenBundled", false);
                return gated;
            }
            if (resolved.candidates.length() > 1 || previewOnly && resolved.candidates.length() > 0) {
                JSONObject choices = manager.error(409, "artifact_selection_required", "Choose a model file to download.");
                choices.put("candidates", resolved.candidates);
                choices.put("modelFacts", resolved.facts);
                if (!resolved.readme.isEmpty()) choices.put("modelCard", resolved.readme);
                return choices;
            }
            if (resolved.url.isEmpty()) {
                return manager.error(400, "hf_resolve_failed", "Could not find a downloadable model file in that Hugging Face repo. "
                    + "Paste the repo URL (e.g. https://huggingface.co/taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN) or a direct .../resolve/main/<file> URL.");
            }
            url = resolved.url;
            selectedArtifact = resolved.candidates.optJSONObject(0);
        }
        if (selectedArtifact != null) {
            String required = selectedArtifact.optString("minimumRuntimeVersion", "");
            if (!required.isEmpty() && !TaiArtifactCompatibility.versionAtLeast(com.termux.BuildConfig.LITERT_LM_VERSION, required))
                return manager.error(400, "runtime_update_required", "This model file needs LiteRT-LM " + required
                    + " or later. This app includes " + com.termux.BuildConfig.LITERT_LM_VERSION + ".");
        }
        LinkedHashSet<String> capabilities = manager.capabilitiesFromRequest(request, modelId, url);
        TaiModelProfile runtimeProfile = null;
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(TaiModelSpec.inferBackend(url))) {
            TaiModelSpec importSpec = new TaiModelSpec(modelId, request.optString("displayName", modelId),
                "Downloaded model", "downloaded", url,
                request.optString("license", "User accepted provider terms externally"), 0L,
                capabilities, false);
            runtimeProfile = TaiModelProfile.fromRequest(request, TaiModelProfile.forModel(importSpec));
        }
        JSONObject data = manager.modelDownloader.startDownload(
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
        if (entry == null) return manager.error(404, "model_not_found", "Unknown catalog model: " + modelId);
        if (!entry.downloadAvailable) {
            JSONObject error = manager.error(409, "catalog_download_unavailable", entry.unavailableReason);
            error.put("providerPageUrl", entry.providerPageUrl);
            error.put("downloadAvailable", false);
            error.put("unavailableReason", entry.unavailableReason);
            return error;
        }
        if (entry.gated) {
            String token = manager.settings.getHuggingFaceToken();
            if (token.trim().isEmpty()) {
                JSONObject error = manager.error(403, "gated_model_requires_auth", "This model is gated on Hugging Face. Save a Hugging Face token after accepting the model terms.");
                error.put("providerPageUrl", entry.providerPageUrl);
                error.put("downloadUrl", entry.downloadUrl);
                error.put("huggingFaceTokenBundled", false);
                return error;
            }
        }
        return manager.modelDownloader.startCatalogDownload(entry, manager.settings.getHuggingFaceToken());
    }

    /** Downloads a speech-to-text catalog entry with the given window's graph (5 or 10 seconds).
     *  An unknown window (or a non-speech/window-less entry) downloads the entry's default artifact. */
    @NonNull
    public JSONObject downloadSpeechModel(@NonNull String modelId, int windowSeconds) throws JSONException {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return manager.error(404, "model_not_found", "Unknown catalog model: " + modelId);
        if (!entry.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) {
            return manager.error(400, "not_a_speech_model", "Model " + modelId + " is not a speech-to-text model.");
        }
        return manager.modelDownloader.startCatalogDownload(entry.withWindow(windowSeconds), manager.settings.getHuggingFaceToken());
    }

    @NonNull
    public JSONObject deleteModel(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        String modelId = manager.sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return manager.error(400, "bad_request", "Missing model id");
        TaiRuntimeState state = manager.getRuntimeState();
        boolean activeModelLoaded = state.loadedModelId != null && state.loadedModelId.equals(modelId);
        // A transfer still running for this model would re-create its record and files under the
        // delete; stop it first (its partial files go with it).
        if (manager.downloadEngine != null && request.optBoolean("confirm", false)) manager.downloadEngine.cancel(modelId);
        TaiModelStore.DeleteResult deleteResult = manager.modelStore.deleteUserModel(modelId,
            activeModelLoaded, request.optBoolean("confirm", false));
        if (!deleteResult.ok) return manager.error(409, deleteResult.errorCode, deleteResult.message);
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
        TaiRuntimeHistory.removeModel(manager.appContext, modelId);
        try {
            manager.benchHost().benchStore().removeModel(modelId);
        } catch (IOException ignored) {
        }
    }

    @NonNull
    public JSONObject downloads() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("downloads", manager.modelStore.getDownloads());
        data.put("parallel", manager.settings.getDownloadParallel());
        JSONArray running = new JSONArray();
        if (manager.downloadEngine != null) for (String id : manager.downloadEngine.runningTransferIds()) running.put(id);
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
        JSONObject request = manager.parseBody(body);
        String modelId = manager.sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return manager.error(400, "bad_request", "Missing model id");
        if (manager.downloadEngine == null) return manager.error(500, "downloads_unavailable", "Downloads run in the app process only.");
        boolean applied;
        switch (action) {
            case "pause": applied = manager.downloadEngine.pause(modelId, TaiModelStore.PAUSED_USER); break;
            case "resume": applied = manager.downloadEngine.resume(modelId); break;
            case "prioritize": applied = manager.downloadEngine.prioritize(modelId); break;
            default: applied = manager.downloadEngine.cancel(modelId); break;
        }
        JSONObject record = manager.modelStore.findDownloadForModel(modelId);
        if (!applied && record == null) return manager.error(404, "download_not_found", "No download for model " + modelId);
        if (!applied) {
            JSONObject conflict = manager.error(409, "download_state_conflict",
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
}
