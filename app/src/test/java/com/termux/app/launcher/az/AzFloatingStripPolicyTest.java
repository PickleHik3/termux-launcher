package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.az.AzFloatingStripPolicy.Growth;
import com.termux.app.launcher.az.AzFloatingStripPolicy.LabelSide;
import com.termux.app.launcher.az.AzFloatingStripPolicy.Strip;
import com.termux.app.launcher.az.AzScrubGesture.Bounds;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/** The floating strip's arithmetic: slots, pages, hit-testing, label side and the breath. */
public class AzFloatingStripPolicyTest {

    private static final float DENSITY = 2.75f;

    private static final float GAP = AzFloatingStripPolicy.ANCHOR_GAP_DP * DENSITY;
    private static final float MARGIN = AzFloatingStripPolicy.SIDE_MARGIN_DP * DENSITY;
    private static final float ICON = AzFloatingStripPolicy.ICON_SIZE_DP * DENSITY;

    /** A bottom bar the way the screen gives it: letters along the foot of a host this wide. */
    private static Strip bottom(float hostLeft, float hostWidth, float anchorX, int count) {
        return AzFloatingStripPolicy.layout(Edge.BOTTOM,
            new Bounds(hostLeft, 800f, hostLeft + hostWidth, 860f),
            new Bounds(hostLeft, 0f, hostLeft + hostWidth, 2000f),
            anchorX, 830f, count, DENSITY);
    }

    @Test
    public void slotsFillTheWidthBetweenTheMarginsAndStopAtTheCap() {
        // 40dp icons on a 10dp pitch inside 16dp margins: 1080px at 2.75 leaves 1080-88 = 992px,
        // and each icon after the first costs 137.5px on top of the first 110px.
        assertEquals(7, AzFloatingStripPolicy.slotsForWidth(1080f, DENSITY));
        // A wide tablet is capped rather than turning the strip into a second app drawer.
        assertEquals(AzFloatingStripPolicy.MAX_SLOTS,
            AzFloatingStripPolicy.slotsForWidth(4000f, DENSITY));
        // Never zero: a sliver still gets one slot to draw into.
        assertEquals(1, AzFloatingStripPolicy.slotsForWidth(40f, DENSITY));
        assertEquals(1, AzFloatingStripPolicy.slotsForWidth(0f, DENSITY));
    }

    @Test
    public void aStripCentredOnTheMiddleLetterIsCentredInItsHostAndRestsAboveTheLetters() {
        Strip strip = bottom(0f, 1000f, 500f, 4);
        assertNotNull(strip);
        assertEquals(4, strip.slotCount);
        float icon = AzFloatingStripPolicy.ICON_SIZE_DP * DENSITY;
        float spacing = AzFloatingStripPolicy.SLOT_SPACING_DP * DENSITY;
        assertEquals((4f * icon) + (3f * spacing), strip.width(), 0.01f);
        assertEquals(500f, strip.centerX(), 0.01f);
        // The band hangs entirely above the anchor, clear of the letters by the anchor gap.
        assertEquals(800f - (AzFloatingStripPolicy.ANCHOR_GAP_DP * DENSITY), strip.bottom, 0.01f);
        assertEquals(icon, strip.height(), 0.01f);
        assertTrue(strip.bottom < 800f);
    }

    @Test
    public void aStripCentresOnTheLetterUnderTheThumbAndStaysInsideTheMargins() {
        float margin = AzFloatingStripPolicy.SIDE_MARGIN_DP * DENSITY;
        // A letter a third of the way along: the band sits under it, not in the middle of the bar.
        Strip under = bottom(0f, 1000f, 330f, 3);
        assertNotNull(under);
        assertEquals(330f, under.centerX(), 0.01f);
        // The first letters: a band centred there would run off the left, so it slides in to the margin.
        Strip atStart = bottom(0f, 1000f, 30f, 3);
        assertNotNull(atStart);
        assertEquals(margin, atStart.left, 0.01f);
        // The last letters: it slides back from the right margin, keeping its full width.
        Strip atEnd = bottom(0f, 1000f, 980f, 3);
        assertNotNull(atEnd);
        assertEquals(1000f - margin, atEnd.right, 0.01f);
        assertEquals(under.width(), atEnd.width(), 0.01f);
        // A host offset on screen clamps against its own edges, not the screen's.
        Strip offset = bottom(200f, 1000f, 190f, 3);
        assertNotNull(offset);
        assertEquals(200f + margin, offset.left, 0.01f);
    }

