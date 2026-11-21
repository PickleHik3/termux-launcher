package com.termux.app.launcher.drawer;

import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.IGNORE;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.LIFT_APP;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.LIFT_FOLDER;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.LIFT_TILE;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.OPEN_FOLDER;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.PAGE_SWIPE;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.PASS_THROUGH;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.PENDING;
import static com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action.SCROLL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerCategoryTouchRegions.Part;
import com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Action;
import com.termux.app.launcher.drawer.AppDrawerEditGesturePolicy.Touched;
import com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Claim;
import com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Down;
import com.termux.app.launcher.drawer.AppDrawerSectionGesturePolicy.Target;

import org.junit.Test;

public class AppDrawerEditGesturePolicyTest {

    private static final float SLOP = 8f;
    private static final AppDrawerViewType DEFAULT = AppDrawerViewType.DEFAULT;
    private static final AppDrawerSection APPS = AppDrawerSection.APPS;
    private static final AppDrawerSection CATS = AppDrawerSection.CATEGORIES;

    private static AppDrawerEditGesturePolicy begun(boolean editing, AppDrawerViewType type,
                                                    AppDrawerSection section, Touched touched) {
        AppDrawerEditGesturePolicy p = new AppDrawerEditGesturePolicy();
        p.begin(editing, type, section, touched, false, true);
        return p;
    }

    @Test public void notEditingEverythingPassesThrough() {
        for (Touched t : Touched.values()) {
            AppDrawerEditGesturePolicy p = begun(false, DEFAULT, APPS, t);
            assertEquals(t.name(), PASS_THROUGH, p.action());
            assertEquals(t.name(), PASS_THROUGH, p.evaluate(-60f, 0f, 50, SLOP, false));
            assertFalse(p.blocksPageSwipe());
        }
    }

    @Test public void anAppIconLiftsAtOnceOnEveryGrid() {
        for (AppDrawerViewType type : new AppDrawerViewType[] {
            AppDrawerViewType.VERTICAL, AppDrawerViewType.HORIZONTAL, DEFAULT}) {
            AppDrawerEditGesturePolicy p = begun(true, type, APPS, Touched.APP_CELL);
            assertEquals(type.name(), LIFT_APP, p.action());
            assertTrue(p.blocksPageSwipe());
        }
    }

    @Test public void anAppIconDragNeverTurnsIntoASwipeOrScroll() {
        AppDrawerEditGesturePolicy p = begun(true, DEFAULT, APPS, Touched.APP_CELL);
        assertEquals(LIFT_APP, p.evaluate(-80f, 0f, 30, SLOP, false));
        assertEquals(LIFT_APP, p.evaluate(0f, 90f, 60, SLOP, false));
    }

    @Test public void aFolderTapOpensIt() {
        AppDrawerEditGesturePolicy p = begun(true, AppDrawerViewType.VERTICAL, APPS,
            Touched.FOLDER_CELL);
        assertEquals(PENDING, p.action());
        assertEquals(PENDING, p.evaluate(1f, 1f, 40, SLOP, false));
        assertEquals(OPEN_FOLDER, p.evaluate(1f, 1f, 80, SLOP, true));
    }

    @Test public void aFolderDragLiftsTheFolder() {
        AppDrawerEditGesturePolicy p = begun(true, AppDrawerViewType.VERTICAL, APPS,
            Touched.FOLDER_CELL);
        assertEquals(LIFT_FOLDER, p.evaluate(0f, 20f, 40, SLOP, false));
        assertTrue(p.blocksPageSwipe());
    }

    @Test public void aStillHoldOnAFolderLiftsIt() {
        AppDrawerEditGesturePolicy p = begun(true, AppDrawerViewType.VERTICAL, APPS,
            Touched.FOLDER_CELL);
        assertEquals(LIFT_FOLDER, p.evaluate(0f, 0f, AppDrawerEditGesturePolicy.HOLD_LIFT_MS,
            SLOP, false));
    }

    @Test public void aPendingFolderBlocksTheSectionSwipe() {
        assertTrue(begun(true, DEFAULT, APPS, Touched.FOLDER_CELL).blocksPageSwipe());
    }

    @Test public void everyPartOfATileLiftsIt() {
        for (Touched t : new Touched[] {Touched.TILE_HEADING, Touched.TILE_BODY,
            Touched.TILE_PREVIEW_ICON}) {
            assertEquals(t.name(), LIFT_TILE,
                begun(true, AppDrawerViewType.CATEGORIES, APPS, t).action());
            assertEquals(t.name(), LIFT_TILE, begun(true, DEFAULT, CATS, t).action());
        }
    }

