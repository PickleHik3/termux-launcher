package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.tour.TourEdition;
import com.termux.app.x11.GuiAppsSetup.Distro;
import com.termux.app.x11.GuiAppsSetup.Route;
import com.termux.app.x11.GuiAppsSetup.StarterApp;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * The one command the Get GUI apps screen hands the user, read back for every route, every distro
 * and every set of ticks. A wrong package name or a lost quote is a command that stops halfway
 * with the user watching, so both are checked here rather than on a phone.
 */
public class GuiAppsSetupTest {

    private static final Set<StarterApp> ALL = EnumSet.allOf(StarterApp.class);
    private static final Set<StarterApp> NONE = EnumSet.noneOf(StarterApp.class);

    private String command(Route route, Distro distro, Set<StarterApp> starters) {
        return GuiAppsSetup.command(route, distro, starters);
    }

    // --- route one: Termux's own X11 packages -------------------------------------------------

    @Test public void theX11RouteAddsTheRepositoryBeforeTheApps() {
        String command = command(Route.X11_REPO, Distro.DEBIAN, ALL);

        assertTrue(command.startsWith("pkg install -y x11-repo && pkg install -y "));
        assertTrue(command.indexOf("x11-repo") < command.indexOf("firefox"));
    }

    @Test public void theX11RouteNamesTermuxsOwnPackages() {
        String command = command(Route.X11_REPO, Distro.DEBIAN, ALL);

        assertTrue(command.contains("firefox"));
        assertTrue(command.contains("pcmanfm"));
        assertTrue(command.contains("mousepad"));
        assertTrue(command.contains("xfce4-terminal"));
        // The x11 repository has no xterm, so a command naming it would never install.
        assertFalse(command.contains(" xterm"));
    }

    @Test public void theX11RouteWithNothingTickedIsJustTheRepository() {
        assertEquals("pkg install -y x11-repo", command(Route.X11_REPO, Distro.DEBIAN, NONE));
    }

    @Test public void theX11RouteInstallsOnlyWhatIsTicked() {
        String command = command(Route.X11_REPO, Distro.DEBIAN,
            EnumSet.of(StarterApp.TEXT_EDITOR));

        assertEquals("pkg install -y x11-repo && pkg install -y mousepad", command);
    }

    @Test public void theX11RouteIgnoresTheDistroChoice() {
        for (Distro distro : Distro.values()) {
            assertEquals(command(Route.X11_REPO, Distro.DEBIAN, ALL),
                command(Route.X11_REPO, distro, ALL));
        }
    }

    // --- route two: a distro container --------------------------------------------------------

    @Test public void theDistroRouteInstallsTheToolAndTheContainerOnlyWhenMissing() {
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, ALL);

