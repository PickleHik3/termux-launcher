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
    /** The most beads the glass program draws ({@code uBeadXA} and {@code uBeadXB}: two float4s). */
    public static final int MAX_BEADS = 8;

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
