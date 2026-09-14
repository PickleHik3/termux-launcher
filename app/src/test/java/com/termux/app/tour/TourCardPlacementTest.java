package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Where the card lands for the control it is about.
 *
 * <p>A portrait phone's numbers throughout: a 1080 x 2400 overlay, a 600 x 300 card, a 16dp margin
 * and an 8dp gap at 3x, so a failure reads as the placement a user would have seen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class TourCardPlacementTest {

    private static final int W = 1080;
    private static final int H = 2400;
    private static final int CARD_W = 600;
    private static final int CARD_H = 300;
    private static final int MARGIN = 48;
    private static final int GAP = 24;
    private static final int POINTER = 21;
    private static final int POINTER_HALF = 27;
    /** Gap plus pointer: how far the card's own edge stands off the control. */
    private static final int STAND_OFF = GAP + POINTER;

    private static TourCardPlacement place(Rect target) {
        return place(target, CARD_W, CARD_H);
    }

    private static TourCardPlacement place(Rect target, int cardWidth, int cardHeight) {
        return place(target, cardWidth, cardHeight, MARGIN, MARGIN);
    }

    private static TourCardPlacement place(Rect target, int cardWidth, int cardHeight,
                                           int topMargin, int bottomMargin) {
        return TourCardPlacement.place(W, H, cardWidth, cardHeight, target, MARGIN, topMargin,
            bottomMargin, GAP, POINTER, POINTER_HALF);
    }

    @Test
    public void aTargetInTheTopHalfPutsTheCardUnderItPointingUp() {
        Rect target = new Rect(400, 200, 680, 300);
        TourCardPlacement placement = place(target);
        assertEquals(300 + STAND_OFF, placement.top);
        assertEquals(target.centerX() - (CARD_W / 2), placement.left);
        assertEquals(TourCardPlacement.POINTER_TOP, placement.pointerEdge);
        assertEquals(target.centerX(), placement.pointerCenterX);
    }

    @Test
    public void aTargetInTheBottomHalfPutsTheCardOverItPointingDown() {
        Rect target = new Rect(400, 1900, 680, 2000);
        TourCardPlacement placement = place(target);
        assertEquals(1900 - STAND_OFF - CARD_H, placement.top);
        assertEquals(TourCardPlacement.POINTER_BOTTOM, placement.pointerEdge);
        assertEquals(target.centerX(), placement.pointerCenterX);
    }

    @Test
    public void theCardIsCentredOnItsTargetNotOnTheScreen() {
        TourCardPlacement left = place(new Rect(300, 200, 420, 260));
        TourCardPlacement right = place(new Rect(660, 200, 780, 260));
        assertEquals(360 - (CARD_W / 2), left.left);
        assertEquals(720 - (CARD_W / 2), right.left);
    }

    @Test
    public void aTargetAgainstTheLeftEdgeKeepsTheCardInsideTheMargin() {
        Rect target = new Rect(0, 200, 90, 260);
        TourCardPlacement placement = place(target);
        assertEquals(MARGIN, placement.left);
        assertTrue(placement.hasPointer());
        // The pointer still leans toward the control, without riding the card's rounded corner.
        assertTrue(placement.pointerCenterX >= placement.left);
        assertTrue(placement.pointerCenterX < placement.left + (CARD_W / 2));
    }

    @Test
    public void aTargetAgainstTheRightEdgeKeepsTheCardInsideTheMargin() {
        Rect target = new Rect(990, 200, 1080, 260);
        TourCardPlacement placement = place(target);
        assertEquals(W - MARGIN - CARD_W, placement.left);
        assertTrue(placement.pointerCenterX <= placement.left + CARD_W);
        assertTrue(placement.pointerCenterX > placement.left + (CARD_W / 2));
    }

    @Test
    public void theCardNeverCoversItsTarget() {
        for (int top = 0; top + 120 <= H; top += 60) {
            Rect target = new Rect(400, top, 680, top + 120);
            TourCardPlacement placement = place(target);
            if (!placement.hasPointer()) continue;
            boolean above = placement.top + CARD_H <= target.top;
            boolean below = placement.top >= target.bottom;
            assertTrue("card overlaps a target at " + top, above || below);
        }
    }

    @Test
    public void aTargetInTheTopHalfWithNoRoomUnderItFlipsTheCardAbove() {
        // A bottom keep-out the size of the keyboard: the target is still in the top half, so
        // below is tried first, and only the flip leaves room for the card.
        Rect target = new Rect(400, 900, 680, 1000);
        TourCardPlacement placement = place(target, CARD_W, CARD_H, MARGIN, 1500);
        assertEquals(900 - STAND_OFF - CARD_H, placement.top);
        assertEquals(TourCardPlacement.POINTER_BOTTOM, placement.pointerEdge);
    }

    @Test
    public void aTargetInTheBottomHalfWithNoRoomOverItFlipsTheCardBelow() {
        Rect target = new Rect(400, 1400, 680, 1500);
        TourCardPlacement placement = place(target, CARD_W, CARD_H, 1300, MARGIN);
        assertEquals(1500 + STAND_OFF, placement.top);
        assertEquals(TourCardPlacement.POINTER_TOP, placement.pointerEdge);
    }

    @Test
    public void aCardThatFitsOnNeitherSideIsClampedOnScreenAndDropsItsPointer() {
        Rect target = new Rect(400, 1150, 680, 1250);
        TourCardPlacement placement = place(target, CARD_W, 2200);
        assertFalse(placement.hasPointer());
        assertTrue(placement.top >= MARGIN);
        assertTrue(placement.top + 2200 <= H - MARGIN);
    }

    @Test
    public void noTargetKeepsTheMiddleOfTheOverlayAndNoPointer() {
        TourCardPlacement placement = place(null);
        assertEquals((W - CARD_W) / 2, placement.left);
        assertEquals((H - CARD_H) / 2, placement.top);
        assertEquals(TourCardPlacement.POINTER_NONE, placement.pointerEdge);
        assertFalse(placement.hasPointer());
    }

    @Test
    public void aCardWithNoTargetStaysOutFromUnderTheSystemBars() {
        // A keep-out taller than the room left for a centred card: it comes to rest under the
        // status bar's inset rather than on top of it.
        TourCardPlacement placement = place(null, CARD_W, CARD_H, 1400, 900);
        assertEquals(1400, placement.top);
    }

    @Test
    public void anEmptyTargetIsTheSameAsNoTarget() {
        TourCardPlacement placement = place(new Rect(500, 500, 500, 500));
        assertEquals((H - CARD_H) / 2, placement.top);
        assertFalse(placement.hasPointer());
    }

    @Test
    public void anOverlayThatHasNotBeenLaidOutYetAsksForNothingImpossible() {
        TourCardPlacement placement = TourCardPlacement.place(0, 0, CARD_W, CARD_H,
            new Rect(0, 0, 10, 10), MARGIN, MARGIN, MARGIN, GAP, POINTER, POINTER_HALF);
        assertEquals(MARGIN, placement.left);
        assertEquals(MARGIN, placement.top);
        assertFalse(placement.hasPointer());
    }

    @Test
    public void aCardWiderThanTheOverlayStillStartsInsideTheMargin() {
        TourCardPlacement placement = place(new Rect(400, 200, 680, 260), W + 400, CARD_H);
        assertEquals(MARGIN, placement.left);
    }
}
