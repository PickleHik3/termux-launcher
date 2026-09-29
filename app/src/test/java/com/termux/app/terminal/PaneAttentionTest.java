package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/** Which panes are asking for the user: any cause holds it, focus or clearing drops it. */
public class PaneAttentionTest {

    @Test
    public void aPaneAsksWhileAnyCauseHolds() {
        PaneAttention attention = new PaneAttention();
        attention.set(1, PaneAttention.Cause.BELL, true);
        attention.set(1, PaneAttention.Cause.BLOCKED, true);
        attention.set(1, PaneAttention.Cause.BELL, false);
        assertTrue("still blocked", attention.isSet(1));
        attention.set(1, PaneAttention.Cause.BLOCKED, false);
        assertFalse(attention.isSet(1));
    }

    @Test
    public void theListenerHearsOnlyTheFlips() {
        PaneAttention attention = new PaneAttention();
        int[] calls = {0};
        attention.setListener(id -> calls[0]++);
        attention.set(2, PaneAttention.Cause.BELL, true);
        attention.set(2, PaneAttention.Cause.PROGRESS_ERROR, true);
        assertEquals("a second cause is not news", 1, calls[0]);
        attention.clear(2);
        assertEquals(2, calls[0]);
        attention.clear(2);
        assertEquals("clearing nothing is silent", 2, calls[0]);
    }

    @Test
    public void retainForgetsDeadPanes() {
        PaneAttention attention = new PaneAttention();
        attention.set(3, PaneAttention.Cause.BELL, true);
        attention.retain(Collections.<Integer>emptySet());
        assertFalse(attention.isSet(3));
    }
}
