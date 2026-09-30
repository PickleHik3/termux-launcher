package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.TaiManager;
import com.termux.ai.TaiSettings;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The image routes' refusals and discovery, before any generation is committed to. */
@RunWith(RobolectricTestRunner.class)
public class ImageGenerationRouteTest {
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
        TaiManager manager = TaiManager.getInstance(context);
        Field runtimeField = TaiManager.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        runtimeField.set(manager, new com.termux.ai.MultiBackendTaiRuntime(context));
        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
        server = LauncherCtlApiServer.getInstance();
        server.start(context);
        JSONObject endpoint = server.endpointSettings(context);
        port = endpoint.getInt("activePort");
        token = endpoint.getString("token");
        awaitAnswering(port);
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
    public void withoutAuthTheRouteIsRefused() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/ai/images/generations").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(401, conn.getResponseCode());
    }

    @Test
    public void aMissingPromptIsAnOpenAiShapedBadRequest() throws Exception {
        HttpURLConnection conn = post("/v1/ai/images/generations", new JSONObject().put("model", "whatever"));
        assertEquals(400, conn.getResponseCode());
        assertEquals("missing_prompt", new JSONObject(readBody(conn)).getJSONObject("error").getString("code"));
    }

    @Test
    public void moreThanOneImageIsRefusedEvenWhenStreaming() throws Exception {
        HttpURLConnection conn = post("/v1/ai/images/generations",
            new JSONObject().put("model", "whatever").put("prompt", "p").put("n", 2).put("stream", true));
        assertEquals(400, conn.getResponseCode());
        assertEquals("unsupported_n", new JSONObject(readBody(conn)).getJSONObject("error").getString("code"));
    }

    @Test
    public void anUnknownModelIsNotFoundAndAForbiddenPathIsForbidden() throws Exception {
        HttpURLConnection unknown = post("/v1/ai/images/generations", new JSONObject().put("model", "nope").put("prompt", "p"));
        assertEquals(404, unknown.getResponseCode());
        HttpURLConnection forbidden = post("/v1/ai/images/generations",
            new JSONObject().put("model_path", "/proc/self").put("prompt", "p"));
        assertEquals(403, forbidden.getResponseCode());
    }

    @Test
    public void cancelAnswersOkWhenNothingIsRunning() throws Exception {
        HttpURLConnection conn = post("/v1/ai/images/cancel", new JSONObject());
        assertEquals(200, conn.getResponseCode());
        JSONObject body = new JSONObject(readBody(conn));
        assertTrue(body.getBoolean("ok"));
        assertFalse(body.getBoolean("cancelled"));
    }

    @Test
    public void theRouteIsListedAmongTheSupportedEndpoints() throws Exception {
        assertTrue(server.endpointSettings(context).getJSONArray("supportedEndpoints").toString()
            .contains("/v1/ai/images/generations"));
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
        while ((n = stream.read(b)) > 0) buf.write(b, 0, n);
        return buf.toString("UTF-8");
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
}
