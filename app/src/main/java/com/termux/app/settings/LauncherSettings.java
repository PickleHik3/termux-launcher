package com.termux.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SharedPreferencesSettingStore;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.List;
import java.util.Locale;

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

    /** The door's own adapters over stores it owns; asked once, when the registry is first built. */
    public interface Adapters {
        @NonNull
        List<SettingAdapter> forDevice(@NonNull Context context);
    }

    @Nullable private static volatile Screen sResumed;
    @Nullable private static SettingsRegistry sRegistry;
    @Nullable private static SettingsApplier sApplier;

    private LauncherSettings() {}

    /** The registry over the device's stores, built on first use; {@code adapters} only then. */
    @NonNull
    public static synchronized SettingsRegistry registry(@NonNull Context context,
                                                         @NonNull Adapters adapters) {
        if (sRegistry == null) {
            Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
            sRegistry = new SettingsRegistry(new DeviceStores(app), SettingDeclarations.ALL,
                adapters.forDevice(app), RunnableChoices.forDevice());
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

    /** The app's shared preferences as a registry store, for an adapter that reads them itself. */
    @NonNull
    public static SettingStore preferencesStore(@Nullable SharedPreferences prefs) {
        return new SharedPreferencesSettingStore(prefs);
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
                mPreferences = new SharedPreferencesSettingStore(preferences == null ? null
                    : preferences.getSharedPreferences());
            }
            return mPreferences;
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

        @Override public boolean getBoolean(@NonNull String key, boolean defaultValue) {
            String text = getString(key);
            if (text == null) return defaultValue;
            String value = text.trim().toLowerCase(Locale.ROOT);
            return value.equals("true") || (!value.equals("false") && defaultValue);
        }

        @Override public void putBoolean(@NonNull String key, boolean value) {
            TermuxPropertiesFile.write(key, String.valueOf(value));
        }

        @Override public void putString(@NonNull String key, @NonNull String value) {
            TermuxPropertiesFile.write(key, value);
        }

        @Override public void putInt(@NonNull String key, int value) {
            TermuxPropertiesFile.write(key, String.valueOf(value));
        }
    }
}
