package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

/**
 * Where the Lens beads stand ({@link DockSurfaceSpec#LENS_BEADS}): one round bead of glass under
 * each icon of Home's dock row, about {@link #BEAD_SCALE} of the icon, centred on it.
 *
 * <p>The beads follow the row's own slots, so they keep its rhythm and its order: the row is the
 * user's order, never sorted, and a bead is wherever its icon was laid out. A bead is held so it
 * leaves {@link #MIN_GAP_DP} to its neighbour and stays {@link #EDGE_CLEARANCE_DP} inside the
 * glass's own bounds (the surface it is drawn on clips there). All beads of a row are one size,
 * so the row reads as one set.</p>
 *
 * <p>A row that cannot be drawn as beads answers null and the dock keeps its slab: nothing to
 * draw, more icons than the glass program holds ({@link #MAX_BEADS}), or so little room that a
 * bead would be smaller than its icon.</p>
 */
public final class LensBeadGeometry {

    /** A bead's diameter as a share of its icon: 58 dp gives about 70 dp. */
    public static final float BEAD_SCALE = 1.2f;
    /** The least space left between two beads, dp, so each reads as its own lens. */
    public static final float MIN_GAP_DP = 6f;
    /** How far inside the glass's bounds a bead stays, dp, so its edge is never clipped. */
    public static final float EDGE_CLEARANCE_DP = 1f;
    /**
     * The most beads a row at rest is drawn with; a row of more keeps the slab. The program reads
     * {@link #MAX_DRAWN_BEADS}, so a page swipe has room for the neighbouring page's beads too.
     */
    public static final int MAX_BEADS = 8;
    /**
     * The most beads drawn at once ({@code uBeadXA}, {@code uBeadXB}, {@code uBeadXC}: three
     * float4s, {@code GlassRefraction.BEAD_SLOTS}): a full row at rest and the two or three of
     * the neighbouring page a swipe carries in beside it.
     */
    public static final int MAX_DRAWN_BEADS = 12;
    /** Where {@link #follow} parks its one bead when none is in reach: far off every surface. */
    static final float PARKED_X = -1.0e6f;

    private LensBeadGeometry() {}

    /** One row of beads, in the coordinates of the surface they were computed for. */
    public static final class Beads {
        /** Each bead's centre x, left to right as the row laid them out. */
        @NonNull private final float[] mCentersX;
        /** The row's centre line. */
        public final float centerY;
        /** Every bead's radius. */
        public final float radiusPx;
        /** Half the icon's size: where its picture's top and bottom stand about the centre line. */
        public final float iconHalfPx;

        Beads(@NonNull float[] centersX, float centerY, float radiusPx, float iconHalfPx) {
            mCentersX = centersX;
            this.centerY = centerY;
            this.radiusPx = radiusPx;
            this.iconHalfPx = iconHalfPx;
        }

        public int count() {
            return mCentersX.length;
        }

        public float centerX(int index) {
            return mCentersX[index];
        }

