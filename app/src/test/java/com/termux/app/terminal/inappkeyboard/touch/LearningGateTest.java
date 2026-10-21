package com.termux.app.terminal.inappkeyboard.touch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LearningGateTest {

    /**
     * Counts {@code taps} taps, every {@code every}th of them a retype that takes the one before
     * it back, in the order the engine reports them.
     */
    private static void type(LearningGate gate, int taps, int every) {
        for (int i = 1; i <= taps; i++) {
            if (every > 0 && i % every == 0)
                gate.onTakeBack();
            gate.onTap();
        }
    }

    @Test
    public void warmUpSetsTheBaselineAndThenCorrects() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS - 1, 50);
        assertFalse(gate.isCorrecting());
        type(gate, 1, 1);
        assertTrue(gate.isCorrecting());
        // One in fifty is two per hundred.
        assertEquals(2f, gate.baseline(), 0.05f);
    }

    @Test
    public void aSteadyRateStaysActive() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS, 50);
        type(gate, 5_000, 50);
        assertTrue(gate.isCorrecting());
    }

    @Test
    public void twelveInAWindowAgainstTwoPerHundredIsChanceAndDoesNotPause() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS, 50); // 2 per 100: 10 expected in a window
        type(gate, LearningGate.DRIFT_WINDOW, 50);
        // Two more take-backs inside the window make 12: 20% up, but well within chance.
        type(gate, 40, 20);
        assertEquals(12, gate.windowTakeBacks());
        assertTrue(gate.isCorrecting());
    }

    @Test
    public void aClearlyBadWindowAgainstTwoPerHundredPauses() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS, 50);
        type(gate, LearningGate.DRIFT_WINDOW, 50);
        // One in twenty is 25 a window, two and a half times the baseline.
        type(gate, LearningGate.DRIFT_WINDOW, 20);
        assertEquals(LearningGate.STATE_PAUSED, gate.state());
    }

    @Test
    public void againstAZeroBaselineOneTakeBackDoesNotPauseAndFourDo() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS, 0);
        assertEquals(0f, gate.baseline(), 0f);
        type(gate, LearningGate.DRIFT_WINDOW, 0);
        type(gate, 10, 10);
        assertEquals(1, gate.windowTakeBacks());
        assertTrue(gate.isCorrecting());
        type(gate, 30, 10);
        assertEquals(LearningGate.STATE_PAUSED, gate.state());
    }

    @Test
    public void theRuleNeedsBothTheRiseAndTheChanceMargin() {
        // Baseline 2 per 100, 10 expected: 12 meets the rise only; 19 meets both.
        assertFalse(LearningGate.isDrift(12, 2f));
        assertFalse(LearningGate.isDrift(18, 2f));
        assertTrue(LearningGate.isDrift(19, 2f));
        // Baseline 0: 3.33 by chance.
        assertFalse(LearningGate.isDrift(3, 0f));
        assertTrue(LearningGate.isDrift(4, 0f));
        // 20 per 100, 100 expected: the rise asks 120, chance asks 124.3.
        assertFalse(LearningGate.isDrift(124, 20f));
        assertTrue(LearningGate.isDrift(125, 20f));
    }

    @Test
    public void theWindowForgetsTakeBacksThatSlideOutOfIt() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS, 50);
        type(gate, LearningGate.DRIFT_WINDOW, 50);
        int before = gate.windowTakeBacks();
        type(gate, LearningGate.DRIFT_WINDOW, 0);
        assertEquals(10, before);
        assertEquals(0, gate.windowTakeBacks());
    }

    @Test
    public void resumeRestartsTheWarmUp() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS, 0);
        gate.pause();
        gate.restartWarmUp();
        assertEquals(LearningGate.STATE_LEARNING, gate.state());
        assertEquals(0, gate.warmUpTaps());
        assertEquals(0, gate.windowFill());
    }

    @Test
    public void roundTripsThroughItsState() {
        LearningGate gate = new LearningGate();
        type(gate, LearningGate.WARM_UP_TAPS, 40);
        type(gate, 300, 40);
        LearningGate copy = new LearningGate(gate.state(), gate.warmUpTaps(),
            gate.warmUpTakeBacks(), gate.baseline(), gate.window(), gate.windowNext(),
            gate.windowFill());
        assertEquals(gate.state(), copy.state());
        assertEquals(gate.baseline(), copy.baseline(), 0f);
        assertEquals(gate.windowTakeBacks(), copy.windowTakeBacks());
        assertEquals(gate.windowFill(), copy.windowFill());
    }

    @Test
    public void aDamagedStateStartsAFreshWarmUp() {
        LearningGate copy = new LearningGate(LearningGate.STATE_ACTIVE, 5, 9, -1f,
            new long[1], 0, 0);
        assertEquals(LearningGate.STATE_LEARNING, copy.state());
        assertEquals(0, copy.warmUpTaps());
    }
}
