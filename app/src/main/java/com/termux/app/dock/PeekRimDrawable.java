package com.termux.app.dock;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

import androidx.annotation.NonNull;

/**
 * The rim under the Peek glass card (below Android 13, where the glass program does not draw the
 * glass's edge): the rim inset to the card inside the dock's glass host, taking no part in layout.
 *
 * <p>A plain {@link InsetDrawable} reports its insets as padding, and {@link
 * android.view.View#setBackground} hands a background's padding to its view, where it stays after
 * the background is swapped for one without padding. Worn by the glass host it padded the host by
 * the card's side inset and top (32 px at the sides, 95 px down on pong), so the frost, the tint,
 * the glow and the reflection were laid out inside the card's box rather than over the host — and
 * kept that box once the keyboard came up and the card was gone: the extra keys bar stood off the
 * glass and the keyboard's glass stopped short of the screen's sides. This reports no padding and
 * no size, so the host's children always span it and the card is only the host's outline.
 */
public final class PeekRimDrawable extends InsetDrawable {

    public PeekRimDrawable(@NonNull Drawable rim, int insetLeft, int insetTop, int insetRight) {
        super(rim, Math.max(0, insetLeft), Math.max(0, insetTop), Math.max(0, insetRight), 0);
    }

    /** The rim cut to {@code card} in a host {@code hostWidthPx} wide, its bottom edge open. */
    @NonNull
    public static PeekRimDrawable forCard(@NonNull Drawable rim, @NonNull PeekGlassGeometry.Card card,
                                          int hostWidthPx) {
        return new PeekRimDrawable(rim, Math.round(card.left), Math.round(card.top),
            hostWidthPx - Math.round(card.right));
    }

    // No padding and no size: see the class comment.
    @Override
    public boolean getPadding(@NonNull Rect padding) {
        padding.set(0, 0, 0, 0);
        return false;
    }

    @Override public int getIntrinsicWidth() { return -1; }
    @Override public int getIntrinsicHeight() { return -1; }
    @Override public int getMinimumWidth() { return 0; }
    @Override public int getMinimumHeight() { return 0; }
}
