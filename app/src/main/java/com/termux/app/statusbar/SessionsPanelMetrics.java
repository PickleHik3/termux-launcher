package com.termux.app.statusbar;

/**
 * Width and marquee arithmetic for {@link SessionsPanelView}, kept free of Android imports so it
 * can be unit-tested. Three separate limits used to decide how wide the sessions pop-down ends up
 * and how much of that width a row title actually gets; none of them knew about the others, which
 * is how a title could overflow a panel whose whole job is to be as wide as its widest row.
 */
public final class SessionsPanelMetrics {

    /** Narrower than this and the panel reads as a tooltip rather than a list. */
    public static final int MIN_WIDTH_DP = 200;
    /** Wider than this and the pop-down stops reading as anchored to its status chip. */
    public static final int MAX_WIDTH_DP = 320;
    /**
     * A title that overflows by less than this scrolls its tail out and restarts inside the stock
     * marquee delay, which reads as a twitch rather than as motion. Such rows keep an ellipsis.
     */
    public static final float MARQUEE_MIN_OVERFLOW_DP = 12f;

    /** Horizontal margin {@code StatusCardHost.portraitMaxWidthPx} keeps on each side. */
    private static final int SCREEN_MARGIN_DP = 12;

    /** Result of {@link #calculate}: the width to ask for, and what the title really gets. */
    public static final class Layout {
        /** What {@code SessionsPanelView.desiredWidthDp()} should return. */
        public final int widthDp;
        /** Pixels left for a row title after chrome, once every clamp has been applied. */
        public final float availableTitlePx;

        public Layout(int widthDp, float availableTitlePx) {
            this.widthDp = widthDp;
            this.availableTitlePx = availableTitlePx;
        }
    }

    private SessionsPanelMetrics() {}

    /**
     * Width for a panel whose widest row text measures {@code widestTextPx} inside
     * {@code chromePx} of fixed row and container furniture.
     *
     * <p>The text cap is derived from {@link #MAX_WIDTH_DP} rather than hardcoded, so the two can
     * never drift: asking for more text than the clamp can carry only produces a panel that
     * truncates. {@code availableTitlePx} additionally mirrors
     * {@code StatusCardHost.portraitMaxWidthPx} — that method is the authority on the final popup
     * width and shrinks it to the portrait screen minus a margin per side, a third cap the view
     * itself never saw.
     */
    public static Layout calculate(float widestTextPx, float chromePx,
                                   int screenPortraitWidthPx, float density) {
        float safeDensity = density > 0f ? density : 1f;
        float maxTextPx = Math.max(0f, MAX_WIDTH_DP * safeDensity - chromePx);
        float textPx = Math.max(0f, Math.min(widestTextPx, maxTextPx));
        int widthDp = Math.max(MIN_WIDTH_DP, Math.min(MAX_WIDTH_DP,
            Math.round((textPx + chromePx) / safeDensity)));

        int requestedPx = Math.round(widthDp * safeDensity);
        int floorPx = Math.round(MIN_WIDTH_DP * safeDensity);
        int screenCapPx = Math.max(floorPx,
            screenPortraitWidthPx - 2 * Math.round(SCREEN_MARGIN_DP * safeDensity));
        int appliedPx = Math.min(requestedPx, screenCapPx);
        return new Layout(widthDp, Math.max(0f, appliedPx - chromePx));
    }

    /**
     * Whether a title wide enough to overflow its row by a readable amount should autoscroll.
     * The comparison is strict, so a title that exactly fills its space — or exactly reaches the
     * threshold — stays still.
     */
    public static boolean shouldMarquee(float textWidthPx, float availableTitlePx,
                                        float minOverflowPx) {
        return textWidthPx - availableTitlePx > minOverflowPx;
    }
}
