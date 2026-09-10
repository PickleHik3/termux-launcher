package com.termux.app.terminal.inappkeyboard;

import com.termux.app.place.PlaceLayoutStore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the arithmetic a floating keyboard is sized and placed by: the width share, the travel it
 * moves in, the round trip between a dragged pixel and the fraction that is remembered for it, and
 * what a drag on the card's bottom-left grip does to its two scales.
 */
public class FloatingKeyboardGeometryTest {

    @Test
    public void anUnmovedKeyboardStartsWhereADockedOneSits() {
        assertFalse(FloatingKeyboardGeometry.isPositionSet(PlaceLayoutStore.FLOAT_POSITION_UNSET));
        assertFalse(FloatingKeyboardGeometry.isPositionSet(Float.NaN));
        assertFalse(FloatingKeyboardGeometry.isPositionSet(1.5f));
        assertTrue(FloatingKeyboardGeometry.isPositionSet(0f));
        assertTrue(FloatingKeyboardGeometry.isPositionSet(1f));

        // Bottom-centre, which is the docked keyboard's own place: switching type moves nothing
        // until the user drags it.
        assertEquals(0.5f,
            FloatingKeyboardGeometry.xFractionOr(PlaceLayoutStore.FLOAT_POSITION_UNSET), 0f);
        assertEquals(1f,
            FloatingKeyboardGeometry.yFractionOr(PlaceLayoutStore.FLOAT_POSITION_UNSET), 0f);
        assertEquals(0.25f, FloatingKeyboardGeometry.xFractionOr(0.25f), 0f);
        assertEquals(0.25f, FloatingKeyboardGeometry.yFractionOr(0.25f), 0f);
    }

    @Test
    public void theWidthIsAShareOfTheRoomWithAFloorAndACeiling() {
        assertEquals(648, FloatingKeyboardGeometry.frameWidthPx(1080, 0.6f, 400));
        assertEquals(972, FloatingKeyboardGeometry.frameWidthPx(1080, 0.9f, 400));
        // A share that leaves nothing to type on is raised to the floor; the floor itself can never
        // push the frame wider than the room it floats in.
        assertEquals(400, FloatingKeyboardGeometry.frameWidthPx(1080, 0.1f, 400));
        assertEquals(320, FloatingKeyboardGeometry.frameWidthPx(320, 0.1f, 400));
        assertEquals(1080, FloatingKeyboardGeometry.frameWidthPx(1080, 1f, 400));
        // Nonsense in, no frame out — never a negative width.
        assertEquals(0, FloatingKeyboardGeometry.frameWidthPx(0, 0.6f, 400));
        assertEquals(0, FloatingKeyboardGeometry.frameWidthPx(-10, 0.6f, 400));
        assertEquals(1080, FloatingKeyboardGeometry.frameWidthPx(1080, Float.NaN, 400));
    }

    @Test
    public void travelIsTheRoomLeftOverAndNeverNegative() {
        assertEquals(432, FloatingKeyboardGeometry.travelPx(1080, 648));
        assertEquals(0, FloatingKeyboardGeometry.travelPx(1080, 1080));
        assertEquals(0, FloatingKeyboardGeometry.travelPx(1080, 1400));
        assertEquals(0, FloatingKeyboardGeometry.travelPx(-5, 0));
    }

    @Test
    public void aFractionPlacesTheLeadingEdgeInsideTheTravel() {
        assertEquals(0, FloatingKeyboardGeometry.positionPx(0f, 432));
        assertEquals(216, FloatingKeyboardGeometry.positionPx(0.5f, 432));
        assertEquals(432, FloatingKeyboardGeometry.positionPx(1f, 432));
        // A frame that fills its axis is at the only place it can be.
        assertEquals(0, FloatingKeyboardGeometry.positionPx(0.5f, 0));
        assertEquals(432, FloatingKeyboardGeometry.positionPx(2f, 432));
        assertEquals(0, FloatingKeyboardGeometry.positionPx(-1f, 432));
    }

