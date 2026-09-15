package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Where a corner tab lands. One rule serves the Widgets page, the Display page and a terminal
 * pane, so this is where it is held: the tab hangs off the corner that was tapped, slides out of
 * the edge that corner is on, lines up inside the border that frame paints rather than against the
 * bounding box behind it, starts past the arc that border turns, and never crosses the far side
 * however narrow the frame gets.
 */
@RunWith(RobolectricTestRunner.class)
public class CornerTabGeometryTest {

    private static final float GAP = 8f;
    private static final float PAD = 5f;
    private static final float HEIGHT = 32f;
    private static final float MARGIN = 3f;
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

    private void layout(int corner, RectF bounds, float inset, float progress) {
        layout(corner, bounds, 0f, inset, progress);
    }

    private void layout(int corner, RectF bounds, float border, float inset, float progress) {
        CornerTabGeometry.layout(corner, bounds, PAIR, 2, GAP, PAD, HEIGHT, border, inset, MARGIN,
            progress, mTab, mButtons);
    }

    private static RectF frame() {
        return new RectF(0f, 0f, 600f, 800f);
    }

    @Test
    public void eachCornerPutsTheTabOnItsOwnSideAndItsOwnEdge() {
        RectF frame = frame();

        layout(CornerZones.TOP_RIGHT, frame, 0f, 1f);
        assertEquals(600f - MARGIN, mTab.right, 0.01f);
        assertEquals(600f - MARGIN - PAIR_WIDTH, mTab.left, 0.01f);
        assertEquals(0f, mTab.top, 0.01f);

        layout(CornerZones.TOP_LEFT, frame, 0f, 1f);
        assertEquals(MARGIN, mTab.left, 0.01f);
        assertEquals(MARGIN + PAIR_WIDTH, mTab.right, 0.01f);
        assertEquals(0f, mTab.top, 0.01f);

        layout(CornerZones.BOTTOM_LEFT, frame, 0f, 1f);
        assertEquals(MARGIN, mTab.left, 0.01f);
        assertEquals(800f - HEIGHT, mTab.top, 0.01f);
        assertEquals(800f, mTab.bottom, 0.01f);

        layout(CornerZones.BOTTOM_RIGHT, frame, 0f, 1f);
        assertEquals(600f - MARGIN, mTab.right, 0.01f);
        assertEquals(800f - HEIGHT, mTab.top, 0.01f);
    }

    @Test
    public void aRetractedTabSitsEntirelyOutsideTheEdgeItComesOutOf() {
        RectF frame = frame();

        layout(CornerZones.TOP_LEFT, frame, 0f, 0f);
        assertEquals(-HEIGHT, mTab.top, 0.01f);
        assertEquals(0f, mTab.bottom, 0.01f);

        layout(CornerZones.BOTTOM_LEFT, frame, 0f, 0f);
        assertEquals(800f, mTab.top, 0.01f);

        layout(CornerZones.TOP_LEFT, frame, 0f, .5f);
        assertEquals(-HEIGHT / 2f, mTab.top, 0.01f);
    }

    @Test
    public void theCornerArcPushesTheTabInPastIt() {
        RectF frame = frame();
        layout(CornerZones.TOP_RIGHT, frame, 14f, 1f);
        assertEquals(600f - 14f, mTab.right, 0.01f);

        layout(CornerZones.TOP_LEFT, frame, 14f, 1f);
        assertEquals(14f, mTab.left, 0.01f);

        // An arc shallower than the margin never pulls the tab back out to the rim.
        layout(CornerZones.TOP_LEFT, frame, 1f, 1f);
        assertEquals(MARGIN, mTab.left, 0.01f);
    }

    /**
     * The frame the tab lives in is the one the border draws on its inside, not the bounding box:
     * the tab starts one stroke further in on every side, and comes out from behind the line the
     * eye reads rather than from behind the pixel column outside it.
     */
    @Test
    public void theBorderStrokeMovesTheWholeTabInsideTheLineItDraws() {
        RectF frame = frame();

        layout(CornerZones.TOP_LEFT, frame, 2f, 0f, 1f);
        assertEquals("in from the side by the stroke", 2f + MARGIN, mTab.left, 0.01f);
        assertEquals("and out of the border's inner edge, not the box", 2f, mTab.top, 0.01f);

        layout(CornerZones.BOTTOM_RIGHT, frame, 2f, 0f, 1f);
        assertEquals(600f - 2f - MARGIN, mTab.right, 0.01f);
        assertEquals(800f - 2f, mTab.bottom, 0.01f);

        // Retracted, it is still entirely outside that inner edge.
        layout(CornerZones.TOP_LEFT, frame, 2f, 0f, 0f);
        assertEquals(2f, mTab.bottom, 0.01f);
    }

