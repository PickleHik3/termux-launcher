package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.az.AzFloatingStripPolicy.LabelSide;
import com.termux.app.launcher.az.AzFloatingStripPolicy.Strip;

import org.junit.Test;

/** The floating strip's arithmetic: slots, pages, hit-testing, label side and the breath. */
public class AzFloatingStripPolicyTest {

    private static final float DENSITY = 2.75f;

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
        Strip strip = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 500f, 4, DENSITY);
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
        Strip under = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 330f, 3, DENSITY);
        assertNotNull(under);
        assertEquals(330f, under.centerX(), 0.01f);
        // The first letters: a band centred there would run off the left, so it slides in to the margin.
        Strip atStart = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 30f, 3, DENSITY);
        assertNotNull(atStart);
        assertEquals(margin, atStart.left, 0.01f);
        // The last letters: it slides back from the right margin, keeping its full width.
        Strip atEnd = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 980f, 3, DENSITY);
        assertNotNull(atEnd);
        assertEquals(1000f - margin, atEnd.right, 0.01f);
        assertEquals(under.width(), atEnd.width(), 0.01f);
        // A host offset on screen clamps against its own edges, not the screen's.
        Strip offset = AzFloatingStripPolicy.layout(200f, 1000f, 800f, 190f, 3, DENSITY);
        assertNotNull(offset);
        assertEquals(200f + margin, offset.left, 0.01f);
    }

    @Test
    public void aStripNeverLaysOutMoreSlotsThanTheWidthHolds() {
        Strip strip = AzFloatingStripPolicy.layout(0f, 400f, 500f, 200f, 8, DENSITY);
        assertNotNull(strip);
        assertEquals(AzFloatingStripPolicy.slotsForWidth(400f, DENSITY), strip.slotCount);
        assertTrue(strip.left >= AzFloatingStripPolicy.SIDE_MARGIN_DP * DENSITY - 0.01f);
        assertNull("nothing to show is no strip",
            AzFloatingStripPolicy.layout(0f, 400f, 500f, 200f, 0, DENSITY));
    }

    @Test
    public void slotCentresRepeatOnTheIconAndSpacingPitchAndClampOutOfRange() {
        Strip strip = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 500f, 3, DENSITY);
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
        Strip strip = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 500f, 4, DENSITY);
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
        Strip strip = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 500f, 4, DENSITY);
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
        Strip strip = AzFloatingStripPolicy.layout(0f, 1000f, 800f, 500f, 6, DENSITY);
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