    @Test
    public void aDragIsHeldInsideTheContent() {
        assertEquals(0, FloatingKeyboardGeometry.clampPx(-40, 432));
        assertEquals(200, FloatingKeyboardGeometry.clampPx(200, 432));
        assertEquals(432, FloatingKeyboardGeometry.clampPx(900, 432));
        assertEquals(0, FloatingKeyboardGeometry.clampPx(900, 0));
    }

    @Test
    public void aDraggedPixelAndItsRememberedFractionAreTheSamePlace() {
        int travel = 432;
        for (int px : new int[] {0, 1, 216, 431, 432}) {
            float fraction = FloatingKeyboardGeometry.fractionFor(px, travel);
            assertEquals("px " + px + " must survive the round trip",
                px, FloatingKeyboardGeometry.positionPx(fraction, travel));
        }
        // The same fraction against different travel is the same distance along, which is what
        // survives a rotation.
        assertEquals(0.5f, FloatingKeyboardGeometry.fractionFor(216, 432), 1e-6f);
        assertEquals(108, FloatingKeyboardGeometry.positionPx(0.5f, 216));
        // Nowhere to move is remembered as the edge it starts from, not as a division by zero.
        assertEquals(0f, FloatingKeyboardGeometry.fractionFor(120, 0), 0f);
    }

    // ------------------------------------------------------------------- the grip

