package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.DisplayPagePolicy.Landing;
import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;

/** What landing on a Display page does, and where a drawer-tapped Linux app opens (A3c). */
public class DisplayPageLandingTest {

    private static Landing land(String binding, boolean found, boolean running, boolean starting,
                                boolean hasWindow, boolean routed) {
        return DisplayPagePolicy.landing(binding, found, running, starting, hasWindow, routed);
    }

    @Test public void unboundPageOnlyStartsTheDisplay() {
        assertEquals(Landing.START_DISPLAY, land(null, false, false, false, false, false));
        assertEquals(Landing.NOTHING, land(null, false, true, false, false, false));
        assertEquals(Landing.NOTHING, land(null, false, false, true, false, false));
    }

    @Test public void boundPageRaisesTheWindowOrRunsTheApp() {
        assertEquals(Landing.RAISE_APP, land("firefox", true, true, false, true, false));
        assertEquals(Landing.RUN_APP, land("firefox", true, true, false, false, false));
        assertEquals(Landing.RUN_APP, land("firefox", true, false, false, false, false));
    }

    @Test public void displayOnItsWayLeavesTheAppToItsRunner() {
        assertEquals(Landing.NOTHING, land("firefox", true, false, true, false, false));
    }

    @Test public void goneAppAndRoutedLandingDoNothing() {
        assertEquals(Landing.NOTHING, land("firefox", false, true, false, false, false));
        assertEquals(Landing.NOTHING, land("firefox", true, true, false, true, true));
        assertEquals(Landing.NOTHING, land("firefox", true, false, false, false, true));
    }

    @Test public void routedLandingIsOnlyThatPageWithinTheWindow() {
        assertTrue(DisplayPagePolicy.isRoutedLanding(5, 5, 1000, 1500));
        assertFalse(DisplayPagePolicy.isRoutedLanding(4, 5, 1000, 1500));
        assertFalse(DisplayPagePolicy.isRoutedLanding(5, -1, 1000, 1500));
        assertFalse(DisplayPagePolicy.isRoutedLanding(5, 5, 1000,
            1000 + DisplayPagePolicy.ROUTED_LANDING_MS + 1));
    }

    @Test public void routeThenAddLeavesTheNewPageUnbound() {
        WallPages pages = WallPagesMigration.seed("home", 0, "home", 2, true);
        // The seed has an unbound Display page: it is routed to, nothing added.
        WallPages.Route route = pages.routeLinuxApp("firefox", 1);
        assertFalse(route.needsNewPage());
        WallPages.Page display = pages.page(route.pageId);
        assertEquals(PageKind.DISPLAY, display.kind);
        assertNull(display.binding);
        // With the page bound to another app, a new unbound one is added.
        WallPages bound = pages.bind(route.pageId, "gimp").pages;
        WallPages.Route add = bound.routeLinuxApp("firefox", 1);
        assertTrue(add.needsNewPage());
        WallPages.Result added = add.addTo(bound);
        assertTrue(added.ok());
        assertNull(added.page.binding);
        assertEquals(route.pageId, bound.routeLinuxApp("gimp", 1).pageId);
    }
}
