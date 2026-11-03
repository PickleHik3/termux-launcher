package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.Element;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;

import org.junit.Test;

import java.util.Arrays;

/**
 * "Lens beads do nothing under Docked" (2026-11-03). The developer's setup: Lens beads, Docked,
 * every bar put away (so the joined Docked frame card stands and the dock's sheet stands down on
 * it, {@code TermuxActivity#stackStandsDownOnFrame}), the apps row along the bottom with or
 * without the A–Z row under it. At base 457f1bc33 every case below gave the slab; now the frame
 * leaves the row's band out and the row wears its beads ({@link
 * LensBeadGeometry#rowWearsBeadsCuttingFrame}), while Floating and the Peek card answer as before.
 */
public class LensBeadDockedFrameTest {

    private static PlaceLayout home(boolean azShown, PlaceLayout.Edge azEdge) {
        return new PlaceLayout(PlaceLayout.Edge.TOP, PlaceLayout.RowPlacement.BOTTOM, azShown,
            azEdge, PlaceLayout.RowPlacement.HIDDEN,
            PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    }

    @Test
    public void theJoinedFrameLetsTheRowAloneWearBeads() {
        PlaceLayout appsAlone = home(false, PlaceLayout.Edge.BOTTOM);
        assertTrue(LensBeadGeometry.rowWearsBeadsCuttingFrame(appsAlone, true, false));
        // A keyboard sharing the glass still keeps the slab.
        assertFalse(LensBeadGeometry.rowWearsBeadsCuttingFrame(appsAlone, true, true));
    }

    @Test
    public void theJoinedFrameLetsTheRowWearBeadsOverTheAzRow() {
        PlaceLayout withAz = home(true, PlaceLayout.Edge.BOTTOM);
        // The setup: the A–Z row under the icons, nearer the screen's edge (outermost first).
        assertEquals(Arrays.asList(Element.AZ, Element.APPS),
            AccessoryStackLayoutPolicy.plankBands(
                EdgeStackPolicy.stack(withAz, PlaceLayout.Edge.BOTTOM)));
        assertTrue(LensBeadGeometry.rowWearsBeadsCuttingFrame(withAz, true, false));
        // The A–Z row on another edge leaves the row alone: beads too.
        assertTrue(LensBeadGeometry.rowWearsBeadsCuttingFrame(
            home(true, PlaceLayout.Edge.TOP), true, false));
    }

    @Test
    public void withoutTheFrameTheAnswersAreAsBefore() {
        PlaceLayout appsAlone = home(false, PlaceLayout.Edge.BOTTOM);
        PlaceLayout withAz = home(true, PlaceLayout.Edge.BOTTOM);
        PlaceLayout azTop = home(true, PlaceLayout.Edge.TOP);
        for (PlaceLayout layout : Arrays.asList(appsAlone, withAz, azTop)) {
            for (boolean keyboard : new boolean[] {false, true}) {
                assertEquals(LensBeadGeometry.rowWearsBeads(layout, false, keyboard),
                    LensBeadGeometry.rowWearsBeadsCuttingFrame(layout, false, keyboard));
            }
        }
        // Floating with the A–Z row on the dock keeps the slab (its own task follows).
        assertFalse(LensBeadGeometry.rowWearsBeadsCuttingFrame(withAz, false, false));
        assertTrue(LensBeadGeometry.rowWearsBeadsCuttingFrame(appsAlone, false, false));
    }

    @Test
    public void thePeekCardStillStandsDownOnTheFrame() {
        PlaceLayout appsAlone = home(false, PlaceLayout.Edge.BOTTOM);
        assertFalse(LensBeadGeometry.rowWearsBeads(appsAlone, true, false));
        assertFalse(DockPlacement.peekCardStands(true, true, appsAlone, true, false, false, false));
    }
}
