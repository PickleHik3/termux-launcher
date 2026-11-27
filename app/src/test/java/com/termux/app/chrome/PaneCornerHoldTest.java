package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PaneCornerHoldTest {

    private static final float D = 2f;
    private final List<RectF> twoPanes = Arrays.asList(
        new RectF(0, 0, 500, 1000), new RectF(510, 0, 1000, 1000));

    @Test
    public void bottomRightArcOfEachPaneInASplit() {
        assertEquals(0, PaneCornerHold.pick(twoPanes, 2, 0, 480, 980, D, false, 12));
        assertEquals(1, PaneCornerHold.pick(twoPanes, 2, 0, 990, 990, D, false, 12));
    }

    @Test
    public void otherCornersAndTheMiddleAreTheTerminals() {
        assertEquals(-1, PaneCornerHold.pick(twoPanes, 2, 0, 10, 10, D, false, 12));
        assertEquals(-1, PaneCornerHold.pick(twoPanes, 2, 0, 10, 990, D, false, 12));
        assertEquals(-1, PaneCornerHold.pick(twoPanes, 2, 0, 490, 10, D, false, 12));
        assertEquals(-1, PaneCornerHold.pick(twoPanes, 2, 0, 250, 500, D, false, 12));
    }

    @Test
    public void theArcIsThirtyTwoDpSquare() {
        // 64 px at density 2: 440 is inside, 430 is not.
        assertEquals(0, PaneCornerHold.pick(twoPanes, 2, 0, 440, 940, D, false, 0));
        assertEquals(-1, PaneCornerHold.pick(twoPanes, 2, 0, 430, 940, D, false, 0));
        RectF out = new RectF();
        PaneCornerHold.rect(twoPanes.get(0), D, false, out);
        assertEquals(436f, out.left, 0f);
        assertEquals(936f, out.top, 0f);
        assertEquals(500f, out.right, 0f);
        assertEquals(1000f, out.bottom, 0f);
    }

    @Test
    public void aSinglePaneHasNoHold() {
        List<RectF> one = Arrays.asList(new RectF(0, 0, 1000, 1000));
        assertEquals(-1, PaneCornerHold.pick(one, 1, 0, 990, 990, D, false, 12));
        assertFalse(PaneCornerHold.offered(1));
        assertTrue(PaneCornerHold.offered(2));
    }

    @Test
    public void aMaximisedPaneKeepsItsHoldWhileItsSiblingsAreOffScreen() {
        List<RectF> shown = Arrays.asList(new RectF(0, 0, 1000, 1000));
        assertEquals(0, PaneCornerHold.pick(shown, 3, 0, 990, 990, D, false, 12));
    }

    @Test
    public void rightToLeftHoldsTheBottomLeft() {
        assertEquals(0, PaneCornerHold.pick(twoPanes, 2, 0, 10, 990, D, true, 12));
        assertEquals(-1, PaneCornerHold.pick(twoPanes, 2, 0, 490, 990, D, true, 12));
        assertEquals(CornerZones.BOTTOM_LEFT, PaneCornerHold.corner(true));
        assertEquals(CornerZones.BOTTOM_RIGHT, PaneCornerHold.corner(false));
    }

    @Test
    public void aSmallPaneClampsTheArcToHalfItsShorterSide() {
        List<RectF> small = Arrays.asList(new RectF(0, 0, 60, 40), new RectF(70, 0, 200, 40));
        // Half of 40 is 20 px: (50, 35) is in, (30, 35) is the middle.
        assertEquals(0, PaneCornerHold.pick(small, 2, 0, 50, 35, D, false, 0));
        assertEquals(-1, PaneCornerHold.pick(small, 2, 0, 30, 35, D, false, 0));
    }

    @Test
    public void dividerPixelsReachOnlyAnArcThatIsNearThem() {
        // x=505 is in the gap: only the first pane's bottom-right arc reaches it.
        assertEquals(0, PaneCornerHold.pick(twoPanes, 2, 1, 505, 990, D, false, 12));
    }
}
