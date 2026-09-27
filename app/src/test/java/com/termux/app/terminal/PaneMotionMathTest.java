package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the one fault the pane motion layer actually shipped with: a ghost or a cursor trail target
 * computed from a detached view's stale size. The cursor trail's own law is tested in
 * {@code com.termux.view.KittyCursorTrailTest} now that {@code KittyCursorTrail} owns it.
 */
public class PaneMotionMathTest {

    /**
     * The one that matters: a detached view keeps its last measured size, so a size-only check
     * passes while getLocationOnScreen reports 0,0 and every derived rect lands at the origin.
     */
    @Test
    public void measuredButDetachedIsNotAnimatable() {
        assertFalse(PaneMotionMath.canAnimate(false, true, 800, 600));
        assertFalse(PaneMotionMath.canAnimate(true, false, 800, 600));
        assertFalse(PaneMotionMath.canAnimate(true, true, 0, 600));
        assertTrue(PaneMotionMath.canAnimate(true, true, 800, 600));
    }
}
