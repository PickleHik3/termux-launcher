package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
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
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
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

    @Before
    public void seedPreferencesFileBeforeMultiProcessReaders() throws Exception {
        Context context = RuntimeEnvironment.getApplication().createPackageContext(
            TermuxConstants.TERMUX_PACKAGE_NAME, Context.CONTEXT_RESTRICTED);
        SharedPreferences prefs = context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putBoolean("keyboard_test_seed", true).commit());
        assertTrue(prefs.edit().remove("keyboard_test_seed").commit());
    }

    private KeyboardPreferencesFragment launch() {
        return launch(KeyboardPreferencesFragment.class);
    }

    /** The overview, or one of its focused subpages (they share the controller). */
    private KeyboardPreferencesFragment launch(Class<? extends KeyboardPreferencesFragment> page) {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, page.getName());
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

    /**
     * The keyboard's type is the Layout editor's alone now (spec §6): three chips beside the
     * keyboard on the layout canvas. The Settings row that wrote the same key is gone.
     */
    @Test
    public void theKeyboardTypeRowHasLeftForTheLayoutEditor() {
        KeyboardPreferencesFragment fragment = launch();
        assertNull(fragment.getPreferenceScreen().findPreference("in_app_keyboard_form"));
        assertNull(fragment.getPreferenceScreen().findPreference("in_app_keyboard_bottom_padding"));
    }
    @Test
    public void theVoiceRowsReadTheirDefaultsThroughTheStore() {
        KeyboardPreferencesDataStore store = store();
        assertEquals("system", store.getString("keyboard_voice_engine", null));
        assertEquals("auto", store.getString("keyboard_voice_language", null));
        assertEquals("600", store.getString("keyboard_voice_pause_ms", null));
        assertEquals("10000", store.getString("keyboard_voice_silence_timeout_ms", null));
        assertTrue(store.getBoolean("keyboard_voice_sounds", false));
        assertTrue(store.getBoolean("keyboard_voice_polish", false));
        assertEquals("polished", store.getString("keyboard_voice_polish_level", null));
        assertEquals("normal", store.getString("keyboard_voice_mic_sensitivity", null));

        KeyboardPreferencesFragment fragment = launch(KeyboardVoicePreferencesFragment.class);
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_engine"));
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_model"));
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_polish_model"));
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_polish_level"));
        assertNotNull(fragment.getPreferenceScreen().findPreference("keyboard_voice_mic_sensitivity"));
    }

    @Test
    public void theCleanupModelRowShowsAutomaticByDefault() {
        KeyboardPreferencesFragment fragment = launch(KeyboardVoicePreferencesFragment.class);
        Preference polishModel = fragment.getPreferenceScreen().findPreference("keyboard_voice_polish_model");
        assertNotNull(polishModel);
        assertEquals("Automatic", polishModel.getSummary().toString());
    }

    @Test
    public void thePassCtrlSpaceRowRoundTripsThroughTheStore() {
        KeyboardPreferencesDataStore.resetForTesting();
        KeyboardPreferencesDataStore store = store();
        TermuxAppSharedPreferences prefs =
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true);

        assertFalse(store.getBoolean("pass_ctrl_space_to_android", false));
        store.putBoolean("pass_ctrl_space_to_android", true);
        assertTrue(prefs.isPassCtrlSpaceToAndroidEnabled());
        assertTrue(store.getBoolean("pass_ctrl_space_to_android", false));
        store.putBoolean("pass_ctrl_space_to_android", false);
        assertFalse(prefs.isPassCtrlSpaceToAndroidEnabled());
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

        // The cleanup level: light or polished, and anything else reads as Polished.
        store.putString("keyboard_voice_polish_level", "light");
        assertEquals("light", prefs.getInAppKeyboardVoicePolishLevel());
        assertEquals("light", store.getString("keyboard_voice_polish_level", null));
        store.putString("keyboard_voice_polish_level", "careful");
        assertEquals("polished", prefs.getInAppKeyboardVoicePolishLevel());

        // Mic sensitivity: normal or high, and anything else reads as Normal.
        store.putString("keyboard_voice_mic_sensitivity", "high");
        assertEquals("high", prefs.getInAppKeyboardVoiceMicSensitivity());
        assertEquals("high", store.getString("keyboard_voice_mic_sensitivity", null));
        store.putString("keyboard_voice_mic_sensitivity", "max");
        assertEquals("normal", prefs.getInAppKeyboardVoiceMicSensitivity());
    }

    private static void assertEnabled(KeyboardPreferencesFragment fragment, boolean expected, String... keys) {
        for (String key : keys) {
            Preference row = fragment.getPreferenceScreen().findPreference(key);
            assertNotNull(key, row);
            assertEquals(key, expected, row.isEnabled());
        }
    }

    /**
     * Android IME and Off dim only the built-in keyboard's own pages; voice, the hardware page,
     * extra keys and the vibration shortcut stay usable.
     */
    @Test
    public void switchingTheInputMethodDisablesOnlyTheBuiltInKeyboardLinks() {
        KeyboardPreferencesDataStore.resetForTesting();
        KeyboardPreferencesFragment fragment = launch();
        SegmentedPillPreference method = fragment.getPreferenceScreen()
            .findPreference("keyboard_input_method");
        assertNotNull(method);
        String[] builtInOnly = {"keyboard_sub_layout", "keyboard_sub_size", "keyboard_sub_typing"};
        String[] always = {"keyboard_sub_voice", "keyboard_sub_hardware", "edit_extra_keys_row",
            "extra_keys_text_all_caps", "keyboard_vibration_link"};
        assertEnabled(fragment, true, builtInOnly);
        assertEnabled(fragment, true, always);
        for (String value : new String[] {"android", "none"}) {
            method.getOnPreferenceChangeListener().onPreferenceChange(method, value);
            assertEnabled(fragment, false, builtInOnly);
            assertEnabled(fragment, true, always);
        }
        method.getOnPreferenceChangeListener().onPreferenceChange(method, "built_in");
        assertEnabled(fragment, true, builtInOnly);
        assertEnabled(fragment, true, always);
    }

    /** A subpage follows the stored input method; the voice page is never dimmed by it. */
    @Test
    public void voiceAndHardwareSubpagesStayUsableWithAndroidKeyboardOrOff() {
        for (String value : new String[] {"android", "none"}) {
            KeyboardPreferencesDataStore.resetForTesting();
            store().putString("keyboard_input_method", value);
            try {
                assertEnabled(launch(KeyboardVoicePreferencesFragment.class), true,
                    "keyboard_voice_engine", "keyboard_voice_polish", "keyboard_voice_sounds",
                    "keyboard_voice_model", "keyboard_voice_pause_ms");
                assertEnabled(launch(KeyboardHardwarePreferencesFragment.class), true,
                    "in_app_keyboard_hide_on_hardware", "pass_ctrl_space_to_android");
            } finally {
                store().putString("keyboard_input_method", "built_in");
            }
        }
    }

    /** The built-in pages do dim on a subpage when the built-in keyboard is not in use. */
    @Test
    public void builtInSubpagesDimWhenAnotherInputMethodIsStored() {
        KeyboardPreferencesDataStore.resetForTesting();
        store().putString("keyboard_input_method", "android");
        try {
            KeyboardPreferencesFragment typing = launch(KeyboardTypingPreferencesFragment.class);
            assertEnabled(typing, false, "keyboard_typing", "keyboard_feedback");
        } finally {
            store().putString("keyboard_input_method", "built_in");
        }
    }
}
