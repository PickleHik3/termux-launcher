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
}
