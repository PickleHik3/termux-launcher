package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxWindowTabsTest {

    @Test
    public void aNewWindowJoinsTheEndAndIsShown() {
        LinuxWindowTabs tabs = new LinuxWindowTabs();
        assertEquals(LinuxWindowTabs.NONE, tabs.shown());
        assertTrue(tabs.add(7));
        assertTrue(tabs.add(9));
        assertEquals(9, tabs.shown());
        assertEquals(7, tabs.idAt(0));
        assertEquals(9, tabs.idAt(1));
        assertFalse(tabs.add(7));
        assertFalse(tabs.add(LinuxWindowTabs.NONE));
        assertEquals(2, tabs.size());
    }

    @Test
    public void closingTheShownWindowShowsTheNextElseThePrevious() {
        LinuxWindowTabs tabs = new LinuxWindowTabs();
        tabs.add(1);
        tabs.add(2);
        tabs.add(3);
        tabs.show(2);
        assertTrue(tabs.remove(2));
        assertEquals(3, tabs.shown());
        assertTrue(tabs.remove(3));
        assertEquals(1, tabs.shown());
        assertTrue(tabs.remove(1));
        assertEquals(LinuxWindowTabs.NONE, tabs.shown());
        assertEquals(0, tabs.size());
    }

    @Test
    public void closingAHiddenWindowKeepsTheShownOne() {
        LinuxWindowTabs tabs = new LinuxWindowTabs();
        tabs.add(1);
        tabs.add(2);
        assertTrue(tabs.remove(1));
        assertEquals(2, tabs.shown());
        assertFalse(tabs.remove(5));
    }

    @Test
    public void showTakesOnlyAnotherTab() {
        LinuxWindowTabs tabs = new LinuxWindowTabs();
        tabs.add(1);
        tabs.add(2);
        assertTrue(tabs.show(1));
        assertFalse(tabs.show(1));
        assertFalse(tabs.show(8));
        assertEquals(1, tabs.shown());
        tabs.clear();
        assertEquals(LinuxWindowTabs.NONE, tabs.shown());
        assertFalse(tabs.contains(1));
    }

    @Test
    public void tabsShareTheRowButNeverGoUnderTheMinimum() {
        assertEquals(400, LinuxWindowTabs.tabWidth(400, 1, 100, 10, 0));
        assertEquals(195, LinuxWindowTabs.tabWidth(400, 2, 100, 10, 0));
        assertEquals(175, LinuxWindowTabs.tabWidth(400, 2, 100, 10, 20));
        assertEquals(100, LinuxWindowTabs.tabWidth(400, 4, 100, 10, 20));
        assertEquals(100, LinuxWindowTabs.tabWidth(400, 0, 100, 10, 20));
    }

    @Test
    public void scrollShowsTheWholeTabWithItsEndAndPadding() {
        assertEquals(0, LinuxWindowTabs.scrollTarget(0, 400, 100, 300, 0));
        assertEquals(150, LinuxWindowTabs.scrollTarget(0, 400, 350, 550, 0));
        assertEquals(60, LinuxWindowTabs.scrollTarget(200, 400, 60, 260, 0));
        assertEquals(200, LinuxWindowTabs.scrollTarget(500, 400, 200, 700, 0));
        // The last tab ends at 550 and the row pads 20 beyond it.
        assertEquals(170, LinuxWindowTabs.scrollTarget(0, 400, 350, 550, 20));
        // The first tab starts at its row's padding: scrolled back to 0, not negative.
        assertEquals(0, LinuxWindowTabs.scrollTarget(30, 400, 20, 220, 20));
    }

    @Test
    public void labelIsTheTitleElseTheAppIdElseTheFallback() {
        assertEquals("~/src", LinuxWindowTabs.label("~/src", "kitty", "Window"));
        assertEquals("kitty", LinuxWindowTabs.label("~", "kitty", "Window"));
        assertEquals("kitty", LinuxWindowTabs.label("", "kitty", "Window"));
        assertEquals("kitty", LinuxWindowTabs.label(null, " kitty ", "Window"));
        assertEquals("~", LinuxWindowTabs.label("~", "", "Window"));
        assertEquals("Window", LinuxWindowTabs.label("  ", null, "Window"));
    }
}
