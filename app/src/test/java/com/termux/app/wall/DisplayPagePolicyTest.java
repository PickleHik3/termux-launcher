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

    @Test
    public void wallSlotsLeaveDisplayPagesOutUnlessAsked() {
        WallPages pages = oneDisplay();
        assertEquals(2, PaneWallPolicy.wallSlots(pages, true).size());
        assertEquals(2, PaneWallPolicy.wallSlots(pages, true, false).size());
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(pages, true, true);
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL,
            PaneWallPage.DISPLAY), PaneWallPolicy.kindsOf(slots));
        assertEquals(Collections.singletonList(4), slots.get(2).pageIds);
    }

    @Test
    public void displayPagesAreOneSlotEach() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(twoDisplays(), true, true);
        assertEquals(4, slots.size());
        assertEquals(5, slots.get(1).id());
        assertEquals(4, slots.get(3).id());
    }

    @Test
    public void displaySlotsStayWithoutAWindow() {
        // The Terminal slot goes with the row's last window; the Display slots do not.
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(twoDisplays(), false, true);
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.DISPLAY,
            PaneWallPage.DISPLAY), PaneWallPolicy.kindsOf(slots));
    }

    // ---- the bound Display slot -------------------------------------------------------------

    @Test
    public void boundDisplaySlotIsTheCurrentDisplayPage() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(twoDisplays(), true, true);
        assertEquals(1, PaneWallPolicy.boundDisplaySlot(slots, 1, 3));
        assertEquals(3, PaneWallPolicy.boundDisplaySlot(slots, 3, 1));
    }

    @Test
    public void boundDisplaySlotFromTheTerminalIsTheOneNextDoor() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(twoDisplays(), true, true);
        // From the Terminal, a Display page on each side: the left one, unless the right is bound.
        assertEquals(1, PaneWallPolicy.boundDisplaySlot(slots, 2, -1));
        assertEquals(3, PaneWallPolicy.boundDisplaySlot(slots, 2, 3));
        // From Home, the Display page next door takes the view, whichever was bound.
        assertEquals(1, PaneWallPolicy.boundDisplaySlot(slots, 0, 3));
        assertEquals(1, PaneWallPolicy.boundDisplaySlot(slots, 0, -1));
    }

    @Test
    public void boundDisplaySlotKeepsTheBoundNeighbourOnEitherSide() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(displayBetweenHomes(), true, true);
        // [Home 1][Display 4][Home 2][Terminal 3]
        assertEquals(1, PaneWallPolicy.boundDisplaySlot(slots, 0, -1));
        assertEquals(1, PaneWallPolicy.boundDisplaySlot(slots, 2, -1));
        assertEquals(1, PaneWallPolicy.boundDisplaySlot(slots, 3, -1));
    }

    @Test
    public void noDisplaySlotBindsNothing() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(oneDisplay(), true);
        assertEquals(-1, PaneWallPolicy.boundDisplaySlot(slots, 1, -1));
    }

    @Test
    public void boundHomeSlotReadsAsBefore() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(displayBetweenHomes(), true, true);
        assertEquals(0, PaneWallPolicy.boundHomeSlot(slots, 0, 2));
        assertEquals(2, PaneWallPolicy.boundHomeSlot(slots, 1, 2));
        assertEquals(0, PaneWallPolicy.boundHomeSlot(slots, 1, -1));
        assertEquals(2, PaneWallPolicy.boundHomeSlot(slots, 3, 0));
    }

    // ---- the built window ---------------------------------------------------------------------

    @Test
    public void builtWindowHasTheDisplayPageBesideTheTerminal() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(oneDisplay(), true, true);
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL,
            PaneWallPage.DISPLAY), PaneWallPolicy.builtWindow(slots, 1, 0, 2));
        assertEquals(Arrays.asList(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            PaneWallPolicy.builtWindow(slots, 2, 0, 2));
    }

    @Test
    public void builtWindowHoldsOnlyTheBoundDisplaySlot() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(displayBetweenHomes(), true, true);
        // [Home 1][Display 4][Home 2][Terminal 3], on Display 4: both Home runs are its
        // neighbours, and only the bound one stands.
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.DISPLAY),
            PaneWallPolicy.builtWindow(slots, 1, 0, 1));
        assertEquals(Arrays.asList(PaneWallPage.DISPLAY, PaneWallPage.WIDGETS),
            PaneWallPolicy.builtWindow(slots, 1, 2, 1));
        // On Home 1 with the view bound elsewhere, the Display page is left out.
        assertEquals(Collections.singletonList(PaneWallPage.WIDGETS),
            PaneWallPolicy.builtWindow(slots, 0, 0, -1));
    }

    @Test
    public void twoDisplayPagesMayStandSideBySide() {
        // Added right after the Display page, the second stands beside it.
        WallPages.Result beside = oneDisplay().add(PageKind.DISPLAY, 4, "firefox");
        assertTrue(beside.ok());
        assertTrue(beside.pages.hasAdjacentDisplays());
    }

    @Test
    public void onlyTheBoundDisplayPageStandsBesideTheTerminal() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(twoDisplays(), true, true);
        // On the Terminal between them, only the bound Display page stands beside it.
        assertEquals(Arrays.asList(PaneWallPage.DISPLAY, PaneWallPage.TERMINAL),
            PaneWallPolicy.builtWindow(slots, 2, 0, 1));
    }

    @Test
    public void threeArgBuiltWindowIsUnchangedWithoutDisplaySlots() {
        List<WallPages.Slot> slots = PaneWallPolicy.wallSlots(oneDisplay(), true);
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL),
            PaneWallPolicy.builtWindow(slots, 1, 0));
    }

    // ---- what a Display page shows ------------------------------------------------------------

    @Test
    public void onlyTheBoundPageIsLiveAndOnlyWhileRunning() {
        assertEquals(DisplayPagePolicy.Shows.LIVE, DisplayPagePolicy.shows(4, 4, true, false));
        assertEquals(DisplayPagePolicy.Shows.CARD, DisplayPagePolicy.shows(4, 4, false, false));
        assertEquals(DisplayPagePolicy.Shows.PICTURE, DisplayPagePolicy.shows(4, 4, false, true));
        assertEquals(DisplayPagePolicy.Shows.PICTURE, DisplayPagePolicy.shows(5, 4, true, true));
        assertEquals(DisplayPagePolicy.Shows.CARD, DisplayPagePolicy.shows(5, 4, true, false));
        assertEquals(DisplayPagePolicy.Shows.CARD, DisplayPagePolicy.shows(5, -1, true, false));
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
        DisplayPagePolicy.Pictures<String> pictures = new DisplayPagePolicy.Pictures<>();
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
        DisplayPagePolicy.Pictures<String> pictures = new DisplayPagePolicy.Pictures<>();
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
        DisplayPagePolicy.Pictures<String> pictures = new DisplayPagePolicy.Pictures<>();
        String a = "a";
        pictures.put(4, a);
        assertSame(a, pictures.get(4));
        assertNull(pictures.put(4, a));
    }
}
