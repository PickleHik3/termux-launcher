package com.termux.app.terminal.inappkeyboard;

import com.termux.app.place.PlaceLayoutStore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the arithmetic a floating keyboard is sized and placed by: the width share, the travel it
 * moves in, and the round trip between a dragged pixel and the fraction that is remembered for it.
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
}