    @Test
    public void aStripNeverLaysOutMoreSlotsThanTheWidthHolds() {
        Strip strip = bottom(0f, 400f, 200f, 8);
        assertNotNull(strip);
        assertEquals(AzFloatingStripPolicy.slotsForWidth(400f, DENSITY), strip.slotCount);
        assertTrue(strip.left >= AzFloatingStripPolicy.SIDE_MARGIN_DP * DENSITY - 0.01f);
        assertNull("nothing to show is no strip",
            bottom(0f, 400f, 200f, 0));
    }

    @Test
    public void slotCentresRepeatOnTheIconAndSpacingPitchAndClampOutOfRange() {
        Strip strip = bottom(0f, 1000f, 500f, 3);
        assertNotNull(strip);
        float pitch = strip.slotPitchPx();
        assertEquals(strip.left + (strip.iconSizePx * 0.5f), strip.slotCenterX(0), 0.01f);
        assertEquals(strip.slotCenterX(0) + pitch, strip.slotCenterX(1), 0.01f);
        assertEquals(strip.slotCenterX(2), strip.slotCenterX(9), 0.01f);
        assertEquals(strip.slotCenterX(0), strip.slotCenterX(-4), 0.01f);
    }

    @Test
    public void pagesPullTheLastOneBackSoTheStripIsNeverAShortTail() {
        assertEquals(1, AzFloatingStripPolicy.pageCount(0, 5));
        assertEquals(1, AzFloatingStripPolicy.pageCount(5, 5));
        assertEquals(2, AzFloatingStripPolicy.pageCount(6, 5));
        assertEquals(0, AzFloatingStripPolicy.pageStart(6, 0, 5));
        assertEquals(1, AzFloatingStripPolicy.pageStart(6, 1, 5));
        // Which means every page but a single short one shows a full strip.
        assertEquals(5, AzFloatingStripPolicy.pageSize(6, 1, 5));
        assertEquals(3, AzFloatingStripPolicy.pageSize(3, 0, 5));
        assertEquals(0, AzFloatingStripPolicy.pageSize(0, 0, 5));
        // A page index past the end clamps rather than reading off the list.
        assertEquals(AzFloatingStripPolicy.pageStart(6, 1, 5),
            AzFloatingStripPolicy.pageStart(6, 9, 5));
    }

    @Test
    public void slotHitTestingHoldsTheLastSlotAcrossItsBoundary() {
        Strip strip = bottom(0f, 1000f, 500f, 4);
        assertNotNull(strip);
        float y = strip.centerY();
        float pitch = strip.slotPitchPx();
        assertEquals(0, AzFloatingStripPolicy.slotAt(strip, strip.slotCenterX(0), y, -1, DENSITY));
        assertEquals(2, AzFloatingStripPolicy.slotAt(strip, strip.slotCenterX(2), y, -1, DENSITY));

        // Just past the 0|1 boundary, with slot 0 held: hysteresis keeps it.
        float justPast = strip.left + pitch + (pitch * 0.1f);
        assertEquals(0, AzFloatingStripPolicy.slotAt(strip, justPast, y, 0, DENSITY));
        assertEquals("with nothing held the same point reads as the new slot",
            1, AzFloatingStripPolicy.slotAt(strip, justPast, y, -1, DENSITY));
        // Well past it, the focus moves on.
        float wellPast = strip.left + pitch + (pitch * 0.5f);
        assertEquals(1, AzFloatingStripPolicy.slotAt(strip, wellPast, y, 0, DENSITY));
        // And the same in reverse.
        float justBefore = strip.left + pitch - (pitch * 0.1f);
        assertEquals(1, AzFloatingStripPolicy.slotAt(strip, justBefore, y, 1, DENSITY));
    }

