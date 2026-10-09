package com.termux.ai;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/** The observable the model centre subscribes to: main-thread delivery, throttled per item. */
@RunWith(RobolectricTestRunner.class)
public class TaiDownloadHubTest {
    private Context context;
    private TaiModelStore store;
    private TaiDownloadHub hub;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        TaiDownloadHub.resetForTesting();
        store = new TaiModelStore(context);
        hub = TaiDownloadHub.get(context);
    }

    @Test
    public void addListener_deliversTheStoredRecordsOnTheMainThread() throws Exception {
        store.upsertDownload(record("download-a", "a", TaiModelStore.STATE_QUEUED, 0L, 100L));
        List<List<TaiDownloadHub.Snapshot>> seen = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        hub.addListener(downloads -> {
            seen.add(downloads);
            threads.add(Thread.currentThread());
        });
        assertTrue("delivery waits for the main looper", seen.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, seen.size());
        assertEquals(Looper.getMainLooper().getThread(), threads.get(0));
        assertEquals("a", seen.get(0).get(0).modelId);
        assertEquals(1, seen.get(0).get(0).queuePosition);
    }

    @Test
    public void publish_overlaysLiveProgressOnTheStoredRecord_andThrottlesToFiveHertz() throws Exception {
        store.upsertDownload(record("download-a", "a", TaiModelStore.STATE_DOWNLOADING, 0L, 100L));
        List<List<TaiDownloadHub.Snapshot>> seen = new ArrayList<>();
        hub.addListener(seen::add);
        shadowOf(Looper.getMainLooper()).idle();
        seen.clear();

        hub.publish(record("download-a", "a", TaiModelStore.STATE_DOWNLOADING, 10L, 100L));
        hub.publish(record("download-a", "a", TaiModelStore.STATE_DOWNLOADING, 20L, 100L));
        hub.publish(record("download-a", "a", TaiModelStore.STATE_DOWNLOADING, 30L, 100L));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("three publishes inside 200 ms are one emission", 1, seen.size());
        assertEquals("with the newest bytes", 30L, seen.get(0).get(0).bytesRead);
        assertEquals("preferences still say 0 until the worker's next 1 MB write",
            0L, store.getDownload("download-a").getLong("bytesRead"));

        // The coalesced one lands once the interval has passed.
        hub.publish(record("download-a", "a", TaiModelStore.STATE_DOWNLOADING, 40L, 100L));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TaiDownloadHub.MIN_EMIT_INTERVAL_MS + 10L));
        assertEquals(2, seen.size());
        assertEquals(40L, seen.get(1).get(0).bytesRead);
    }

    @Test
    public void aStatusChange_goesOutAtOnce() throws Exception {
        store.upsertDownload(record("download-a", "a", TaiModelStore.STATE_DOWNLOADING, 0L, 100L));
        List<List<TaiDownloadHub.Snapshot>> seen = new ArrayList<>();
        hub.addListener(seen::add);
        shadowOf(Looper.getMainLooper()).idle();
        seen.clear();

        hub.publish(record("download-a", "a", TaiModelStore.STATE_DOWNLOADING, 10L, 100L));
        shadowOf(Looper.getMainLooper()).idle();
        JSONObject paused = record("download-a", "a", TaiModelStore.STATE_PAUSED, 10L, 100L)
            .put("pausedReason", TaiModelStore.PAUSED_NETWORK);
        hub.publish(paused);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(2, seen.size());
        TaiDownloadHub.Snapshot last = seen.get(1).get(0);
        assertEquals(TaiModelStore.STATE_PAUSED, last.status);
        assertEquals(TaiModelStore.PAUSED_NETWORK, last.pausedReason);
        assertTrue(last.isPaused());
        assertEquals("no rate while paused", 0.0, last.bytesPerSecond, 0.0);
    }

    @Test
    public void aRecordDroppedFromTheStore_leavesTheSnapshot() throws Exception {
        store.upsertDownload(record("download-a", "a", TaiModelStore.STATE_INSTALLED, 100L, 100L));
        hub.publish(record("download-a", "a", TaiModelStore.STATE_INSTALLED, 100L, 100L));
        assertEquals(1, hub.snapshot().size());

        store.removeDownload("a");

        assertTrue(hub.snapshot().isEmpty());
    }

    private static JSONObject record(String id, String modelId, String status, long bytesRead, long totalBytes) throws Exception {
        return new JSONObject().put("id", id).put("modelId", modelId).put("displayName", "Model " + modelId)
            .put("status", status).put("bytesRead", bytesRead).put("totalBytes", totalBytes)
            .put("updatedAtMs", System.currentTimeMillis());
    }
}
