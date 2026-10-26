package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** An app icon in its Home cell: the dock's 48 dp with room, smaller without, the label last. */
public class HomeAppIconMetricsTest {
    private static final float DENSITY = 2f;
    private static final int LABEL = 26;

    @Test public void aRoomyCellDrawsTheDockSizeWithTheLabel() {
        HomeAppIconMetrics metrics = HomeAppIconMetrics.of(300, 300, LABEL, DENSITY);
        assertEquals(96, metrics.iconPx);
        assertTrue(metrics.showLabel);
    }

    @Test public void aNarrowCellShrinksTheIconToItsWidth() {
        // 80 px across less two 8 px insets.
        HomeAppIconMetrics metrics = HomeAppIconMetrics.of(80, 300, LABEL, DENSITY);
        assertEquals(64, metrics.iconPx);
        assertTrue(metrics.showLabel);
    }

    @Test public void aShortCellShrinksTheIconAboveTheLabel() {
        // 120 tall: 16 of insets, 26 of label, 8 of gap leave 70.
        HomeAppIconMetrics metrics = HomeAppIconMetrics.of(300, 120, LABEL, DENSITY);
        assertEquals(70, metrics.iconPx);
        assertTrue(metrics.showLabel);
    }

    @Test public void aCellTooShortForBothGivesTheLabelsLineToTheIcon() {
        // With the label the icon would be 40 px, under the 48 px floor; bare it gets 74.
        HomeAppIconMetrics metrics = HomeAppIconMetrics.of(300, 90, LABEL, DENSITY);
        assertFalse(metrics.showLabel);
        assertEquals(74, metrics.iconPx);
    }

    @Test public void theIconNeverCollapsesToNothing() {
        HomeAppIconMetrics metrics = HomeAppIconMetrics.of(4, 4, LABEL, DENSITY);
        assertFalse(metrics.showLabel);
        assertEquals(1, metrics.iconPx);
    }
}
