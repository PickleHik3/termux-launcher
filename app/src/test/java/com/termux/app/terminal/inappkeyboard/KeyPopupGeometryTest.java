package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The arithmetic behind the pressed-key glyph: how big it is and where it sits. */
public class KeyPopupGeometryTest {

    private static final float EPS = 0.01f;

    @Test
    public void theGlyphStepsDownThroughFourSizesAsTheLabelGrows() {
        KeyPopupGeometry.Metrics one = KeyPopupGeometry.metricsFor("a", 1f);
        assertEquals(30f, one.glyphSizePx, EPS);
        assertEquals(300, one.weight);
        assertFalse("a single character keeps the label face", one.monospace);

        KeyPopupGeometry.Metrics two = KeyPopupGeometry.metricsFor("->", 1f);
        assertEquals(20f, two.glyphSizePx, EPS);
        assertEquals(400, two.weight);
        assertTrue("everything wider than one character is monospace", two.monospace);

        KeyPopupGeometry.Metrics four = KeyPopupGeometry.metricsFor("Ctrl", 1f);
        assertEquals(14f, four.glyphSizePx, EPS);
        assertEquals(500, four.weight);

        KeyPopupGeometry.Metrics many = KeyPopupGeometry.metricsFor("space", 1f);
        assertEquals(12f, many.glyphSizePx, EPS);
        assertEquals(500, many.weight);
    }

    @Test
    public void everySizeIsInDeviceIndependentPixels() {
        assertEquals(30f * 3f, KeyPopupGeometry.metricsFor("a", 3f).glyphSizePx, EPS);
        assertEquals(12f * 3f, KeyPopupGeometry.metricsFor("space", 3f).glyphSizePx, EPS);
    }

    @Test
    public void anEmptyOrMissingLabelIsSizedLikeASingleCharacter() {
        assertEquals(30f, KeyPopupGeometry.metricsFor(null, 1f).glyphSizePx, EPS);
        assertEquals(30f, KeyPopupGeometry.metricsFor("", 1f).glyphSizePx, EPS);
    }

    @Test
    public void anEdgeColumnGlyphSlidesInwardInsteadOfBeingCutOff() {
        float half = 10f;
        float margin = KeyPopupGeometry.SIDE_MARGIN_DP;
        // q sits at the very left of a 1080-wide keyboard, p at the very right.
        assertEquals(half + margin, KeyPopupGeometry.anchorX(5f, half, 0f, 1080f, 1f), EPS);
        assertEquals(1080f - half - margin, KeyPopupGeometry.anchorX(1075f, half, 0f, 1080f, 1f),
            EPS);
        // A key in the middle is not moved at all.
        assertEquals(540f, KeyPopupGeometry.anchorX(540f, half, 0f, 1080f, 1f), EPS);
    }

    @Test
    public void theClampIsRelativeToWhereTheKeyboardIsNotToTheScreen() {
        // A floating keyboard 400 wide, offset 300 from the left of the window.
        assertEquals(300f + 10f + 4f, KeyPopupGeometry.anchorX(302f, 10f, 300f, 700f, 1f), EPS);
        assertEquals(700f - 10f - 4f, KeyPopupGeometry.anchorX(698f, 10f, 300f, 700f, 1f), EPS);
    }

    @Test
    public void theSideMarginIsInDeviceIndependentPixelsToo() {
        assertEquals(10f + 4f * 3f, KeyPopupGeometry.anchorX(0f, 10f, 0f, 1080f, 3f), EPS);
    }

    @Test
    public void aGlyphWiderThanTheRoomItHasIsCentredOnWhatRoomThereIs() {
        assertEquals(250f, KeyPopupGeometry.anchorX(10f, 400f, 0f, 500f, 1f), EPS);
    }

    @Test
    public void theGlyphSitsJustAboveTheCapAndNeverAboveTheOverlay() {
        // Bottom row: the line box clears the cap's top edge by the 6dp gap.
        assertEquals(900f - 6f - 15f, KeyPopupGeometry.anchorY(900f, 30f, 0f, 1f), EPS);
        // A smaller glyph rides lower, because the rise follows the glyph.
        assertEquals(900f - 6f - 6f, KeyPopupGeometry.anchorY(900f, 12f, 0f, 1f), EPS);
        // Top row: the formula would put the glyph off the top of the overlay.
        assertEquals(15f, KeyPopupGeometry.anchorY(10f, 30f, 0f, 1f), EPS);
    }

    @Test
    public void theRiseIsInDeviceIndependentPixels() {
        assertEquals(900f - 6f * 2f - 30f, KeyPopupGeometry.anchorY(900f, 60f, 0f, 2f), EPS);
    }

    @Test
    public void theGlyphIsFarCloserToTheCapThanTheHaloFloatPopupWas() {
        // v1 lifted a single-character popup about 66dp; v2 lifts a 30dp glyph 21dp.
        float rise = 900f - KeyPopupGeometry.anchorY(900f, 30f, 0f, 1f);
        assertEquals(21f, rise, EPS);
    }
}
