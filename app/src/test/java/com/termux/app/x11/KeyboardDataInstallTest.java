package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.tour.TourEdition;

import org.junit.Test;

import java.util.List;

/**
 * The Display page's install command, per edition and per package manager: what the window runs,
 * in what order, and how it ends.
 */
public class KeyboardDataInstallTest {

    @Test public void theCommandIsTheScriptHandedToBash() {
        List<String> command = KeyboardDataInstall.command(TourEdition.TERMUX);
        assertNotNull(command);
        assertEquals(3, command.size());
        assertEquals("bash", command.get(0));
        assertEquals("-c", command.get(1));
        assertEquals(KeyboardDataInstall.script(TourEdition.TERMUX), command.get(2));
    }

    @Test public void nixHasNoCommand() {
        assertNull("nix installs the data from home.nix",
            KeyboardDataInstall.command(TourEdition.NIX));
        assertNull(KeyboardDataInstall.script(TourEdition.NIX));
    }

    /** Termux on apt: the repository that carries the package first, then the developer's line. */
    @Test public void termuxAptAddsX11RepoFirst() {
        assertEquals("pkg install -y x11-repo && pkg update && pkg upgrade -y "
                + "-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold "
                + "&& pkg install -y xkeyboard-config",
            KeyboardDataInstall.aptInstall(true));
    }

    @Test public void termuxUsesTheAptRouteWithX11RepoUnlessThePrefixIsPacman() {
        String script = KeyboardDataInstall.script(TourEdition.TERMUX);
        assertNotNull(script);
        assertEquals("if " + KeyboardDataInstall.PACMAN_PREFIX_TEST
                + "; then " + KeyboardDataInstall.pacmanInstall()
                + "; else " + KeyboardDataInstall.aptInstall(true)
                + "; fi || " + KeyboardDataInstall.ON_FAILURE,
            script);
    }

    /** The test is x11-gpu-setup.sh's own: pacman's config in the prefix and no dpkg. */
    @Test public void aPacmanPrefixIsTheOneWithPacmanConfigAndNoDpkg() {
        String test = KeyboardDataInstall.PACMAN_PREFIX_TEST;
        assertTrue(test.contains("command -v pacman"));
        assertTrue(test.contains("[ -f \"$PREFIX/etc/pacman.conf\" ]"));
        assertTrue(test.contains("! command -v dpkg"));
    }

    /** Pacman's x11 repository comes configured, and pacman never asks with --noconfirm. */
    @Test public void pacmanSkipsX11RepoAndNeverAsks() {
        String pacman = KeyboardDataInstall.pacmanInstall();
        assertEquals("pacman -Syu --noconfirm && pacman -S --noconfirm --needed xkeyboard-config",
            pacman);
        assertFalse(pacman.contains("x11-repo"));
        assertFalse("pkg's pacman branch would read -y as a refresh", pacman.contains("pkg "));
    }

    /** VAJ's own repository carries the package: no x11-repo, and no pacman branch. */
    @Test public void vajIsTheDevelopersLineWithoutX11Repo() {
        String script = KeyboardDataInstall.script(TourEdition.VAJ);
        assertNotNull(script);
        assertEquals("{ pkg update && pkg upgrade -y "
                + "-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold "
                + "&& pkg install -y xkeyboard-config; } || " + KeyboardDataInstall.ON_FAILURE,
            script);
        assertFalse(script.contains("x11-repo"));
        assertFalse(script.contains("pacman"));
    }

    /**
     * A finished shell closes its window whatever its status, so success simply ends and failure
     * keeps the window by handing it to the user's own shell, the error still above the prompt.
     */
    @Test public void onlyAFailureKeepsTheWindowOpen() {
        assertEquals("exec \"${SHELL:-sh}\" -l", KeyboardDataInstall.ON_FAILURE);
        for (TourEdition edition : new TourEdition[] {TourEdition.TERMUX, TourEdition.VAJ}) {
            String script = KeyboardDataInstall.script(edition);
            assertNotNull(script);
            assertTrue(edition + " falls back only on failure",
                script.endsWith(" || " + KeyboardDataInstall.ON_FAILURE));
            assertFalse(edition + " has no unconditional exit or exec",
                script.contains("; exit") || script.contains("; exec"));
        }
    }

    /** The upgrade keeps the user's changed config files instead of stopping to ask about them. */
    @Test public void theUpgradeNeverStopsOnAConfigFileQuestion() {
        for (boolean x11Repo : new boolean[] {true, false}) {
            String apt = KeyboardDataInstall.aptInstall(x11Repo);
            assertTrue(apt.contains("pkg upgrade -y " + KeyboardDataInstall.KEEP_CONFIG_FILES));
        }
    }
}
