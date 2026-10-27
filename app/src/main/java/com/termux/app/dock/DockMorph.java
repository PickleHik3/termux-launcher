package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The dock's dress between two rests, for one frame of a crossing (ADR 0012, decision 3): the
 * leaving half's {@link DockLayout} at 0, the arriving half's at 1, and every measurement the user
 * sees interpolated in between, so the last frame of a slide already is the arriving dress. A band
 * one side carries and the other does not grows from nothing (its height interpolates against 0)
 * and fades with it; the Drawer tile, which takes a slot rather than a band, only fades.
 *
 * <p>The rows' height is the sum of the bands it shows ({@link #rowsPx()}), each band counted as
 * {@link DockLayout#combinedHeight} counts it (the bands of the pinned apps' row only while that
 * row shows), so the bands and the rows' height never disagree, even by a rounding.
 *
 * <p>A rail (the pinned apps standing up on a side) has no row lying down to morph: between a
 * rail and a row, or two rails, only the bands morph and the rail fades ({@link #railAlpha}); the
 * slot's icon, the row's width and its inset stay the lying-down side's, so the row draws as it is
 * laid out ({@link #rowMorphs}).
 *
 * <p>One value, reused frame to frame: {@link #between} is called once per crossing, {@link #at}
 * once per frame, and neither allocates. The rests themselves are Dock geometry's answers, never
 * changed here. Lens beads melt by the crossing's own share (SeamCrossing), not by this.
 *
 * <p>Each measurement is a linear interpolation rounded to the pixel, so it is exactly each rest
 * at the ends and monotonic in the fraction between them: a reversal runs the same numbers back.
 */
public final class DockMorph {

    @Nullable private DockLayout mFrom;
    @Nullable private DockLayout mTo;
    private boolean mFromTile;
    private boolean mToTile;
    private float mFraction;

    private int mSlotIconPx;
    private int mAppsRowIconPx;
    private int mAppsRowUsableWidthPx;
    private int mAppsRowEdgeInsetPx;
    private int mAppsBarHeightPx;
    private int mAzRowHeightPx;
    private int mIndicatorBandHeightPx;
    private int mPromptFieldBandPx;
    private int mWallStripBandPx;
    private int mRowsPx;
    private float mAppsAlpha;
    private float mAzAlpha;
    private float mIndicatorAlpha;
    private float mSearchFieldAlpha;
    private float mWallStripAlpha;
    private float mDrawerTileAlpha;
    private float mRailAlpha;
    private boolean mRowMorphs;

    /**
     * The two rests this morph runs between: {@code from} the leaving half's at rest, {@code to}
     * the arriving half's, with whether each carries the Drawer tile. Stands at {@code from}.
     */
    public void between(@NonNull DockLayout from, boolean fromTile, @NonNull DockLayout to,
                        boolean toTile) {
        mFrom = from;
        mTo = to;
        mFromTile = fromTile;
        mToTile = toTile;
        at(0f);
    }

    /** Whether a crossing has given the morph its rests; false once {@link #clear}ed. */
    public boolean active() {
        return mFrom != null && mTo != null;
    }

    /** The leaving half's rest; null while no crossing has given one. */
    @Nullable
    public DockLayout from() {
        return mFrom;
    }

    /** The arriving half's rest; null while no crossing has given one. */
    @Nullable
    public DockLayout to() {
        return mTo;
    }

    /** Lets the rests go: nothing is held between crossings. */
    public void clear() {
        mFrom = null;
        mTo = null;
        mFromTile = false;
        mToTile = false;
        mFraction = 0f;
        mSlotIconPx = 0;
        mAppsRowIconPx = 0;
        mAppsRowUsableWidthPx = 0;
        mAppsRowEdgeInsetPx = 0;
        mAppsBarHeightPx = 0;
        mAzRowHeightPx = 0;
        mIndicatorBandHeightPx = 0;
        mPromptFieldBandPx = 0;
        mWallStripBandPx = 0;
        mRowsPx = 0;
        mAppsAlpha = 0f;
        mAzAlpha = 0f;
        mIndicatorAlpha = 0f;
        mSearchFieldAlpha = 0f;
        mWallStripAlpha = 0f;
        mDrawerTileAlpha = 0f;
        mRailAlpha = 0f;
        mRowMorphs = false;
    }

    /**
     * The dress {@code fraction} of the way from the leaving rest to the arriving one, clamped to
     * 0..1. Does nothing before {@link #between}.
     */
    public void at(float fraction) {
        DockLayout a = mFrom;
        DockLayout b = mTo;
        if (a == null || b == null) return;
        float t = Float.isNaN(fraction) ? 0f : fraction < 0f ? 0f : fraction > 1f ? 1f : fraction;
        mFraction = t;
        // A rail has no row lying down to morph: the row keeps the lying-down side's numbers.
        mRowMorphs = !a.railActive && !b.railActive;
        DockLayout rowFrom = mRowMorphs || !a.railActive ? a : b;
        DockLayout rowTo = mRowMorphs ? b : rowFrom;
        mSlotIconPx = lerp(rowFrom.slotIconPx, rowTo.slotIconPx, t);
        mAppsRowIconPx = lerp(rowFrom.appsRowIconPx, rowTo.appsRowIconPx, t);
        mAppsRowUsableWidthPx = lerp(rowFrom.appsRowUsableWidthPx, rowTo.appsRowUsableWidthPx, t);
        mAppsRowEdgeInsetPx = lerp(rowFrom.appsRowEdgeInsetPx, rowTo.appsRowEdgeInsetPx, t);
        mAppsBarHeightPx = lerp(a.appsBarHeightPx, b.appsBarHeightPx, t);
        mAzRowHeightPx = lerp(a.azRowHeightPx, b.azRowHeightPx, t);
        mIndicatorBandHeightPx = lerp(appsBand(a, a.indicatorBandHeightPx),
            appsBand(b, b.indicatorBandHeightPx), t);
        mPromptFieldBandPx = lerp(appsBand(a, a.promptFieldBandPx),
            appsBand(b, b.promptFieldBandPx), t);
        mWallStripBandPx = lerp(appsBand(a, a.wallStripBandPx), appsBand(b, b.wallStripBandPx), t);
        // The rows' height is the bands', never a lerp of its own, so the two cannot disagree.
        mRowsPx = mAppsBarHeightPx + mPromptFieldBandPx + mWallStripBandPx
            + mIndicatorBandHeightPx + mAzRowHeightPx;
        mAppsAlpha = fade(a.appsBarHeightPx > 0, b.appsBarHeightPx > 0, t);
        mAzAlpha = fade(a.azRowHeightPx > 0, b.azRowHeightPx > 0, t);
        mIndicatorAlpha = fade(appsBand(a, a.indicatorBandHeightPx) > 0,
            appsBand(b, b.indicatorBandHeightPx) > 0, t);
        mSearchFieldAlpha = fade(a.promptFieldBandPx > 0, b.promptFieldBandPx > 0, t);
        mWallStripAlpha = fade(a.wallStripBandPx > 0, b.wallStripBandPx > 0, t);
        mDrawerTileAlpha = fade(mFromTile, mToTile, t);
        mRailAlpha = fade(a.railActive, b.railActive, t);
    }

    /**
     * A band of the pinned apps' row as the rows' height counts it: only while that row shows
     * ({@link DockLayout#combinedHeight}). The A-Z row is a row of its own and counts by itself.
     */
    private static int appsBand(@NonNull DockLayout layout, int bandPx) {
        return layout.appsBarHeightPx > 0 ? bandPx : 0;
    }

    /**
     * The dock rows' height a rest stands, less the extra keys row (as a crossing measures it):
     * {@link DockLayout#combinedHeight}, the sum of the bands {@link #at} interpolates.
     */
    public static int rowsPx(@NonNull DockLayout layout) {
        return layout.combinedHeight(0, false);
    }

    /** {@code a} to {@code b} by {@code t}, rounded to the pixel: exactly each end at 0 and 1. */
    public static int lerp(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        return Math.round(a + (b - a) * t);
    }

    /** How much of a band shows: 1 where both sides carry it, 0 where neither, else its share. */
    static float fade(boolean from, boolean to, float t) {
        if (from == to) return from ? 1f : 0f;
        return from ? 1f - t : t;
    }

    // ---- How the row draws a frame of it ----

    /**
     * Where the row draws a slot whose icon's centre is laid out {@code laidOutX} along it, for a
     * frame of the morph: the row is laid out at one rest ({@code laidOutInsetPx} in from the
     * dock's side, {@code laidOutWidthPx} wide) and drawn at the frame's ({@code frameInsetPx},
     * {@code frameWidthPx}), so every slot keeps its share of the row's width. In the row's own
     * coordinates, whose origin is the laid-out inset. Unknown widths draw the slot in place.
     */
    public static float slotCentreX(float laidOutX, int laidOutInsetPx, int laidOutWidthPx,
                                    int frameInsetPx, int frameWidthPx) {
        if (laidOutWidthPx <= 0 || frameWidthPx <= 0) return laidOutX;
        return (frameInsetPx - laidOutInsetPx) + laidOutX * frameWidthPx / (float) laidOutWidthPx;
    }

    /**
     * How much the row scales an icon rendered {@code renderedPx} across to draw it
     * {@code framePx} across; 1 for an unknown size.
     */
    public static float iconScale(int framePx, int renderedPx) {
        if (framePx <= 0 || renderedPx <= 0) return 1f;
        return framePx / (float) renderedPx;
    }

    // ---- The frame's dress ----

    public float fraction() { return mFraction; }
    /** The icon one pinned slot draws ({@link DockLayout#slotIconPx}). */
    public int slotIconPx() { return mSlotIconPx; }
    public int appsRowIconPx() { return mAppsRowIconPx; }
    /** The width the pinned row shares between its slots ({@link DockLayout#appsRowUsableWidthPx}). */
    public int appsRowUsableWidthPx() { return mAppsRowUsableWidthPx; }
    public int appsRowEdgeInsetPx() { return mAppsRowEdgeInsetPx; }
    public int appsBarHeightPx() { return mAppsBarHeightPx; }
    public int azRowHeightPx() { return mAzRowHeightPx; }
    public int indicatorBandHeightPx() { return mIndicatorBandHeightPx; }
    /** The Search field's band ({@link DockLayout#promptFieldBandPx}). */
    public int promptFieldBandPx() { return mPromptFieldBandPx; }
    /** The Wall strip's band ({@link DockLayout#wallStripBandPx}). */
    public int wallStripBandPx() { return mWallStripBandPx; }
    /** The dock rows' height, less the extra keys row: the sum of the bands above. */
    public int rowsPx() { return mRowsPx; }
    public float appsAlpha() { return mAppsAlpha; }
    public float azAlpha() { return mAzAlpha; }
    /** How much of the page ticks' band shows. */
    public float indicatorAlpha() { return mIndicatorAlpha; }
    public float searchFieldAlpha() { return mSearchFieldAlpha; }
    public float wallStripAlpha() { return mWallStripAlpha; }
    public float drawerTileAlpha() { return mDrawerTileAlpha; }
    /** How much of a rail shows, where one side stands the pinned apps up and the other not. */
    public float railAlpha() { return mRailAlpha; }
    /**
     * Whether the row lying down morphs its icons and slots: false wherever either side stands
     * the pinned apps as a rail, and the row then draws as it is laid out.
     */
    public boolean rowMorphs() { return mRowMorphs; }
}
