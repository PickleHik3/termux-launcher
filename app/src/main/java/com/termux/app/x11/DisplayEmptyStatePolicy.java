package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.AppEdition;

/**
 * What the display window's empty state says while no server is running: the plain "nothing
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
 * in nix's own words instead of naming a {@code pkg} command that edition does not have, and the
 * guide where the other editions get a button that installs the package.
 */
public final class DisplayEmptyStatePolicy {

    /** The empty state's message and whether its start control is out. */
    public static final class State {
        @StringRes public final int messageRes;
        public final boolean startVisible;
        /** The server's own words for why a Start ended before a display came up, else null. */
        @Nullable public final String failureReason;

        private State(@StringRes int messageRes, boolean startVisible) {
            this(messageRes, startVisible, null);
        }

        private State(@StringRes int messageRes, boolean startVisible,
                      @Nullable String failureReason) {
            this.messageRes = messageRes;
            this.startVisible = startVisible;
            this.failureReason = failureReason;
        }

        /**
         * True while the message is the reason the last Start died before a display connected.
         * The start control stays out, and the window links to Startup and support.
         */
        public boolean failed() {
            return failureReason != null;
        }

        /**
         * True while the message names the missing package on an edition that installs it with
         * {@code pkg}: the button runs {@link KeyboardDataInstall}'s command for it.
         */
        public boolean installVisible() {
            return messageRes == R.string.termux_x11_needs_keyboard_data;
        }

        /**
         * True while nix's message names the missing package. Nix installs it from
         * {@code home.nix}, which the launcher does not edit, so that edition keeps the route to
         * the guide instead of an install button.
         */
        public boolean guideVisible() {
            return messageRes == R.string.termux_x11_needs_keyboard_data_nix;
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
     * hand in yet; {@link #decide(boolean, boolean, AppEdition)} is what a Display place calls.
     *
     * @param enabled         the Linux display setting
     * @param hasKeyboardData whether {@code xkeyboard-config}'s files are in the prefix, computed
     *                        outside this method
     */
    @NonNull
    public static State decide(boolean enabled, boolean hasKeyboardData) {
        return decide(enabled, hasKeyboardData, AppEdition.TERMUX);
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
                                @NonNull AppEdition edition) {
        return decide(enabled, hasKeyboardData, edition, null);
    }

    /**
     * @param startFailure why the last Start died before a display connected (the caller's
     *                     {@link X11StartFailure#reason} reading), or null when it did not or the
     *                     next Start has cleared it. It speaks over the resting state only: a
     *                     setting that is off or missing keyboard data is the thing to fix first.
     */
    @NonNull
    public static State decide(boolean enabled, boolean hasKeyboardData,
                                @NonNull AppEdition edition, @Nullable String startFailure) {
        if (!enabled) return new State(R.string.termux_x11_display_off, true);
        if (!hasKeyboardData) {
            return new State(edition == AppEdition.NIX
                ? R.string.termux_x11_needs_keyboard_data_nix
                : R.string.termux_x11_needs_keyboard_data, false);
        }
        if (startFailure != null) {
            return new State(R.string.termux_x11_start_failed, true, startFailure);
        }
        return new State(R.string.termux_x11_no_display, true);
    }
}
