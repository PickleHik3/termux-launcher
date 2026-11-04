package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/** An installed distro is known from the disk and leaves the install step out of the command. */
public class GuiAppsSetupInstalledDistroTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static String command(GuiAppsSetup.Distro distro, boolean installed) {
        return GuiAppsSetup.command(GuiAppsSetup.Route.DISTRO, distro,
            EnumSet.allOf(GuiAppsSetup.StarterApp.class), AppEdition.TERMUX, null, null, installed);
    }

    @Test public void aDistroNotInstalledKeepsItsInstallStep() {
        for (GuiAppsSetup.Distro distro : GuiAppsSetup.Distro.values()) {
            assertTrue(distro.name(), command(distro, false).contains(GuiAppsSetup.installStep(distro)));
        }
    }

    @Test public void anInstalledDistroHasNoInstallStep() {
        for (GuiAppsSetup.Distro distro : GuiAppsSetup.Distro.values()) {
            String command = command(distro, true);
            assertFalse(distro.name(), command.contains("proot-distro install"));
            assertTrue(distro.name(), command.contains("proot-distro login " + distro.alias));
        }
    }

    @Test public void theDefaultCommandIsTheNotInstalledOne() {
        assertEquals(command(GuiAppsSetup.Distro.DEBIAN, false),
            GuiAppsSetup.command(GuiAppsSetup.Route.DISTRO, GuiAppsSetup.Distro.DEBIAN,
                EnumSet.allOf(GuiAppsSetup.StarterApp.class), AppEdition.TERMUX));
    }

    @Test public void installedDistrosAreTheOnesWithARootfs() throws IOException {
        File containers = tmp.newFolder("containers");
        assertTrue(new File(containers, "debian/rootfs").mkdirs());
        assertTrue(new File(containers, "ubuntu").mkdirs()); // half-installed: no rootfs
        assertTrue(new File(containers, "mine/rootfs").mkdirs()); // not one of the guide's distros

        Set<GuiAppsSetup.Distro> found = GuiAppsSetupGuide.installedDistros(containers);

        assertEquals(EnumSet.of(GuiAppsSetup.Distro.DEBIAN), found);
    }

    @Test public void noContainersDirMeansNothingInstalled() {
        assertTrue(GuiAppsSetupGuide.installedDistros(new File(tmp.getRoot(), "none")).isEmpty());
    }

    private static String inside(GuiAppsSetup.Distro distro, boolean desktop) {
        Set<GuiAppsSetup.StarterApp> starters = EnumSet.of(GuiAppsSetup.StarterApp.BROWSER);
        if (desktop) starters.add(GuiAppsSetup.StarterApp.DESKTOP);
        return GuiAppsSetup.insideScript(distro, starters);
    }

    @Test public void aTickedDesktopInstallsXfceInsideEveryDistro() {
        for (GuiAppsSetup.Distro distro : GuiAppsSetup.Distro.values()) {
            String script = inside(distro, true);
            assertTrue(distro.name(), script.contains(" xfce4"));
            assertTrue(distro.name(), script.contains(distro.busPackage));
            assertFalse(distro.name(), inside(distro, false).contains("xfce4"));
        }
        assertTrue(inside(GuiAppsSetup.Distro.DEBIAN, true).contains("dbus-x11"));
    }

    @Test public void theDesktopIsOffOnTheDistroRouteOnlyAndNeverInstalledByTheX11Route() {
        assertTrue(GuiAppsSetupGuide.startersOffered(AppEdition.TERMUX, GuiAppsSetup.Route.DISTRO)
            .contains(GuiAppsSetup.StarterApp.DESKTOP));
        assertFalse(GuiAppsSetupGuide.startersOffered(AppEdition.TERMUX, GuiAppsSetup.Route.X11_REPO)
            .contains(GuiAppsSetup.StarterApp.DESKTOP));
        assertFalse(GuiAppsSetup.defaultStarters().contains(GuiAppsSetup.StarterApp.DESKTOP));
        assertFalse(GuiAppsSetup.command(GuiAppsSetup.Route.X11_REPO, GuiAppsSetup.Distro.DEBIAN,
            EnumSet.allOf(GuiAppsSetup.StarterApp.class), AppEdition.TERMUX).contains("xfce4 "));
    }
}
