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
 * the edge that corner is on, starts past the frame's own corner arc, and never crosses the far
 * side however narrow the frame gets.
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

    private final RectF mTab = new RectF();
    private final RectF[] mButtons = {new RectF(), new RectF(), new RectF()};

    private void layout(int corner, RectF bounds, float inset, float progress) {
        CornerTabGeometry.layout(corner, bounds, PAIR, 2, GAP, PAD, HEIGHT, inset, MARGIN,
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
            0f, MARGIN, 1f, mTab, mButtons);
        assertTrue(mTab.isEmpty());
        assertTrue(mButtons[0].isEmpty());
    }

    @Test
    public void theWidthIsThePaddingTheGapsAndTheButtons() {
        assertEquals(PAIR_WIDTH, CornerTabGeometry.naturalWidth(PAIR, 2, GAP, PAD), 0.01f);
        assertEquals(40f, CornerTabGeometry.naturalWidth(PAIR, 1, GAP, PAD), 0.01f);
        assertEquals(0f, CornerTabGeometry.naturalWidth(PAIR, 0, GAP, PAD), 0.01f);
    }
}
