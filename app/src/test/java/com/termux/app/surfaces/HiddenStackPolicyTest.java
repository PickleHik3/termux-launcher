package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The stack of hidden items beside the frame as arithmetic, on pong's numbers (411 dp wide at
 * 2.625): each card as tall as its own label, every label whole in the card's width at font scale 1, 1.3 and 2, and the
 * frame moved and scaled clear of the stack.
 */
public class HiddenStackPolicyTest {

    private static final float DENSITY = 2.625f;
    private static final int WINDOW_W = 1079;

    /** Glyphs 0.55 em wide, lines 1.2 em tall: close to a sans face, and exact for the test. */
    private static final HiddenStackPolicy.Measure MEASURE = new HiddenStackPolicy.Measure() {
        @Override public float width(@NonNull String text, float sizePx) {
            return text.length() * 0.55f * sizePx;
        }

        @Override public float lineHeight(float sizePx) {
            return 1.2f * sizePx;
        }
    };

    private static final List<String> LABELS = Arrays.asList("Status bar", "Pinned apps",
        "A–Z index", "Extra keys", "Keyboard", "Search field", "Drawer tile");

    private static float refPx(float fontScale) {
        return HiddenStackPolicy.LABEL_SP * fontScale * DENSITY;
    }

    private static final int CARD_W = HiddenStackPolicy.cardWidthPx(DENSITY);
    private static final int COLUMN_TOP = 120;
    private static final int COLUMN_H = 1500;
    private static final int LEFT = Math.round(HiddenStackPolicy.SIDE_DP * DENSITY);
    /** One word and two words: a one-line card and a two-line card at the reference size. */
    private static final List<String> MIXED = Arrays.asList("Keyboard", "Pinned apps",
        "A–Z index", "Status bar");

    private static float labelPx(float fontScale) {
        return HiddenStackPolicy.labelSizePx(LABELS, MEASURE, CARD_W, DENSITY, refPx(fontScale));
    }

    private static HiddenStackPolicy.Rail rail(List<String> labels, float sizePx) {
        int[] heights = HiddenStackPolicy.cardHeightsPx(labels, MEASURE, sizePx, CARD_W, DENSITY);
        return HiddenStackPolicy.rail(heights,
            HiddenStackPolicy.slotHeightPx(MEASURE, sizePx, DENSITY), LEFT, COLUMN_TOP, COLUMN_H,
            DENSITY);
    }

    @Test
    public void eachCardIsAsTallAsItsOwnLinesAndThePadding() {
        float size = labelPx(1f);
        int inset = HiddenStackPolicy.insetPx(DENSITY);
        int line = (int) Math.ceil(MEASURE.lineHeight(size));
        int one = HiddenStackPolicy.cardHeightPx("Keyboard", MEASURE, size, CARD_W, DENSITY);
        int two = HiddenStackPolicy.cardHeightPx("Pinned apps", MEASURE, size, CARD_W, DENSITY);
        assertEquals(line + 2 * inset, one);
        assertEquals(2 * line + 2 * inset, two);
        assertEquals(one, HiddenStackPolicy.slotHeightPx(MEASURE, size, DENSITY));
    }

    @Test
    public void cardsOfOneAndTwoLinesDifferAndKeepTheSameGap() {
        float size = labelPx(1f);
        int gap = Math.round(HiddenStackPolicy.GAP_DP * DENSITY);
        HiddenStackPolicy.Rail rail = rail(MIXED, size);
        int[] first = rail.cards[0];
        int[] second = rail.cards[1];
        assertTrue("a one-line card is shorter than a two-line one",
            first[3] - first[1] < second[3] - second[1]);
        for (int i = 1; i < rail.cards.length; i++)
            assertEquals(rail.cards[i - 1][3] + gap, rail.cards[i][1]);
        assertEquals(rail.cards[rail.cards.length - 1][3] + gap, rail.slot[1]);
    }

