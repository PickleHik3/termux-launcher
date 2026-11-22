package com.termux.privileged.lane;

import static com.termux.privileged.lane.PrivilegedLaneBinding.State;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LaneChoiceTest {

    @Test
    public void policyOffIsOffWhateverElseHolds() {
        assertEquals(State.OFF, LaneChoice.choose(false, true, true, true));
    }

    @Test
    public void usableShizukuWinsOverRoot() {
        assertEquals(State.BINDING, LaneChoice.choose(true, true, true, true));
        assertEquals(State.BINDING, LaneChoice.choose(true, true, true, false));
    }

    @Test
    public void rootCarriesWhenShizukuIsNotUsable() {
        assertEquals(State.ROOT, LaneChoice.choose(true, false, false, true));
        assertEquals(State.ROOT, LaneChoice.choose(true, true, false, true));
    }

    @Test
    public void neitherNamesWhatShizukuLacks() {
        assertEquals(State.NO_SHIZUKU, LaneChoice.choose(true, false, false, false));
        assertEquals(State.NO_PERMISSION, LaneChoice.choose(true, true, false, false));
    }
}
