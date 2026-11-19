package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Home's share of the stack across a crossing: 1 at Home's rest, 0 at the Workspace's, the
 * material curve between ({@link StackScene#materialShare}), so the Peek card blends with the
 * stack's look and is exactly the arriving rest's on the landing frame.
 */
public class StackSceneHomeShareTest {

    @Test public void withinOneSideNothingMoves() {
        for (float p : new float[] {0f, 0.3f, 0.5f, 1f}) {
            assertEquals(1f, StackScene.homeShare(true, true, p), 0f);
            assertEquals(0f, StackScene.homeShare(false, false, p), 0f);
        }
    }

    @Test public void homeToWorkspaceRunsFromOneToZero() {
        assertEquals(1f, StackScene.homeShare(true, false, 0f), 0f);
        assertEquals(0f, StackScene.homeShare(true, false, 1f), 0f);
    }

    @Test public void workspaceToHomeRunsFromZeroToOne() {
        assertEquals(0f, StackScene.homeShare(false, true, 0f), 0f);
        assertEquals(1f, StackScene.homeShare(false, true, 1f), 0f);
    }

    @Test public void followsTheMaterialCurve() {
        for (int i = 0; i <= 20; i++) {
            float p = i / 20f;
            float w = StackScene.materialShare(p);
            assertEquals(w, StackScene.homeShare(false, true, p), 1e-6f);
            assertEquals(1f - w, StackScene.homeShare(true, false, p), 1e-6f);
        }
    }

    @Test public void neverStepsBack() {
        float last = -1f;
        for (int i = 0; i <= 100; i++) {
            float share = StackScene.homeShare(false, true, i / 100f);
            assertTrue("step back at " + i, share >= last);
            last = share;
        }
    }
}
