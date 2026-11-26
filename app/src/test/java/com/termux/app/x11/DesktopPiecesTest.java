package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}
