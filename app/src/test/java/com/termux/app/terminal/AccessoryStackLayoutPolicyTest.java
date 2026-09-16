package com.termux.app.terminal;

import com.termux.app.place.Element;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccessoryStackLayoutPolicyTest {

    @Test
    public void combinedHeight_sumsAllVisibleSegments() {
        int combined = AccessoryStackLayoutPolicy.computeCombinedHeight(120, 42, 20, 6);
        assertEquals(188, combined);
    }

    @Test
    public void combinedHeight_clampsNegativeValues() {
        int combined = AccessoryStackLayoutPolicy.computeCombinedHeight(120, -5, 20, -2);
        assertEquals(140, combined);
    }

    @Test
    public void combinedHeight_coversAllIndependentRowCombinations() {
        int[] expected = {
            0,       // none
            60,      // apps
            20,      // A-Z
            86,      // apps + gap + A-Z
            40,      // extra keys
            100,     // apps + extra keys
            60,      // A-Z + extra keys
            126      // all three
        };
        for (int mask = 0; mask < 8; mask++) {
            assertEquals("mask=" + mask, expected[mask],
                AccessoryStackLayoutPolicy.computeCombinedHeight(
                    (mask & 1) != 0,
                    (mask & 2) != 0,
                    (mask & 4) != 0,
                    60, 20, 40, 6));
        }
    }

    @Test
    public void appsBarInterRowGap_isZeroWhenAzDisabled() {
        int gap = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(false, 3f, 1.5f);
        assertEquals(0, gap);
    }

    @Test
    public void appsBarInterRowGap_scalesWithIconScaleWhenAzEnabled() {
        int gapDefaultScale = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(true, 3f, 1f);
        int gapLargeScale = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(true, 3f, 1.5f);
        assertEquals(9, gapDefaultScale);
        assertEquals(12, gapLargeScale);
    }

    @Test
    public void dockIconFillRatio_clampsAtEstablishedBounds() {
        assertEquals(0.68f, AccessoryStackLayoutPolicy.computeDockIconFillRatio(1f), 0f);
        assertEquals(0.76f, AccessoryStackLayoutPolicy.computeDockIconFillRatio(1.4f), 0.0001f);
        assertEquals(0.84f, AccessoryStackLayoutPolicy.computeDockIconFillRatio(2f), 0.0001f);
    }

    @Test
    public void presetCurve_hitsEveryControlPoint() {
        float[] progress = {0.27f, 0.50f, 0.73f, 1f};
        float[] values = {1.72f, 1.96f, 2.21f, 2.51f};
        for (int i = 0; i < progress.length; i++) {
            assertEquals(values[i], AccessoryStackLayoutPolicy.interpolatePresetCurve(
                progress[i], progress, values), 0.0001f);
        }
    }

    @Test
    public void pageIndicatorBandHeight_usesFixedStrip() {
        assertEquals(9, AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(true, 3f));
        assertEquals(0, AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(false, 3f));
    }

    @Test
    public void azRowHeight_usesFixedHeight() {
        assertEquals(57, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, true, true, 3f));
        assertEquals(0, AccessoryStackLayoutPolicy.computeAzRowHeightPx(false, true, true, 3f));
    }

    @Test
    public void azRowCarriesAChinOnlyWhenItIsTheDocksBottomRow() {
        // Extra keys hidden: the 19dp letter band plus a 10dp chin, all of it touchable.
        assertEquals(87, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, true, false, 3f));
        assertEquals(30, AccessoryStackLayoutPolicy.computeAzRowChinPaddingPx(true, false, 3f));
        // Extra keys shown: that row is the one on the rim, so the A-Z row is the band alone.
        assertEquals(0, AccessoryStackLayoutPolicy.computeAzRowChinPaddingPx(true, true, 3f));
        // A row that is off has neither.
        assertEquals(0, AccessoryStackLayoutPolicy.computeAzRowChinPaddingPx(false, false, 3f));
    }

    @Test
    public void azRowCarriesACrownOnlyWhenItIsTheDocksTopRow() {
        // Apps row hidden, extra keys shown: the 19dp band plus 6dp of air over the letters.
        assertEquals(75, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, false, true, 3f));
        assertEquals(18, AccessoryStackLayoutPolicy.computeAzRowCrownPaddingPx(true, false, 3f));
        // Apps row shown: the indicator band keeps the letters off the rim, no crown.
        assertEquals(0, AccessoryStackLayoutPolicy.computeAzRowCrownPaddingPx(true, true, 3f));
        assertEquals(0, AccessoryStackLayoutPolicy.computeAzRowCrownPaddingPx(false, false, 3f));
        // Letters alone on the dock: crown over, chin under.
        assertEquals(105, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, false, false, 3f));
    }

    /**
     * The Alphabets bar standing alone: no apps row above it, no extra keys below it. The stack is
     * the letter band with its crown and chin and nothing else — no inter-row gap is paid for a row that is
     * not there — so the glass drawn over that height ends flush on the chin the letters sit in.
     */
    @Test
    public void theLettersAloneAreTheWholeDockAndTheGlassIsExactlyTheirHeight() {
        int alone = AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, false, false, 3f);
        assertEquals(105, alone);
        assertEquals(alone, AccessoryStackLayoutPolicy.computeCombinedHeight(
            false, true, false, 300, alone, 112, 9));
        // With the extra keys back the letters drop the chin and the two rows are the whole stack.
        int banded = AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, false, true, 3f);
        assertEquals(banded + 112, AccessoryStackLayoutPolicy.computeCombinedHeight(
            false, true, true, 300, banded, 112, 9));
    }

    // ---------------------------------------------------------------- crown and chin, in order

    /** A bottom stack, outermost (on the dock's rim) first, the way EdgeStackPolicy gives them. */
    private static List<Element> stack(Element... outermostFirst) {
        return Arrays.asList(outermostFirst);
    }

    @Test
    public void theDocksOwnRowsAreTheStackWithoutTheStatusBar() {
        assertEquals(stack(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            AccessoryStackLayoutPolicy.dockRows(
                stack(Element.EXTRA_KEYS, Element.AZ, Element.APPS, Element.STATUS)));
    }

    @Test
    public void theShippedOrderPutsARowOverAndUnderTheLetters() {
        List<Element> shipped = stack(Element.EXTRA_KEYS, Element.AZ, Element.APPS, Element.STATUS);
        assertTrue(AccessoryStackLayoutPolicy.rowOverAz(shipped));
        assertTrue(AccessoryStackLayoutPolicy.rowUnderAz(shipped));
        // Which is the shipped A-Z row: the 19dp band, no crown and no chin.
        assertEquals(57, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true,
            AccessoryStackLayoutPolicy.rowOverAz(shipped),
            AccessoryStackLayoutPolicy.rowUnderAz(shipped), 3f));
    }

    @Test
    public void aReorderedStackMovesTheCrownAndTheChinWithTheLetters() {
        // The letters innermost, with the apps row and the keys under them: a crown, no chin.
        List<Element> onTop = stack(Element.APPS, Element.EXTRA_KEYS, Element.AZ);
        assertFalse(AccessoryStackLayoutPolicy.rowOverAz(onTop));
        assertTrue(AccessoryStackLayoutPolicy.rowUnderAz(onTop));

        // The letters on the rim, under both: a chin, no crown. The status bar never counts —
        // it stands above the whole dock rather than on its glass.
        List<Element> onTheRim = stack(Element.AZ, Element.EXTRA_KEYS, Element.APPS,
            Element.STATUS);
        assertTrue(AccessoryStackLayoutPolicy.rowOverAz(onTheRim));
        assertFalse(AccessoryStackLayoutPolicy.rowUnderAz(onTheRim));

        // Alone on the dock: both.
        List<Element> alone = Collections.singletonList(Element.AZ);
        assertFalse(AccessoryStackLayoutPolicy.rowOverAz(alone));
        assertFalse(AccessoryStackLayoutPolicy.rowUnderAz(alone));
        assertEquals(105, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, false, false, 3f));
    }

    @Test
    public void lettersOffTheBottomEdgeAskForNeither() {
        List<Element> noLetters = stack(Element.EXTRA_KEYS, Element.APPS);
        assertFalse(AccessoryStackLayoutPolicy.rowOverAz(noLetters));
        assertFalse(AccessoryStackLayoutPolicy.rowUnderAz(noLetters));
    }

    @Test
    public void terminalToolbarHeight_scalesWithRowsAndScale() {
        assertEquals(228, AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(38, 2, 3f));
    }
}