    @Test
    public void aFrameTooNarrowKeepsTheTabAndItsButtonsInside() {
        // Room for 44 of the 78 the pair asks for.
        RectF narrow = new RectF(100f, 0f, 150f, 400f);
        layout(CornerZones.TOP_RIGHT, narrow, 0f, 1f);
        assertEquals(150f - MARGIN, mTab.right, 0.01f);
        assertEquals(100f + MARGIN, mTab.left, 0.01f);
        for (int i = 0; i < 2; i++) {
            assertTrue("button " + i + " left of the tab", mButtons[i].left >= mTab.left - 0.01f);
            assertTrue("button " + i + " past the tab", mButtons[i].right <= mTab.right + 0.01f);
        }
        // And never past the frame either, which is the whole point of the clamp.
        assertTrue(mTab.left >= narrow.left);
        assertTrue(mTab.right <= narrow.right);
    }

    @Test
    public void theButtonsFillTheTabEdgeToEdgeAndDoNotOverlap() {
        RectF frame = frame();
        layout(CornerZones.TOP_RIGHT, frame, 0f, 1f);
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
        CornerTabGeometry.layout(CornerZones.TOP_RIGHT, frame, FIVE, 5, GAP, PAD, HEIGHT, 0f, 0f,
            MARGIN, 1f, mTab, mButtons);

        assertEquals(FIVE_WIDTH, CornerTabGeometry.naturalWidth(FIVE, 5, GAP, PAD), 0.01f);
        assertEquals(600f - MARGIN, mTab.right, 0.01f);
        assertEquals(600f - MARGIN - FIVE_WIDTH, mTab.left, 0.01f);
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
        // Room for 94 of the 192 the five ask for: a 100dp-wide pane of a vertical split.
        RectF narrow = new RectF(0f, 0f, 100f, 800f);
        CornerTabGeometry.layout(CornerZones.TOP_RIGHT, narrow, FIVE, 5, GAP, PAD, HEIGHT, 0f, 0f,
            MARGIN, 1f, mTab, mButtons);

        assertEquals(100f - MARGIN, mTab.right, 0.01f);
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
        layout(CornerZones.BOTTOM_RIGHT, pane, 0f, 1f);
        assertEquals(800f - HEIGHT, mTab.top, 0.01f);
        assertEquals(600f - MARGIN, mTab.right, 0.01f);
        assertTrue(mTab.top >= pane.top);

        layout(CornerZones.TOP_LEFT, pane, 0f, 1f);
        assertEquals(404f, mTab.top, 0.01f);
    }

    @Test
    public void noButtonsIsAnEmptyTab() {
        CornerTabGeometry.layout(CornerZones.TOP_RIGHT, frame(), new float[0], 0, GAP, PAD, HEIGHT,
            0f, 0f, MARGIN, 1f, mTab, mButtons);
        assertTrue(mTab.isEmpty());
        assertTrue(mButtons[0].isEmpty());
    }

    /** A border thicker than the frame leaves nothing to lay a tab out in, rather than inverting it. */
    @Test
    public void aBorderWiderThanTheFrameIsAnEmptyTab() {
        CornerTabGeometry.layout(CornerZones.TOP_LEFT, new RectF(0f, 0f, 10f, 10f), PAIR, 2, GAP,
            PAD, HEIGHT, 20f, 0f, MARGIN, 1f, mTab, mButtons);
        assertTrue(mTab.isEmpty());
        assertTrue(mButtons[0].isEmpty());
    }

    @Test
    public void theWidthIsThePaddingTheGapsAndTheButtons() {
        assertEquals(PAIR_WIDTH, CornerTabGeometry.naturalWidth(PAIR, 2, GAP, PAD), 0.01f);
        assertEquals(40f, CornerTabGeometry.naturalWidth(PAIR, 1, GAP, PAD), 0.01f);
        assertEquals(0f, CornerTabGeometry.naturalWidth(PAIR, 0, GAP, PAD), 0.01f);
    }

