package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The squares a frame keeps and the edges it gives away. Everything the wall's frames decide on a
 * touch down comes through here, so this is where the rules are held: four corners, nothing in
 * the middle of an edge, never more than half a small frame, and a shared divider that belongs to
 * the panes on both sides of it.
 */
@RunWith(RobolectricTestRunner.class)
public class CornerZonesTest {

    private static final float SIZE = 32f;

    @Test
    public void eachCornerAnswersForItsOwnSquare() {
        assertEquals(CornerZones.TOP_LEFT, CornerZones.cornerAt(0f, 0f, 600f, 800f, SIZE));
        assertEquals(CornerZones.TOP_RIGHT, CornerZones.cornerAt(600f, 0f, 600f, 800f, SIZE));
        assertEquals(CornerZones.BOTTOM_RIGHT, CornerZones.cornerAt(600f, 800f, 600f, 800f, SIZE));
        assertEquals(CornerZones.BOTTOM_LEFT, CornerZones.cornerAt(0f, 800f, 600f, 800f, SIZE));
        // The far corner of each square still counts; one pixel past it does not.
        assertEquals(CornerZones.TOP_LEFT, CornerZones.cornerAt(SIZE, SIZE, 600f, 800f, SIZE));
        assertEquals(CornerZones.NONE, CornerZones.cornerAt(SIZE + 1f, SIZE + 1f, 600f, 800f, SIZE));
    }

    @Test
    public void theEdgesBelongToTheContent() {
        assertEquals("the middle of the leading edge",
            CornerZones.NONE, CornerZones.cornerAt(0f, 400f, 600f, 800f, SIZE));
        assertEquals("the middle of the top edge",
            CornerZones.NONE, CornerZones.cornerAt(300f, 0f, 600f, 800f, SIZE));
        assertEquals("the middle of the trailing edge",
            CornerZones.NONE, CornerZones.cornerAt(600f, 400f, 600f, 800f, SIZE));
        assertEquals("the middle of the bottom edge",
            CornerZones.NONE, CornerZones.cornerAt(300f, 800f, 600f, 800f, SIZE));
        assertEquals("the middle of the frame",
            CornerZones.NONE, CornerZones.cornerAt(300f, 400f, 600f, 800f, SIZE));
        // A band's worth in from the edge, which the old 12dp band would have taken.
        assertEquals(CornerZones.NONE, CornerZones.cornerAt(6f, 400f, 600f, 800f, SIZE));
    }

    @Test
    public void aTouchOutsideTheFrameIsNobodys() {
        assertEquals(CornerZones.NONE, CornerZones.cornerAt(-1f, -1f, 600f, 800f, SIZE));
        assertEquals(CornerZones.NONE, CornerZones.cornerAt(601f, 10f, 600f, 800f, SIZE));
    }

    @Test
    public void aSmallFrameKeepsFourCornersAndNoMiddle() {
        // Forty pixels across: a 32 square from each side would swallow the whole frame, so each
        // one is cut to half and the centre is still the content's.
        assertEquals(20f, CornerZones.clampSize(SIZE, 40f, 200f), 0f);
        assertEquals(CornerZones.TOP_LEFT, CornerZones.cornerAt(0f, 0f, 40f, 200f, SIZE));
        assertEquals(CornerZones.TOP_RIGHT, CornerZones.cornerAt(40f, 0f, 40f, 200f, SIZE));
        assertEquals(CornerZones.BOTTOM_LEFT, CornerZones.cornerAt(0f, 200f, 40f, 200f, SIZE));
        assertEquals(CornerZones.NONE, CornerZones.cornerAt(20f, 100f, 40f, 200f, SIZE));
        assertEquals("a frame with no size has no corners",
            CornerZones.NONE, CornerZones.cornerAt(0f, 0f, 0f, 0f, SIZE));
    }

    @Test
    public void aFrameSomewhereElseAnswersInItsParentsCoordinates() {
        RectF pane = new RectF(100f, 200f, 500f, 600f);
        assertEquals(CornerZones.TOP_LEFT, CornerZones.cornerAt(105f, 205f, pane, SIZE, 0f));
        assertEquals(CornerZones.BOTTOM_RIGHT, CornerZones.cornerAt(495f, 595f, pane, SIZE, 0f));
        assertEquals(CornerZones.NONE, CornerZones.cornerAt(300f, 205f, pane, SIZE, 0f));
        assertEquals("a hair outside, with no slop", CornerZones.NONE,
            CornerZones.cornerAt(98f, 205f, pane, SIZE, 0f));
        assertEquals("the same point, with the divider's slop", CornerZones.TOP_LEFT,
            CornerZones.cornerAt(98f, 205f, pane, SIZE, 4f));
    }

    @Test
    public void theCornerSquareIsWhereTheBracketGoes() {
        RectF pane = new RectF(100f, 200f, 500f, 600f);
        RectF out = new RectF();
        CornerZones.cornerRect(CornerZones.TOP_LEFT, pane, SIZE, out);
        assertEquals(new RectF(100f, 200f, 132f, 232f), out);
        CornerZones.cornerRect(CornerZones.BOTTOM_RIGHT, pane, SIZE, out);
        assertEquals(new RectF(468f, 568f, 500f, 600f), out);
    }

    @Test
    public void leadingAndTrailingFollowTheLayoutDirection() {
        assertEquals(CornerZones.TOP_LEFT, CornerZones.corner(true, true, false));
        assertEquals(CornerZones.TOP_RIGHT, CornerZones.corner(true, true, true));
        assertEquals(CornerZones.BOTTOM_RIGHT, CornerZones.corner(false, false, false));
        assertEquals(CornerZones.BOTTOM_LEFT, CornerZones.corner(false, false, true));
        assertTrue(CornerZones.isTop(CornerZones.TOP_RIGHT));
        assertTrue(CornerZones.isLeft(CornerZones.BOTTOM_LEFT));
    }

    /** Two panes side by side with a four-pixel divider between them. */
    private static List<RectF> sideBySide() {
        return Arrays.asList(new RectF(0f, 0f, 298f, 800f), new RectF(302f, 0f, 600f, 800f));
    }

    @Test
    public void aPaneThatHoldsThePointWinsOverOneThatOnlyReachesIt() {
        List<RectF> panes = sideBySide();
        CornerZones.Hit hit = CornerZones.pick(panes, 1, 296f, 4f, SIZE, 8f);
        assertNotNull(hit);
        assertEquals("the pane the finger is actually on", 0, hit.index);
        assertEquals(CornerZones.TOP_RIGHT, hit.corner);
    }

    @Test
    public void theDividersEmptyPixelsGoToTheFocusedPane() {
        List<RectF> panes = sideBySide();
        CornerZones.Hit first = CornerZones.pick(panes, 0, 300f, 2f, SIZE, 8f);
        assertNotNull(first);
        assertEquals("the focused pane keeps the seam", 0, first.index);
        assertEquals(CornerZones.TOP_RIGHT, first.corner);

        CornerZones.Hit second = CornerZones.pick(panes, 1, 300f, 2f, SIZE, 8f);
        assertNotNull(second);
        assertEquals(1, second.index);
        assertEquals(CornerZones.TOP_LEFT, second.corner);
    }

    @Test
    public void nothingIsPickedInTheMiddleOfASeam() {
        assertNull(CornerZones.pick(sideBySide(), 0, 300f, 400f, SIZE, 8f));
        assertNull(CornerZones.pick(Collections.emptyList(), 0, 0f, 0f, SIZE, 8f));
    }
}
