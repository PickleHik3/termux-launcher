package com.termux.app.wayland;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/**
 * Whether a Linux app's text field raises or lowers the user's keyboard, and where typed text
 * goes. The controller feeds it the host's text-field reports
 * ({@code ILinuxWindowsCallback.onTextInput}) and does what it answers.
 *
 * <ul>
 *   <li>A field focused in the window in front brings up the user's keyboard: the launcher's
 *       ({@link Action#RAISE}) or the phone's ({@link Action#RAISE_PHONE}, through the window's
 *       own input connection, so the phone's text reaches the app and not the terminal behind
 *       it). A window not in front never raises either.</li>
 *   <li>With the phone's keyboard already up, the window still takes its input connection
 *       ({@link Action#FOCUS_PHONE}).</li>
 *   <li>A field left lowers the keyboard only if a field raised it: one the user opened by hand
 *       stays up. The lowering waits one main-thread turn ({@link Action#LOWER_SOON}), so focus
 *       moving straight to another field keeps the keyboard up instead of dropping and raising
 *       it.</li>
 *   <li>Typed text goes to the field as text only while the focused window has one; otherwise it
 *       stays key presses, as for X11 apps and terminals.</li>
 * </ul>
 */
final class LinuxTextFieldPolicy {

    enum Action {
        NONE,
        /** Raise the launcher's keyboard. */
        RAISE,
        /** Give the window the phone keyboard's input connection and show the phone's keyboard. */
        RAISE_PHONE,
        /** Give the window the phone keyboard's input connection; the keyboard is already up. */
        FOCUS_PHONE,
        /** Post {@link #lowerDue}: the keyboard goes down unless a field takes focus first. */
        LOWER_SOON,
        /** Lower the launcher's keyboard now. */
        LOWER,
        /** Hide the phone's keyboard and take the window's input connection back. */
        LOWER_PHONE,
        /** Take the window's input connection back; the phone's keyboard stays as the user left it. */
        RELEASE_PHONE
    }

    // zwp_text_input_v3 content_purpose values.
    static final int PURPOSE_DIGITS = 2;
    static final int PURPOSE_NUMBER = 3;
    static final int PURPOSE_PHONE = 4;
    static final int PURPOSE_URL = 5;
    static final int PURPOSE_EMAIL = 6;
    static final int PURPOSE_NAME = 7;
    static final int PURPOSE_PASSWORD = 8;
    static final int PURPOSE_PIN = 9;
    static final int PURPOSE_DATE = 10;
    static final int PURPOSE_TIME = 11;
    static final int PURPOSE_DATETIME = 12;
    static final int PURPOSE_TERMINAL = 13;
    // zwp_text_input_v3 content_hint bits.
    static final int HINT_COMPLETION = 0x1;
    static final int HINT_SPELLCHECK = 0x2;
    static final int HINT_AUTO_CAPITALIZATION = 0x4;
    static final int HINT_UPPERCASE = 0x10;
    static final int HINT_TITLECASE = 0x20;
    static final int HINT_HIDDEN_TEXT = 0x40;
    static final int HINT_SENSITIVE_DATA = 0x80;
    static final int HINT_MULTILINE = 0x200;

    /** The window whose field has focus, as the host last told; 0 for none. */
    private long fieldId;
    /** A field raised the keyboard and it has not been lowered since. */
    private boolean raised;
    /** The field is served through the window's input connection for the phone's keyboard. */
    private boolean phone;
    /** A field was left while it held the keyboard; {@link #lowerDue} settles it. */
    private boolean lowerPending;

    /**
     * Window {@code id}'s field took focus (or its app asked for the keyboard again). {@code
     * inFront}: the window holds the launcher's keys (shown on the page in front, a focused pane,
     * or a dialog over the page). {@code launcherKeyboard}: the launcher's own keyboard is the
     * user's keyboard. {@code keyboardUp}: the user's keyboard is up.
     */
    Action focused(long id, boolean inFront, boolean launcherKeyboard, boolean keyboardUp) {
        if (id == 0) return Action.NONE;
        fieldId = id;
        if (!inFront) return Action.NONE;
        // Focus that went from one field straight to this one keeps what the first one held.
        lowerPending = false;
        phone = !launcherKeyboard;
        if (keyboardUp) return phone ? Action.FOCUS_PHONE : Action.NONE;
        raised = true;
        return phone ? Action.RAISE_PHONE : Action.RAISE;
    }

    /** Window {@code id}'s field lost focus, or the window went. */
    Action left(long id) {
        if (id == 0 || id != fieldId) return Action.NONE;
        fieldId = 0;
        if (!raised && !phone) return Action.NONE;
        lowerPending = true;
        return Action.LOWER_SOON;
    }

    /** The turn after a {@link Action#LOWER_SOON}: lower the keyboard if no field took it since. */
    Action lowerDue(boolean keyboardUp) {
        if (!lowerPending) return Action.NONE;
        lowerPending = false;
        boolean wasPhone = phone;
        boolean wasRaised = raised;
        phone = false;
        raised = false;
        if (wasPhone) return wasRaised && keyboardUp ? Action.LOWER_PHONE : Action.RELEASE_PHONE;
        return wasRaised && keyboardUp ? Action.LOWER : Action.NONE;
    }

    /** Whether text typed for window {@code focusedId} goes to its field as text. */
    boolean typesText(long focusedId) {
        return focusedId != 0 && fieldId == focusedId;
    }

    /** Whether window {@code id}'s field is served through its input connection for the phone's keyboard. */
    boolean servesPhone(long id) {
        return phone && id != 0 && fieldId == id;
    }

    /** The host went or every window was dropped: the field, if any, is left. */
    Action dropped() {
        return left(fieldId);
    }

    /** The phone keyboard's input type for a field of this content hint and purpose. */
    static int inputType(int hint, int purpose) {
        switch (purpose) {
            case PURPOSE_DIGITS:
                return InputType.TYPE_CLASS_NUMBER;
            case PURPOSE_PIN:
                return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
            case PURPOSE_NUMBER:
                return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED;
            case PURPOSE_PHONE:
                return InputType.TYPE_CLASS_PHONE;
            case PURPOSE_DATE:
                return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE;
            case PURPOSE_TIME:
                return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME;
            case PURPOSE_DATETIME:
                return InputType.TYPE_CLASS_DATETIME;
            default:
                break;
        }
        int type = InputType.TYPE_CLASS_TEXT;
        if (purpose == PURPOSE_PASSWORD || (hint & HINT_HIDDEN_TEXT) != 0) {
            type |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
        } else if (purpose == PURPOSE_URL) {
            type |= InputType.TYPE_TEXT_VARIATION_URI;
        } else if (purpose == PURPOSE_EMAIL) {
            type |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        } else if (purpose == PURPOSE_NAME) {
            type |= InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
        }
        if ((hint & HINT_UPPERCASE) != 0) type |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
        else if ((hint & HINT_TITLECASE) != 0) type |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        else if ((hint & HINT_AUTO_CAPITALIZATION) != 0) type |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        if ((hint & HINT_SPELLCHECK) != 0) type |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        if ((hint & HINT_COMPLETION) != 0) type |= InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
        if ((hint & HINT_SENSITIVE_DATA) != 0 || purpose == PURPOSE_TERMINAL) {
            type |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        }
        if ((hint & HINT_MULTILINE) != 0) type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        return type;
    }

    /**
     * The phone keyboard's options: never its own full-screen edit box (the app shows the text),
     * and Enter as a key, which the app reads as it does from a hardware keyboard.
     */
    static int imeOptions() {
        return EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN
            | EditorInfo.IME_ACTION_NONE;
    }
}
