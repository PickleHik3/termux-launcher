package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.statusbar.WindowActivityRing;

import org.junit.Test;

/** The chip watermark's numbers: strength, corner dots, the travelling arc, the × as it opens. */
public class ChipWatermarkGeometryTest {

    @Test
    public void theWatermarkIsFifteenPercentAtRestAndTwentySixSelected() {
        assertEquals(38, ChipWatermarkGeometry.glyphAlpha(0f));
        assertEquals(66, ChipWatermarkGeometry.glyphAlpha(1f));
        // A selection slide brightens it on the way rather than switching at the end.
        assertEquals(52, ChipWatermarkGeometry.glyphAlpha(0.5f), 2);
        // Out of range is not a brighter watermark.
        assertEquals(38, ChipWatermarkGeometry.glyphAlpha(-3f));
        assertEquals(66, ChipWatermarkGeometry.glyphAlpha(4f));
        assertEquals(Math.round(255 * .15f), ChipWatermarkGeometry.GLYPH_ALPHA);
        assertEquals(Math.round(255 * .26f), ChipWatermarkGeometry.SELECTED_GLYPH_ALPHA);
    }

    /**
     * The glyph is taller than the chip and hugs its leading edge, so it is cropped by the outline
     * rather than floating inside it, and the title is pushed clear of it.
     */
    @Test
    public void theGlyphHugsTheLeadingEdgeAndOutgrowsTheChip() {
        assertEquals(21f, ChipWatermarkGeometry.GLYPH_SIZE_DP, .0001f);
        assertEquals(2f, ChipWatermarkGeometry.GLYPH_LEADING_INSET_DP, .0001f);
        assertEquals(5f, ChipWatermarkGeometry.TITLE_NUDGE_DP, .0001f);
        // Taller than the 20dp chip: the rounded outline is what crops it.
        assertTrue(ChipWatermarkGeometry.GLYPH_SIZE_DP > 20f);

        // Reading left to right, the box starts 2px in and the glyph is centred in it.
        assertEquals(12.5f,
            ChipWatermarkGeometry.glyphCentreOnAxis(0f, 60f, 2f, 21f), .0001f);
        // Reading right to left, the same inset from the other edge.
        assertEquals(47.5f,
            ChipWatermarkGeometry.glyphCentreOnAxis(60f, 0f, 2f, 21f), .0001f);
    }

    /** The halo is the chip's own fill with an alpha that can actually hide the glyph. */
    @Test
    public void theHaloKeepsTheFillsHueAndNothingOfItsAlpha() {
        assertEquals(1.5f, ChipWatermarkGeometry.TITLE_HALO_DP, .0001f);
        assertEquals(200, ChipWatermarkGeometry.TITLE_HALO_ALPHA);
        // A fill at alpha 16 becomes the same colour at 200.
        assertEquals(0xC8336699, ChipWatermarkGeometry.haloColor(0x10336699));
        assertEquals(0xC8336699, ChipWatermarkGeometry.haloColor(0xFF336699));
        assertEquals(0xC8000000, ChipWatermarkGeometry.haloColor(0x00000000));
    }

    /**
     * A rounded chip has room in its corners, and that is where the dot goes; a square one has
     * none, so the dot is pulled back inside the edge rather than clipped off it.
     */
    @Test
    public void aCornerDotLeavesTheOutlineAlongTheDiagonalButNeverTheChip() {
        float dotRadius = 2.5f;
        float gap = 2f;
        float halo = 1f;

        // Top-trailing on a 20-tall capsule chip: hard against the corner, halo still inside.
        float cy = ChipWatermarkGeometry.dotCentreOnAxis(0f, 20f, 10f, gap, dotRadius, halo);
        assertEquals(3.5f, cy, .001f);
        float cx = ChipWatermarkGeometry.dotCentreOnAxis(60f, 0f, 10f, gap, dotRadius, halo);
        assertEquals(56.5f, cx, .001f);

        // Bottom-leading on the same chip: the other two edges, the same inset.
        assertEquals(16.5f,
            ChipWatermarkGeometry.dotCentreOnAxis(20f, 0f, 10f, gap, dotRadius, halo), .001f);
        assertEquals(3.5f,
            ChipWatermarkGeometry.dotCentreOnAxis(0f, 60f, 10f, gap, dotRadius, halo), .001f);

        // A square chip: the diagonal buys nothing, and the clamp is what keeps the dot drawable.
        assertEquals(3.5f,
            ChipWatermarkGeometry.dotCentreOnAxis(0f, 20f, 0f, gap, dotRadius, halo), .001f);

        // A radius larger than the chip is the capsule case, not a dot outside it.
        float shallow = ChipWatermarkGeometry.dotCentreOnAxis(0f, 12f, 40f, gap, dotRadius, halo);
        assertTrue(shallow >= dotRadius + halo);
        assertTrue(shallow <= 12f - dotRadius - halo);
    }

