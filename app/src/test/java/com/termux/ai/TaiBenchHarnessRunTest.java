package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A whole bench_v2 entry through the harness against a fake host: which phases run and how many
 * times, the record's shape, the Standard median over two runs, the long log fitted to the loaded
 * window, and the peak memory sampled while it is read.
 */
public class TaiBenchHarnessRunTest {
    private static final TaiBenchSuite.EntryPlan ENTRY = new TaiBenchSuite.EntryPlan("fake", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false);

    /** A build log long enough to need cutting in a 2048-token window. */
    private static String longLog() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 400; i++) log.append("> Task :app:step").append(i).append(" UP-TO-DATE\n");
        return log.append("BUILD FAILED in 1m 12s").toString();
    }

    private static final class FakeHost implements TaiBenchHarness.Host {
        final int contextWindow;
        final boolean sane;
        final List<String> prompts = new ArrayList<>();
        final AtomicInteger pssCalls = new AtomicInteger();
        /** The PSS reads in order, then the last one repeats: the peak is 3000 whenever two reads happen. */
        final long[] pssSequence = {2_000L, 3_000L, 1_000L};
        private int chats;

        FakeHost(int contextWindow, boolean sane) {
            this.contextWindow = contextWindow;
            this.sane = sane;
        }

        @NonNull
        @Override
        public JSONObject load(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            return new JSONObject().put("ok", true)
                .put("memoryBudget", new JSONObject().put("contextWindow", contextWindow));
        }

        @NonNull
        @Override
        public JSONObject unload() throws JSONException {
            return new JSONObject().put("ok", true);
        }

        @NonNull
        @Override
        public JSONObject chat(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull String userPrompt, int maxTokens,
                               @NonNull TaiGenerationCallback callback) throws JSONException {
            prompts.add(userPrompt);
            int run = ++chats;
            String reply = "word";
            for (TaiBenchSuite.Check check : TaiBenchSuite.CHECKS) {
                if (check.prompt.equals(userPrompt)) reply = sane ? sanitised(check.name) : "no idea";
            }
            boolean longInput = userPrompt.endsWith(TaiBenchSuite.LONG_INPUT_QUESTION);
            // A wait before the first token (longer on every run, so two runs have a range), then a few
            // tokens a few milliseconds apart; the long page also takes long enough to be sampled.
            sleep(longInput ? 150L : 10L + 30L * run);
            for (int i = 0; i < 5; i++) {
                callback.onToken(i == 0 ? reply : " x");
                sleep(5L);
            }
            callback.onComplete(reply);
            return new JSONObject().put("ok", true).put("finishReason", "stop").put("usageEstimated", false)
                .put("usage", new JSONObject().put("prompt_tokens", 2600).put("completion_tokens", 5));
        }

        private static String sanitised(String check) {
            switch (check) {
                case "arithmetic": return "42";
                case "json": return "{\"color\":\"blue\"}";
                default: return "pineapple";
            }
        }

        private static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void cancel() {
        }

        @NonNull
        @Override
        public TaiLoadMeter startMeter() {
            return TaiLoadMeter.start(null);
        }

        @NonNull
        @Override
        public String longInputLog() throws IOException {
            return longLog();
        }

        @NonNull
        @Override
        public JSONObject describe(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            return new JSONObject().put("displayName", "Fake").put("sizeBytes", 1L).put("runtimeVersion", "1");
        }

        @Override
        public void clearMmapCache(@NonNull TaiBenchSuite.EntryPlan entry) {
        }

        @Override
        public long processPssBytes() {
            int call = pssCalls.getAndIncrement();
            return pssSequence[Math.min(call, pssSequence.length - 1)];
        }
    }

    private static final class Run {
        final List<JSONObject> events = new ArrayList<>();
        JSONObject record;

        int count(String name) {
            int count = 0;
            for (JSONObject event : events) if (name.equals(event.optString("event"))) count++;
            return count;
        }
    }

    private static Run run(FakeHost host, TaiBenchSuite.Preset preset) throws Exception {
        Run run = new Run();
        TaiBenchGuard guard = new TaiBenchGuard() {
            @NonNull
            @Override
            public Decision beforePhase(@NonNull String phase, @NonNull TaiBenchSuite.EntryPlan entry) {
                return Decision.proceed();
            }
        };
        TaiBenchHarness harness = new TaiBenchHarness(preset, Collections.singletonList(ENTRY), host, guard,
            event -> run.events.add(event), "test", new JSONObject());
        harness.run();
        for (JSONObject event : run.events) {
            if ("entry_done".equals(event.optString("event"))) run.record = event.getJSONObject("record");
        }
        assertNotNull(run.record);
        return run;
    }

    @Test
    public void standardRunsEachTestTwiceAndKeepsTheMedianAsTheMeanOfTheTwo() throws Exception {
        Run run = run(new FakeHost(4096, true), TaiBenchSuite.Preset.STANDARD);
        JSONObject record = run.record;
        assertEquals(TaiBenchSuite.BENCH_VERSION, record.getString("benchVersion"));
        assertEquals("complete", record.getString("status"));
        JSONObject phases = record.getJSONObject("phases");
        JSONObject chat = phases.getJSONObject("chat");
        JSONObject ttft = chat.getJSONObject("ttftMs");
        assertEquals(2, ttft.getInt("runs"));
        // The second run waited longer, so the two differ and the median sits halfway.
        assertTrue(ttft.getDouble("max") > ttft.getDouble("min"));
        assertEquals((ttft.getDouble("min") + ttft.getDouble("max")) / 2.0, ttft.getDouble("med"), 1e-9);
        assertEquals(2, chat.getJSONObject("decodeTps").getInt("runs"));
        assertEquals("word x x x x", chat.getString("reply"));
        assertEquals(2, phases.getJSONObject("longInput").getJSONObject("readMs").getInt("runs"));
        assertEquals(2600, phases.getJSONObject("longInput").getInt("promptTokens"));
        // One warm-up, two chats, two long inputs, three sanity questions: one run_start each.
        assertEquals(1 + 2 + 2 + 3, run.count("run_start"));
        // Bench v2 has no reading, first word, writing or sustained phase.
        assertFalse(phases.has("reading"));
        assertFalse(phases.has("writing"));
        assertFalse(phases.has("sustained"));
        assertEquals(3, record.getJSONObject("check").getInt("passed"));
    }

    @Test
    public void quickRunsEachTestOnce() throws Exception {
        Run run = run(new FakeHost(4096, true), TaiBenchSuite.Preset.QUICK);
        JSONObject phases = run.record.getJSONObject("phases");
        assertEquals(1, phases.getJSONObject("chat").getJSONObject("ttftMs").getInt("runs"));
        assertEquals(1, phases.getJSONObject("longInput").getJSONObject("readMs").getInt("runs"));
        assertEquals(1 + 1 + 1 + 3, run.count("run_start"));
    }

    @Test
    public void thePeakMemorySampledWhileTheLongPageIsReadIsKept() throws Exception {
        Run run = run(new FakeHost(4096, true), TaiBenchSuite.Preset.QUICK);
        JSONObject longInput = run.record.getJSONObject("phases").getJSONObject("longInput");
        assertEquals(3_000L, longInput.getLong("peakPssBytes"));
        // Not taken after the warm-up any more: the load records only the MemAvailable difference.
        assertFalse(run.record.getJSONObject("phases").getJSONObject("load").has("pssBytes"));
    }

    @Test
    public void aWindowTooSmallForTheLogCutsItFromTheTopAndSaysSo() throws Exception {
        FakeHost host = new FakeHost(2048, true);
        Run run = run(host, TaiBenchSuite.Preset.QUICK);
        JSONObject longInput = run.record.getJSONObject("phases").getJSONObject("longInput");
        assertTrue(longInput.getBoolean("truncated"));
        assertEquals(2048, longInput.getInt("contextWindow"));
        assertTrue(longInput.getInt("keptChars") < longInput.getInt("totalChars"));
        String sent = null;
        for (String prompt : host.prompts) if (prompt.endsWith(TaiBenchSuite.LONG_INPUT_QUESTION)) sent = prompt;
        assertNotNull(sent);
        assertTrue(sent.contains("BUILD FAILED in 1m 12s"));
        assertFalse(sent.contains("step0 UP-TO-DATE"));
    }

    @Test
    public void aWindowThatHoldsTheLogSendsItWhole() throws Exception {
        FakeHost host = new FakeHost(32_768, true);
        Run run = run(host, TaiBenchSuite.Preset.QUICK);
        JSONObject longInput = run.record.getJSONObject("phases").getJSONObject("longInput");
        assertFalse(longInput.getBoolean("truncated"));
        assertEquals(longInput.getInt("totalChars"), longInput.getInt("keptChars"));
    }

    @Test
    public void aFailedSanityCheckMakesTheVerdictBroken() throws Exception {
        Run run = run(new FakeHost(4096, false), TaiBenchSuite.Preset.QUICK);
        assertEquals("broken", run.record.getString("verdict"));
        assertEquals(0, run.record.getJSONObject("check").getInt("passed"));
    }

    @Test
    public void theLoadedWindowComesFromTheLoadsMemoryBudget() throws Exception {
        assertEquals(4096, TaiBenchHarness.loadedContextWindow(new JSONObject()
            .put("memoryBudget", new JSONObject().put("contextWindow", 4096))));
        assertEquals(0, TaiBenchHarness.loadedContextWindow(new JSONObject()));
        assertEquals(0, TaiBenchHarness.loadedContextWindow(null));
    }
}
