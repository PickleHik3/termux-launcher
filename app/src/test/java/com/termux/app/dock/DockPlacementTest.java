package com.termux.app.dock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

/**
 * The dock's placement: the Floating Style places it as the Floating card, except while the Peek
 * glass card stands, which rises flush from the screen's edge; Docked it is always flush. And the
 * card stands only where every fact it needs holds.
 */
public class DockPlacementTest {

    /** Home: the pinned apps alone along the bottom, the row the card stands for. */
    private static final PlaceLayout APPS_ALONE = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A window: the extra keys share the dock's glass with the apps. */
    private static final PlaceLayout WITH_EXTRA_KEYS = new PlaceLayout(Edge.TOP,
        RowPlacement.BOTTOM, false, Edge.BOTTOM, RowPlacement.BOTTOM,
        PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private static boolean stands(PlaceLayout layout) {
        return DockPlacement.peekCardStands(true, true, layout, false, false, false, false);
    }

    @Test
    public void theCardStandsForTheAppsRowAloneWithNothingInItsWay() {
        assertTrue(LensBeadGeometry.rowWearsBeads(APPS_ALONE, false, false));
        assertTrue(stands(APPS_ALONE));
    }

    @Test
    public void everyFactTheCardNeedsRefusesItOnItsOwn() {
        assertFalse("another design",
            DockPlacement.peekCardStands(false, true, APPS_ALONE, false, false, false, false));
        assertFalse("the dock away",
            DockPlacement.peekCardStands(true, false, APPS_ALONE, false, false, false, false));
        assertFalse("standing down on a joined frame",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, true, false, false, false));
        assertFalse("the keyboard up",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, false, true, false, false));
        assertFalse("lifted over the system keyboard",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, false, false, true, false));
        assertFalse("the Search field under the row",
            DockPlacement.peekCardStands(true, true, APPS_ALONE, false, false, false, true));
        assertFalse("another band on the dock's glass", stands(WITH_EXTRA_KEYS));
    }

    @Test
    public void floatingTheDockIsTheFloatingCardUnlessThePeekCardStands() {
        assertTrue(DockPlacement.floats(true, false));
        assertFalse("the card rises from the screen's edge", DockPlacement.floats(true, true));
    }

    @Test
    public void dockedTheDockIsAlwaysFlush() {
        assertFalse(DockPlacement.floats(false, false));
        assertFalse(DockPlacement.floats(false, true));
    }

    @Test
    public void theKeyboardComingUpHandsTheFloatingTrayBack() {
        boolean down = DockPlacement.peekCardStands(true, true, APPS_ALONE, false, false, false,
            false);
        boolean up = DockPlacement.peekCardStands(true, true, APPS_ALONE, false, true, false,
            false);
        assertFalse(DockPlacement.floats(true, down));
        assertTrue(DockPlacement.floats(true, up));
    }
}
