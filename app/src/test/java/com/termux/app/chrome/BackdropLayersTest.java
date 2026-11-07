package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.BackdropLayers.Layer;
import com.termux.app.chrome.BackdropLayers.Motion;
import com.termux.app.chrome.BackdropLayers.Move;
import com.termux.app.chrome.BackdropLayers.Place;
import com.termux.app.chrome.BackdropLayers.SeeThrough;

import org.junit.Test;

/**
 * Which backdrop layer a Live surface draws and which is stale, frame by frame, under each page
 * transition. Poses are built as the wall poses its pages for each transition style.
 */
public class BackdropLayersTest {

    private static final float W = 1080f;

    /** One pose: the 3x3 matrix (scale, skew, translation, perspective), then left, top, alpha. */
    private static float[] poses(float[]... each) {
        float[] out = new float[each.length * BackdropLayers.POSE_STRIDE];
        for (int i = 0; i < each.length; i++) {
            System.arraycopy(each[i], 0, out, i * BackdropLayers.POSE_STRIDE,
                BackdropLayers.POSE_STRIDE);
        }
        return out;
    }

    private static float[] pose(float tx, float scale, float skew, float persp, float left,
                                float alpha) {
        return new float[] {scale, skew, tx, -skew, scale, 0f, persp, 0f, 1f, left, 0f, alpha};
    }

    private static float[] flat(float tx, float left) {
        return pose(tx, 1f, 0f, 0f, left, 1f);
    }

    private static final float[] REST = poses(flat(0f, 0f), flat(W, W));

    private static int chrome(Move pages, boolean ready) {
        return BackdropLayers.plan(Place.CHROME, true, true, true, false, false, true, pages,
            ready, false, false);
    }

    private static int onPage(boolean pagesBeneath, Move pages) {
        return BackdropLayers.plan(Place.PAGE, true, true, true, false, false, pagesBeneath,
            pages, true, false, false);
    }

