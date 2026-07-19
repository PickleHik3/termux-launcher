package com.termux.app;

/** Pure edge detection shared by letter, drag-focus, and keyboard-focus haptic ticks. */
final class RowHapticTickHelper {

    private RowHapticTickHelper() {}

    static boolean isBoundaryCrossing(int previousSelection, int currentSelection) {
        return previousSelection >= 0
            && currentSelection >= 0
            && previousSelection != currentSelection;
    }
}
