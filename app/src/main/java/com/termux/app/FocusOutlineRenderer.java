package com.termux.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/** Shared artwork-contour focus renderer for dock drag and terminal-search focus. */
final class FocusOutlineRenderer {

    private static final float STROKE_WIDTH_DP = 1.5f;
    private static final float HALO_RADIUS_DP = 6f;
    private static final float HALO_ALPHA = 0.25f;

    static final class Visual {
        @NonNull final Bitmap crispMask;
        @NonNull final Bitmap haloMask;
        final int sourceWidth;
        final int sourceHeight;
        final int outerPadding;

        Visual(@NonNull Bitmap crispMask, @NonNull Bitmap haloMask,
               int sourceWidth, int sourceHeight, int outerPadding) {
            this.crispMask = crispMask;
            this.haloMask = haloMask;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.outerPadding = outerPadding;
        }
    }

    static final class RenderPaints {
        final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint crisp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    /** Drawable adapter used by ImageView foreground focus; drawing still goes through {@link #draw}. */
    static final class OutlineDrawable extends Drawable {
        @NonNull private final Visual visual;
        @ColorInt private final int accent;
        @NonNull private final RenderPaints paints = new RenderPaints();
        private float focusAlpha = 1f;
        private float focusScale = 1f;
        private int drawableAlpha = 255;

        OutlineDrawable(@NonNull Visual visual, @ColorInt int accent) {
            this.visual = visual;
            this.accent = accent;
        }

        void setFocusAlpha(float alpha) {
            focusAlpha = clamp01(alpha);
            invalidateSelf();
        }

        float getFocusAlpha() {
            return focusAlpha;
        }

        void setFocusScale(float scale) {
            focusScale = Math.max(0f, scale);
            invalidateSelf();
        }

        float getFocusScale() {
            return focusScale;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty() || visual.sourceWidth <= 0 || visual.sourceHeight <= 0) return;
            float xScale = bounds.width() / (float) visual.sourceWidth;
            float yScale = bounds.height() / (float) visual.sourceHeight;
            float padX = visual.outerPadding * xScale;
            float padY = visual.outerPadding * yScale;
            RectF target = new RectF(
                bounds.left - padX,
                bounds.top - padY,
                bounds.right + padX,
                bounds.bottom + padY
            );
            FocusOutlineRenderer.draw(canvas, visual, target, accent,
                focusAlpha * (drawableAlpha / 255f), focusScale, paints);
        }

        @Override
        public void setAlpha(int alpha) {
            drawableAlpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            // The shared renderer owns tinting so both focus entry points stay pixel-identical.
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private FocusOutlineRenderer() {}

    @ColorInt
    static int resolveAccent(@NonNull android.view.View view) {
        return MaterialColors.getColor(view, com.termux.shared.R.attr.termuxColorPrimary,
            androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.termux_primary));
    }

    @NonNull
    static Visual buildVisual(@NonNull Bitmap cleanArtwork, float density) {
        int stroke = Math.max(1, Math.round(Math.max(0f, density) * STROKE_WIDTH_DP));
        int halo = Math.max(1, Math.round(Math.max(0f, density) * HALO_RADIUS_DP));
        int outerPadding = stroke + halo;
        Bitmap crisp = buildFocusOutlineMask(cleanArtwork, 0, stroke, outerPadding);
        Bitmap haloMask = Bitmap.createBitmap(crisp.getWidth(), crisp.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas haloCanvas = new Canvas(haloMask);
        Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        haloPaint.setColor(Color.WHITE);
        haloPaint.setMaskFilter(new BlurMaskFilter(halo, BlurMaskFilter.Blur.NORMAL));
        haloCanvas.drawBitmap(crisp, 0f, 0f, haloPaint);
        haloPaint.setMaskFilter(null);
        clearArtworkInterior(cleanArtwork, haloMask, outerPadding);
        return new Visual(crisp, haloMask, cleanArtwork.getWidth(), cleanArtwork.getHeight(), outerPadding);
    }

    /** Keeps the feather outside the artwork so the halo never becomes a translucent icon fill. */
    private static void clearArtworkInterior(@NonNull Bitmap artwork, @NonNull Bitmap haloMask,
                                             int outerPadding) {
        int width = artwork.getWidth();
        int height = artwork.getHeight();
        int[] artworkPixels = new int[width * height];
        artwork.getPixels(artworkPixels, 0, width, 0, 0, width, height);
        int maxAlpha = 0;
        for (int pixel : artworkPixels) maxAlpha = Math.max(maxAlpha, pixel >>> 24);
        int threshold = Math.max(8, Math.round(maxAlpha * 0.25f));

        int haloWidth = haloMask.getWidth();
        int haloHeight = haloMask.getHeight();
        int[] haloPixels = new int[haloWidth * haloHeight];
        haloMask.getPixels(haloPixels, 0, haloWidth, 0, 0, haloWidth, haloHeight);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((artworkPixels[(y * width) + x] >>> 24) >= threshold) {
                    haloPixels[((y + outerPadding) * haloWidth) + x + outerPadding] = Color.TRANSPARENT;
                }
            }
        }
        haloMask.setPixels(haloPixels, 0, haloWidth, 0, 0, haloWidth, haloHeight);
    }

