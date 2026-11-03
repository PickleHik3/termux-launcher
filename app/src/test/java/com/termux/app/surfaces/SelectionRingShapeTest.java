package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SelectionRingShapeTest {

    private static final float OWN = 24f;
    private static final float SCREEN = 110f;
    private static final float GROW = 4f;

    @Test
    public void dockedStatusBarTakesTheScreenRadiusOnTopAndItsOwnBelow() {
        float[] radii = SelectionRingShape.cornerRadii(EditorCard.STATUS, true, OWN, SCREEN, GROW);
        assertArrayEquals(new float[] {114f, 114f, 114f, 114f, 28f, 28f, 28f, 28f}, radii, 0f);
    }

    @Test
    public void dockedKeyboardTakesItsOwnRadiusOnTopAndTheScreenRadiusBelow() {
        float[] radii = SelectionRingShape.cornerRadii(EditorCard.KEYBOARD, true, OWN, SCREEN,
            GROW);
        assertArrayEquals(new float[] {28f, 28f, 28f, 28f, 114f, 114f, 114f, 114f}, radii, 0f);
    }

    @Test
    public void floatingStatusBarAndKeyboardKeepTheirOwnRadiusAllRound() {
        float[] own = {28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f};
        assertArrayEquals(own,
            SelectionRingShape.cornerRadii(EditorCard.STATUS, false, OWN, SCREEN, GROW), 0f);
        assertArrayEquals(own,
            SelectionRingShape.cornerRadii(EditorCard.KEYBOARD, false, OWN, SCREEN, GROW), 0f);
    }

    @Test
    public void otherElementsIgnoreTheScreenRadiusEvenDocked() {
        float[] own = {28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f};
        assertArrayEquals(own,
            SelectionRingShape.cornerRadii(EditorCard.DOCK, true, OWN, SCREEN, GROW), 0f);
        assertArrayEquals(own,
            SelectionRingShape.cornerRadii(EditorCard.TERMINAL, true, OWN, SCREEN, GROW), 0f);
    }

    @Test
    public void radiiNeverGoNegative() {
        float[] radii = SelectionRingShape.cornerRadii(EditorCard.STATUS, true, 0f, 0f, -3f);
        for (float r : radii)
            assertEquals(0f, r, 0f);
    }

    @Test
    public void pageCardsWearTheHaloNotASquare() {
        assertTrue(SelectionRingShape.wearsHalo(EditorCard.HOME));
        assertTrue(SelectionRingShape.wearsHalo(EditorCard.WORKSPACE));
        assertFalse(SelectionRingShape.wearsHalo(EditorCard.STATUS));
        assertFalse(SelectionRingShape.wearsHalo(EditorCard.KEYBOARD));
        assertFalse(SelectionRingShape.wearsHalo(EditorCard.APPEARANCE));
        assertFalse(SelectionRingShape.wearsHalo(null));
    }

    @Test
    public void haloFollowsTheRoundedOutlineAndFadesInward() {
        // The outermost layer keeps (nearly) the frame's corner: rounded, never square.
        assertEquals(SCREEN - 1f, SelectionRingShape.haloLayerRadius(SCREEN, 1f), 0f);
        assertTrue(SelectionRingShape.haloLayerRadius(SCREEN, 1f) > 0f);
        float previous = Float.MAX_VALUE;
        for (int i = 0; i < SelectionRingShape.HALO_LAYERS; i++) {
            float alpha = SelectionRingShape.haloLayerAlpha(i);
            assertTrue(alpha > 0f);
            assertTrue(alpha < previous);
            previous = alpha;
        }
        assertEquals(0f, SelectionRingShape.haloLayerAlpha(SelectionRingShape.HALO_LAYERS), 0f);
    }
}
