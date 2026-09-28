package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The keyboard swipe tied to the finger: the keyboard follows it one to one over its own height,
 * and the release goes on past a third of the way or on a fling, and comes back short of both or
 * on a fling back.
 */
public class KeyboardRevealTest {

    private static final int KEYBOARD = 900;
    private static final float EPS = 0.001f;
    /** The fling in the reveal's own units, for a phone at density 2.75. */
    private static final float FLING = KeyboardReveal.FLING_DP_PER_SEC * 2.75f / KEYBOARD;

    @Test
    public void theKeyboardFollowsTheFingerOneToOne() {
        // Up from closed: a third of the keyboard's height shows a third of it.
        assertEquals(1f / 3f, KeyboardReveal.reveal(0f, -300f, KEYBOARD), EPS);
        // Down from open, the same way back.
        assertEquals(2f / 3f, KeyboardReveal.reveal(1f, 300f, KEYBOARD), EPS);
        // Never past either end, however far the finger goes.
        assertEquals(1f, KeyboardReveal.reveal(0f, -5000f, KEYBOARD), EPS);
        assertEquals(0f, KeyboardReveal.reveal(1f, 5000f, KEYBOARD), EPS);
        assertEquals(0f, KeyboardReveal.reveal(0f, 200f, KEYBOARD), EPS);
        // A keyboard with no height does not move.
        assertEquals(1f, KeyboardReveal.reveal(1f, 300f, 0), EPS);
    }

    @Test
    public void theFingersSpeedIsTheReveals() {
        // Moving up at a keyboard a second is rising at one reveal a second.
        assertEquals(1f, KeyboardReveal.revealVelocity(-KEYBOARD, KEYBOARD), EPS);
        assertEquals(-0.5f, KeyboardReveal.revealVelocity(KEYBOARD / 2f, KEYBOARD), EPS);
        assertEquals(0f, KeyboardReveal.revealVelocity(Float.NaN, KEYBOARD), EPS);
    }

    @Test
    public void pastAThirdOfTheWayItGoesOnAndShortOfItItComesBack() {
        // Opening from closed.
        assertTrue(KeyboardReveal.settlesOpen(false, 0.34f, 0f, FLING));
        assertFalse(KeyboardReveal.settlesOpen(false, 0.3f, 0f, FLING));
        // Closing from open: a third of the way down is enough, not a whole swipe.
        assertFalse(KeyboardReveal.settlesOpen(true, 0.66f, 0f, FLING));
        assertTrue(KeyboardReveal.settlesOpen(true, 0.7f, 0f, FLING));
    }

    @Test
    public void aShortDecisiveFlickClosesIt() {
        // A tenth of the way down, but moving down at the fling's speed.
        float down = -FLING;
        assertFalse(KeyboardReveal.settlesOpen(true, 0.9f, down, FLING));
        // And the same flick up opens it from closed.
        assertTrue(KeyboardReveal.settlesOpen(false, 0.1f, FLING, FLING));
    }

    @Test
    public void aFlingBackAgainstTheSwipeComesBackFromAnywhere() {
        // Dragged most of the way down, then flicked back up: it stays open.
        assertTrue(KeyboardReveal.settlesOpen(true, 0.2f, FLING * 1.5f, FLING));
        // Dragged most of the way up, then flicked back down: it stays closed.
        assertFalse(KeyboardReveal.settlesOpen(false, 0.8f, -FLING * 1.5f, FLING));
    }

    @Test
    public void aSlowReleaseIsDecidedByWhereItStands() {
        float slow = FLING * 0.5f;
        assertFalse("slowly rising, but short of a third",
            KeyboardReveal.settlesOpen(false, 0.2f, slow, FLING));
        assertTrue("slowly sinking, but short of a third down",
            KeyboardReveal.settlesOpen(true, 0.8f, -slow, FLING));
    }

    @Test
    public void theTickIsFeltCrossingTheCommitPointEitherWay() {
        float point = KeyboardReveal.commitPoint(false);
        assertEquals(KeyboardReveal.COMMIT_FRACTION, point, EPS);
        assertEquals(1f - KeyboardReveal.COMMIT_FRACTION, KeyboardReveal.commitPoint(true), EPS);
        assertTrue(KeyboardReveal.crossesCommit(false, point - 0.01f, point + 0.01f));
        assertTrue("back across it too", KeyboardReveal.crossesCommit(false, point + 0.01f,
            point - 0.01f));
        assertFalse(KeyboardReveal.crossesCommit(false, 0.1f, 0.2f));
        assertFalse(KeyboardReveal.crossesCommit(false, 0.5f, 0.9f));
        float closing = KeyboardReveal.commitPoint(true);
        assertTrue(KeyboardReveal.crossesCommit(true, closing + 0.01f, closing - 0.01f));
        assertFalse(KeyboardReveal.crossesCommit(true, 0.9f, 0.8f));
    }

    @Test
    public void theSettleLandsAKeyboardInAGesturesTime() {
        SettleSpring spring = SettleSpring.of(-1f, 0f, KeyboardReveal.SETTLE_OMEGA,
            KeyboardReveal.SETTLE_MAX_OMEGA);
        long ms = spring.durationMs(KeyboardReveal.SETTLE_REST_PX / KEYBOARD);
        assertTrue("a whole keyboard from still: " + ms, ms >= 300L && ms <= 600L);
    }
}
