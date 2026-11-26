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

    @Test
    public void aSessionBelongsToTheDesktopThatNamesItself() {
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, "XFCE"));
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, "GNOME;xfce"));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, "LXQt"));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, ""));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE, null));
    }
}
