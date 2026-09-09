package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

/** What the dock draws for one arrangement — the derivation the chrome spec is built from. */
public class PlaceChromePolicyTest {

    private static PlaceLayout layout(RowPlacement appsRow, boolean azRowShown,
                                      RowPlacement extraKeys) {
        return layout(appsRow, azRowShown, Edge.BOTTOM, extraKeys);
    }

    private static PlaceLayout layout(RowPlacement appsRow, boolean azRowShown, Edge azBarEdge,
                                      RowPlacement extraKeys) {
        return new PlaceLayout(Edge.TOP, appsRow, azRowShown, azBarEdge, extraKeys,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
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
    public void appsOnAnEdgeBecomeTheRailAndLeaveTheLettersStandingAlone() {
        PlaceLayout left = layout(RowPlacement.LEFT, true, RowPlacement.BOTTOM);
        assertFalse(PlaceChromePolicy.appsRowShown(left));
        assertTrue("the letters are their own index",
            PlaceChromePolicy.azRowShown(left));
        assertTrue("with no row to fill, the matches ride the floating strip",
            PlaceChromePolicy.azIndexStandsAlone(left));
        assertTrue(PlaceChromePolicy.appsRailShown(left));
        assertFalse(PlaceChromePolicy.appsRailOnRight(left));
        // The extra keys still hold the bottom, so the dock is still drawn.
        assertTrue(PlaceChromePolicy.dockShown(left));

        PlaceLayout right = layout(RowPlacement.RIGHT, true, RowPlacement.BOTTOM);
        assertTrue(PlaceChromePolicy.appsRailShown(right));
        assertTrue(PlaceChromePolicy.appsRailOnRight(right));
    }

    @Test
    public void theLettersStandAloneWhereverTheAppsRowIsNotOnTheBottom() {
        assertFalse("with the apps row under them the matches land in it",
            PlaceChromePolicy.azIndexStandsAlone(layout(RowPlacement.BOTTOM, true, RowPlacement.BOTTOM)));
        assertTrue(PlaceChromePolicy.azIndexStandsAlone(
            layout(RowPlacement.HIDDEN, true, RowPlacement.BOTTOM)));
        assertFalse("the switch is off, so there is no index at all",
            PlaceChromePolicy.azIndexStandsAlone(layout(RowPlacement.HIDDEN, false, RowPlacement.BOTTOM)));
    }

    @Test
    public void theLettersAloneAreEnoughToDrawTheDock() {
        PlaceLayout l = layout(RowPlacement.LEFT, true, RowPlacement.RIGHT);
        assertTrue(PlaceChromePolicy.azRowShown(l));
        assertTrue(PlaceChromePolicy.dockShown(l));
    }

    @Test
    public void hiddenAppsLeaveNeitherRowNorRail() {
        PlaceLayout l = layout(RowPlacement.HIDDEN, false, RowPlacement.HIDDEN);
        assertFalse(PlaceChromePolicy.appsRowShown(l));
        assertFalse(PlaceChromePolicy.azRowShown(l));
        assertFalse(PlaceChromePolicy.appsRailShown(l));
        assertFalse(PlaceChromePolicy.extraKeysRowShown(l));
        assertFalse(PlaceChromePolicy.extraKeysColumnShown(l));
        assertFalse(PlaceChromePolicy.dockShown(l));
    }

    @Test
    public void hiddenAppsWithTheSwitchOnLeaveTheLettersAsTheWholeDock() {
        PlaceLayout l = layout(RowPlacement.HIDDEN, true, RowPlacement.HIDDEN);
        assertFalse(PlaceChromePolicy.appsRowShown(l));
        assertFalse(PlaceChromePolicy.appsRailShown(l));
        assertTrue(PlaceChromePolicy.azRowShown(l));
        assertTrue(PlaceChromePolicy.azIndexStandsAlone(l));
        assertTrue(PlaceChromePolicy.dockShown(l));
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
    public void everythingOnAnEdgeAndNoLettersLeavesNoDockAtAll() {
        PlaceLayout l = layout(RowPlacement.LEFT, false, RowPlacement.RIGHT);
        assertFalse(PlaceChromePolicy.dockShown(l));
        assertTrue(PlaceChromePolicy.appsRailShown(l));
        assertTrue(PlaceChromePolicy.extraKeysColumnShown(l));
    }

    @Test
    public void theBarsStoredEdgeAppliesOnlyWhileItStandsAlone() {
        // Riding under the apps row: the stored edge is ignored, the bar is always bottom.
        PlaceLayout ridingRow = layout(RowPlacement.BOTTOM, true, Edge.LEFT, RowPlacement.BOTTOM);
        assertFalse(PlaceChromePolicy.azIndexStandsAlone(ridingRow));
        assertEquals(Edge.BOTTOM, PlaceChromePolicy.azBarEdge(ridingRow));

        // Standing alone: the stored edge is honoured, on every edge.
        for (Edge edge : Edge.values()) {
            PlaceLayout standalone = layout(RowPlacement.LEFT, true, edge, RowPlacement.BOTTOM);
            assertTrue(PlaceChromePolicy.azIndexStandsAlone(standalone));
            assertEquals(edge, PlaceChromePolicy.azBarEdge(standalone));
        }

        // The switch off: standing alone is moot, the bar is bottom.
        PlaceLayout off = layout(RowPlacement.LEFT, false, Edge.RIGHT, RowPlacement.BOTTOM);
        assertEquals(Edge.BOTTOM, PlaceChromePolicy.azBarEdge(off));
    }

    /**
     * A bar standing on another edge gives the dock nothing: it has a host and a glass sheet of
     * its own, so the dock reserves no row for it and goes altogether when it was the only thing
     * that would have been on it.
     */
    @Test
    public void aBarOnAnotherEdgeIsNotOnTheDock() {
        PlaceLayout onDock = layout(RowPlacement.HIDDEN, true, Edge.BOTTOM, RowPlacement.HIDDEN);
        assertTrue(PlaceChromePolicy.azRowOnDock(onDock));
        assertTrue(PlaceChromePolicy.dockShown(onDock));

        for (Edge edge : new Edge[]{Edge.TOP, Edge.LEFT, Edge.RIGHT}) {
            PlaceLayout elsewhere = layout(RowPlacement.HIDDEN, true, edge, RowPlacement.HIDDEN);
            // The letters are still shown — just not here.
            assertTrue(PlaceChromePolicy.azRowShown(elsewhere));
            assertFalse(PlaceChromePolicy.azRowOnDock(elsewhere));
            assertFalse(PlaceChromePolicy.dockShown(elsewhere));
        }

        // Anything else on the dock keeps the dock, wherever the bar has gone.
        PlaceLayout withKeys = layout(RowPlacement.HIDDEN, true, Edge.LEFT, RowPlacement.BOTTOM);
        assertFalse(PlaceChromePolicy.azRowOnDock(withKeys));
        assertTrue(PlaceChromePolicy.dockShown(withKeys));

        // Riding under the apps row is on the dock however the stored edge reads.
        PlaceLayout riding = layout(RowPlacement.BOTTOM, true, Edge.RIGHT, RowPlacement.HIDDEN);
        assertTrue(PlaceChromePolicy.azRowOnDock(riding));
        assertTrue(PlaceChromePolicy.dockShown(riding));
    }
}
