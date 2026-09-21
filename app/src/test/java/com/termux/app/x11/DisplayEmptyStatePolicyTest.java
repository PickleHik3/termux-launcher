package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.tour.TourEdition;
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

    /**
     * D9's offer hangs off exactly one of the three states: the one a home screen rests in. The
     * other two already carry the single thing the user has to do, and a second call to action
     * beside it is a nag.
     */
    @Test public void onlyThePlainNoDisplayStateHasRoomForAnOffer() {
        assertTrue(DisplayEmptyStatePolicy.decide(true, true).resting());
        assertFalse(DisplayEmptyStatePolicy.decide(true, false).resting());
        assertFalse(DisplayEmptyStatePolicy.decide(false, true).resting());
        assertFalse(DisplayEmptyStatePolicy.decide(false, false).resting());
    }

    /** The two-argument overload is exactly the Termux edition's answer. */
    @Test public void theTwoArgumentOverloadIsTheTermuxEdition() {
        for (boolean enabled : new boolean[] {true, false}) {
            for (boolean hasKeyboardData : new boolean[] {true, false}) {
                State plain = DisplayEmptyStatePolicy.decide(enabled, hasKeyboardData);
                State termux = DisplayEmptyStatePolicy.decide(enabled, hasKeyboardData,
                    TourEdition.TERMUX);
                assertEquals(plain.messageRes, termux.messageRes);
                assertEquals(plain.startVisible, termux.startVisible);
            }
        }
    }

    /**
     * Nix installs the keyboard data from its own config rather than with {@code pkg}, so the
     * missing-data state is the same state with nix's own sentence — and once the data is there
     * nix rests exactly like Termux, start button and all.
     */
    @Test public void nixNamesItsOwnWayToTheKeyboardDataAndOtherwiseReadsLikeTermux() {
        State withoutData = DisplayEmptyStatePolicy.decide(true, false, TourEdition.NIX);
        assertEquals(R.string.termux_x11_needs_keyboard_data_nix, withoutData.messageRes);
        assertFalse("nothing to start without the keyboard data", withoutData.startVisible);
        assertTrue("there is something to read about it", withoutData.guideVisible());
        assertFalse(withoutData.resting());

        State withData = DisplayEmptyStatePolicy.decide(true, true, TourEdition.NIX);
        assertEquals(R.string.termux_x11_no_display, withData.messageRes);
        assertTrue(withData.startVisible);
        assertTrue(withData.resting());
        assertFalse(withData.guideVisible());
    }

    @Test public void nixStillSaysTheSettingIsOffWhenItIs() {
        State off = DisplayEmptyStatePolicy.decide(false, false, TourEdition.NIX);
        assertEquals(R.string.termux_x11_display_off, off.messageRes);
        assertTrue(off.startVisible);
    }

    @Test public void vajReadsTheSameAsTermux() {
        for (boolean enabled : new boolean[] {true, false}) {
            for (boolean hasKeyboardData : new boolean[] {true, false}) {
                State vaj = DisplayEmptyStatePolicy.decide(enabled, hasKeyboardData,
                    TourEdition.VAJ);
                State termux = DisplayEmptyStatePolicy.decide(enabled, hasKeyboardData,
                    TourEdition.TERMUX);
                assertEquals(termux.messageRes, vaj.messageRes);
                assertEquals(termux.startVisible, vaj.startVisible);
            }
        }
    }
}
