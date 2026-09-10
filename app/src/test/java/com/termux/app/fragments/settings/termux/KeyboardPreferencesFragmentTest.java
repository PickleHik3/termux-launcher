package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.concurrent.TimeUnit;

/**
 * The Keyboard page's own keyboard-type row. It is the blunt one: it reads and writes every place
 * at once for the orientation the phone is in, where the Layout page's row is per place. Mirrors
 * {@link LayoutPreferencesFragmentTest}'s coverage of that row — that the pill is there with its
 * three segments, what a write lands in, and what a read gives back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class KeyboardPreferencesFragmentTest {

    private KeyboardPreferencesFragment launch() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                KeyboardPreferencesFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof KeyboardPreferencesFragment);
        return (KeyboardPreferencesFragment) fragment;
    }

    @NonNull
    private KeyboardPreferencesDataStore store() {
        return KeyboardPreferencesDataStore.getInstance(RuntimeEnvironment.getApplication());
    }

    @NonNull
    private PlaceLayoutStore places() {
        return new PlaceLayoutStore(
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true));
    }

    @Test
    public void theKeyboardTypeRowSitsAtTheTopOfTheShapesCategoryWithAllThreeTypes() {
        KeyboardPreferencesFragment fragment = launch();
        SegmentedPillPreference pill =
            fragment.getPreferenceScreen().findPreference("in_app_keyboard_form");
        assertNotNull(pill);
        assertEquals(3, pill.segmentCount());
        assertEquals("Keyboard type", pill.getTitle());
    }

    @Test
    public void aWriteSetsTheKeyboardTypeOnEveryPlaceInThisOrientation() {
        KeyboardPreferencesDataStore store = store();
        store.putString("in_app_keyboard_form", "floating");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);

        PlaceLayoutStore places = places();
        for (PaneWallPage place : PaneWallPage.values()) {
            assertEquals(place + " follows the page", KeyboardForm.FLOATING,
                places.keyboardForm(place, PlaceOrientation.PORTRAIT));
        }
        // The other orientation is somebody else's business.
        assertEquals(KeyboardForm.DOCKED,
            places.keyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE));

        SharedPreferences prefs = TermuxAppSharedPreferences
            .build(RuntimeEnvironment.getApplication(), true).getSharedPreferences();
        assertEquals("floating", prefs.getString("place.terminal.portrait.keyboard_form", null));
        // The widget grid is "home" in the store's own key names.
        assertEquals("floating", prefs.getString("place.home.portrait.keyboard_form", null));
        assertEquals("floating", prefs.getString("place.display.portrait.keyboard_form", null));
    }

    @Test
    public void aReadGivesTheSharedTypeWhenThePlacesAgreeAndNothingWhenTheyDoNot() {
        KeyboardPreferencesDataStore store = store();
        // A fresh install: every place is docked, so the pill has an answer.
        assertEquals("docked", store.getString("in_app_keyboard_form", "docked"));

        store.putString("in_app_keyboard_form", "split");
        assertEquals("split", store.getString("in_app_keyboard_form", "docked"));

        // One place given a type of its own on the Layout page: they no longer agree, and no
        // segment is the honest answer.
        places().setKeyboardForm(PaneWallPage.WIDGETS, PlaceOrientation.PORTRAIT,
            KeyboardForm.DOCKED);
        assertEquals(SegmentedPillPreference.VALUE_NONE,
            store.getString("in_app_keyboard_form", "docked"));

        // Writing from this page settles the disagreement.
        store.putString("in_app_keyboard_form", "docked");
        assertEquals("docked", store.getString("in_app_keyboard_form", "split"));
    }

    @Test
    @Config(qualifiers = "+land")
    public void theRowWritesTheOrientationThePhoneIsHeldIn() {
        KeyboardPreferencesDataStore store = store();
        store.putString("in_app_keyboard_form", "floating");

        PlaceLayoutStore places = places();
        assertEquals(KeyboardForm.FLOATING,
            places.keyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.LANDSCAPE));
        assertEquals(KeyboardForm.DOCKED,
            places.keyboardForm(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT));
    }

    @Test
    public void aWriteAsksTheLauncherToRelayoutTheWayTheLayoutPageDoes() {
        Application app = RuntimeEnvironment.getApplication();
        KeyboardPreferencesDataStore store = store();
        int before = Shadows.shadowOf(app).getBroadcastIntents().size();

        store.putString("in_app_keyboard_form", "split");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);
        assertTrue("the running launcher is asked to restyle",
            Shadows.shadowOf(app).getBroadcastIntents().size() > before);

        // Writing the type it already has changes nothing, so nothing is asked for either.
        int after = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putString("in_app_keyboard_form", "split");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);
        assertEquals(after, Shadows.shadowOf(app).getBroadcastIntents().size());
    }
}
