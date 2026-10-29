package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.WallPages.Error;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.app.wall.WallPages.Result;
import com.termux.app.wall.WallPages.Stop;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The page list: its edits, its rules and the stops it expands to. */
public class WallPagesTest {

    /** [Home 1][Home 2][Terminal], home = Home 1, ids 1 2 3. */
    private static WallPages three() {
        return WallPagesMigration.seed("home", 0, "home", 2);
    }

    private static List<Integer> ids(WallPages p) {
        List<Integer> out = new ArrayList<>();
        for (WallPages.Page page : p.pages()) out.add(page.id);
        return out;
    }

    private static WallPages ok(Result r) {
        assertTrue("refused: " + r.error, r.ok());
        return r.pages;
    }

    @Test
    public void addInsertsAfterAndHandsOutFreshIds() {
        WallPages p = ok(three().add(PageKind.HOME, 1, null));
        assertEquals(java.util.Arrays.asList(1, 4, 2, 3), ids(p));
        assertEquals(5, p.nextId());
        Result end = p.add(PageKind.DISPLAY, WallPages.END, "app");
        assertEquals(java.util.Arrays.asList(1, 4, 2, 3, 5), ids(end.pages));
        assertEquals("app", end.page.binding);
        assertEquals(4, end.toIndex);
    }

    @Test
    public void addRefusesSecondTerminalUnknownAnchorAndBindingOnNonDisplay() {
        assertEquals(Error.SECOND_TERMINAL, three().add(PageKind.TERMINAL, 1, null).error);
        assertEquals(Error.UNKNOWN_PAGE, three().add(PageKind.HOME, 99, null).error);
        assertEquals(Error.BINDING_NOT_DISPLAY, three().add(PageKind.HOME, 1, "x").error);
        WallPages p = three();
        assertSame(p, p.add(PageKind.TERMINAL, 1, null).pages);
    }

    @Test
    public void firstPageAddedToAnEmptyValueIsHome() {
        WallPages p = ok(WallPages.EMPTY.add(PageKind.TERMINAL, WallPages.END, null));
        assertEquals(1, p.home().id);
    }

    @Test
    public void idsAreNeverReused() {
        WallPages p = ok(three().remove(2));
        p = ok(p.commitRemoval(2));
        p = ok(p.add(PageKind.HOME, WallPages.END, null));
        assertEquals(4, p.pages().get(p.pages().size() - 1).id);
    }

    @Test
    public void moveReordersAmongPagesOnTheWall() {
        WallPages p = ok(three().move(3, 0));
        assertEquals(java.util.Arrays.asList(3, 1, 2), ids(p));
        p = ok(p.move(3, 2));
        assertEquals(java.util.Arrays.asList(1, 2, 3), ids(p));
        Result r = three().move(1, 1);
        assertEquals(java.util.Arrays.asList(2, 1, 3), ids(r.pages));
        assertEquals(0, r.fromIndex);
        assertEquals(1, r.toIndex);
        assertEquals(WallPages.ChangeType.NONE, three().move(2, 1).change);
        assertEquals(Error.BAD_INDEX, three().move(1, 3).error);
        assertEquals(Error.BAD_INDEX, three().move(1, -1).error);
        assertEquals(Error.UNKNOWN_PAGE, three().move(9, 0).error);
    }

    @Test
    public void moveSkipsPendingPagesWhenCountingIndexes() {
        WallPages p = ok(three().remove(2));
        p = ok(p.move(3, 0));
        assertEquals(java.util.Arrays.asList(3, 1), ids(p));
        p = ok(p.undo(2));
        assertEquals(3, p.pages().size());
        assertTrue(ids(p).containsAll(java.util.Arrays.asList(1, 2, 3)));
    }

