package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The page bar's geometry in px, for one pane width, density and slot count. The pill is centred
 * on the window (in the pane when the pane is centred), its slots fill it end to end and the first glyph centre sits half a slot in. While
 * a finger slides, {@link #slidePositions} spreads the glyphs around the focus slot (whose glyph
 * is drawn in the bubble above the pill) so the row follows the finger without an animator.
 * Every number is declared once, in dp.
 */
public final class PageBarLayout {

    public static final float PILL_HEIGHT_DP = 48f;
    public static final float GLYPH_DP = 22f;
    public static final float MIN_SLOT_DP = 40f;
    public static final float MAX_SLOT_DP = 53f;
    /** The pill is at most this far from the pane's edges. */
    public static final float SIDE_INSET_DP = 16f;
    public static final float NEIGHBOUR_SCALE = 1.25f;
    public static final float FOCUS_SCALE = 2f;
    public static final float BUBBLE_DIAMETER_DP = 68f;
    /** Bubble bottom to pill top. */
    public static final float BUBBLE_GAP_DP = 12f;
    /** Label baseline to bubble top. */
    public static final float LABEL_GAP_DP = 8f;
    /** Pill top to the grid above. */
    public static final float TOP_AIR_DP = 4f;
    /** Pill bottom to the dock's reserve, where dock rows stand below the pill. */
    public static final float BOTTOM_AIR_DP = 12f;
    /** The finger leaving the pill vertically by more than this cancels the slide. */
    public static final float CANCEL_DP = 32f;

    /** The key's pop-up band, air on both sides; Home's band is {@link PageBarBand}'s. */
    public static float bandHeightDp() {
        return TOP_AIR_DP + PILL_HEIGHT_DP + BOTTOM_AIR_DP;
    }

    /** How many slots fit a pane of this width before runs must fold; at least 1. */
    public static int maxSlots(float density, float paneWidthPx) {
        float area = paneWidthPx - 2f * SIDE_INSET_DP * density;
        return Math.max(1, (int) Math.floor(area / (MIN_SLOT_DP * density)));
    }

    private final float mDensity;
    private final float mPaneWidth;
    private final int mSlotCount;
    private final float mSlotWidth;
    private final float mPillLeft;
    private final float[] mScratchCenters;
    private final float[] mScratchScales;
    private boolean[] mFolded;

    /** Home's bar: the pill hugs its glyphs, each slot at most {@link #MAX_SLOT_DP}. */
    public PageBarLayout(float density, float paneWidthPx, int slotCount) {
        this(density, paneWidthPx, slotCount, false);
    }

    /**
     * @param fill the pill spans the pane less {@link #SIDE_INSET_DP} on each side, its slots equal
     *             and uncapped: the bar the page switcher key pops up on a workspace page
     */
    public PageBarLayout(float density, float paneWidthPx, int slotCount, boolean fill) {
        this(density, paneWidthPx, slotCount, fill, 0f, paneWidthPx);
    }

    /**
     * Home's bar in a pane that may stand off the window's centre: the hugged pill is centred on
     * the window ({@link PageBarBand#pillLeftPx}), clamped inside the pane. With the pane centred
     * in the window it is the pane-centred bar.
     *
     * @param paneLeftPx    the pane's left in the window
     * @param windowWidthPx the window's width; 0 or less centres the pill in the pane
     */
    public PageBarLayout(float density, float paneWidthPx, int slotCount, float paneLeftPx,
                         float windowWidthPx) {
        this(density, paneWidthPx, slotCount, false, paneLeftPx, windowWidthPx);
    }

    private PageBarLayout(float density, float paneWidthPx, int slotCount, boolean fill,
                          float paneLeftPx, float windowWidthPx) {
        mDensity = density;
        mPaneWidth = paneWidthPx;
        mSlotCount = Math.max(1, slotCount);
        float area = Math.max(0f, paneWidthPx - 2f * SIDE_INSET_DP * density);
        mSlotWidth = fill ? area / mSlotCount : Math.min(MAX_SLOT_DP * density, area / mSlotCount);
        float pillWidth = mSlotWidth * mSlotCount;
        mPillLeft = fill ? (paneWidthPx - pillWidth) / 2f
            : PageBarBand.pillLeftPx(paneLeftPx, paneWidthPx, windowWidthPx, pillWidth, density);
        mScratchCenters = new float[mSlotCount];
        mScratchScales = new float[mSlotCount];
    }

    public int slotCount() {
        return mSlotCount;
    }

    public float slotWidthPx() {
        return mSlotWidth;
    }

    public float pillLeftPx() {
        return mPillLeft;
    }

    public float pillWidthPx() {
        return mSlotWidth * mSlotCount;
    }

    public float pillHeightPx() {
        return PILL_HEIGHT_DP * mDensity;
    }

    public float glyphPx() {
        return GLYPH_DP * mDensity;
    }

    public float bandHeightPx() {
        return bandHeightDp() * mDensity;
    }

    /** Pill top within the band. */
    public float pillTopPx() {
        return TOP_AIR_DP * mDensity;
    }

    public float pillBottomPx() {
        return pillTopPx() + pillHeightPx();
    }

    public float cancelPx() {
        return CANCEL_DP * mDensity;
    }

    public float restCenterX(int slot) {
        return mPillLeft + mSlotWidth * (slot + 0.5f);
    }

    /** The slot under {@code x}, from the rest slots only; left of the pill is 0, right of it the last. */
    public int slotAt(float x) {
        int slot = (int) Math.floor((x - mPillLeft) / mSlotWidth);
        return Math.max(0, Math.min(mSlotCount - 1, slot));
    }

