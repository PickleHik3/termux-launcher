package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.controlbar.ControlBarToolOrder.Place;
import com.termux.app.tiling.WindowKind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Which tools each place holds, and in what order (#452). */
public class ControlBarToolOrderTest {

    @Test
    public void nothingStoredLeavesTheBarsOwnLists() {
        assertEquals(Arrays.asList(ControlBarTool.KEYBOARD, ControlBarTool.SPLIT,
            ControlBarTool.NEW_TERMINAL, ControlBarTool.WINDOW_MENU),
            ControlBarToolOrder.resolve(Place.TERMINAL, null, false));
        assertEquals(Arrays.asList(ControlBarTool.MOUSE_MODE, ControlBarTool.KEYBOARD,
            ControlBarTool.WINDOW_MENU), ControlBarToolOrder.resolve(Place.LINUX_APP, "", false));
        assertEquals(Arrays.asList(ControlBarTool.DRAWER, ControlBarTool.PINNED_APPS),
            ControlBarToolOrder.resolve(Place.HOME, null, true));
        assertEquals(Collections.singletonList(ControlBarTool.PINNED_APPS),
            ControlBarToolOrder.resolve(Place.HOME, null, false));
    }

    @Test
    public void storedToolsStandInTheirOrderAndAToolLeftOutIsOff() {
        assertEquals(Arrays.asList(ControlBarTool.NEW_TERMINAL, ControlBarTool.KEYBOARD),
            ControlBarToolOrder.resolve(Place.TERMINAL, "new_terminal,keyboard", false));
    }

    @Test
    public void unknownRepeatedAndForeignNamesAreIgnored() {
        assertEquals(Arrays.asList(ControlBarTool.KEYBOARD, ControlBarTool.SPLIT),
            ControlBarToolOrder.resolve(Place.TERMINAL,
                "keyboard,bogus,keyboard,mouse_mode, split", false));
    }

    @Test
    public void noToolAtAllIsStoredAsADashAndStaysEmpty() {
        assertEquals("-", ControlBarToolOrder.format(Collections.<ControlBarTool>emptyList()));
        assertTrue(ControlBarToolOrder.resolve(Place.TERMINAL, "-", false).isEmpty());
        assertEquals("-", ControlBarToolOrder.normalise(Place.TERMINAL, ""));
    }

    @Test
    public void homeHoldsTheDrawerOnlyWhileItsSettingIsOn() {
        assertEquals(Arrays.asList(ControlBarTool.PINNED_APPS, ControlBarTool.DRAWER),
            ControlBarToolOrder.resolve(Place.HOME, "pinned_apps,drawer", true));
        assertEquals(Collections.singletonList(ControlBarTool.PINNED_APPS),
            ControlBarToolOrder.resolve(Place.HOME, "pinned_apps,drawer", false));
        // Switched on but missing from the stored order: it leads.
        assertEquals(Arrays.asList(ControlBarTool.DRAWER, ControlBarTool.PINNED_APPS),
            ControlBarToolOrder.resolve(Place.HOME, "pinned_apps", true));
    }

    @Test
    public void thePinnedAppsCanBeLeftOutOfHome() {
        assertEquals(Collections.singletonList(ControlBarTool.DRAWER),
            ControlBarToolOrder.resolve(Place.HOME, "drawer", true));
    }

    @Test
    public void formatAndNormaliseRoundTrip() {
        String text = ControlBarToolOrder.format(Arrays.asList(ControlBarTool.SPLIT,
            ControlBarTool.KEYBOARD));
        assertEquals("split,keyboard", text);
        assertEquals(text, ControlBarToolOrder.normalise(Place.TERMINAL, text));
        assertEquals("keyboard", ControlBarToolOrder.normalise(Place.TERMINAL, "keyboard,mouse_mode"));
    }

    @Test
    public void everyPlaceOffersToolsItsRowsCanHold() {
        assertTrue(ControlBarToolOrder.options(Place.HOME).contains(ControlBarTool.DRAWER));
        assertTrue(ControlBarToolOrder.options(Place.HOME).contains(ControlBarTool.PINNED_APPS));
        assertFalse(ControlBarToolOrder.options(Place.TERMINAL).contains(ControlBarTool.MOUSE_MODE));
        assertTrue(ControlBarToolOrder.options(Place.LINUX_APP).contains(ControlBarTool.MOUSE_MODE));
        assertNull(Place.ofId("bogus"));
        assertEquals(Place.LINUX_APP, Place.ofId("linux"));
    }

    @Test
    public void rowsReadTheStoredToolsForThePlaceInFront() {
        ControlBarRows.StoredTools stored = new ControlBarRows.StoredTools(
            "pinned_apps", "split,keyboard", "window_menu");
        ControlBarFront shell = ControlBarFront.workspace(1, WindowKind.SHELL,
            Collections.singletonList(new ControlBarFront.Window(5, WindowKind.SHELL, null)), 5);
        assertEquals(Arrays.asList(ControlBarTool.SPLIT, ControlBarTool.KEYBOARD),
            ControlBarRows.of(shell, true, stored).tools);
        ControlBarFront app = ControlBarFront.workspace(1, WindowKind.LINUX_APP,
            Collections.singletonList(new ControlBarFront.Window(6, WindowKind.LINUX_APP, null)), 6);
        assertEquals(Collections.singletonList(ControlBarTool.WINDOW_MENU),
            ControlBarRows.of(app, true, stored).tools);
        assertEquals(Arrays.asList(ControlBarTool.DRAWER, ControlBarTool.PINNED_APPS),
            ControlBarRows.of(ControlBarFront.home(1, 0), true, stored).tools);
    }

    @Test
    public void marginZeroStandsTheBarFlushAndTheRangeHoldsAround() {
        assertTrue(ControlBarPlacement.flush(0));
        assertFalse(ControlBarPlacement.flush(6));
        assertEquals(24, ControlBarPlacement.clampMarginDp(99));
        assertEquals(0, ControlBarPlacement.clampMarginDp(-4));
    }
}