    @Test
    public void removeIsPendingThenUndoPutsItBackWhereItStood() {
        Result r = three().remove(2);
        assertEquals(WallPages.ChangeType.REMOVAL_PENDING, r.change);
        assertEquals(java.util.Arrays.asList(1, 3), ids(r.pages));
        assertTrue(r.pages.isPending(2));
        assertEquals(1, r.fromIndex);
        Result u = r.pages.undo(2);
        assertEquals(java.util.Arrays.asList(1, 2, 3), ids(u.pages));
        assertEquals(WallPages.ChangeType.UNDONE, u.change);
        assertEquals(1, u.toIndex);
        assertEquals(Error.NOT_PENDING, three().undo(2).error);
        assertEquals(Error.UNKNOWN_PAGE, three().undo(9).error);
    }

    @Test
    public void commitRemovalDropsThePageAndReportsIt() {
        WallPages p = ok(three().remove(2));
        Result c = p.commitRemoval(2);
        assertEquals(WallPages.ChangeType.REMOVAL_COMMITTED, c.change);
        assertEquals(2, c.page.id);
        assertEquals(2, c.pages.allPages().size());
        assertTrue(c.pages.pendingIds().isEmpty());
        assertEquals(Error.NOT_PENDING, three().commitRemoval(2).error);
        assertEquals(Error.UNKNOWN_PAGE, c.pages.commitRemoval(2).error);
    }

    @Test
    public void pendingPageIsNotAPageStopOrTarget() {
        WallPages p = ok(three().remove(1));
        assertNull(p.page(1));
        assertEquals(Error.UNKNOWN_PAGE, p.setHome(1).error);
        assertEquals(Error.UNKNOWN_PAGE, p.rename(1, "x").error);
        assertEquals(2, p.stops(1).size());
    }

    @Test
    public void setHomeMovesTheHomeAndClearsTheDisplayWindowTarget() {
        WallPages p = ok(three().setHome(3));
        assertEquals(3, p.home().id);
        WallPages d = ok(three().setHomeDisplayWindow());
        assertTrue(d.homeIsDisplayWindow());
        assertNull(d.home());
        assertEquals(2, ok(d.setHome(2)).home().id);
        assertFalse(ok(d.setHome(2)).homeIsDisplayWindow());
        assertEquals(Error.UNKNOWN_PAGE, three().setHome(9).error);
    }

    @Test
    public void removingTheHomePageMakesTheFirstPageHome() {
        WallPages p = ok(three().setHome(3));
        p = ok(p.remove(3));
        assertEquals(1, p.home().id);
        WallPages q = ok(three().remove(1));
        assertEquals(2, q.home().id);
        // A page that is not home leaves home alone.
        assertEquals(1, ok(three().remove(2)).home().id);
    }

    @Test
    public void removingTheLastHomePageIsRefusedWhileHomeAndAlone() {
        WallPages alone = WallPagesMigration.seed("home", 0, "home", 1);
        WallPages onlyHome = ok(alone.remove(2));
        assertEquals(java.util.Arrays.asList(1), ids(onlyHome));
        Result r = onlyHome.remove(1);
        assertEquals(Error.LAST_HOME, r.error);
        assertSame(onlyHome, r.pages);
        // The only page that is not Home has its own refusal.
        WallPages terminalOnly = WallPagesMigration.seed("home", 0, "terminal", 1);
        assertEquals(Error.LAST_PAGE, terminalOnly.remove(2).error);
    }

    @Test
    public void lastHomeAmongOthersCanGoAndHandsHomeOver() {
        WallPages p = ok(WallPagesMigration.seed("home", 0, "home", 1).remove(1));
        assertEquals(2, p.home().id);
    }

    @Test
    public void terminalWithWindowsNeedsConfirmation() {
        Result r = three().remove(3, 2, false);
        assertEquals(Error.NEEDS_CONFIRM, r.error);
        assertTrue(ok(three().remove(3, 2, true)).isPending(3));
        assertTrue(ok(three().remove(3, 0, false)).isPending(3));
        assertTrue(ok(three().remove(3)).isPending(3));
    }

    @Test
    public void aPendingTerminalStillBlocksASecondOne() {
        WallPages p = ok(three().remove(3));
        assertEquals(Error.SECOND_TERMINAL, p.add(PageKind.TERMINAL, WallPages.END, null).error);
        WallPages c = ok(p.commitRemoval(3));
        assertTrue(c.add(PageKind.TERMINAL, WallPages.END, null).ok());
    }

