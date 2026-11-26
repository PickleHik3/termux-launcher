package com.termux.app.x11;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
    public void xfceIsTheOneOfferedEntryAndIsFoundById() {
        assertEquals(Arrays.asList(XFCE), DesktopCatalogue.offered());
        assertEquals(Arrays.asList(XFCE), DesktopCatalogue.all());
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
        assertArrayEquals(new String[]{".config/xfce4"}, XFCE.settingsFolders());
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
    public void packagesReturnsACopy() {
        XFCE.packages()[0] = "changed";
        assertEquals("xfce4", XFCE.packages()[0]);
    }
}
