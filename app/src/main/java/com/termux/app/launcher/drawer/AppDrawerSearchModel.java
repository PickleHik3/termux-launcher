package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * The drawer search field's entire state: a query and a caret into it.
 *
 * <p>Pure, and deliberately not an {@code Editable}. The drawer's search pill is focusless — it
 * never holds an {@code InputConnection}, because the terminal does — so there is no text widget to
 * ask for the state and this object is the only copy. Three intake channels (the in-app keyboard's
 * interceptor, hardware key events and system-IME code points) all land here.
 *
 * <p>Every mutator answers whether the query actually changed, which is what lets the controller
 * re-rank on a keystroke and skip the work for a caret move or a backspace at position zero.
 *
 * <p>Edits step by whole code points rather than chars: an app name — or a query pasted from one —
 * can contain a surrogate pair, and a caret parked inside one renders as a broken glyph and deletes
 * as half a character.
 */
public final class AppDrawerSearchModel {

    private final StringBuilder mQuery = new StringBuilder();
    private int mCaret;

    @NonNull
    public String query() {
        return mQuery.toString();
    }

    /** Caret offset in chars, always in {@code 0..length()} and never inside a surrogate pair. */
    public int caret() {
        return mCaret;
    }

    public int length() {
        return mQuery.length();
    }

    public boolean isEmpty() {
        return mQuery.length() == 0;
    }

    /**
     * Inserts one typed code point at the caret.
     *
     * @return true when the query changed
     */
    public boolean insertCodePoint(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) return false;
        return insert(new String(Character.toChars(codePoint)));
    }

    /**
     * Inserts a run of text at the caret and leaves the caret after it.
     *
     * @return true when the query changed
     */
    public boolean insert(@NonNull String text) {
        if (text.isEmpty()) return false;
        mQuery.insert(mCaret, text);
        mCaret += text.length();
        return true;
    }

    /**
     * Deletes the code point before the caret.
     *
     * @return true when the query changed, i.e. false at the start of the query
     */
    public boolean backspace() {
        if (mCaret <= 0) return false;
        int width = Character.charCount(mQuery.codePointBefore(mCaret));
        mQuery.delete(mCaret - width, mCaret);
        mCaret -= width;
        return true;
    }

    /**
     * Empties the query.
     *
     * @return true when there was anything to empty
     */
    /**
     * Replaces the whole query, as a text field reports it after the keyboard has edited it: a
     * committed word, an autocorrection, a swipe. The caret is clamped into the new text.
     *
     * @return true when either the text or the caret moved
     */
    public boolean replace(@NonNull String text, int caret) {
        int clamped = Math.max(0, Math.min(caret, text.length()));
        if (text.contentEquals(mQuery) && clamped == mCaret) return false;
        mQuery.setLength(0);
        mQuery.append(text);
        mCaret = clamped;
        return true;
    }

    public boolean clear() {
        if (mQuery.length() == 0) {
            mCaret = 0;
            return false;
        }
        mQuery.setLength(0);
        mCaret = 0;
        return true;
    }

    /**
     * Moves the caret by whole code points, clamped to the query at both ends.
     *
     * @param steps positive moves toward the end, negative toward the start
     * @return true when the caret moved
     */
    public boolean moveCursor(int steps) {
        int caret = mCaret;
        boolean forward = steps > 0;
        for (int remaining = Math.abs(steps); remaining > 0; remaining--) {
            if (forward) {
                if (caret >= mQuery.length()) break;
                caret += Character.charCount(mQuery.codePointAt(caret));
            } else {
                if (caret <= 0) break;
                caret -= Character.charCount(mQuery.codePointBefore(caret));
            }
        }
        if (caret == mCaret) return false;
        mCaret = caret;
        return true;
    }
}
