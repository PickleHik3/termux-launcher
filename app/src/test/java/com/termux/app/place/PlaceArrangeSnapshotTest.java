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
        places.setKeyboardShown(false);
        // And, after a rotation mid-session, the other orientation as well.
        places.setStatusBarEdge(LANDSCAPE, Edge.LEFT);
        places.setWidgetColumns(LANDSCAPE, 6);
    }

    /** A band moved under the keyboard is as unsaved as any move, and Discard takes it back. */
    @Test
    public void aBandUnderTheKeyboardIsSomethingToLose() {
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        String entrySignature = entry.signature();

        places.setSlot(PORTRAIT, Element.EXTRA_KEYS, new Slot(false, Edge.BOTTOM, 0, true));
        assertNotEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());

        entry.restore(places);
        assertEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());
        assertFalse(places.slotUnderKeyboard(PORTRAIT, Element.EXTRA_KEYS));

        // And the other way: an entry with the band under it is put back under it.
        places.setSlot(PORTRAIT, Element.APPS, new Slot(false, Edge.BOTTOM, 0, true));
        PlaceArrangeSnapshot under = PlaceArrangeSnapshot.capture(places);
        places.setSlot(PORTRAIT, Element.APPS, new Slot(false, Edge.BOTTOM, 2));
        under.restore(places);
        assertTrue(places.slotUnderKeyboard(PORTRAIT, Element.APPS));
    }

    /** The keyboard element's switch is as unsaved as a moved bar, and Discard puts it back. */
    @Test
    public void theKeyboardSwitchIsSomethingToLose() {
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        String entrySignature = entry.signature();

        places.setKeyboardShown(false);
        assertNotEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());
        assertTrue("the same switch as the palette's Keyboard on/off",
            prefs.getBoolean(TERMUX_APP.KEY_KEYBOARD_TURNED_OFF, false));

        entry.restore(places);
        assertEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());
        assertTrue(places.isKeyboardShown());
        assertFalse(prefs.getBoolean(TERMUX_APP.KEY_KEYBOARD_TURNED_OFF, false));
    }

    /**
     * A bar on the top edge is put back on the top edge. The snapshot used to read the pinned apps
     * and the extra keys through the old three-way row placement, which has no top row, so Undo
     * and Discard folded a top bar to the bottom and a move between the two was not unsaved.
     */
    @Test
    public void aTopEdgeBarComesBackToTheTopAndATopBottomMoveIsSomethingToLose() {
        places.setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.TOP, Element.APPS));
        places.setSlot(PORTRAIT, Element.EXTRA_KEYS, Slot.on(Edge.TOP, Element.EXTRA_KEYS));
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        String entrySignature = entry.signature();

        places.setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        assertNotEquals("top to bottom is a move", entrySignature,
            PlaceArrangeSnapshot.capture(places).signature());
        places.setSlot(PORTRAIT, Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));

        entry.restore(places);
        assertEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());
        assertEquals(Edge.TOP, places.slot(PORTRAIT, Element.APPS).edge);
        assertEquals(Edge.TOP, places.slot(PORTRAIT, Element.EXTRA_KEYS).edge);
        assertEquals(Edge.TOP, places.resolve(PORTRAIT).slot(Element.APPS).edge);
    }

    /** A bar put away remembers the edge it left, so the tray can bring it back there. */
    @Test
    public void aHiddenBarRemembersItsEdge() {
        places.setSlot(PORTRAIT, Element.EXTRA_KEYS, Slot.on(Edge.TOP, Element.EXTRA_KEYS));
        places.setSlot(PORTRAIT, Element.EXTRA_KEYS,
            places.slot(PORTRAIT, Element.EXTRA_KEYS).withHidden(true));
        Slot away = places.slot(PORTRAIT, Element.EXTRA_KEYS);
        assertTrue(away.hidden);
        assertEquals(Edge.TOP, away.edge);

        places.setSlot(PORTRAIT, Element.STATUS,
            Slot.on(Edge.LEFT, Element.STATUS).withHidden(true));
        assertEquals(Edge.LEFT, places.slot(PORTRAIT, Element.STATUS).edge);

        // A bar that never went away with an edge remembered answers with its own default.
        places.setAppsRow(PORTRAIT, RowPlacement.HIDDEN);
        assertEquals(Edge.BOTTOM, places.slot(PORTRAIT, Element.APPS).edge);
    }

    @Test
    public void aHiddenStatusBarIsSomethingToLoseAndComesBack() {
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places);
        String entrySignature = entry.signature();

        places.setSlot(PORTRAIT, Element.STATUS,
            places.slot(PORTRAIT, Element.STATUS).withHidden(true));
        assertTrue(places.resolve(PORTRAIT).slot(Element.STATUS).hidden);
        assertNotEquals("its edge alone could not say it was gone",
            entrySignature, PlaceArrangeSnapshot.capture(places).signature());

        entry.restore(places);
        assertEquals(entrySignature, PlaceArrangeSnapshot.capture(places).signature());
        assertFalse(places.resolve(PORTRAIT).slot(Element.STATUS).hidden);
        assertEquals(Edge.TOP, places.statusBarEdge(PORTRAIT));
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
        assertTrue(places.isKeyboardShown());
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

    /** The editor opened in minimal mode edits the minimal layout, and Discard puts that one back. */
    @Test
    public void aSnapshotOfTheMinimalLayoutRestoresOnlyTheMinimalLayout() {
        places.setStatusBarEdge(PORTRAIT, Edge.BOTTOM);
        PlaceLayoutStore minimal = places.forVariant(LayoutVariant.MINIMAL);
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(places, LayoutVariant.MINIMAL);
        assertEquals(LayoutVariant.MINIMAL, entry.variant());
        String entrySignature = entry.signature();

        minimal.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        minimal.setKeyboardForm(PORTRAIT, KeyboardForm.SPLIT);
        places.setKeyboardForm(PORTRAIT, KeyboardForm.SPLIT);
        assertNotEquals(entrySignature,
            PlaceArrangeSnapshot.capture(places, LayoutVariant.MINIMAL).signature());

        entry.restore(places);
        assertEquals(entrySignature,
            PlaceArrangeSnapshot.capture(places, LayoutVariant.MINIMAL).signature());
        assertTrue(minimal.resolve(PORTRAIT).slot(Element.APPS).hidden);
        assertEquals(KeyboardForm.DOCKED, minimal.resolve(PORTRAIT).keyboardForm);
        // Nothing of the normal layout was in it: its own edit survives the restore.
        assertEquals(KeyboardForm.SPLIT,
            places.forVariant(LayoutVariant.NORMAL).resolve(PORTRAIT).keyboardForm);
    }

    /** Each variant is its own question to the unsaved-changes check. */
    @Test
    public void editsToOneVariantAreNotUnsavedChangesToTheOther() {
        String normalEntry = PlaceArrangeSnapshot.capture(places, LayoutVariant.NORMAL).signature();
        places.forVariant(LayoutVariant.MINIMAL).setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        assertEquals(normalEntry,
            PlaceArrangeSnapshot.capture(places, LayoutVariant.NORMAL).signature());
    }
}
