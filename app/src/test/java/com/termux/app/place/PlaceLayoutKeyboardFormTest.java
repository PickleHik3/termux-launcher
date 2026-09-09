package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.KeyboardForm;
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
 * The keyboard type: what every place resolves to before anything is written, that the cycle key's
 * arithmetic comes back round, and that a write is scoped to the one place and orientation the
 * user made it on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutKeyboardFormTest {

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-keyboard-form-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    // ------------------------------------------------------------------ the value

    @Test
    public void everyPlaceStartsDockedInBothOrientations() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                assertEquals(place + " " + orientation, KeyboardForm.DOCKED,
                    store.keyboardForm(place, orientation));
                assertEquals(place + " " + orientation, KeyboardForm.DOCKED,
                    store.resolve(place, orientation).keyboardForm);
            }
        }
    }

    @Test
    public void aWrittenTypeIsWhatThatPlaceAndOrientationReadsBack() {
        PlaceLayoutStore store = store();
        store.setKeyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE,
            KeyboardForm.FLOATING);

        assertEquals(KeyboardForm.FLOATING,
            store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE).keyboardForm);
        // The same place in the other orientation, and every other place, are untouched.
        assertEquals(KeyboardForm.DOCKED,
            store.keyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
        assertEquals(KeyboardForm.DOCKED,
            store.keyboardForm(PaneWallPage.WIDGETS, PlaceOrientation.LANDSCAPE));
        assertEquals(KeyboardForm.DOCKED,
            store.keyboardForm(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE));
    }

    /** The key the value is under is a contract: a tool and the Layout page must find each other. */
    @Test
    public void theStoredKeyIsTheOneTheSpecNames() {
        store().setKeyboardForm(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT, KeyboardForm.SPLIT);
        assertEquals("split", prefs.getString("place.home.portrait.keyboard_form", null));
    }

    /** A change to the type has to retire a held layout, or nothing re-runs its geometry. */
    @Test
    public void aTypeChangeMovesTheRevisionAndTheResolvedValue() {
        PlaceLayoutStore store = store();
        PlaceLayout before = store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        int revision = store.revision();

        store.setKeyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, KeyboardForm.SPLIT);

        assertNotEquals(revision, store.revision());
        assertNotEquals(before, store.resolve(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
    }

    /** Clearing a place's arrangement puts the type back with the rest of it. */
    @Test
    public void clearingThePlaceForgetsTheType() {
        PlaceLayoutStore store = store();
        store.setKeyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT,
            KeyboardForm.FLOATING);
        store.clear(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        assertEquals(KeyboardForm.DOCKED,
            store.keyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
    }

    @Test
    public void anUnreadableStoredValueReadsAsDocked() {
        prefs.edit().putString("place.terminal.portrait.keyboard_form", "sideways").commit();
        assertEquals(KeyboardForm.DOCKED,
            store().keyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
    }

    // ------------------------------------------------------------------ the cycle

    @Test
    public void cyclingForwardGoesDockedFloatingSplitAndRound() {
        assertEquals(KeyboardForm.FLOATING, KeyboardForm.DOCKED.cycled(1));
        assertEquals(KeyboardForm.SPLIT, KeyboardForm.FLOATING.cycled(1));
        assertEquals(KeyboardForm.DOCKED, KeyboardForm.SPLIT.cycled(1));
    }

    @Test
    public void cyclingBackwardWalksTheSameRingTheOtherWay() {
        assertEquals(KeyboardForm.SPLIT, KeyboardForm.DOCKED.cycled(-1));
        assertEquals(KeyboardForm.DOCKED, KeyboardForm.FLOATING.cycled(-1));
        assertEquals(KeyboardForm.FLOATING, KeyboardForm.SPLIT.cycled(-1));
    }

    @Test
    public void aWholeTurnInEitherDirectionLandsWhereItStarted() {
        for (KeyboardForm form : KeyboardForm.values()) {
            assertEquals(form, form.cycled(3));
            assertEquals(form, form.cycled(-3));
            assertEquals(form, form.cycled(0));
            assertEquals(form, form.cycled(1).cycled(-1));
        }
    }

    @Test
    public void storageValuesRoundTripAndAnUnknownOneFallsBack() {
        for (KeyboardForm form : KeyboardForm.values()) {
            assertEquals(form, KeyboardForm.parse(form.storageValue(), KeyboardForm.SPLIT));
        }
        assertEquals(KeyboardForm.DOCKED, KeyboardForm.parse(null, KeyboardForm.DOCKED));
        assertEquals(KeyboardForm.DOCKED, KeyboardForm.parse("", KeyboardForm.DOCKED));
        assertEquals(KeyboardForm.DOCKED, KeyboardForm.parse("FLOATING", KeyboardForm.DOCKED));
    }

    // ------------------------------------------------------------------ remembered position

    @Test
    public void aFloatingKeyboardHasNoRememberedPlaceUntilItIsMoved() {
        PlaceLayoutStore store = store();
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardX(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0f);
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardY(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0f);
    }

    @Test
    public void aRememberedPositionIsScopedToItsPlaceAndOrientation() {
        PlaceLayoutStore store = store();
        store.setFloatingKeyboardPosition(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE,
            0.25f, 0.75f);

        assertEquals(0.25f,
            store.floatingKeyboardX(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), 1e-6f);
        assertEquals(0.75f,
            store.floatingKeyboardY(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE), 1e-6f);
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardX(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0f);
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardX(PaneWallPage.WIDGETS, PlaceOrientation.LANDSCAPE), 0f);
    }

    /** A fraction from outside the frame is not a position; it forgets rather than clamping. */
    @Test
    public void anOutOfRangePositionIsForgotten() {
        PlaceLayoutStore store = store();
        store.setFloatingKeyboardPosition(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT,
            0.5f, 0.5f);
        store.setFloatingKeyboardPosition(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT,
            -0.2f, 1.4f);

        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardX(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0f);
        assertEquals(PlaceLayoutStore.FLOAT_POSITION_UNSET,
            store.floatingKeyboardY(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT), 0f);
    }

    @Test
    public void theEdgesOfTheFrameAreValidPositions() {
        PlaceLayoutStore store = store();
        store.setFloatingKeyboardPosition(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE, 0f, 1f);
        assertEquals(0f,
            store.floatingKeyboardX(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE), 0f);
        assertEquals(1f,
            store.floatingKeyboardY(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE), 0f);
    }
}
