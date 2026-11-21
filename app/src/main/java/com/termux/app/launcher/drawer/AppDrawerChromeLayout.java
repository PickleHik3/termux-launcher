package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * The drawer's top chrome stacked, pure: the section switch (Default only), the search bar, and the
 * top where the content (grid, pages, category view, results label, no-results state, A-Z column)
 * starts. The content view applies the answer in its layout-change paths; nothing here runs per
 * frame.
 *
 * <p>Without the switch every number is what the drawer had before the switch existed: the search
 * bar {@link #TOP_MARGIN_DP} from the top and the content {@link #SEARCH_TO_CONTENT_DP} under it.
 * With it the switch takes the top margin and the search bar moves under it by
 * {@link #SWITCH_TO_SEARCH_DP}, so the content keeps its place relative to the search bar.
 */
public final class AppDrawerChromeLayout {

    /** The drawer's side padding: the margin of the search bar and the switch's widest extent. */
    public static final float SIDE_MARGIN_DP = 16f;
    /** The first element's top margin: the search bar's, or the switch's when it is shown. */
    public static final float TOP_MARGIN_DP = 12f;
    /** The search bar's height. */
    public static final float SEARCH_HEIGHT_DP = AppDrawerSearchPillView.HEIGHT_DP;
    /** Under the section switch, above the search bar. */
    public static final float SWITCH_TO_SEARCH_DP = 8f;
    /** Under the search bar, above the content. */
    public static final float SEARCH_TO_CONTENT_DP = 10f;

    /** Everything the stack is solved from. */
    public static final class Input {
        public final float density;
        public final int availableWidthPx;
        public final boolean switchShown;
        public final int switchHeightPx;

        public Input(float density, int availableWidthPx, boolean switchShown,
                     int switchHeightPx) {
            this.density = density;
            this.availableWidthPx = availableWidthPx;
            this.switchShown = switchShown;
            this.switchHeightPx = switchHeightPx;
        }
    }

    /** Where each part goes, in pixels from the content view's top-left. */
    public static final class Result {
        public final int sideMarginPx;
        /** Zero while the switch is not shown. */
        public final int switchTopPx;
        public final int switchHeightPx;
        /** The widest the switch may be: the width between the side margins. */
        public final int switchMaxWidthPx;
        public final int searchTopPx;
        public final int searchHeightPx;
        /** Where the content starts; the grid's top margin before any lowering. */
        public final int contentTopPx;

        Result(int sideMarginPx, int switchTopPx, int switchHeightPx, int switchMaxWidthPx,
               int searchTopPx, int searchHeightPx, int contentTopPx) {
            this.sideMarginPx = sideMarginPx;
            this.switchTopPx = switchTopPx;
            this.switchHeightPx = switchHeightPx;
            this.switchMaxWidthPx = switchMaxWidthPx;
            this.searchTopPx = searchTopPx;
            this.searchHeightPx = searchHeightPx;
            this.contentTopPx = contentTopPx;
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
        int searchTop = in.switchShown
            ? top + switchHeight + px(SWITCH_TO_SEARCH_DP, in.density) : top;
        int contentTop = searchTop + searchHeight + px(SEARCH_TO_CONTENT_DP, in.density);
        return new Result(side, in.switchShown ? top : 0, switchHeight,
            Math.max(0, in.availableWidthPx - 2 * side), searchTop, searchHeight, contentTop);
    }

    private static int px(float dp, float density) {
        return Math.round(dp * density);
    }
}
