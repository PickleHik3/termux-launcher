package com.termux.app.dock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.Slot;
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

    /** The developer's Home: the apps row with the A–Z row under it on the dock (the default order). */
    private static final PlaceLayout APPS_OVER_AZ = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** The A–Z row ordered over the apps row. */
    private static final PlaceLayout AZ_OVER_APPS = APPS_OVER_AZ
        .withSlot(Element.AZ, Slot.on(Edge.BOTTOM, 5));
    private static final PlaceLayout APPS_AZ_AND_KEYS = new PlaceLayout(Edge.TOP,
        RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.BOTTOM,
        PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    private static final PlaceLayout RAIL = new PlaceLayout(Edge.TOP, RowPlacement.LEFT,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    private static final PlaceLayout AZ_ON_TOP = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.TOP, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    @Test
    public void theCardStandsWithTheAzRowOnItUnderTheIcons() {
        assertTrue(stands(APPS_OVER_AZ));
        assertTrue(DockPlacement.peekCardCarriesAz(APPS_OVER_AZ, false));
        assertFalse(DockPlacement.peekCardCarriesAz(APPS_ALONE, false));
        // Hidden A–Z, or the A–Z row on another edge: as before, the row alone.
        assertTrue(stands(AZ_ON_TOP));
        assertFalse(DockPlacement.peekCardCarriesAz(AZ_ON_TOP, false));
    }

    @Test
    public void theCardGivesWayToAnAzRowOverTheIconsAndToEveryOtherBand() {
        assertFalse("the letters would stand outside the card", stands(AZ_OVER_APPS));
        assertFalse(DockPlacement.peekCardCarriesAz(AZ_OVER_APPS, false));
        assertFalse("extra keys on the bottom", stands(APPS_AZ_AND_KEYS));
        assertFalse("a rail", stands(RAIL));
        assertFalse("a joined frame", DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, true,
            false, false, false));
    }

    @Test
    public void withTheAzRowTheKeyboardIMEAndSearchFieldRuleStandsAsBefore() {
        assertFalse(DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, false, true, false,
            false));
        assertTrue("Home: the card stands on the keyboard", DockPlacement.peekCardStands(true,
            true, APPS_OVER_AZ, false, true, false, false, true));
        assertFalse(DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, false, false, true,
            false, true));
        assertFalse(DockPlacement.peekCardStands(true, true, APPS_OVER_AZ, false, false, false,
            true, true));
    }

    @Test
    public void lensBeadsStillTakeTheAzRowsGlass() {
        assertFalse(LensBeadGeometry.rowWearsBeads(APPS_OVER_AZ, false, false));
        assertFalse(LensBeadGeometry.rowWearsBeads(AZ_OVER_APPS, false, false));
        assertTrue(LensBeadGeometry.rowWearsBeads(APPS_ALONE, false, false));
    }

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
