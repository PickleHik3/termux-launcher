package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Claim;
import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Eligibility;
import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Pull;

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
    public void pagerUsesTheExactAsymmetricThresholdsAndOneWayLatch() {
        AppDrawerGestureArbiter vertical = new AppDrawerGestureArbiter();
        vertical.begin(0f, 0f, Eligibility.allClear());
        assertEquals(Claim.PENDING, vertical.evaluate(0f, SLOP * 1.14f, SLOP));
        assertEquals(Claim.DRAWER_DRAG, vertical.evaluate(0f, SLOP * 1.15f, SLOP));
        assertEquals(Claim.DRAWER_DRAG, vertical.evaluate(SLOP * 10f, SLOP * 1.15f, SLOP));

        AppDrawerGestureArbiter horizontal = new AppDrawerGestureArbiter();
        horizontal.begin(0f, 0f, Eligibility.allClear());
        assertEquals(Claim.PAGE_SWIPE, horizontal.evaluate(SLOP, 0f, SLOP));
        assertEquals(Claim.PAGE_SWIPE, horizontal.evaluate(SLOP, SLOP * 10f, SLOP));
    }

    @Test
    public void pagerNeutralDiagonalAndUpwardMotionNeverClose() {
        AppDrawerGestureArbiter diagonal = new AppDrawerGestureArbiter();
        diagonal.begin(0f, 0f, Eligibility.allClear());
        assertEquals(Claim.PENDING, diagonal.evaluate(SLOP * 4f, SLOP * 4f, SLOP));

        AppDrawerGestureArbiter upward = new AppDrawerGestureArbiter();
        upward.begin(0f, 0f, Eligibility.allClear());
        assertEquals(Claim.PENDING, upward.evaluate(0f, -SLOP * 5f, SLOP));
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

    @Test
    public void railOnTheLeftClaimsASwipeAwayFromItAndNothingElse() {
        AppDrawerGestureArbiter right = new AppDrawerGestureArbiter();
        right.begin(60f, 500f, rail(Pull.RIGHT));
        assertEquals(Claim.PENDING, right.evaluate(60f + (SLOP * 1.14f), 500f, SLOP));
        assertEquals(Claim.DRAWER_DRAG, right.evaluate(60f + (SLOP * 1.15f), 500f, SLOP));

        // Back into the rail's own edge: nothing is there, and the rail has no pager to fall
        // through to — a page claim would deaden every sideways drag short of the drawer's slop.
        AppDrawerGestureArbiter inwards = new AppDrawerGestureArbiter();
        inwards.begin(60f, 500f, rail(Pull.RIGHT));
        assertEquals(Claim.PENDING, inwards.evaluate(60f - (SLOP * 5f), 500f, SLOP));
        assertEquals(Claim.PENDING, inwards.evaluate(60f + (SLOP * 1.14f), 500f, SLOP));
        // And the pull still lands once it clears the threshold, mid-stream.
        assertEquals(Claim.DRAWER_DRAG, inwards.evaluate(60f + (SLOP * 5f), 500f, SLOP));
    }

    @Test
    public void railOnTheRightMirrorsThePull() {
        AppDrawerGestureArbiter left = new AppDrawerGestureArbiter();
        left.begin(900f, 500f, rail(Pull.LEFT));
        assertEquals(Claim.DRAWER_DRAG, left.evaluate(900f - (SLOP * 5f), 500f, SLOP));

        AppDrawerGestureArbiter outwards = new AppDrawerGestureArbiter();
        outwards.begin(900f, 500f, rail(Pull.LEFT));
        assertEquals(Claim.PENDING, outwards.evaluate(900f + (SLOP * 5f), 500f, SLOP));
    }

    @Test
    public void railScrollIsNeverADrawerPull() {
        // The landscape rail scrolls vertically; both directions have to stay its own.
        for (float dy : new float[]{SLOP * 20f, -SLOP * 20f}) {
            for (Pull pull : new Pull[]{Pull.RIGHT, Pull.LEFT}) {
                AppDrawerGestureArbiter arbiter = new AppDrawerGestureArbiter();
                arbiter.begin(60f, 500f, rail(pull));
                assertEquals(pull + " dy=" + dy,
                    Claim.PENDING, arbiter.evaluate(60f, 500f + dy, SLOP));
                // Still the rail's, with the sideways drift a finger dragging down a rail has.
                assertEquals(pull + " dy=" + dy,
                    Claim.PENDING, arbiter.evaluate(60f + (SLOP * 2f), 500f + dy, SLOP));
                assertFalse(arbiter.isLatched());
            }
        }
    }

    @Test
    public void noPullSurfaceVetoesTheDrawerWithoutTakingThePage() {
        Eligibility none = rail(Pull.NONE);
        assertFalse(none.drawerEligible());

        AppDrawerGestureArbiter arbiter = new AppDrawerGestureArbiter();
        arbiter.begin(60f, 500f, none);
        assertEquals(Claim.PENDING, arbiter.evaluate(60f, 1600f, SLOP));
        assertEquals(Claim.PAGE_SWIPE, arbiter.evaluate(600f, 520f, SLOP));
    }

    @Test
    public void thePortraitFlagStillMeansPullDown() {
        assertEquals(Pull.DOWN, eligible().pull);
        assertEquals(Pull.NONE, eligibleExcept(3).pull);
        assertEquals(Pull.DOWN, Eligibility.allClear().pull);
    }

    private static Eligibility rail(Pull pull) {
        return new Eligibility(true, true, true, pull, true, true, true, true, true);
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
