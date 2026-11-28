package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.home.HomePageIndicator;
import com.termux.app.page.PageKind;

import org.junit.Test;

/** v1's wall under Terl: Home's pages alone, and no glyph row under the Home grid. */
public class TerlWallTest {

    @Test public void underTerlOnlyHomePagesAreLaidOut() {
        assertTrue(TerlWall.laysOut(PageKind.HOME, true));
        assertFalse(TerlWall.laysOut(PageKind.TERMINAL, true));
        assertFalse(TerlWall.laysOut(PageKind.DISPLAY, true));
        assertFalse(TerlWall.laysOut(PageKind.ASSISTANT, true));
    }

    @Test public void withTheSwitchOffEveryPageIsLaidOut() {
        for (PageKind kind : PageKind.values()) assertTrue(kind.name(), TerlWall.laysOut(kind, false));
    }

    @Test public void theGlyphRowIsOffUnderTerlAndAsChosenOtherwise() {
        assertEquals(HomePageIndicator.OFF, TerlWall.indicator(HomePageIndicator.GLYPHS, true));
        assertEquals(HomePageIndicator.GLYPHS, TerlWall.indicator(HomePageIndicator.GLYPHS, false));
        assertEquals(HomePageIndicator.OFF, TerlWall.indicator(HomePageIndicator.OFF, false));
    }
}