    @Test
    public void theEndsOfTheStripKeepTheirIconsSoAnEdgeDwellDoesNotLoseFocus() {
        Strip strip = bottom(0f, 1000f, 500f, 4);
        assertNotNull(strip);
        float y = strip.centerY();
        assertEquals(0, AzFloatingStripPolicy.slotAt(strip, strip.left - 60f, y, -1, DENSITY));
        assertEquals(3, AzFloatingStripPolicy.slotAt(strip, strip.right + 60f, y, -1, DENSITY));
        // Off the band vertically is off the strip, though, with slack for a wandering thumb.
        float slack = AzFloatingStripPolicy.SLOT_VERTICAL_SLACK_DP * DENSITY;
        assertEquals(0, AzFloatingStripPolicy.slotAt(strip, strip.slotCenterX(0),
            strip.top - (slack * 0.5f), -1, DENSITY));
        assertEquals(-1, AzFloatingStripPolicy.slotAt(strip, strip.slotCenterX(0),
            strip.top - (slack * 2f), -1, DENSITY));
        assertEquals(-1, AzFloatingStripPolicy.slotAt(null, 10f, 10f, -1, DENSITY));
    }

    @Test
    public void anEdgeOnlyExistsWhenThereIsAnotherPageToReach() {
        Strip strip = bottom(0f, 1000f, 500f, 6);
        assertNotNull(strip);
        assertEquals(AzFloatingStripPolicy.EDGE_LEFT,
            AzFloatingStripPolicy.edgeAt(strip, strip.left + 2f, true, DENSITY));
        assertEquals(AzFloatingStripPolicy.EDGE_RIGHT,
            AzFloatingStripPolicy.edgeAt(strip, strip.right - 2f, true, DENSITY));
        assertEquals(AzFloatingStripPolicy.EDGE_NONE,
            AzFloatingStripPolicy.edgeAt(strip, strip.centerX(), true, DENSITY));
        assertEquals("one page has nowhere to dwell to",
            AzFloatingStripPolicy.EDGE_NONE,
            AzFloatingStripPolicy.edgeAt(strip, strip.left + 2f, false, DENSITY));
    }

    // ----------------------------------------------- the band grows towards the screen's middle

    /** A 1080x2000 screen with the bar standing on one edge of it, the way the activity gives it. */
    private static Bounds barOn(Edge edge) {
        switch (edge) {
            case TOP: return new Bounds(0f, 0f, 1080f, 60f);
            case LEFT: return new Bounds(0f, 200f, 60f, 1800f);
            case RIGHT: return new Bounds(1020f, 200f, 1080f, 1800f);
            case BOTTOM:
            default: return new Bounds(0f, 1940f, 1080f, 2000f);
        }
    }

    private static Bounds canvasFor(Edge edge) {
        Bounds bar = barOn(edge);
        return edge.isOnSide()
            ? new Bounds(0f, bar.top, 1080f, bar.bottom)
            : new Bounds(bar.left, 0f, bar.right, 2000f);
    }

    private static Strip onEdge(Edge edge, float anchorX, float anchorY, int count) {
        return AzFloatingStripPolicy.layout(edge, barOn(edge), canvasFor(edge),
            anchorX, anchorY, count, DENSITY);
    }

