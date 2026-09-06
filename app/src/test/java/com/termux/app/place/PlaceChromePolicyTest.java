package com.termux.app.place;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

/** What the dock draws for one arrangement — the derivation the chrome spec is built from. */
public class PlaceChromePolicyTest {

    private static PlaceLayout layout(RowPlacement appsRow, boolean azRowShown,
                                      RowPlacement extraKeys) {
        return new PlaceLayout(Edge.TOP, appsRow, azRowShown, extraKeys, KeyboardMode.RESIZE, 4, 5);
    }

    @Test
    public void aBottomArrangementDrawsAllThreeRows() {
        PlaceLayout l = layout(RowPlacement.BOTTOM, true, RowPlacement.BOTTOM);
        assertTrue(PlaceChromePolicy.appsRowShown(l));
        assertTrue(PlaceChromePolicy.azRowShown(l));
        assertTrue(PlaceChromePolicy.extraKeysRowShown(l));
        assertTrue(PlaceChromePolicy.dockShown(l));
        assertFalse(PlaceChromePolicy.appsRailShown(l));
        assertFalse(PlaceChromePolicy.extraKeysColumnShown(l));
    }

    @Test
    public void appsOnAnEdgeBecomeTheRailAndTakeTheAlphabetsRowWithThem() {
        PlaceLayout left = layout(RowPlacement.LEFT, true, RowPlacement.BOTTOM);
        assertFalse(PlaceChromePolicy.appsRowShown(left));
        assertFalse("the letters index a row that is not there",
            PlaceChromePolicy.azRowShown(left));
        assertTrue(PlaceChromePolicy.appsRailShown(left));
        assertFalse(PlaceChromePolicy.appsRailOnRight(left));
        // The extra keys still hold the bottom, so the dock is still drawn.
        assertTrue(PlaceChromePolicy.dockShown(left));

        PlaceLayout right = layout(RowPlacement.RIGHT, true, RowPlacement.BOTTOM);
        assertTrue(PlaceChromePolicy.appsRailShown(right));
        assertTrue(PlaceChromePolicy.appsRailOnRight(right));
    }

    @Test
    public void hiddenAppsLeaveNeitherRowNorRail() {
        PlaceLayout l = layout(RowPlacement.HIDDEN, true, RowPlacement.HIDDEN);
        assertFalse(PlaceChromePolicy.appsRowShown(l));
        assertFalse(PlaceChromePolicy.azRowShown(l));
        assertFalse(PlaceChromePolicy.appsRailShown(l));
        assertFalse(PlaceChromePolicy.extraKeysRowShown(l));
        assertFalse(PlaceChromePolicy.extraKeysColumnShown(l));
        assertFalse(PlaceChromePolicy.dockShown(l));
    }

    @Test
    public void keysOnAnEdgeCollapseTheirRowAndStandInAColumn() {
        PlaceLayout l = layout(RowPlacement.BOTTOM, false, RowPlacement.RIGHT);
        assertFalse(PlaceChromePolicy.extraKeysRowShown(l));
        assertTrue(PlaceChromePolicy.extraKeysColumnShown(l));
        assertTrue(PlaceChromePolicy.extraKeysColumnOnRight(l));
        // The pinned apps still hold the bottom.
        assertTrue(PlaceChromePolicy.dockShown(l));
    }

    @Test
    public void everythingOnAnEdgeLeavesNoDockAtAll() {
        PlaceLayout l = layout(RowPlacement.LEFT, true, RowPlacement.RIGHT);
        assertFalse(PlaceChromePolicy.dockShown(l));
        assertTrue(PlaceChromePolicy.appsRailShown(l));
        assertTrue(PlaceChromePolicy.extraKeysColumnShown(l));
    }
}
