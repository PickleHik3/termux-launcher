package com.termux.app.help;

import android.app.Application;
import android.os.Bundle;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The page stack as a bundle. It is what carries the reader across leaving help for the launcher
 * and coming back to it, so it has to survive the round trip whole — the place, the query, and
 * every frame's own scroll and open definition.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HelpNavigationStateTest {

    private static HelpNavigation restored(Bundle state) {
        HelpNavigation nav = new HelpNavigation();
        assertTrue("nothing was restored", nav.restoreState(state));
        return nav;
    }

    @Test public void theWholeStackSurvivesTheRoundTrip() {
        HelpNavigation nav = new HelpNavigation();
        nav.open(PaneWallPage.DISPLAY);
        nav.setScroll(120);
        nav.search();
        nav.setQuery("dock");
        nav.topic("dock");
        nav.setScroll(60);
        nav.openTerm("pane");

        HelpNavigation back = restored(nav.saveState());
        assertEquals(PaneWallPage.DISPLAY, back.place());
        assertEquals("dock", back.query());
        assertEquals(3, back.depth());
        assertEquals(HelpNavigation.Screen.TOPIC, back.screen());
        assertEquals("dock", back.id());
        assertEquals(60, back.frame().scroll);
        assertEquals("pane", back.frame().openTermId);

        // And Back walks the restored stack the same way it walked the original.
        assertTrue(back.back());
        assertNull(back.frame().openTermId);
        assertTrue(back.back());
        assertEquals(HelpNavigation.Screen.SEARCH, back.screen());
        assertTrue(back.back());
        assertEquals(HelpNavigation.Screen.HOME, back.screen());
        assertEquals(120, back.frame().scroll);
        assertFalse("Back at the root is the caller's to answer", back.back());
    }

    /** The explorer is the launcher's view of help, not a page: a saved stack never holds one. */
    @Test public void anExploreFrameIsNotSaved() {
        HelpNavigation nav = new HelpNavigation();
        nav.open(PaneWallPage.TERMINAL);
        nav.topic("dock");
        nav.explore("dock");
        assertEquals(HelpNavigation.Screen.EXPLORE, nav.screen());

        HelpNavigation back = restored(nav.saveState());
        assertEquals(2, back.depth());
        assertEquals(HelpNavigation.Screen.TOPIC, back.screen());
        assertEquals("dock", back.id());
    }

    /** Text entry belongs to the visit, not to the page. */
    @Test public void textEntryIsNotCarriedOver() {
        HelpNavigation nav = new HelpNavigation();
        nav.open(PaneWallPage.TERMINAL);
        nav.search();
        nav.setTextEntryActive(true);
        assertFalse(restored(nav.saveState()).textEntryActive());
    }

    @Test public void nothingToRestoreLeavesHelpAtItsHomePage() {
        HelpNavigation nav = new HelpNavigation();
        assertFalse(nav.restoreState(null));
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        assertEquals(1, nav.depth());

        HelpNavigation empty = new HelpNavigation();
        assertFalse(empty.restoreState(new Bundle()));
        assertEquals(HelpNavigation.Screen.HOME, empty.screen());
        assertEquals(PaneWallPage.TERMINAL, empty.place());
        assertEquals(1, empty.depth());
    }
}
