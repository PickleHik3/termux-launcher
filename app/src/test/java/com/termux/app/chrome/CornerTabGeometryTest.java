package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Where a corner tab lands, and what shape it is. One rule serves the Widgets page, the Display
 * page and a terminal pane, so this is where it is held: the tab hangs off the corner that was
 * tapped, slides out of the edge that corner is on, lies inside the border that frame paints rather
 * than against the bounding box behind it, sits flush in its corner so that border is its own outer
 * edge, and never crosses the far side however narrow the frame gets.
 */
@RunWith(RobolectricTestRunner.class)
public class CornerTabGeometryTest {

    private static final float GAP = 8f;
    private static final float PAD = 5f;
    private static final float HEIGHT = 32f;
    private static final float MARGIN = 3f;
    /** Most of these ask about the tab itself, so the frame's corner keeps none of it. */
    private static final float HOLD = 0f;
    /** Two 30dp buttons: 5 + 30 + 8 + 30 + 5. */
    private static final float[] PAIR = {30f, 30f};
    private static final float PAIR_WIDTH = 78f;
    /** The radii the surface editor can put under a tab, including both ends of its range. */
    private static final float[] RADII = {0f, 6f, 14f, 28f};
    /** No border, a plain pane's stroke, and a thick one. */
    private static final float[] STROKES = {0f, 1f, 2f};
    private static final int[] CORNERS = {
        CornerZones.TOP_LEFT, CornerZones.TOP_RIGHT,
        CornerZones.BOTTOM_LEFT, CornerZones.BOTTOM_RIGHT
    };

    /** Five 30dp buttons: 5 + 30 + (8 + 30) x 4 + 5. */
    private static final float[] FIVE = {30f, 30f, 30f, 30f, 30f};
    private static final float FIVE_WIDTH = 192f;

    private final RectF mTab = new RectF();
    private final RectF[] mButtons = {new RectF(), new RectF(), new RectF(), new RectF(),
        new RectF()};
    private final float[] mPoints = new float[CornerTabGeometry.PATH_POINTS * 2];

    private void layout(int corner, RectF bounds, float progress) {
        layout(corner, bounds, 0f, progress);
    }

    private void layout(int corner, RectF bounds, float border, float progress) {
        CornerTabGeometry.layout(corner, bounds, PAIR, 2, GAP, PAD, HEIGHT, border, MARGIN, HOLD,
            progress, mTab, mButtons);
    }

    private static RectF frame() {
        return new RectF(0f, 0f, 600f, 800f);
    }

    @Test
    public void eachCornerPutsTheTabOnItsOwnSideAndItsOwnEdge() {
        RectF frame = frame();

        layout(CornerZones.TOP_RIGHT, frame, 1f);
        assertEquals(600f, mTab.right, 0.01f);
        assertEquals(600f - PAIR_WIDTH, mTab.left, 0.01f);
        assertEquals(0f, mTab.top, 0.01f);

        layout(CornerZones.TOP_LEFT, frame, 1f);
        assertEquals(0f, mTab.left, 0.01f);
        assertEquals(PAIR_WIDTH, mTab.right, 0.01f);
        assertEquals(0f, mTab.top, 0.01f);

        layout(CornerZones.BOTTOM_LEFT, frame, 1f);
        assertEquals(0f, mTab.left, 0.01f);
        assertEquals(800f - HEIGHT, mTab.top, 0.01f);
        assertEquals(800f, mTab.bottom, 0.01f);

        layout(CornerZones.BOTTOM_RIGHT, frame, 1f);
        assertEquals(600f, mTab.right, 0.01f);
        assertEquals(800f - HEIGHT, mTab.top, 0.01f);
    }

    @Test
    public void aRetractedTabSitsEntirelyOutsideTheEdgeItComesOutOf() {
        RectF frame = frame();

        layout(CornerZones.TOP_LEFT, frame, 0f);
        assertEquals(-HEIGHT, mTab.top, 0.01f);
        assertEquals(0f, mTab.bottom, 0.01f);

        layout(CornerZones.BOTTOM_LEFT, frame, 0f);
        assertEquals(800f, mTab.top, 0.01f);

        layout(CornerZones.TOP_LEFT, frame, .5f);
        assertEquals(-HEIGHT / 2f, mTab.top, 0.01f);
    }

