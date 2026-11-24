package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The thin row's enlarge and settle: press, slide, tap, timeout, and every way out (#334). */
public class RowEnlargementTest {

    private static final float SLOP = 10f;

    private final RowEnlargement e = new RowEnlargement(SLOP);

    @Test
    public void restsSmallWithNothingArmed() {
        assertEquals(RowEnlargement.Phase.IDLE, e.phase());
        assertFalse(e.enlarged());
        assertEquals(0L, e.timeoutMs());
    }

    @Test
    public void aPressEnlargesAtOnceAndArmsOnlyTheBackstop() {
        e.down(100f, 50f);
        assertTrue(e.enlarged());
        assertEquals(RowEnlargement.Phase.PRESSED, e.phase());
        assertEquals(RowEnlargement.BACKSTOP_MS, e.timeoutMs());
    }

    @Test
    public void aTapThatDoesNotMoveHoldsTheRowForTwoSeconds() {
        e.down(100f, 50f);
        e.move(103f, 52f);
        assertEquals(RowEnlargement.Release.TAP_ENLARGED_ONLY, e.up());
        assertTrue(e.enlarged());
        assertEquals(RowEnlargement.Phase.HELD, e.phase());
        assertEquals(2000L, e.timeoutMs());
        e.timeout();
        assertFalse(e.enlarged());
        assertEquals(0L, e.timeoutMs());
    }

    @Test
    public void theTapThatEnlargedDoesNotActButTheNextOneDoes() {
        e.down(0f, 0f);
        assertEquals(RowEnlargement.Release.TAP_ENLARGED_ONLY, e.up());
        e.down(0f, 0f);
        assertEquals(RowEnlargement.Phase.PRESSED, e.phase());
        assertEquals(RowEnlargement.BACKSTOP_MS, e.timeoutMs());
        assertEquals(RowEnlargement.Release.TAP, e.up());
        assertTrue(e.enlarged());
        assertEquals("each touch restarts the two seconds", 2000L, e.timeoutMs());
    }

    @Test
    public void movingPastSlopIsASlideAndReleaseEndsIt() {
        e.down(100f, 50f);
        e.move(100f + SLOP + 1f, 50f);
        assertEquals(RowEnlargement.Phase.SLIDING, e.phase());
        assertTrue(e.enlarged());
        assertEquals(RowEnlargement.Release.SLIDE_ENDED, e.up());
        assertFalse(e.enlarged());
        assertEquals(0L, e.timeoutMs());
    }

    @Test
    public void slopIsMeasuredAsDistanceFromThePress() {
        e.down(0f, 0f);
        e.move(8f, 8f);
        assertEquals(RowEnlargement.Phase.SLIDING, e.phase());
        e.up();
        e.down(0f, 0f);
        e.move(7f, 0f);
        e.move(0f, 9f);
        assertEquals(RowEnlargement.Phase.PRESSED, e.phase());
    }

    @Test
    public void aSlideFromTheHeldRowEndsOnRelease() {
        e.down(0f, 0f);
        e.up();
        e.down(0f, 0f);
        e.move(40f, 0f);
        assertEquals(RowEnlargement.Release.SLIDE_ENDED, e.up());
        assertFalse(e.enlarged());
    }

    @Test
    public void aSlideNeverComesBackToATap() {
        e.down(0f, 0f);
        e.move(40f, 0f);
        e.move(0f, 0f);
        assertEquals(RowEnlargement.Phase.SLIDING, e.phase());
    }

    @Test
    public void everyWayOutEndsIt() {
        e.down(0f, 0f);
        e.cancel();
        assertFalse(e.enlarged());
        e.down(0f, 0f);
        e.move(40f, 0f);
        e.pageChanged();
        assertFalse(e.enlarged());
        e.down(0f, 0f);
        e.up();
        e.pageChanged();
        assertFalse(e.enlarged());
        e.down(0f, 0f);
        e.up();
        e.end();
        assertFalse(e.enlarged());
        assertEquals(0L, e.timeoutMs());
    }

    @Test
    public void theBackstopEndsAPressWhoseReleaseNeverCame() {
        e.down(0f, 0f);
        e.timeout();
        assertFalse(e.enlarged());
        assertEquals(RowEnlargement.Release.NONE, e.up());
    }

    @Test
    public void eventsWithNoPressChangeNothing() {
        e.move(100f, 100f);
        assertEquals(RowEnlargement.Release.NONE, e.up());
        e.cancel();
        e.timeout();
        assertEquals(RowEnlargement.Phase.IDLE, e.phase());
    }

    @Test
    public void aFreshPressAfterASlideIsAFirstTapAgain() {
        e.down(0f, 0f);
        e.move(40f, 0f);
        e.up();
        e.down(0f, 0f);
        assertEquals(RowEnlargement.Release.TAP_ENLARGED_ONLY, e.up());
    }
}
