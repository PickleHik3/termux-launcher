package com.termux.view;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The click point and the scroll-delivery rules of {@link TapPrecision}. */
public class TapPrecisionTest {

    /** A 12dp font on pong: cells are about 18 x 36 pixels, the touch slop about 21. */
    private static final float ROW_HEIGHT = 36f;

    @Test
    public void aStillFingerClicksWhereItLanded() {
        assertArrayEquals(new float[] { 100f, 200f },
            TapPrecision.clickPointFor(100f, 200f, 100f, 200f, ROW_HEIGHT), 0.001f);
    }

    @Test
    public void tremorInsideOneRowStillClicksWhereItLanded() {
        // Past the 21 pixel touch slop, so the gesture detector calls this a scroll, but well
        // inside the row that decides whether the finger meant to move.
        assertArrayEquals(new float[] { 100f, 200f },
            TapPrecision.clickPointFor(100f, 200f, 115f, 218f, ROW_HEIGHT), 0.001f);
    }

    @Test
    public void travelBeyondOneRowClicksWhereItLifted() {
        assertArrayEquals(new float[] { 160f, 280f },
            TapPrecision.clickPointFor(100f, 200f, 160f, 280f, ROW_HEIGHT), 0.001f);
    }

    @Test
    public void exactlyOneRowOfTravelIsAlreadyMovement() {
        assertArrayEquals(new float[] { 100f, 236f },
            TapPrecision.clickPointFor(100f, 200f, 100f, 236f, ROW_HEIGHT), 0.001f);
    }

    @Test
    public void travelIsMeasuredInBothDirectionsTogether() {
        // 26 across and 26 down is 36.8 of travel: more than a row, even though neither axis is.
        assertArrayEquals(new float[] { 126f, 226f },
            TapPrecision.clickPointFor(100f, 200f, 126f, 226f, ROW_HEIGHT), 0.001f);
    }

    @Test
    public void withoutARowHeightTheLiftDecides() {
        assertArrayEquals(new float[] { 101f, 201f },
            TapPrecision.clickPointFor(100f, 200f, 101f, 201f, 0f), 0.001f);
    }

    @Test
    public void aFreshGestureHasDeliveredNothing() {
        TapPrecision.ScrollDelivery delivery = new TapPrecision.ScrollDelivery();
        assertFalse(delivery.delivered());
    }

    @Test
    public void aScrollThatMovedNoRowOrColumnDeliveredNothing() {
        TapPrecision.ScrollDelivery delivery = new TapPrecision.ScrollDelivery();
        delivery.rowsScrolled(0);
        delivery.columnsScrolled(0);
        delivery.pixelsScrolled(-120f, -120f);
        assertFalse(delivery.delivered());
    }

    @Test
    public void oneRowIsADelivery() {
        TapPrecision.ScrollDelivery delivery = new TapPrecision.ScrollDelivery();
        delivery.rowsScrolled(0);
        delivery.rowsScrolled(-1);
        assertTrue(delivery.delivered());
    }

    @Test
    public void oneColumnIsADelivery() {
        TapPrecision.ScrollDelivery delivery = new TapPrecision.ScrollDelivery();
        delivery.columnsScrolled(2);
        assertTrue(delivery.delivered());
    }

    @Test
    public void aPixelOfSmoothScrollIsADelivery() {
        TapPrecision.ScrollDelivery delivery = new TapPrecision.ScrollDelivery();
        delivery.pixelsScrolled(-120f, -123.5f);
        assertTrue(delivery.delivered());
    }

    @Test
    public void deliveryLastsUntilTheNextGesture() {
        TapPrecision.ScrollDelivery delivery = new TapPrecision.ScrollDelivery();
        delivery.rowsScrolled(3);
        delivery.rowsScrolled(0);
        assertTrue(delivery.delivered());
        delivery.reset();
        assertFalse(delivery.delivered());
    }
}
