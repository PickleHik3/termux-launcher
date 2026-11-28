package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ScrollingLayoutPolicyTest {

    private static final float DELTA = 0.01f;
    /** Portrait phone: 1000 wide, 2000 tall, 20 gap. */
    private static final ScrollingLayoutPolicy.Viewport PORTRAIT =
        new ScrollingLayoutPolicy.Viewport(1000f, 2000f, 20f, false);
    /** Landscape: 2000 wide, 1000 tall, 20 gap. */
    private static final ScrollingLayoutPolicy.Viewport LANDSCAPE =
        new ScrollingLayoutPolicy.Viewport(2000f, 1000f, 20f, true);

    private static ScrollingLayoutPolicy.State open(ScrollingLayoutPolicy.State s,
        ScrollingLayoutPolicy.Viewport v, int... ids) {
        for (int id : ids) s = ScrollingLayoutPolicy.open(s, v, id);
        return s;
    }

    private static ScrollingLayoutPolicy.Placement at(ScrollingLayoutPolicy.State s,
        ScrollingLayoutPolicy.Viewport v, int id) {
        ScrollingLayoutPolicy.Placement p = ScrollingLayoutPolicy.placementOf(s, v, id);
        assertTrue("window " + id + " placed", p != null);
        return p;
    }

    private static long[] order(ScrollingLayoutPolicy.State s) {
        return s.windowIdsInOrder();
    }

    // -- open ---------------------------------------------------------------------------------

    @Test
    public void open_firstWindowFillsTheViewportAndIsFocused() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1);
        ScrollingLayoutPolicy.Placement p = at(s, PORTRAIT, 1);
        assertEquals(0f, p.left, DELTA);
        assertEquals(1000f, p.right, DELTA);
        assertEquals(0f, p.top, DELTA);
        assertEquals(2000f, p.bottom, DELTA);
        assertEquals(1, s.focusedWindow);
    }

    @Test
    public void open_becomesTheColumnRightAfterTheFocusedOne() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2, 3);
        // focus the first, then open: the new window lands between 1 and 2
        s = ScrollingLayoutPolicy.focusWindow(s, PORTRAIT, 1);
        s = ScrollingLayoutPolicy.open(s, PORTRAIT, 4);
        assertEquals(Arrays.toString(new long[] {1, 4, 2, 3}), Arrays.toString(order(s)));
        assertEquals(4, s.focusedWindow);
    }

    @Test
    public void open_afterAStackedFocusGoesAfterTheWholeColumn() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        s = ScrollingLayoutPolicy.open(s, PORTRAIT, 3);
        assertEquals(2, s.columnCount());
        assertEquals(Arrays.toString(new long[] {1, 2, 3}), Arrays.toString(order(s)));
    }

    @Test
    public void open_scrollsTheNewColumnIntoView() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2, 3);
        // Updated for round 2: every column a half (490 wide): the strip is 3 * 490 + 2 * 20 and
        // the third column is shown whole at the viewport's right.
        assertEquals(510f, s.scroll, DELTA);
        assertEquals(1020f, at(s, PORTRAIT, 3).left, DELTA);
    }

    @Test
    public void open_inPortraitASecondWindowStandsHalfBesideTheFirst() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        assertEquals(490f, at(s, PORTRAIT, 1).width(), DELTA);
        assertEquals(490f, at(s, PORTRAIT, 2).width(), DELTA);
        assertEquals(0f, s.scroll, DELTA);
    }

    @Test
    public void open_inLandscapeASecondWindowTakesAThirdBesideTwoThirds() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, LANDSCAPE, 1, 2);
        float third = (2000f - 40f) / 3f;
        assertEquals(2f * third + 20f, at(s, LANDSCAPE, 1).width(), DELTA);
        assertEquals(third, at(s, LANDSCAPE, 2).width(), DELTA);
        assertEquals(2000f, at(s, LANDSCAPE, 2).right - s.scroll, DELTA);
    }

    @Test
    public void openedWidth_loneIsFull_besideOthersAHalfOrAThird() {
        assertEquals(1f, ScrollingLayoutPolicy.openedWidth(true, false), 0f);
        assertEquals(1f, ScrollingLayoutPolicy.openedWidth(true, true), 0f);
        assertEquals(0.5f, ScrollingLayoutPolicy.openedWidth(false, false), 0f);
        assertEquals(1f / 3f, ScrollingLayoutPolicy.openedWidth(false, true), 0.0001f);
    }

    // -- presets ------------------------------------------------------------------------------

    @Test
    public void presets_portraitHasFullAndHalf_landscapeAddsThirds() {
        assertEquals(2, ScrollingLayoutPolicy.presets(false).length);
        assertEquals(4, ScrollingLayoutPolicy.presets(true).length);
        assertEquals(1f, ScrollingLayoutPolicy.presets(false)[0], 0f);
        assertEquals(0.5f, ScrollingLayoutPolicy.presets(false)[1], 0f);
        assertEquals(1f / 3f, ScrollingLayoutPolicy.presets(true)[2], 0.0001f);
        assertEquals(2f / 3f, ScrollingLayoutPolicy.presets(true)[3], 0.0001f);
    }

    @Test
    public void halfPreset_twoColumnsFillTheViewportWithOneGap() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 2, 0.5f);
        assertEquals(490f, at(s, PORTRAIT, 1).width(), DELTA);
        assertEquals(490f, at(s, PORTRAIT, 2).width(), DELTA);
        assertEquals(510f, at(s, PORTRAIT, 2).left - s.scroll + 0f, DELTA);
        assertEquals(1000f, at(s, PORTRAIT, 2).right - s.scroll, DELTA);
    }

    @Test
    public void thirdsPreset_threeColumnsFillTheLandscapeViewport() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, LANDSCAPE, 1, 2, 3);
        float third = 1f / 3f;
        for (int id = 1; id <= 3; id++) s = ScrollingLayoutPolicy.setWidthPreset(s, LANDSCAPE, id, third);
        float w = (2000f - 40f) / 3f;
        assertEquals(w, at(s, LANDSCAPE, 2).width(), DELTA);
        assertEquals(w + 20f, at(s, LANDSCAPE, 2).left, DELTA);
        assertEquals(2000f, at(s, LANDSCAPE, 3).right, DELTA);
    }

    @Test
    public void setWidthPreset_ignoresAFractionThatIsNotAPresetForTheOrientation() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1);
        assertSame(s, ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 1f / 3f));
    }

    @Test
    public void setWidthPreset_keepsTheColumnWhole() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 2, 0.5f);
        ScrollingLayoutPolicy.Placement p = at(s, PORTRAIT, 2);
        assertTrue(p.left >= s.scroll - DELTA);
        assertTrue(p.right <= s.scroll + 1000f + DELTA);
    }

    // -- resize and snap ----------------------------------------------------------------------

    @Test
    public void resizeFromGap_isFreeAndMovesTheColumnToTheLeftOfTheGap() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 2, 0.5f);
        s = ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 0, 123f);
        assertEquals(490f + 123f, at(s, PORTRAIT, 1).width(), DELTA);
        assertEquals(490f, at(s, PORTRAIT, 2).width(), DELTA);
        assertEquals(at(s, PORTRAIT, 1).right + 20f, at(s, PORTRAIT, 2).left, DELTA);
    }

    @Test
    public void resizeFromGap_clampsToTheMinimumAndTheViewport() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        ScrollingLayoutPolicy.State wide = ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 0, 5000f);
        assertEquals(1000f, at(wide, PORTRAIT, 1).width(), DELTA);
        ScrollingLayoutPolicy.State narrow = ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 0, -5000f);
        float min = ScrollingLayoutPolicy.MIN_WIDTH_FRACTION * (1000f + 20f) - 20f;
        assertEquals(min, at(narrow, PORTRAIT, 1).width(), DELTA);
    }

    @Test
    public void resizeFromGap_ignoresAGapThatDoesNotExist() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        assertSame(s, ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 1, 50f));
        assertSame(s, ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, -1, 50f));
    }

    @Test
    public void doubleTapGap_snapsTheColumnToTheNearestPreset() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        // 490 + 100 = 590 wide: nearer half (490) than full (1000)
        ScrollingLayoutPolicy.State free = ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 0, 100f);
        assertEquals(490f, at(ScrollingLayoutPolicy.doubleTapGap(free, PORTRAIT, 0), PORTRAIT, 1).width(), DELTA);
        // 490 + 400 = 890: nearer full
        free = ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 0, 400f);
        assertEquals(1000f, at(ScrollingLayoutPolicy.doubleTapGap(free, PORTRAIT, 0), PORTRAIT, 1).width(), DELTA);
    }

    @Test
    public void doubleTapGap_usesThirdsInLandscapeOnly() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, LANDSCAPE, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, LANDSCAPE, 1, 0.5f);
        // 980 wide, shrink by 300 = 680: a third is 653.3, a half is 990
        ScrollingLayoutPolicy.State free = ScrollingLayoutPolicy.resizeFromGap(s, LANDSCAPE, 0, -300f);
        float third = (2000f - 40f) / 3f;
        assertEquals(third, at(ScrollingLayoutPolicy.doubleTapGap(free, LANDSCAPE, 0), LANDSCAPE, 1).width(), DELTA);
        // the same width in portrait terms has no thirds: nearest of full/half
        ScrollingLayoutPolicy.State p = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        p = ScrollingLayoutPolicy.setWidthPreset(p, PORTRAIT, 1, 0.5f);
        p = ScrollingLayoutPolicy.resizeFromGap(p, PORTRAIT, 0, -200f);
        assertEquals(490f, at(ScrollingLayoutPolicy.doubleTapGap(p, PORTRAIT, 0), PORTRAIT, 1).width(), DELTA);
    }

    // -- stacking -----------------------------------------------------------------------------

    @Test
    public void stackInto_leftJoinsTheLeftColumnUnderItsWindow() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        assertEquals(1, s.columnCount());
        ScrollingLayoutPolicy.Placement top = at(s, PORTRAIT, 1);
        ScrollingLayoutPolicy.Placement bottom = at(s, PORTRAIT, 2);
        assertEquals(0f, top.top, DELTA);
        assertEquals(990f, top.bottom, DELTA);
        assertEquals(1010f, bottom.top, DELTA);
        assertEquals(2000f, bottom.bottom, DELTA);
        assertEquals(top.left, bottom.left, DELTA);
        assertEquals(top.right, bottom.right, DELTA);
        assertEquals(2, s.focusedWindow);
    }

    @Test
    public void stackInto_rightJoinsTheRightColumnOverItsWindow() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 1, ScrollingLayoutPolicy.RIGHT);
        assertEquals(1, s.columnCount());
        assertTrue(at(s, PORTRAIT, 1).bottom <= at(s, PORTRAIT, 2).top);
    }

    @Test
    public void stackInto_isRefusedAtTheEdgeFromAStackAndIntoAFullStack() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2, 3);
        assertSame(s, ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 1, ScrollingLayoutPolicy.LEFT));
        assertSame(s, ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 3, ScrollingLayoutPolicy.RIGHT));
        ScrollingLayoutPolicy.State stacked = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        // 2 is in a two-stack now: it cannot be stacked again, and 3 cannot join a full stack
        assertSame(stacked, ScrollingLayoutPolicy.stackInto(stacked, PORTRAIT, 2, ScrollingLayoutPolicy.RIGHT));
        assertSame(stacked, ScrollingLayoutPolicy.stackInto(stacked, PORTRAIT, 3, ScrollingLayoutPolicy.LEFT));
    }

    @Test
    public void unstack_expelsTheWindowIntoItsOwnColumnOnTheChosenSide() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        ScrollingLayoutPolicy.State right = ScrollingLayoutPolicy.unstack(s, PORTRAIT, 2, ScrollingLayoutPolicy.RIGHT);
        assertEquals(2, right.columnCount());
        assertEquals(Arrays.toString(new long[] {1, 2}), Arrays.toString(order(right)));
        assertEquals(2000f, at(right, PORTRAIT, 2).height(), DELTA);
        ScrollingLayoutPolicy.State left = ScrollingLayoutPolicy.unstack(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        assertEquals(Arrays.toString(new long[] {2, 1}), Arrays.toString(order(left)));
    }

    @Test
    public void unstack_keepsTheStackWidthAndIsRefusedForASoleWindow() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        assertSame(s, ScrollingLayoutPolicy.unstack(s, PORTRAIT, 1, ScrollingLayoutPolicy.RIGHT));
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        s = ScrollingLayoutPolicy.unstack(s, PORTRAIT, 2, ScrollingLayoutPolicy.RIGHT);
        assertEquals(490f, at(s, PORTRAIT, 2).width(), DELTA);
    }

    // -- focus --------------------------------------------------------------------------------

    @Test
    public void focusWindow_scrollsTheLeastDistanceThatShowsTheColumnWhole() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, LANDSCAPE, 1, 2, 3, 4);
        float third = 1f / 3f;
        for (int id = 1; id <= 4; id++) s = ScrollingLayoutPolicy.setWidthPreset(s, LANDSCAPE, id, third);
        float w = (2000f - 40f) / 3f;
        // columns at 0, w+20, 2(w+20), 3(w+20); focused 4 shows columns 2..4
        s = ScrollingLayoutPolicy.focusWindow(s, LANDSCAPE, 4);
        assertEquals(3 * (w + 20f) + w - 2000f, s.scroll, DELTA);
        // focusing 3, already whole: no movement
        float before = s.scroll;
        s = ScrollingLayoutPolicy.focusWindow(s, LANDSCAPE, 3);
        assertEquals(before, s.scroll, DELTA);
        // focusing 1: scroll back to its left edge, no further
        s = ScrollingLayoutPolicy.focusWindow(s, LANDSCAPE, 1);
        assertEquals(0f, s.scroll, DELTA);
        // focusing 2 from 1: only its right edge needs to show, strip fits so scroll stays 0
        s = ScrollingLayoutPolicy.focusWindow(s, LANDSCAPE, 2);
        assertEquals(0f, s.scroll, DELTA);
    }

    @Test
    public void focusWindow_unknownWindowChangesNothing() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1);
        assertSame(s, ScrollingLayoutPolicy.focusWindow(s, PORTRAIT, 99));
    }

    // -- close --------------------------------------------------------------------------------

    @Test
    public void close_focusMovesToTheNextColumnOrThePreviousWhenLast() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2, 3);
        s = ScrollingLayoutPolicy.focusWindow(s, PORTRAIT, 2);
        ScrollingLayoutPolicy.State closed = ScrollingLayoutPolicy.close(s, PORTRAIT, 2);
        assertEquals(3, closed.focusedWindow);
        assertEquals(Arrays.toString(new long[] {1, 3}), Arrays.toString(order(closed)));
        closed = ScrollingLayoutPolicy.close(closed, PORTRAIT, 3);
        assertEquals(1, closed.focusedWindow);
    }

    @Test
    public void close_ofAnUnfocusedWindowKeepsTheFocus() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2, 3);
        ScrollingLayoutPolicy.State closed = ScrollingLayoutPolicy.close(s, PORTRAIT, 1);
        assertEquals(3, closed.focusedWindow);
        assertEquals(Arrays.toString(new long[] {2, 3}), Arrays.toString(order(closed)));
    }

    @Test
    public void close_oneOfAStackLeavesTheOtherFillingTheColumnAndFocused() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        s = ScrollingLayoutPolicy.close(s, PORTRAIT, 2);
        assertEquals(1, s.focusedWindow);
        assertEquals(2000f, at(s, PORTRAIT, 1).height(), DELTA);
    }

    @Test
    public void close_lastWindowGivesTheEmptyState() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1);
        s = ScrollingLayoutPolicy.close(s, PORTRAIT, 1);
        assertEquals(0, s.columnCount());
        assertEquals(ScrollingLayoutPolicy.NO_WINDOW, s.focusedWindow);
        assertEquals(0f, s.scroll, 0f);
    }

    @Test
    public void close_pullsTheScrollBackWhenTheStripGetsShorter() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2, 3);
        assertEquals(510f, s.scroll, DELTA);
        s = ScrollingLayoutPolicy.close(s, PORTRAIT, 3);
        // Updated for round 2: two half columns left, focus on 2: the strip is the viewport, no scroll.
        assertEquals(0f, s.scroll, DELTA);
    }

    @Test
    public void close_leavesALoneWindowFullWidth() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.close(s, PORTRAIT, 2);
        assertEquals(1000f, at(s, PORTRAIT, 1).width(), DELTA);
    }

    // -- rotation and the whole layout ---------------------------------------------------------

    @Test
    public void relayout_toAnotherViewportKeepsFractionsAndClampsScroll() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 2, 0.5f);
        ScrollingLayoutPolicy.State rotated = ScrollingLayoutPolicy.relayout(s, LANDSCAPE);
        assertEquals(0f, rotated.scroll, DELTA);
        assertEquals((2000f - 20f) / 2f, at(rotated, LANDSCAPE, 1).width(), DELTA);
    }

    @Test
    public void relayout_dropsAThirdWhenTheViewportTurnsPortrait() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, LANDSCAPE, 1);
        s = ScrollingLayoutPolicy.setWidthPreset(s, LANDSCAPE, 1, 1f / 3f);
        ScrollingLayoutPolicy.State portrait = ScrollingLayoutPolicy.relayout(s, PORTRAIT);
        assertEquals(490f, at(portrait, PORTRAIT, 1).width(), DELTA);
    }

    @Test
    public void layout_listsEveryWindowAndTheStripWidth() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2, 3);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 3, ScrollingLayoutPolicy.LEFT);
        ScrollingLayoutPolicy.Layout l = ScrollingLayoutPolicy.layout(s, PORTRAIT);
        assertEquals(3, l.placements.size());
        // Updated for round 2: two half columns (490 + 20 + 490).
        assertEquals(1000f, l.stripWidth, DELTA);
    }

    @Test
    public void emptyState_hasNoPlacementsAndZeroStrip() {
        ScrollingLayoutPolicy.Layout l = ScrollingLayoutPolicy.layout(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT);
        assertEquals(0, l.placements.size());
        assertEquals(0f, l.stripWidth, 0f);
    }

    @Test
    public void resizeFromGap_keepsTheScrollSoTheGapFollowsTheFinger() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 2, 0.5f);
        s = ScrollingLayoutPolicy.focusWindow(s, PORTRAIT, 2);
        float gapBefore = at(s, PORTRAIT, 1).right - s.scroll;
        ScrollingLayoutPolicy.State dragged = ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 0, 123f);
        assertEquals(s.scroll, dragged.scroll, DELTA);
        assertEquals(gapBefore + 123f, at(dragged, PORTRAIT, 1).right - dragged.scroll, DELTA);
    }

    @Test
    public void relayout_keepsADraggedWidthThatEqualsAThird() {
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, PORTRAIT, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, 1, 0.5f);
        // 490 + (-170) = 320 px: fraction (320 + 20) / 1020 = 1/3, but a drag made it
        s = ScrollingLayoutPolicy.resizeFromGap(s, PORTRAIT, 0, -170f);
        ScrollingLayoutPolicy.State same = ScrollingLayoutPolicy.relayout(s, PORTRAIT);
        assertEquals(320f, at(same, PORTRAIT, 1).width(), DELTA);
    }

    @Test
    public void layout_withAViewportSmallerThanItsGapHasNoInvertedRectangles() {
        ScrollingLayoutPolicy.Viewport tiny = new ScrollingLayoutPolicy.Viewport(10f, 10f, 20f, false);
        ScrollingLayoutPolicy.State s = open(ScrollingLayoutPolicy.State.EMPTY, tiny, 1, 2);
        s = ScrollingLayoutPolicy.setWidthPreset(s, tiny, 2, 0.5f);
        s = ScrollingLayoutPolicy.stackInto(s, tiny, 2, ScrollingLayoutPolicy.LEFT);
        for (ScrollingLayoutPolicy.Placement p : ScrollingLayoutPolicy.layout(s, tiny).placements) {
            assertTrue(p.width() >= 0f);
            assertTrue(p.height() >= 0f);
        }
    }

    @Test
    public void state_isNotChangedByACallerReusingItsIdArray() {
        long[] ids = {1};
        ScrollingLayoutPolicy.Column c = new ScrollingLayoutPolicy.Column(ids, 1f, true);
        ids[0] = 99;
        assertEquals(1, c.windowAt(0));
    }
}
