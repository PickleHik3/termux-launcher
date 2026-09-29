package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The results file: what it keeps, how it is replaced, and what the leaderboard ranks. */
public class TaiBenchStoreTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static int nextId = 1;

    private TaiBenchStore store() throws Exception {
        return new TaiBenchStore(new File(new File(temp.getRoot(), "tai"), TaiBenchStore.FILE_NAME));
    }

    private static JSONObject series(double median) throws Exception {
        return new JSONObject().put("med", median).put("min", median).put("max", median).put("runs", 1);
    }

    /** A complete bench_v2 record with the figures the leaderboard reads; the long page takes 4 s to read. */
    private static JSONObject record(String modelId, String accelerator, boolean speculative,
                                     double decodeTps, double ttftMs, boolean checkPassed) throws Exception {
        return record(modelId, accelerator, speculative, decodeTps, ttftMs, 4_000.0, checkPassed,
            TaiBenchSuite.BENCH_VERSION, TaiBenchStore.STATUS_COMPLETE);
    }

    private static JSONObject record(String modelId, String accelerator, boolean speculative,
                                     double decodeTps, double ttftMs, double readMs, boolean checkPassed,
                                     String benchVersion, String status) throws Exception {
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3200L).put("memBytes", 1_600_000_000L))
            .put("chat", new JSONObject().put("ttftMs", series(ttftMs)).put("decodeTps", series(decodeTps))
                .put("tokens", 231).put("reply", "An alias is a shortcut."))
            .put("longInput", new JSONObject().put("readMs", series(readMs)).put("promptTokens", 2600)
                .put("truncated", false).put("peakPssBytes", 2_100_000_000L));
        return new JSONObject()
            .put("id", "r" + (nextId++))
            .put("benchVersion", benchVersion)
            .put("preset", "standard")
            .put("timestamp", 1_700_000_000_000L + nextId)
            .put("modelId", modelId)
            .put("displayName", modelId)
            .put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("accelerator", accelerator)
            .put("speculative", speculative)
            .put("runtimeVersion", "3.6.1")
            .put("appVersion", "0.2.40")
            .put("phases", phases)
            .put("check", new JSONObject().put("passed", checkPassed ? 3 : 2).put("total", 3))
            .put("status", status);
    }

    /** What bench_v1 wrote: writing, first word and reading phases, ranked by the writing median. */
    private static JSONObject v1Record(String modelId, double writingTps) throws Exception {
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3200L).put("memBytes", 1_600_000_000L).put("pssBytes", 900_000_000L))
            .put("reading", new JSONObject().put("med", 400.0).put("runs", 3))
            .put("firstWord", new JSONObject().put("med", 200.0).put("runs", 3))
            .put("writing", new JSONObject().put("med", writingTps).put("runs", 3).put("tokens", 128))
            .put("sustained", JSONObject.NULL);
        return new JSONObject()
            .put("id", "v1-" + (nextId++))
            .put("benchVersion", "bench_v1")
            .put("preset", "standard")
            .put("timestamp", 1_690_000_000_000L)
            .put("modelId", modelId)
            .put("displayName", modelId)
            .put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("accelerator", "cpu")
            .put("speculative", false)
            .put("phases", phases)
            .put("check", new JSONObject().put("passed", 3).put("total", 3))
            .put("status", TaiBenchStore.STATUS_COMPLETE)
            .put("verdict", "smooth");
    }

    private static double decodeMedian(JSONObject record) throws Exception {
        return record.getJSONObject("phases").getJSONObject("chat").getJSONObject("decodeTps").getDouble("med");
    }

    @Test
    public void writesAndReadsBack() throws Exception {
        TaiBenchStore store = store();
        assertEquals(0, store.records().length());
        store.append(record("qwen", "cpu", false, 21.0, 200.0, true));
        JSONArray records = store.records();
        assertEquals(1, records.length());
        assertEquals("qwen", records.getJSONObject(0).getString("modelId"));
        assertTrue(store.file().isFile());
        // A second store over the same file sees the same records: nothing is cached.
        assertEquals(1, store().records().length());
    }

    @Test
    public void replacesTheFileAtomically() throws Exception {
        TaiBenchStore store = store();
        store.append(record("qwen", "cpu", false, 21.0, 200.0, true));
        store.append(record("qwen", "cpu", false, 22.0, 190.0, true));
        File dir = store.file().getParentFile();
        String[] names = dir.list();
        assertEquals(1, names.length);
        assertEquals(TaiBenchStore.FILE_NAME, names[0]);
        String text = new String(Files.readAllBytes(store.file().toPath()), StandardCharsets.UTF_8);
        assertEquals(2, new JSONObject(text).getJSONArray("records").length());
    }

    @Test
    public void aFileThatDoesNotParseStartsOver() throws Exception {
        TaiBenchStore store = store();
        store.file().getParentFile().mkdirs();
        Files.write(store.file().toPath(), "not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, store.records().length());
        store.append(record("qwen", "cpu", false, 21.0, 200.0, true));
        assertEquals(1, store.records().length());
    }

    @Test
    public void keepsTheLastTwentyPerEntry() throws Exception {
        TaiBenchStore store = store();
        for (int i = 0; i < 25; i++) {
            store.append(record("qwen", "cpu", false, 10.0 + i, 200.0, true));
        }
        JSONArray records = store.records();
        assertEquals(TaiBenchStore.KEEP_PER_KEY, records.length());
        // The oldest five went; the newest is still last.
        assertEquals(15.0, decodeMedian(records.getJSONObject(0)), 1e-9);
        assertEquals(34.0, decodeMedian(records.getJSONObject(19)), 1e-9);
    }

    @Test
    public void entriesAreTrimmedSeparately() throws Exception {
        TaiBenchStore store = store();
        for (int i = 0; i < 22; i++) store.append(record("qwen", "cpu", false, 20.0, 200.0, true));
        for (int i = 0; i < 22; i++) store.append(record("qwen", "gpu", false, 15.0, 300.0, true));
        for (int i = 0; i < 3; i++) store.append(record("qwen", "cpu", true, 25.0, 250.0, true));
        JSONArray records = store.records();
        assertEquals(20 + 20 + 3, records.length());
        int cpu = 0, gpu = 0, eagle = 0;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (record.getBoolean("speculative")) eagle++;
            else if ("gpu".equals(record.getString("accelerator"))) gpu++;
            else cpu++;
        }
        assertEquals(20, cpu);
        assertEquals(20, gpu);
        assertEquals(3, eagle);
    }

    @Test
    public void leaderboardRanksByVerdictThenDecodeSpeedThenFirstToken() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("slow", "cpu", false, 4.0, 900.0, true))
            .put(record("usable", "cpu", false, 10.0, 1_000.0, true))
            // Fast writer, but the long page takes 15 s to read: Usable, so it ranks below every Smooth entry.
            .put(record("fast-reader-no", "cpu", false, 30.0, 600.0, 15_000.0, true, TaiBenchSuite.BENCH_VERSION, TaiBenchStore.STATUS_COMPLETE))
            .put(record("smooth-late", "cpu", false, 21.0, 700.0, true))
            .put(record("smooth-early", "cpu", false, 21.0, 400.0, true))
            .put(record("smooth-slower", "cpu", false, 13.0, 300.0, true));
        JSONObject board = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        JSONArray ranked = board.getJSONArray("ranked");
        assertEquals(6, ranked.length());
        String[] order = {"smooth-early", "smooth-late", "smooth-slower", "fast-reader-no", "usable", "slow"};
        String[] verdicts = {"smooth", "smooth", "smooth", "usable", "usable", "slow"};
        for (int i = 0; i < order.length; i++) {
            assertEquals(order[i], ranked.getJSONObject(i).getString("modelId"));
            assertEquals(verdicts[i], ranked.getJSONObject(i).getString("verdict"));
            assertEquals(i + 1, ranked.getJSONObject(i).getInt("rank"));
        }
        assertEquals(0, board.getJSONArray("broken").length());
    }

    @Test
    public void leaderboardRowCarriesTheThreeNumbersAndTheMemory() throws Exception {
        JSONArray records = new JSONArray().put(record("qwen", "cpu", false, 14.0, 600.0, true));
        JSONObject row = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION).getJSONArray("ranked").getJSONObject(0);
        assertEquals(14.0, row.getDouble("decodeTps"), 1e-9);
        assertEquals(600.0, row.getDouble("ttftMs"), 1e-9);
        assertEquals(4_000.0, row.getDouble("readMs"), 1e-9);
        assertEquals(2_100_000_000L, row.getLong("memBytes"));
        assertFalse(row.getBoolean("truncated"));
    }

    @Test
    public void leaderboardUsesTheLatestCompleteRecordPerEntry() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("qwen", "cpu", false, 30.0, 200.0, true))
            .put(record("qwen", "cpu", false, 18.0, 210.0, true))
            .put(record("qwen", "cpu", false, 50.0, 100.0, 1_000.0, true, TaiBenchSuite.BENCH_VERSION, "stopped:battery"))
            .put(record("qwen", "gpu", false, 15.0, 300.0, true));
        JSONObject board = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        JSONArray ranked = board.getJSONArray("ranked");
        assertEquals(2, ranked.length());
        // The stopped run is skipped; the previous complete run of the CPU entry is what ranks.
        assertEquals("cpu", ranked.getJSONObject(0).getString("accelerator"));
        assertEquals(18.0, ranked.getJSONObject(0).getDouble("decodeTps"), 1e-9);
        assertEquals("gpu", ranked.getJSONObject(1).getString("accelerator"));
    }

    @Test
    public void brokenEntriesAreReturnedButNotRanked() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("nonsense", "cpu", false, 40.0, 100.0, false))
            .put(record("qwen", "cpu", false, 21.0, 200.0, true));
        JSONObject board = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        JSONArray ranked = board.getJSONArray("ranked");
        assertEquals(1, ranked.length());
        assertEquals("qwen", ranked.getJSONObject(0).getString("modelId"));
        JSONArray broken = board.getJSONArray("broken");
        assertEquals(1, broken.length());
        assertEquals("nonsense", broken.getJSONObject(0).getString("modelId"));
        assertEquals("broken", broken.getJSONObject(0).getString("verdict"));
        assertFalse(broken.getJSONObject(0).getBoolean("checkPassed"));
        assertFalse(broken.getJSONObject(0).has("rank"));
    }

    @Test
    public void benchVersionsAreNeverRankedTogether() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("old", "cpu", false, 99.0, 50.0, 1_000.0, true, "bench_v0", TaiBenchStore.STATUS_COMPLETE))
            .put(record("qwen", "cpu", false, 21.0, 200.0, true));
        JSONObject current = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        assertEquals(1, current.getJSONArray("ranked").length());
        assertEquals("qwen", current.getJSONArray("ranked").getJSONObject(0).getString("modelId"));
        JSONObject previous = TaiBenchStore.leaderboard(records, "bench_v0");
        assertEquals(1, previous.getJSONArray("ranked").length());
        assertEquals("old", previous.getJSONArray("ranked").getJSONObject(0).getString("modelId"));
        assertEquals(2, TaiBenchStore.benchVersions(records).size());
    }

    /** Old files stay readable and stay on file, but a v1 record never reaches the v2 leaderboard. */
    @Test
    public void v1RecordsAreKeptAndReadButNotRanked() throws Exception {
        TaiBenchStore store = store();
        store.append(v1Record("legacy", 30.0));
        store.append(record("qwen", "cpu", false, 21.0, 200.0, true));
        assertEquals(2, store.records().length());
        JSONObject json = store.toJson(TaiBenchSuite.BENCH_VERSION);
        JSONArray ranked = json.getJSONObject("leaderboard").getJSONArray("ranked");
        assertEquals(1, ranked.length());
        assertEquals("qwen", ranked.getJSONObject(0).getString("modelId"));
        assertEquals(0, json.getJSONObject("leaderboard").getJSONArray("broken").length());
        assertEquals(2, json.getJSONArray("benchVersions").length());
        // On its own, a v1 record leaves the board empty: the model shows as untested.
        JSONArray onlyOld = new JSONArray().put(v1Record("legacy", 30.0));
        JSONObject empty = TaiBenchStore.leaderboard(onlyOld, TaiBenchSuite.BENCH_VERSION);
        assertEquals(0, empty.getJSONArray("ranked").length());
        assertEquals(0, empty.getJSONArray("broken").length());
    }

    @Test
    public void leaderboardMemoryPrefersThePeakPssOverMemAvailable() throws Exception {
        JSONObject withPeak = record("qwen", "cpu", false, 21.0, 200.0, true);
        JSONObject noPeak = record("gemma", "cpu", false, 12.0, 300.0, true);
        noPeak.getJSONObject("phases").getJSONObject("longInput").put("peakPssBytes", -1L);
        JSONObject noLongInput = record("smol", "cpu", false, 8.0, 300.0, true);
        noLongInput.getJSONObject("phases").put("longInput", JSONObject.NULL);
        JSONArray records = new JSONArray().put(withPeak).put(noPeak).put(noLongInput);
        JSONObject board = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        JSONArray ranked = board.getJSONArray("ranked");
        assertEquals("qwen", ranked.getJSONObject(0).getString("modelId"));
        assertEquals(2_100_000_000L, ranked.getJSONObject(0).getLong("memBytes"));
        // No peak sampled: the MemAvailable difference of the load is the fallback.
        assertEquals(1_600_000_000L, ranked.getJSONObject(1).getLong("memBytes"));
        assertEquals(1_600_000_000L, ranked.getJSONObject(2).getLong("memBytes"));
        assertEquals(-1L, TaiBenchStore.memoryBytes(new JSONObject()));
        assertEquals(-1L, TaiBenchStore.memoryBytes(null));
    }

    @Test
    public void clearRemovesEverythingOrOneModel() throws Exception {
        TaiBenchStore store = store();
        store.append(record("qwen", "cpu", false, 21.0, 200.0, true));
        store.append(record("gemma", "gpu", false, 12.0, 400.0, true));
        assertEquals(1, store.clear("qwen"));
        assertEquals(1, store.records().length());
        assertEquals("gemma", store.records().getJSONObject(0).getString("modelId"));
        assertEquals(0, store.clear("nobody"));
        assertEquals(1, store.clear(null));
        assertEquals(0, store.records().length());
    }

    @Test
    public void toJsonCarriesRecordsLeaderboardAndVersions() throws Exception {
        TaiBenchStore store = store();
        store.append(record("qwen", "cpu", false, 21.0, 200.0, true));
        JSONObject json = store.toJson(TaiBenchSuite.BENCH_VERSION);
        assertTrue(json.getBoolean("ok"));
        assertEquals(1, json.getJSONArray("records").length());
        assertEquals(1, json.getJSONObject("leaderboard").getJSONArray("ranked").length());
        assertEquals(TaiBenchSuite.BENCH_VERSION, json.getJSONArray("benchVersions").getString(0));
    }
}
