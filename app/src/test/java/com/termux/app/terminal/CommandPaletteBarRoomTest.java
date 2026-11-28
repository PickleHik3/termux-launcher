package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where the palette stands when the Terl control bar's search opens it. */
public class CommandPaletteBarRoomTest {

    private static final float NONE = Float.NaN;
    private static final float INSET = 14f;
    private static final float STRIP = 46f;
    private static final float MAX = 480f;

    @Test
    public void aBarInARowLeavesTheRoomAboveIt() {
        CommandPaletteBarRoom room = CommandPaletteBarRoom.of(400f, 0f, 900f, 8f, 780f, 392f, NONE,
            INSET, STRIP, MAX);
        assertEquals(780f, room.freeBottom, 0f);
        assertEquals("the strip hangs between the glass and the bar", 780f - STRIP, room.anchor, 0f);
        assertEquals(INSET, room.left, 0f);
        assertEquals(400f - INSET, room.right, 0f);
        assertEquals(0f, room.freeLeft, 0f);
        assertEquals(400f, room.freeRight, 0f);
        assertEquals(INSET, room.top, 0f);
    }

    @Test
    public void theKeyboardOverTheBarLowersTheRoomToItsTop() {
        CommandPaletteBarRoom room = CommandPaletteBarRoom.of(400f, 0f, 900f, 8f, 780f, 392f, 600f,
            INSET, STRIP, MAX);
        assertEquals(600f, room.freeBottom, 0f);
        assertEquals(600f - STRIP, room.anchor, 0f);
    }

    @Test
    public void aRailKeepsItsSideAndTheGlassStandsBesideIt() {
        // A rail on the right: the room ends at its left side.
        CommandPaletteBarRoom right = CommandPaletteBarRoom.of(900f, 0f, 400f, 820f, 10f, 890f, NONE,
            INSET, STRIP, MAX);
        assertEquals(820f, right.freeRight, 0f);
        assertEquals(400f, right.freeBottom, 0f);
        // A rail on the left: the room starts at its right side.
        CommandPaletteBarRoom left = CommandPaletteBarRoom.of(900f, 0f, 400f, 10f, 10f, 80f, NONE,
            INSET, STRIP, MAX);
        assertEquals(80f, left.freeLeft, 0f);
        assertEquals(900f, left.freeRight, 0f);
    }

    @Test
    public void aWideRoomCentresTheGlassAtTheSheetsMeasure() {
        CommandPaletteBarRoom room = CommandPaletteBarRoom.of(900f, 0f, 400f, 820f, 10f, 890f, NONE,
            INSET, STRIP, MAX);
        assertEquals(MAX, room.right - room.left, 0.01f);
        assertEquals(410f, (room.left + room.right) / 2f, 0.01f);
    }

    @Test
    public void withNoBarTheRoomIsThePage() {
        CommandPaletteBarRoom room = CommandPaletteBarRoom.of(400f, 50f, 900f, NONE, NONE, NONE, NONE,
            INSET, STRIP, MAX);
        assertEquals(900f, room.freeBottom, 0f);
        assertEquals(50f + INSET, room.top, 0f);
    }
}
