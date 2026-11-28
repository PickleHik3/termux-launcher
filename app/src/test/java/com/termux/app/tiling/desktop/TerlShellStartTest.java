package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Under Terl no v1 shell starts behind the removed Terminal page; a New session is a stack window. */
public class TerlShellStartTest {

    @Test public void underTerlANewSessionIsAWindowOfTheStack() {
        assertEquals(TerlShellStart.Start.TERL_WINDOW, TerlShellStart.of(true, true));
    }

    @Test public void underTerlAPlainStartStartsNoV1Shell() {
        assertEquals(TerlShellStart.Start.NOTHING, TerlShellStart.of(true, false));
    }

    @Test public void withTheSwitchOffEveryStartIsV1s() {
        assertEquals(TerlShellStart.Start.V1, TerlShellStart.of(false, true));
        assertEquals(TerlShellStart.Start.V1, TerlShellStart.of(false, false));
    }
}