    /**
     * The corner side keeps nothing back, at any radius: the tab's outer edge is the frame's own
     * side, which is what makes the frame's stroke the tab's outer outline rather than a second
     * line beside it. The arc is not dodged either — it is the tab's own outer corner, and the
     * caller clips it to the frame's shape.
     */
    @Test
    public void theTabIsFlushWithTheSideItsCornerIsOn() {
        RectF frame = frame();

        layout(CornerZones.TOP_RIGHT, frame, 1f);
        assertEquals(600f, mTab.right, 0.01f);

        layout(CornerZones.TOP_LEFT, frame, 1f);
        assertEquals(0f, mTab.left, 0.01f);

        layout(CornerZones.BOTTOM_RIGHT, frame, 1f);
        assertEquals(600f, mTab.right, 0.01f);
    }

    /**
     * The frame the tab lives in is the one the border draws on its inside, not the bounding box:
     * the tab starts one stroke further in on every side, and comes out from behind the line the
     * eye reads rather than from behind the pixel column outside it.
     */
    @Test
    public void theBorderStrokeMovesTheWholeTabInsideTheLineItDraws() {
        RectF frame = frame();
        layout(CornerZones.TOP_LEFT, frame, 2f, 1f);
        assertEquals("flush against the border's inner edge, not the box", 2f, mTab.left, 0.01f);
        assertEquals("and out of that inner edge, not the box", 2f, mTab.top, 0.01f);

        layout(CornerZones.BOTTOM_RIGHT, frame, 2f, 1f);
        assertEquals(600f - 2f, mTab.right, 0.01f);
        assertEquals(800f - 2f, mTab.bottom, 0.01f);

        // Retracted, it is still entirely outside that inner edge.
        layout(CornerZones.TOP_LEFT, frame, 2f, 0f);
        assertEquals(2f, mTab.bottom, 0.01f);
    }

    @Test
    public void aFrameTooNarrowKeepsTheTabAndItsButtonsInside() {
        // Room for 47 of the 78 the pair asks for.
        RectF narrow = new RectF(100f, 0f, 150f, 400f);
        layout(CornerZones.TOP_RIGHT, narrow, 1f);
        assertEquals(150f, mTab.right, 0.01f);
        assertEquals(100f + MARGIN, mTab.left, 0.01f);
        for (int i = 0; i < 2; i++) {
            assertTrue("button " + i + " left of the tab", mButtons[i].left >= mTab.left - 0.01f);
            assertTrue("button " + i + " past the tab", mButtons[i].right <= mTab.right + 0.01f);
        }
        // And never past the frame either, which is the whole point of the clamp.
        assertTrue(mTab.left >= narrow.left);
        assertTrue(mTab.right <= narrow.right);
    }

    /**
     * Except for the sliver the frame's corner keeps: the tab is flush in the corner now, so its
     * outermost button would otherwise sit exactly where the hold that opened the tab landed, and
     * holding that corner again would run the button rather than put the tab away.
     */
    @Test
    public void theFramesCornerKeepsASliverOfTheTabsOuterEnd() {
        RectF frame = frame();
        CornerTabGeometry.layout(CornerZones.TOP_RIGHT, frame, PAIR, 2, GAP, PAD, HEIGHT, 0f,
            MARGIN, 8f, 1f, mTab, mButtons);
        assertEquals("the tab itself is still flush", 600f, mTab.right, 0.01f);
        assertEquals("but the corner keeps the outer 8 of it",
            600f - 8f, mButtons[1].right, 0.01f);
        assertEquals("and the far end is untouched", mTab.left, mButtons[0].left, 0.01f);

        CornerTabGeometry.layout(CornerZones.BOTTOM_LEFT, frame, PAIR, 2, GAP, PAD, HEIGHT, 0f,
            MARGIN, 8f, 1f, mTab, mButtons);
        assertEquals(0f, mTab.left, 0.01f);
        assertEquals("the corner is on the other side now", 8f, mButtons[0].left, 0.01f);
        assertEquals(mTab.right, mButtons[1].right, 0.01f);
    }

    @Test
    public void theButtonsFillTheTabEdgeToEdgeAndDoNotOverlap() {
        RectF frame = frame();
        layout(CornerZones.TOP_RIGHT, frame, 1f);
        assertEquals("the first button reaches the tab's leading edge",
            mTab.left, mButtons[0].left, 0.01f);
        assertEquals("the last button reaches its trailing edge",
            mTab.right, mButtons[1].right, 0.01f);
        assertEquals("they meet in the middle of the gap",
            mButtons[0].right, mButtons[1].left, 0.01f);
        for (int i = 0; i < 2; i++) {
            assertEquals(mTab.top, mButtons[i].top, 0.01f);
            assertEquals(mTab.bottom, mButtons[i].bottom, 0.01f);
        }
    }

