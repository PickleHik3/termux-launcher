package com.termux.app.launcher.widget;

import org.junit.Test;

import static org.junit.Assert.*;

/** The six card shapes, and which span lands on which. */
public class WidgetPickerCardTemplateTest {

    @Test public void aSpanTakesTheSmallestTemplateThatHoldsIt() {
        assertEquals("1x1", WidgetPickerCardTemplate.forSpan(1, 1).toString());
        assertEquals("2x1", WidgetPickerCardTemplate.forSpan(2, 1).toString());
        assertEquals("2x2", WidgetPickerCardTemplate.forSpan(2, 2).toString());
        assertEquals("4x1", WidgetPickerCardTemplate.forSpan(3, 1).toString());
        assertEquals("4x1", WidgetPickerCardTemplate.forSpan(4, 1).toString());
        assertEquals("4x2", WidgetPickerCardTemplate.forSpan(3, 2).toString());
        assertEquals("4x4", WidgetPickerCardTemplate.forSpan(4, 4).toString());
    }

    /** A tall narrow widget has no narrow template to sit in, so it takes the square that holds it. */
    @Test public void aSpanWithNoExactTemplateGrowsRatherThanCrops() {
        assertEquals("2x2", WidgetPickerCardTemplate.forSpan(1, 2).toString());
        assertEquals("4x4", WidgetPickerCardTemplate.forSpan(1, 3).toString());
        assertEquals("4x4", WidgetPickerCardTemplate.forSpan(2, 3).toString());
    }

    /** A widget on a grid larger than the templates clamps to the largest rather than overflowing. */
    @Test public void oversizedAndDegenerateSpansClampToTheLargestTemplate() {
        assertEquals("4x4", WidgetPickerCardTemplate.forSpan(6, 5).toString());
        assertEquals("4x4", WidgetPickerCardTemplate.largest().toString());
        assertEquals("1x1", WidgetPickerCardTemplate.forSpan(0, 0).toString());
        assertEquals("1x1", WidgetPickerCardTemplate.forSpan(-3, -3).toString());
    }

    /** Two widgets on the same template get the same card, which is what keeps the rows level. */
    @Test public void twoSpansOnOneTemplateMeasureTheSame() {
        WidgetPickerCardTemplate a = WidgetPickerCardTemplate.forSpan(3, 2);
        WidgetPickerCardTemplate b = WidgetPickerCardTemplate.forSpan(4, 2);
        assertEquals(a, b);
        assertEquals(a.widthPx(2f), b.widthPx(2f));
        assertEquals(a.heightPx(2f), b.heightPx(2f));
    }

    @Test public void cardPixelsFollowTheCellAndTheDensity() {
        WidgetPickerCardTemplate wide = WidgetPickerCardTemplate.forSpan(4, 1);
        assertEquals(4 * WidgetPickerCardTemplate.CELL_DP * 2, wide.widthPx(2f));
        assertEquals(WidgetPickerCardTemplate.CELL_DP * 2, wide.heightPx(2f));
        assertEquals(wide.widthPx(2f), wide.extentPx(2f));
        assertEquals(1, WidgetPickerCardTemplate.forSpan(1, 1).widthPx(0f));
    }
}
