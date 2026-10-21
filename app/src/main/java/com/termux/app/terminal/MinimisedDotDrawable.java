package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one mark a minimised Home float's chip wears (#91): a small dot in the chip's top end
 * corner, in the chip's own ink. The bar's pills and the status column's chips both wear it.
 *
 * <p>Built once per chip and handed to it as its foreground. The ink is read from the chip at draw
 * time, so a chip that changes colour — a selection slide, a re-measured band — never has to touch
 * its foreground, and nothing here ever asks for a layout.
 */
public final class MinimisedDotDrawable extends Drawable {

    /** How far the dot stands in from the chip's top and end edges. */
    public static final float INSET_DP = 3f;

    @NonNull private final TextView mHost;
    private final float mSizePx;
    private final float mInsetPx;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mAlpha = 255;

    public MinimisedDotDrawable(@NonNull TextView host) {
        mHost = host;
        float density = host.getResources().getDisplayMetrics().density;
        mSizePx = TerminalWindowBar.WindowItem.MINIMISED_DOT_DP * density;
        mInsetPx = INSET_DP * density;
    }

    /**
     * Wears or drops the dot on {@code chip}, touching its foreground only when that changes:
     * setting a foreground asks for a layout, which a chip re-dressed from a draw pass must not.
     */
    public static void wear(@NonNull TextView chip, boolean minimised) {
        Drawable current = chip.getForeground();
        if (minimised) {
            if (!(current instanceof MinimisedDotDrawable)) {
                chip.setForeground(new MinimisedDotDrawable(chip));
            }
        } else if (current instanceof MinimisedDotDrawable) {
            chip.setForeground(null);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int ink = mHost.getCurrentTextColor();
        mPaint.setColor(ink);
        mPaint.setAlpha(android.graphics.Color.alpha(ink) * mAlpha / 255);
        boolean rtl = mHost.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float radius = mSizePx / 2f;
        float cx = rtl ? getBounds().left + mInsetPx + radius
            : getBounds().right - mInsetPx - radius;
        canvas.drawCircle(cx, getBounds().top + mInsetPx + radius, radius, mPaint);
    }

    @Override public void setAlpha(int alpha) {
        if (mAlpha == alpha) return;
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter filter) {
        mPaint.setColorFilter(filter);
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