    @Test
    public void aGripDragGrowsTheCardTowardTheFingerWithItsRightEdgeFixed() {
        // 1080 wide, starting at 60%: 648px. The grip is on the left, so dragging 108px left is
        // 108px of new width, and the share goes up by exactly that tenth of the room.
        assertEquals(0.7f, FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, -108, 1080, 400, 0.35f, 1f), 1e-6f);
        // And pushing the same distance the other way takes it back off again.
        assertEquals(0.5f, FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, 108, 1080, 400, 0.35f, 1f), 1e-6f);
        // A finger that has not moved has not resized anything.
        assertEquals(0.6f, FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, 0, 1080, 400, 0.35f, 1f), 1e-6f);
    }

    @Test
    public void theWidthDragStopsAtTheFloorAndAtTheHostWidth() {
        // Dragged out past the room it floats in: the whole room, never more.
        assertEquals(1f, FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, -5000, 1080, 400, 0.35f, 1f), 1e-6f);
        // Dragged in past the point there is a keyboard left: 400px of the 1080 is the floor, and
        // it is above the 0.35 the share itself allows, so it is the one that stops the drag.
        assertEquals(400 / 1080f, FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, 5000, 1080, 400, 0.35f, 1f), 1e-6f);
        // On a wider host the share's floor is the higher of the two, and takes over.
        assertEquals(0.35f, FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, 5000, 2000, 400, 0.35f, 1f), 1e-6f);
        // No room, no resize.
        assertEquals(0.6f, FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, -300, 0, 400, 0.35f, 1f), 1e-6f);
    }

    @Test
    public void theLeftEdgeMovesByWhateverWidthTheCardGained() {
        // 648 wide at x=200, grown to 748: the right edge stayed at 848, so the left edge came out
        // to 100.
        assertEquals(100, FloatingKeyboardGeometry.resizeXPx(200, 648, 748, 1080));
        // Narrowed instead, and the left edge walks back in.
        assertEquals(300, FloatingKeyboardGeometry.resizeXPx(200, 648, 548, 1080));
        // A card grown against the left edge is held there rather than walking off it.
        assertEquals(0, FloatingKeyboardGeometry.resizeXPx(40, 648, 1000, 1080));
        // And one that would end past the right edge is held inside too.
        assertEquals(80, FloatingKeyboardGeometry.resizeXPx(600, 480, 1000, 1080));
    }

    @Test
    public void theBottomEdgeStaysWhateverHeightTheCardTook() {
        // 500 tall at y=200, grown to 600: the bottom edge stayed at 700, so the top came up
        // to 100.
        assertEquals(100, FloatingKeyboardGeometry.resizeYPx(200, 500, 600, 1800));
        // Shorter instead, and the top edge walks back down.
        assertEquals(300, FloatingKeyboardGeometry.resizeYPx(200, 500, 400, 1800));
        // A card grown past the top of the room is held at it, which is the one thing that moves
        // its bottom edge.
        assertEquals(0, FloatingKeyboardGeometry.resizeYPx(40, 500, 900, 1800));
        // And one that would end below the bottom is held inside too.
        assertEquals(800, FloatingKeyboardGeometry.resizeYPx(1400, 400, 1000, 1800));
    }

    @Test
    public void theHeightFollowsTheShareOfTheKeyboardTheFingerDragged() {
        // 400px of keyboard, dragged up by a quarter of itself: up is taller.
        assertEquals(1.25f, FloatingKeyboardGeometry.heightScaleForResize(
            1f, -100, 400, 0.6f, 1.6f), 1e-6f);
        assertEquals(0.75f, FloatingKeyboardGeometry.heightScaleForResize(
            1f, 100, 400, 0.6f, 1.6f), 1e-6f);
        // Every frame is measured from the scale the drag started at, so an already-grown card
        // grows from there rather than from 1.
        assertEquals(1.5f, FloatingKeyboardGeometry.heightScaleForResize(
            1.2f, -100, 400, 0.6f, 1.6f), 1e-6f);
        assertEquals(1f, FloatingKeyboardGeometry.heightScaleForResize(
            1f, 0, 400, 0.6f, 1.6f), 1e-6f);
    }

    @Test
    public void theHeightDragStopsAtBothEndsOfItsRange() {
        assertEquals(1.6f, FloatingKeyboardGeometry.heightScaleForResize(
            1f, -5000, 400, 0.6f, 1.6f), 1e-6f);
        assertEquals(0.6f, FloatingKeyboardGeometry.heightScaleForResize(
            1f, 5000, 400, 0.6f, 1.6f), 1e-6f);
        // A drag further down than the keyboard is tall is a zero height asked for, not a negative
        // one, and the floor answers it.
        assertEquals(0.6f, FloatingKeyboardGeometry.heightScaleForResize(
            1f, 400, 400, 0.6f, 1.6f), 1e-6f);
        // Nothing measured yet: nothing to scale against, so the scale stands.
        assertEquals(1.2f, FloatingKeyboardGeometry.heightScaleForResize(
            1.2f, -300, 0, 0.6f, 1.6f), 1e-6f);
    }

    @Test
    public void aResizedWidthAndItsPlaceComeBackTheSameOnTheNextDrag() {
        // Out and back again by the same distance is the share it started at, and the left edge
        // is where it started too.
        float grown = FloatingKeyboardGeometry.widthScaleForResize(
            0.6f, -108, 1080, 400, 0.35f, 1f);
        int grownWidth = FloatingKeyboardGeometry.frameWidthPx(1080, grown, 400);
        int grownX = FloatingKeyboardGeometry.resizeXPx(200, 648, grownWidth, 1080);
        assertEquals(756, grownWidth);
        assertEquals(92, grownX);

        float back = FloatingKeyboardGeometry.widthScaleForResize(
            grown, 108, 1080, 400, 0.35f, 1f);
        int backWidth = FloatingKeyboardGeometry.frameWidthPx(1080, back, 400);
        assertEquals(0.6f, back, 1e-6f);
        assertEquals(648, backWidth);
        assertEquals(200, FloatingKeyboardGeometry.resizeXPx(grownX, grownWidth, backWidth, 1080));
    }

    @Test
    public void aScaleIsHeldBetweenTheTwoEndsItsPreferenceAllows() {
        assertEquals(0.8f, FloatingKeyboardGeometry.clampScale(0.8f, 0.6f, 1.6f), 0f);
        assertEquals(0.6f, FloatingKeyboardGeometry.clampScale(0.1f, 0.6f, 1.6f), 0f);
        assertEquals(1.6f, FloatingKeyboardGeometry.clampScale(9f, 0.6f, 1.6f), 0f);
        assertEquals(0.6f, FloatingKeyboardGeometry.clampScale(Float.NaN, 0.6f, 1.6f), 0f);
        assertEquals(0.6f,
            FloatingKeyboardGeometry.clampScale(Float.NEGATIVE_INFINITY, 0.6f, 1.6f), 0f);
    }
}
