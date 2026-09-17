package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.x11.DisplayEmptyStatePolicy.State;

import org.junit.Test;

/**
 * The Display page's empty state: which message it shows and whether its start control is out,
 * across the setting and the keyboard-data probe's full input matrix — including the flip from
 * "needs the package" to "ready" that item 03 was about, which is exactly what the caller sees
 * once it re-derives readiness on arrival instead of only on a running-state change.
 */
public class DisplayEmptyStatePolicyTest {

    @Test public void offSaysSoWhateverTheKeyboardDataProbeAnswers() {
        State off = DisplayEmptyStatePolicy.decide(false, true);
        assertEquals(R.string.termux_x11_display_off, off.messageRes);
        assertTrue("nothing to fix while the setting itself is off", off.startVisible);
        assertFalse(off.guideVisible());

        State stillOff = DisplayEmptyStatePolicy.decide(false, false);
        assertEquals(R.string.termux_x11_display_off, stillOff.messageRes);
        assertTrue(stillOff.startVisible);
        assertFalse(stillOff.guideVisible());
    }

    @Test public void enabledWithoutTheKeyboardDataNamesThePackageAndHidesStart() {
        State state = DisplayEmptyStatePolicy.decide(true, false);
        assertEquals(R.string.termux_x11_needs_keyboard_data, state.messageRes);
        assertFalse("a server that cannot start has nothing for Start to do", state.startVisible);
        assertTrue("the only state with something to read about setting one up",
            state.guideVisible());
    }

    @Test public void enabledWithTheKeyboardDataIsThePlainNoDisplayMessage() {
        State state = DisplayEmptyStatePolicy.decide(true, true);
        assertEquals(R.string.termux_x11_no_display, state.messageRes);
        assertTrue(state.startVisible);
        assertFalse(state.guideVisible());
    }

    /**
     * The flip itself: installing the package while the page is showing turns the same call from
     * "needs it" to "ready" with nothing else about the inputs changing — the caller's job is
     * making sure this method actually runs again when that happens, not answered here.
     */
    @Test public void installingThePackageFlipsNeedsItToReady() {
        State before = DisplayEmptyStatePolicy.decide(true, false);
        State after = DisplayEmptyStatePolicy.decide(true, true);

        assertEquals(R.string.termux_x11_needs_keyboard_data, before.messageRes);
        assertFalse(before.startVisible);
        assertTrue(before.guideVisible());

        assertEquals(R.string.termux_x11_no_display, after.messageRes);
        assertTrue(after.startVisible);
        assertFalse(after.guideVisible());
    }
}
