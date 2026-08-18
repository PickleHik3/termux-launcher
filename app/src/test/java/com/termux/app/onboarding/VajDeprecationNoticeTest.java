package com.termux.app.onboarding;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class VajDeprecationNoticeTest {

    private SharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(
            VajDeprecationNotice.PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void showsOnceOnTheVajEdition() {
        assertTrue(VajDeprecationNotice.shouldShow(preferences, "io.vaj.tl"));

        preferences.edit()
            .putInt(VajDeprecationNotice.KEY_SHOWN_VERSION, VajDeprecationNotice.NOTICE_VERSION)
            .commit();
        assertFalse(VajDeprecationNotice.shouldShow(preferences, "io.vaj.tl"));
    }

    @Test
    public void neverShowsOnTheOtherEditions() {
        assertFalse(VajDeprecationNotice.shouldShow(preferences, "com.termux"));
        assertFalse(VajDeprecationNotice.shouldShow(preferences, "com.termux.launcher.nix"));
    }

    @Test
    public void doNotShowAgainSurvivesALaterNoticeVersion() {
        preferences.edit().putBoolean(VajDeprecationNotice.KEY_SUPPRESSED, true).commit();
        assertFalse(VajDeprecationNotice.shouldShow(preferences, "io.vaj.tl"));

        // A bumped notice re-notifies users who only dismissed it, never users who opted out.
        preferences.edit()
            .putInt(VajDeprecationNotice.KEY_SHOWN_VERSION,
                VajDeprecationNotice.NOTICE_VERSION - 1)
            .commit();
        assertFalse(VajDeprecationNotice.shouldShow(preferences, "io.vaj.tl"));

        preferences.edit().putBoolean(VajDeprecationNotice.KEY_SUPPRESSED, false).commit();
        assertTrue(VajDeprecationNotice.shouldShow(preferences, "io.vaj.tl"));
    }
}
