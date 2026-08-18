package com.termux.launcherctl;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlNotificationStoreTest {

    private File tempDir;

    @Before
    public void setUp() {
        tempDir = new File(ApplicationProvider.getApplicationContext().getFilesDir(),
            "notif-store-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);
        LauncherCtlNotificationStore.resetForTesting();
    }

    @After
    public void tearDown() {
        LauncherCtlNotificationStore.resetForTesting();
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(tempDir);
    }

    @Test
    public void insertEvent_persistsToJsonl() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        JSONObject notification = notificationJson("pkg1", "Hello", "World");
        store.insertEvent(new LauncherCtlNotificationEvent("posted", 1000L, notification));

        List<String> lines = Files.readAllLines(LauncherCtlStorage.getNotificationsJsonlFile().toPath(), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        JSONObject line = new JSONObject(lines.get(0));
        assertEquals("posted", line.getString("eventType"));
        assertEquals(1000L, line.getLong("eventTime"));
        assertEquals("Hello", line.getJSONObject("notification").getString("title"));
    }

    @Test
    public void jsonl_rotatesOnceItPassesTheByteCeiling() throws Exception {
        File jsonl = LauncherCtlStorage.getNotificationsJsonlFile();
        jsonl.getParentFile().mkdirs();
        byte[] filler = new byte[(int) LauncherCtlNotificationStore.MAX_JSONL_BYTES + 1];
        java.util.Arrays.fill(filler, (byte) 'x');
        Files.write(jsonl.toPath(), filler);

        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));

        File rotated = new File(jsonl.getParentFile(), jsonl.getName() + ".1");
        assertTrue(rotated.isFile());
        assertEquals(filler.length, rotated.length());
        // The live stream restarts from the event that triggered the rotation.
        assertEquals(1, Files.readAllLines(jsonl.toPath(), StandardCharsets.UTF_8).size());
    }

    @Test
    public void jsonl_keepsAtMostOneRotation() throws Exception {
        File jsonl = LauncherCtlStorage.getNotificationsJsonlFile();
        jsonl.getParentFile().mkdirs();
        byte[] filler = new byte[(int) LauncherCtlNotificationStore.MAX_JSONL_BYTES + 1];
        java.util.Arrays.fill(filler, (byte) 'x');
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();

        Files.write(jsonl.toPath(), filler);
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));
        Files.write(jsonl.toPath(), filler);
        store.insertEvent(event("posted", 2000L, "pkg2", "B", ""));

        File[] streams = jsonl.getParentFile().listFiles((dir, name) -> name.startsWith(jsonl.getName()));
        assertEquals(2, streams == null ? 0 : streams.length);
    }

    @Test
    public void clearAll_removesRowsAndEveryStream() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));
        File jsonl = LauncherCtlStorage.getNotificationsJsonlFile();
        assertTrue(jsonl.isFile());

        store.clearAll();

        assertTrue(store.queryRecent(10).isEmpty());
        assertTrue(!jsonl.exists());
        assertTrue(!new File(jsonl.getParentFile(), jsonl.getName() + ".1").exists());
    }

    @Test
    public void queryRecent_returnsEventsInReverseChronologicalOrder() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));
        store.insertEvent(event("posted", 3000L, "pkg2", "B", ""));
        store.insertEvent(event("posted", 2000L, "pkg3", "C", ""));

        List<LauncherCtlNotificationEvent> events = store.queryRecent(2);

        assertEquals(2, events.size());
        assertEquals("B", events.get(0).title);
        assertEquals("C", events.get(1).title);
    }

    @Test
    public void querySince_filtersByEventTime() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));
        store.insertEvent(event("posted", 3000L, "pkg2", "B", ""));
        store.insertEvent(event("posted", 2000L, "pkg3", "C", ""));

        List<LauncherCtlNotificationEvent> events = store.querySince(1500L, 50);

        assertEquals(2, events.size());
        assertEquals("B", events.get(0).title);
        assertEquals("C", events.get(1).title);
    }

    @Test
    public void querySearch_matchesTitleAndText() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "Hello world", "summary"));
        store.insertEvent(event("posted", 2000L, "pkg2", "Other", "hello again"));
        store.insertEvent(event("posted", 3000L, "pkg3", "No match", "nope"));

        List<LauncherCtlNotificationEvent> events = store.querySearch("hello", 50);

        assertEquals(2, events.size());
        assertEquals("Other", events.get(0).title);
        assertEquals("Hello world", events.get(1).title);
    }

    @Test
    public void querySearch_returnsEmptyForBlankQuery() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));

        assertTrue(store.querySearch("", 50).isEmpty());
        assertTrue(store.querySearch(null, 50).isEmpty());
    }

    @Test
    public void queryStats_returnsCountsAndPackageSummary() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));
        store.insertEvent(event("posted", 2000L, "pkg1", "B", ""));
        store.insertEvent(event("removed", 3000L, "pkg2", "C", ""));

        JSONObject stats = store.queryStats(null);

        assertEquals(3, stats.getLong("total"));
        assertEquals(2, stats.getLong("posted"));
        assertEquals(1, stats.getLong("removed"));
        assertEquals(2, stats.getJSONArray("packages").length());
        assertEquals("pkg1", stats.getJSONArray("packages").getJSONObject(0).getString("packageName"));
        assertEquals(2, stats.getJSONArray("packages").getJSONObject(0).getLong("count"));
    }

    @Test
    public void queryStats_sinceFiltersResults() throws Exception {
        LauncherCtlNotificationStore store = LauncherCtlNotificationStore.getInstance();
        store.insertEvent(event("posted", 1000L, "pkg1", "A", ""));
        store.insertEvent(event("posted", 3000L, "pkg1", "B", ""));

        JSONObject stats = store.queryStats(2000L);

        assertEquals(1, stats.getLong("total"));
        assertEquals(1, stats.getLong("posted"));
        assertEquals(0, stats.getLong("removed"));
        assertEquals(2000L, stats.getLong("since"));
    }

    private static LauncherCtlNotificationEvent event(String eventType, long eventTime, String packageName, String title, String text) throws Exception {
        return new LauncherCtlNotificationEvent(eventType, eventTime, notificationJson(packageName, title, text));
    }

    private static JSONObject notificationJson(String packageName, String title, String text) throws Exception {
        JSONObject n = new JSONObject();
        n.put("key", packageName + ":" + System.nanoTime());
        n.put("packageName", packageName);
        n.put("id", 1);
        n.put("tag", JSONObject.NULL);
        n.put("postTime", 0);
        n.put("isOngoing", false);
        n.put("isClearable", true);
        n.put("category", JSONObject.NULL);
        n.put("title", title);
        n.put("text", text);
        n.put("subText", JSONObject.NULL);
        n.put("bigText", JSONObject.NULL);
        return n;
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
