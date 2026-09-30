package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/** Docked edges touching the screen or a system-bar strip are seams and draw no stroke; the capsule is unchanged. */
public class ChromeEdgeRuleTest {

    private static final int TOP_ROW = ChromeEdgeRule.flushEdges(Edge.TOP);
    private static final int BOTTOM_ROW = ChromeEdgeRule.flushEdges(Edge.BOTTOM);

    @Test
    public void theEdgeBitsAreTheRefractionSeams() {
        assertEquals(GlassRefraction.SEAM_LEFT, ChromeEdgeRule.LEFT);
        assertEquals(GlassRefraction.SEAM_TOP, ChromeEdgeRule.TOP);
        assertEquals(GlassRefraction.SEAM_RIGHT, ChromeEdgeRule.RIGHT);
        assertEquals(GlassRefraction.SEAM_BOTTOM, ChromeEdgeRule.BOTTOM);
    }

    @Test
    public void aRowIsFlushWithItsEdgeAndBothSidesAColumnOnlyWithItsSide() {
        assertEquals(ChromeEdgeRule.TOP | ChromeEdgeRule.LEFT | ChromeEdgeRule.RIGHT, TOP_ROW);
        assertEquals(ChromeEdgeRule.BOTTOM | ChromeEdgeRule.LEFT | ChromeEdgeRule.RIGHT, BOTTOM_ROW);
        assertEquals(ChromeEdgeRule.LEFT, ChromeEdgeRule.flushEdges(Edge.LEFT));
        assertEquals(ChromeEdgeRule.RIGHT, ChromeEdgeRule.flushEdges(Edge.RIGHT));
        assertEquals(ChromeEdgeRule.BOTTOM, ChromeEdgeRule.innerEdge(Edge.TOP));
        assertEquals(ChromeEdgeRule.TOP, ChromeEdgeRule.innerEdge(Edge.BOTTOM));
    }

    @Test
    public void dockedEveryOuterAndJoinedEdgeIsASeam() {
        assertEquals("the status strip: the screen on three sides, the bar below",
            ChromeEdgeRule.ALL, ChromeEdgeRule.seams(false, TOP_ROW, ChromeEdgeRule.BOTTOM));
        assertEquals("the under-pill strip: the screen on three sides, the keyboard above",
            ChromeEdgeRule.ALL, ChromeEdgeRule.seams(false, BOTTOM_ROW, ChromeEdgeRule.TOP));
        assertEquals("the leading window bar keeps its rim only along the terminal",
            ChromeEdgeRule.ALL & ~ChromeEdgeRule.BOTTOM,
            ChromeEdgeRule.seams(false, ChromeEdgeRule.LEFT | ChromeEdgeRule.RIGHT,
                ChromeEdgeRule.TOP));
    }

    @Test
    public void theCapsuleOnlySeamsWhereGlassJoinsGlass() {
        assertEquals(0, ChromeEdgeRule.seams(true, TOP_ROW, 0));
        assertEquals(ChromeEdgeRule.BOTTOM, ChromeEdgeRule.seams(true, BOTTOM_ROW,
            ChromeEdgeRule.BOTTOM));
    }

    @Test
    public void dockedTheStrokeRunsOnlyAlongTheInnerEdge() {
        assertEquals(ChromeEdgeRule.BOTTOM,
            ChromeEdgeRule.strokeEdges(false, true, true, TOP_ROW, 0));
        assertEquals("a window bar under another band keeps its top line",
            ChromeEdgeRule.TOP | ChromeEdgeRule.BOTTOM,
            ChromeEdgeRule.strokeEdges(false, true, true,
                ChromeEdgeRule.LEFT | ChromeEdgeRule.RIGHT, 0));
        assertEquals("an inner edge that never had a stroke gets none",
            ChromeEdgeRule.NONE, ChromeEdgeRule.strokeEdges(false, true, false, BOTTOM_ROW, 0));
        assertFalse(ChromeEdgeRule.strokes(false, true, false, BOTTOM_ROW, 0));
        assertEquals("a docked card standing clear keeps its whole rim",
            ChromeEdgeRule.ALL, ChromeEdgeRule.strokeEdges(false, true, true, 0, 0));
    }

    @Test
    public void theCapsuleKeepsItsWholeStroke() {
        assertEquals(ChromeEdgeRule.ALL, ChromeEdgeRule.strokeEdges(true, true, false, TOP_ROW, 0));
        assertEquals(ChromeEdgeRule.NONE,
            ChromeEdgeRule.strokeEdges(true, false, true, TOP_ROW, 0));
    }

    @Test
    public void onlyADockedSurfaceWithAnOuterEdgeSquaresItsOuterCorners() {
        assertTrue(ChromeEdgeRule.innerCornersOnly(false, ChromeEdgeRule.TOP));
        assertFalse(ChromeEdgeRule.innerCornersOnly(false, 0));
        assertFalse(ChromeEdgeRule.innerCornersOnly(true, ChromeEdgeRule.TOP));
    }

    @Test
    public void theStatusInsetStripShowsOnlyDockedWithAnInsetAndALead() {
        ChromeEdgeRule.TopLead bar = ChromeEdgeRule.TopLead.WINDOW_BAR;
        ChromeEdgeRule.TopLead sheet = ChromeEdgeRule.TopLead.DOCK_SHEET;
        assertEquals(bar, ChromeEdgeRule.statusInsetLead(false, false, 80, bar));
        assertEquals(sheet, ChromeEdgeRule.statusInsetLead(false, false, 80, sheet));
        assertEquals(ChromeEdgeRule.TopLead.NONE,
            ChromeEdgeRule.statusInsetLead(true, false, 80, bar));
        assertEquals(ChromeEdgeRule.TopLead.NONE,
            ChromeEdgeRule.statusInsetLead(false, true, 80, bar));
        assertEquals(ChromeEdgeRule.TopLead.NONE,
            ChromeEdgeRule.statusInsetLead(false, false, 0, sheet));
        assertEquals(ChromeEdgeRule.TopLead.NONE,
            ChromeEdgeRule.statusInsetLead(false, false, 80, ChromeEdgeRule.TopLead.NONE));
    }

    @Test
    public void theLightModelSplitsByHeight() {
        assertEquals(0.25f, ChromeEdgeRule.stripFraction(40, 120), 1e-6f);
        assertEquals(0f, ChromeEdgeRule.stripFraction(0, 120), 0f);
        assertEquals(0f, ChromeEdgeRule.stripFraction(0, 0), 0f);
    }
}
