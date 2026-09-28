package com.termux.app.launcher.az;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.CornerZones;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.BorderDrag;

import org.junit.Test;

/** Where the minimised index's pull tab stands, what takes it, and where the letters slide. */
public class AzTabPolicyTest {

    private static final float D = 2f;
    private static final float W = 400f * D;
    private static final float H = 700f * D;

    @Test
    public void aBottomTabRestsOnTheEdgeAtTheLeadingEndPastTheCorner() {
        AzTabPolicy.Placement tab = AzTabPolicy.place(Edge.BOTTOM, false, W, H, D);
        assertEquals(AzTabPolicy.leadInPx(D), tab.touch.left, 0f);
        assertEquals(H, tab.touch.bottom, 0f);
        assertEquals(AzTabPolicy.TOUCH_LENGTH_DP * D, tab.touch.width(), 0f);
        assertEquals(AzTabPolicy.TOUCH_THICKNESS_DP * D, tab.touch.height(), 0f);
        // The pill is the design's 48 x 28, inside the touch area, off the edge by its gap.
        assertEquals(AzTabPolicy.TAB_LENGTH_DP * D, tab.visual.width(), 0f);
        assertEquals(AzTabPolicy.TAB_THICKNESS_DP * D, tab.visual.height(), 0f);
        assertEquals(H - AzTabPolicy.TAB_EDGE_GAP_DP * D, tab.visual.bottom, 0f);
        assertTrue(tab.touch.left <= tab.visual.left && tab.visual.right <= tab.touch.right);
    }

    @Test
    public void theTouchAreaIsAFullTargetEveryWay() {
        for (Edge edge : Edge.values()) {
            AzTabPolicy.Placement tab = AzTabPolicy.place(edge, false, W, H, D);
            assertTrue(edge + " is 48dp at least",
                Math.min(tab.touch.width(), tab.touch.height()) >= 48f * D);
        }
    }

    @Test
    public void theTabNeverTouchesTheCornerSquare() {
        float corner = CornerZones.paneSizePx(D);
        for (Edge edge : Edge.values()) {
            for (boolean rtl : new boolean[] {false, true}) {
                AzTabPolicy.Placement tab = AzTabPolicy.place(edge, rtl, W, H, D);
                for (int c = 0; c < 4; c++) {
                    float x = CornerZones.isLeft(c) ? corner / 2f : W - corner / 2f;
                    float y = CornerZones.isTop(c) ? corner / 2f : H - corner / 2f;
                    assertFalse(edge + " rtl=" + rtl + " corner " + c, tab.hit(x, y));
                }
                // And the whole touch area is clear of the corner squares.
                boolean inCornerColumn = tab.touch.left < corner || tab.touch.right > W - corner;
                boolean inCornerRow = tab.touch.top < corner || tab.touch.bottom > H - corner;
                assertFalse(edge + " rtl=" + rtl, inCornerColumn && inCornerRow);
            }
        }
    }

    @Test
    public void theTabSitsInTheBorderBandSoItHasToTakeTheTouchFirst() {
        // The border hold-drag reaches 24dp either side of the page's edge: the tab is on it, which
        // is why the tab's view takes the DOWN before the wall ever sees it.
        AzTabPolicy.Placement tab = AzTabPolicy.place(Edge.BOTTOM, false, W, H, D);
        float x = tab.touch.left + tab.touch.width() / 2f;
        float y = H - 4f * D;
        assertTrue(tab.hit(x, y));
        assertEquals(BorderDrag.Border.BOTTOM, BorderDrag.borderAt(x, y, 0f, 0f, W, H,
            BorderDrag.BAND_DP * D, CornerZones.paneSizePx(D)));
    }

    @Test
    public void aRightToLeftRowLeadsFromTheRightAndAColumnAlwaysFromTheTop() {
        AzTabPolicy.Placement rtl = AzTabPolicy.place(Edge.TOP, true, W, H, D);
        assertEquals(W - AzTabPolicy.leadInPx(D), rtl.touch.right, 0f);
        assertEquals(0f, rtl.touch.top, 0f);

        AzTabPolicy.Placement left = AzTabPolicy.place(Edge.LEFT, true, W, H, D);
        assertEquals(AzTabPolicy.leadInPx(D), left.touch.top, 0f);
        assertEquals(0f, left.touch.left, 0f);
        assertEquals(AzTabPolicy.TAB_THICKNESS_DP * D, left.visual.width(), 0f);
        assertEquals(AzTabPolicy.TAB_LENGTH_DP * D, left.visual.height(), 0f);

        AzTabPolicy.Placement right = AzTabPolicy.place(Edge.RIGHT, false, W, H, D);
        assertEquals(W, right.touch.right, 0f);
        assertEquals(W - AzTabPolicy.TAB_EDGE_GAP_DP * D, right.visual.right, 0f);
    }

