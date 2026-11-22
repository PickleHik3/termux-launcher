package com.termux.app.launcher.drawer;

import static com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Claim.CHILD;
import static com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Claim.PENDING;
import static com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Claim.SECTION;
import static org.junit.Assert.assertEquals;

import com.termux.app.launcher.drawer.AppDrawerCategoryTouchRegions.Part;
import com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Down;
import com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Target;

import org.junit.Test;

public class AppDrawerSectionGesturePolicyTest {

    private static final float SLOP = 8f;

    private static Down apps(Target target, boolean rtl) {
        return new Down(AppDrawerSection.APPS, target, Part.OUTSIDE, false, false, true, false, rtl);
    }

    private static Down categories(Part part, boolean overview) {
        return new Down(AppDrawerSection.CATEGORIES, Target.CATEGORIES, part, overview, false,
            true, false, false);
    }

    private static AppDrawerSectionGesturePolicy begun(Down down) {
        AppDrawerSectionGesturePolicy policy = new AppDrawerSectionGesturePolicy();
        policy.begin(down);
        return policy;
    }

    @Test public void appsClaimsATowardsCategoriesDragInLtr() {
        AppDrawerSectionGesturePolicy policy = begun(apps(Target.APPS_GRID, false));
        assertEquals(PENDING, policy.claim());
        assertEquals(PENDING, policy.evaluate(-4f, 0f, SLOP, false));
        assertEquals(SECTION, policy.evaluate(-30f, 2f, SLOP, false));
    }

    @Test public void appsClaimsTheOtherWayInRtl() {
        assertEquals(SECTION, begun(apps(Target.APPS_GRID, true)).evaluate(30f, 0f, SLOP, false));
        assertEquals(CHILD, begun(apps(Target.APPS_GRID, true)).evaluate(-30f, 0f, SLOP, false));
    }

    @Test public void appsNeverClaimsAwayFromCategories() {
        assertEquals(CHILD, begun(apps(Target.APPS_GRID, false)).evaluate(40f, 0f, SLOP, false));
    }

    @Test public void categoriesClaimsTowardsApps() {
        AppDrawerSectionGesturePolicy policy = begun(categories(Part.OVERVIEW_LIST, true));
        assertEquals(SECTION, policy.evaluate(40f, 3f, SLOP, false));
        assertEquals(CHILD, begun(categories(Part.OVERVIEW_LIST, true))
            .evaluate(-40f, 0f, SLOP, false));
    }

    @Test public void categoriesOverviewPartsMaySwipeOthersMayNot() {
        for (Part part : new Part[] {Part.OVERVIEW_LIST, Part.EXPAND_ACTION, Part.EMPTY_CHROME})
            assertEquals(part.name(), PENDING, begun(categories(part, true)).claim());
        for (Part part : new Part[] {Part.DETAIL_LIST, Part.COLLAPSE_ACTION, Part.DETAIL_EMPTY,
            Part.TRANSITION_BODY, Part.OUTSIDE})
            assertEquals(part.name(), CHILD, begun(categories(part, true)).claim());
    }

    @Test public void aCategoryThatIsNotInItsOverviewNeverSwipes() {
        assertEquals(CHILD, begun(categories(Part.OVERVIEW_LIST, false)).claim());
    }

    @Test public void theAzColumnAndChromeNeverSwipe() {
        assertEquals(CHILD, begun(apps(Target.AZ_COLUMN, false)).claim());
        assertEquals(CHILD, begun(apps(Target.CHROME, false)).claim());
    }

    @Test public void aQueryOrASearchRequestFreezesTheSections() {
        Down searching = new Down(AppDrawerSection.APPS, Target.APPS_GRID, Part.OUTSIDE, false,
            true, true, false, false);
        assertEquals(CHILD, begun(searching).claim());
    }

    @Test public void aDrawerThatIsNotInteractiveNeverSwipes() {
        Down closed = new Down(AppDrawerSection.APPS, Target.APPS_GRID, Part.OUTSIDE, false,
            false, false, false, false);
        assertEquals(CHILD, begun(closed).claim());
    }

    @Test public void aTargetOfTheOtherSectionNeverSwipes() {
        Down mismatch = new Down(AppDrawerSection.CATEGORIES, Target.APPS_GRID, Part.OUTSIDE,
            true, false, true, false, false);
        assertEquals(CHILD, begun(mismatch).claim());
    }