            assertTrue(distro.name(), command.contains(
                "command -v proot-distro >/dev/null 2>&1 || pkg install -y proot-distro"));
            assertTrue(distro.name(), command.contains(
                "[ -d \"$PREFIX/var/lib/proot-distro/containers/" + distro.alias
                    + "/rootfs\" ] || proot-distro install " + distro.alias));
        }
    }

    @Test public void theDistroRouteAsksForANameAndWillNotTakeABadOne() {
        String command = command(Route.DISTRO, Distro.DEBIAN, ALL);

        assertTrue(command.contains("printf 'Choose a username for Linux: '"));
        assertTrue(command.contains("read -r LUSER"));
        assertTrue(command.contains("while [ -z \"$LUSER\" ]; do"));
        assertTrue(command.contains("*[!a-z0-9_]*"));
        assertTrue(command.contains("tr 'A-Z' 'a-z'"));
    }

    @Test public void theNameIsCarriedIntoTheContainerAndUsedThere() {
        String command = command(Route.DISTRO, Distro.DEBIAN, ALL);

        assertTrue(command.contains(
            "proot-distro login debian -e LUSER=\"$LUSER\" -- /bin/sh -c 'set -e\n"));
        assertTrue(command.contains("id \"$LUSER\" >/dev/null 2>&1 || useradd -m -s "));
        assertTrue(command.contains("passwd \"$LUSER\""));
    }

    @Test public void thePasswordIsAskedForInTheContainerAndNeverWrittenDown() {
        String command = command(Route.DISTRO, Distro.DEBIAN, ALL);

        assertEquals(1, command.split("passwd ", -1).length - 1);
        // The record file holds the name and nothing else.
        assertFalse(command.contains("PASSWORD"));
    }

    @Test public void theNameIsRecordedBesideTheContainerAfterTheLoginReturns() {
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, ALL);
            String write = "printf '%s\\n' \"$LUSER\" > \"$PREFIX/var/lib/proot-distro/containers/"
                + distro.alias + "/launcher-user\"";

            assertTrue(distro.name(), command.contains(write));
            assertTrue(distro.name(),
                command.indexOf("proot-distro login " + distro.alias) < command.indexOf(write));
        }
    }

    @Test public void everyDistroRouteEndsOnTheOneClosingLine() {
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, ALL);

            assertTrue(distro.name(), command.contains("printf '%s\\n' 'Done. "
                + "Your apps appear in the app drawer under Linux apps.'"));
            assertTrue(distro.name(), command.endsWith("\n)"));
        }
    }

    @Test public void debianNamesDebiansPackages() {
        String command = command(Route.DISTRO, Distro.DEBIAN, ALL);

        assertTrue(command.contains("apt-get update"));
        assertTrue(command.contains("DEBIAN_FRONTEND=noninteractive apt-get install -y "));
        for (String name : new String[] {"xfonts-base", "fonts-dejavu-core", "libgl1",
                "libgl1-mesa-dri", "firefox-esr", "pcmanfm", "mousepad", "xterm"}) {
            assertTrue(name, command.contains(name));
        }
    }

    @Test public void ubuntuNeverNamesFirefox() {
        String command = command(Route.DISTRO, Distro.UBUNTU, ALL);

        // On Ubuntu the firefox deb is a stub for the snap, and a snap cannot run under proot.
        assertFalse(command.contains("firefox"));
        assertTrue(command.contains("falkon"));
    }

    @Test public void ubuntuNamesTheRestOfDebiansPackages() {
        String command = command(Route.DISTRO, Distro.UBUNTU, ALL);

        for (String name : new String[] {"xfonts-base", "fonts-dejavu-core", "libgl1",
                "libgl1-mesa-dri", "pcmanfm", "mousepad", "xterm"}) {
            assertTrue(name, command.contains(name));
        }
    }

    @Test public void archUsesPacmanAndArchsOwnNames() {
        String command = command(Route.DISTRO, Distro.ARCH, ALL);

        assertTrue(command.contains("pacman -Syu --noconfirm"));
        assertTrue(command.contains("pacman -S --needed --noconfirm "));
        assertFalse(command.contains("apt-get"));
        for (String name : new String[] {"xorg-fonts-misc", "ttf-dejavu", "mesa", "firefox",
                "pcmanfm", "mousepad", "xterm"}) {
            assertTrue(name, command.contains(name));
        }
    }

    @Test public void theContainerAliasIsWhatProotDistroKnows() {
        assertEquals("debian", Distro.DEBIAN.alias);
        assertEquals("ubuntu", Distro.UBUNTU.alias);
        assertEquals("archlinux", Distro.ARCH.alias);
    }

    @Test public void fontsAndGraphicsGoInWhateverIsTicked() {
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, NONE);

            for (String name : distro.fontPackages.split(" ")) {
                assertTrue(distro.name() + " " + name, command.contains(name));
            }
            for (String name : distro.graphicsPackages.split(" ")) {
                assertTrue(distro.name() + " " + name, command.contains(name));
            }
        }
    }

    @Test public void anUntickedAppIsNotInstalled() {
        String command = command(Route.DISTRO, Distro.DEBIAN, EnumSet.of(StarterApp.BROWSER));

        assertTrue(command.contains("firefox-esr"));
        assertFalse(command.contains("pcmanfm"));
        assertFalse(command.contains("mousepad"));
        assertFalse(command.contains("xterm"));
    }

    @Test public void oneRefreshAndOneInstallPerRun() {
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, ALL);
            String refresh = distro.packageManager == GuiAppsSetup.PackageManager.APT
                ? "apt-get update" : "pacman -Syu";

            assertEquals(distro.name(), 1, command.split(refresh, -1).length - 1);
        }
    }

    @Test public void theWholeThingIsOneSubshellSoAFailureCannotTakeTheUsersShellDown() {
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, ALL);

            assertTrue(distro.name(), command.startsWith("( set -e\n"));
            assertTrue(distro.name(), command.endsWith(")"));
        }
    }

    @Test public void nothingEndsWithANewlineSoTheUsersOwnEnterStartsIt() {
        for (Route route : Route.values()) {
            for (Distro distro : Distro.values()) {
                String command = command(route, distro, ALL);
                assertFalse(route + " " + distro, command.endsWith("\n"));
            }
        }
    }

    // --- defaults and stored choices ----------------------------------------------------------

    @Test public void theDefaultTicksAreABrowserAFileManagerAndAnEditor() {
        assertEquals(EnumSet.of(StarterApp.BROWSER, StarterApp.FILE_MANAGER,
            StarterApp.TEXT_EDITOR), GuiAppsSetup.defaultStarters());
    }

    // --- which routes an edition offers -------------------------------------------------------

    @Test public void termuxOffersBothRoutes() {
        assertEquals(Arrays.asList(Route.X11_REPO, Route.DISTRO),
            GuiAppsSetup.routesFor(TourEdition.TERMUX));
        assertEquals(Route.X11_REPO, GuiAppsSetup.defaultRoute(TourEdition.TERMUX));
    }

    @Test public void vajOffersOnlyTheDistroRoute() {
        assertEquals(Arrays.asList(Route.DISTRO), GuiAppsSetup.routesFor(TourEdition.VAJ));
        assertEquals(Route.DISTRO, GuiAppsSetup.defaultRoute(TourEdition.VAJ));
    }

    @Test public void nixOffersNoRoute() {
        // Decision, 2026-09-20: graphical apps on nix come from nixpkgs and home.nix, a third
        // way this class builds no command for.
        assertTrue(GuiAppsSetup.routesFor(TourEdition.NIX).isEmpty());
        assertEquals(null, GuiAppsSetup.defaultRoute(TourEdition.NIX));
    }

    @Test public void anUnknownStoredChoiceFallsBackToTheRecommendedOne() {
        assertEquals(Route.X11_REPO, Route.of(null));
        assertEquals(Route.X11_REPO, Route.of("nonsense"));
        assertEquals(Route.DISTRO, Route.of("distro"));
        assertEquals(Distro.DEBIAN, Distro.of(null));
        assertEquals(Distro.DEBIAN, Distro.of("nonsense"));
        assertEquals(Distro.ARCH, Distro.of("arch"));
    }

    // --- the command is a command -------------------------------------------------------------

    @Test public void everyCommandParsesAsAShellScript() throws IOException, InterruptedException {
        File shell = shell();
        for (Route route : Route.values()) {
            for (Distro distro : Distro.values()) {
                for (Set<StarterApp> starters : Arrays.asList(ALL, NONE,
                        GuiAppsSetup.defaultStarters())) {
                    String command = command(route, distro, starters);
                    String label = route + " " + distro + " " + starters;
                    if (shell == null) assertBalancedQuotes(label, command);
                    else assertParses(shell, label, command);
                }
            }
        }
    }

    /** A POSIX shell to check the quoting with, or null on a host that has none. */
    private File shell() {
        for (String path : new String[] {"/bin/sh", "/usr/bin/sh", "/bin/dash", "/bin/bash"}) {
            File file = new File(path);
            if (file.canExecute()) return file;
        }
        return null;
    }

    /** {@code sh -n}: the shell reads the whole thing and runs none of it. */
    private void assertParses(File shell, String label, String command)
            throws IOException, InterruptedException {
        File script = File.createTempFile("gui-apps-setup", ".sh");
        script.deleteOnExit();
        Files.write(script.toPath(), command.getBytes(StandardCharsets.UTF_8));
        Process process = new ProcessBuilder(shell.getAbsolutePath(), "-n",
            script.getAbsolutePath()).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        int read;
        while ((read = process.getInputStream().read()) != -1) output.append((char) read);
        assertEquals(label + "\n" + output + "\n--- command ---\n" + command,
            0, process.waitFor());
    }

    /** The fallback where no shell exists: every quote opened is closed again. */
    private void assertBalancedQuotes(String label, String command) {
        int single = 0;
        int doubles = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'') single++;
            else if (c == '"') doubles++;
        }
        assertEquals(label + " single quotes", 0, single % 2);
        assertEquals(label + " double quotes", 0, doubles % 2);
    }
}
