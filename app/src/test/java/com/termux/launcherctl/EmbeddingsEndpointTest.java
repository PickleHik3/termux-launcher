package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiRuntimeOptions;
import com.termux.ai.TaiSettings;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class EmbeddingsEndpointTest {
    private Context context;
    private LauncherCtlApiServer server;
    private TaiManager manager;
    private FakeMultiBackendRuntime fakeRuntime;
    private int port;
    private String token;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();

        // Reset TaiManager singleton
        Field taiInstance = TaiManager.class.getDeclaredField("instance");
        taiInstance.setAccessible(true);
        taiInstance.set(null, null);
        manager = TaiManager.getInstance(context);

        // Inject fake runtime
        Field runtimeField = TaiManager.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        fakeRuntime = new FakeMultiBackendRuntime(context);
        runtimeField.set(manager, fakeRuntime);

        // Reset LauncherCtlApiServer singleton
        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
        server = LauncherCtlApiServer.getInstance();
        server.start(context);

        JSONObject endpoint = server.endpointSettings(context);
        port = endpoint.getInt("activePort");
        token = endpoint.getString("token");
        awaitAnswering(port);

        // importModel scopes local paths to the Termux home directory, which is not a real
        // writable path on the test JVM; let fixtures import from the JVM temp dir instead.
        TaiManager.setImportPathAllowedRootForTesting(System.getProperty("java.io.tmpdir"));
    }

    @After
    public void tearDown() throws Exception {
        TaiManager.setImportPathAllowedRootForTesting(null);
        if (server != null) server.stop();

        Field taiInstance = TaiManager.class.getDeclaredField("instance");
        taiInstance.setAccessible(true);
        taiInstance.set(null, null);

        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
    }

    @Test
    public void embeddings_withoutAuth_returns401() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/embeddings").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(401, conn.getResponseCode());
    }

    @Test
    public void embeddings_withEmbeddingsCapableMnnModel_returnsOpenAiEmbeddingList() throws Exception {
        File tempFile = File.createTempFile("embed-model", ".mnn");
        tempFile.deleteOnExit();
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "embed-mnn")
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS))
            .toString());
        fakeRuntime.addEmbeddingsCapableModel("embed-mnn");
        assertEmbeddingModelIsNotLoadable("embed-mnn");

        HttpURLConnection conn = post("/v1/embeddings", new JSONObject()
            .put("model", "embed-mnn")
            .put("input", "hello world"));

        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("list", response.getString("object"));
        assertEquals(1, response.getJSONArray("data").length());
        JSONObject embedding = response.getJSONArray("data").getJSONObject(0);
        assertEquals("embedding", embedding.getString("object"));
        assertEquals(768, embedding.getJSONArray("embedding").length());
        assertEquals("embed-mnn", response.getString("model"));
        assertTrue(response.has("usage"));
    }

    @Test
    public void embeddings_withArrayInput_returnsOneEmbeddingPerInput() throws Exception {
        File tempFile = File.createTempFile("embed-array-model", ".mnn");
        tempFile.deleteOnExit();
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "embed-array-mnn")
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS))
            .toString());
        fakeRuntime.addEmbeddingsCapableModel("embed-array-mnn");
        assertEmbeddingModelIsNotLoadable("embed-array-mnn");

        HttpURLConnection conn = post("/v1/embeddings", new JSONObject()
            .put("model", "embed-array-mnn")
            .put("input", new JSONArray().put("hello").put("world")));

        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        JSONArray data = response.getJSONArray("data");
        assertEquals(2, data.length());
        assertEquals(0, data.getJSONObject(0).getInt("index"));
        assertEquals(1, data.getJSONObject(1).getInt("index"));
        assertEquals(768, data.getJSONObject(0).getJSONArray("embedding").length());
    }

    @Test
    public void embeddings_withChatOnlyMnnModel_returnsCapabilityNotSupported() throws Exception {
        File tempFile = File.createTempFile("chat-model", ".mnn");
        tempFile.deleteOnExit();
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "chat-mnn")
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_CHAT))
            .toString());
        manager.loadModel(new JSONObject().put("model", "chat-mnn").toString());

        HttpURLConnection conn = post("/v1/embeddings", new JSONObject()
            .put("model", "chat-mnn")
            .put("input", "hello world"));

        int code = conn.getResponseCode();
        assertTrue("Expected 400 or 501, got " + code, code == 400 || code == 501);
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("capability_not_supported", response.getJSONObject("error").getString("code"));
    }

    @Test
    public void embeddings_withLiteRtModel_returnsUnsupportedError() throws Exception {
        File tempFile = File.createTempFile("litert-model", ".litertlm");
        tempFile.deleteOnExit();
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "litert-model")
            .toString());

        HttpURLConnection conn = post("/v1/embeddings", new JSONObject()
            .put("model", "litert-model")
            .put("input", "hello world"));

        int code = conn.getResponseCode();
        assertTrue("Expected 400 or 501, got " + code, code == 400 || code == 501);
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("capability_not_supported", response.getJSONObject("error").getString("code"));
    }

    @Test
    public void models_returnsStandardOpenAiListWithBackendAndCapabilities() throws Exception {
        File tempFile = File.createTempFile("listed-litert-model", ".litertlm");
        tempFile.deleteOnExit();
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "listed-litert-model")
            .toString());

        HttpURLConnection conn = get("/v1/models");
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("list", response.getString("object"));
        JSONArray data = response.getJSONArray("data");
        assertTrue(data.length() > 0);
        JSONObject first = data.getJSONObject(0);
        assertTrue(first.has("id"));
        assertTrue(first.has("object"));
        assertTrue(first.has("created"));
        assertTrue(first.has("owned_by"));
        assertTrue(first.has("_backend"));
        assertTrue(first.has("_capabilities"));
    }

    @Test
    public void chatCompletions_endpointStillResponds() throws Exception {
        HttpURLConnection conn = post("/v1/chat/completions", new JSONObject()
            .put("model", "nonexistent")
            .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "hi"))));
        assertRouteExists(conn);
    }

    @Test
    public void completions_endpointStillResponds() throws Exception {
        HttpURLConnection conn = post("/v1/completions", new JSONObject()
            .put("model", "nonexistent")
            .put("prompt", "hi"));
        assertRouteExists(conn);
    }

    /**
     * An unknown model is a legitimate 404 ("model_not_found"), so a bare {@code != 404} check
     * cannot tell a live route from a missing one. The router's own miss is {@code "not_found"} with
     * message "Unknown endpoint"; anything else means the handler ran, which is what these assert.
     */
    private void assertRouteExists(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        if (code != 404) return;
        JSONObject response = new JSONObject(readBody(conn));
        String errorCode = response.optJSONObject("error") != null
            ? response.getJSONObject("error").optString("code")
            : response.optString("error");
        assertFalse("route is not registered on the server: " + response,
            "not_found".equals(errorCode));
    }

    /**
     * Embedding-only models are served on demand and are explicitly not loadable into the
     * generation runtime, so the tests above must not try to "load" one first.
     */
    private void assertEmbeddingModelIsNotLoadable(String modelId) throws Exception {
        JSONObject result = manager.loadModel(new JSONObject().put("model", modelId).toString());
        assertEquals("embedding_model_not_loadable", result.optString("error"));
        assertEquals(400, result.optInt("_statusCode"));
    }

    private static void awaitAnswering(int targetPort) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + targetPort + "/v1/models").openConnection();
                conn.setConnectTimeout(250);
                conn.setReadTimeout(250);
                conn.getResponseCode();
                return;
            } catch (IOException e) {
                if (System.currentTimeMillis() >= deadline) throw e;
                Thread.sleep(20);
            }
        }
    }

    private HttpURLConnection post(String path, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        return conn;
    }

    private HttpURLConnection get(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = stream.read(b)) != -1) buf.write(b, 0, n);
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    static class FakeMultiBackendRuntime extends com.termux.ai.MultiBackendTaiRuntime {
        private final Set<String> embeddingsCapableModels = new HashSet<>();
        private final Set<String> loadedModels = new HashSet<>();

        FakeMultiBackendRuntime(Context context) {
            super(context);
        }

        void addEmbeddingsCapableModel(String modelId) {
            embeddingsCapableModels.add(modelId);
        }

        @Override
        public JSONObject load(com.termux.ai.TaiModelSpec spec, com.termux.ai.TaiRuntimeOptions options) throws org.json.JSONException {
            loadedModels.add(spec.id);
            JSONObject ok = new JSONObject();
            ok.put("ok", true);
            return ok;
        }

        @Override
        public boolean isModelLoaded(String modelId) {
            return loadedModels.contains(modelId);
        }

        @Override
        public JSONObject embed(String modelId, String input) throws org.json.JSONException {
            return embeddingResponse(modelId, java.util.Collections.singletonList(input), 768);
        }

        @Override
        public JSONObject embed(TaiModelSpec spec, List<String> inputs, int dimensions) throws org.json.JSONException {
            return embeddingResponse(spec.id, inputs, dimensions > 0 ? dimensions : 768);
        }

        // No "model not loaded" gate here on purpose: TaiManager.loadModel() rejects embedding-only
        // models outright ("embedding_model_not_loadable") because they are served on demand, so an
        // embedding model is never in loadedModels and gating on it could only ever fail.
        private JSONObject embeddingResponse(String modelId, List<String> inputs, int dimensions) throws org.json.JSONException {
            if (!embeddingsCapableModels.contains(modelId)) {
                JSONObject error = new JSONObject();
                error.put("message", "Embeddings are not supported for model '" + modelId + "'.");
                error.put("type", "invalid_request_error");
                error.put("param", "model");
                error.put("code", "capability_not_supported");
                JSONObject response = new JSONObject();
                response.put("error", error);
                response.put("_statusCode", 400);
                return response;
            }
            JSONArray dataArray = new JSONArray();
            for (int index = 0; index < inputs.size(); index++) {
                JSONArray embedding = new JSONArray();
                for (int i = 0; i < dimensions; i++) embedding.put(i % 2 == 0 ? 0.1 : -0.1);
                JSONObject item = new JSONObject();
                item.put("object", "embedding");
                item.put("embedding", embedding);
                item.put("index", index);
                dataArray.put(item);
            }
            JSONObject response = new JSONObject();
            response.put("object", "list");
            response.put("data", dataArray);
            response.put("model", modelId);
            JSONObject usage = new JSONObject();
            usage.put("prompt_tokens", inputs.size() * 4);
            usage.put("total_tokens", inputs.size() * 4);
            response.put("usage", usage);
            return response;
        }
    }
}
