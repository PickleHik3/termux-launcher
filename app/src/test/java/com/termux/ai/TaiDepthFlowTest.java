package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public class TaiDepthFlowTest {
    private final List<String> heard = new ArrayList<>();
    private final TaiDepthFlow flow = new TaiDepthFlow(new TaiDepthFlow.Sink() {
        @Override public void onDownloading(int percent) { heard.add("dl" + percent); }
        @Override public void onMaking(int percent) { heard.add("mk" + percent); }
        @Override public void onDone() { heard.add("done"); }
        @Override public void onFailed(String code) { heard.add("fail:" + code); }
    });

    private static TaiDownloadHub.Snapshot snap(String status, long read, long total) throws Exception {
        JSONObject record = new JSONObject().put("id", "download-m").put("modelId", "m").put("status", status)
            .put("bytesRead", read).put("totalBytes", total);
        return new TaiDownloadHub.Snapshot(record, 0.0, -1L, 0);
    }

    @Test
    public void missingModelDownloadsThenMakesThenDone() throws Exception {
        assertEquals(TaiDepthFlow.Action.START_DOWNLOAD, flow.begin(false));
        flow.onDownloadSnapshot(snap("downloading", 25, 100), true);
        flow.onDownloadSnapshot(snap("downloading", 50, 100), true);
        // A repeated figure is not heard twice.
        flow.onDownloadSnapshot(snap("downloading", 50, 100), true);
        assertEquals(TaiDepthFlow.Action.START_MAKE, flow.onDownloadSnapshot(snap("installed", 100, 100), true));
        flow.onMakeStage(0);
        flow.onMakeStage(25);
        flow.onMakeStage(100);
        flow.onMakeResult(true, "ok");
        assertEquals(Arrays.asList("dl0", "dl25", "dl50", "mk0", "mk25", "mk100", "done"), heard);
        assertTrue(flow.finished());
    }

    @Test
    public void presentModelSkipsTheDownload() {
        assertEquals(TaiDepthFlow.Action.START_MAKE, flow.begin(true));
        flow.onMakeStage(25);
        flow.onMakeResult(true, "ok");
        assertEquals(Arrays.asList("mk25", "done"), heard);
    }

    @Test
    public void failedDownloadFailsAndIgnoresWhatFollows() throws Exception {
        flow.begin(false);
        flow.onDownloadSnapshot(snap("failed", 10, 100), true);
        assertEquals(Arrays.asList("dl0", "fail:download_failed"), heard);
        flow.onMakeResult(true, "ok");
        assertEquals(2, heard.size());
    }

    @Test
    public void downloadThatCannotBeQueuedFails() {
        flow.begin(false);
        flow.onDownloadStartFailed();
        assertEquals("fail:download_start_failed", heard.get(heard.size() - 1));
    }

    @Test
    public void aRecordThatVanishesAfterBeingSeenFails() throws Exception {
        flow.begin(false);
        flow.onDownloadSnapshot(null, false);
        assertEquals(1, heard.size());
        flow.onDownloadSnapshot(snap("downloading", 1, 100), true);
        flow.onDownloadSnapshot(null, true);
        assertEquals("fail:download_failed", heard.get(heard.size() - 1));
    }

    @Test
    public void runFailureCarriesItsCode() {
        flow.begin(true);
        flow.onMakeResult(false, DepthMaker.CODE_PHOTO_UNREADABLE);
        assertEquals(Arrays.asList("fail:photo_unreadable"), heard);
    }

    @Test
    public void cancelDuringDownloadStopsTheDownloadAndSilencesEverything() throws Exception {
        flow.begin(false);
        flow.onDownloadSnapshot(snap("downloading", 10, 100), true);
        assertEquals(TaiDepthFlow.Action.CANCEL_DOWNLOAD, flow.cancel());
        flow.onDownloadSnapshot(snap("downloading", 60, 100), true);
        flow.onDownloadSnapshot(snap("installed", 100, 100), true);
        flow.onMakeStage(50);
        flow.onMakeResult(true, "ok");
        assertEquals(Arrays.asList("dl0", "dl10"), heard);
    }

    @Test
    public void cancelDuringTheRunStopsTheRunAndSilencesEverything() {
        flow.begin(true);
        flow.onMakeStage(25);
        assertEquals(TaiDepthFlow.Action.CANCEL_MAKE, flow.cancel());
        flow.onMakeStage(90);
        flow.onMakeResult(false, DepthMaker.CODE_CANCELLED);
        flow.onMakeResult(true, "ok");
        assertEquals(Arrays.asList("mk25"), heard);
    }

    @Test
    public void cancelBeforeBeginStopsTheRequestFromStarting() {
        assertEquals(TaiDepthFlow.Action.NONE, flow.cancel());
        assertEquals(TaiDepthFlow.Action.NONE, flow.begin(true));
        assertTrue(heard.isEmpty());
    }

    @Test
    public void cancelAfterDoneIsANoOp() {
        flow.begin(true);
        flow.onMakeResult(true, "ok");
        assertEquals(TaiDepthFlow.Action.NONE, flow.cancel());
        assertEquals(TaiDepthFlow.Phase.DONE, flow.phase());
    }

    @Test
    public void tooLittleSpaceIsNamed() throws Exception {
        flow.begin(false);
        JSONObject record = new JSONObject().put("id", "download-m").put("modelId", "m").put("status", "failed")
            .put("requiredBytes", 100L).put("freeBytes", 10L);
        flow.onDownloadSnapshot(new TaiDownloadHub.Snapshot(record, 0.0, -1L, 0), true);
        assertEquals("fail:no_space", heard.get(heard.size() - 1));
    }
}
