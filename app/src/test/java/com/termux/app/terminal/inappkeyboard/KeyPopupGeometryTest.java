package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The arithmetic behind the pressed-key popup: where it sits and how big its parts are. */
public class KeyPopupGeometryTest {

    private static final float R = KeyPopupGeometry.RING_RADIUS_DP;
    private static final float EPS = 0.01f;

    @Test
    public void theCentreGlyphStepsDownThroughFourSizesAsTheLabelGrows() {
        KeyPopupGeometry.Metrics one = KeyPopupGeometry.metricsFor("a", 1f);
        assertEquals(46f, one.glyphSizePx, EPS);
        assertEquals(1.4f, one.strokeWidthPx, EPS);
        assertEquals(34f, one.halfWidthPx, EPS);
        assertFalse("a single character keeps the label face", one.monospace);

        KeyPopupGeometry.Metrics two = KeyPopupGeometry.metricsFor("->", 1f);
        assertEquals(30f, two.glyphSizePx, EPS);
        assertTrue("everything wider than one character is monospace", two.monospace);

        KeyPopupGeometry.Metrics four = KeyPopupGeometry.metricsFor("Ctrl", 1f);
        assertEquals(22f, four.glyphSizePx, EPS);
        assertEquals(38f, four.halfWidthPx, EPS);

        KeyPopupGeometry.Metrics many = KeyPopupGeometry.metricsFor("space", 1f);
        assertEquals(17f, many.glyphSizePx, EPS);
        assertEquals(42f, many.halfWidthPx, EPS);
    }

    @Test
    public void everySizeIsInDeviceIndependentPixels() {
        KeyPopupGeometry.Metrics dense = KeyPopupGeometry.metricsFor("a", 3f);
        assertEquals(46f * 3f, dense.glyphSizePx, EPS);
        assertEquals(1.4f * 3f, dense.strokeWidthPx, EPS);
        assertEquals(14f * 3f, KeyPopupGeometry.ringGlyphSizePx("1", 3f), EPS);
    }

    @Test
    public void anAlternateWithMoreThanOneCharacterIsSmallerAndSitsFurtherOut() {
        KeyPopupGeometry.Metrics centre = KeyPopupGeometry.metricsFor("g", 1f);
        assertEquals(14f, KeyPopupGeometry.ringGlyphSizePx("-", 1f), EPS);
        assertEquals(11f, KeyPopupGeometry.ringGlyphSizePx("Esc", 1f), EPS);
        assertEquals(R, KeyPopupGeometry.orbitRadiusPx(centre, R, "-", false, 1f), EPS);
        assertEquals(R + 9f, KeyPopupGeometry.orbitRadiusPx(centre, R, "Esc", false, 1f), EPS);
    }

    @Test
    public void theTargetedAlternateIsFlungOutward() {
        KeyPopupGeometry.Metrics centre = KeyPopupGeometry.metricsFor("g", 1f);
        float rest = KeyPopupGeometry.orbitRadiusPx(centre, R, "-", false, 1f);
        float target = KeyPopupGeometry.orbitRadiusPx(centre, R, "-", true, 1f);
        assertEquals(13f, target - rest, EPS);
        assertEquals(1.45f, KeyPopupGeometry.TARGET_SCALE, EPS);
    }

    @Test
    public void aWideCentreLabelWidensTheWholeRing() {
        KeyPopupGeometry.Metrics narrow = KeyPopupGeometry.metricsFor("g", 1f);
        KeyPopupGeometry.Metrics wide = KeyPopupGeometry.metricsFor("space", 1f);
        float narrowOrbit = KeyPopupGeometry.orbitRadiusPx(narrow, R, "-", false, 1f);
        float wideOrbit = KeyPopupGeometry.orbitRadiusPx(wide, R, "-", false, 1f);
        assertEquals("the ring follows the label's half-width", 42f - 34f, wideOrbit - narrowOrbit,
            EPS);
    }

    @Test
    public void theHaloGrowsWithTheLabelAndTheRing() {
        KeyPopupGeometry.Metrics one = KeyPopupGeometry.metricsFor("a", 1f);
        assertEquals(Math.round((34f + R) * 1.55f) / 2f, KeyPopupGeometry.haloRadiusPx(one, R),
            EPS);
        KeyPopupGeometry.Metrics many = KeyPopupGeometry.metricsFor("space", 1f);
        assertTrue(KeyPopupGeometry.haloRadiusPx(many, R) > KeyPopupGeometry.haloRadiusPx(one, R));
    }

