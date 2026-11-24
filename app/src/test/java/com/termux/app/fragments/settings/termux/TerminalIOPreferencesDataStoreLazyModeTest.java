package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.app.fragments.settings.RegistryPreferenceDataStore;
import com.termux.app.settings.LauncherSettings;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import androidx.preference.PreferenceDataStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalIOPreferencesDataStoreLazyModeTest {

    private static RegistryPreferenceDataStore storeOver(Context context) {
        SettingStore preferences = LauncherSettings.preferencesStore(
            TermuxAppSharedPreferences.build(context).getSharedPreferences());
        SettingsRegistry registry = new SettingsRegistry(which -> preferences);
        SettingsApplier applier = new SettingsApplier(new SettingsApplier.Host() {
            @Override public boolean isLauncherResumed() { return false; }
            @Override public void run(SettingRefresh refresh) { }
        });
        return new RegistryPreferenceDataStore(new PreferenceDataStore() { }, registry, applier,
            RegistryPreferenceDataStore.ROWS, message -> { });
    }

    @Test
    public void lazyModeRoundTripsThroughTheRegistryRowAndTheAccessorTheAppReads() {
        // Guards the whole path the Battery & performance screen uses: row -> registry ->
        // shared preferences -> the accessor the app reads.
        Context context = RuntimeEnvironment.getApplication();
        RegistryPreferenceDataStore store = storeOver(context);

        assertFalse(store.getBoolean("lazy_mode", true));

        store.putBoolean("lazy_mode", true);

        assertTrue(store.getBoolean("lazy_mode", false));
        assertTrue(TermuxAppSharedPreferences.build(context).isLazyModeEnabled());

        store.putBoolean("lazy_mode", false);
        assertFalse(store.getBoolean("lazy_mode", true));
        assertFalse(TermuxAppSharedPreferences.build(context).isLazyModeEnabled());
    }

    @Test
    public void theLiveWindowsSwitchIsNowKeptWhereTheLauncherReadsIt() {
        // The old store had no case for this row, so the switch never reached the preferences.
        Context context = RuntimeEnvironment.getApplication();
        RegistryPreferenceDataStore store = storeOver(context);
        assertTrue(TermuxAppSharedPreferences.build(context).isLiveWindowsWhileSwipingEnabled());

        store.putBoolean("live_windows_while_swiping", false);

        assertFalse(TermuxAppSharedPreferences.build(context).isLiveWindowsWhileSwipingEnabled());
        assertTrue(SettingDeclarations.LIVE_WINDOWS.key.equals("live_windows_while_swiping"));
    }
}