    @Test
    public void renameAndBind() {
        WallPages p = ok(three().rename(1, "  Work "));
        assertEquals("Work", p.page(1).name);
        assertNull(ok(p.rename(1, "  ")).page(1).name);
        assertNull(ok(p.rename(1, null)).page(1).name);
        WallPages d = ok(three().add(PageKind.DISPLAY, WallPages.END, null));
        int id = d.pages().get(3).id;
        assertEquals("gimp", ok(d.bind(id, "gimp")).page(id).binding);
        assertNull(ok(ok(d.bind(id, "gimp")).bind(id, null)).page(id).binding);
        assertEquals(Error.BINDING_NOT_DISPLAY, d.bind(1, "gimp").error);
        assertEquals(Error.UNKNOWN_PAGE, d.bind(99, "gimp").error);
    }

    @Test
    public void displayNames() {
        WallPages p = ok(three().add(PageKind.DISPLAY, WallPages.END, "gimp"));
        assertEquals("Home 1", p.displayName(p.page(1), null));
        assertEquals("Home 2", p.displayName(p.page(2), null));
        assertEquals("Terminal", p.displayName(p.page(3), null));
        assertEquals("GIMP", p.displayName(p.page(4), "GIMP"));
        assertEquals("Display", p.displayName(p.page(4), null));
        WallPages unbound = ok(three().add(PageKind.DISPLAY, WallPages.END, null));
        assertEquals("Display", unbound.displayName(unbound.page(4), "GIMP"));
        assertEquals("Mine", ok(p.rename(2, "Mine")).displayName(ok(p.rename(2, "Mine")).page(2), null));
    }

    @Test
    public void homeNumbersFollowPositionAmongHomePagesOnTheWall() {
        WallPages p = ok(three().move(2, 0));
        assertEquals("Home 1", p.displayName(p.page(2), null));
        assertEquals("Home 2", p.displayName(p.page(1), null));
        WallPages gone = ok(three().remove(1));
        assertEquals("Home 1", gone.displayName(gone.page(2), null));
    }

    @Test
    public void stopsForNoWindowsOneWindowAndThree() {
        WallPages p = three();
        List<Stop> none = p.stops(0);
        assertEquals(3, none.size());
        assertTrue(none.get(2).startsShell);
        assertEquals(3, none.get(2).pageId);
        assertEquals(-1, none.get(0).window);
        assertFalse(none.get(0).startsShell);

        List<Stop> one = p.stops(1);
        assertEquals(3, one.size());
        assertFalse(one.get(2).startsShell);
        assertEquals(0, one.get(2).window);

        List<Stop> three = p.stops(3);
        assertEquals(5, three.size());
        assertEquals(Stop.window(3, 0), three.get(2));
        assertEquals(Stop.window(3, 2), three.get(4));
    }

    @Test
    public void stopsFollowAFreeOrder() {
        WallPages p = ok(three().move(3, 0));
        List<Stop> stops = p.stops(2);
        assertEquals(Stop.window(3, 0), stops.get(0));
        assertEquals(Stop.window(3, 1), stops.get(1));
        assertEquals(Stop.of(1), stops.get(2));
    }

    @Test
    public void indexOfAndNeighboursAtBothEnds() {
        WallPages p = three();
        assertEquals(0, p.indexOf(Stop.of(1), 2));
        assertEquals(3, p.indexOf(Stop.window(3, 1), 2));
        assertEquals(-1, p.indexOf(Stop.window(3, 5), 2));
        WallPages.Neighbours first = p.neighbours(Stop.of(1), 2);
        assertNull(first.previous);
        assertEquals(Stop.of(2), first.next);
        WallPages.Neighbours mid = p.neighbours(Stop.window(3, 0), 2);
        assertEquals(Stop.of(2), mid.previous);
        assertEquals(Stop.window(3, 1), mid.next);
        WallPages.Neighbours last = p.neighbours(Stop.window(3, 1), 2);
        assertEquals(Stop.window(3, 0), last.previous);
        assertNull(last.next);
        WallPages.Neighbours stranger = p.neighbours(Stop.of(9), 2);
        assertNull(stranger.previous);
        assertNull(stranger.next);
    }

