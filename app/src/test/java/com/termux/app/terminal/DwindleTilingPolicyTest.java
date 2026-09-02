package com.termux.app.terminal;

import android.graphics.RectF;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DwindleTilingPolicyTest {

    @Test
    public void splitOrientation_followsTheLongerSideAndStacksSquaresAndUnmeasuredPanes() {
        assertEquals(LinearLayout.HORIZONTAL, DwindleTilingPolicy.splitOrientationFor(1000, 600));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.splitOrientationFor(600, 1000));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.splitOrientationFor(500, 500));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.splitOrientationFor(0, 0));
    }

    @Test
    public void dropSide_usesTheTargetsAxisAndTheHalfUnderTheFinger() {
        RectF wide = new RectF(0, 0, 1000, 400);
        assertEquals(DwindleTilingPolicy.SIDE_LEFT, DwindleTilingPolicy.dropSideFor(wide, 100, 390));
        assertEquals(DwindleTilingPolicy.SIDE_RIGHT, DwindleTilingPolicy.dropSideFor(wide, 900, 10));
        // Overshooting the edge still lands on that edge's half.
        assertEquals(DwindleTilingPolicy.SIDE_RIGHT, DwindleTilingPolicy.dropSideFor(wide, 1200, 200));

        RectF tall = new RectF(0, 0, 400, 1000);
        assertEquals(DwindleTilingPolicy.SIDE_TOP, DwindleTilingPolicy.dropSideFor(tall, 390, 100));
        assertEquals(DwindleTilingPolicy.SIDE_BOTTOM, DwindleTilingPolicy.dropSideFor(tall, 10, 900));
    }

    @Test
    public void side_mapsToOrientationOrderAndHalf() {
        assertEquals(LinearLayout.HORIZONTAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_LEFT));
        assertEquals(LinearLayout.HORIZONTAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_RIGHT));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_TOP));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_BOTTOM));
        assertTrue(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_LEFT));
        assertTrue(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_TOP));
        assertFalse(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_RIGHT));
        assertFalse(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_BOTTOM));

        RectF target = new RectF(100, 200, 500, 1000);
        RectF out = new RectF();
        assertEquals(new RectF(100, 200, 300, 1000),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_LEFT, out));
        assertEquals(new RectF(300, 200, 500, 1000),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_RIGHT, out));
        assertEquals(new RectF(100, 200, 500, 600),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_TOP, out));
        assertEquals(new RectF(100, 600, 500, 1000),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_BOTTOM, out));
    }

    @Test
    public void spiralOrientations_halveTheLongerSideEachStep() {
        // Portrait: stack, then the 600x500 half is wider so it splits side by side, then the
        // 300x500 quarter stacks again.
        assertArrayEquals(new int[]{LinearLayout.VERTICAL, LinearLayout.HORIZONTAL, LinearLayout.VERTICAL},
            DwindleTilingPolicy.spiralOrientations(4, 600, 1000));
        // Landscape starts side by side.
        assertArrayEquals(new int[]{LinearLayout.HORIZONTAL, LinearLayout.VERTICAL},
            DwindleTilingPolicy.spiralOrientations(3, 1000, 600));
        assertArrayEquals(new int[0], DwindleTilingPolicy.spiralOrientations(1, 1000, 600));
        assertArrayEquals(new int[0], DwindleTilingPolicy.spiralOrientations(0, 1000, 600));
    }
}
