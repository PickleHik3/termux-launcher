package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The minimised index's reveal: tucked, out while held, back on release. */
public class AzTabRevealTest {

    @Test
    public void itRestsTuckedWithNothingButTheTab() {
        AzTabReveal reveal = new AzTabReveal();
        assertEquals(AzTabReveal.Phase.TUCKED, reveal.phase());
        assertFalse(reveal.lettersVisible());
        assertEquals(0f, reveal.target(), 0f);
    }

    @Test
    public void theFirstTouchTakesTheGestureAndAsksTheLettersOut() {
        AzTabReveal reveal = new AzTabReveal();
        assertTrue(reveal.press());
        assertEquals(AzTabReveal.Phase.OUT, reveal.phase());
        assertTrue(reveal.isHeld());
        assertTrue(reveal.lettersVisible());
        assertEquals(1f, reveal.target(), 0f);
        // Settling out does not end anything: the finger is still down.
        reveal.settled(1f);
        assertEquals(AzTabReveal.Phase.OUT, reveal.phase());
    }

    @Test
    public void releaseSendsThemBackAndTheyStopDrawingOnceHome() {
        AzTabReveal reveal = new AzTabReveal();
        reveal.press();
        reveal.release();
        assertEquals(AzTabReveal.Phase.RETURNING, reveal.phase());
        assertTrue("still drawn on the way back", reveal.lettersVisible());
        assertEquals(0f, reveal.target(), 0f);
        reveal.settled(0.4f);
        assertEquals(AzTabReveal.Phase.RETURNING, reveal.phase());
        reveal.settled(0f);
        assertEquals(AzTabReveal.Phase.TUCKED, reveal.phase());
        assertFalse(reveal.lettersVisible());
    }

    @Test
    public void aTouchOnTheWayBackTakesThemOutAgain() {
        AzTabReveal reveal = new AzTabReveal();
        reveal.press();
        reveal.release();
        assertTrue(reveal.press());
        assertEquals(AzTabReveal.Phase.OUT, reveal.phase());
        reveal.settled(0f);
        assertEquals("a slide that settled home under a finger is not tucked",
            AzTabReveal.Phase.OUT, reveal.phase());
    }

    @Test
    public void aReleaseWithoutAPressChangesNothing() {
        AzTabReveal reveal = new AzTabReveal();
        reveal.release();
        assertEquals(AzTabReveal.Phase.TUCKED, reveal.phase());
    }

    @Test
    public void switchedOffItTakesNoTouchAndTucksAtOnce() {
        AzTabReveal reveal = new AzTabReveal();
        reveal.press();
        reveal.setEnabled(false);
        assertEquals(AzTabReveal.Phase.TUCKED, reveal.phase());
        assertFalse(reveal.press());
        assertFalse(reveal.lettersVisible());
        reveal.setEnabled(true);
        assertTrue(reveal.press());
    }

    @Test
    public void tuckIsImmediate() {
        AzTabReveal reveal = new AzTabReveal();
        reveal.press();
        reveal.tuck();
        assertEquals(AzTabReveal.Phase.TUCKED, reveal.phase());
        assertEquals(0f, reveal.target(), 0f);
    }

    @Test
    public void theTabFadesAsTheLettersArrive() {
        assertEquals(1f, AzTabReveal.tabAlpha(0f), 0f);
        assertEquals(0.25f, AzTabReveal.tabAlpha(0.75f), 1e-6f);
        assertEquals(0f, AzTabReveal.tabAlpha(1f), 0f);
        assertEquals("a spring past its target never draws a negative tab",
            0f, AzTabReveal.tabAlpha(1.1f), 0f);
        assertEquals(1f, AzTabReveal.tabAlpha(-0.2f), 0f);
    }
}
