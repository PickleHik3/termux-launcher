package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.DressResolver;
import com.termux.app.page.PageKind;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayoutStore;

import org.junit.Test;

/**
 * The Assistant page (ADR 0022) is of the Workspace (ADR 0025), as terminal windows and Display
 * pages are: it wears the Workspace's layout, look and dress. What stays its own is its content:
 * its pane's insets, its body swipe, and no window chips on its bar.
 */
public class AssistantPlacePolicyTest {

    @Test
    public void terminalDisplayAndAssistantAreWorkspacePlaces() {
        assertTrue(PaneWallPolicy.isWorkspacePlace(PageKind.TERMINAL));
        assertTrue(PaneWallPolicy.isWorkspacePlace(PageKind.DISPLAY));
        assertTrue(PaneWallPolicy.isWorkspacePlace(PageKind.ASSISTANT));
        assertFalse(PaneWallPolicy.isWorkspacePlace(PageKind.HOME));
        assertFalse(PaneWallPolicy.isWorkspacePlace(null));
    }

    @Test
    public void onlyWidgetsIsAHomePlace() {
        assertTrue(PaneWallPolicy.isHomePlace(PageKind.HOME));
        assertFalse(PaneWallPolicy.isHomePlace(PageKind.ASSISTANT));
        assertFalse(PaneWallPolicy.isHomePlace(PageKind.TERMINAL));
    }

    @Test
    public void theAssistantPageWearsTheWorkspacesDress() {
        assertSame(PageKind.TERMINAL, DressResolver.dressKindOf(PageKind.ASSISTANT));
        assertSame(PageKind.TERMINAL, DressResolver.dressKindOf(PageKind.DISPLAY));
        for (Element element : Element.values()) {
            assertEquals(element.name(),
                PlaceLayoutStore.kindShowsByDefault(PageKind.TERMINAL, element),
                PlaceLayoutStore.kindShowsByDefault(PageKind.ASSISTANT, element));
        }
    }

    @Test
    public void aSlideBetweenTwoWorkspacePagesCrossesNoSeam() {
        assertFalse(com.termux.app.place.SeamTravel.crosses(PageKind.TERMINAL,
            PageKind.ASSISTANT));
        assertFalse(com.termux.app.place.SeamTravel.crosses(PageKind.DISPLAY,
            PageKind.ASSISTANT));
        assertTrue(com.termux.app.place.SeamTravel.crosses(PageKind.HOME,
            PageKind.ASSISTANT));
        assertSame(PageKind.TERMINAL,
            com.termux.app.place.SeamDress.screenOf(PageKind.ASSISTANT));
    }

    @Test
    public void theAssistantPageIsNoHomePage() {
        assertSame(PageRef.ASSISTANT, PageRef.of(PageKind.ASSISTANT));
        assertFalse(PageRef.ASSISTANT.isHome());
    }

    @Test
    public void theWindowChipsShowOnEveryPlace_theAssistantPageIncluded() {
        for (PageKind page : PageKind.values())
            assertTrue(page.name(), PaneWallPolicy.showsWindowChips(page));
    }

    @Test
    public void theAssistantPaneStandsTwelveInOnTheSidesAndEightAtTheEnds() {
        float density = 2.625f;
        assertEquals(32, PaneWallPolicy.pageInsetPx(PageKind.ASSISTANT, 48, 3, true, density));
        assertEquals(21, PaneWallPolicy.pageInsetPx(PageKind.ASSISTANT, 48, 3, false, density));
        // The others keep their rule, whatever the side.
        assertEquals(48, PaneWallPolicy.pageInsetPx(PageKind.TERMINAL, 48, 3, true, density));
        assertEquals(0, PaneWallPolicy.pageInsetPx(PageKind.HOME, 48, 3, false, density));
        assertEquals(45, PaneWallPolicy.pageInsetPx(PageKind.DISPLAY, 48, 3, true, density));
    }

    @Test
    public void aSidewaysSwipeOnTheChatPagesTheWallUnlessAScrollerKeepsIt() {
        assertTrue(PaneWallPolicy.bodySwipePages(PageKind.ASSISTANT, false, true, false));
        assertFalse(PaneWallPolicy.bodySwipePages(PageKind.ASSISTANT, true, false, false));
        assertTrue(PaneWallPolicy.bodySwipePages(PageKind.TERMINAL, true, false, false));
        assertFalse(PaneWallPolicy.bodySwipePages(PageKind.TERMINAL, false, true, false));
        assertFalse(PaneWallPolicy.bodySwipePages(PageKind.HOME, true, true, false));
    }

    @Test
    public void aDisplayPagePagesByAPlainSwipeOnlyWhileNoDisplayRuns() {
        // Off, the page is an empty state like any other page and a sideways swipe pages.
        assertTrue(PaneWallPolicy.bodySwipePages(PageKind.DISPLAY, false, false, false));
        // Running, the picture takes every touch and only the border pages; the others' answers
        // do not leak in.
        assertFalse(PaneWallPolicy.bodySwipePages(PageKind.DISPLAY, true, true, true));
        // The running display rules only its own page.
        assertTrue(PaneWallPolicy.bodySwipePages(PageKind.TERMINAL, true, false, true));
    }

    @Test
    public void theAssistantCardIsAStillCard() {
        assertFalse(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.ASSISTANT, 1));
        assertFalse(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.DISPLAY, 1));
        assertTrue(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.TERMINAL, 1));
        assertTrue(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.HOME_PAGE, 1));
    }
}
