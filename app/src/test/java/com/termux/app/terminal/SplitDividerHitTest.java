package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which divider a finger is down on: the gap between panes, or the least strip about its seam. */
public class SplitDividerHitTest {

    private static final Object OUTER = "outer";
    private static final Object INNER = "inner";
    /** The least touch strip, at density 1. */
    private static final float MIN = SplitDividerHit.MIN_TOUCH_DP;

    /** Two side-by-side panes, 0..495 and 505..1000, a 10 wide gutter between, 500 tall. */
    private static final SplitDividerHit.Gap VERTICAL =
        new SplitDividerHit.Gap(OUTER, true, 495f, 0f, 505f, 500f);
    private static final SplitDividerHit.Pane LEFT = new SplitDividerHit.Pane(0f, 0f, 495f, 500f);
    private static final SplitDividerHit.Pane RIGHT =
        new SplitDividerHit.Pane(505f, 0f, 1000f, 500f);

    @Test
    public void aPointInsideTheGapIsTheDivider() {
        List<SplitDividerHit.Gap> hit = SplitDividerHit.gapsAt(
            Collections.singletonList(VERTICAL), Arrays.asList(LEFT, RIGHT), 500f, 250f, MIN);
        assertEquals(1, hit.size());
        assertSame(OUTER, hit.get(0).token);
    }

    @Test
    public void theGapRunsTheWholeSharedEdge() {
        List<SplitDividerHit.Gap> gaps = Collections.singletonList(VERTICAL);
        List<SplitDividerHit.Pane> panes = Arrays.asList(LEFT, RIGHT);
        assertEquals(1, SplitDividerHit.gapsAt(gaps, panes, 500f, 0f, MIN).size());
        assertEquals(1, SplitDividerHit.gapsAt(gaps, panes, 495f, 499f, MIN).size());
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 500f, 500f, MIN).isEmpty());
    }

    @Test
    public void aPointOnAPanesContentIsNeverTheDivider() {
        List<SplitDividerHit.Gap> gaps = Collections.singletonList(VERTICAL);
        List<SplitDividerHit.Pane> panes = Arrays.asList(LEFT, RIGHT);
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 494f, 250f, MIN).isEmpty());
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 505f, 250f, MIN).isEmpty());
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 100f, 100f, MIN).isEmpty());
        // Even a gap that overlaps a pane (a stale frame) loses to the pane's content.
        SplitDividerHit.Gap fat = new SplitDividerHit.Gap(OUTER, true, 480f, 0f, 520f, 500f);
        assertTrue(SplitDividerHit.gapsAt(Collections.singletonList(fat), panes, 490f, 250f, MIN)
            .isEmpty());
    }

    /**
     * A split whose right half is split again top over bottom: the outer seam runs the full height,
     * the inner one runs across the right half only, and they meet at a T.
     */
    @Test
    public void nestedSplitsPickTheGapTheFingerIsIn() {
        SplitDividerHit.Gap horizontal =
            new SplitDividerHit.Gap(INNER, false, 505f, 245f, 1000f, 255f);
        List<SplitDividerHit.Gap> gaps = Arrays.asList(VERTICAL, horizontal);
        List<SplitDividerHit.Pane> panes = Arrays.asList(LEFT,
            new SplitDividerHit.Pane(505f, 0f, 1000f, 245f),
            new SplitDividerHit.Pane(505f, 255f, 1000f, 500f));

        assertSame(OUTER, SplitDividerHit.gapsAt(gaps, panes, 500f, 400f, MIN).get(0).token);
        assertSame(INNER, SplitDividerHit.gapsAt(gaps, panes, 800f, 250f, MIN).get(0).token);
        // The inner seam stops at the outer one: nothing to grab on the left half at that height.
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 200f, 250f, MIN).isEmpty());
    }

    @Test
    public void whereTwoGapsCrossTheFirstMovementDecides() {
        SplitDividerHit.Gap horizontal =
            new SplitDividerHit.Gap(INNER, false, 0f, 245f, 1000f, 255f);
        List<SplitDividerHit.Gap> gaps = Arrays.asList(VERTICAL, horizontal);
        List<SplitDividerHit.Pane> panes = Arrays.asList(
            new SplitDividerHit.Pane(0f, 0f, 495f, 245f),
            new SplitDividerHit.Pane(505f, 0f, 1000f, 245f),
            new SplitDividerHit.Pane(0f, 255f, 495f, 500f),
            new SplitDividerHit.Pane(505f, 255f, 1000f, 500f));

        List<SplitDividerHit.Gap> hit = SplitDividerHit.gapsAt(gaps, panes, 500f, 250f, MIN);
        assertEquals(2, hit.size());
        assertSame("along x moves the seam that runs top to bottom",
            OUTER, SplitDividerHit.pick(hit, 12f, 3f).token);
        assertSame("along y moves the seam that runs left to right",
            INNER, SplitDividerHit.pick(hit, -2f, -14f).token);
        assertSame("a lone candidate is the answer",
            INNER, SplitDividerHit.pick(Collections.singletonList(horizontal), 20f, 0f).token);
        assertNull(SplitDividerHit.pick(Collections.<SplitDividerHit.Gap>emptyList(), 1f, 1f));
    }

    /** Inner gap 0: the panes touch at 500, the divider has no thickness and still drags. */
    @Test
    public void aZeroGapStillTakesTheLeastStripAboutTheSeam() {
        SplitDividerHit.Gap seam = new SplitDividerHit.Gap(OUTER, true, 500f, 0f, 500f, 500f);
        List<SplitDividerHit.Gap> gaps = Collections.singletonList(seam);
        List<SplitDividerHit.Pane> panes = Arrays.asList(
            new SplitDividerHit.Pane(0f, 0f, 500f, 500f),
            new SplitDividerHit.Pane(500f, 0f, 1000f, 500f));
        assertSame(OUTER, SplitDividerHit.gapsAt(gaps, panes, 500f, 250f, MIN).get(0).token);
        assertEquals(1, SplitDividerHit.gapsAt(gaps, panes, 495f, 250f, MIN).size());
        assertEquals(1, SplitDividerHit.gapsAt(gaps, panes, 504.9f, 250f, MIN).size());
        // Past half the strip either side, the pane's content is the terminal's again.
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 494.9f, 250f, MIN).isEmpty());
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 505f, 250f, MIN).isEmpty());
        // Along the seam only: not past its ends.
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 500f, 500f, MIN).isEmpty());
    }

    /** Inner gap 2: the strip is still the least one, not the 2 the gap draws. */
    @Test
    public void aThinGapTakesTheLeastStripNotItsOwn() {
        SplitDividerHit.Gap thin = new SplitDividerHit.Gap(INNER, false, 0f, 249f, 1000f, 251f);
        List<SplitDividerHit.Gap> gaps = Collections.singletonList(thin);
        List<SplitDividerHit.Pane> panes = Arrays.asList(
            new SplitDividerHit.Pane(0f, 0f, 1000f, 249f),
            new SplitDividerHit.Pane(0f, 251f, 1000f, 500f));
        assertEquals(1, SplitDividerHit.gapsAt(gaps, panes, 300f, 250f, MIN).size());
        assertEquals(1, SplitDividerHit.gapsAt(gaps, panes, 300f, 245f, MIN).size());
        assertEquals(1, SplitDividerHit.gapsAt(gaps, panes, 300f, 254.9f, MIN).size());
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 300f, 244.9f, MIN).isEmpty());
        assertTrue(SplitDividerHit.gapsAt(gaps, panes, 300f, 255f, MIN).isEmpty());
    }
}
