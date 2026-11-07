package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.BackdropLayers.Mode;

import org.junit.Test;

/**
 * When live glass splits its picture into the ground and a page layer, and when a swipe's page
 * layer is only moved rather than recorded again ({@link BackdropLayers}).
 */
public class BackdropLayersPageTest {

    private static final int S = BackdropLayers.POSE_STRIDE;

    @Test
    public void atRestWithoutASceneNothingChanges() {
        assertEquals(Mode.LIVE, BackdropLayers.mode(true, false, false));
    }

    @Test
    public void aSwipeFreezesThePagesAndASceneAtRestReferencesThem() {
        assertEquals(Mode.FROZEN, BackdropLayers.mode(true, true, false));
        assertEquals(Mode.FROZEN, BackdropLayers.mode(true, true, true));
        assertEquals(Mode.REFERENCE, BackdropLayers.mode(true, false, true));
    }

    @Test
    public void aSurfaceThatCannotSplitKeepsOneLayer() {
        assertEquals(Mode.LIVE, BackdropLayers.mode(false, true, true));
    }

    @Test
    public void pagesSlidingTogetherShareOneShift() {
        float[] recorded = poses(0f, 1080f);
        float[] now = poses(-240f, 840f);
        assertEquals(-240f, BackdropLayers.shift(recorded, 2, now, 2), 1e-4f);
    }

    @Test
    public void pagesThatMoveApartOrTurnHaveNoShift() {
        float[] recorded = poses(0f, 1080f);
        assertTrue(Float.isNaN(BackdropLayers.shift(recorded, 2, poses(-240f, 900f), 2)));
        float[] turned = poses(-240f, 840f);
        turned[S] = 0.94f; // the second page's scale moved: a turn or a sink
        assertTrue(Float.isNaN(BackdropLayers.shift(recorded, 2, turned, 2)));
        float[] faded = poses(-240f, 840f);
        faded[BackdropLayers.POSE_ALPHA] = 0.5f;
        assertTrue(Float.isNaN(BackdropLayers.shift(recorded, 2, faded, 2)));
    }

    @Test
    public void aDifferentNumberOfPagesHasNoShift() {
        assertTrue(Float.isNaN(BackdropLayers.shift(poses(0f), 1, poses(0f, 1080f), 2)));
        assertTrue(Float.isNaN(BackdropLayers.shift(poses(0f), -1, poses(0f), 1)));
        assertEquals(0f, BackdropLayers.shift(new float[0], 0, new float[0], 0), 0f);
    }

    @Test
    public void aFrozenLayerIsMovedNotRecordedWhileThePagesSlide() {
        assertFalse(BackdropLayers.needsRecord(Mode.FROZEN, Mode.FROZEN, true, -240f, true));
        assertEquals(-240f, BackdropLayers.aim(Mode.FROZEN, -240f), 0f);
    }

    @Test
    public void theLayerIsRecordedAgainWhenAnythingElseMoves() {
        assertTrue(BackdropLayers.needsRecord(Mode.FROZEN, Mode.REFERENCE, true, 0f, true));
        assertTrue(BackdropLayers.needsRecord(Mode.FROZEN, Mode.FROZEN, false, 0f, true));
        assertTrue(BackdropLayers.needsRecord(Mode.FROZEN, Mode.FROZEN, true, Float.NaN, true));
        assertTrue(BackdropLayers.needsRecord(Mode.FROZEN, Mode.FROZEN, true, 10f, false));
        // A reference layer draws the pages where they are: pages that moved at rest re-record.
        assertTrue(BackdropLayers.needsRecord(Mode.REFERENCE, Mode.REFERENCE, true, 3f, true));
        assertFalse(BackdropLayers.needsRecord(Mode.REFERENCE, Mode.REFERENCE, true, 0f, true));
        assertEquals(0f, BackdropLayers.aim(Mode.REFERENCE, 3f), 0f);
        assertFalse(BackdropLayers.needsRecord(Mode.LIVE, Mode.FROZEN, false, Float.NaN, false));
    }

    @Test
    public void aLayerCoversASurfaceUntilThePagesCarryItPastItsReach() {
        // A dock 1080 wide; the layer reaches a width either way.
        assertTrue(BackdropLayers.covers(-1080f, 2000f, 2160f, 2300f,
            0f, 2000f, 1080f, 2300f, -1080f));
        assertTrue(BackdropLayers.covers(-1080f, 2000f, 2160f, 2300f,
            0f, 2000f, 1080f, 2300f, 1080f));
        assertFalse(BackdropLayers.covers(-1080f, 2000f, 2160f, 2300f,
            0f, 2000f, 1080f, 2300f, -1200f));
        // The dock lifted past the layer's top.
        assertFalse(BackdropLayers.covers(-1080f, 2000f, 2160f, 2300f,
            0f, 1900f, 1080f, 2200f, 0f));
    }

    @Test
    public void thePadIsTheWidestThatFitsTheBudget() {
        long base = LiveBackdrop.layerBytes(1080, 300);
        int pad = BackdropLayers.pad(base, 1080, 300, true, 1080);
        assertEquals(1080, pad);
        assertTrue(BackdropLayers.fits(base, 1080 + 2 * pad, 300));
        // A layer that does not fit even unpadded is no layer.
        long screen = LiveBackdrop.layerBytes(1440, 3200);
        assertEquals(-1, BackdropLayers.pad(screen, 1440, 3200, true, 1440));
        // Near the budget the pad stops where the next pixel would not fit.
        long half = LiveBackdrop.layerBytes(1440, 1600);
        int tight = BackdropLayers.pad(half, 1440, 1000, false, 1600);
        assertTrue(BackdropLayers.fits(half, 1440, 1000 + 2 * tight));
        assertFalse(BackdropLayers.fits(half, 1440, 1000 + 2 * (tight + 4)));
    }

    @Test
    public void theWallOfPagesIsTheWallLiveGlassSplitsAround() throws Exception {
        Class<?> wall = Class.forName("com.termux.app.wall.PaneWallLayout", false,
            getClass().getClassLoader());
        assertTrue(LiveBackdrop.Wall.class.isAssignableFrom(wall));
    }

    /** One pose per x: an unscaled page at that translation, left and top 0, opaque. */
    private static float[] poses(float... translationX) {
        float[] out = new float[translationX.length * S];
        for (int i = 0; i < translationX.length; i++) {
            int at = i * S;
            out[at] = 1f;
            out[at + 4] = 1f;
            out[at + 8] = 1f;
            out[at + BackdropLayers.POSE_TRANS_X] = translationX[i];
            out[at + BackdropLayers.POSE_ALPHA] = 1f;
        }
        return out;
    }
}
