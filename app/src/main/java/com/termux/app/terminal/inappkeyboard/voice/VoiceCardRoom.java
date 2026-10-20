package com.termux.app.terminal.inappkeyboard.voice;

/**
 * Where the dictation card may sit vertically while its text is being edited, with a keyboard
 * over its room. A docked keyboard is already below the place viewport the room is read from; a
 * floating one sits over it, and an edit is typed on it, so the card takes the larger of the
 * stretches above and below the keyboard rather than covering the keys or being covered by them.
 *
 * <p>Pure: the indicator reads the rectangles and applies the answer.
 */
final class VoiceCardRoom {

    private VoiceCardRoom() {
    }

    /**
     * The room's {@code {top, bottom}} with {@code [keyboardTop, keyboardBottom)} kept out: the
     * room as it was when they do not overlap, otherwise the larger stretch beside the keyboard
     * (above on a tie, as the card grows from the top by default). Never inverted.
     */
    static int[] avoid(int top, int bottom, int keyboardTop, int keyboardBottom) {
        if (keyboardBottom <= top || keyboardTop >= bottom || keyboardBottom <= keyboardTop) {
            return new int[] {top, bottom};
        }
        int above = Math.max(0, keyboardTop - top);
        int below = Math.max(0, bottom - keyboardBottom);
        if (above >= below) return new int[] {top, top + above};
        return new int[] {bottom - below, bottom};
    }
}
