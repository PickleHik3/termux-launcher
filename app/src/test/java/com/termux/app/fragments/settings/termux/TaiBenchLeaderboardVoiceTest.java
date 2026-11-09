package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiBenchStore;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiVoiceBench;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** The voice board as Home and Result read it, beside the text one in the same results file. */
public class TaiBenchLeaderboardVoiceTest {
    private static final TaiBenchLeaderboard.Versions NOW = new TaiBenchLeaderboard.Versions("0.2.40", "0.11.0", "3.6.1", "2.3.2");
    private static int nextId = 1;

    private static JSONObject voice(String modelId, String accelerator, String ranOn, double realtime, double wer, long timestamp)
            throws JSONException {
        JSONObject figures = new JSONObject().put("audioMs", 59_000L).put("realtime", realtime).put("wer", wer)
            .put("words", 155).put("errors", (int) Math.round(wer * 155));
        return new JSONObject().put("id", "v" + nextId++).put("kind", TaiVoiceBench.KIND)
            .put("benchVersion", TaiVoiceBench.BENCH_VERSION).put("preset", TaiVoiceBench.PRESET_ID)
            .put("timestamp", timestamp).put("modelId", modelId).put("displayName", modelId).put("engine", "parakeet")
            .put("backend", TaiVoiceBench.BACKEND).put("accelerator", accelerator).put("ranOn", ranOn).put("speculative", false)
            .put("runtimeVersion", "2.3.2").put("appVersion", "0.2.40").put("conditions", new JSONObject())
            .put("phases", new JSONObject().put("load", new JSONObject().put("ms", 1200L)).put("voice", figures))
            .put("status", TaiBenchStore.STATUS_COMPLETE);
    }

    private static JSONObject text(long timestamp) throws JSONException {
        JSONObject chat = new JSONObject().put("decodeTps", new JSONObject().put("med", 14.0).put("min", 14.0).put("max", 14.0).put("runs", 1));
        return new JSONObject().put("id", "t" + nextId++).put("benchVersion", TaiBenchSuite.BENCH_VERSION).put("timestamp", timestamp)
            .put("modelId", "gemma").put("backend", "litert-lm").put("accelerator", "gpu").put("speculative", false)
            .put("phases", new JSONObject().put("chat", chat)).put("check", new JSONObject().put("passed", 3).put("total", 3))
            .put("status", TaiBenchStore.STATUS_COMPLETE);
    }

    private static JSONObject benchmarks(JSONArray records) throws JSONException {
        return new JSONObject().put("benchVersion", TaiBenchSuite.BENCH_VERSION).put("records", records)
            .put("leaderboard", TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION))
            .put("voiceLeaderboard", TaiVoiceBench.leaderboard(records));
    }

    @Test
    public void eachChoiceReadsItsOwnBoardAndCounts() throws Exception {
        JSONArray records = new JSONArray().put(text(10L))
            .put(voice("parakeet", "cpu", "cpu", 15.0, 0.03, 20L))
            .put(voice("parakeet", "gpu", "cpu", 14.0, 0.03, 30L));
        JSONObject json = benchmarks(records);
        TaiBenchLeaderboard.Board textBoard = TaiBenchLeaderboard.read(json, NOW);
        TaiBenchLeaderboard.Board voiceBoard = TaiBenchLeaderboard.read(json, NOW, true);
        assertEquals(1, textBoard.ranked.size());
        assertEquals("gemma", textBoard.ranked.get(0).modelId);
        // Voice records are not "an older benchmark" on the text side.
        assertEquals(0, textBoard.otherVersionRecords);
        assertEquals(10L, textBoard.lastRunMs);
        assertEquals(1, voiceBoard.ranked.size());
        assertEquals(1, voiceBoard.broken.size());
        assertEquals(30L, voiceBoard.lastRunMs);
        assertEquals(0, voiceBoard.otherVersionRecords);
        TaiBenchLeaderboard.Row cpu = voiceBoard.ranked.get(0);
        assertTrue(cpu.voice);
        assertEquals(15.0, cpu.realtime, 1e-9);
        assertEquals(0.03, cpu.wer, 1e-9);
        assertEquals(1200L, cpu.loadMs);
        assertEquals("parakeet", cpu.engine);
        assertFalse(cpu.olderVersion);
        TaiBenchLeaderboard.Row gpu = voiceBoard.broken.get(0);
        assertTrue(gpu.gpuFallback);
        assertEquals("cpu", gpu.ranOn);
    }

    @Test
    public void aVoiceRecordOnAnotherLiteRtIsMarkedOlder() throws Exception {
        JSONObject old = voice("parakeet", "cpu", "cpu", 15.0, 0.03, 20L).put("runtimeVersion", "2.1.0");
        TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks(new JSONArray().put(old)), NOW, true);
        assertTrue(board.ranked.get(0).olderVersion);
    }

    @Test
    public void voiceHistoryChartsSpeedAndSkipsTextRecords() throws Exception {
        JSONArray records = new JSONArray().put(text(5L))
            .put(voice("parakeet", "cpu", "cpu", 12.0, 0.03, 20L))
            .put(voice("parakeet", "cpu", "cpu", 15.0, 0.9, 30L));
        List<TaiBenchLeaderboard.Point> points = TaiBenchLeaderboard.voiceHistory(benchmarks(records), "parakeet|stt|cpu|off");
        assertEquals(2, points.size());
        assertEquals(12.0, points.get(0).decodeTps, 1e-9);
        assertTrue(points.get(0).checkPassed);
        assertFalse(points.get(1).checkPassed);
        assertTrue(TaiBenchLeaderboard.history(benchmarks(records), "parakeet|stt|cpu|off").isEmpty());
    }
}
