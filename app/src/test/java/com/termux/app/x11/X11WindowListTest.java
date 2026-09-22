package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The next/previous step over the display's apps, as the keyboard's window actions take it. */
public class X11WindowListTest {

    @Test
    public void stepsWrapRoundAtBothEnds() {
        assertEquals(1, X11WindowList.neighbourIndex(0, 3, true));
        assertEquals(0, X11WindowList.neighbourIndex(2, 3, true));
        assertEquals(2, X11WindowList.neighbourIndex(0, 3, false));
        assertEquals(1, X11WindowList.neighbourIndex(2, 3, false));
    }

    @Test
    public void noFrontWindowStartsFromTheNearEnd() {
        assertEquals(0, X11WindowList.neighbourIndex(-1, 3, true));
        assertEquals(2, X11WindowList.neighbourIndex(-1, 3, false));
        // A stale index past the list counts as no front window.
        assertEquals(0, X11WindowList.neighbourIndex(7, 3, true));
    }

    @Test
    public void nothingToGoToWithoutWindows() {
        assertEquals(-1, X11WindowList.neighbourIndex(-1, 0, true));
        assertEquals(-1, X11WindowList.neighbourIndex(0, 0, false));
        // One window: the step lands where it started, which the caller treats as a no-op.
        assertEquals(0, X11WindowList.neighbourIndex(0, 1, true));
    }
}
