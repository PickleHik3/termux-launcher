package com.termux.ai;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * How the harness reads a reply's end: the runtime's {@code finishReason}, plus the cap fallback
 * for a backend that always says {@code stop}, folded per phase into {@code phase_done}'s
 * {@code finishReason}, {@code reasoningTokens} and {@code tokenLimit}.
 */
public class TaiBenchHarnessFinishReasonTest {

    private static TaiBenchHarness.Generation reply(String finishReason, int tokens, int limit, boolean ok) {
        TaiBenchHarness.Generation generation = new TaiBenchHarness.Generation();
        generation.finishReason = finishReason;
        generation.callbackTokens = tokens;
        generation.tokenLimit = limit;
        generation.ok = ok;
        return generation;
    }

    @Test
    public void theRuntimesLengthIsKept() {
        assertEquals("length", reply("length", 20, 128, true).effectiveFinishReason());
    }

    @Test
    public void aStopThatUsedTheWholeCapReadsAsLength() {
        // MNN reports stop for everything; the cap is the only tell.
        assertEquals("length", reply("stop", 128, 128, true).effectiveFinishReason());
        assertEquals("length", reply("", 32, 32, true).effectiveFinishReason());
    }

    @Test
    public void aShorterReplyKeepsItsReason() {
        assertEquals("stop", reply("stop", 40, 128, true).effectiveFinishReason());
        assertEquals("stop", reply("", 40, 128, true).effectiveFinishReason());
        assertEquals("tool_calls", reply("tool_calls", 40, 128, true).effectiveFinishReason());
    }

    @Test
    public void aFailedReplyIsNotMistakenForALengthStop() {
        assertEquals("stop", reply("", 128, 128, false).effectiveFinishReason());
    }

    @Test
    public void aPhaseEndsAtTheCapWhenAnyRunDid() throws JSONException {
        TaiBenchHarness.PhaseEnd end = new TaiBenchHarness.PhaseEnd();
        TaiBenchHarness.Generation first = reply("length", 128, 128, true);
        first.reasoningTokens = 7;
        end.add(first);
        TaiBenchHarness.Generation second = reply("stop", 60, 128, true);
        second.reasoningTokens = 3;
        end.add(second);
        JSONObject json = end.into(new JSONObject());
        assertEquals("length", json.getString("finishReason"));
        assertEquals(7, json.getInt("reasoningTokens"));
        assertEquals(128, json.getInt("tokenLimit"));
    }

    @Test
    public void aPhaseThatNeverHitTheCapSaysStop() throws JSONException {
        TaiBenchHarness.PhaseEnd end = new TaiBenchHarness.PhaseEnd();
        end.add(reply("stop", 10, 32, true));
        end.add(reply("stop", 12, 32, true));
        assertEquals("stop", end.into(new JSONObject()).getString("finishReason"));
    }
}
