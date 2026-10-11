package com.termux.app.statusbar;

/**
 * How the status row shares its width between the window chips and the readings after them (CPU,
 * RAM, weather, the AI glyph, the mouse).
 *
 * <p>The chips run right up to the readings and stop one {@link #CHIPS_READINGS_GAP_DP} short of
 * the first reading's ink. Whatever spacing the readings already carry before their ink — a
 * container's padding, a widget's own padding — counts towards that gap, so the chips' end padding
 * is only what is left of it.
 */
public final class StatusRowFitPolicy {

    private StatusRowFitPolicy() {}

    /**
     * The one gap between the chips' last visible pixel and the first reading's ink. The same 6dp
     * the badge keeps before the chips, and the stats cluster keeps before the end widgets.
     */
    public static final float CHIPS_READINGS_GAP_DP = 6f;

    /**
     * The padding the chip strip keeps at its end so the readings' own leading inset and it add up
     * to {@code gapPx}: never negative, so a reading whose own inset is already wider than the gap
     * keeps its inset and the chips simply stop at its edge.
     *
     * @param readingsLeadPx the space between the chip strip's end and the first reading's ink,
     *     or a negative value when no reading follows the chips (the gap then belongs to the row's
     *     end, which keeps its own margin)
     */
    public static int chipsEndPaddingPx(int gapPx, int readingsLeadPx) {
        if (readingsLeadPx < 0) return 0;
        return Math.max(0, gapPx - readingsLeadPx);
    }

    /**
     * Whether the readings stand tight: their dots drop and the room between them shrinks, so the
     * chips get it. Exactly when the chips would run past their viewport with the readings at
     * their usual spacing. Decided from that spacious layout whichever form the row is in now, so
     * the answer cannot flip back and forth between two passes: a row that fits only once the
     * readings are tight stays tight.
     *
     * @param chipsContentPx the chips' own width at rest (a close button being offered is not
     *     counted: it comes and goes on a tap, and the readings should not move with it)
     * @param barWidthPx the window bar's width as measured in the row's current form
     * @param tightNow whether that form is the tight one
     * @param tightGainPx how much wider the bar is for the readings standing tight
     * @param barStartPaddingPx the bar's padding before the chips
     * @param spaciousEndPaddingPx the bar's end padding with the readings at their usual spacing
     */
    public static boolean readingsTight(int chipsContentPx, int barWidthPx, boolean tightNow,
                                        int tightGainPx, int barStartPaddingPx,
                                        int spaciousEndPaddingPx) {
        int spaciousBar = barWidthPx - (tightNow ? Math.max(0, tightGainPx) : 0);
        int viewport = spaciousBar - barStartPaddingPx - spaciousEndPaddingPx;
        return chipsContentPx > viewport;
    }

    /**
     * The part of {@code savingPx} that reaches the chips: the row hands freed room to its
     * stretching children by weight, and on the Widgets place a centring spacer shares it with the
     * strip the chips are in.
     */
    public static int stripShareOf(int savingPx, float stripWeight, float totalWeight) {
        if (savingPx <= 0 || stripWeight <= 0f || totalWeight <= 0f) return 0;
        return Math.round(savingPx * Math.min(1f, stripWeight / totalWeight));
    }
}
