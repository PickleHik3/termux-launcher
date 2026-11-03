package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.PanelBackdropPolicy.Kind;

import org.junit.Test;

import java.util.Arrays;

public class PanelBackdropPolicyTest {

    private static final int S = 31;

    @Test public void liveOnlyWithTheSwitchInTheLaunchersWindowAndASlotFree() {
        assertEquals(Kind.LIVE, PanelBackdropPolicy.kind(S, true, true, true, true));
        assertEquals(Kind.SNAPSHOT, PanelBackdropPolicy.kind(S, true, false, true, true));
        // A popup menu is a window of its own: the recorder does not refresh it.
        assertEquals(Kind.SNAPSHOT, PanelBackdropPolicy.kind(S, true, true, false, true));
        assertEquals(Kind.SNAPSHOT, PanelBackdropPolicy.kind(S, true, true, true, false));
    }

    @Test public void theStoredFrameBelowAndroid12OrWithNoRecorder() {
        assertEquals(Kind.STORED, PanelBackdropPolicy.kind(30, true, true, true, true));
        assertEquals(Kind.STORED, PanelBackdropPolicy.kind(30, true, false, true, true));
        assertEquals(Kind.STORED, PanelBackdropPolicy.kind(S, false, true, true, true));
        assertFalse(PanelBackdropPolicy.canRecord(30));
        assertTrue(PanelBackdropPolicy.canRecord(S));
    }

    @Test public void aSnapshotOnOpenAndOnSettleAndNeverWhileMoving() {
        assertTrue(PanelBackdropPolicy.snapshotOnOpen(Kind.SNAPSHOT));
        assertFalse(PanelBackdropPolicy.snapshotOnOpen(Kind.LIVE));
        assertFalse(PanelBackdropPolicy.snapshotOnOpen(Kind.STORED));
        // Comes to rest open: one more.
        assertTrue(PanelBackdropPolicy.snapshotOnMotion(Kind.SNAPSHOT, true, true, false));
        // Starts moving, keeps moving, stays at rest: frozen.
        assertFalse(PanelBackdropPolicy.snapshotOnMotion(Kind.SNAPSHOT, true, false, true));
        assertFalse(PanelBackdropPolicy.snapshotOnMotion(Kind.SNAPSHOT, true, true, true));
        assertFalse(PanelBackdropPolicy.snapshotOnMotion(Kind.SNAPSHOT, true, false, false));
        // Shut, or not a Snapshot: never.
        assertFalse(PanelBackdropPolicy.snapshotOnMotion(Kind.SNAPSHOT, false, true, false));
        assertFalse(PanelBackdropPolicy.snapshotOnMotion(Kind.LIVE, true, true, false));
        assertFalse(PanelBackdropPolicy.snapshotOnMotion(Kind.STORED, true, true, false));
    }

    /**
     * The old RealtimeBlurView ran a full-scale RenderEffect at its radius R (sigma 0.57735 R + 0.5)
     * over a quarter-scale capture scaled back up; the panel's quarter-scale effect at r has
     * sigma 4 (0.57735 r + 0.5) at full scale. The two match.
     */
    @Test public void aPanelKeepsItsOldBlurStrength() {
        for (float radius : new float[] {27.5f, 49.5f, 160f}) {
            float r = LivePicture.panelQuarterRadius(radius);
            float panelSigma = LivePicture.SCALE * (0.57735f * r + 0.5f);
            assertEquals(0.57735f * radius + 0.5f, panelSigma, 1e-3f);
        }
        assertEquals(0f, LivePicture.panelQuarterRadius(0f), 0f);
        // Below a few px the quarter node needs no blur at all.
        assertEquals(0f, LivePicture.panelQuarterRadius(2f), 0f);
    }

    @Test public void aWindowAboveSeesEveryChildThatDraws() {
        int[] preorder = {0, 1, 2, 3};
        float[] z = {0f, 5f, 0f, 0f};
        boolean[] drawn = {true, true, false, true};
        int[] order = new int[4];
        int[] out = new int[4];
        int n = LiveBackdrop.beneath(4, preorder, z, drawn, LiveBackdrop.EVERY_CHILD, order, out);
        // Bottom first; the raised child last; the hidden one never.
        assertArrayEquals(new int[] {0, 3, 1}, Arrays.copyOf(out, n));
    }
}
