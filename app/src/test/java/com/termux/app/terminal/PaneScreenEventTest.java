package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PaneScreenEventTest {

    @Test public void eachKindRunsForItsSpecDurationAndAllStayUnder400Ms() {
        assertEquals(150L, PaneScreenEvent.Kind.BELL_TEAR.durationMs);
        assertEquals(400L, PaneScreenEvent.Kind.POWER_ON.durationMs);
        assertEquals(250L, PaneScreenEvent.Kind.POWER_OFF.durationMs);
        assertEquals(300L, PaneScreenEvent.Kind.FOCUS_WOBBLE.durationMs);
        assertEquals(300L, PaneScreenEvent.Kind.DEATH_SNOW.durationMs);
        for (PaneScreenEvent.Kind k : PaneScreenEvent.Kind.values())
            assertTrue(k.name(), k.durationMs <= 400L);
    }

    @Test public void idsAreWhatTheShaderReads() {
        assertEquals(0, PaneScreenEvent.Kind.NONE.id);
        assertEquals(1, PaneScreenEvent.Kind.BELL_TEAR.id);
        assertEquals(2, PaneScreenEvent.Kind.POWER_ON.id);
        assertEquals(3, PaneScreenEvent.Kind.POWER_OFF.id);
        assertEquals(4, PaneScreenEvent.Kind.FOCUS_WOBBLE.id);
        assertEquals(5, PaneScreenEvent.Kind.DEATH_SNOW.id);
    }

    @Test public void progressRunsZeroToOneAndHolds() {
        PaneScreenEvent e = new PaneScreenEvent(PaneScreenEvent.Kind.FOCUS_WOBBLE, 1000L, 0.5f);
        assertEquals(0f, e.progress(900L), 0f);
        assertEquals(0f, e.progress(1000L), 0f);
        assertEquals(0.5f, e.progress(1150L), 1e-6f);
        assertEquals(1f, e.progress(1300L), 0f);
        assertEquals(1f, e.progress(5000L), 0f);
    }

    @Test public void doneOnlyPastTheEnd() {
        PaneScreenEvent e = new PaneScreenEvent(PaneScreenEvent.Kind.BELL_TEAR, 1000L, 0f);
        assertFalse(e.done(1000L));
        assertFalse(e.done(1149L));
        assertTrue(e.done(1150L));
        assertTrue(new PaneScreenEvent(PaneScreenEvent.Kind.NONE, 0L, 0f).done(0L));
    }

    @Test public void aNewEventReplacesTheLiveOneAndTheSlotEmptiesWhenDone() {
        PaneScreenEvent.Slot slot = new PaneScreenEvent.Slot();
        assertNull(slot.live(0L));
        slot.fire(PaneScreenEvent.Kind.POWER_ON, 1000L, 0.1f);
        PaneScreenEvent first = slot.live(1100L);
        assertNotNull(first);
        assertSame(PaneScreenEvent.Kind.POWER_ON, first.kind);
        slot.fire(PaneScreenEvent.Kind.BELL_TEAR, 1200L, 0.9f);
        PaneScreenEvent second = slot.live(1250L);
        assertNotNull(second);
        assertSame(PaneScreenEvent.Kind.BELL_TEAR, second.kind);
        assertEquals(1200L, second.startUptime);
        // Past the bell's end: gone, even though the power-on it replaced would still be running.
        assertNull(slot.live(1350L));
        assertNull(slot.live(1360L));
        slot.fire(PaneScreenEvent.Kind.DEATH_SNOW, 2000L, 0f);
        slot.fire(PaneScreenEvent.Kind.NONE, 2001L, 0f);
        assertNull(slot.live(2002L));
    }

    @Test public void fireNeedsCrtAnimatorsQuietInputAndTheSwitch() {
        assertTrue(PaneScreenEvent.mayFire(true, true, true, true));
        assertFalse("not CRT (TFT, none)", PaneScreenEvent.mayFire(false, true, true, true));
        assertFalse("Remove animations", PaneScreenEvent.mayFire(true, false, true, true));
        assertFalse("typing or scrolling", PaneScreenEvent.mayFire(true, true, false, true));
        assertFalse("Screen events off", PaneScreenEvent.mayFire(true, true, true, false));
    }

    @Test public void inputIsQuietOnly1500MsAfterTheLastKeyOrScroll() {
        long never = Long.MIN_VALUE / 4;
        assertTrue(InputQuiet.quiet(10_000L, never, never));
        assertFalse(InputQuiet.quiet(10_000L, 9_000L, never));
        assertFalse(InputQuiet.quiet(10_000L, never, 8_600L));
        assertTrue(InputQuiet.quiet(10_000L, 8_500L, never));
        assertTrue(InputQuiet.quiet(10_000L, 8_000L, 8_500L));
        assertFalse(InputQuiet.quiet(10_000L, 8_000L, 9_999L));
    }
}
