package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The deck look's slab (wave 8): the window bar as opaque plastic in the bezel's tone, its lit top
 * ({@link DeckLook#bedTop}) running down to the bed, with the bezel's own shade over it
 * ({@link PaneBezel#SHADE_COLOURS}: white 8 % at the top edge gone by the middle, black 18 % at
 * the bottom edge), so the strip and the CRT card read as one stack. The host's outline clips it.
 */
public final class DeckSlabDrawable extends Drawable {

    private final DeckLook mDeck;
    private final Paint mBed = new Paint();
    private final Paint mShade = new Paint();

    public DeckSlabDrawable(@NonNull DeckLook deck) {
        mDeck = deck;
    }

    @NonNull
    public DeckLook deck() {
        return mDeck;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        float top = bounds.top;
        float bottom = bounds.bottom;
        mBed.setShader(new LinearGradient(0f, top, 0f, bottom, mDeck.bedTop, mDeck.bed,
            Shader.TileMode.CLAMP));
        mShade.setShader(new LinearGradient(0f, top, 0f, bottom, PaneBezel.SHADE_COLOURS,
            PaneBezel.SHADE_POSITIONS, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        canvas.drawRect(b, mBed);
        canvas.drawRect(b, mShade);
    }

    @Override public void setAlpha(int alpha) {
        mBed.setAlpha(alpha);
        mShade.setAlpha(alpha);
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mBed.setColorFilter(colorFilter);
        mShade.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