    @Test
    public void theSizeShrinksOnlyWhenTheStackWouldNotFitAndNeverBelowItsFloor() {
        float base = labelPx(1f);
        assertEquals(base, HiddenStackPolicy.stackLabelPx(MIXED, MEASURE, base, CARD_W, 2000,
            DENSITY), 0f);
        float tight = HiddenStackPolicy.stackLabelPx(MIXED, MEASURE, base, CARD_W, 450, DENSITY);
        assertTrue(tight < base);
        int[] heights = HiddenStackPolicy.cardHeightsPx(MIXED, MEASURE, tight, CARD_W, DENSITY);
        int gap = Math.round(HiddenStackPolicy.GAP_DP * DENSITY);
        int air = 2 * Math.round(HiddenStackPolicy.ZONE_PAD_DP * DENSITY);
        int used = HiddenStackPolicy.slotHeightPx(MEASURE, tight, DENSITY) + air;
        for (int h : heights) used += h + gap;
        assertTrue(used <= 450);
        // No room at all: the floor, and the rail runs on past the column.
        assertEquals(base * HiddenStackPolicy.MIN_LABEL_SHRINK,
            HiddenStackPolicy.stackLabelPx(MIXED, MEASURE, base, CARD_W, 0, DENSITY), 0f);
    }

    private static HiddenStackPolicy.Rail rail(int count) {
        return rail(MIXED.subList(0, Math.min(count, MIXED.size())), labelPx(1f));
    }

    private static boolean strictlyInside(int[] inner, int[] outer) {
        return inner[0] > outer[0] && inner[1] > outer[1] && inner[2] < outer[2]
            && inner[3] < outer[3];
    }

    @Test
    public void theRailsBorderHoldsEveryCardWithTheSameAirOnEverySide() {
        int pad = Math.round(HiddenStackPolicy.ZONE_PAD_DP * DENSITY);
        for (int count = 0; count <= MIXED.size(); count++) {
            HiddenStackPolicy.Rail rail = rail(count);
            assertEquals(count, rail.cards.length);
            for (int[] card : rail.cards)
                assertTrue(count + " cards: a card inside the border",
                    strictlyInside(card, rail.border));
            assertTrue(count + " cards: the slot inside the border",
                strictlyInside(rail.slot, rail.border));
            int[] first = count == 0 ? rail.slot : rail.cards[0];
            assertEquals("air on the left", pad, first[0] - rail.border[0]);
            assertEquals("air on the right", pad, rail.border[2] - first[2]);
            assertEquals("air above", pad, first[1] - rail.border[1]);
            assertEquals("air below", pad, rail.border[3] - rail.slot[3]);
        }
    }

    @Test
    public void theEmptySlotComesAfterTheLastCardAndTheBorderHugsThem() {
        int gap = Math.round(HiddenStackPolicy.GAP_DP * DENSITY);
        int pad = Math.round(HiddenStackPolicy.ZONE_PAD_DP * DENSITY);
        HiddenStackPolicy.Rail rail = rail(3);
        int total = rail.slot[3] - rail.slot[1];
        for (int[] card : rail.cards)
            total += card[3] - card[1];
        assertEquals(rail.cards[2][3] + gap, rail.slot[1]);
        // Resting on the column's bottom, the border's top hugs the first card, not the column.
        assertEquals(COLUMN_TOP + COLUMN_H, rail.border[3]);
        assertEquals(total + 3 * gap + 2 * pad, rail.border[3] - rail.border[1]);
        assertTrue(rail.border[1] > COLUMN_TOP);
    }

    @Test
    public void theBorderStaysInTheBandBesideTheFrame() {
        HiddenStackPolicy.Rail rail = rail(2);
        assertTrue("clear of the window's edge", rail.border[0] > 0);
        assertTrue("clear of the frame", rail.border[2] < HiddenStackPolicy.reservePx(DENSITY));
    }

    @Test
    public void aRailTooLongForItsColumnStartsAtTheTopUnderTheBordersAir() {
        int pad = Math.round(HiddenStackPolicy.ZONE_PAD_DP * DENSITY);
        float size = labelPx(1f);
        int[] heights = HiddenStackPolicy.cardHeightsPx(MIXED, MEASURE, size, CARD_W, DENSITY);
        HiddenStackPolicy.Rail rail = HiddenStackPolicy.rail(heights,
            HiddenStackPolicy.slotHeightPx(MEASURE, size, DENSITY), LEFT, COLUMN_TOP, 100,
            DENSITY);
        assertEquals(COLUMN_TOP, rail.border[1]);
        assertEquals(COLUMN_TOP + pad, rail.cards[0][1]);
    }