    @Test
    public void aShellStartStopMatchesItsWindowZero() {
        WallPages p = three();
        assertEquals(2, p.indexOf(Stop.window(3, 0), 0));
        assertEquals(Stop.of(2), p.neighbours(new Stop(3, 0, true), 0).previous);
    }

    @Test
    public void aRefusalLeavesTheValueAsItWas() {
        WallPages p = three();
        assertSame(p, p.setHome(9).pages);
        assertNull(p.setHome(9).page);
        assertFalse(p.setHome(9).ok());
    }

    @Test
    public void routeLinuxAppPrefersTheBoundPageThenTheFirstUnboundThenAddsAfterCurrent() {
        // three(): Home 1, Home 2, Terminal 3; no Display page yet.
        WallPages p = three();
        assertEquals("add after 2", p.routeLinuxApp("gimp", 2).toString());
        assertEquals(2, p.routeLinuxApp("gimp", 2).addAfterId);
        assertEquals(WallPages.END, p.routeLinuxApp("gimp", 99).addAfterId);
        Result added = p.routeLinuxApp("gimp", 3).addTo(p);
        assertEquals(PageKind.DISPLAY, added.page.kind);
        assertNull(added.page.binding);
        assertEquals(3, ids(added.pages).get(2).intValue());
        assertEquals(4, ids(added.pages).get(3).intValue());

        WallPages withUnbound = added.pages;
        WallPages.Route unbound = withUnbound.routeLinuxApp("gimp", 1);
        assertEquals(4, unbound.pageId);
        assertFalse(unbound.needsNewPage());
        assertNull(unbound.addTo(withUnbound));

        WallPages two = ok(withUnbound.add(PageKind.DISPLAY, 4, "gimp"));
        assertEquals(5, two.routeLinuxApp("gimp", 1).pageId);
        assertEquals(4, two.routeLinuxApp("blender", 1).pageId);
        assertEquals(4, two.routeLinuxApp(null, 1).pageId);

        // Every Display page bound: a new one, after the current page.
        WallPages bound = ok(two.bind(4, "krita"));
        assertEquals("add after 5", bound.routeLinuxApp("blender", 5).toString());
        // A pending page does not count.
        WallPages gone = ok(two.remove(5));
        assertEquals(4, gone.routeLinuxApp("gimp", 1).pageId);
    }

    @Test
    public void startInIsDerivedFromWhereHomeIs() {
        assertEquals("home", three().startIn());
        assertEquals("workspace", ok(three().setHome(3)).startIn());
        assertEquals("display", ok(three().setHomeDisplayWindow()).startIn());
        assertEquals(1, ok(three().setHome(2)).homeIndex());
        assertEquals(0, ok(three().setHome(3)).homeIndex());
    }

    @Test
    public void chooseStartInKeepsTheHomePageHomeAlreadyIs() {
        WallPages p = ok(three().setHome(2));
        assertEquals(WallPages.ChangeType.NONE, p.chooseStartIn("home").change);
        assertEquals(3, ok(p.chooseStartIn("workspace")).home().id);
        assertEquals(2, ok(ok(p.chooseStartIn("workspace")).chooseStartIn("home")).home().id);
        WallPages d = ok(p.chooseStartIn("display"));
        assertFalse(d.homeIsDisplayWindow());
        assertEquals(PageKind.DISPLAY, d.home().kind);
        assertEquals("display", d.startIn());
        assertEquals(2, ok(d.chooseStartIn("home")).home().id);
        assertEquals(WallPages.ChangeType.NONE, d.chooseStartIn("display").change);

        // The display page home is the first unbound one, not a second.
        WallPages two = ok(ok(three().add(PageKind.DISPLAY, WallPages.END, null))
            .setHome(2));
        assertEquals(4, ok(two.chooseStartIn("display")).home().id);
        assertEquals(4, ok(two.chooseStartIn("display")).pages().size());
        assertEquals(Error.BAD_INDEX, three().chooseHomePage(2).error);
        assertEquals(2, ok(three().chooseHomePage(1)).home().id);
    }

