package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** The card's room while its text is edited keeps a keyboard floating over it out. */
public class VoiceCardRoomTest {

    @Test
    public void aKeyboardOutsideTheRoomChangesNothing() {
        assertArrayEquals(new int[] {100, 900}, VoiceCardRoom.avoid(100, 900, 950, 1400));
        assertArrayEquals(new int[] {100, 900}, VoiceCardRoom.avoid(100, 900, 0, 100));
        assertArrayEquals(new int[] {100, 900}, VoiceCardRoom.avoid(100, 900, 500, 500));
    }

    @Test
    public void theLargerStretchBesideAFloatingKeyboardIsTaken() {
        // Low on the screen: the room above it.
        assertArrayEquals(new int[] {100, 600}, VoiceCardRoom.avoid(100, 900, 600, 850));
        // High on the screen: the room below it.
        assertArrayEquals(new int[] {500, 900}, VoiceCardRoom.avoid(100, 900, 150, 500));
        // A tie goes above, where an unmoved card grows from.
        assertArrayEquals(new int[] {100, 300}, VoiceCardRoom.avoid(100, 900, 300, 700));
    }

    @Test
    public void aKeyboardCoveringTheWholeRoomLeavesAnEmptyStretchNeverAnInvertedOne() {
        int[] slab = VoiceCardRoom.avoid(100, 900, 50, 950);
        assertArrayEquals(new int[] {100, 100}, slab);
    }
}
