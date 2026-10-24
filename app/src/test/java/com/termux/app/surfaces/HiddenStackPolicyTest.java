package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The stack of hidden items beside the frame as arithmetic, on pong's numbers (411 dp wide at
 * 2.625): every card alike, every label whole in its card at font scale 1, 1.3 and 2, and the
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

    @Test
    public void everyCardIsTheSameSizeAndShrinksOnlyWhenTheStackWouldNotFit() {
        int full = Math.round(HiddenStackPolicy.CARD_H_DP * DENSITY);
        int min = Math.round(HiddenStackPolicy.MIN_CARD_H_DP * DENSITY);
        assertEquals(full, HiddenStackPolicy.cardHeightPx(1, 2000, DENSITY));
        assertEquals(full, HiddenStackPolicy.cardHeightPx(7, 2000, DENSITY));
        int tight = HiddenStackPolicy.cardHeightPx(7, 7 * min + 6 * Math.round(8 * DENSITY),
            DENSITY);
        assertEquals(min, tight);
        // No room at all: the minimum, never zero.
        assertEquals(min, HiddenStackPolicy.cardHeightPx(7, 0, DENSITY));
        int mid = HiddenStackPolicy.cardHeightPx(7, 1200, DENSITY);
        assertTrue(mid >= min && mid <= full);
        assertTrue(7 * mid + 6 * Math.round(8 * DENSITY) <= 1200 || mid == min);
    }

    @Test
    public void cardsStandOneAfterAnotherFromTheTop() {
        int h = 100;
        int gap = Math.round(HiddenStackPolicy.GAP_DP * DENSITY);
        assertEquals(50, HiddenStackPolicy.cardTopPx(50, 0, h, DENSITY));
        assertEquals(50 + h + gap, HiddenStackPolicy.cardTopPx(50, 1, h, DENSITY));
        assertEquals(50 + 3 * (h + gap), HiddenStackPolicy.cardTopPx(50, 3, h, DENSITY));
    }

    @Test
    public void everyLabelStandsWholeInItsCardAtEveryFontScale() {
        int width = HiddenStackPolicy.cardWidthPx(DENSITY);
        int pad = Math.round(HiddenStackPolicy.PAD_DP * DENSITY) + 1;
        for (float scale : new float[] {1f, 1.3f, 2f}) {
            for (int cardH : new int[] {Math.round(HiddenStackPolicy.CARD_H_DP * DENSITY),
                Math.round(HiddenStackPolicy.MIN_CARD_H_DP * DENSITY)}) {
                float size = HiddenStackPolicy.labelSizePx(LABELS, MEASURE, width, cardH, pad,
                    refPx(scale));
                assertTrue(size <= refPx(scale));
                float innerW = width - 2 * pad;
                float innerH = cardH - 2 * pad;
                int lines = Math.min(HiddenStackPolicy.MAX_LINES,
                    (int) Math.floor(innerH / MEASURE.lineHeight(size)));
                assertTrue("a line of text fits the card's height at " + scale, lines >= 1);
                for (String label : LABELS) {
                    assertTrue(label + " fits at " + scale + " in " + cardH + "px",
                        HiddenStackPolicy.linesFor(label, MEASURE, size, innerW) <= lines);
                    for (String word : label.split(" "))
                        assertTrue(word + " is not wider than the card",
                            MEASURE.width(word, size) <= innerW);
                }
            }
        }
    }

    @Test
    public void aLabelThatAlreadyFitsKeepsItsReferenceSize() {
        float size = HiddenStackPolicy.labelSizePx(Arrays.asList("Keys"), MEASURE,
            HiddenStackPolicy.cardWidthPx(DENSITY),
            Math.round(HiddenStackPolicy.CARD_H_DP * DENSITY), 12, refPx(1f));
        assertEquals(refPx(1f), size, 0.001f);
    }

    @Test
    public void aLongWordShrinksTheSizeRatherThanBeingCut() {
        List<String> labels = Arrays.asList("Extraordinarily");
        int width = HiddenStackPolicy.cardWidthPx(DENSITY);
        int pad = Math.round(HiddenStackPolicy.PAD_DP * DENSITY) + 1;
        float size = HiddenStackPolicy.labelSizePx(labels, MEASURE, width,
            Math.round(HiddenStackPolicy.CARD_H_DP * DENSITY), pad, refPx(2f));
        assertTrue(MEASURE.width("Extraordinarily", size) <= width - 2 * pad);
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
