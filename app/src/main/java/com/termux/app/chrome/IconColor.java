package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * An app icon's own colour, read once from a 40&times;40 render of it.
 *
 * <p>Only chromatic pixels vote: adaptive icons are mostly a white or neutral plate, and letting
 * that mass win pushed every result to the fallback. Pixels are grouped into 4096 buckets (4 bits a
 * channel), each weighted by alpha &times; saturation, and the heaviest bucket wins. An icon with
 * almost no chromatic pixels at all — a grey glyph, a themed monochrome icon — answers the caller's
 * fallback, which is the theme's accent wherever this is used.</p>
 *
 * <p>Two readings share the vote. {@link #dominant} is the launch ripple's: the heaviest bucket,
 * whatever it is. {@link #tint} is for a surface washed in the colour (the pinned notification
 * card): when the heaviest bucket is a near-white pastel it would wash the card in nothing, so the
 * most saturated bucket that still carries a real share of the vote is taken instead.</p>
 *
 * <p>The HSV maths is done here on plain ints rather than through {@code android.graphics.Color}
 * so the whole vote unit tests on the JVM ({@link #fromPixels}).</p>
 */
public final class IconColor {

    /** Side of the square the icon is rendered at for the vote. */
    public static final int SAMPLE_SIZE = 40;

    private static final int MIN_ALPHA = 64;
    private static final float MIN_SATURATION = .18f;
    private static final float MIN_VALUE = .18f;
    /** Fewer than one chromatic pixel in this many opaque ones and the icon counts as neutral. */
    private static final int NEUTRAL_RATIO = 25;
    /** A bucket this pale and this bright is "white" for {@link #tint}. */
    private static final float PALE_SATURATION = .32f;
    private static final float PALE_VALUE = .9f;
    /** A secondary bucket must carry at least this share of the winner's weight to replace it. */
    private static final float SECONDARY_SHARE = .08f;

    private IconColor() {}

    /** The heaviest chromatic colour of {@code drawable}, or {@code fallback} for a neutral icon. */
    @ColorInt
    public static int dominant(@Nullable Drawable drawable, @ColorInt int fallback) {
        int[] pixels = render(drawable);
        return pixels == null ? fallback : fromPixels(pixels, fallback, false);
    }

    /**
     * {@link #dominant}, except that a near-white winner gives way to the most saturated bucket
     * that still has a real share of the vote: the colour a tint wash can actually be seen in.
     */
    @ColorInt
    public static int tint(@Nullable Drawable drawable, @ColorInt int fallback) {
        int[] pixels = render(drawable);
        return pixels == null ? fallback : fromPixels(pixels, fallback, true);
    }

    @Nullable
    private static int[] render(@Nullable Drawable drawable) {
        if (drawable == null) return null;
        final int size = SAMPLE_SIZE;
        try {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Rect oldBounds = drawable.copyBounds();
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            drawable.setBounds(oldBounds);
            int[] pixels = new int[size * size];
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
            bitmap.recycle();
            return pixels;
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** The vote itself, on ARGB pixels. */
    @VisibleForTesting
    @ColorInt
    public static int fromPixels(@NonNull int[] pixels, @ColorInt int fallback,
                                 boolean avoidPale) {
        int[] buckets = new int[4096];
        int bestBucket = -1;
        int bestWeight = 0;
        int opaqueCount = 0;
        int chromaticCount = 0;
        float[] hsv = new float[3];
        for (int pixel : pixels) {
            int alpha = (pixel >>> 24) & 0xFF;
            if (alpha < MIN_ALPHA) continue;
            opaqueCount++;
            hsv(pixel, hsv);
            if (hsv[1] < MIN_SATURATION || hsv[2] < MIN_VALUE) continue;
            chromaticCount++;
            int bucket = bucketOf(pixel);
            int weight = Math.round(alpha * hsv[1]);
            buckets[bucket] += weight;
            if (buckets[bucket] > bestWeight) {
                bestWeight = buckets[bucket];
                bestBucket = bucket;
            }
        }
        if (bestBucket < 0 || opaqueCount == 0 || chromaticCount * NEUTRAL_RATIO < opaqueCount) {
            return fallback;
        }
        int best = colorOf(bestBucket);
        if (!avoidPale || !isPale(best, hsv)) return best;
        int alternative = -1;
        float alternativeSaturation = 0f;
        int floor = Math.max(1, Math.round(bestWeight * SECONDARY_SHARE));
        for (int bucket = 0; bucket < buckets.length; bucket++) {
            if (bucket == bestBucket || buckets[bucket] < floor) continue;
            hsv(colorOf(bucket), hsv);
            if (hsv[1] > alternativeSaturation && !isPale(colorOf(bucket), new float[3])) {
                alternativeSaturation = hsv[1];
                alternative = bucket;
            }
        }
        return alternative >= 0 ? colorOf(alternative) : best;
    }

    private static boolean isPale(int color, @NonNull float[] scratch) {
        hsv(color, scratch);
        return scratch[1] < PALE_SATURATION && scratch[2] > PALE_VALUE;
    }

    private static int bucketOf(int pixel) {
        int r = ((pixel >> 16) & 0xFF) >> 4;
        int g = ((pixel >> 8) & 0xFF) >> 4;
        int b = (pixel & 0xFF) >> 4;
        return (r << 8) | (g << 4) | b;
    }

    @ColorInt
    private static int colorOf(int bucket) {
        int r = ((bucket >> 8) & 0xF) * 17;
        int g = ((bucket >> 4) & 0xF) * 17;
        int b = (bucket & 0xF) * 17;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Hue (degrees), saturation and value of an ARGB colour, as {@code Color.colorToHSV}. */
    @VisibleForTesting
    static void hsv(int color, @NonNull float[] out) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0f) hue = 0f;
        else if (max == r) hue = 60f * (((g - b) / delta) % 6f);
        else if (max == g) hue = 60f * (((b - r) / delta) + 2f);
        else hue = 60f * (((r - g) / delta) + 4f);
        if (hue < 0f) hue += 360f;
        out[0] = hue;
        out[1] = max == 0f ? 0f : delta / max;
        out[2] = max;
    }
}
