package com.termux.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

/**
 * The app's one Settings registry and Settings applier, over the stores the settings already use:
 * the app's shared preferences and termux.properties. Both are built on first use by a door
 * (launcherctl today), never on the launcher's start-up path: the launcher only reports its
 * resume and pause here, which costs a field write until a door has built the applier.
 */
public final class LauncherSettings {

    /** What the resumed launcher lends the applier: it carries out a refresh on its own screen. */
    public interface Screen {
        void run(@NonNull SettingRefresh refresh);
    }

    @Nullable private static volatile Screen sResumed;
    @Nullable private static SettingsRegistry sRegistry;
    @Nullable private static SettingsApplier sApplier;

    private LauncherSettings() {}

    /** The registry over the device's stores, built on first use. */
    @NonNull
    public static synchronized SettingsRegistry registry(@NonNull Context context) {
        if (sRegistry == null) {
            Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
            sRegistry = new SettingsRegistry(new DeviceStores(app), SettingDeclarations.ALL,
                com.termux.launcherctl.LauncherCtlAdapters.forDevice(app));
        }
        return sRegistry;
    }

    /** The applier, built on first use; it reaches the launcher through {@link Screen}. */
    @NonNull
    public static synchronized SettingsApplier applier() {
        if (sApplier == null) {
            sApplier = new SettingsApplier(new SettingsApplier.Host() {
                @Override public boolean isLauncherResumed() {
                    return sResumed != null;
                }

                @Override public void run(@NonNull SettingRefresh refresh) {
                    Screen screen = sResumed;
                    if (screen != null) screen.run(refresh);
                }
            });
        }
        return sApplier;
    }

    /** The launcher resumed: what was saved while it was away refreshes now. */
    public static void onLauncherResumed(@NonNull Screen screen) {
        sResumed = screen;
        SettingsApplier applier;
        synchronized (LauncherSettings.class) {
            applier = sApplier;
        }
        if (applier != null) applier.onLauncherResumed();
    }

    /** The launcher paused: a save from now on waits for its next resume. */
    public static void onLauncherPaused() {
        sResumed = null;
    }

    /** Whether a door has built the registry or the applier; for the start-up test. */
    @VisibleForTesting
    static synchronized boolean isBuilt() {
        return sRegistry != null || sApplier != null;
    }

    /** The device's stores: the app's shared preferences, and termux.properties. */
    private static final class DeviceStores implements SettingStore.Stores {
        @NonNull private final Context mContext;
        @Nullable private SettingStore mPreferences;
        @Nullable private SettingStore mProperties;

        DeviceStores(@NonNull Context context) {
            mContext = context;
        }

        @NonNull
        @Override
        public synchronized SettingStore store(@NonNull SettingDeclaration.Store which) {
            if (which == SettingDeclaration.Store.PROPERTIES) {
                if (mProperties == null) mProperties = new PropertiesStore(mContext);
                return mProperties;
            }
            if (mPreferences == null) {
                TermuxAppSharedPreferences preferences =
                    TermuxAppSharedPreferences.build(mContext, false);
                mPreferences = new PreferencesStore(preferences == null ? null
                    : preferences.getSharedPreferences());
            }
            return mPreferences;
        }
    }

    /**
     * The app's shared preferences. A read of a key holding another type answers the default, and
     * with no preferences every read is the default and every write is dropped.
     */
    private static final class PreferencesStore implements SettingStore {
        @Nullable private final SharedPreferences mPrefs;

        PreferencesStore(@Nullable SharedPreferences prefs) {
            mPrefs = prefs;
        }

        @Nullable @Override public String getString(@NonNull String key) {
            if (mPrefs == null) return null;
            try { return mPrefs.getString(key, null); } catch (ClassCastException e) { return null; }
        }

        @Override public int getInt(@NonNull String key, int defaultValue) {
            if (mPrefs == null) return defaultValue;
            try { return mPrefs.getInt(key, defaultValue); } catch (ClassCastException e) { return defaultValue; }
        }

        @Override public void putString(@NonNull String key, @NonNull String value) {
            if (mPrefs != null) mPrefs.edit().putString(key, value).apply();
        }

        @Override public void putInt(@NonNull String key, int value) {
            if (mPrefs != null) mPrefs.edit().putInt(key, value).apply();
        }
    }

    /** termux.properties, read from disk each time and written as the Settings rows write it. */
    private static final class PropertiesStore implements SettingStore {
        @NonNull private final Context mContext;

        PropertiesStore(@NonNull Context context) {
            mContext = context;
        }

        @Nullable @Override public String getString(@NonNull String key) {
            return TermuxPropertiesFile.load(mContext).getProperty(key);
        }

        @Override public int getInt(@NonNull String key, int defaultValue) {
            String text = getString(key);
            if (text == null) return defaultValue;
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        @Override public void putString(@NonNull String key, @NonNull String value) {
            TermuxPropertiesFile.write(key, value);
        }

        @Override public void putInt(@NonNull String key, int value) {
            TermuxPropertiesFile.write(key, String.valueOf(value));
        }
    }
}