    /** Which of a folded slot's {@code members} is under {@code x}: its width split equally. */
    public int subIndexAt(float x, int slot, int members) {
        if (members <= 1) return 0;
        float within = (x - (mPillLeft + mSlotWidth * slot)) / mSlotWidth;
        return Math.max(0, Math.min(members - 1, (int) Math.floor(within * members)));
    }

    /** The finger as a fractional slot, 0..slotCount-1, for {@link #slidePositions}. */
    public float focusAt(float x) {
        float f = (x - mPillLeft) / mSlotWidth - 0.5f;
        return Math.max(0f, Math.min(mSlotCount - 1, f));
    }

    /**
     * Glyph centres and scales while sliding. The focus glyph is not drawn (scale 0); its two
     * neighbours stand NEIGHBOUR_SCALE slots either side at that scale; the end glyphs stay at
     * their rest centres; glyphs between an end and a neighbour are spaced evenly between them.
     * A fractional {@code focusFloat} interpolates between the floor and ceiling layouts, and
     * {@code enter} (0..1) blends rest into that. Writes {@link #slotCount} values; allocates
     * nothing but is not reentrant.
     */
    public void slidePositions(float focusFloat, float enter, @NonNull float[] centers,
                               @NonNull float[] scales) {
        slidePositions(focusFloat, enter, centers, scales, null);
    }

    /**
     * As above; {@code folded[i]} (null: none) marks a folded slot, whose plates reach past its
     * glyph, so a neighbour clamped at the pill's end keeps them inside the pill too.
     */
    public void slidePositions(float focusFloat, float enter, @NonNull float[] centers,
                               @NonNull float[] scales, @Nullable boolean[] folded) {
        mFolded = folded;
        float f = Math.max(0f, Math.min(mSlotCount - 1, focusFloat));
        int lo = (int) Math.floor(f);
        int hi = Math.min(mSlotCount - 1, lo + 1);
        float t = f - lo;
        layoutFor(lo, centers, scales);
        if (t > 0f && hi != lo) {
            layoutFor(hi, mScratchCenters, mScratchScales);
            // Between two focus slots both glyphs are neighbours: each blends at the neighbour's
            // size, and only the one nearer the finger leaves the row for the bubble, so no glyph
            // passes through a shrunken size on its way.
            scales[lo] = NEIGHBOUR_SCALE;
            mScratchScales[hi] = NEIGHBOUR_SCALE;
            for (int i = 0; i < mSlotCount; i++) {
                centers[i] += (mScratchCenters[i] - centers[i]) * t;
                scales[i] += (mScratchScales[i] - scales[i]) * t;
            }
            scales[Math.round(f)] = 0f;
        }
        for (int i = 0; i < mSlotCount; i++) {
            float rest = restCenterX(i);
            centers[i] = rest + (centers[i] - rest) * enter;
            scales[i] = 1f + (scales[i] - 1f) * enter;
        }
    }

    private void layoutFor(int focus, float[] centers, float[] scales) {
        int n = mSlotCount;
        for (int i = 0; i < n; i++) {
            centers[i] = restCenterX(i);
            scales[i] = 1f;
        }
        scales[focus] = 0f;
        float focusX = restCenterX(focus);
        if (focus > 0) spread(centers, scales, 0, focus - 1, focusX - NEIGHBOUR_SCALE * mSlotWidth);
        if (focus < n - 1) {
            spread(centers, scales, n - 1, focus + 1, focusX + NEIGHBOUR_SCALE * mSlotWidth);
        }
    }

    /**
     * Places neighbour {@code nb} at {@code nbX} and the glyphs between it and the end glyph
     * {@code end}, which keeps its rest centre unless it is the neighbour itself.
     */
    private void spread(float[] centers, float[] scales, int end, int nb, float nbX) {
        float unit = glyphPx() * NEIGHBOUR_SCALE / PageBarGlyphPainter.GLYPH_UNITS;
        // The pill's ends are round: a glyph centred less than the cap's radius from an end
        // crosses the outline at its corners, so the cap's radius is the least room it keeps.
        float half = Math.max(PageBarGlyphPainter.GLYPH_UNITS * unit / 2f, pillHeightPx() / 2f);
        boolean stack = mFolded != null && nb < mFolded.length && mFolded[nb];
        float pillRight = mPillLeft + pillWidthPx();
        float minX = mPillLeft + half
            + (stack ? PageBarGlyphPainter.STACK_OVERHANG_LEFT * unit : 0f);
        float maxX = pillRight - half
            - (stack ? PageBarGlyphPainter.STACK_OVERHANG_RIGHT * unit : 0f);
        float x = Math.max(minX, Math.min(maxX, nbX));
        centers[nb] = x;
        scales[nb] = NEIGHBOUR_SCALE;
        if (end == nb) return;
        float endX = restCenterX(end);
        int steps = Math.abs(nb - end);
        int dir = nb > end ? 1 : -1;
        for (int k = 1; k < steps; k++) {
            centers[end + dir * k] = endX + (x - endX) * k / steps;
        }
    }

    /** The bubble's centre x: the focus slot's rest centre, kept inside the pane. */
    public float bubbleCenterX(int slot) {
        float r = BUBBLE_DIAMETER_DP * mDensity / 2f;
        return Math.max(r, Math.min(mPaneWidth - r, restCenterX(slot)));
    }

    /** The bubble's centre y, relative to the pill top (negative is above it). */
    public float bubbleCenterYFromPillTopPx() {
        return -(BUBBLE_GAP_DP + BUBBLE_DIAMETER_DP / 2f) * mDensity;
    }

    /** The label's baseline y, relative to the pill top. */
    public float labelBaselineFromPillTopPx() {
        return -(BUBBLE_GAP_DP + BUBBLE_DIAMETER_DP + LABEL_GAP_DP) * mDensity;
    }
}
