package com.termux.app.wall.switcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.CornerZones;

import org.junit.Test;

public class DotPlacementTest {

    private static final float D = 28f;

    @Test public void centerXKeepsAQuarterDiameterGapOnEachEdge() {
        assertEquals(21f, DotPlacement.centerX(Edge.LEFT, 400f, D), 0.001f);
        assertEquals(379f, DotPlacement.centerX(Edge.RIGHT, 400f, D), 0.001f);
    }

    @Test public void bandTopClearsTheCornerZoneAndMargin() {
        float expected = (CornerZones.PANE_SIZE_DP + 8f) * 2f + 14f;
        assertEquals(expected, DotPlacement.bandTop(D, 2f), 0.001f);
    }

    @Test public void bandBottomIsTheHigherOfCornerAndChrome() {
        // The chrome is high: it wins.
        assertEquals(1000f - 16f - 14f, DotPlacement.bandBottom(2000f, 1000f, D, 2f), 0.001f);
        // No chrome (its top is the layer's height): the corner zone wins.
        float corner = 2000f - (CornerZones.PANE_SIZE_DP + 8f) * 2f - 14f;
        assertEquals(corner, DotPlacement.bandBottom(2000f, 2000f, D, 2f), 0.001f);
    }

    @Test public void restYIsTheFractionInsideTheBand() {
        assertEquals(1200f, DotPlacement.restY(0.6f, 2000f, 200f, 1800f, D), 0.001f);
        assertEquals(200f, DotPlacement.restY(0.01f, 2000f, 200f, 1800f, D), 0.001f);
        assertEquals(1800f, DotPlacement.restY(0.99f, 2000f, 200f, 1800f, D), 0.001f);
    }

    @Test public void restYWithAnInvertedBandTakesTheBottom() {
        assertEquals(150f, DotPlacement.restY(0.6f, 2000f, 200f, 150f, D), 0.001f);
        // Never above half the diameter.
        assertEquals(14f, DotPlacement.restY(0.6f, 2000f, 200f, -50f, D), 0.001f);
    }

    @Test public void nearerEdgeSplitsAtTheMiddle() {
        assertSame(Edge.LEFT, DotPlacement.nearerEdge(199f, 400f));
        assertSame(Edge.RIGHT, DotPlacement.nearerEdge(200f, 400f));
    }

    @Test public void fractionIsClampedAndDefaultsOnAnUnmeasuredLayer() {
        assertEquals(0.5f, DotPlacement.fractionOf(500f, 1000f), 0.001f);
        assertEquals(0f, DotPlacement.fractionOf(-10f, 1000f), 0.001f);
        assertEquals(1f, DotPlacement.fractionOf(1500f, 1000f), 0.001f);
        assertEquals(DotPlacement.DEFAULT_HEIGHT_FRACTION,
            DotPlacement.fractionOf(500f, 0f), 0.001f);
    }

    @Test public void dragStaysInsideTheLayer() {
        assertEquals(14f, DotPlacement.dragX(-5f, 400f, D), 0.001f);
        assertEquals(386f, DotPlacement.dragX(500f, 400f, D), 0.001f);
        assertEquals(200f, DotPlacement.dragX(200f, 400f, D), 0.001f);
        assertEquals(100f, DotPlacement.dragY(50f, 100f, 900f), 0.001f);
        assertEquals(900f, DotPlacement.dragY(950f, 100f, 900f), 0.001f);
        assertEquals(80f, DotPlacement.dragY(500f, 100f, 80f), 0.001f);
    }

    @Test public void theHitCircleIncludesItsRim() {
        assertTrue(DotPlacement.onDot(130f, 100f, 100f, 100f, 30f));
        assertTrue(DotPlacement.onDot(100f, 70f, 100f, 100f, 30f));
        assertFalse(DotPlacement.onDot(131f, 100f, 100f, 100f, 30f));
        assertFalse(DotPlacement.onDot(125f, 125f, 100f, 100f, 30f));
    }
}
