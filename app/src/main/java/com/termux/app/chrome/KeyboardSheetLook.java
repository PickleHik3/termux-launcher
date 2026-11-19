package com.termux.app.chrome;

/**
 * The exception to ADR 0031's one look for the chrome: while the app drawer is open, the in-app
 * keyboard under it is part of the drawer's sheet. It wears {@link SheetGlass}'s glass. Its corners stay
 * its own: a rounded top would clip pockets that show the layer behind it untinted, and the
 * drawer's sheet above carries the curve. Once the drawer is shut the keyboard is back to its own look.
 *
 * <p>Pure: the drawer's own handoff fraction goes in (0 shut, 1 the sheet's glass has taken over,
 * see {@code AppDrawerController}), so the keyboard follows the drawer's motion with no animator
 * of its own and no step at either end.</p>
 */
public final class KeyboardSheetLook {

    private KeyboardSheetLook() {}

    /**
     * How much of the sheet's glass the keyboard wears, 0..1: the drawer's fraction while the
     * keyboard is up, 0 while it is down (a keyboard that is not drawn has nothing to dress).
     */
    public static float glassFraction(boolean keyboardShown, float drawerFraction) {
        if (!keyboardShown || !(drawerFraction > 0f)) return 0f;
        return Math.min(1f, drawerFraction);
    }
}
