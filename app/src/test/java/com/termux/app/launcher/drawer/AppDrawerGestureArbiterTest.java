package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Claim;
import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Eligibility;

import org.junit.Test;

import java.util.Arrays;

/**
 * Ownership of the dock's touch stream.
 *
 * <p>The regressions these guard are the two that a "recompute the intent on every move" arbiter
 * produces: a vertical pull that drifts sideways flipping the dock to its page swipe half way
 * down, and a diagonal flick satisfying both tests at once. Hence the neutral-cone and one-way
 * latch cases, and hence every eligibility veto being checked to block the drawer <em>without</em>
 * taking the page swipe down with it — the dock must keep paging when the drawer is switched off.
 */
public class AppDrawerGestureArbiterTest {

    private static final float SLOP = 24f;

    /** Index into the eligibility flags, in constructor order. */
    private static final String[] VETO_NAMES = {
        "drawerEnabled", "searchEmpty", "azInactive", "portrait",
        "notDockTuning", "paletteClosed", "noActivePickup", "drawerIdle",
    };

    @Test
    public void straightDownClaimsTheDrawer() {
        AppDrawerGestureArbiter arbiter = new AppDrawerGestureArbiter();
        arbiter.begin(300f, 900f, eligible());
        assertEquals(Claim.PENDING, arbiter.evaluate(300f, 910f, SLOP));
        assertEquals(Claim.DRAWER_DRAG, arbiter.evaluate(300f, 1000f, SLOP));
        assertTrue(arbiter.isDrawerDrag());
    }

    @Test
    public void straightAcrossClaimsThePage() {
        AppDrawerGestureArbiter arbiter = new AppDrawerGestureArbiter();
        arbiter.begin(300f, 900f, eligible());
        assertEquals(Claim.PENDING, arbiter.evaluate(310f, 900f, SLOP));
        assertEquals(Claim.PAGE_SWIPE, arbiter.evaluate(400f, 900f, SLOP));
    }

    @Test
    public void diagonalNeutralConeClaimsNeither() {
        // Exactly 45 degrees, far past slop on both axes: 1.2 and 1.1 leave this to no one.
        AppDrawerGestureArbiter arbiter = new AppDrawerGestureArbiter();
        arbiter.begin(300f, 900f, eligible());
        assertEquals(Claim.PENDING, arbiter.evaluate(400f, 1000f, SLOP));
        assertEquals(Claim.PENDING, arbiter.evaluate(200f, 1000f, SLOP));
        assertFalse(arbiter.isLatched());
    }

    @Test
    public void upwardDragNeverClaimsTheDrawer() {
        AppDrawerGestureArbiter arbiter = new AppDrawerGestureArbiter();
        arbiter.begin(300f, 900f, eligible());
        assertEquals(Claim.PENDING, arbiter.evaluate(300f, 400f, SLOP));
        // A steep upward drag with a little sideways drift stays unclaimed rather than paging.
        assertEquals(Claim.PENDING, arbiter.evaluate(310f, 400f, SLOP));
        assertFalse(arbiter.isLatched());
    }

    @Test
    public void latchIsOneWay() {
        AppDrawerGestureArbiter drawer = new AppDrawerGestureArbiter();
        drawer.begin(300f, 900f, eligible());
        assertEquals(Claim.DRAWER_DRAG, drawer.evaluate(300f, 1000f, SLOP));
        assertEquals(Claim.DRAWER_DRAG, drawer.evaluate(900f, 1000f, SLOP));
        assertEquals(Claim.DRAWER_DRAG, drawer.claimChild());

        AppDrawerGestureArbiter page = new AppDrawerGestureArbiter();
        page.begin(300f, 900f, eligible());
        assertEquals(Claim.PAGE_SWIPE, page.evaluate(500f, 900f, SLOP));
        assertEquals(Claim.PAGE_SWIPE, page.evaluate(500f, 1600f, SLOP));

        AppDrawerGestureArbiter child = new AppDrawerGestureArbiter();
        child.begin(300f, 900f, eligible());
        assertEquals(Claim.CHILD_OWNED, child.claimChild());
        assertEquals(Claim.CHILD_OWNED, child.evaluate(300f, 1600f, SLOP));
    }

    @Test
    public void beginResetsAPreviousLatch() {
        AppDrawerGestureArbiter arbiter = new AppDrawerGestureArbiter();
        arbiter.begin(300f, 900f, eligible());
        assertEquals(Claim.DRAWER_DRAG, arbiter.evaluate(300f, 1000f, SLOP));
        arbiter.begin(300f, 900f, eligible());
        assertEquals(Claim.PENDING, arbiter.claim());
        arbiter.reset();
        assertEquals(Claim.PENDING, arbiter.claim());
        // With no snapshot, a move before the next begin() can claim nothing.
        assertEquals(Claim.PENDING, arbiter.evaluate(300f, 1600f, SLOP));
    }

    @Test
    public void everyEligibilityVetoBlocksTheDrawerButNotThePage() {
        for (int i = 0; i < VETO_NAMES.length; i++) {
            Eligibility vetoed = eligibleExcept(i);
            assertFalse(VETO_NAMES[i] + " should veto the drawer", vetoed.drawerEligible());

            AppDrawerGestureArbiter drawer = new AppDrawerGestureArbiter();
            drawer.begin(300f, 900f, vetoed);
            assertEquals("veto: " + VETO_NAMES[i],
                Claim.PENDING, drawer.evaluate(300f, 1600f, SLOP));

            AppDrawerGestureArbiter page = new AppDrawerGestureArbiter();
            page.begin(300f, 900f, vetoed);
            assertEquals("veto: " + VETO_NAMES[i],
                Claim.PAGE_SWIPE, page.evaluate(600f, 900f, SLOP));
        }
    }

    private static Eligibility eligible() {
        return eligibleExcept(-1);
    }

    private static Eligibility eligibleExcept(int vetoIndex) {
        boolean[] flags = new boolean[VETO_NAMES.length];
        Arrays.fill(flags, true);
        if (vetoIndex >= 0) flags[vetoIndex] = false;
        return new Eligibility(flags[0], flags[1], flags[2], flags[3],
            flags[4], flags[5], flags[6], flags[7]);
    }
}
