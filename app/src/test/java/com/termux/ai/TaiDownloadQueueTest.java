package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The queue's rules over persisted records: who starts next, who is an orphan, what fits. */
public class TaiDownloadQueueTest {
    private static final long MB = 1024L * 1024L;
    private static final long GB = 1024L * MB;

    @Test
    public void parallelSettingIsClampedToOneThroughThree() {
        assertEquals(1, TaiDownloadQueue.clampParallel(0));
        assertEquals(1, TaiDownloadQueue.clampParallel(-5));
        assertEquals(2, TaiDownloadQueue.clampParallel(2));
        assertEquals(3, TaiDownloadQueue.clampParallel(3));
        assertEquals(3, TaiDownloadQueue.clampParallel(99));
        assertEquals(2, TaiDownloadQueue.DEFAULT_PARALLEL);
    }

    @Test
    public void queuedRecordsComeFirstInLineFirst_andOtherStatesAreLeftOut() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("c", TaiModelStore.STATE_QUEUED, 300L))
            .put(record("running", TaiModelStore.STATE_DOWNLOADING, 50L))
            .put(record("a", TaiModelStore.STATE_QUEUED, 100L))
            .put(record("paused", TaiModelStore.STATE_PAUSED, 10L))
            .put(record("b", TaiModelStore.STATE_QUEUED, 200L));

        List<JSONObject> queued = TaiDownloadQueue.queuedInOrder(records);

        assertEquals(Arrays.asList("a", "b", "c"), ids(queued));
    }

    @Test
    public void scheduler_startsOnlyAsManyAsTheLimitLeavesFree() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("one", TaiModelStore.STATE_DOWNLOADING, 1L))
            .put(record("two", TaiModelStore.STATE_QUEUED, 2L))
            .put(record("three", TaiModelStore.STATE_QUEUED, 3L))
            .put(record("four", TaiModelStore.STATE_QUEUED, 4L));
        HashSet<String> running = new HashSet<>(Collections.singleton("one"));

        assertEquals(Collections.singletonList("two"), ids(TaiDownloadQueue.nextToStart(records, running, 2)));
        assertEquals(Arrays.asList("two", "three"), ids(TaiDownloadQueue.nextToStart(records, running, 3)));
        assertTrue(TaiDownloadQueue.nextToStart(records, running, 1).isEmpty());
        // A limit change applies to the next decision: with nothing running, 3 fills three slots.
        assertEquals(Arrays.asList("two", "three", "four"), ids(TaiDownloadQueue.nextToStart(records, new HashSet<>(), 3)));
    }

    @Test
    public void scheduler_neverStartsARecordThatAlreadyHasAWorker() throws Exception {
        // A queued record whose worker is being set up (or a stale status) must not get a second one.
        JSONArray records = new JSONArray()
            .put(record("busy", TaiModelStore.STATE_QUEUED, 1L))
            .put(record("next", TaiModelStore.STATE_QUEUED, 2L));

        List<JSONObject> start = TaiDownloadQueue.nextToStart(records, new HashSet<>(Collections.singleton("busy")), 2);

        assertEquals(Collections.singletonList("next"), ids(start));
    }

    @Test
    public void frontOfQueue_isAheadOfEveryQueuedRecord() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("a", TaiModelStore.STATE_QUEUED, 100L))
            .put(record("b", TaiModelStore.STATE_QUEUED, 200L));

        long front = TaiDownloadQueue.frontOfQueue(records);

        assertTrue(front < 100L);
        records.put(record("now", TaiModelStore.STATE_QUEUED, front));
        assertEquals("now", ids(TaiDownloadQueue.queuedInOrder(records)).get(0));
    }

    @Test
    public void oldestRunning_isTheOneThatHasHeldItsSlotLongest() throws Exception {
        JSONArray records = new JSONArray()
            .put(started("late", 500L))
            .put(started("early", 100L))
            .put(started("idle", 1L));
        HashSet<String> running = new HashSet<>(Arrays.asList("late", "early"));

        JSONObject oldest = TaiDownloadQueue.oldestRunning(records, running);

        assertNotNull(oldest);
        assertEquals("early", oldest.getString("id"));
        assertNull(TaiDownloadQueue.oldestRunning(records, new HashSet<>()));
    }

    @Test
    public void orphans_areInFlightRecordsWithNoWorker_andQueuedOnesAreNot() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("dl", TaiModelStore.STATE_DOWNLOADING, 1L))
            .put(record("verify", TaiModelStore.STATE_VERIFYING, 2L))
            .put(record("live", TaiModelStore.STATE_DOWNLOADING, 3L))
            .put(record("queued", TaiModelStore.STATE_QUEUED, 4L))
            .put(record("done", TaiModelStore.STATE_INSTALLED, 5L))
            .put(record("paused", TaiModelStore.STATE_PAUSED, 6L));

        List<JSONObject> orphans = TaiDownloadQueue.orphaned(records, new HashSet<>(Collections.singleton("live")));

        assertEquals(Arrays.asList("dl", "verify"), ids(orphans));
    }

    @Test
    public void autoResume_appliesToAppClosedAndNetworkPauses_notUserOrNoSpace() throws Exception {
        assertTrue(TaiDownloadQueue.autoResumable(paused(TaiModelStore.PAUSED_APP_CLOSED)));
        assertTrue(TaiDownloadQueue.autoResumable(paused(TaiModelStore.PAUSED_NETWORK)));
        assertTrue(TaiDownloadQueue.autoResumable(paused(TaiDownloadQueue.PAUSED_SWAP_OUT)));
        assertFalse(TaiDownloadQueue.autoResumable(paused(TaiModelStore.PAUSED_USER)));
        assertFalse(TaiDownloadQueue.autoResumable(paused(TaiModelStore.PAUSED_NO_SPACE)));
        assertFalse(TaiDownloadQueue.autoResumable(record("q", TaiModelStore.STATE_QUEUED, 1L)));
    }

    @Test
    public void reserve_isFiveHundredMegabytesOrFivePercent_whicheverIsMore() {
        assertEquals(500L * MB, TaiDownloadQueue.reserveBytes(4L * GB));
        assertEquals(500L * MB, TaiDownloadQueue.reserveBytes(0L));
        assertEquals((long) (128L * GB * 0.05), TaiDownloadQueue.reserveBytes(128L * GB));
    }

    @Test
    public void spaceCheck_asksForTheRestOfTheFilePlusTheReserve() {
        long total = 64L * GB; // 5% = 3.2 GB reserve
        long reserve = TaiDownloadQueue.reserveBytes(total);
        // 3.7 GB file, 1 GB already on disk: needs 2.7 GB more, plus the reserve.
        TaiDownloadQueue.SpaceCheck fits = TaiDownloadQueue.checkSpace(reserve + 2_700L * MB, total, 3_700L * MB, 1_000L * MB);
        assertTrue(fits.fits);
        assertEquals(reserve + 2_700L * MB, fits.requiredBytes);

        TaiDownloadQueue.SpaceCheck tight = TaiDownloadQueue.checkSpace(reserve + 2_700L * MB - 1L, total, 3_700L * MB, 1_000L * MB);
        assertFalse(tight.fits);
        assertEquals(reserve + 2_700L * MB, tight.requiredBytes);
        assertEquals(reserve + 2_700L * MB - 1L, tight.freeBytes);

        // Unknown size: only the reserve is asked for.
        assertTrue(TaiDownloadQueue.checkSpace(reserve, total, 0L, 0L).fits);
        assertFalse(TaiDownloadQueue.checkSpace(reserve - 1L, total, -1L, 0L).fits);
    }

    private static JSONObject record(String id, String status, long queuedAtMs) throws Exception {
        return new JSONObject().put("id", id).put("modelId", id).put("status", status).put("queuedAtMs", queuedAtMs);
    }

    private static JSONObject started(String id, long startedAtMs) throws Exception {
        return record(id, TaiModelStore.STATE_DOWNLOADING, 0L).put("startedAtMs", startedAtMs);
    }

    private static JSONObject paused(String reason) throws Exception {
        return record("p", TaiModelStore.STATE_PAUSED, 1L).put("pausedReason", reason);
    }

    private static List<String> ids(List<JSONObject> records) {
        List<String> ids = new java.util.ArrayList<>();
        for (JSONObject record : records) ids.add(record.optString("id"));
        return ids;
    }
}
