package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Edit pages' rules over the page list: what the overview reads and the edits it makes. */
public class PageOverviewPagesTest {

    /** Records every step the rules ask of the launcher. */
    private static final class Recorder implements PageOverviewPages.Launcher {
        final List<String> calls = new ArrayList<>();
        final Map<String, String> apps = new HashMap<>();
        int after = -1;
        @Nullable StartInPolicy.Choice choice;

        @Override public void moveHomePage(int id, int to) { calls.add("move " + id + " " + to); }
        @Override public void removeHomePage(int id) { calls.add("remove " + id); }
        @Override public void applyStartIn(@NonNull StartInPolicy.Choice choice) {
            this.choice = choice;
            calls.add("startIn " + choice.startIn);
        }
        @Override @Nullable public String displayAppName(@NonNull String binding) {
            return apps.get(binding);
        }
        @Override public int pageAfterCurrent() { return after; }
        @Override public void refreshDisplayCards() { calls.add("cards"); }
        @Override public void refreshPages() { calls.add("pages"); }
    }

    private final Recorder launcher = new Recorder();

    private static WallPages of(int homeId, Page... pages) {
        int next = 1;
        for (Page p : pages) next = Math.max(next, p.id + 1);
        return new WallPages(Arrays.asList(pages), Collections.emptyList(),
            Collections.emptyList(), homeId, next, false);
    }

    private static Page home(int id) { return new Page(id, PageKind.HOME, null, null); }

    private static Page terminal(int id) { return new Page(id, PageKind.TERMINAL, null, null); }

    private WallPagesStore store(@NonNull WallPages pages) {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        store.replace(pages);
        return store;
    }

    private PageOverviewPages rules(@NonNull WallPagesStore store) {
        return new PageOverviewPages(store, "Assistant", launcher);
    }

    // ---- choosing home, by kind --------------------------------------------------------------

    @Test
    public void choosingAHomePageMakesThatHomePageHome() {
        WallPagesStore store = store(of(1, home(1), home(2), terminal(3)));
        rules(store).chooseHome(PageOverviewPolicy.Kind.HOME_PAGE, 1);
        assertEquals(2, store.current().homeId());
        assertEquals(1, rules(store).defaultPage());
        assertTrue(launcher.calls.isEmpty());
    }

    @Test
    public void choosingTheAssistantMakesThatPageHomeById() {
        WallPagesStore store = store(of(1, home(1), terminal(2),
            new Page(5, PageKind.ASSISTANT, null, null)));
        PageOverviewPages rules = rules(store);
        assertFalse(rules.assistantIsHome());
        rules.chooseHome(PageOverviewPolicy.Kind.ASSISTANT, 5);
        assertEquals(5, store.current().homeId());
        assertTrue(rules.assistantIsHome());
        assertTrue(launcher.calls.isEmpty());
    }

    @Test
    public void choosingATerminalWindowGoesThroughStartIn() {
        WallPagesStore store = store(of(1, home(1), terminal(2)));
        WallPages before = store.current();
        rules(store).chooseHome(PageOverviewPolicy.Kind.TERMINAL, 0);
        assertNotNull(launcher.choice);
        assertEquals(WallRow.START_IN_WORKSPACE, launcher.choice.startIn);
        assertSame(before, store.current());
    }

    @Test
    public void choosingTheDisplayGoesThroughStartIn() {
        rules(store(of(1, home(1), terminal(2)))).chooseHome(PageOverviewPolicy.Kind.DISPLAY, 0);
        assertEquals(WallRow.START_IN_DISPLAY, launcher.choice.startIn);
    }

    @Test
    public void setTerminalHomeNeedsATerminalPage() {
        WallPagesStore store = store(of(1, home(1), terminal(2)));
        rules(store).setTerminalHome();
        assertEquals(2, store.current().homeId());

        WallPagesStore none = store(of(1, home(1), home(2)));
        rules(none).setTerminalHome();
        assertEquals(1, none.current().homeId());
    }

    // ---- moves and removals at each bound ----------------------------------------------------

    @Test
    public void aHomePageMoveOutsideTheHomePagesAsksNothing() {
        PageOverviewPages rules = rules(store(of(1, home(1), home(2), terminal(3))));
        rules.movePage(-1, 0);
        rules.movePage(2, 0);
        rules.removePage(-1);
        rules.removePage(2);
        assertTrue(launcher.calls.isEmpty());
    }

    @Test
    public void aHomePageMoveAndRemovalNameThePageById() {
        PageOverviewPages rules = rules(store(of(1, home(7), home(4), terminal(3))));
        rules.movePage(0, 1);
        rules.movePage(1, 0);
        rules.removePage(1);
        assertEquals(Arrays.asList("move 7 1", "move 4 0", "remove 4"), launcher.calls);
    }

    @Test
    public void aHomePageMovesOnTheWallPastTheTerminalPage() {
        WallPagesStore store = store(of(1, home(1), home(2), terminal(3)));
        PageOverviewPages rules = rules(store);
        assertEquals(2, rules.homePagesBeforeTerminal());
        rules.moveHomePageOnWall(0, 2);
        assertEquals(Arrays.asList(2, 3, 1), ids(store.current().pages()));
        assertEquals(1, rules.homePagesBeforeTerminal());
    }

