package com.termux.app.editorshell;

import com.termux.app.editorshell.EditorShellMetrics.BodyCap;
import com.termux.app.editorshell.EditorShellMetrics.PaneSplit;
import com.termux.app.editorshell.EditorShellMetrics.RowMetrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The shell's sizes at the two devices the redesign was measured on, plus the portrait phone that
 * has to fall back to one column.
 *
 * <p><b>Wide</b> is 1300x600 px at density 1.125 — 1155 x 533 dp, font 1.0x. <b>Short</b> is the
 * same panel at density 1.625 with font 1.3x — 800 x 369 dp. Every case below is stated at both,
 * because the whole point of the shell is that one rule holds at both.
 */
public class EditorShellMetricsTest {

    private static final float WIDE_DENSITY = 1.125f;
    private static final int WIDE_WIDTH_PX = 1300;
    private static final int WIDE_HEIGHT_PX = 600;

    private static final float SHORT_DENSITY = 1.625f;
    private static final int SHORT_WIDTH_PX = 1300;

    private static final float PHONE_DENSITY = 3f;
    private static final int PHONE_WIDTH_PX = 1080;

    private static float dp(int px, float density) {
        return px / density;
    }

    // ---------------------------------------------------------------------------- the card's width

    @Test
    public void wideDeviceGetsTwoPanesAtTheMaximumRowWidthAndLeavesAirAroundTheCard() {
        int content = EditorShellMetrics.contentWidthPx(WIDE_WIDTH_PX, WIDE_DENSITY);
        assertEquals(1103f, dp(content, WIDE_DENSITY), 1f);

        PaneSplit split = EditorShellMetrics.paneSplit(content, 0, WIDE_DENSITY);
        assertEquals(2, split.paneCount);
        assertEquals(488f, dp(split.leadingWidthPx, WIDE_DENSITY), 1f);
        assertEquals(split.leadingWidthPx, split.trailingWidthPx);
        assertEquals(24f, dp(split.gutterPx, WIDE_DENSITY), 1f);

        int card = EditorShellMetrics.cardWidthPx(WIDE_WIDTH_PX, split, WIDE_DENSITY);
        assertEquals(1032f, dp(card, WIDE_DENSITY), 1f);
        // The leftover is air with the live place showing through it, not stretched controls.
        assertEquals(61f, dp((WIDE_WIDTH_PX - card) / 2, WIDE_DENSITY), 1f);
    }

    @Test
    public void shortDeviceGetsTwoNarrowerPanesAndAlmostNoAir() {
        int content = EditorShellMetrics.contentWidthPx(SHORT_WIDTH_PX, SHORT_DENSITY);
        assertEquals(748f, dp(content, SHORT_DENSITY), 1f);

        PaneSplit split = EditorShellMetrics.paneSplit(content, 0, SHORT_DENSITY);
        assertEquals(2, split.paneCount);
        assertEquals(362f, dp(split.leadingWidthPx, SHORT_DENSITY), 1f);

        int card = EditorShellMetrics.cardWidthPx(SHORT_WIDTH_PX, split, SHORT_DENSITY);
        assertEquals(780f, dp(card, SHORT_DENSITY), 1f);
        assertEquals(10f, dp((SHORT_WIDTH_PX - card) / 2, SHORT_DENSITY), 1f);
    }

    @Test
    public void portraitPhoneFallsBackToOneColumn() {
        int content = EditorShellMetrics.contentWidthPx(PHONE_WIDTH_PX, PHONE_DENSITY);
        assertEquals(308f, dp(content, PHONE_DENSITY), 1f);

        PaneSplit split = EditorShellMetrics.paneSplit(content, 0, PHONE_DENSITY);
        assertEquals(1, split.paneCount);
        assertEquals(308f, dp(split.leadingWidthPx, PHONE_DENSITY), 1f);
        assertEquals(0, split.trailingWidthPx);

        int card = EditorShellMetrics.cardWidthPx(PHONE_WIDTH_PX, split, PHONE_DENSITY);
        assertEquals(340f, dp(card, PHONE_DENSITY), 1f);
    }

