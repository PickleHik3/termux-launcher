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

    private static List<Row> removal(boolean lxqtThere, RunPhase phase) {
        return DesktopsPage.rows(f -> lxqtThere && f.equals("bin/startlxqt"), f -> false, "lxqt", phase,
            DesktopsPage.RunKind.REMOVE);
    }

    @Test
    public void aRemovalThatGoesShowsRemovingWithATerminalAndBlocksOthers() {
        List<Row> rows = removal(true, RunPhase.RUNNING);
        Row lxqt = row("lxqt", rows);
        assertEquals(Status.REMOVING, lxqt.status);
        assertTrue(lxqt.showsTerminal());
        assertFalse(lxqt.canInstall());
        assertEquals(Status.UNAVAILABLE, row("xfce", rows).status);
    }

    @Test
    public void aFinishedRemovalLeavesNotInstalledAndOffersInstallAgain() {
        Row lxqt = row("lxqt", removal(false, RunPhase.FINISHED));
        assertEquals(Status.NOT_INSTALLED, lxqt.status);
        assertTrue(lxqt.canInstall());
        assertTrue(lxqt.dismissesRun());
        assertFalse(lxqt.showsTerminal());
    }

    @Test
    public void aRemovalThatLeftTheDesktopThereIsFailedAndKeepsItsTerminalAndPage() {
        Row lxqt = row("lxqt", removal(true, RunPhase.FINISHED));
        assertEquals(Status.REMOVE_FAILED, lxqt.status);
        assertTrue(lxqt.showsTerminal());
        assertTrue(lxqt.opensSettings());
        assertFalse(lxqt.dismissesRun());
    }

    @Test
    public void theSessionNamesFindTheCatalogueDesktop() {
        assertEquals(DesktopCatalogue.XFCE, DesktopsPage.desktopOf("XFCE"));
        assertEquals(DesktopCatalogue.LXQT, DesktopsPage.desktopOf("X-Foo;LXQt"));
        assertEquals(null, DesktopsPage.desktopOf("GNOME"));
        assertEquals(null, DesktopsPage.desktopOf(null));
    }
}
