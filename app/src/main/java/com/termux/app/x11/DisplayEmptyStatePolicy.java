package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.tour.TourEdition;

/**
 * What the Display page's empty state says while no server is running: the plain "nothing
 * running" message, unless the setting is off or the server cannot even start yet, and whether
 * the start control is worth showing while that is true.
 *
 * <p>A server cannot start at all without the {@code xkeyboard-config} package's XKB data, so the
 * start button is withheld and the message names it instead — better than sending the user to an
 * Xorg error in their shell. Whether that data is there is a filesystem probe
 * ({@link X11CliInstaller#hasKeyboardData()}), which the caller runs once and hands in as a plain
 * boolean: this class does no I/O of its own, which is what lets a test flip "installed" without a
 * real prefix and is why the caller must re-run the probe itself on every arrival at the place —
 * this policy only ever answers for the reading it is given.
 *
 * <p>The nix edition installs that data the same way it installs everything else — named in
 * {@code home.nix} and switched in — so it gets the same two states, with a message that says it
 * in nix's own words instead of naming a {@code pkg} command that edition does not have.
 */
public final class DisplayEmptyStatePolicy {

    /** The empty state's message and whether its start control is out. */
    public static final class State {
        @StringRes public final int messageRes;
        public final boolean startVisible;

        private State(@StringRes int messageRes, boolean startVisible) {
            this.messageRes = messageRes;
            this.startVisible = startVisible;
        }

        /**
         * True exactly while the message names the missing package: the only state with
         * something to read about setting one up, and so the only one the guide route is out for.
         */
        public boolean guideVisible() {
            return messageRes == R.string.termux_x11_needs_keyboard_data
                || messageRes == R.string.termux_x11_needs_keyboard_data_nix;
        }

        /**
         * True in the state a home screen rests in: the display is switched on, it could start,
         * and nothing is running on it. It is the only state with room for an offer of its own —
         * the other two already have one thing for the user to do, and a second call to action
         * beside it is how a home screen starts to nag (see {@code DistroSetupStore#shouldOffer}).
         */
        public boolean resting() {
            return messageRes == R.string.termux_x11_no_display;
        }

        @Override
        @NonNull
        public String toString() {
            return "State{" + messageRes + ", start " + (startVisible ? "visible" : "hidden") + "}";
        }
    }

    private DisplayEmptyStatePolicy() {}

    /**
     * The Termux edition's answer. Kept for the callers that have no edition of their own to
     * hand in yet; {@link #decide(boolean, boolean, TourEdition)} is what a Display place calls.
     *
     * @param enabled         the Linux display setting
     * @param hasKeyboardData whether {@code xkeyboard-config}'s files are in the prefix, computed
     *                        outside this method
     */
    @NonNull
    public static State decide(boolean enabled, boolean hasKeyboardData) {
        return decide(enabled, hasKeyboardData, TourEdition.TERMUX);
    }

    /**
     * @param enabled         the Linux display setting
     * @param hasKeyboardData whether the XKB files are in the prefix, computed outside this
     *                        method — on nix by way of the link
     *                        {@link X11CliInstaller#linkKeyboardData()} keeps into the store
     * @param edition         which edition is asking; only the missing-data message differs
     */
    @NonNull
    public static State decide(boolean enabled, boolean hasKeyboardData,
                                @NonNull TourEdition edition) {
        if (!enabled) return new State(R.string.termux_x11_display_off, true);
        if (!hasKeyboardData) {
            return new State(edition == TourEdition.NIX
                ? R.string.termux_x11_needs_keyboard_data_nix
                : R.string.termux_x11_needs_keyboard_data, false);
        }
        return new State(R.string.termux_x11_no_display, true);
    }
}
