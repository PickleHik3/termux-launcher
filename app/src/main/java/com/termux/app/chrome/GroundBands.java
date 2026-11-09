package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * The wall's ground strip as a list of solid bands (page-chrome spec, "C: ground per page"): each
 * page paints its own ground, and between two pages whose grounds differ a soft seam travels with
 * the page edge during a slide. Pure and reusable: {@link #set} fills the same arrays every call,
 * so a draw loop allocates nothing.
 *
 * <p>Coordinates are the wall's own, in px from its left edge; the viewport is {@code [0, stride]}
 * (one page wide). Stop {@code k} sits at {@code (k - current) * stride + offsetPx}: {@code
 * offsetPx} is signed as {@code PaneWallLayout.offsetPx} reports it, positive when the pages sit to
 * the right of where they will land. At rest the viewport is exactly one band, the current stop's
 * ground.
 *
 * <p>The seam between two differing neighbours is centred on their edge and is {@code featherPx}
 * wide, but never reaches past the viewport: its half-width is capped at the edge's distance to the
 * nearer viewport side, so a page edge at the screen edge (the page at rest) has no seam at all
 * and the seam grows as the edge travels in. It is drawn as {@link #SEAM_STEPS} solid bands of
 * premultiplied-interpolated colour.
 *
 * <p>The colour under a rect is the colour at its centre ({@link #groundUnder}): ink needs a
 * stable answer, and the seam is narrow (about 24 dp), so the centre is within a few steps of
 * what an average would say and does not shimmer with the rect's width.
 */
public final class GroundBands {

    /** How many solid bands a seam is cut into. */
    public static final int SEAM_STEPS = 8;
    /** The most bands one {@link #set} can produce: a band each side of the seam and its steps. */
    public static final int MAX_BANDS = SEAM_STEPS + 2;

    private final float[] mStart = new float[MAX_BANDS];
    private final float[] mEnd = new float[MAX_BANDS];
    private final int[] mColor = new int[MAX_BANDS];
    private int mCount;

    public int count() { return mCount; }

    public float start(int band) { return mStart[band]; }

    public float end(int band) { return mEnd[band]; }

    public int color(int band) { return mColor[band]; }

    /**
     * Recomputes the bands.
     *
     * @param grounds each stop's ground colour (ARGB), in wall order
     * @param stops how many of {@code grounds} count (at least 1)
     * @param current the index of the stop the wall rests on
     * @param stride one page's width in px
     * @param offsetPx the wall's signed offset from the current stop's rest, in px
     * @param featherPx the seam's full width in px
     */
    public void set(@NonNull int[] grounds, int stops, int current, float stride, float offsetPx,
                    float featherPx) {
        mCount = 0;
        if (stops <= 0) return;
        int cur = Math.max(0, Math.min(stops - 1, current));
        if (stride <= 0f || stops == 1) {
            add(0f, Math.max(stride, 0f), grounds[cur]);
            return;
        }
        // The stop under the viewport's left edge: the one whose left edge is at or before 0.
        int first = cur + (int) Math.floor(-offsetPx / stride);
        first = Math.max(0, Math.min(stops - 1, first));
        float edge = (first + 1 - cur) * stride + offsetPx;   // the right edge of `first`
        int a = grounds[first];
        if (first + 1 >= stops || edge <= 0f || edge >= stride || a == grounds[first + 1]) {
            // No edge strictly inside the viewport (or nothing to tell apart across it).
            int paint = edge >= stride || first + 1 >= stops ? a : grounds[first + 1];
            if (edge <= 0f && first + 1 < stops) paint = grounds[first + 1];
            // An edge inside with equal grounds paints `a` either way.
            add(0f, stride, paint);
            return;
        }
        int b = grounds[first + 1];
        float half = Math.max(0f, Math.min(featherPx / 2f, Math.min(edge, stride - edge)));
        if (half <= 0f) {
            add(0f, edge, a);
            add(edge, stride, b);
            return;
        }
        add(0f, edge - half, a);
        float step = 2f * half / SEAM_STEPS;
        for (int i = 0; i < SEAM_STEPS; i++) {
            float t = (i + 0.5f) / SEAM_STEPS;
            add(edge - half + i * step, edge - half + (i + 1) * step, lerp(a, b, t));
        }
        add(edge + half, stride, b);
    }

    private void add(float start, float end, int color) {
        if (end <= start) return;
        mStart[mCount] = start;
        mEnd[mCount] = end;
        mColor[mCount] = color;
        mCount++;
    }

    /**
     * The ground under a rect spanning {@code [left, right]} (wall px): the colour at its centre.
     * A centre outside the viewport takes the nearest band; no bands answer transparent.
     */
    public int groundUnder(float left, float right) {
        if (mCount == 0) return 0;
        float x = (left + right) / 2f;
        for (int i = 0; i < mCount; i++) {
            if (x < mEnd[i]) return mColor[i];
        }
        return mColor[mCount - 1];
    }

    /** Straight-alpha ARGB blended at {@code t} (0 = {@code a}), through premultiplied values. */
    static int lerp(int a, int b, float t) {
        float aa = (a >>> 24) / 255f;
        float ba = (b >>> 24) / 255f;
        float alpha = aa + (ba - aa) * t;
        if (alpha <= 0f) return 0;
        int out = Math.round(alpha * 255f) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float ap = ((a >> shift) & 0xFF) * aa;
            float bp = ((b >> shift) & 0xFF) * ba;
            int c = Math.round((ap + (bp - ap) * t) / alpha);
            out |= Math.min(255, Math.max(0, c)) << shift;
        }
        return out;
    }
}
