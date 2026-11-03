package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.home.HomeGestures.Action;
import com.termux.app.launcher.home.HomeGestures.Availability;
import com.termux.app.launcher.home.HomeGestures.Gesture;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HomeGesturesTest {

    private static final Availability EVERYTHING = new Availability(true, true);
    private static final Availability NO_HELPER = new Availability(false, true);

    @Test
    public void unsetGesturesDoWhatHomeAlwaysDid() {
        assertSame(Action.PALETTE, HomeGestures.parse(Gesture.SWIPE_UP, null));
        for (Gesture gesture : new Gesture[] {Gesture.SWIPE_DOWN, Gesture.DOUBLE_TAP,
                Gesture.TWO_FINGER_UP, Gesture.TWO_FINGER_DOWN}) {
            assertSame(gesture.name(), Action.NONE, HomeGestures.parse(gesture, null));
        }
    }

    @Test
    public void swipeUpKeepsTheOldPaletteGesturesKeyAndValues() {
        assertEquals("app_launcher_home_palette_gesture", Gesture.SWIPE_UP.key);
        assertSame(Action.PALETTE, HomeGestures.parse(Gesture.SWIPE_UP, "swipe_up"));
        assertSame(Action.NONE, HomeGestures.parse(Gesture.SWIPE_UP, "off"));
        assertEquals("swipe_up", HomeGestures.storedValue(Gesture.SWIPE_UP, Action.PALETTE));
        assertEquals("off", HomeGestures.storedValue(Gesture.SWIPE_UP, Action.NONE));
        assertEquals("drawer", HomeGestures.storedValue(Gesture.SWIPE_UP, Action.DRAWER));
    }

    @Test
    public void everyActionSurvivesBeingStoredOnEveryGesture() {
        for (Gesture gesture : Gesture.values()) {
            for (Action action : Action.values()) {
                assertSame(gesture + " " + action, action,
                    HomeGestures.parse(gesture, HomeGestures.storedValue(gesture, action)));
            }
        }
    }

    @Test
    public void anUnknownValueFallsBackToTheDefault() {
        assertSame(Action.PALETTE, HomeGestures.parse(Gesture.SWIPE_UP, "spin"));
        assertSame(Action.NONE, HomeGestures.parse(Gesture.DOUBLE_TAP, "spin"));
    }

    @Test
    public void keysAreDistinctAndFoundBack() {
        for (Gesture gesture : Gesture.values()) {
            assertSame(gesture, Gesture.forKey(gesture.key));
            assertEquals(gesture.key + "_app", gesture.appKey());
        }
        assertNull(Gesture.forKey("app_launcher_start_in"));
    }

    @Test
    public void helperActionsAreOfferedOnlyWithAHelper() {
        List<Action> with = HomeGestures.choices(Action.NONE, EVERYTHING);
        assertEquals(Arrays.asList(Action.values()), with);
        List<Action> without = HomeGestures.choices(Action.NONE, NO_HELPER);
        assertFalse(without.contains(Action.LOCK_SCREEN));
        assertFalse(without.contains(Action.NOTIFICATIONS));
        assertFalse(without.contains(Action.QUICK_SETTINGS));
        assertTrue(without.contains(Action.TERMINAL));
        assertTrue(without.contains(Action.APP));
        assertTrue(HomeGestures.helperMissing(NO_HELPER));
        assertFalse(HomeGestures.helperMissing(EVERYTHING));
    }

    @Test
    public void theChosenActionStaysListedWhenItsHelperWentAway() {
        List<Action> choices = HomeGestures.choices(Action.LOCK_SCREEN, NO_HELPER);
        assertTrue(choices.contains(Action.LOCK_SCREEN));
        assertFalse(choices.contains(Action.NOTIFICATIONS));
    }

    @Test
    public void theDrawerIsOfferedOnlyWhileItIsOn() {
        assertFalse(HomeGestures.choices(Action.NONE, new Availability(true, false))
            .contains(Action.DRAWER));
    }

    @Test
    public void onlyGesturesSetToSomethingAreWatched() {
        assertFalse(HomeGestures.watched(Action.NONE));
        assertTrue(HomeGestures.watched(Action.LOCK_SCREEN));
    }

    @Test
    public void aSecondTapLandsSoonAndNearTheFirst() {
        assertTrue(HomeGestures.isSecondTap(1000, 100, 100, 1200, 110, 95, 300, 50));
        assertFalse("too late", HomeGestures.isSecondTap(1000, 100, 100, 1301, 100, 100, 300, 50));
        assertFalse("too far", HomeGestures.isSecondTap(1000, 100, 100, 1100, 160, 100, 300, 50));
        assertFalse("no first tap", HomeGestures.isSecondTap(-1, 100, 100, 1100, 100, 100, 300, 50));
        assertFalse("before the first", HomeGestures.isSecondTap(1000, 100, 100, 900, 100, 100, 300, 50));
    }

    @Test
    public void aTwoFingerSwipeNeedsVerticalTravelPastTheCommit() {
        assertEquals(-1, HomeGestures.twoFingerDirection(10, -80, 64));
        assertEquals(1, HomeGestures.twoFingerDirection(-5, 70, 64));
        assertEquals("short", 0, HomeGestures.twoFingerDirection(0, 40, 64));
        assertEquals("sideways", 0, HomeGestures.twoFingerDirection(120, 90, 64));
    }
}