    @Test
    public void theArcCoversThreeQuartersOfTheOutlineAndStepsInLazyMode() {
        assertEquals(.75f, ChipWatermarkGeometry.RING_SWEEP_FRACTION, .0001f);
        assertEquals(.25f, ChipWatermarkGeometry.ringStartFraction(.25f, false), .0001f);
        // Eight stops a turn, as the label's ring had: .3 of a turn is the second stop.
        assertEquals(.25f, ChipWatermarkGeometry.ringStartFraction(.3f, true), .0001f);
        assertEquals(8, WindowActivityRing.LAZY_STEPS);
        assertEquals(160L, WindowActivityRing.LAZY_TICK_MS);
        assertEquals(1280L, WindowActivityRing.SPIN_MS);
        // A phase that has run past the turn starts again rather than running off the path.
        assertEquals(.5f, ChipWatermarkGeometry.ringStartFraction(3.5f, false), .0001f);
    }

    @Test
    public void aReportedPercentageFillsItsShareOfTheOutline() {
        assertEquals(0f, ChipWatermarkGeometry.determinateFraction(0), .0001f);
        assertEquals(.42f, ChipWatermarkGeometry.determinateFraction(42), .0001f);
        assertEquals(1f, ChipWatermarkGeometry.determinateFraction(100), .0001f);
        // A shell reporting 140% has a bug; the ring must not wrap and look nearly empty.
        assertEquals(1f, ChipWatermarkGeometry.determinateFraction(140), .0001f);
        assertEquals(0f, ChipWatermarkGeometry.determinateFraction(-8), .0001f);
    }

    @Test
    public void aTravellingSegmentWrapsAtTheSeamRatherThanStoppingThere() {
        float length = 200f;
        float start = ChipWatermarkGeometry.segmentStartPx(length, .5f);
        float sweep = ChipWatermarkGeometry.segmentSweepPx(length, .75f);
        assertEquals(100f, start, .0001f);
        assertEquals(150f, sweep, .0001f);
        // Half way round plus three quarters runs 50px past the end and continues from the start.
        assertEquals(50f, ChipWatermarkGeometry.segmentTailPx(length, start, sweep), .0001f);
        // One that fits has no tail at all.
        assertEquals(0f, ChipWatermarkGeometry.segmentTailPx(length, 0f, sweep), .0001f);
        // A full turn never draws twice over itself.
        assertEquals(length,
            ChipWatermarkGeometry.segmentSweepPx(length, 2f), .0001f);
        assertEquals(0f, ChipWatermarkGeometry.segmentStartPx(0f, .5f), .0001f);
    }

    @Test
    public void aHollowMarkStaysTheSizeOfAFilledOne() {
        // Failed and attention share the corner and the colour; only the stroke tells them apart,
        // so the ring must not be drawn at some other size and read as a different mark.
        assertTrue(ChipWatermarkGeometry.DOT_RING_WIDTH_DP > 0f);
        assertTrue("the ring has to fit inside the dot it replaces",
            ChipWatermarkGeometry.DOT_RING_WIDTH_DP < ChipWatermarkGeometry.DOT_DIAMETER_DP / 2f);
    }

    @Test
    public void theCloseSegmentOpensToTwentyFourDpAndNoFurther() {
        assertEquals(24f, ChipWatermarkGeometry.CLOSE_SEGMENT_DP, .0001f);
        assertEquals(180L, ChipWatermarkGeometry.CLOSE_REVEAL_MS);
        assertEquals(0, ChipWatermarkGeometry.closeSegmentWidthPx(0f, 48f));
        assertEquals(24, ChipWatermarkGeometry.closeSegmentWidthPx(.5f, 48f));
        assertEquals(48, ChipWatermarkGeometry.closeSegmentWidthPx(1f, 48f));
        assertEquals(48, ChipWatermarkGeometry.closeSegmentWidthPx(2f, 48f));
        assertEquals(0, ChipWatermarkGeometry.closeSegmentWidthPx(-1f, 48f));
    }
}
