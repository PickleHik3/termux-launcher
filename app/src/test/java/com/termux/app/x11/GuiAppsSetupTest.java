package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;
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

    /** The done-marker step every Termux-route command now ends with. */
    private static final String X11_MARKER = " && mkdir -p \"$PREFIX/var/lib/termux-launcher\" && printf '%s\\n' 'x11' > \"$PREFIX/var/lib/termux-launcher/gui-apps-setup\"";

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

    @Test public void theX11RouteAlwaysIncludesTheSessionBus() {
        // D6: dbus rides along like the keyboard data, whatever was ticked.
        assertTrue(command(Route.X11_REPO, Distro.DEBIAN, ALL).contains(" dbus"));
        assertTrue(command(Route.X11_REPO, Distro.DEBIAN, NONE).contains(" dbus"));
    }

    @Test public void termuxsX11RouteInstallsFcitx5ForTextFieldFocusAndVajsDoesNot() {
        for (Set<StarterApp> starters : java.util.Arrays.asList(ALL, NONE)) {
            String termux = command(Route.X11_REPO, Distro.DEBIAN, starters);
            assertTrue(termux.contains(" fcitx5 fcitx5-gtk3 fcitx5-qt pulseaudio"));
            assertFalse(GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN, starters,
                AppEdition.VAJ).contains("fcitx5"));
            assertFalse(command(Route.DISTRO, Distro.DEBIAN, starters).contains("fcitx5"));
        }
    }

    @Test public void termuxsX11RouteInstallsPulseaudioForSoundAndVajsDoesNot() {
        for (Set<StarterApp> starters : java.util.Arrays.asList(ALL, NONE)) {
            assertTrue(command(Route.X11_REPO, Distro.DEBIAN, starters).contains(" pulseaudio"));
            assertFalse(GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN, starters,
                AppEdition.VAJ).contains("pulseaudio"));
        }
    }

    @Test public void theDistroRouteAsksForTheSoundServerOnTheTermuxSideWithoutStoppingForIt() {
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, NONE);

            assertTrue(distro.name(), command.contains(
                "command -v pulseaudio >/dev/null 2>&1 || pkg install -y pulseaudio || true\n"));
            assertTrue(command.indexOf("pkg install -y pulseaudio")
                < command.indexOf("proot-distro login"));
        }
    }

    @Test public void aDistroGetsAlsasPulseaudioPluginAndAnAsoundConfOnlyWhereNoneIsTheUsers() {
        assertEquals("libasound2-plugins", Distro.DEBIAN.alsaPackages);
        assertEquals("libasound2-plugins", Distro.UBUNTU.alsaPackages);
        assertEquals("alsa-plugins libpulse", Distro.ARCH.alsaPackages);
        for (Distro distro : Distro.values()) {
            String inside = GuiAppsSetup.insideScript(distro, NONE);

            for (String name : distro.alsaPackages.split(" ")) {
                assertTrue(distro.name() + " " + name, inside.contains(" " + name));
            }
            assertTrue(inside.endsWith("if [ ! -e /etc/asound.conf ] || grep -q 'written by "
                + "termux-launcher' /etc/asound.conf; then printf '%s' "
                + GuiAppsSetup.singleQuote(GuiAppsSetup.ASOUND_CONF) + " > /etc/asound.conf; fi"));
            assertTrue(inside.indexOf(distro.alsaPackages.split(" ")[0])
                < inside.indexOf("/etc/asound.conf"));
        }
        assertTrue(GuiAppsSetup.ASOUND_CONF.startsWith("# written by termux-launcher"));
        assertTrue(GuiAppsSetup.ASOUND_CONF.contains("\npcm.!default { type pulse }\n"));
        assertTrue(GuiAppsSetup.ASOUND_CONF.contains("\nctl.!default { type pulse }\n"));
        // It rides inside the single-quoted container script, so it must hold no quote itself.
        assertFalse(GuiAppsSetup.ASOUND_CONF.contains("'"));
    }

    @Test public void theX11RouteWithNothingTickedIsJustTheRepository() {
        assertEquals("pkg install -y x11-repo && pkg install -y xkeyboard-config dbus openbox fcitx5 fcitx5-gtk3 fcitx5-qt pulseaudio" + X11_MARKER,
            command(Route.X11_REPO, Distro.DEBIAN, NONE));
    }

    @Test public void theX11RouteInstallsOnlyWhatIsTicked() {
        String command = command(Route.X11_REPO, Distro.DEBIAN,
            EnumSet.of(StarterApp.TEXT_EDITOR));

        assertEquals("pkg install -y x11-repo && pkg install -y xkeyboard-config dbus openbox fcitx5 fcitx5-gtk3 fcitx5-qt pulseaudio mousepad" + X11_MARKER,
            command);
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
            assertTrue(command.contains("pkg install -y xkeyboard-config"));
            // The window manager is the Termux side's, whichever route the apps came from.
            assertTrue(command.contains("command -v openbox >/dev/null 2>&1 || {"));
            assertTrue(command.contains("pkg install -y openbox; }"));
            assertTrue(distro.name(), command.contains(
                "[ -d \"$PREFIX/var/lib/proot-distro/containers/" + distro.alias
                    + "/rootfs\" ] || " + GuiAppsSetup.installStep(distro)));
        }
        assertEquals("proot-distro install debian", GuiAppsSetup.installStep(Distro.DEBIAN));
        assertEquals("proot-distro install ubuntu", GuiAppsSetup.installStep(Distro.UBUNTU));
    }

    @Test public void archOnArm64ComesFromTheArchLinuxArmImageUnderTheSameName() {
        String step = GuiAppsSetup.installStep(Distro.ARCH);

        assertTrue(step.contains("if [ \"$(uname -m)\" = aarch64 ]; then\n"
            + "proot-distro install menci/archlinuxarm --name archlinux\n"
            + "else\nproot-distro install archlinux\nfi"));
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
                "libgl1-mesa-dri", "dbus-x11", "firefox-esr", "pcmanfm", "mousepad", "xterm"}) {
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
                "libgl1-mesa-dri", "dbus-x11", "pcmanfm", "mousepad", "xterm"}) {
            assertTrue(name, command.contains(name));
        }
    }

    @Test public void archUsesPacmanAndArchsOwnNames() {
        String command = command(Route.DISTRO, Distro.ARCH, ALL);

        assertTrue(command.contains("pacman -Syu --noconfirm"));
        assertTrue(command.contains("pacman -S --needed --noconfirm "));
        assertFalse(command.contains("apt-get"));
        for (String name : new String[] {"xorg-fonts-misc", "ttf-dejavu", "mesa", "dbus",
                "firefox", "pcmanfm", "mousepad", "xterm"}) {
            assertTrue(name, command.contains(name));
        }
    }

    @Test public void everyDistroInstallsItsOwnBusPackage() {
        // D6: dbus-launch is split into dbus-x11 on Debian/Ubuntu but lives in dbus itself on
        // Arch (verified against packages.debian.org, packages.ubuntu.com and archlinux.org).
        assertEquals("dbus-x11", Distro.DEBIAN.busPackage);
        assertEquals("dbus-x11", Distro.UBUNTU.busPackage);
        assertEquals("dbus", Distro.ARCH.busPackage);
        for (Distro distro : Distro.values()) {
            assertTrue(distro.name(),
                command(Route.DISTRO, distro, NONE).contains(distro.busPackage));
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

    @Test public void theCommandEndsByWritingTheDoneMarkerOnlyAfterEverythingSucceeded() {
        String marker = "mkdir -p \"$PREFIX/var/lib/termux-launcher\" && printf '%s\\n' '";
        String x11 = command(Route.X11_REPO, Distro.DEBIAN, ALL);
        assertTrue(x11, x11.contains(" && " + marker));
        assertTrue(x11, x11.endsWith("/gui-apps-setup\""));
        for (Distro distro : Distro.values()) {
            String command = command(Route.DISTRO, distro, ALL);
            int at = command.indexOf(marker);
            assertTrue(distro.name(), at > command.indexOf("proot-distro login"));
            assertTrue(distro.name(), at < command.indexOf("Done."));
            assertTrue(distro.name(), command.startsWith("( set -e\n"));
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
            GuiAppsSetup.routesFor(AppEdition.TERMUX));
        assertEquals(Route.X11_REPO, GuiAppsSetup.defaultRoute(AppEdition.TERMUX));
    }

    @Test public void vajOffersBothRoutesLikeTermux() {
        // Reversed 2026-09-21: repo.pathayam.xyz now carries xkeyboard-config and the x11 apps,
        // so VAJ's X11 route works there too.
        assertEquals(Arrays.asList(Route.X11_REPO, Route.DISTRO),
            GuiAppsSetup.routesFor(AppEdition.VAJ));
        assertEquals(Route.X11_REPO, GuiAppsSetup.defaultRoute(AppEdition.VAJ));
    }

    @Test public void nixOffersNoRoute() {
        // Decision, 2026-09-20: graphical apps on nix come from nixpkgs and home.nix, a third
        // way this class builds no command for.
        assertTrue(GuiAppsSetup.routesFor(AppEdition.NIX).isEmpty());
        assertEquals(null, GuiAppsSetup.defaultRoute(AppEdition.NIX));
    }

    // --- VAJ's edition-aware X11 command --------------------------------------------------------

    @Test public void vajsX11CommandHasNoSeparateRepoStepAndDefaultsToPcmanfmAndMousepad() {
        String command = GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN,
            GuiAppsSetup.defaultStarters(), AppEdition.VAJ);

        assertEquals("pkg install -y xkeyboard-config dbus pcmanfm mousepad && { pkg install -y openbox || true; }" + X11_MARKER, command);
    }

    @Test public void vajsX11CommandAddsTheTerminalWhenTicked() {
        Set<StarterApp> starters = EnumSet.copyOf(GuiAppsSetup.defaultStarters());
        starters.add(StarterApp.TERMINAL);
        String command = GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN, starters,
            AppEdition.VAJ);

        assertEquals("pkg install -y xkeyboard-config dbus pcmanfm mousepad xfce4-terminal && { pkg install -y openbox || true; }" + X11_MARKER,
            command);
    }

    @Test public void vajsX11CommandDropsTheBrowserEvenWhenTicked() {
        String command = GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN,
            EnumSet.of(StarterApp.BROWSER), AppEdition.VAJ);

        // VAJ ships no browser at all; ticking it adds nothing to the command.
        assertEquals("pkg install -y xkeyboard-config dbus && { pkg install -y openbox || true; }" + X11_MARKER, command);
        assertFalse(command.contains("firefox"));
    }

    @Test public void termuxsX11CommandIsUnchangedByTheEditionAwareOverload() {
        assertEquals(GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN, ALL),
            GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN, ALL, AppEdition.TERMUX));
        assertEquals("pkg install -y x11-repo && pkg install -y "
                + "xkeyboard-config dbus openbox fcitx5 fcitx5-gtk3 fcitx5-qt pulseaudio firefox pcmanfm mousepad xfce4-terminal" + X11_MARKER,
            GuiAppsSetup.command(Route.X11_REPO, Distro.DEBIAN, ALL, AppEdition.TERMUX));
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

    /**
     * The Starter Apps hints: the name of the app each tick would actually fetch, which is the
     * whole reason the rows carry one - Ubuntu's browser is not Debian's.
     */
    @Test public void theStarterHintNamesTheBrowserEachChoiceWouldFetch() {
        assertEquals("Firefox ESR", GuiAppsSetup.starterAppName(
            GuiAppsSetup.Route.DISTRO, GuiAppsSetup.Distro.DEBIAN, GuiAppsSetup.StarterApp.BROWSER));
        assertEquals("Falkon", GuiAppsSetup.starterAppName(
            GuiAppsSetup.Route.DISTRO, GuiAppsSetup.Distro.UBUNTU, GuiAppsSetup.StarterApp.BROWSER));
        assertEquals("Firefox", GuiAppsSetup.starterAppName(
            GuiAppsSetup.Route.DISTRO, GuiAppsSetup.Distro.ARCH, GuiAppsSetup.StarterApp.BROWSER));
        // The X11 route installs Termux's own packages, whatever the distro row happens to say.
        assertEquals("Firefox", GuiAppsSetup.starterAppName(
            GuiAppsSetup.Route.X11_REPO, GuiAppsSetup.Distro.UBUNTU,
            GuiAppsSetup.StarterApp.BROWSER));
    }

    @Test public void theFileManagerIsTheSameOneEverywhere() {
        for (GuiAppsSetup.Distro distro : GuiAppsSetup.Distro.values()) {
            assertEquals("PCManFM", GuiAppsSetup.starterAppName(
                GuiAppsSetup.Route.DISTRO, distro, GuiAppsSetup.StarterApp.FILE_MANAGER));
        }
        assertEquals("PCManFM", GuiAppsSetup.starterAppName(
            GuiAppsSetup.Route.X11_REPO, GuiAppsSetup.Distro.DEBIAN,
            GuiAppsSetup.StarterApp.FILE_MANAGER));
    }
}
