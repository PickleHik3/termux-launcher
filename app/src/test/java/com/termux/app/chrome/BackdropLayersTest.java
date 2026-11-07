package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.BackdropLayers.Holder;
import com.termux.app.chrome.BackdropLayers.Layer;
import com.termux.app.chrome.BackdropLayers.Motion;
import com.termux.app.chrome.BackdropLayers.Move;
import com.termux.app.chrome.BackdropLayers.Place;
import com.termux.app.chrome.BackdropLayers.SeeThrough;
import com.termux.app.chrome.BackdropLayers.Tier;

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
        // Fade: both pages drift by one shift while their alphas move. The frozen layer holds
        // each page at full opacity and composites it at its alpha now, so nothing is stale.
        float[] faded = poses(pose(-90f, 1f, 0f, 0f, 0f, 0.5f), pose(W - 90f, 1f, 0f, 0f, W, 0.5f));
        assertEquals(Move.FADE, BackdropLayers.move(REST, 2, faded, 2));
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.PAGES
            | BackdropLayers.LOCAL, chrome(Move.FADE, true));
        // A fade over a turn is a turn.
        float[] turned = poses(pose(0f, 0.9f, 0f, 0f, 0f, 0.5f), pose(W, 1f, 0f, 0f, W, 0.5f));
        assertEquals(Move.TURN, BackdropLayers.move(REST, 2, turned, 2));
    }

    @Test
    public void aPageFadingUnderChromeKeepsTheFrozenLayer() {
        // Measured on pong: Home2's widget pane fades (0.84, 0.71, 0.67, 0.59) as the pages
        // slide under the accessory bar's glass, which fell back to the one picture every frame.
        float[] recorded = poses(pose(0f, 1f, 0f, 0f, 0f, 0.84f), flat(W, W));
        float[] alphas = {0.71f, 0.67f, 0.59f};
        float[] shifts = {-40f, -75f, -130f};
        for (int f = 0; f < alphas.length; f++) {
            float[] now = poses(pose(shifts[f], 1f, 0f, 0f, 0f, alphas[f]),
                flat(W + shifts[f], W));
            assertEquals(Move.FADE, BackdropLayers.move(recorded, 2, now, 2));
            // One travel for the frozen layer; the strict shift still tells a fade apart.
            assertEquals(shifts[f], BackdropLayers.shift(recorded, 2, now, 2, true), 1e-3f);
            assertTrue(Float.isNaN(BackdropLayers.shift(recorded, 2, now, 2)));
            assertFalse(BackdropLayers.needsRecord(BackdropLayers.Mode.FROZEN,
                BackdropLayers.Mode.FROZEN, true, BackdropLayers.shift(recorded, 2, now, 2, true),
                true));
            assertEquals(alphas[f], BackdropLayers.pageAlpha(now, 0), 0f);
            assertEquals(1f, BackdropLayers.pageAlpha(now, 1), 0f);
            // The trace's fallback (pagesMove=TURN, pagesReady=false) is now a frozen layer.
            assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.PAGES
                | BackdropLayers.LOCAL, BackdropLayers.plan(Place.CHROME, true, true, true, true,
                false, true, Move.SHIFT, true, false, false));
        }
    }

    @Test
    public void aStripWithSeamsCrossesChromeThatStays() {
        // Pages with different grounds: the seam travels under the dock, so no layer holds it.
        assertEquals(Holder.FIXED, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            false, true, true, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            false, true, false, false, false));
        // Under a surface on a page the seam rides with it.
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            false, true, true, true, false));
    }

    @Test
    public void slotsShareThePadBudget() {
        int one = BackdropLayers.pad(0L, 1200, 300, true, 1080);
        int three = BackdropLayers.pad(0L, 1200, 300, true, 1080, BackdropLayers.frozenSlots(2));
        assertTrue(three <= one);
        assertTrue(BackdropLayers.fits(0L, 3 * (1200 + 2 * three), 300));
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
    public void oneLayerTurnsTheLowerGlassToTheStoredFrame() {
        // The back float under the front one: the stored frame, so the front float sees the back
        // one, frosted over the wallpaper, but not the widgets through both.
        assertTrue(BackdropLayers.drawsStored(SeeThrough.ONE, true));
        // The topmost glass records as always.
        assertFalse(BackdropLayers.drawsStored(SeeThrough.ONE, false));
        assertFalse(BackdropLayers.drawsStored(SeeThrough.ALL, true));
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

    @Test
    public void theGroundStripRidesWithASurfaceOnAPage() {
        // Measured on pong (Home2 <-> Workspace, a live scene): the terminal pane's glass found the
        // ground strip beneath the wall in its band and fell back to the one picture. The strip's
        // band under a page is that page's own ground and moves as the page does.
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            false, true, true, false));
        // Chrome that stands still where the surface sweeps has no layer of its own.
        assertEquals(Holder.FIXED, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            false, false, true, false));
        assertEquals(Holder.NONE, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            false, false, false, false));
        // The wallpaper and the scene are the ground, wherever they stand.
        assertEquals(Holder.GROUND, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, true,
            false, false, true, false));
        assertEquals(Holder.GROUND, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, true,
            false, false, false, false));
    }

    @Test
    public void viewsBeneathFindTheirLayer() {
        // On a page: its own views ride with it; a lower page under it is a pages layer.
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.PAGE, Tier.ON_PAGE, false, false,
            false, false, false));
        assertEquals(Holder.PAGES, BackdropLayers.holder(Place.PAGE, Tier.PAGE, false, false,
            false, false, true));
        assertEquals(Holder.NONE, BackdropLayers.holder(Place.PAGE, Tier.PAGE, false, false,
            false, false, false));
        // Off the wall: the view holding the wall is the pages; everything else, the strip too,
        // is the local layer, as before.
        assertEquals(Holder.PAGES, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            true, false, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            false, true, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.FLOAT, Tier.BENEATH_WALL, false,
            false, false, false, false));
    }

    @Test
    public void thePaneOverALiveSceneDrawsItsLayersOnceTheStripRides() {
        // The pong fallback: place=PAGE, groundReady, sceneLive, pagesBeneath=false,
        // pagesMove=TURN, pagesReady=false. With fixedBeneath it was the one picture.
        assertEquals(0, BackdropLayers.plan(Place.PAGE, true, true, true, true, false, false,
            Move.TURN, false, false, true));
        // The strip sorted into the local layer leaves nothing fixed: the layers draw.
        boolean fixed = BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false, false, true,
            true, false) == Holder.FIXED;
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.LOCAL,
            BackdropLayers.plan(Place.PAGE, true, true, true, true, false, false, Move.TURN, false,
                false, fixed));
        // Its first layered frame records the local layer, and a new scene frame the ground.
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.GROUND | BackdropLayers.LOCAL
            | BackdropLayers.STALE_GROUND | BackdropLayers.STALE_LOCAL,
            BackdropLayers.plan(Place.PAGE, true, true, true, true, true, false, Move.TURN, false,
                true, fixed));
    }
}
