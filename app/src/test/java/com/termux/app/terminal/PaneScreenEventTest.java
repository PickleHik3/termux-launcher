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
        assertEquals(6, PaneScreenEvent.Kind.AMBIENT_TEAR.id);
        assertTrue(PaneScreenEvent.Kind.AMBIENT_TEAR.durationMs <= 200L);
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

    @Test public void fireNeedsCrtAnimatorsAndTheSwitchAndOnlyTheAmbientTearWaitsForQuiet() {
        for (PaneScreenEvent.Kind k : PaneScreenEvent.Kind.values()) {
            if (k == PaneScreenEvent.Kind.NONE) continue;
            assertTrue(k.name(), PaneScreenEvent.mayFire(k, true, true, true, true));
            assertFalse(k + " not CRT (TFT, none)", PaneScreenEvent.mayFire(k, false, true, true, true));
            assertFalse(k + " Remove animations", PaneScreenEvent.mayFire(k, true, false, true, true));
            assertFalse(k + " Screen events off", PaneScreenEvent.mayFire(k, true, true, true, false));
        }
        assertFalse("ambient tear while typing or scrolling",
            PaneScreenEvent.mayFire(PaneScreenEvent.Kind.AMBIENT_TEAR, true, true, false, true));
        // Events the user or their shell caused play at once, even right after a key.
        PaneScreenEvent.Kind[] caused = {PaneScreenEvent.Kind.BELL_TEAR,
            PaneScreenEvent.Kind.POWER_ON, PaneScreenEvent.Kind.POWER_OFF,
            PaneScreenEvent.Kind.FOCUS_WOBBLE, PaneScreenEvent.Kind.DEATH_SNOW};
        for (PaneScreenEvent.Kind k : caused) {
            assertFalse(k.name(), k.waitsForQuiet);
            assertTrue(k + " while typing", PaneScreenEvent.mayFire(k, true, true, false, true));
        }
        assertTrue(PaneScreenEvent.Kind.AMBIENT_TEAR.waitsForQuiet);
    }

    @Test public void theSameKindWhileLiveIsDroppedSoABellFloodRestartsNothing() {
        PaneScreenEvent.Slot slot = new PaneScreenEvent.Slot();
        assertTrue(slot.fire(PaneScreenEvent.Kind.BELL_TEAR, 1000L, 0.2f));
        assertFalse(slot.fire(PaneScreenEvent.Kind.BELL_TEAR, 1050L, 0.7f));
        assertFalse(slot.fire(PaneScreenEvent.Kind.BELL_TEAR, 1149L, 0.7f));
        PaneScreenEvent live = slot.live(1100L);
        assertNotNull(live);
        assertEquals(1000L, live.startUptime);
        assertEquals(0.2f, live.seed, 0f);
        // Once the first tear has run its 150 ms, the next bell tears again.
        assertTrue(slot.fire(PaneScreenEvent.Kind.BELL_TEAR, 1150L, 0.5f));
        assertEquals(1150L, slot.live(1160L).startUptime);
    }

    @Test public void aDifferentKindReplacesTheLiveOne() {
        PaneScreenEvent.Slot slot = new PaneScreenEvent.Slot();
        assertTrue(slot.fire(PaneScreenEvent.Kind.BELL_TEAR, 1000L, 0f));
        assertTrue(slot.fire(PaneScreenEvent.Kind.DEATH_SNOW, 1050L, 0f));
        PaneScreenEvent live = slot.live(1060L);
        assertNotNull(live);
        assertSame(PaneScreenEvent.Kind.DEATH_SNOW, live.kind);
        assertEquals(1050L, live.startUptime);
        assertFalse("NONE starts nothing", slot.fire(PaneScreenEvent.Kind.NONE, 1070L, 0f));
        assertNull(slot.live(1071L));
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