    @Test
    public void aPaneNeverGrowsPastTheWidestRowWorthHaving() {
        // A 2000 dp tablet: the panes stop at 488 dp and everything else becomes air.
        float density = 2f;
        int content = EditorShellMetrics.contentWidthPx(4000, density);
        PaneSplit split = EditorShellMetrics.paneSplit(content, 0, density);
        assertEquals(2, split.paneCount);
        assertEquals(EditorShellMetrics.ROW_MAX_INNER_DP, dp(split.leadingWidthPx, density), 1f);
        int card = EditorShellMetrics.cardWidthPx(4000, split, density);
        assertTrue("the card must leave real air on a tablet",
            (4000 - card) / 2 > EditorShellMetrics.px(400, density));
    }

    @Test
    public void thereAreNeverThreePanes() {
        for (int screenPx = 400; screenPx <= 6000; screenPx += 37) {
            PaneSplit split = EditorShellMetrics.paneSplit(
                EditorShellMetrics.contentWidthPx(screenPx, 2f), 0, 2f);
            assertTrue(screenPx + "px gave " + split.paneCount + " panes",
                split.paneCount == 1 || split.paneCount == 2);
        }
    }

    @Test
    public void theTwoPaneThresholdIsTwoWholeRowsAndAGutter() {
        float density = 2f;
        int justUnder = EditorShellMetrics.px(EditorShellMetrics.TWO_PANE_MIN_CONTENT_DP - 1,
            density);
        int justOver = EditorShellMetrics.px(EditorShellMetrics.TWO_PANE_MIN_CONTENT_DP, density);
        assertEquals(1, EditorShellMetrics.paneSplit(justUnder, 0, density).paneCount);
        assertEquals(2, EditorShellMetrics.paneSplit(justOver, 0, density).paneCount);
    }

    @Test
    public void aLeadingPaneSizedFromItsOwnContentStaysBetweenAQuarterAndAHalfOfTheBody() {
        int content = EditorShellMetrics.contentWidthPx(WIDE_WIDTH_PX, WIDE_DENSITY);
        // A portrait miniature is a sliver; it still gets a quarter of the body, not less.
        PaneSplit sliver = EditorShellMetrics.paneSplit(content, 20, WIDE_DENSITY);
        assertEquals(2, sliver.paneCount);
        assertEquals(0.25f * content, sliver.leadingWidthPx, 2f);
        // A landscape miniature is wide; it is held to half, and never takes the rows' pane.
        PaneSplit wide = EditorShellMetrics.paneSplit(content, content, WIDE_DENSITY);
        assertEquals(0.5f * content, wide.leadingWidthPx, 2f);
        assertTrue(wide.trailingWidthPx > 0);
    }

    // --------------------------------------------------------------------------------- the row

    @Test
    public void everyRowClearsThePlatformTouchFloorAtBothDensities() {
        assertEquals(54, EditorShellMetrics.rowMetrics(WIDE_DENSITY).minHeightPx);
        assertEquals(78, EditorShellMetrics.rowMetrics(SHORT_DENSITY).minHeightPx);
        assertEquals(48f, dp(EditorShellMetrics.rowMetrics(WIDE_DENSITY).minHeightPx,
            WIDE_DENSITY), 0.5f);
        assertEquals(48f, dp(EditorShellMetrics.rowMetrics(SHORT_DENSITY).minHeightPx,
            SHORT_DENSITY), 0.5f);
    }

    @Test
    public void theTrackIsNeverASmudgeAndNeverASweep() {
        for (float density : new float[] {WIDE_DENSITY, SHORT_DENSITY, PHONE_DENSITY}) {
            RowMetrics row = EditorShellMetrics.rowMetrics(density);
            for (int inner = 100; inner <= 4000; inner += 13) {
                int track = EditorShellMetrics.trackWidthPx(row.controlWidthPx(inner), density);
                assertTrue(dp(track, density) >= EditorShellMetrics.TRACK_MIN_DP - 1);
                assertTrue(dp(track, density) <= EditorShellMetrics.TRACK_MAX_DP + 1);
            }
        }
    }

