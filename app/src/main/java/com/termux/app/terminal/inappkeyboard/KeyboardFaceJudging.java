package com.termux.app.terminal.inappkeyboard;

/**
 * When the launcher re-judges the keyboard's legends against the faces under it
 * ({@code TermuxInAppKeyboard#setGlassFaces}). Choosing them reads the band's rows and may build a
 * glass palette, several ms on the main thread, so it waits out the Look editor's held thumb, and
 * a crossing between two places or Home pages judges them once: on the first of its frames that
 * measured the keyboard's band. A keyboard the crossing brings up is measured where it will rest,
 * so it rises in the inks it lands in; judged never, it rose in the inks of the page it left (the
 * put-away keyboard's, chosen on no faces) and every function chip and legend changed at landing.
 * The landing's apply judges once more, at rest, where the palette memo has already seen the faces.
 */
public final class KeyboardFaceJudging {
    private KeyboardFaceJudging() {}

    /**
     * Whether this apply judges the legends: never under a held thumb, always at rest, and in a
     * crossing only until it has judged once ({@code judgedThisCrossing}).
     */
    public static boolean due(boolean glassDragPreview, boolean crossing,
                              boolean judgedThisCrossing) {
        if (glassDragPreview) return false;
        return !crossing || !judgedThisCrossing;
    }

    /**
     * Whether an apply with no band measured hands the keyboard no faces (the theme's base). In a
     * crossing it does not: the keyboard keeps its inks until a frame measures the band, or the
     * landing does.
     */
    public static boolean clearsWithoutBand(boolean crossing) {
        return !crossing;
    }
}
