package com.termux.app.chrome;

import static com.termux.app.chrome.TerlBackPolicy.Surface.*;
import static org.junit.Assert.*;

import org.junit.Test;

import java.util.EnumSet;

public class TerlBackPolicyTest {

    @Test public void nothingOpenHasNoTopmostAndBackIsSpentUnderTerl() {
        assertNull(TerlBackPolicy.topmost(EnumSet.noneOf(TerlBackPolicy.Surface.class)));
        assertTrue(TerlBackPolicy.spendsBackWhenNothingOpen(true));
        assertFalse(TerlBackPolicy.spendsBackWhenNothingOpen(false));
    }

    @Test public void aSingleOpenSurfaceIsTheTopmost() {
        for (TerlBackPolicy.Surface s : TerlBackPolicy.Surface.values())
            assertEquals(s, TerlBackPolicy.topmost(EnumSet.of(s)));
    }

    @Test public void everyCombinationClosesTheHighestOne() {
        TerlBackPolicy.Surface[] all = TerlBackPolicy.Surface.values();
        for (int mask = 1; mask < (1 << all.length); mask++) {
            EnumSet<TerlBackPolicy.Surface> open = EnumSet.noneOf(TerlBackPolicy.Surface.class);
            for (int i = 0; i < all.length; i++) if ((mask & (1 << i)) != 0) open.add(all[i]);
            TerlBackPolicy.Surface expected = null;
            for (TerlBackPolicy.Surface s : TerlBackPolicy.ORDER) if (open.contains(s)) { expected = s; break; }
            assertEquals(expected, TerlBackPolicy.topmost(open));
        }
    }

    @Test public void specificOrders() {
        assertEquals(SEARCH, TerlBackPolicy.topmost(EnumSet.of(SEARCH, OVERVIEW, FULL_SCREEN)));
        // The hint card stands over everything, the search included.
        assertEquals(HINT_CARD, TerlBackPolicy.topmost(EnumSet.of(SEARCH, HINT_CARD, NAVIGATION_MODE)));
        assertEquals(SHEET, TerlBackPolicy.topmost(EnumSet.of(SHEET, OVERVIEW)));
        assertEquals(OVERVIEW, TerlBackPolicy.topmost(EnumSet.of(OVERVIEW, FULL_SCREEN)));
        assertEquals(NAVIGATION_MODE, TerlBackPolicy.topmost(EnumSet.of(FULL_SCREEN, NAVIGATION_MODE)));
    }
}
