package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

/** Edit pages' reorder in the durable store: a page moves with everything on it, in one commit. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class LauncherWidgetRepositoryMovePageTest {

    private static LauncherWidgetRecord widget(int id, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 1, 1), page, null, null);
    }

    private static LauncherWidgetRepository threePages(WidgetTestFixtures.Memory memory) {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertTrue(repository.putRecord(widget(10, 0)));
        assertTrue(repository.putRecord(widget(11, 1)));
        assertTrue(repository.putRecord(widget(12, 2)));
        return repository;
    }

    @Test
    public void aPageMovesWithItsWidgetsAndThePagesBetweenShift() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = threePages(memory);
        assertTrue(repository.movePage(0, 2));
        assertEquals(2, repository.get(10).page);
        assertEquals(0, repository.get(11).page);
        assertEquals(1, repository.get(12).page);
        assertEquals(3, repository.pageCount());

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        assertEquals(2, reloaded.get(10).page);
        assertEquals(0, reloaded.get(11).page);
    }

    @Test
    public void aMoveThatGoesNowhereIsRefusedAndChangesNothing() {
        LauncherWidgetRepository repository = threePages(new WidgetTestFixtures.Memory());
        long revision = repository.revision();
        assertFalse(repository.movePage(1, 1));
        assertFalse(repository.movePage(0, 3));
        assertEquals(revision, repository.revision());
        assertEquals(1, repository.get(11).page);
    }

    @Test
    public void aPageAddedByHandStaysFreshWhereverItMoves() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new WidgetTestFixtures.Memory());
        assertTrue(repository.putRecord(widget(10, 0)));
        assertEquals(1, repository.addFreshPage());
        assertTrue(repository.movePage(1, 0));
        assertEquals(Collections.singleton(0), repository.freshPages());
        assertEquals(1, repository.get(10).page);
        assertTrue(repository.trimEmptyPages());
        assertEquals("the fresh page survives a trim after its move", 2, repository.pageCount());
    }
}
