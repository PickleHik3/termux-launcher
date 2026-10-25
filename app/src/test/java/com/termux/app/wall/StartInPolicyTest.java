package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageOverviewPolicy.Kind;

import org.junit.Test;

/**
 * Which page is Home. Start in is the one store: the Home marker in Edit pages is read from it,
 * choosing a card writes it, and Display holds only while the display starts with the launcher.
 */
public class StartInPolicyTest {

    private static final String HOME = WallRow.START_IN_HOME;
    private static final String WORKSPACE = WallRow.START_IN_WORKSPACE;
    private static final String DISPLAY = WallRow.START_IN_DISPLAY;

    @Test
    public void storedValuesKeepTheirMeaningAndAnythingUnknownIsHome() {
        assertEquals(HOME, StartInPolicy.read("home"));
        assertEquals(WORKSPACE, StartInPolicy.read("workspace"));
        assertEquals(DISPLAY, StartInPolicy.read("display"));
        assertEquals(HOME, StartInPolicy.read(null));
        assertEquals(HOME, StartInPolicy.read("TERMINAL"));
    }

    @Test
    public void choosingACardWritesStartIn() {
        StartInPolicy.Choice page = StartInPolicy.choose(Kind.HOME_PAGE);
        assertEquals(HOME, page.startIn);
        assertFalse(page.startsDisplay);
        StartInPolicy.Choice terminal = StartInPolicy.choose(Kind.TERMINAL);
        assertEquals(WORKSPACE, terminal.startIn);
        assertFalse(terminal.startsDisplay);
        StartInPolicy.Choice display = StartInPolicy.choose(Kind.DISPLAY);
        assertEquals(DISPLAY, display.startIn);
        assertTrue("Display turns the display's start on", display.startsDisplay);
    }

    @Test
    public void choosingInSettingsWritesTheSame() {
        assertTrue(StartInPolicy.choose(DISPLAY).startsDisplay);
        assertFalse(StartInPolicy.choose(WORKSPACE).startsDisplay);
        assertEquals(HOME, StartInPolicy.choose("bogus").startIn);
    }

    @Test
    public void displayHoldsOnlyWhileTheDisplayStartsWithTheLauncher() {
        assertEquals(DISPLAY, StartInPolicy.effective(DISPLAY, true, false));
        assertEquals("off, with a terminal open", WORKSPACE,
            StartInPolicy.effective(DISPLAY, false, true));
        assertEquals("off, with none", HOME, StartInPolicy.effective(DISPLAY, false, false));
        // Home and the Workspace do not depend on the display.
        assertEquals(HOME, StartInPolicy.effective(HOME, false, true));
        assertEquals(WORKSPACE, StartInPolicy.effective(WORKSPACE, false, false));
    }

    @Test
    public void removingTheDisplayThatIsHomeFallsBack() {
        assertEquals(WORKSPACE, StartInPolicy.afterRemoval(DISPLAY, Kind.DISPLAY, 2));
        assertEquals(HOME, StartInPolicy.afterRemoval(DISPLAY, Kind.DISPLAY, 0));
        assertNull("it was not Home", StartInPolicy.afterRemoval(WORKSPACE, Kind.DISPLAY, 0));
    }

    @Test
    public void removingTerminalsFallsBackOnlyWhenNoneIsLeft() {
        assertNull("another terminal is first now",
            StartInPolicy.afterRemoval(WORKSPACE, Kind.TERMINAL, 1));
        assertEquals(HOME, StartInPolicy.afterRemoval(WORKSPACE, Kind.TERMINAL, 0));
        assertNull(StartInPolicy.afterRemoval(DISPLAY, Kind.TERMINAL, 0));
        assertNull(StartInPolicy.afterRemoval(HOME, Kind.TERMINAL, 0));
    }

    @Test
    public void removingAHomePageLeavesStartIn() {
        assertNull(StartInPolicy.afterRemoval(HOME, Kind.HOME_PAGE, 0));
    }

    @Test
    public void theMarkerFollowsStartIn() {
        assertTrue(StartInPolicy.isHome(Kind.HOME_PAGE, true, false, HOME));
        assertFalse("not the page Home opens on",
            StartInPolicy.isHome(Kind.HOME_PAGE, false, false, HOME));
        assertFalse("Start in left Home",
            StartInPolicy.isHome(Kind.HOME_PAGE, true, false, WORKSPACE));
        assertTrue(StartInPolicy.isHome(Kind.TERMINAL, false, true, WORKSPACE));
        assertFalse("only the first terminal",
            StartInPolicy.isHome(Kind.TERMINAL, false, false, WORKSPACE));
        assertTrue(StartInPolicy.isHome(Kind.DISPLAY, false, false, DISPLAY));
        assertFalse(StartInPolicy.isHome(Kind.DISPLAY, false, false, HOME));
    }

    @Test
    public void exactlyOneKindWearsTheMarkerForEachValue() {
        for (String startIn : new String[] {HOME, WORKSPACE, DISPLAY}) {
            int marked = 0;
            for (Kind kind : Kind.values()) {
                if (StartInPolicy.isHome(kind, true, true, startIn)) marked++;
            }
            assertEquals(startIn, 1, marked);
        }
    }
}
