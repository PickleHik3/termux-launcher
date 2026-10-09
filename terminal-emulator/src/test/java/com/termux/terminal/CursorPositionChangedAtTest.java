package com.termux.terminal;

/**
 * {@link TerminalEmulator#getCursorPositionChangedAtMillis()} is the timestamp kitty's cursor
 * trail gates on ({@code cursor_trail}'s delay): it must move only when {@code append} actually
 * changed the cursor's row or column, not on every call.
 */
public class CursorPositionChangedAtTest extends TerminalTestCase {

    private long[] mClockMillis;
    private int mClockIndex;

    private void withFakeClock(long... millis) {
        mClockMillis = millis;
        mClockIndex = 0;
        mTerminal.setClockForTests(() -> mClockMillis[Math.min(mClockIndex++, mClockMillis.length - 1)]);
    }

    public void testStampsOnlyWhenCursorActuallyMoves() {
        withTerminalSized(10, 4);
        withFakeClock(1000L, 2000L, 3000L);
        // A cursor-moving write: the timestamp must be stamped with the clock's next value.
        enterString("a");
        assertEquals(1000L, mTerminal.getCursorPositionChangedAtMillis());
        // A write that changes colour only, leaving the cursor in the same cell: no new stamp.
        enterString("\033[31m");
        assertEquals(1000L, mTerminal.getCursorPositionChangedAtMillis());
        // Moving again stamps again, with the clock's next value.
        enterString("b");
        assertEquals(2000L, mTerminal.getCursorPositionChangedAtMillis());
    }

    public void testCursorRepositioningCountsAsAMove() {
        withTerminalSized(10, 4);
        enterString("hello");
        withFakeClock(5000L);
        // Move the cursor without writing any glyph: CUP to row 1, column 1.
        enterString("\033[1;1H");
        assertEquals(5000L, mTerminal.getCursorPositionChangedAtMillis());
    }
}
