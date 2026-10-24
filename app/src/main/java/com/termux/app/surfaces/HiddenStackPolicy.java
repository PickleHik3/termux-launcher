package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the stack of hidden-item cards beside the editor's frame, as pure arithmetic in
 * pixels: the card's size (the same for every card, shorter only when many cards must fit above
 * the half line), where each stands, the band the frame leaves clear for them, and the one label
 * size at which every possible label stands whole inside a card, wrapped at spaces on at most
 * {@link #MAX_LINES} lines, never cut.
 */
final class HiddenStackPolicy {

    private HiddenStackPolicy() {}

    static final int CARD_W_DP = 72;
    static final int CARD_H_DP = 44;
    /** The shortest a card shrinks to when the stack would not otherwise fit. */
    static final int MIN_CARD_H_DP = 30;
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
     * One card's height for {@code count} cards in {@code availablePx} of height: {@link
     * #CARD_H_DP}, or less down to {@link #MIN_CARD_H_DP} when that many would not fit. Never
     * less than the minimum: a stack too long for the window runs under the card sheet.
     */
    static int cardHeightPx(int count, int availablePx, float density) {
        int max = Math.round(CARD_H_DP * density);
        int min = Math.round(MIN_CARD_H_DP * density);
        int n = Math.max(1, count);
        int gap = Math.round(GAP_DP * density);
        int fit = (availablePx - gap * (n - 1)) / n;
        return Math.max(min, Math.min(max, fit));
    }

    /**
     * Where the first card's top stands so the last card's bottom rests on the bottom of the
     * column ({@code columnTopPx} down {@code columnHeightPx}) holding {@code count} cards; never
     * above the column's top, so a stack too long for it still starts there.
     */
    static int stackTopPx(int columnTopPx, int columnHeightPx, int count, int cardHeightPx,
                          float density) {
        int n = Math.max(0, count);
        int gap = Math.round(GAP_DP * density);
        int used = n == 0 ? 0 : n * cardHeightPx + (n - 1) * gap;
        return columnTopPx + Math.max(0, columnHeightPx - used);
    }

    /** The top of card {@code index}, counted from the stack's top. */
    static int cardTopPx(int stackTopPx, int index, int cardHeightPx, float density) {
        return stackTopPx + index * (cardHeightPx + Math.round(GAP_DP * density));
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

    /**
     * The card height for {@code count} cards and the drop slot in a column {@code columnPx}
     * tall: {@link #cardHeightPx} for one more card than there are, in the column less the
     * zone's air above and below.
     */
    static int railCardHeightPx(int count, int columnPx, float density) {
        int pad = Math.round(ZONE_PAD_DP * density);
        return cardHeightPx(Math.max(0, count) + 1, Math.max(0, columnPx - 2 * pad), density);
    }

    /**
     * The rail for {@code count} cards {@code cardHeightPx} tall in the column from
     * {@code columnTopPx} down {@code columnHeightPx}, the cards {@code leftPx} from the content
     * view's left. The cards and the slot after them rest on the column's bottom less the zone's
     * air; the border hugs them (it never runs past the slot to the column's bottom), and a rail
     * too long for the column starts at its top and runs on past its bottom, as the stack did.
     */
    @NonNull
    static Rail rail(int count, int leftPx, int columnTopPx, int columnHeightPx, int cardHeightPx,
                     float density) {
        int n = Math.max(0, count);
        int pad = Math.round(ZONE_PAD_DP * density);
        int gap = Math.round(GAP_DP * density);
        int width = cardWidthPx(density);
        int items = n + 1;
        int used = items * cardHeightPx + (items - 1) * gap;
        int contentBottom = columnTopPx + columnHeightPx - pad;
        int contentTop = Math.max(columnTopPx + pad, contentBottom - used);
        int[][] cards = new int[n][];
        for (int i = 0; i < n; i++) {
            int top = contentTop + i * (cardHeightPx + gap);
            cards[i] = new int[] {leftPx, top, leftPx + width, top + cardHeightPx};
        }
        int slotTop = contentTop + n * (cardHeightPx + gap);
        int[] slot = {leftPx, slotTop, leftPx + width, slotTop + cardHeightPx};
        int[] border = {leftPx - pad, contentTop - pad, leftPx + width + pad,
            slot[3] + pad};
        return new Rail(cards, slot, border);
    }

    /**
     * The largest label size, at most {@code refPx}, at which every label fits a card
     * {@code cardWidthPx} by {@code cardHeightPx} less {@code padPx} each side: each word within
     * the width, and the words wrapped greedily into no more lines than the height holds (and
     * {@link #MAX_LINES}). Stepped down by half a pixel to one pixel, so there is always an
     * answer. One size for all the labels keeps the cards alike.
     */
    static float labelSizePx(@NonNull List<String> labels, @NonNull Measure measure,
                             int cardWidthPx, int cardHeightPx, int padPx, float refPx) {
        float width = Math.max(1, cardWidthPx - 2 * padPx);
        float height = Math.max(1, cardHeightPx - 2 * padPx);
        for (float size = refPx; size > 1f; size -= 0.5f) {
            int lines = Math.min(MAX_LINES, (int) Math.floor(height / measure.lineHeight(size)));
            if (lines < 1)
                continue;
            boolean fits = true;
            for (String label : labels) {
                if (linesFor(label, measure, size, width) > lines) {
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
