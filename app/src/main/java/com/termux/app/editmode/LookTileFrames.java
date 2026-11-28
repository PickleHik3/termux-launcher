package com.termux.app.editmode;

import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Look tiles' own pictures of the wallpaper: one small crop of a frame the blur cache holds,
 * blurred at each Look's radius, so every tile shows its own glass whichever Look is worn. Made
 * once per card (a few kilobytes each) and let go with it.
 */
final class LookTileFrames {

    /** A tile picture's side, px: about a tile's size on a phone, so nothing is scaled up much. */
    static final int SIDE_PX = 64;

    private LookTileFrames() { }

    /**
     * {@code source}'s tile-sized crop (the part under the card), blurred at {@code blurDp} as a
     * glass of that radius draws it on a screen {@code screenWidthPx} wide at {@code density}.
     */
    @NonNull
    static Bitmap of(@NonNull Bitmap source, int blurDp, float density, int screenWidthPx) {
        Rect crop = new Rect();
        LookTileView.cropCentre(source.getWidth(), source.getHeight(), SIDE_PX, SIDE_PX, crop);
        Bitmap cut = Bitmap.createBitmap(source, crop.left, crop.top, crop.width(), crop.height());
        Bitmap tile = Bitmap.createScaledBitmap(cut, SIDE_PX, SIDE_PX, true);
        if (cut != tile && cut != source) cut.recycle();
        if (tile == source) tile = source.copy(Bitmap.Config.ARGB_8888, true);
        int radius = radiusPx(blurDp, density, screenWidthPx, source.getWidth(), crop.width());
        if (radius > 0) {
            int[] pixels = new int[SIDE_PX * SIDE_PX];
            tile.getPixels(pixels, 0, SIDE_PX, 0, 0, SIDE_PX, SIDE_PX);
            blur(pixels, SIDE_PX, SIDE_PX, radius);
            if (!tile.isMutable()) tile = tile.copy(Bitmap.Config.ARGB_8888, true);
            tile.setPixels(pixels, 0, SIDE_PX, 0, 0, SIDE_PX, SIDE_PX);
        }
        return tile;
    }

    /**
     * A blur of {@code blurDp} in a tile picture's pixels: the screen's px for those dp, scaled as
     * the frame scales the screen ({@code frameWidthPx} of {@code screenWidthPx}) and as the
     * picture scales its crop ({@link #SIDE_PX} of {@code cropWidthPx}).
     */
    static int radiusPx(int blurDp, float density, int screenWidthPx, int frameWidthPx, int cropWidthPx) {
        if (blurDp <= 0 || screenWidthPx <= 0 || cropWidthPx <= 0) return 0;
        float screenPx = blurDp * density;
        float framePx = screenPx * frameWidthPx / (float) screenWidthPx;
        return Math.max(0, Math.round(framePx * SIDE_PX / (float) cropWidthPx));
    }

    /** Two passes of a box blur of {@code radius} over ARGB {@code pixels}, in place (close to a Gaussian). */
    static void blur(@NonNull int[] pixels, int width, int height, int radius) {
        if (radius <= 0 || width <= 0 || height <= 0) return;
        int[] scratch = new int[pixels.length];
        for (int pass = 0; pass < 2; pass++) {
            boxRows(pixels, scratch, width, height, radius);
            boxColumns(scratch, pixels, width, height, radius);
        }
    }

    private static void boxRows(int[] in, int[] out, int width, int height, int radius) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) out[row + x] = average(in, row, 1, width, x, radius);
        }
    }

    private static void boxColumns(int[] in, int[] out, int width, int height, int radius) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) out[y * width + x] = average(in, x, width, height, y, radius);
        }
    }

    /** The mean of the {@code count}-long line at {@code start} by {@code step}, around {@code at}, edges clamped. */
    private static int average(int[] line, int start, int step, int count, int at, int radius) {
        long a = 0, r = 0, g = 0, b = 0;
        int n = 0;
        for (int i = at - radius; i <= at + radius; i++) {
            int c = line[start + step * Math.max(0, Math.min(count - 1, i))];
            a += c >>> 24;
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
            n++;
        }
        return (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /** How much {@code pixels}' brightness varies: a sharper picture varies more. */
    static double variance(@Nullable int[] pixels) {
        if (pixels == null || pixels.length == 0) return 0;
        double sum = 0, squares = 0;
        for (int c : pixels) {
            double v = ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
            sum += v;
            squares += v * v;
        }
        double mean = sum / pixels.length;
        return squares / pixels.length - mean * mean;
    }
}
