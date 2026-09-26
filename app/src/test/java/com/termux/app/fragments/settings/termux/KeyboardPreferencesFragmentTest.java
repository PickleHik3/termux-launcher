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
import androidx.preference.Preference;

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
        assertEquals(KeyboardForm.FLOATING, places.keyboardForm(PlaceOrientation.PORTRAIT));
        // The other orientation is somebody else's business.
        assertEquals(KeyboardForm.DOCKED,
            places.keyboardForm(PlaceOrientation.LANDSCAPE));

        SharedPreferences prefs = TermuxAppSharedPreferences
            .build(RuntimeEnvironment.getApplication(), true).getSharedPreferences();
        assertEquals("floating", prefs.getString("layout.portrait.keyboard_form", null));
        // The widget grid is "home" in the store's own key names.
        assertEquals("floating", prefs.getString("layout.portrait.keyboard_form", null));
        assertEquals("floating", prefs.getString("layout.portrait.keyboard_form", null));
    }

    @Test
    public void aReadGivesTheLayoutsTypeWhereverItWasWritten() {
        KeyboardPreferencesDataStore store = store();
        // A fresh install is docked, so the pill has an answer.
        assertEquals("docked", store.getString("in_app_keyboard_form", "docked"));

        store.putString("in_app_keyboard_form", "split");
        assertEquals("split", store.getString("in_app_keyboard_form", "docked"));

        // The Layout editor writes the same one key, so the page reads what it picked.
        places().setKeyboardForm(PlaceOrientation.PORTRAIT, KeyboardForm.FLOATING);
        assertEquals("floating", store.getString("in_app_keyboard_form", "docked"));
    }

    @Test
    @Config(qualifiers = "+land")
    public void theRowWritesTheOrientationThePhoneIsHeldIn() {
        KeyboardPreferencesDataStore store = store();
        store.putString("in_app_keyboard_form", "floating");

        PlaceLayoutStore places = places();
        assertEquals(KeyboardForm.FLOATING,
            places.keyboardForm(PlaceOrientation.LANDSCAPE));
        assertEquals(KeyboardForm.DOCKED,
            places.keyboardForm(PlaceOrientation.PORTRAIT));
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

    @Test
    public void theVoiceRowsReadTheirDefaultsThroughTheStore() {
        KeyboardPreferencesDataStore store = store();
        assertEquals("system", store.getString("keyboard_voice_engine", null));
        assertEquals("auto", store.getString("keyboard_voice_language", null));
        assertEquals("600", store.getString("keyboard_voice_pause_ms", null));
        assertEquals("10000", store.getString("keyboard_voice_silence_timeout_ms", null));
        assertTrue(store.getBoolean("keyboard_voice_sounds", false));
        assertTrue(!store.getBoolean("keyboard_voice_polish", true));

        KeyboardPreferencesFragment fragment = launch();
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_engine"));
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_model"));
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_polish_model"));
    }

    @Test
    public void theCleanupModelRowOpensTheCleanupModelScreenAndShowsAutomaticByDefault() {
        KeyboardPreferencesFragment fragment = launch();
        Preference polishModel = fragment.getPreferenceScreen().findPreference("keyboard_voice_polish_model");
        assertNotNull(polishModel);
        assertEquals("Automatic", polishModel.getSummary().toString());
    }

    @Test
    public void theVoiceRowsWriteThroughToTheSharedPreferencesAndRejectStrays() {
        // The store is a cached singleton; one another test built can hold preferences from a
        // context this test does not read, and the writes below would land where nothing looks.
        KeyboardPreferencesDataStore.resetForTesting();
        KeyboardPreferencesDataStore store = store();
        TermuxAppSharedPreferences prefs =
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true);

        store.putString("keyboard_voice_engine", "on_device");
        store.putString("keyboard_voice_language", "DE");
        store.putString("keyboard_voice_pause_ms", "1200");
        store.putString("keyboard_voice_silence_timeout_ms", "0");
        store.putBoolean("keyboard_voice_sounds", false);
        store.putBoolean("keyboard_voice_polish", true);

        assertTrue(prefs.isInAppKeyboardVoiceOnDevice());
        assertTrue(!prefs.isInAppKeyboardVoiceSoundsEnabled());
        assertTrue(prefs.isInAppKeyboardVoicePolishEnabled());
        assertEquals("de", prefs.getInAppKeyboardVoiceLanguage());
        assertEquals(1200, prefs.getInAppKeyboardVoicePauseMs());
        assertEquals("1200", store.getString("keyboard_voice_pause_ms", null));
        assertEquals(0, prefs.getInAppKeyboardVoiceSilenceTimeoutMs());
        assertEquals("0", store.getString("keyboard_voice_silence_timeout_ms", null));

        // A pause, silence timeout or engine the lists do not offer reads as the default.
        store.putString("keyboard_voice_pause_ms", "999");
        assertEquals("600", store.getString("keyboard_voice_pause_ms", null));
        store.putString("keyboard_voice_silence_timeout_ms", "2500");
        assertEquals("10000", store.getString("keyboard_voice_silence_timeout_ms", null));
        store.putString("keyboard_voice_engine", "cloud");
        assertEquals("system", store.getString("keyboard_voice_engine", null));
    }
}
