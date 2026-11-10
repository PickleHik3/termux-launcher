package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.DressResolver;
import com.termux.app.place.Element;
import com.termux.app.wall.WallPages.PageKind;
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
        assertTrue(PaneWallPolicy.isWorkspacePlace(PaneWallPage.TERMINAL));
        assertTrue(PaneWallPolicy.isWorkspacePlace(PaneWallPage.DISPLAY));
        assertTrue(PaneWallPolicy.isWorkspacePlace(PaneWallPage.ASSISTANT));
        assertFalse(PaneWallPolicy.isWorkspacePlace(PaneWallPage.WIDGETS));
        assertFalse(PaneWallPolicy.isWorkspacePlace(null));
    }

    @Test
    public void onlyWidgetsIsAHomePlace() {
        assertTrue(PaneWallPolicy.isHomePlace(PaneWallPage.WIDGETS));
        assertFalse(PaneWallPolicy.isHomePlace(PaneWallPage.ASSISTANT));
        assertFalse(PaneWallPolicy.isHomePlace(PaneWallPage.TERMINAL));
    }

    @Test
    public void theAssistantPageWearsTheWorkspacesDress() {
        assertSame(PageKind.TERMINAL, DressResolver.kindOf(PaneWallPage.ASSISTANT));
        assertSame(PageKind.TERMINAL, DressResolver.kindOf(PaneWallPage.DISPLAY));
        for (Element element : Element.values()) {
            assertEquals(element.name(),
                PlaceLayoutStore.kindShowsByDefault(PageKind.TERMINAL, element),
                PlaceLayoutStore.kindShowsByDefault(PageKind.ASSISTANT, element));
        }
    }

    @Test
    public void aSlideBetweenTwoWorkspacePagesCrossesNoSeam() {
        assertFalse(com.termux.app.place.SeamTravel.crosses(PaneWallPage.TERMINAL,
            PaneWallPage.ASSISTANT));
        assertFalse(com.termux.app.place.SeamTravel.crosses(PaneWallPage.DISPLAY,
            PaneWallPage.ASSISTANT));
        assertTrue(com.termux.app.place.SeamTravel.crosses(PaneWallPage.WIDGETS,
            PaneWallPage.ASSISTANT));
        assertSame(PaneWallPage.TERMINAL,
            com.termux.app.place.SeamDress.screenOf(PaneWallPage.ASSISTANT));
    }

    @Test
    public void theAssistantPageIsNoHomePage() {
        assertSame(PageRef.ASSISTANT, PageRef.of(PaneWallPage.ASSISTANT));
        assertFalse(PageRef.ASSISTANT.isHome());
    }

    @Test
    public void theWindowChipsStayOffTheAssistantPage() {
        assertFalse(PaneWallPolicy.showsWindowChips(PaneWallPage.ASSISTANT));
        assertTrue(PaneWallPolicy.showsWindowChips(PaneWallPage.TERMINAL));
        assertTrue(PaneWallPolicy.showsWindowChips(PaneWallPage.WIDGETS));
        assertTrue(PaneWallPolicy.showsWindowChips(PaneWallPage.DISPLAY));
    }

    @Test
    public void theAssistantPaneStandsTwelveInOnTheSidesAndEightAtTheEnds() {
        float density = 2.625f;
        assertEquals(32, PaneWallPolicy.pageInsetPx(PaneWallPage.ASSISTANT, 48, 3, true, density));
        assertEquals(21, PaneWallPolicy.pageInsetPx(PaneWallPage.ASSISTANT, 48, 3, false, density));
        // The others keep their rule, whatever the side.
        assertEquals(48, PaneWallPolicy.pageInsetPx(PaneWallPage.TERMINAL, 48, 3, true, density));
        assertEquals(0, PaneWallPolicy.pageInsetPx(PaneWallPage.WIDGETS, 48, 3, false, density));
        assertEquals(45, PaneWallPolicy.pageInsetPx(PaneWallPage.DISPLAY, 48, 3, true, density));
    }

    @Test
    public void aSidewaysSwipeOnTheChatPagesTheWallUnlessAScrollerKeepsIt() {
        assertTrue(PaneWallPolicy.bodySwipePages(PaneWallPage.ASSISTANT, false, true, false));
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.ASSISTANT, true, false, false));
        assertTrue(PaneWallPolicy.bodySwipePages(PaneWallPage.TERMINAL, true, false, false));
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.TERMINAL, false, true, false));
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.WIDGETS, true, true, false));
    }

    @Test
    public void aDisplayPagePagesByAPlainSwipeOnlyWhileNoDisplayRuns() {
        // Off, the page is an empty state like any other page and a sideways swipe pages.
        assertTrue(PaneWallPolicy.bodySwipePages(PaneWallPage.DISPLAY, false, false, false));
        // Running, the picture takes every touch and only the border pages; the others' answers
        // do not leak in.
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.DISPLAY, true, true, true));
        // The running display rules only its own page.
        assertTrue(PaneWallPolicy.bodySwipePages(PaneWallPage.TERMINAL, true, false, true));
    }

    @Test
    public void theAssistantCardIsAStillCard() {
        assertFalse(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.ASSISTANT));
        assertFalse(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.DISPLAY));
        assertTrue(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.TERMINAL));
        assertTrue(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.HOME_PAGE));
    }
}
