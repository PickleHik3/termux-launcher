package com.termux.app.surfaces;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The preset mocks' geometry: the one decision the presets disagree on most — Docked's flush
 * bottom slab against Floating's inset capsule — has to survive the scale-down, or the cards all
 * read alike.
 */
public class SurfaceEditorPresetPreviewTest {

    private static final float DENSITY = 2.625f;
    private static final int W = Math.round(SurfaceEditorPresetPreview.CARD_WIDTH_DP * DENSITY);
    private static final int H = Math.round(SurfaceEditorPresetPreview.CARD_HEIGHT_DP * DENSITY);

    @Test
    public void dockedSlabRunsFlushToTheCardEdges() {
        int[] insets = SurfaceEditorPresetPreview.bottomSlabInsets(W, H, DENSITY, 12, false);
        assertEquals(0, insets[0]);
        assertEquals(0, insets[2]);
        assertEquals(0, insets[3]);
        assertEquals(0f, SurfaceEditorPresetPreview.surfaceRadiusPx(DENSITY, 24, false), 0.001f);
    }

    @Test
    public void floatingSlabPullsInAndRounds() {
        int[] insets = SurfaceEditorPresetPreview.bottomSlabInsets(W, H, DENSITY, 16, true);
        assertTrue("side air must survive the scale-down", insets[0] > 0);
        assertEquals(insets[0], insets[2]);
        assertTrue("bottom air must survive the scale-down", insets[3] > 0);
        assertTrue(SurfaceEditorPresetPreview.surfaceRadiusPx(DENSITY, 28, true) > 0f);
    }

    @Test
    public void aWiderSideGapReadsWiderOnTheCard() {
        int narrow = SurfaceEditorPresetPreview.bottomSlabInsets(W, H, DENSITY, 12, true)[0];
        int wide = SurfaceEditorPresetPreview.bottomSlabInsets(W, H, DENSITY, 48, true)[0];
        assertTrue(narrow + " !< " + wide, narrow < wide);
    }

    @Test
    public void layersStackTopToBottomWithoutOverlap() {
        int[] status = SurfaceEditorPresetPreview.statusInsets(W, H, DENSITY, 12);
        int[] terminal = SurfaceEditorPresetPreview.terminalInsets(W, H, DENSITY, 12, 24);
        int[] slab = SurfaceEditorPresetPreview.bottomSlabInsets(W, H, DENSITY, 12, false);
        int statusBottom = H - status[3];
        int terminalTop = terminal[1];
        int terminalBottom = H - terminal[3];
        int slabTop = slab[1];
        assertTrue("status must end above the terminal", statusBottom <= terminalTop);
        assertTrue("terminal must end above the slab", terminalBottom <= slabTop);
        assertTrue("every band must have real height", statusBottom > status[1]
            && terminalBottom > terminalTop && H - slab[3] > slabTop);
    }

    @Test
    public void roundedTerminalGainsItsMarginAndSquareOneStaysFullBleed() {
        int[] square = SurfaceEditorPresetPreview.terminalInsets(W, H, DENSITY, 12, 0);
        int[] rounded = SurfaceEditorPresetPreview.terminalInsets(W, H, DENSITY, 12, 24);
        assertEquals(0, square[0]);
        assertTrue(rounded[0] > 0);
        assertEquals(0f, SurfaceEditorPresetPreview.terminalRadiusPx(DENSITY, 0), 0.001f);
        assertTrue(SurfaceEditorPresetPreview.terminalRadiusPx(DENSITY, 24) > 0f);
    }
}
