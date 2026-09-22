package com.termux.app;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.TermuxStylePreferencesFragment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class StylePreferencesInstrumentationTest {

    private static final String[] STYLE_PREFERENCE_KEYS = {
            "use_system_wallpaper",
            "terminal_background_opacity",
            "app_bar_opacity"
    };

    @Rule
    public ActivityTestRule<SettingsActivity> activityRule = new ActivityTestRule<>(SettingsActivity.class);

    @Test
    public void stylePreferencesContainRegressionKeys() throws Throwable {
        SettingsActivity activity = activityRule.getActivity();
        activityRule.runOnUiThread(() -> {
            FragmentManager fragmentManager = activity.getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.settings, new TermuxStylePreferencesFragment())
                    .commitNowAllowingStateLoss();
        });
        TermuxStylePreferencesFragment fragment = (TermuxStylePreferencesFragment) activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertNotNull(fragment);
        for (String key : STYLE_PREFERENCE_KEYS) {
            Preference preference = fragment.findPreference(key);
            assertNotNull("Preference " + key + " should exist", preference);
        }
    }
}
