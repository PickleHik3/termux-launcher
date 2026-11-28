package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsSearchPreference;
import com.termux.app.fragments.settings.terl.TerlChangedFragment;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * Round 5: Terl's Settings opens on its search field with the keyboard down until the field is
 * tapped, and a Changed chip beside the field reaches the Changed filter (#453), as the
 * prototype's root.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerlSettingsRootLandingTest {

    private void terl(boolean on) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(
            RuntimeEnvironment.getApplication(), false);
        assertNotNull(preferences);
        preferences.getSharedPreferences().edit()
            .putBoolean(TermuxPreferenceConstants.TERMUX_APP.KEY_TERL_DESKTOP, on).commit();
    }

    @Before
    public void setUp() {
        terl(true);
    }

    @After
    public void tearDown() {
        terl(false);
    }

    private static SettingsActivity open() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class,
            new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class))
            .create().start().resume().visible().get();
        idle(activity);
        return activity;
    }

    private static void idle(SettingsActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Fragment shown(SettingsActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings);
    }

    @Test
    public void settingsOpensWithTheKeyboardDownUntilTheFieldIsTapped() {
        SettingsActivity activity = open();
        Fragment root = shown(activity);
        EditText field = root.getView().findViewById(R.id.settings_search_input);
        assertNotNull("the search field is on the root", field);
        assertFalse("the field waits for a tap", field.hasFocus());
        InputMethodManager ime = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        assertFalse("no keyboard on opening", shadowOf(ime).isSoftInputVisible());
    }

    @Test
    public void theChangedChipOpensTheChangedFilter() {
        SettingsActivity activity = open();
        Fragment root = shown(activity);
        View chip = root.getView().findViewById(R.id.settings_search_chip);
        assertNotNull(chip);
        assertEquals(View.VISIBLE, chip.getVisibility());
        assertEquals(activity.getString(R.string.terl_settings_changed_title), ((TextView) chip).getText().toString());
        assertTrue(chip.performClick());
        idle(activity);
        assertTrue("the chip reaches the Changed filter", shown(activity) instanceof TerlChangedFragment);
    }

    /** With the switch off v1's root shows no chip. */
    @Test
    public void v1sRootShowsNoChip() {
        terl(false);
        SettingsActivity activity = open();
        SettingsSearchPreference search = ((androidx.preference.PreferenceFragmentCompat) shown(activity))
            .findPreference("settings_search");
        assertNotNull(search);
        assertEquals(null, search.getChipTap());
        View chip = shown(activity).getView().findViewById(R.id.settings_search_chip);
        assertTrue(chip == null || chip.getVisibility() == View.GONE);
    }
}
