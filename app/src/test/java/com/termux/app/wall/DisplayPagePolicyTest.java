package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Display pages as wall slots (ADR 0015, A3b), the pure layer: the slots keep Display pages when
 * asked, the one display view binds to one Display slot by the Home frame's rule, the built
 * window holds only that slot for {@link PaneWallPage#DISPLAY}, and each Display page shows live,
 * its picture, or its card.
 */
public class DisplayPagePolicyTest {

    /** [Home 1][Home 2][Terminal 3][Display 4]. */
    private static WallPages oneDisplay() {
        return WallPagesMigration.seed("home", 0, "home", 2, true);
    }

    /**
     * [Home 1][Home 2][Display 5 = firefox][Terminal 3][Display 4]: two Display pages, a Terminal
     * page between them.
     */
    private static WallPages twoDisplays() {
        WallPages.Result r = oneDisplay().add(PageKind.DISPLAY, 2, "firefox");
        assertTrue(r.ok());
        return r.pages;
    }

    /** [Home 1][Display 4][Home 2][Terminal 3]: a Display page between two Home runs. */
    private static WallPages displayBetweenHomes() {
        WallPages.Result r = oneDisplay().move(4, 1);
        assertTrue(r.ok());
        return r.pages;
    }

    // ---- slots ----------------------------------------------------------------------------

    // ---- the bound Display slot -------------------------------------------------------------

    // ---- the built window ---------------------------------------------------------------------

    @Test
    public void twoDisplayPagesMayStandSideBySide() {
        // Added right after the Display page, the second stands beside it.
        WallPages.Result beside = oneDisplay().add(PageKind.DISPLAY, 4, "firefox");
        assertTrue(beside.ok());
        List<WallPages.Page> v = beside.pages.pages();
        assertEquals(PageKind.DISPLAY, v.get(v.indexOf(beside.page) - 1).kind);
    }

    // ---- what a Display page shows ------------------------------------------------------------

    @Test
    public void onlyTheBoundPageIsLiveAndOnlyWhileRunning() {
        assertEquals(PageStillPolicy.Shows.LIVE, PageStillPolicy.shows(4, 4, true, false));
        assertEquals(PageStillPolicy.Shows.CARD, PageStillPolicy.shows(4, 4, false, false));
        assertEquals(PageStillPolicy.Shows.PICTURE, PageStillPolicy.shows(4, 4, false, true));
        assertEquals(PageStillPolicy.Shows.PICTURE, PageStillPolicy.shows(5, 4, true, true));
        assertEquals(PageStillPolicy.Shows.CARD, PageStillPolicy.shows(5, 4, true, false));
        assertEquals(PageStillPolicy.Shows.CARD, PageStillPolicy.shows(5, -1, true, false));
    }

    @Test
    public void theCardNamesTheBoundAppOrDisplay() {
        WallPages pages = twoDisplays();
        assertEquals("Display", DisplayPagePolicy.cardName(pages.page(4), "Firefox"));
        assertEquals("Firefox", DisplayPagePolicy.cardName(pages.page(5), "Firefox"));
        assertEquals("Display", DisplayPagePolicy.cardName(pages.page(5), null));
        assertEquals("Display", DisplayPagePolicy.cardName(pages.page(5), " "));
    }

    @Test
    public void landingOnADisplayPageStartsTheDisplayOnce() {
        assertTrue(DisplayPagePolicy.startsOnLanding(PageKind.DISPLAY, false, false));
        assertFalse(DisplayPagePolicy.startsOnLanding(PageKind.DISPLAY, true, false));
        assertFalse(DisplayPagePolicy.startsOnLanding(PageKind.DISPLAY, false, true));
        assertFalse(DisplayPagePolicy.startsOnLanding(PageKind.TERMINAL, false, false));
        assertFalse(DisplayPagePolicy.startsOnLanding(PageKind.HOME, false, false));
    }

    @Test
    public void homeDisplayPageIsHomeWhenHomeIsADisplayPage() {
        assertEquals(-1, DisplayPagePolicy.homeDisplayPage(oneDisplay()));
        assertEquals(-1, DisplayPagePolicy.homeDisplayPage(null));
        WallPages.Result r = twoDisplays().setHome(5);
        assertTrue(r.ok());
        assertEquals(5, DisplayPagePolicy.homeDisplayPage(r.pages));
        assertEquals(4, DisplayPagePolicy.homeDisplayPage(
            WallPagesMigration.seed("display", 0, "home", 2)));
    }

    // ---- pictures by page -----------------------------------------------------------------

    @Test
    public void picturesAreKeptPerPage() {
        PageStillPolicy.Pictures<String> pictures = new PageStillPolicy.Pictures<>();
        assertNull(pictures.put(4, "a"));
        assertNull(pictures.put(5, "b"));
        assertEquals("a", pictures.get(4));
        assertEquals("a", pictures.put(4, "c"));
        assertNull(pictures.put(4, "c"));
        assertEquals("c", pictures.put(4, null));
        assertFalse(pictures.has(4));
        assertTrue(pictures.has(5));
    }

    @Test
    public void picturesOfPagesGoneAreHandedBack() {
        PageStillPolicy.Pictures<String> pictures = new PageStillPolicy.Pictures<>();
        pictures.put(4, "a");
        pictures.put(5, "b");
        assertEquals(Collections.singletonList("b"),
            pictures.retain(Collections.singletonList(4)));
        assertEquals(1, pictures.size());
        assertEquals(Collections.singletonList("a"), pictures.clear());
        assertEquals(0, pictures.size());
    }

    @Test
    public void picturesHandBackTheSameObjectNever() {
        PageStillPolicy.Pictures<String> pictures = new PageStillPolicy.Pictures<>();
        String a = "a";
        pictures.put(4, a);
        assertSame(a, pictures.get(4));
        assertNull(pictures.put(4, a));
    }
}
