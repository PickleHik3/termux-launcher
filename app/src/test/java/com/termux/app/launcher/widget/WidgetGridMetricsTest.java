package com.termux.app.launcher.widget;

import android.graphics.Rect;

import com.termux.app.chrome.ChromePolicy;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockSurfaceSpec;
import com.termux.app.place.KeyboardOverlayPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.app.Application;
import android.os.Build;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetGridMetricsTest {
    @Test public void allCellsStayInsideBodyAndRemainderReachesFinalEdge() {
        WidgetGridMetrics metrics = new WidgetGridMetrics(new Rect(10, 20, 413, 627),
            48, 7, 3, new WidgetGridDefinition(6, 4), false);
        Rect content = metrics.contentBounds();
        Rect first = metrics.boundsFor(new WidgetCellRect(0, 0, 1, 1));
        Rect last = metrics.boundsFor(new WidgetCellRect(3, 5, 4, 6));
        assertTrue(content.contains(first)); assertTrue(content.contains(last));
        assertEquals(content.right, last.right); assertEquals(content.bottom, last.bottom);
    }

    /**
     * Always on takes two rows of a five-row grid: the grid's cells keep the height they had and
     * the grid ends two rows sooner, so a widget keeps its size instead of stretching over the
     * body (devcheck: a 3-row clock grew from 805 to 1363 px).
     */
    @Test public void reservedRowsKeepTheCellsTheirHeight() {
        Rect body = new Rect(0, 0, 1080, 2200);
        WidgetGridMetrics full = new WidgetGridMetrics(body, 0, 16, 21,
            new WidgetGridDefinition(5, 4), false);
        WidgetGridMetrics shrunk = new WidgetGridMetrics(body, 0, 16, 21,
            new WidgetGridDefinition(3, 4), false, 2);
        WidgetCellRect clock = new WidgetCellRect(0, 0, 2, 3);
        assertEquals(full.boundsFor(clock), shrunk.boundsFor(clock));
        assertEquals(full.boundsFor(new WidgetCellRect(3, 2, 4, 3)),
            shrunk.boundsFor(new WidgetCellRect(3, 2, 4, 3)));
        assertTrue("the shrunk grid ends above the body's bottom",
            shrunk.boundsFor(new WidgetCellRect(0, 2, 1, 3)).bottom
                < full.boundsFor(new WidgetCellRect(0, 4, 1, 5)).bottom);
        WidgetGridMetrics stretched = new WidgetGridMetrics(body, 0, 16, 21,
            new WidgetGridDefinition(3, 4), false);
        assertTrue("without the reserve the same rows stretch",
            stretched.boundsFor(clock).height() > full.boundsFor(clock).height());
        assertNotEquals(stretched, shrunk);
    }

    @Test public void adjacentCellsDoNotSharePaintedPixels() {
        WidgetGridMetrics metrics = new WidgetGridMetrics(new Rect(0, 0, 401, 601),
            0, 0, 3, new WidgetGridDefinition(6, 4), false);
        Rect a = metrics.boundsFor(new WidgetCellRect(0, 0, 1, 1));
        Rect b = metrics.boundsFor(new WidgetCellRect(1, 0, 2, 1));
        assertEquals(3, b.left - a.right);
    }

    @Test public void rtlMirrorsPixelsWithoutChangingPersistedCoordinates() {
        WidgetCellRect cell = new WidgetCellRect(0, 0, 1, 1);
        WidgetGridDefinition grid = new WidgetGridDefinition(6, 4);
        Rect ltr = new WidgetGridMetrics(new Rect(0, 0, 400, 600), 0, 0, 0, grid, false)
            .boundsFor(cell);
        Rect rtl = new WidgetGridMetrics(new Rect(0, 0, 400, 600), 0, 0, 0, grid, true)
            .boundsFor(cell);
        assertEquals(0, ltr.left); assertEquals(400, rtl.right); assertEquals(0, cell.left);
    }

    @Test public void spanLabelsComeFromActualPixelRectangles() {
        WidgetGridMetrics metrics = new WidgetGridMetrics(new Rect(0, 0, 403, 607),
            0, 4, 3, new WidgetGridDefinition(6, 4), false);
        int oneWidth = metrics.boundsFor(new WidgetCellRect(0, 0, 1, 1)).width();
        WidgetGridMetrics.Span one = metrics.spanForPixels(oneWidth, 1);
        WidgetGridMetrics.Span two = metrics.spanForPixels(oneWidth + 1, 1);
        assertEquals(1, one.columns); assertEquals(2, two.columns); assertTrue(two.fits);
        assertFalse(metrics.spanForPixels(10000, 1).fits);
    }

    @Test public void largestSpanWithinReadsTheSamePixelRectanglesAsTheMinimum() {
        WidgetGridMetrics metrics = new WidgetGridMetrics(new Rect(0, 0, 403, 607),
            0, 4, 3, new WidgetGridDefinition(6, 4), false);
        int oneWidth = metrics.boundsFor(new WidgetCellRect(0, 0, 1, 1)).width();
        int twoWidth = metrics.boundsFor(new WidgetCellRect(0, 0, 2, 1)).width();
        assertEquals(2, metrics.largestSpanWithin(twoWidth, 0).columns);
        assertEquals(1, metrics.largestSpanWithin(twoWidth - 1, 0).columns);
        assertEquals(0, metrics.largestSpanWithin(oneWidth - 1, 0).columns);
        assertEquals("unset is no limit", 0, metrics.largestSpanWithin(0, 0).columns);
        assertEquals(4, metrics.largestSpanWithin(100000, 0).columns);
        assertEquals(6, metrics.largestSpanWithin(0, 100000).rows);
    }

    /**
     * Home's Margin is the rim: a 360 x 640 dp page at 3 px/dp, a five-row, four-column grid with
     * the fixed 8 dp gap, at no Margin, the seeded 12 dp and the 48 dp maximum. The first cell
     * starts a rim in, the last ends a rim short, and the cells give up what the rim takes.
     */
    @Test public void homesMarginIsTheRimRoundTheCells() {
        Rect body = new Rect(0, 0, 1080, 1920);
        WidgetGridDefinition grid = new WidgetGridDefinition(5, 4);
        int gap = Math.round(WidgetGridMetrics.GAP_DP * 3f);
        int previousWidth = Integer.MAX_VALUE;
        for (int marginDp : new int[] {0, 12, 48}) {
            int rim = marginDp * 3;
            WidgetGridMetrics metrics = new WidgetGridMetrics(body, 0, rim, gap, grid, false);
            assertEquals(new Rect(rim, rim, 1080 - rim, 1920 - rim), metrics.contentBounds());
            Rect first = metrics.boundsFor(new WidgetCellRect(0, 0, 1, 1));
            Rect last = metrics.boundsFor(new WidgetCellRect(3, 4, 4, 5));
            assertEquals(rim, first.left);
            assertEquals(rim, first.top);
            assertEquals(1080 - rim, last.right);
            assertEquals(1920 - rim, last.bottom);
            assertEquals("the gap between cells stays the grid's own", gap,
                metrics.boundsFor(new WidgetCellRect(1, 0, 2, 1)).left - first.right);
            assertTrue("a wider rim leaves narrower cells at " + marginDp + " dp",
                first.width() < previousWidth);
            previousWidth = first.width();
        }
        assertNotEquals("a rim change misses the catalog cache",
            new WidgetGridMetrics(body, 0, 36, gap, grid, false),
            new WidgetGridMetrics(body, 0, 144, gap, grid, false));
    }

    @Test public void aMaximumUnderTheMinimumOrUnsetIsNoCap() {
        assertEquals(6, WidgetGridMetrics.effectiveMaxSpan(0, 1, 6));
        assertEquals(6, WidgetGridMetrics.effectiveMaxSpan(1, 2, 6));
        assertEquals(3, WidgetGridMetrics.effectiveMaxSpan(3, 2, 6));
        assertEquals(6, WidgetGridMetrics.effectiveMaxSpan(9, 2, 6));
    }

    /**
     * #104: on Home the content reaches under the whole accessory stack, so the dock floats over
     * the grid; the emulator showed row 5 of a 5-row grid behind it. For every dock design (and
     * the plain dock), with the Strip's row fully in, edge to edge and as a capsule, the grid's
     * last row ends at or above the dock's top, as the dock's own layout reports its height. On
     * the Workspace half the content already ends at the stack's top and nothing is reserved; the
     * last row stands in the same place on both halves, so a slide across the seam re-cuts nothing.
     */
    @Test public void theLastRowEndsAboveTheDock_forEveryDockDesign_onBothHalves() {
        float density = 2.75f;
        int width = 1080, height = 2400, toolbarPx = 103;
        int rim = Math.round(16 * density);
        int gap = Math.round(WidgetGridMetrics.GAP_DP * density);
        WidgetGridDefinition grid = new WidgetGridDefinition(5, 4);
        WidgetCellRect lastRow = new WidgetCellRect(0, 4, 2, 5);
        DockSurfaceSpec[] designs = {DockSurfaceSpec.PLAIN, DockSurfaceSpec.GLASS_TRAY,
            DockSurfaceSpec.LENS_BEADS, DockSurfaceSpec.WALL_STRIP};
        for (DockSurfaceSpec design : designs) {
            for (boolean capsule : new boolean[] {false, true}) {
                for (boolean extraKeys : new boolean[] {false, true}) {
                    DockLayout dock = DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
                        .capsule(capsule).appsRowAlone(false).appsRowNextToCanvas(true)
                        .density(density).barHeightScale(DockLayoutPolicy.sizePreset(0))
                        .configuredCornerRadiusDp(-1).appsRowEnabledPref(true)
                        .azRowEnabledPref(true).baseToolbarHeightPx(toolbarPx)
                        .surface(design).wallStripReveal(1f).build());
                    int dockPx = dock.combinedHeight(toolbarPx, extraKeys);
                    int edgeGap = ChromePolicy.bottomEdgeGapPx(capsule, dock.capsuleBottomGapPx);
                    int dockTop = height - edgeGap - dockPx;
                    String what = design + " capsule=" + capsule + " extraKeys=" + extraKeys;
                    assertTrue(what + ": the dock has a height", dockPx > 0);

                    int homeReserve = KeyboardOverlayPolicy.homeGridDockReservePx(true, dockPx,
                        edgeGap);
                    Rect homeBody = new Rect(0, 0, width,
                        height - WidgetPaneView.gridBottomInsetPx(homeReserve, density));
                    Rect home = new WidgetGridMetrics(homeBody, 0, rim, gap, grid, false)
                        .boundsFor(lastRow);
                    assertTrue(what + ": Home's last row ends at " + home.bottom
                        + ", the dock's top is " + dockTop, home.bottom <= dockTop);

                    int workspaceReserve = KeyboardOverlayPolicy.homeGridDockReservePx(false,
                        dockPx, edgeGap);
                    assertEquals(what, 0, workspaceReserve);
                    Rect workspaceBody = new Rect(0, 0, width,
                        dockTop - WidgetPaneView.gridBottomInsetPx(workspaceReserve, density));
                    Rect workspace = new WidgetGridMetrics(workspaceBody, 0, rim, gap, grid,
                        false).boundsFor(lastRow);
                    assertTrue(what, workspace.bottom <= dockTop);
                    assertEquals(what + ": the same cell on both halves", home, workspace);
                }
            }
        }
    }

    /** With no dock rows on screen nothing is reserved, so a hidden dock gives the rows back. */
    @Test public void noDockReservesNothing() {
        assertEquals(0, KeyboardOverlayPolicy.homeGridDockReservePx(true, 0, 30));
        assertEquals(0, KeyboardOverlayPolicy.homeGridDockReservePx(false, 300, 30));
        assertEquals(330, KeyboardOverlayPolicy.homeGridDockReservePx(true, 300, 30));
    }
}
