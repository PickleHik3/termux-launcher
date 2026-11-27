package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsSearchFragment;
import com.termux.app.fragments.settings.terl.TerlSettingsSearchFragment;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The Assistant's "No matching setting" door ({@link SettingsActivity#openSearch(Context, String)})
 * opens the search of the Settings on show: Terl's, with the words typed in, while the Terl
 * desktop switch is on; v1's while it is off.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerlSettingsSearchDoorTest {

    private final Context context = RuntimeEnvironment.getApplication();

    private void terl(boolean on) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        assertNotNull(preferences);
        assertTrue(preferences.getSharedPreferences().edit()
            .putBoolean(TermuxPreferenceConstants.TERMUX_APP.KEY_TERL_DESKTOP, on).commit());
    }

    @After
    public void switchOff() {
        terl(false);
    }

    private Fragment openedBy(Intent door) {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class, door)
            .create().start().resume().visible().get();
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings);
    }

    @Test
    public void withTheSwitchOnItOpensTerlsSearchWithTheWords() {
        terl(true);
        Intent door = SettingsActivity.searchIntent(context, "scrollback");
        assertEquals(TerlSettingsSearchFragment.class.getName(),
            door.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT));
        Fragment shown = openedBy(door);
        assertTrue(String.valueOf(shown), shown instanceof TerlSettingsSearchFragment);
        assertEquals("scrollback", shown.getArguments().getString(SettingsSearchFragment.ARG_QUERY));
    }

    @Test
    public void withTheSwitchOffItOpensV1sSearch() {
        terl(false);
        Intent door = SettingsActivity.searchIntent(context, "scrollback");
        assertEquals(SettingsSearchFragment.class.getName(),
            door.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT));
        assertTrue(openedBy(door) instanceof SettingsSearchFragment);
    }
}