    @Test public void aTileInADrawerThatIsNotOnCategoriesIsIgnored() {
        assertEquals(IGNORE, begun(true, DEFAULT, APPS, Touched.TILE_BODY).action());
        assertEquals(IGNORE, begun(true, AppDrawerViewType.VERTICAL, APPS,
            Touched.TILE_BODY).action());
    }

    @Test public void anAppCellOnCategoriesIsIgnored() {
        assertEquals(IGNORE, begun(true, DEFAULT, CATS, Touched.APP_CELL).action());
    }

    @Test public void aTileInAnExpandedCategoryIsNotLifted() {
        AppDrawerEditGesturePolicy p = new AppDrawerEditGesturePolicy();
        assertEquals(IGNORE, p.begin(true, AppDrawerViewType.CATEGORIES, APPS,
            Touched.TILE_BODY, false, false));
    }

    @Test public void theSearchBarIsIgnoredInEditMode() {
        for (AppDrawerViewType type : AppDrawerViewType.values()) {
            assertEquals(IGNORE, begun(true, type, APPS, Touched.SEARCH_BAR).action());
        }
    }

    @Test public void theAzIndexAndTheCornerKeepWorking() {
        assertEquals(PASS_THROUGH, begun(true, DEFAULT, APPS, Touched.AZ_INDEX).action());
        assertEquals(PASS_THROUGH, begun(true, DEFAULT, APPS, Touched.CORNER).action());
        assertFalse(begun(true, DEFAULT, APPS, Touched.AZ_INDEX).blocksPageSwipe());
    }

    @Test public void theSectionSwitchAlwaysSwipesInDefault() {
        assertEquals(PAGE_SWIPE, begun(true, DEFAULT, APPS, Touched.SECTION_SWITCH).action());
        assertEquals(PAGE_SWIPE, begun(true, DEFAULT, CATS, Touched.SECTION_SWITCH).action());
    }

    @Test public void thereIsNoSectionSwitchOutsideDefault() {
        assertEquals(IGNORE, begun(true, AppDrawerViewType.VERTICAL, APPS,
            Touched.SECTION_SWITCH).action());
    }

    @Test public void aGapDragTowardsTheOtherSectionSwipes() {
        assertEquals(PAGE_SWIPE, begun(true, DEFAULT, APPS, Touched.GAP)
            .evaluate(-40f, 2f, 50, SLOP, false));
        assertEquals(PAGE_SWIPE, begun(true, DEFAULT, CATS, Touched.PANE_EDGE)
            .evaluate(40f, 2f, 50, SLOP, false));
    }

    @Test public void aGapDragAwayFromTheOtherSectionScrolls() {
        assertEquals(SCROLL, begun(true, DEFAULT, APPS, Touched.GAP)
            .evaluate(40f, 0f, 50, SLOP, false));
    }

    @Test public void aVerticalGapDragScrolls() {
        assertEquals(SCROLL, begun(true, DEFAULT, APPS, Touched.GAP)
            .evaluate(2f, 30f, 50, SLOP, false));
    }

    @Test public void aGapDragWaitsInsideTheSlop() {
        assertEquals(PENDING, begun(true, DEFAULT, APPS, Touched.GAP)
            .evaluate(-3f, 1f, 50, SLOP, false));
    }

    @Test public void aGapDragOutsideDefaultNeverSwipes() {
        AppDrawerEditGesturePolicy p = begun(true, AppDrawerViewType.HORIZONTAL, APPS,
            Touched.GAP);
        assertEquals(SCROLL, p.evaluate(-60f, 0f, 50, SLOP, false));
    }

    @Test public void swipeFlipsInRtl() {
        AppDrawerEditGesturePolicy p = new AppDrawerEditGesturePolicy();
        p.begin(true, DEFAULT, APPS, Touched.GAP, true, true);
        assertEquals(PAGE_SWIPE, p.evaluate(40f, 0f, 50, SLOP, false));
    }

    @Test public void anExpandedCategoryOwnsItsDragsSoNoSwipe() {
        AppDrawerEditGesturePolicy p = new AppDrawerEditGesturePolicy();
        p.begin(true, DEFAULT, CATS, Touched.GAP, false, false);
        assertEquals(SCROLL, p.evaluate(40f, 0f, 50, SLOP, false));
    }

