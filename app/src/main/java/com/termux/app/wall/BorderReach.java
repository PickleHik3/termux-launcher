package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * How far the wall's border gestures may reach into the page in front, and where that page's
 * content starts. The one decision behind {@link BorderDrag.Zone#ownContent}.
 *
 * <p>A terminal window shares its edges with its borders on purpose: the side borders take the
 * clearance before its text ({@link BorderDrag#SIDE_REACH_DP}), the vertical swipes start a row
 * into it by the grabber pills ({@link BorderDrag#PILL_SWIPE_REACH_DP}), the seam band reaches
 * into its last row ({@link SeamSwipe#PANE_REACH_DP}), and where its air is thin a border reaches
 * in by the shortfall ({@link BorderDrag#MIN_BAND_DP}). The display window does not: a Linux
 * app's picture runs to the frame's rim, and a press anywhere inside it is the app's — a scroll at
 * its foot, a menu at its top, a drag along its edge. So on the display every border stops at
 * the page's edge and lives in the air around it, and only where that air is thinner than the
 * minimum band does a border reach in, by the frame line alone ({@link #FRAME_STRIP_DP}), drawn
 * over the picture's outermost pixels. The dock's top keeps its part of the seam band, which lies
 * below the page.
 *
 * <p>Pure, so the rule is tested without a view.
 */
public final class BorderReach {

    private BorderReach() {}

    /**
     * How far a border may reach into a page that owns its content, where the air beside it is
     * thinner than {@link BorderDrag#MIN_BAND_DP}, in dp: the frame line, the rim's hairline
     * ({@code PaneRim.STOCK_STROKE_DP}, which the glass rim matches at every density).
     */
    public static final float FRAME_STRIP_DP = 1f;

    /**
     * Whether the page in front at {@code page} owns everything inside its edges: a Display page
     * (ADR 0015, A3b). A terminal window and a Home page do not.
     */
    public static boolean ownsContent(@NonNull PaneWallPage page) {
        return page == PaneWallPage.DISPLAY;
    }

    /** {@link #FRAME_STRIP_DP} in px. */
    public static float frameStripPx(float density) {
        return FRAME_STRIP_DP * Math.max(0f, density);
    }

    /**
     * Sets {@code zone} for its page: the display window's ({@code ownsContent}) keeps every
     * border out of it but the frame line, any other page shares its edges as it always has.
     */
    @NonNull
    public static BorderDrag.Zone apply(@NonNull BorderDrag.Zone zone, boolean ownsContent,
                                        float density) {
        return ownsContent ? zone.ownContent(frameStripPx(density)) : zone.shareContent();
    }

    /**
     * Where the page's own content is, as {@code left, top, right, bottom} in {@code out}: the
     * page, less the frame line on a page that owns its content, since that line is the border's.
     * False, with {@code out} untouched, where there is no page.
     */
    public static boolean contentBounds(@NonNull BorderDrag.Zone zone, @NonNull float[] out) {
        float inset = zone.mOwnsContent ? zone.mFrameStrip : 0f;
        float l = zone.mLeft + inset;
        float t = zone.mTop + inset;
        float r = zone.mRight - inset;
        float b = zone.mBottom - inset;
        if (r <= l || b <= t) return false;
        out[0] = l;
        out[1] = t;
        out[2] = r;
        out[3] = b;
        return true;
    }

    /**
     * Whether any of the zone's touch areas — the four borders, the boxes by the two pills, and
     * the seam band's last row — overlaps the page's content ({@link #contentBounds}). Never on
     * the display window; always on a terminal window, whose side borders take its clearance.
     */
    public static boolean reachesContent(@NonNull BorderDrag.Zone zone) {
        float[] content = new float[4];
        if (!contentBounds(zone, content)) return false;
        float[] box = new float[4];
        for (BorderDrag.Border side : BorderDrag.Border.values()) {
            if (side != BorderDrag.Border.NONE && zone.bounds(side, box)
                && overlaps(box, content)) {
                return true;
            }
        }
        if (zone.pillBounds(BorderDrag.Border.TOP, box) && overlaps(box, content)) return true;
        if (zone.pillBounds(BorderDrag.Border.BOTTOM, box) && overlaps(box, content)) return true;
        return zone.seamPaneBounds(box) && overlaps(box, content);
    }

    /** Whether two {@code left, top, right, bottom} boxes share any area. */
    static boolean overlaps(@NonNull float[] a, @NonNull float[] b) {
        return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    }
}
