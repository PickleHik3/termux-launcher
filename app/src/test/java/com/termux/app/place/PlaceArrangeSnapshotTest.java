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
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

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
        places.setStatusBarEdge(PORTRAIT, Edge.BOTTOM);
        places.setAppsRow(PORTRAIT, RowPlacement.HIDDEN);
        places.setExtraKeys(PORTRAIT, RowPlacement.HIDDEN);
        places.setAzRowShown(PORTRAIT, false);
        places.setKeyboardForm(PORTRAIT, KeyboardForm.SPLIT);
        places.setDockHeightScale(PORTRAIT, 1.4f);
        places.setKeyboardHeightScale(PORTRAIT, 1.25f);
        places.setKeyboardChinDp(PORTRAIT, 16);
        // And, after a rotation mid-session, the other orientation as well.
        places.setStatusBarEdge(LANDSCAPE, Edge.LEFT);
        places.setWidgetColumns(LANDSCAPE, 6);
    }

    @Test
    public void aMovedBarIsSomethingToLose() {
        String entry = PlaceArrangeSnapshot.capture(places).signature();

        places.setStatusBarEdge(PORTRAIT, Edge.BOTTOM);

        assertNotEquals(entry, PlaceArrangeSnapshot.capture(places).signature());
    }

    @Test
    public void anUntouchedArrangementIsNotDirty() {
        String entry = PlaceArrangeSnapshot.capture(places).signature();

        // Re-writing a value with the one it already had is not a change.
        places.setStatusBarEdge(PORTRAIT,
            places.statusBarEdge(PORTRAIT));

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
        assertEquals(Edge.TOP, places.statusBarEdge(PORTRAIT));
        assertEquals(RowPlacement.BOTTOM, places.appsRow(PORTRAIT));
        assertEquals(RowPlacement.BOTTOM, places.extraKeys(PORTRAIT));
        assertTrue(places.azRowShown(PORTRAIT));
        assertEquals(KeyboardForm.DOCKED, places.keyboardForm(PORTRAIT));
        assertEquals("the orientation a rotation handed the editor comes back too",
            Edge.TOP, places.statusBarEdge(LANDSCAPE));
        assertEquals(4, places.widgetColumns(LANDSCAPE));
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT,
            places.dockHeightScale(PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            places.keyboardHeightScale(PORTRAIT), 0.0001f);
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING,
            places.keyboardChinDp(PORTRAIT));
    }

    @Test
    public void eachSizeIsSomethingToLoseOnItsOwn() {
        String entry = PlaceArrangeSnapshot.capture(places).signature();

        places.setDockHeightScale(LANDSCAPE, 1.4f);
        String afterDock = PlaceArrangeSnapshot.capture(places).signature();
        assertNotEquals("the dock's height", entry, afterDock);

        places.setKeyboardHeightScale(LANDSCAPE, 1.25f);
        String afterKeyboard = PlaceArrangeSnapshot.capture(places).signature();
        assertNotEquals("the keyboard's height", afterDock, afterKeyboard);

        places.setKeyboardChinDp(LANDSCAPE, 16);
        assertNotEquals("the keyboard's chin", afterKeyboard,
            PlaceArrangeSnapshot.capture(places).signature());
    }

    @Test
    public void discardingPutsEverySizeBackWhereItStood() {
        places.setKeyboardHeightScale(LANDSCAPE, 0.7f);
        places.setKeyboardChinDp(LANDSCAPE, 8);
        places.setDockHeightScale(LANDSCAPE, 2.4f);
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);

        places.setKeyboardHeightScale(LANDSCAPE, 1.6f);
        places.setKeyboardChinDp(LANDSCAPE, 40);
        places.setDockHeightScale(LANDSCAPE, 0.5f);
        entry.restore(places);

        assertEquals(0.7f, places.keyboardHeightScale(LANDSCAPE), 0.0001f);
        assertEquals(8, places.keyboardChinDp(LANDSCAPE));
        assertEquals(2.4f, places.dockHeightScale(LANDSCAPE), 0.0001f);
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
        assertEquals(Edge.BOTTOM, places.statusBarEdge(PORTRAIT));
        assertEquals(RowPlacement.HIDDEN, places.extraKeys(PORTRAIT));
        assertFalse(places.azRowShown(PORTRAIT));
    }

    @Test
    public void aSnapshotOfAFreshInstallRestoresTheArrangementItShippedWith() {
        // Nothing has been written, so the snapshot is what the shared layer answers. Restoring it
        // materialises those answers as scoped keys, and the arrangement is unchanged by it.
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        entry.restore(places);

        for (PaneWallPage place : PaneWallPage.values()) {
            assertEquals(place + " portrait", RowPlacement.BOTTOM, places.appsRow(PORTRAIT));
            assertEquals(place + " landscape rail", RowPlacement.LEFT,
                places.appsRow(LANDSCAPE));
            assertEquals(place + " status", Edge.TOP, places.statusBarEdge(PORTRAIT));
        }
    }
}
