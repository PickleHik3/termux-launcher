package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceArrangePolicy.Bar;
import com.termux.app.place.PlaceArrangePolicy.Slot;
import com.termux.app.place.PlaceArrangePolicy.Targets;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where a bar lifted inside the surface editor may be dropped, which slot the finger is over, and
 * what a release writes. The model table is the whole specification: the status bar never hides,
 * a row has no top position, a column down a side is landscape's alone, and the A–Z index picks an
 * edge of its own only while the pinned apps have left the dock band.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceArrangePolicyTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private Application app;
    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-arrange-policy-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    private static PlaceLayout layout(RowPlacement appsRow, boolean azShown,
                                      RowPlacement extraKeys) {
        return new PlaceLayout(Edge.TOP, appsRow, azShown, Edge.BOTTOM, extraKeys,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static Targets targets(PlaceOrientation orientation, PlaceLayout layout, Bar bar) {
        return PlaceArrangePolicy.targets(PaneWallPage.TERMINAL, orientation, layout, bar);
    }

    // ------------------------------------------------------------------ every bar × orientation

    @Test
    public void theStatusBarMovesInPortraitAndNeverHides() {
        Targets targets = targets(PORTRAIT, layout(RowPlacement.BOTTOM, true, RowPlacement.BOTTOM),
            Bar.STATUS_BAR);

        assertEquals(Arrays.asList(Edge.TOP, Edge.BOTTOM), targets.edges);
        assertFalse("the wall's pager rides the bar, so it is never put away", targets.tray);
        assertFalse(targets.isEmpty());
    }

    @Test
    public void theStatusBarGainsBothSidesInLandscape() {
        Targets targets = targets(LANDSCAPE, layout(RowPlacement.LEFT, true, RowPlacement.BOTTOM),
            Bar.STATUS_BAR);

        assertEquals(Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT), targets.edges);
        assertFalse(targets.tray);
    }

    @Test
    public void aRowHasNoTopPositionAndMayAlwaysBePutAway() {
        for (Bar bar : new Bar[] {Bar.APPS_ROW, Bar.EXTRA_KEYS}) {
            Targets portrait = targets(PORTRAIT,
                layout(RowPlacement.BOTTOM, true, RowPlacement.BOTTOM), bar);
            assertEquals(bar + " portrait", Arrays.asList(Edge.BOTTOM), portrait.edges);
            assertTrue(bar + " portrait tray", portrait.tray);

            Targets landscape = targets(LANDSCAPE,
                layout(RowPlacement.LEFT, true, RowPlacement.RIGHT), bar);
            assertEquals(bar + " landscape",
                Arrays.asList(Edge.BOTTOM, Edge.LEFT, Edge.RIGHT), landscape.edges);
            assertTrue(bar + " landscape tray", landscape.tray);
        }
    }

    @Test
    public void theIndexRidingTheAppsRowCanOnlyBePutAway() {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            Targets targets = targets(orientation,
                layout(RowPlacement.BOTTOM, true, RowPlacement.BOTTOM), Bar.AZ_INDEX);
            assertTrue(orientation + " has no edge of its own", targets.edges.isEmpty());
            assertTrue(orientation + " may still be put away", targets.tray);
            assertFalse(orientation + " is still liftable", targets.isEmpty());
        }
    }

    @Test
    public void theIndexPicksItsOwnEdgeOnceTheAppsRowHasLeftTheDock() {
        PlaceLayout railed = layout(RowPlacement.LEFT, true, RowPlacement.BOTTOM);
        assertTrue(PlaceChromePolicy.azIndexStandsAlone(railed));

        Targets portrait = targets(PORTRAIT,
            layout(RowPlacement.HIDDEN, true, RowPlacement.BOTTOM), Bar.AZ_INDEX);
        assertEquals(Arrays.asList(Edge.TOP, Edge.BOTTOM), portrait.edges);
        assertTrue(portrait.tray);

        Targets landscape = targets(LANDSCAPE, railed, Bar.AZ_INDEX);
        assertEquals(Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT), landscape.edges);
        assertTrue(landscape.tray);
    }

    @Test
    public void aHiddenRowStillOffersEveryEdgeSoItCanComeBack() {
        Targets targets = targets(LANDSCAPE,
            layout(RowPlacement.HIDDEN, false, RowPlacement.HIDDEN), Bar.EXTRA_KEYS);

        assertEquals(Arrays.asList(Edge.BOTTOM, Edge.LEFT, Edge.RIGHT), targets.edges);
        assertTrue(targets.tray);
    }

    // ---------------------------------------------------------------------------- hit-testing

    private static List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        slots.add(new Slot(Edge.TOP, 0, 0, 1000, 100));
        slots.add(new Slot(Edge.BOTTOM, 0, 1900, 1000, 2000));
        slots.add(new Slot(Edge.LEFT, 0, 0, 100, 2000));
        slots.add(new Slot(Edge.RIGHT, 900, 0, 1000, 2000));
        slots.add(new Slot(null, 300, 900, 700, 1000));
        return slots;
    }

    @Test
    public void aPointInsideOneSlotFindsIt() {
        assertEquals(Edge.TOP, PlaceArrangePolicy.slotUnder(slots(), 500, 40).edge);
        assertEquals(Edge.BOTTOM, PlaceArrangePolicy.slotUnder(slots(), 500, 1950).edge);
        assertEquals(Edge.LEFT, PlaceArrangePolicy.slotUnder(slots(), 40, 1000).edge);
        assertEquals(Edge.RIGHT, PlaceArrangePolicy.slotUnder(slots(), 960, 1000).edge);
        assertTrue(PlaceArrangePolicy.slotUnder(slots(), 500, 950).isTray());
    }

    @Test
    public void theCornerTwoSlotsResolveByTheNearerCentre() {
        // The top band and the left band overlap in the corner, and nearest centre decides rather
        // than whichever was offered first: against a full-height side band the top band is the
        // nearer of the two everywhere inside the overlap.
        assertEquals(Edge.TOP, PlaceArrangePolicy.slotUnder(slots(), 10, 10).edge);
        assertEquals(Edge.TOP, PlaceArrangePolicy.slotUnder(slots(), 90, 90).edge);
        // Two slots of the same shape meeting: the finger's own side of the overlap wins.
        List<Slot> pair = new ArrayList<>();
        pair.add(new Slot(Edge.TOP, 0, 0, 200, 200));
        pair.add(new Slot(Edge.BOTTOM, 100, 0, 300, 200));
        assertEquals(Edge.TOP, PlaceArrangePolicy.slotUnder(pair, 120, 100).edge);
        assertEquals(Edge.BOTTOM, PlaceArrangePolicy.slotUnder(pair, 180, 100).edge);
    }

    @Test
    public void aPointOverNoSlotFindsNothing() {
        assertNull(PlaceArrangePolicy.slotUnder(slots(), 500, 500));
        assertNull(PlaceArrangePolicy.slotUnder(slots(), 200, 950));
        assertNull(PlaceArrangePolicy.slotUnder(new ArrayList<>(), 500, 40));
    }

    // ---------------------------------------------------------------------------- the release

    @Test
    public void aDropOnASlotWritesThePlacesOwnKeyForThatOrientation() {
        PlaceLayoutStore places = store();

        assertTrue(PlaceArrangePolicy.dropOn(places, PaneWallPage.TERMINAL, PORTRAIT,
            Bar.STATUS_BAR, new Slot(Edge.BOTTOM, 0, 1900, 1000, 2000)));

        assertEquals(Edge.BOTTOM, places.statusBarEdge(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals("only the orientation that was dropped in moved",
            Edge.TOP, places.statusBarEdge(PaneWallPage.TERMINAL, LANDSCAPE));
        assertEquals("and only the place that was dropped in",
            Edge.TOP, places.statusBarEdge(PaneWallPage.WIDGETS, PORTRAIT));
    }

    @Test
    public void aDropOnTheTrayPutsARowAway() {
        PlaceLayoutStore places = store();

        assertTrue(PlaceArrangePolicy.dropOn(places, PaneWallPage.TERMINAL, PORTRAIT,
            Bar.EXTRA_KEYS, new Slot(null, 300, 900, 700, 1000)));

        assertEquals(RowPlacement.HIDDEN, places.extraKeys(PaneWallPage.TERMINAL, PORTRAIT));
    }

    @Test
    public void aDropOnASideStandsARowInAColumn() {
        PlaceLayoutStore places = store();

        assertTrue(PlaceArrangePolicy.dropOn(places, PaneWallPage.DISPLAY, LANDSCAPE,
            Bar.APPS_ROW, new Slot(Edge.RIGHT, 900, 0, 1000, 2000)));

        assertEquals(RowPlacement.RIGHT, places.appsRow(PaneWallPage.DISPLAY, LANDSCAPE));
    }

    @Test
    public void aDropTakesTheIndexOffTheAppsRowOntoItsOwnEdge() {
        PlaceLayoutStore places = store();
        places.setAppsRow(PaneWallPage.TERMINAL, PORTRAIT, RowPlacement.HIDDEN);

        assertTrue(PlaceArrangePolicy.dropOn(places, PaneWallPage.TERMINAL, PORTRAIT,
            Bar.AZ_INDEX, new Slot(Edge.TOP, 0, 0, 1000, 100)));

        assertTrue(places.azRowShown(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(Edge.TOP, places.azBarEdge(PaneWallPage.TERMINAL, PORTRAIT));
    }

    @Test
    public void aReleaseOverNothingWritesNothing() {
        PlaceLayoutStore places = store();
        String before = PlaceArrangeSnapshot.capture(places).signature();

        assertFalse(PlaceArrangePolicy.dropOn(places, PaneWallPage.TERMINAL, PORTRAIT,
            Bar.STATUS_BAR, null));

        assertEquals(before, PlaceArrangeSnapshot.capture(places).signature());
    }

    @Test
    public void aReleaseOnATargetThatBarIsNotOfferedWritesNothing() {
        PlaceLayoutStore places = store();
        String before = PlaceArrangeSnapshot.capture(places).signature();

        // The status bar is never hidden, and a row has no top position — neither release lands
        // even though the rectangle is a real slot for some other bar.
        assertFalse(PlaceArrangePolicy.dropOn(places, PaneWallPage.TERMINAL, PORTRAIT,
            Bar.STATUS_BAR, new Slot(null, 300, 900, 700, 1000)));
        assertFalse(PlaceArrangePolicy.dropOn(places, PaneWallPage.TERMINAL, PORTRAIT,
            Bar.EXTRA_KEYS, new Slot(Edge.TOP, 0, 0, 1000, 100)));
        // And a column is landscape's: portrait does not offer one.
        assertFalse(PlaceArrangePolicy.dropOn(places, PaneWallPage.TERMINAL, PORTRAIT,
            Bar.APPS_ROW, new Slot(Edge.LEFT, 0, 0, 100, 2000)));

        assertEquals(before, PlaceArrangeSnapshot.capture(places).signature());
    }
}
