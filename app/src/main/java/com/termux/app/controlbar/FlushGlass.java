package com.termux.app.controlbar;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

import androidx.annotation.NonNull;

/**
 * The bar's glass when it stands flush with the screen's bottom (margin 0): the glass is cut for
 * rounded corners all round, so it is laid {@code extraPx} taller than the bar, which puts its
 * rounded bottom corners below the bar's edge where the bar's outline clips them. What shows has
 * square bottom corners and the top corners' own radius.
 */
final class FlushGlass extends DrawableWrapper {

    private final int mExtraPx;

    FlushGlass(@NonNull Drawable glass, int extraPx) {
        super(glass);
        mExtraPx = Math.max(0, extraPx);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        Drawable glass = getDrawable();
        if (glass != null) {
            glass.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom + mExtraPx);
        }
    }
}
