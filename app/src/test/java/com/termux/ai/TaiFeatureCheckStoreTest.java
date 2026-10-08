package com.termux.ai;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The feature check's results file: appends, trimming, the latest of each run, and a run the app died in. */
public class TaiFeatureCheckStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private TaiFeatureCheckStore store() {
        return TaiFeatureCheckStore.in(folder.getRoot());
    }

    private static JSONObject record(String accelerator, double speed, long timestamp) throws Exception {
        TaiFeatureCheck.Measurement m = TaiFeatureCheckTest.measurement(accelerator, false, speed);
        m.timestamp = timestamp;
        return TaiFeatureCheck.record(m);
    }

    @Test
    public void appendsAreKeptAndTheLatestOfEachRunIsRead() throws Exception {
        TaiFeatureCheckStore store = store();
        store.append(record("gpu", 8.0, 1L));
        store.append(record("cpu", 5.0, 2L));
        store.append(record("gpu", 9.0, 3L));
        assertEquals(3, store.records().size());
        List<JSONObject> latest = TaiFeatureCheckStore.latest(store.records());
        assertEquals(2, latest.size());
        assertEquals(5.0, latest.get(0).getDouble("speed"), 1e-9);
        assertEquals(9.0, latest.get(1).getDouble("speed"), 1e-9);
    }

    private static JSONObject stopped(String accelerator, long timestamp) throws Exception {
        return record(accelerator, 0.0, timestamp).put("status", "stopped:cancelled");
    }

    @Test
    public void aStoppedRunNeverReplacesACompletedOne() throws Exception {
        List<JSONObject> latest = TaiFeatureCheckStore.latest(java.util.Arrays.asList(
            record("gpu", 8.0, 1L), record("cpu", 5.0, 2L), stopped("gpu", 3L)));
        assertEquals(2, latest.size());
        assertEquals(8.0, latest.get(0).getDouble("speed"), 1e-9);
        assertEquals("complete", latest.get(0).getString("status"));
        assertEquals(5.0, latest.get(1).getDouble("speed"), 1e-9);
    }

    @Test
    public void aStoppedRunIsLatestWhenNothingBetterExists() throws Exception {
        List<JSONObject> latest = TaiFeatureCheckStore.latest(java.util.Arrays.asList(stopped("gpu", 1L), stopped("gpu", 2L)));
        assertEquals(1, latest.size());
        assertEquals(2L, latest.get(0).getLong("timestamp"));
    }

    @Test
    public void aCompletedRunReplacesAStoppedOne() throws Exception {
        List<JSONObject> latest = TaiFeatureCheckStore.latest(java.util.Arrays.asList(stopped("gpu", 1L), record("gpu", 8.0, 2L)));
        assertEquals(1, latest.size());
        assertEquals("complete", latest.get(0).getString("status"));
    }

    @Test
    public void eachRunKeepsOnlyItsNewestRecords() throws Exception {
        List<JSONObject> records = new ArrayList<>();
        for (int i = 0; i < TaiFeatureCheckStore.KEEP_PER_KEY + 2; i++) records.add(record("gpu", i, i));
        records.add(record("cpu", 1.0, 99L));
        List<JSONObject> kept = TaiFeatureCheckStore.trim(records);
        assertEquals(TaiFeatureCheckStore.KEEP_PER_KEY + 1, kept.size());
        // The oldest of the GPU runs went; the newest stayed.
        assertEquals(2.0, kept.get(0).getDouble("speed"), 1e-9);
        assertEquals(1.0, kept.get(kept.size() - 1).getDouble("speed"), 1e-9);
    }

    @Test
    public void aRunTheAppDiedInIsRecordedAsCrashed() throws Exception {
        TaiFeatureCheckStore store = store();
        assertNull(store.recoverStaleMarker("gone"));
        store.markInProgress(record("gpu", 0.0, 5L));
        JSONObject crashed = store.recoverStaleMarker("The app closed during this check.");
        assertNotNull(crashed);
        assertEquals(TaiFeatureCheck.STATUS_CRASHED, crashed.getString("status"));
        assertFalse(crashed.getBoolean("passed"));
        assertEquals(1, store.records().size());
        // The marker is gone: a second recovery finds nothing.
        assertNull(store.recoverStaleMarker("gone"));
        assertFalse(TaiFeatureCheck.resultOf(store.records().get(0), "size:1:mtime:2|litert-lm 0.18.0").passed);
    }

    @Test
    public void aFileThatDoesNotParseReadsAsEmptyAndIsReplaced() throws Exception {
        TaiFeatureCheckStore store = store();
        File file = store.file();
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.write(file.toPath(), "not json".getBytes(StandardCharsets.UTF_8));
        assertTrue(store.records().isEmpty());
        store.append(record("cpu", 4.0, 1L));
        assertEquals(1, store.records().size());
    }
}
