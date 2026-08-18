package com.termux.app.launcher.widget;

import android.graphics.Rect;

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
}
