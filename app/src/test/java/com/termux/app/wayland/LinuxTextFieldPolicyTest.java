package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wayland.LinuxTextFieldPolicy.Action;

import org.junit.Test;

/** When a Linux app's text field raises or lowers the launcher's keyboard, and where text goes. */
public class LinuxTextFieldPolicyTest {

    private final LinuxTextFieldPolicy p = new LinuxTextFieldPolicy();

    @Test
    public void aFieldInFrontRaisesTheKeyboard() {
        assertEquals(Action.RAISE, p.focused(3, true, true, false));
    }

    @Test
    public void aWindowNotInFrontNeverRaisesIt() {
        assertEquals(Action.NONE, p.focused(3, false, true, false));
        assertEquals(Action.NONE, p.left(3));
    }

    @Test
    public void thePhonesKeyboardIsNotRaised() {
        assertEquals(Action.NONE, p.focused(3, true, false, false));
        assertEquals(Action.NONE, p.left(3));
    }

    @Test
    public void aFieldLeftLowersTheKeyboardItRaised() {
        p.focused(3, true, true, false);
        assertEquals(Action.LOWER_SOON, p.left(3));
        assertEquals(Action.LOWER, p.lowerDue(true));
        assertEquals(Action.NONE, p.lowerDue(true));
    }

    @Test
    public void aKeyboardTheUserOpenedStaysUp() {
        assertEquals(Action.NONE, p.focused(3, true, true, true));
        assertEquals(Action.NONE, p.left(3));
        assertEquals(Action.NONE, p.lowerDue(true));
    }

    @Test
    public void aKeyboardAlreadyDownIsNotLoweredAgain() {
        p.focused(3, true, true, false);
        p.left(3);
        assertEquals(Action.NONE, p.lowerDue(false));
    }

    @Test
    public void focusMovingStraightToAnotherFieldKeepsTheKeyboard() {
        p.focused(3, true, true, false);
        assertEquals(Action.LOWER_SOON, p.left(3));
        assertEquals(Action.NONE, p.focused(4, true, true, true));
        assertEquals(Action.NONE, p.lowerDue(true));
        // Still the field's keyboard: leaving the second field lowers it.
        assertEquals(Action.LOWER_SOON, p.left(4));
        assertEquals(Action.LOWER, p.lowerDue(true));
    }

    @Test
    public void aStaleLeftForAnotherWindowIsIgnored() {
        p.focused(4, true, true, false);
        assertEquals(Action.NONE, p.left(3));
        assertTrue(p.typesText(4));
    }

    @Test
    public void aContentTypeChangeInTheSameFieldDoesNotRaiseAgain() {
        p.focused(3, true, true, false);
        assertEquals(Action.NONE, p.focused(3, true, true, true));
        assertEquals(Action.LOWER_SOON, p.left(3));
    }

    @Test
    public void textGoesAsTextOnlyToTheFocusedWindowsField() {
        assertFalse(p.typesText(3));
        p.focused(3, false, true, false);
        assertTrue(p.typesText(3));
        assertFalse(p.typesText(4));
        assertFalse(p.typesText(0));
        p.left(3);
        assertFalse(p.typesText(3));
    }

    @Test
    public void droppingEveryWindowLeavesTheField() {
        p.focused(3, true, true, false);
        assertEquals(Action.LOWER_SOON, p.dropped());
        assertFalse(p.typesText(3));
        assertEquals(Action.NONE, p.dropped());
    }
}
