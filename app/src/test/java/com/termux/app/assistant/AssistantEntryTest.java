package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.wall.WallPages;
import com.termux.app.wall.WallPagesMigration;

import org.junit.Test;

/** The one way into the Assistant page: go, or add after Home 1 with an Undo (ADR 0022). */
public class AssistantEntryTest {

    /** A wall the entry edits for real; only the view and the notice are faked. */
    private static final class FakeHost implements AssistantEntry.Host {
        WallPages pages;
        int current;
        int wentTo = -2;
        boolean animated;
        Runnable undo;
        int shown;
        boolean refuseAdd;
        boolean noHome;

        FakeHost(WallPages pages, int current) {
            this.pages = pages;
            this.current = current;
        }

        @Override public boolean hasAssistantPage() { return pages.assistant() != null; }
        @Override public int firstHomePageId() {
            return noHome || pages.homePages().isEmpty() ? -1 : pages.homePages().get(0).id;
        }
        @Override public int currentPageId() { return current; }
        @Override public boolean goToAssistantPage(boolean animate) {
            WallPages.Page page = pages.assistant();
            if (page == null) return false;
            wentTo = page.id;
            animated = animate;
            current = page.id;
            return true;
        }
        @Override public int addAssistantPage(int afterId) {
            if (refuseAdd) return -1;
            WallPages.Result added = pages.add(WallPages.PageKind.ASSISTANT,
                pages.page(afterId) != null ? afterId : WallPages.END, null);
            if (!added.ok()) return -1;
            pages = added.pages;
            return added.page.id;
        }
        @Override public void removeAssistantPage(int id) {
            WallPages.Result removed = pages.remove(id, 0, true);
            if (removed.ok()) pages = removed.pages.commitRemoval(id).pages;
        }
        @Override public void goToPage(int id, boolean animate) {
            current = id;
            wentTo = id;
        }
        @Override public void showAdded(@NonNull Runnable undo) {
            shown++;
            this.undo = undo;
        }
    }

    private static java.util.List<Integer> ids(WallPages pages) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (WallPages.Page p : pages.pages()) out.add(p.id);
        return out;
    }

    /** Home 1, Home 2, Terminal (ids 1, 2, 3). */
    private static WallPages wall() {
        return WallPagesMigration.seed("home", 0, "home", 2);
    }

    @Test public void withThePageOnTheWallItGoesThereAnimatedAndAddsNothing() {
        WallPages.Result added = wall().add(WallPages.PageKind.ASSISTANT, 1, null);
        FakeHost host = new FakeHost(added.pages, 3);

        assertEquals(AssistantEntry.Outcome.WENT, AssistantEntry.open(host));

        assertEquals(added.page.id, host.wentTo);
        assertTrue(host.animated);
        assertEquals(0, host.shown);
        assertSame(added.pages, host.pages);
    }

    private static void assertSame(Object a, Object b) {
        org.junit.Assert.assertSame(a, b);
    }

    @Test public void withoutOneItIsAddedAfterHomeOneLandedOnAndOffersUndo() {
        WallPages start = wall();
        int terminal = start.terminal().id;
        FakeHost host = new FakeHost(start, terminal);
        int homeOne = start.homePages().get(0).id;

        assertEquals(AssistantEntry.Outcome.ADDED, AssistantEntry.open(host));

        WallPages.Page assistant = host.pages.assistant();
        assertNotNull(assistant);
        assertEquals(ids(host.pages).indexOf(homeOne) + 1, ids(host.pages).indexOf(assistant.id));
        assertEquals(assistant.id, host.current);
        assertEquals(1, host.shown);
        assertNotNull(host.undo);
    }

    @Test public void undoTakesThePageOffAndReturnsToWhereThePersonWas() {
        WallPages start = wall();
        int terminal = start.terminal().id;
        FakeHost host = new FakeHost(start, terminal);
        AssistantEntry.open(host);

        host.undo.run();

        assertNull(host.pages.assistant());
        assertEquals(terminal, host.current);
        assertEquals(ids(start), ids(host.pages));
    }

    @Test public void withNoHomePageItGoesAfterThePageInFront() {
        WallPages start = wall();
        int front = start.terminal().id;
        FakeHost host = new FakeHost(start, front);
        host.noHome = true;

        assertEquals(AssistantEntry.Outcome.ADDED, AssistantEntry.open(host));

        assertEquals(ids(host.pages).indexOf(front) + 1,
            ids(host.pages).indexOf(host.pages.assistant().id));
    }

    @Test public void aRefusedAddShowsNoNoticeAndDoesNotMove() {
        FakeHost host = new FakeHost(wall(), 3);
        host.refuseAdd = true;

        assertEquals(AssistantEntry.Outcome.FAILED, AssistantEntry.open(host));

        assertEquals(0, host.shown);
        assertFalse(host.animated);
        assertNull(host.pages.assistant());
    }

    @Test public void theActionNameCarriesThePackageNotALiteral() {
        assertTrue(AssistantEntry.ACTION_OPEN_ASSISTANT.endsWith(".app.open_assistant"));
        assertTrue(AssistantEntry.ACTION_OPEN_ASSISTANT.startsWith(
            com.termux.shared.termux.TermuxConstants.TERMUX_PACKAGE_NAME));
    }
}
