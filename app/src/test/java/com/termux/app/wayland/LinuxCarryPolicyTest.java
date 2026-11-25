package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LinuxCarryPolicyTest {

    private static final float STRIP = 50f;

    @Test
    public void theStripsAreTheWindowsSideEdgesAndTheMiddleIsNone() {
        assertEquals(-1, LinuxCarryPolicy.edgeAt(10f, 0f, 400f, STRIP));
        assertEquals(1, LinuxCarryPolicy.edgeAt(390f, 0f, 400f, STRIP));
        assertEquals(0, LinuxCarryPolicy.edgeAt(200f, 0f, 400f, STRIP));
        assertEquals(0, LinuxCarryPolicy.edgeAt(-5f, 0f, 400f, STRIP));
        assertEquals(0, LinuxCarryPolicy.edgeAt(5f, 0f, 0f, STRIP));
    }

    @Test
    public void aNarrowWindowGivesTheNearerEdge() {
        assertEquals(-1, LinuxCarryPolicy.edgeAt(30f, 0f, 80f, STRIP));
        assertEquals(1, LinuxCarryPolicy.edgeAt(60f, 0f, 80f, STRIP));
    }

    @Test
    public void thirdWindowCarriedToBetweenTheFirstAndSecond() {
        // Row [A B C], C (place 2) carried; B (place 1) in front; the left strip is the gap before B.
        LinuxCarryPolicy.Drop drop = LinuxCarryPolicy.decide(10f, 0f, 400f, STRIP, 1, 3, 2);
        assertEquals(LinuxCarryPolicy.Kind.BETWEEN, drop.kind);
        assertEquals(1, drop.to);
    }

    @Test
    public void carryingForwardLandsOneBeforeTheGapOnceLiftedOut() {
        // Row [A B C], A (0) carried to the gap after B (gap 2): the row reads [B A C].
        LinuxCarryPolicy.Drop drop = LinuxCarryPolicy.decide(395f, 0f, 400f, STRIP, 1, 3, 0);
        assertEquals(LinuxCarryPolicy.Kind.BETWEEN, drop.kind);
        assertEquals(1, drop.to);
    }

    @Test
    public void aDropWhereItAlreadyStandsReturns() {
        // Its own window in front: either strip is a gap next to itself.
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(10f, 0f, 400f, STRIP, 1, 3, 1).kind);
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(390f, 0f, 400f, STRIP, 1, 3, 1).kind);
        // The gap just after the window before it, which is the gap before it too.
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(390f, 0f, 400f, STRIP, 0, 3, 1).kind);
    }

    @Test
    public void aDropInTheMiddleOrOffTheRowReturns() {
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(200f, 0f, 400f, STRIP, 1, 3, 2).kind);
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(10f, 0f, 400f, STRIP, -1, 3, 2).kind);
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(10f, 0f, 400f, STRIP, 5, 3, 2).kind);
    }

    @Test
    public void thePreviewShowsExactlyTheGapsADropWouldUse() {
        assertEquals(1, LinuxCarryPolicy.previewGap(10f, 0f, 400f, STRIP, 1, 3, 2));
        assertEquals(-1, LinuxCarryPolicy.previewGap(390f, 0f, 400f, STRIP, 1, 3, 1));
        assertEquals(-1, LinuxCarryPolicy.previewGap(200f, 0f, 400f, STRIP, 1, 3, 2));
    }

    @Test
    public void everyGapOfTheRowMapsToAPlaceOrTheOwnSpot() {
        for (int carried = 0; carried < 4; carried++) {
            for (int gap = 0; gap <= 4; gap++) {
                int to = LinuxCarryPolicy.placeForGap(gap, carried, 4);
                boolean own = gap == carried || gap == carried + 1;
                assertEquals("carried " + carried + " gap " + gap, own, to < 0);
            }
        }
    }

    @Test
    public void aSecondFingerPagesOnlyPastTheThreshold() {
        assertEquals(1, LinuxCarryPolicy.pageSteps(-80f, 72f));
        assertEquals(-1, LinuxCarryPolicy.pageSteps(80f, 72f));
        assertEquals(0, LinuxCarryPolicy.pageSteps(40f, 72f));
    }

    // ---- split drops (#387) ----

    private static final float[] PANE = {0f, 0f, 400f, 800f};

    private static LinuxCarryPolicy.Drop over(float x, float y, int carried) {
        return LinuxCarryPolicy.decide(x, y, 0f, 400f, STRIP, 0, 1, carried, PANE);
    }

    @Test
    public void aTallPaneSplitsOnTheHalfAboveOrBelowTheFinger() {
        LinuxCarryPolicy.Drop top = over(200f, 100f, 0);
        assertEquals(LinuxCarryPolicy.Kind.SPLIT, top.kind);
        assertEquals(com.termux.app.terminal.DwindleTilingPolicy.SIDE_TOP, top.side);
        assertEquals(com.termux.app.terminal.DwindleTilingPolicy.SIDE_BOTTOM, over(200f, 700f, 0).side);
    }

    @Test
    public void aWidePaneSplitsOnTheLeftOrRightHalf() {
        float[] wide = {0f, 0f, 800f, 400f};
        LinuxCarryPolicy.Drop right = LinuxCarryPolicy.decide(600f, 200f, 0f, 800f, STRIP, 0, 1,
            LinuxCarryPolicy.FROM_PANE, wide);
        assertEquals(LinuxCarryPolicy.Kind.SPLIT, right.kind);
        assertEquals(com.termux.app.terminal.DwindleTilingPolicy.SIDE_RIGHT, right.side);
        assertEquals(com.termux.app.terminal.DwindleTilingPolicy.SIDE_LEFT,
            LinuxCarryPolicy.sideAt(100f, 200f, wide));
    }

    @Test
    public void theEdgeStripsStayTheGapsBetweenWindows() {
        // Row [A B C], C carried over B's left strip: still the gap, though a pane is under it.
        LinuxCarryPolicy.Drop drop = LinuxCarryPolicy.decide(10f, 300f, 0f, 400f, STRIP, 1, 3, 2, PANE);
        assertEquals(LinuxCarryPolicy.Kind.BETWEEN, drop.kind);
        assertEquals(1, drop.to);
    }

    @Test
    public void noPaneOrAFingerOffItReturnsTheWindow() {
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(200f, 300f, 0f, 400f, STRIP, 0, 1, 0, null).kind);
        assertEquals(LinuxCarryPolicy.Kind.RETURN, over(200f, 900f, 0).kind);
        // Nothing is in front (Home, the display): nothing to split.
        assertEquals(LinuxCarryPolicy.Kind.RETURN,
            LinuxCarryPolicy.decide(200f, 300f, 0f, 400f, STRIP, -1, 1, 0, PANE).kind);
    }

    @Test
    public void aPaneCarriedOutTakesTheGapItselfAsItsPlace() {
        // A pane is in no row place: gap g is the place g, in a row of three (gaps 0..3).
        LinuxCarryPolicy.Drop drop = LinuxCarryPolicy.decide(395f, 300f, 0f, 400f, STRIP, 1, 3,
            LinuxCarryPolicy.FROM_PANE, PANE);
        assertEquals(LinuxCarryPolicy.Kind.BETWEEN, drop.kind);
        assertEquals(2, drop.to);
        assertEquals(-1, LinuxCarryPolicy.placeForGap(4, LinuxCarryPolicy.FROM_PANE, 3));
    }

    @Test
    public void theHalfTheDropTakesIsTheOneUnderTheFinger() {
        float[] out = new float[4];
        LinuxCarryPolicy.halfOf(PANE, com.termux.app.terminal.DwindleTilingPolicy.SIDE_BOTTOM, out);
        assertEquals(400f, out[1], 0f);
        assertEquals(800f, out[3], 0f);
        LinuxCarryPolicy.halfOf(PANE, com.termux.app.terminal.DwindleTilingPolicy.SIDE_LEFT, out);
        assertEquals(200f, out[2], 0f);
        assertEquals(0f, out[0], 0f);
    }
}
