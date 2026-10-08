package com.termux.app.statusbar;

/**
 * Where the media widget's full row puts its parts across the widget's width, worked out away from
 * the canvas so the placement can be tested. {@link MediaWidgetView} draws and hit-tests the answer.
 *
 * <p>The transport ends clear of the bar's end: the widget's last edge sits beside the neighbour
 * place mark peeking in there, and a tap that lands past the next button reaches that mark and
 * pages the wall. So the next button's touch box, not just its glyph, stops where the neighbour's
 * own target begins ({@link StatusBarLensMetrics#NEIGHBOUR_REACH_DP}); the room the slot already
 * leaves between the widget and the bar's end counts towards it.</p>
 *
 * <p>With room for the title, the row runs art, title, transport. Without it the art and the
 * transport are one cluster, one gap apart, set against the transport's end inset rather than
 * leaving the art stranded at the far start. A cluster that does not fit gives up the inset
 * first and then the art: the controls are what the widget is for.</p>
 */
final class MediaWidgetLayout {

    static final float ART_DP = 40f;
    /** Between the art and the title, the title and the transport, or the art and the transport. */
    static final float GAP_DP = 8f;
    static final float SKIP_BOX_DP = 24f;
    static final float PLAY_BOX_DP = 26f;
    static final float TRANSPORT_GAP_DP = 7f;
    static final float TRANSPORT_DP = SKIP_BOX_DP + TRANSPORT_GAP_DP + PLAY_BOX_DP
        + TRANSPORT_GAP_DP + SKIP_BOX_DP;
    /** The least title run worth drawing; narrower than this the row drops its text. */
    static final float MIN_TEXT_DP = 24f;
    /** Every transport box's touch target is grown to at least this, on both axes. */
    static final float TOUCH_DP = 40f;

    /** The positions, in px from the widget's start. */
    static final class Full {
        boolean showsArt;
        float artLeft;
        boolean showsText;
        float textLeft;
        float textRight;
        float transportLeft;
        float transportRight;
        /** The next button's touch box ends here, as the view's hit test grows it. */
        float nextTouchRight;
    }

    private MediaWidgetLayout() {}

    /**
     * @param widthPx       the widget's width
     * @param barEndRoomPx  what lies between the widget's end and the bar's end
     */
    static Full full(float widthPx, float barEndRoomPx, float density) {
        Full out = new Full();
        float art = ART_DP * density;
        float gap = GAP_DP * density;
        float transport = TRANSPORT_DP * density;
        float touchGrow = Math.max(0f, TOUCH_DP - SKIP_BOX_DP) / 2f * density;
        float inset = Math.max(0f,
            (StatusBarLensMetrics.NEIGHBOUR_REACH_DP * density) + touchGrow - barEndRoomPx);

        float textLeft = art + gap;
        float textRight = widthPx - inset - transport - gap;
        if (textRight - textLeft > MIN_TEXT_DP * density) {
            out.showsArt = true;
            out.artLeft = 0f;
            out.showsText = true;
            out.textLeft = textLeft;
            out.textRight = textRight;
            out.transportLeft = widthPx - inset - transport;
        } else {
            float cluster = art + gap + transport;
            out.showsArt = widthPx >= cluster;
            float room = Math.max(0f, widthPx - (out.showsArt ? cluster : transport));
            out.transportLeft = Math.max(0f, widthPx - Math.min(inset, room) - transport);
            out.artLeft = out.showsArt ? out.transportLeft - gap - art : 0f;
        }
        out.transportRight = out.transportLeft + transport;
        out.nextTouchRight = out.transportRight + touchGrow;
        return out;
    }
}
