package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

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
        assertEquals(true, DisplayKeyboardRoute.drawerSearchRaisesBuiltIn(true, true, true));
    }

    /** Anywhere else the terminal view is served, and the request keeps its old way. */
    @org.junit.Test
    public void theDrawerSearchLeavesEveryOtherPlaceAlone() {
        assertEquals(false, DisplayKeyboardRoute.drawerSearchRaisesBuiltIn(false, true, true));
        assertEquals(false, DisplayKeyboardRoute.drawerSearchRaisesBuiltIn(true, false, true));
        assertEquals(false, DisplayKeyboardRoute.drawerSearchRaisesBuiltIn(true, true, false));
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
