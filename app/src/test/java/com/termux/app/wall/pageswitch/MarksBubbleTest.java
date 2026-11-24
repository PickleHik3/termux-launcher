package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksBubbleTest {

    private final MarksBubble.Box box = new MarksBubble.Box();

    @Test public void aRowAtTheTopDropsTheBubbleBelowTheBar() {
        assertEquals(MarksBubble.Side.BELOW, MarksBubble.sideFor(false, 500f, 30f, 1000f, 2000f));
        MarksBubble.place(MarksBubble.Side.BELOW, 0f, 0f, 1000f, 60f, 500f, 200f, 80f, 8f, 16f,
            1000f, 2000f, box);
        assertEquals(68f, box.top, 1e-3f);
        assertEquals(400f, box.left, 1e-3f);
    }

    @Test public void aRowAtTheBottomLiftsTheBubbleAboveTheBar() {
        assertEquals(MarksBubble.Side.ABOVE, MarksBubble.sideFor(false, 500f, 1970f, 1000f, 2000f));
        MarksBubble.place(MarksBubble.Side.ABOVE, 0f, 1940f, 1000f, 2000f, 500f, 200f, 80f, 8f,
            16f, 1000f, 2000f, box);
        assertEquals(1940f - 8f - 80f, box.top, 1e-3f);
    }

    @Test public void aColumnTurnsTheBubbleTowardTheScreenCentre() {
        assertEquals(MarksBubble.Side.RIGHT, MarksBubble.sideFor(true, 30f, 800f, 1000f, 2000f));
        assertEquals(MarksBubble.Side.LEFT, MarksBubble.sideFor(true, 970f, 800f, 1000f, 2000f));
        MarksBubble.place(MarksBubble.Side.RIGHT, 0f, 0f, 60f, 2000f, 800f, 200f, 80f, 8f, 16f,
            1000f, 2000f, box);
        assertEquals(68f, box.left, 1e-3f);
        assertEquals(760f, box.top, 1e-3f);
        MarksBubble.place(MarksBubble.Side.LEFT, 940f, 0f, 1000f, 2000f, 800f, 200f, 80f, 8f, 16f,
            1000f, 2000f, box);
        assertEquals(940f - 8f - 200f, box.left, 1e-3f);
    }

    @Test public void theBubbleStaysInsideTheHostAtTheEnds() {
        MarksBubble.place(MarksBubble.Side.BELOW, 0f, 0f, 1000f, 60f, 5f, 200f, 80f, 8f, 16f,
            1000f, 2000f, box);
        assertEquals(16f, box.left, 1e-3f);
        MarksBubble.place(MarksBubble.Side.BELOW, 0f, 0f, 1000f, 60f, 995f, 200f, 80f, 8f, 16f,
            1000f, 2000f, box);
        assertEquals(1000f - 16f - 200f, box.left, 1e-3f);
        MarksBubble.place(MarksBubble.Side.RIGHT, 0f, 0f, 60f, 2000f, 3f, 200f, 80f, 8f, 16f,
            1000f, 2000f, box);
        assertEquals(16f, box.top, 1e-3f);
    }

    @Test public void theWidthIsTheDiscPlusTheNameCappedByTheRoom() {
        assertEquals(40f + 100f + 12f, MarksBubble.widthFor(40f, 100f, 12f, 400f), 1e-3f);
        assertEquals(400f, MarksBubble.widthFor(40f, 900f, 12f, 400f), 1e-3f);
        assertTrue(MarksBubble.widthFor(40f, 0f, 12f, 400f) >= 40f);
    }
}
