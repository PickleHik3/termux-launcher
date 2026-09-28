package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The drag a grab handle makes, shared by the floating keyboard and the dictation pill. */
public class GrabHandleTest {

    @Test
    public void theCardFollowsTheFingerFromWhereItStarted() {
        GrabHandle.Drag drag = new GrabHandle.Drag();
        assertFalse(drag.isActive());
        drag.begin(500f, 900f, 120, 40);
        assertTrue(drag.isActive());
        assertEquals(120, drag.x(500f));
        assertEquals(40, drag.y(900f));
        assertEquals(90, drag.x(470.4f));
        assertEquals(300, drag.y(1160f));
        drag.end();
        assertFalse(drag.isActive());
    }

    @Test
    public void theSlopIsMeasuredFromWhereTheFingerCameDown() {
        GrabHandle.Drag drag = new GrabHandle.Drag();
        drag.begin(100f, 100f, 0, 0);
        assertFalse(drag.isPast(103f, 104f, 5f));
        assertTrue(drag.isPast(104f, 104f, 5f));
    }

    @Test
    public void thePillNeverThinsBelowTwoPixels() {
        assertEquals(10, GrabHandle.pillHeightPx(3f));
        assertEquals(2, GrabHandle.pillHeightPx(0.1f));
    }
}
