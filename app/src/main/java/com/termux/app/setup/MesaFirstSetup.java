package com.termux.app.setup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.AppEdition;
import com.termux.app.wayland.MesaWaylandOverlay;
import com.termux.app.x11.KeyboardDataInstall;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Graphics for Linux apps on first setup (Terl decisions, round 3, and the review rulings of
 * 2026-11-28): right after the bootstrap this install laid down, whatever the Terl switch, Termux's
 * {@code mesa} package and tlstore's {@code mesa-wayland-egl} add-on are installed at matching
 * versions ({@link MesaWaylandOverlay}'s rule), in the background and never in the launcher's way.
 * It is a first-setup step only: an existing install (an app update) is never armed, and once the
 * step is done or given up it never runs again, so a {@code mesa} the user removes stays removed.
 * It installs on any network (the developer's decision); offline it waits for one. The welcome
 * pages will take this step over when they exist.
 *
 * <p>Pure: what to do now, the script, and what the Setup card says.
 */
public final class MesaFirstSetup {

    private MesaFirstSetup() {}

    /** How long a failed try rests before the next. */
    public static final long RETRY_AFTER_MS = 30L * 60L * 1000L;

    /** Failed tries before the step is given up for good. */
    public static final int MAX_TRIES = 5;

    /** How long apt waits for another holder of the package lock (the wizard's, the user's pkg). */
    static final int LOCK_WAIT_SECONDS = 600;

    /** The add-on tlstore installs once Mesa is in. */
    static final String ADD_ON = MesaWaylandOverlay.ITEM_ID;

    /** Where the step stands for this install; stored, so a later start knows. */
    public enum Phase {
        /** Never armed: this install did not set up its bootstrap with this step (an update). */
        NOT_ARMED,
        /** Armed by a fresh bootstrap, not done yet. */
        PENDING,
        /** Final: both installed at matching versions. */
        DONE,
        /** Final: {@link #MAX_TRIES} tries failed. */
        GAVE_UP
    }

    /** What to do now. */
    public enum Step {
        /** Nothing, now or later: another edition, not a first setup, or a final phase. */
        SKIP,
        /** Both are in and match: the step is done. */
        DONE,
        /** Not yet: no prefix, no network, the package manager busy, or a failed try resting. */
        WAIT,
        /** Install (or bring the add-on up to the installed Mesa). */
        INSTALL
    }

    /**
     * The phase after a launcher start: a start that installed the bootstrap ({@code
     * installedBootstrapNow}) arms an install that was never armed; nothing else ever arms it.
     */
    @NonNull
    public static Phase onStart(@NonNull Phase stored, boolean installedBootstrapNow) {
        return stored == Phase.NOT_ARMED && installedBootstrapNow ? Phase.PENDING : stored;
    }

    /**
     * @param edition        this build's edition
     * @param phase          the stored phase
     * @param prefixReady    the bootstrap is in place (its package manager can run)
     * @param packagesBusy   the setup wizard's own install holds the package manager
     * @param overlay        the add-on's state now ({@link MesaWaylandOverlay#check})
     * @param online         some network reaches the internet (any kind: no metered wait)
     * @param now            the time now
     * @param lastFailureAt  when the last try failed, or 0 for never
     */
    @NonNull
    public static Step next(@NonNull AppEdition edition, @NonNull Phase phase, boolean prefixReady,
                            boolean packagesBusy, @NonNull MesaWaylandOverlay.State overlay,
                            boolean online, long now, long lastFailureAt) {
        if (edition != AppEdition.TERMUX || phase != Phase.PENDING) return Step.SKIP;
        if (overlay == MesaWaylandOverlay.State.USE) return Step.DONE;
        if (!prefixReady || packagesBusy || !online) return Step.WAIT;
        if (lastFailureAt > 0 && now - lastFailureAt >= 0 && now - lastFailureAt < RETRY_AFTER_MS)
            return Step.WAIT;
        return Step.INSTALL;
    }

    /** The phase after a try that ended {@code ok} with the add-on at {@code after}; {@code tries} counts this one. */
    @NonNull
    public static Phase afterTry(boolean ok, @NonNull MesaWaylandOverlay.State after, int tries) {
        if (ok && after == MesaWaylandOverlay.State.USE) return Phase.DONE;
        return tries >= MAX_TRIES ? Phase.GAVE_UP : Phase.PENDING;
    }

    /**
     * The script, run by bash in the prefix (KeyboardDataInstall's package route): it first
     * repairs a dpkg an earlier try left half-done, then on apt adds {@code x11-repo} (Termux keeps
     * {@code mesa} there), updates and upgrades and installs, every apt step waiting up to {@link
     * #LOCK_WAIT_SECONDS} for the lock; on pacman (told apart by {@link
     * KeyboardDataInstall#PACMAN_PREFIX_TEST}) it waits for the database lock, upgrades and
     * installs. Then tlstore installs the add-on. The first line records the process group, so a
     * timeout ends the whole run, apt and dpkg included.
     */
    @NonNull
    public static String script(@NonNull String pidFile) {
        String lock = "-o DPkg::Lock::Timeout=" + LOCK_WAIT_SECONDS;
        String apt = "{ dpkg --configure -a --force-confdef --force-confold || true; }"
            + " && pkg install -y " + lock + " x11-repo"
            + " && pkg update"
            + " && pkg upgrade -y " + lock + " " + KeyboardDataInstall.KEEP_CONFIG_FILES
            + " && pkg install -y " + lock + " " + KeyboardDataInstall.KEEP_CONFIG_FILES + " mesa";
        String pacman = "n=0; while [ -e \"$PREFIX/var/lib/pacman/db.lck\" ] && [ $n -lt "
            + (LOCK_WAIT_SECONDS / 5) + " ]; do sleep 5; n=$((n+1)); done"
            + " && pacman -Syu --noconfirm && pacman -S --noconfirm --needed mesa";
        return "echo $$ > \"" + pidFile + "\"\n"
            + "export DEBIAN_FRONTEND=noninteractive TLSTORE_ASSUME_YES=1\n"
            + "if " + KeyboardDataInstall.PACMAN_PREFIX_TEST + "; then " + pacman
            + "; else " + apt + "; fi"
            + " && tlstore install " + ADD_ON + "\n";
    }

    /**
     * The process group a timeout kills ({@code kill(-group)}), from the pid file the script wrote
     * under {@code setsid}; 0 when it cannot be read, so nothing but the shell is signalled.
     */
    public static int groupToKill(@Nullable String pidFileText) {
        if (pidFileText == null) return 0;
        try {
            int pid = Integer.parseInt(pidFileText.trim());
            return pid > 1 ? -pid : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final Pattern APT_SIZE = Pattern.compile("Need to get ([0-9.,]+ ?[kMG]?B)");
    private static final Pattern PACMAN_SIZE =
        Pattern.compile("Total Download Size:\\s+([0-9.,]+ ?[KMG]iB)");

    /** The size the run's output says it downloads (the last one apt or pacman named), or null. */
    @Nullable
    public static String downloadSize(@Nullable String output) {
        if (output == null) return null;
        String size = null;
        for (Pattern pattern : new Pattern[] {APT_SIZE, PACMAN_SIZE}) {
            Matcher matcher = pattern.matcher(output);
            while (matcher.find()) size = matcher.group(1);
        }
        return size;
    }

    /** What the Setup card says. */
    public enum CardState {
        /** No card: another edition, or not a first setup. */
        NONE,
        INSTALLING,
        /** Waiting: for a network, the package manager, or the next try. */
        WAITING,
        /** Both are in, but the add-on is for another Mesa version: it waits for an update. */
        MISMATCH,
        DONE,
        /** Given up after {@link #MAX_TRIES} tries. */
        FAILED
    }

    /** The card's state from the edition, the phase, whether a try runs, and the add-on's state. */
    @NonNull
    public static CardState card(@NonNull AppEdition edition, @NonNull Phase phase, boolean running,
                                 @NonNull MesaWaylandOverlay.State overlay) {
        if (edition != AppEdition.TERMUX) return CardState.NONE;
        switch (phase) {
            case DONE: return CardState.DONE;
            case GAVE_UP: return CardState.FAILED;
            case PENDING:
                if (running) return CardState.INSTALLING;
                if (overlay == MesaWaylandOverlay.State.USE) return CardState.DONE;
                return overlay == MesaWaylandOverlay.State.MISMATCH ? CardState.MISMATCH
                    : CardState.WAITING;
            default:
                return CardState.NONE;
        }
    }

    /** A stored phase name; anything else reads as never armed. */
    @NonNull
    public static Phase phaseOf(@Nullable String name) {
        if (name != null) {
            for (Phase phase : Phase.values()) if (phase.name().equals(name)) return phase;
        }
        return Phase.NOT_ARMED;
    }
}
