package com.termux.app.terminal;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentStatusTrackerTest {

    private static final String WORKING_SCREEN = "✳ Pondering… (esc to interrupt)\n";
    private static final String BLOCKED_SCREEN = "Do you want to proceed?\n❯ 1. Yes\n  2. No\n";

    /** Counts how often the tracker actually asked for the screen. */
    private static final class CountingScreen implements AgentStatusTracker.ScreenSource {
        String text;
        int reads;

        CountingScreen(String text) {
            this.text = text;
        }

        @Override public String screenTail() {
            reads++;
            return text;
        }
    }

    @Test
    public void screenReadingIsThrottledAndSkippedWhileTheScreenIsUnchanged() {
        AgentStatusTracker tracker = new AgentStatusTracker();
        CountingScreen screen = new CountingScreen(WORKING_SCREEN);

        assertTrue(tracker.observe(10, AgentStatus.AGENT_CLAUDE, true, screen, 1_000L));
        assertEquals(AgentStatus.State.WORKING, tracker.get(10).state);
        assertEquals(AgentStatus.Source.SCREEN, tracker.get(10).source);
        assertEquals(1, screen.reads);

        // Inside the throttle window the screen is not even asked for.
        assertFalse(tracker.observe(10, AgentStatus.AGENT_CLAUDE, true, screen, 1_400L));
        assertEquals(1, screen.reads);

        // Past it, but the same text: read once, no rules run, no change reported.
        assertFalse(tracker.observe(10, AgentStatus.AGENT_CLAUDE, true, screen, 2_000L));
        assertEquals(2, screen.reads);

        screen.text = BLOCKED_SCREEN;
        assertTrue(tracker.observe(10, AgentStatus.AGENT_CLAUDE, false, screen, 3_000L));
        assertEquals(AgentStatus.State.BLOCKED, tracker.get(10).state);
    }

    @Test
    public void hookReportOutranksTheScreenUntilItIsCleared() {
        AgentStatusTracker tracker = new AgentStatusTracker();
        CountingScreen screen = new CountingScreen(BLOCKED_SCREEN);

        assertTrue(tracker.report(11, AgentStatus.AGENT_CLAUDE, AgentStatus.State.WORKING, 500L));
        assertTrue(tracker.isHooked(11));
        assertEquals(AgentStatus.Source.HOOK, tracker.get(11).source);

        // The screen says blocked; the hook says working and the hook wins, unread.
        assertFalse(tracker.observe(11, AgentStatus.AGENT_CLAUDE, false, screen, 5_000L));
        assertEquals(AgentStatus.State.WORKING, tracker.get(11).state);
        assertEquals(0, screen.reads);

        assertTrue(tracker.report(11, AgentStatus.AGENT_CLAUDE, AgentStatus.State.BLOCKED, 6_000L));
        assertEquals(AgentStatus.State.BLOCKED, tracker.get(11).state);

        // clear hands the pane back to the screen rules.
        assertTrue(tracker.report(11, AgentStatus.AGENT_CLAUDE, null, 7_000L));
        assertNull(tracker.get(11));
        assertFalse(tracker.isHooked(11));
        assertTrue(tracker.observe(11, AgentStatus.AGENT_CLAUDE, false, screen, 8_000L));
        assertEquals(AgentStatus.State.BLOCKED, tracker.get(11).state);
        assertEquals(AgentStatus.Source.SCREEN, tracker.get(11).source);
    }

    @Test
    public void theAgentLeavingTheForegroundDropsEvenAHookReport() {
        AgentStatusTracker tracker = new AgentStatusTracker();
        CountingScreen screen = new CountingScreen(WORKING_SCREEN);
        tracker.report(12, AgentStatus.AGENT_CLAUDE, AgentStatus.State.WORKING, 100L);

        assertTrue(tracker.observe(12, null, false, screen, 200L));
        assertNull(tracker.get(12));
        assertFalse(tracker.isHooked(12));
    }

    @Test
    public void anAgentWithNoRulesNeverNeedsTheScreen() {
        AgentStatusTracker tracker = new AgentStatusTracker();
        CountingScreen screen = new CountingScreen(BLOCKED_SCREEN);

        assertTrue(tracker.observe(13, "aider", true, screen, 1_000L));
        assertEquals(AgentStatus.State.WORKING, tracker.get(13).state);
        assertTrue(tracker.observe(13, "aider", false, screen, 3_000L));
        assertEquals(AgentStatus.State.IDLE, tracker.get(13).state);
        assertEquals(0, screen.reads);
    }

    @Test
    public void retainForgetsPanesWhoseShellsAreGone() {
        AgentStatusTracker tracker = new AgentStatusTracker();
        tracker.report(14, AgentStatus.AGENT_CLAUDE, AgentStatus.State.WORKING, 0L);
        tracker.report(15, AgentStatus.AGENT_CODEX, AgentStatus.State.IDLE, 0L);
        tracker.retain(new HashSet<>(Collections.singletonList(14)));
        assertEquals(AgentStatus.State.WORKING, tracker.get(14).state);
        assertNull(tracker.get(15));
    }

    @Test
    public void parseState_acceptsTheWireWordsAndNothingElse() {
        assertEquals(AgentStatus.State.WORKING, AgentStatusTracker.parseState("working"));
        assertEquals(AgentStatus.State.BLOCKED, AgentStatusTracker.parseState(" BLOCKED "));
        assertEquals(AgentStatus.State.IDLE, AgentStatusTracker.parseState("Idle"));
        assertNull(AgentStatusTracker.parseState("clear"));
        assertNull(AgentStatusTracker.parseState(null));
    }
}
