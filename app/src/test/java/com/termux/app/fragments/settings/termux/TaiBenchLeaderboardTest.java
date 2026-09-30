package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiBenchStore;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiModelSpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The Home screen's reading of the benchmarks JSON: the marks on a row, the one ranked list, the
 * broken list, and one entry's history with its version dividers.
 */
public class TaiBenchLeaderboardTest {
    private static final TaiBenchLeaderboard.Versions NOW = new TaiBenchLeaderboard.Versions("0.2.40", "0.9.0", "3.6.1");
    private static int nextId = 1;

    private static JSONObject series(double median) throws JSONException {
        return new JSONObject().put("med", median).put("min", median).put("max", median).put("runs", 1);
    }

    /** A complete v2 record; a {@code ttftMs} or {@code peakBytes} at or below zero is a figure that was not measured. */
    private static JSONObject record(String modelId, String backend, String accelerator, long timestamp, double decodeTps,
                                     double ttftMs, long peakBytes, boolean checkPassed, String appVersion, String runtimeVersion,
                                     JSONObject conditions) throws JSONException {
        JSONObject chat = new JSONObject().put("decodeTps", series(decodeTps)).put("tokens", 231);
        if (ttftMs > 0) chat.put("ttftMs", series(ttftMs));
        JSONObject longInput = new JSONObject().put("readMs", series(4_000.0)).put("promptTokens", 2600).put("truncated", false)
            .put("peakPssBytes", peakBytes > 0 ? peakBytes : -1L);
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3200L).put("memBytes", 1_600_000_000L))
            .put("chat", chat)
            .put("longInput", longInput);
        return new JSONObject()
            .put("id", "r" + (nextId++))
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION)
            .put("preset", "standard")
            .put("timestamp", timestamp)
            .put("modelId", modelId)
            .put("displayName", modelId)
            .put("backend", backend)
            .put("accelerator", accelerator)
            .put("speculative", false)
            .put("runtimeVersion", runtimeVersion)
            .put("appVersion", appVersion)
            .put("conditions", conditions == null ? JSONObject.NULL : conditions)
            .put("phases", phases)
            .put("check", new JSONObject().put("passed", checkPassed ? 3 : 2).put("total", 3))
            .put("status", TaiBenchStore.STATUS_COMPLETE)
            .put("installed", true);
    }

    private static JSONObject conditions(int batteryStart, boolean charging, boolean warmStart) throws JSONException {
        return new JSONObject().put("batteryStart", batteryStart).put("batteryEnd", batteryStart - 3).put("charging", charging)
            .put("thermalStart", "none").put("thermalEnd", "light").put("warmStart", warmStart);
    }

    /** What {@code TaiManager.benchmarks()} answers for these records: the store's leaderboard over them. */
    private static JSONObject benchmarks(JSONObject... records) throws JSONException {
        JSONArray array = new JSONArray();
        for (JSONObject record : records) array.put(record);
        return new JSONObject()
            .put("ok", true)
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION)
            .put("records", array)
            .put("leaderboard", TaiBenchStore.leaderboard(array, TaiBenchSuite.BENCH_VERSION))
            .put("benchVersions", new JSONArray().put(TaiBenchSuite.BENCH_VERSION));
    }

    @Test
    public void rowsCarryTheirMarks() throws JSONException {
        JSONObject charging = record("a", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 21.0, 200.0, 900L, true, "0.2.40", "3.6.1", conditions(80, true, false));
        JSONObject warm = record("b", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1001L, 20.0, 200.0, 900L, true, "0.2.40", "3.6.1", conditions(80, false, true));
        JSONObject lowBattery = record("c", TaiModelSpec.BACKEND_LITERT_LM, "gpu", 1002L, 19.0, 200.0, 900L, true, "0.2.40", "0.9.0", conditions(22, false, false));
        JSONObject olderApp = record("d", TaiModelSpec.BACKEND_LITERT_LM, "cpu", 1003L, 18.0, 200.0, 900L, true, "0.2.39", "0.9.0", conditions(80, false, false));
        JSONObject olderRuntime = record("e", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1004L, 17.0, 200.0, 900L, true, "0.2.40", "3.6.0", conditions(80, false, false));
        JSONObject gone = record("f", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1005L, 16.0, 200.0, 900L, true, "0.2.40", "3.6.1", null).put("installed", false);
        TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks(charging, warm, lowBattery, olderApp, olderRuntime, gone), NOW);
        assertEquals(6, board.ranked.size());
        TaiBenchLeaderboard.Row a = board.ranked.get(0);
        assertTrue(a.charging);
        assertFalse(a.warmStart);
        assertFalse(a.lowBattery);
        assertFalse(a.olderVersion);
        assertTrue(a.installed);
        assertEquals(1, a.rank);
        assertTrue(board.ranked.get(1).warmStart);
        assertTrue(board.ranked.get(2).lowBattery);
        assertTrue(board.ranked.get(3).olderVersion);
        assertTrue(board.ranked.get(4).olderVersion);
        // A LiteRT record is measured against the LiteRT version, not MNN's.
        assertFalse(board.ranked.get(2).olderVersion);
        assertFalse(board.ranked.get(5).installed);
        assertEquals(1005L, board.lastRunMs);
        assertEquals(0, board.otherVersionRecords);
        assertEquals(21.0, board.bestTps(), 1e-9);
    }

    @Test
    public void aRunOnTheChargerIsNotLowBatteryHoweverLowItStarted() throws JSONException {
        JSONObject plugged = record("a", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 21.0, 200.0, 900L, true, "0.2.40", "3.6.1", conditions(12, true, false));
        TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks(plugged), NOW);
        assertTrue(board.ranked.get(0).charging);
        assertFalse(board.ranked.get(0).lowBattery);
    }

    @Test
    public void theListIsOneRankByVerdictThenSpeed() throws JSONException {
        JSONObject fast = record("fast", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 25.0, 500.0, 2_000L, true, "0.2.40", "3.6.1", null);
        JSONObject quickWord = record("quick-word", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1001L, 12.0, 150.0, 3_000L, true, "0.2.40", "3.6.1", null);
        JSONObject usable = record("usable", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1002L, 8.0, 300.0, 1_000L, true, "0.2.40", "3.6.1", null);
        // Quick to write but no first token was measured: it clears no line, so it is Slow and last.
        JSONObject noWord = record("no-word", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1003L, 20.0, 0.0, -1L, true, "0.2.40", "3.6.1", null);
        TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks(fast, quickWord, usable, noWord), NOW);
        assertEquals(Arrays.asList("fast", "quick-word", "usable", "no-word"), ids(board.ranked));
        assertEquals(Arrays.asList(1, 2, 3, 4), Arrays.asList(board.ranked.get(0).rank, board.ranked.get(1).rank,
            board.ranked.get(2).rank, board.ranked.get(3).rank));
        assertEquals("smooth", board.ranked.get(1).verdict);
        assertEquals("usable", board.ranked.get(2).verdict);
        assertEquals("slow", board.ranked.get(3).verdict);
        assertTrue(Double.isNaN(TaiBenchLeaderboard.find(board, "no-word|mnn-llm|cpu|off").ttftMs));
    }

    @Test
    public void aRowCarriesTheThreeNumbersAndTheMemory() throws JSONException {
        JSONObject run = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 14.0, 600.0, 2_100_000_000L, true, "0.2.40", "3.6.1", null);
        TaiBenchLeaderboard.Row row = TaiBenchLeaderboard.read(benchmarks(run), NOW).ranked.get(0);
        assertEquals(14.0, row.decodeTps, 1e-9);
        assertEquals(600.0, row.ttftMs, 1e-9);
        assertEquals(4_000.0, row.readMs, 1e-9);
        assertEquals(2_100_000_000L, row.memBytes);
        assertFalse(row.truncated);
    }

    @Test
    public void brokenRowsAreApartAndOtherVersionsAreCounted() throws JSONException {
        JSONObject good = record("good", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 21.0, 200.0, 900L, true, "0.2.40", "3.6.1", null);
        JSONObject broken = record("broken", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1001L, 40.0, 100.0, 900L, false, "0.2.40", "3.6.1", null);
        JSONObject old = record("old", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 900L, 50.0, 100.0, 900L, true, "0.2.40", "3.6.1", null).put("benchVersion", "bench_v0");
        TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks(good, broken, old), NOW);
        assertEquals(1, board.ranked.size());
        assertEquals(1, board.broken.size());
        assertEquals("broken", board.broken.get(0).modelId);
        assertEquals(0, board.broken.get(0).rank);
        assertEquals("broken", board.broken.get(0).verdict);
        assertEquals(1, board.otherVersionRecords);
        assertFalse(board.empty());
        assertNotNull(TaiBenchLeaderboard.find(board, "broken|mnn-llm|cpu|off"));
        assertNull(TaiBenchLeaderboard.find(board, "old|mnn-llm|cpu|off"));
    }

    @Test
    public void onlyTheVersionCoreDecidesOlder() throws JSONException {
        assertEquals("1.0.0", TaiBenchLeaderboard.versionCore("1.0.0+dev.a1b2c3d"));
        assertEquals("0.2.40", TaiBenchLeaderboard.versionCore("0.2.40-nix"));
        assertEquals("0.2.37", TaiBenchLeaderboard.versionCore("0.2.37-vaj+hotfix1"));
        assertEquals("0.2.35", TaiBenchLeaderboard.versionCore("0.2.35-a"));
        TaiBenchLeaderboard.Versions now = new TaiBenchLeaderboard.Versions("0.2.40+dev.abc1234", "0.9.0", "3.6.1");
        JSONObject nightly = record("m1", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 21.0, 200.0, 900L, true, "0.2.40+dev.def5678", "3.6.1", null);
        JSONObject legacy = record("m2", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1001L, 20.0, 200.0, 900L, true, "0.2.40-nix", "3.6.1", null);
        JSONObject older = record("m3", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1002L, 19.0, 200.0, 900L, true, "0.2.39", "3.6.1", null);
        TaiBenchLeaderboard.Board board = TaiBenchLeaderboard.read(benchmarks(nightly, legacy, older), now);
        assertFalse(TaiBenchLeaderboard.find(board, "m1|mnn-llm|cpu|off").olderVersion);
        assertFalse(TaiBenchLeaderboard.find(board, "m2|mnn-llm|cpu|off").olderVersion);
        assertTrue(TaiBenchLeaderboard.find(board, "m3|mnn-llm|cpu|off").olderVersion);
    }

    @Test
    public void anEmptyOrMissingAnswerIsAnEmptyBoard() throws JSONException {
        assertTrue(TaiBenchLeaderboard.read(null, NOW).empty());
        assertTrue(TaiBenchLeaderboard.read(benchmarks(), NOW).empty());
        assertEquals(0.0, TaiBenchLeaderboard.read(null, NOW).bestTps(), 1e-9);
    }

    @Test
    public void historyIsOneEntrysMeasuredRunsOldestFirstWithDividersAtVersionChanges() throws JSONException {
        String key = "qwen|mnn-llm|cpu|off";
        JSONObject third = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 3000L, 22.0, 200.0, 900L, true, "0.2.40", "3.6.1", null);
        JSONObject first = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 18.0, 200.0, 900L, true, "0.2.39", "3.6.0", null);
        JSONObject second = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 2000L, 19.0, 200.0, 900L, false, "0.2.39", "3.6.0", null);
        JSONObject stopped = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 2500L, 0.0, 0.0, 900L, false, "0.2.39", "3.6.0", null).put("status", "stopped:battery_low");
        JSONObject gpu = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "gpu", 2600L, 15.0, 200.0, 900L, true, "0.2.40", "3.6.1", null);
        JSONObject fourth = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 4000L, 23.0, 200.0, 900L, true, "0.2.40", "3.6.1", null).put("status", "timeout");
        JSONObject benchmarks = benchmarks(third, first, second, stopped, gpu, fourth);
        List<TaiBenchLeaderboard.Point> history = TaiBenchLeaderboard.history(benchmarks, key);
        assertEquals(4, history.size());
        assertEquals(1000L, history.get(0).timestamp);
        assertEquals(18.0, history.get(0).decodeTps, 1e-9);
        assertTrue(history.get(0).checkPassed);
        assertFalse(history.get(1).checkPassed);
        assertEquals(4000L, history.get(3).timestamp);
        assertEquals("timeout", history.get(3).status);
        // The versions changed between the second and third run: one divider, before index 2.
        assertEquals(Arrays.asList(2), TaiBenchLeaderboard.dividers(history));
        // The latest record of the key, and the latest one that measured.
        assertEquals(4000L, TaiBenchLeaderboard.latestRecord(benchmarks, key).optLong("timestamp"));
        assertEquals(4000L, TaiBenchLeaderboard.latestMeasured(benchmarks, key).optLong("timestamp"));
        assertEquals(2500L, TaiBenchLeaderboard.latestRecord(benchmarks(stopped), key).optLong("timestamp"));
        assertNull(TaiBenchLeaderboard.latestMeasured(benchmarks(stopped), key));
    }

    @Test
    public void bestSpeedByModelTakesTheFastestRankedRunPerModelAcrossAccelerators() throws JSONException {
        JSONObject cpuRun = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "cpu", 1000L, 12.4, 200.0, 900L, true, "0.2.40", "3.6.1", null);
        JSONObject gpuRun = record("qwen", TaiModelSpec.BACKEND_MNN_LLM, "gpu", 2000L, 21.0, 200.0, 900L, true, "0.2.40", "3.6.1", null);
        JSONObject other = record("phi", TaiModelSpec.BACKEND_LITERT_LM, "cpu", 3000L, 7.49, 200.0, 900L, true, "0.2.40", "0.9.0", null);
        java.util.Map<String, Double> best = TaiBenchLeaderboard.bestSpeedByModel(benchmarks(cpuRun, gpuRun, other));
        assertEquals(21.0, best.get("qwen"), 1e-9);
        assertEquals(7.49, best.get("phi"), 1e-9);
        assertEquals(2, best.size());
    }

    @Test
    public void bestSpeedByModelIsEmptyWithNoBenchmarksOrNoRankedRows() {
        assertTrue(TaiBenchLeaderboard.bestSpeedByModel(null).isEmpty());
    }

    @Test
    public void formatTpsValueRoundsToAnIntegerAtTenAndOneDecimalBelowIt() {
        assertEquals("21", TaiBenchLeaderboard.formatTpsValue(21.0));
        assertEquals("10", TaiBenchLeaderboard.formatTpsValue(9.96));
        assertEquals("7.5", TaiBenchLeaderboard.formatTpsValue(7.49));
        assertEquals("1", TaiBenchLeaderboard.formatTpsValue(1.0));
        assertEquals("0.3", TaiBenchLeaderboard.formatTpsValue(0.3));
    }

    private static List<String> ids(List<TaiBenchLeaderboard.Row> rows) {
        String[] ids = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) ids[i] = rows.get(i).modelId;
        return Arrays.asList(ids);
    }
}
