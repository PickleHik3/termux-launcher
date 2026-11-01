package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.wall.WallPages.PageKind;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The page overview lists every Display page of the wall, where it stands (#193). */
public class PageOverviewDisplayCardsTest {

    private static final int B = PageOverviewPolicy.BLOCK;

    /** pong's stray wall: [HOME 1, DISPLAY 5, TERMINAL 4] shows the Display page between them. */
    @Test public void strayDisplayPageBetweenHomeAndTerminal() {
        List<Integer> order = PageOverviewPolicy.wallOrder(1, true, 1,
            new int[] {1}, new boolean[] {false});
        assertEquals(Arrays.asList(0, -1, B), order);
    }

    @Test public void displayAfterTerminalStandsAfterTheBlock() {
        List<Integer> order = PageOverviewPolicy.wallOrder(2, true, 1,
            new int[] {1, 2}, new boolean[] {true, false});
        // Home 0, Terminal block, Display 0 (after the block), Home 1, Display 1.
        assertEquals(Arrays.asList(0, B, -1, 1, -2), order);
    }

    @Test public void noDisplayPagesIsTheOldStrip() {
        assertEquals(Arrays.asList(0, 1, B), PageOverviewPolicy.wallOrder(2, true, 2,
            new int[0], new boolean[0]));
        assertEquals(Arrays.asList(B, 0), PageOverviewPolicy.wallOrder(1, true, 0,
            new int[0], new boolean[0]));
    }

    @Test public void displayCardIsNotPartOfTheWindowBlock() {
        List<PageOverviewPolicy.Kind> strip = Arrays.asList(PageOverviewPolicy.Kind.HOME_PAGE,
            PageOverviewPolicy.Kind.DISPLAY, PageOverviewPolicy.Kind.TERMINAL);
        assertEquals(2, PageOverviewPolicy.blockBounds(strip)[0]);
        assertEquals(PageOverviewPolicy.NONE, PageOverviewPolicy.dropTarget(1, 0, strip));
        assertEquals(1, PageOverviewPolicy.pageIndexAt(1, strip));
        assertEquals(2, PageOverviewPolicy.pageIndexAt(2, strip));
        assertEquals(PaneWallPage.DISPLAY, PageOverviewPolicy.wallPosition(1, strip).kind);
        assertEquals(PaneWallPage.WIDGETS, PageOverviewPolicy.wallPosition(0, strip).kind);
    }

    @Test public void blockBoundsNullWithoutTerminal() {
        assertNull(PageOverviewPolicy.blockBounds(Arrays.asList(PageOverviewPolicy.Kind.HOME_PAGE,
            PageOverviewPolicy.Kind.DISPLAY)));
    }

    private static WallPagesStore storeWithDisplay() {
        WallPages p = WallPagesMigration.seed("workspace", 1, "home", 3);
        p = p.add(PageKind.DISPLAY, 1, "gimp").pages;
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        store.replace(p);
        return store;
    }

    private static boolean has(WallPagesStore store, int id) {
        for (WallPages.Page pg : store.current().allPages()) if (pg.id == id) return true;
        return false;
    }

    private static int displayId(WallPagesStore store) {
        for (WallPages.Page pg : store.current().allPages()) {
            if (pg.kind == PageKind.DISPLAY) return pg.id;
        }
        throw new AssertionError("no display page");
    }

    /** #193: a removed Display page ends gone from the store, not left pending. */
    @Test public void removalCommitLeavesNoPendingDisplayPage() {
        WallPagesStore store = storeWithDisplay();
        int id = displayId(store);
        store.edit(pages -> pages.remove(id));
        assertEquals(java.util.Collections.singletonList(id), store.current().pendingIds());
        store.commitRemoval(id);
        assertEquals(0, store.current().pendingIds().size());
        assertEquals(false, has(store, id));
    }

    /** Cancelling the stop question takes the page back from pending. */
    @Test public void undoRestoresTheDisplayPage() {
        WallPagesStore store = storeWithDisplay();
        int id = displayId(store);
        store.edit(pages -> pages.remove(id));
        store.edit(pages -> pages.undo(id));
        assertEquals(0, store.current().pendingIds().size());
        assertEquals(true, has(store, id));
        assertEquals(true, store.current().pages().stream().anyMatch(pg -> pg.id == id));
    }
}
