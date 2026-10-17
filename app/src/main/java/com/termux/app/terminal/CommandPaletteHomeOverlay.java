package com.termux.app.terminal;

import androidx.annotation.Nullable;

/**
 * The Home palette and the keyboard as one overlay (spec §5.1): the palette gesture opens both,
 * and when the palette closes for any reason — Esc, a tap outside, an action that ran — the
 * keyboard it raised goes down with it, unless the keyboard is always on. A keyboard that was
 * already up when the palette opened (a Home float's, always on) is not the palette's to close.
 *
 * <p>Pure state, no views: the controller records the opening and asks at the close.
 */
final class CommandPaletteHomeOverlay {

    /** Whether the open palette is the Home overlay and raised the keyboard itself. */
    private boolean mRaisedKeyboard;

    /** The palette gesture opened the Home palette; {@code keyboardWasUp} as the swipe began. */
    void opened(boolean keyboardWasUp) {
        mRaisedKeyboard = !keyboardWasUp;
    }

    /** Whether the palette is paired with a keyboard it raised. */
    boolean isPaired() {
        return mRaisedKeyboard;
    }

    /**
     * The palette closed. Ends the pairing and says whether the keyboard goes down with it: only
     * the keyboard the overlay raised, only while Home is still in view, and never while always
     * on keeps it up.
     */
    boolean closed(boolean onHome, boolean alwaysOn) {
        boolean lower = mRaisedKeyboard && onHome && !alwaysOn;
        mRaisedKeyboard = false;
        return lower;
    }

    /**
     * Whether a key typed on Home opens the palette with it as the query: with always on, no
     * palette open, no Home float holding the typing, and the bound keys passed it by.
     */
    static boolean typingOpens(boolean onHome, boolean alwaysOn, boolean paletteOpen,
                               boolean floatHasFocus, @Nullable String text) {
        return onHome && alwaysOn && !floatHasFocus
            && CommandPaletteHomeSet.opensOnTyped(paletteOpen, text);
    }
}