    static void draw(@NonNull Canvas canvas, @NonNull Visual visual, @NonNull RectF target,
                     @ColorInt int accent, float alpha, float scale,
                     @NonNull RenderPaints paints) {
        float boundedAlpha = clamp01(alpha);
        if (boundedAlpha <= 0f || target.isEmpty()) return;
        int accentAlpha = Color.alpha(accent);
        int opaqueAccent = Color.rgb(Color.red(accent), Color.green(accent), Color.blue(accent));
        PorterDuffColorFilter tint = new PorterDuffColorFilter(opaqueAccent, PorterDuff.Mode.SRC_IN);
        paints.halo.setColorFilter(tint);
        paints.halo.setAlpha(Math.round(accentAlpha * boundedAlpha * HALO_ALPHA));
        paints.crisp.setColorFilter(tint);
        paints.crisp.setAlpha(Math.round(accentAlpha * boundedAlpha));

        int save = canvas.save();
        canvas.scale(scale, scale, target.centerX(), target.centerY());
        canvas.drawBitmap(visual.haloMask, null, target, paints.halo);
        canvas.drawBitmap(visual.crispMask, null, target, paints.crisp);
        canvas.restoreToCount(save);
        paints.halo.setColorFilter(null);
        paints.crisp.setColorFilter(null);
    }

    /**
     * Fallback for focus targets without an artwork mask (folder previews, views measured at zero).
     * Same stroke, halo, and tint parameters as the contour path so the two are visually siblings.
     */
    static void drawRoundRectFallback(@NonNull Canvas canvas, @NonNull RectF target,
                                      float cornerRadius, @ColorInt int accent, float alpha,
                                      float scale, float density) {
        float boundedAlpha = clamp01(alpha);
        if (boundedAlpha <= 0f || target.isEmpty()) return;
        int accentAlpha = Color.alpha(accent);
        int opaqueAccent = Color.rgb(Color.red(accent), Color.green(accent), Color.blue(accent));
        float strokeWidth = Math.max(1f, density * STROKE_WIDTH_DP);
        float haloRadius = Math.max(1f, density * HALO_RADIUS_DP);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(opaqueAccent);

        int save = canvas.save();
        canvas.scale(scale, scale, target.centerX(), target.centerY());
        paint.setStrokeWidth(strokeWidth);
        paint.setMaskFilter(new BlurMaskFilter(haloRadius, BlurMaskFilter.Blur.NORMAL));
        paint.setAlpha(Math.round(accentAlpha * boundedAlpha * HALO_ALPHA));
        canvas.drawRoundRect(target, cornerRadius, cornerRadius, paint);
        paint.setMaskFilter(null);
        paint.setAlpha(Math.round(accentAlpha * boundedAlpha));
        canvas.drawRoundRect(target, cornerRadius, cornerRadius, paint);
        canvas.restoreToCount(save);
    }

    /** Builds the crisp exterior contour; retained separately for small geometry unit tests. */
    @NonNull
    static Bitmap buildFocusOutlineMask(@NonNull Bitmap source, int gap, int stroke) {
        int outer = Math.max(0, gap) + Math.max(1, stroke);
        return buildFocusOutlineMask(source, gap, stroke, outer);
    }

    @NonNull
    private static Bitmap buildFocusOutlineMask(@NonNull Bitmap source, int gap, int stroke,
                                                int outerPadding) {
        int safeGap = Math.max(0, gap);
        int safeStroke = Math.max(1, stroke);
        int safeOuterPadding = Math.max(safeGap + safeStroke, outerPadding);
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int maxAlpha = 0;
        for (int pixel : pixels) maxAlpha = Math.max(maxAlpha, pixel >>> 24);
        int threshold = Math.max(8, Math.round(maxAlpha * 0.25f));
        int resultWidth = width + (safeOuterPadding * 2);
        int resultHeight = height + (safeOuterPadding * 2);
        boolean[] outerMask = new boolean[resultWidth * resultHeight];
        boolean[] innerMask = new boolean[resultWidth * resultHeight];
        int dilation = safeGap + safeStroke;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[(y * width) + x] >>> 24) < threshold) continue;
                int centerX = safeOuterPadding + x;
                int centerY = safeOuterPadding + y;
                dilateInto(outerMask, resultWidth, resultHeight, centerX, centerY, dilation);
                dilateInto(innerMask, resultWidth, resultHeight, centerX, centerY, safeGap);
            }
        }
        int[] resultPixels = new int[resultWidth * resultHeight];
        for (int i = 0; i < resultPixels.length; i++) {
            resultPixels[i] = outerMask[i] && !innerMask[i] ? Color.WHITE : Color.TRANSPARENT;
        }
        Bitmap result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
        result.setPixels(resultPixels, 0, resultWidth, 0, 0, resultWidth, resultHeight);
        return result;
    }

    private static void dilateInto(@NonNull boolean[] output, int width, int height,
                                   int centerX, int centerY, int radius) {
        int radiusSquared = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if ((x * x) + (y * y) <= radiusSquared) {
                    int targetX = centerX + x;
                    int targetY = centerY + y;
                    if (targetX >= 0 && targetX < width && targetY >= 0 && targetY < height) {
                        output[(targetY * width) + targetX] = true;
                    }
                }
            }
        }
    }

    static boolean animationsEnabled(@NonNull Context context) {
        try {
            return Settings.Global.getFloat(
                context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    /** Cancellation-safe incoming scale: revive smoothly, touch 1.04, then settle at 1.0. */
    static float incomingScale(float startScale, float progress) {
        float boundedProgress = clamp01(progress);
        if (startScale >= 1.039f) {
            return startScale + ((1f - startScale) * boundedProgress);
        }
        if (boundedProgress < 0.45f) {
            return startScale + ((1.04f - startScale) * (boundedProgress / 0.45f));
        }
        return 1.04f + ((1f - 1.04f) * ((boundedProgress - 0.45f) / 0.55f));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(1f, value);
    }
}