    @Test
    public void everyLabelStandsWholeInTheCardsWidthAtEveryFontScale() {
        float innerW = CARD_W - 2 * HiddenStackPolicy.insetPx(DENSITY);
        for (float scale : new float[] {1f, 1.3f, 2f}) {
            float size = labelPx(scale);
            assertTrue(size <= refPx(scale));
            for (String label : LABELS) {
                assertTrue(label + " fits at " + scale,
                    HiddenStackPolicy.linesFor(label, MEASURE, size, innerW)
                        <= HiddenStackPolicy.MAX_LINES);
                for (String word : label.split(" "))
                    assertTrue(word + " is not wider than the card",
                        MEASURE.width(word, size) <= innerW);
            }
        }
    }

    @Test
    public void aLabelThatAlreadyFitsKeepsItsReferenceSize() {
        assertEquals(refPx(1f), HiddenStackPolicy.labelSizePx(Arrays.asList("Keys"), MEASURE,
            CARD_W, DENSITY, refPx(1f)), 0.001f);
    }

    @Test
    public void aLongWordShrinksTheSizeRatherThanBeingCut() {
        float size = HiddenStackPolicy.labelSizePx(Arrays.asList("Extraordinarily"), MEASURE,
            CARD_W, DENSITY, refPx(2f));
        assertTrue(MEASURE.width("Extraordinarily", size)
            <= CARD_W - 2 * HiddenStackPolicy.insetPx(DENSITY));
        assertTrue(size < refPx(2f));
    }

    @Test
    public void linesWrapAtSpacesOnly() {
        // 3 glyphs at 10px = 16.5px a word, a space 5.5px: two words need 38.5px.
        assertEquals(1, HiddenStackPolicy.linesFor("abc abc", MEASURE, 10f, 40f));
        assertEquals(2, HiddenStackPolicy.linesFor("abc abc", MEASURE, 10f, 30f));
        assertEquals(3, HiddenStackPolicy.linesFor("abc abc abc", MEASURE, 10f, 17f));
        assertEquals(HiddenStackPolicy.MAX_LINES + 1,
            HiddenStackPolicy.linesFor("abcdefgh", MEASURE, 10f, 30f));
    }

    private static EditorFramePose.Reserve reserve() {
        return new EditorFramePose.Reserve(HiddenStackPolicy.reservePx(DENSITY), 0, WINDOW_W,
            WINDOW_W, Math.round(HiddenStackPolicy.SIDE_DP * DENSITY));
    }

    @Test
    public void theFrameClearsTheStackAndKeepsItsRightMargin() {
        EditorFramePose.Reserve reserve = reserve();
        for (float scale : new float[] {0.84f, 0.76f, 0.6f, 0.44f}) {
            float s = Math.min(scale, reserve.maxScale());
            float left = WINDOW_W * (1f - s) / 2f + reserve.shiftFor(s);
            float right = left + WINDOW_W * s;
            assertTrue("left edge clears the stack at " + scale, left >= reserve.leftPx - 0.01f);
            assertTrue("right edge keeps its margin at " + scale,
                right <= WINDOW_W - reserve.rightMarginPx + 0.01f);
        }
    }

    @Test
    public void aFrameAlreadyClearOfTheStackIsNotMoved() {
        EditorFramePose.Reserve reserve = reserve();
        assertEquals(0f, reserve.shiftFor(0.3f), 0f);
    }

    @Test
    public void theScaleIsCappedOnlyWhereTheStackLeavesTooLittleWidth() {
        EditorFramePose.Reserve reserve = reserve();
        float room = (WINDOW_W - reserve.rightMarginPx - reserve.leftPx) / (float) WINDOW_W;
        assertEquals(room, reserve.maxScale(), 0.0001f);
        assertTrue(reserve.maxScale() < EditorFramePose.REST_MAX_SCALE);
        assertTrue(reserve.maxScale() > EditorFramePose.HALF_SCALE);
    }
}
