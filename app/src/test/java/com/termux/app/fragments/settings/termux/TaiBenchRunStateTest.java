package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiBenchGuardRules;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiModelSpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * The Run screen's reducer, driven by the event sequences the harness and the session produce:
 * the stepper's rows and phases, the live text, the dial, a cool-down, a skipped entry, a stop,
 * an error, a {@code conditions_not_met} refusal, and the leaderboard's ranks.
 */
public class TaiBenchRunStateTest {
    private static final String QWEN = "qwen3-vl-2b";
    private static final String GEMMA = "gemma-4-e2b";

    private TaiBenchRunState state;

    @Before
    public void setUp() {
        state = new TaiBenchRunState();
        state.begin(TaiBenchSuite.Preset.STANDARD.id, Arrays.asList(
            new TaiBenchRunState.Planned(QWEN, "Qwen3-VL 2B", false),
            new TaiBenchRunState.Planned(GEMMA, "Gemma 4 E2B", true)), 18.0, 1_000L);
    }

    // ---- helpers ----

    private static JSONObject event(String name, long at) throws JSONException {
        return new JSONObject().put("event", name).put("at", at);
    }

    private static JSONObject plan(String modelId, String accelerator) throws JSONException {
        return new TaiBenchSuite.EntryPlan(modelId, TaiModelSpec.BACKEND_MNN_LLM, accelerator, false).toJson();
    }

    private void entryStart(int index, int total, String modelId, String accelerator, long at) throws JSONException {
        state.apply(event("entry_start", at).put("index", index).put("total", total).put("entry", plan(modelId, accelerator)));
    }

    private static JSONObject series(double median) throws JSONException {
        return new JSONObject().put("med", median).put("min", median).put("max", median).put("runs", 2);
    }

