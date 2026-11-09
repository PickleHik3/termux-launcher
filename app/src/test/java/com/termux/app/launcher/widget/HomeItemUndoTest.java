package com.termux.app.launcher.widget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.Bundle;

import com.termux.app.launcher.model.AppRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Undo after a drop on Remove: the item back exactly where it was (page, cell, a folder's
 * name and apps), a page that went with it put back where it stood, and the nearest place left
 * when its own has been taken since.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class HomeItemUndoTest {
    private static final AppRef MAPS = new AppRef("com.maps", "com.maps.Main");
    private static final AppRef CLOCK = new AppRef("com.clock", "com.clock.Main");
    private static final AppRef MAIL = new AppRef("com.mail", "com.mail.Main");

    private static Bundle label(String value) {
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, value);
        return options;
    }

    private static WidgetCellRect cell(int column, int row) {
        return new WidgetCellRect(column, row, column + 1, row + 1);
    }

    /** Maps (key -1) at (0, 0) and Clock (key -2) at (1, 0), both on page 0. */
    private static LauncherWidgetRepository home() {
        LauncherWidgetRepository repository =
            new LauncherWidgetRepository(new WidgetTestFixtures.Memory());
        assertNotNull(repository.addAppAt(MAPS, 0, cell(0, 0), label("Maps")));
        assertNotNull(repository.addAppAt(CLOCK, 0, cell(1, 0), label("Clock")));
        return repository;
    }

    @Test
    public void anIconComesBackToItsOwnCellUnderItsOwnKey() {
        LauncherWidgetRepository repository = home();
        LauncherWidgetRecord clock = repository.get(-2);
        assertTrue(repository.removeRecord(-2));

        HomeItemUndo.Result result = HomeItemUndo.restore(repository,
            new HomeItemUndo.Snapshot(clock, false));

        assertNotNull(result);
        LauncherWidgetRecord back = repository.get(-2);
        assertNotNull(back);
        assertEquals(cell(1, 0), back.cell);
        assertEquals(0, back.page);
        assertEquals("Clock", back.appLabel());
        assertEquals(1, repository.pageCount());
    }

    /** Its cell and its key taken since: the nearest free cell, under a new key. */
    @Test
    public void aTakenCellSendsItToTheNearestFreeOne() {
        LauncherWidgetRepository repository = home();
        LauncherWidgetRecord clock = repository.get(-2);
        assertTrue(repository.removeRecord(-2));
        LauncherWidgetRecord mail = repository.addAppAt(MAIL, 0, cell(1, 0), label("Mail"));
        assertNotNull(mail);
        assertEquals(-2, mail.appWidgetId);

        HomeItemUndo.Result result = HomeItemUndo.restore(repository,
            new HomeItemUndo.Snapshot(clock, false));

        assertNotNull(result);
        assertEquals(-3, result.record.appWidgetId);
        LauncherWidgetRecord back = repository.get(-3);
        assertNotNull(back);
        assertEquals(cell(2, 0), back.cell);
        assertEquals(CLOCK.packageName, back.appRef().packageName);
        assertEquals("Mail", repository.get(-2).appLabel());
    }

    @Test
    public void aFolderComesBackWithItsNameAndApps() {
        LauncherWidgetRepository repository = home();
        LauncherWidgetRecord folder = repository.mergeIntoFolder(-1, -2, "Folder");
        assertNotNull(folder);
        assertTrue(repository.removeRecord(folder.appWidgetId));

        HomeItemUndo.Result result = HomeItemUndo.restore(repository,
            new HomeItemUndo.Snapshot(folder, false));

        assertNotNull(result);
        LauncherWidgetRecord back = repository.get(folder.appWidgetId);
        assertNotNull(back);
        assertTrue(back.isFolder());
        assertEquals(folder.folderName(), back.folderName());
        assertEquals(2, back.folderApps().size());
        assertEquals(folder.folderApps().get(0).stableId(), back.folderApps().get(0).stableId());
        assertEquals(folder.folderApps().get(1).stableId(), back.folderApps().get(1).stableId());
        assertEquals(folder.cell, back.cell);
    }

    /** The last thing on a middle page: the page goes back where it stood, the later page moves on. */
    @Test
    public void aPageThatWentWithItComesBackInPlace() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(
            new WidgetTestFixtures.Memory());
        assertNotNull(repository.addAppAt(MAPS, 0, cell(0, 0), label("Maps")));
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertNotNull(repository.addAppAt(CLOCK, 1, cell(2, 3), label("Clock")));
        assertNotNull(repository.addAppAt(MAIL, 2, cell(0, 0), label("Mail")));
        LauncherWidgetRecord clock = repository.get(-2);
        assertTrue(repository.removeRecord(-2));
        assertFalse(repository.trimEmptyPageIds().isEmpty());
        assertEquals(2, repository.pageCount());
        assertEquals(1, repository.get(-3).page);

        HomeItemUndo.Result result = HomeItemUndo.restore(repository,
            new HomeItemUndo.Snapshot(clock, true));

        assertNotNull(result);
        assertEquals(3, repository.pageCount());
        LauncherWidgetRecord back = repository.get(-2);
        assertEquals(1, back.page);
        assertEquals(cell(2, 3), back.cell);
        assertEquals(2, repository.get(-3).page);
        assertEquals(0, repository.get(-1).page);
        // The page came back in the middle under a new id (the owner does not reuse 2).
        assertEquals(java.util.Arrays.asList(1, 4, 3), repository.pageIds());
    }

    /** The last page went with it: it is added again after the others, and nothing renumbers. */
    @Test
    public void aLastPageThatWentWithItIsAddedAgain() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(
            new WidgetTestFixtures.Memory());
        assertNotNull(repository.addAppAt(MAPS, 0, cell(0, 0), label("Maps")));
        assertEquals(1, repository.addPage());
        assertNotNull(repository.addAppAt(CLOCK, 1, cell(3, 4), label("Clock")));
        LauncherWidgetRecord clock = repository.get(-2);
        assertTrue(repository.removeRecord(-2));
        assertFalse(repository.trimEmptyPageIds().isEmpty());
        assertEquals(1, repository.pageCount());

        HomeItemUndo.Result result = HomeItemUndo.restore(repository,
            new HomeItemUndo.Snapshot(clock, true));

        assertNotNull(result);
        assertEquals(2, repository.pageCount());
        assertEquals(1, repository.get(-2).page);
        assertEquals(cell(3, 4), repository.get(-2).cell);
    }

    /** No room left on its page: wherever a new Home item would go, a new page at the worst. */
    @Test
    public void aFullPageSendsItWhereANewItemWouldGo() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(
            new WidgetTestFixtures.Memory());
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(1, 1)));
        assertNotNull(repository.addAppAt(MAPS, 0, cell(0, 0), label("Maps")));
        LauncherWidgetRecord maps = repository.get(-1);
        assertTrue(repository.removeRecord(-1));
        assertNotNull(repository.addAppAt(CLOCK, 0, cell(0, 0), label("Clock")));

        HomeItemUndo.Result result = HomeItemUndo.restore(repository,
            new HomeItemUndo.Snapshot(maps, false));

        assertNotNull(result);
        assertEquals(2, repository.pageCount());
        assertEquals(1, result.record.page);
        assertEquals(cell(0, 0), result.record.cell);
        assertEquals(1, repository.get(result.record.appWidgetId).page);
    }

    /** The plan alone: a removed page past the end is simply appended. */
    @Test
    public void aRemovedPagePastTheEndIsAppended() {
        LauncherWidgetRecord clock = LauncherWidgetRecord.app(-2, CLOCK, cell(0, 0), 4, null);
        HomeItemUndo.Plan plan = HomeItemUndo.plan(new HomeItemUndo.Snapshot(clock, true),
            WidgetGridDefinition.DEFAULT, 2, page -> java.util.Collections.emptyList());
        assertNotNull(plan);
        assertEquals(2, plan.page);
        assertTrue(plan.appendsPage);
        assertEquals(false, plan.insertsPage);
    }
}
