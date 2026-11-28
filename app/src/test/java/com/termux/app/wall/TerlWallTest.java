package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;

import org.junit.Test;

/** v1's wall under Terl: Home's pages alone,. */
public class TerlWallTest {

    @Test public void underTerlOnlyHomePagesAreLaidOut() {
        assertTrue(TerlWall.laysOut(PageKind.HOME, true));
        assertFalse(TerlWall.laysOut(PageKind.TERMINAL, true));
        assertFalse(TerlWall.laysOut(PageKind.DISPLAY, true));
        assertFalse(TerlWall.laysOut(PageKind.ASSISTANT, true));
    }

    @Test public void underTerlTheGlyphRowIsTerlsOwnAndOffByDefault() {
        com.termux.app.launcher.home.HomePageIndicator glyphs = com.termux.app.launcher.home.HomePageIndicator.GLYPHS;
        com.termux.app.launcher.home.HomePageIndicator off = com.termux.app.launcher.home.HomePageIndicator.OFF;
        assertEquals(off, TerlWall.indicator(glyphs, true, false));
        assertEquals(glyphs, TerlWall.indicator(off, true, true));
        assertEquals("v1's choice with the switch off", glyphs, TerlWall.indicator(glyphs, false, false));
    }

    @Test public void withTheSwitchOffEveryPageIsLaidOut() {
        for (PageKind kind : PageKind.values()) assertTrue(kind.name(), TerlWall.laysOut(kind, false));
    }
}
