package com.termux.app;

/**
 * Pure edge detection shared by letter, drag-focus, and keyboard-focus haptic ticks.
 *
 * <p>Public rather than package-private because the drawer's A-Z column ({@code
 * com.termux.app.launcher.drawer.AppDrawerRopeColumnView}) ticks on the same rule: a letter change
 * per boundary crossing, never per frame. A second copy of that rule in another package is how two
 * A-Z surfaces end up ticking differently.
 */
public final class RowHapticTickHelper {

    private RowHapticTickHelper() {}

    public static boolean isBoundaryCrossing(int previousSelection, int currentSelection) {
        return previousSelection >= 0
            && currentSelection >= 0
            && previousSelection != currentSelection;
    }
}