        /** The same beads in a surface whose origin sits {@code (-dx, -dy)} from this one's. */
        @NonNull
        public Beads offset(float dx, float dy) {
            float[] moved = new float[mCentersX.length];
            for (int i = 0; i < moved.length; i++) moved[i] = mCentersX[i] + dx;
            return new Beads(moved, centerY + dy, radiusPx, iconHalfPx);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Beads)) return false;
            Beads that = (Beads) o;
            return centerY == that.centerY && radiusPx == that.radiusPx
                && iconHalfPx == that.iconHalfPx && Arrays.equals(mCentersX, that.mCentersX);
        }

        @Override
        public int hashCode() {
            int h = Arrays.hashCode(mCentersX);
            h = 31 * h + Float.floatToIntBits(centerY);
            h = 31 * h + Float.floatToIntBits(radiusPx);
            return 31 * h + Float.floatToIntBits(iconHalfPx);
        }

        @NonNull
        @Override
        public String toString() {
            return "Beads{" + Arrays.toString(mCentersX) + " y=" + centerY + " r=" + radiusPx + "}";
        }
    }

    /**
     * The beads as the row draws its icons while a page swipe carries them, so each bead rides
     * under its own icon and no frame shows an icon off its bead or a bead with no icon: the
     * beads at rest ({@code rest}) moved by {@code offsetPx}, then one under each icon of the
     * neighbouring page the swipe stages ({@code neighbourCentersX}, {@code neighbourCount} of
     * them, already in the surface's coordinates), every one the rest's size times
     * {@code scale} (the row's fade while a switch fades it, 1 otherwise). Only the beads whose
     * circle reaches into {@code left..right} (where the row's drawing is clipped) are kept,
     * left to right; past {@link #MAX_DRAWN_BEADS} the ones nearest the middle. With none in
     * reach one bead is parked far off, so the glass stays beads with nothing drawn rather than
     * dropping back to the slab for a frame.
     */
    @NonNull
    public static Beads follow(@NonNull Beads rest, float offsetPx,
                               @NonNull float[] neighbourCentersX, int neighbourCount,
                               float left, float right, float scale) {
        float s = Float.isNaN(scale) ? 1f : Math.max(0f, Math.min(1f, scale));
        // Never quite nothing: a radius of 0 is the program's "no beads", which is the slab.
        float radius = Math.max(0.5f, rest.radiusPx * s);
        int neighbours = Math.max(0, Math.min(neighbourCount, neighbourCentersX.length));
        float[] all = new float[rest.count() + neighbours];
        int n = 0;
        for (int i = 0; i < rest.count(); i++) {
            float cx = rest.centerX(i) + offsetPx;
            if (cx + radius > left && cx - radius < right) all[n++] = cx;
        }
        for (int i = 0; i < neighbours; i++) {
            float cx = neighbourCentersX[i];
            if (cx + radius > left && cx - radius < right) all[n++] = cx;
        }
        if (n == 0) {
            return new Beads(new float[] {PARKED_X}, rest.centerY, radius, rest.iconHalfPx);
        }
        float[] kept = Arrays.copyOf(all, n);
        if (n > MAX_DRAWN_BEADS) {
            // Keep the ones nearest the middle: the ones at the ends are leaving or arriving.
            final float middle = (left + right) * 0.5f;
            Float[] boxed = new Float[n];
            for (int i = 0; i < n; i++) boxed[i] = kept[i];
            Arrays.sort(boxed, (a, b) -> Float.compare(Math.abs(a - middle), Math.abs(b - middle)));
            kept = new float[MAX_DRAWN_BEADS];
            for (int i = 0; i < MAX_DRAWN_BEADS; i++) kept[i] = boxed[i];
        }
        Arrays.sort(kept);
        return new Beads(kept, rest.centerY, radius, rest.iconHalfPx);
    }

    /**
     * The beads for a row whose icons' boxes are {@code boxes} (left, top, right, bottom per
     * icon, {@code count} of them, in the surface's coordinates), on a surface spanning
     * {@code left..right} by {@code top..bottom} there, or null when the row cannot be drawn as
     * beads.
     */
    @Nullable
    public static Beads compute(@NonNull float[] boxes, int count, float left, float top,
                                float right, float bottom, float density) {
        if (count <= 0 || count > MAX_BEADS || boxes.length < count * 4) return null;
        float d = Math.max(0f, density);
        float[] centers = new float[count];
        float iconPx = 0f;
        float centerYSum = 0f;
        for (int i = 0; i < count; i++) {
            float l = boxes[i * 4], t = boxes[i * 4 + 1], r = boxes[i * 4 + 2], b = boxes[i * 4 + 3];
            if (!(r > l) || !(b > t)) return null;
            centers[i] = (l + r) * 0.5f;
            centerYSum += (t + b) * 0.5f;
            iconPx = Math.max(iconPx, Math.max(r - l, b - t));
        }
        float centerY = centerYSum / count;
        float diameter = BEAD_SCALE * iconPx;
        // Never into the neighbour's gap: the closest two icons set the row's pitch.
        float[] sorted = centers.clone();
        Arrays.sort(sorted);
        for (int i = 1; i < count; i++) {
            diameter = Math.min(diameter, sorted[i] - sorted[i - 1] - MIN_GAP_DP * d);
        }
        // Never past the glass's own bounds, where the surface clips.
        float clearance = EDGE_CLEARANCE_DP * d;
        diameter = Math.min(diameter, 2f * (centerY - top - clearance));
        diameter = Math.min(diameter, 2f * (bottom - clearance - centerY));
        diameter = Math.min(diameter, 2f * (sorted[0] - left - clearance));
        diameter = Math.min(diameter, 2f * (right - clearance - sorted[count - 1]));
        // A bead smaller than its icon is no lens: the slab is drawn instead.
        if (!(diameter >= iconPx)) return null;
        return new Beads(centers, centerY, diameter * 0.5f, iconPx * 0.5f);
    }
}
