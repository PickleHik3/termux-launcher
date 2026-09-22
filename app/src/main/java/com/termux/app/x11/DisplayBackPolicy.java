package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What Android's Back means on the Display place: the keyboard first, then the app.
 *
 * <p>A phone's Back is the only navigation key the display gets, and two things want it. The
 * keyboard wins while it is one the place put up itself — raised for a text field
 * ({@link DisplayTextFocusPolicy.State#AUTO_OPEN}), or standing in front of mouse mode's touchpad
 * ({@link DisplayFrameContentPolicy.Content#KEYBOARD}) — because a user who is done typing reaches
 * for Back before anything else. With nothing of the place's own to put down, Back is the app's:
 * the X client is sent its own Back and goes back a page, which is what the button does in every
 * other app on the phone.
 *
 * <p>A keyboard the user asked for themselves is not put down by this. It is theirs until they ask
 * again, the same rule the text-focus policy already keeps, so Back on top of it reaches the app.
 *
 * <p>All policy, no views: the caller applies the answer, and does nothing at all for
 * {@link Action#PASS} so Back keeps its launcher-wide meaning off the place and with no display
 * running.
 */
public final class DisplayBackPolicy {

    /** What the caller does with the key. */
    public enum Action {
        /** Not the display's: Back does whatever it does everywhere else in the launcher. */
        PASS,
        /** Put down the keyboard the place raised, through the user's own hide path. */
        LOWER_KEYBOARD,
        /** Send the focused X client its own Back. */
        BACK_IN_APP
    }

    private DisplayBackPolicy() {}

    /**
     * @param onDisplayPlace whether the wall rests on the Display place
     * @param displayRunning whether a display server is up with the page attached
     * @param textFocus      the text-focus policy's state, or null when there is no display
     * @param frameContent   what mouse mode's frame holds, {@code NONE} when mouse mode is off
     */
    @NonNull
    public static Action decide(boolean onDisplayPlace, boolean displayRunning,
                                @Nullable DisplayTextFocusPolicy.State textFocus,
                                @NonNull DisplayFrameContentPolicy.Content frameContent) {
        if (!onDisplayPlace) return Action.PASS;
        if (textFocus == DisplayTextFocusPolicy.State.AUTO_OPEN
                || frameContent == DisplayFrameContentPolicy.Content.KEYBOARD)
            return Action.LOWER_KEYBOARD;
        if (!displayRunning) return Action.PASS;
        return Action.BACK_IN_APP;
    }
}
