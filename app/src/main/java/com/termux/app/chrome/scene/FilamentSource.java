package com.termux.app.chrome.scene;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the Filament scene shows and draws its tubes with, shared by the full-resolution program
 * and the glass pass: the focused window's name (written on the main thread) and the glyph atlas
 * (built once, on the scene thread, from the terminal's own font).
 *
 * <p>The atlas is {@link #COLS} x rows cells of {@link #CELL_W} x {@link #CELL_H} px, one per glyph
 * of {@link FilamentLayout#GLYPHS}, in one ARGB bitmap: the red channel is the sharp glyph, the
 * green channel the same glyph blurred (the glow around the cathode). A single sample inside a
 * tube's glyph box gives both. Built from {@link #setTypeface the terminal's typeface} when one
 * has been given by then, else the platform monospace; a later font change is not followed.</p>
 */
final class FilamentSource {

    static final int COLS = 8;
    static final int CELL_W = 48;
    static final int CELL_H = 64;
    private static final float TEXT_PX = 50f;
    private static final float BLUR_PX = 5f;

    @Nullable private volatile String mName;
    @Nullable private volatile Typeface mTypeface;
    @Nullable private Bitmap mAtlas;

    /** The window name the tubes spell; main thread writes, scene thread reads. */
    void setName(@Nullable String name) {
        mName = name;
    }

    @Nullable
    String name() {
        return mName;
    }

    /** The font the atlas is drawn in; only the first one given before the atlas is built counts. */
    void setTypeface(@Nullable Typeface typeface) {
        if (typeface != null && mTypeface == null) mTypeface = typeface;
    }

    static int rows() {
        return (FilamentLayout.GLYPH_COUNT + COLS - 1) / COLS;
    }

    /** The atlas, built on first use. Scene thread. */
    @NonNull
    synchronized Bitmap atlas() {
        Bitmap atlas = mAtlas;
        if (atlas == null) {
            atlas = build(mTypeface);
            mAtlas = atlas;
        }
        return atlas;
    }

    @NonNull
    private static Bitmap build(@Nullable Typeface typeface) {
        int w = COLS * CELL_W, h = rows() * CELL_H;
        Bitmap sharp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Bitmap blur = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(typeface != null ? typeface : Typeface.MONOSPACE);
        p.setTextSize(TEXT_PX);
        p.setTextScaleX(0.9f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE);
        Paint.FontMetrics fm = p.getFontMetrics();
        float baseline = CELL_H / 2f - (fm.ascent + fm.descent) / 2f;
        Canvas cs = new Canvas(sharp);
        Canvas cb = new Canvas(blur);
        Paint glow = new Paint(p);
        glow.setMaskFilter(new BlurMaskFilter(BLUR_PX, BlurMaskFilter.Blur.NORMAL));
        for (int g = 0; g < FilamentLayout.GLYPH_COUNT; g++) {
            float cx = (g % COLS) * CELL_W + CELL_W / 2f;
            float cy = (g / COLS) * CELL_H + baseline;
            String s = String.valueOf(FilamentLayout.GLYPHS.charAt(g));
            cs.drawText(s, cx, cy, p);
            cb.drawText(s, cx, cy, glow);
        }
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        sharp.getPixels(a, 0, w, 0, 0, w, h);
        blur.getPixels(b, 0, w, 0, 0, w, h);
        sharp.recycle();
        blur.recycle();
        for (int i = 0; i < a.length; i++) {
            int r = a[i] >>> 24;
            int gl = Math.min(255, (b[i] >>> 24) * 2);
            a[i] = 0xFF000000 | (r << 16) | (gl << 8);
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888);
    }
}
