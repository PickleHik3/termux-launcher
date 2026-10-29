package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The status bar's pull and fold off the top border, with and without the two-finger setting. */
public class BorderDragTwoFingerStatusTest {

    private static final float TOP = 200f;
    private static final float GAP = 36f;
    private static final float SLOP = 24f;
    private static final float MID_X = 550f;
    private static final float DOWN_Y = TOP - GAP / 2f;

    private static BorderDrag.Zone zone() {
        return new BorderDrag.Zone().page(100f, TOP, 1000f, 1800f).gaps(GAP, GAP, GAP, GAP)
            .minBand(18f).sideReach(6f).corner(120f);
    }

    private static BorderDrag press(boolean twoFingers) {
        BorderDrag drag = new BorderDrag();
        drag.setTwoFingerStatus(twoFingers);
        assertTrue(drag.down(MID_X, DOWN_Y, zone(), SLOP, true, true, true,
            Float.NEGATIVE_INFINITY));
        return drag;
    }

    @Test
    public void offOneFingerClaimsStatusAsBefore() {
        BorderDrag drag = press(false);
        assertEquals(BorderDrag.Claim.STATUS, drag.move(MID_X, DOWN_Y + SLOP + 5f));
    }

    @Test
    public void offSecondFingerStillAbandons() {
        BorderDrag drag = press(false);
        assertEquals(BorderDrag.Claim.ABANDONED, drag.secondPointer());
    }

    @Test
    public void onOneFingerNeverClaimsStatus() {
        BorderDrag down = press(true);
        assertEquals(BorderDrag.Claim.ABANDONED, down.move(MID_X, DOWN_Y + 200f));
        assertFalse(down.isStatusSwipe());
        BorderDrag up = press(true);
        assertEquals(BorderDrag.Claim.ABANDONED, up.move(MID_X, DOWN_Y - 200f));
    }

    @Test
    public void onSecondFingerClaimsStatusAndTheDragContinues() {
        BorderDrag drag = press(true);
        assertEquals(BorderDrag.Claim.STATUS, drag.secondPointer());
        assertTrue(drag.isStatusSwipe());
        assertEquals(BorderDrag.Claim.STATUS, drag.move(MID_X, DOWN_Y + 300f));
        assertEquals(BorderDrag.StatusSwipe.EXPAND,
            drag.statusRelease(DOWN_Y + 300f, 0f, 96f, 1500f));
    }

    @Test
    public void onSidewaysStillPagesWithOneFinger() {
        BorderDrag drag = press(true);
        assertEquals(BorderDrag.Claim.PAGING, drag.move(MID_X + 200f, DOWN_Y));
    }

    @Test
    public void onSecondFingerOffTheStatusZoneAbandons() {
        BorderDrag drag = new BorderDrag();
        drag.setTwoFingerStatus(true);
        // The bottom border is the keyboard's, never the status bar's.
        assertTrue(drag.down(MID_X, 1800f + GAP / 2f, zone(), SLOP, true, true, true,
            Float.NEGATIVE_INFINITY));
        assertEquals(BorderDrag.Claim.ABANDONED, drag.secondPointer());
    }
}
