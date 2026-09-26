package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fetches one model (and its sidecars or package files) over HTTPS into the app-private models
 * directory, keeping a {@code .part} file so an interrupted transfer continues from where it
 * stopped. {@link #startDownload} only records the request and hands it to
 * {@link TaiDownloadEngine}; the engine's workers call {@link #runDownload} from the foreground
 * service's process and own the record while the transfer runs.
 */
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

    /** Progress writes to preferences happen this often; the callback fires more often than that. */
    private static final long PERSIST_EVERY_BYTES = 1024L * 1024L;
    /** The callback is not called more often than this while bytes stream; the hub throttles again. */
    private static final long CALLBACK_EVERY_MS = 100L;
    /** While hashing, the verified byte count goes out this often. */
    private static final long VERIFY_REPORT_EVERY_BYTES = 8L * 1024L * 1024L;
    /** A transfer that is paused by the network this many times in a row without moving a byte
     *  is not waiting for the network; it is failing, and auto-resume must stop retrying it. */
    static final int MAX_NETWORK_RETRIES_WITHOUT_PROGRESS = 3;

    /** Record fields that describe one attempt, not the download; a new status clears them. */
    private static final Set<String> VOLATILE_FIELDS = new HashSet<>(Arrays.asList(
        "pausedReason", "requiredBytes", "freeBytes", "currentFile", "verifiedBytes"));

    public interface ProgressCallback {
        void onProgress(@NonNull JSONObject transfer);
    }

    /**
     * The one handle a running transfer is steered by. The worker checks it between chunks; a
     * pause stops the transfer and keeps the partial file, a cancel stops it and deletes the
     * partial file. Once set, a request is not cleared: the worker acts on it exactly once.
     */
    public static final class Control {
        private volatile boolean cancel;
        @Nullable private volatile String pauseReason;

        public void requestCancel() {
            cancel = true;
        }

        /** @param reason one of the {@code TaiModelStore.PAUSED_*} values */
        public void requestPause(@NonNull String reason) {
            pauseReason = reason;
        }

        public boolean isCancelRequested() {
            return cancel;
        }

        public boolean isPauseRequested() {
            return pauseReason != null;
        }

        void checkpoint() throws Interrupted {
            if (cancel) throw new Interrupted(null);
            String reason = pauseReason;
            if (reason != null) throw new Interrupted(reason);
        }
    }

    /** Thrown out of the transfer loop when the control asks it to stop. */
    static final class Interrupted extends Exception {
        /** The pause reason, or null for a cancel. */
        @Nullable final String pauseReason;

        Interrupted(@Nullable String pauseReason) {
            super(pauseReason == null ? "Download cancelled." : "Download paused: " + pauseReason);
            this.pauseReason = pauseReason;
        }
    }

    private final Context appContext;
    private final TaiModelStore store;

    public TaiModelDownloader(@NonNull Context context, @NonNull TaiModelStore store) {
        appContext = context.getApplicationContext();
        this.store = store;
    }

    // ---- starting: build the record, hand it to the engine ----

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
        return startDownload(modelId, url, displayName, license, capabilities, authToken, runtimeProfile, null);
    }

    public JSONObject startDownload(String modelId, String url, String displayName, String license,
            LinkedHashSet<String> capabilities, String authToken, TaiModelProfile runtimeProfile,
            JSONObject artifact) throws JSONException {
        boolean packageDownload = TaiModelSpec.BACKEND_MNN_LLM.equals(TaiModelSpec.inferBackend(url));
        String artifactLicense = artifact == null ? "" : artifact.optString("license", "");
        return startDownload(modelId, url, displayName, artifactLicense.isEmpty() ? license : artifactLicense, capabilities,
            TaiModelSpec.inferBackend(url), TaiModelSpec.inferFormat(url), "", "", 4096, 0,
            artifact == null ? "" : artifact.optString("sha256", ""),
            artifact == null || packageDownload ? 0L : Math.max(0L, artifact.optLong("sizeBytes", 0)), authToken, runtimeProfile,
            Collections.<TaiModelCatalog.CatalogEntry.Sidecar>emptyList());
    }

    @NonNull
    public JSONObject startCatalogDownload(@NonNull TaiModelCatalog.CatalogEntry entry,
                                           @Nullable String authToken) throws JSONException {
        return startDownload(entry.modelId, entry.downloadUrl, entry.displayName, entry.license,
            entry.sourceCapabilities, entry.backend, entry.format, entry.architecture, entry.quantization,
            entry.endpointContextWindow, entry.recommendedRamGb, entry.sha256, entry.sizeBytes, authToken, null,
            entry.sidecars);
    }

    @NonNull
    private JSONObject startDownload(
        @NonNull String modelId, @NonNull String url, @NonNull String displayName,
        @NonNull String license, @NonNull LinkedHashSet<String> capabilities,
        @NonNull String backend, @NonNull String format, @Nullable String architecture,
        @Nullable String quantization, int contextWindow, int recommendedRamGb,
        @Nullable String expectedSha256, long expectedSizeBytes, @Nullable String authToken,
        @Nullable TaiModelProfile runtimeProfile,
        @NonNull List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars
    ) throws JSONException {
        String safeModelId = sanitize(modelId);
        if (safeModelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (!url.startsWith("https://")) return error(400, "insecure_url", "Model downloads require an https URL");

        File modelDir = new File(store.getModelsDirectory(), safeModelId);
        File output = new File(modelDir, fileNameFromUrl(url));
        String transferId = "download-" + safeModelId;
        // The record carries everything a worker needs to run or resume the transfer, so the
        // scheduler can restart it after a pause or a process death from preferences alone. The
        // token is the one thing left out: it lives in TaiSettings, never in the download list.
        JSONObject transfer = withMetadata(transfer(transferId, safeModelId, url, output.getAbsolutePath(),
            TaiModelStore.STATE_QUEUED, partialBytes(output), expectedSizeBytes, ""),
            displayName, license, capabilities, backend, format, architecture, quantization, contextWindow,
            recommendedRamGb, expectedSha256);
        transfer.put("expectedSizeBytes", expectedSizeBytes);
        if (runtimeProfile != null) transfer.put("runtimeProfile", runtimeProfile.toJson());
        if (!sidecars.isEmpty()) transfer.put("sidecars", sidecarsToJson(sidecars));
        JSONObject queued = TaiDownloadEngine.getInstance(appContext).enqueue(transfer, authToken);
        return started(queued);
    }

    /** What the partial file already holds, so a re-queued record shows its progress at once. */
    private long partialBytes(@NonNull File output) {
        File partial = new File(output.getAbsolutePath() + ".part");
        return partial.isFile() ? partial.length() : 0L;
    }

    // ---- running: called by the engine's workers ----

    /** Runs the transfer a persisted record describes (see {@link #startDownload}). */
    public void runDownload(@NonNull JSONObject record, @Nullable String authToken,
                            @NonNull Control control, @Nullable ProgressCallback callback) {
        TaiModelProfile runtimeProfile = null;
        JSONObject profileJson = record.optJSONObject("runtimeProfile");
        if (profileJson != null) {
            try {
                runtimeProfile = TaiModelProfile.fromJson(profileJson);
            } catch (Exception ignored) {
            }
        }
        JSONArray sidecarsJson = record.optJSONArray("sidecars");
        runDownload(
            record.optString("id", ""),
            record.optString("modelId", ""),
            record.optString("url", ""),
            new File(record.optString("path", "")),
            record.optString("displayName", ""),
            record.optString("license", ""),
            capabilitiesOf(record),
            record.optString("backend", ""),
            record.optString("format", ""),
            record.optString("architecture", ""),
            record.optString("quantization", ""),
            record.optInt("contextWindow", 4096),
            record.optInt("recommendedRamGb", 0),
            record.optString("sha256", ""),
            record.optLong("expectedSizeBytes", 0L),
            authToken,
            runtimeProfile,
            sidecarsFromJson(sidecarsJson == null ? null : sidecarsJson.toString()),
            control,
            callback);
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
            expectedSizeBytes, authToken, null, Collections.<TaiModelCatalog.CatalogEntry.Sidecar>emptyList(),
            new Control(), callback);
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
        runDownload(transferId, modelId, url, output, displayName, license, capabilities, backend,
            format, architecture, quantization, contextWindow, recommendedRamGb, expectedSha256,
            expectedSizeBytes, authToken, runtimeProfile, Collections.<TaiModelCatalog.CatalogEntry.Sidecar>emptyList(),
            new Control(), callback);
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
        @NonNull List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars,
        @Nullable ProgressCallback callback
    ) {
        runDownload(transferId, modelId, url, output, displayName, license, capabilities, backend,
            format, architecture, quantization, contextWindow, recommendedRamGb, expectedSha256,
            expectedSizeBytes, authToken, runtimeProfile, sidecars, new Control(), callback);
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
        @NonNull List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars,
        @NonNull Control control,
        @Nullable ProgressCallback callback
    ) {
        Run run;
        try {
            run = new Run(transferId, modelId, url, output, displayName, license, capabilities, backend,
                format, architecture, quantization, contextWindow, recommendedRamGb, expectedSha256,
                expectedSizeBytes, sidecars, control, callback);
        } catch (JSONException e) {
            return;
        }
        long bytesRead = 0L;
        long startBytes = 0L;
        long contentLength = 0L;
        try {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Failed to create model directory");
            }

            File partial = new File(output.getAbsolutePath() + ".part");
            long existing = resumeOffset(partial, url);
            HttpURLConnection connection = open(url, authToken, existing);
            int status = connection.getResponseCode();
            if (status == 416 && existing > 0) {
                connection.disconnect();
                connection = open(url, authToken, 0);
                status = connection.getResponseCode();
                existing = 0;
            }
            if (status == 206 && !validContentRange(connection.getHeaderField("Content-Range"), existing)) {
                connection.disconnect();
                throw new IllegalStateException("Invalid partial response");
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Download failed with HTTP " + status);
            }
            boolean resumed = existing > 0L && status == 206;
            if (!resumed) existing = 0L;
            long responseLength = connection.getHeaderFieldLong("Content-Length", -1L);
            // What this response promised, for the short-read check below; contentLength (shown
            // to the user) may be raised to the catalogue's size when the server says less.
            long promised = responseLength > 0 ? existing + responseLength : -1L;
            contentLength = promised;
            if (expectedSizeBytes > 0L) contentLength = Math.max(contentLength, expectedSizeBytes);
            bytesRead = existing;
            startBytes = existing;
            run.persist(run.record(TaiModelStore.STATE_DOWNLOADING, bytesRead, contentLength, ""));

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(partial, resumed)) {
                byte[] buffer = new byte[1024 * 64];
                int read;
                long lastPersisted = bytesRead;
                while ((read = input.read(buffer)) != -1) {
                    control.checkpoint();
                    outputStream.write(buffer, 0, read);
                    bytesRead += read;
                    if (bytesRead - lastPersisted >= PERSIST_EVERY_BYTES) {
                        run.persist(run.record(TaiModelStore.STATE_DOWNLOADING, bytesRead, contentLength, ""));
                        lastPersisted = bytesRead;
                    } else {
                        run.report(run.record(TaiModelStore.STATE_DOWNLOADING, bytesRead, contentLength, ""));
                    }
                }
            }
            // A connection that ends before the promised length is the network going away, not a
            // finished file; some HTTP stacks report it as a plain end of stream, so say so here.
            if (promised > 0L && bytesRead < promised) {
                throw new IOException("The connection closed after " + bytesRead + " of " + promised + " bytes");
            }

            // "Checking file" is shown while the hash actually runs, so the wait after the last
            // byte is explained; the verified byte count moves the bar meanwhile.
            run.persist(run.record(TaiModelStore.STATE_VERIFYING, bytesRead, contentLength, ""));
            if (!expectedSha256.isEmpty()) {
                final long total = contentLength;
                final long done = bytesRead;
                String actual = sha256(partial, control, verified -> {
                    try {
                        run.report(withVerified(run.record(TaiModelStore.STATE_VERIFYING, done, total, ""), verified));
                    } catch (JSONException ignored) {
                    }
                });
                if (!expectedSha256.equalsIgnoreCase(actual)) {
                    throw new IllegalStateException("Downloaded model failed SHA-256 verification.");
                }
            }

            if (isMnnPackage(url, backend, format)) {
                if (!looksLikeSmallJson(partial, output.getName())) {
                    throw new IllegalStateException("Downloaded MNN config does not look like JSON. It may be an HTML login or error page.");
                }
                if (output.exists() && !output.delete()) throw new IllegalStateException("Could not replace model config.");
                if (!partial.renameTo(output)) throw new IllegalStateException("Could not finalize MNN config download.");
                clearResumeMarker(partial);

                File modelDir = output.getParentFile();
                String baseUrl = baseUrlFromUrl(url);
                LinkedHashSet<String> packageFiles = mnnPackageFilesFromHuggingFace(url, authToken);
                if (packageFiles.isEmpty()) {
                    for (String fileName : MNN_MODEL_FILES) packageFiles.add(fileName);
                }
                packageFiles = TaiMnnPackage.files(TaiMnnPackage.readConfig(output), packageFiles);
                long currentBytes = output.length();
                bytesRead = currentBytes;
                long packageTotalBytes = expectedSizeBytes > 0L ? expectedSizeBytes : -1L;
                run.persist(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""));

                java.util.ArrayList<String> pendingFiles = new java.util.ArrayList<>(packageFiles);
                for (int packageIndex = 0; packageIndex < pendingFiles.size(); packageIndex++) {
                    if (pendingFiles.size() > 10000) throw new IllegalStateException("Model package has too many files.");
                    String fileName = pendingFiles.get(packageIndex);
                    if (output.getName().equals(fileName)) continue;
                    String fileUrl = baseUrl + encodeHuggingFacePath(fileName);
                    File fileOutput = new File(modelDir, fileName);
                    File fileParent = fileOutput.getParentFile();
                    if (fileParent != null && !fileParent.exists() && !fileParent.mkdirs()) {
                        throw new IllegalStateException("Could not create MNN package directory.");
                    }
                    File filePartial = new File(fileOutput.getAbsolutePath() + ".part");
                    long offset = resumeOffset(filePartial, fileUrl);
                    HttpURLConnection fileConn = open(fileUrl, authToken, offset);
                    int fileStatus = fileConn.getResponseCode();
                    if (fileStatus == 416 && offset > 0) {
                        fileConn.disconnect();
                        fileConn = open(fileUrl, authToken, 0);
                        fileStatus = fileConn.getResponseCode();
                        offset = 0;
                    }
                    boolean resume = offset > 0 && fileStatus == 206
                        && validContentRange(fileConn.getHeaderField("Content-Range"), offset);
                    if (fileStatus == 206 && !resume) {
                        fileConn.disconnect();
                        throw new IllegalStateException("Invalid partial response for " + fileName);
                    }
                    if (!resume) offset = 0;
                    currentBytes += offset;
                    if (fileStatus < 200 || fileStatus >= 300) {
                        fileConn.disconnect();
                        throw new IllegalStateException("MNN package file missing: " + fileName);
                    }
                    run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), fileName));
                    try (InputStream fileInput = new BufferedInputStream(fileConn.getInputStream());
                         FileOutputStream fileOut = new FileOutputStream(filePartial, resume)) {
                        byte[] buffer = new byte[1024 * 64];
                        int read;
                        while ((read = fileInput.read(buffer)) != -1) {
                            control.checkpoint();
                            fileOut.write(buffer, 0, read);
                            currentBytes += read;
                            bytesRead = currentBytes;
                            if (currentBytes % PERSIST_EVERY_BYTES < read) {
                                run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), fileName));
                            } else {
                                run.report(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), fileName));
                            }
                        }
                    }
                    fileConn.disconnect();
                    if (fileOutput.exists() && !fileOutput.delete()) throw new IllegalStateException("Could not replace file.");
                    if (!filePartial.renameTo(fileOutput)) throw new IllegalStateException("Could not finalize file download.");
                    clearResumeMarker(filePartial);
                    if (fileName.endsWith(".json")) {
                        TaiMnnPackage.references(TaiMnnPackage.readConfig(fileOutput), packageFiles);
                        for (String dependency : packageFiles)
                            if (!pendingFiles.contains(dependency)) pendingFiles.add(dependency);
                    }
                    run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), fileName));
                }

                run.persist(run.record(TaiModelStore.STATE_VERIFYING, currentBytes, packageTotalBytes, ""));
                TaiMnnPackage.validate(output);
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
                run.persist(withEffectiveConfig(run.record(TaiModelStore.STATE_INSTALLED, currentBytes, currentBytes, ""), output));
                return;
            }

            if (!looksLikeModelFile(partial, output.getName())) {
                throw new IllegalStateException("Downloaded file does not look like a LiteRT-LM model. It may be an HTML login or error page.");
            }
            if (output.exists() && !output.delete()) throw new IllegalStateException("Could not replace model file.");
            if (!partial.renameTo(output)) throw new IllegalStateException("Could not finalize model download.");
            clearResumeMarker(partial);
            long installedBytes = output.length();
            if (requiresLiteRtEmbeddingTokenizer(output, capabilities)) {
                installedBytes += downloadLiteRtEmbeddingSidecars(run, url, output, authToken,
                    output.length(), expectedSizeBytes);
            }
            if (!sidecars.isEmpty()) {
                installedBytes += downloadCatalogSidecars(run, output, sidecars, authToken,
                    installedBytes, expectedSizeBytes);
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
            run.persist(run.record(TaiModelStore.STATE_INSTALLED, installedBytes, installedBytes, ""));
        } catch (Interrupted e) {
            try {
                if (e.pauseReason == null) {
                    // Cancel means "I do not want this": the partial files go, so a later download
                    // of the same model starts clean and the disk is not left holding gigabytes.
                    deletePartials(output);
                    run.persist(run.record(TaiModelStore.STATE_CANCELLED, bytesRead, contentLength, "cancelled"));
                } else {
                    run.persist(withPause(run.record(TaiModelStore.STATE_PAUSED, bytesRead, contentLength, ""), e.pauseReason, 0L, 0L));
                }
            } catch (JSONException ignored) {
            }
        } catch (IOException e) {
            // The socket died under the transfer, or the disk filled. Both keep the partial file
            // and pause rather than fail: the bytes on disk are good, and the reason is one the
            // engine can wait out (the network) or the user can fix (the space).
            try {
                if (isDiskFull(e)) {
                    File dir = output.getParentFile() == null ? store.getModelsDirectory() : output.getParentFile();
                    TaiDownloadQueue.SpaceCheck space = TaiDownloadQueue.checkSpace(dir.getUsableSpace(),
                        dir.getTotalSpace(), contentLength, bytesRead);
                    run.persist(withPause(run.record(TaiModelStore.STATE_PAUSED, bytesRead, contentLength, ""),
                        TaiModelStore.PAUSED_NO_SPACE, space.requiredBytes, space.freeBytes));
                } else if (e instanceof FileNotFoundException) {
                    run.persist(run.record(TaiModelStore.STATE_FAILED, bytesRead, contentLength, e.getMessage()));
                } else {
                    JSONObject paused = withPause(run.record(TaiModelStore.STATE_PAUSED, bytesRead, contentLength,
                        e.getMessage() == null ? "" : e.getMessage()), TaiModelStore.PAUSED_NETWORK, 0L, 0L);
                    int retries = bytesRead > startBytes ? 0 : run.base.optInt("networkRetries", 0) + 1;
                    paused.put("networkRetries", retries);
                    run.persist(paused);
                }
            } catch (JSONException ignored) {
            }
        } catch (Exception e) {
            try {
                run.persist(run.record(TaiModelStore.STATE_FAILED, bytesRead, contentLength, e.getMessage()));
            } catch (JSONException ignored) {
            }
        }
    }

    /** One attempt's bookkeeping: the base record every progress write is derived from. */
    private final class Run {
        final JSONObject base;
        final Control control;
        @Nullable final ProgressCallback callback;
        long lastCallbackMs;

        Run(String transferId, String modelId, String url, File output, String displayName, String license,
            LinkedHashSet<String> capabilities, String backend, String format, String architecture,
            String quantization, int contextWindow, int recommendedRamGb, String expectedSha256,
            long expectedSizeBytes, List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars,
            Control control, @Nullable ProgressCallback callback) throws JSONException {
            this.control = control;
            this.callback = callback;
            base = withMetadata(transfer(transferId, modelId, url, output.getAbsolutePath(),
                TaiModelStore.STATE_QUEUED, 0L, expectedSizeBytes, ""),
                displayName, license, capabilities, backend, format, architecture, quantization,
                contextWindow, recommendedRamGb, expectedSha256);
            base.put("expectedSizeBytes", expectedSizeBytes);
            if (!sidecars.isEmpty()) base.put("sidecars", sidecarsToJson(sidecars));
            // Whatever the stored record knows beyond the arguments (queue position, the runtime
            // profile, retry counts) rides along, so a progress write never loses it.
            JSONObject stored = store.getDownload(transferId);
            if (stored != null) {
                Iterator<String> keys = stored.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!base.has(key) && !VOLATILE_FIELDS.contains(key)) base.put(key, stored.opt(key));
                }
            }
        }

        /** A fresh record for this attempt with the given state; attempt-specific fields cleared. */
        @NonNull
        JSONObject record(String status, long bytesRead, long totalBytes, String error) throws JSONException {
            JSONObject json = new JSONObject();
            Iterator<String> keys = base.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!VOLATILE_FIELDS.contains(key)) json.put(key, base.opt(key));
            }
            json.put("status", status);
            json.put("bytesRead", bytesRead);
            json.put("totalBytes", totalBytes);
            json.put("error", error == null ? "" : error);
            json.put("updatedAtMs", System.currentTimeMillis());
            return json;
        }

        @NonNull
        Control control() {
            return control;
        }

        /** Writes the record to preferences and tells the callback. */
        void persist(@NonNull JSONObject transfer) {
            store.upsertDownload(transfer);
            lastCallbackMs = android.os.SystemClock.elapsedRealtime();
            if (callback != null) callback.onProgress(transfer);
        }

        /** Tells the callback only, at most every {@link #CALLBACK_EVERY_MS}; preferences are
         *  written by {@link #persist} on the coarser byte schedule. */
        void report(@NonNull JSONObject transfer) {
            if (callback == null) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastCallbackMs < CALLBACK_EVERY_MS) return;
            lastCallbackMs = now;
            callback.onProgress(transfer);
        }
    }

    // ---- record helpers ----

    @NonNull
    static LinkedHashSet<String> capabilitiesOf(@NonNull JSONObject record) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        JSONArray array = record.optJSONArray("capabilities");
        if (array != null) for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) capabilities.add(value);
        }
        return capabilities;
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
    static JSONObject withPause(@NonNull JSONObject transfer, @NonNull String reason, long requiredBytes, long freeBytes) throws JSONException {
        transfer.put("pausedReason", reason);
        if (TaiModelStore.PAUSED_NO_SPACE.equals(reason)) {
            transfer.put("requiredBytes", requiredBytes);
            transfer.put("freeBytes", freeBytes);
        }
        return transfer;
    }

    @NonNull
    private JSONObject withVerified(@NonNull JSONObject transfer, long verifiedBytes) {
        try {
            transfer.put("verifiedBytes", verifiedBytes);
        } catch (JSONException ignored) {
        }
        return transfer;
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
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read <= 0) return false;
            String prefix = new String(buffer, 0, read, StandardCharsets.UTF_8).trim().toLowerCase();
            return !(prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<head"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean looksLikeSmallJson(@NonNull File file, @NonNull String originalName) {
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".json") || file.length() <= 0L || file.length() > 10L * 1024L * 1024L) {
            return false;
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read <= 0) return false;
            String prefix = new String(buffer, 0, read, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
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
        // A raw .tflite artifact in this app is either a LiteRT embedding model or a Whisper ACFT
        // speech-to-text graph — branch on the declared capability, not the extension. Every .tflite
        // used to take this path, which made a Whisper download fail hunting for a nonexistent
        // sentencepiece.model; Whisper's tokenizer.json travels as an explicit catalog sidecar instead
        // (see downloadCatalogSidecars).
        return output.getName().toLowerCase(Locale.ROOT).endsWith(".tflite")
            && capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
    }

    /** ENOSPC surfaces as an IOException whose message names it; there is no typed signal. */
    static boolean isDiskFull(@NonNull IOException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return message.contains("ENOSPC") || message.toLowerCase(Locale.ROOT).contains("no space left");
    }

    /** Every partial file and resume marker under the model's directory; the finished files stay. */
    static void deletePartials(@NonNull File output) {
        new File(output.getAbsolutePath() + ".part").delete();
        new File(output.getAbsolutePath() + ".part.source").delete();
        File dir = output.getParentFile();
        if (dir == null) return;
        deletePartialsUnder(dir, 0);
    }

    private static void deletePartialsUnder(@NonNull File dir, int depth) {
        File[] children = dir.listFiles();
        if (children == null || depth > 8) return;
        for (File child : children) {
            if (child.isDirectory()) {
                deletePartialsUnder(child, depth + 1);
            } else if (child.getName().endsWith(".part") || child.getName().endsWith(".part.source")) {
                child.delete();
            }
        }
    }

    /**
     * Downloads a catalog entry's declared sidecars (e.g. Whisper's {@code tokenizer.json} from the
     * paired {@code openai/whisper-*} repo) next to the main artifact, reusing the same .part/resume/
     * Range/hash helpers as the main download. A sidecar already present with a matching hash (or no
     * expected hash) is skipped; a sidecar that never returns a 2xx response fails the whole download
     * cleanly rather than leaving a model installed with a missing tokenizer.
     */
    private long downloadCatalogSidecars(@NonNull Run run, @NonNull File output,
                                         @NonNull List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars,
                                         @Nullable String authToken, long currentBytes, long expectedSizeBytes) throws Exception {
        File modelDir = output.getParentFile();
        if (modelDir == null) throw new IllegalStateException("Model directory is missing.");
        long packageTotalBytes = expectedSizeBytes > 0L ? expectedSizeBytes : -1L;
        long addedBytes = 0L;

        for (TaiModelCatalog.CatalogEntry.Sidecar sidecar : sidecars) {
            File sidecarOutput = new File(modelDir, sidecar.localName);
            if (sidecarOutput.isFile() && sidecarOutput.length() > 0L
                && (sidecar.sha256 == null || sidecar.sha256.equalsIgnoreCase(sha256(sidecarOutput, null, null)))) {
                continue;
            }
            File sidecarPartial = new File(sidecarOutput.getAbsolutePath() + ".part");
            long offset = resumeOffset(sidecarPartial, sidecar.url);
            HttpURLConnection connection = open(sidecar.url, authToken, offset);
            int status = connection.getResponseCode();
            if (status == 416 && offset > 0) {
                connection.disconnect();
                connection = open(sidecar.url, authToken, 0);
                status = connection.getResponseCode();
                offset = 0;
            }
            boolean resume = offset > 0 && status == 206 && validContentRange(connection.getHeaderField("Content-Range"), offset);
            if (status == 206 && !resume) {
                connection.disconnect();
                throw new IllegalStateException("Invalid partial response for sidecar " + sidecar.localName);
            }
            if (!resume) offset = 0;
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IllegalStateException("Missing required sidecar: " + sidecar.localName);
            }
            currentBytes += offset;
            addedBytes += offset;
            run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), sidecar.localName));
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(sidecarPartial, resume)) {
                byte[] buffer = new byte[1024 * 64];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    run.control().checkpoint();
                    out.write(buffer, 0, read);
                    currentBytes += read;
                    addedBytes += read;
                    if (currentBytes % PERSIST_EVERY_BYTES < read) {
                        run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), sidecar.localName));
                    } else {
                        run.report(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), sidecar.localName));
                    }
                }
            }
            connection.disconnect();
            if (sidecar.sha256 != null && !sidecar.sha256.equalsIgnoreCase(sha256(sidecarPartial, null, null))) {
                throw new IllegalStateException("Sidecar failed SHA-256 verification: " + sidecar.localName);
            }
            if (sidecarOutput.exists() && !sidecarOutput.delete()) throw new IllegalStateException("Could not replace sidecar " + sidecar.localName);
            if (!sidecarPartial.renameTo(sidecarOutput)) throw new IllegalStateException("Could not finalize sidecar download: " + sidecar.localName);
            clearResumeMarker(sidecarPartial);
        }
        return addedBytes;
    }

    /** Serializes catalog sidecars into the download record so a worker (now, or after a restart)
     *  can re-download them via {@link #runDownload(JSONObject, String, Control, ProgressCallback)}.
     *  Kept as a plain JSON array of {@code {url, localName, sha256}}. */
    @NonNull
    public static JSONArray sidecarsToJson(@NonNull List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars) {
        JSONArray array = new JSONArray();
        for (TaiModelCatalog.CatalogEntry.Sidecar sidecar : sidecars) {
            try {
                JSONObject json = new JSONObject();
                json.put("url", sidecar.url);
                json.put("localName", sidecar.localName);
                json.put("sha256", sidecar.sha256 == null ? JSONObject.NULL : sidecar.sha256);
                array.put(json);
            } catch (JSONException ignored) {
            }
        }
        return array;
    }

    @NonNull
    public static List<TaiModelCatalog.CatalogEntry.Sidecar> sidecarsFromJson(@Nullable String json) {
        List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return sidecars;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                String localName = item.optString("localName", "");
                if (url.isEmpty() || localName.isEmpty()) continue;
                String sha256 = item.isNull("sha256") ? null : item.optString("sha256", null);
                sidecars.add(new TaiModelCatalog.CatalogEntry.Sidecar(url, localName, sha256));
            }
        } catch (JSONException ignored) {
        }
        return sidecars;
    }

    private long downloadLiteRtEmbeddingSidecars(@NonNull Run run, @NonNull String url, @NonNull File output,
                                                 @Nullable String authToken, long currentBytes,
                                                 long expectedSizeBytes) throws Exception {
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
            run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), candidate));
            long fileBytes = 0L;
            try (InputStream fileInput = new BufferedInputStream(fileConn.getInputStream());
                 FileOutputStream fileOut = new FileOutputStream(tokenizerPartial)) {
                byte[] buffer = new byte[1024 * 64];
                int read;
                while ((read = fileInput.read(buffer)) != -1) {
                    run.control().checkpoint();
                    fileOut.write(buffer, 0, read);
                    currentBytes += read;
                    fileBytes += read;
                    if (currentBytes % PERSIST_EVERY_BYTES < read) {
                        run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), candidate));
                    }
                }
            }
            if (fileBytes <= 0L) {
                if (tokenizerPartial.exists() && !tokenizerPartial.delete()) { /* best effort */ }
                continue;
            }
            if (tokenizerOutput.exists() && !tokenizerOutput.delete()) throw new IllegalStateException("Could not replace tokenizer sidecar.");
            if (!tokenizerPartial.renameTo(tokenizerOutput)) throw new IllegalStateException("Could not finalize tokenizer sidecar download.");
            run.persist(withCurrentFile(run.record(TaiModelStore.STATE_DOWNLOADING, currentBytes, packageTotalBytes, ""), LITERT_EMBEDDING_TOKENIZER));
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
            HttpURLConnection connection = open(TaiHuggingFace.parse(url).metadataUrl(), authToken, 0);
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
                TaiHuggingFace source = TaiHuggingFace.parse(url);
                String directory = source.path.substring(0, source.path.lastIndexOf('/') + 1);
                if (fileName.startsWith(directory)) {
                    String relative = fileName.substring(directory.length());
                    if (TaiHuggingFace.safePath(relative) && isMnnPackageFile(relative)) files.add(relative);
                }
            }
        } catch (Exception ignored) {
        }
        return files;
    }

    public static final class HfResolve {
        public final String url;
        public final boolean authRequired;
        public final JSONArray candidates;
        HfResolve(String url, boolean authRequired, JSONArray candidates) {
            this.url = url; this.authRequired = authRequired; this.candidates = candidates;
        }
    }

    /** Never chooses silently between different exports; every candidate uses the resolved commit. */
    public HfResolve resolveHuggingFaceEntry(String url, String authToken) {
        TaiHuggingFace source = TaiHuggingFace.parse(url);
        if (source == null) return new HfResolve("", false, new JSONArray());
        HttpURLConnection connection = null;
        try {
            connection = open(source.metadataUrl(), authToken, 0);
            int code = connection.getResponseCode();
            if (code == 401 || code == 403) return new HfResolve("", true, new JSONArray());
            if (code < 200 || code >= 300) return new HfResolve("", false, new JSONArray());
            JSONObject metadata;
            try (InputStream input = connection.getInputStream()) {
                metadata = new JSONObject(readSmallUtf8(input, 2L * 1024L * 1024L));
            }
            JSONArray candidates = source.candidates(metadata);
            String selected = candidates.length() == 1 ? candidates.getJSONObject(0).getString("url") : "";
            if (!selected.isEmpty() && requiresAuth(selected, authToken))
                return new HfResolve("", true, candidates);
            return new HfResolve(selected, false, candidates);
        } catch (Exception ignored) {
            return new HfResolve("", false, new JSONArray());
        } finally {
            if (connection != null) connection.disconnect();
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

    static boolean validContentRange(String header, long offset) {
        return header != null && header.startsWith("bytes " + offset + "-");
    }

    /** The finished file no longer needs the marker that told resume which URL the bytes came from. */
    private void clearResumeMarker(File partial) {
        new File(partial.getAbsolutePath() + ".source").delete();
    }

    private long resumeOffset(File partial, String url) throws IOException {
        File source = new File(partial.getAbsolutePath() + ".source");
        String previous = source.isFile() ? new String(java.nio.file.Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8) : "";
        long offset = url.equals(previous) && partial.isFile() ? partial.length() : 0L;
        if (offset == 0 && partial.isFile()) {
            try (FileOutputStream truncate = new FileOutputStream(partial, false)) { }
        }
        java.nio.file.Files.write(source.toPath(), url.getBytes(StandardCharsets.UTF_8));
        return offset;
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
            transfer.put("effectiveConfig", new JSONObject(readSmallUtf8(new FileInputStream(config), 10L * 1024L * 1024L)));
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
                builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
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

    private JSONObject started(JSONObject transfer) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("started", true);
        data.put("transfer", transfer);
        data.put("message", "Download queued in the Android app process. Check tai downloads for progress.");
        return data;
    }

    private static final int MAX_REDIRECTS = 5;

    /**
     * Follows redirects manually instead of via {@code setInstanceFollowRedirects(true)} so the
     * Hugging Face bearer token is re-evaluated per hop. Auto-follow reattaches every outgoing
     * header including Authorization, so a redirect to a non-Hugging-Face host (a CDN, a
     * compromised mirror) would otherwise leak the token off huggingface.co.
     *
     * <p>Protocol faults here are {@link IllegalStateException}s on purpose: the transfer loop
     * reads an {@link IOException} as "the network went away" and pauses, and a broken redirect is
     * a failure, not a wait.
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
                    throw new IllegalStateException("Redirect from " + currentUrl + " carried no Location header");
                }
                currentUrl = new URL(new URL(currentUrl), location).toString();
                continue;
            }
            return connection;
        }
        throw new IllegalStateException("Too many redirects resolving " + url);
    }

    /** Called with the bytes hashed so far, every {@link #VERIFY_REPORT_EVERY_BYTES}. */
    interface HashProgress {
        void onHashed(long bytes);
    }

    private String sha256(File file, @Nullable Control control, @Nullable HashProgress progress) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[1024 * 128];
            int read;
            long hashed = 0L;
            long lastReported = 0L;
            while ((read = input.read(buffer)) != -1) {
                if (control != null) control.checkpoint();
                digest.update(buffer, 0, read);
                hashed += read;
                if (progress != null && hashed - lastReported >= VERIFY_REPORT_EVERY_BYTES) {
                    progress.onHashed(hashed);
                    lastReported = hashed;
                }
            }
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
