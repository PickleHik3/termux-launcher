package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The merged Keyboard theme page: opened straight through {@link SettingsActivity}'s deep link, it
 * carries the Theme choice (writing the same preference the old pill did), and Settings → Style
 * keeps one row for it instead of three.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class KeyboardThemePageTest {

    private Fragment launch(Class<? extends Fragment> fragmentClass) {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, fragmentClass.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragmentClass.isInstance(fragment));
        return fragment;
    }

    @Test
    public void themeChoiceWritesTheKeyboardThemePreference() {
        Fragment page = launch(KeyboardColorSchemeFragment.class);
        MaterialButtonToggleGroup group = page.requireView().findViewById(R.id.keyboard_theme_track);
        assertNotNull(group);
        // No palette is imported, so the fourth segment stays away.
        assertEquals(View.GONE, group.findViewById(R.id.keyboard_theme_custom).getVisibility());

        group.check(R.id.keyboard_theme_dark);

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(
            RuntimeEnvironment.getApplication(), true);
        assertEquals("dark", preferences.getInAppKeyboardTheme());
    }

    @Test
    public void styleScreenHasOneKeyboardThemeRow() {
        Fragment style = launch(TermuxStylePreferencesFragment.class);
        TermuxStylePreferencesFragment fragment = (TermuxStylePreferencesFragment) style;
        Preference row = fragment.findPreference("in_app_keyboard_color_scheme_editor");
        assertNotNull(row);
        assertEquals(KeyboardColorSchemeFragment.class.getName(), row.getFragment());
        assertNull(fragment.findPreference("in_app_keyboard_theme"));
        assertNull(fragment.findPreference("in_app_keyboard_font"));
        assertNotNull(fragment.findPreference("customize_keyboard_surface"));
    }
}
