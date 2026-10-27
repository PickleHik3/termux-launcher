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
        assertEquals(1, ok(ok(p.chooseStartIn("workspace")).chooseStartIn("home")).home().id);
        assertTrue(ok(p.chooseStartIn("display")).homeIsDisplayWindow());
        assertEquals(1, ok(ok(p.chooseStartIn("display")).chooseStartIn("home")).home().id);
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
        // Home is not given back: it stays where Terminal only put it.
        assertEquals(3, back.home().id);
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
}
