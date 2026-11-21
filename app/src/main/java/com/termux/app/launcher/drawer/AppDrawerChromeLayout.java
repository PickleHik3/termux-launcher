package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * The drawer's chrome stacked, pure: the section switch (Default only), the search bar, and the
 * top and bottom between which the content (grid, pages, category view, results label, no-results
 * state, A-Z column) runs. The content view applies the answer in its layout-change paths; nothing
 * here runs per frame.
 *
 * <p>Without the switch, and with the bar on top, every number is what the drawer had before the
 * switch existed: the search bar {@link #TOP_MARGIN_DP} from the top and the content
 * {@link #SEARCH_TO_CONTENT_DP} under it. With the switch it takes the top margin and the search
 * bar moves under it by {@link #SWITCH_TO_SEARCH_DP}, so the content keeps its place relative to
 * the search bar.
 *
 * <p>The search bar is a centred capsule, {@link #SEARCH_WIDTH_SHARE} of the width between the
 * side margins, kept between {@link #SEARCH_MIN_WIDTH_DP} and {@link #SEARCH_MAX_WIDTH_DP}
 * (and never wider than the margins allow), so it reads as a capsule even on a phone. With
 * {@code searchAtBottom} it stands at the content's bottom, {@code bottomInsetPx} above the card's
 * usable bottom (the inset the keyboard decision already moves), and the content runs from the top
 * chrome to {@link #SEARCH_TO_CONTENT_DP} above it; the content's own bottom inset is then zero.
 */
public final class AppDrawerChromeLayout {

    /** The drawer's side padding: the margin of the search bar and the switch's widest extent. */
    public static final float SIDE_MARGIN_DP = 16f;
    /** The first element's top margin: the search bar's, or the switch's when it is shown. */
    public static final float TOP_MARGIN_DP = 12f;
    /** The search bar's height. */
    public static final float SEARCH_HEIGHT_DP = AppDrawerSearchPillView.HEIGHT_DP;
    /** The search bar's share of the width between the side margins. */
    public static final float SEARCH_WIDTH_SHARE = 0.78f;
    /** The widest the search bar grows: on a tablet or in landscape it stays this wide, centred. */
    public static final float SEARCH_MAX_WIDTH_DP = 360f;
    /** The narrowest the share may make it, while the margins allow. */
    public static final float SEARCH_MIN_WIDTH_DP = 220f;
    /** Under the section switch, above the search bar. */
    public static final float SWITCH_TO_SEARCH_DP = 8f;
    /** Between the search bar and the content, on whichever side the content is. */
    public static final float SEARCH_TO_CONTENT_DP = 10f;

    /** Everything the stack is solved from. */
    public static final class Input {
        public final float density;
        public final int availableWidthPx;
        public final boolean switchShown;
        public final int switchHeightPx;
        public final boolean searchAtBottom;
        public final int bottomInsetPx;
        /** The content view's height; zero before its first layout (every top is then zero). */
        public final int heightPx;
        /** The round button parks in the left corner; else the right (see {@link #morph}). */
        public final boolean buttonAtLeft;
        /** The bar rests as the round button: the list leaves room for it (bar on top only). */
        public final boolean collapsed;

        public Input(float density, int availableWidthPx, boolean switchShown,
                     int switchHeightPx, boolean searchAtBottom, int bottomInsetPx) {
            this(density, availableWidthPx, switchShown, switchHeightPx, searchAtBottom,
                bottomInsetPx, 0, false, false);
        }

        public Input(float density, int availableWidthPx, boolean switchShown,
                     int switchHeightPx, boolean searchAtBottom, int bottomInsetPx,
                     int heightPx, boolean buttonAtLeft, boolean collapsed) {
            this.heightPx = Math.max(0, heightPx);
            this.buttonAtLeft = buttonAtLeft;
            this.collapsed = collapsed;
            this.density = density;
            this.availableWidthPx = availableWidthPx;
            this.switchShown = switchShown;
            this.switchHeightPx = switchHeightPx;
            this.searchAtBottom = searchAtBottom;
            this.bottomInsetPx = Math.max(0, bottomInsetPx);
        }
    }

    /** Where each part goes, in pixels from the content view's edges. */
    public static final class Result {
        public final int sideMarginPx;
        /** Zero while the switch is not shown. */
        public final int switchTopPx;
        public final int switchHeightPx;
        /** The widest the switch may be: the width between the side margins. */
        public final int switchMaxWidthPx;
        /** From the top; zero when the bar is at the bottom. */
        public final int searchTopPx;
        public final int searchHeightPx;
        /** The bar's width: its share of the width between the margins, clamped (see the class). */
        public final int searchWidthPx;
        /** The bar's margin above the bottom edge; zero when the bar is at the top. */
        public final int searchBottomMarginPx;
        /** Where the content starts; the grid's top margin before any lowering. */
        public final int contentTopPx;
        /** How far the content's bottom stands above the bottom edge; zero with the bar on top. */
        public final int contentBottomPx;
        /** The inset the content keeps under its last row: the whole inset, or zero over the bar. */
        public final int innerBottomInsetPx;
        /** The full bar's left and top in the content view (top is zero before a height is known). */
        public final int barLeftPx;
        public final int barTopPx;
        /** The round button's left and top, and its diameter (the bar's height). */
        public final int buttonLeftPx;
        public final int buttonTopPx;
        public final int buttonSizePx;

        Result(int sideMarginPx, int switchTopPx, int switchHeightPx, int switchMaxWidthPx,
               int searchTopPx, int searchHeightPx, int searchWidthPx, int searchBottomMarginPx,
               int contentTopPx, int contentBottomPx, int innerBottomInsetPx,
               int barLeftPx, int barTopPx, int buttonLeftPx, int buttonTopPx,
               int buttonSizePx) {
            this.barLeftPx = barLeftPx;
            this.barTopPx = barTopPx;
            this.buttonLeftPx = buttonLeftPx;
            this.buttonTopPx = buttonTopPx;
            this.buttonSizePx = buttonSizePx;
            this.sideMarginPx = sideMarginPx;
            this.switchTopPx = switchTopPx;
            this.switchHeightPx = switchHeightPx;
            this.switchMaxWidthPx = switchMaxWidthPx;
            this.searchTopPx = searchTopPx;
            this.searchHeightPx = searchHeightPx;
            this.searchWidthPx = searchWidthPx;
            this.searchBottomMarginPx = searchBottomMarginPx;
            this.contentTopPx = contentTopPx;
            this.contentBottomPx = contentBottomPx;
            this.innerBottomInsetPx = innerBottomInsetPx;
        }
    }

    private AppDrawerChromeLayout() {
    }

    @NonNull
    public static Result resolve(@NonNull Input in) {
        int side = px(SIDE_MARGIN_DP, in.density);
        int top = px(TOP_MARGIN_DP, in.density);
        int searchHeight = px(SEARCH_HEIGHT_DP, in.density);
        int switchHeight = in.switchShown ? Math.max(0, in.switchHeightPx) : 0;
        int betweenMargins = Math.max(0, in.availableWidthPx - 2 * side);
        int searchWidth = searchWidthPx(betweenMargins, in.density);
        int gap = px(SEARCH_TO_CONTENT_DP, in.density);
        int switchTop = in.switchShown ? top : 0;
        int topChrome = in.switchShown
            ? top + switchHeight + px(SWITCH_TO_SEARCH_DP, in.density) : top;
        int barLeft = (in.availableWidthPx - searchWidth) / 2;
        // The button keeps the bar's side margin from the card's edge; from the bottom it keeps the
        // bar's own margin when the bar is down there, else at least the side margin.
        int buttonBottom = in.searchAtBottom ? in.bottomInsetPx : Math.max(in.bottomInsetPx, side);
        int buttonLeft = in.buttonAtLeft ? side : in.availableWidthPx - side - searchHeight;
        int buttonTop = in.heightPx > 0 ? in.heightPx - buttonBottom - searchHeight : 0;
        if (in.searchAtBottom) {
            int barTop = in.heightPx > 0 ? in.heightPx - in.bottomInsetPx - searchHeight : 0;
            return new Result(side, switchTop, switchHeight, betweenMargins, 0, searchHeight,
                searchWidth, in.bottomInsetPx, topChrome, in.bottomInsetPx + searchHeight + gap,
                0, barLeft, barTop, buttonLeft, buttonTop, searchHeight);
        }
        // The bar on top: the round button floats over the list's last rows, so a folded bar has
        // the list keep that much under its last row. The top band is kept as it is.
        int inner = in.collapsed
            ? Math.max(in.bottomInsetPx, buttonBottom + searchHeight + gap) : in.bottomInsetPx;
        return new Result(side, switchTop, switchHeight, betweenMargins, topChrome, searchHeight,
            searchWidth, 0, topChrome + searchHeight + gap, 0, inner, barLeft, topChrome,
            buttonLeft, buttonTop, searchHeight);
    }

    /**
     * Where the bar is at one fold fraction: the pill view stays laid out as the full bar and is
     * moved by {@code translationX/Y} (a draw-time transform, no layout) while it draws the capsule
     * between {@code drawnLeft} and {@code drawnRight} in its own coordinates.
     */
    public static final class Morph {
        public float translationX;
        public float translationY;
        public float drawnLeft;
        public float drawnRight;
    }

    /**
     * The bar at {@code fraction} (0 the full bar, 1 the round button), written into {@code out}.
     * The capsule keeps the edge nearest its corner and narrows toward it, so the glyph rides to
     * the middle of the circle; the view slides in a straight line to the corner.
     */
    public static void morph(@NonNull Result r, float fraction, @NonNull Morph out) {
        float f = Math.max(0f, Math.min(1f, fraction));
        float width = r.searchWidthPx;
        float narrow = Math.max(0f, width - r.buttonSizePx);
        boolean toRight = r.buttonLeftPx > r.barLeftPx;
        float originFinal = toRight ? r.buttonLeftPx - narrow : r.buttonLeftPx;
        out.translationX = f * (originFinal - r.barLeftPx);
        out.translationY = f * (r.buttonTopPx - r.barTopPx);
        out.drawnLeft = toRight ? f * narrow : 0f;
        out.drawnRight = toRight ? width : width - f * narrow;
    }

    /** The bar's width for {@code contentWidthPx} between the side margins. */
    static int searchWidthPx(int contentWidthPx, float density) {
        int share = Math.round(contentWidthPx * SEARCH_WIDTH_SHARE);
        int clamped = Math.max(px(SEARCH_MIN_WIDTH_DP, density),
            Math.min(px(SEARCH_MAX_WIDTH_DP, density), share));
        return Math.max(0, Math.min(contentWidthPx, clamped));
    }

    private static int px(float dp, float density) {
        return Math.round(dp * density);
    }
}
