package com.termux.app.wall.switcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HintGateTest {

    @Test public void coveredAtTheFirstFrameThenUncoveredShowsThen() {
        assertFalse(HintGate.opensNow(true, true, false, true));
        assertFalse(HintGate.opensNow(true, true, false, true));
        assertTrue(HintGate.opensNow(true, true, false, false));
    }

    @Test public void shownOnceNeverAgain() {
        assertFalse(HintGate.opensNow(true, true, true, false));
    }

    @Test public void switcherOffOrDotHiddenDoesNotOpen() {
        assertFalse(HintGate.opensNow(false, true, false, false));
        assertFalse(HintGate.opensNow(true, false, false, false));
    }
}