    @Test
    public void homePageEditsByHomeIndexFollowTheRepository() {
        WallPages p = ok(three().addHomePage(-1));
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(p));
        p = ok(p.addHomePage(0));
        assertEquals(java.util.Arrays.asList(1, 5, 2, 4, 3), ids(p));
        p = ok(p.moveHomePage(3, 0));
        assertEquals(java.util.Arrays.asList(4, 1, 5, 2, 3), ids(p));
        Result r = p.removeHomePage(1);
        assertEquals(1, r.page.id);
        assertEquals(java.util.Arrays.asList(4, 5, 2, 3), ids(r.pages));
        assertEquals(Error.BAD_INDEX, p.moveHomePage(0, 4).error);
        assertEquals(Error.BAD_INDEX, p.removeHomePage(4).error);
    }

    @Test
    public void detachHomesMovesHomePagesAndHomeGoesToTheTerminal() {
        WallPages p = ok(three().detachHomes());
        assertEquals(java.util.Arrays.asList(3), ids(p));
        assertEquals(2, p.detached().size());
        assertEquals(3, p.home().id);
        // Detaching again changes nothing.
        assertEquals(WallPages.ChangeType.NONE, p.detachHomes().change);
    }

    @Test
    public void detachHomesKeepsTheDisplayWindowAsHome() {
        WallPages p = ok(ok(three().setHomeDisplayWindow()).detachHomes());
        assertTrue(p.homeIsDisplayWindow());
        assertEquals(3, p.pages().get(0).id);
    }

    @Test
    public void attachDetachedPutsHomePagesBackBeforeTheTerminalWithTheirIds() {
        WallPages detached = ok(three().detachHomes());
        WallPages back = ok(detached.attachDetached());
        assertEquals(java.util.Arrays.asList(1, 2, 3), ids(back));
        assertTrue(back.detached().isEmpty());
        // Home goes back to the Home page it was parked from.
        assertEquals(1, back.home().id);
        assertEquals(WallPages.ChangeType.NONE, three().attachDetached().change);
        // Pages added meanwhile stay after the Homes' return point, before the Terminal.
        WallPages more = ok(detached.add(PageKind.DISPLAY, 3, null));
        assertEquals(java.util.Arrays.asList(1, 2, 3, 4), ids(ok(more.attachDetached())));
    }

    @Test
    public void detachThenAttachKeepsEveryId() {
        WallPages p = ok(ok(three().addHomePage(-1)).detachHomes());
        assertEquals(java.util.Arrays.asList(1, 2, 4), idsOf(p.detached()));
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(ok(p.attachDetached())));
    }

    private static List<Integer> idsOf(List<WallPages.Page> pages) {
        List<Integer> out = new ArrayList<>();
        for (WallPages.Page page : pages) out.add(page.id);
        return out;
    }

    @Test
    public void aTripThroughTerminalOnlyComesBackToTheSameHomePage() {
        WallPages p = ok(three().setHome(2));
        WallPages terminalOnly = ok(p.detachHomes());
        assertEquals(3, terminalOnly.home().id);
        assertEquals(2, terminalOnly.lastHomePageId());
        WallPages back = ok(terminalOnly.attachDetached());
        assertEquals(2, back.home().id);
        assertEquals("home", back.startIn());
        assertFalse(back.homeParked());
    }

    @Test
    public void aTripThroughTerminalOnlyFromTheWorkspaceStaysOnTheWorkspace() {
        WallPages p = ok(ok(three().setHome(2)).setHome(3));
        WallPages back = ok(ok(p.detachHomes()).attachDetached());
        assertEquals(3, back.home().id);
        // ... and Start in Home from there still finds Home page 2.
        assertEquals(2, ok(back.chooseStartIn("home")).home().id);
    }

    @Test
    public void aTripThroughTerminalOnlyAndTheDisplayKeepsHomeAndMemory() {
        WallPages p = ok(ok(three().setHome(2)).setHomeDisplayWindow());
        assertEquals(2, p.lastHomePageId());
        WallPages back = ok(ok(p.detachHomes()).attachDetached());
        assertTrue(back.homeIsDisplayWindow());
        assertEquals(2, ok(back.chooseStartIn("home")).home().id);
    }

    @Test
    public void startInHomeAfterTheWorkspaceReturnsToTheRememberedHomePage() {
        WallPages p = ok(three().setHome(2));
        WallPages ws = ok(p.chooseStartIn("workspace"));
        assertEquals(2, ws.lastHomePageId());
        assertEquals(2, ok(ws.chooseStartIn("home")).home().id);
        // When that page is gone, the first Home page.
        WallPages gone = ok(ok(ws.remove(2)).commitRemoval(2));
        assertEquals(1, ok(gone.chooseStartIn("home")).home().id);
        // A fresh seed has no memory and falls to the first Home page.
        assertEquals(1, ok(WallPagesMigration.seed("workspace", 1, "home", 3)
            .chooseStartIn("home")).home().id);
    }

    @Test
    public void withHomeCountAppendsAndDropsAtTheEnd() {
        WallPages p = three();
        assertSame(p, p.withHomeCount(2));
        WallPages more = p.withHomeCount(4);
        assertEquals(java.util.Arrays.asList(1, 2, 4, 5, 3), ids(more));
        assertEquals(6, more.nextId());
        WallPages fewer = more.withHomeCount(1);
        assertEquals(java.util.Arrays.asList(1, 3), ids(fewer));
        assertEquals(1, fewer.homeCount());
        assertEquals(java.util.Arrays.asList(1, 3), ids(p.withHomeCount(0)));
    }

    @Test
    public void withHomeCountHandsHomeOverWhenTheHomePageIsDropped() {
        WallPages p = ok(three().setHome(2)).withHomeCount(1);
        assertEquals(1, p.home().id);
    }

    @Test
    public void withHomeCountCountsDetachedHomesUnderTerminalOnly() {
        WallPages t = ok(three().detachHomes());
        assertEquals(2, t.homeCount());
        WallPages more = t.withHomeCount(3);
        assertEquals(java.util.Arrays.asList(3), ids(more));
        assertEquals(3, more.detached().size());
        assertEquals(1, t.withHomeCount(1).detached().size());
    }

    @Test
    public void withHomeMappingReordersAndDropsLikeTheRepository() {
        WallPages p = three().withHomeCount(3); // Homes 1 2 4, Terminal 3
        WallPages moved = p.withHomeMapping(new int[] {2, 0, 1}); // page 0 to the end
        assertEquals(java.util.Arrays.asList(2, 4, 1, 3), ids(moved));
        WallPages removed = p.withHomeMapping(new int[] {0, -1, 1});
        assertEquals(java.util.Arrays.asList(1, 4, 3), ids(removed));
        assertSame(p, p.withHomeMapping(new int[] {0, 1}));
        assertEquals(java.util.Arrays.asList(1, 3), ids(three().withHomeMapping(new int[] {-1, -1})));
    }

    @Test
    public void slotsGroupHomeRunsAndKeepTheTerminalAlone() {
        WallPages p = three();
        assertEquals(2, p.slots().size());
        assertEquals(java.util.Arrays.asList(1, 2), p.slots().get(0).pageIds);
        assertEquals(PageKind.TERMINAL, p.slots().get(1).kind);
        // [Home 1][Terminal][Home 2]: the terminal moves to index 1.
        WallPages mid = ok(p.move(3, 1));
        assertEquals(3, mid.slots().size());
        assertEquals(java.util.Arrays.asList(1), mid.slots().get(0).pageIds);
        assertEquals(java.util.Arrays.asList(2), mid.slots().get(2).pageIds);
        assertEquals(2, mid.slotIndexOf(2));
        assertEquals(1, mid.slotIndexOf(3));
        assertEquals(-1, mid.slotIndexOf(99));
        // [Terminal][Home 1][Home 2]
        assertEquals(2, ok(p.move(3, 0)).slots().size());
    }

    @Test
    public void parallaxRowReadsTheStopsInAnyOrder() {
        WallPages p = three();
        for (int w = 0; w < 3; w++) {
            assertEquals(WallParallax.rowIndex(PaneWallPage.TERMINAL, w, 2, 3),
                WallParallax.rowIndex(p, Stop.window(3, w), 3));
        }
        assertEquals(WallParallax.rowCount(2, 3), WallParallax.rowCount(p, 3));
        assertEquals(1, WallParallax.rowIndex(p, Stop.of(2), 3));
        WallPages mid = ok(p.move(3, 1));
        assertEquals(2, WallParallax.rowIndex(mid, Stop.window(3, 1), 3));
        assertEquals(4, WallParallax.rowIndex(mid, Stop.of(2), 3));
    }

    @Test
    public void aMoveMayStandTwoDisplayPagesSideBySide() {
        WallPages d = ok(three().add(PageKind.DISPLAY, WallPages.END, null));
        List<WallPages.Page> v = d.pages();
        // A second Display page with one page between it and the first, the last.
        WallPages two = ok(d.add(PageKind.DISPLAY, v.get(v.size() - 3).id, null));
        List<WallPages.Page> w = two.pages();
        assertFalse(two.hasAdjacentDisplays());
        int between = w.get(w.size() - 2).id;
        Result moved = two.move(between, 0);
        assertTrue(moved.ok());
        assertEquals(WallPages.ChangeType.MOVED, moved.change);
        assertTrue(moved.pages.hasAdjacentDisplays());
        int other = w.get(w.size() - 1).id;
        assertEquals(PageKind.DISPLAY, moved.pages.leftOf(other).kind);
    }

    /** A wall of these kinds, ids 1.. in order, home = the first page. */
    private static WallPages wall(PageKind... kinds) {
        List<WallPages.Page> list = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++) list.add(new WallPages.Page(i + 1, kinds[i], null, null));
        return new WallPages(list, java.util.Collections.<Integer>emptyList(),
            java.util.Collections.<WallPages.Page>emptyList(), 1, kinds.length + 1, false);
    }

    @Test
    public void aDisplayPageAddedBesideADisplayPageStaysWhereItWasAsked() {
        // [H1][D2][H3][T4]: the drawer on D2 adds right after D2.
        WallPages p = wall(PageKind.HOME, PageKind.DISPLAY, PageKind.HOME, PageKind.TERMINAL);
        WallPages bound = ok(p.bind(2, "gimp"));
        WallPages.Route route = bound.routeLinuxApp("krita", 2);
        assertEquals(2, route.addAfterId);
        Result added = route.addTo(bound);
        assertTrue(added.ok());
        assertEquals(java.util.Arrays.asList(1, 2, 5, 3, 4), ids(added.pages));
        assertEquals(2, added.toIndex);
        assertTrue(added.pages.hasAdjacentDisplays());
        // At the end beside a last Display page: still last.
        WallPages end = wall(PageKind.HOME, PageKind.TERMINAL, PageKind.DISPLAY);
        assertEquals(java.util.Arrays.asList(1, 2, 3, 4), ids(ok(end.add(PageKind.DISPLAY, WallPages.END, null))));
    }

    @Test
    public void aRemovalThatJoinsTwoDisplayPagesLeavesThemSideBySide() {
        // [H1][D2][T3][D4][H5]: removing T3 stands D2 by D4, and undo puts T3 back between.
        WallPages p = wall(PageKind.HOME, PageKind.DISPLAY, PageKind.TERMINAL, PageKind.DISPLAY,
            PageKind.HOME);
        WallPages gone = ok(p.remove(3));
        assertEquals(java.util.Arrays.asList(1, 2, 4, 5), ids(gone));
        assertTrue(gone.hasAdjacentDisplays());
        WallPages back = ok(gone.undo(3));
        assertEquals(java.util.Arrays.asList(1, 2, 3, 4, 5), ids(back));
        // Stored side by side (an older build): separated on request, the later one moved.
        WallPages stored = wall(PageKind.HOME, PageKind.DISPLAY, PageKind.DISPLAY,
            PageKind.TERMINAL);
        assertTrue(stored.hasAdjacentDisplays());
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(stored.separateDisplays()));
        WallPages fine = three();
        assertSame(fine, fine.separateDisplays());
    }

    @Test
    public void cellIndexAndNeighboursFollowThePageOrder() {
        WallPages p = wall(PageKind.HOME, PageKind.TERMINAL, PageKind.DISPLAY);
        assertEquals(0, p.cellIndexOf(1));
        assertEquals(2, p.cellIndexOf(3));
        assertEquals(-1, p.cellIndexOf(9));
        assertNull(p.leftOf(1));
        assertEquals(2, p.rightOf(1).id);
        assertEquals(1, p.leftOf(2).id);
        assertEquals(3, p.rightOf(2).id);
        assertEquals(2, p.leftOf(3).id);
        assertNull(p.rightOf(3));
        assertNull(p.leftOf(9));
        assertNull(p.rightOf(9));
        assertNull(WallPages.EMPTY.rightOf(1));
    }

    @Test
    public void homeTargetIsTheHomePageOrTheNearestHomePage() {
        // One page of the kind.
        WallPages one = wall(PageKind.HOME, PageKind.TERMINAL);
        assertEquals(1, one.homeTarget(2).id);
        // Home runs on both sides of the Terminal page: the home page itself.
        WallPages runs = wall(PageKind.HOME, PageKind.TERMINAL, PageKind.HOME);
        assertEquals(1, runs.homeTarget(3).id);
        // The home page is the Display page: nearest Home from the right-hand Home, tie left.
        WallPages d = wall(PageKind.HOME, PageKind.DISPLAY, PageKind.HOME, PageKind.TERMINAL,
            PageKind.HOME);
        WallPages dHome = ok(d.setHome(2));
        assertEquals(3, dHome.homeTarget(4).id);
        assertEquals(5, dHome.homeTarget(5).id);
        assertEquals(1, dHome.homeTarget(2).id);
        // The home page is the Terminal page: nearest Home page.
        WallPages t = ok(wall(PageKind.HOME, PageKind.TERMINAL, PageKind.DISPLAY, PageKind.HOME)
            .setHome(2));
        assertEquals(1, t.homeTarget(2).id);
        assertEquals(4, t.homeTarget(4).id);
        // No Home page.
        assertNull(wall(PageKind.TERMINAL, PageKind.DISPLAY).homeTarget(1));
    }

    @Test
    public void displayTargetIsTheBoundPageElseTheNearestDisplayPage() {
        // [D1][D2][T3][D4] with two Display pages side by side.
        WallPages p = wall(PageKind.DISPLAY, PageKind.DISPLAY, PageKind.TERMINAL, PageKind.DISPLAY);
        assertEquals(1, p.displayTarget(4, 1).id);
        assertEquals(2, p.displayTarget(1, 2).id);
        // Bound page gone: nearest by cell distance, tie to the left.
        assertEquals(2, p.displayTarget(3, 9).id);
        assertEquals(4, p.displayTarget(4, 0).id);
        assertEquals(1, p.displayTarget(1, 0).id);
        // A bound id that is not a Display page does not count.
        assertEquals(2, p.displayTarget(3, 3).id);
        assertNull(wall(PageKind.HOME, PageKind.TERMINAL).displayTarget(1, 0));
        // A pending removal is not a target.
        WallPages gone = ok(p.remove(1));
        assertEquals(2, gone.displayTarget(2, 1).id);
    }

    @Test
    public void terminalTargetIsTheTerminalPageOrNull() {
        assertEquals(2, wall(PageKind.HOME, PageKind.TERMINAL).terminalTarget().id);
        assertNull(wall(PageKind.HOME, PageKind.DISPLAY).terminalTarget());
    }
}
