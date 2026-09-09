package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Whether the keyboard should be up on the Display place: it goes up when a tap lands on
 * something the X session draws a text cursor over, and down again when the next tap lands
 * anywhere else.
 *
 * <p>All policy, no views. Two signals reach it and neither is a focus event in itself:
 *
 * <ul>
 *   <li><b>The cursor's name.</b> X tells a host nothing about focus, but the window under the
 *       pointer names its cursor — {@code xterm} over a text field, {@code left_ptr} over most
 *       other things — and the server forwards that name. A name on its own means nothing: the
 *       pointer crosses a hundred widgets while a finger is nowhere near the screen. So a name
 *       only decides something in the {@value #TAP_WINDOW_MS} ms after a tap, and the last one
 *       inside that window is the answer.
 *   <li><b>An input method's own focus.</b> {@code keyboard.show --source focus} and its hide,
 *       which a script on the Linux side wires to fcitx5's focus-in and focus-out. That signal is
 *       exact, so it needs no window.
 * </ul>
 *
 * <p>The keyboard is only ever put down again if this is what raised it — a keyboard the user
 * asked for is theirs, and a tap on the desktop must not take it away. Asking for it pins it:
 * from then on focus is ignored until it is asked down again, or until the wall leaves the place.
 *
 * <p>Inert unless the Display place is on screen, the touch mode is Touchscreen and the setting
 * is on: in Trackpad and Direct touch a tap is not a tap on a widget, and the two modes are
 * meant to behave exactly as they always have.
 */
public final class DisplayTextFocusPolicy {

    /** How long after a tap a cursor name still counts as that tap's answer. */
    public static final long TAP_WINDOW_MS = 150L;

    /** {@code touchMode} 2 — Touchscreen. The policy sleeps through Trackpad and Direct touch. */
    public static final int TOUCH_MODE_TOUCHSCREEN = 2;

    /** The cursor names an X client draws over text. Anything else, or nothing, is not text. */
    private static final String[] TEXT_CURSORS = {"xterm", "text", "ibeam", "vertical-text"};

    /** Where the answer goes. */
    public interface Keyboard {
        /** Raise the keyboard for a text field that took focus. */
        void showKeyboardForTextFocus();
        /** Put down the keyboard this policy raised. */
        void hideKeyboardForTextFocus();
    }

    /** The tap window's timer, handed in so the window can be stepped in a test. */
    public interface Scheduler {
        /** Run {@code action} in {@code delayMs}; only ever one is pending. */
        void schedule(long delayMs, @NonNull Runnable action);
        /** Drop a pending action, if there is one. */
        void cancel();
    }

    /** Every decision, for the debug log. */
    public interface Trace {
        void onTextFocusDecision(@NonNull String message);
    }

    public enum State {
        /** Nothing this policy raised is on screen. */
        CLOSED,
        /** The keyboard is up because a text field took focus, so a tap elsewhere closes it. */
        AUTO_OPEN,
        /** The user asked for the keyboard: focus signals are ignored until they ask again. */
        PINNED
    }

    @NonNull private final Keyboard keyboard;
    @NonNull private final Scheduler scheduler;
    @Nullable private final Trace trace;

    @NonNull private State state = State.CLOSED;
    private boolean enabled = true;
    private int touchMode = TOUCH_MODE_TOUCHSCREEN;
    private boolean onPlace;
    /** The name the pointer's cursor last reported, whether or not a finger was involved. */
    @NonNull private String cursorName = "";
    /** A name that arrived inside the open tap window, or null while none has. */
    @Nullable private String tappedCursorName;
    private boolean windowOpen;

    private final Runnable decide = this::decideOnTap;

    public DisplayTextFocusPolicy(@NonNull Keyboard keyboard, @NonNull Scheduler scheduler,
                                  @Nullable Trace trace) {
        this.keyboard = keyboard;
        this.scheduler = scheduler;
        this.trace = trace;
    }

    // ---- What the policy has been told ------------------------------------------------------

    @NonNull
    public State state() {
        return state;
    }

    /** True while the signals mean anything at all. */
    public boolean isActive() {
        return enabled && onPlace && touchMode == TOUCH_MODE_TOUCHSCREEN;
    }

    /** True while a tap is still waiting for the cursor name that answers it. */
    public boolean isTapWindowOpen() {
        return windowOpen;
    }

    // ---- The settings it reads ---------------------------------------------------------------

    /** The "Keyboard follows text fields" setting. */
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!isActive()) standDown("setting " + (value ? "on" : "off"));
    }

    /** The display's {@code touchMode}: only Touchscreen is read as taps on widgets. */
    public void setTouchMode(int mode) {
        if (touchMode == mode) return;
        touchMode = mode;
        if (!isActive()) standDown("touch mode " + mode);
    }

    // ---- The wall ----------------------------------------------------------------------------

    /**
     * The wall settled on the Display place. {@code keyboardRaisedOnEnter} says the place asked
     * for the keyboard as it arrived, which is the user's own choice and so pins it.
     */
    public void onPlaceEntered(boolean keyboardRaisedOnEnter) {
        onPlace = true;
        cursorName = "";
        closeWindow();
        state = keyboardRaisedOnEnter ? State.PINNED : State.CLOSED;
        say("entered the place" + (keyboardRaisedOnEnter ? ", keyboard on enter" : ""));
    }

    /**
     * The wall left the Display place. The keyboard is not touched — the wall's own arrival and
     * departure rules own it there — but a pin does not survive the move.
     */
    public void onPlaceLeft() {
        onPlace = false;
        closeWindow();
        state = State.CLOSED;
        say("left the place");
    }

    // ---- The signals -------------------------------------------------------------------------

    /**
     * A tap landed on the display itself: an {@code ACTION_UP} that never turned into a drag, and
     * never on the keyboard or on the launcher's own chrome. It opens the window the cursor name
     * answers in.
     */
    public void onDisplayTap() {
        if (!isActive()) return;
        tappedCursorName = null;
        windowOpen = true;
        scheduler.cancel();
        scheduler.schedule(TAP_WINDOW_MS, decide);
    }

    /** The pointer's cursor changed its name. On its own this decides nothing. */
    public void onCursorName(@Nullable String name) {
        cursorName = name == null ? "" : name;
        if (windowOpen) tappedCursorName = cursorName;
    }

    /**
     * Signal B: an input method on the Linux side says a text field took focus, or lost it. True
     * when the policy took the signal, false when it is inert and the caller should fall back to
     * showing the keyboard itself.
     */
    public boolean onTextFocusSignal(boolean focused) {
        if (!isActive()) return false;
        closeWindow();
        if (state == State.PINNED) {
            say("focus " + (focused ? "in" : "out") + " ignored: pinned");
            return true;
        }
        if (focused) {
            if (state == State.CLOSED) {
                state = State.AUTO_OPEN;
                say("focus in -> show");
                keyboard.showKeyboardForTextFocus();
            }
        } else if (state == State.AUTO_OPEN) {
            state = State.CLOSED;
            say("focus out -> hide");
            keyboard.hideKeyboardForTextFocus();
        }
        return true;
    }

    /**
     * The user put the keyboard up or down themselves — the toggle key, the mouse key, the
     * keyboard tools with a manual source. Up pins it; down hands it back to the policy.
     */
    public void onUserKeyboardIntent(boolean shown) {
        closeWindow();
        state = shown ? State.PINNED : State.CLOSED;
        say("user " + (shown ? "showed" : "hid") + " the keyboard -> " + state);
    }

    // ---- The tap window ----------------------------------------------------------------------

    /**
     * The window closed: the last name inside it is the answer. A tap that changed nothing —
     * a second tap in the same field, say, where X sends no new name — is answered by the name
     * the pointer is already showing.
     */
    private void decideOnTap() {
        windowOpen = false;
        if (!isActive()) return;
        String name = tappedCursorName != null ? tappedCursorName : cursorName;
        tappedCursorName = null;
        boolean text = isTextCursor(name);
        String seen = name.isEmpty() ? "(no name)" : name;
        if (state == State.PINNED) {
            say("tap over " + seen + " ignored: pinned");
            return;
        }
        if (text) {
            if (state == State.CLOSED) {
                state = State.AUTO_OPEN;
                say("tap over " + seen + " -> show");
                keyboard.showKeyboardForTextFocus();
            } else {
                say("tap over " + seen + " -> already open");
            }
        } else if (state == State.AUTO_OPEN) {
            state = State.CLOSED;
            say("tap over " + seen + " -> hide");
            keyboard.hideKeyboardForTextFocus();
        } else {
            say("tap over " + seen + " -> nothing to do");
        }
    }

    private void closeWindow() {
        windowOpen = false;
        tappedCursorName = null;
        scheduler.cancel();
    }

    /** Went inert: forget the pin and the window, and leave the keyboard exactly as it is. */
    private void standDown(@NonNull String why) {
        closeWindow();
        state = State.CLOSED;
        say("inert: " + why);
    }

    private void say(@NonNull String message) {
        if (trace != null) trace.onTextFocusDecision(message);
    }

    /** Whether {@code name} is a cursor an X client draws over text. */
    public static boolean isTextCursor(@Nullable String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        for (String text : TEXT_CURSORS) {
            if (text.equalsIgnoreCase(trimmed)) return true;
        }
        return false;
    }
}
