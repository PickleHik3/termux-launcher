package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Home palette and the keyboard as one overlay (spec §5.1): the palette gesture opens both,
 * and when the palette closes for any reason — Esc, a tap outside, an action that ran — the
 * keyboard it raised goes down with it, unless the keyboard is always on. A keyboard that was
 * already up when the palette opened (a Home float's, always on) is not the palette's to close.
 *
 * <p>Pure state, no views: the controller records the opening and asks at the close, and measures
 * the room and the bottom stack and asks where the glass may stand: in the screen's middle,
 * growing about its centre, and never over the dock or the keyboard ({@link #bottomInsetPx},
 * {@link #availableHeightPx}, {@link #glassBottomPx}).
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

    /** How a field-shaped entry to the palette opens it ({@link #fieldOpen}). */
    enum FieldOpen {
        /** Nothing to raise, or the keyboard is already up: the palette opens now, unpaired. */
        NOW,
        /** The keyboard is raised first and the palette opens on the next frame, paired with it. */
        AFTER_KEYBOARD
    }

    /**
     * How a tap on a field that stands for the palette's search (the Prompt line) opens it: with
     * {@code withKeyboard} and the keyboard down, the keyboard comes first, since the palette
     * sprouts from where the keyboard stands; otherwise the palette opens at once.
     */
    @NonNull
    static FieldOpen fieldOpen(boolean withKeyboard, boolean keyboardUp) {
        return withKeyboard && !keyboardUp ? FieldOpen.AFTER_KEYBOARD : FieldOpen.NOW;
    }

    /**
     * After the keyboard was raised for a field's open, the frame it lands on: open the palette
     * when Home is still in view; otherwise the keyboard raised for nothing goes down again,
     * unless always on keeps it.
     */
    static boolean lowersRaisedKeyboard(boolean onHome, boolean alwaysOn) {
        return !onHome && !alwaysOn;
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

    /**
     * How tall the bottom stack stands in the page's room: the dock's rows riding on the keyboard,
     * and the keyboard (spec §5), measured from the room's bottom to the stack's top. On Home both
     * are overlays, so the page runs on under them and the room alone would put the palette over
     * them. Zero when nothing stands there (no stack, or its top at or below the room's bottom),
     * and never more than the room.
     */
    static float stackHeightPx(float roomTopPx, float roomBottomPx, float stackTopPx) {
        float room = Math.max(0f, roomBottomPx - roomTopPx);
        if (Float.isNaN(stackTopPx)) return 0f;
        return Math.max(0f, Math.min(room, roomBottomPx - stackTopPx));
    }

    /**
     * How far above the room's bottom the Home palette's glass ends: past the whole bottom stack
     * ({@link #stackHeightPx}) and the keycap strip that hangs under the glass, so neither the
     * glass nor its strip lies over the dock or the keyboard. Never more than the room.
     */
    static float bottomInsetPx(float roomHeightPx, float stackHeightPx, float stripReservePx) {
        float room = Math.max(0f, roomHeightPx);
        return Math.min(room, Math.max(0f, stackHeightPx) + Math.max(0f, stripReservePx));
    }

    /**
     * The tallest the Home palette's glass may grow: the room above its lowest bottom edge less a
     * gap at the top, and never past {@code maxHeightPx}. A short room (landscape, or the keyboard
     * up) shrinks the list rather than pushing the glass under the dock.
     */
    static float availableHeightPx(float roomHeightPx, float bottomInsetPx, float topGapPx,
                                   float maxHeightPx) {
        float above = Math.max(0f, roomHeightPx) - Math.max(0f, bottomInsetPx)
            - Math.max(0f, topGapPx);
        return Math.max(0f, Math.min(maxHeightPx, above));
    }

    /**
     * Where the Home palette's glass ends for a glass {@code heightPx} tall: centred on
     * {@code centerYPx} (the screen's middle), so as results arrive it grows about its centre and
     * its top rises by half of what it gained, but kept inside the room. Its top never rises past
     * {@code highestTopPx}, and its bottom never falls past {@code lowestBottomPx}, the edge above
     * the dock, the keyboard and the keycap strip; where the middle would put it under them the
     * glass stands on that edge instead and grows upward from it. A glass taller than the room
     * ({@link #availableHeightPx} keeps it from being one) keeps its bottom clear of the stack.
     */
    static float glassBottomPx(float heightPx, float centerYPx, float highestTopPx,
                               float lowestBottomPx) {
        float height = Math.max(0f, heightPx);
        float bottom = Math.max(centerYPx + height / 2f, highestTopPx + height);
        return Math.min(bottom, lowestBottomPx);
    }
}
