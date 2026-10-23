package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilamentLayoutTest {

    private final int[] glyphs = new int[FilamentLayout.MAX_TUBES];

    @Test
    public void tubeCountFollowsNameLengthWithinSixToTen() {
        assertEquals(6, FilamentLayout.tubeCount(0));
        assertEquals(6, FilamentLayout.tubeCount(4));
        assertEquals(8, FilamentLayout.tubeCount(8));
        assertEquals(10, FilamentLayout.tubeCount(25));
    }

    @Test
    public void shortNameIsCentredAndPaddedWithUnlitTubes() {
        int n = FilamentLayout.layout("home", glyphs);
        assertEquals(6, n);
        assertEquals(FilamentLayout.UNLIT, glyphs[0]);
        assertEquals(FilamentLayout.GLYPHS.indexOf('H'), glyphs[1]);
        assertEquals(FilamentLayout.GLYPHS.indexOf('E'), glyphs[4]);
        assertEquals(FilamentLayout.UNLIT, glyphs[5]);
    }

    @Test
    public void longNameIsCutAtTenAndUnknownCharactersBecomeQuestionMarks() {
        int n = FilamentLayout.layout("abcdefghijklmnop", glyphs);
        assertEquals(10, n);
        assertEquals(FilamentLayout.GLYPHS.indexOf('J'), glyphs[9]);
        FilamentLayout.layout("aé", glyphs);
        assertEquals(FilamentLayout.GLYPHS.indexOf('?'), glyphs[3]);
    }

    @Test
    public void nullAndEmptyNamesLeaveSixUnlitTubes() {
        assertEquals(6, FilamentLayout.layout(null, glyphs));
        for (int i = 0; i < 6; i++) assertEquals(FilamentLayout.UNLIT, glyphs[i]);
        assertEquals(6, FilamentLayout.layout("   ", glyphs));
    }

    @Test
    public void chipTextLosesItsLeadingIconGlyphAndSpaces() {
        assertEquals("claude", FilamentLayout.clean(" claude"));
        assertEquals("", FilamentLayout.clean(null));
    }

    @Test
    public void atlasHoldsEveryGlyphOfTheTable() {
        assertTrue(FilamentLayout.GLYPH_COUNT <= FilamentSource.COLS * FilamentSource.rows());
        assertEquals(FilamentLayout.GLYPH_COUNT, FilamentLayout.GLYPHS.codePointCount(0,
            FilamentLayout.GLYPHS.length()));
    }

    @Test
    public void tubesSettleLeftToRightBetweenFourAndSixHundredMs() {
        assertEquals(400L, FilamentLayout.settleMs(0, 8));
        assertEquals(600L, FilamentLayout.settleMs(7, 8));
        for (int i = 1; i < 8; i++) {
            assertTrue(FilamentLayout.settleMs(i, 8) > FilamentLayout.settleMs(i - 1, 8));
        }
    }

    @Test
    public void cyclingTubeScramblesThenSettlesOnTarget() {
        int target = FilamentLayout.GLYPHS.indexOf('C');
        // Mid-cycle every step shows a different glyph from the one before.
        for (long age = 0; age < 380L; age += FilamentLayout.CYCLE_STEP_MS) {
            int now = FilamentLayout.shownGlyph(2, 8, age, target);
            int next = FilamentLayout.shownGlyph(2, 8, age + FilamentLayout.CYCLE_STEP_MS, target);
            assertNotEquals(now, next);
            assertTrue(now >= 0 && now < FilamentLayout.GLYPH_COUNT);
        }
        assertEquals(target, FilamentLayout.shownGlyph(2, 8, FilamentLayout.settleMs(2, 8), target));
        assertEquals(target, FilamentLayout.shownGlyph(2, 8, 5000L, target));
        // No cycle running (negative age) shows the target; an unlit tube never lights.
        assertEquals(target, FilamentLayout.shownGlyph(2, 8, -1L, target));
        assertEquals(FilamentLayout.UNLIT,
            FilamentLayout.shownGlyph(0, 8, 100L, FilamentLayout.UNLIT));
    }

    @Test
    public void cycleBrightnessDipsGentlyAndReturnsToOne() {
        assertEquals(1f, FilamentLayout.cycleBrightness(-1L), 0f);
        assertEquals(1f, FilamentLayout.cycleBrightness(FilamentLayout.CYCLE_MS), 0f);
        float prev = FilamentLayout.cycleBrightness(0L);
        for (long age = 10L; age < FilamentLayout.CYCLE_MS; age += 10L) {
            float b = FilamentLayout.cycleBrightness(age);
            assertTrue(b >= 0.79f && b <= 1f);
            assertTrue("no step over 3 percent per 10 ms", Math.abs(b - prev) < 0.03f);
            prev = b;
        }
    }

    @Test
    public void lettersAreDealtAcrossTheNixiesInOrderThenRepeat() {
        int n = FilamentLayout.layout("claude", glyphs);
        int[] out = new int[7];
        FilamentLayout.spread(glyphs, n, 7, out);
        assertEquals(FilamentLayout.GLYPHS.indexOf('C'), out[0]);
        assertEquals(FilamentLayout.GLYPHS.indexOf('E'), out[5]);
        assertEquals(FilamentLayout.GLYPHS.indexOf('C'), out[6]);
        // A short name pads with unlit tubes in the layout; the dealing skips them.
        n = FilamentLayout.layout("ab", glyphs);
        FilamentLayout.spread(glyphs, n, 7, out);
        assertEquals(FilamentLayout.GLYPHS.indexOf('A'), out[0]);
        assertEquals(FilamentLayout.GLYPHS.indexOf('B'), out[1]);
        assertEquals(FilamentLayout.GLYPHS.indexOf('A'), out[2]);
        n = FilamentLayout.layout(null, glyphs);
        FilamentLayout.spread(glyphs, n, 7, out);
        for (int i = 0; i < 7; i++) assertEquals(FilamentLayout.UNLIT, out[i]);
    }

    @Test
    public void focusSlotRunsOneCycleAndIgnoresAChangeRightAfter() {
        SceneEvents e = new SceneEvents();
        assertEquals(-1L, e.focusAgeMs(1000L));
        assertFalse(e.active(1000L));
        e.addFocus(1000L);
        assertEquals(0L, e.focusAgeMs(1000L));
        assertTrue(e.active(1300L));
        e.addFocus(1200L); // within the gap: the cycle keeps its start
        assertEquals(300L, e.focusAgeMs(1300L));
        assertEquals(-1L, e.focusAgeMs(1000L + FilamentLayout.CYCLE_MS));
        assertFalse(e.active(1000L + FilamentLayout.CYCLE_MS));
        e.addFocus(2000L);
        e.clearFocus();
        assertEquals(-1L, e.focusAgeMs(2001L));
    }
}