    // ---------------------------------------------------------------- the rule itself

    /** The arc's own tangent: nothing at the edge it starts from, all of it once it has run out. */
    @Test
    public void theArcTangentIsWhereTheArcHasCurvedToAtThatDepth() {
        assertEquals("a square corner curves nowhere", 0f,
            CornerTabGeometry.arcTangentPx(0f, 24f), 0.001f);
        assertEquals("at the edge itself the arc is at its widest", 14f,
            CornerTabGeometry.arcTangentPx(14f, 0f), 0.001f);
        assertEquals("and by its own depth it has reached the side", 14f,
            CornerTabGeometry.arcTangentPx(14f, 14f), 0.001f);
        // A 28dp arc, 24dp down: 28 - sqrt(28^2 - 4^2).
        assertEquals(28f - (float) Math.sqrt(28f * 28f - 16f),
            CornerTabGeometry.arcTangentPx(28f, 24f), 0.001f);
        // Halfway down a 14dp arc: 14 - sqrt(14^2 - 7^2).
        assertEquals(14f - (float) Math.sqrt(196f - 49f),
            CornerTabGeometry.arcTangentPx(14f, 7f), 0.001f);
    }

    /**
     * And the room that arc takes from a tab: its full depth for any tab that crosses the whole of
     * it, the tab's own depth for one too shallow to — which still clears the tangent, and meets
     * the first rule continuously where the two change places.
     */
    @Test
    public void theArcClearanceCoversTheWholeArcATabCrosses() {
        assertEquals(0f, CornerTabGeometry.arcClearancePx(0f, 24f), 0.001f);
        assertEquals("a tab deeper than the arc starts past all of it",
            14f, CornerTabGeometry.arcClearancePx(14f, 24f), 0.001f);
        assertEquals("exactly as deep is the same answer",
            24f, CornerTabGeometry.arcClearancePx(24f, 24f), 0.001f);
        assertEquals("a tab shallower than the arc starts at its own depth",
            24f, CornerTabGeometry.arcClearancePx(28f, 24f), 0.001f);
        // No cliff either side of the crossover: a slider dragged through it moves the tab smoothly.
        assertEquals(CornerTabGeometry.arcClearancePx(23.9f, 24f),
            CornerTabGeometry.arcClearancePx(24.1f, 24f), 0.25f);
        for (float radius : RADII) {
            assertTrue("clearance " + radius + " must clear the arc at the tab's far corner",
                CornerTabGeometry.arcClearancePx(radius, 24f)
                    >= CornerTabGeometry.arcTangentPx(radius, 24f) - 0.001f);
        }
    }

    /** The border's inner edge turns a shallower arc than its outside, and never a negative one. */
    @Test
    public void theInnerRadiusIsTheArcTheBordersInsideTurns() {
        assertEquals(13f, CornerTabGeometry.innerRadiusPx(14f, 1f), 0.001f);
        assertEquals(14f, CornerTabGeometry.innerRadiusPx(14f, 0f), 0.001f);
        assertEquals("a stroke deeper than the radius leaves a square inner corner",
            0f, CornerTabGeometry.innerRadiusPx(1f, 4f), 0.001f);
    }

    /**
     * The inset, at every radius the surface editor offers and every stroke a surface wears: past
     * the arc, and half the tab's own outline again so that outline lands inside the border's line
     * rather than across it.
     */
    @Test
    public void theInsetIsTheArcPlusHalfTheTabsOwnOutline() {
        float outline = CornerTabGeometry.TAB_OUTLINE_DP;
        for (float radius : RADII) {
            for (float stroke : STROKES) {
                float inner = CornerTabGeometry.innerRadiusPx(radius, stroke);
                float expected = CornerTabGeometry.arcClearancePx(inner, 24f) + outline / 2f;
                assertEquals("radius " + radius + " stroke " + stroke, expected,
                    CornerTabGeometry.cornerInsetPx(radius, stroke, 24f, outline), 0.001f);
                assertTrue("the tab always starts inside its own outline",
                    CornerTabGeometry.cornerInsetPx(radius, stroke, 24f, outline) >= outline / 2f);
            }
        }
        // The 1-2dp the phone showed. Sideways the border's own width cancels — the stroke takes
        // one dp off the edge and gives the shallower inner arc the same dp back — so what moves
        // the tab in is half its outline, which used to hang over the border's line. Downwards it
        // moves the whole stroke: it now comes out from behind that line rather than from behind
        // the pixel column outside it.
        assertEquals("a plain split pane: 6dp radius, a 1dp stroke",
            6f + 0.5f, 1f + CornerTabGeometry.cornerInsetPx(6f, 1f, 24f, 1f), 0.001f);
        assertEquals("a glass pane: 14dp radius, the rim's 1.25dp",
            14f + 0.5f, 1.25f + CornerTabGeometry.cornerInsetPx(14f, 1.25f, 24f, 1f), 0.001f);
        assertEquals("and a square pane, which used to sit flat on its own edge",
            1.5f, 1f + CornerTabGeometry.cornerInsetPx(0f, 1f, 24f, 1f), 0.001f);
    }

