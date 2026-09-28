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

    /** A complete bench_v1 record with the figures the leaderboard reads. */
    private static JSONObject record(String modelId, String accelerator, boolean speculative,
                                     double writingTps, double firstWordMs, boolean checkPassed) throws Exception {
        return record(modelId, accelerator, speculative, writingTps, firstWordMs, checkPassed,
            TaiBenchSuite.BENCH_VERSION, TaiBenchStore.STATUS_COMPLETE);
    }

    private static JSONObject record(String modelId, String accelerator, boolean speculative,
                                     double writingTps, double firstWordMs, boolean checkPassed,
                                     String benchVersion, String status) throws Exception {
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3200L).put("memBytes", 1_600_000_000L))
            .put("reading", new JSONObject().put("med", 400.0).put("min", 390.0).put("max", 410.0).put("runs", 3))
            .put("firstWord", new JSONObject().put("med", firstWordMs).put("min", firstWordMs).put("max", firstWordMs).put("runs", 3))
            .put("writing", new JSONObject().put("med", writingTps).put("min", writingTps - 1).put("max", writingTps + 1).put("runs", 3).put("tokens", 128))
            .put("sustained", JSONObject.NULL);
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
            .put("status", status)
            .put("verdict", TaiBenchStats.verdict(writingTps, checkPassed));
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
        assertEquals(15.0, records.getJSONObject(0).getJSONObject("phases").getJSONObject("writing").getDouble("med"), 1e-9);
        assertEquals(34.0, records.getJSONObject(19).getJSONObject("phases").getJSONObject("writing").getDouble("med"), 1e-9);
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
    public void leaderboardRanksByWritingSpeedThenFirstWord() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("slow", "cpu", false, 6.0, 500.0, true))
            .put(record("fast", "cpu", false, 21.0, 200.0, true))
            .put(record("fast-late-word", "cpu", false, 21.0, 350.0, true))
            .put(record("mid", "cpu", false, 12.0, 250.0, true));
        JSONObject board = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        JSONArray ranked = board.getJSONArray("ranked");
        assertEquals(4, ranked.length());
        assertEquals("fast", ranked.getJSONObject(0).getString("modelId"));
        assertEquals(1, ranked.getJSONObject(0).getInt("rank"));
        assertEquals("fast-late-word", ranked.getJSONObject(1).getString("modelId"));
        assertEquals("mid", ranked.getJSONObject(2).getString("modelId"));
        assertEquals("slow", ranked.getJSONObject(3).getString("modelId"));
        assertEquals(4, ranked.getJSONObject(3).getInt("rank"));
        assertEquals("smooth", ranked.getJSONObject(0).getString("verdict"));
        assertEquals("usable", ranked.getJSONObject(2).getString("verdict"));
        assertEquals("slow", ranked.getJSONObject(3).getString("verdict"));
        assertEquals(0, board.getJSONArray("broken").length());
    }

    @Test
    public void leaderboardUsesTheLatestCompleteRecordPerEntry() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("qwen", "cpu", false, 30.0, 200.0, true))
            .put(record("qwen", "cpu", false, 18.0, 210.0, true))
            .put(record("qwen", "cpu", false, 50.0, 100.0, true, TaiBenchSuite.BENCH_VERSION, "stopped:battery"))
            .put(record("qwen", "gpu", false, 15.0, 300.0, true));
        JSONObject board = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        JSONArray ranked = board.getJSONArray("ranked");
        assertEquals(2, ranked.length());
        // The stopped run is skipped; the previous complete run of the CPU entry is what ranks.
        assertEquals("cpu", ranked.getJSONObject(0).getString("accelerator"));
        assertEquals(18.0, ranked.getJSONObject(0).getDouble("writingTps"), 1e-9);
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
            .put(record("old", "cpu", false, 99.0, 50.0, true, "bench_v0", TaiBenchStore.STATUS_COMPLETE))
            .put(record("qwen", "cpu", false, 21.0, 200.0, true));
        JSONObject current = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        assertEquals(1, current.getJSONArray("ranked").length());
        assertEquals("qwen", current.getJSONArray("ranked").getJSONObject(0).getString("modelId"));
        JSONObject previous = TaiBenchStore.leaderboard(records, "bench_v0");
        assertEquals(1, previous.getJSONArray("ranked").length());
        assertEquals("old", previous.getJSONArray("ranked").getJSONObject(0).getString("modelId"));
        assertEquals(2, TaiBenchStore.benchVersions(records).size());
    }

    @Test
    public void leaderboardMemoryPrefersTheProcessPssOverMemAvailable() throws Exception {
        JSONObject withPss = record("qwen", "cpu", false, 21.0, 200.0, true);
        withPss.getJSONObject("phases").getJSONObject("load").put("pssBytes", 900_000_000L);
        JSONObject older = record("gemma", "cpu", false, 12.0, 300.0, true);
        JSONObject unreadable = record("smol", "cpu", false, 8.0, 300.0, true);
        unreadable.getJSONObject("phases").getJSONObject("load").put("pssBytes", -1L);
        JSONArray records = new JSONArray().put(withPss).put(older).put(unreadable);
        JSONArray ranked = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION).getJSONArray("ranked");
        assertEquals(900_000_000L, ranked.getJSONObject(0).getLong("memBytes"));
        // A record from before the PSS was measured still reads its MemAvailable figure.
        assertEquals(1_600_000_000L, ranked.getJSONObject(1).getLong("memBytes"));
        // An unreadable PSS falls back the same way.
        assertEquals(1_600_000_000L, ranked.getJSONObject(2).getLong("memBytes"));
        assertEquals(-1L, TaiBenchStore.memoryBytes(new JSONObject().put("ms", 10L)));
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
