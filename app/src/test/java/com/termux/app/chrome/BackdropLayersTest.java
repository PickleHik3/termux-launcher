package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.BackdropLayers.Holder;
import com.termux.app.chrome.BackdropLayers.Layer;
import com.termux.app.chrome.BackdropLayers.Mode;
import com.termux.app.chrome.BackdropLayers.Motion;
import com.termux.app.chrome.BackdropLayers.Move;
import com.termux.app.chrome.BackdropLayers.Place;
import com.termux.app.chrome.BackdropLayers.Tier;

import org.junit.Test;

/**
 * Which backdrop layers a Live surface draws and which is stale, at rest and frame by frame under
 * each page transition (ADR 0029 section 3: the same layers at rest and moving). Poses are built as the wall poses its pages for each transition style.
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

    private static final int LAYERS_GROUND_LOCAL = BackdropLayers.LAYERS | BackdropLayers.GROUND
        | BackdropLayers.LOCAL;
    private static final int WITH_PAGES = LAYERS_GROUND_LOCAL | BackdropLayers.PAGES;

    /** The dock's plan for one frame of a swipe whose pages moved by {@code pages}. */
    private static int chrome(Move pages, boolean frozenReady) {
        boolean held = BackdropLayers.holdsPages(Place.CHROME, Mode.FROZEN, pages, frozenReady,
            false);
        return BackdropLayers.plan(true, false, false, held, false, false, false);
    }

    @Test
    public void restAndEveryFrameOfASwipeDrawTheSameLayers() {
        // At rest: the ground and the local layer, which holds the pages as they are.
        int rest = BackdropLayers.plan(true, false, false, false, false, false, false);
        assertEquals(LAYERS_GROUND_LOCAL, rest);
        assertFalse(BackdropLayers.holdsPages(Place.CHROME, Mode.LIVE, Move.STILL, true, false));
        // The first frame of a swipe, before the frozen pages land: the same composition.
        assertEquals(rest, chrome(Move.STILL, false));
        // Landed: the pages go back into the local layer; the same layers again.
        assertEquals(rest, BackdropLayers.plan(true, false, false, false, false, false, true));
        // Nothing at rest is recorded again on its own: no frame, no blur.
        assertEquals(0, rest & (BackdropLayers.STALE_GROUND | BackdropLayers.STALE_PAGES
            | BackdropLayers.STALE_LOCAL));
    }

    @Test
    public void slideMovesEveryLayerAndRecordsNone() {
        float[] slid = poses(flat(-300f, 0f), flat(W - 300f, W));
        Move move = BackdropLayers.move(REST, 2, slid, 2);
        assertEquals(Move.SHIFT, move);
        assertEquals(WITH_PAGES, chrome(move, true));
        // On a page under a slide no other page is beneath it: ground and local only.
        assertFalse(BackdropLayers.holdsPages(Place.PAGE, Mode.FROZEN, Move.SHIFT, true, false));
        assertEquals(LAYERS_GROUND_LOCAL,
            BackdropLayers.plan(true, false, false, false, false, false, true));
    }

    @Test
    public void plankTiltKeepsThePagesUnderTheDockInTheLocalLayer() {
        float[] tilted = poses(pose(-300f, 0.97f, 0f, 0.0004f, 0f, 1f), flat(W - 300f, W));
        Move move = BackdropLayers.move(REST, 2, tilted, 2);
        assertEquals(Move.TURN, move);
        // No frozen picture can follow a turn: the pages are drawn as they are, in the local layer.
        assertEquals(LAYERS_GROUND_LOCAL, chrome(move, true));
        // A widget on the tilted plank: its page's views tilt with it, the ground is re-aimed.
        int plan = BackdropLayers.plan(true, false, false, false, false, false, true);
        assertEquals(0, plan & (BackdropLayers.STALE_GROUND | BackdropLayers.STALE_LOCAL));
    }

    @Test
    public void sinkDepthAndCarouselTurnThePages() {
        float[] sunk = poses(pose(0f, 0.92f, 0f, 0f, 0f, 1f), flat(W, W));
        float[] depth = poses(flat(-400f, 0f), pose(-W + 120f, 0.85f, 0f, 0f, W, 1f));
        float[] carousel = poses(pose(-300f, 0.96f, 0.08f, 0f, 0f, 1f), flat(W - 300f, W));
        for (float[] now : new float[][] {sunk, depth, carousel}) {
            assertEquals(Move.TURN, BackdropLayers.move(REST, 2, now, 2));
            assertEquals(LAYERS_GROUND_LOCAL, chrome(Move.TURN, true));
        }
    }

    @Test
    public void fadeMovesAlpha() {
        // Fade: both pages drift by one shift while their alphas move. The frozen layer holds
        // each page at full opacity and composites it at its alpha now, so nothing is stale.
        float[] faded = poses(pose(-90f, 1f, 0f, 0f, 0f, 0.5f), pose(W - 90f, 1f, 0f, 0f, W, 0.5f));
        assertEquals(Move.FADE, BackdropLayers.move(REST, 2, faded, 2));
        assertEquals(WITH_PAGES, chrome(Move.FADE, true));
        // A fade over a turn is a turn.
        float[] turned = poses(pose(0f, 0.9f, 0f, 0f, 0f, 0.5f), pose(W, 1f, 0f, 0f, W, 0.5f));
        assertEquals(Move.TURN, BackdropLayers.move(REST, 2, turned, 2));
    }

    @Test
    public void aPageFadingUnderChromeKeepsTheFrozenLayer() {
        // Measured on pong: Home2's widget pane fades (0.84, 0.71, 0.67, 0.59) as the pages
        // slide under the accessory bar's glass.
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
            assertFalse(BackdropLayers.needsRecord(Mode.FROZEN, Mode.FROZEN, true,
                BackdropLayers.shift(recorded, 2, now, 2, true), true));
            assertEquals(alphas[f], BackdropLayers.pageAlpha(now, 0), 0f);
            assertEquals(1f, BackdropLayers.pageAlpha(now, 1), 0f);
            assertEquals(WITH_PAGES, chrome(Move.SHIFT, true));
        }
    }

    @Test
    public void aStripWithSeamsKeepsThePagesInTheLocalLayer() {
        // Pages with different grounds: the strip lies under the pages, so a frozen pages layer
        // under it would be covered. The pages stay in the local layer, in their order.
        assertFalse(BackdropLayers.holdsPages(Place.CHROME, Mode.FROZEN, Move.SHIFT, true, true));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            true, false, false, true, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            true, true, false, false, false, false));
        // Under a surface on a page the strip rides with it.
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            true, false, false, true, true, false));
    }

    @Test
    public void slotsShareThePadBudget() {
        int one = BackdropLayers.pad(0L, 1200, 300, true, 1080);
        int three = BackdropLayers.pad(0L, 1200, 300, true, 1080, BackdropLayers.frozenSlots(2));
        assertTrue(three <= one);
        assertTrue(BackdropLayers.fits(0L, 3 * (1200 + 2 * three), 300));
    }

    @Test
    public void aLowerPageUnderGlassOnTheTopPageIsRecordedWhenItMovesAgainstIt() {
        // Stack: the page beneath lies under the glass on the top page; it is local and fixed.
        assertEquals(Holder.FIXED, BackdropLayers.holder(Place.PAGE, Tier.PAGE, false, true,
            false, false, false, false, true));
        // Moved against it: the local layer is recorded again; not moved: nothing is.
        assertEquals(BackdropLayers.STALE_LOCAL,
            BackdropLayers.plan(true, false, false, false, false, true, true)
                & BackdropLayers.STALE_LOCAL);
        assertEquals(0, BackdropLayers.plan(true, false, false, false, false, true, false)
            & BackdropLayers.STALE_LOCAL);
    }

    @Test
    public void aSceneFrameRecordsOnlyTheGround() {
        int plan = BackdropLayers.plan(true, true, true, false, false, false, false);
        assertEquals(LAYERS_GROUND_LOCAL | BackdropLayers.STALE_GROUND, plan);
        assertEquals(Motion.SCENE, BackdropLayers.motion(Layer.GROUND, Place.PAGE, true));
        assertEquals(Motion.PARALLAX, BackdropLayers.motion(Layer.GROUND, Place.PAGE, false));
        // No new scene frame: nothing is recorded again.
        assertEquals(0, BackdropLayers.plan(true, true, false, false, false, false, false)
            & BackdropLayers.STALE_GROUND);
    }

    @Test
    public void aFloatOnThePinnedLayerSeesThePagesTravelAndItsLowerFloatsHold() {
        assertEquals(Motion.TRAVEL, BackdropLayers.motion(Layer.PAGES, Place.FLOAT, false));
        assertEquals(Motion.FIXED, BackdropLayers.motion(Layer.LOCAL, Place.FLOAT, false));
        assertTrue(BackdropLayers.holdsPages(Place.FLOAT, Mode.FROZEN, Move.SHIFT, true, false));
        assertEquals(WITH_PAGES, BackdropLayers.plan(true, false, false, true, false, false, false));
        // A float raised or a float beneath shown: only the local layer is recorded again.
        int plan = BackdropLayers.plan(true, false, false, true, true, false, false);
        assertEquals(BackdropLayers.STALE_LOCAL, plan & (BackdropLayers.STALE_GROUND
            | BackdropLayers.STALE_PAGES | BackdropLayers.STALE_LOCAL));
    }

    @Test
    public void aFrozenPictureNotYetLandedLeavesThePagesLocal() {
        assertFalse(BackdropLayers.holdsPages(Place.CHROME, Mode.FROZEN, Move.SHIFT, false, false));
        assertEquals(LAYERS_GROUND_LOCAL, chrome(Move.SHIFT, false));
    }

    @Test
    public void withoutTheStoredFrameTheWallpaperIsLocal() {
        // No ground at hand: the local layer holds the wallpaper, and no pages layer is drawn.
        assertEquals(BackdropLayers.LAYERS | BackdropLayers.LOCAL,
            BackdropLayers.plan(false, false, false, true, false, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, true,
            false, false, false, false, false, false));
        // Under a surface on a page it stands still as the page moves.
        assertEquals(Holder.FIXED, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, true,
            false, false, false, false, true, false));
    }

    @Test
    public void theGroundStripRidesWithASurfaceOnAPage() {
        // Measured on pong (Home2 <-> Workspace, a live scene): the strip's band under a page is
        // that page's own ground and moves as the page does.
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            true, false, false, true, true, false));
        // Chrome that stands still where the surface sweeps is local, recorded when it moves.
        assertEquals(Holder.FIXED, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            true, false, false, false, true, false));
        assertEquals(Holder.NONE, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, false,
            true, false, false, false, false, false));
        // The wallpaper and the scene are the ground, wherever they stand.
        assertEquals(Holder.GROUND, BackdropLayers.holder(Place.PAGE, Tier.BENEATH_WALL, true,
            true, false, false, false, true, false));
        assertEquals(Holder.GROUND, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, true,
            true, false, false, false, false, false));
    }

    @Test
    public void viewsBeneathFindTheirLayer() {
        // On a page: its own views ride with it; a lower page not under it draws nothing.
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.PAGE, Tier.ON_PAGE, false, true,
            false, false, false, false, false));
        assertEquals(Holder.NONE, BackdropLayers.holder(Place.PAGE, Tier.PAGE, false, true,
            false, false, false, false, false));
        // Off the wall: the view holding the wall is the pages while a frozen layer is held, and
        // local otherwise; everything else, the strip too, is the local layer.
        assertEquals(Holder.PAGES, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            true, true, true, false, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            true, true, false, false, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.CHROME, Tier.BENEATH_WALL, false,
            true, false, false, true, false, false));
        assertEquals(Holder.LOCAL, BackdropLayers.holder(Place.FLOAT, Tier.BENEATH_WALL, false,
            true, false, false, false, false, false));
    }
}
