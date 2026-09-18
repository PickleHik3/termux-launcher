package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/** Where the reader is, and where Back takes them. */
public class HelpNavigationTest {

    private HelpNavigation opened() {
        HelpNavigation nav = new HelpNavigation();
        nav.open(PaneWallPage.TERMINAL);
        return nav;
    }

    @Test public void everyInvocationStartsAtHelpHome() {
        HelpNavigation nav = opened();
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        assertEquals(PaneWallPage.TERMINAL, nav.place());
        assertEquals(1, nav.depth());
        assertNull(nav.id());
        assertEquals("", nav.query());
        assertFalse(nav.textEntryActive());
    }

    @Test public void aLearnMoreLinkOpensItsTopicAndBackLeavesHelp() {
        HelpNavigation nav = new HelpNavigation();
        nav.openTopic(PaneWallPage.DISPLAY, "start");
        assertEquals(HelpNavigation.Screen.TOPIC, nav.screen());
        assertEquals("start", nav.id());
        assertEquals(PaneWallPage.DISPLAY, nav.place());
        assertFalse("Close returns to the source", nav.back());
        // The Home control is the way to help home from a linked topic.
        nav.openTopic(PaneWallPage.DISPLAY, "start");
        nav.home();
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        assertEquals(1, nav.depth());
    }

    @Test public void aFreshInvocationDoesNotReopenAnOldSearch() {
        HelpNavigation nav = opened();
        nav.search();
        nav.setQuery("paste");
        assertEquals("paste", nav.query());
        nav.open(PaneWallPage.TERMINAL);
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        assertEquals("", nav.query());
    }

    @Test public void queryAndScrollSurviveNavigationWithinOneInvocation() {
        HelpNavigation nav = opened();
        nav.search();
        nav.setQuery("split");
        nav.setScroll(240);
        nav.topic("panes");
        nav.setScroll(96);
        assertEquals(HelpNavigation.Screen.TOPIC, nav.screen());
        assertEquals(96, nav.frame().scroll);
        assertTrue(nav.back());
        assertEquals(HelpNavigation.Screen.SEARCH, nav.screen());
        assertEquals("split", nav.frame().query);
        assertEquals(240, nav.frame().scroll);
    }

    @Test public void theSearchDestinationIsNotStackedOnItself() {
        HelpNavigation nav = opened();
        nav.search();
        nav.search();
        nav.glossary();
        nav.glossary();
        assertEquals(3, nav.depth());
        nav.topic("dock");
        nav.topic("dock");
        assertEquals(4, nav.depth());
        nav.topic("az");
        assertEquals(5, nav.depth());
    }

    @Test public void backClosesADefinitionThenTextEntryThenOneScreen() {
        HelpNavigation nav = opened();
        nav.topic("base_values");
        nav.setTextEntryActive(true);
        nav.openTerm("base");
        assertEquals("base", nav.frame().openTermId);
        assertTrue(nav.back());
        assertNull(nav.frame().openTermId);
        assertTrue(nav.textEntryActive());
        assertTrue(nav.back());
        assertFalse(nav.textEntryActive());
        assertEquals(HelpNavigation.Screen.TOPIC, nav.screen());
        assertTrue(nav.back());
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        assertFalse("at help home Back closes help", nav.back());
    }

    @Test public void closingATermByHandIsTheSameAsBackingOutOfIt() {
        HelpNavigation nav = opened();
        nav.topic("appearance_editor");
        nav.openTerm("surface");
        nav.closeTerm();
        assertNull(nav.frame().openTermId);
        assertTrue(nav.back());
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
    }

    @Test public void exploringSitsOnTopOfHelpAndBackReturnsToIt() {
        HelpNavigation nav = opened();
        nav.explore("dock");
        assertEquals(HelpNavigation.Screen.EXPLORE, nav.screen());
        assertEquals("dock", nav.id());
        assertTrue(nav.back());
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        nav.topic("dock");
        nav.explore(null);
        assertNull(nav.id());
        assertTrue(nav.back());
        assertEquals(HelpNavigation.Screen.TOPIC, nav.screen());
        assertEquals("dock", nav.id());
    }

    @Test public void homeUnwindsRatherThanStackingAnotherHome() {
        HelpNavigation nav = opened();
        nav.search();
        nav.topic("dock");
        nav.glossary();
        assertEquals(4, nav.depth());
        nav.home();
        assertEquals(1, nav.depth());
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        nav.home();
        assertEquals(1, nav.depth());
    }

    @Test public void practiceComesBackToTheTopicItStartedFrom() {
        HelpNavigation nav = opened();
        nav.topic("dock");
        nav.setScroll(120);
        nav.practiceStart();
        assertTrue(nav.isPracticing());
        HelpNavigation.Frame restored = nav.practiceEnd();
        assertFalse(nav.isPracticing());
        assertNotNull(restored);
        assertEquals(HelpNavigation.Screen.TOPIC, restored.screen);
        assertEquals("dock", restored.id);
        assertEquals(120, restored.scroll);
        assertEquals(HelpNavigation.Screen.TOPIC, nav.screen());
    }

    @Test public void practiceRestoresItsTopicEvenWhenHelpWasClosedMeanwhile() {
        HelpNavigation nav = opened();
        nav.topic("keyboard");
        nav.practiceStart();
        // Help closed for the lesson, so the next invocation starts clean; End practice still knows.
        HelpNavigation after = new HelpNavigation();
        after.open(PaneWallPage.TERMINAL);
        assertEquals(HelpNavigation.Screen.HOME, after.screen());
        HelpNavigation.Frame restored = nav.practiceEnd();
        assertEquals("keyboard", restored.id);
        assertNull("nothing was remembered twice", nav.practiceEnd());
    }

    @Test public void practiceEndWithoutPracticeChangesNothing() {
        HelpNavigation nav = opened();
        assertNull(nav.practiceEnd());
        assertEquals(HelpNavigation.Screen.HOME, nav.screen());
        assertEquals(1, nav.depth());
    }

    @Test public void nothingHereSwitchesPlace() {
        HelpNavigation nav = opened();
        nav.search();
        nav.setQuery("display");
        nav.topic("start");
        nav.explore("start");
        assertEquals(PaneWallPage.TERMINAL, nav.place());
    }
}
