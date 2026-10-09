package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShapeTokensTest {

    @Test public void radiiMapToNearestToken() {
        assertEquals(ShapeTokens.Corner.SMALL, ShapeTokens.nearest(7f));
        assertEquals(ShapeTokens.Corner.SMALL, ShapeTokens.nearest(8f));
        assertEquals(ShapeTokens.Corner.MEDIUM, ShapeTokens.nearest(10f));
        assertEquals(ShapeTokens.Corner.MEDIUM, ShapeTokens.nearest(12f));
        assertEquals(ShapeTokens.Corner.LARGE, ShapeTokens.nearest(14f));
        assertEquals(ShapeTokens.Corner.LARGE, ShapeTokens.nearest(20f));
        assertEquals(ShapeTokens.Corner.EXTRA_LARGE, ShapeTokens.nearest(22f));
        assertEquals(ShapeTokens.Corner.EXTRA_LARGE, ShapeTokens.nearest(28f));
    }

    @Test public void elevationsSnapToM3Levels() {
        assertEquals(0, ShapeTokens.elevationLevel(0f));
        assertEquals(2, ShapeTokens.elevationLevel(3f));
        assertEquals(4, ShapeTokens.elevationLevel(8f));
        assertEquals(5, ShapeTokens.elevationLevel(56f));
        assertEquals(12f, ShapeTokens.elevationDp(5), 0f);
        assertEquals(6f, ShapeTokens.elevationDp(3), 0f);
    }
}