    @Test
    public void theTrackAtEachReferenceDevice() {
        // Wide: the full 280 dp, so the 240-step key radius addresses every tenth.
        RowMetrics wide = EditorShellMetrics.rowMetrics(WIDE_DENSITY);
        PaneSplit wideSplit = EditorShellMetrics.paneSplit(
            EditorShellMetrics.contentWidthPx(WIDE_WIDTH_PX, WIDE_DENSITY), 0, WIDE_DENSITY);
        assertEquals(280f, dp(EditorShellMetrics.trackWidthPx(
            wide.controlWidthPx(wideSplit.leadingWidthPx), WIDE_DENSITY), WIDE_DENSITY), 1f);

        RowMetrics small = EditorShellMetrics.rowMetrics(SHORT_DENSITY);
        PaneSplit shortSplit = EditorShellMetrics.paneSplit(
            EditorShellMetrics.contentWidthPx(SHORT_WIDTH_PX, SHORT_DENSITY), 0, SHORT_DENSITY);
        assertEquals(154f, dp(EditorShellMetrics.trackWidthPx(
            small.controlWidthPx(shortSplit.leadingWidthPx), SHORT_DENSITY), SHORT_DENSITY), 1f);

        RowMetrics phone = EditorShellMetrics.rowMetrics(PHONE_DENSITY);
        PaneSplit phoneSplit = EditorShellMetrics.paneSplit(
            EditorShellMetrics.contentWidthPx(PHONE_WIDTH_PX, PHONE_DENSITY), 0, PHONE_DENSITY);
        assertEquals(100f, dp(EditorShellMetrics.trackWidthPx(
            phone.controlWidthPx(phoneSplit.leadingWidthPx), PHONE_DENSITY), PHONE_DENSITY), 1f);
    }

    @Test
    public void aWiderCardDoesNotBuyAWiderTrack() {
        RowMetrics row = EditorShellMetrics.rowMetrics(WIDE_DENSITY);
        int atMaxPane = EditorShellMetrics.trackWidthPx(
            row.controlWidthPx(EditorShellMetrics.px(EditorShellMetrics.ROW_MAX_INNER_DP,
                WIDE_DENSITY)), WIDE_DENSITY);
        int atTenTimes = EditorShellMetrics.trackWidthPx(
            row.controlWidthPx(EditorShellMetrics.px(10 * EditorShellMetrics.ROW_MAX_INNER_DP,
                WIDE_DENSITY)), WIDE_DENSITY);
        assertEquals(atMaxPane, atTenTimes);
    }

    // ---------------------------------------------------------------------------- the segments

    @Test
    public void everySegmentOnALineIsOneWidth() {
        // Two questions, two lines, and inside a line no segment is wider than its neighbour: the
        // shipped 238 dp Docked beside a 192 dp Solid is what read as one ragged five-way choice.
        for (float density : new float[] {WIDE_DENSITY, SHORT_DENSITY, PHONE_DENSITY}) {
            int inner = EditorShellMetrics.px(EditorShellMetrics.ROW_MAX_INNER_DP, density);
            int control = EditorShellMetrics.segmentControlWidthPx(inner, density);
            int two = EditorShellMetrics.segmentWidthPx(control, 2, density);
            int three = EditorShellMetrics.segmentWidthPx(control, 3, density);
            assertTrue(2 * two <= control);
            assertTrue(3 * three <= control);
        }
    }

    @Test
    public void aGroupNeverRunsPastItsColumn() {
        for (float density : new float[] {WIDE_DENSITY, SHORT_DENSITY, PHONE_DENSITY}) {
            for (int inner = 200; inner <= 3000; inner += 11) {
                int control = EditorShellMetrics.segmentControlWidthPx(inner, density);
                for (int count = 1; count <= 4; count++) {
                    int width = EditorShellMetrics.segmentWidthPx(control, count, density);
                    assertTrue("count " + count + " overflowed " + control,
                        width * count <= control);
                }
            }
        }
    }

    @Test
    public void theEightyEightDpFloorHoldsWhereverTheColumnCanAffordIt() {
        // Wide: both groups clear the floor comfortably.
        PaneSplit wideSplit = EditorShellMetrics.paneSplit(
            EditorShellMetrics.contentWidthPx(WIDE_WIDTH_PX, WIDE_DENSITY), 0, WIDE_DENSITY);
        int wideControl = EditorShellMetrics.segmentControlWidthPx(
            wideSplit.leadingWidthPx, WIDE_DENSITY);
        assertTrue(dp(EditorShellMetrics.segmentWidthPx(wideControl, 3, WIDE_DENSITY),
            WIDE_DENSITY) >= EditorShellMetrics.SEGMENT_MIN_DP);
        assertEquals(EditorShellMetrics.SEGMENT_MAX_DP,
            dp(EditorShellMetrics.segmentWidthPx(wideControl, 2, WIDE_DENSITY), WIDE_DENSITY), 1f);

        // Short: Docked / Floating clears it; Solid / Glass / Frost cannot, and takes an even
        // share of the column it has rather than running off the side of the card.
        PaneSplit shortSplit = EditorShellMetrics.paneSplit(
            EditorShellMetrics.contentWidthPx(SHORT_WIDTH_PX, SHORT_DENSITY), 0, SHORT_DENSITY);
        int shortControl = EditorShellMetrics.segmentControlWidthPx(
            shortSplit.leadingWidthPx, SHORT_DENSITY);
        assertTrue(dp(EditorShellMetrics.segmentWidthPx(shortControl, 2, SHORT_DENSITY),
            SHORT_DENSITY) >= EditorShellMetrics.SEGMENT_MIN_DP);
        assertEquals(84f, dp(EditorShellMetrics.segmentWidthPx(shortControl, 3, SHORT_DENSITY),
            SHORT_DENSITY), 1.5f);
    }

