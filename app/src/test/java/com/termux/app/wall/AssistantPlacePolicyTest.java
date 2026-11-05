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
 * The Assistant page (ADR 0022) is neither Home nor the Workspace: every place decision that
 * used to read "not Home" as "a window" asks these instead.
 */
public class AssistantPlacePolicyTest {

    @Test
    public void onlyTerminalAndDisplayAreWorkspacePlaces() {
        assertTrue(PaneWallPolicy.isWorkspacePlace(PaneWallPage.TERMINAL));
        assertTrue(PaneWallPolicy.isWorkspacePlace(PaneWallPage.DISPLAY));
        assertFalse(PaneWallPolicy.isWorkspacePlace(PaneWallPage.ASSISTANT));
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
    public void theAssistantKindWearsTheStatusBarAlone() {
        assertSame(PageKind.ASSISTANT, DressResolver.kindOf(PaneWallPage.ASSISTANT));
        assertTrue(PlaceLayoutStore.kindShowsByDefault(PageKind.ASSISTANT, Element.STATUS));
        assertFalse(PlaceLayoutStore.kindShowsByDefault(PageKind.ASSISTANT, Element.EXTRA_KEYS));
        assertFalse(PlaceLayoutStore.kindShowsByDefault(PageKind.ASSISTANT, Element.APPS));
        assertFalse(PlaceLayoutStore.kindShowsByDefault(PageKind.ASSISTANT, Element.AZ));
    }

    @Test
    public void theAssistantArrangementIsItsOwnNotTheTerminals() {
        assertSame(PageRef.ASSISTANT, PageRef.of(PaneWallPage.ASSISTANT));
        assertFalse(PageRef.ASSISTANT.equals(PageRef.TERMINAL));
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
        assertTrue(PaneWallPolicy.bodySwipePages(PaneWallPage.ASSISTANT, false, true));
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.ASSISTANT, true, false));
        assertTrue(PaneWallPolicy.bodySwipePages(PaneWallPage.TERMINAL, true, false));
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.TERMINAL, false, true));
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.DISPLAY, true, true));
        assertFalse(PaneWallPolicy.bodySwipePages(PaneWallPage.WIDGETS, true, true));
    }

    @Test
    public void theAssistantCardIsAStillCard() {
        assertFalse(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.ASSISTANT));
        assertFalse(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.DISPLAY));
        assertTrue(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.TERMINAL));
        assertTrue(PageOverviewPolicy.drawsLive(PageOverviewPolicy.Kind.HOME_PAGE));
    }
}
