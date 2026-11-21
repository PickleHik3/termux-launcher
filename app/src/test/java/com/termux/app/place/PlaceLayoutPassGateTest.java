package com.termux.app.place;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The landing lays the place out once: the second pass on the same inputs is skipped. */
public class PlaceLayoutPassGateTest {

    private static final PlaceLayout LAYOUT = LayoutFixtures.HOME;

    @Test
    public void aSecondPassOnTheSameInputsInOneSettleIsRedundant() {
        PlaceLayoutPassGate gate = new PlaceLayoutPassGate();
        gate.begin();
        assertFalse("nothing has run yet", gate.redundant(LAYOUT, 3, 0, false, 7L));
        gate.ran(LAYOUT, 3, 0, false, 7L);
        assertTrue(gate.redundant(LAYOUT, 3, 0, false, 7L));
    }

    @Test
    public void anyInputThatMovedRunsThePass() {
        PlaceLayoutPassGate gate = new PlaceLayoutPassGate();
        gate.begin();
        gate.ran(LAYOUT, 3, 0, false, 7L);
        assertFalse(gate.redundant(LAYOUT, 4, 0, false, 7L));
        assertFalse(gate.redundant(LAYOUT, 3, 1, false, 7L));
        assertFalse(gate.redundant(LAYOUT, 3, 0, true, 7L));
        assertFalse(gate.redundant(LAYOUT, 3, 0, false, 8L));
        PlaceLayout other = new PlaceLayout(PlaceLayout.Edge.BOTTOM, PlaceLayout.RowPlacement.BOTTOM,
            false, PlaceLayout.Edge.BOTTOM, PlaceLayout.RowPlacement.HIDDEN,
            PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);
        assertFalse(gate.redundant(other, 3, 0, false, 7L));
    }

    @Test
    public void aPassOutsideASettleIsNeverRedundantAndNothingCarriesToTheNextSettle() {
        PlaceLayoutPassGate gate = new PlaceLayoutPassGate();
        gate.ran(LAYOUT, 3, 0, false, 7L);
        assertFalse("no settle is open", gate.redundant(LAYOUT, 3, 0, false, 7L));
        gate.begin();
        gate.ran(LAYOUT, 3, 0, false, 7L);
        gate.end();
        gate.begin();
        assertFalse("a new settle starts with no pass behind it",
            gate.redundant(LAYOUT, 3, 0, false, 7L));
    }

    @Test
    public void theLandingRunsTheLayoutOnce() {
        // The landing's two triggers, as the settle makes them: the commit's pass, then the
        // slide's own. The second asks the gate and finds the first's inputs unchanged.
        PlaceLayoutPassGate gate = new PlaceLayoutPassGate();
        int passes = 0;
        gate.begin();
        for (int trigger = 0; trigger < 2; trigger++) {
            if (gate.redundant(LAYOUT, 5, 0, true, 9L)) continue;
            passes++;
            gate.ran(LAYOUT, 5, 0, true, 9L);
        }
        gate.end();
        org.junit.Assert.assertEquals(1, passes);
    }
}
