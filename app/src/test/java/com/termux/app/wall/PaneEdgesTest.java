package com.termux.app.wall;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/**
 * The pane whose bottom border a finger pressed ({@link PaneEdges#pick}): the bottom border's glow
 * lies under that pane only, so a split lights the air under the pane pressed and not the page's
 * whole width.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PaneEdgesTest {

    private static final float EPS = 0.001f;

    @Test
    public void aLonePaneIsTheEdge() {
        float[] out = new float[3];
        assertTrue(PaneEdges.pick(Collections.singletonList(new RectF(12f, 8f, 1068f, 1700f)),
            540f, out));
        assertArrayEquals(new float[]{12f, 1068f, 1700f}, out, EPS);
    }

    @Test
    public void sideBySideTheEdgeIsThePaneUnderTheFinger() {
        float[] out = new float[3];
        java.util.List<RectF> panes = Arrays.asList(new RectF(0f, 0f, 500f, 1700f),
            new RectF(520f, 0f, 1080f, 1700f));
        assertTrue(PaneEdges.pick(panes, 800f, out));
        assertArrayEquals(new float[]{520f, 1080f, 1700f}, out, EPS);
        assertTrue(PaneEdges.pick(panes, 100f, out));
        assertArrayEquals(new float[]{0f, 500f, 1700f}, out, EPS);
        // In the air between the two cards: the nearer one.
        assertTrue(PaneEdges.pick(panes, 515f, out));
        assertArrayEquals(new float[]{520f, 1080f, 1700f}, out, EPS);
    }

    @Test
    public void stackedTheEdgeIsTheBottomPane() {
        float[] out = new float[3];
        java.util.List<RectF> panes = Arrays.asList(new RectF(0f, 0f, 1080f, 840f),
            new RectF(0f, 860f, 1080f, 1700f));
        assertTrue(PaneEdges.pick(panes, 540f, out));
        assertArrayEquals(new float[]{0f, 1080f, 1700f}, out, EPS);
        // A stacked pair beside a tall pane: the lower of the pair under the finger.
        java.util.List<RectF> mixed = Arrays.asList(new RectF(0f, 0f, 500f, 840f),
            new RectF(0f, 860f, 500f, 1700f), new RectF(520f, 0f, 1080f, 1700f));
        assertTrue(PaneEdges.pick(mixed, 250f, out));
        assertArrayEquals(new float[]{0f, 500f, 1700f}, out, EPS);
    }

    @Test
    public void noPaneNoEdge() {
        float[] out = {1f, 2f, 3f};
        assertFalse(PaneEdges.pick(Collections.<RectF>emptyList(), 10f, out));
        assertFalse(PaneEdges.pick(Collections.singletonList(new RectF(5f, 5f, 5f, 90f)), 5f, out));
        assertArrayEquals("untouched", new float[]{1f, 2f, 3f}, out, EPS);
    }
}
