package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.view.MouseModePress.Step;

import org.junit.Test;

/** When mouse mode's left button goes down, and what a pane corner's claim costs the program. */
public class MouseModePressTest {

    @Test
    public void anOrdinaryFingerPressesWhereItLands() {
        MouseModePress press = new MouseModePress();
        assertEquals(Step.PRESS, press.down(true, false));
        assertTrue(press.isPressed());
        assertEquals(Step.NOTHING, press.move(true));
        assertEquals(Step.RELEASE, press.up());
        assertFalse(press.isPressed());
    }

    @Test
    public void aProgramThatIsNotReadingTheMouseIsToldNothing() {
        MouseModePress press = new MouseModePress();
        assertEquals(Step.NOTHING, press.down(false, false));
        assertEquals(Step.NOTHING, press.move(true));
        assertEquals(Step.NOTHING, press.up());
        assertFalse(press.isPressed());
    }

    @Test
    public void aFingerInAPaneCornerPressesNothingYet() {
        MouseModePress press = new MouseModePress();
        assertEquals(Step.NOTHING, press.down(true, true));
        assertFalse(press.isPressed());
        // Resting there is not a drag, so the press is still waiting.
        assertEquals(Step.NOTHING, press.move(false));
        assertFalse(press.isPressed());
    }

    @Test
    public void aCornerHoldThatIsClaimedCostsTheProgramNothingAtAll() {
        MouseModePress press = new MouseModePress();
        press.down(true, true);
        assertEquals(Step.NOTHING, press.cancel());
        assertFalse(press.isPressed());
        assertEquals(Step.NOTHING, press.up());
    }

    @Test
    public void draggingOutOfACornerSendsTheDeferredPressAndThenDrags() {
        MouseModePress press = new MouseModePress();
        press.down(true, true);
        assertEquals(Step.PRESS, press.move(true));
        assertTrue(press.isPressed());
        // The press only ever goes out once.
        assertEquals(Step.NOTHING, press.move(true));
        assertEquals(Step.RELEASE, press.up());
    }

    @Test
    public void tappingACornerWithoutMovingIsOneWholeClick() {
        MouseModePress press = new MouseModePress();
        press.down(true, true);
        assertEquals(Step.CLICK, press.up());
        assertFalse(press.isPressed());
        assertEquals(Step.NOTHING, press.up());
    }

    @Test
    public void aPressAlreadySentStillGetsItsReleaseOnACancel() {
        MouseModePress press = new MouseModePress();
        assertEquals(Step.PRESS, press.down(true, false));
        assertEquals(Step.RELEASE, press.cancel());
        assertEquals(Step.NOTHING, press.cancel());
    }

    @Test
    public void aSecondFingerIsTheWheelAndWasNeverAClick() {
        MouseModePress deferred = new MouseModePress();
        deferred.down(true, true);
        assertEquals(Step.NOTHING, deferred.pointerDown());
        assertEquals(Step.NOTHING, deferred.up());

        MouseModePress pressed = new MouseModePress();
        pressed.down(true, false);
        assertEquals(Step.RELEASE, pressed.pointerDown());
        assertEquals(Step.NOTHING, pressed.up());
    }

    @Test
    public void switchingMouseModeOffLetsAHeldButtonUpAndForgetsAnOwedPress() {
        MouseModePress pressed = new MouseModePress();
        pressed.down(true, false);
        assertEquals(Step.RELEASE, pressed.release());

        MouseModePress deferred = new MouseModePress();
        deferred.down(true, true);
        assertEquals(Step.NOTHING, deferred.release());
        assertEquals(Step.NOTHING, deferred.up());
    }

    @Test
    public void everyGestureStartsFromItsOwnLanding() {
        MouseModePress press = new MouseModePress();
        press.down(true, true);
        assertEquals(Step.PRESS, press.down(true, false));
        assertTrue(press.isPressed());
        assertEquals(Step.NOTHING, press.down(true, true));
        assertFalse(press.isPressed());
    }
}