    @Test
    public void aHomePageMoveOnTheWallOutsideTheHomePagesChangesNothing() {
        WallPagesStore store = store(of(1, home(1), home(2), terminal(3)));
        WallPages before = store.current();
        rules(store).moveHomePageOnWall(-1, 0);
        rules(store).moveHomePageOnWall(2, 0);
        assertSame(before, store.current());
    }

    @Test
    public void theTerminalPageMovesToTheFrontAndWithoutOneNothingMoves() {
        WallPagesStore store = store(of(1, home(1), home(2), terminal(3)));
        PageOverviewPages rules = rules(store);
        rules.moveTerminalPage(0);
        assertEquals(Arrays.asList(3, 1, 2), ids(store.current().pages()));
        assertEquals(0, rules.homePagesBeforeTerminal());

        WallPagesStore none = store(of(1, home(1), home(2)));
        WallPages before = none.current();
        rules(none).moveTerminalPage(0);
        assertSame(before, none.current());
        assertEquals(Integer.MAX_VALUE, rules(none).homePagesBeforeTerminal());
    }

    @Test
    public void aMoveOnTheWallCountsOnlyListedPages() {
        // A temporary Display page stands at wall index 1; the overview's index 2 is the end.
        Page temp = new Page(9, PageKind.DISPLAY, null, null, true);
        WallPagesStore store = store(of(1, home(1), temp, home(2), terminal(3)));
        rules(store).moveTerminalPage(1);
        assertEquals(Arrays.asList(1, 9, 3, 2), ids(store.current().pages()));
    }

    @Test
    public void theTerminalPageIsRemovableOnlyBesideAnotherPage() {
        assertFalse(rules(store(of(1, terminal(1)))).terminalPageRemovable());
        assertTrue(rules(store(of(1, home(2), terminal(1)))).terminalPageRemovable());
        assertFalse(rules(store(of(1, home(1), home(2)))).terminalPageRemovable());
        assertFalse(rules(store(of(1, home(1)))).hasTerminalPage());
    }

    // ---- the Display page list --------------------------------------------------------------

    @Test
    public void displayPagesListInWallOrderWithTheirNamesAndPlaces() {
        launcher.apps.put("gimp", "GIMP");
        WallPagesStore store = store(of(1,
            new Page(10, PageKind.DISPLAY, null, null),
            home(1),
            new Page(11, PageKind.DISPLAY, null, "gimp"),
            terminal(2),
            new Page(12, PageKind.DISPLAY, "Art", "gimp"),
            new Page(13, PageKind.DISPLAY, null, "unknown"),
            new Page(14, PageKind.ASSISTANT, null, null)));
        List<PageOverviewView.DisplayPage> out = rules(store).displayPages();
        assertEquals(5, out.size());
        assertDisplay(out.get(0), 10, "Display", 0, false, false);
        assertDisplay(out.get(1), 11, "GIMP", 1, false, false);
        assertDisplay(out.get(2), 12, "Art", 1, true, false);
        assertDisplay(out.get(3), 13, "Display", 1, true, false);
        assertDisplay(out.get(4), 14, "Assistant", 1, true, true);
    }

    @Test
    public void aTemporaryDisplayPageIsNotListedButIsTemporary() {
        WallPagesStore store = store(of(1, home(1), terminal(2),
            new Page(5, PageKind.DISPLAY, null, null, true)));
        PageOverviewPages rules = rules(store);
        assertTrue(rules.displayPages().isEmpty());
        assertTrue(rules.displayTemporary());

        assertFalse(rules(store(of(1, home(1), new Page(5, PageKind.DISPLAY, null, null))))
            .displayTemporary());
    }

    @Test
    public void aNamedAssistantPageWearsItsName() {
        List<PageOverviewView.DisplayPage> out = rules(store(of(1, home(1),
            new Page(4, PageKind.ASSISTANT, "Chat", null)))).displayPages();
        assertEquals("Chat", out.get(0).label);
        assertTrue(rules(store(of(1, home(1), new Page(4, PageKind.ASSISTANT, null, null))))
            .hasAssistantPage());
        assertFalse(rules(store(of(1, home(1)))).hasAssistantPage());
    }

    @Test
    public void bindingADisplayPageWritesItAndRefreshes() {
        WallPagesStore store = store(of(1, home(1), new Page(4, PageKind.DISPLAY, null, null)));
        PageOverviewPages rules = rules(store);
        rules.bindDisplayPage(4, "gimp");
        assertEquals("gimp", rules.displayBinding(4));
        assertNull(rules.displayBinding(99));
        assertEquals(Arrays.asList("cards", "pages"), launcher.calls);
    }