    @Test public void theAnswerLatches() {
        AppDrawerEditGesturePolicy p = begun(true, DEFAULT, APPS, Touched.GAP);
        assertEquals(PAGE_SWIPE, p.evaluate(-40f, 0f, 50, SLOP, false));
        assertEquals(PAGE_SWIPE, p.evaluate(0f, 90f, 90, SLOP, false));
    }

    // Parity with the section pager: the two never both claim, and agree on a gap.

    private static final float[][] MOVES = {
        {-40f, 2f}, {40f, 2f}, {2f, 40f}, {-3f, 1f}, {-30f, 30f}, {0f, -60f}, {60f, 10f},
        {-80f, -5f}, {20f, -20f}, {-12f, 4f}
    };

    private static Down pagerDown(AppDrawerSection section, Touched touched, boolean rtl) {
        Target target = section == APPS ? Target.APPS_GRID : Target.CATEGORIES;
        Part part = section == APPS ? Part.OUTSIDE : Part.EMPTY_CHROME;
        if (touched == Touched.TILE_HEADING) part = Part.EXPAND_ACTION;
        if (touched == Touched.TILE_BODY || touched == Touched.TILE_PREVIEW_ICON) {
            part = Part.OVERVIEW_LIST;
        }
        return new Down(section, target, part, true, false, true, false, rtl);
    }

    @Test public void parityOnAGapTheTwoClaimTheSameDrags() {
        for (AppDrawerSection section : AppDrawerSection.values()) {
            for (Touched t : new Touched[] {Touched.GAP, Touched.PANE_EDGE}) {
                for (boolean rtl : new boolean[] {false, true}) {
                    for (float[] m : MOVES) {
                        AppDrawerSectionGesturePolicy pager = new AppDrawerSectionGesturePolicy();
                        pager.begin(pagerDown(section, t, rtl));
                        AppDrawerEditGesturePolicy edit = new AppDrawerEditGesturePolicy();
                        edit.begin(true, DEFAULT, section, t, rtl, true);
                        Action a = edit.evaluate(m[0], m[1], 50, SLOP, false);
                        Claim c = pager.evaluate(m[0], m[1], SLOP, edit.blocksPageSwipe());
                        String why = section + " " + t + " rtl=" + rtl + " " + m[0] + "," + m[1];
                        assertEquals(why, a == PAGE_SWIPE, c == Claim.SECTION);
                    }
                }
            }
        }
    }

    @Test public void parityADownThatLiftsIsNeverClaimedByThePager() {
        Touched[] lifting = {Touched.APP_CELL, Touched.FOLDER_CELL, Touched.TILE_HEADING,
            Touched.TILE_BODY, Touched.TILE_PREVIEW_ICON};
        for (Touched t : lifting) {
            AppDrawerSection section = t == Touched.APP_CELL || t == Touched.FOLDER_CELL
                ? APPS : CATS;
            for (boolean rtl : new boolean[] {false, true}) {
                for (float[] m : MOVES) {
                    AppDrawerSectionGesturePolicy pager = new AppDrawerSectionGesturePolicy();
                    pager.begin(pagerDown(section, t, rtl));
                    AppDrawerEditGesturePolicy edit = new AppDrawerEditGesturePolicy();
                    edit.begin(true, DEFAULT, section, t, rtl, true);
                    Action a = edit.evaluate(m[0], m[1], 50, SLOP, false);
                    Claim c = pager.evaluate(m[0], m[1], SLOP, edit.blocksPageSwipe());
                    String why = t + " rtl=" + rtl + " " + m[0] + "," + m[1] + " " + a;
                    assertTrue(why, c != Claim.SECTION);
                    assertTrue(why, a != PAGE_SWIPE);
                }
            }
        }
    }

    @Test public void parityTheSectionSwitchIsNeverTheGridPagersClaim() {
        Down chrome = new Down(APPS, Target.CHROME, Part.OUTSIDE, true, false, true, false, false);
        AppDrawerSectionGesturePolicy pager = new AppDrawerSectionGesturePolicy();
        assertEquals(Claim.CHILD, pager.begin(chrome));
        assertEquals(PAGE_SWIPE, begun(true, DEFAULT, APPS, Touched.SECTION_SWITCH).action());
    }
}
