package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerHighlightShapeTest {

    private static final int PAD = 18;   // 6dp at 3x
    private static final int SPARE = 3;  // 1dp at 3x

    private static int[] resolve(int w, int h, int[] content, int cx, boolean center) {
        int[] out = new int[4];
        AppDrawerHighlightShape.resolve(w, h, content, cx, center, PAD, SPARE, out);
        return out;
    }

    @Test public void gridCellShortLabelHugsContentWithOnePaddingAndStaysSymmetric() {
        // 270px cell, icon 120px centred (75..195), short name narrower than the icon.
        int[] out = resolve(270, 400, new int[] {75, 40, 195, 330}, 135, true);
        assertEquals(75 - PAD, out[0]);
        assertEquals(195 + PAD, out[2]);
        assertEquals(135, (out[0] + out[2]) / 2);
        assertEquals(40 - PAD, out[1]);
        assertEquals(330 + PAD, out[3]);
    }

    @Test public void longLabelWiderThanIconWidensBothSidesEqually() {
        int[] out = resolve(270, 400, new int[] {20, 40, 250, 330}, 135, true);
        assertEquals(SPARE, out[0]);
        assertEquals(270 - SPARE, out[2]);
        assertEquals(135 - out[0], out[2] - 135);
    }

    @Test public void clampingKeepsSymmetryWhenOneSideRunsOutOfCell() {
        int[] out = resolve(270, 260, new int[] {30, 20, 150, 200}, 135, true);
        assertEquals(135 - out[0], out[2] - 135);
        assertTrue(out[0] >= SPARE);
        assertTrue(out[2] <= 270 - SPARE);
    }

    @Test public void tinyCellClampsToTheSpareOnEverySide() {
        int[] out = resolve(60, 60, new int[] {2, 2, 58, 58}, 30, true);
        assertArrayEquals(new int[] {SPARE, SPARE, 60 - SPARE, 60 - SPARE}, out);
    }

    @Test public void topEdgeAtTheCellTopIsClampedToTheSpare() {
        int[] out = resolve(270, 400, new int[] {75, 0, 195, 330}, 135, true);
        assertEquals(SPARE, out[1]);
    }

    @Test public void rowLayoutHugsIconAndTextWithoutCentring() {
        // Row 900 x 150: icon 24..156, two text lines ending at 520.
        int[] out = resolve(900, 150, new int[] {24, 27, 520, 123}, 90, false);
        assertEquals(24 - PAD, out[0]);
        assertEquals(520 + PAD, out[2]);
        assertEquals(27 - PAD, out[1]);
        assertEquals(123 + PAD, out[3]);
    }

    @Test public void largerFontTakesTheBeadTallerNotWider() {
        int[] normal = resolve(270, 400, new int[] {75, 40, 195, 300}, 135, true);
        int[] large = resolve(270, 400, new int[] {75, 40, 195, 340}, 135, true);
        assertEquals(normal[0], large[0]);
        assertEquals(normal[2], large[2]);
        assertEquals(normal[3] + 40, large[3]);
    }

    @Test public void degenerateCellFallsBackToItsWholeBounds() {
        int[] out = resolve(4, 4, new int[] {0, 0, 4, 4}, 2, true);
        assertArrayEquals(new int[] {0, 0, 4, 4}, out);
    }

    @Test public void radiusIsConcentricWithTheIconAndCapsAtAPill() {
        assertEquals(120 * 0.30f + PAD,
            AppDrawerHighlightShape.radiusPx(120, PAD, 300, 400), 0.001f);
        assertEquals(30f, AppDrawerHighlightShape.radiusPx(120, PAD, 300, 60), 0.001f);
    }
}
