package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** What the launcher does about whatever is at the Wayland socket's path before it listens. */
public class WaylandSocketOwnerTest {

    @Test
    public void nothingThereIsBound() {
        assertEquals(WaylandSocketOwner.Step.BIND, WaylandSocketOwner.step(false, false, false));
    }

    @Test
    public void aStaleSocketFromAKilledLauncherOrADeadHostIsReplaced() {
        assertEquals(WaylandSocketOwner.Step.REPLACE, WaylandSocketOwner.step(true, false, false));
    }

    @Test
    public void aSocketSomeoneServesIsLeftAlone() {
        // A host left running by an earlier launcher process, or the user's own compositor.
        assertEquals(WaylandSocketOwner.Step.LEAVE, WaylandSocketOwner.step(true, false, true));
    }

    @Test
    public void itsOwnSocketIsKeptAndNeverProbed() {
        assertEquals(WaylandSocketOwner.Step.KEEP, WaylandSocketOwner.step(true, true, false));
        assertEquals(WaylandSocketOwner.Step.KEEP, WaylandSocketOwner.step(true, true, true));
    }
}
