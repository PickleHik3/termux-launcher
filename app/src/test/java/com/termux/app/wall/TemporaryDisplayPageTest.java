package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.WallPages.ChangeType;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.app.wall.WallPages.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A drawer launch with no Display page to run on gets a temporary one (ADR 0015's 2026-11-04
 * amendment): it is on the wall, not listed in Edit pages, goes with its display, and "Add
 * display" makes it lasting.
 */
public class TemporaryDisplayPageTest {

    /** [Home 1][Home 2][Terminal], home = Home 1, ids 1 2 3. */
    private static WallPages three() {
        return WallPagesMigration.seed("home", 0, "home", 2);
    }

    private static WallPages ok(Result r) {
        assertTrue("refused: " + r.error, r.ok());
        return r.pages;
    }

    private static List<Integer> ids(List<WallPages.Page> pages) {
        List<Integer> out = new ArrayList<>();
        for (WallPages.Page p : pages) out.add(p.id);
        return out;
    }

    /** three() after a drawer launch from page 2: the temporary page is id 4, after Home 2. */
    private static WallPages launched() {
        WallPages p = three();
        WallPages.Route route = p.routeLinuxApp("firefox", 2);
        assertTrue(route.needsNewPage());
        return ok(route.addTo(p));
    }

    @Test
    public void aLaunchWithNoDisplayPageGetsATemporaryPage() {
        WallPages p = launched();
        WallPages.Page page = p.page(4);
        assertNotNull(page);
        assertEquals(PageKind.DISPLAY, page.kind);
        assertNull(page.binding);
        assertTrue(page.temporary);
        assertSame(page, p.temporaryDisplay());
        // On the wall where the launch put it, so the wall can land there.
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(p.pages()));
    }

    @Test
    public void aLaunchWithALastingDisplayPageRoutesThereAndAddsNoTemporaryPage() {
        WallPages p = ok(three().add(PageKind.DISPLAY, 3, null));
        WallPages.Route route = p.routeLinuxApp("firefox", 1);
        assertFalse(route.needsNewPage());
        assertEquals(4, route.pageId);
        assertNull(p.temporaryDisplay());
        // A page bound to the app is taken first.
        WallPages bound = ok(p.bind(4, "firefox"));
        assertEquals(4, bound.routeLinuxApp("firefox", 1).pageId);
    }

    @Test
    public void aSecondLaunchReusesTheTemporaryPage() {
        WallPages p = launched();
        WallPages.Route again = p.routeLinuxApp("gimp", 1);
        assertFalse(again.needsNewPage());
        assertEquals(4, again.pageId);
    }

    @Test
    public void aLastingUnboundPageIsPreferredOverTheTemporaryOne() {
        WallPages p = ok(launched().add(PageKind.DISPLAY, 3, null));
        assertEquals(5, p.routeLinuxApp("gimp", 1).pageId);
    }

    @Test
    public void stoppingRemovesTheTemporaryPage() {
        WallPages p = launched();
        WallPages gone = ok(ok(p.remove(4)).commitRemoval(4));
        assertNull(gone.page(4));
        assertNull(gone.temporaryDisplay());
        assertEquals(java.util.Arrays.asList(1, 2, 3), ids(gone.pages()));
    }

    @Test
    public void theStartUpSweepDropsTemporaryPagesAndKeepsTheRest() {
        WallPages p = launched();
        WallPages swept = p.withoutTemporaryPages();
        assertEquals(java.util.Arrays.asList(1, 2, 3), ids(swept.allPages()));
        assertEquals(p.homeId(), swept.homeId());
        assertEquals(p.nextId(), swept.nextId());
        // Nothing temporary: the same value.
        WallPages none = three();
        assertSame(none, none.withoutTemporaryPages());
        // Pending too.
        WallPages pending = ok(p.remove(4));
        assertTrue(pending.withoutTemporaryPages().pendingIds().isEmpty());
    }

    @Test
    public void theStartUpSweepMovesHomeOffATemporaryPage() {
        WallPages p = launched();
        WallPages onTemp = new WallPages(p.allPages(), p.pendingIds(), p.detached(), 4,
            p.nextId(), false);
        assertEquals(1, onTemp.withoutTemporaryPages().homeId());
    }

    @Test
    public void editPagesListsNoTemporaryPage() {
        WallPages p = ok(launched().add(PageKind.DISPLAY, 3, null));
        assertEquals(java.util.Arrays.asList(1, 2, 3, 5), ids(p.listedPages()));
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3, 5), ids(p.pages()));
        // Listed index 2 (Terminal) is wall index 3; past the end is the last.
        assertEquals(0, p.wallIndexOfListed(0));
        assertEquals(1, p.wallIndexOfListed(1));
        assertEquals(3, p.wallIndexOfListed(2));
        assertEquals(4, p.wallIndexOfListed(3));
        assertEquals(4, p.wallIndexOfListed(9));
    }

    @Test
    public void addDisplayMakesTheTemporaryPageLastingWhereItStands() {
        WallPages p = launched();
        Result made = p.makePermanent(4);
        assertTrue(made.ok());
        assertEquals(ChangeType.MADE_PERMANENT, made.change);
        WallPages lasting = made.pages;
        assertFalse(lasting.page(4).temporary);
        assertNull(lasting.temporaryDisplay());
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(lasting.pages()));
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(lasting.listedPages()));
        // The sweep leaves it; a second make-lasting changes nothing.
        assertSame(lasting, lasting.withoutTemporaryPages());
        assertEquals(ChangeType.NONE, lasting.makePermanent(4).change);
        assertFalse(p.makePermanent(99).ok());
    }

    @Test
    public void addDisplayWithNoPagePlacesOneLastingUnboundPageAfterTheCurrentOne() {
        Result r = three().addDisplayPage(2);
        assertEquals(ChangeType.ADDED, r.change);
        WallPages.Page page = r.pages.page(4);
        assertEquals(PageKind.DISPLAY, page.kind);
        assertNull(page.binding);
        assertFalse(page.temporary);
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(r.pages.pages()));
        // Edit pages shows its card.
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(r.pages.listedPages()));
        // An unknown current page puts it at the end; a later drawer launch routes to it.
        assertEquals(4, ids(three().addDisplayPage(99).pages.pages()).get(3).intValue());
        assertEquals(4, r.pages.routeLinuxApp("firefox", 1).pageId);
        assertFalse(r.pages.routeLinuxApp("firefox", 1).needsNewPage());
    }

    @Test
    public void addDisplayWithATemporaryPagePromotesThatPage() {
        WallPages p = launched();
        Result r = p.addDisplayPage(1);
        assertEquals(ChangeType.MADE_PERMANENT, r.change);
        assertEquals(4, r.page.id);
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(r.pages.pages()));
        assertFalse(r.pages.page(4).temporary);
        assertEquals(java.util.Arrays.asList(1, 2, 4, 3), ids(r.pages.listedPages()));
    }

    @Test
    public void addDisplayWithALastingUnboundPageAddsNoSecond() {
        WallPages p = ok(three().add(PageKind.DISPLAY, 3, null));
        Result r = p.addDisplayPage(1);
        assertEquals(ChangeType.NONE, r.change);
        assertEquals(4, r.page.id);
        assertSame(p, r.pages);
    }

    @Test
    public void homeOnDisplayMakesTheTemporaryPageLasting() {
        WallPages p = launched();
        Result r = p.homeOnDisplayPage();
        assertEquals(4, r.pages.homeId());
        assertFalse(r.pages.page(4).temporary);
    }

    @Test
    public void removingHomeNeverHandsItToATemporaryPage() {
        // [Display~ 4][Home 1][Home 2][Terminal 3], home Home 1, which is removed.
        WallPages p = ok(three().addTemporaryDisplay(WallPages.END));
        p = ok(p.move(4, 0));
        WallPages removed = ok(p.remove(1));
        assertEquals(2, removed.homeId());
    }

    @Test
    public void theAddDisplayPillStaysOfferedForATemporaryDisplay() {
        assertTrue(PageOverviewPolicy.canAddDisplay(false, false));
        assertFalse(PageOverviewPolicy.canAddDisplay(true, false));
        assertTrue(PageOverviewPolicy.canAddDisplay(true, true));
        assertTrue(PageOverviewPolicy.canAddDisplay(false, true));
    }
}
