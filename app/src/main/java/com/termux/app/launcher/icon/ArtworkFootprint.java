package com.termux.app.launcher.icon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How much of its square an icon's visible artwork spans, and the one scale a whole icon pack is
 * drawn at so its icons read as big as stock icons. Pure arithmetic over ARGB pixels, so it is
 * tested without a device; {@link DrawablePixels} rasterises and applies the answer.
 *
 * <p>Icon-pack PNGs usually carry a transparent margin of their own, and every surface draws every
 * source into the same box: a pack icon came out smaller than the stock adaptive icon beside it,
 * whose mask reaches the edges of its box. The margin is measured over a sample of the pack and
 * the pack is scaled as one, about the centre of the box, so the glyphs inside it keep the sizes
 * and the placement its designer gave them relative to one another.
 */
public final class ArtworkFootprint {

    /**
     * The raster artwork is measured at: one pixel is about 1% of the icon's side, finer than any
     * margin a pack leaves.
     */
    public static final int MEASURE_PX = 96;

    /**
     * The most a pack is ever enlarged. A pack whose typical icon spans under 62% of its box is a
     * deliberately small style or a measure gone wrong; past this, one odd pack would blow its
     * icons up over their neighbours.
     */
    public static final float MAX_PACK_SCALE = 1.6f;

    /**
     * A pixel counts as artwork from this share of the icon's strongest alpha up, which leaves a
     * soft drop shadow out of the measure while keeping the antialiased edge of the shape.
     */
    static final float VISIBLE_ALPHA_FRACTION = 0.25f;
    /** And never from below this alpha, so an icon drawn wholly faint still has an edge. */
    static final int MIN_VISIBLE_ALPHA = 8;

    private ArtworkFootprint() {
    }

    /**
     * The visible box of {@code width} x {@code height} ARGB pixels as {@code {left, top, right,
     * bottom}}, right and bottom exclusive, or null when nothing in it is visible.
     */
    @Nullable
    public static int[] visibleBounds(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) return null;
        int maxAlpha = 0;
        for (int i = 0; i < width * height; i++) {
            maxAlpha = Math.max(maxAlpha, argb[i] >>> 24);
        }
        int threshold = Math.max(MIN_VISIBLE_ALPHA, Math.round(maxAlpha * VISIBLE_ALPHA_FRACTION));
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                if ((argb[row + x] >>> 24) < threshold) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return right >= left && bottom >= top
            ? new int[] {left, top, right + 1, bottom + 1}
            : null;
    }

    /**
     * How much of its square the visible box spans along its longer side: 1 for artwork that
     * reaches both edges, less for artwork inside a margin.
     */
    public static float extent(@NonNull int[] bounds, int width, int height) {
        return Math.max((bounds[2] - bounds[0]) / (float) width,
            (bounds[3] - bounds[1]) / (float) height);
    }

    /**
     * Which of {@code size} entries to measure, at most {@code max} of them, spread evenly across
     * the list rather than its head, which an appfilter often groups by app family.
     */
    @NonNull
    public static int[] sampleIndices(int size, int max) {
        int count = Math.max(0, Math.min(size, max));
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = (int) ((long) i * size / count);
        }
        return indices;
    }

    /** The median of the first {@code count} values; 0 when there are none. */
    public static float median(@NonNull float[] values, int count) {
        int n = Math.max(0, Math.min(count, values.length));
        if (n == 0) return 0f;
        float[] sorted = java.util.Arrays.copyOf(values, n);
        java.util.Arrays.sort(sorted);
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2f;
    }

    /**
     * The one scale a pack is drawn at: what takes its typical icon's extent to the stock icon's,
     * never below 1 (a pack is never made smaller than it ships) and never past
     * {@link #MAX_PACK_SCALE}. Under one measured pixel of growth it is exactly 1, so a pack with
     * almost no margin is left untouched. 1 when either extent is unknown.
     */
    public static float packScale(float stockExtent, float packExtent) {
        if (stockExtent <= 0f || packExtent <= 0f) return 1f;
        float scale = Math.min(MAX_PACK_SCALE, stockExtent / packExtent);
        return scale - 1f < 1f / MEASURE_PX ? 1f : scale;
    }

    /**
     * Where the whole artwork is drawn at {@code scale} about the centre of its box, as
     * {@code {left, top, right, bottom}} fractions of the box. The margin around the artwork is
     * what falls outside; the artwork keeps its own place relative to the centre.
     */
    @NonNull
    public static float[] placementAboutCentre(float scale) {
        float half = scale / 2f;
        return new float[] {0.5f - half, 0.5f - half, 0.5f + half, 0.5f + half};
    }
}
