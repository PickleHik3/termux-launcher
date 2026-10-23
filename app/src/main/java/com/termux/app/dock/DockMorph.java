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
 * <p>One value, reused frame to frame: {@link #between} is called once per crossing (and again
 * when it is retargeted toward another rest), {@link #at} once per frame, and neither allocates.
 * The rests themselves are Dock geometry's answers, never changed here.
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
    private float mSearchFieldAlpha;
    private float mWallStripAlpha;
    private float mDrawerTileAlpha;
    private float mBeadShare;

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
        mSearchFieldAlpha = 0f;
        mWallStripAlpha = 0f;
        mDrawerTileAlpha = 0f;
        mBeadShare = 0f;
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
        mSlotIconPx = lerp(a.slotIconPx, b.slotIconPx, t);
        mAppsRowIconPx = lerp(a.appsRowIconPx, b.appsRowIconPx, t);
        mAppsRowUsableWidthPx = lerp(a.appsRowUsableWidthPx, b.appsRowUsableWidthPx, t);
        mAppsRowEdgeInsetPx = lerp(a.appsRowEdgeInsetPx, b.appsRowEdgeInsetPx, t);
        mAppsBarHeightPx = lerp(a.appsBarHeightPx, b.appsBarHeightPx, t);
        mAzRowHeightPx = lerp(a.azRowHeightPx, b.azRowHeightPx, t);
        mIndicatorBandHeightPx = lerp(a.indicatorBandHeightPx, b.indicatorBandHeightPx, t);
        mPromptFieldBandPx = lerp(a.promptFieldBandPx, b.promptFieldBandPx, t);
        mWallStripBandPx = lerp(a.wallStripBandPx, b.wallStripBandPx, t);
        mRowsPx = lerp(rowsPx(a), rowsPx(b), t);
        mAppsAlpha = fade(a.appsBarHeightPx > 0, b.appsBarHeightPx > 0, t);
        mAzAlpha = fade(a.azRowHeightPx > 0, b.azRowHeightPx > 0, t);
        mSearchFieldAlpha = fade(a.promptFieldBandPx > 0, b.promptFieldBandPx > 0, t);
        mWallStripAlpha = fade(a.wallStripBandPx > 0, b.wallStripBandPx > 0, t);
        mDrawerTileAlpha = fade(mFromTile, mToTile, t);
        mBeadShare = fade(a.surface.beads, b.surface.beads, t);
    }

    /** The dock rows' height a rest stands, less the extra keys row (as a crossing measures it). */
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
    /** The dock rows' height, less the extra keys row ({@link #rowsPx}). */
    public int rowsPx() { return mRowsPx; }
    public float appsAlpha() { return mAppsAlpha; }
    public float azAlpha() { return mAzAlpha; }
    public float searchFieldAlpha() { return mSearchFieldAlpha; }
    public float wallStripAlpha() { return mWallStripAlpha; }
    public float drawerTileAlpha() { return mDrawerTileAlpha; }
    /** How much of the dock wears Lens beads: 1 on a beaded side, 0 on a slab. */
    public float beadShare() { return mBeadShare; }
}
