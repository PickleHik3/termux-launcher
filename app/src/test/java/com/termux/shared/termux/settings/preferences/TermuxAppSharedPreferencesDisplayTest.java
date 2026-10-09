package com.termux.shared.termux.settings.preferences;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The font size is kept per display, and a handle's own context is made from the application,
 * which on Android 11 and later has no display to ask: the display is the caller's, read at
 * {@link TermuxAppSharedPreferences#build}. Run where {@code Context#getDisplay} throws for a
 * context tied to no display, which the suite's default SDK never reaches.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU)
public class TermuxAppSharedPreferencesDisplayTest {

    @Test
    public void anActivityCallerReadsAndWritesTheFontSize() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(activity, false);
        assertNotNull(prefs);
        int size = prefs.getFontSize();
        prefs.setFontSize(size + 1);
        assertEquals(size + 1, prefs.getFontSize());
    }

    @Test
    public void anApplicationCallerReadsTheDefaultDisplaysFontSize() {
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TermuxAppSharedPreferences fromActivity = TermuxAppSharedPreferences.build(activity);
        TermuxAppSharedPreferences fromApp = TermuxAppSharedPreferences.build(app);
        assertNotNull(fromActivity);
        assertNotNull(fromApp);
        fromActivity.setFontSize(fromActivity.getFontSize() + 1);
        assertEquals(fromActivity.getFontSize(), fromApp.getFontSize());
    }
}
