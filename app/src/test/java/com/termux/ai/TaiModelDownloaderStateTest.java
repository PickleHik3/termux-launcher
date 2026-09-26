package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TaiModelDownloaderStateTest {
    private Context context;
    private TaiModelStore store;
    private HttpServer server;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        store = new TaiModelStore(context);
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
        store.deleteUserModel("state-test");
        store.deleteUserModel("cancel-test");
        store.deleteUserModel("pause-test");
        store.deleteUserModel("verify-test");
        store.deleteUserModel("verify-ok-test");
        store.deleteUserModel("network-test");
        store.deleteUserModel("retry-test");
        store.deleteUserModel("metadata-test");
        store.deleteUserModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        store.deleteUserModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
        store.deleteUserModel("Qwen2.5-Coder-1.5B-GGUF");
        store.deleteUserModel("resolvable-test");
    }

    @Test
    public void startDownload_rejectsHttpUrls() throws Exception {
        TaiModelDownloader downloader = new TaiModelDownloader(context, store);

        JSONObject result = downloader.startDownload("http-test", "http://example.com/model.litertlm",
            "HTTP Test", "license", capabilities(), null);

        assertFalse(result.getBoolean("ok"));
        assertEquals("insecure_url", result.getString("error"));
    }

    @Test
    public void mnnConfigDeclaresVisionAndEagleCapabilities() throws Exception {
        File directory = new File(context.getCacheDir(), "mnn-capabilities-test");
        assertTrue(directory.mkdirs() || directory.isDirectory());
        File config = new File(directory, "config.json");
        java.nio.file.Files.write(config.toPath(), new JSONObject()
            .put("mllm", new JSONObject().put("vision_model", "visual.mnn"))
            .put("speculative_type", "eagle")
            .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        LinkedHashSet<String> capabilities = TaiModelStore.mnnPackageCapabilities(config,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT));

        assertTrue(capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertTrue(capabilities.contains(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING));
        config.delete();
        directory.delete();
    }

    @Test
    public void runDownload_transitionsDownloadingVerifyingInstalled() throws Exception {
        byte[] model = modelBytes('a');
        String url = serve(new FixedBytesHandler(model));
        List<String> states = new ArrayList<>();
        File output = output("state-test", "model.litertlm");

        run("state-test", url, output, states);

        assertTrue(states.contains(TaiModelStore.STATE_DOWNLOADING));
        assertTrue(states.contains(TaiModelStore.STATE_VERIFYING));
        assertEquals(TaiModelStore.STATE_INSTALLED, states.get(states.size() - 1));
        assertTrue(output.isFile());
        assertFalse(new File(output.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void runDownload_cancelledDeletesThePartialFile() throws Exception {
        // Cancel means "I do not want this": the partial file and its resume marker go, so the
        // disk is not left holding a gigabyte nobody asked to keep.
        byte[] model = modelBytes('b');
        String url = serve(new FixedBytesHandler(model));
        List<String> states = new ArrayList<>();
        File output = output("cancel-test", "model.litertlm");
        TaiModelDownloader.Control control = new TaiModelDownloader.Control();

        run("cancel-test", url, output, states, control, transfer -> {
            if (TaiModelStore.STATE_DOWNLOADING.equals(transfer.optString("status"))) control.requestCancel();
        });

        assertEquals(TaiModelStore.STATE_CANCELLED, states.get(states.size() - 1));
        assertFalse(output.exists());
        assertFalse(new File(output.getAbsolutePath() + ".part").exists());
        assertFalse(new File(output.getAbsolutePath() + ".part.source").exists());
    }

    @Test
    public void runDownload_pausedKeepsThePartAndResumesFromTheOffset() throws Exception {
        // Pause keeps the partial file; the next run asks for a Range from its length and the
        // server sees exactly that offset, so nothing restarts from zero.
        byte[] model = modelBytes('p');
        RangeHandler handler = new RangeHandler(model);
        String url = serve(handler);
        File output = output("pause-test", "model.litertlm");
        List<String> states = new ArrayList<>();
        TaiModelDownloader.Control control = new TaiModelDownloader.Control();

        run("pause-test", url, output, states, control, transfer -> {
            if (TaiModelStore.STATE_DOWNLOADING.equals(transfer.optString("status")) && transfer.optLong("bytesRead") > 0L) {
                control.requestPause(TaiModelStore.PAUSED_USER);
            }
        });

        assertEquals(TaiModelStore.STATE_PAUSED, states.get(states.size() - 1));
        JSONObject paused = latestDownload("download-pause-test");
        assertNotNull(paused);
        assertEquals(TaiModelStore.PAUSED_USER, paused.getString("pausedReason"));
        File part = new File(output.getAbsolutePath() + ".part");
        assertTrue(part.isFile());
        long kept = part.length();
        assertTrue(kept > 0L && kept < model.length);
        assertEquals(kept, paused.getLong("bytesRead"));

        List<String> resumed = new ArrayList<>();
        run("pause-test", url, output, resumed, new TaiModelDownloader.Control(), transfer -> { });

        assertEquals(TaiModelStore.STATE_INSTALLED, resumed.get(resumed.size() - 1));
        assertEquals("the second request must start where the first stopped", kept, handler.lastOffset);
        assertEquals(model.length, output.length());
        assertFalse(part.exists());
        JSONObject installed = latestDownload("download-pause-test");
        assertNotNull(installed);
        assertFalse("a new state clears the pause reason", installed.has("pausedReason"));
    }

    @Test
    public void runDownload_persistsVerifyingBeforeTheHashRuns() throws Exception {
        // The "checking file" state used to be written after the hash had already been compared,
        // so a screen never saw it while the wait was happening.
        byte[] model = modelBytes('v');
        String url = serve(new FixedBytesHandler(model));
        File output = output("verify-test", "model.litertlm");
        List<String> states = new ArrayList<>();
        List<String> storedAtVerifying = new ArrayList<>();

        new TaiModelDownloader(context, store).runDownload("download-verify-test", "verify-test", url, output,
            "Verify Test", "license", capabilities(), TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "", "", 4096, 0, "0000000000000000000000000000000000000000000000000000000000000000",
            0L, null, null, Collections.emptyList(), new TaiModelDownloader.Control(), transfer -> {
                states.add(transfer.optString("status"));
                if (TaiModelStore.STATE_VERIFYING.equals(transfer.optString("status"))) {
                    JSONObject stored = latestDownload("download-verify-test");
                    storedAtVerifying.add(stored == null ? "" : stored.optString("status"));
                }
            });

        assertTrue(states.indexOf(TaiModelStore.STATE_VERIFYING) >= 0);
        assertTrue(states.indexOf(TaiModelStore.STATE_VERIFYING) < states.indexOf(TaiModelStore.STATE_FAILED));
        assertEquals(TaiModelStore.STATE_FAILED, states.get(states.size() - 1));
        assertEquals(Collections.singletonList(TaiModelStore.STATE_VERIFYING), storedAtVerifying);

        // And a matching hash installs.
        List<String> good = new ArrayList<>();
        File goodOutput = output("verify-ok-test", "model.litertlm");
        new TaiModelDownloader(context, store).runDownload("download-verify-ok-test", "verify-ok-test", url, goodOutput,
            "Verify OK", "license", capabilities(), TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "", "", 4096, 0, sha256Hex(model), 0L, null, null,
            Collections.emptyList(), new TaiModelDownloader.Control(), transfer -> good.add(transfer.optString("status")));
        assertEquals(TaiModelStore.STATE_INSTALLED, good.get(good.size() - 1));
    }

    @Test
    public void runDownload_socketLossBecomesPausedNetworkWithThePartKept() throws Exception {
        // The server promises the whole file and then drops the connection halfway. That is the
        // network going away, not a bad download: the bytes on disk are good and the record pauses
        // (network) rather than fails, so an unmetered network can pick it up again.
        byte[] model = modelBytes('n');
        String url = serve(new TruncatingHandler(model, model.length / 2));
        File output = output("network-test", "model.litertlm");
        List<String> states = new ArrayList<>();

        run("network-test", url, output, states);

        assertEquals(TaiModelStore.STATE_PAUSED, states.get(states.size() - 1));
        JSONObject paused = latestDownload("download-network-test");
        assertNotNull(paused);
        assertEquals(TaiModelStore.PAUSED_NETWORK, paused.getString("pausedReason"));
        assertEquals("bytes moved, so this is not a retry without progress", 0, paused.optInt("networkRetries", -1));
        assertTrue(new File(output.getAbsolutePath() + ".part").isFile());
        assertTrue(paused.getLong("bytesRead") > 0L);
    }

    @Test
    public void runDownload_failedValidationCanRetryFromPart() throws Exception {
        byte[] html = modelBytes('<');
        html[0] = '<';
        html[1] = 'h';
        html[2] = 't';
        html[3] = 'm';
        html[4] = 'l';
        byte[] valid = modelBytes('c');
        String url = serve(new SequenceHandler(html, valid));
        File output = output("retry-test", "model.litertlm");
        List<String> firstStates = new ArrayList<>();

        run("retry-test", url, output, firstStates);

        assertEquals(TaiModelStore.STATE_FAILED, firstStates.get(firstStates.size() - 1));
        assertFalse(output.exists());
        assertTrue(new File(output.getAbsolutePath() + ".part").exists());

        List<String> retryStates = new ArrayList<>();
        run("retry-test", url, output, retryStates);

        assertEquals(TaiModelStore.STATE_INSTALLED, retryStates.get(retryStates.size() - 1));
        assertTrue(output.isFile());
        assertFalse(new File(output.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void completedDownloadRecordAdvertisesReadableModel() throws Exception {
        File output = output(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, "gemma-4-E2B-it.litertlm");
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        java.nio.file.Files.write(output.toPath(), modelBytes('g'));
        store.upsertDownload(new JSONObject()
            .put("id", "legacy-gemma-download")
            .put("modelId", TaiModelRegistry.MODEL_GEMMA_4_E2B_IT)
            .put("path", output.getAbsolutePath())
            .put("status", "complete")
            .put("bytesRead", output.length())
            .put("totalBytes", output.length())
            .put("capabilities", new org.json.JSONArray()
                .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                .put("stale_false_capability")));

        TaiModelSpec advertised = store.getDownloadedReadableModels().get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);

        assertNotNull(advertised);
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, advertised.backend);
        assertTrue(advertised.capabilities.contains("image_input"));
        assertTrue(advertised.capabilities.contains("audio_input"));
        assertTrue(advertised.capabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertTrue(advertised.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertFalse(advertised.sourceCapabilities.contains("stale_false_capability"));
        assertTrue(advertised.builtInCatalogEntry);
        assertEquals(4096, advertised.endpointContextWindow);
        assertEquals(32768, advertised.sourceContextWindow);
        assertEquals(4000, advertised.defaultMaxOutputTokens);
        assertEquals(output.length(), advertised.sizeBytes);
    }

    @Test
    public void completedGgufDownloadRecordIsNotEndpointVisible() throws Exception {
        File output = output("Qwen2.5-Coder-1.5B-GGUF", "qwen2.5-coder-1.5b.gguf");
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        java.nio.file.Files.write(output.toPath(), modelBytes('q'));
        store.upsertDownload(new JSONObject()
            .put("id", "legacy-gguf-download")
            .put("modelId", "Qwen2.5-Coder-1.5B-GGUF")
            .put("path", output.getAbsolutePath())
            .put("status", "complete")
            .put("bytesRead", output.length())
            .put("totalBytes", output.length()));

        assertFalse(store.getDownloadedReadableModels().containsKey("Qwen2.5-Coder-1.5B-GGUF"));
    }

    @Test
    public void installedAndDownloadedReadableModels_areResolvableBySameId() throws Exception {
        byte[] model = modelBytes('r');
        String url = serve(new FixedBytesHandler(model));
        File output = output("resolvable-test", "model.litertlm");
        run("resolvable-test", url, output, new ArrayList<>());

        // After a real install, both the installed-user-model store and the download record
        // must resolve the same id, so /v1/models listings can never 404 on resolveModel.
        assertNotNull("installed download must be resolvable via getUserModel", store.getUserModel("resolvable-test"));
        assertTrue("installed download must be in getDownloadedReadableModels",
            store.getDownloadedReadableModels().containsKey("resolvable-test"));
        java.util.Map<String, TaiModelSpec> readable = store.getDownloadedReadableModels();
        for (String id : readable.keySet()) {
            assertTrue("every /v1/models item must be a supported backend/format or excluded",
                TaiModelSpec.isSupportedBackendFormat(readable.get(id).backend, readable.get(id).format));
        }
        store.deleteUserModel("resolvable-test");
    }

    @Test
    public void runDownload_preservesQueuedMetadataForCatalogRows() throws Exception {
        byte[] model = modelBytes('m');
        String url = serveEmbeddingPackage(model, new byte[] {'s', 'p', 'm'});
        File output = output("metadata-test", "model.tflite");
        LinkedHashSet<String> caps = new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        store.upsertDownload(new JSONObject()
            .put("id", "download-metadata-test")
            .put("modelId", "metadata-test")
            .put("url", url)
            .put("path", output.getAbsolutePath())
            .put("status", TaiModelStore.STATE_QUEUED)
            .put("displayName", "Metadata Test")
            .put("backend", TaiModelSpec.BACKEND_LITERT_LM)
            .put("format", TaiModelSpec.FORMAT_LITERTLM)
            .put("capabilities", new org.json.JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)));

        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-metadata-test", "metadata-test", url, output,
            "Metadata Test", "license", caps, TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "", "", 4096, 0, "", 0L, null, transfer -> { });

        JSONObject stored = latestDownload("download-metadata-test");
        assertNotNull(stored);
        assertEquals("Metadata Test", stored.getString("displayName"));
        assertEquals(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS,
            stored.getJSONArray("capabilities").getString(0));
        assertTrue(new File(output.getParentFile(), "sentencepiece.model").isFile());
        TaiModelSpec spec = store.getDownloadedReadableModels().get("metadata-test");
        assertNotNull(spec);
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        assertFalse(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    @Test
    public void speechToTextTflite_doesNotTakeTheSentencepieceEmbeddingPath() throws Exception {
        // Before this change, requiresLiteRtEmbeddingTokenizer branched on the .tflite extension
        // alone, so a Whisper download would hunt for sentencepiece.model/tokenizer.model/spiece.model
        // next to the graph and fail (none of those exist in a Whisper repo). Serving only the model
        // itself and no sentencepiece candidate must now succeed, with no tokenizer sidecar fetched.
        byte[] model = modelBytes('w');
        String url = serve(new FixedBytesHandler(model));
        File output = output("whisper-branch-test", "acft_whisper_base.en_10s_drq.tflite");
        LinkedHashSet<String> caps = new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        List<String> states = new ArrayList<>();

        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-whisper-branch-test", "whisper-branch-test", url, output,
            "Whisper Branch Test", "license", caps, TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "whisper-acft", "int8_drq", 128, 0, "", 0L, null,
            transfer -> states.add(transfer.optString("status")));

        assertEquals(TaiModelStore.STATE_INSTALLED, states.get(states.size() - 1));
        assertTrue(output.isFile());
        assertFalse("a speech_to_text .tflite must not fetch a SentencePiece sidecar",
            new File(output.getParentFile(), "sentencepiece.model").isFile());
        TaiModelSpec spec = store.getDownloadedReadableModels().get("whisper-branch-test");
        assertNotNull(spec);
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        assertFalse(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        store.deleteUserModel("whisper-branch-test");
    }

    @Test
    public void catalogSidecar_downloadsTokenizerAlongsideTheModelAndVerifiesHash() throws Exception {
        byte[] model = modelBytes('x');
        byte[] tokenizer = "{\"tokenizer\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String tokenizerSha256 = sha256Hex(tokenizer);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.tflite", new FixedBytesHandler(model));
        server.createContext("/tokenizer.json", new FixedBytesHandler(tokenizer));
        server.start();
        String modelUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/model.tflite";
        String tokenizerUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/tokenizer.json";
        File output = output("whisper-sidecar-test", "model.tflite");
        List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars = Collections.singletonList(
            new TaiModelCatalog.CatalogEntry.Sidecar(tokenizerUrl, "tokenizer.json", tokenizerSha256));
        LinkedHashSet<String> caps = new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        List<String> states = new ArrayList<>();

        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-whisper-sidecar-test", "whisper-sidecar-test", modelUrl, output,
            "Whisper Sidecar Test", "license", caps, TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "whisper-acft", "int8_drq", 128, 0, "", 0L, null, null,
            sidecars, transfer -> states.add(transfer.optString("status")));

        assertEquals(TaiModelStore.STATE_INSTALLED, states.get(states.size() - 1));
        File tokenizerFile = new File(output.getParentFile(), "tokenizer.json");
        assertTrue("declared sidecar must be downloaded next to the model", tokenizerFile.isFile());
        assertEquals(new String(tokenizer, java.nio.charset.StandardCharsets.UTF_8),
            new String(java.nio.file.Files.readAllBytes(tokenizerFile.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        store.deleteUserModel("whisper-sidecar-test");
    }

    @Test
    public void missingCatalogSidecar_failsTheDownloadCleanly() throws Exception {
        byte[] model = modelBytes('y');
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.tflite", new FixedBytesHandler(model));
        // No /tokenizer.json handler registered — the sidecar request 404s.
        server.start();
        String modelUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/model.tflite";
        String tokenizerUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/tokenizer.json";
        File output = output("whisper-missing-sidecar-test", "model.tflite");
        List<TaiModelCatalog.CatalogEntry.Sidecar> sidecars = Collections.singletonList(
            new TaiModelCatalog.CatalogEntry.Sidecar(tokenizerUrl, "tokenizer.json", null));
        LinkedHashSet<String> caps = new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        List<String> states = new ArrayList<>();

        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-whisper-missing-sidecar-test", "whisper-missing-sidecar-test", modelUrl,
            output, "Whisper Missing Sidecar Test", "license", caps, TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "whisper-acft", "int8_drq", 128, 0, "", 0L, null, null,
            sidecars, transfer -> states.add(transfer.optString("status")));

        assertEquals(TaiModelStore.STATE_FAILED, states.get(states.size() - 1));
        store.deleteUserModel("whisper-missing-sidecar-test");
    }

    @Test
    public void staleInstalledE4bMetadata_isRebuiltFromCatalogFacts() throws Exception {
        File output = output(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, "gemma-4-E4B-it.litertlm");
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        byte[] bytes = modelBytes('e');
        java.nio.file.Files.write(output.toPath(), bytes);

        // Stale installed record that drops audio and never declared llm_thinking.
        JSONObject stale = new JSONObject()
            .put("id", TaiModelRegistry.MODEL_GEMMA_4_E4B_IT)
            .put("displayName", "Old E4B Name")
            .put("roleHint", "old role")
            .put("source", "imported")
            .put("localPath", output.getAbsolutePath())
            .put("license", "stale")
            .put("sizeBytes", 999L)
            .put("builtInCatalogEntry", false)
            .put("backend", TaiModelSpec.BACKEND_LITERT_LM)
            .put("format", TaiModelSpec.FORMAT_LITERTLM)
            .put("endpointContextWindow", 2048)
            .put("sourceContextWindow", 2048)
            .put("defaultMaxOutputTokens", 512)
            .put("recommendedRamGb", 0)
            .put("sourceCapabilities", new org.json.JSONArray()
                .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                .put(TaiModelSpec.CAPABILITY_IMAGE_INPUT))
            .put("endpointCapabilities", new org.json.JSONArray()
                .put(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        android.content.SharedPreferences prefs =
            context.getSharedPreferences(TaiSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("tai_user_models_json", new org.json.JSONArray().put(stale).toString()).apply();

        TaiModelSpec rebuilt = store.getUserModels().get(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);

        assertNotNull(rebuilt);
        assertTrue("stale installed metadata must not suppress catalog audio",
            rebuilt.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(rebuilt.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(rebuilt.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertTrue(rebuilt.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertEquals(4096, rebuilt.endpointContextWindow);
        assertEquals(32768, rebuilt.sourceContextWindow);
        assertEquals(4000, rebuilt.defaultMaxOutputTokens);
        assertEquals(output.length(), rebuilt.sizeBytes);
        assertTrue(rebuilt.builtInCatalogEntry);
        store.deleteUserModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
    }

    private void run(String modelId, String url, File output, List<String> states) {
        run(modelId, url, output, states, new TaiModelDownloader.Control(), transfer -> { });
    }

    private void run(String modelId, String url, File output, List<String> states,
                     TaiModelDownloader.Control control, TaiModelDownloader.ProgressCallback extraCallback) {
        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-" + modelId, modelId, url, output,
            modelId, "license", capabilities(), TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "", "", 4096, 0, "", 0L, null, null,
            Collections.emptyList(), control,
            transfer -> {
                states.add(transfer.optString("status"));
                extraCallback.onProgress(transfer);
            });
    }

    private String serve(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.litertlm", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/model.litertlm";
    }

    private String serveEmbeddingPackage(byte[] model, byte[] tokenizer) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.tflite", new FixedBytesHandler(model));
        server.createContext("/sentencepiece.model", new FixedBytesHandler(tokenizer));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/model.tflite";
    }

    private File output(String modelId, String name) {
        return new File(new File(store.getModelsDirectory(), modelId), name);
    }

    private JSONObject latestDownload(String transferId) {
        org.json.JSONArray downloads = store.getDownloads();
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && transferId.equals(item.optString("id", ""))) return item;
        }
        return null;
    }

    private static LinkedHashSet<String> capabilities() {
        return new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest(bytes)) builder.append(String.format(java.util.Locale.US, "%02x", value));
        return builder.toString();
    }

    private static byte[] modelBytes(char fill) {
        byte[] bytes = new byte[1024 * 1024 + 16];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) fill;
        bytes[0] = 'T';
        bytes[1] = 'A';
        bytes[2] = 'I';
        return bytes;
    }

    private static class FixedBytesHandler implements HttpHandler {
        private final byte[] bytes;

        FixedBytesHandler(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    /** Honours Range requests with a 206 and records the last offset asked for. */
    private static final class RangeHandler implements HttpHandler {
        private final byte[] bytes;
        volatile long lastOffset = -1L;

        RangeHandler(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String range = exchange.getRequestHeaders().getFirst("Range");
            long offset = 0L;
            if (range != null && range.startsWith("bytes=")) {
                offset = Long.parseLong(range.substring("bytes=".length(), range.indexOf('-')));
            }
            lastOffset = offset;
            int remaining = bytes.length - (int) offset;
            if (offset > 0L) {
                exchange.getResponseHeaders().add("Content-Range", "bytes " + offset + "-" + (bytes.length - 1) + "/" + bytes.length);
                exchange.sendResponseHeaders(206, remaining);
            } else {
                exchange.sendResponseHeaders(200, remaining);
            }
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes, (int) offset, remaining);
            }
        }
    }

    /** Promises the whole body, sends part of it, then drops the connection. */
    private static final class TruncatingHandler implements HttpHandler {
        private final byte[] bytes;
        private final int sendBytes;

        TruncatingHandler(byte[] bytes, int sendBytes) {
            this.bytes = bytes;
            this.sendBytes = sendBytes;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream output = exchange.getResponseBody();
            output.write(bytes, 0, sendBytes);
            output.flush();
            exchange.close();
        }
    }

    private static final class SequenceHandler implements HttpHandler {
        private final byte[] first;
        private final byte[] second;
        private int calls;

        SequenceHandler(byte[] first, byte[] second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public synchronized void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = calls++ == 0 ? first : second;
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
