package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.x11.DesktopsPage.Row;

import java.util.List;

import org.junit.Test;

public class DesktopsPageTest {
    @Test
    public void everyOfferedDesktopGetsARowInCatalogueOrder() {
        List<Row> rows = DesktopsPage.rows(f -> false, f -> false);
        assertEquals(DesktopCatalogue.offered().size(), rows.size());
        assertEquals("xfce", rows.get(0).desktopId);
        assertEquals("XFCE", rows.get(0).name);
    }

    @Test
    public void xfceWithItsOwnFileIsInstalledAndOpensItsSettings() {
        List<Row> rows = DesktopsPage.rows("bin/startxfce4"::equals, f -> false);
        assertTrue(rows.get(0).installed);
        assertTrue(rows.get(0).opensSettings());
    }

    @Test
    public void noFilesMeansNotInstalledAndNothingToOpen() {
        Row row = DesktopsPage.rows(f -> false, f -> false).get(0);
        assertFalse(row.installed);
        assertFalse(row.opensSettings());
    }

    private static LinuxAppCatalog.LinuxApp session(ProotDistro.Container c, String names) {
        return new LinuxAppCatalog.LinuxApp(c, "xfce", "Xfce Session", "startxfce4", "", "", "", false,
            true, names);
    }

    private static ProotDistro.Container distro(String name) {
        return new ProotDistro.Container(name, new java.io.File("/x/" + name), "root", "/root");
    }

    @Test
    public void aDesktopInAContainerIsListedWithItsDistroAndOpensItsSettings() {
        ProotDistro.Container debian = distro("debian");
        LinuxAppCatalog.LinuxApp app = session(debian, "XFCE");
        List<DesktopsPage.ContainerRow> rows =
            DesktopsPage.containerRows(java.util.Arrays.asList(app));
        assertEquals(1, rows.size());
        assertEquals("XFCE · Debian", rows.get(0).name);
        assertEquals(DesktopCatalogue.State.INSTALLED_IN_CONTAINER, rows.get(0).state());
        assertEquals(app.id, rows.get(0).appId);
        assertEquals("debian", X11Apps.containerOf(rows.get(0).appId));
    }

    @Test
    public void prefixSessionsApplicationsAndUnknownDesktopsAreNotContainerRows() {
        ProotDistro.Container debian = distro("debian");
        LinuxAppCatalog.LinuxApp plainApp = new LinuxAppCatalog.LinuxApp(debian, "x", "X", "x",
            "", "", "", false, false, "");
        List<LinuxAppCatalog.LinuxApp> apps = java.util.Arrays.asList(
            session(ProotDistro.Container.PREFIX, "XFCE"), plainApp, session(debian, "Cinnamon"));
        assertTrue(DesktopsPage.containerRows(apps).isEmpty());
    }

    @Test
    public void aSessionBelongsToTheDesktopThatNamesItself() {
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, "XFCE"));
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, "GNOME;xfce"));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, "LXQt"));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, ""));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, null));
    }
}
