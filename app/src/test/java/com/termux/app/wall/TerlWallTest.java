package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.look.WallPagesMigration;
import com.termux.app.page.PageKind;

import org.junit.Test;

/** v1's wall under Terl: Home's pages, and the AI page left of Home 1 while it is on. */
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

    @Test public void theAssistantPageIsLaidOutUnderTerlOnlyWhileTheAiPageIsOn() {
        assertTrue(TerlWall.laysOut(PageKind.ASSISTANT, true, true));
        assertFalse(TerlWall.laysOut(PageKind.ASSISTANT, true, false));
        assertTrue(TerlWall.laysOut(PageKind.HOME, true, true));
        // The AI page brings no Terminal or Display page back.
        assertFalse(TerlWall.laysOut(PageKind.TERMINAL, true, true));
        assertFalse(TerlWall.laysOut(PageKind.DISPLAY, true, true));
    }

    private static java.util.List<PageKind> kinds(WallPages pages) {
        java.util.List<PageKind> out = new java.util.ArrayList<>();
        for (WallPages.Page page : pages.pages()) out.add(page.kind);
        return out;
    }

    @Test public void placingTheAiPageAddsTheAssistantPageLeftOfHome1() {
        WallPages pages = WallPagesMigration.seed("home", 0, "home", 2);
        WallPages.Result placed = TerlWall.placeAiPage(pages);
        assertTrue(placed.ok());
        assertEquals(WallPages.ChangeType.ADDED, placed.change);
        assertEquals(PageKind.ASSISTANT, placed.pages.pages().get(0).kind);
        assertEquals(PageKind.HOME, placed.pages.pages().get(1).kind);
        assertEquals(pages.pages().size() + 1, placed.pages.pages().size());
        // Home stays home: the first page added is not the AI page.
        assertEquals(pages.homePages().get(0).id, placed.pages.homePages().get(0).id);
        // Placing it again changes nothing.
        assertEquals(WallPages.ChangeType.NONE, TerlWall.placeAiPage(placed.pages).change);
    }

    @Test public void anAssistantPageStandingElsewhereMovesLeftOfHome1() {
        WallPages pages = WallPagesMigration.seed("home", 0, "home", 2);
        WallPages withAssistant = pages.add(PageKind.ASSISTANT, WallPages.END, null).pages;
        assertEquals(PageKind.ASSISTANT, kinds(withAssistant).get(kinds(withAssistant).size() - 1));
        WallPages.Result placed = TerlWall.placeAiPage(withAssistant);
        assertEquals(WallPages.ChangeType.MOVED, placed.change);
        assertEquals(PageKind.ASSISTANT, placed.pages.pages().get(0).kind);
        assertEquals(withAssistant.assistant().id, placed.pages.assistant().id);
    }

    @Test public void aWallWithNoHomePageGetsNoAiPage() {
        assertEquals(WallPages.ChangeType.NONE, TerlWall.placeAiPage(WallPages.EMPTY).change);
    }
}
