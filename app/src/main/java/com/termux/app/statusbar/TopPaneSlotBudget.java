package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * The vertical budget of the widget slot's right-hand column: where the pinned cards and the media
 * strip sit inside the slot's height. Pure, so it tests on the JVM.
 *
 * <p>The slot's top edge is the bar's content edge — on a top bar it is flush with the glass under
 * the system status bar's strip — so anything laid from the slot's top touches the bar's edge. The
 * clock never showed it, because its face is centred in the slot with room to spare; the card and
 * media column filled 66 of the 68dp and sat 1dp off the edge, reading as spilling out of the bar.
 * The column now keeps {@link #AIR_DP} clear above and below, and when what it wants does not fit
 * in what is left, the gap and then the card give way, never the air.</p>
 */
public final class TopPaneSlotBudget {

    /** Air kept clear above and below the column, inside the slot. */
    public static final float AIR_DP = 5f;
    /** What the single card wants beside the clock: title plus two body lines. */
    public static final float SINGLE_CARD_DP = 48f;
    /** The gap the contention column wants between the card and the media strip. */
    public static final float CONTENTION_GAP_DP = 6f;
    /**
     * The least that gap may shrink to before the card itself gives way. In the 65dp slot it is
     * what the card's body line rides on: 55dp inside the air, less the strip and this gap,
     * leaves the card 32dp, and its padding, title and one body line need about 31.5dp.
     */
    public static final float CONTENTION_GAP_MIN_DP = 3f;

    /** Tops and heights in px; a height of 0 means that part is not shown. */
    public static final class Column {
        public final int cardsTop;
        public final int cardsHeight;
        public final int mediaTop;
        public final int mediaHeight;

        Column(int cardsTop, int cardsHeight, int mediaTop, int mediaHeight) {
            this.cardsTop = cardsTop;
            this.cardsHeight = cardsHeight;
            this.mediaTop = mediaTop;
            this.mediaHeight = mediaHeight;
        }
    }

    private TopPaneSlotBudget() {}

    /**
     * Where a row {@code heightPx} tall stands so its centre meets {@code anchorCenterYPx} — the
     * clock's digit line, for the media row beside a full face, which reads lower than the digits
     * when it is centred on the slot instead — held within the slot's air. With no anchor
     * (negative), the slot's centre, as before.
     */
    public static int anchoredTop(int slotHeight, int heightPx, float anchorCenterYPx,
                                  float density) {
        if (anchorCenterYPx < 0f) return Math.max(0, (slotHeight - heightPx) / 2);
        int air = Math.round(AIR_DP * density);
        int top = Math.round(anchorCenterYPx - heightPx / 2f);
        int lowest = Math.max(0, slotHeight - air - heightPx);
        return Math.max(Math.min(air, lowest), Math.min(top, lowest));
    }

    @NonNull
    public static Column layout(@NonNull TopPaneSlotMode mode, int pinnedCount, int slotHeight,
                                float density) {
        int air = Math.round(AIR_DP * density);
        int inner = Math.max(0, slotHeight - 2 * air);
        switch (mode) {
            case NOTIFICATIONS_AND_MEDIA: {
                int strip = Math.min(inner, Math.round(MediaWidgetView.STRIP_HEIGHT_DP * density));
                int gap = Math.round(CONTENTION_GAP_DP * density);
                int card = Math.round(PinnedNotificationsView.CONTENTION_CARD_HEIGHT_DP * density);
                if (card + gap + strip > inner) {
                    gap = Math.max(Math.round(CONTENTION_GAP_MIN_DP * density),
                        inner - card - strip);
                    card = Math.max(0, inner - gap - strip);
                }
                int column = card + gap + strip;
                int top = Math.max(0, (slotHeight - column) / 2);
                return new Column(top, card, top + card + gap, strip);
            }
            case NOTIFICATIONS: {
                // One card gets its two body lines; two or more share everything the air leaves,
                // one line each, and anything past the second scrolls into the same room.
                int desired = pinnedCount == 1 ? Math.round(SINGLE_CARD_DP * density) : inner;
                int height = Math.min(inner, desired);
                int top = Math.max(0, (slotHeight - height) / 2);
                return new Column(top, height, 0, 0);
            }
            case MEDIA: {
                int height = Math.min(inner, Math.round(MediaWidgetView.FULL_HEIGHT_DP * density));
                int top = Math.max(0, (slotHeight - height) / 2);
                return new Column(0, 0, top, height);
            }
            default:
                return new Column(0, 0, 0, 0);
        }
    }
}
