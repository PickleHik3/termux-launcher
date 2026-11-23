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
}