    /**
     * A tab of any length. Every place on the wall builds its buttons from a list now, so five is
     * as ordinary as two: they lay out in order, meet edge to edge, and the tab is exactly as wide
     * as they asked for while the frame has room.
     */
    @Test
    public void fiveButtonsLayOutInOrderAndFillTheTab() {
        RectF frame = frame();
        CornerTabGeometry.layout(CornerZones.TOP_RIGHT, frame, FIVE, 5, GAP, PAD, HEIGHT, 0f,
            MARGIN, HOLD, 1f, mTab, mButtons);

        assertEquals(FIVE_WIDTH, CornerTabGeometry.naturalWidth(FIVE, 5, GAP, PAD), 0.01f);
        assertEquals(600f, mTab.right, 0.01f);
        assertEquals(600f - FIVE_WIDTH, mTab.left, 0.01f);
        assertEquals("the first button reaches the tab's leading edge",
            mTab.left, mButtons[0].left, 0.01f);
        assertEquals("the last button reaches its trailing edge",
            mTab.right, mButtons[4].right, 0.01f);
        for (int i = 0; i < 5; i++) {
            assertEquals(mTab.top, mButtons[i].top, 0.01f);
            assertEquals(mTab.bottom, mButtons[i].bottom, 0.01f);
            if (i > 0) {
                assertEquals("button " + i + " meets the one before it",
                    mButtons[i - 1].right, mButtons[i].left, 0.01f);
            }
        }
    }

    /**
     * The narrow-pane rule with a full tab on it: a split pane far too narrow for five buttons
     * shrinks them in proportion rather than walking them off its far side, so the fifth is still
     * inside the frame and still has a slot a thumb can land in.
     */
    @Test
    public void fiveButtonsScaleDownRatherThanOverflowANarrowFrame() {
        // Room for 97 of the 192 the five ask for: a 100dp-wide pane of a vertical split.
        RectF narrow = new RectF(0f, 0f, 100f, 800f);
        CornerTabGeometry.layout(CornerZones.TOP_RIGHT, narrow, FIVE, 5, GAP, PAD, HEIGHT, 0f,
            MARGIN, HOLD, 1f, mTab, mButtons);

        assertEquals(100f, mTab.right, 0.01f);
        assertEquals(MARGIN, mTab.left, 0.01f);
        assertTrue("the tab was cut down to the room there is",
            mTab.width() < FIVE_WIDTH);
        for (int i = 0; i < 5; i++) {
            assertTrue("button " + i + " left of the tab", mButtons[i].left >= mTab.left - 0.01f);
            assertTrue("button " + i + " past the tab", mButtons[i].right <= mTab.right + 0.01f);
            assertTrue("button " + i + " has no slot to tap", mButtons[i].width() > 0f);
            if (i > 0) {
                assertEquals("button " + i + " meets the one before it",
                    mButtons[i - 1].right, mButtons[i].left, 0.01f);
            }
        }
        assertEquals("and they still fill it edge to edge", mTab.left, mButtons[0].left, 0.01f);
        assertEquals(mTab.right, mButtons[4].right, 0.01f);
        assertTrue(mTab.left >= narrow.left);
        assertTrue(mTab.right <= narrow.right);
    }

    @Test
    public void aTabInHostCoordinatesHangsOffItsOwnPaneNotTheHost() {
        // The second pane of a vertical split: the maths is told the pane, so the tab lands on the
        // pane's own edges and nowhere near the host's.
        RectF pane = new RectF(0f, 404f, 600f, 800f);
        layout(CornerZones.BOTTOM_RIGHT, pane, 1f);
        assertEquals(800f - HEIGHT, mTab.top, 0.01f);
        assertEquals(600f, mTab.right, 0.01f);
        assertTrue(mTab.top >= pane.top);

        layout(CornerZones.TOP_LEFT, pane, 1f);
        assertEquals(404f, mTab.top, 0.01f);
    }

    @Test
    public void noButtonsIsAnEmptyTab() {
        CornerTabGeometry.layout(CornerZones.TOP_RIGHT, frame(), new float[0], 0, GAP, PAD, HEIGHT,
            0f, MARGIN, HOLD, 1f, mTab, mButtons);
        assertTrue(mTab.isEmpty());
        assertTrue(mButtons[0].isEmpty());
    }

    /** A border thicker than the frame leaves nothing to lay a tab out in, rather than inverting it. */
    @Test
    public void aBorderWiderThanTheFrameIsAnEmptyTab() {
        CornerTabGeometry.layout(CornerZones.TOP_LEFT, new RectF(0f, 0f, 10f, 10f), PAIR, 2, GAP,
            PAD, HEIGHT, 20f, MARGIN, HOLD, 1f, mTab, mButtons);
        assertTrue(mTab.isEmpty());
        assertTrue(mButtons[0].isEmpty());
    }

