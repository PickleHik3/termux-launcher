package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/**
 * The live route's sharp edge binds the unblurred record node as its effect's content. An effect
 * whose input reaches its own node overflows the RenderThread's stack (the exp/glass-motion crash),
 * so the sharp picture may reach only what the frosted picture already reaches, never the
 * holder's own nodes, and the record node never holds the glass surface or anything above it.
 */
public class LiveSharpEdgeReachTest {

    @Test
    public void theSharpPictureReachesOnlyWhatTheFrostAlreadyDoesAndNeverTheHolder() {
        for (boolean layers : new boolean[] {false, true}) {
            for (boolean pages : new boolean[] {false, true}) {
                for (boolean local : new boolean[] {false, true}) {
                    int frost = LivePicture.frostReach(layers, pages);
                    int sharp = LivePicture.sharpReach(layers, local);
                    String at = "layers=" + layers + " pages=" + pages + " local=" + local;
                    assertEquals(at, 0, sharp & ~frost);
                    assertEquals(at, 0, sharp & LivePicture.NODE_HOLDER);
                    assertEquals(at, 0, frost & LivePicture.NODE_HOLDER);
                }
            }
        }
    }

    @Test
    public void theSharpPictureIsTheRecordAtRestAndNothingWhileTheWallMoves() {
        assertEquals(LivePicture.NODE_RECORD, LivePicture.sharpReach(false, false));
        // The layers shown, or the record holding the local layer only: the edge keeps the frost.
        assertEquals(0, LivePicture.sharpReach(true, false));
        assertEquals(0, LivePicture.sharpReach(false, true));
        assertEquals(0, LivePicture.sharpReach(true, true));
    }

    @Test
    public void theRecordNeverHoldsTheSurfaceOrAnythingDrawnAfterIt() {
        // LiveBackdrop.beneath picks, at every level of the surface's path, the siblings the
        // record node draws: whatever the drawing order and the Z, never the path child (the
        // surface, or the ancestor holding it) and never one drawn after it.
        Random random = new Random(315);
        for (int round = 0; round < 500; round++) {
            int n = 1 + random.nextInt(8);
            int[] preorder = new int[n];
            for (int i = 0; i < n; i++) preorder[i] = i;
            for (int i = n - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = preorder[i];
                preorder[i] = preorder[j];
                preorder[j] = t;
            }
            float[] z = new float[n];
            boolean[] drawn = new boolean[n];
            for (int i = 0; i < n; i++) {
                z[i] = random.nextInt(3);
                drawn[i] = random.nextInt(5) != 0;
            }
            int path = random.nextInt(n);
            int[] out = new int[n];
            int written = LiveBackdrop.beneath(n, preorder, z, drawn, path, new int[n], out);
            int[] position = drawingPositions(preorder, z);
            for (int i = 0; i < written; i++) {
                assertTrue("round " + round + ": the surface itself", out[i] != path);
                assertTrue("round " + round + ": a sibling drawn after the surface",
                    position[out[i]] < position[path]);
            }
        }
    }

    /** Where each child draws: the drawing order, then a stable sort by Z, as ViewGroup draws. */
    private static int[] drawingPositions(int[] preorder, float[] z) {
        int n = preorder.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = preorder[i];
        java.util.Arrays.sort(order, (a, b) -> Float.compare(z[a], z[b]));
        int[] position = new int[n];
        for (int i = 0; i < n; i++) position[order[i]] = i;
        return position;
    }
}