    // ------------------------------------------------------------------------------- the header

    @Test
    public void theHeaderGoesCompactOnlyWhereTheCardIsShort() {
        assertEquals(56f, dp(EditorShellMetrics.headerHeightPx(WIDE_HEIGHT_PX, WIDE_DENSITY),
            WIDE_DENSITY), 1f);
        // The short device's Appearance card has about 264 dp between the status bar and the dock.
        int shortCardPx = EditorShellMetrics.px(264, SHORT_DENSITY);
        assertEquals(44f, dp(EditorShellMetrics.headerHeightPx(shortCardPx, SHORT_DENSITY),
            SHORT_DENSITY), 1f);
        assertEquals(56f, dp(EditorShellMetrics.headerHeightPx(
            EditorShellMetrics.px(280, SHORT_DENSITY), SHORT_DENSITY), SHORT_DENSITY), 1f);
    }

    @Test
    public void theChooserUnpinsOnlyWhereItsChromeWouldCostRows() {
        assertTrue(EditorShellMetrics.chooserPinned(
            EditorShellMetrics.px(200, WIDE_DENSITY), WIDE_DENSITY));
        assertFalse(EditorShellMetrics.chooserPinned(
            EditorShellMetrics.px(199, SHORT_DENSITY), SHORT_DENSITY));
    }

    // ----------------------------------------------------------------------------- the overflow