    private static JSONObject record(String modelId, String accelerator, String status, double decodeTps, boolean checkPassed) throws JSONException {
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3000L).put("memBytes", 100L))
            .put("chat", new JSONObject().put("decodeTps", series(decodeTps)).put("ttftMs", series(600.0)).put("tokens", 231))
            .put("longInput", new JSONObject().put("readMs", series(4_000.0)).put("promptTokens", 2600).put("peakPssBytes", 900L));
        return new JSONObject()
            .put("id", "r-" + modelId + "-" + accelerator)
            .put("modelId", modelId)
            .put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("accelerator", accelerator)
            .put("speculative", false)
            .put("status", status)
            .put("phases", phases)
            .put("check", new JSONObject().put("passed", checkPassed ? 3 : 1).put("total", 3))
            .put("verdict", status.equals("complete") ? (checkPassed ? "smooth" : "broken") : JSONObject.NULL);
    }

    private void runWholeEntry(int index, String modelId, String accelerator, double tps, long at) throws JSONException {
        entryStart(index, 2, modelId, accelerator, at);
        state.apply(event("phase_start", at + 1).put("phase", "load").put("runs", 1));
        state.apply(event("phase_done", at + 2).put("phase", "load").put("status", "ok")
            .put("metrics", new JSONObject().put("ms", 3000L).put("memBytes", 100L)));
        state.apply(event("phase_start", at + 3).put("phase", "chat").put("runs", 2).put("prompt", "Explain an alias."));
        state.apply(event("run_start", at + 3).put("phase", "chat").put("run", 1).put("runs", 2));
        state.apply(event("token", at + 4).put("phase", "chat").put("run", 1).put("runs", 2).put("text", "An ").put("tokens", 2).put("tps", tps).put("ttftMs", 300L));
        state.apply(event("phase_done", at + 5).put("phase", "chat").put("status", "ok")
            .put("metrics", new JSONObject().put("decodeTps", series(tps)).put("ttftMs", series(600.0)).put("tokens", 231)));
        state.apply(event("entry_done", at + 6).put("index", index).put("total", 2).put("record", record(modelId, accelerator, "complete", tps, true)).put("stored", true));
    }

    // ---- stepper ----

    @Test
    public void beginLeavesEveryPlannedModelPending() {
        assertEquals(TaiBenchRunState.Phase.IDLE, state.phase);
        assertEquals(2, state.pending().size());
        assertTrue(state.entries.isEmpty());
        assertEquals("Gemma 4 E2B", state.displayName(GEMMA));
        assertEquals(18.0, state.bestTpsBeforeRun, 1e-9);
    }

    @Test
    public void anEntryStartTakesItsModelOffThePendingRowsAndStartsRunning() throws JSONException {
        entryStart(0, 3, QWEN, "cpu", 2_000L);
        assertEquals(TaiBenchRunState.Phase.RUNNING, state.phase);
        assertEquals(1, state.entries.size());
        TaiBenchRunState.Entry entry = state.current();
        assertNotNull(entry);
        assertEquals("Qwen3-VL 2B", entry.displayName);
        assertEquals("cpu", entry.accelerator);
        assertEquals(QWEN + "|mnn-llm|cpu|off", entry.key);
        assertEquals(TaiBenchRunState.EntryStatus.RUNNING, entry.status);
        // Gemma is still to come; Qwen's second processor will arrive as its own entry.
        assertEquals(1, state.pending().size());
        assertEquals(GEMMA, state.pending().get(0).modelId);
    }

    @Test
    public void phasesMoveThroughTheStepperWithTheirRuns() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "load").put("runs", 1));
        TaiBenchRunState.Entry entry = state.current();
        assertNotNull(entry);
        assertEquals("load", entry.currentPhase);
        assertEquals(TaiBenchRunState.StepStatus.RUNNING, entry.steps.get("load").status);
        assertFalse(state.live.active);
        state.apply(event("phase_done", 2_002L).put("phase", "load").put("status", "ok")
            .put("metrics", new JSONObject().put("ms", 4200L).put("memBytes", 50L)));
        assertEquals(TaiBenchRunState.StepStatus.DONE, entry.steps.get("load").status);
        state.apply(event("phase_start", 2_003L).put("phase", "warmup").put("runs", 1).put("prompt", "Say hello."));
        state.apply(event("phase_done", 2_004L).put("phase", "warmup").put("status", "ok"));
        state.apply(event("phase_start", 2_005L).put("phase", "longInput").put("runs", 2).put("prompt", "What went wrong?"));
        state.apply(event("run_start", 2_005L).put("phase", "longInput").put("run", 2).put("runs", 2));
        assertEquals(2, entry.steps.get("longInput").run);
        assertEquals(2, entry.steps.get("longInput").runs);
        state.apply(event("phase_done", 2_007L).put("phase", "longInput").put("status", "ok")
            .put("metrics", new JSONObject().put("readMs", series(4_000.0)).put("promptTokens", 2600)));
        assertEquals(TaiBenchRunState.StepStatus.DONE, entry.steps.get("longInput").status);
    }

    @Test
    public void aTimedOutPhaseIsMarkedFailedButTheEntryGoesOn() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "chat").put("runs", 2).put("prompt", "p"));
        state.apply(event("error", 2_002L).put("entry", plan(QWEN, "cpu")).put("phase", "chat").put("code", "timeout").put("message", "Stopped after 300 s."));
        state.apply(event("phase_done", 2_003L).put("phase", "chat").put("status", "timeout").put("metrics", new JSONObject().put("runs", 0)));
        TaiBenchRunState.Entry entry = state.current();
        assertNotNull(entry);
        assertEquals(TaiBenchRunState.EntryStatus.RUNNING, entry.status);
        assertEquals(TaiBenchRunState.StepStatus.FAILED, entry.steps.get("chat").status);
        assertEquals("Stopped after 300 s.", entry.reason);
        assertEquals(TaiBenchRunState.Phase.RUNNING, state.phase);
    }

    // ---- live text and the dial ----

    @Test
    public void tokensStreamIntoTheLiveViewAndTheDial() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "chat").put("runs", 2).put("prompt", "Explain an alias."));
        assertTrue(state.live.active);
        assertEquals("Explain an alias.", state.live.prompt);
        assertEquals("chat", state.live.phase);
        assertEquals(TaiBenchRunState.DialKind.NONE, state.dial(2_500L).kind);
        state.apply(event("token", 2_002L).put("phase", "chat").put("run", 1).put("runs", 2).put("text", "An ").put("tokens", 2).put("tps", 20.0).put("ttftMs", 300L));
        state.apply(event("token", 2_003L).put("phase", "chat").put("run", 1).put("runs", 2).put("text", "alias").put("tokens", 3).put("tps", 21.0).put("ttftMs", 300L));
        assertEquals("An alias", state.live.reply.toString());
        assertEquals(3, state.live.tokens);
        assertEquals(21.0, state.live.tps, 1e-9);
        // The dial follows the running figure until the phase's median lands.
        assertEquals(TaiBenchRunState.DialKind.DECODE, state.dial(2_500L).kind);
        assertEquals(21.0, state.dial(2_500L).value, 1e-9);
        // The second run starts the reply over but keeps the prompt.
        state.apply(event("token", 2_004L).put("phase", "chat").put("run", 2).put("runs", 2).put("text", "Water").put("tokens", 1).put("tps", 0.0).put("ttftMs", 280L));
        assertEquals("Water", state.live.reply.toString());
        assertEquals(2, state.live.run);
        assertEquals("Explain an alias.", state.live.prompt);
        state.apply(event("phase_done", 2_005L).put("phase", "chat").put("status", "ok")
            .put("metrics", new JSONObject().put("decodeTps", series(20.5)).put("ttftMs", series(290.0)).put("tokens", 231)));
        assertEquals(20.5, state.dial(2_600L).value, 1e-9);
        assertFalse(state.live.active);
    }

    @Test
    public void aRunStartResetsTheReplyAndStartsTheReadingClock() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "longInput").put("runs", 2).put("prompt", "What went wrong?"));
        state.apply(event("run_start", 2_010L).put("phase", "longInput").put("run", 1).put("runs", 2));
        assertTrue(state.live.active);
        assertEquals(2_010L, state.live.startedAtMs);
        assertEquals("What went wrong?", state.live.prompt);
        token(2_050L, "longInput", 1, "It", 1, 0.0, 40L);
        state.apply(event("run_start", 6_000L).put("phase", "longInput").put("run", 2).put("runs", 2));
        assertEquals("", state.live.reply.toString());
        assertEquals(2, state.live.run);
        assertEquals(6_000L, state.live.startedAtMs);
        assertEquals("What went wrong?", state.live.prompt);
    }

    @Test
    public void theDialCountsSecondsWhileALongPageIsReadThenHoldsHowLongItTook() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "longInput").put("runs", 2).put("prompt", "q"));
        state.apply(event("run_start", 10_000L).put("phase", "longInput").put("run", 1).put("runs", 2));
        TaiBenchRunState.Dial reading = state.dial(13_000L);
        assertEquals(TaiBenchRunState.DialKind.READING, reading.kind);
        assertEquals(3.0, reading.value, 1e-9);
        // The clock is the caller's: later reads count on, and never below zero.
        assertEquals(7.0, state.dial(17_000L).value, 1e-9);
        assertEquals(0.0, state.dial(9_000L).value, 1e-9);
        // The first token of the answer ends the wait: the dial holds the read time.
        token(15_200L, "longInput", 1, "It", 1, 0.0, 5_200L);
        TaiBenchRunState.Dial read = state.dial(20_000L);
        assertEquals(TaiBenchRunState.DialKind.READ, read.kind);
        assertEquals(5.2, read.value, 1e-9);
        // The median of both runs lands with phase_done and is held from then on.
        state.apply(event("phase_done", 30_000L).put("phase", "longInput").put("status", "ok")
            .put("metrics", new JSONObject().put("readMs", series(4_800.0)).put("promptTokens", 2600)));
        assertEquals(TaiBenchRunState.DialKind.READ, state.dial(31_000L).kind);
        assertEquals(4.8, state.dial(31_000L).value, 1e-9);
        // The short sanity replies after it never move it.
        state.apply(event("phase_start", 31_000L).put("phase", "check").put("runs", 3).put("prompt", "p"));
        token(31_100L, "check", 1, "42", 2, 40.0, 150L);
        assertEquals(4.8, state.dial(31_200L).value, 1e-9);
        // The next entry starts the dial over.
        entryStart(1, 2, QWEN, "gpu", 32_000L);
        assertEquals(TaiBenchRunState.DialKind.NONE, state.dial(32_100L).kind);
    }

    private void token(long at, String phase, int run, String text, int tokens, double tps, long ttftMs) throws JSONException {
        state.apply(event("token", at).put("phase", phase).put("run", run).put("runs", 3).put("text", text)
            .put("tokens", tokens).put("tps", tps).put("ttftMs", ttftMs));
    }

    @Test
    public void everyPhaseMovesTheDecodeDialUntilTheChatMedianLands() throws JSONException {
        for (String phase : new String[]{"warmup", "chat", "check"}) {
            entryStart(0, 1, QWEN, "cpu", 2_000L);
            state.apply(event("phase_start", 2_001L).put("phase", phase).put("runs", 1).put("prompt", "p"));
            token(2_002L, phase, 1, "a", 5, 12.5, 420L);
            assertEquals(phase, TaiBenchRunState.DialKind.DECODE, state.dial(3_000L).kind);
            assertEquals(phase, 12.5, state.dial(3_000L).value, 1e-9);
            token(2_003L, phase, 1, "b", 9, 14.0, 420L);
            assertEquals(phase, 14.0, state.dial(3_000L).value, 1e-9);
        }
    }

    @Test
    public void theChatMedianIsHeldAndTheSanityRepliesNeverOverwriteIt() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_004L).put("phase", "chat").put("runs", 2).put("prompt", "p"));
        token(2_005L, "chat", 1, "a", 9, 18.0, 450L);
        state.apply(event("phase_done", 2_006L).put("phase", "chat").put("status", "ok")
            .put("metrics", new JSONObject().put("decodeTps", series(17.5)).put("ttftMs", series(450.0)).put("tokens", 200)));
        state.apply(event("phase_start", 2_007L).put("phase", "check").put("runs", 1).put("prompt", "p"));
        token(2_008L, "check", 1, "42", 2, 40.0, 150L);
        assertEquals(TaiBenchRunState.DialKind.DECODE, state.dial(3_000L).kind);
        assertEquals(17.5, state.dial(3_000L).value, 1e-9);
    }

    @Test
    public void aWarmupWithoutAMedianLeavesTheLastLiveFigureOnTheDial() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "warmup").put("runs", 1));
        token(2_002L, "warmup", 1, "a", 5, 9.0, 800L);
        state.apply(event("phase_done", 2_003L).put("phase", "warmup").put("status", "ok"));
        assertEquals(9.0, state.dial(3_000L).value, 1e-9);
        assertFalse(state.live.active);
    }

    @Test
    public void testNumbersNameTheThreeTests() {
        assertEquals(1, TaiBenchRunState.testNumber("chat"));
        assertEquals(2, TaiBenchRunState.testNumber("longInput"));
        assertEquals(3, TaiBenchRunState.testNumber("check"));
        assertEquals(0, TaiBenchRunState.testNumber("load"));
        assertEquals(0, TaiBenchRunState.testNumber("warmup"));
        assertEquals(0, TaiBenchRunState.testNumber(null));
        assertEquals(3, TaiBenchRunState.TEST_COUNT);
    }

    @Test
    public void aThinkingOnlyTokenEventStillMovesTheDials() throws JSONException {
        // The harness emits these with empty text; the reducer must count them.
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "chat").put("runs", 2).put("prompt", "p"));
        token(2_002L, "chat", 1, "", 40, 31.0, 250L);
        assertEquals(40, state.live.tokens);
        assertEquals(31.0, state.dial(3_000L).value, 1e-9);
        assertEquals("", state.live.reply.toString());
    }

    @Test
    public void phaseDoneKeepsFinishReasonReasoningTokensAndTheLimit() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "chat").put("runs", 2).put("prompt", "p"));
        state.apply(event("phase_done", 2_002L).put("phase", "chat").put("status", "ok")
            .put("finishReason", "length").put("reasoningTokens", 12).put("tokenLimit", 320)
            .put("metrics", new JSONObject().put("decodeTps", series(20.0)).put("tokens", 320)));
        TaiBenchRunState.Step step = state.current().steps.get("chat");
        assertEquals("length", step.finishReason);
        assertEquals(12, step.reasoningTokens);
        assertEquals(320, step.tokenLimit);
        assertTrue(step.hitTokenLimit());
        state.apply(event("phase_start", 2_003L).put("phase", "check").put("runs", 3));
        state.apply(event("phase_done", 2_004L).put("phase", "check").put("status", "ok")
            .put("finishReason", "stop").put("tokenLimit", 32).put("metrics", new JSONObject().put("passed", 3).put("total", 3)));
        assertFalse(state.current().steps.get("check").hitTokenLimit());
    }

    @Test
    public void tokenLimitHitReadsARecordsPhaseAndCheckDetails() throws JSONException {
        assertEquals(320, TaiBenchRunState.tokenLimitHit(new JSONObject().put("finishReason", "length").put("tokenLimit", 320)));
        assertEquals(0, TaiBenchRunState.tokenLimitHit(new JSONObject().put("finishReason", "stop").put("tokenLimit", 128)));
        assertEquals(0, TaiBenchRunState.tokenLimitHit(new JSONObject().put("finishReason", "length")));
        assertEquals(0, TaiBenchRunState.tokenLimitHit((JSONObject) null));
        JSONObject check = new JSONObject().put("details", new JSONArray()
            .put(new JSONObject().put("finishReason", "stop").put("tokenLimit", 32))
            .put(new JSONObject().put("finishReason", "length").put("tokenLimit", 32)));
        assertEquals(32, TaiBenchRunState.checkTokenLimitHit(check));
        assertEquals(0, TaiBenchRunState.checkTokenLimitHit(new JSONObject().put("details", new JSONArray())));
        assertEquals(0, TaiBenchRunState.checkTokenLimitHit(null));
    }

    @Test
    public void checkQuestionsReplaceThePromptPerQuestion() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "check").put("runs", 3));
        state.apply(event("check_start", 2_002L).put("phase", "check").put("run", 2).put("name", "json").put("prompt", "Reply with JSON"));
        assertEquals("Reply with JSON", state.live.prompt);
        assertEquals(2, state.live.run);
        assertEquals(2, state.current().steps.get("check").run);
    }

    @Test
    public void conditionsFillTheHeatBatteryAndRamTiles() throws JSONException {
        state.apply(event("conditions", 2_000L).put("batteryPercent", 64).put("charging", true)
            .put("thermalStatus", "light").put("headroom", 0.42).put("freeRamBytes", 3_000_000_000L));
        assertEquals(64, state.conditions.batteryPercent);
        assertTrue(state.conditions.charging);
        assertEquals("light", state.conditions.thermalStatus);
        assertEquals(0.42, state.conditions.headroom, 1e-9);
        assertEquals(3_000_000_000L, state.conditions.freeRamBytes);
        state.apply(event("conditions", 2_500L).put("batteryPercent", -1).put("charging", false)
            .put("thermalStatus", JSONObject.NULL).put("headroom", JSONObject.NULL).put("freeRamBytes", -1L));
        assertNull(state.conditions.thermalStatus);
        assertTrue(Double.isNaN(state.conditions.headroom));
    }

    // ---- cool-down ----

    @Test
    public void aCooldownIsTimedFromItsFirstPauseAndClearedByTheNextPhase() throws JSONException {
        runWholeEntry(0, QWEN, "cpu", 21.0, 2_000L);
        entryStart(1, 2, QWEN, "gpu", 10_000L);
        state.apply(event("paused", 10_000L).put("phase", "load").put("ms", 2_000L).put("reason", "cooldown")
            .put("thermalStatus", "moderate").put("headroom", 0.9));
        assertEquals(TaiBenchRunState.Phase.WAITING, state.phase);
        TaiBenchRunState.Wait wait = state.wait;
        assertNotNull(wait);
        assertTrue(wait.cooldown());
        assertEquals(10_000L, wait.startedAtMs);
        assertEquals("moderate", wait.thermalStatus);
        assertEquals(0.9, wait.headroom, 1e-9);
        assertEquals(TaiBenchGuardRules.COOLDOWN_CAP_MS, wait.capMs());
        state.apply(event("paused", 12_000L).put("phase", "load").put("ms", 2_000L).put("reason", "cooldown").put("thermalStatus", "light"));
        assertEquals(10_000L, wait.startedAtMs);
        assertEquals(12_000L, wait.lastAtMs);
        assertEquals("light", wait.thermalStatus);
        assertEquals(30_000L, wait.elapsedMs(40_000L));
        assertEquals(TaiBenchGuardRules.COOLDOWN_CAP_MS, wait.elapsedMs(10_000L + TaiBenchGuardRules.COOLDOWN_CAP_MS + 5_000L));
        // The model that just finished is what the cool-down card shows.
        assertNotNull(state.lastFinished);
        assertEquals("cpu", state.lastFinished.accelerator);
        assertEquals(21.0, state.lastFinished.decodeTps, 1e-9);
        // The guard let it through: the load starts and the wait is over.
        state.apply(event("phase_start", 14_000L).put("phase", "load").put("runs", 1));
        assertNull(state.wait);
        assertEquals(TaiBenchRunState.Phase.RUNNING, state.phase);
    }

    @Test
    public void aThermalPauseInsideAnEntryHasTheLongerCapAndNoSkip() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("paused", 5_000L).put("phase", "chat").put("ms", 5_000L).put("reason", "thermal"));
        assertNotNull(state.wait);
        assertFalse(state.wait.cooldown());
        assertEquals(TaiBenchGuardRules.THERMAL_TIMEOUT_MS, state.wait.capMs());
        assertEquals("chat", state.wait.phase);
    }

    @Test
    public void aHeldPauseIsVisibleAsLeftTheScreenAndTheNextPhaseClearsIt() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("paused", 5_000L).put("phase", "chat").put("ms", 1_000L).put("reason", "left"));
        assertEquals(TaiBenchRunState.Phase.WAITING, state.phase);
        TaiBenchRunState.Wait wait = state.wait;
        assertNotNull(wait);
        assertTrue(wait.left());
        assertFalse(wait.cooldown());
        assertEquals(TaiBenchGuardRules.HELD_TIMEOUT_MS, wait.capMs());
        assertEquals("chat", wait.phase);
        // Back on screen: the next phase_start clears the wait.
        state.apply(event("phase_start", 6_000L).put("phase", "chat").put("runs", 1).put("prompt", "Write."));
        assertNull(state.wait);
        assertEquals(TaiBenchRunState.Phase.RUNNING, state.phase);
    }

    // ---- leaderboard, skipped, stop, error ----

    @Test
    public void theLeaderboardRanksFinishedEntriesAndFlagsANewBest() throws JSONException {
        runWholeEntry(0, QWEN, "cpu", 21.0, 2_000L);
        TaiBenchRunState.Entry entry = state.entries.get(0);
        assertEquals(TaiBenchRunState.EntryStatus.DONE, entry.status);
        assertEquals("smooth", entry.verdict);
        assertNotNull(entry.record);
        assertEquals(0, entry.rank);
        state.apply(event("leaderboard", 2_010L).put("ranked", new JSONArray()
            .put(new JSONObject().put("key", entry.key).put("rank", 1).put("decodeTps", 21.0))
            .put(new JSONObject().put("key", "other|mnn-llm|cpu|off").put("rank", 2).put("decodeTps", 18.0))));
        assertEquals(1, entry.rank);
        // 21 beats the 18 the board had before the run.
        assertTrue(entry.newBest);
        runWholeEntry(1, QWEN, "gpu", 15.0, 3_000L);
        TaiBenchRunState.Entry gpu = state.entries.get(1);
        state.apply(event("leaderboard", 3_010L).put("ranked", new JSONArray()
            .put(new JSONObject().put("key", entry.key).put("rank", 1).put("decodeTps", 21.0))
            .put(new JSONObject().put("key", gpu.key).put("rank", 2).put("decodeTps", 15.0))));
        assertEquals(2, gpu.rank);
        assertFalse(gpu.newBest);
        assertTrue(entry.newBest);
    }

    @Test
    public void aFirstEntryThatOnlyTiesTheOldBestIsNotNew() throws JSONException {
        runWholeEntry(0, QWEN, "cpu", 18.0, 2_000L);
        TaiBenchRunState.Entry entry = state.entries.get(0);
        state.apply(event("leaderboard", 2_010L).put("ranked", new JSONArray()
            .put(new JSONObject().put("key", entry.key).put("rank", 1).put("decodeTps", 18.0))));
        assertEquals(1, entry.rank);
        assertFalse(entry.newBest);
    }

    @Test
    public void aSkippedEntryCarriesItsReasonAndLeavesTheRunGoing() throws JSONException {
        entryStart(0, 2, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "load").put("runs", 1));
        state.apply(event("skipped", 2_002L).put("entry", plan(QWEN, "cpu")).put("code", "memory_budget")
            .put("reason", "Not enough free memory for this model."));
        state.apply(event("entry_done", 2_003L).put("index", 0).put("total", 2)
            .put("record", record(QWEN, "cpu", "skipped:memory_budget", 0.0, false).put("skipReason", "Not enough free memory for this model.")));
        TaiBenchRunState.Entry entry = state.entries.get(0);
        assertEquals(TaiBenchRunState.EntryStatus.SKIPPED, entry.status);
        assertEquals("Not enough free memory for this model.", entry.reason);
        assertEquals(TaiBenchRunState.StepStatus.FAILED, entry.steps.get("load").status);
        assertNull(entry.verdict);
        assertEquals(TaiBenchRunState.Phase.RUNNING, state.phase);
        assertNull(state.current());
    }

    @Test
    public void aStopKeepsWhatFinishedAndEndsAsStopped() throws JSONException {
        runWholeEntry(0, QWEN, "cpu", 21.0, 2_000L);
        entryStart(1, 2, QWEN, "gpu", 3_000L);
        state.apply(event("phase_start", 3_001L).put("phase", "chat").put("runs", 2).put("prompt", "p"));
        state.apply(event("stop_requested", 3_002L));
        assertEquals(TaiBenchRunState.Phase.STOPPING, state.phase);
        state.apply(event("error", 3_003L).put("entry", plan(QWEN, "gpu")).put("phase", "chat").put("code", "generation_cancelled").put("message", "Cancelled."));
        state.apply(event("entry_done", 3_004L).put("index", 1).put("total", 2).put("record", record(QWEN, "gpu", "stopped:cancelled", 0.0, false)));
        state.apply(event("done", 3_005L).put("ok", true).put("stopped", "cancelled").put("records", new JSONArray()).put("skipped", new JSONArray()));
        assertEquals(TaiBenchRunState.Phase.STOPPED, state.phase);
        assertEquals("cancelled", state.stopReason);
        assertEquals(3_005L, state.endedAtMs);
        assertEquals(TaiBenchRunState.EntryStatus.DONE, state.entries.get(0).status);
        assertEquals(TaiBenchRunState.EntryStatus.STOPPED, state.entries.get(1).status);
        assertEquals("Cancelled.", state.entries.get(1).reason);
        assertTrue(state.finished());
        assertFalse(state.active());
        // A late session_end changes nothing once the run has ended.
        state.apply(event("session_end", 3_006L).put("reason", "ended"));
        assertEquals(TaiBenchRunState.Phase.STOPPED, state.phase);
        assertEquals("cancelled", state.stopReason);
    }

    @Test
    public void aDoneWithoutAStopReasonIsDone() throws JSONException {
        runWholeEntry(0, QWEN, "cpu", 21.0, 2_000L);
        state.apply(event("done", 2_100L).put("ok", true).put("stopped", JSONObject.NULL));
        assertEquals(TaiBenchRunState.Phase.DONE, state.phase);
        assertNull(state.stopReason);
    }

    @Test
    public void aConditionsRefusalFailsTheRunWithItsReadings() throws JSONException {
        state.apply(event("error", 2_000L).put("code", "conditions_not_met").put("status", 409)
            .put("message", "Battery is below 30%.").put("reason", "battery_low").put("batteryPercent", 22).put("charging", false)
            .put("thermalStatus", 0));
        assertEquals(TaiBenchRunState.Phase.FAILED, state.phase);
        assertEquals("conditions_not_met", state.errorCode);
        assertEquals("Battery is below 30%.", state.errorMessage);
        assertNotNull(state.errorDetail);
        assertEquals("battery_low", state.errorDetail.optString("reason"));
        assertEquals(22, state.errorDetail.optInt("batteryPercent"));
        assertTrue(state.finished());
        state.apply(event("session_end", 2_001L).put("reason", "ended"));
        assertEquals(TaiBenchRunState.Phase.FAILED, state.phase);
    }

    @Test
    public void aStreamThatEndsWithoutDoneReadsAsStopped() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("session_end", 2_001L).put("reason", "ended"));
        assertEquals(TaiBenchRunState.Phase.STOPPED, state.phase);
        assertEquals("ended", state.stopReason);
    }

    @Test
    public void aRuntimeErrorOutsideAnEntryFailsTheRun() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("error", 2_001L).put("code", "tai_runtime_unavailable").put("message", "The runtime died."));
        assertEquals(TaiBenchRunState.Phase.FAILED, state.phase);
        assertEquals("tai_runtime_unavailable", state.stopReason);
    }

    // ---- downloads ----

    @Test
    public void downloadsAreLeadingStepsAndAFailedOneDropsItsModel() throws JSONException {
        state.apply(event("download_start", 1_500L).put("modelId", GEMMA).put("displayName", "Gemma 4 E2B").put("totalBytes", 2_400L));
        assertEquals(TaiBenchRunState.Phase.DOWNLOADING, state.phase);
        assertEquals(1, state.downloads.size());
        state.apply(event("download_progress", 1_600L).put("modelId", GEMMA).put("bytesRead", 1_200L).put("totalBytes", 2_400L));
        assertEquals(1_200L, state.downloads.get(0).bytesRead);
        assertEquals(TaiBenchRunState.StepStatus.RUNNING, state.downloads.get(0).status);
        state.apply(event("download_done", 1_700L).put("modelId", GEMMA).put("ok", false).put("reason", "network lost"));
        assertEquals(TaiBenchRunState.StepStatus.FAILED, state.downloads.get(0).status);
        assertEquals("network lost", state.downloads.get(0).reason);
        // Gemma will never get an entry now; only Qwen is still pending.
        assertEquals(Collections.singletonList(QWEN), Arrays.asList(state.pending().get(0).modelId));
        assertEquals(1, state.pending().size());
        // A finished download fills its bar.
        state.apply(event("download_start", 1_800L).put("modelId", QWEN).put("displayName", "Qwen").put("totalBytes", 1_000L));
        state.apply(event("download_done", 1_900L).put("modelId", QWEN).put("ok", true));
        assertEquals(1_000L, state.downloads.get(1).bytesRead);
        assertEquals(TaiBenchRunState.StepStatus.DONE, state.downloads.get(1).status);
    }

    @Test
    public void aCacheRebuildStartsTheEntryOver() throws JSONException {
        entryStart(0, 1, QWEN, "cpu", 2_000L);
        state.apply(event("phase_start", 2_001L).put("phase", "load").put("runs", 1));
        state.apply(event("phase_done", 2_002L).put("phase", "load").put("status", "ok").put("metrics", new JSONObject().put("ms", 10L).put("memBytes", 1L)));
        state.apply(event("cache_rebuilt", 2_003L).put("entry", plan(QWEN, "cpu")).put("reason", "mnn_check_degenerate"));
        TaiBenchRunState.Entry entry = state.current();
        assertNotNull(entry);
        assertTrue(entry.cacheRebuilt);
        assertTrue(entry.steps.isEmpty());
        assertEquals(TaiBenchRunState.DialKind.NONE, state.dial(3_000L).kind);
    }

    @Test
    public void unknownAndMalformedEventsChangeNothing() throws JSONException {
        long version = state.version;
        state.apply(event("something_else", 1L));
        assertEquals(version, state.version);
        state.apply(event("phase_start", 1L));
        state.apply(event("entry_start", 1L));
        assertTrue(state.entries.isEmpty());
        assertEquals(TaiBenchRunState.Phase.IDLE, state.phase);
    }

    @Test
    public void theStepperListsLoadWarmupChatAndLongInputButNotTheSilentSanityCheck() {
        assertEquals(Arrays.asList("load", "warmup", "chat", "longInput"), TaiBenchRunState.phasesFor());
    }
}
