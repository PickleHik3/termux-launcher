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
public class TerminalIOPreferencesDataStoreTrimWrappedTrailingSpacesTest {

    @Test
    public void trimWrappedTrailingSpacesRoundTripsThroughTheDataStore() {
        // Same path as lazy mode's guard: data store -> shared preferences -> the accessor the
        // app reads, except this switch ships on, so the default has to read back true too.
        Context context = RuntimeEnvironment.getApplication();
        TerminalIOPreferencesDataStore store = TerminalIOPreferencesDataStore.getInstance(context);

        assertTrue(store.getBoolean("terminal_trim_wrapped_trailing_spaces", true));
        assertTrue(TermuxAppSharedPreferences.build(context).isTrimWrappedTrailingSpacesEnabled());

        store.putBoolean("terminal_trim_wrapped_trailing_spaces", false);
        assertFalse(store.getBoolean("terminal_trim_wrapped_trailing_spaces", true));
        assertFalse(TermuxAppSharedPreferences.build(context).isTrimWrappedTrailingSpacesEnabled());

        store.putBoolean("terminal_trim_wrapped_trailing_spaces", true);
        assertTrue(store.getBoolean("terminal_trim_wrapped_trailing_spaces", false));
        assertTrue(TermuxAppSharedPreferences.build(context).isTrimWrappedTrailingSpacesEnabled());
    }
}
