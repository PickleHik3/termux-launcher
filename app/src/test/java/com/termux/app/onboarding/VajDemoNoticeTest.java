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
public class VajDemoNoticeTest {

    private SharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(
            VajDemoNotice.PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void showsOnceOnTheVajEdition() {
        assertTrue(VajDemoNotice.shouldShow(preferences, "io.vaj.tl"));

        preferences.edit()
            .putInt(VajDemoNotice.KEY_SHOWN_VERSION, VajDemoNotice.NOTICE_VERSION)
            .commit();
        assertFalse(VajDemoNotice.shouldShow(preferences, "io.vaj.tl"));
    }

    /** The deprecation wording was notice version 2; the demo wording has to reach those users. */
    @Test
    public void theDemoWordingReachesWhoeverOnlySawTheDeprecationOne() {
        preferences.edit().putInt(VajDemoNotice.KEY_SHOWN_VERSION, 2).commit();
        assertTrue(VajDemoNotice.shouldShow(preferences, "io.vaj.tl"));
    }

    @Test
    public void neverShowsOnTheOtherEditions() {
        assertFalse(VajDemoNotice.shouldShow(preferences, "com.termux"));
        assertFalse(VajDemoNotice.shouldShow(preferences, "com.termux.launcher.nix"));
    }

    @Test
    public void doNotShowAgainSurvivesALaterNoticeVersion() {
        preferences.edit().putBoolean(VajDemoNotice.KEY_SUPPRESSED, true).commit();
        assertFalse(VajDemoNotice.shouldShow(preferences, "io.vaj.tl"));

        // A bumped notice re-notifies users who only dismissed it, never users who opted out.
        preferences.edit()
            .putInt(VajDemoNotice.KEY_SHOWN_VERSION,
                VajDemoNotice.NOTICE_VERSION - 1)
            .commit();
        assertFalse(VajDemoNotice.shouldShow(preferences, "io.vaj.tl"));

        preferences.edit().putBoolean(VajDemoNotice.KEY_SUPPRESSED, false).commit();
        assertTrue(VajDemoNotice.shouldShow(preferences, "io.vaj.tl"));
    }
}
