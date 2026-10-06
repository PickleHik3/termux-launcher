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
}
