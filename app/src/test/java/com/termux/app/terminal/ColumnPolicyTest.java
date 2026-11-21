package com.termux.app.terminal;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ColumnPolicyTest {

    @Test
    public void sideDefaultsToRightAndRefusesAnythingButLeftOrRight() {
        assertEquals(ColumnPolicy.RIGHT, ColumnPolicy.side(null));
        assertEquals(ColumnPolicy.RIGHT, ColumnPolicy.side(""));
        assertEquals(ColumnPolicy.RIGHT, ColumnPolicy.side(" Right "));
        assertEquals(ColumnPolicy.LEFT, ColumnPolicy.side("LEFT"));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.side("up"));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.side("2"));
    }

    @Test
    public void consumePullsTheNeighbourOnTheAskedSide() {
        // A B C with B current.
        assertEquals(2, ColumnPolicy.consumeSource(1, 3, false));
        assertEquals(0, ColumnPolicy.consumeSource(1, 3, true));
    }

    @Test
    public void consumeHasNothingToPullAtEitherEndOfTheRow() {
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(2, 3, false));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(0, 3, true));
        assertEquals(1, ColumnPolicy.consumeSource(0, 3, false));
        assertEquals(1, ColumnPolicy.consumeSource(2, 3, true));
    }

    @Test
    public void consumeWithOneWindowOrNoneIsInvalid() {
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(0, 1, false));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(0, 1, true));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(0, 0, false));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(-1, 3, false));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(3, 3, true));
    }

    @Test
    public void consumeWithTwoWindowsLeavesOne() {
        assertEquals(1, ColumnPolicy.consumeSource(0, 2, false));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(0, 2, true));
        assertEquals(0, ColumnPolicy.consumeSource(1, 2, true));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.consumeSource(1, 2, false));
        // Either way the survivor is window 0.
        assertEquals(0, ColumnPolicy.currentAfterRemove(0, 1));
        assertEquals(0, ColumnPolicy.currentAfterRemove(1, 0));
    }

    @Test
    public void theCurrentWindowStaysCurrentWhenItsNeighbourLeaves() {
        List<String> row = new ArrayList<>(Arrays.asList("A", "B", "C"));
        int current = 1;
        int source = ColumnPolicy.consumeSource(current, row.size(), false);
        row.remove(source);
        current = ColumnPolicy.currentAfterRemove(current, source);
        assertEquals(Arrays.asList("A", "B"), row);
        assertEquals("B", row.get(current));

        row = new ArrayList<>(Arrays.asList("A", "B", "C"));
        current = 1;
        source = ColumnPolicy.consumeSource(current, row.size(), true);
        row.remove(source);
        current = ColumnPolicy.currentAfterRemove(current, source);
        assertEquals(Arrays.asList("B", "C"), row);
        assertEquals("B", row.get(current));
    }

    @Test
    public void expelPutsTheNewWindowRightAfterOrRightBeforeAndItBecomesCurrent() {
        List<String> row = new ArrayList<>(Arrays.asList("A", "B", "C"));
        int target = ColumnPolicy.expelTarget(1, row.size(), false);
        row.add(target, "B'");
        assertEquals(Arrays.asList("A", "B", "B'", "C"), row);
        assertEquals("B'", row.get(ColumnPolicy.currentAfterExpel(target)));

        row = new ArrayList<>(Arrays.asList("A", "B", "C"));
        target = ColumnPolicy.expelTarget(1, row.size(), true);
        row.add(target, "B'");
        assertEquals(Arrays.asList("A", "B'", "B", "C"), row);
        assertEquals("B'", row.get(ColumnPolicy.currentAfterExpel(target)));
    }

    @Test
    public void expelAtEitherEndAndFromALoneWindow() {
        // The row's last window expels past the end; its first expels before the start.
        assertEquals(3, ColumnPolicy.expelTarget(2, 3, false));
        assertEquals(0, ColumnPolicy.expelTarget(0, 3, true));
        // One window: the new one lands on either side of it. Whether there is a pane to expel
        // is the pane controller's question, not the row's.
        assertEquals(1, ColumnPolicy.expelTarget(0, 1, false));
        assertEquals(0, ColumnPolicy.expelTarget(0, 1, true));
        // Two windows.
        assertEquals(2, ColumnPolicy.expelTarget(1, 2, false));
        assertEquals(1, ColumnPolicy.expelTarget(1, 2, true));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.expelTarget(0, 0, false));
        assertEquals(ColumnPolicy.INVALID, ColumnPolicy.expelTarget(3, 3, false));
    }
}
