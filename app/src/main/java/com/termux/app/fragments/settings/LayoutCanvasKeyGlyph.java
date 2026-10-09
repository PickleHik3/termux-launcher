package com.termux.app.fragments.settings;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * An extra key's icon: the Nerd Font code point the real {@code ExtraKeysView} draws with the
 * bundled symbols face, drawn the same way as a Drawable the canvas can size to a slot square and
 * tint to its own text colour (the canvas sets a colour filter, which this paints with).
 */
public final class LayoutCanvasKeyGlyph extends Drawable {

    @NonNull private final String mGlyph;
    @NonNull private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LayoutCanvasKeyGlyph(@NonNull String glyph, @NonNull Typeface typeface) {
        mGlyph = glyph;
        mPaint.setTypeface(typeface);
        mPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float size = Math.min(bounds.width(), bounds.height());
        mPaint.setTextSize(size);
        Paint.FontMetrics metrics = mPaint.getFontMetrics();
        float baseline = bounds.exactCenterY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(mGlyph, bounds.exactCenterX(), baseline, mPaint);
    }

    @Override public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
