package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import com.termux.app.x11.DisplayBackPolicy.Action;
import com.termux.app.x11.DisplayFrameContentPolicy.Content;
import com.termux.app.x11.DisplayTextFocusPolicy.State;

import org.junit.Test;

/** Back on the Display place: the keyboard the place raised, then the app on the display. */
public class DisplayBackPolicyTest {

    @Test public void offThePlaceBackIsTheLaunchersOwn() {
        assertEquals(Action.PASS,
            DisplayBackPolicy.decide(false, false, true, State.AUTO_OPEN, Content.KEYBOARD));
        assertEquals(Action.PASS,
            DisplayBackPolicy.decide(false, false, false, null, Content.NONE));
    }

    /** The drawer, the palette or a sheet over the display window takes Back, as over a terminal. */
    @Test public void anOpenLayerTakesBackBeforeEverythingOnThePlace() {
        assertEquals(Action.PASS,
            DisplayBackPolicy.decide(true, true, true, State.CLOSED, Content.NONE));
        assertEquals("even over a keyboard the place raised", Action.PASS,
            DisplayBackPolicy.decide(true, true, true, State.AUTO_OPEN, Content.KEYBOARD));
        assertEquals("and over a pinned one", Action.PASS,
            DisplayBackPolicy.decide(true, true, true, State.PINNED, Content.NONE));
    }

    @Test public void aKeyboardRaisedForATextFieldGoesDownFirst() {
        assertEquals(Action.LOWER_KEYBOARD,
            DisplayBackPolicy.decide(true, false, true, State.AUTO_OPEN, Content.NONE));
        assertEquals("and while mouse mode parks the pad behind it", Action.LOWER_KEYBOARD,
            DisplayBackPolicy.decide(true, false, true, State.AUTO_OPEN, Content.KEYBOARD));
    }

    @Test public void theKeyboardInFrontOfTheTouchpadGoesDownWhoeverPutItThere() {
        assertEquals(Action.LOWER_KEYBOARD,
            DisplayBackPolicy.decide(true, false, true, State.PINNED, Content.KEYBOARD));
        assertEquals(Action.LOWER_KEYBOARD,
            DisplayBackPolicy.decide(true, false, true, State.CLOSED, Content.KEYBOARD));
    }

    @Test public void withNothingOfThePlacesToPutDownTheAppGoesBack() {
        assertEquals(Action.BACK_IN_APP,
            DisplayBackPolicy.decide(true, false, true, State.CLOSED, Content.NONE));
        assertEquals("the pad is up, not a keyboard", Action.BACK_IN_APP,
            DisplayBackPolicy.decide(true, false, true, State.CLOSED, Content.PAD));
    }

    @Test public void aKeyboardTheUserAskedForIsTheirsAndBackReachesTheApp() {
        assertEquals(Action.BACK_IN_APP,
            DisplayBackPolicy.decide(true, false, true, State.PINNED, Content.NONE));
    }

    @Test public void withNoDisplayRunningThereIsNothingToGoBackIn() {
        assertEquals(Action.PASS,
            DisplayBackPolicy.decide(true, false, false, State.CLOSED, Content.NONE));
        assertEquals(Action.PASS,
            DisplayBackPolicy.decide(true, false, false, null, Content.NONE));
        assertEquals("but a keyboard it raised still goes down", Action.LOWER_KEYBOARD,
            DisplayBackPolicy.decide(true, false, false, State.AUTO_OPEN, Content.NONE));
    }
}
