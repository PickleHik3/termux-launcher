package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import com.termux.app.wall.WallPages;
import com.termux.app.wall.WallPagesStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The repository tells every change of its page numbers, and a store that follows it by the same
 * calls the activity makes keeps the wall's Home pages in step: order, count and ids.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class LauncherWidgetRepositoryPageListenerTest {

    private LauncherWidgetRepository repository;
    private WallPagesStore store;
    private final List<Integer> removed = new ArrayList<>();

    private static LauncherWidgetRecord widget(int id, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 1, 1), page, null, null);
    }

    @Before public void setUp() {
        repository = new LauncherWidgetRepository(new WidgetTestFixtures.Memory());
        store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        store.replace(WallPages.seed(repository.pageCount()));
        store.addListener(new WallPagesStore.Listener() {
            @Override public void onChanged(WallPages pages) {}
            @Override public void onRemoved(WallPages.Page page) { removed.add(page.id); }
        });
        repository.setPageListener((oldCount, newCount, mapping) -> {
            if (mapping != null && mapping.length == store.current().homeCount()) {
                store.transform(pages -> pages.withHomeMapping(mapping));
            }
            store.transform(pages -> pages.withHomeCount(newCount));
        });
    }

    private List<Integer> homeIds() {
        List<Integer> ids = new ArrayList<>();
        for (WallPages.Page p : store.current().homePages()) ids.add(p.id);
        return ids;
    }

    @Test
    public void addedPagesAppendHomePages() {
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addFreshPage());
        assertEquals(Arrays.asList(1, 3, 4), homeIds());
        assertEquals(WallPages.PageKind.TERMINAL, store.current().pages().get(3).kind);
    }

    @Test
    public void aMovedPageMovesItsHomePageAndKeepsItsId() {
        repository.addPage();
        repository.addPage();
        assertTrue(repository.movePage(0, 2));
        assertEquals(Arrays.asList(3, 4, 1), homeIds());
        assertEquals(WallPages.PageKind.TERMINAL, store.current().pages().get(3).kind);
    }

    @Test
    public void aRemovedPageIsFinalAndFiresRemovedOnce() {
        repository.addPage();
        repository.addPage();
        assertTrue(repository.removePage(1));
        assertEquals(Arrays.asList(1, 4), homeIds());
        assertEquals(Arrays.asList(3), removed);
    }

    @Test
    public void aTrimDropsTheEmptyPagesHomePages() {
        repository.addPage();
        repository.addPage();
        assertTrue(repository.putRecord(widget(10, 2)));
        assertTrue(repository.trimEmptyPages());
        assertEquals(repository.pageCount(), store.current().homeCount());
        assertEquals(Arrays.asList(4), homeIds());
    }

    @Test
    public void aFailedEditTellsNothing() {
        assertFalse(repository.removePage(0));
        assertEquals(Arrays.asList(1), homeIds());
    }
}
