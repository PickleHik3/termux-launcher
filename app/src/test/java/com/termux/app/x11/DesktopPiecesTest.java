package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class DesktopPiecesTest {
    @Test
    public void everySetupPackageHasAFileToTest() {
        for (String name : GuiAppsSetup.DESKTOP_TERMUX_PACKAGES) {
            assertTrue(name, DesktopPieces.filesFor(name).length > 0);
        }
    }

    @Test
    public void unknownOrNullPackageHasNoFiles() {
        assertEquals(0, DesktopPieces.filesFor("nope").length);
        assertEquals(0, DesktopPieces.filesFor(null).length);
    }

    @Test
    public void wholeDesktopReportsNothing() {
        assertTrue(DesktopPieces.missing(GuiAppsSetup.DESKTOP_TERMUX_PACKAGES, f -> true).isEmpty());
    }

    @Test
    public void metapackageOnlyReportsEverythingElse() {
        Set<String> present = new HashSet<>(Arrays.asList(DesktopPieces.filesFor("xfce4")));
        java.util.List<String> missing =
            DesktopPieces.missing(GuiAppsSetup.DESKTOP_TERMUX_PACKAGES, present::contains);
        assertEquals(GuiAppsSetup.DESKTOP_TERMUX_PACKAGES.length - 1, missing.size());
        assertTrue(missing.contains("xfdesktop"));
    }

    @Test
    public void anyOneFileIsEnough() {
        String second = DesktopPieces.filesFor("tumbler")[1];
        assertTrue(DesktopPieces.missing(new String[]{"tumbler"}, second::equals).isEmpty());
    }

    @Test
    public void nullListAndUnknownReportNothing() {
        assertTrue(DesktopPieces.missing(null, f -> false).isEmpty());
        assertTrue(DesktopPieces.missing(new String[]{"nope"}, f -> false).isEmpty());
    }

    @Test
    public void installCommandChainsTheRepository() {
        assertEquals("pkg install -y x11-repo && pkg install -y xfdesktop ristretto",
            DesktopPieces.installCommand(Arrays.asList("xfdesktop", "ristretto")));
        assertNull(DesktopPieces.installCommand(Collections.<String>emptyList()));
    }

    private static LinuxAppCatalog.LinuxApp session(ProotDistro.Container container, String exec,
                                                    String names) {
        return new LinuxAppCatalog.LinuxApp(container, "xfce.desktop", "Xfce Session", exec, "", "",
            "", false, true, names);
    }

    @Test
    public void onlyTheTermuxXfceSessionInThePrefixIsChecked() {
        ProotDistro.Container prefix = ProotDistro.Container.PREFIX;
        assertTrue(X11LinuxAppRunner.checksDesktopPieces(
            session(prefix, "startxfce4", "XFCE"), AppEdition.TERMUX));
        // Termux's xfce.desktop: the session by its program alone, or by its DesktopNames alone.
        assertTrue(X11LinuxAppRunner.checksDesktopPieces(
            session(prefix, "xfce4-session", ""), AppEdition.TERMUX));
        assertTrue(X11LinuxAppRunner.checksDesktopPieces(
            session(prefix, "my-start", "XFCE"), AppEdition.TERMUX));
        assertFalse(X11LinuxAppRunner.checksDesktopPieces(
            session(prefix, "startplasma-x11", "KDE"), AppEdition.TERMUX));
        assertFalse(X11LinuxAppRunner.checksDesktopPieces(
            session(prefix, "startxfce4", "XFCE"), AppEdition.VAJ));
        assertFalse(X11LinuxAppRunner.checksDesktopPieces(
            session(new ProotDistro.Container("debian", new File("/d"), "root", "/root"),
                "startxfce4", "XFCE"), AppEdition.TERMUX));
        assertFalse(X11LinuxAppRunner.checksDesktopPieces(new LinuxAppCatalog.LinuxApp(prefix,
            "mousepad.desktop", "Mousepad", "mousepad", "", "", "", false), AppEdition.TERMUX));
    }

    @Test
    public void noDesktopAtAllIsNotOfferedItsPieces() {
        assertTrue(DesktopPieces.missingFromInstalledDesktop(GuiAppsSetup.DESKTOP_TERMUX_PACKAGES,
            f -> false).isEmpty());
        assertEquals(Collections.singletonList("xfdesktop"),
            DesktopPieces.missingFromInstalledDesktop(GuiAppsSetup.DESKTOP_TERMUX_PACKAGES,
                f -> !f.equals("bin/xfdesktop")));
    }

    @Test
    public void setupWindowRunsTheCommandInALoginShellThatWaitsAtTheEnd() {
        java.util.List<String> argv = DesktopPieces.windowCommand("/p/bash", "pkg install -y xfdesktop");
        assertEquals("/p/bash", argv.get(0));
        assertEquals("-lc", argv.get(1));
        assertTrue(argv.get(2).startsWith("pkg install -y xfdesktop; "));
        assertTrue(argv.get(2).contains("read -r"));
    }
}