    @Test
    public void theWidthIsThePaddingTheGapsAndTheButtons() {
        assertEquals(PAIR_WIDTH, CornerTabGeometry.naturalWidth(PAIR, 2, GAP, PAD), 0.01f);
        assertEquals(40f, CornerTabGeometry.naturalWidth(PAIR, 1, GAP, PAD), 0.01f);
        assertEquals(0f, CornerTabGeometry.naturalWidth(PAIR, 0, GAP, PAD), 0.01f);
    }

    // ---------------------------------------------------------------- the shape itself

    /** The border's inner edge turns a shallower arc than its outside, and never a negative one. */
    @Test
    public void theInnerRadiusIsTheArcTheBordersInsideTurns() {
        assertEquals(13f, CornerTabGeometry.innerRadiusPx(14f, 1f), 0.001f);
        assertEquals(14f, CornerTabGeometry.innerRadiusPx(14f, 0f), 0.001f);
        assertEquals("a stroke deeper than the radius leaves a square inner corner",
            0f, CornerTabGeometry.innerRadiusPx(1f, 4f), 0.001f);
    }

    /**
     * The tab's own two corners turn the frame's radius, not one of their own — held back only
     * where the tab is too short to turn it twice, or where turning it would reach back into the
     * frame's own arc and be cut by the clip that rounds the tab's outer corner.
     */
    @Test
    public void theTabsOwnCornersTurnTheFramesRadius() {
        assertEquals("the frame's radius, as the frame turns it",
            5f, CornerTabGeometry.tabCornerRadiusPx(5f, HEIGHT, PAIR_WIDTH), 0.001f);
        assertEquals("a square frame gets a square tab",
            0f, CornerTabGeometry.tabCornerRadiusPx(0f, HEIGHT, PAIR_WIDTH), 0.001f);
        assertEquals("an arc almost as deep as the tab leaves only what is past it",
            5f, CornerTabGeometry.tabCornerRadiusPx(27f, HEIGHT, PAIR_WIDTH), 0.001f);
        assertEquals("and one deeper than the tab leaves nothing",
            0f, CornerTabGeometry.tabCornerRadiusPx(40f, HEIGHT, PAIR_WIDTH), 0.001f);
        assertEquals("a tab too short to turn it twice halves it",
            3f, CornerTabGeometry.tabCornerRadiusPx(5f, HEIGHT, 6f), 0.001f);
        for (float radius : RADII) {
            float r = CornerTabGeometry.tabCornerRadiusPx(radius, HEIGHT, PAIR_WIDTH);
            assertTrue("radius " + radius + " must clear the frame's own arc",
                r >= 0f && r + radius <= HEIGHT + 0.001f);
        }
    }

    /**
     * The one line a tab draws, corner by corner: it leaves the frame's own side a radius short of
     * its far edge, turns, runs across, turns again and drops back to the edge it came out of.
     * Nothing in it lies along the frame's own side or around its own corner — those are the
     * frame's line, and drawing them again is the double line this shape exists to stop.
     */
    @Test
    public void thePathTurnsAtTheSameFourPointsOnEveryCorner() {
        RectF frame = frame();
        float r = 5f;

        layout(CornerZones.TOP_LEFT, frame, 1f);
        CornerTabGeometry.tabPathPoints(CornerZones.TOP_LEFT, frame, mTab, r, mPoints);
        assertPoints("top left", new float[]{
            0f, HEIGHT - r, r, HEIGHT, PAIR_WIDTH - r, HEIGHT, PAIR_WIDTH, HEIGHT - r,
            PAIR_WIDTH, 0f});

        layout(CornerZones.TOP_RIGHT, frame, 1f);
        CornerTabGeometry.tabPathPoints(CornerZones.TOP_RIGHT, frame, mTab, r, mPoints);
        assertPoints("top right", new float[]{
            600f, HEIGHT - r, 600f - r, HEIGHT, 600f - PAIR_WIDTH + r, HEIGHT,
            600f - PAIR_WIDTH, HEIGHT - r, 600f - PAIR_WIDTH, 0f});

        layout(CornerZones.BOTTOM_LEFT, frame, 1f);
        CornerTabGeometry.tabPathPoints(CornerZones.BOTTOM_LEFT, frame, mTab, r, mPoints);
        assertPoints("bottom left", new float[]{
            0f, 800f - HEIGHT + r, r, 800f - HEIGHT, PAIR_WIDTH - r, 800f - HEIGHT,
            PAIR_WIDTH, 800f - HEIGHT + r, PAIR_WIDTH, 800f});

        layout(CornerZones.BOTTOM_RIGHT, frame, 1f);
        CornerTabGeometry.tabPathPoints(CornerZones.BOTTOM_RIGHT, frame, mTab, r, mPoints);
        assertPoints("bottom right", new float[]{
            600f, 800f - HEIGHT + r, 600f - r, 800f - HEIGHT, 600f - PAIR_WIDTH + r, 800f - HEIGHT,
            600f - PAIR_WIDTH, 800f - HEIGHT + r, 600f - PAIR_WIDTH, 800f});
    }

