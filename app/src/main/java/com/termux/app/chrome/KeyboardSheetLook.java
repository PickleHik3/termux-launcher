package com.termux.app.chrome;

/**
 * The exception to ADR 0031's one look for the chrome: while the app drawer is open, the in-app
 * keyboard card under it wears the drawer's glass ({@link SheetGlass}) and dim, so the two cards
 * read as one material. Its shape stays its own. Once the drawer is shut the keyboard is back to
 * its own look.
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
     * The black alpha (0..255) the keyboard lays under the sheet's glass: the drawer's own dim
     * over the scene ({@code GlassBackdropTint}) at its progress, in step with the glass. The
     * drawer's glass sits over that dim; the keyboard card is the dim's hole, so without its own
     * it would sit over the undimmed wallpaper and read lighter.
     */
    public static int dimAlpha(float fraction, float drawerProgress) {
        if (!(fraction > 0f)) return 0;
        int full = com.termux.app.GlassBackdropTint.colorFor(drawerProgress) >>> 24;
        return Math.round(full * Math.min(1f, fraction));
    }
}
