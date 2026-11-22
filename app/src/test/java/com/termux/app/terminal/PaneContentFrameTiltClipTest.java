package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The press rig tips the frame itself: while it is tipped the frame is in its own layer with its
 * outline clip moved into its draw, and back on its outline with no layer once it is flat.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.TIRAMISU}, application = Application.class)
public class PaneContentFrameTiltClipTest {

    private static PaneContentFrame shapedFrame() {
        PaneContentFrame frame = new PaneContentFrame(RuntimeEnvironment.getApplication());
        frame.setPaneShape(60f, true);
        return frame;
    }

    @Test
    public void aTippedFrameDrawsInItsOwnLayerAndClipsItself() {
        PaneContentFrame frame = shapedFrame();
        assertTrue(frame.getClipToOutline());
        assertEquals(View.LAYER_TYPE_NONE, frame.getLayerType());

        frame.setRotationY(1.1f);
        assertTrue(frame.tiltLayered());
        assertFalse(frame.getClipToOutline());
        assertEquals(View.LAYER_TYPE_HARDWARE, frame.getLayerType());

        frame.setRotationX(-0.5f);
        frame.setRotationY(0f);
        assertTrue(frame.tiltLayered());

        frame.setRotationX(0f);
        assertFalse(frame.tiltLayered());
        assertTrue(frame.getClipToOutline());
        assertEquals(View.LAYER_TYPE_NONE, frame.getLayerType());
    }

    @Test
    public void aShapeChangeWhileTippedKeepsTheClipInTheDraw() {
        PaneContentFrame frame = shapedFrame();
        frame.setRotationY(1.1f);
        frame.setPaneShape(40f, true);
        assertFalse(frame.getClipToOutline());
        frame.setPaneShape(40f, false);
        assertFalse(frame.tiltLayered());
        assertFalse(frame.getClipToOutline());
        assertEquals(View.LAYER_TYPE_NONE, frame.getLayerType());
        frame.setPaneShape(40f, true);
        assertTrue(frame.tiltLayered());
        assertFalse(frame.getClipToOutline());
    }

    @Test
    public void aLayerSomeoneElsePutOnIsLeftOn() {
        PaneContentFrame frame = shapedFrame();
        frame.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        frame.setRotationY(1.1f);
        frame.setRotationY(0f);
        assertEquals(View.LAYER_TYPE_HARDWARE, frame.getLayerType());
        assertTrue(frame.getClipToOutline());
    }
}
