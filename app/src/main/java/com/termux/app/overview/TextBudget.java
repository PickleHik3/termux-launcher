package com.termux.app.overview;

import androidx.annotation.NonNull;

/**
 * The most text characters all of Overview's cards may hold between them while it is open: the
 * explicit budget of the only thing a card keeps from its page. Whoever fills the facts draws from
 * one budget per opening; once it runs dry the panes left over are blocks. Pure.
 */
public final class TextBudget {

    /** About 48 KB of chars across the strip, whatever the number of pages. */
    public static final int DEFAULT_CHARS = 24_000;

    private int mLeft;

    public TextBudget(int chars) {
        mLeft = Math.max(0, chars);
    }

    @NonNull
    public static TextBudget standard() {
        return new TextBudget(DEFAULT_CHARS);
    }

    /** Takes up to {@code chars} from the budget; returns how many it gave, 0 when it is dry. */
    public int take(int chars) {
        int given = Math.max(0, Math.min(chars, mLeft));
        mLeft -= given;
        return given;
    }

    public int left() {
        return mLeft;
    }

    /** {@code text} cut to the room the budget has, charged for what stays; "" when dry. */
    @NonNull
    public String charge(@NonNull String text) {
        int given = take(text.length());
        return given == text.length() ? text : text.substring(0, given);
    }
}
