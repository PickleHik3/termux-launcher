package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalIOPreferencesDataStoreLazyModeTest {

    @Test
    public void lazyModeRoundTripsThroughTheDataStore() {
        // The switch wrote into the store's default branch, which drops the value, so the toggle
        // read back as off the moment the screen was reopened. Guards the whole path the settings
        // screen uses: data store -> shared preferences -> the accessor the app reads.
        Context context = RuntimeEnvironment.getApplication();
        TerminalIOPreferencesDataStore store = TerminalIOPreferencesDataStore.getInstance(context);

        assertFalse(store.getBoolean("lazy_mode", false));

        store.putBoolean("lazy_mode", true);

        assertTrue(store.getBoolean("lazy_mode", false));
        assertTrue(TermuxAppSharedPreferences.build(context).isLazyModeEnabled());

        store.putBoolean("lazy_mode", false);
        assertFalse(store.getBoolean("lazy_mode", false));
        assertFalse(TermuxAppSharedPreferences.build(context).isLazyModeEnabled());
    }
}