    /** The ears are shortened to the room there is rather than flaring across the border. */
    @Test
    public void theEarsNeverReachPastTheBorderTheyFlareAlong() {
        float outline = CornerTabGeometry.TAB_OUTLINE_DP;
        float want = CornerTabGeometry.TAB_EAR_DP;
        for (float radius : RADII) {
            for (float stroke : STROKES) {
                float ear = CornerTabGeometry.earReachPx(want, radius, stroke, 24f, outline);
                assertTrue("radius " + radius + " stroke " + stroke + " ear " + ear,
                    ear >= 0f && ear <= want + 0.001f);
            }
        }
        assertEquals("a tab with no room has no ears", 0f,
            CornerTabGeometry.earReachPx(want, 0f, 1f, 24f, outline), 0.001f);
        assertEquals("and one asked for none keeps none", 0f,
            CornerTabGeometry.earReachPx(0f, 14f, 1f, 24f, outline), 0.001f);
    }

    /** The containment test the whole rule exists to pass, at every radius and every stroke. */
    @Test
    public void theTabAndItsEarsLieInsideTheBorderAtEveryRadiusAndStroke() {
        RectF pane = new RectF(0f, 0f, 600f, 400f);
        float outline = CornerTabGeometry.TAB_OUTLINE_DP;
        float tabHeight = 24f;
        float[] widths = {22.4f, 22.4f, 22.4f};
        RectF tab = new RectF();
        RectF[] buttons = {new RectF(), new RectF(), new RectF()};
        for (float radius : RADII) {
            for (float stroke : STROKES) {
                float inset = CornerTabGeometry.cornerInsetPx(radius, stroke, tabHeight, outline);
                float ear = CornerTabGeometry.earReachPx(CornerTabGeometry.TAB_EAR_DP, radius,
                    stroke, tabHeight, outline);
                for (int corner : CORNERS) {
                    CornerTabGeometry.layout(corner, pane, widths, 3, 0f, 2.4f, tabHeight, stroke,
                        inset, 3f, 1f, tab, buttons);
                    String where = "radius " + radius + " stroke " + stroke + " corner " + corner;
                    // The far edge — the one the tab actually shows a corner on.
                    float far = CornerZones.isTop(corner) ? tab.bottom : tab.top;
                    assertInside(where + " far outer corner", pane, radius, stroke,
                        CornerZones.isLeft(corner) ? tab.left - outline / 2f
                            : tab.right + outline / 2f, far);
                    assertInside(where + " far inner corner", pane, radius, stroke,
                        CornerZones.isLeft(corner) ? tab.right : tab.left, far);
                    // And the ear tip, which runs along the border's inner line where the arc is at
                    // its widest — taken at the far side of its own stroke, the deepest row it
                    // paints and the one the clip leaves standing.
                    float edge = CornerZones.isTop(corner)
                        ? pane.top + stroke + outline / 2f : pane.bottom - stroke - outline / 2f;
                    float tip = CornerZones.isLeft(corner)
                        ? tab.left - ear - outline / 2f : tab.right + ear + outline / 2f;
                    assertInside(where + " ear tip", pane, radius, stroke, tip, edge);
                    for (RectF button : buttons) {
                        assertTrue(where + " button inside the tab",
                            button.left >= tab.left - 0.001f && button.right <= tab.right + 0.001f);
                    }
                }
            }
        }
    }

    private static void assertInside(String what, RectF pane, float radius, float stroke,
                                     float x, float y) {
        assertTrue(what + " at (" + x + ", " + y + ")",
            CornerTabGeometry.insideBorder(pane, radius, stroke, x, y));
    }
}
