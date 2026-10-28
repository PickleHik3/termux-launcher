package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScopeChipTest {

    private static final Page HOME_1 = new Page(1, PageKind.HOME, "Home 1", null);
    private static final Page HOME_2 = new Page(2, PageKind.HOME, "Home 2", null);
    private static final Page TERMINAL = new Page(3, PageKind.TERMINAL, "Terminal", null);
    private static final Page DISPLAY = new Page(4, PageKind.DISPLAY, "Display", null);

    /** What each kind shows with nothing stored. */
    private static final ScopeChip.Shows DEFAULTS =
        (page, element) -> PlaceLayoutStore.kindShowsByDefault(page.kind, element);

    @Test
    public void statusBarShowsOnEveryPageByDefault() {
        List<Page> wall = Arrays.asList(HOME_1, TERMINAL);
        assertEquals(2, ScopeChip.pagesShowing(wall, SurfaceSlot.STATUS, DEFAULTS));
        assertTrue(ScopeChip.offers(wall, SurfaceSlot.STATUS, DEFAULTS));
    }

    @Test
    public void dockOnOneHomePageHasNoChip() {
        List<Page> wall = Arrays.asList(HOME_1, TERMINAL);
        assertEquals(1, ScopeChip.pagesShowing(wall, SurfaceSlot.DOCK, DEFAULTS));
        assertFalse(ScopeChip.offers(wall, SurfaceSlot.DOCK, DEFAULTS));
    }

    @Test
    public void dockOnTwoHomePagesHasTheChip() {
        List<Page> wall = Arrays.asList(HOME_1, HOME_2, TERMINAL);
        assertTrue(ScopeChip.offers(wall, SurfaceSlot.DOCK, DEFAULTS));
    }

    @Test
    public void extraKeysCountTerminalAndDisplay() {
        assertFalse(ScopeChip.offers(Arrays.asList(HOME_1, HOME_2, TERMINAL),
            SurfaceSlot.KEYBOARD, DEFAULTS));
        assertTrue(ScopeChip.offers(Arrays.asList(HOME_1, TERMINAL, DISPLAY),
            SurfaceSlot.KEYBOARD, DEFAULTS));
    }

    @Test
    public void canvasStandsOnTheTerminalPageAlone() {
        List<Page> wall = Arrays.asList(HOME_1, HOME_2, TERMINAL, DISPLAY);
        assertEquals(1, ScopeChip.pagesShowing(wall, SurfaceSlot.CANVAS, DEFAULTS));
        assertFalse(ScopeChip.offers(wall, SurfaceSlot.CANVAS, DEFAULTS));
    }

    @Test
    public void aPageThatHidesTheSurfaceDoesNotCount() {
        // Home 2 wears a layout of its own with the dock put away.
        ScopeChip.Shows shows = (page, element) -> page.id != 2
            && PlaceLayoutStore.kindShowsByDefault(page.kind, element);
        assertFalse(ScopeChip.offers(Arrays.asList(HOME_1, HOME_2, TERMINAL), SurfaceSlot.DOCK,
            shows));
        // The A-Z bar alone still puts the dock surface on a page.
        ScopeChip.Shows azOnly = (page, element) -> page.kind == PageKind.HOME
            && element == Element.AZ;
        assertTrue(ScopeChip.offers(Arrays.asList(HOME_1, HOME_2), SurfaceSlot.DOCK, azOnly));
    }

    @Test
    public void appearanceCardNeedsTwoPages() {
        assertFalse(ScopeChip.offers(Collections.singletonList(TERMINAL), null, DEFAULTS));
        assertTrue(ScopeChip.offers(Arrays.asList(HOME_1, TERMINAL), null, DEFAULTS));
    }
}
