package com.termux.app.setup;

import androidx.annotation.NonNull;

import com.termux.app.AppEdition;
import com.termux.app.wayland.MesaWaylandOverlay;

/**
 * Graphics for Linux apps on first setup (Terl decisions, round 3): right after the bootstrap,
 * Termux's {@code mesa} package and tlstore's {@code mesa-wayland-egl} add-on are installed at
 * matching versions ({@link MesaWaylandOverlay}'s rule), in the background and never in the
 * launcher's way. Offline, or after a failed try, it waits and tries again quietly. The welcome
 * pages will take this step over when they exist.
 *
 * <p>Pure: what to do now, from the edition, the overlay's state and the last try.
 */
public final class MesaFirstSetup {

    private MesaFirstSetup() {}

    /** How long a failed try waits before the next, while the phone stays online. */
    public static final long RETRY_AFTER_MS = 30L * 60L * 1000L;

    /** The command that installs both, in a shell with the prefix's environment. */
    public static final String SCRIPT = "set -e\n"
        + "export DEBIAN_FRONTEND=noninteractive TLSTORE_ASSUME_YES=1\n"
        + "if command -v pacman >/dev/null 2>&1; then pacman -S --needed --noconfirm mesa;"
        + " else pkg install -y mesa; fi\n"
        + "tlstore install " + MesaWaylandOverlay.ITEM_ID + "\n";

    /** What to do now. */
    public enum Step {
        /** Not for this edition: the add-on is built for the stock package only. No card. */
        SKIP,
        /** Both are in and match: nothing to do. */
        DONE,
        /** Not yet: no prefix, no network, or a failed try is still resting. */
        WAIT,
        /** Install (or bring the add-on up to the installed Mesa). */
        INSTALL
    }

    /**
     * @param edition       this build's edition
     * @param prefixReady   the bootstrap is in place (its package manager can run)
     * @param overlay       the add-on's state now ({@link MesaWaylandOverlay#check})
     * @param online        the phone has a network
     * @param now           the time now
     * @param lastFailureAt when the last try failed, or 0 for never
     */
    @NonNull
    public static Step next(@NonNull AppEdition edition, boolean prefixReady,
                            @NonNull MesaWaylandOverlay.State overlay, boolean online, long now,
                            long lastFailureAt) {
        if (edition != AppEdition.TERMUX) return Step.SKIP;
        if (overlay == MesaWaylandOverlay.State.USE) return Step.DONE;
        if (!prefixReady || !online) return Step.WAIT;
        if (lastFailureAt > 0 && now - lastFailureAt >= 0 && now - lastFailureAt < RETRY_AFTER_MS)
            return Step.WAIT;
        return Step.INSTALL;
    }

    /** What the Setup card says. */
    public enum CardState {
        /** No card (another edition). */
        NONE,
        INSTALLING,
        /** Waiting for a network or the next try. */
        WAITING,
        /** Both are in, but the add-on was built for another Mesa: it waits for a matching one. */
        MISMATCH,
        DONE
    }

    /** The card's state from the step, whether a try is running, and the overlay's state. */
    @NonNull
    public static CardState card(@NonNull Step step, boolean running,
                                 @NonNull MesaWaylandOverlay.State overlay) {
        if (step == Step.SKIP) return CardState.NONE;
        if (step == Step.DONE) return CardState.DONE;
        if (running) return CardState.INSTALLING;
        if (overlay == MesaWaylandOverlay.State.MISMATCH) return CardState.MISMATCH;
        return CardState.WAITING;
    }
}
