package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuiltinWidgetSpanTest {
    @Test public void exactSpansMapToThemselves() {
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forCells(1, 1));
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forCells(2, 1));
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forCells(2, 2));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forCells(4, 1));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forCells(4, 2));
    }

    @Test public void inBetweenSpansSnapToTheLargestBucketTheyContain() {
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forCells(3, 1));
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forCells(3, 3));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forCells(5, 4));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forCells(6, 1));
    }

    @Test public void aSingleColumnIsAlwaysTheSmallestBucket() {
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forCells(1, 3));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forCells(0, 0));
    }

    @Test public void everyKindHasADurableIdThatRoundTrips() {
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            assertEquals(kind, BuiltinWidgetKind.fromId(kind.id));
        }
        assertEquals(null, BuiltinWidgetKind.fromId("no.such.widget"));
        assertEquals(null, BuiltinWidgetKind.fromId(null));
    }

    @Test public void roomAlonePromotesAtTheDesign() {
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(88, 92));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(40, 40));
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(184, 92));
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forSize(184, 192));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forSize(376, 92));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(376, 192));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(1000, 1000));
        // Just under the design is still the smaller bucket when the cells are not known.
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(150, 126));
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(300, 150));
    }

    @Test public void aSpanKeepsItsBucketDownToAndroidsMinimum() {
        // A 2x1 on the default phone grid (183x126 dp) is a 2x1, not a 2x2: one big row.
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(183, 126, 2, 1));
        // The same room spanning two rows of a dense grid is a 2x2.
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forSize(183, 126, 2, 2));
        // A 2x1 on a six-column grid (119 dp) holds; on an eight-column one (90 dp) it is a 1x1.
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(119, 126, 2, 1));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(90, 126, 2, 1));
        // A 4x1 holds from 245 dp; a 4x2 from 245x115.
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forSize(246, 126, 4, 1));
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(244, 126, 4, 1));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(245, 115, 4, 2));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forSize(245, 114, 4, 2));
        // Below one row's minimum there is only the 1x1.
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(300, 55, 4, 1));
    }

    @Test public void moreRoomThanTheSpanPromotesAtTheDesign() {
        // A 2x1 on a two-column grid has the whole width: it draws the 4x1.
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forSize(386, 126, 2, 1));
        // A 3x1 on the default grid (278 dp) stays a 2x1: under the 4x1's design.
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(278, 126, 3, 1));
        // A 1x2 has no bucket of its own: a column alone is a 1x1 however tall.
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(87, 260, 1, 2));
    }

    @Test public void designedSizesFollowTheReferenceGrid() {
        assertEquals(88, BuiltinWidgetSpan.ONE_BY_ONE.widthDp);
        assertEquals(184, BuiltinWidgetSpan.TWO_BY_ONE.widthDp);
        assertEquals(192, BuiltinWidgetSpan.TWO_BY_TWO.heightDp);
        assertEquals(376, BuiltinWidgetSpan.FOUR_BY_TWO.widthDp);
    }
}
