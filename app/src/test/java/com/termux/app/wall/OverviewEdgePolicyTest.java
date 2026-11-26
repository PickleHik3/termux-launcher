package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Overview before the first page (#356): the drag's pose and the settle's landing come from one
 * policy, and the landing is the wall's own page rule.
 */
public class OverviewEdgePolicyTest {

    private static final int WIDTH = 1000;
    private static final float DENSITY = 2.75f;
    private static final float EPS = 0.001f;

    @Test
    public void thePullFollowsTheFingerOneToOneUpToAWidth() {
        assertEquals(0f, OverviewEdgePolicy.reveal(OverviewEdgePolicy.PULL, 0f, WIDTH), EPS);
        assertEquals(300f, OverviewEdgePolicy.reveal(OverviewEdgePolicy.PULL, 300f, WIDTH), EPS);
        assertEquals("never past Overview's rest", WIDTH,
            OverviewEdgePolicy.reveal(OverviewEdgePolicy.PULL, 1500f, WIDTH), EPS);
        assertEquals("never back past the first page's rest", 0f,
            OverviewEdgePolicy.reveal(OverviewEdgePolicy.PULL, -200f, WIDTH), EPS);
        // The edge it replaces only ever took a share of the finger.
        assertTrue(OverviewEdgePolicy.reveal(OverviewEdgePolicy.PULL, 300f, WIDTH)
            > PaneWallLayoutOffsets.resisted(300f));
    }

    @Test
    public void theReturnStartsWithOverviewOpenAndFollowsTheFinger() {
        assertEquals(WIDTH, OverviewEdgePolicy.reveal(OverviewEdgePolicy.RETURN, 0f, WIDTH), EPS);
        assertEquals(WIDTH - 400f,
            OverviewEdgePolicy.reveal(OverviewEdgePolicy.RETURN, -400f, WIDTH), EPS);
        assertEquals(0f, OverviewEdgePolicy.reveal(OverviewEdgePolicy.RETURN, -3000f, WIDTH), EPS);
        assertEquals(WIDTH, OverviewEdgePolicy.reveal(OverviewEdgePolicy.RETURN, 200f, WIDTH), EPS);
    }

    /**
     * Parity: at every point of a drag Overview's right edge is the first page's left edge, and
     * the poses a drag reaches at either end are exactly the rests the settle lands on, the open
     * one being where every other way in puts Overview (no slide).
     */
    @Test
    public void theDragsPoseAndTheSettlesRestAreOneWall() {
        for (int swipe : new int[] {OverviewEdgePolicy.PULL, OverviewEdgePolicy.RETURN}) {
            for (float dx = -1200f; dx <= 1200f; dx += 37f) {
                float reveal = OverviewEdgePolicy.reveal(swipe, dx, WIDTH);
                float overviewRight = OverviewEdgePolicy.overviewTranslationX(reveal, WIDTH) + WIDTH;
                assertEquals(OverviewEdgePolicy.pageOffset(reveal), overviewRight, EPS);
            }
        }
        float fullPull = OverviewEdgePolicy.reveal(OverviewEdgePolicy.PULL, WIDTH, WIDTH);
        assertEquals(OverviewEdgePolicy.restReveal(true, WIDTH), fullPull, EPS);
        float fullReturn = OverviewEdgePolicy.reveal(OverviewEdgePolicy.RETURN, -WIDTH, WIDTH);
        assertEquals(OverviewEdgePolicy.restReveal(false, WIDTH), fullReturn, EPS);
        assertEquals("open in place stands where the settle lands", 0f,
            OverviewEdgePolicy.overviewTranslationX(OverviewEdgePolicy.restReveal(true, WIDTH),
                WIDTH), EPS);
        assertEquals("closed, the first page is at its rest", 0f,
            OverviewEdgePolicy.pageOffset(OverviewEdgePolicy.restReveal(false, WIDTH)), EPS);
        assertEquals(OverviewEdgePolicy.startReveal(OverviewEdgePolicy.PULL, WIDTH),
            OverviewEdgePolicy.restReveal(false, WIDTH), EPS);
        assertEquals(OverviewEdgePolicy.startReveal(OverviewEdgePolicy.RETURN, WIDTH),
            OverviewEdgePolicy.restReveal(true, WIDTH), EPS);
    }

    /** Parity with the wall: the pull lands as a drag onto a page on the left would. */
    @Test
    public void thePullSettlesByTheWallsRule() {
        float[] velocities = {-6000f, -1500f, -700f, -100f, 0f, 100f, 700f, 1100f, 1500f, 6000f};
        for (float reveal = 0f; reveal <= WIDTH; reveal += 25f) {
            for (float v : velocities) {
                for (float lead : new float[] {0f, 30f}) {
                    boolean wallCommits = PaneWallPolicy.settle(reveal, v, WIDTH, DENSITY,
                        true, true, lead) == -1;
                    assertEquals("reveal " + reveal + " v " + v, wallCommits,
                        OverviewEdgePolicy.settlesOpen(OverviewEdgePolicy.PULL, reveal, v, WIDTH,
                            DENSITY, lead));
                }
            }
        }
    }

    /** And the return lands as a drag from the left page onto the first page would. */
    @Test
    public void theReturnSettlesByTheWallsRule() {
        float[] velocities = {-6000f, -1500f, -700f, -100f, 0f, 100f, 700f, 1500f, 6000f};
        for (float reveal = 0f; reveal <= WIDTH; reveal += 25f) {
            for (float v : velocities) {
                boolean wallCommits = PaneWallPolicy.settle(reveal - WIDTH, v, WIDTH, DENSITY,
                    true, true, 0f) == 1;
                assertEquals("reveal " + reveal + " v " + v, !wallCommits,
                    OverviewEdgePolicy.settlesOpen(OverviewEdgePolicy.RETURN, reveal, v, WIDTH,
                        DENSITY, 0f));
            }
        }
    }

    @Test
    public void distanceOrAFlingOpensAndShortOfEitherSpringsBack() {
        int pull = OverviewEdgePolicy.PULL;
        assertFalse("a short slow pull springs back",
            OverviewEdgePolicy.settlesOpen(pull, 200f, 0f, WIDTH, DENSITY, 0f));
        assertTrue("past the commit fraction opens",
            OverviewEdgePolicy.settlesOpen(pull, 500f, 0f, WIDTH, DENSITY, 0f));
        assertTrue("a short fast flick opens",
            OverviewEdgePolicy.settlesOpen(pull, 120f, 3000f, WIDTH, DENSITY, 0f));
        assertFalse("a far pull flung back springs back",
            OverviewEdgePolicy.settlesOpen(pull, 600f, -3000f, WIDTH, DENSITY, 0f));
        int back = OverviewEdgePolicy.RETURN;
        assertTrue("a short return springs back to Overview",
            OverviewEdgePolicy.settlesOpen(back, WIDTH - 200f, 0f, WIDTH, DENSITY, 0f));
        assertFalse("a long return lands on the first page",
            OverviewEdgePolicy.settlesOpen(back, WIDTH - 600f, 0f, WIDTH, DENSITY, 0f));
        assertFalse("a quick flick back lands on the first page",
            OverviewEdgePolicy.settlesOpen(back, WIDTH - 120f, -3000f, WIDTH, DENSITY, 0f));
    }

    @Test
    public void theChromeGoesAsOverviewCoversIt() {
        assertEquals(1f, OverviewEdgePolicy.chromeShown(0f, WIDTH), EPS);
        assertEquals(0.5f, OverviewEdgePolicy.chromeShown(WIDTH / 2f, WIDTH), EPS);
        assertEquals(0f, OverviewEdgePolicy.chromeShown(WIDTH, WIDTH), EPS);
        assertEquals(1f, OverviewEdgePolicy.chromeShown(100f, 0), EPS);
    }

    /** The edge the pull replaces, kept here only to show the pull is no longer resisted. */
    private static final class PaneWallLayoutOffsets {
        static float resisted(float dx) {
            return PaneWallPolicy.offsetForDrag(dx, WIDTH, false, true);
        }
    }
}
