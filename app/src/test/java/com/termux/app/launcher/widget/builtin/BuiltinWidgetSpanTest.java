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
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(300, 150));
        assertEquals(BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.forSize(184, 192));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.forSize(376, 92));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(376, 192));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.forSize(1000, 1000));
    }

    @Test public void aLittleUnderTheDesignStillCountsAsFitting() {
        // Four cells of an 8-column grid come to a few dp under the two-cell design.
        assertEquals(BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.forSize(176, 90));
        assertEquals(BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.forSize(150, 90));
    }

    @Test public void designedSizesFollowTheReferenceGrid() {
        assertEquals(88, BuiltinWidgetSpan.ONE_BY_ONE.widthDp);
        assertEquals(184, BuiltinWidgetSpan.TWO_BY_ONE.widthDp);
        assertEquals(192, BuiltinWidgetSpan.TWO_BY_TWO.heightDp);
        assertEquals(376, BuiltinWidgetSpan.FOUR_BY_TWO.widthDp);
    }
}
