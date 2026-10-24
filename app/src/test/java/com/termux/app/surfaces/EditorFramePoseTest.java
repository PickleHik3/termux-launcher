package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;

import org.junit.Test;

/**
 * The frame, the half line and the card's detents as arithmetic, on pong's numbers: 411 × 914 dp
 * at 2.625, a 41 dp status bar, gesture navigation (wireframes README), at font scale 1 and 1.3.
 * The rule every case checks: the whole frame stands between the top line and the half line, and
 * the half line stands over the card (or the editor bar) without touching either.
 */
public class EditorFramePoseTest {

    private static final float DENSITY = 2.625f;
    private static final int WINDOW_H = 2399;
    private static final int STATUS_PX = 108;
    private static final int NAV_PX = 63;
    private static final int CONTAINER_TOP = STATUS_PX;
    private static final int CONTAINER_H = WINDOW_H - STATUS_PX - NAV_PX;
    /** The container, the status band the frame reveals over it, and the navigation band. */
    private static final int REVEAL = STATUS_PX;
    private static final int VISIBLE_H = CONTAINER_H + REVEAL + NAV_PX;

    private static int dp(float value) {
        return Math.round(value * DENSITY);
    }

    private static EditorFramePose.Window pong(float fontScale) {
        return new EditorFramePose.Window(WINDOW_H, STATUS_PX, NAV_PX, DENSITY, fontScale,
            CONTAINER_TOP, VISIBLE_H, REVEAL, dp(48), dp(8));
    }

    /** The frame's visible top in the content view. */
    private static float frameTop(EditorFramePose.Window window, EditorFramePose.Pose pose) {
        return window.containerTopPx + pose.translationY - window.revealTopPx * pose.scale;
    }

    private static void assertWholeFrameAbove(EditorFramePose.Window window,
                                              EditorFramePose.Pose pose, float halfLineTop) {
        assertEquals("the frame's top on the top line", window.topPx(), frameTop(window, pose),
            0.5f);
        assertTrue("never under the status bar", frameTop(window, pose) >= STATUS_PX);
        assertTrue("the frame ends a gap over the half line, " + pose.frameBottomPx(window)
                + " vs " + halfLineTop,
            pose.frameBottomPx(window) <= halfLineTop - window.gapPx() + 1);
    }

    @Test
    public void theRestingFrameIsAboutPointEightWithTheHalfLineOverTheBar() {
        for (float fontScale : new float[] {1f, 1.3f}) {
            EditorFramePose.Window window = pong(fontScale);
            EditorFramePose.Pose rest = EditorFramePose.restPose(window);
            assertTrue("about ×0.8, was " + rest.scale, rest.scale > 0.75f && rest.scale <= 0.84f);
            float barTop = WINDOW_H - NAV_PX - dp(8) - dp(48);
            float halfLineTop = barTop - window.gapPx() - window.halfRowPx();
            assertWholeFrameAbove(window, rest, halfLineTop);
            assertTrue("the half line clears the bar", rest.halfLineTopPx + window.halfRowPx()
                <= barTop - window.gapPx() + 1);
        }
        // A tall room never scales past the cap.
        EditorFramePose.Window tall = new EditorFramePose.Window(5000, 0, 0, 1f, 1f, 0, 1000, 0,
            48, 8);
        assertEquals(EditorFramePose.REST_MAX_SCALE, EditorFramePose.restPose(tall).scale, 0f);
    }

    @Test
    public void theHalfDetentLeavesTheWholeFrameAtPointSix() {
        EditorFramePose.Window window = pong(1f);
        int full = EditorFramePose.fullSheetPx(window);
        int half = EditorFramePose.halfSheetPx(window, dp(64 + 72 + 16) + NAV_PX, full);
        EditorFramePose.Pose pose = EditorFramePose.pose(window, WINDOW_H - half);
        assertEquals(EditorFramePose.HALF_SCALE, pose.scale, 0.01f);
        float halfLineTop = WINDOW_H - half - window.gapPx() - window.halfRowPx();
        assertWholeFrameAbove(window, pose, halfLineTop);
        assertTrue("the full detent is the taller one", full > half + dp(48));
    }

    @Test
    public void theFullDetentLeavesTheWholeFrameAtPointFourFour() {
        EditorFramePose.Window window = pong(1f);
        int full = EditorFramePose.fullSheetPx(window);
        EditorFramePose.Pose pose = EditorFramePose.pose(window, WINDOW_H - full);
        assertEquals(EditorFramePose.FULL_SCALE, pose.scale, 0.01f);
        assertWholeFrameAbove(window, pose,
            WINDOW_H - full - window.gapPx() - window.halfRowPx());
        assertTrue("the card takes about half the window", full > WINDOW_H * 0.45f);
    }

    @Test
    public void aLargeFontKeepsTheFrameWholeAndBelowTheStatusBar() {
        EditorFramePose.Window window = pong(1.3f);
        assertTrue("the half line grows with the text",
            window.halfRowPx() > pong(1f).halfRowPx());
        int full = EditorFramePose.fullSheetPx(window);
        // A card whose first row needs more than the half detent would leave: the frame shrinks.
        int half = EditorFramePose.halfSheetPx(window, Math.round(WINDOW_H * 0.5f), full);
        EditorFramePose.Pose pose = EditorFramePose.pose(window, WINDOW_H - half);
        assertTrue(pose.scale < EditorFramePose.HALF_SCALE);
        assertWholeFrameAbove(window, pose, WINDOW_H - half - window.gapPx() - window.halfRowPx());
    }

