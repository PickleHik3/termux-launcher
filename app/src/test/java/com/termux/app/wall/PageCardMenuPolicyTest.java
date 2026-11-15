package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import com.termux.app.wall.PageCardMenuPolicy.Action;
import com.termux.app.wall.PageOverviewPolicy.Kind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A page card's long-press menu and the Display picker's list (ADR 0015 section 2). */
public class PageCardMenuPolicyTest {

    @Test
    public void aHomePageOffersSetAsHomeRenameAndRemove() {
        assertEquals(Arrays.asList(Action.SET_HOME, Action.RENAME, Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.HOME_PAGE, false, 2, false));
    }

    @Test
    public void theHomePageDoesNotOfferSetAsHome() {
        assertEquals(Arrays.asList(Action.RENAME, Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.HOME_PAGE, true, 2, false));
    }

    @Test
    public void theLastHomePageCannotBeRemoved() {
        assertEquals(Arrays.asList(Action.SET_HOME, Action.RENAME),
            PageCardMenuPolicy.actions(Kind.HOME_PAGE, false, 1, false));
    }

    @Test
    public void aDisplayPageAlsoOffersBindApp() {
        assertEquals(Arrays.asList(Action.SET_HOME, Action.RENAME, Action.BIND_APP, Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.DISPLAY, false, 1, false));
    }

    @Test
    public void theTerminalCardOffersSetAsHomeAndNoWindowRemoval() {
        assertEquals(Collections.singletonList(Action.SET_HOME),
            PageCardMenuPolicy.actions(Kind.TERMINAL, false, 1, false));
        assertEquals("already home", Collections.emptyList(),
            PageCardMenuPolicy.actions(Kind.TERMINAL, true, 1, false));
    }

    @Test
    public void theTerminalCardOffersRemoveTerminalWhileAnotherPageIsLeft() {
        assertEquals(Arrays.asList(Action.SET_HOME, Action.REMOVE_TERMINAL),
            PageCardMenuPolicy.actions(Kind.TERMINAL, false, 1, true));
        assertEquals(Collections.singletonList(Action.REMOVE_TERMINAL),
            PageCardMenuPolicy.actions(Kind.TERMINAL, true, 1, true));
        assertEquals("only the Terminal card", Arrays.asList(Action.SET_HOME, Action.RENAME,
            Action.REMOVE), PageCardMenuPolicy.actions(Kind.HOME_PAGE, false, 2, true));
    }

    @Test
    public void thePickerLeavesOutAppsAnotherPageIsBoundTo() {
        List<String> catalog = Arrays.asList("gimp", "firefox", "xterm");
        assertEquals(Arrays.asList("gimp", "xterm"),
            PageCardMenuPolicy.pickableApps(catalog, Collections.singleton("firefox"), null));
    }

    @Test
    public void bindingAPageKeepsItsOwnAppListed() {
        List<String> catalog = Arrays.asList("gimp", "firefox", "firefox");
        assertEquals(Arrays.asList("gimp", "firefox"),
            PageCardMenuPolicy.pickableApps(catalog, Collections.singleton("firefox"), "firefox"));
    }

    @Test
    public void aCardShowsItsOwnNameElseItsDefault() {
        assertEquals("Work", PageCardMenuPolicy.cardName("  Work ", "Page 2"));
        assertEquals("Page 2", PageCardMenuPolicy.cardName(" ", "Page 2"));
        assertEquals("Page 2", PageCardMenuPolicy.cardName(null, "Page 2"));
    }
}
