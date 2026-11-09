package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one mark a minimised Home float's chip wears (#91): a small dot in the chip's top end
 * corner, in the chip's own ink, solid. The bar's pills and the status column's chips both wear it.
 *
 * <p>Built once per chip and handed to it as its foreground. The ink is read from the chip at draw
 * time, so a chip that changes colour — a selection slide, a re-measured band — never has to touch
 * its foreground, and nothing here ever asks for a layout.
 */
public final class MinimisedDotDrawable extends Drawable {

    /** How far the dot stands in from the chip's top and end edges. */
    public static final float INSET_DP = 3f;

    /** The clear gap between a minimised chip's label and its dot. */
    public static final float GAP_DP = 2f;

    /**
     * The end padding a minimised chip gains so its dot sits in padding of its own, clear of the
     * label: the dot's inset, its size and a small gap.
     */
    public static int extraEndPaddingPx(float density) {
        return Math.round((INSET_DP + TerminalWindowBar.WindowItem.MINIMISED_DOT_DP + GAP_DP)
            * density);
    }

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
                // The padding moves only with the foreground, i.e. only when the state changes;
                // an open chip's padding is never touched. Relative, so RTL mirrors it.
                chip.setPaddingRelative(chip.getPaddingStart(), chip.getPaddingTop(),
                    chip.getPaddingEnd() + extraEndPaddingPx(
                        chip.getResources().getDisplayMetrics().density),
                    chip.getPaddingBottom());
                chip.setForeground(new MinimisedDotDrawable(chip));
            }
        } else if (current instanceof MinimisedDotDrawable) {
            chip.setPaddingRelative(chip.getPaddingStart(), chip.getPaddingTop(),
                chip.getPaddingEnd() - extraEndPaddingPx(
                    chip.getResources().getDisplayMetrics().density),
                chip.getPaddingBottom());
            chip.setForeground(null);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int ink = mHost.getCurrentTextColor();
        // The ink's RGB only: an unselected title's ink is translucent (alpha ~58%), and under the
        // chip's own 0.75 rest alpha the dot came out too faint to see. The mark is the one thing
        // on the chip that must read, so it is drawn solid.
        mPaint.setColor(ink | 0xFF000000);
        mPaint.setAlpha(mAlpha);
        boolean rtl = mHost.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float radius = mSizePx / 2f;
        // A view's foreground scrolls with its content, and a chip's title is one centred line,
        // which a TextView lays out very wide and scrolls into view: the chip sits half a million
        // pixels in, and the dot drawn at its bounds alone landed far outside the chip (the
        // 10-21 emulator check's dimmed chip with no dot). The background is put back in place
        // for the scroll by the view itself; the foreground is not, so the mark does it.
        float sx = mHost.getScrollX();
        float sy = mHost.getScrollY();
        float cx = sx + (rtl ? getBounds().left + mInsetPx + radius
            : getBounds().right - mInsetPx - radius);
        // Vertically the dot's top sits at the label's cap-height top, a corner mark; before the
        // first layout (no baseline yet) or no label it falls back to the inset from the chip's top.
        float top = getBounds().top + mInsetPx;
        int baseline = mHost.getBaseline();
        if (baseline >= 0 && mHost.getText().length() > 0) {
            Rect cap = new Rect();
            mHost.getPaint().getTextBounds("H", 0, 1, cap);
            top = baseline + cap.top;
        }
        canvas.drawCircle(cx, sy + top + radius, radius, mPaint);
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