    @Test
    public void slideMovesEveryLayerAndRecordsNone() {
        float[] slid = poses(flat(-300f, 0f), flat(W - 300f, W));
        Move move = BackdropLayers.move(REST, 2, slid, 2);
        assertEquals(Move.SHIFT, move);
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.PAGES
            | BackdropLayers.LOCAL, chrome(move, true));
        // On a page under a slide no other page is beneath it: ground and local only.
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.LOCAL,
            onPage(false, Move.STILL));
    }

    @Test
    public void plankTiltTurnsThePagesUnderTheDockButNotGlassOnTheTiltedPage() {
        float[] tilted = poses(pose(-300f, 0.97f, 0f, 0.0004f, 0f, 1f), flat(W - 300f, W));
        Move move = BackdropLayers.move(REST, 2, tilted, 2);
        assertEquals(Move.TURN, move);
        assertEquals(BackdropLayers.STALE_PAGES, chrome(move, true));
        // A widget on the tilted plank: its page's views tilt with it, the ground is re-aimed.
        int plan = onPage(false, Move.STILL);
        assertTrue((plan & BackdropLayers.LAYERS) != 0);
        assertEquals(0, plan & (BackdropLayers.STALE_GROUND | BackdropLayers.STALE_LOCAL));
    }

    @Test
    public void sinkTurnsThePagesUnderTheDock() {
        float[] sunk = poses(pose(0f, 0.92f, 0f, 0f, 0f, 1f), flat(W, W));
        assertEquals(Move.TURN, BackdropLayers.move(REST, 2, sunk, 2));
        assertEquals(BackdropLayers.STALE_PAGES, chrome(Move.TURN, true));
        assertTrue((onPage(false, Move.STILL) & BackdropLayers.LAYERS) != 0);
    }

    @Test
    public void depthScalesTheIncomingPage() {
        float[] depth = poses(flat(-400f, 0f), pose(-W + 120f, 0.85f, 0f, 0f, W, 1f));
        assertEquals(Move.TURN, BackdropLayers.move(REST, 2, depth, 2));
        assertEquals(BackdropLayers.STALE_PAGES, chrome(Move.TURN, true));
    }

    @Test
    public void fadeMovesAlpha() {
        float[] faded = poses(pose(0f, 1f, 0f, 0f, 0f, 0.5f), pose(-W, 1f, 0f, 0f, W, 0.5f));
        assertEquals(Move.FADE, BackdropLayers.move(REST, 2, faded, 2));
        assertEquals(BackdropLayers.STALE_PAGES, chrome(Move.FADE, true));
    }

    @Test
    public void carouselTurnsThePages() {
        float[] carousel = poses(pose(-300f, 0.96f, 0.08f, 0f, 0f, 1f), flat(W - 300f, W));
        assertEquals(Move.TURN, BackdropLayers.move(REST, 2, carousel, 2));
        assertEquals(BackdropLayers.STALE_PAGES, chrome(Move.TURN, true));
    }

    @Test
    public void stackPutsAMovingPageUnderGlassOnTheTopPage() {
        // The page beneath slides against the top page the glass rides: stale.
        assertEquals(BackdropLayers.STALE_PAGES, onPage(true, Move.SHIFT));
        // A lower page that rides with it is folded into the local layer, never a pages layer.
        int plan = onPage(true, Move.STILL);
        assertTrue((plan & BackdropLayers.LAYERS) != 0);
        assertEquals(0, plan & BackdropLayers.PAGES);
    }

    @Test
    public void aSceneFrameRecordsOnlyTheGround() {
        int plan = BackdropLayers.plan(Place.PAGE, true, true, true, true, true, false,
            Move.STILL, true, false, false);
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.LOCAL
            | BackdropLayers.STALE_GROUND, plan);
        assertEquals(Motion.SCENE, BackdropLayers.motion(Layer.GROUND, Place.PAGE, true));
        assertEquals(Motion.PARALLAX, BackdropLayers.motion(Layer.GROUND, Place.PAGE, false));
        // No new scene frame: nothing is recorded again.
        assertEquals(0, BackdropLayers.plan(Place.PAGE, true, true, true, true, false, false,
            Move.STILL, true, false, false) & BackdropLayers.STALE_GROUND);
    }

    @Test
    public void aFloatOnThePinnedLayerSeesThePagesTravelAndItsLowerFloatsHold() {
        assertEquals(Motion.TRAVEL, BackdropLayers.motion(Layer.PAGES, Place.FLOAT, false));
        assertEquals(Motion.FIXED, BackdropLayers.motion(Layer.LOCAL, Place.FLOAT, false));
        int plan = BackdropLayers.plan(Place.FLOAT, true, true, true, false, false, true,
            Move.SHIFT, true, false, false);
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.PAGES
            | BackdropLayers.LOCAL, plan);
        // A float raised or a float beneath shown: only the local layer is recorded again.
        plan = BackdropLayers.plan(Place.FLOAT, true, true, true, false, false, true,
            Move.SHIFT, true, true, false);
        assertEquals(BackdropLayers.STALE_LOCAL, plan & (BackdropLayers.STALE_GROUND
            | BackdropLayers.STALE_PAGES | BackdropLayers.STALE_LOCAL));
    }

    @Test
    public void oneLayerLeavesTheLowerGlassOut() {
        assertFalse(BackdropLayers.keep(SeeThrough.ONE, true, false));
        assertTrue(BackdropLayers.keep(SeeThrough.ONE, false, false));
        // The pages beneath the dock hold widgets' glass, but they are what is directly beneath.
        assertTrue(BackdropLayers.keep(SeeThrough.ONE, true, true));
        assertTrue(BackdropLayers.keep(SeeThrough.ALL, true, false));
    }

    @Test
    public void bothEndsOfASwipeAreTheRestingPicture() {
        // The wall has left rest but nothing has moved yet: the first frame is the resting one.
        assertEquals(0, BackdropLayers.plan(Place.PAGE, true, false, true, false, false, false,
            Move.STILL, true, false, false));
        // Landed: the resting one again, recorded where the surface stands.
        assertEquals(0, BackdropLayers.plan(Place.CHROME, false, true, true, false, false, true,
            Move.SHIFT, true, false, false));
    }

    @Test
    public void aLayerNotAtHandFallsBackToTheOnePicture() {
        assertEquals(0, chrome(Move.SHIFT, false));
        assertEquals(0, BackdropLayers.plan(Place.PAGE, true, true, false, false, false, false,
            Move.STILL, true, false, false));
        assertEquals(0, BackdropLayers.plan(Place.PAGE, true, true, true, false, false, false,
            Move.STILL, true, false, true));
    }
}
