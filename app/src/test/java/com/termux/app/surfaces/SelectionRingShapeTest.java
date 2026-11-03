package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SelectionRingShapeTest {

    private static final float OWN = 28f;
    private static final float SCREEN = 110f;
    /** The mini UI's outline in the overlay's space. */
    private static final int[] CLIP = {0, 0, 1080, 2340};

    @Test
    public void dockedStatusRingIsPulledInsideTheScreenEdge() {
        // The bar spans the width from the top; the ring grown 4px out would cross three edges.
        int[] ring = {-4, -4, 1084, 140};
        assertTrue(SelectionRingShape.meetsScreen(EditorCard.STATUS, true));
        SelectionRingShape.keepInside(ring, CLIP);
        assertArrayEquals(new int[] {0, 0, 1080, 140}, ring);
    }

    @Test
    public void dockedStatusBarTakesTheScreenRadiusOnTopAndItsOwnBelow() {
        int[] ring = {0, 0, 1080, 140};
        float[] radii = SelectionRingShape.cornerRadii(EditorCard.STATUS, true, OWN, SCREEN, ring,
            CLIP);
        assertArrayEquals(new float[] {110f, 110f, 110f, 110f, 28f, 28f, 28f, 28f}, radii, 0f);
    }

    @Test
    public void screenCornerRunsParallelToTheScreensWhenInset() {
        // Standing 6px in from the sides: the arc is the screen's less the inset.
        int[] ring = {6, 0, 1074, 140};
        float[] radii = SelectionRingShape.cornerRadii(EditorCard.STATUS, true, OWN, SCREEN, ring,
            CLIP);
        assertEquals(104f, radii[0], 0f);
        assertEquals(104f, radii[2], 0f);
        // Past the screen's arc the corner is the bar's own again.
        assertEquals(OWN, SelectionRingShape.screenCorner(SCREEN, 200, 0, OWN), 0f);
    }

    @Test
    public void dockedKeyboardTakesItsOwnRadiusOnTopAndTheScreenRadiusBelow() {
        int[] ring = {-4, 1500, 1084, 2344};
        SelectionRingShape.keepInside(ring, CLIP);
        assertArrayEquals(new int[] {0, 1500, 1080, 2340}, ring);
        float[] radii = SelectionRingShape.cornerRadii(EditorCard.KEYBOARD, true, OWN, SCREEN,
            ring, CLIP);
        assertArrayEquals(new float[] {28f, 28f, 28f, 28f, 110f, 110f, 110f, 110f}, radii, 0f);
    }

    @Test
    public void floatingStatusBarAndKeyboardKeepTheirOwnRadiusAllRound() {
        float[] own = {28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f};
        int[] ring = {0, 0, 1080, 140};
        assertFalse(SelectionRingShape.meetsScreen(EditorCard.STATUS, false));
        assertFalse(SelectionRingShape.meetsScreen(EditorCard.KEYBOARD, false));
        assertArrayEquals(own, SelectionRingShape.cornerRadii(EditorCard.STATUS, false, OWN,
            SCREEN, ring, CLIP), 0f);
        assertArrayEquals(own, SelectionRingShape.cornerRadii(EditorCard.KEYBOARD, false, OWN,
            SCREEN, ring, CLIP), 0f);
    }

    @Test
    public void otherElementsIgnoreTheScreenEvenDocked() {
        float[] own = {28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f};
        int[] ring = {0, 0, 1080, 140};
        assertFalse(SelectionRingShape.meetsScreen(EditorCard.DOCK, true));
        assertFalse(SelectionRingShape.meetsScreen(EditorCard.TERMINAL, true));
        assertArrayEquals(own, SelectionRingShape.cornerRadii(EditorCard.DOCK, true, OWN, SCREEN,
            ring, CLIP), 0f);
        assertArrayEquals(own, SelectionRingShape.cornerRadii(EditorCard.TERMINAL, true, OWN,
            SCREEN, ring, CLIP), 0f);
    }

    @Test
    public void pageCardsWearTheHaloNotASquare() {
        assertTrue(SelectionRingShape.wearsHalo(EditorCard.HOME));
        assertTrue(SelectionRingShape.wearsHalo(EditorCard.WORKSPACE));
        assertFalse(SelectionRingShape.wearsHalo(EditorCard.STATUS));
        assertFalse(SelectionRingShape.wearsHalo(EditorCard.KEYBOARD));
        assertTrue("Customization wears the page's halo too",
            SelectionRingShape.wearsHalo(EditorCard.APPEARANCE));
        assertFalse(SelectionRingShape.wearsHalo(null));
    }

    @Test
    public void haloGrowsOutwardOnTheRoundedOutlineAndFadesSmoothly() {
        // Every layer stands outside the outline, its corner the frame's grown: never square.
        assertEquals(SCREEN + 3f, SelectionRingShape.haloLayerRadius(SCREEN, 3f), 0f);
        float previous = Float.MAX_VALUE;
        float biggestDrop = 0f;
        for (int i = 0; i < SelectionRingShape.HALO_LAYERS; i++) {
            float alpha = SelectionRingShape.haloLayerAlpha(i);
            assertTrue(alpha > 0f);
            assertTrue(alpha < previous);
            if (i > 0) biggestDrop = Math.max(biggestDrop, previous - alpha);
            previous = alpha;
        }
        // Smooth: no step between neighbouring layers is more than a tenth of the peak.
        assertTrue(biggestDrop < SelectionRingShape.HALO_PEAK_ALPHA / 10f);
        assertTrue(previous < 0.01f);
        assertEquals(0f, SelectionRingShape.haloLayerAlpha(SelectionRingShape.HALO_LAYERS), 0f);
    }
}
