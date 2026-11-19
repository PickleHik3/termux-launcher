package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageOverviewPolicy.Kind;

import org.junit.Test;

/**
 * Which page is Home. Start in is the one store: the Home marker in Edit pages is read from it,
 * choosing a card writes it, and Display holds only while the display starts with the launcher.
 */
public class StartInPolicyTest {

    private static final String HOME = WallRow.START_IN_HOME;
    private static final String TERMINAL = WallRow.START_IN_TERMINAL;
    private static final String DISPLAY = WallRow.START_IN_DISPLAY;

    @Test
    public void storedValuesKeepTheirMeaningAndAnythingUnknownIsHome() {
        assertEquals(HOME, StartInPolicy.read("home"));
        assertEquals(TERMINAL, StartInPolicy.read("workspace"));
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
        assertEquals(TERMINAL, terminal.startIn);
        assertFalse(terminal.startsDisplay);
        StartInPolicy.Choice display = StartInPolicy.choose(Kind.DISPLAY);
        assertEquals(DISPLAY, display.startIn);
        assertTrue("Display turns the display's start on", display.startsDisplay);
    }

    @Test
    public void choosingInSettingsWritesTheSame() {
        assertTrue(StartInPolicy.choose(DISPLAY).startsDisplay);
        assertFalse(StartInPolicy.choose(TERMINAL).startsDisplay);
        assertEquals(HOME, StartInPolicy.choose("bogus").startIn);
    }

    @Test
    public void displayHoldsOnlyWhileTheDisplayStartsWithTheLauncher() {
        assertEquals(DISPLAY, StartInPolicy.effective(DISPLAY, true, false));
        assertEquals("off, with a terminal open", TERMINAL,
            StartInPolicy.effective(DISPLAY, false, true));
        assertEquals("off, with none", HOME, StartInPolicy.effective(DISPLAY, false, false));
        // Home and the Terminal do not depend on the display.
        assertEquals(HOME, StartInPolicy.effective(HOME, false, true));
        assertEquals(TERMINAL, StartInPolicy.effective(TERMINAL, false, false));
    }

    @Test
    public void theMarkerFollowsStartIn() {
        assertTrue(StartInPolicy.isHome(Kind.HOME_PAGE, true, HOME));
        assertFalse("not the page Home opens on",
            StartInPolicy.isHome(Kind.HOME_PAGE, false, HOME));
        assertFalse("Start in left Home",
            StartInPolicy.isHome(Kind.HOME_PAGE, true, TERMINAL));
        assertTrue("the Terminal card, whatever its windows",
            StartInPolicy.isHome(Kind.TERMINAL, false, TERMINAL));
        assertFalse(StartInPolicy.isHome(Kind.TERMINAL, false, HOME));
        assertTrue(StartInPolicy.isHome(Kind.DISPLAY, false, DISPLAY));
        assertFalse(StartInPolicy.isHome(Kind.DISPLAY, false, HOME));
    }

    @Test
    public void theHomeHalfOpensOnTheChosenPageOnlyWhileHomeIsStartIn() {
        assertEquals(2, StartInPolicy.homePage(2, HOME));
        assertEquals("a window as Home: page 1", 0, StartInPolicy.homePage(2, TERMINAL));
        assertEquals(0, StartInPolicy.homePage(2, DISPLAY));
        assertEquals("unknown reads as Home", 2, StartInPolicy.homePage(2, "elsewhere"));
        assertEquals(0, StartInPolicy.homePage(-1, HOME));
    }

    @Test
    public void assistantIsAStartInValueAndNoHomeCardWearsItsMarker() {
        assertEquals(WallRow.START_IN_ASSISTANT, StartInPolicy.read(WallRow.START_IN_ASSISTANT));
        assertEquals(0, StartInPolicy.homePage(2, WallRow.START_IN_ASSISTANT));
        for (Kind kind : Kind.values()) {
            assertFalse(kind.name(), StartInPolicy.isHome(kind, true,
                WallRow.START_IN_ASSISTANT));
        }
    }

    @Test
    public void exactlyOneKindWearsTheMarkerForEachValue() {
        for (String startIn : new String[] {HOME, TERMINAL, DISPLAY}) {
            int marked = 0;
            for (Kind kind : Kind.values()) {
                if (StartInPolicy.isHome(kind, true, startIn)) marked++;
            }
            assertEquals(startIn, 1, marked);
        }
    }
}
