package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import com.termux.app.x11.DisplayKeyboardRoute.DrawerSearch;
import com.termux.app.x11.DisplayKeyboardRoute.Target;

import org.junit.Test;

/** Which keyboard a request lands on, from the setting and where the wall is standing. */
public class DisplayKeyboardRouteTest {

    @Test
    public void withTheSettingOffNothingChanges() {
        assertEquals(Target.BUILT_IN, DisplayKeyboardRoute.decide(false, true, true, true));
        assertEquals(Target.BUILT_IN, DisplayKeyboardRoute.decide(false, false, false, true));
        assertEquals(Target.NONE, DisplayKeyboardRoute.decide(false, true, true, false));
    }

    @Test
    public void onTheDisplayPlaceTheSettingHandsTheRequestToThePhonesKeyboard() {
        assertEquals(Target.SYSTEM_IME, DisplayKeyboardRoute.decide(true, true, true, true));
    }

    /** The setting is the Display place's; the terminal keeps the launcher's keyboard. */
    @Test
    public void offTheDisplayPlaceTheSettingIsNotRead() {
        assertEquals(Target.BUILT_IN, DisplayKeyboardRoute.decide(true, false, true, true));
        assertEquals(Target.NONE, DisplayKeyboardRoute.decide(true, false, true, false));
    }

    /**
     * The drawer's search types through the launcher's keyboard, and over a display the focus is
     * the display's own, so the system keyboard it would ask for is refused as "not served": the
     * launcher's keyboard has to be raised for it there.
     */
    @org.junit.Test
    public void theDrawerSearchRaisesTheBuiltInKeyboardOverARunningDisplay() {
        assertEquals(DrawerSearch.RAISE_BUILT_IN,
            DisplayKeyboardRoute.drawerSearch(true, true, true, false, false));
    }

    /** Keyboard off is the user's standing say, and a tap on search is a request for one. */
    @org.junit.Test
    public void aKeyboardTheUserSwitchedOffComesBackForTheSearch() {
        assertEquals(DrawerSearch.TURN_ON_AND_RAISE,
            DisplayKeyboardRoute.drawerSearch(true, true, true, true, false));
    }

    /** A launcher keyboard set to stay away from a physical keyboard stays away: keys do the typing. */
    @org.junit.Test
    public void aKeyboardThatYieldsToAPhysicalKeyboardRaisesNothing() {
        assertEquals(DrawerSearch.NONE,
            DisplayKeyboardRoute.drawerSearch(true, true, true, false, true));
        assertEquals(DrawerSearch.NONE,
            DisplayKeyboardRoute.drawerSearch(true, true, true, true, true));
    }

    /** Anywhere else the terminal view is served, and the request keeps its old way. */
    @org.junit.Test
    public void theDrawerSearchLeavesEveryOtherPlaceAlone() {
        assertEquals(DrawerSearch.SYSTEM_ASK,
            DisplayKeyboardRoute.drawerSearch(false, true, true, true, true));
        assertEquals(DrawerSearch.SYSTEM_ASK,
            DisplayKeyboardRoute.drawerSearch(true, false, true, true, true));
        assertEquals(DrawerSearch.SYSTEM_ASK,
            DisplayKeyboardRoute.drawerSearch(true, true, false, false, false));
    }

    /** A keyboard the drawer raised, still up and not touched by the user, goes down with it. */
    @org.junit.Test
    public void aKeyboardTheDrawerRaisedGoesDownWhenTheDrawerCloses() {
        assertEquals(true, DisplayKeyboardRoute.drawerCloseLowers(true, false, true));
    }

    /** A keyboard that was already up when the drawer opened is the user's, and stays. */
    @org.junit.Test
    public void aKeyboardThatWasAlreadyUpStaysWhenTheDrawerCloses() {
        assertEquals(false, DisplayKeyboardRoute.drawerCloseLowers(false, false, true));
    }

    /** Pinned by hand while the drawer was open, it is the user's now, and stays. */
    @org.junit.Test
    public void aKeyboardTheUserPinnedMeanwhileStaysWhenTheDrawerCloses() {
        assertEquals(false, DisplayKeyboardRoute.drawerCloseLowers(true, true, true));
    }

    /** Already put away by hand, there is nothing left to lower. */
    @org.junit.Test
    public void aKeyboardAlreadyDownIsLeftBe() {
        assertEquals(false, DisplayKeyboardRoute.drawerCloseLowers(true, false, false));
    }

    /** An empty Display page has nothing to type into, so the keyboard is the launcher's. */
    @Test
    public void withNoDisplayRunningTheRequestFallsBackToTheBuiltInKeyboard() {
        assertEquals(Target.BUILT_IN, DisplayKeyboardRoute.decide(true, true, false, true));
        assertEquals(Target.NONE, DisplayKeyboardRoute.decide(true, true, false, false));
    }

    /** The phone's keyboard answers on the Display place even with the launcher's switched off. */
    @Test
    public void thePhonesKeyboardDoesNotNeedTheBuiltInOne() {
        assertEquals(Target.SYSTEM_IME, DisplayKeyboardRoute.decide(true, true, true, false));
    }
}
