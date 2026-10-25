package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the stack of hidden-item cards beside the editor's frame, as pure arithmetic in
 * pixels: each card's own height (its label's wrapped lines plus the padding, so cards differ),
 * where each stands, the band the frame leaves clear for them, and the one label size at which
 * every possible label stands whole in the card's width, wrapped at spaces on at most
 * {@link #MAX_LINES} lines, never cut.
 */
final class HiddenStackPolicy {

    private HiddenStackPolicy() {}

    static final int CARD_W_DP = 72;
    /** The least the label shrinks to, as a share of its fitted size, when the stack is too tall. */
    static final float MIN_LABEL_SHRINK = 0.75f;
    /** Room left round the text inside the padding, so a glyph never meets the card's edge. */
    static final int TEXT_SLACK_PX = 1;
    static final int GAP_DP = 8;
    /** The stack's distance from the window's left edge, and the frame's from the stack. */
    static final int SIDE_DP = 8;
    /** The card's inner padding on every side. */
    static final int PAD_DP = 4;
    /** A card's corner; the zone's border, {@link #ZONE_PAD_DP} outside, rounds concentric with it. */
    static final int CARD_CORNER_DP = 12;
    /** A label's size at font scale 1, before it is made to fit. */
    static final float LABEL_SP = 12f;
    static final int MAX_LINES = 2;

    /** Text measurement, so the fit is computed with real font metrics yet tested without any. */
    interface Measure {
        float width(@NonNull String text, float sizePx);

        float lineHeight(float sizePx);
    }

    /** The band the stack takes from the window's left edge, up to where the frame may start. */
    static int reservePx(float density) {
        return Math.round((SIDE_DP + CARD_W_DP + SIDE_DP) * density);
    }

    static int cardWidthPx(float density) {
        return Math.round(CARD_W_DP * density);
    }

    /**
     * The air between the drop zone's border and what it holds, the same on every side. The
     * border stands this far outside the cards' column, in the {@link #SIDE_DP} either side of it,
     * so the cards never move or shrink when it shows.
     */
    static final int ZONE_PAD_DP = 4;

    /**
     * The rail as one piece, in px of the content view, {left, top, right, bottom} each: the
     * cards one after another, the empty drop slot after the last, and the zone's border round
     * them all with {@link #ZONE_PAD_DP} of air on every side. Cards, slot and border come from
     * the one computation, so the border always holds the cards.
     */
    static final class Rail {
        @NonNull final int[][] cards;
        @NonNull final int[] slot;
        @NonNull final int[] border;

        Rail(@NonNull int[][] cards, @NonNull int[] slot, @NonNull int[] border) {
            this.cards = cards;
            this.slot = slot;
            this.border = border;
        }
    }

    /** The padding inside a card plus its slack, each side: what the label's box is inset by. */
    static int insetPx(float density) {
        return Math.round(PAD_DP * density) + TEXT_SLACK_PX;
    }

    /** The text height of {@code lines} lines at {@code sizePx}, whole pixels. */
    private static int textHeightPx(int lines, @NonNull Measure measure, float sizePx) {
        return lines * (int) Math.ceil(measure.lineHeight(sizePx));
    }

    /**
     * One card's height for {@code label} at {@code sizePx}: its own wrapped lines (at most
     * {@link #MAX_LINES}) and the padding, nothing more, so a one-line label's card is shorter
     * than a two-line one's.
     */
    static int cardHeightPx(@NonNull String label, @NonNull Measure measure, float sizePx,
                            int cardWidthPx, float density) {
        float width = Math.max(1, cardWidthPx - 2 * insetPx(density));
        int lines = Math.max(1, Math.min(MAX_LINES, linesFor(label, measure, sizePx, width)));
        return textHeightPx(lines, measure, sizePx) + 2 * insetPx(density);
    }

    /** The empty drop slot's height: a card of one line. */
    static int slotHeightPx(@NonNull Measure measure, float sizePx, float density) {
        return textHeightPx(1, measure, sizePx) + 2 * insetPx(density);
    }

    /** The cards' heights for {@code labels}, in order. */
    @NonNull
    static int[] cardHeightsPx(@NonNull List<String> labels, @NonNull Measure measure,
                               float sizePx, int cardWidthPx, float density) {
        int[] heights = new int[labels.size()];
        for (int i = 0; i < heights.length; i++)
            heights[i] = cardHeightPx(labels.get(i), measure, sizePx, cardWidthPx, density);
        return heights;
    }

    /** The rail's content height: every card, the slot, the gaps between them. */
    private static int usedPx(@NonNull int[] heights, int slotPx, float density) {
        int gap = Math.round(GAP_DP * density);
        int used = slotPx;
        for (int h : heights)
            used += h + gap;
        return used;
    }

    /**
     * The one label size for the hidden {@code labels}: {@code basePx} (what every possible
     * label fits at), stepped down by half a pixel, to no less than {@link #MIN_LABEL_SHRINK} of
     * it, until the cards, the slot and the zone's air fit {@code availablePx}. A column too
     * short even then runs on past its bottom at the smallest size.
     */
    static float stackLabelPx(@NonNull List<String> labels, @NonNull Measure measure,
                              float basePx, int cardWidthPx, int availablePx, float density) {
        float floor = basePx * MIN_LABEL_SHRINK;
        int air = 2 * Math.round(ZONE_PAD_DP * density);
        float size = basePx;
        while (size > floor) {
            int[] heights = cardHeightsPx(labels, measure, size, cardWidthPx, density);
            if (usedPx(heights, slotHeightPx(measure, size, density), density) + air
                <= availablePx)
                return size;
            size -= 0.5f;
        }
        return floor;
    }

    /**
     * The rail for cards of {@code heights} (top to bottom) and the slot {@code slotPx} tall,
     * in the column from {@code columnTopPx} down {@code columnHeightPx}, the cards
     * {@code leftPx} from the content view's left. The cards and the slot after them rest on
     * the column's bottom less the zone's air; the border hugs them (it never runs past the
     * slot to the column's bottom), and a rail too long for the column starts at its top and
     * runs on past its bottom.
     */
    @NonNull
    static Rail rail(@NonNull int[] heights, int slotPx, int leftPx, int columnTopPx,
                     int columnHeightPx, float density) {
        int n = heights.length;
        int pad = Math.round(ZONE_PAD_DP * density);
        int gap = Math.round(GAP_DP * density);
        int width = cardWidthPx(density);
        int used = usedPx(heights, slotPx, density);
        int contentBottom = columnTopPx + columnHeightPx - pad;
        int contentTop = Math.max(columnTopPx + pad, contentBottom - used);
        int[][] cards = new int[n][];
        int top = contentTop;
        for (int i = 0; i < n; i++) {
            cards[i] = new int[] {leftPx, top, leftPx + width, top + heights[i]};
            top += heights[i] + gap;
        }
        int[] slot = {leftPx, top, leftPx + width, top + slotPx};
        int[] border = {leftPx - pad, contentTop - pad, leftPx + width + pad, slot[3] + pad};
        return new Rail(cards, slot, border);
    }

    /**
     * The largest label size, at most {@code refPx}, at which every label fits the card's width:
     * each word within it, and the words wrapped greedily into no more than {@link #MAX_LINES}
     * lines. Stepped down by half a pixel to one pixel, so there is always an answer. One size
     * for all the labels keeps the cards alike.
     */
    static float labelSizePx(@NonNull List<String> labels, @NonNull Measure measure,
                             int cardWidthPx, float density, float refPx) {
        float width = Math.max(1, cardWidthPx - 2 * insetPx(density));
        for (float size = refPx; size > 1f; size -= 0.5f) {
            boolean fits = true;
            for (String label : labels) {
                if (linesFor(label, measure, size, width) > MAX_LINES) {
                    fits = false;
                    break;
                }
            }
            if (fits)
                return size;
        }
        return 1f;
    }

    /**
     * The lines {@code label} takes at {@code sizePx} in {@code widthPx}, wrapped at spaces; a
     * word wider than the width cannot be wrapped and counts as more lines than any card holds.
     */
    static int linesFor(@NonNull String label, @NonNull Measure measure, float sizePx,
                        float widthPx) {
        List<String> words = new ArrayList<>();
        for (String word : label.trim().split("\\s+")) {
            if (!word.isEmpty())
                words.add(word);
        }
        if (words.isEmpty())
            return 1;
        int lines = 1;
        float used = 0f;
        float space = measure.width(" ", sizePx);
        for (String word : words) {
            float w = measure.width(word, sizePx);
            if (w > widthPx)
                return MAX_LINES + 1;
            if (used == 0f) {
                used = w;
            } else if (used + space + w <= widthPx) {
                used += space + w;
            } else {
                lines++;
                used = w;
            }
        }
        return lines;
    }
}