    @Test public void aNullDownIsNeverTheSections() {
        AppDrawerSectionGesturePolicy policy = new AppDrawerSectionGesturePolicy();
        policy.begin(null);
        assertEquals(CHILD, policy.evaluate(-50f, 0f, SLOP, false));
    }

    @Test public void anArmedPickupLatchesChild() {
        AppDrawerSectionGesturePolicy policy = begun(apps(Target.APPS_GRID, false));
        assertEquals(CHILD, policy.evaluate(-50f, 0f, SLOP, true));
        assertEquals(CHILD, policy.evaluate(-90f, 0f, SLOP, false));
    }

    @Test public void aClearlyVerticalMoveLatchesChild() {
        AppDrawerSectionGesturePolicy policy = begun(apps(Target.APPS_GRID, false));
        assertEquals(PENDING, policy.evaluate(0f, 3f, SLOP, false));
        assertEquals(CHILD, policy.evaluate(-4f, 20f, SLOP, false));
        // One-way: a later horizontal run cannot take it back.
        assertEquals(CHILD, policy.evaluate(-300f, 20f, SLOP, false));
    }

    @Test public void theDiagonalConeStaysPending() {
        AppDrawerSectionGesturePolicy policy = begun(apps(Target.APPS_GRID, false));
        // 45 degrees: neither dominant enough for the section nor vertical enough for a child.
        assertEquals(PENDING, policy.evaluate(-24f, 24f, SLOP, false));
        assertEquals(PENDING, policy.evaluate(-24f, 30f, SLOP, false));
        // Then it turns clearly sideways.
        assertEquals(SECTION, policy.evaluate(-80f, 30f, SLOP, false));
    }

    @Test public void theSectionLatchIsOneWay() {
        AppDrawerSectionGesturePolicy policy = begun(apps(Target.APPS_GRID, false));
        assertEquals(SECTION, policy.evaluate(-40f, 0f, SLOP, false));
        assertEquals(SECTION, policy.evaluate(-40f, 400f, SLOP, false));
        assertEquals(SECTION, policy.evaluate(-40f, 0f, SLOP, true));
    }

    @Test public void aSteepSidewaysDragNeedsMoreThanTheSlop() {
        // Past PagePolicy damping angle the slop grows: 9 px along at ~38 degrees is not enough.
        AppDrawerSectionGesturePolicy policy = begun(apps(Target.APPS_GRID, false));
        assertEquals(PENDING, policy.evaluate(-9f, 7f, SLOP, false));
    }

    @Test public void aDownOnASettlingPagerCatchesItAtOnce() {
        Down settlingApps = new Down(AppDrawerSection.APPS, Target.APPS_GRID, Part.OUTSIDE, false,
            false, true, true, false);
        assertEquals(SECTION, begun(settlingApps).claim());
        Down settlingCategories = new Down(AppDrawerSection.CATEGORIES, Target.CATEGORIES,
            Part.TRANSITION_BODY, false, false, true, true, false);
        assertEquals(SECTION, begun(settlingCategories).claim());
        // Even the strip is the page's while it moves.
        Down settlingOnColumn = new Down(AppDrawerSection.APPS, Target.AZ_COLUMN, Part.OUTSIDE,
            false, false, true, true, false);
        assertEquals(CHILD, begun(settlingOnColumn).claim());
    }

    @Test public void aCatchNeverHappensOnChromeOrWhileSearchingOrClosed() {
        assertEquals(CHILD, begun(new Down(AppDrawerSection.APPS, Target.CHROME, Part.OUTSIDE,
            false, false, true, true, false)).claim());
        assertEquals(CHILD, begun(new Down(AppDrawerSection.APPS, Target.APPS_GRID, Part.OUTSIDE,
            false, true, true, true, false)).claim());
        assertEquals(CHILD, begun(new Down(AppDrawerSection.APPS, Target.APPS_GRID, Part.OUTSIDE,
            false, false, false, true, false)).claim());
    }

    @Test public void resetDropsTheLatch() {
        AppDrawerSectionGesturePolicy policy = begun(apps(Target.APPS_GRID, false));
        policy.evaluate(-40f, 0f, SLOP, false);
        policy.reset();
        assertEquals(CHILD, policy.claim());
    }
}
