package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.controlbar.ControlBarRows.Zone;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The bar's shape and the zones each shape holds (#427). */
public class ControlBarFormTest {

    @Test
    public void portraitAtRestIsTwoRows() {
        assertEquals(ControlBarForm.TWO_ROWS, ControlBarForm.of(false, false, false));
    }

    @Test
    public void typingFoldsTheBarToOneRow() {
        assertEquals(ControlBarForm.ONE_ROW, ControlBarForm.of(false, true, false));
    }

    @Test
    public void underTheTerminalLauncherModeTheBarIsAlwaysOneRow() {
        assertEquals(ControlBarForm.ONE_ROW, ControlBarForm.of(false, false, true));
        assertEquals(ControlBarForm.ONE_ROW, ControlBarForm.of(false, true, true));
    }

    @Test
    public void landscapeIsARailWhateverTheKeyboardOrMode() {
        for (boolean typing : new boolean[] {false, true}) {
            for (boolean terminal : new boolean[] {false, true}) {
                ControlBarForm form = ControlBarForm.of(true, typing, terminal);
                assertEquals(ControlBarForm.RAIL, form);
                assertTrue(form.isRail());
                assertFalse(form.ridesKeyboard());
            }
        }
        assertTrue(ControlBarForm.ONE_ROW.ridesKeyboard());
        assertTrue(ControlBarForm.TWO_ROWS.ridesKeyboard());
    }

    @Test
    public void twoRowsHoldWhereYouAreAboveTheTools() {
        assertEquals(Arrays.asList(
            Arrays.asList(Zone.PLACE, Zone.MARKS, Zone.READING),
            Arrays.asList(Zone.TOOLS, Zone.NAVIGATION, Zone.SEARCH)),
            ControlBarRows.lines(ControlBarForm.TWO_ROWS));
    }

    @Test
    public void oneRowHoldsPlaceToolsTheReadingNavigationAndSearchWithoutMarks() {
        assertEquals(Collections.singletonList(
            Arrays.asList(Zone.PLACE, Zone.TOOLS, Zone.READING, Zone.NAVIGATION, Zone.SEARCH)),
            ControlBarRows.lines(ControlBarForm.ONE_ROW));
    }

    @Test
    public void withTheReadingSlotOffNoFormHoldsIt() {
        for (ControlBarForm form : ControlBarForm.values()) {
            for (java.util.List<Zone> line : ControlBarRows.lines(form, false)) {
                assertFalse(form + " holds a reading zone", line.contains(Zone.READING));
            }
        }
        assertEquals(Collections.singletonList(
            Arrays.asList(Zone.PLACE, Zone.TOOLS, Zone.NAVIGATION, Zone.SEARCH)),
            ControlBarRows.lines(ControlBarForm.ONE_ROW, false));
    }

    @Test
    public void theRailHoldsTheOneRowsZonesTopToBottom() {
        assertEquals(ControlBarRows.lines(ControlBarForm.ONE_ROW),
            ControlBarRows.lines(ControlBarForm.RAIL));
    }

    @Test
    public void theRailStandsOnTheRightUnlessToldLeft() {
        assertEquals(ControlBarPlacement.RailSide.RIGHT, ControlBarPlacement.RailSide.fromPreference(null));
        assertEquals(ControlBarPlacement.RailSide.RIGHT, ControlBarPlacement.RailSide.fromPreference("right"));
        assertEquals(ControlBarPlacement.RailSide.RIGHT, ControlBarPlacement.RailSide.fromPreference("up"));
        assertEquals(ControlBarPlacement.RailSide.LEFT, ControlBarPlacement.RailSide.fromPreference("left"));
    }
}
