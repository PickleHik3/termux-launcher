package com.termux.app.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ScreenOnReasonsTest {

    private final List<Boolean> calls = new ArrayList<>();
    private final ScreenOnReasons reasons = new ScreenOnReasons(calls::add);

    @Test
    public void theFlagFollowsTheUnionOfReasons() {
        reasons.set(ScreenOnReasons.VOICE, true);
        reasons.set(ScreenOnReasons.LINUX_WINDOW, true);
        reasons.set(ScreenOnReasons.VOICE, false);
        assertEquals("the second reason keeps it on", List.of(true), calls);
        reasons.set(ScreenOnReasons.LINUX_WINDOW, false);
        assertEquals(List.of(true, false), calls);
    }

    @Test
    public void settingAReasonTwiceOrClearingAnUnsetOneTouchesNothing() {
        reasons.set(ScreenOnReasons.VOICE, false);
        reasons.set(ScreenOnReasons.VOICE, true);
        reasons.set(ScreenOnReasons.VOICE, true);
        assertEquals(List.of(true), calls);
    }
}
