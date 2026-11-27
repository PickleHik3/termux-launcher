package com.termux.app.leader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.leader.LeaderGate.Verdict;

import org.junit.Test;

/** Every screen asks the binding table first; only a bound leader chord is Terl's (ADR 0042). */
public class LeaderGateTest {

    @Test
    public void aBoundTerlKeyChordRuns() {
        assertEquals(Verdict.RUN, LeaderGate.decide(true, true, true));
    }

    @Test
    public void anUnboundTerlKeyChordGoesOnWithoutTheTerlKey() {
        assertEquals(Verdict.DROP_TERL_MODIFIER, LeaderGate.decide(true, true, false));
    }

    @Test
    public void aBoundHardwareLeaderChordRuns() {
        assertEquals(Verdict.RUN, LeaderGate.decide(true, false, true));
    }

    @Test
    public void anUnboundHardwareLeaderChordReachesWhatIsInFront() {
        assertEquals(Verdict.PASS, LeaderGate.decide(true, false, false));
    }

    @Test
    public void withTheSwitchOffNoScreenGainsALeader() {
        assertEquals(Verdict.PASS, LeaderGate.decide(false, false, true));
        assertEquals(Verdict.PASS, LeaderGate.decide(false, false, false));
    }

    @Test
    public void theReleaseAndRepeatsOfARunKeyAreSwallowedOnce() {
        LeaderGate gate = new LeaderGate();
        assertFalse(gate.swallows(true, 47, 0));
        gate.ran(47);
        assertTrue(gate.swallows(true, 47, 1));
        assertTrue(gate.swallows(false, 47, 0));
        // The release ended it: the next press of the key is asked afresh.
        assertFalse(gate.swallows(false, 47, 0));
        assertFalse(gate.swallows(true, 47, 0));
    }

    @Test
    public void aKeyThatDidNotRunIsNeverSwallowed() {
        LeaderGate gate = new LeaderGate();
        gate.ran(47);
        assertFalse(gate.swallows(true, 48, 0));
        assertFalse(gate.swallows(false, 48, 0));
    }
}