    @Test
    public void theCutLandsAPeekIntoARowAndNeverThroughItsGlyphs() {
        int pitch = EditorShellMetrics.px(EditorShellMetrics.ROW_MIN_HEIGHT_DP, WIDE_DENSITY);
        int peek = EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, WIDE_DENSITY);
        for (int available = pitch; available < 40 * pitch; available++) {
            BodyCap cap = EditorShellMetrics.bodyCap(available, 100 * pitch, pitch, peek);
            assertTrue(cap.overflows);
            assertTrue(cap.capPx <= available);
            if (cap.capPx == available)
                continue;
            assertEquals("the cut must land a peek into a whole number of rows",
                cap.wholeRows * pitch + peek, cap.capPx);
        }
    }

    @Test
    public void aBodyThatFitsIsNotQuantisedAndDoesNotClaimMoreBelow() {
        int pitch = EditorShellMetrics.px(48, WIDE_DENSITY);
        int peek = EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, WIDE_DENSITY);
        BodyCap cap = EditorShellMetrics.bodyCap(10 * pitch, 3 * pitch, pitch, peek);
        assertFalse(cap.overflows);
        assertEquals(3, cap.wholeRows);
    }

    @Test
    public void theShortDeviceReportsWholeRowsAndSaysThereIsMoreBelow() {
        // 200 dp of body, a row measured at 48 dp, a 16 dp peek: three whole rows and a sliver of
        // the fourth. DESIGN.md's two worked examples (2 rows in section 5, 4 in section 6.4) are
        // both wrong; this is the arithmetic they describe.
        int available = EditorShellMetrics.px(200, SHORT_DENSITY);
        int pitch = EditorShellMetrics.px(48, SHORT_DENSITY);
        int peek = EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, SHORT_DENSITY);
        BodyCap cap = EditorShellMetrics.bodyCap(available, EditorShellMetrics.px(456,
            SHORT_DENSITY), pitch, peek);
        assertEquals(3, cap.wholeRows);
        assertTrue(cap.overflows);
        assertEquals(3 * pitch + peek, cap.capPx);
    }

    @Test
    public void aLargerFontScaleMovesTheCutByTheMeasuredPitchRatherThanTheNominalOne() {
        int available = EditorShellMetrics.px(200, SHORT_DENSITY);
        int peek = EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, SHORT_DENSITY);
        int nominal = EditorShellMetrics.px(48, SHORT_DENSITY);
        // A 1.3x scale on a tall locale grows the measured row; the cap follows the measurement.
        int measured = EditorShellMetrics.px(62, SHORT_DENSITY);
        assertEquals(3, EditorShellMetrics.bodyCap(available, 10 * nominal, nominal, peek)
            .wholeRows);
        assertEquals(2, EditorShellMetrics.bodyCap(available, 10 * measured, measured, peek)
            .wholeRows);
    }

    // ------------------------------------------------ the cut, taken from the rows themselves

    /** pong: 1080x2412 at 420dpi. */
    private static final float PONG_DENSITY = 2.625f;
    private static final int PONG_HEIGHT_PX = 2412;

    /**
     * The room the Layout editor's rows have on pong, in portrait, on the Terminal place, worked
     * out the way {@code LayoutEditorController.applyCanvasHeight} works it out.
     */
    private static int pongRowsRoomPx() {
        int header = EditorShellMetrics.headerHeightPx(PONG_HEIGHT_PX, PONG_DENSITY);
        int chooser = EditorShellMetrics.px(EditorShellMetrics.CHOOSER_DP, PONG_DENSITY);
        // The card's own bottom padding, the sheet's handle, and the gaps around the canvas. No
        // notice lines: the Terminal place in portrait has nothing to say about its arrangement.
        int padding = EditorShellMetrics.px(10, PONG_DENSITY);
        int handleAndGaps = EditorShellMetrics.px(18 + 10, PONG_DENSITY);
        int chrome = header + chooser + padding + handleAndGaps;
        // The portrait frame at 42% of the screen, plus the hide tray's 48dp under it.
        int miniature = Math.round(0.42f * PONG_HEIGHT_PX)
            + EditorShellMetrics.px(48, PONG_DENSITY);
        int floor = EditorShellMetrics.px(96, PONG_DENSITY);
        // The sheet stands in four fifths of the screen; the fifth above it is the live place.
        int budget = Math.round(0.80f * PONG_HEIGHT_PX);
        return Math.max(floor, budget - miniature - chrome);
    }

    /**
     * The rows standing under the miniature on pong: the "Keyboard" heading, a pick row — which is
     * the segment's own 48dp slot and no more — and two number rows, each the 48dp floor inside the
     * row's 6dp of air. Three shapes, which is the whole reason one pitch cannot describe them.
     */
    private static int[] pongRowsPx() {
        int heading = EditorShellMetrics.px(EditorShellMetrics.SECTION_MIN_HEIGHT_DP, PONG_DENSITY)
            + EditorShellMetrics.px(EditorShellMetrics.SECTION_BOTTOM_MARGIN_DP, PONG_DENSITY);
        int pick = EditorShellMetrics.px(EditorShellMetrics.SEGMENT_HEIGHT_DP, PONG_DENSITY);
        int number = EditorShellMetrics.px(EditorShellMetrics.ROW_MIN_HEIGHT_DP, PONG_DENSITY)
            + (2 * EditorShellMetrics.px(EditorShellMetrics.ROW_VERTICAL_PADDING_DP, PONG_DENSITY));
        return new int[] {heading, pick, number, number};
    }

    @Test
    public void theRoomPongLeavesTheRowsCutsThroughARow() {
        // The reproduction: the room the card has is not a whole number of anything the body is
        // made of, so a body simply given that room ends inside a row's glyphs.
        int room = pongRowsRoomPx();
        int[] rows = pongRowsPx();
        int bottom = 0;
        boolean landsOnARow = false;
        for (int height : rows) {
            int top = bottom;
            bottom += height;
            if (room > top && room < bottom)
                landsOnARow = true;
        }
        assertTrue("pong's room should fall inside a row, or this is not the bug", landsOnARow);
    }

    @Test
    public void theCutTakenFromTheRowsThemselvesLandsAPeekPastAWholeRow() {
        int room = pongRowsRoomPx();
        int[] rows = pongRowsPx();
        int peek = EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, PONG_DENSITY);
        BodyCap cap = EditorShellMetrics.bodyCap(room, rows, peek);
        assertTrue(cap.overflows);
        assertTrue("the cut never takes more than the room there is", cap.capPx <= room);
        int bottom = 0;
        for (int index = 0; index < cap.wholeRows; index++)
            bottom += rows[index];
        assertEquals("the cut lands a peek past the last whole row", bottom + peek, cap.capPx);
        assertEquals("the heading and two rows, with a sliver of the last", 3, cap.wholeRows);
    }

    @Test
    public void everyRoomCutsAtARowBoundaryWhateverTheRowsAreMadeOf() {
        // Mixed heights are the point: a heading is half a row, a row with a note under it is
        // taller than one without, and one pitch cannot describe any of that.
        int[] rows = {74, 158, 126, 126, 190, 126};
        int peek = 42;
        for (int room = 1; room <= 1200; room++) {
            BodyCap cap = EditorShellMetrics.bodyCap(room, rows, peek);
            assertTrue(cap.capPx <= room);
            if (!cap.overflows || cap.wholeRows == 0)
                continue;
            int bottom = 0;
            for (int index = 0; index < cap.wholeRows; index++)
                bottom += rows[index];
            assertEquals("room " + room, bottom + peek, cap.capPx);
        }
    }

    @Test
    public void aBodyThatFitsIsNotCutAtAll() {
        int[] rows = {74, 158, 126};
        BodyCap cap = EditorShellMetrics.bodyCap(1000, rows, 42);
        assertFalse(cap.overflows);
        assertEquals(1000, cap.capPx);
        assertEquals(3, cap.wholeRows);
    }

    @Test
    public void aRoomTooShortForTheFirstRowStillReportsTheRoomItHas() {
        int[] rows = {158, 158};
        BodyCap cap = EditorShellMetrics.bodyCap(100, rows, 42);
        assertTrue(cap.overflows);
        assertEquals(100, cap.capPx);
        assertEquals(0, cap.wholeRows);
    }

    // ------------------------------------------------------------------------- the segment slot

    @Test
    public void aSegmentIsPaintedShortOfTheSlotItIsTappedIn() {
        assertEquals("the slot is the platform's touch floor",
            EditorShellMetrics.ROW_MIN_HEIGHT_DP, EditorShellMetrics.SEGMENT_HEIGHT_DP);
        assertEquals(36, EditorShellMetrics.SEGMENT_VISUAL_HEIGHT_DP);
        assertEquals("the air above and below is what keeps the target at the floor", 6,
            EditorShellMetrics.SEGMENT_INSET_DP);
        assertEquals(EditorShellMetrics.SEGMENT_HEIGHT_DP,
            EditorShellMetrics.SEGMENT_VISUAL_HEIGHT_DP + (2 * EditorShellMetrics.SEGMENT_INSET_DP));
    }

    @Test
    public void aRoomTooShortForOneRowStillReportsTheRoomItHas() {
        int pitch = EditorShellMetrics.px(48, WIDE_DENSITY);
        int peek = EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, WIDE_DENSITY);
        BodyCap cap = EditorShellMetrics.bodyCap(pitch / 2, 10 * pitch, pitch, peek);
        assertEquals(pitch / 2, cap.capPx);
        assertTrue(cap.overflows);
        assertEquals(1, cap.wholeRows);
    }

    // ------------------------------------------------------------------- filling the two panes

    @Test
    public void theSharedLayerPutsMaterialInOnePaneAndShapeAndWallpaperInTheOther() {
        // Material is a heading, the Solid / Glass / Frost row and three sliders; Shape is a
        // heading, Docked / Floating and two sliders; Wallpaper is a heading and one slider.
        assertEquals(1, EditorShellMetrics.sectionsInLeadingPane(new int[] {5, 4, 2}));
    }

    @Test
    public void aPanelWhoseLastSectionIsTheBigOneStillFillsBothPanes() {
        // The keyboard: one material row, one shape row, four key rows. Material and Shape lead.
        assertEquals(2, EditorShellMetrics.sectionsInLeadingPane(new int[] {2, 2, 5}));
    }

    @Test
    public void theTrailingPaneIsNeverEmpty() {
        int[][] shapes = {{1}, {1, 1}, {9, 1}, {1, 9}, {4, 3, 2}, {1, 1, 1, 1}};
        for (int[] shape : shapes) {
            int leading = EditorShellMetrics.sectionsInLeadingPane(shape);
            assertTrue("a second column with nothing in it", leading >= 1);
            assertTrue(leading <= Math.max(1, shape.length - 1));
        }
        assertEquals("one section cannot be split", 1,
            EditorShellMetrics.sectionsInLeadingPane(new int[] {6}));
    }
}
