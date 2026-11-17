package com.termux.app.wall.pageswitch;

import com.termux.app.ScrubWave;

/**
 * Geometry of the page switch panel: one row of equal cells across the panel, a title line above
 * them, and room for the lifted cell. Pure; every input and answer is in px, in panel
 * coordinates, with density passed in.
 */
public final class PageSwitchLayout {

    public static final float CELL_HEIGHT_DP = 40f;
    public static final float GLYPH_DP = 22f;
    /** The A-Z bar's wave amplitude. */
    public static final float LIFT_DP = 15f;
    /** The A-Z bar's scale gain. */
    public static final float GROW = 0.34f;
    public static final float TITLE_GAP_DP = 6f;
    public static final float PANEL_SIDE_PADDING_DP = 12f;
    public static final float PANEL_VERTICAL_PADDING_DP = 8f;

    private final float mDensity;
    private final int mPanelWidthPx;
    private final int mCellCount;
    private final float mTitleLineHeightPx;

    public PageSwitchLayout(float density, int panelWidthPx, int cellCount,
                            float titleLineHeightPx) {
        mDensity = density;
        mPanelWidthPx = panelWidthPx;
        mCellCount = Math.max(0, cellCount);
        mTitleLineHeightPx = titleLineHeightPx;
    }

    public float sidePaddingPx() {
        return PANEL_SIDE_PADDING_DP * mDensity;
    }

    public float cellHeightPx() {
        return CELL_HEIGHT_DP * mDensity;
    }

    public float glyphPx() {
        return GLYPH_DP * mDensity;
    }

    public float liftPx() {
        return LIFT_DP * mDensity;
    }

    /** Room for the title, the lifted cell and the cell at rest, so nothing draws outside. */
    public float panelHeightPx() {
        return PANEL_VERTICAL_PADDING_DP * mDensity * 2f + mTitleLineHeightPx
            + TITLE_GAP_DP * mDensity + liftPx() + cellHeightPx();
    }

    /** Top of a cell at rest, in panel coordinates. */
    public float cellTopRestPx() {
        return PANEL_VERTICAL_PADDING_DP * mDensity + mTitleLineHeightPx
            + TITLE_GAP_DP * mDensity + liftPx();
    }

    public float cellWidthPx() {
        if (mCellCount == 0) return 0f;
        return (mPanelWidthPx - 2f * sidePaddingPx()) / mCellCount;
    }

    public float cellCentreX(int i) {
        return sidePaddingPx() + cellWidthPx() * (i + 0.5f);
    }

    /** The cell under x, clamped to the row; -1 when there are no cells. Rest positions only. */
    public int indexAtX(float x) {
        if (mCellCount == 0) return -1;
        float w = cellWidthPx();
        if (w <= 0f) return 0;
        int i = (int) Math.floor((x - sidePaddingPx()) / w);
        return Math.max(0, Math.min(mCellCount - 1, i));
    }

    /**
     * The wave at cell {@code i} for a finger at {@code fingerX}: {@code out[0]} is the lift in px,
     * {@code out[1]} the scale.
     */
    public void wave(int i, float fingerX, float strength, float[] out) {
        float w = Math.max(1f, cellWidthPx());
        float env = ScrubWave.envelope(Math.abs(cellCentreX(i) - fingerX) / w);
        out[0] = ScrubWave.lift(env) * LIFT_DP * mDensity * strength;
        out[1] = 1f + GROW * env * strength;
    }

    /** The widest a title may be; the caller ellipsizes to it. */
    public float maxTitleWidthPx() {
        return Math.max(0f, mPanelWidthPx - 2f * sidePaddingPx());
    }

    /** Where a title centred over a cell is centred, kept inside the side padding. */
    public float titleCentreX(float cellCentreX, float titleWidthPx) {
        float half = titleWidthPx / 2f;
        float min = sidePaddingPx() + half;
        float max = mPanelWidthPx - sidePaddingPx() - half;
        if (min > max) return mPanelWidthPx / 2f;
        return Math.max(min, Math.min(max, cellCentreX));
    }
}