    @Test
    public void aBoundDisplayGoesAfterTheCurrentPageElseAtTheEnd() {
        WallPagesStore store = store(of(1, home(1), home(2), terminal(3)));
        launcher.after = 1;
        int id = rules(store).addBoundDisplay("gimp");
        assertEquals(Arrays.asList(1, id, 2, 3), ids(store.current().pages()));
        assertEquals("gimp", store.current().page(id).binding);
        assertEquals(Arrays.asList("cards", "pages"), launcher.calls);

        launcher.after = 77;
        int end = rules(store).addBoundDisplay("xterm");
        List<Integer> order = ids(store.current().pages());
        assertEquals(end, (int) order.get(order.size() - 1));
    }

    // ---- removing a window, and Start in ---------------------------------------------------

    @Test
    public void removingTheLastWindowMovesStartInOffTheTerminal() {
        WallPagesStore store = store(of(2, home(1), terminal(2)));
        assertEquals(WallRow.START_IN_WORKSPACE, store.current().startIn());
        rules(store).startInAfterRemoval(PageOverviewPolicy.Kind.TERMINAL, 0);
        assertEquals(WallRow.START_IN_HOME, store.current().startIn());
        assertEquals(1, store.current().homeId());
    }

    @Test
    public void removingAWindowWithOthersLeftKeepsStartIn() {
        WallPagesStore store = store(of(2, home(1), terminal(2)));
        WallPages before = store.current();
        rules(store).startInAfterRemoval(PageOverviewPolicy.Kind.TERMINAL, 1);
        assertSame(before, store.current());
    }

    @Test
    public void removingAWindowWhileHomeIsAHomePageKeepsStartIn() {
        WallPagesStore store = store(of(1, home(1), terminal(2)));
        WallPages before = store.current();
        rules(store).startInAfterRemoval(PageOverviewPolicy.Kind.TERMINAL, 0);
        assertSame(before, store.current());
    }

    // ---- hidden and detached Home pages ----------------------------------------------------

    @Test
    public void hiddenHomePagesAreTheListedOnesOnlyWhileTheWallHidesThem() {
        PageOverviewPages rules = rules(store(of(1, home(1), home(2), terminal(3))));
        assertEquals(0, rules.hiddenHomePages(true));
        assertEquals(2, rules.hiddenHomePages(false));
        assertEquals(0, rules.detachedHomePages());
    }

    @Test
    public void detachedHomePagesCountOnlyHomePages() {
        List<Page> detached = Arrays.asList(home(5), home(6),
            new Page(7, PageKind.DISPLAY, null, null));
        WallPages pages = new WallPages(Collections.singletonList(terminal(1)),
            Collections.emptyList(), detached, 1, 8, false);
        PageOverviewPages rules = rules(store(pages));
        assertEquals(2, rules.detachedHomePages());
        assertEquals(0, rules.hiddenHomePages(false));
    }

    // ---- Home page names --------------------------------------------------------------------

    @Test
    public void aSingleHomePageHasItsOwnNameOrNone() {
        assertNull(rules(store(of(1, home(1), terminal(2)))).homePageName(0));
        assertEquals("Mine", rules(store(of(1, new Page(1, PageKind.HOME, "Mine", null),
            terminal(2)))).homePageName(0));
    }

    @Test
    public void severalHomePagesAreNamedByTheirPlaceAmongHomePages() {
        PageOverviewPages rules = rules(store(of(1,
            new Page(1, PageKind.HOME, "First", null), terminal(3),
            new Page(2, PageKind.HOME, null, null),
            new Page(4, PageKind.HOME, "Third", null))));
        assertEquals("First", rules.homePageName(0));
        assertNull(rules.homePageName(1));
        assertEquals("Third", rules.homePageName(2));
        assertNull(rules.homePageName(-1));
        assertNull(rules.homePageName(3));
    }

    // ---- card kinds for the wall's positions ------------------------------------------------

    @Test
    public void eachWallPositionKindHasItsCardKind() {
        assertEquals(PageOverviewPolicy.Kind.HOME_PAGE,
            PageOverviewPages.cardKind(PaneWallPage.WIDGETS));
        assertEquals(PageOverviewPolicy.Kind.TERMINAL,
            PageOverviewPages.cardKind(PaneWallPage.TERMINAL));
        assertEquals(PageOverviewPolicy.Kind.DISPLAY,
            PageOverviewPages.cardKind(PaneWallPage.DISPLAY));
        assertEquals(PageOverviewPolicy.Kind.ASSISTANT,
            PageOverviewPages.cardKind(PaneWallPage.ASSISTANT));
    }

    private static void assertDisplay(@NonNull PageOverviewView.DisplayPage page, int id,
                                      @NonNull String label, int homesBefore,
                                      boolean afterTerminal, boolean assistant) {
        assertEquals(id, page.id);
        assertEquals(label, page.label);
        assertEquals(homesBefore, page.homesBefore);
        assertEquals(afterTerminal, page.afterTerminal);
        assertEquals(assistant, page.assistant);
    }

    @NonNull
    private static List<Integer> ids(@NonNull List<Page> pages) {
        List<Integer> out = new ArrayList<>();
        for (Page p : pages) out.add(p.id);
        return out;
    }
}
