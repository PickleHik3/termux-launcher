package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tour.TourEdition;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The command behind the Display page's "Install xkeyboard-config" button: the X keyboard data a
 * display cannot start without, installed in a terminal window the user watches.
 *
 * <p>Nothing here runs anything: an edition goes in, an argv comes out, and a test reads every
 * branch of it back. The launcher opens a window running that argv and closes it when it exits.
 *
 * <h3>Which package manager</h3>
 *
 * The Termux edition ships an apt bootstrap, but a user can swap the prefix for Termux's pacman
 * bootstrap afterwards, and nothing on the Java side can tell: {@code TermuxBootstrap} only knows
 * APT, and the APK's {@code TERMUX_PACKAGE_VARIANT} is fixed at build time. So the choice is made
 * by the shell, with the same test {@code x11-gpu-setup.sh} already uses
 * ({@link #PACMAN_PREFIX_TEST}): pacman's own config is in the prefix and dpkg is not.
 *
 * <ul>
 *   <li><b>apt</b>: {@code x11-repo} first, because Termux keeps {@code xkeyboard-config} in that
 *       repository (see {@link GuiAppsSetup}); then a full update and upgrade, because installing
 *       onto stale libraries leaves a package built against versions the phone does not have.
 *   <li><b>pacman</b>: no {@code x11-repo} step — the x11 repository comes configured — and
 *       pacman itself rather than {@code pkg}, whose pacman branch passes {@code -y} through to
 *       pacman, where it means something else.
 *   <li><b>VAJ</b>: apt only, its own repository carries the package, so no {@code x11-repo}.
 *   <li><b>Nix</b>: nothing; that edition installs the data from {@code home.nix}.
 * </ul>
 *
 * <h3>Ending</h3>
 *
 * The window is the command's: a shell that exits closes its window whatever its status (that is
 * how a finished pane is dropped). So the script keeps it open itself when something failed — it
 * hands the window to the user's login shell, with the error still on screen above the prompt —
 * and simply ends when everything worked, which closes the window.
 */
public final class KeyboardDataInstall {

    /** The shell that runs the script: bash is in every apt and pacman prefix. */
    static final String SHELL = "bash";

    /**
     * True on a pacman prefix. The same test {@code assets/x11/x11-gpu-setup.sh} makes, so the two
     * never disagree about which package manager a phone has.
     */
    static final String PACMAN_PREFIX_TEST = "command -v pacman >/dev/null 2>&1"
        + " && [ -f \"$PREFIX/etc/pacman.conf\" ] && ! command -v dpkg >/dev/null 2>&1";

    /**
     * {@code pkg upgrade} is {@code apt full-upgrade "$@"}, so these reach apt and on to dpkg: a
     * config file the user changed is kept, one they never touched takes the package's new
     * version, and neither stops the upgrade to ask.
     */
    static final String KEEP_CONFIG_FILES =
        "-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold";

    /** What runs when the install failed: the user's own shell, so the window stays to read. */
    static final String ON_FAILURE = "exec \"${SHELL:-sh}\" -l";

    private KeyboardDataInstall() {}

    /**
     * The argv a terminal window runs to install the keyboard data on {@code edition}, or null
     * when that edition has no such command (nix).
     */
    @Nullable
    public static List<String> command(@NonNull TourEdition edition) {
        String script = script(edition);
        return script == null ? null
            : Collections.unmodifiableList(Arrays.asList(SHELL, "-c", script));
    }

    /** The script {@link #command} hands to {@link #SHELL}, or null on nix. */
    @Nullable
    static String script(@NonNull TourEdition edition) {
        switch (edition) {
            case NIX: return null;
            case VAJ: return "{ " + aptInstall(false) + "; } || " + ON_FAILURE;
            default:
                return "if " + PACMAN_PREFIX_TEST + "; then " + pacmanInstall()
                    + "; else " + aptInstall(true) + "; fi || " + ON_FAILURE;
        }
    }

    /** The apt route, with Termux's {@code x11-repo} added first when {@code x11Repo}. */
    @NonNull
    static String aptInstall(boolean x11Repo) {
        String install = "pkg update && pkg upgrade -y " + KEEP_CONFIG_FILES
            + " && pkg install -y " + GuiAppsSetup.KEYBOARD_DATA_PACKAGE;
        return x11Repo ? "pkg install -y x11-repo && " + install : install;
    }

    /** The pacman route: a full upgrade first, since pacman's {@code -Sy} alone is a partial one. */
    @NonNull
    static String pacmanInstall() {
        return "pacman -Syu --noconfirm && pacman -S --noconfirm --needed "
            + GuiAppsSetup.KEYBOARD_DATA_PACKAGE;
    }
}
