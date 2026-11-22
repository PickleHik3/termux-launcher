package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.HomeFloatPolicy.Launch;

import org.junit.Test;

/**
 * A terminal launched from Home opens as a Home float ({@link HomeFloatPolicy#launchesAsFloat}).
 * The regression: the first terminal launched from Home, with no window anywhere, went into the
 * Terminal as a full window and took the screen, because the Terminal entry's tap into an empty
 * Terminal, the new-window keybind and the local API's new session all added a row window
 * whatever page was in front.
 */
public class HomeFloatLaunchTest {

    @Test
    public void theFirstTerminalFromHomeIsAFloat() {
        // No window anywhere: the Terminal is not on the row.
        assertTrue(HomeFloatPolicy.launchesAsFloat(Launch.ENTRY_TAP, true, false, true));
        assertTrue(HomeFloatPolicy.launchesAsFloat(Launch.NEW_TERMINAL, true, false, true));
    }

    @Test
    public void everyNewTerminalFromHomeIsAFloat() {
        assertTrue(HomeFloatPolicy.launchesAsFloat(Launch.NEW_TERMINAL, true, true, true));
    }

    @Test
    public void theEntryTapGoesToATerminalThatExists() {
        // A tap with a Terminal on the row is a way there, not a launch.
        assertFalse(HomeFloatPolicy.launchesAsFloat(Launch.ENTRY_TAP, true, true, true));
    }

    @Test
    public void newWindowStaysAWindow() {
        assertFalse(HomeFloatPolicy.launchesAsFloat(Launch.NEW_WINDOW, true, false, true));
        assertFalse(HomeFloatPolicy.launchesAsFloat(Launch.NEW_WINDOW, true, true, true));
    }

    @Test
    public void offHomeTheRowTakesIt() {
        assertFalse(HomeFloatPolicy.launchesAsFloat(Launch.NEW_TERMINAL, false, true, true));
        assertFalse(HomeFloatPolicy.launchesAsFloat(Launch.ENTRY_TAP, false, false, true));
    }

    @Test
    public void withoutAFloatToCarryItTheRowTakesIt() {
        // Compatibility mode or no Home pages: nothing to float over.
        assertFalse(HomeFloatPolicy.launchesAsFloat(Launch.NEW_TERMINAL, true, false, false));
        assertFalse(HomeFloatPolicy.launchesAsFloat(Launch.ENTRY_TAP, true, false, false));
    }
}
