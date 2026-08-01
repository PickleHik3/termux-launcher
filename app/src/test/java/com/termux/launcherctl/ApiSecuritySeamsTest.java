package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.MultiBackendTaiRuntime;
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
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for API and security seams: LAN binding, auth, CORS,
 * and embeddings endpoint behavior.
 *
 * <p>All tests are deterministic, use fakes/mocks, and do not require a real
 * MNN native runtime or network access.
 */
@RunWith(RobolectricTestRunner.class)
public class ApiSecuritySeamsTest {
    private Context context;
    private LauncherCtlApiServer server;
    private int port;
    private String token;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();

        Field taiInstance = TaiManager.class.getDeclaredField("instance");
        taiInstance.setAccessible(true);
        taiInstance.set(null, null);

        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
        server = LauncherCtlApiServer.getInstance();
        server.start(context);
        Thread.sleep(150);

        JSONObject endpoint = server.endpointSettings(context);
        port = endpoint.getInt("activePort");
        token = endpoint.getString("token");
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) server.stop();

        Field taiInstance = TaiManager.class.getDeclaredField("instance");
        taiInstance.setAccessible(true);
        taiInstance.set(null, null);

        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
    }

    @Test
    public void lanDefault_localhostBinding() throws Exception {
        TaiSettings settings = new TaiSettings(context);
        assertEquals(TaiSettings.BIND_MODE_LOCALHOST, settings.getApiBindMode());
        assertEquals("127.0.0.1", LauncherCtlApiServer.bindAddressForMode(settings.getApiBindMode()));
    }

    @Test
    public void lanOptIn_bindsToAllInterfaces() throws Exception {
        try (java.net.ServerSocket socket = LauncherCtlApiServer.createLoopbackServerSocket(0, TaiSettings.BIND_MODE_LAN)) {
            assertTrue(socket.getInetAddress().isAnyLocalAddress());
            assertEquals("0.0.0.0", LauncherCtlApiServer.bindAddressForMode(TaiSettings.BIND_MODE_LAN));
        }
    }

    @Test
    public void lanAuth_requiredForBothModes() throws Exception {
        assertUnauthorized(port);

        // Switch to LAN mode and verify auth still required
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("tai_api_bind_mode", TaiSettings.BIND_MODE_LAN).commit();

        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
        LauncherCtlApiServer lanServer = LauncherCtlApiServer.getInstance();
        lanServer.start(context);
        Thread.sleep(150);

        try {
            JSONObject lanEndpoint = lanServer.endpointSettings(context);
            int lanPort = lanEndpoint.getInt("activePort");
            assertUnauthorized(lanPort);
        } finally {
            lanServer.stop();
            serverInstance.set(null, null);
        }
    }

    @Test
    public void corsOrigin_presentButAuthStillGates() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LauncherCtlApiServer.writeResponse(output, LauncherCtlApiServer.unauthorizedResponse());
        String response = output.toString(StandardCharsets.UTF_8.name());

        assertTrue(response.startsWith("HTTP/1.1 401 Unauthorized"));
        assertTrue(response.toLowerCase().contains("access-control-allow-origin: *"));
    }

    @Test
    public void embeddingsLiteRt_returnsUnsupported() throws Exception {
        File tempFile = File.createTempFile("litert-model", ".litertlm");
        tempFile.deleteOnExit();
        TaiManager manager = TaiManager.getInstance(context);
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "litert-embed-model")
            .toString());

        // Without an in-process runtime, TaiManager.embeddings() delegates to the separate runtime
        // process before it ever reaches the capability check, and a unit test has no such process
        // (503). Inject the fake so this test exercises the capability seam it is named for.
        injectFakeRuntime(manager);

        HttpURLConnection conn = post("/v1/embeddings", new JSONObject()
            .put("model", "litert-embed-model")
            .put("input", "hello world"));

        int code = conn.getResponseCode();
        assertTrue("Expected 400 or 501 for LiteRT embeddings, got " + code, code == 400 || code == 501);
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("capability_not_supported", response.getJSONObject("error").getString("code"));
    }

    @Test
    public void embeddingsMnnChatOnly_returnsUnsupported() throws Exception {
        File tempFile = File.createTempFile("chat-model", ".mnn");
        tempFile.deleteOnExit();
        TaiManager manager = TaiManager.getInstance(context);
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "chat-only-mnn")
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_CHAT))
            .toString());

        // Inject fake runtime so load succeeds without real native libs
        injectFakeRuntime(manager);
        manager.loadModel(new JSONObject().put("model", "chat-only-mnn").toString());

        HttpURLConnection conn = post("/v1/embeddings", new JSONObject()
            .put("model", "chat-only-mnn")
            .put("input", "hello world"));

        int code = conn.getResponseCode();
        assertTrue("Expected 400 or 501 for chat-only MNN embeddings, got " + code, code == 400 || code == 501);
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("capability_not_supported", response.getJSONObject("error").getString("code"));
    }

    @Test
    public void embeddingsMnnCapable_returnsSuccess() throws Exception {
        File tempFile = File.createTempFile("embed-model", ".mnn");
        tempFile.deleteOnExit();
        TaiManager manager = TaiManager.getInstance(context);
        manager.importModel(new JSONObject()
            .put("path", tempFile.getAbsolutePath())
            .put("modelId", "embed-capable-mnn")
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS))
            .toString());

        FakeMultiBackendRuntime fake = injectFakeRuntime(manager);
        fake.addEmbeddingsCapableModel("embed-capable-mnn");
        // Embedding-only models are served on demand, never loaded into the generation runtime.
        JSONObject load = manager.loadModel(new JSONObject().put("model", "embed-capable-mnn").toString());
        assertEquals("embedding_model_not_loadable", load.optString("error"));

        HttpURLConnection conn = post("/v1/embeddings", new JSONObject()
            .put("model", "embed-capable-mnn")
            .put("input", "hello world"));

        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("list", response.getString("object"));
        assertEquals(1, response.getJSONArray("data").length());
        JSONObject embedding = response.getJSONArray("data").getJSONObject(0);
        assertEquals("embedding", embedding.getString("object"));
        assertEquals(768, embedding.getJSONArray("embedding").length());
        assertEquals("embed-capable-mnn", response.getString("model"));
        assertTrue(response.has("usage"));
    }

    private void assertUnauthorized(int targetPort) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + targetPort + "/v1/models").openConnection();
        conn.setRequestMethod("GET");
        assertEquals(401, conn.getResponseCode());
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

    private String readBody(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = stream.read(b)) != -1) buf.write(b, 0, n);
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    private FakeMultiBackendRuntime injectFakeRuntime(TaiManager manager) throws Exception {
        Field runtimeField = TaiManager.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        FakeMultiBackendRuntime fake = new FakeMultiBackendRuntime(context);
        runtimeField.set(manager, fake);
        return fake;
    }

    static class FakeMultiBackendRuntime extends MultiBackendTaiRuntime {
        private final java.util.Set<String> embeddingsCapableModels = new java.util.HashSet<>();
        private final java.util.Set<String> loadedModels = new java.util.HashSet<>();

        FakeMultiBackendRuntime(Context context) {
            super(context);
        }

        void addEmbeddingsCapableModel(String modelId) {
            embeddingsCapableModels.add(modelId);
        }

        @Override
        public JSONObject load(TaiModelSpec spec, TaiRuntimeOptions options) throws org.json.JSONException {
            loadedModels.add(spec.id);
            JSONObject ok = new JSONObject();
            ok.put("ok", true);
            return ok;
        }

        @Override
        public boolean isModelLoaded(String modelId) {
            return loadedModels.contains(modelId);
        }

        // No "model not loaded" gate: TaiManager.loadModel() rejects embedding-only models
        // ("embedding_model_not_loadable") because they are served on demand, so such a model is
        // never in loadedModels and gating on it could only ever fail.
        @Override
        public JSONObject embed(String modelId, String input) throws org.json.JSONException {
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
            JSONArray embedding = new JSONArray();
            for (int i = 0; i < 768; i++) embedding.put(i % 2 == 0 ? 0.1 : -0.1);
            JSONObject item = new JSONObject();
            item.put("object", "embedding");
            item.put("embedding", embedding);
            item.put("index", 0);
            JSONArray dataArray = new JSONArray();
            dataArray.put(item);
            JSONObject response = new JSONObject();
            response.put("object", "list");
            response.put("data", dataArray);
            response.put("model", modelId);
            JSONObject usage = new JSONObject();
            usage.put("prompt_tokens", 4);
            usage.put("total_tokens", 4);
            response.put("usage", usage);
            return response;
        }
    }
}
