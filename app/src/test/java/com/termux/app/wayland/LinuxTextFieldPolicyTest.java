package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import com.termux.app.wayland.LinuxTextFieldPolicy.Action;

import org.junit.Test;

/** When a Linux app's text field raises or lowers the user's keyboard, and where text goes. */
public class LinuxTextFieldPolicyTest {

    private final LinuxTextFieldPolicy p = new LinuxTextFieldPolicy();

    @Test
    public void aFieldInFrontRaisesTheKeyboard() {
        assertEquals(Action.RAISE, p.focused(3, true, true, false));
    }

    @Test
    public void aWindowNotInFrontNeverRaisesIt() {
        assertEquals(Action.NONE, p.focused(3, false, true, false));
        assertEquals(Action.NONE, p.focused(3, false, false, false));
        assertEquals(Action.NONE, p.left(3));
        assertFalse(p.servesPhone(3));
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
    public void askingAgainAfterTheUserLoweredItRaisesItAgain() {
        p.focused(3, true, true, false);
        assertEquals(Action.RAISE, p.focused(3, true, true, false));
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

    @Test
    public void thePhonesKeyboardIsRaisedThroughTheWindowsConnection() {
        assertEquals(Action.RAISE_PHONE, p.focused(3, true, false, false));
        assertTrue(p.servesPhone(3));
        assertFalse(p.servesPhone(4));
        assertEquals(Action.LOWER_SOON, p.left(3));
        assertFalse(p.servesPhone(3));
        assertEquals(Action.LOWER_PHONE, p.lowerDue(true));
    }

    @Test
    public void aPhoneKeyboardAlreadyUpOnlyMovesTheConnection() {
        assertEquals(Action.FOCUS_PHONE, p.focused(3, true, false, true));
        assertTrue(p.servesPhone(3));
        assertEquals(Action.LOWER_SOON, p.left(3));
        assertEquals(Action.RELEASE_PHONE, p.lowerDue(true));
    }

    @Test
    public void aPhoneKeyboardTheUserClosedIsOnlyReleased() {
        p.focused(3, true, false, false);
        p.left(3);
        assertEquals(Action.RELEASE_PHONE, p.lowerDue(false));
    }

    @Test
    public void thePhonesKeyboardStaysUpFromFieldToField() {
        p.focused(3, true, false, false);
        p.left(3);
        assertEquals(Action.FOCUS_PHONE, p.focused(4, true, false, true));
        assertEquals(Action.NONE, p.lowerDue(true));
        p.left(4);
        assertEquals(Action.LOWER_PHONE, p.lowerDue(true));
    }

    @Test
    public void inputTypesFollowThePurpose() {
        assertEquals(InputType.TYPE_CLASS_TEXT, LinuxTextFieldPolicy.inputType(0, 0));
        assertEquals(InputType.TYPE_CLASS_NUMBER,
            LinuxTextFieldPolicy.inputType(0, LinuxTextFieldPolicy.PURPOSE_DIGITS));
        assertEquals(InputType.TYPE_CLASS_PHONE,
            LinuxTextFieldPolicy.inputType(0, LinuxTextFieldPolicy.PURPOSE_PHONE));
        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            LinuxTextFieldPolicy.inputType(0, LinuxTextFieldPolicy.PURPOSE_PASSWORD));
        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            LinuxTextFieldPolicy.inputType(LinuxTextFieldPolicy.HINT_HIDDEN_TEXT, 0));
        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
            LinuxTextFieldPolicy.inputType(0, LinuxTextFieldPolicy.PURPOSE_URL));
        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            LinuxTextFieldPolicy.inputType(0, LinuxTextFieldPolicy.PURPOSE_EMAIL));
        assertEquals(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            LinuxTextFieldPolicy.inputType(0, LinuxTextFieldPolicy.PURPOSE_PIN));
    }

    @Test
    public void inputTypeFlagsFollowTheHints() {
        int type = LinuxTextFieldPolicy.inputType(LinuxTextFieldPolicy.HINT_MULTILINE
            | LinuxTextFieldPolicy.HINT_AUTO_CAPITALIZATION | LinuxTextFieldPolicy.HINT_SPELLCHECK, 0);
        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, type);
        assertTrue((LinuxTextFieldPolicy.inputType(LinuxTextFieldPolicy.HINT_SENSITIVE_DATA, 0)
            & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
        assertTrue((LinuxTextFieldPolicy.inputType(0, LinuxTextFieldPolicy.PURPOSE_TERMINAL)
            & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
    }

    @Test
    public void thePhonesKeyboardNeverOpensItsOwnEditBox() {
        int options = LinuxTextFieldPolicy.imeOptions();
        assertTrue((options & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0);
        assertTrue((options & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0);
    }
}