    @Test
    public void anEdgeColumnPopupSlidesInwardInsteadOfBeingCutOff() {
        KeyPopupGeometry.Metrics centre = KeyPopupGeometry.metricsFor("q", 1f);
        String[] ring = new String[9];
        ring[2] = "1";
        ring[3] = "Esc";
        assertTrue(KeyPopupGeometry.hasWideRing(ring));
        float side = KeyPopupGeometry.sideExtentPx(centre, R, true, 1f);
        assertEquals(R + 22f, side, EPS);

        // q sits at the very left of a 1080-wide keyboard, p at the very right.
        assertEquals(side + 8f, KeyPopupGeometry.anchorX(20f, side, 0f, 1080f, 1f), EPS);
        assertEquals(1080f - side - 8f, KeyPopupGeometry.anchorX(1060f, side, 0f, 1080f, 1f), EPS);
        // A key in the middle is not moved at all.
        assertEquals(540f, KeyPopupGeometry.anchorX(540f, side, 0f, 1080f, 1f), EPS);
    }

    @Test
    public void theClampIsRelativeToWhereTheKeyboardIsNotToTheScreen() {
        KeyPopupGeometry.Metrics centre = KeyPopupGeometry.metricsFor("q", 1f);
        float side = KeyPopupGeometry.sideExtentPx(centre, R, false, 1f);
        // A floating keyboard 400 wide, offset 300 from the left of the window.
        assertEquals(300f + side + 8f, KeyPopupGeometry.anchorX(310f, side, 300f, 700f, 1f), EPS);
    }

    @Test
    public void aTopRowPopupIsHeldInsideTheWindowAndABottomRowOneFloatsFreeOfItsCap() {
        KeyPopupGeometry.Metrics centre = KeyPopupGeometry.metricsFor("q", 1f);
        float below = KeyPopupGeometry.belowPx(centre, R, false, 1f);
        float above = KeyPopupGeometry.abovePx(centre, R, 1f);
        assertEquals(Math.max(R + 6f, 46f * 0.55f + 6f), below, EPS);
        assertEquals(Math.max(R + 8f, 46f * 0.62f), above, EPS);

        // Top row: the formula would put the anchor off the top of the window.
        assertEquals(above + 10f, KeyPopupGeometry.anchorY(10f, below, above, 0f, 1f), EPS);
        // Bottom row: clear of the cap by the full rise.
        assertEquals(900f - below - 20f, KeyPopupGeometry.anchorY(900f, below, above, 0f, 1f),
            EPS);
    }

    @Test
    public void aModifierLeavesRoomUnderItsGlyphForTheHeldMark() {
        KeyPopupGeometry.Metrics centre = KeyPopupGeometry.metricsFor("Ctrl", 1f);
        float plain = KeyPopupGeometry.belowPx(centre, R, false, 1f);
        float modifier = KeyPopupGeometry.belowPx(centre, R, true, 1f);
        assertEquals(12f, modifier - plain, EPS);
    }

    @Test
    public void theEightCornersPointTheWayTheKeyboardDrawsThem() {
        // 1 nw, 2 ne, 3 sw, 4 se, 5 w, 6 e, 7 n, 8 s.
        assertTrue(KeyPopupGeometry.DIRECTION_X[1] < 0 && KeyPopupGeometry.DIRECTION_Y[1] < 0);
        assertTrue(KeyPopupGeometry.DIRECTION_X[2] > 0 && KeyPopupGeometry.DIRECTION_Y[2] < 0);
        assertTrue(KeyPopupGeometry.DIRECTION_X[3] < 0 && KeyPopupGeometry.DIRECTION_Y[3] > 0);
        assertTrue(KeyPopupGeometry.DIRECTION_X[4] > 0 && KeyPopupGeometry.DIRECTION_Y[4] > 0);
        assertEquals(-1f, KeyPopupGeometry.DIRECTION_X[5], EPS);
        assertEquals(0f, KeyPopupGeometry.DIRECTION_Y[5], EPS);
        assertEquals(1f, KeyPopupGeometry.DIRECTION_X[6], EPS);
        assertEquals(-1f, KeyPopupGeometry.DIRECTION_Y[7], EPS);
        assertEquals(1f, KeyPopupGeometry.DIRECTION_Y[8], EPS);
    }

    @Test
    public void aRingOfSingleCharactersDoesNotAskForTheWiderClamp() {
        String[] ring = new String[9];
        ring[2] = "1";
        ring[7] = "-";
        assertFalse(KeyPopupGeometry.hasWideRing(ring));
        KeyPopupGeometry.Metrics centre = KeyPopupGeometry.metricsFor("g", 1f);
        assertEquals(R + 12f, KeyPopupGeometry.sideExtentPx(centre, R, false, 1f), EPS);
    }
}
