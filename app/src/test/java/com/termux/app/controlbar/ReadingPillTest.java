package com.termux.app.controlbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Round 5: "100%" was cut to "100" with the keyboard up and down the rail. */
public class ReadingPillTest {

    @Test
    public void aRowPillKeepsItsDesignLengthWhenTheTextFits() {
        assertEquals(60f, ReadingPill.rowLengthPx(60f, 16f, 3f, 20f, 8f), 0f);
    }

    @Test
    public void aRowPillGrowsToHoldTheWholeReading() {
        // 8 + 16 + 3 + 40 + 8 = 75: "100%" at a large font.
        assertEquals(75f, ReadingPill.rowLengthPx(60f, 16f, 3f, 40f, 8f), 0f);
    }

    @Test
    public void anIconAloneNeedsNoGap() {
        assertEquals(32f, ReadingPill.rowLengthPx(0f, 16f, 3f, 0f, 8f), 0f);
    }

    @Test
    public void railTextShrinksToTheRailsWidth() {
        assertEquals(1f, ReadingPill.fitScale(30f, 36f, 0.6f), 0f);
        float scale = ReadingPill.fitScale(45f, 36f, 0.6f);
        assertEquals(0.8f, scale, 1e-6f);
        assertTrue(45f * scale <= 36f);
        assertEquals("never unreadably small", 0.6f, ReadingPill.fitScale(100f, 36f, 0.6f), 0f);
    }

    /** Review P3-6: past the least scale the rail shows the number alone rather than clip it. */
    @Test
    public void aRailTooNarrowEvenAtTheLeastScaleFallsBackToTheNumber() {
        assertTrue(ReadingPill.wholeFits(46f, 36f, 0.75f));
        assertTrue(!ReadingPill.wholeFits(80f, 36f, 0.6f));
    }
    /**
     * Round 6 (emulator, 50): in the dark theme the bar's pale ink was faint on the pill at the
     * bar's bright end. The pill's backing is the side away from its ink, enough to carry the
     * reading to large-text contrast (3:1) on a bright ground under pale ink and on a dark ground
     * under dark ink.
     */
    @Test
    public void theBackingCarriesTheReadingOnAGroundUnlikeTheBarsFace() {
        int pale = 0xFFF0F0F0;
        int dark = 0xFF1C1B1F;
        int brightYellow = 0xFFDCD78C;
        int darkGreen = 0xFF145A32;
        int underPale = ReadingPill.backingColor(pale);
        int underDark = ReadingPill.backingColor(dark);
        assertTrue("under pale ink a dark veil", ReadingPill.luminance(underPale) < 0.05d);
        assertTrue("under dark ink a pale veil", ReadingPill.luminance(underDark) > 0.8d);
        assertTrue(contrast(pale, over(underPale, brightYellow)) >= 3d);
        assertTrue(contrast(dark, over(underDark, darkGreen)) >= 3d);
        // Bare, the pale ink on that end failed.
        assertTrue(contrast(pale, brightYellow) < 2d);
    }

    private static double contrast(int a, int b) {
        double la = ReadingPill.luminance(a) + 0.05d;
        double lb = ReadingPill.luminance(b) + 0.05d;
        return Math.max(la, lb) / Math.min(la, lb);
    }

    /** {@code top} (ARGB) over the opaque {@code ground}. */
    private static int over(int top, int ground) {
        float a = (top >>> 24) / 255f;
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int t = (top >> shift) & 0xFF;
            int g = (ground >> shift) & 0xFF;
            out |= Math.round(t * a + g * (1f - a)) << shift;
        }
        return out;
    }

    /**
     * Review P3: through a page crossing the bar's ink blends between the two pages'; the backing
     * follows it on the same curve, with no switch, and the theme's inks take the glass halos'
     * own colours.
     */
    @Test
    public void theBackingBlendsWithTheInkAndWearsTheGlassHalos() {
        int pale = 0xFFF0F0F0;
        int dark = 0xFF1C1B1F;
        assertEquals((ReadingPill.BACKING_ALPHA << 24) | (com.termux.app.chrome.GlassInk.HALO_DARK
            & 0xFFFFFF), ReadingPill.backingColor(pale));
        assertEquals((ReadingPill.BACKING_ALPHA << 24) | (com.termux.app.chrome.GlassInk.HALO_PALE
            & 0xFFFFFF), ReadingPill.backingColor(dark));
        int before = ReadingPill.backingColor(dark);
        for (int step = 1; step <= 64; step++) {
            int ink = over(((step * 255 / 64) << 24) | (pale & 0xFFFFFF), dark);
            int now = ReadingPill.backingColor(ink);
            for (int shift = 16; shift >= 0; shift -= 8) {
                int jump = Math.abs(((now >> shift) & 0xFF) - ((before >> shift) & 0xFF));
                assertTrue("step " + step + " jumps " + jump, jump <= 40);
            }
            before = now;
        }
    }
}