    @Test
    public void everyEdgeGrowsTheBandAwayFromItsBarAndNeverOverItSaid() {
        assertEquals(Growth.UP, AzFloatingStripPolicy.growthFor(Edge.BOTTOM));
        assertEquals(Growth.DOWN, AzFloatingStripPolicy.growthFor(Edge.TOP));
        assertEquals(Growth.LEFT, AzFloatingStripPolicy.growthFor(Edge.RIGHT));
        assertEquals(Growth.RIGHT, AzFloatingStripPolicy.growthFor(Edge.LEFT));

        // A bottom bar: the band is above the letters, clear of them by the anchor gap.
        Strip bottomBar = onEdge(Edge.BOTTOM, 540f, 1970f, 3);
        assertNotNull(bottomBar);
        assertEquals(barOn(Edge.BOTTOM).top - GAP, bottomBar.bottom, 0.01f);
        // A top bar: below them, by the same gap, and the band still reads left to right.
        Strip topBar = onEdge(Edge.TOP, 540f, 30f, 3);
        assertNotNull(topBar);
        assertEquals(barOn(Edge.TOP).bottom + GAP, topBar.top, 0.01f);
        assertEquals(bottomBar.width(), topBar.width(), 0.01f);
        // A right-hand column: to the LEFT of the letters, as one row of icons, level with the
        // finger. This is the defect — it used to rise up the screen over the letters themselves.
        Strip rightBar = onEdge(Edge.RIGHT, 1050f, 900f, 3);
        assertNotNull(rightBar);
        assertEquals(barOn(Edge.RIGHT).left - GAP, rightBar.right, 0.01f);
        assertEquals(900f, rightBar.centerY(), 0.01f);
        assertEquals("a column's matches are the same row of icons a bottom bar's are",
            bottomBar.width(), rightBar.width(), 0.01f);
        // A left-hand column: to the right of the letters.
        Strip leftBar = onEdge(Edge.LEFT, 30f, 900f, 3);
        assertNotNull(leftBar);
        assertEquals(barOn(Edge.LEFT).right + GAP, leftBar.left, 0.01f);
        assertEquals(900f, leftBar.centerY(), 0.01f);
    }

    @Test
    public void noEdgeEverDrawsTheBandOverTheBarItCameFrom() {
        for (Edge edge : Edge.values()) {
            for (int count = 1; count <= AzFloatingStripPolicy.MAX_SLOTS; count++) {
                Bounds bar = barOn(edge);
                // Anchored hard against both ends of the bar, which is where a clamp would push
                // the band back onto it if it were going to.
                float[][] anchors = {
                    {bar.left, bar.top}, {bar.right, bar.bottom},
                    {(bar.left + bar.right) * 0.5f, (bar.top + bar.bottom) * 0.5f}
                };
                for (float[] anchor : anchors) {
                    Strip strip = onEdge(edge, anchor[0], anchor[1], count);
                    assertNotNull(edge + " x" + count, strip);
                    String why = "band over the bar on " + edge + " with " + count;
                    boolean clear = strip.right <= bar.left || strip.left >= bar.right
                        || strip.bottom <= bar.top || strip.top >= bar.bottom;
                    assertTrue(why, clear);
                }
            }
        }
    }

    @Test
    public void everyEdgeClampsTheBandInsideTheCanvasItWasGiven() {
        for (Edge edge : Edge.values()) {
            Bounds canvas = canvasFor(edge);
            for (int count = 1; count <= AzFloatingStripPolicy.MAX_SLOTS; count++) {
                float[][] anchors = {
                    {canvas.left - 400f, canvas.top - 400f},
                    {canvas.right + 400f, canvas.bottom + 400f}
                };
                for (float[] anchor : anchors) {
                    Strip strip = onEdge(edge, anchor[0], anchor[1], count);
                    assertNotNull(strip);
                    String why = edge + " with " + count + " slots";
                    assertTrue(why, strip.left >= canvas.left + MARGIN - 0.01f);
                    assertTrue(why, strip.right <= canvas.right - MARGIN + 0.01f);
                    if (edge.isOnSide()) {
                        assertTrue(why, strip.top >= canvas.top + MARGIN - 0.01f);
                        assertTrue(why, strip.bottom <= canvas.bottom - MARGIN + 0.01f);
                    }
                }
            }
        }
    }

    @Test
    public void aColumnNeverLaysOutMoreIconsThanTheRoomBesideItHolds() {
        // A right-hand column on a narrow screen: the run is what is left between the bar and the
        // far margin, so the band can never be laid out longer than the space it grows into.
        Bounds bar = new Bounds(420f, 0f, 480f, 900f);
        Bounds canvas = new Bounds(0f, 0f, 480f, 900f);
        Strip strip = AzFloatingStripPolicy.layout(Edge.RIGHT, bar, canvas, 450f, 450f, 8, DENSITY);
        assertNotNull(strip);
        float run = AzFloatingStripPolicy.availableLengthPx(Edge.RIGHT, bar, canvas, DENSITY);
        assertEquals(AzFloatingStripPolicy.slotsForLength(run, DENSITY), strip.slotCount);
        assertTrue("and it still sits inside the room it measured",
            strip.width() <= run + 0.01f);
        assertTrue(strip.right <= bar.left - GAP + 0.01f);
        assertTrue(strip.left >= canvas.left + MARGIN - 0.01f);
    }

