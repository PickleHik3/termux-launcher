package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The shared preview contract: the miniature's visible top stands on one line under the page bar
 * on every page, the sheet reserve makes it one size on every page, and the band above the
 * container wraps the element at the top of the screen with the air it has beside it.
 */
public class AppearancePreviewAreaTest {

    /** A Nothing Phone (2): 411x918dp at 2.625, a 41dp status bar with its cutout, gesture nav. */
    private static final float DENSITY = 2.625f;
    private static final int WINDOW_H = 2410;
    private static final int STATUS_PX = 108;
    private static final int NAV_PX = 42;
    private static final int CONTAINER_TOP = STATUS_PX;
    private static final int CONTAINER_H = WINDOW_H - STATUS_PX - NAV_PX;
    private static final int CONTAINER_W = 1080;
    private static final float DISPLAY_RADIUS = 105f;

    @Test
    public void thePreviewStandsTheGapUnderTheBar() {
        int top = AppearancePreviewArea.topPx(STATUS_PX, DENSITY);
        assertEquals(STATUS_PX + Math.round(72 * DENSITY), top);
        for (int reveal : new int[] {0, 21, STATUS_PX}) {
            int sheet = Math.round(160 * DENSITY);
            float scale = AppearancePreviewArea.scale(CONTAINER_H, reveal, NAV_PX, top,
                AppearancePreviewArea.bottomPx(WINDOW_H, sheet, DENSITY));
            float ty = AppearancePreviewArea.translationY(top, CONTAINER_TOP, reveal, scale);
            // The clip's top is the shown band above the container's scaled top: on the line.
            float visibleTop = CONTAINER_TOP + ty - reveal * scale;
            assertEquals("reveal " + reveal, top, visibleTop, 0.5f);
            float visibleBottom = CONTAINER_TOP + ty + (CONTAINER_H + NAV_PX) * scale;
            assertTrue("reveal " + reveal + " clears the sheet's gap",
                visibleBottom <= WINDOW_H - sheet - Math.round(8 * DENSITY) + 0.5f);
        }
    }

    @Test
    public void everyPageAndStopGetsTheSameRectCustomIncluded() {
        int look = Math.round(80 * DENSITY);
        int layout = Math.round(136 * DENSITY);
        int icons = Math.round(184 * DENSITY);
        int custom = Math.round(312 * DENSITY);
        int top = AppearancePreviewArea.topPx(STATUS_PX, DENSITY);
        int cap = AppearancePreviewArea.sheetCapPx(WINDOW_H, top, CONTAINER_H, 0, NAV_PX, DENSITY);
        int reserve = AppearancePreviewArea.reservePx(Math.max(look, Math.max(layout, icons)),
            custom, cap);
        // The phone has the room: the reserve is the Custom row's own height.
        assertEquals(custom, reserve);
        float rest = -1f;
        for (int page : new int[] {look, layout, icons, custom}) {
            int sheet = AppearancePreviewArea.sheetPx(reserve, page);
            assertEquals(reserve, sheet);
            float scale = AppearancePreviewArea.scale(CONTAINER_H, 0, NAV_PX, top,
                AppearancePreviewArea.bottomPx(WINDOW_H, sheet, DENSITY));
            if (rest < 0f) rest = scale;
            assertEquals(rest, scale, 0f);
        }
        assertTrue(rest > AppearanceEditorFrame.MIN_SCALE);
    }

    @Test
    public void onAShortPhoneTheReserveStopsWhereThePreviewWouldTurnUnreadable() {
        int window = 780;
        int top = AppearancePreviewArea.topPx(24, 1f);
        int container = window - 24;
        int cap = AppearancePreviewArea.sheetCapPx(window, top, container, 0, 0, 1f);
        int reserve = AppearancePreviewArea.reservePx(160, 340, cap);
        assertEquals("the Custom row scrolls inside the cap", cap, reserve);
        float scale = AppearancePreviewArea.scale(container, 0, 0, top,
            AppearancePreviewArea.bottomPx(window, reserve, 1f));
        assertEquals(AppearanceEditorFrame.MIN_SCALE, scale, 0.002f);
        // The other pages never scroll: their tallest is the floor, cap or not.
        assertEquals(200, AppearancePreviewArea.reservePx(200, 340, 150));
    }

    @Test
    public void theBarAndTheContentAgreeOnThePageInset() {
        assertEquals(108, AppearancePreviewArea.pageInsetTopPx(108, 0));
        // A content view that already starts below part of the bar keeps only the rest.
        assertEquals(8, AppearancePreviewArea.pageInsetTopPx(108, 100));
        assertEquals(0, AppearancePreviewArea.pageInsetTopPx(108, 200));
    }

