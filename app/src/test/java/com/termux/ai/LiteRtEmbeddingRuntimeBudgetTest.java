package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The pure math behind dawn brief items 1 and 2 (task-prefix text and in-window token budgeting)
 * and the window-routing brief's window selection, all without needing a loaded TFLite interpreter.
 */
public class LiteRtEmbeddingRuntimeBudgetTest {

    @Test
    public void buildPrefix_forQuery_ignoresTitle() {
        assertEquals("task: search result | query: ",
            LiteRtEmbeddingRuntime.buildPrefix(LiteRtEmbeddingRuntime.INPUT_TYPE_QUERY, "My Note"));
        assertEquals("task: search result | query: ",
            LiteRtEmbeddingRuntime.buildPrefix(LiteRtEmbeddingRuntime.INPUT_TYPE_QUERY, null));
    }

    @Test
    public void buildPrefix_forDocumentWithoutTitle_usesNone() {
        assertEquals("title: none | text: ",
            LiteRtEmbeddingRuntime.buildPrefix(LiteRtEmbeddingRuntime.INPUT_TYPE_DOCUMENT, null));
        assertEquals("title: none | text: ",
            LiteRtEmbeddingRuntime.buildPrefix(LiteRtEmbeddingRuntime.INPUT_TYPE_DOCUMENT, "   "));
    }

    @Test
    public void buildPrefix_forDocumentWithTitle_foldsInTheHeading() {
        assertEquals("title: Meeting Notes | text: ",
            LiteRtEmbeddingRuntime.buildPrefix(LiteRtEmbeddingRuntime.INPUT_TYPE_DOCUMENT, "  Meeting Notes  "));
    }

    @Test
    public void computeBudget_whenEverythingFits_reportsNoTruncation() {
        // seq 512: 510 slots for prefix+body once BOS/EOS are reserved.
        LiteRtEmbeddingRuntime.Budget budget = LiteRtEmbeddingRuntime.computeBudget(512, 8, 100);
        assertEquals(8, budget.prefixTokens);
        assertEquals(100, budget.bodyTokens);
        assertEquals(108, budget.reportedTokens);
        assertFalse(budget.truncated);
    }

    @Test
    public void computeBudget_trimsOnlyTheBody_neverThePrefix() {
        // seq 32: 30 slots for prefix+body. An 8-token prefix leaves 22 for a 400-token body.
        LiteRtEmbeddingRuntime.Budget budget = LiteRtEmbeddingRuntime.computeBudget(32, 8, 400);
        assertEquals("the prefix must never be the part that is cut", 8, budget.prefixTokens);
        assertEquals(22, budget.bodyTokens);
        assertTrue(budget.truncated);
        // The reported count is prefix + body before the cut, not the shortened body actually run.
        assertEquals(408, budget.reportedTokens);
    }

    @Test
    public void computeBudget_neverGoesNegativeWhenThePrefixAloneOverflowsTheWindow() {
        // A pathological tiny window: the prefix alone would not fit inside sequenceLength - 2.
        LiteRtEmbeddingRuntime.Budget budget = LiteRtEmbeddingRuntime.computeBudget(4, 8, 50);
        assertEquals(2, budget.prefixTokens); // clamped to framingBudget (sequenceLength - 2)
        assertEquals(0, budget.bodyTokens);
        assertTrue(budget.truncated);
    }

    @Test
    public void computeBudget_exactFit_isNotTruncated() {
        // seq 10: 8 slots for prefix+body; a 3-token prefix plus a 5-token body fits exactly.
        LiteRtEmbeddingRuntime.Budget budget = LiteRtEmbeddingRuntime.computeBudget(10, 3, 5);
        assertEquals(3, budget.prefixTokens);
        assertEquals(5, budget.bodyTokens);
        assertFalse(budget.truncated);
    }

    // ---- window selection (window-routing brief) ----

    @Test
    public void pickWindow_choosesTheSmallestWindowThatFits() {
        assertEquals(256, LiteRtEmbeddingRuntime.pickWindow(Arrays.asList(256, 512, 1024), 100));
        assertEquals(512, LiteRtEmbeddingRuntime.pickWindow(Arrays.asList(256, 512, 1024), 300));
        assertEquals(1024, LiteRtEmbeddingRuntime.pickWindow(Arrays.asList(256, 512, 1024), 600));
    }

    @Test
    public void pickWindow_atExactBoundary_fitsWithoutGoingUpAWindow() {
        // needed == 256 exactly must still route to the 256 window, not the next one up.
        assertEquals(256, LiteRtEmbeddingRuntime.pickWindow(Arrays.asList(256, 512, 1024), 256));
    }

    @Test
    public void pickWindow_whenNothingFits_fallsBackToTheLargestWindow() {
        assertEquals(1024, LiteRtEmbeddingRuntime.pickWindow(Arrays.asList(256, 512, 1024), 5000));
    }

    @Test
    public void pickWindow_withOneGraphInstalled_behavesExactlyAsToday() {
        // A one-graph model has nothing to route between: every input, fitting or not, uses it.
        assertEquals(512, LiteRtEmbeddingRuntime.pickWindow(Collections.singletonList(512), 10));
        assertEquals(512, LiteRtEmbeddingRuntime.pickWindow(Collections.singletonList(512), 5000));
    }
}