    @Test
    public void aShortCanvasGivesUpTheLeadInBeforeTheTab() {
        float length = 70f * D;
        AzTabPolicy.Placement tab = AzTabPolicy.place(Edge.BOTTOM, false, length, H, D);
        assertEquals(AzTabPolicy.TOUCH_LENGTH_DP * D, tab.touch.width(), 0f);
        assertEquals(length, tab.touch.right, 0f);

        AzTabPolicy.Placement tiny = AzTabPolicy.place(Edge.BOTTOM, false, 30f * D, H, D);
        assertEquals(0f, tiny.touch.left, 0f);
        assertEquals(30f * D, tiny.touch.right, 0f);
        assertTrue(tiny.visual.width() <= 30f * D);
    }

    @Test
    public void anEmptyCanvasHasNothingToHit() {
        AzTabPolicy.Placement tab = AzTabPolicy.place(Edge.BOTTOM, false, 0f, 0f, D);
        assertTrue(tab.touch.isEmpty());
        assertFalse(tab.hit(0f, 0f));
    }

    @Test
    public void theLettersComeOutAlongTheWholeEdgeInTheBarsOwnAir() {
        float thickness = 29f * D;
        float margin = 10f * D;
        float inset = 12f * D;
        AzTabPolicy.Box row = AzTabPolicy.revealBox(Edge.BOTTOM, W, H, thickness, margin, inset);
        assertEquals(new AzTabPolicy.Box(inset, H - margin - thickness, W - inset, H - margin),
            row);

        AzTabPolicy.Box top = AzTabPolicy.revealBox(Edge.TOP, W, H, thickness, margin, inset);
        assertEquals(margin, top.top, 0f);
        assertEquals(margin + thickness, top.bottom, 0f);

        AzTabPolicy.Box column = AzTabPolicy.revealBox(Edge.RIGHT, W, H, thickness, margin, inset);
        assertEquals(new AzTabPolicy.Box(W - margin - thickness, margin, W - margin, H - margin),
            column);
    }

    @Test
    public void theLettersCoverTheTabWhenTheyAreOut() {
        float thickness = 29f * D;
        float margin = 10f * D;
        for (Edge edge : Edge.values()) {
            AzTabPolicy.Placement tab = AzTabPolicy.place(edge, false, W, H, D);
            AzTabPolicy.Box row = AzTabPolicy.revealBox(edge, W, H, thickness, margin, 12f * D);
            float cx = (tab.visual.left + tab.visual.right) / 2f;
            float cy = (tab.visual.top + tab.visual.bottom) / 2f;
            assertTrue(edge + ": a finger resting on the tab is on the letters",
                row.contains(cx, cy));
        }
    }

    @Test
    public void tuckedTheLettersStandOneTravelPastTheirEdge() {
        float travel = AzTabPolicy.travelPx(29f, 10f);
        assertEquals(39f, travel, 0f);
        assertArrayEquals(new float[] {0f, 39f},
            AzTabPolicy.slideOffset(Edge.BOTTOM, 0f, travel), 0f);
        assertArrayEquals(new float[] {0f, -39f},
            AzTabPolicy.slideOffset(Edge.TOP, 0f, travel), 0f);
        assertArrayEquals(new float[] {-39f, 0f},
            AzTabPolicy.slideOffset(Edge.LEFT, 0f, travel), 0f);
        assertArrayEquals(new float[] {39f, 0f},
            AzTabPolicy.slideOffset(Edge.RIGHT, 0f, travel), 0f);
        assertArrayEquals(new float[] {0f, 0f},
            AzTabPolicy.slideOffset(Edge.BOTTOM, 1f, travel), 0f);
        assertArrayEquals(new float[] {0f, 19.5f},
            AzTabPolicy.slideOffset(Edge.BOTTOM, 0.5f, travel), 1e-4f);
    }
}
