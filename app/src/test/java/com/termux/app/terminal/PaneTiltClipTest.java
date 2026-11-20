package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A pane frame clips to its shape exactly once, in a space the GPU clips without a mask: through
 * its outline at rest, in its own layer while its press rig tips it.
 */
public class PaneTiltClipTest {

    @Test
    public void aFlatFrameClipsThroughItsOutlineWithNoLayer() {
        assertFalse(PaneTiltClip.layered(true, 0f, 0f, 0f));
        assertTrue(PaneTiltClip.clipsByOutline(true, false));
        assertFalse(PaneTiltClip.clipsInDraw(true, false));
    }

    @Test
    public void aTippedFrameClipsInItsOwnLayerAndNotThroughItsOutline() {
        for (float[] turn : new float[][] {{0f, 1.1f, 0f}, {0f, 0f, -0.4f}, {2f, 0f, 0f}}) {
            boolean layered = PaneTiltClip.layered(true, turn[0], turn[1], turn[2]);
            assertTrue(layered);
            assertFalse(PaneTiltClip.clipsByOutline(true, layered));
            assertTrue(PaneTiltClip.clipsInDraw(true, layered));
        }
    }

    @Test
    public void aFrameThatClipsToNoShapeTakesNoLayerAndNoClip() {
        boolean layered = PaneTiltClip.layered(false, 0f, 1.1f, 0.5f);
        assertFalse(layered);
        assertFalse(PaneTiltClip.clipsByOutline(false, layered));
        assertFalse(PaneTiltClip.clipsInDraw(false, layered));
    }

    @Test
    public void exactlyOneOfTheTwoClipsAppliesToAShapedFrame() {
        for (boolean layered : new boolean[] {false, true})
            assertTrue(PaneTiltClip.clipsByOutline(true, layered)
                ^ PaneTiltClip.clipsInDraw(true, layered));
    }
}
