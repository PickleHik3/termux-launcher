package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.TaiManager;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlAgentRouteExecuteTest {

    private Context context;
    private LauncherCtlApiServer server;
    private int port;
    private String token;
    private File tempDir;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();

        tempDir = new File(context.getFilesDir(), "agent-route-execute-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);
        LauncherCtlNotificationStore.resetForTesting();
        LauncherCtlEventStore.resetForTesting();
        LauncherCtlMemoryStore.resetForTesting();

        resetSingleton(TaiManager.class, "instance");
        resetSingleton(LauncherCtlApiServer.class, "instance");
        LauncherToolRegistry.resetForTesting();

        server = LauncherCtlApiServer.getInstance();
        server.start(context);
        Thread.sleep(150);

        JSONObject endpoint = server.endpointSettings(context);
        port = endpoint.getInt("activePort");
        token = endpoint.getString("token");
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        resetSingleton(TaiManager.class, "instance");
        resetSingleton(LauncherCtlApiServer.class, "instance");
        LauncherCtlNotificationStore.resetForTesting();
        LauncherCtlEventStore.resetForTesting();
        LauncherCtlMemoryStore.resetForTesting();
        LauncherToolRegistry.resetForTesting();
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(tempDir);
    }

    @Test
    public void postAgentRoute_openMaps_routesToAppsLaunch() throws Exception {
        HttpURLConnection conn = post("/v1/agent/route", new JSONObject().put("request", "open maps"));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertTrue(response.getBoolean("routed"));
        assertEquals("apps.launch", response.getString("tool"));
        assertEquals("maps", response.getJSONObject("arguments").getString("query"));
        assertTrue(response.getBoolean("requiresConfirmation"));
        assertEquals("medium", response.getString("risk"));
    }

    @Test
    public void postAgentRoute_resources_routesToSystemResources() throws Exception {
        HttpURLConnection conn = post("/v1/agent/route", new JSONObject().put("request", "show resources"));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("system.resources", response.getString("tool"));
        assertFalse(response.getBoolean("requiresConfirmation"));
    }

    @Test
    public void postAgentRoute_whatIsPlaying_routesToMedia() throws Exception {
        HttpURLConnection conn = post("/v1/agent/route", new JSONObject().put("request", "what is playing"));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("media.now_playing", response.getString("tool"));
        assertFalse(response.getBoolean("requiresConfirmation"));
    }

    @Test
    public void postAgentRoute_noObviousIntent_defaultsToCapabilities() throws Exception {
        HttpURLConnection conn = post("/v1/agent/route", new JSONObject().put("request", "hello"));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("capabilities.get", response.getString("tool"));
    }

    @Test
    public void postAgentRoute_doesNotExecute() throws Exception {
        HttpURLConnection conn = post("/v1/agent/route", new JSONObject().put("request", "open termux"));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        // Route should return metadata, not an execution result.
        assertTrue(response.has("tool"));
        assertFalse(response.has("result"));
        assertFalse(response.optBoolean("executed", false));
    }

    @Test
    public void postAgentExecute_lowRiskTool_runsWithoutConfirmation() throws Exception {
        HttpURLConnection conn = post("/v1/agent/execute", new JSONObject()
            .put("tool", "capabilities.get")
            .put("arguments", new JSONObject()));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertTrue(response.getJSONObject("result").getBoolean("ok"));
    }

    @Test
    public void postAgentExecute_requiresConfirmation_withoutConfirm_returnsForbidden() throws Exception {
        HttpURLConnection conn = post("/v1/agent/execute", new JSONObject()
            .put("tool", "apps.launch")
            .put("arguments", new JSONObject().put("query", "maps")));
        assertEquals(403, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("confirmation_required", response.getJSONObject("error").getString("code"));
        assertEquals("confirmation_required", response.getJSONObject("tai").getString("error"));
        assertEquals("apps.launch", response.getString("tool"));
    }

    @Test
    public void postAgentExecute_openAiName_mapsToInternalName() throws Exception {
        HttpURLConnection conn = post("/v1/agent/execute", new JSONObject()
            .put("name", "capabilities_get")
            .put("arguments", new JSONObject()));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
    }

    @Test
    public void postAgentExecute_memoryWrite_andSearch() throws Exception {
        HttpURLConnection conn = post("/v1/agent/execute", new JSONObject()
            .put("tool", "memory.write")
            .put("arguments", new JSONObject()
                .put("key", "note")
                .put("value", "hello"))
            .put("confirm", true));
        assertEquals(200, conn.getResponseCode());
        JSONObject writeResponse = new JSONObject(readBody(conn));
        assertTrue(writeResponse.getBoolean("ok"));

        HttpURLConnection searchConn = post("/v1/agent/execute", new JSONObject()
            .put("tool", "memory.search")
            .put("arguments", new JSONObject()
                .put("query", "hello"))
            .put("confirm", true));
        assertEquals(200, searchConn.getResponseCode());
        JSONObject searchResponse = new JSONObject(readBody(searchConn));
        assertTrue(searchResponse.getBoolean("ok"));
        assertEquals(1, searchResponse.getJSONObject("result").getInt("count"));
    }

    @Test
    public void postAgentExecute_appendsAuditEvents() throws Exception {
        HttpURLConnection conn = post("/v1/agent/execute", new JSONObject()
            .put("tool", "capabilities.get")
            .put("arguments", new JSONObject()));
        assertEquals(200, conn.getResponseCode());
        new JSONObject(readBody(conn));

        LauncherCtlEventStore.getInstance().awaitWritesForTesting(2_000);
        JSONArray events = new JSONArray();
        for (JSONObject event : LauncherCtlEventStore.getInstance().tailEvents(10, null)) {
            events.put(event);
        }
        assertFalse(LauncherCtlEventStore.getInstance().tailAgentRuns(10, null).isEmpty());
        assertTrue(events.length() > 0);
        JSONObject payload = events.getJSONObject(events.length() - 1).getJSONObject("payload");
        assertEquals("capabilities.get", payload.getString("tool"));
    }

    @Test
    public void postEventsTail_returnsRecentEvents() throws Exception {
        LauncherCtlEventStore.getInstance().appendEventSync("test", new JSONObject(),
            LauncherCtlStorage.getEventsJsonlFile());

        HttpURLConnection conn = post("/v1/events/tail", new JSONObject().put("limit", 10));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertEquals(1, response.getInt("count"));
        JSONArray events = response.getJSONArray("events");
        assertEquals("test", events.getJSONObject(0).getString("type"));
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

    private static void resetSingleton(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
