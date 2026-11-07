package com.termux.app.terminal.inappkeyboard;

/**
 * When the launcher re-judges the keyboard's legends against the faces under it
 * ({@code TermuxInAppKeyboard#setGlassFaces}). Choosing them reads the band's rows and may build a
 * glass palette, several ms on the main thread, so it waits out every moment the chrome is
 * moving: the Look editor's held thumb, and a crossing between two places or Home pages, whose
 * frames each apply the chrome spec while the faces under the keys are mid-way between the two
 * pages' dresses. The keys keep the inks they have; the landing's apply re-judges once, on the
 * faces the page rests in, which the palette memo has already seen from the last visit.
 */
public final class KeyboardFaceJudging {
    private KeyboardFaceJudging() {}

    public static boolean due(boolean glassDragPreview, boolean crossing) {
        return !glassDragPreview && !crossing;
    }
}
