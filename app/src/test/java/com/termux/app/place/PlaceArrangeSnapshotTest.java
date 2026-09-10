package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
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

/**
 * The arrangement half of the surface editor's entry snapshot: what Back → Discard and the revert
 * tap put back, and what tells the editor a bar has been moved since it opened.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceArrangeSnapshotTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private SharedPreferences prefs;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-arrange-snapshot-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    /** Everything one editor session could write, on the place and orientation it is open on. */
    private void moveEverything() {
        places.setStatusBarEdge(PaneWallPage.TERMINAL, PORTRAIT, Edge.BOTTOM);
        places.setAppsRow(PaneWallPage.TERMINAL, PORTRAIT, RowPlacement.HIDDEN);
        places.setExtraKeys(PaneWallPage.TERMINAL, PORTRAIT, RowPlacement.HIDDEN);
        places.setAzRowShown(PaneWallPage.TERMINAL, PORTRAIT, false);
        places.setKeyboardForm(PaneWallPage.TERMINAL, PORTRAIT, KeyboardForm.SPLIT);
        places.setKeyboardOnEnter(PaneWallPage.TERMINAL, KeyboardOnEnter.CLOSED);
        // And, after a rotation mid-session, the other orientation as well.
        places.setStatusBarEdge(PaneWallPage.TERMINAL, LANDSCAPE, Edge.LEFT);
        places.setWidgetColumns(PaneWallPage.WIDGETS, LANDSCAPE, 6);
    }

    @Test
    public void aMovedBarIsSomethingToLose() {
        String entry = PlaceArrangeSnapshot.capture(places).signature();

        places.setStatusBarEdge(PaneWallPage.TERMINAL, PORTRAIT, Edge.BOTTOM);

        assertNotEquals(entry, PlaceArrangeSnapshot.capture(places).signature());
    }

    @Test
    public void anUntouchedArrangementIsNotDirty() {
        String entry = PlaceArrangeSnapshot.capture(places).signature();

        // Re-writing a value with the one it already had is not a change.
        places.setStatusBarEdge(PaneWallPage.TERMINAL, PORTRAIT,
            places.statusBarEdge(PaneWallPage.TERMINAL, PORTRAIT));

        assertEquals(entry, PlaceArrangeSnapshot.capture(places).signature());
    }

    @Test
    public void discardingPutsEveryPlacesArrangementBack() {
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        String entrySignature = entry.signature();
        moveEverything();
        assertNotEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());

        entry.restore(places);

        assertEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());
        assertEquals(Edge.TOP, places.statusBarEdge(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(RowPlacement.BOTTOM, places.appsRow(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(RowPlacement.BOTTOM, places.extraKeys(PaneWallPage.TERMINAL, PORTRAIT));
        assertTrue(places.azRowShown(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(KeyboardForm.DOCKED, places.keyboardForm(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(KeyboardOnEnter.AS_LEFT, places.keyboardOnEnter(PaneWallPage.TERMINAL));
        assertEquals("the orientation a rotation handed the editor comes back too",
            Edge.TOP, places.statusBarEdge(PaneWallPage.TERMINAL, LANDSCAPE));
        assertEquals(4, places.widgetColumns(PaneWallPage.WIDGETS, LANDSCAPE));
    }

    @Test
    public void committingKeepsEverythingThatWasMoved() {
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        moveEverything();
        String committed = PlaceArrangeSnapshot.capture(places).signature();

        // ✓ commits by doing nothing at all: the writes already landed, and the snapshot is
        // dropped rather than restored.
        assertNotEquals(entry.signature(), committed);
        assertEquals(committed, PlaceArrangeSnapshot.capture(places).signature());
        assertEquals(Edge.BOTTOM, places.statusBarEdge(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(RowPlacement.HIDDEN, places.extraKeys(PaneWallPage.TERMINAL, PORTRAIT));
        assertFalse(places.azRowShown(PaneWallPage.TERMINAL, PORTRAIT));
    }

    @Test
    public void aSnapshotOfAFreshInstallRestoresTheArrangementItShippedWith() {
        // Nothing has been written, so the snapshot is what the shared layer answers. Restoring it
        // materialises those answers as scoped keys, and the arrangement is unchanged by it.
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        entry.restore(places);

        for (PaneWallPage place : PaneWallPage.values()) {
            assertEquals(place + " portrait", RowPlacement.BOTTOM, places.appsRow(place, PORTRAIT));
            assertEquals(place + " landscape rail", RowPlacement.LEFT,
                places.appsRow(place, LANDSCAPE));
            assertEquals(place + " status", Edge.TOP, places.statusBarEdge(place, PORTRAIT));
        }
    }
}