    /**
     * The path leaves the frame on the border's own inner edge, whatever stroke that border is: the
     * line the tab draws meets the line the frame draws, with nothing between them.
     */
    @Test
    public void thePathLeavesTheFrameOnTheBordersInnerEdge() {
        RectF frame = frame();
        RectF inner = new RectF();
        for (float stroke : STROKES) {
            CornerTabGeometry.innerBounds(frame, stroke, inner);
            layout(CornerZones.BOTTOM_LEFT, frame, stroke, 1f);
            CornerTabGeometry.tabPathPoints(CornerZones.BOTTOM_LEFT, inner, mTab, 5f, mPoints);
            assertEquals("stroke " + stroke, inner.left, mPoints[0], 0.001f);
            assertEquals("stroke " + stroke, inner.bottom, mPoints[9], 0.001f);
        }
    }

    /**
     * The containment test the whole rule exists to pass, at every radius and every stroke: the
     * line the tab draws stays inside the frame's, so the clip has nothing to cut but the tab's
     * outer corner — which is the frame's own corner. A frame rounded deeper than the tab is tall
     * is the one exception: there the tab lies wholly within the arc, which the clip trims to the
     * frame's shape, and all that can be asked is that it stays inside the frame's sides.
     */
    @Test
    public void everythingTheTabDrawsLiesInsideTheBorderAtEveryRadiusAndStroke() {
        RectF pane = new RectF(0f, 0f, 600f, 400f);
        RectF inner = new RectF();
        float tabHeight = 24f;
        float[] widths = {22.4f, 22.4f, 22.4f};
        RectF tab = new RectF();
        RectF[] buttons = {new RectF(), new RectF(), new RectF()};
        for (float radius : RADII) {
            for (float stroke : STROKES) {
                float arc = CornerTabGeometry.innerRadiusPx(radius, stroke);
                CornerTabGeometry.innerBounds(pane, stroke, inner);
                for (int corner : CORNERS) {
                    CornerTabGeometry.layout(corner, pane, widths, 3, 0f, 2.4f, tabHeight, stroke,
                        3f, HOLD, 1f, tab, buttons);
                    float r = CornerTabGeometry.tabCornerRadiusPx(arc, tab.height(), tab.width());
                    CornerTabGeometry.tabPathPoints(corner, inner, tab, r, mPoints);
                    String where = "radius " + radius + " stroke " + stroke + " corner " + corner;
                    boolean clipOwnsTheTab = arc > tab.height();
                    for (int i = 0; i < CornerTabGeometry.PATH_POINTS; i++) {
                        float x = mPoints[i * 2];
                        float y = mPoints[i * 2 + 1];
                        if (clipOwnsTheTab) {
                            assertTrue(where + " path point " + i + " past the frame's sides",
                                x >= inner.left - 0.001f && x <= inner.right + 0.001f
                                    && y >= inner.top - 0.001f && y <= inner.bottom + 0.001f);
                        } else {
                            assertInside(where + " path point " + i, pane, radius, stroke, x, y);
                        }
                    }
                    // Flush, to the pixel, against the side its corner is on.
                    assertEquals(where + " outer edge", CornerZones.isLeft(corner)
                        ? inner.left : inner.right, mPoints[0], 0.001f);
                    for (RectF button : buttons) {
                        assertTrue(where + " button inside the tab",
                            button.left >= tab.left - 0.001f && button.right <= tab.right + 0.001f);
                    }
                }
            }
        }
    }

    private void assertPoints(String where, float[] expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(where + " point " + (i / 2) + (i % 2 == 0 ? " x" : " y"),
                expected[i], mPoints[i], 0.001f);
        }
    }

    private static void assertInside(String what, RectF pane, float radius, float stroke,
                                     float x, float y) {
        assertTrue(what + " at (" + x + ", " + y + ")",
            CornerTabGeometry.insideBorder(pane, radius, stroke, x, y));
    }
}
