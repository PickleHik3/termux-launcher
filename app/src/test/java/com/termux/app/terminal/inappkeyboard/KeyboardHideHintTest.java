package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The shape of the swipe-away hint: when the pill shows, how far the keys dip, and for whom. */
public class KeyboardHideHintTest {

    private static final float NUDGE = 12f;

    private final KeyboardHideHint hint = new KeyboardHideHint(NUDGE, false);

    @Test
    public void thePillFadesInStandsAndFadesOut() {
        assertEquals(0f, hint.pillAlpha(0L), 1e-4f);
        assertEquals(0.5f, hint.pillAlpha(KeyboardHideHint.PILL_IN_MS / 2), 1e-4f);
        assertEquals(1f, hint.pillAlpha(KeyboardHideHint.PILL_IN_MS), 1e-4f);
        long outStart = KeyboardHideHint.PILL_IN_MS + KeyboardHideHint.PILL_HOLD_MS;
        assertEquals(1f, hint.pillAlpha(outStart - 1), 1e-4f);
        assertEquals(0.5f, hint.pillAlpha(outStart + KeyboardHideHint.PILL_OUT_MS / 2), 1e-4f);
        assertEquals(0f, hint.pillAlpha(KeyboardHideHint.durationMs()), 1e-4f);
        assertEquals(0f, hint.pillAlpha(-1L), 1e-4f);
    }

    @Test
    public void theKeysDipToTheNudgeAndComeBackInsideThePillsStand() {
        assertEquals(0f, hint.nudgePx(0L), 1e-4f);
        assertEquals(NUDGE, hint.nudgePx(KeyboardHideHint.NUDGE_DOWN_MS), 1e-4f);
        // Eased: past halfway down by half the time.
        assertTrue(hint.nudgePx(KeyboardHideHint.NUDGE_DOWN_MS / 2) > NUDGE / 2f);
        long backHalf = KeyboardHideHint.NUDGE_DOWN_MS + KeyboardHideHint.NUDGE_BACK_MS / 2;
        float mid = hint.nudgePx(backHalf);
        assertTrue(mid > 0f && mid < NUDGE / 2f);
        long settled = KeyboardHideHint.NUDGE_DOWN_MS + KeyboardHideHint.NUDGE_BACK_MS;
        assertEquals(0f, hint.nudgePx(settled), 1e-4f);
        assertEquals(0f, hint.nudgePx(KeyboardHideHint.durationMs()), 1e-4f);
        // The keys are at rest before the pill has gone: nothing settles after the hint.
        assertTrue(settled < KeyboardHideHint.PILL_IN_MS + KeyboardHideHint.PILL_HOLD_MS);
    }

    @Test
    public void theReturnOnlyEverDescends() {
        float previous = NUDGE;
        for (long t = KeyboardHideHint.NUDGE_DOWN_MS; t <= KeyboardHideHint.durationMs(); t += 10L) {
            float now = hint.nudgePx(t);
            assertTrue(now <= previous + 1e-4f);
            previous = now;
        }
    }

    @Test
    public void reducedMotionKeepsTheKeysStillAndStandsThePill() {
        KeyboardHideHint still = new KeyboardHideHint(NUDGE, true);
        assertEquals(0f, still.nudgePx(KeyboardHideHint.NUDGE_DOWN_MS), 1e-4f);
        assertEquals(1f, still.pillAlpha(0L), 1e-4f);
        assertEquals(1f, still.pillAlpha(KeyboardHideHint.durationMs() - 1), 1e-4f);
        assertEquals(0f, still.pillAlpha(KeyboardHideHint.durationMs()), 1e-4f);
    }

    @Test
    public void aNegativeNudgeIsNoNudge() {
        assertEquals(0f, new KeyboardHideHint(-4f, false).nudgePx(KeyboardHideHint.NUDGE_DOWN_MS),
            1e-4f);
    }

    @Test
    public void theFloatingCardKeepsItsOwnHandleAndGetsNoPill() {
        assertTrue(KeyboardHideHint.showsPill(false));
        assertFalse(KeyboardHideHint.showsPill(true));
    }
}
