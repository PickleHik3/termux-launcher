package com.termux.app.wayland;

/**
 * Whether a Linux app's text field raises or lowers the launcher's keyboard, and where the
 * keyboard's typed text goes. The controller feeds it the host's text-field reports
 * ({@code ILinuxWindowsCallback.onTextInput}) and does what it answers.
 *
 * <ul>
 *   <li>A field focused in the window in front raises the launcher's keyboard when it is down.
 *       A window not in front never raises it.</li>
 *   <li>A field left lowers the keyboard only if a field raised it: one the user opened by hand
 *       stays up. The lowering waits one main-thread turn ({@link Action#LOWER_SOON}), so focus
 *       moving straight to another field keeps the keyboard up instead of dropping and raising
 *       it.</li>
 *   <li>With the phone's keyboard as the user's keyboard nothing is raised: the Linux window has
 *       no input connection for it yet, so its text would land in the view that holds focus.</li>
 *   <li>Typed text goes to the field as text only while the focused window has one; otherwise it
 *       stays key presses, as for X11 apps and terminals.</li>
 * </ul>
 */
final class LinuxTextFieldPolicy {

    enum Action {
        NONE,
        /** Raise the launcher's keyboard. */
        RAISE,
        /** Post {@link #lowerDue}: the keyboard goes down unless a field takes focus first. */
        LOWER_SOON,
        /** Lower the launcher's keyboard now. */
        LOWER
    }

    /** The window whose field has focus, as the host last told; 0 for none. */
    private long fieldId;
    /** A field raised the keyboard and it has not been lowered since. */
    private boolean raised;
    /** A field was left while it held the keyboard raised; {@link #lowerDue} settles it. */
    private boolean lowerPending;

    /**
     * Window {@code id}'s field took focus. {@code inFront}: the window holds the launcher's
     * keys (shown on the page in front, a focused pane, or a dialog over the page).
     * {@code launcherKeyboard}: the launcher's own keyboard is the user's keyboard.
     */
    Action focused(long id, boolean inFront, boolean launcherKeyboard, boolean keyboardUp) {
        if (id == 0) return Action.NONE;
        fieldId = id;
        if (!inFront || !launcherKeyboard) return Action.NONE;
        if (lowerPending) {
            // Focus went from one field straight to this one: the keyboard stays, still ours.
            lowerPending = false;
            raised = true;
            return keyboardUp ? Action.NONE : Action.RAISE;
        }
        if (keyboardUp) return Action.NONE;
        raised = true;
        return Action.RAISE;
    }

    /** Window {@code id}'s field lost focus, or the window went. */
    Action left(long id) {
        if (id == 0 || id != fieldId) return Action.NONE;
        fieldId = 0;
        if (!raised) return Action.NONE;
        raised = false;
        lowerPending = true;
        return Action.LOWER_SOON;
    }

    /** The turn after a {@link Action#LOWER_SOON}: lower the keyboard if no field took it since. */
    Action lowerDue(boolean keyboardUp) {
        if (!lowerPending) return Action.NONE;
        lowerPending = false;
        return keyboardUp ? Action.LOWER : Action.NONE;
    }

    /** Whether text typed for window {@code focusedId} goes to its field as text. */
    boolean typesText(long focusedId) {
        return focusedId != 0 && fieldId == focusedId;
    }

    /** The host went or every window was dropped: the field, if any, is left. */
    Action dropped() {
        return left(fieldId);
    }
}
