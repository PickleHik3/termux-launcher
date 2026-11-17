package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A Home float keeps Home's rest through a crossing (D1-A): what covers it (the dock's top, the
 * keyboard's top) is read off live views, which a crossing moves every frame, so none of it
 * reaches the float layer until the crossing has landed. A float resized by a crossing is a
 * resize of the shell's grid mid-slide.
 */
public class HomeFloatCrossingRoomTest {

    @Test public void aCrossingLeavesTheFloatsRoomAlone() {
        assertFalse(HomeFloatPolicy.obstructionsMayMove(true, false));
        assertFalse(HomeFloatPolicy.obstructionsMayMove(true, true));
    }

    @Test public void theSeamsLayoutAloneLeavesItAlone() {
        assertFalse(HomeFloatPolicy.obstructionsMayMove(false, true));
    }

    @Test public void atRestTheFloatsRoomFollowsWhatCoversIt() {
        assertTrue(HomeFloatPolicy.obstructionsMayMove(false, false));
    }
}
