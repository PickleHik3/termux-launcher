package com.termux.app.dock;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.SeamTravel;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The slide from a terminal window to Home on "Home's own" layouts as the developer's phone has
 * them (density 2.625, Docked): Home's dock is the pinned-apps row with the A-Z index under it,
 * the window's is the extra keys alone, the status bar along the top on both. The dock the slide
 * stands Home's rows in ({@link SeamTravel#withDockOf}) is built with the activity's own inputs
 * and must hand the row exactly the icon Home's dock hands it at rest, at every size: the policy
 * is not where a row smaller during the slide than after the settle comes from.
 */
public class DockTravelIconTest {

    private static final float DENSITY = 2.625f;

    /** A terminal window: the extra keys along the bottom, no pinned apps, no letters. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** Home: the pinned apps with the A-Z index along the bottom, no extra keys. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private static boolean alone(PlaceLayout layout) {
        return PlaceChromePolicy.appsShown(layout)
            && DockLayoutPolicy.appsRowAloneOnDock(EdgeStackPolicy.stack(layout, Edge.BOTTOM));
    }

    /** The dock for {@code layout} as the activity's buildDockInputs builds it. */
    private static DockLayout dock(PlaceLayout layout, boolean capsule, float scale) {
        List<Element> bottom = EdgeStackPolicy.stack(layout, Edge.BOTTOM);
        DockLayoutPolicy.DockInputs.Builder inputs = DockLayoutPolicy.DockInputs.builder();
        if (!PlaceChromePolicy.appsShown(layout))
            inputs.hiddenRowAsShown(capsule, scale, alone(HOME));
        return DockLayoutPolicy.compute(inputs
            .preferencesAvailable(true)
            .capsule(capsule)
            .appsRowEnabledPref(PlaceChromePolicy.appsShown(layout))
            .azRowEnabledPref(PlaceChromePolicy.azRowOnDock(layout))
            .appsRowAlone(alone(layout))
            .appsRowNextToCanvas(!bottom.isEmpty()
                && bottom.get(bottom.size() - 1) == Element.APPS)
            .rowOverAz(AccessoryStackLayoutPolicy.rowOverAz(bottom))
            .rowUnderAz(AccessoryStackLayoutPolicy.rowUnderAz(bottom))
            .density(DENSITY)
            .barHeightScale(scale)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .build());
    }

    @Test
    public void theTravelLayoutSharesItsRowAsHomeDoes() {
        assertEquals(SeamTravel.DockPlan.DIP, SeamTravel.dockPlan(WINDOW, HOME));
        PlaceLayout travel = SeamTravel.withDockOf(WINDOW, HOME);
        assertEquals(EdgeStackPolicy.stack(HOME, Edge.BOTTOM),
            EdgeStackPolicy.stack(travel, Edge.BOTTOM));
        // The letters share the row's sheet on both: neither hands out the lone baseline.
        assertFalse(alone(HOME));
        assertFalse(alone(travel));
    }

    @Test
    public void theTravelLayoutHandsHomesIconAtEverySize() {
        PlaceLayout travel = SeamTravel.withDockOf(WINDOW, HOME);
        for (boolean capsule : new boolean[] {false, true}) {
            float min = DockLayoutPolicy.minUsefulScale();
            float max = DockLayoutPolicy.maxUsefulScale(capsule);
            for (float scale = min; scale <= max + 1e-4f; scale += 0.01f) {
                String at = (capsule ? "floating" : "docked") + " scale " + scale;
                int home = dock(HOME, capsule, scale).appsRowIconPx;
                assertEquals(at, home, dock(travel, capsule, scale).appsRowIconPx);
                assertEquals(at, home, dock(WINDOW, capsule, scale).appsRowIconPx);
            }
        }
    }

    /**
     * The shipped Docked size at 2.625: Home's shared row draws 105 px, a lone row would draw
     * 134. The slide's 110 px against the settle's 133 is neither pair, so it is not the lone
     * baseline against its fill ratio either.
     */
    @Test
    public void theShippedDockedSizeAt2625() {
        float shipped = DockLayoutPolicy.sizePreset(2);
        assertEquals(105, dock(HOME, false, shipped).appsRowIconPx);
        assertEquals(134, DockLayoutPolicy.iconPxForScale(false, true, shipped, DENSITY));
    }
}
