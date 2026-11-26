package com.termux.app.wall.pageswitch;

/**
 * Home's pill under the Marks look, pure: a small glass pill fitted to its marks with no spare
 * room. Its width is the line's content plus an even inset on both ends and it changes only when
 * the marks do; it stands where the Glyphs pill does (centred on the window, kept inside the
 * pane, {@link PageBarBand#pillLeftPx}) and is {@link #HEIGHT_DP} tall. The view
 * ({@link PageBarView}) applies these answers; the line inside it is the status bar's own
 * ({@code MarksLineView}), filling the pill's height.
 */
public final class MarksPillPolicy {

    private MarksPillPolicy() {}

    /** The pill's height: a mark and its ring with the inset above and below. */
    public static final float HEIGHT_DP = 32f;
    /** The air between the marks' ends and the pill's ends. */
    public static final float INSET_DP = 8f;

    public static float heightPx(float density) {
        return HEIGHT_DP * density;
    }

    public static float insetPx(float density) {
        return INSET_DP * density;
    }

    /**
     * The length the line may take in a pane {@code paneWidthPx} wide: the pane less the pill's
     * side inset and the pill's own inset on both ends; never negative.
     */
    public static float lineRoomPx(float paneWidthPx, float density) {
        return Math.max(0f, paneWidthPx - 2f * (PageBarLayout.SIDE_INSET_DP + INSET_DP) * density);
    }

    /** The pill's width for a line of {@code contentPx}: the content and the inset on both ends; 0 for no marks. */
    public static float widthPx(float contentPx, float density) {
        return contentPx > 0f ? contentPx + 2f * insetPx(density) : 0f;
    }

    /** The pill's left in its pane, as the Glyphs pill's ({@link PageBarBand#pillLeftPx}). */
    public static float leftPx(float paneLeftPx, float paneWidthPx, float windowWidthPx,
                               float pillWidthPx, float density) {
        return PageBarBand.pillLeftPx(paneLeftPx, paneWidthPx, windowWidthPx, pillWidthPx, density);
    }

    /** The line's left inside a pill whose left is {@code pillLeftPx}. */
    public static float lineLeftPx(float pillLeftPx, float density) {
        return pillLeftPx + insetPx(density);
    }
}
