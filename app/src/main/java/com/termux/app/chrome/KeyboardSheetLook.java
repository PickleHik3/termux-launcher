package com.termux.app.chrome;

/**
 * The exception to ADR 0031's one look for the chrome: while the app drawer is open, the in-app
 * keyboard under it is part of the drawer's sheet. It wears {@link SheetGlass}'s glass and, where
 * it is a slice of the Docked frame, rounds its top corners with the drawer's radius so the two
 * read as one sheet. Once the drawer is shut the keyboard is back to its own look.
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

    /**
     * The radius of the keyboard's top corners, px. A Docked keyboard is a square slice of the
     * frame (or has the screen's corners) and takes the drawer's radius in step with the glass; a
     * card (Floating, or a floating keyboard) keeps the corners it already has.
     *
     * @param card whether the keyboard is a card of its own rather than a Docked slice
     * @param ownTopPx the top radius the keyboard wears with the drawer shut
     * @param drawerRadiusPx the drawer's own corner radius
     * @param fraction {@link #glassFraction}
     */
    public static float topRadiusPx(boolean card, float ownTopPx, float drawerRadiusPx,
                                    float fraction) {
        float own = Math.max(0f, ownTopPx);
        if (card || !(fraction > 0f)) return own;
        float target = Math.max(own, Math.max(0f, drawerRadiusPx));
        return own + (target - own) * Math.min(1f, fraction);
    }

    /**
     * The black alpha (0..255) the keyboard lays under the sheet's glass: the drawer's own dim
     * over the scene ({@code GlassBackdropTint}) at its progress, in step with the glass. The
     * drawer's glass sits over that dim; the keyboard, drawn over the unified dock glass and not
     * under the drawer's host, would otherwise sit over the undimmed wallpaper and read lighter.
     */
    public static int dimAlpha(float fraction, float drawerProgress) {
        if (!(fraction > 0f)) return 0;
        int full = com.termux.app.GlassBackdropTint.colorFor(drawerProgress) >>> 24;
        return Math.round(full * Math.min(1f, fraction));
    }
}
