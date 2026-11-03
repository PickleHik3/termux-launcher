package com.termux.app.dock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;

import org.junit.Test;

/**
 * Reproduction of "Lens beads do nothing under Docked" (2026-11-03). The developer's setup: Lens
 * beads, Docked, every bar put away (so the joined Docked frame card stands and the dock's sheet
 * stands down on it, {@code TermuxActivity#stackStandsDownOnFrame}), the apps row along the
 * bottom with the A–Z row under it.
 *
 * <p>These pin today's answers, which are the cause: the joined frame refuses beads on its own,
 * and the A–Z row on the dock's plank refuses them too, under Floating as well as Docked. The
 * tests that asserted the wanted answers ran red on all three cases at base 457f1bc33; they are
 * kept as the refusals here until the frame can leave the apps row's band out of its glass.</p>
 */
public class LensBeadDockedFrameTest {

    private static PlaceLayout home(boolean azShown, PlaceLayout.Edge azEdge) {
        return new PlaceLayout(PlaceLayout.Edge.TOP, PlaceLayout.RowPlacement.BOTTOM, azShown,
            azEdge, PlaceLayout.RowPlacement.HIDDEN,
            PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    }

    @Test
    public void theJoinedFrameAloneRefusesTheBeads() {
        PlaceLayout appsAlone = home(false, PlaceLayout.Edge.BOTTOM);
        // Floating (no frame to stand down on): beads.
        assertTrue(LensBeadGeometry.rowWearsBeads(appsAlone, false, false));
        // Docked under the joined frame: the slab, though nothing else shares the row's glass.
        assertFalse(LensBeadGeometry.rowWearsBeads(appsAlone, true, false));
    }

    @Test
    public void theAzRowOnTheDockRefusesTheBeadsUnderEitherStyle() {
        PlaceLayout withAz = home(true, PlaceLayout.Edge.BOTTOM);
        assertFalse("Floating, A-Z on the dock", LensBeadGeometry.rowWearsBeads(withAz, false, false));
        assertFalse("Docked frame, A-Z on the dock",
            LensBeadGeometry.rowWearsBeads(withAz, true, false));
        // The A–Z row on another edge leaves the row alone again, Floating only.
        PlaceLayout azTop = home(true, PlaceLayout.Edge.TOP);
        assertTrue(LensBeadGeometry.rowWearsBeads(azTop, false, false));
        assertFalse(LensBeadGeometry.rowWearsBeads(azTop, true, false));
    }
}
