package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The voice benchmark's figures, clips and leaderboard. */
public class TaiVoiceBenchTest {

    @Test
    public void wordsAreLowerCaseWithoutPunctuation() {
        assertEquals(Arrays.asList("and", "so", "my", "fellow", "americans"),
            TaiVoiceBench.words("And so, my fellow Americans."));
        assertEquals(Arrays.asList("our", "nations", "future"), TaiVoiceBench.words("our Nation's  future"));
        assertEquals(Arrays.asList("one", "point", "our"), TaiVoiceBench.words("one point: Our"));
        assertEquals(Collections.emptyList(), TaiVoiceBench.words(null));
        assertEquals(Collections.emptyList(), TaiVoiceBench.words("  ...  "));
    }

    @Test
    public void wordErrorsCountSubstitutionsInsertionsAndDeletions() {
        assertEquals(0, TaiVoiceBench.wordErrors(TaiVoiceBench.words("a b c"), TaiVoiceBench.words("A, b. C!")));
        assertEquals(1, TaiVoiceBench.wordErrors(TaiVoiceBench.words("a b c"), TaiVoiceBench.words("a x c")));
        assertEquals(1, TaiVoiceBench.wordErrors(TaiVoiceBench.words("a b c"), TaiVoiceBench.words("a c")));
        assertEquals(1, TaiVoiceBench.wordErrors(TaiVoiceBench.words("a b c"), TaiVoiceBench.words("a b b c")));
        assertEquals(3, TaiVoiceBench.wordErrors(TaiVoiceBench.words("a b c"), TaiVoiceBench.words("")));
        assertEquals(2, TaiVoiceBench.wordErrors(TaiVoiceBench.words(""), TaiVoiceBench.words("x y")));
    }

    @Test
    public void wordErrorRateIsErrorsOverReferenceWords() {
        String reference = TaiVoiceBench.CLIPS.get(1).reference;
        assertEquals(0.0, TaiVoiceBench.wordErrorRate(reference, "and so my fellow americans ask not what your country "
            + "can do for you ask what you can do for your country"), 1e-9);
        // 22 words; "as not" for "ask not" is one substitution.
        assertEquals(22, TaiVoiceBench.words(reference).size());
        assertEquals(1.0 / 22.0, TaiVoiceBench.wordErrorRate(reference, "And so, my fellow Americans, as not what your "
            + "country can do for you, ask what you can do for your country."), 1e-9);
        assertEquals(1.0, TaiVoiceBench.wordErrorRate(reference, ""), 1e-9);
        assertEquals(0.0, TaiVoiceBench.wordErrorRate("", ""), 1e-9);
    }

    @Test
    public void realtimeIsAudioOverWall() {
        assertEquals(4.0, TaiVoiceBench.realtime(59_000L, 14_750.0), 1e-9);
        assertEquals(0.0, TaiVoiceBench.realtime(59_000L, 0.0), 1e-9);
        assertEquals(0.0, TaiVoiceBench.realtime(0L, 1000.0), 1e-9);
    }

    @Test
    public void bundledClipsAreSixteenKilohertzMonoAtTheirLengths() throws Exception {
        long total = 0L;
        for (TaiVoiceBench.Clip clip : TaiVoiceBench.CLIPS) {
            File file = asset(clip.asset);
            byte[] bytes = Files.readAllBytes(file.toPath());
            // fmt: PCM, 1 channel, 16 kHz, 16-bit.
            assertEquals(clip.id, 1, bytes[20] | bytes[21] << 8);
            assertEquals(clip.id, 1, bytes[22] | bytes[23] << 8);
            assertEquals(clip.id, 16_000, (bytes[24] & 0xff) | (bytes[25] & 0xff) << 8 | (bytes[26] & 0xff) << 16);
            assertEquals(clip.id, 16, bytes[34]);
            long ms = TaiVoiceBench.wavDurationMs(Arrays.copyOf(bytes, 512), bytes.length);
            assertEquals(clip.id, clip.nominalSeconds * 1000.0, ms, 20.0);
            total += ms;
        }
        assertEquals(59_000.0, total, 50.0);
    }

    @Test
    public void wavDurationRefusesWhatIsNotAWav() {
        assertEquals(-1L, TaiVoiceBench.wavDurationMs(new byte[64], 64L));
        assertEquals(-1L, TaiVoiceBench.wavDurationMs("RIFF".getBytes(), 4L));
    }

