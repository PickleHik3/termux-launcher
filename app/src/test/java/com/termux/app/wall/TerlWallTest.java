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

    @Test public void underTerlTheAiPageIsLaidOutFirstWithoutTouchingTheWallsPages() {
        WallPages pages = WallPagesMigration.seed("home", 0, "home", 2);
        java.util.List<Integer> before = new java.util.ArrayList<>();
        for (WallPages.Page page : pages.pages()) before.add(page.id);
        java.util.List<Integer> ids = TerlWall.laidOutIds(pages, true, true);
        // No Assistant page on the wall: the AI page goes by the wall's kind id for it.
        assertEquals(Integer.valueOf(PaneWallLayout.kindPageId(PageKind.ASSISTANT)), ids.get(0));
        java.util.List<Integer> homes = new java.util.ArrayList<>();
        for (WallPages.Page page : pages.homePages()) homes.add(page.id);
        assertEquals(homes, ids.subList(1, ids.size()));
        java.util.List<Integer> after = new java.util.ArrayList<>();
        for (WallPages.Page page : pages.pages()) after.add(page.id);
        assertEquals("the wall's pages are not changed", before, after);
        assertFalse(TerlWall.laidOutIds(pages, true, false).contains(PaneWallLayout.kindPageId(PageKind.ASSISTANT)));
    }

    @Test public void v1sAssistantPageKeepsItsPlaceInV1AndIsTheAiPageUnderTerl() {
        WallPages pages = WallPagesMigration.seed("home", 0, "home", 2);
        WallPages withAssistant = pages.add(PageKind.ASSISTANT, WallPages.END, null).pages;
        int assistant = withAssistant.assistant().id;
        java.util.List<Integer> v1 = new java.util.ArrayList<>();
        for (WallPages.Page page : withAssistant.pages()) v1.add(page.id);
        // Switch off: every page where the user put it, the Assistant page last here.
        assertEquals(v1, TerlWall.laidOutIds(withAssistant, false, true));
        assertEquals(Integer.valueOf(assistant), v1.get(v1.size() - 1));
        // Under Terl the same page stands first, by its own id, with its chats.
        assertEquals(Integer.valueOf(assistant), TerlWall.laidOutIds(withAssistant, true, true).get(0));
        assertEquals(assistant, TerlWall.aiPageId(withAssistant));
    }

    @Test public void terlWallNoLongerEditsTheWallsPages() {
        for (java.lang.reflect.Method method : TerlWall.class.getDeclaredMethods()) {
            assertFalse(method.getName() + " answers an edit of the wall's pages",
                method.getReturnType() == WallPages.Result.class);
        }
    }
}
