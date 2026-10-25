package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContrastFloorTest {

    /** Dark grounds: a dark wallpaper through a thin pane, the usual terminal backgrounds. */
    private static final int[] DARK_BACKDROPS = {
        0xFF000000, 0xFF0B0F14, 0xFF1E1E2E, 0xFF202830, 0xFF2D3A2E, 0xFF3A2A40, 0xFF555555,
    };
    /** Pale grounds: a pale wallpaper behind a clear surface, light themes. */
    private static final int[] PALE_BACKDROPS = {
        0xFFFFFFFF, 0xFFF5F0E6, 0xFFDDE6F0, 0xFFC8D8C0, 0xFFB0B0B0,
    };

    @Test
    public void luminanceAndRatioFollowWcag() {
        assertEquals(0d, ContrastFloor.luminance(0xFF000000), 1e-9);
        assertEquals(1d, ContrastFloor.luminance(0xFFFFFFFF), 1e-9);
        assertEquals(21d, ContrastFloor.contrast(0xFFFFFFFF, 0xFF000000), 1e-9);
        assertEquals(21d, ContrastFloor.contrast(0xFF000000, 0xFFFFFFFF), 1e-9);
        // #777777 on white is the textbook just-under-AA grey.
        assertEquals(4.48d, ContrastFloor.contrast(0xFF777777, 0xFFFFFFFF), 0.01d);
    }

    @Test
    public void colourAlreadyAtItsFloorIsUntouched() {
        assertEquals(0xFFFFFFFF, ContrastFloor.atLeast(0xFFFFFFFF, 0xFF000000, ContrastFloor.TEXT));
        assertEquals(0xFFE0DEF4, ContrastFloor.atLeast(0xFFE0DEF4, 0xFF191724, ContrastFloor.TEXT));
        assertEquals(0xFF1C1B1F, ContrastFloor.atLeast(0xFF1C1B1F, 0xFFF5F0E6, ContrastFloor.TEXT));
        // Exactly the colour, alpha included, not a re-derived equal.
        assertEquals(0x80FFFFFF, ContrastFloor.atLeast(0x80FFFFFF, 0xFF000000, ContrastFloor.TEXT));
    }

    @Test
    public void everyColourReachesItsFloorOnEveryBackdrop() {
        for (int backdrop : concat(DARK_BACKDROPS, PALE_BACKDROPS)) {
            for (int color : sweep()) {
                int text = ContrastFloor.atLeast(color, backdrop, ContrastFloor.TEXT);
                assertTrue(hex(color) + " on " + hex(backdrop),
                    ContrastFloor.contrast(text, backdrop) >= ContrastFloor.TEXT);
                int dim = ContrastFloor.atLeast(color, backdrop, ContrastFloor.DIM_TEXT);
                assertTrue(hex(color) + " dim on " + hex(backdrop),
                    ContrastFloor.contrast(dim, backdrop) >= ContrastFloor.DIM_TEXT);
            }
        }
    }

    @Test
    public void nudgeIsTheLeastThatReachesTheFloor() {
        // The floor is met, and not overshot by more than one mix step can move it.
        for (int backdrop : concat(DARK_BACKDROPS, PALE_BACKDROPS)) {
            for (int color : sweep()) {
                if (ContrastFloor.contrast(color, backdrop) >= ContrastFloor.TEXT) continue;
                double reached = ContrastFloor.contrast(
                    ContrastFloor.atLeast(color, backdrop, ContrastFloor.TEXT), backdrop);
                assertTrue(hex(color) + " on " + hex(backdrop) + " reached " + reached,
                    reached < ContrastFloor.TEXT + 0.2d);
            }
        }
    }

    @Test
    public void nudgeMovesAwayFromTheBackdropAndKeepsHue() {
        // The welcome sheet's faint description grey on a dark wallpaper goes lighter, not darker.
        int grey = 0xFF4A4A4A;
        int lifted = ContrastFloor.atLeast(grey, 0xFF101418, ContrastFloor.TEXT);
        assertTrue(ContrastFloor.luminance(lifted) > ContrastFloor.luminance(grey));
        // A pale accent on a pale wallpaper goes darker.
        int paleBlue = 0xFF9EC5FF;
        int deepened = ContrastFloor.atLeast(paleBlue, 0xFFF5F0E6, ContrastFloor.TEXT);
        assertTrue(ContrastFloor.luminance(deepened) < ContrastFloor.luminance(paleBlue));
        assertEquals(hue(paleBlue), hue(deepened), 3f);
        // ANSI blue on black: lifted toward white, still blue.
        int blue = 0xFF2A5ADA;
        int liftedBlue = ContrastFloor.atLeast(blue, 0xFF000000, ContrastFloor.TEXT);
        assertNotEquals(blue, liftedBlue);
        assertEquals(hue(blue), hue(liftedBlue), 3f);
    }

    @Test
    public void midGreyBackdropFallsBackToTheSideThatReaches() {
        // Lightening #9A9A9A on #808080 cannot reach 4.5 (white only makes ~3.95); black can.
        int backdrop = 0xFF808080;
        int ink = ContrastFloor.atLeast(0xFF9A9A9A, backdrop, ContrastFloor.TEXT);
        assertTrue(ContrastFloor.contrast(ink, backdrop) >= ContrastFloor.TEXT);
        assertTrue(ContrastFloor.luminance(ink) < ContrastFloor.luminance(backdrop));
    }

    @Test
    public void alphaIsKept() {
        int ink = ContrastFloor.atLeast(0x80303030, 0xFF000000, ContrastFloor.TEXT);
        assertEquals(0x80, ink >>> 24);
    }

    @Test
    public void dimStaysDimmerThanNormalOnDarkBackdrops() {
        // On a dark ground the faint ink never reads louder than the normal ink beside it, floors
        // included: both at their floors, or the dim one below the normal one's.
        for (int backdrop : DARK_BACKDROPS) {
            ContrastFloor.Memo memo = new ContrastFloor.Memo();
            memo.setBackdrop(backdrop);
            for (int color : sweep()) {
                double normal = ContrastFloor.contrast(memo.text(color), backdrop);
                double dim = ContrastFloor.contrast(memo.dimText(color), backdrop);
                assertTrue(hex(color) + " on " + hex(backdrop) + ": dim " + dim + " vs " + normal,
                    dim <= normal + 1e-9);
            }
        }
    }

    @Test
    public void floorNeverInvertsTheOrderItWasGiven() {
        // Wherever the faint ink read quieter than the normal one before the floor, it still does.
        for (int backdrop : concat(DARK_BACKDROPS, PALE_BACKDROPS)) {
            ContrastFloor.Memo memo = new ContrastFloor.Memo();
            memo.setBackdrop(backdrop);
            for (int color : sweep()) {
                double rawNormal = ContrastFloor.contrast(color, backdrop);
                double rawDim = ContrastFloor.contrast(ContrastFloor.dim(color), backdrop);
                if (rawDim > rawNormal) continue;
                assertTrue(hex(color) + " on " + hex(backdrop),
                    ContrastFloor.contrast(memo.dimText(color), backdrop)
                        <= ContrastFloor.contrast(memo.text(color), backdrop) + 1e-9);
            }
        }
    }

    @Test
    public void memoAnswersAsTheSearchDoesAndForgetsOnANewBackdrop() {
        ContrastFloor.Memo memo = new ContrastFloor.Memo();
        memo.setBackdrop(0xFF000000);
        for (int color : sweep()) {
            assertEquals(ContrastFloor.atLeast(color, 0xFF000000, ContrastFloor.TEXT),
                memo.text(color));
            assertEquals(ContrastFloor.atLeast(ContrastFloor.dim(color), 0xFF000000,
                ContrastFloor.DIM_TEXT), memo.dimText(color));
        }
        int grey = 0xFF606060;
        int onBlack = memo.text(grey);
        memo.setBackdrop(0xFFFFFFFF);
        assertNotEquals(onBlack, memo.text(grey));
        assertEquals(ContrastFloor.atLeast(grey, 0xFFFFFFFF, ContrastFloor.TEXT), memo.text(grey));
    }

    @Test
    public void compositeLaysTheTintAtItsAlpha() {
        assertEquals(0xFFFFFFFF, ContrastFloor.composite(0x00000000, 0xFFFFFFFF));
        assertEquals(0xFF000000, ContrastFloor.composite(0xFF000000, 0xFFFFFFFF));
        // Black at 128/255 leaves 127/255 of white.
        assertEquals(0xFF7F7F7F, ContrastFloor.composite(0x80000000, 0xFFFFFFFF));
    }

    @Test
    public void seamRunsAreRecognised() {
        assertTrue(TerminalRenderer.isSeamRun("─│█".toCharArray(), 0, 3));
        assertTrue(TerminalRenderer.isSeamRun("".toCharArray(), 0, 1));
        assertTrue(TerminalRenderer.isSeamRun("  ".toCharArray(), 0, 3));
        assertFalse(TerminalRenderer.isSeamRun("~ $".toCharArray(), 0, 3));
        assertFalse(TerminalRenderer.isSeamRun("─x".toCharArray(), 0, 2));
        assertFalse(TerminalRenderer.isSeamRun(new char[0], 0, 0));
    }

    /** A spread of greys and hues, saturated and muted, light and dark. */
    private static int[] sweep() {
        int[] channel = {0x00, 0x22, 0x44, 0x66, 0x88, 0xAA, 0xCC, 0xEE, 0xFF};
        int[] out = new int[channel.length * channel.length * channel.length];
        int i = 0;
        for (int r : channel) for (int g : channel) for (int b : channel) {
            out[i++] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** HSV hue in degrees, computed here so the test needs no Android framework. */
    private static float hue(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (delta == 0f) return 0f;
        float h;
        if (max == r) h = ((g - b) / delta) % 6f;
        else if (max == g) h = (b - r) / delta + 2f;
        else h = (r - g) / delta + 4f;
        h *= 60f;
        return h < 0f ? h + 360f : h;
    }

    private static String hex(int color) {
        return String.format("#%08X", color);
    }
}
