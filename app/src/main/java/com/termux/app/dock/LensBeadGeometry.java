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

    /**
     * How much more the dock's apps row is inset on each side under Lens beads, dp. Seven icons
     * on a 411 dp phone with the row edge to edge are 58.7 dp slots, and a bead held by its
     * neighbour's gap then reaches to the screen's edge; 9 dp a side makes them 56.1 dp, so a
     * bead is about 1.1 of the 46 dp icon and the end ones stand at least
     * {@link #MIN_SCREEN_EDGE_DP} from the edge.
     * The row's slots are even and start at its own edge, so the end bead's margin is this inset
     * plus half the neighbour's gap ({@link #MIN_GAP_DP}) at least, on every row.
     */
    public static final float ROW_SIDE_INSET_DP = 9f;
    /** The least room between an end bead and the screen's edge the inset buys, dp. */
    public static final float MIN_SCREEN_EDGE_DP = ROW_SIDE_INSET_DP + MIN_GAP_DP / 2f;

    /**
     * The least a bead is of its icon where the row's slots are what hold it: the icon is held
     * so a bead this much larger fits its slot with {@link #MIN_GAP_DP} to its neighbour.
     */
    public static final float MIN_FIT_SCALE = 1.1f;

    private LensBeadGeometry() {}

    /** {@link #ROW_SIDE_INSET_DP} in px. */
    public static int rowSideInsetPx(float density) {
        return Math.round(Math.max(0f, density) * ROW_SIDE_INSET_DP);
    }

    /**
     * The icon a row lying down draws under Lens beads, for the size it would draw
     * ({@code iconPx}) in slots {@code slotPx} wide: no larger than leaves a bead of
     * {@link #MIN_FIT_SCALE} of it {@link #MIN_GAP_DP} from its neighbour's. Without it the row's
     * own fit (the slot less 4 dp of air) hands icons the beads cannot cover once the slots are
     * narrow, and {@link #compute} gives the slab: seven 51 dp icons in the 56 dp slots the
     * {@link #ROW_SIDE_INSET_DP} inset leaves on a 411 dp phone. A pixel is kept back for the
     * row's slots, which are whole pixels and not all the same.
     */
    public static int fitIconPx(int iconPx, float slotPx, float density) {
        if (iconPx <= 0 || !(slotPx > 0f)) return iconPx;
        float room = slotPx - 1f - MIN_GAP_DP * Math.max(0f, density);
        int fit = (int) Math.floor(room / MIN_FIT_SCALE);
        return Math.max(1, Math.min(iconPx, fit));
    }

    /**
     * Whether the dock's apps row stands where Lens beads can be drawn, before its icons are
     * measured: the row shown, lying along the bottom (no rail), alone on the dock's glass (the
     * A–Z row or another band on the same plank would lose its glass to the beads), the Docked
     * frame not standing in for the dock's sheet, and the keyboard not sharing that glass. A
     * hidden A–Z row is no band, whatever edge it was last put on.
     */
    public static boolean rowWearsBeads(@NonNull com.termux.app.place.PlaceLayout layout,
                                        boolean sheetStandsDownOnFrame,
                                        boolean keyboardSharesGlass) {
        if (!com.termux.app.place.PlaceChromePolicy.appsShown(layout)
            || com.termux.app.place.PlaceChromePolicy.appsRailShown(layout)
            || com.termux.app.place.PlaceChromePolicy.appsEdge(layout)
                != com.termux.app.place.PlaceLayout.Edge.BOTTOM) return false;
        if (sheetStandsDownOnFrame || keyboardSharesGlass) return false;
        return com.termux.app.terminal.AccessoryStackLayoutPolicy.plankBands(
            com.termux.app.place.EdgeStackPolicy.stack(layout,
                com.termux.app.place.PlaceLayout.Edge.BOTTOM)).size() <= 1;
    }

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

        /** The same beads at radius {@code radiusPx}. */
        @NonNull
        public Beads withRadius(float radiusPx) {
            return radiusPx == this.radiusPx ? this
                : new Beads(mCentersX, centerY, radiusPx, iconHalfPx);
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
        return follow(rest, offsetPx, Float.NaN, neighbourCentersX, neighbourCount, left, right,
            scale);
    }

    /**
     * {@link #follow(Beads, float, float[], int, float, float, float)} for a row holding the
     * Drawer tile (#97), which is no page's and stands still while the pages move: the rest's
     * bead nearest {@code stillCenterX} (within a radius of it) stays where it was, under the
     * tile, while every other one rides with its icon. NaN is a row without the tile.
     */
    @NonNull
    public static Beads follow(@NonNull Beads rest, float offsetPx, float stillCenterX,
                               @NonNull float[] neighbourCentersX, int neighbourCount,
                               float left, float right, float scale) {
        float s = Float.isNaN(scale) ? 1f : Math.max(0f, Math.min(1f, scale));
        // Never quite nothing: a radius of 0 is the program's "no beads", which is the slab.
        float radius = Math.max(0.5f, rest.radiusPx * s);
        int neighbours = Math.max(0, Math.min(neighbourCount, neighbourCentersX.length));
        int still = -1;
        if (!Float.isNaN(stillCenterX)) {
            float nearest = rest.radiusPx;
            for (int i = 0; i < rest.count(); i++) {
                float away = Math.abs(rest.centerX(i) - stillCenterX);
                if (away <= nearest) {
                    nearest = away;
                    still = i;
                }
            }
        }
        float[] all = new float[rest.count() + neighbours];
        int n = 0;
        for (int i = 0; i < rest.count(); i++) {
            float cx = rest.centerX(i) + (i == still ? 0f : offsetPx);
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
     * The share of Home's slide over which the beads melt into the slab, counted from the
     * Workspace's side: from Home's share 1 down to this they swell, below it they cover the slab
     * whole, so the hand-over to the slab at share 0 changes nothing the clip lets through.
     */
    public static final float MELT_COVERED_SHARE = 0.15f;

    /**
     * The radius at which the beads, each clipped to the slab {@code width} by {@code height}
     * (the beads' surface), cover all of it: the farthest any point of the slab stands from
     * the nearest bead's centre, sideways (half the widest gap, or an end bead to its edge) and
     * up and down (the row's line to the slab's top or bottom), plus a pixel.
     */
    public static float coverRadiusPx(@NonNull Beads beads, float width, float height) {
        int n = beads.count();
        if (n <= 0) return beads.radiusPx;
        float[] sorted = beads.mCentersX.clone();
        Arrays.sort(sorted);
        float dx = Math.max(sorted[0], width - sorted[n - 1]);
        for (int i = 1; i < n; i++) dx = Math.max(dx, (sorted[i] - sorted[i - 1]) * 0.5f);
        float dy = Math.max(beads.centerY, height - beads.centerY);
        dx = Math.max(0f, dx);
        dy = Math.max(0f, dy);
        return Math.max(beads.radiusPx, (float) Math.sqrt(dx * dx + dy * dy) + 1f);
    }

    /**
     * The beads' radius while a slide across the seam carries Home's dock away or back:
     * {@code homeShare} is Home's share of the slide (1 resting on Home, 0 on the Workspace).
     * At 1 the beads are their own size; toward {@link #MELT_COVERED_SHARE} they swell on a
     * smooth step to {@code coverPx}, where, clipped to the slab, they are the slab; below it
     * they stay there.
     */
    public static float meltRadiusPx(float restPx, float coverPx, float homeShare) {
        return restPx + (Math.max(restPx, coverPx) - restPx) * meltProgress(homeShare);
    }

    /**
     * How far the beads have melted into the slab at Home's share {@code homeShare}, 0 (their own
     * size) to 1 (covering it, from {@link #MELT_COVERED_SHARE} down), on the smooth step
     * {@link #meltRadiusPx} swells them by. Their shadows and glow on the wallpaper fade by the
     * rest of it ({@link #beadShelfAlpha}).
     */
    public static float meltProgress(float homeShare) {
        float share = Float.isNaN(homeShare) ? 1f : Math.max(0f, Math.min(1f, homeShare));
        float t = (1f - share) / (1f - MELT_COVERED_SHARE);
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    /**
     * How much of the beads' own shadows and glow on the wallpaper show at Home's share
     * {@code homeShare}: all of them at rest, none once the beads cover the slab. The shadows sit
     * under each bead's lower edge, which the swelling glass leaves behind; kept whole they were
     * left on the wallpaper as a pale line along the dock's foot after the beads had become the
     * slab (pass 6, outbound).
     */
    public static float beadShelfAlpha(float homeShare) {
        return 1f - meltProgress(homeShare);
    }

    /**
     * Home's share the beads melt by while Home's dock rows leave the screen across the seam (a
     * dip or a slide out, from Home). The stack sinks below its own laid-out bottom, which cuts
     * whatever passes it flat; beads still melting by Home's share of the chrome's way were cut
     * there as beads, a flat line across their lower halves (pass 6). So they melt over the
     * stretch of the wall's way where the chrome still rests, {@code leadIn} of it (the chrome's
     * landing margin), and are the slab, cut by nothing but its own outline, before the stack
     * moves; from there it sinks as the slab, as it rises on the way back. One function of the
     * wall's position, so a drag that turns back un-melts them through the same numbers.
     *
     * @param awayFraction how far the wall itself has gone from Home's half, 0 to 1
     * @param leadIn       the stretch of the wall's way over which the chrome rests
     */
    public static float leavingMeltShare(float awayFraction, float leadIn) {
        if (Float.isNaN(awayFraction)) return 1f;
        if (leadIn <= 0f) return awayFraction > 0f ? 0f : 1f;
        return 1f - Math.max(0f, Math.min(1f, awayFraction / leadIn));
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
