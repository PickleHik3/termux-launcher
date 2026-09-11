package com.termux.app.x11;

import androidx.annotation.NonNull;

/**
 * What the keyboard frame holds on the Display place while mouse mode owns it: the touchpad, or
 * the keyboard standing in front of a parked pad.
 *
 * <p>Mouse mode gives the pad the keyboard's frame, so for as long as it is on there is only one
 * frame and two things that want it. Rather than take the frame away and put it back — which
 * resizes the X screen twice and loses the pointer — the frame keeps its size and swaps its
 * content: {@link Content#PAD} while the user is pointing, {@link Content#KEYBOARD} while they
 * are typing into whatever took text focus. Turning mouse mode off is the only thing that hands
 * the frame back to the keyboard alone ({@link Content#NONE}).
 *
 * <p>All policy, no views: every event answers with a {@link Decision} the caller applies. Three
 * things ask for the swap and they mean different things to the text-focus policy beside this one:
 *
 * <ul>
 *   <li><b>The mouse key.</b> With the keyboard parked in front of the pad it brings the pad
 *       back, still in mouse mode; with the pad up it leaves mouse mode, as it always has.
 *   <li><b>The keyboard key</b>, and {@code keyboard.show}/{@code keyboard.hide} with a manual
 *       source. The user asking for the keyboard is the user asking, so it {@link Intent#PIN
 *       pins} the text-focus policy off; asking for it down hands it back.
 *   <li><b>A text field taking or losing focus</b>, through the text-focus policy itself. It has
 *       already moved its own state, so these carry {@link Intent#NONE}.
 * </ul>
 *
 * <p>A {@link Decision} that was not {@link Decision#taken} means mouse mode does not own the
 * frame: the caller does the plain thing it does everywhere else — show the keyboard, hide it,
 * toggle it — and this class has changed nothing.
 */
public final class DisplayFrameContentPolicy {

    /** What the keyboard frame holds. {@link #NONE} is mouse mode off: it is the keyboard's own. */
    public enum Content {
        /** Mouse mode is off; there is no pad and the keyboard frame answers to nobody here. */
        NONE,
        /** The touchpad: mouse mode's own content, and what it starts and comes back to. */
        PAD,
        /** The keyboard, with the pad parked behind it until the typing is done. */
        KEYBOARD
    }

    /** What the text-focus policy beside this one is told once a decision is applied. */
    public enum Intent {
        /** Nothing: the swap was that policy's own doing, or nothing about a pin changed. */
        NONE,
        /**
         * The user asked for the keyboard: {@code onUserKeyboardIntent(true)}, which pins the
         * text-focus policy off. Only reported while a keyboard is actually on screen — the
         * caller owns that check, because only it knows.
         */
        PIN,
        /** The keyboard is the text-focus policy's business again: {@code onUserKeyboardIntent(false)}. */
        UNPIN
    }

    /** One event's answer: what the frame holds now, and what the text-focus policy is told. */
    public static final class Decision {

        /** What the frame holds after the event. */
        @NonNull public final Content content;
        /** What the text-focus policy is told. */
        @NonNull public final Intent intent;
        /**
         * False when mouse mode does not own the frame, and the caller should do the plain thing
         * it does off the Display place instead. Nothing was changed.
         */
        public final boolean taken;

        private Decision(@NonNull Content content, @NonNull Intent intent, boolean taken) {
            this.content = content;
            this.intent = intent;
            this.taken = taken;
        }

        /** True while mouse mode is on, pad up or keyboard parked in front of it. */
        public boolean mouseMode() {
            return content != Content.NONE;
        }

        @Override
        @NonNull
        public String toString() {
            return "Decision{" + content + ", " + intent + (taken ? "" : ", not taken") + "}";
        }
    }

    @NonNull private Content content = Content.NONE;

    /** What the frame holds. */
    @NonNull
    public Content content() {
        return content;
    }

    /** True while mouse mode is on — the pad owns the frame, whether or not it is parked. */
    public boolean isMouseMode() {
        return content != Content.NONE;
    }

    /**
     * Whether a keyboard is the frame's content. This is what {@code isKeyboardUp()} means while
     * mouse mode owns the frame: the keyboard view is up either way, as the pad's frame.
     */
    public boolean isKeyboardContent() {
        return content == Content.KEYBOARD;
    }

    // ---- Mouse mode itself --------------------------------------------------------------------

    /** Mouse mode went on: the pad takes the frame, and the keyboard under it is nobody's pin. */
    @NonNull
    public Decision onMouseModeOn() {
        if (content != Content.NONE) return decide(content, Intent.NONE);
        return decide(Content.PAD, Intent.UNPIN);
    }

    /**
     * Mouse mode went off — the pad's own exit arrow, the three-finger swipe down, or anything
     * else that ends it. The keyboard frame stays as it is, so a keyboard left on screen is the
     * user's from here on.
     */
    @NonNull
    public Decision onMouseModeOff() {
        if (content == Content.NONE) return notTaken();
        return decide(Content.NONE, Intent.PIN);
    }

    /**
     * The mouse key, or {@code mouse.toggle}. Off it turns mouse mode on; with the pad up it
     * turns it off; with the keyboard parked in front of the pad it brings the pad back instead,
     * still in mouse mode, and hands the keyboard back to the text-focus policy.
     */
    @NonNull
    public Decision onMouseKey() {
        switch (content) {
            case KEYBOARD:
                return decide(Content.PAD, Intent.UNPIN);
            case PAD:
                return onMouseModeOff();
            default:
                return onMouseModeOn();
        }
    }

    // ---- The keyboard ------------------------------------------------------------------------

    /**
     * The keyboard key while mouse mode owns the frame: the keyboard comes to the front, or goes
     * back behind the pad. Either way it is the user asking, so the text-focus policy is pinned
     * or handed back.
     */
    @NonNull
    public Decision onKeyboardKey() {
        if (content == Content.NONE) return notTaken();
        return onKeyboardIntent(content != Content.KEYBOARD);
    }

    /**
     * The user asked for the keyboard up or down by name — {@code keyboard.show}/{@code hide}
     * with a manual source, the keyboard's own hide key, the display's Back button. Asking for
     * it down while the pad is already up changes no content: the frame is already not a
     * keyboard, and only the pin moves.
     */
    @NonNull
    public Decision onKeyboardIntent(boolean show) {
        if (content == Content.NONE) return notTaken();
        return decide(show ? Content.KEYBOARD : Content.PAD, show ? Intent.PIN : Intent.UNPIN);
    }

    // ---- Text focus ---------------------------------------------------------------------------

    /**
     * The text-focus policy asked for the keyboard, or asked for it away. It has moved its own
     * state already, so nothing is reported back to it.
     */
    @NonNull
    public Decision onTextFocus(boolean focused) {
        if (content == Content.NONE) return notTaken();
        return decide(focused ? Content.KEYBOARD : Content.PAD, Intent.NONE);
    }

    // ---- The frame going away ----------------------------------------------------------------

    /**
     * Mouse mode no longer owns the keyboard frame: the wall left the Display place, the display
     * stopped, or the keyboard went away. Mouse mode itself is a session choice and survives all
     * three, so the pad stays what mouse mode shows — a parked keyboard is not carried back.
     */
    @NonNull
    public Decision onFrameLost() {
        if (content == Content.NONE) return notTaken();
        return decide(Content.PAD, Intent.NONE);
    }

    @NonNull
    private Decision decide(@NonNull Content next, @NonNull Intent intent) {
        content = next;
        return new Decision(next, intent, true);
    }

    @NonNull
    private Decision notTaken() {
        return new Decision(content, Intent.NONE, false);
    }
}
