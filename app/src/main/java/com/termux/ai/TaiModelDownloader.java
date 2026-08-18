package com.termux.ai;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class TaiModelDownloader {
    private static final String[] MNN_MODEL_FILES = new String[] {
        "config.json",
        "llm.mnn",
        "llm.mnn.weight",
        "llm_config.json",
        "llm.mnn.json",
        "tokenizer.mtok",
        "tokenizer.txt"
    };
    // The embedding runtime always loads the tokenizer from a file named exactly "sentencepiece.model"
    // next to the .tflite. Different HF repos publish that SentencePiece model under different names,
    // so we try each candidate and persist whichever we find under the canonical local name.
    private static final String LITERT_EMBEDDING_TOKENIZER = "sentencepiece.model";
    private static final String[] LITERT_EMBEDDING_TOKENIZER_CANDIDATES = new String[] {
        "sentencepiece.model",
        "tokenizer.model",
        "spiece.model"
    };

    public interface ProgressCallback {
        void onProgress(@NonNull JSONObject transfer);
    }

    private final Context appContext;
    private final TaiModelStore store;

    public TaiModelDownloader(@NonNull Context context, @NonNull TaiModelStore store) {
        appContext = context.getApplicationContext();
        this.store = store;
    }

    @NonNull
    public JSONObject startDownload(
        @NonNull String modelId,
        @NonNull String url,
        @NonNull String displayName,
        @NonNull String license,
        @NonNull LinkedHashSet<String> capabilities,
        @Nullable String authToken
    ) throws JSONException {
        return startDownload(modelId, url, displayName, license, capabilities, authToken, null);
    }

    @NonNull
    public JSONObject startDownload(
        @NonNull String modelId,
        @NonNull String url,
        @NonNull String displayName,
        @NonNull String license,
        @NonNull LinkedHashSet<String> capabilities,
        @Nullable String authToken,
        @Nullable TaiModelProfile runtimeProfile
    ) throws JSONException {
        return startDownload(modelId, url, displayName, license, capabilities,
            TaiModelSpec.inferBackend(url), TaiModelSpec.inferFormat(url), "", "", 4096, 0,
            "", 0L, authToken, runtimeProfile);
    }

    @NonNull
    public JSONObject startCatalogDownload(@NonNull TaiModelCatalog.CatalogEntry entry,
                                           @Nullable String authToken) throws JSONException {
        return startDownload(entry.modelId, entry.downloadUrl, entry.displayName, entry.license,
            entry.sourceCapabilities, entry.backend, entry.format, entry.architecture, entry.quantization,
            entry.endpointContextWindow, entry.recommendedRamGb, entry.sha256, entry.sizeBytes, authToken, null);
    }

    @NonNull
    private JSONObject startDownload(
        @NonNull String modelId, @NonNull String url, @NonNull String displayName,
        @NonNull String license, @NonNull LinkedHashSet<String> capabilities,
        @NonNull String backend, @NonNull String format, @Nullable String architecture,
        @Nullable String quantization, int contextWindow, int recommendedRamGb,
        @Nullable String expectedSha256, long expectedSizeBytes, @Nullable String authToken,
        @Nullable TaiModelProfile runtimeProfile
    ) throws JSONException {
        String safeModelId = sanitize(modelId);
        if (safeModelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (!url.startsWith("https://")) return error(400, "insecure_url", "Model downloads require an https URL");

        File modelDir = new File(store.getModelsDirectory(), safeModelId);
        File output = new File(modelDir, fileNameFromUrl(url));
        String transferId = "download-" + safeModelId;
        TaiModelDownloadService.clearCancellation(safeModelId);
        JSONObject transfer = withMetadata(transfer(transferId, safeModelId, url, output.getAbsolutePath(),
            TaiModelStore.STATE_QUEUED, 0L, expectedSizeBytes, ""),
            displayName, license, capabilities, backend, format, architecture, quantization, contextWindow,
            recommendedRamGb, expectedSha256);
        store.upsertDownload(transfer);

        Intent intent = new Intent(appContext, TaiModelDownloadService.class);
        intent.setAction(TaiModelDownloadService.ACTION_DOWNLOAD);
        intent.putExtra(TaiModelDownloadService.EXTRA_TRANSFER_ID, transferId);
        intent.putExtra(TaiModelDownloadService.EXTRA_MODEL_ID, safeModelId);
        intent.putExtra(TaiModelDownloadService.EXTRA_URL, url);
        intent.putExtra(TaiModelDownloadService.EXTRA_OUTPUT_PATH, output.getAbsolutePath());
        intent.putExtra(TaiModelDownloadService.EXTRA_DISPLAY_NAME, displayName);
        intent.putExtra(TaiModelDownloadService.EXTRA_LICENSE, license);
        intent.putExtra(TaiModelDownloadService.EXTRA_CAPABILITIES, capabilities.toArray(new String[0]));
        intent.putExtra(TaiModelDownloadService.EXTRA_AUTH_TOKEN, authToken == null ? "" : authToken);
        intent.putExtra(TaiModelDownloadService.EXTRA_EXPECTED_SIZE_BYTES, expectedSizeBytes);
        if (runtimeProfile != null) {
            intent.putExtra(TaiModelDownloadService.EXTRA_RUNTIME_PROFILE, runtimeProfile.toJson().toString());
        }
        putRuntimeMetadata(intent, backend, format, architecture, quantization, contextWindow,
            recommendedRamGb, expectedSha256);
        startService(intent);

        return started(transfer);
    }

    public void runDownload(
        String transferId,
        String modelId,
        String url,
        File output,
        String displayName,
        String license,
        LinkedHashSet<String> capabilities,
        String backend,
        String format,
        String architecture,
        String quantization,
        int contextWindow,
        int recommendedRamGb,
        String expectedSha256,
        long expectedSizeBytes,
        @Nullable String authToken,
        @Nullable ProgressCallback callback
    ) {
        runDownload(transferId, modelId, url, output, displayName, license, capabilities, backend,
            format, architecture, quantization, contextWindow, recommendedRamGb, expectedSha256,
            expectedSizeBytes, authToken, null, callback);
    }

    public void runDownload(
        String transferId,
        String modelId,
        String url,
        File output,
        String displayName,
        String license,
        LinkedHashSet<String> capabilities,
        String backend,
        String format,
        String architecture,
        String quantization,
        int contextWindow,
        int recommendedRamGb,
        String expectedSha256,
        long expectedSizeBytes,
        @Nullable String authToken,
        @Nullable TaiModelProfile runtimeProfile,
        @Nullable ProgressCallback callback
    ) {
        long bytesRead = 0L;
        long contentLength = 0L;
        try {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Failed to create model directory");
            }

            File partial = new File(output.getAbsolutePath() + ".part");
            long existing = partial.isFile() ? partial.length() : 0L;
            HttpURLConnection connection = open(url, authToken, existing);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Download failed with HTTP " + status);
            }
            boolean resumed = existing > 0L && status == 206;
            if (!resumed) existing = 0L;
            long responseLength = connection.getHeaderFieldLong("Content-Length", -1L);
            contentLength = responseLength > 0 ? existing + responseLength : -1L;
            if (expectedSizeBytes > 0L) contentLength = Math.max(contentLength, expectedSizeBytes);
            bytesRead = existing;
            persist(transfer(transferId, modelId, url, output.getAbsolutePath(), TaiModelStore.STATE_DOWNLOADING, bytesRead, contentLength, ""), callback);

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(partial, resumed)) {
                byte[] buffer = new byte[1024 * 64];
                int read;
                long lastPersisted = 0L;
                while ((read = input.read(buffer)) != -1) {
                    if (TaiModelDownloadService.isCancelled(modelId)) throw new InterruptedException("Download cancelled.");
                    outputStream.write(buffer, 0, read);
                    bytesRead += read;
                    if (bytesRead - lastPersisted >= 1024L * 1024L) {
                        persist(transfer(transferId, modelId, url, output.getAbsolutePath(), TaiModelStore.STATE_DOWNLOADING, bytesRead, contentLength, ""), callback);
                        lastPersisted = bytesRead;
                    }
                }
            }

            if (!expectedSha256.isEmpty() && !expectedSha256.equalsIgnoreCase(sha256(partial))) {
                throw new IllegalStateException("Downloaded model failed SHA-256 verification.");
            }
            persist(transfer(transferId, modelId, url, output.getAbsolutePath(), TaiModelStore.STATE_VERIFYING, bytesRead, contentLength, ""), callback);

            if (isMnnPackage(url, backend, format)) {
                if (!looksLikeSmallJson(partial, output.getName())) {
                    throw new IllegalStateException("Downloaded MNN config does not look like JSON. It may be an HTML login or error page.");
                }
                if (output.exists() && !output.delete()) throw new IllegalStateException("Could not replace model config.");
                if (!partial.renameTo(output)) throw new IllegalStateException("Could not finalize MNN config download.");

                File modelDir = output.getParentFile();
                String baseUrl = baseUrlFromUrl(url);
                LinkedHashSet<String> packageFiles = mnnPackageFilesFromHuggingFace(url, authToken);
                if (packageFiles.isEmpty()) {
                    for (String fileName : MNN_MODEL_FILES) packageFiles.add(fileName);
                }
                long currentBytes = output.length();
                bytesRead = currentBytes;
                long packageTotalBytes = expectedSizeBytes > 0L ? expectedSizeBytes : -1L;
                persist(transfer(transferId, modelId, url, output.getAbsolutePath(), TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), callback);

                for (String fileName : packageFiles) {
                    if (output.getName().equals(fileName)) continue;
                    String fileUrl = baseUrl + encodeHuggingFacePath(fileName);
                    File fileOutput = new File(modelDir, fileName);
                    File fileParent = fileOutput.getParentFile();
                    if (fileParent != null && !fileParent.exists() && !fileParent.mkdirs()) {
                        throw new IllegalStateException("Could not create MNN package directory.");
                    }
                    File filePartial = new File(fileOutput.getAbsolutePath() + ".part");
                    HttpURLConnection fileConn = open(fileUrl, authToken, 0);
                    int fileStatus = fileConn.getResponseCode();
                    if (fileStatus < 200 || fileStatus >= 300) {
                        if (isRequiredMnnPackageFile(fileName)) {
                            throw new IllegalStateException("MNN package file missing: " + fileName);
                        }
                        continue;
                    }
                    persist(withCurrentFile(transfer(transferId, modelId, url, output.getAbsolutePath(),
                        TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), fileName), callback);
                    try (InputStream fileInput = new BufferedInputStream(fileConn.getInputStream());
                         FileOutputStream fileOut = new FileOutputStream(filePartial)) {
                        byte[] buffer = new byte[1024 * 64];
                        int read;
                        while ((read = fileInput.read(buffer)) != -1) {
                            if (TaiModelDownloadService.isCancelled(modelId)) throw new InterruptedException("Download cancelled.");
                            fileOut.write(buffer, 0, read);
                            currentBytes += read;
                            bytesRead = currentBytes;
                            if (currentBytes % (1024L * 1024L) < read) {
                                persist(withCurrentFile(transfer(transferId, modelId, url, output.getAbsolutePath(),
                                    TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), fileName), callback);
                            }
                        }
                    }
                    if (fileOutput.exists() && !fileOutput.delete()) throw new IllegalStateException("Could not replace file.");
                    if (!filePartial.renameTo(fileOutput)) throw new IllegalStateException("Could not finalize file download.");
                    persist(withCurrentFile(transfer(transferId, modelId, url, output.getAbsolutePath(),
                        TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), fileName), callback);
                }

                LinkedHashSet<String> packageCapabilities =
                    TaiModelStore.mnnPackageCapabilities(output, capabilities);
                TaiModelSpec spec = new TaiModelSpec(
                    modelId,
                    displayName.isEmpty() ? modelId : displayName,
                    "Downloaded MNN model",
                    "downloaded",
                    output.getAbsolutePath(),
                    license.isEmpty() ? "User accepted provider terms externally" : license,
                    currentBytes,
                    packageCapabilities,
                    false,
                    null,
                    TaiModelSpec.BACKEND_MNN_LLM,
                    TaiModelSpec.FORMAT_MNN,
                    emptyToNull(architecture),
                    emptyToNull(quantization),
                    contextWindow,
                    recommendedRamGb,
                    emptyToNull(expectedSha256)
                );
                store.upsertUserModel(spec);
                persist(withEffectiveConfig(transfer(transferId, modelId, url, output.getAbsolutePath(),
                    TaiModelStore.STATE_INSTALLED, currentBytes, currentBytes, ""), output), callback);
                return;
            }

            if (!looksLikeModelFile(partial, output.getName())) {
                throw new IllegalStateException("Downloaded file does not look like a LiteRT-LM model. It may be an HTML login or error page.");
            }
            if (output.exists() && !output.delete()) throw new IllegalStateException("Could not replace model file.");
            if (!partial.renameTo(output)) throw new IllegalStateException("Could not finalize model download.");
            long installedBytes = output.length();
            if (requiresLiteRtEmbeddingTokenizer(output, capabilities)) {
                installedBytes += downloadLiteRtEmbeddingSidecars(transferId, modelId, url, output, authToken,
                    output.length(), expectedSizeBytes, callback);
            }

            TaiModelSpec spec = new TaiModelSpec(
                modelId,
                displayName.isEmpty() ? modelId : displayName,
                "Downloaded model",
                "downloaded",
                output.getAbsolutePath(),
                license.isEmpty() ? "User accepted provider terms externally" : license,
                installedBytes,
                capabilities,
                false,
                runtimeProfile,
                backend.isEmpty() ? TaiModelSpec.inferBackend(output.getAbsolutePath()) : backend,
                format.isEmpty() ? TaiModelSpec.inferFormat(output.getAbsolutePath()) : format,
                emptyToNull(architecture), emptyToNull(quantization), contextWindow,
                recommendedRamGb, emptyToNull(expectedSha256)
            );
            store.upsertUserModel(spec);
            persist(transfer(transferId, modelId, url, output.getAbsolutePath(), TaiModelStore.STATE_INSTALLED, installedBytes, installedBytes, ""), callback);
        } catch (InterruptedException e) {
            try {
                persist(transfer(transferId, modelId, url, output.getAbsolutePath(), TaiModelStore.STATE_CANCELLED, bytesRead, contentLength, "cancelled"), callback);
            } catch (JSONException ignored) {
            }
        } catch (Exception e) {
            try {
                persist(transfer(transferId, modelId, url, output.getAbsolutePath(), TaiModelStore.STATE_FAILED, bytesRead, contentLength, e.getMessage()), callback);
            } catch (JSONException ignored) {
            }
        }
    }

    private void persist(@NonNull JSONObject transfer, @Nullable ProgressCallback callback) {
        store.upsertDownload(preserveMetadata(transfer));
        if (callback != null) callback.onProgress(transfer);
    }

    @NonNull
    private JSONObject preserveMetadata(@NonNull JSONObject transfer) {
        String id = transfer.optString("id", "");
        JSONArray downloads = store.getDownloads();
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject existing = downloads.optJSONObject(i);
            if (existing == null || !id.equals(existing.optString("id", ""))) continue;
            copyIfMissing(transfer, existing, "displayName");
            copyIfMissing(transfer, existing, "license");
            copyIfMissing(transfer, existing, "capabilities");
            copyIfMissing(transfer, existing, "backend");
            copyIfMissing(transfer, existing, "format");
            copyIfMissing(transfer, existing, "architecture");
            copyIfMissing(transfer, existing, "quantization");
            copyIfMissing(transfer, existing, "contextWindow");
            copyIfMissing(transfer, existing, "recommendedRamGb");
            copyIfMissing(transfer, existing, "sha256");
            break;
        }
        return transfer;
    }

    private void copyIfMissing(@NonNull JSONObject target, @NonNull JSONObject source, @NonNull String key) {
        if (target.has(key) || !source.has(key)) return;
        try {
            target.put(key, source.opt(key));
        } catch (JSONException ignored) {
        }
    }

    @NonNull
    private JSONObject withMetadata(
        @NonNull JSONObject transfer,
        @NonNull String displayName,
        @NonNull String license,
        @NonNull LinkedHashSet<String> capabilities,
        @NonNull String backend,
        @NonNull String format,
        @Nullable String architecture,
        @Nullable String quantization,
        int contextWindow,
        int recommendedRamGb,
        @Nullable String sha256
    ) throws JSONException {
        transfer.put("displayName", displayName);
        transfer.put("license", license);
        JSONArray caps = new JSONArray();
        for (String capability : capabilities) caps.put(capability);
        transfer.put("capabilities", caps);
        transfer.put("backend", backend);
        transfer.put("format", format);
        transfer.put("architecture", architecture == null ? "" : architecture);
        transfer.put("quantization", quantization == null ? "" : quantization);
        transfer.put("contextWindow", contextWindow);
        transfer.put("recommendedRamGb", recommendedRamGb);
        transfer.put("sha256", sha256 == null ? "" : sha256);
        return transfer;
    }

    private boolean shouldAttachBearerToken(@NonNull String url, @Nullable String authToken) {
        return authToken != null
            && !authToken.trim().isEmpty()
            && (url.startsWith("https://huggingface.co/") || url.startsWith("https://www.huggingface.co/"));
    }

    @NonNull
    private JSONObject transfer(String id, String modelId, String url, String path, String status, long bytesRead, long totalBytes, String error) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("modelId", modelId);
        json.put("url", url);
        json.put("path", path);
        json.put("status", status);
        json.put("bytesRead", bytesRead);
        json.put("totalBytes", totalBytes);
        json.put("error", error == null ? "" : error);
        json.put("updatedAtMs", System.currentTimeMillis());
        return json;
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("error", code);
        json.put("message", message);
        json.put("_statusCode", statusCode);
        return json;
    }

    @NonNull
    private String sanitize(@NonNull String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    @NonNull
    private String fileNameFromUrl(@NonNull String url) {
        int slash = url.lastIndexOf('/');
        String name = slash >= 0 ? url.substring(slash + 1) : url;
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        name = sanitize(name);
        return name.isEmpty() ? "model.bin" : name;
    }

    private boolean looksLikeModelFile(@NonNull File file, @NonNull String originalName) {
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".litertlm") && !lowerName.endsWith(".task") && !lowerName.endsWith(".tflite")) {
            return false;
        }
        if (file.length() < 1024L * 1024L) {
            return false;
        }
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read <= 0) return false;
            String prefix = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase();
            return !(prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<head"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean looksLikeSmallJson(@NonNull File file, @NonNull String originalName) {
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".json") || file.length() <= 0L || file.length() > 10L * 1024L * 1024L) {
            return false;
        }
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read <= 0) return false;
            String prefix = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            return prefix.startsWith("{") && !(prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<head"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMnnPackage(@NonNull String url, @NonNull String backend, @NonNull String format) {
        boolean paramHint = TaiModelSpec.BACKEND_MNN_LLM.equals(backend) && TaiModelSpec.FORMAT_MNN.equals(format);
        boolean urlHint = url.toLowerCase(Locale.ROOT).contains("-mnn") || url.toLowerCase(Locale.ROOT).contains("taobao-mnn/");
        return paramHint || urlHint;
    }

    private boolean requiresLiteRtEmbeddingTokenizer(@NonNull File output, @NonNull LinkedHashSet<String> capabilities) {
        // A raw .tflite artifact is only ever a LiteRT embedding model in this app (chat packages are
        // .litertlm/.task containers), so it always needs its SentencePiece tokenizer sidecar — no matter
        // how capabilities were declared or whether the download URL carried a ?download= query.
        return output.getName().toLowerCase(Locale.ROOT).endsWith(".tflite");
    }

    private long downloadLiteRtEmbeddingSidecars(@NonNull String transferId, @NonNull String modelId,
                                                 @NonNull String url, @NonNull File output,
                                                 @Nullable String authToken, long currentBytes,
                                                 long expectedSizeBytes,
                                                 @Nullable ProgressCallback callback) throws Exception {
        String baseUrl = baseUrlFromUrl(url);
        File modelDir = output.getParentFile();
        if (modelDir == null) throw new IllegalStateException("Model directory is missing.");
        long packageTotalBytes = expectedSizeBytes > 0L ? expectedSizeBytes : -1L;

        File tokenizerOutput = new File(modelDir, LITERT_EMBEDDING_TOKENIZER);
        if (tokenizerOutput.isFile() && tokenizerOutput.length() > 0L) return 0L;
        File tokenizerPartial = new File(tokenizerOutput.getAbsolutePath() + ".part");

        for (String candidate : LITERT_EMBEDDING_TOKENIZER_CANDIDATES) {
            String fileUrl = baseUrl + encodeHuggingFacePath(candidate);
            HttpURLConnection fileConn = open(fileUrl, authToken, 0);
            int fileStatus = fileConn.getResponseCode();
            if (fileStatus < 200 || fileStatus >= 300) continue;
            persist(withCurrentFile(transfer(transferId, modelId, url, output.getAbsolutePath(),
                TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), candidate), callback);
            long fileBytes = 0L;
            try (InputStream fileInput = new BufferedInputStream(fileConn.getInputStream());
                 FileOutputStream fileOut = new FileOutputStream(tokenizerPartial)) {
                byte[] buffer = new byte[1024 * 64];
                int read;
                while ((read = fileInput.read(buffer)) != -1) {
                    if (TaiModelDownloadService.isCancelled(modelId)) throw new InterruptedException("Download cancelled.");
                    fileOut.write(buffer, 0, read);
                    currentBytes += read;
                    fileBytes += read;
                    if (currentBytes % (1024L * 1024L) < read) {
                        persist(withCurrentFile(transfer(transferId, modelId, url, output.getAbsolutePath(),
                            TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), candidate), callback);
                    }
                }
            }
            if (fileBytes <= 0L) {
                if (tokenizerPartial.exists() && !tokenizerPartial.delete()) { /* best effort */ }
                continue;
            }
            if (tokenizerOutput.exists() && !tokenizerOutput.delete()) throw new IllegalStateException("Could not replace tokenizer sidecar.");
            if (!tokenizerPartial.renameTo(tokenizerOutput)) throw new IllegalStateException("Could not finalize tokenizer sidecar download.");
            persist(withCurrentFile(transfer(transferId, modelId, url, output.getAbsolutePath(),
                TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), LITERT_EMBEDDING_TOKENIZER), callback);
            return fileBytes;
        }
        throw new IllegalStateException("LiteRT embedding model is missing a SentencePiece tokenizer "
            + "(looked for sentencepiece.model, tokenizer.model, spiece.model next to the .tflite).");
    }

    @NonNull
    private LinkedHashSet<String> mnnPackageFilesFromHuggingFace(@NonNull String url, @Nullable String authToken) {
        LinkedHashSet<String> files = new LinkedHashSet<>();
        String repoId = huggingFaceRepoIdFromResolveUrl(url);
        if (repoId.isEmpty()) return files;
        try {
            HttpURLConnection connection = open("https://huggingface.co/api/models/" + repoId, authToken, 0);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return files;
            String body = readSmallUtf8(connection.getInputStream(), 2L * 1024L * 1024L);
            JSONObject json = new JSONObject(body);
            JSONArray siblings = json.optJSONArray("siblings");
            if (siblings == null) return files;
            for (int i = 0; i < siblings.length(); i++) {
                JSONObject sibling = siblings.optJSONObject(i);
                if (sibling == null) continue;
                String fileName = sibling.optString("rfilename", "");
                if (isMnnPackageFile(fileName)) files.add(fileName);
            }
        } catch (Exception ignored) {
        }
        return files;
    }

    /** Outcome of resolving a Hugging Face URL: the concrete file URL, or a flag that the repo is
     *  gated/private and needs an access token. */
    public static final class HfResolve {
        @NonNull public final String url;
        public final boolean authRequired;
        HfResolve(@NonNull String url, boolean authRequired) { this.url = url; this.authRequired = authRequired; }
    }

    /**
     * Resolve a Hugging Face URL to a concrete downloadable file URL, auto-detecting the backend from
     * the repo's file list. A {@code .../resolve/...} URL is returned unchanged; a bare repo URL is
     * resolved to the package entry point — a {@code .litertlm}/{@code .task}/{@code .tflite}
     * (LiteRT) if present, else {@code config.json} of an MNN package — so users never pick a backend
     * or hunt the file list. Reports {@code authRequired} when the repo is gated/private (HTTP 401/403).
     */
    @NonNull
    public HfResolve resolveHuggingFaceEntry(@NonNull String url, @Nullable String authToken) {
        String trimmed = url.trim();
        if (trimmed.contains("/resolve/")) return new HfResolve(trimmed, false);
        String repoId = huggingFaceRepoIdFromRepoUrl(trimmed);
        if (repoId.isEmpty()) return new HfResolve("", false);
        try {
            HttpURLConnection connection = open("https://huggingface.co/api/models/" + repoId, authToken, 0);
            int code = connection.getResponseCode();
            if (code == 401 || code == 403) return new HfResolve("", true);
            if (code < 200 || code >= 300) return new HfResolve("", false);
            JSONArray siblings = new JSONObject(readSmallUtf8(connection.getInputStream(), 2L * 1024L * 1024L))
                .optJSONArray("siblings");
            LinkedHashSet<String> files = new LinkedHashSet<>();
            if (siblings != null) {
                for (int i = 0; i < siblings.length(); i++) {
                    JSONObject sibling = siblings.optJSONObject(i);
                    if (sibling != null) files.add(sibling.optString("rfilename", ""));
                }
            }
            String entry = chooseEntryFile(files);
            if (entry.isEmpty()) return new HfResolve("", false);
            String fileUrl = "https://huggingface.co/" + repoId + "/resolve/main/" + entry;
            // License-gated repos (e.g. Gemma) expose metadata publicly but 401/403 on the actual
            // file unless a token is set; a HEAD-style check surfaces that as "needs token" up front.
            if (requiresAuth(fileUrl, authToken)) return new HfResolve("", true);
            return new HfResolve(fileUrl, false);
        } catch (Exception ignored) {
            return new HfResolve("", false);
        }
    }

    /** True when the resolved file URL returns 401/403 (gated/private and the token is missing or
     *  unaccepted). Reads only the status line, not the body. */
    private boolean requiresAuth(@NonNull String fileUrl, @Nullable String authToken) {
        try {
            HttpURLConnection connection = open(fileUrl, authToken, 0);
            int code = connection.getResponseCode();
            connection.disconnect();
            return code == 401 || code == 403;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Repo id ("org/name") from a bare repo URL, ignoring any /tree, /blob, query or trailing slash. */
    @NonNull
    static String huggingFaceRepoIdFromRepoUrl(@NonNull String url) {
        String prefix = "https://huggingface.co/";
        if (!url.startsWith(prefix)) return "";
        String path = url.substring(prefix.length());
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        cut = path.indexOf('#');
        if (cut >= 0) path = path.substring(0, cut);
        String[] parts = path.split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return "";
        return parts[0] + "/" + parts[1];
    }

    /** Pick the package entry file from a repo's file list, auto-detecting the backend: a LiteRT
     *  package ({@code .litertlm}/{@code .task}/{@code .tflite}) wins; otherwise an MNN package
     *  ({@code config.json} alongside a {@code .mnn} weight). Returns "" when the repo holds no
     *  supported model. */
    @NonNull
    static String chooseEntryFile(@NonNull LinkedHashSet<String> files) {
        for (String f : files) if (f.toLowerCase(Locale.ROOT).endsWith(".litertlm")) return f;
        for (String f : files) if (f.toLowerCase(Locale.ROOT).endsWith(".task")) return f;
        String tflite = chooseLiteRtFlatbuffer(files);
        if (!tflite.isEmpty()) return tflite;
        if (files.contains("config.json")) {
            for (String f : files) if (f.toLowerCase(Locale.ROOT).endsWith(".mnn")) return "config.json";
        }
        return "";
    }

    @NonNull
    private static String chooseLiteRtFlatbuffer(@NonNull LinkedHashSet<String> files) {
        String first = "";
        String preferred = "";
        for (String file : files) {
            String lower = file.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".tflite")) continue;
            if (first.isEmpty()) first = file;
            boolean generic = !lower.contains(".qualcomm.")
                && !lower.contains(".mediatek.")
                && !lower.contains(".google.");
            if (generic && lower.contains("seq1024")) return file;
            if (generic && preferred.isEmpty()) preferred = file;
        }
        // ponytail: flatbuffer repos lack a wrapper; prefer generic builds, chipset-specific if that's all there is.
        return preferred.isEmpty() ? first : preferred;
    }

    @NonNull
    private String huggingFaceRepoIdFromResolveUrl(@NonNull String url) {
        String prefix = "https://huggingface.co/";
        if (!url.startsWith(prefix)) return "";
        String path = url.substring(prefix.length());
        int resolve = path.indexOf("/resolve/");
        if (resolve <= 0) return "";
        return path.substring(0, resolve);
    }

    private boolean isMnnPackageFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return !lower.startsWith(".")
            && (lower.endsWith(".mnn")
            || lower.endsWith(".mnn.weight")
            || lower.endsWith(".mnn.json")
            || lower.endsWith(".json")
            || lower.endsWith(".mtok")
            // MNN embedding packages ship a quantized weight table alongside the graph, e.g.
            // embeddings_int4.bin — pull raw .bin model data so embedding models import completely.
            || lower.endsWith(".bin")
            || lower.startsWith("tokenizer."));
    }

    private boolean isRequiredMnnPackageFile(@NonNull String fileName) {
        return "config.json".equals(fileName)
            || "llm.mnn".equals(fileName)
            || "llm.mnn.weight".equals(fileName);
    }

    @NonNull
    private String encodeHuggingFacePath(@NonNull String fileName) {
        StringBuilder builder = new StringBuilder();
        String[] parts = fileName.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append('/');
            try {
                builder.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                builder.append(parts[i]);
            }
        }
        return builder.toString();
    }

    @NonNull
    private JSONObject withCurrentFile(@NonNull JSONObject transfer, @NonNull String currentFile) throws JSONException {
        transfer.put("currentFile", currentFile);
        return transfer;
    }

    @NonNull
    private JSONObject withEffectiveConfig(@NonNull JSONObject transfer, @NonNull File config) throws JSONException {
        try {
            transfer.put("effectiveConfig", new JSONObject(readSmallUtf8(new java.io.FileInputStream(config), 10L * 1024L * 1024L)));
        } catch (Exception ignored) {
            transfer.put("effectiveConfig", JSONObject.NULL);
        }
        return transfer;
    }

    @NonNull
    private String readSmallUtf8(@NonNull InputStream input, long maxBytes) throws Exception {
        try (BufferedInputStream buffered = new BufferedInputStream(input)) {
            byte[] buffer = new byte[8192];
            StringBuilder builder = new StringBuilder();
            long total = 0L;
            int read;
            while ((read = buffered.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalStateException("Response too large.");
                builder.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
            }
            return builder.toString();
        }
    }

    @NonNull
    private String baseUrlFromUrl(@NonNull String url) {
        int query = url.indexOf('?');
        String base = query >= 0 ? url.substring(0, query) : url;
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash < 0) return base.endsWith("/") ? base : base + "/";
        return base.substring(0, lastSlash + 1);
    }

    private void putRuntimeMetadata(Intent intent, String backend, String format, @Nullable String architecture,
        @Nullable String quantization, int contextWindow, int recommendedRamGb, @Nullable String sha256) {
        intent.putExtra(TaiModelDownloadService.EXTRA_BACKEND, backend == null ? "" : backend);
        intent.putExtra(TaiModelDownloadService.EXTRA_FORMAT, format == null ? "" : format);
        intent.putExtra(TaiModelDownloadService.EXTRA_ARCHITECTURE, architecture == null ? "" : architecture);
        intent.putExtra(TaiModelDownloadService.EXTRA_QUANTIZATION, quantization == null ? "" : quantization);
        intent.putExtra(TaiModelDownloadService.EXTRA_CONTEXT_WINDOW, contextWindow);
        intent.putExtra(TaiModelDownloadService.EXTRA_RECOMMENDED_RAM_GB, recommendedRamGb);
        intent.putExtra(TaiModelDownloadService.EXTRA_SHA256, sha256 == null ? "" : sha256);
    }

    private void startService(Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) appContext.startForegroundService(intent);
        else appContext.startService(intent);
    }

    private JSONObject started(JSONObject transfer) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("started", true);
        data.put("transfer", transfer);
        data.put("message", "Download started in the Android app process. Check tai downloads for progress.");
        return data;
    }

    private static final int MAX_REDIRECTS = 5;

    /**
     * Follows redirects manually instead of via {@code setInstanceFollowRedirects(true)} so the
     * Hugging Face bearer token is re-evaluated per hop. Auto-follow reattaches every outgoing
     * header including Authorization, so a redirect to a non-Hugging-Face host (a CDN, a
     * compromised mirror) would otherwise leak the token off huggingface.co.
     */
    private HttpURLConnection open(String url, @Nullable String authToken, long offset) throws Exception {
        String currentUrl = url;
        for (int redirect = 0; redirect < MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(currentUrl).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(false);
            if (offset > 0L) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            if (shouldAttachBearerToken(currentUrl, authToken)) connection.setRequestProperty("Authorization", "Bearer " + authToken.trim());
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new java.io.IOException("Redirect from " + currentUrl + " carried no Location header");
                }
                currentUrl = new URL(new URL(currentUrl), location).toString();
                continue;
            }
            return connection;
        }
        throw new java.io.IOException("Too many redirects resolving " + url);
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[1024 * 128];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) builder.append(String.format(Locale.US, "%02x", value));
        return builder.toString();
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
