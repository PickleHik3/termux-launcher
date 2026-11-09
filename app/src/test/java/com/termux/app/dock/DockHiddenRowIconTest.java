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
import static org.junit.Assert.assertTrue;

/**
 * A window whose layout puts the pinned-apps row away, and Home that shows it: the row stays in
 * the dock's view while it is away, so it must keep the icon Home draws. Handed none, it sized its
 * icons from its own host (a fill ratio, a few dp smaller), slid in with them toward Home and grew
 * at the settle. The window's dock, the dock the slide stands Home's rows in
 * ({@link SeamTravel#withDockOf}) and Home's own at rest all hand the row one icon.
 */
public class DockHiddenRowIconTest {

    private static final float[] DENSITIES = {1f, 2f, 2.625f, 2.75f, 3f, 3.5f};
    private static final boolean[] STYLES = {false, true};

    /** A terminal window: the extra keys along the bottom, no pinned apps, no letters. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** Home: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private static float[] sizes(boolean capsule) {
        return new float[] {DockLayoutPolicy.minUsefulScale(), 1.95f, 2.18f,
            DockLayoutPolicy.maxUsefulScale(capsule)};
    }

    /** Whether the row stands alone in {@code layout}, as the activity reads it on the dock. */
    private static boolean alone(PlaceLayout layout) {
        return PlaceChromePolicy.appsShown(layout)
            && DockLayoutPolicy.appsRowAloneOnDock(EdgeStackPolicy.stack(layout, Edge.BOTTOM));
    }

    /**
     * The dock for {@code layout} as the activity builds its inputs; a layout that puts the row
     * away is told how Home shows it when {@code measureHidden}.
     */
    private static DockLayout dock(PlaceLayout layout, boolean capsule, float scale,
                                   float density, boolean measureHidden) {
        List<Element> bottom = EdgeStackPolicy.stack(layout, Edge.BOTTOM);
        DockLayoutPolicy.DockInputs.Builder inputs = DockLayoutPolicy.DockInputs.builder();
        if (measureHidden && !PlaceChromePolicy.appsShown(layout))
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
            .density(density)
            .barHeightScale(scale)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .build());
    }

    @Test
    public void theSlideStandsHomesRowsAlone() {
        assertEquals(SeamTravel.DockPlan.DIP, SeamTravel.dockPlan(WINDOW, HOME));
        PlaceLayout travel = SeamTravel.withDockOf(WINDOW, HOME);
        assertTrue(PlaceChromePolicy.appsShown(travel));
        assertTrue(alone(travel));
        assertTrue(alone(HOME));
    }

    @Test
    public void theRowKeepsHomesIconInTheWindowThroughTheSlideAndAfterTheSettle() {
        PlaceLayout travel = SeamTravel.withDockOf(WINDOW, HOME);
        for (float density : DENSITIES) {
            for (boolean capsule : STYLES) {
                for (float scale : sizes(capsule)) {
                    String at = "density " + density + (capsule ? " floating" : " docked")
                        + " scale " + scale;
                    DockLayout window = dock(WINDOW, capsule, scale, density, true);
                    DockLayout sliding = dock(travel, capsule, scale, density, true);
                    DockLayout settled = dock(HOME, capsule, scale, density, true);
                    assertTrue(at, settled.appsRowIconPx > 0);
                    assertEquals(at, settled.appsRowIconPx, window.appsRowIconPx);
                    assertEquals(at, settled.appsRowIconPx, sliding.appsRowIconPx);
                    // Home's own icon is what a lone row has always drawn at that size.
                    assertEquals(at, DockLayoutPolicy.iconPxForScale(capsule, true, scale,
                        DockLayoutPolicy.baseToolbarHeightPx(density), density),
                        settled.appsRowIconPx);
                }
            }
        }
    }

    @Test
    public void theWindowAtRestIsUnchanged() {
        for (float density : DENSITIES) {
            for (boolean capsule : STYLES) {
                for (float scale : sizes(capsule)) {
                    String at = "density " + density + (capsule ? " floating" : " docked")
                        + " scale " + scale;
                    DockLayout measured = dock(WINDOW, capsule, scale, density, true);
                    DockLayout before = dock(WINDOW, capsule, scale, density, false);
                    // Before, the window handed the row no icon: its host sized them.
                    assertEquals(at, 0, before.appsRowIconPx);
                    assertEquals(at, 0, measured.appsRowBandPx);
                    assertEquals(at, 0, measured.appsBarHeightPx);
                    assertEquals(at, 0, measured.appsRowBandHintPx);
                    assertEquals(at, 0, measured.appsBarHeightHintPx);
                    assertEquals(at, before.indicatorBandHeightPx, measured.indicatorBandHeightPx);
                    assertEquals(at, before.combinedHeight(103, true),
                        measured.combinedHeight(103, true));
                    assertEquals(at, before.combinedHeight(103, false),
                        measured.combinedHeight(103, false));
                }
            }
        }
    }

    @Test
    public void homeAtRestIsUnchanged() {
        for (float density : DENSITIES) {
            for (boolean capsule : STYLES) {
                for (float scale : sizes(capsule)) {
                    String at = "density " + density + (capsule ? " floating" : " docked")
                        + " scale " + scale;
                    // A row that shows reads none of what a hidden one is told.
                    DockLayout told = DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs
                        .builder().hiddenRowAsShown(!capsule, scale + 0.2f, false)
                        .preferencesAvailable(true).capsule(capsule).appsRowEnabledPref(true)
                        .appsRowAlone(true).appsRowNextToCanvas(true).rowUnderAz(false)
                        .density(density).barHeightScale(scale)
                        .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
                        .build());
                    DockLayout home = dock(HOME, capsule, scale, density, false);
                    assertEquals(at, home.appsRowIconPx, told.appsRowIconPx);
                    assertEquals(at, home.appsRowBandPx, told.appsRowBandPx);
                    assertEquals(at, home.appsBarHeightPx, told.appsBarHeightPx);
                }
            }
        }
    }

    @Test
    public void noHalfShowingTheRowHandsNoIcon() {
        DockLayout window = DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true).appsRowEnabledPref(false).density(2.625f)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(2.625f)).build());
        assertEquals(0, window.appsRowIconPx);
    }
}
