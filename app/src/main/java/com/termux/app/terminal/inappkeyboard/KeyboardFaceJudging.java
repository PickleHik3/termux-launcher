package com.termux.app.terminal.inappkeyboard;

/**
 * When the launcher re-judges the keyboard's legends against the faces under it
 * ({@code TermuxInAppKeyboard#setGlassFaces}). Choosing them reads the band's rows and may build a
 * glass palette, several ms on the main thread, so it waits out the Look editor's held thumb, and
 * a crossing between two places or Home pages judges them once per place the band is laid out at:
 * on the first of its frames that measured the keyboard's band, and again only when a layout has
 * moved the band since. A keyboard the crossing brings up is measured where it will rest, so it
 * rises in the inks it lands in; judged never, it rose in the inks of the page it left (the
 * put-away keyboard's, chosen on no faces) and every function chip and legend changed at landing.
 * The landing's apply judges once more, at rest, where the palette memo has already seen the faces.
 */
public final class KeyboardFaceJudging {
    private KeyboardFaceJudging() {}

    /**
     * Whether this apply judges the legends: never under a held thumb, always at rest, and in a
     * crossing only until it has judged once ({@code judgedThisCrossing}) at the band's present
     * laid-out rect ({@code bandMovedSinceJudged} false).
     *
     * <p>The band's rect is its layout, not the stack's travel, so it moves only when a layout
     * does, a few times a crossing at most. A crossing's first frame can still stand on the layout
     * of the page it left: Home2 to the Workspace on pong measured the band below the screen, read
     * its rows off a stand-in and bought a veil, so the function chips rose at alpha 0xCA and took
     * the landed rect's 0xE1 only at landing. A crossing that kept that first judgment kept the
     * old layout's inks.</p>
     */
    public static boolean due(boolean glassDragPreview, boolean crossing,
                              boolean judgedThisCrossing, boolean bandMovedSinceJudged) {
        if (glassDragPreview) return false;
        return !crossing || !judgedThisCrossing || bandMovedSinceJudged;
    }

    /**
     * Whether a band whose rows were read off a stand-in is judged again now that its own frame
     * has landed ({@code ChromeInk#onBlurFrameReady}): crossing or not, but never under a held
     * thumb. The ink names each stand-in reading once, so this is one judgment, never one per
     * frame.
     */
    public static boolean rejudgeDue(boolean glassDragPreview) {
        return !glassDragPreview;
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
