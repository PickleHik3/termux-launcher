package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.x11.DesktopsPage.Row;
import com.termux.app.x11.DesktopsPage.RunPhase;
import com.termux.app.x11.DesktopsPage.Status;

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

    private static Row row(String id, List<Row> rows) {
        for (Row row : rows) if (row.desktopId.equals(id)) return row;
        throw new AssertionError(id);
    }

    private static List<Row> rows(boolean lxqtThere, String runId, RunPhase phase) {
        return DesktopsPage.rows(f -> lxqtThere && f.equals("bin/startlxqt"), f -> false, runId, phase);
    }

    @Test
    public void aMissingDesktopOffersInstallAndNoTerminal() {
        Row lxqt = row("lxqt", rows(false, null, RunPhase.IDLE));
        assertEquals(Status.NOT_INSTALLED, lxqt.status);
        assertTrue(lxqt.canInstall());
        assertFalse(lxqt.showsTerminal());
    }

    @Test
    public void theRunningDesktopIsInstallingWithATerminalEvenWhenItsFilesAppearEarly() {
        Row lxqt = row("lxqt", rows(true, "lxqt", RunPhase.RUNNING));
        assertEquals(Status.INSTALLING, lxqt.status);
        assertTrue(lxqt.showsTerminal());
        assertFalse(lxqt.canInstall());
        assertFalse(lxqt.opensSettings());
    }

    @Test
    public void whileOneRunsNoOtherInstallIsAvailable() {
        List<Row> rows = rows(false, "xfce", RunPhase.RUNNING);
        assertEquals(Status.UNAVAILABLE, row("lxqt", rows).status);
        assertFalse(row("lxqt", rows).canInstall());
        assertFalse(row("lxqt", rows).showsTerminal());
    }

    @Test
    public void aFailureKeepsTheTerminalAndOffersToTryAgain() {
        Row lxqt = row("lxqt", rows(false, "lxqt", RunPhase.FAILED));
        assertEquals(Status.FAILED, lxqt.status);
        assertTrue(lxqt.showsTerminal());
        assertTrue(lxqt.canInstall());
    }

    @Test
    public void aFailedRunDoesNotBlockAnotherDesktop() {
        Row xfce = row("xfce", rows(false, "lxqt", RunPhase.FAILED));
        assertTrue(xfce.canInstall());
    }

    @Test
    public void aFinishedRunWithTheDesktopInPlaceClosesToInstalled() {
        Row lxqt = row("lxqt", rows(true, "lxqt", RunPhase.FINISHED));
        assertEquals(Status.INSTALLED, lxqt.status);
        assertFalse(lxqt.showsTerminal());
        assertTrue(lxqt.dismissesRun());
    }

    @Test
    public void aFinishedRunWithTheDesktopMissingCountsAsFailed() {
        Row lxqt = row("lxqt", rows(false, "lxqt", RunPhase.FINISHED));
        assertEquals(Status.FAILED, lxqt.status);
        assertFalse(lxqt.dismissesRun());
    }

    @Test
    public void anInstalledDesktopOffersNoInstall() {
        Row lxqt = row("lxqt", rows(true, null, RunPhase.IDLE));
        assertEquals(Status.INSTALLED, lxqt.status);
        assertFalse(lxqt.canInstall());
        assertTrue(lxqt.opensSettings());
    }

    private static LinuxAppCatalog.LinuxApp sessionFile(String file, String exec, String names) {
        return new LinuxAppCatalog.LinuxApp(distro("debian"), file, "Session", exec, "", "", "",
            false, true, names);
    }

    @Test
    public void aSessionNamesItsDesktopByDesktopNames() {
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE,
            sessionFile("other", "other", "XFCE")));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE,
            sessionFile("xfce", "startxfce4", "LXQt")));
    }

    @Test
    public void withoutDesktopNamesTheFileBaseNameIdentifiesTheDesktop() {
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE,
            sessionFile("xfce", "/bin/true", "")));
        assertFalse(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE,
            sessionFile("gnome", "/bin/true", "")));
    }

    @Test
    public void withoutDesktopNamesTheStartCommandIdentifiesTheDesktop() {
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE,
            sessionFile("xfce4-session", "startxfce4", "")));
        assertTrue(DesktopsPage.isSessionOf(DesktopCatalogue.XFCE,
            sessionFile("custom", "/usr/bin/startxfce4 --foo", "")));
        List<DesktopsPage.ContainerRow> rows = DesktopsPage.containerRows(
            java.util.Arrays.asList(sessionFile("custom", "startxfce4", "")));
        assertEquals("XFCE \u00b7 Debian", rows.get(0).name);
    }
}