    @Test
    public void aMissingBarOrCanvasIsNoStripAtAll() {
        assertNull(AzFloatingStripPolicy.layout(Edge.BOTTOM, Bounds.EMPTY,
            canvasFor(Edge.BOTTOM), 540f, 1970f, 3, DENSITY));
        assertNull(AzFloatingStripPolicy.layout(Edge.BOTTOM, barOn(Edge.BOTTOM),
            Bounds.EMPTY, 540f, 1970f, 3, DENSITY));
    }

    @Test
    public void theNameReadsOnTheFarSideOfTheBandFromTheBar() {
        // Off a top bar the band hangs below the letters, so the name has to read below it too or
        // it lands back in the gap it came out of.
        assertEquals(LabelSide.BELOW, AzFloatingStripPolicy.labelSideFor(Edge.TOP, false));
        assertEquals(LabelSide.BELOW, AzFloatingStripPolicy.labelSideFor(Edge.TOP, true));
        // Everywhere else the orientation decides, exactly as it always has.
        assertEquals(LabelSide.ABOVE, AzFloatingStripPolicy.labelSideFor(Edge.BOTTOM, false));
        assertEquals(LabelSide.ABOVE, AzFloatingStripPolicy.labelSideFor(Edge.LEFT, false));
        assertEquals(LabelSide.ABOVE, AzFloatingStripPolicy.labelSideFor(Edge.RIGHT, false));
        assertEquals(LabelSide.BELOW, AzFloatingStripPolicy.labelSideFor(Edge.RIGHT, true));
    }

    @Test
    public void theLabelReadsAboveInPortraitAndBelowInLandscape() {
        assertEquals(LabelSide.ABOVE, AzFloatingStripPolicy.labelSide(false));
        assertEquals(LabelSide.BELOW, AzFloatingStripPolicy.labelSide(true));
    }

    @Test
    public void theBreathIsCalmAtBothTurnsAndBoundedThroughout() {
        assertEquals(0f, AzFloatingStripPolicy.breathPhase(0L), 0.0001f);
        assertEquals(0.5f, AzFloatingStripPolicy.breathPhase(
            AzFloatingStripPolicy.BREATH_PERIOD_MS / 2), 0.0001f);
        assertEquals("a breath wraps rather than running away",
            0f, AzFloatingStripPolicy.breathPhase(AzFloatingStripPolicy.BREATH_PERIOD_MS), 0.0001f);

        assertEquals(0f, AzFloatingStripPolicy.breathEase(0f), 0.0001f);
        assertEquals(1f, AzFloatingStripPolicy.breathEase(0.5f), 0.0001f);
        assertEquals(0f, AzFloatingStripPolicy.breathEase(1f), 0.0001f);
        // Flat at the turns: two samples either side of the bottom differ by almost nothing.
        assertEquals(AzFloatingStripPolicy.breathEase(0.02f),
            AzFloatingStripPolicy.breathEase(-0.02f), 0.001f);

        assertEquals(1f, AzFloatingStripPolicy.breathScale(0f), 0.0001f);
        assertEquals(1f + AzFloatingStripPolicy.BREATH_SCALE_AMPLITUDE,
            AzFloatingStripPolicy.breathScale(0.5f), 0.0001f);
        assertEquals(AzFloatingStripPolicy.BREATH_ALPHA_FLOOR,
            AzFloatingStripPolicy.breathAlpha(0f), 0.0001f);
        assertEquals(1f, AzFloatingStripPolicy.breathAlpha(0.5f), 0.0001f);
        for (int i = 0; i <= 100; i++) {
            float phase = i / 100f;
            float ease = AzFloatingStripPolicy.breathEase(phase);
            assertTrue("ease in range at " + phase, ease >= 0f && ease <= 1f);
            assertTrue("alpha never vanishes at " + phase,
                AzFloatingStripPolicy.breathAlpha(phase) >= AzFloatingStripPolicy.BREATH_ALPHA_FLOOR);
        }
    }
}
