package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Whether the keyboard should be up on the Display place: it goes up when a tap lands on
 * something the X session draws a text cursor over, and down again when the next tap lands
 * anywhere else — or when the next touch becomes a scroll or a swipe that began anywhere else.
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
 *       which the {@code termux-x11-im} helper sends when fcitx5 shows or hides its virtual
 *       keyboard, that is on a field's focus-in and focus-out. That signal is exact, so it needs
 *       no window.
 * </ul>
 *
 * <p><b>Focus decides, once it has been heard.</b> Focus is present from the first focus signal,
 * in or out, that reaches the policy while it is active, until it next goes inert: leaving the
 * place, or the setting going off. The policy learns this from the signal itself, so the host has
 * no fact to pass and none to get wrong. A host flag such as "the helper's bus is up" could be
 * true while fcitx5 is gone, or while the app in front has no input-method module and never
 * reports; and a focus heard on an earlier visit could be stale by now, since nothing reaches the
 * policy while the wall is away. The cost: until the first signal of a visit, taps follow the
 * cursor rules, so a tap on read-only text can still raise the keyboard once. While focus is
 * present:
 *
 * <ul>
 *   <li>focus-in shows the keyboard;
 *   <li>focus-out hides it after {@value #HIDE_GRACE_MS} ms, and a focus-in inside that grace
 *       cancels the hide, so moving from one field to the next never drops the keyboard;
 *   <li>a tap over a text cursor shows the keyboard only if the last focus signal was a focus-in,
 *       which brings back a keyboard put down while the field kept focus. After a focus-out the
 *       text under the finger is read-only (a terminal, page text), so the tap shows nothing;
 *   <li>a tap or a drag anywhere else hides nothing: hiding is focus-out's.
 * </ul>
 *
 * <p>A tap and a focus signal in either order come to one decision. Focus-in first shows the
 * keyboard, and the tap that follows finds it up. A tap first opens its window, and the focus
 * signal that lands inside it closes the window and decides alone: focus is the better answer
 * than the cursor's name. A tap during the grace opens no window at all, since with focus out a
 * tap cannot show anything and only a focus-in can, which shows by itself. So the tap window and
 * the grace are never pending together, and one timer serves both.
 *
 * <p>The keyboard is only ever put down again if this is what raised it — a keyboard the user
 * asked for is theirs, and a tap on the desktop must not take it away. Asking for it pins it:
 * from then on focus is ignored until it is asked down again, or until the wall leaves the place.
 * "Keyboard off" is not this policy's to keep: the launcher drops a focus-in while the keyboard is
 * off, and the keyboard itself refuses to rise for anything this policy asks.
 *
 * <p>Inert unless the Display place is on screen and the setting is on, whatever the touch mode.
 * A tap is a click where it lands: under the finger in Touchscreen and Direct touch, at the
 * pointer in Trackpad and on mouse mode's touchpad, so in every case the cursor name read after it
 * is the name of the widget that was clicked.
 */
public final class DisplayTextFocusPolicy {

    /** How long after a tap a cursor name still counts as that tap's answer. */
    public static final long TAP_WINDOW_MS = 150L;

    /** How long a focus-out waits for the focus-in that would cancel it. */
    public static final long HIDE_GRACE_MS = 150L;

    /** The cursor names an X client draws over text. Anything else, or nothing, is not text. */
    private static final String[] TEXT_CURSORS = {"xterm", "text", "ibeam", "vertical-text"};

    /** Where the answer goes. */
    public interface Keyboard {
        /** Raise the keyboard for a text field that took focus. */
        void showKeyboardForTextFocus();
        /** Put down the keyboard this policy raised. */
        void hideKeyboardForTextFocus();
        /**
         * Whether a keyboard is on screen at all. The policy asks because the user can put one
         * down by a route it never hears about - the keyboard's own hide key - and the next tap
         * in a text field has to raise it again rather than assume it is already there.
         */
        boolean isKeyboardUp();
    }

    /**
     * The timer for the tap window and the hide grace, which are never pending together. Handed
     * in so both can be stepped in a test.
     */
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
    private boolean onPlace;
    /** The name the pointer's cursor last reported, whether or not a finger was involved. */
    @NonNull private String cursorName = "";
    /** A name that arrived inside the open tap window, or null while none has. */
    @Nullable private String tappedCursorName;
    private boolean windowOpen;
    /** A focus signal has reached the policy since it last became active: focus decides. */
    private boolean focusHeard;
    /** The last focus signal was a focus-in. Means nothing while {@link #focusHeard} is false. */
    private boolean focusIn;
    /** A focus-out is waiting out its grace before it hides the keyboard. */
    private boolean hidePending;

    private final Runnable decide = this::decideOnTap;
    private final Runnable hideAfterGrace = this::hideAfterGrace;

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
        return enabled && onPlace;
    }

    /** True while the wall rests on the Display place. */
    public boolean isOnPlace() {
        return onPlace;
    }

    /** True while a tap is still waiting for the cursor name that answers it. */
    public boolean isTapWindowOpen() {
        return windowOpen;
    }

    /** True once a focus signal has been heard on this visit, so focus, not taps, decides. */
    public boolean isFocusPresent() {
        return focusHeard;
    }

    // ---- The settings it reads ---------------------------------------------------------------

    /** The "Keyboard follows text fields" setting. */
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!isActive()) standDown("setting " + (value ? "on" : "off"));
    }

    // ---- The wall ----------------------------------------------------------------------------

    /**
     * The wall settled on the Display place. {@code keyboardRaisedOnEnter} says the place asked
     * for the keyboard as it arrived, which is the user's own choice and so pins it.
     */
    public void onPlaceEntered(boolean keyboardRaisedOnEnter) {
        onPlace = true;
        cursorName = "";
        forgetTimersAndFocus();
        state = keyboardRaisedOnEnter ? State.PINNED : State.CLOSED;
        say("entered the place" + (keyboardRaisedOnEnter ? ", keyboard on enter" : ""));
    }

    /**
     * The wall left the Display place. The keyboard is not touched — the wall's own arrival and
     * departure rules own it there — but a pin does not survive the move.
     */
    public void onPlaceLeft() {
        onPlace = false;
        forgetTimersAndFocus();
        state = State.CLOSED;
        say("left the place");
    }

    // ---- The signals -------------------------------------------------------------------------

    /**
     * A tap landed on the display itself: an {@code ACTION_UP} that never turned into a drag, and
     * never on the keyboard or on the launcher's own chrome. It opens the window the cursor name
     * answers in. During the hide grace it opens none: focus is out, so only a focus-in can bring
     * the keyboard back, and that needs no tap.
     */
    public void onDisplayTap() {
        if (!isActive()) return;
        if (hidePending) {
            say("tap during the hide grace -> focus decides");
            return;
        }
        tappedCursorName = null;
        windowOpen = true;
        scheduler.cancel();
        scheduler.schedule(TAP_WINDOW_MS, decide);
    }

    /**
     * A finger on the display turned into a gesture — a drag past touch slop, or a second finger.
     * A scroll or a swipe on the page is the user working somewhere other than the field that
     * raised the keyboard, so the keyboard goes down, the way a tap elsewhere puts it down —
     * unless the gesture began over text, which is selecting in the field, or the keyboard is the
     * user's own. A gesture never raises the keyboard: the pointer crosses too many widgets on
     * the way for a name to mean anything. While focus is present a gesture hides nothing either:
     * the field still has focus until a focus-out says otherwise.
     */
    public void onDisplayDrag() {
        if (!isActive()) return;
        closeWindow();
        String seen = cursorName.isEmpty() ? "(no name)" : cursorName;
        if (state != State.AUTO_OPEN) {
            say("drag over " + seen + " -> nothing to do" + (state == State.PINNED ? ": pinned" : ""));
            return;
        }
        if (focusHeard) {
            say("drag over " + seen + " -> focus decides");
            return;
        }
        if (isTextCursor(cursorName)) {
            say("drag over " + seen + " -> selecting, keyboard stays");
            return;
        }
        state = State.CLOSED;
        say("drag over " + seen + " -> hide");
        keyboard.hideKeyboardForTextFocus();
    }

    /** The pointer's cursor changed its name. On its own this decides nothing. */
    public void onCursorName(@Nullable String name) {
        cursorName = name == null ? "" : name;
        if (windowOpen) tappedCursorName = cursorName;
    }

    /**
     * Signal B: an input method on the Linux side says a text field took focus, or lost it. True
     * when the policy took the signal, false when it is inert and the caller should fall back to
     * showing the keyboard itself. Heard while pinned it decides nothing, but it is remembered,
     * so the taps after the pin know where focus is.
     */
    public boolean onTextFocusSignal(boolean focused) {
        if (!isActive()) return false;
        closeWindow();
        focusHeard = true;
        focusIn = focused;
        if (state == State.PINNED) {
            say("focus " + (focused ? "in" : "out") + " ignored: pinned");
            return true;
        }
        if (focused) {
            boolean cancelled = hidePending;
            if (cancelled) {
                cancelHide();
                say("hide cancelled: focus in");
            }
            if (state == State.CLOSED || !keyboard.isKeyboardUp()) {
                state = State.AUTO_OPEN;
                say("focus in -> show");
                keyboard.showKeyboardForTextFocus();
            } else if (!cancelled) {
                say("focus in -> already open");
            }
        } else if (state != State.AUTO_OPEN) {
            say("focus out -> nothing to do");
        } else if (hidePending) {
            say("focus out -> hide already pending");
        } else {
            hidePending = true;
            scheduler.schedule(HIDE_GRACE_MS, hideAfterGrace);
            say("focus out -> hide in " + HIDE_GRACE_MS + " ms");
        }
        return true;
    }

    /**
     * The user put the keyboard up or down themselves — the toggle key, the mouse key, the
     * keyboard tools with a manual source. Up pins it; down hands it back to the policy. Either
     * way a pending hide is dropped: up is theirs, and down is already down.
     */
    public void onUserKeyboardIntent(boolean shown) {
        closeWindow();
        cancelHide();
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
        if (focusHeard) {
            decideTapByFocus(text, seen);
            return;
        }
        if (text) {
            if (state == State.CLOSED || !keyboard.isKeyboardUp()) {
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

    /**
     * With focus present a tap only confirms: over text it shows the keyboard if a field has
     * focus, and over anything else it leaves the keyboard to focus-out.
     */
    private void decideTapByFocus(boolean text, @NonNull String seen) {
        if (!text) {
            say("tap over " + seen + " -> focus decides");
        } else if (!focusIn) {
            say("tap over " + seen + " ignored: no focus");
        } else if (state == State.CLOSED || !keyboard.isKeyboardUp()) {
            state = State.AUTO_OPEN;
            say("tap over " + seen + " -> show: focus in");
            keyboard.showKeyboardForTextFocus();
        } else {
            say("tap over " + seen + " -> already open");
        }
    }

    /** The grace ran out with no focus-in: the field really lost focus. */
    private void hideAfterGrace() {
        hidePending = false;
        if (!isActive() || state != State.AUTO_OPEN) return;
        state = State.CLOSED;
        say("focus out -> hide");
        keyboard.hideKeyboardForTextFocus();
    }

    /** Close the tap window, leaving a pending hide alone. */
    private void closeWindow() {
        if (windowOpen) scheduler.cancel();
        windowOpen = false;
        tappedCursorName = null;
    }

    /** Drop a pending hide, leaving the keyboard where it is. */
    private void cancelHide() {
        if (hidePending) scheduler.cancel();
        hidePending = false;
    }

    /** Arriving, leaving, or going inert: no timer and no focus survive it. */
    private void forgetTimersAndFocus() {
        closeWindow();
        cancelHide();
        focusHeard = false;
        focusIn = false;
    }

    /** Went inert: forget the pin, the window and focus, and leave the keyboard exactly as it is. */
    private void standDown(@NonNull String why) {
        forgetTimersAndFocus();
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
