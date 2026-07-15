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
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlNotificationEndpointsTest {

    private Context context;
    private LauncherCtlApiServer server;
    private int port;
    private String token;
    private File tempDir;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();

        tempDir = new File(context.getFilesDir(), "notif-endpoints-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);
        LauncherCtlNotificationStore.resetForTesting();

        resetSingleton(TaiManager.class, "instance");
        resetSingleton(LauncherCtlApiServer.class, "instance");

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
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(tempDir);
    }

    @Test
    public void getNotifications_activeSnapshotIsBackwardCompatible() throws Exception {
        HttpURLConnection conn = get("/v1/notifications");
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertTrue(response.has("notifications"));
        assertFalse(response.getBoolean("listenerConnected"));
    }

    @Test
    public void postNotificationsRecent_returnsRecentEvents() throws Exception {
        insertEvent("posted", 1000L, "com.example", "Recent");

        HttpURLConnection conn = post("/v1/notifications/recent", new JSONObject().put("limit", 10));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertEquals(1, response.getInt("count"));
        JSONArray events = response.getJSONArray("events");
        assertEquals("posted", events.getJSONObject(0).getString("eventType"));
        assertEquals("Recent", events.getJSONObject(0).getJSONObject("notification").getString("title"));
    }

    @Test
    public void postNotificationsSince_filtersByTimestamp() throws Exception {
        insertEvent("posted", 1000L, "com.example", "Old");
        insertEvent("posted", 3000L, "com.example", "New");

        HttpURLConnection conn = post("/v1/notifications/since", new JSONObject().put("since", 2000));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals(1, response.getInt("count"));
        assertEquals("New", response.getJSONArray("events").getJSONObject(0).getJSONObject("notification").getString("title"));
    }

    @Test
    public void postNotificationsSince_missingSince_returnsBadRequest() throws Exception {
        HttpURLConnection conn = post("/v1/notifications/since", new JSONObject());
        assertEquals(400, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("bad_request", response.getJSONObject("error").getString("code"));
        assertEquals("bad_request", response.getJSONObject("tai").getString("error"));
    }

    @Test
    public void postNotificationsSearch_matchesText() throws Exception {
        insertEvent("posted", 1000L, "com.example", "Hello world");
        insertEvent("posted", 2000L, "com.other", "Goodbye");

        HttpURLConnection conn = post("/v1/notifications/search", new JSONObject().put("query", "hello"));
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals(1, response.getInt("count"));
        assertEquals("Hello world", response.getJSONArray("events").getJSONObject(0).getJSONObject("notification").getString("title"));
    }

    @Test
    public void postNotificationsSearch_missingQuery_returnsBadRequest() throws Exception {
        HttpURLConnection conn = post("/v1/notifications/search", new JSONObject());
        assertEquals(400, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("bad_request", response.getJSONObject("error").getString("code"));
        assertEquals("bad_request", response.getJSONObject("tai").getString("error"));
    }

    @Test
    public void postNotificationsStats_returnsCounts() throws Exception {
        insertEvent("posted", 1000L, "com.example", "One");
        insertEvent("posted", 2000L, "com.example", "Two");
        insertEvent("removed", 3000L, "com.other", "Three");

        HttpURLConnection conn = post("/v1/notifications/stats", new JSONObject());
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertEquals(3, response.getLong("total"));
        assertEquals(2, response.getLong("posted"));
        assertEquals(1, response.getLong("removed"));
        assertTrue(response.getJSONArray("packages").length() >= 1);
    }

    private void insertEvent(String eventType, long eventTime, String packageName, String title) throws Exception {
        JSONObject notification = new JSONObject();
        notification.put("key", packageName + ":" + System.nanoTime());
        notification.put("packageName", packageName);
        notification.put("id", 1);
        notification.put("tag", JSONObject.NULL);
        notification.put("postTime", 0);
        notification.put("isOngoing", false);
        notification.put("isClearable", true);
        notification.put("category", JSONObject.NULL);
        notification.put("title", title);
        notification.put("text", "");
        notification.put("subText", JSONObject.NULL);
        notification.put("bigText", JSONObject.NULL);
        LauncherCtlNotificationStore.getInstance().insertEvent(new LauncherCtlNotificationEvent(eventType, eventTime, notification));
    }

    private HttpURLConnection get(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        return conn;
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
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
