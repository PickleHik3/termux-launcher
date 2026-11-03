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

    @Test public void belowAndroid12EveryPanelIsASoftwareSnapshotAndNeverLive() {
        for (int sdk = 26; sdk < S; sdk++) {
            assertEquals(Kind.SNAPSHOT, PanelBackdropPolicy.kind(sdk, true, true, true, true));
            assertEquals(Kind.SNAPSHOT, PanelBackdropPolicy.kind(sdk, true, false, true, true));
            assertEquals(Kind.SNAPSHOT, PanelBackdropPolicy.kind(sdk, true, true, false, true));
            assertTrue(PanelBackdropPolicy.canRecord(sdk));
            assertFalse(PanelBackdropPolicy.onGpu(sdk));
        }
        assertTrue(PanelBackdropPolicy.onGpu(S));
        assertTrue(PanelBackdropPolicy.canRecord(S));
    }

    @Test public void theStoredFrameWithNoRecorderOrBeforeAndroid8() {
        assertEquals(Kind.STORED, PanelBackdropPolicy.kind(S, false, true, true, true));
        assertEquals(Kind.STORED, PanelBackdropPolicy.kind(30, false, false, true, true));
        assertEquals(Kind.STORED, PanelBackdropPolicy.kind(25, true, false, true, true));
        assertFalse(PanelBackdropPolicy.canRecord(25));
    }

    @Test public void aFailedSoftwareBlurFallsBackToTheStoredFrameOnlyBelowAndroid12() {
        assertTrue(PanelBackdropPolicy.pictured(28, false));
        assertFalse(PanelBackdropPolicy.pictured(28, true));
        assertFalse(PanelBackdropPolicy.pictured(30, true));
        // The GPU path never uses the software blur.
        assertTrue(PanelBackdropPolicy.pictured(S, true));
        assertFalse(PanelBackdropPolicy.pictured(25, false));
    }

    /**
     * The software Snapshot blurs as much as the GPU one: RenderScript's sigma 0.4 r + 0.6 on a
     * 1/scale bitmap equals the GPU's full-scale sigma 0.57735 R + 0.5 over scale.
     */
    @Test public void aSoftwareSnapshotBlursAsMuchAsTheGpuOne() {
        for (float radius : new float[] {26.25f, 47.25f, 80f}) {
            int scale = LivePicture.softwareScale(radius, 1134, 2466);
            float r = LivePicture.softwareScriptRadius(radius, scale);
            assertTrue(r > 0f && r <= LivePicture.STORED_SCRIPT_CAP_PX);
            assertEquals(0.57735f * radius + 0.5f, scale * (0.4f * r + 0.6f), 1e-3f);
        }
        // The drawer and the palette at 2.625x stay at the GPU path's quarter scale.
        assertEquals(LivePicture.SCALE, LivePicture.softwareScale(26.25f, 1134, 2466));
        assertEquals(LivePicture.SCALE, LivePicture.softwareScale(47.25f, 1134, 2466));
        // Past RenderScript's 25 px cap the bitmap gets coarser instead.
        assertTrue(LivePicture.softwareScale(300f, 400, 400) > LivePicture.SCALE);
        assertEquals(0f, LivePicture.softwareScriptRadius(0f, LivePicture.SCALE), 0f);
    }

    /** One open panel's software Snapshot: its bitmap plus RenderScript's two buffers, bounded. */
    @Test public void aSoftwareSnapshotStaysInsideItsBudget() {
        assertEquals(3 * LiveBackdrop.MAX_LAYER_BYTES, LivePicture.SOFTWARE_SNAPSHOT_BUDGET_BYTES);
        // A 1080 x 2412 drawer at 2.625x: a 27 px margin each side, 284 x 617 at a quarter.
        assertEquals(284L * 617L * 4L, LivePicture.softwareBitmapBytes(1134, 2466, LivePicture.SCALE));
        // A screen past the budget screen is drawn coarser rather than over budget.
        int scale = LivePicture.softwareScale(26.25f, 2000, 4000);
        assertTrue(LivePicture.softwareBitmapBytes(2000, 4000, scale)
            <= LivePicture.SOFTWARE_SNAPSHOT_MAX_BITMAP_BYTES);
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
