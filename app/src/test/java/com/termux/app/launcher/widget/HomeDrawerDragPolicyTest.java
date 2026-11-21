package com.termux.app.launcher.widget;

import com.termux.app.launcher.home.HomeGestures.Action;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When Home's free area hands a swipe up to the drawer's live drag. */
public class HomeDrawerDragPolicyTest {

    private static final float SLOP = 16f;

    @Test public void upwardVerticalMove_claimsWhenSwipeUpIsTheDrawer() {
        assertTrue(HomeDrawerDragPolicy.claims(Action.DRAWER, 3f, -20f, SLOP));
        assertTrue(HomeDrawerDragPolicy.claims(Action.DRAWER, -10f, -30f, SLOP));
    }

    @Test public void withinTheSlop_doesNotClaimYet() {
        assertFalse(HomeDrawerDragPolicy.claims(Action.DRAWER, 0f, -10f, SLOP));
        assertFalse(HomeDrawerDragPolicy.claims(Action.DRAWER, 0f, -16f, SLOP));
    }

    @Test public void horizontalSwipe_doesNotClaim() {
        assertFalse(HomeDrawerDragPolicy.claims(Action.DRAWER, 40f, -5f, SLOP));
        assertFalse(HomeDrawerDragPolicy.claims(Action.DRAWER, -65f, -62f, SLOP));
    }

    @Test public void downwardSwipe_doesNotClaim() {
        assertFalse(HomeDrawerDragPolicy.claims(Action.DRAWER, 2f, 30f, SLOP));
    }

    @Test public void otherActions_doNotClaim() {
        for (Action action : Action.values()) {
            if (action == Action.DRAWER) continue;
            assertFalse(action.name(), HomeDrawerDragPolicy.claims(action, 0f, -40f, SLOP));
        }
    }

    @Test public void notANumber_doesNotClaim() {
        assertFalse(HomeDrawerDragPolicy.claims(Action.DRAWER, Float.NaN, -40f, SLOP));
        assertFalse(HomeDrawerDragPolicy.claims(Action.DRAWER, 0f, Float.NaN, SLOP));
    }
}