    @Test
    public void aFloatingCardWithEqualAirNeedsNoBand() {
        int gap = Math.round(12 * DENSITY);
        int[] card = {gap, gap, CONTAINER_W - gap};
        // A rounded card well inside the display's arc: the air above it is already its own.
        assertEquals(0, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, STATUS_PX, 0,
            DISPLAY_RADIUS, card, 26 * DENSITY));
    }

    @Test
    public void aCardFlushWithTheContainerTopGetsItsSideAirAbove() {
        int gap = Math.round(12 * DENSITY);
        int[] card = {gap, 0, CONTAINER_W - gap};
        int reveal = AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, STATUS_PX, 0,
            DISPLAY_RADIUS, card, 26 * DENSITY);
        assertTrue("at least the side's air: " + reveal, reveal >= gap);
        assertTrue("never the whole band: " + reveal, reveal < STATUS_PX);
    }

    @Test
    public void aSquareFlushBarShowsTheBandTheArcNeedsToCutNothing() {
        int[] docked = {0, 0, CONTAINER_W};
        // Its corner sits at the arc's foot: the arc stands wholly in the band above it.
        assertEquals((int) Math.ceil(DISPLAY_RADIUS), AppearancePreviewArea.topRevealPx(
            CONTAINER_W, 0, STATUS_PX, 0, DISPLAY_RADIUS, docked, 0f));
        // And never more than the display has above the container.
        assertEquals(60, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, 60, 0,
            DISPLAY_RADIUS, docked, 0f));
    }

    @Test
    public void nothingToWrapKeepsTheDisplaysOwnTop() {
        assertEquals(STATUS_PX, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, STATUS_PX, 0,
            DISPLAY_RADIUS, null, 0f));
        // And no band where the display has none.
        assertEquals(0, AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, 0, 0,
            DISPLAY_RADIUS, new int[] {0, 0, CONTAINER_W}, 0f));
    }

    @Test
    public void theArcHoldsTheCornerItWasCleared() {
        int gap = Math.round(4 * DENSITY);
        float radius = 8 * DENSITY;
        int[] card = {gap, 0, CONTAINER_W - gap};
        int reveal = AppearancePreviewArea.topRevealPx(CONTAINER_W, 0, 500, 0, DISPLAY_RADIUS,
            card, radius);
        // Every point of the card's corner arc is inside the display's arc at that reveal.
        float cx = DISPLAY_RADIUS;
        float cy = DISPLAY_RADIUS - reveal;
        for (int i = 0; i <= 32; i++) {
            double a = Math.PI / 2 * i / 32;
            double x = gap + radius - radius * Math.cos(a);
            double y = radius - radius * Math.sin(a);
            if (x >= cx || y >= cy) continue;
            assertTrue("point " + i, Math.hypot(cx - x, cy - y) <= DISPLAY_RADIUS + 0.75);
        }
    }
    // ---- a tablet in landscape: the preview left, the sheet a side pane right ----------------

    /**
     * Places the side arrangement on a tablet {@code widthPx} x {@code heightPx} at {@code density}
     * (the content edge to edge, the status bar above the container and the navigation bar under
     * it) and checks the rect contract: the pane against the right edge, {@link
     * AppearancePreviewArea#SIDE_PANE_DP} wide plus the right inset, from the preview's top line
     * to the foot; the miniature on that same top line, above the navigation bar's gap, inside
     * its column and centred there; the two never overlap.
     *
     * @return the scale the miniature took
     */
    private static float assertSideArrangement(int widthPx, int heightPx, float density,
                                               int statusPx, int navPx, int leftInsetPx,
                                               int rightInsetPx) {
        int containerLeft = leftInsetPx;
        int containerWidth = widthPx - leftInsetPx - rightInsetPx;
        int containerTop = statusPx;
        int containerHeight = heightPx - statusPx - navPx;
        int top = AppearancePreviewArea.topPx(statusPx, density);
        int bottom = AppearancePreviewArea.bottomPx(heightPx, navPx, density);
        int paneLeft = AppearancePreviewArea.sidePaneLeftPx(widthPx, rightInsetPx, density);
        int paneHeight = AppearancePreviewArea.sidePaneHeightPx(heightPx, top);
        int gap = Math.round(AppearancePreviewArea.GAP_DP * density);

        assertEquals("the pane is its fixed width and the right inset",
            Math.round(AppearancePreviewArea.SIDE_PANE_DP * density) + rightInsetPx,
            widthPx - paneLeft);
        assertEquals("the pane runs from the preview's top line to the foot", heightPx,
            top + paneHeight);
        assertTrue("under the bar", top >= statusPx + Math.round(
            AppearancePreviewArea.BAR_DP * density));

        int[] column = AppearancePreviewArea.sideColumnPx(leftInsetPx, paneLeft, density);
        assertEquals("the gap clear of the left inset", leftInsetPx + gap, column[0]);
        assertEquals("the gap clear of the pane", paneLeft - gap, column[1]);

        float scale = AppearancePreviewArea.sideScale(containerWidth, containerHeight, 0, navPx,
            leftInsetPx, rightInsetPx, top, bottom, column[0], column[1]);
        float tx = AppearancePreviewArea.translationX(column[0], column[1], containerLeft,
            containerWidth, leftInsetPx, rightInsetPx, scale);
        float ty = AppearancePreviewArea.translationY(top, containerTop, 0, scale);

        // The container scales about its top-centre pivot, then translates.
        float pivot = containerLeft + containerWidth / 2f;
        float visibleLeft = pivot + scale * (-leftInsetPx - containerWidth / 2f) + tx;
        float visibleRight = pivot + scale * (containerWidth + rightInsetPx - containerWidth / 2f)
            + tx;
        float visibleTop = containerTop + ty;
        float visibleBottom = containerTop + ty + (containerHeight + navPx) * scale;
        assertEquals("the miniature stands on the top line", top, visibleTop, 0.5f);
        assertTrue("above the navigation bar's gap", visibleBottom <= bottom + 0.5f);
        assertTrue("inside its column: " + visibleLeft + ".." + visibleRight,
            visibleLeft >= column[0] - 0.5f && visibleRight <= column[1] + 0.5f);
        assertEquals("centred in its column", (column[0] + column[1]) / 2f,
            (visibleLeft + visibleRight) / 2f, 0.5f);
        assertTrue("never under the pane", visibleRight < paneLeft);
        assertTrue("never larger than the phone's most",
            scale <= AppearanceEditorFrame.MAX_SCALE);
        return scale;
    }

    @Test
    public void aLandscapeTabletStandsThePreviewBesideTheSidePane() {
        // 1280 x 800 dp at 2.0: a 24dp status bar, a 48dp navigation bar under the container.
        float scale = assertSideArrangement(2560, 1600, 2f, 48, 96, 0, 0);
        assertTrue("the column's width is what holds it here",
            scale < AppearancePreviewArea.scale(1456, 0, 96,
                AppearancePreviewArea.topPx(48, 2f),
                AppearancePreviewArea.bottomPx(1600, 96, 2f)));
    }

    @Test
    public void aNarrowTabletShrinksThePreviewRatherThanRunUnderThePane() {
        // 800 x 600 dp at 1.5, 4:3: the column is narrower than the least scale's miniature.
        float scale = assertSideArrangement(1200, 900, 1.5f, 36, 72, 0, 0);
        assertTrue("below the phone's least scale, never under the pane",
            scale < AppearanceEditorFrame.MIN_SCALE);
    }

    @Test
    public void sideInsetsGoToTheirOwnSide() {
        // A cutout on the left is the column's to stand clear of; a bar on the right, the pane's.
        assertSideArrangement(2560, 1600, 2f, 48, 0, 90, 0);
        assertSideArrangement(2560, 1600, 2f, 48, 0, 0, 96);
    }

    @Test
    public void aPhoneNeverTranslatesAcross() {
        // The bottom sheet's arrangement: no column, so the frame keeps its centre.
        int top = AppearancePreviewArea.topPx(STATUS_PX, DENSITY);
        int bottom = AppearancePreviewArea.bottomPx(WINDOW_H, Math.round(160 * DENSITY), DENSITY);
        float phone = AppearancePreviewArea.scale(CONTAINER_H, 0, NAV_PX, top, bottom);
        float side = AppearancePreviewArea.sideScale(CONTAINER_W, CONTAINER_H, 0, NAV_PX, 0, 0, top,
            bottom, 0, CONTAINER_W);
        assertEquals("a column the container's own width holds the phone's scale", phone, side,
            0f);
        assertEquals(0f, AppearancePreviewArea.translationX(0, CONTAINER_W, 0, CONTAINER_W, 0, 0,
            phone), 0f);
    }
}