    @Test
    public void referencesAreTheSpokenText() {
        assertEquals(5, TaiVoiceBench.words(TaiVoiceBench.CLIPS.get(0).reference).size());
        // Up to the pause at 44.8 s, "…step into voting booths throughout the Nation."
        java.util.List<String> bush = TaiVoiceBench.words(TaiVoiceBench.CLIPS.get(2).reference);
        assertEquals("good", bush.get(0));
        assertEquals("nation", bush.get(bush.size() - 1));
        assertEquals(128, bush.size());
    }

    @Test
    public void acceleratorsAreCpuThenGpuWhereOffered() {
        assertEquals(Collections.singletonList("cpu"), TaiVoiceBench.accelerators(false));
        assertEquals(Arrays.asList("cpu", "gpu"), TaiVoiceBench.accelerators(true));
        assertEquals("parakeet", TaiVoiceBench.engineOf(TaiModelCatalog.PARAKEET_TDT_V3_ID, null));
        assertEquals("whisper", TaiVoiceBench.engineOf("whisper-small-en", "/x/whisper_small.tflite"));
        assertTrue(TaiVoiceBench.estimateMs("whisper", true) > TaiVoiceBench.estimateMs("whisper", false));
    }

    @Test
    public void totalsSumMediansAndWords() throws Exception {
        TaiVoiceBench.ClipResult short3 = new TaiVoiceBench.ClipResult(TaiVoiceBench.CLIPS.get(0), 3_000L);
        short3.wallMs.add(1_000.0);
        short3.wallMs.add(500.0);
        short3.wallMs.add(600.0);
        short3.text = "And so my fellow Americans";
        TaiVoiceBench.ClipResult jfk = new TaiVoiceBench.ClipResult(TaiVoiceBench.CLIPS.get(1), 11_000L);
        jfk.wallMs.add(2_400.0);
        jfk.text = "and so my fellow americans as not what your country can do for you ask what you can do for your country";
        TaiVoiceBench.ClipResult untimed = new TaiVoiceBench.ClipResult(TaiVoiceBench.CLIPS.get(2), 45_000L);
        JSONObject totals = TaiVoiceBench.totals(Arrays.asList(short3, jfk, untimed));
        assertEquals(14_000L, totals.getLong("audioMs"));
        assertEquals(3_000L, totals.getLong("wallMs"));
        assertEquals(14_000.0 / 3_000.0, totals.getDouble("realtime"), 1e-9);
        assertEquals(27, totals.getInt("words"));
        assertEquals(1, totals.getInt("errors"));
        assertEquals(2, totals.getInt("clips"));
        assertEquals(null, TaiVoiceBench.totals(Collections.singletonList(untimed)));
    }

    // ---- leaderboard ----

    private static int nextId = 1;

    static JSONObject record(String modelId, String accelerator, String ranOn, double realtime, double wer,
                             String status, long timestamp) throws Exception {
        JSONObject voice = new JSONObject().put("audioMs", 59_000L).put("wallMs", Math.round(59_000 / realtime))
            .put("realtime", realtime).put("words", 140).put("errors", (int) Math.round(wer * 140)).put("wer", wer);
        JSONObject record = new JSONObject()
            .put("id", "v" + nextId++)
            .put("kind", TaiVoiceBench.KIND)
            .put("benchVersion", TaiVoiceBench.BENCH_VERSION)
            .put("modelId", modelId)
            .put("displayName", modelId)
            .put("backend", TaiVoiceBench.BACKEND)
            .put("accelerator", accelerator)
            .put("ranOn", ranOn)
            .put("speculative", false)
            .put("timestamp", timestamp)
            .put("conditions", new JSONObject())
            .put("phases", new JSONObject().put("load", new JSONObject().put("ms", 900L)).put("voice", voice))
            .put("status", status);
        if (!accelerator.equals(ranOn)) record.put("gpuFallback", "gpu failed");
        return record;
    }

