package com.termux.app.x11;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.x11.DesktopCatalogue.Desktop;
import com.termux.app.x11.DesktopCatalogue.State;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class DesktopCatalogueTest {
    private static final Desktop XFCE = DesktopCatalogue.XFCE;

    @Test
    public void xfceAndLxqtAreOfferedAndFoundById() {
        assertEquals(Arrays.asList(XFCE, DesktopCatalogue.LXQT, DesktopCatalogue.MATE,
            DesktopCatalogue.PLASMA), DesktopCatalogue.offered());
        assertEquals(6, DesktopCatalogue.all().size());
        assertSame(DesktopCatalogue.MATE, DesktopCatalogue.byId("mate"));
        assertSame(DesktopCatalogue.PLASMA, DesktopCatalogue.byId("plasma"));
        assertSame(DesktopCatalogue.LXQT, DesktopCatalogue.byId("lxqt"));
        assertSame(XFCE, DesktopCatalogue.byId("xfce"));
        assertNull(DesktopCatalogue.byId("nope"));
        assertNull(DesktopCatalogue.byId(null));
    }

    @Test
    public void xfceEntryCarriesItsTable() {
        assertEquals("XFCE", XFCE.name);
        assertEquals(DesktopCatalogue.Kind.X11, XFCE.kind);
        assertEquals("startxfce4", XFCE.startCommand);
        assertNull(XFCE.starterConfig);
        assertArrayEquals(new String[]{".config/xfce4"}, XFCE.settingsPaths());
        assertEquals("xfce4", XFCE.packages()[0]);
        assertEquals(13, XFCE.packages().length);
    }

    @Test
    public void installOrderOfXfcePackagesIsUnchanged() {
        assertEquals("xfce4 xfdesktop xfce4-pulseaudio-plugin pavucontrol xfce4-whiskermenu-plugin "
                + "tumbler thunar-archive-plugin xarchiver xfce4-screenshooter xfce4-clipman-plugin "
                + "ristretto mousepad papirus-icon-theme",
            String.join(" ", XFCE.packages()));
    }

    @Test
    public void stateComesFromTheSuppliedFileTests() {
        Set<String> prefix = new HashSet<>(Arrays.asList("bin/startxfce4"));
        Set<String> container = new HashSet<>(Arrays.asList("usr/bin/startxfce4"));
        assertEquals(State.INSTALLED, XFCE.state(prefix::contains, f -> false));
        assertEquals(State.INSTALLED, XFCE.state(prefix::contains, container::contains));
        assertEquals(State.INSTALLED_IN_CONTAINER, XFCE.state(f -> false, container::contains));
        assertEquals(State.NOT_INSTALLED, XFCE.state(f -> false, f -> false));
    }

    @Test
    public void aPieceAloneDoesNotMakeTheDesktopInstalled() {
        assertEquals(State.NOT_INSTALLED,
            XFCE.state("bin/mousepad"::equals, f -> false));
    }

    @Test
    public void mateEntryCarriesItsTable() {
        Desktop mate = DesktopCatalogue.MATE;
        assertEquals(DesktopCatalogue.Kind.X11, mate.kind);
        assertArrayEquals(new String[]{"mate"}, mate.packages());
        assertEquals("mate-session", mate.startCommand);
        assertArrayEquals(new String[]{".config/mate", ".config/caja"}, mate.settingsPaths());
        Set<String> container = new HashSet<>(Arrays.asList("usr/bin/mate-session"));
        assertEquals(State.INSTALLED,
            mate.state("share/xsessions/mate.desktop"::equals, f -> false));
        assertEquals(State.INSTALLED, mate.state("bin/mate-session"::equals, f -> false));
        assertEquals(State.INSTALLED_IN_CONTAINER, mate.state(f -> false, container::contains));
        assertEquals(State.NOT_INSTALLED, mate.state("bin/startlxqt"::equals, f -> false));
    }

    @Test
    public void plasmaIsTheX11SessionOnly() {
        Desktop plasma = DesktopCatalogue.PLASMA;
        assertEquals(DesktopCatalogue.Kind.X11, plasma.kind);
        assertArrayEquals(new String[]{"plasma"}, plasma.packages());
        assertEquals("startplasma-x11", plasma.startCommand);
        assertTrue(plasma.settingsPaths().length > 0);
        Set<String> container = new HashSet<>(Arrays.asList("usr/bin/startplasma-x11"));
        assertEquals(State.INSTALLED, plasma.state("bin/startplasma-x11"::equals, f -> false));
        assertEquals(State.INSTALLED,
            plasma.state("share/xsessions/plasmax11.desktop"::equals, f -> false));
        assertEquals(State.INSTALLED_IN_CONTAINER, plasma.state(f -> false, container::contains));
        assertEquals(State.NOT_INSTALLED,
            plasma.state("share/wayland-sessions/plasma.desktop"::equals, f -> false));
    }

    @Test
    public void packagesReturnsACopy() {
        XFCE.packages()[0] = "changed";
        assertEquals("xfce4", XFCE.packages()[0]);
    }

    @Test
    public void waylandDesktopsAreHeldBackWhileTheSwitchIsOff() {
        assertFalse(DesktopCatalogue.WAYLAND_DESKTOPS_OFFERED);
        assertFalse(DesktopCatalogue.offered().contains(DesktopCatalogue.SWAY));
        assertFalse(DesktopCatalogue.offered().contains(DesktopCatalogue.LABWC));
        assertEquals(4, DesktopCatalogue.offered(false, true).size());
        assertEquals(4, DesktopCatalogue.offered(false, false).size());
    }

    @Test
    public void waylandDesktopsAreOfferedOnlyWithTheSwitchAndAWaylandBuild() {
        assertEquals(Arrays.asList(XFCE, DesktopCatalogue.LXQT, DesktopCatalogue.MATE,
            DesktopCatalogue.PLASMA, DesktopCatalogue.SWAY, DesktopCatalogue.LABWC),
            DesktopCatalogue.offered(true, true));
        assertEquals(4, DesktopCatalogue.offered(true, false).size());
    }

    @Test
    public void swayAndLabwcCarryTheirTables() {
        for (Desktop d : new Desktop[]{DesktopCatalogue.SWAY, DesktopCatalogue.LABWC}) {
            assertEquals(DesktopCatalogue.Kind.WAYLAND, d.kind);
            assertEquals(d.id, d.startCommand);
            assertEquals(d.id, d.packages()[0]);
            assertTrue(DesktopsPage.isWaylandSession(true, d.name));
            assertEquals(State.INSTALLED, d.state(("share/wayland-sessions/" + d.id + ".desktop")::equals, f -> false));
            assertEquals(State.INSTALLED, d.state(("bin/" + d.id)::equals, f -> false));
            // "Also delete its settings" takes the config with the folder.
            assertEquals(1, d.settingsPaths().length);
            assertTrue(d.starterConfig.configPath.startsWith(d.settingsPaths()[0] + "/"));
        }
    }
}
