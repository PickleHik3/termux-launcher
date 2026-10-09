package com.termux.ai;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The state machine the engine drives over persisted records, without the foreground service (so
 * no worker ever runs): queue, pause, resume, prioritise, cancel and the reconcile on start.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiDownloadEngineTest {
    private Context context;
    private TaiModelStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        TaiDownloadHub.resetForTesting();
        TaiDownloadEngine.resetForTesting();
        store = new TaiModelStore(context);
        setNetwork(false);
    }

    @Test
    public void enqueue_recordsTheRequestAsQueued_andAsksTheServiceToRun() throws Exception {
        TaiDownloadEngine engine = TaiDownloadEngine.getInstance(context);
        shadowOf((Application) context).clearStartedServices();

        JSONObject queued = engine.enqueue(record("a"), null);

        assertEquals(TaiModelStore.STATE_QUEUED, queued.getString("status"));
        assertTrue(queued.getLong("queuedAtMs") > 0L);
        JSONObject stored = store.findDownloadForModel("a");
        assertNotNull(stored);
        assertEquals(TaiModelStore.STATE_QUEUED, stored.getString("status"));
        Intent started = shadowOf((Application) context).getNextStartedService();
        assertNotNull("the queue runs only under the foreground service", started);
        assertEquals(TaiModelDownloadService.ACTION_SYNC, started.getAction());
        assertEquals(TaiModelDownloadService.class.getName(), started.getComponent().getClassName());
        assertTrue("no worker without the service", engine.runningTransferIds().isEmpty());
    }

    @Test
    public void pauseResumePrioritize_moveAQueuedRecordThroughTheLine() throws Exception {
        TaiDownloadEngine engine = TaiDownloadEngine.getInstance(context);
        engine.enqueue(record("a"), null);
        Thread.sleep(2L);
        engine.enqueue(record("b"), null);

        assertTrue(engine.pause("a", TaiModelStore.PAUSED_USER));
        JSONObject paused = store.findDownloadForModel("a");
        assertEquals(TaiModelStore.STATE_PAUSED, paused.getString("status"));
        assertEquals(TaiModelStore.PAUSED_USER, paused.getString("pausedReason"));
        assertFalse("already paused", engine.pause("a", TaiModelStore.PAUSED_USER));

        Thread.sleep(2L);
        assertTrue(engine.resume("a"));
        JSONObject resumed = store.findDownloadForModel("a");
        assertEquals(TaiModelStore.STATE_QUEUED, resumed.getString("status"));
        assertFalse(resumed.has("pausedReason"));
        assertEquals("resume goes to the back of the line", "b", queueOrder().get(0));
        assertFalse("a queued record is not resumable", engine.resume("a"));

        assertTrue(engine.prioritize("a"));
        assertEquals("start now goes to the front", "a", queueOrder().get(0));
        assertEquals("b", queueOrder().get(1));
    }

    @Test
    public void cancelOnARecordWithoutAWorker_deletesThePartialFiles() throws Exception {
        TaiDownloadEngine engine = TaiDownloadEngine.getInstance(context);
        JSONObject record = record("a");
        File output = new File(record.getString("path"));
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        File part = new File(output.getAbsolutePath() + ".part");
        File marker = new File(output.getAbsolutePath() + ".part.source");
        java.nio.file.Files.write(part.toPath(), new byte[] {1, 2, 3});
        java.nio.file.Files.write(marker.toPath(), "url".getBytes());
        engine.enqueue(record, null);
        engine.pause("a", TaiModelStore.PAUSED_USER);

        assertTrue(engine.cancel("a"));

        JSONObject cancelled = store.findDownloadForModel("a");
        assertEquals(TaiModelStore.STATE_CANCELLED, cancelled.getString("status"));
        assertFalse(part.exists());
        assertFalse(marker.exists());
        assertFalse("nothing to cancel twice", engine.cancel("a"));
        assertTrue("a cancelled record can be resumed (from zero, the partial is gone)", engine.resume("a"));
    }

    @Test
    public void actionsOnAnUnknownModel_sayNo() {
        TaiDownloadEngine engine = TaiDownloadEngine.getInstance(context);
        assertFalse(engine.pause("nobody", TaiModelStore.PAUSED_USER));
        assertFalse(engine.resume("nobody"));
        assertFalse(engine.cancel("nobody"));
        assertFalse(engine.prioritize("nobody"));
    }

    @Test
    public void reconcile_marksInterruptedTransfersPausedAppClosed_andLeavesQueuedOnesQueued() throws Exception {
        // The process died with one transfer in flight and one waiting.
        store.upsertDownload(record("dying").put("status", TaiModelStore.STATE_DOWNLOADING).put("bytesRead", 42L)
            .put("currentFile", "tokenizer.json"));
        store.upsertDownload(record("waiting").put("status", TaiModelStore.STATE_QUEUED));
        store.upsertDownload(record("done").put("status", TaiModelStore.STATE_INSTALLED));

        TaiDownloadEngine.getInstance(context);

        JSONObject dying = store.findDownloadForModel("dying");
        assertEquals(TaiModelStore.STATE_PAUSED, dying.getString("status"));
        assertEquals(TaiModelStore.PAUSED_APP_CLOSED, dying.getString("pausedReason"));
        assertEquals("the bytes on disk are kept in the record", 42L, dying.getLong("bytesRead"));
        assertFalse("an attempt-specific field does not outlive the attempt", dying.has("currentFile"));
        assertEquals(TaiModelStore.STATE_QUEUED, store.findDownloadForModel("waiting").getString("status"));
        assertEquals(TaiModelStore.STATE_INSTALLED, store.findDownloadForModel("done").getString("status"));
        // On a metered network the paused one waits for a tap: still paused after the reconcile.
        assertEquals(TaiModelStore.STATE_PAUSED, store.findDownloadForModel("dying").getString("status"));
    }

    @Test
    public void reconcile_onAnUnmeteredNetwork_requeuesWhatTheAppOrTheNetworkInterrupted() throws Exception {
        setNetwork(true);
        store.upsertDownload(record("dying").put("status", TaiModelStore.STATE_DOWNLOADING));
        store.upsertDownload(record("dropped").put("status", TaiModelStore.STATE_PAUSED)
            .put("pausedReason", TaiModelStore.PAUSED_NETWORK));
        store.upsertDownload(record("byhand").put("status", TaiModelStore.STATE_PAUSED)
            .put("pausedReason", TaiModelStore.PAUSED_USER));
        store.upsertDownload(record("full").put("status", TaiModelStore.STATE_PAUSED)
            .put("pausedReason", TaiModelStore.PAUSED_NO_SPACE));
        store.upsertDownload(record("hopeless").put("status", TaiModelStore.STATE_PAUSED)
            .put("pausedReason", TaiModelStore.PAUSED_NETWORK)
            .put("networkRetries", TaiModelDownloader.MAX_NETWORK_RETRIES_WITHOUT_PROGRESS));

        TaiDownloadEngine.getInstance(context);

        assertEquals(TaiModelStore.STATE_QUEUED, store.findDownloadForModel("dying").getString("status"));
        assertEquals(TaiModelStore.STATE_QUEUED, store.findDownloadForModel("dropped").getString("status"));
        assertEquals("a user pause waits for the user", TaiModelStore.STATE_PAUSED, store.findDownloadForModel("byhand").getString("status"));
        assertEquals("a full disk waits for the user", TaiModelStore.STATE_PAUSED, store.findDownloadForModel("full").getString("status"));
        assertEquals("a transfer that never moves a byte stops retrying by itself",
            TaiModelStore.STATE_PAUSED, store.findDownloadForModel("hopeless").getString("status"));
        assertNull(store.findDownloadForModel("dying").opt("pausedReason"));
    }

    @Test
    public void resume_forgetsTheRetryCount_soATapGivesTheTransferItsChancesBack() throws Exception {
        store.upsertDownload(record("hopeless").put("status", TaiModelStore.STATE_PAUSED)
            .put("pausedReason", TaiModelStore.PAUSED_NETWORK)
            .put("networkRetries", TaiModelDownloader.MAX_NETWORK_RETRIES_WITHOUT_PROGRESS));
        TaiDownloadEngine engine = TaiDownloadEngine.getInstance(context);

        assertTrue(engine.resume("hopeless"));

        JSONObject queued = store.findDownloadForModel("hopeless");
        assertEquals(TaiModelStore.STATE_QUEUED, queued.getString("status"));
        assertFalse(queued.has("networkRetries"));
    }

    private List<String> queueOrder() {
        List<String> order = new java.util.ArrayList<>();
        for (JSONObject item : TaiDownloadQueue.queuedInOrder(store.getDownloads())) order.add(item.optString("modelId"));
        return order;
    }

    private JSONObject record(String modelId) throws Exception {
        File output = new File(new File(store.getModelsDirectory(), modelId), "model.litertlm");
        return new JSONObject()
            .put("id", "download-" + modelId)
            .put("modelId", modelId)
            .put("displayName", "Model " + modelId)
            .put("url", "https://example.invalid/" + modelId + "/model.litertlm")
            .put("path", output.getAbsolutePath())
            .put("status", TaiModelStore.STATE_QUEUED)
            .put("bytesRead", 0L)
            .put("totalBytes", 100L)
            .put("error", "")
            .put("updatedAtMs", System.currentTimeMillis());
    }

    /** The active network's capabilities: Wi-Fi at home (unmetered) or mobile data (metered). */
    private void setNetwork(boolean unmetered) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active = manager.getActiveNetwork();
        assertNotNull(active);
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (unmetered) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        shadowOf(manager).setNetworkCapabilities(active, capabilities);
    }
}