    @Test
    public void leaderboardRanksBySpeedAndSetsApartWrongTextAndFallbacks() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("whisper", "cpu", "cpu", 3.4, 0.02, TaiBenchStore.STATUS_COMPLETE, 1L))
            .put(record("parakeet", "cpu", "cpu", 15.0, 0.03, TaiBenchStore.STATUS_COMPLETE, 2L))
            .put(record("parakeet", "gpu", "gpu", 9.0, 0.9, TaiBenchStore.STATUS_COMPLETE, 3L))
            .put(record("whisper", "gpu", "cpu", 3.3, 0.02, TaiBenchStore.STATUS_COMPLETE, 4L))
            .put(record("whisper", "cpu", "cpu", 1.0, 0.02, "stopped:cancelled", 5L));
        JSONObject board = TaiVoiceBench.leaderboard(records);
        JSONArray ranked = board.getJSONArray("ranked");
        assertEquals(2, ranked.length());
        assertEquals("parakeet|stt|cpu|off", ranked.getJSONObject(0).getString("key"));
        assertEquals(1, ranked.getJSONObject(0).getInt("rank"));
        // The stopped run does not replace the complete one.
        assertEquals(3.4, ranked.getJSONObject(1).getDouble("realtime"), 1e-9);
        JSONArray apart = board.getJSONArray("broken");
        assertEquals(2, apart.length());
        assertTrue(apart.getJSONObject(0).getBoolean("wrongText"));
        assertTrue(apart.getJSONObject(1).getBoolean("gpuFallback"));
        assertEquals("cpu", apart.getJSONObject(1).getString("ranOn"));
    }

    @Test
    public void leaderboardIgnoresTextRecordsAndTheTextBoardIgnoresVoice() throws Exception {
        JSONObject text = new JSONObject().put("id", "t1").put("benchVersion", TaiBenchSuite.BENCH_VERSION)
            .put("modelId", "gemma").put("backend", "litert-lm").put("accelerator", "cpu").put("status", "complete")
            .put("phases", new JSONObject()).put("check", new JSONObject().put("passed", 3).put("total", 3));
        JSONArray records = new JSONArray().put(text)
            .put(record("parakeet", "cpu", "cpu", 15.0, 0.03, TaiBenchStore.STATUS_COMPLETE, 2L));
        assertEquals(1, TaiVoiceBench.leaderboard(records).getJSONArray("ranked").length());
        JSONObject textBoard = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);
        assertEquals(1, textBoard.getJSONArray("ranked").length() + textBoard.getJSONArray("broken").length());
        assertEquals("gemma", (textBoard.getJSONArray("ranked").length() > 0 ? textBoard.getJSONArray("ranked")
            : textBoard.getJSONArray("broken")).getJSONObject(0).getString("modelId"));
    }

    @Test
    public void aCrashAfterTheLastCompleteRunListsTheEntryApart() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("parakeet", "gpu", "gpu", 9.0, 0.03, TaiBenchStore.STATUS_COMPLETE, 1L))
            .put(record("parakeet", "gpu", "gpu", 0.0, 0.0, TaiBenchStore.STATUS_CRASHED, 2L));
        JSONObject board = TaiVoiceBench.leaderboard(records);
        assertEquals(1, board.getJSONArray("ranked").length());
        assertEquals(1, board.getJSONArray("broken").length());
        assertEquals(TaiBenchStore.STATUS_CRASHED, board.getJSONArray("broken").getJSONObject(0).getString("status"));
    }

    @Test
    public void isVoiceKnowsItsRecords() throws Exception {
        assertTrue(TaiVoiceBench.isVoice(record("p", "cpu", "cpu", 1.0, 0.0, "complete", 1L)));
        assertFalse(TaiVoiceBench.isVoice(new JSONObject().put("benchVersion", TaiBenchSuite.BENCH_VERSION)));
        assertFalse(TaiVoiceBench.isVoice(null));
    }

    @Test
    public void storeJsonCarriesTheVoiceBoard() throws Exception {
        File dir = Files.createTempDirectory("voice-bench").toFile();
        TaiBenchStore store = TaiBenchStore.in(dir);
        store.append(record("parakeet", "cpu", "cpu", 15.0, 0.03, TaiBenchStore.STATUS_COMPLETE, 2L));
        JSONObject json = store.toJson(TaiBenchSuite.BENCH_VERSION);
        assertEquals(0, json.getJSONObject("leaderboard").getJSONArray("ranked").length());
        assertEquals(1, json.getJSONObject("voiceLeaderboard").getJSONArray("ranked").length());
    }

    @Test
    public void aVoiceMarkerCrashKeepsItsKind() throws Exception {
        JSONObject marker = new JSONObject().put("kind", TaiVoiceBench.KIND).put("benchVersion", TaiVoiceBench.BENCH_VERSION)
            .put("modelId", "parakeet").put("backend", TaiVoiceBench.BACKEND).put("accelerator", "gpu");
        JSONObject crashed = TaiBenchStore.crashedRecord(marker, "gone");
        assertTrue(TaiVoiceBench.isVoice(crashed));
        assertFalse(TaiBenchStore.crashedRecord(new JSONObject().put("modelId", "g"), "gone").has("kind"));
    }

    static File asset(String path) {
        File file = new File("src/main/assets/" + path);
        if (!file.isFile()) file = new File("app/src/main/assets/" + path);
        return file;
    }
}
