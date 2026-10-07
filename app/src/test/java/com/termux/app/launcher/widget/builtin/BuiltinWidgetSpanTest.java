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

    @Test public void roomPicksTheLargestDesignThatFits() {
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(88, 92));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(40, 40));
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(184, 92));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(300, 150));
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forSize(184, 192));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forSize(376, 92));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(376, 192));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(1000, 1000));
    }

    @Test public void androidsMinimumsDecideTheBucket() {
        // Handheld ranges from the widget design guide: 2 cells 109 dp, 4 cells 245 dp wide;
        // 1 row 56 dp, 2 rows 115 dp tall.
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(109, 56));
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forSize(109, 115));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forSize(245, 56));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(245, 115));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(108, 300));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(300, 55));
        // A 2x1 cell on the 4x5 phone grid (about 183x126 dp) has the height of a two-row range.
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forSize(183, 126));
    }

    @Test public void aBucketIsNeverChosenBelowItsMinimum() {
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(244, 55));
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(244, 114));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(57, 57));
    }

    @Test public void designedSizesFollowTheReferenceGrid() {
        assertEquals(88, BuiltinWidgetSpan.ONE_BY_ONE.widthDp);
        assertEquals(184, BuiltinWidgetSpan.TWO_BY_ONE.widthDp);
        assertEquals(192, BuiltinWidgetSpan.TWO_BY_TWO.heightDp);
        assertEquals(376, BuiltinWidgetSpan.FOUR_BY_TWO.widthDp);
    }
}