    @Test
    public void aDraggedCardMovesTheFrameContinuouslyAndNeverUnderTheCard() {
        EditorFramePose.Window window = pong(1f);
        float last = Float.MAX_VALUE;
        for (int top = WINDOW_H; top >= WINDOW_H - EditorFramePose.fullSheetPx(window); top -= 37) {
            EditorFramePose.Pose pose = EditorFramePose.pose(window, top);
            assertTrue("shrinks as the card rises", pose.scale <= last + 1e-4f);
            last = pose.scale;
            float halfLineTop = Math.min(top, WINDOW_H - NAV_PX - dp(8) - dp(48))
                - window.gapPx() - window.halfRowPx();
            assertWholeFrameAbove(window, pose, halfLineTop);
        }
    }

    @Test
    public void noCardIsTheRestPose() {
        EditorFramePose.Window window = pong(1f);
        assertEquals(EditorFramePose.restPose(window).scale,
            EditorFramePose.pose(window, WINDOW_H).scale, 0f);
    }

    @Test
    public void halfLineHeightFollowsTheFontScale() {
        assertEquals((int) Math.ceil(20 * DENSITY), EditorFramePose.halfRowPx(DENSITY, 1f));
        assertEquals((int) Math.ceil(26 * DENSITY), EditorFramePose.halfRowPx(DENSITY, 1.3f));
        assertEquals("never under its 16dp", (int) Math.ceil(20 * DENSITY),
            EditorFramePose.halfRowPx(DENSITY, 0.85f));
    }

    @Test
    public void aPutAwayRowsGhostStandsAgainstItsSurfacesInnerSide() {
        int[] azRow = {20, 1700, 980, 1780};
        int[] bottomBand = EditorFramePose.ghostRect(PlaceLayout.Edge.BOTTOM, 0, 1000, 2000,
            100, 20);
        assertArrayEquals("over the A-Z row, where the apps stood", new int[] {20, 1600, 980, 1780},
            EditorFramePose.ghostBeside(azRow, bottomBand, 2000));
        int[] topRow = {20, 200, 980, 280};
        int[] topBand = EditorFramePose.ghostRect(PlaceLayout.Edge.TOP, 0, 1000, 2000, 100, 20);
        assertArrayEquals(new int[] {20, 200, 980, 380},
            EditorFramePose.ghostBeside(topRow, topBand, 2000));
    }

    @Test
    public void aGhostClearsTheSurfacesStillStandingOnItsEdge() {
        // The dock put away while its A-Z row stands: its target reaches over the apps' band.
        int[] azRow = {20, 1700, 980, 1780};
        int[] dockBand = EditorFramePose.ghostRect(PlaceLayout.Edge.BOTTOM, 0, 1000, 2000,
            100, 20);
        int[] dockTarget = EditorFramePose.ghostBeside(azRow, dockBand, 2000);
        // The keyboard switched off on the same edge: its ghost goes past the dock's target,
        // the usual gap above it, so a tap on either reaches its own card.
        int depth = EditorFramePose.depthFrom(PlaceLayout.Edge.BOTTOM, dockTarget, 1000, 2000);
        assertEquals(400, depth);
        int[] keyboard = EditorFramePose.shiftInward(dockBand, PlaceLayout.Edge.BOTTOM, depth);
        assertArrayEquals(new int[] {20, 1480, 980, 1580}, keyboard);
        assertTrue("no overlap", keyboard[3] <= dockTarget[1]);

        int[] topBar = {20, 0, 980, 150};
        assertEquals(150, EditorFramePose.depthFrom(PlaceLayout.Edge.TOP, topBar, 1000, 2000));
        assertArrayEquals(new int[] {20, 170, 980, 270}, EditorFramePose.shiftInward(
            EditorFramePose.ghostRect(PlaceLayout.Edge.TOP, 0, 1000, 2000, 100, 20),
            PlaceLayout.Edge.TOP, 150));
    }

    @Test
    public void ghostsStandOnTheirEdgeAndStackInwards() {
        int band = 100;
        int margin = 20;
        assertArrayEquals(new int[] {20, 1880, 980, 1980},
            EditorFramePose.ghostRect(PlaceLayout.Edge.BOTTOM, 0, 1000, 2000, band, margin));
        assertArrayEquals(new int[] {20, 1780, 980, 1880},
            EditorFramePose.ghostRect(PlaceLayout.Edge.BOTTOM, 1, 1000, 2000, band, margin));
        assertArrayEquals(new int[] {20, 20, 980, 120},
            EditorFramePose.ghostRect(PlaceLayout.Edge.TOP, 0, 1000, 2000, band, margin));
        assertArrayEquals(new int[] {880, 20, 980, 1980},
            EditorFramePose.ghostRect(PlaceLayout.Edge.RIGHT, 0, 1000, 2000, band, margin));
    }
}
