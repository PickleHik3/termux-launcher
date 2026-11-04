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
            PageCardMenuPolicy.actions(Kind.HOME_PAGE, false, false, 2));
    }

    @Test
    public void theHomePageDoesNotOfferSetAsHome() {
        assertEquals(Arrays.asList(Action.RENAME, Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.HOME_PAGE, true, false, 2));
    }

    @Test
    public void theLastHomePageCannotBeRemoved() {
        assertEquals(Arrays.asList(Action.SET_HOME, Action.RENAME),
            PageCardMenuPolicy.actions(Kind.HOME_PAGE, false, false, 1));
    }

    @Test
    public void aDisplayPageAlsoOffersBindApp() {
        assertEquals(Arrays.asList(Action.SET_HOME, Action.RENAME, Action.BIND_APP, Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.DISPLAY, false, false, 1));
    }

    @Test
    public void onlyTheFirstTerminalWindowOffersSetAsHome() {
        assertEquals(Arrays.asList(Action.SET_HOME, Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.TERMINAL, false, true, 1));
        assertEquals(Collections.singletonList(Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.TERMINAL, false, false, 1));
        assertEquals(Collections.singletonList(Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.TERMINAL, true, true, 1));
    }

    @Test
    public void aTerminalCardOffersRemoveTerminalWhileAnotherPageIsLeft() {
        assertEquals(Arrays.asList(Action.REMOVE, Action.REMOVE_TERMINAL),
            PageCardMenuPolicy.actions(Kind.TERMINAL, false, false, 1, true));
        assertEquals(Collections.singletonList(Action.REMOVE),
            PageCardMenuPolicy.actions(Kind.TERMINAL, false, false, 1, false));
        assertEquals("only terminal cards", Arrays.asList(Action.SET_HOME, Action.RENAME,
            Action.REMOVE), PageCardMenuPolicy.actions(Kind.HOME_PAGE, false, false, 2, true));
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
