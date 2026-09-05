package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.x11.X11GpuProbe;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.x11.LoriePreferences;
import com.termux.x11.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Linux display's options: how touch is read, how big the X screen is, whether the clipboard
 * is shared, and how the launcher starts a display when asked to.
 *
 * <p>The display rows write into the store the display server and {@code termux-x11-preference}
 * read — one store, so the three never disagree — and a running display picks a change up at
 * once. The starting rows are the launcher's own.
 */
public final class X11DisplayPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_RESOLUTION_MODE = "displayResolutionMode";
    private static final String KEY_SCALE = "displayScale";
    private static final String KEY_RESOLUTION_EXACT = "displayResolutionExact";
    private static final String KEY_RESOLUTION_CUSTOM = "displayResolutionCustom";
    private static final String KEY_GPU = "x11_gpu";

    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "x11-gpu-probe");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new X11DisplayPreferencesDataStore(context));
        setPreferencesFromResource(R.xml.x11_display_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        ListPreference mode = findPreference(KEY_RESOLUTION_MODE);
        if (mode != null) {
            applyResolutionRows(mode.getValue());
            mode.setOnPreferenceChangeListener((preference, value) -> {
                applyResolutionRows(String.valueOf(value));
                return true;
            });
        }
        probeGpu(context);
    }

    @Override
    public void onDestroy() {
        probeExecutor.shutdownNow();
        super.onDestroy();
    }

    /** Only the size row that the chosen resolution mode reads is shown. */
    private void applyResolutionRows(@Nullable String mode) {
        Preference scale = findPreference(KEY_SCALE);
        Preference exact = findPreference(KEY_RESOLUTION_EXACT);
        Preference custom = findPreference(KEY_RESOLUTION_CUSTOM);
        if (scale != null) scale.setVisible("scaled".equals(mode));
        if (exact != null) exact.setVisible("exact".equals(mode));
        if (custom != null) custom.setVisible("custom".equals(mode));
    }

    /** What this phone's GPU can do for Linux apps, worked out off the main thread. */
    private void probeGpu(@NonNull Context context) {
        Context app = context.getApplicationContext();
        probeExecutor.execute(() -> {
            X11GpuProbe.Result result = X11GpuProbe.probe(app);
            handler.post(() -> {
                Preference gpu = findPreference(KEY_GPU);
                if (gpu == null || !isAdded()) return;
                gpu.setSummary(result.recommended() == null
                    ? getString(R.string.settings_x11_gpu_none)
                    : getString(R.string.settings_x11_gpu_summary, result.headline()));
            });
        });
    }

    /**
     * Routes each key to the store that owns it: the display's own keys to the server's store,
     * everything else to the launcher's preferences.
     */
    static final class X11DisplayPreferencesDataStore extends PreferenceDataStore {

        @NonNull private final Context context;
        @NonNull private final TermuxAppSharedPreferences launcher;
        @Nullable private final Prefs display;

        X11DisplayPreferencesDataStore(@NonNull Context context) {
            this.context = context.getApplicationContext();
            this.launcher = TermuxAppSharedPreferences.build(this.context);
            Prefs prefs;
            try {
                prefs = new Prefs(this.context);
            } catch (RuntimeException e) {
                prefs = null;
            }
            this.display = prefs;
        }

        private boolean isDisplayKey(@Nullable String key) {
            return display != null && key != null && display.keys.containsKey(key);
        }

        /** A running display re-reads its preferences the way it does for termux-x11-preference. */
        private void notifyDisplay(@NonNull String key) {
            Intent intent = new Intent(LoriePreferences.ACTION_PREFERENCES_CHANGED);
            intent.putExtra("key", key);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        }

        @Override
        public void putString(String key, @Nullable String value) {
            if (key == null) return;
            if (isDisplayKey(key)) {
                if (KEY_RESOLUTION_CUSTOM.equals(key) && !isResolution(value)) return;
                LoriePreferences.PrefsProto.Preference pref = display.keys.get(key);
                if (pref instanceof LoriePreferences.PrefsProto.ListPreference) {
                    pref.asList().put(value == null ? "" : value);
                } else {
                    pref.asString().put(value == null ? "" : value);
                }
                notifyDisplay(key);
                return;
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_COMMAND.equals(key)) {
                launcher.setX11DisplayCommand(value == null ? "" : value);
            } else if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_WINDOW_MANAGER.equals(key)) {
                launcher.setX11WindowManager(value);
            }
        }

        @Override
        public String getString(String key, @Nullable String defValue) {
            if (key == null) return defValue;
            if (isDisplayKey(key)) {
                LoriePreferences.PrefsProto.Preference pref = display.keys.get(key);
                return pref instanceof LoriePreferences.PrefsProto.ListPreference
                    ? pref.asList().get() : pref.asString().get();
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_COMMAND.equals(key)) {
                return launcher.getX11DisplayCommand();
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_WINDOW_MANAGER.equals(key)) {
                return launcher.getX11WindowManager();
            }
            return defValue;
        }

        @Override
        public void putInt(String key, int value) {
            if (key == null) return;
            if (isDisplayKey(key)) {
                display.keys.get(key).asInt().put(value);
                notifyDisplay(key);
                return;
            }
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_DPI.equals(key)) {
                launcher.setX11DisplayDpi(value);
            }
        }

        @Override
        public int getInt(String key, int defValue) {
            if (key == null) return defValue;
            if (isDisplayKey(key)) return display.keys.get(key).asInt().get();
            if (TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_DPI.equals(key)) {
                return launcher.getX11DisplayDpi();
            }
            return defValue;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            if (key == null) return;
            if (isDisplayKey(key)) {
                display.keys.get(key).asBoolean().put(value);
                notifyDisplay(key);
                return;
            }
            switch (key) {
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART:
                    launcher.setX11DisplayAutostartEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_SET_DISPLAY_ENV:
                    launcher.setX11SetDisplayEnvEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_LEGACY_DRAWING:
                    launcher.setX11LegacyDrawingEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_FORCE_BGRA:
                    launcher.setX11ForceBgraEnabled(value);
                    break;
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DRAWER_APPS:
                    launcher.setX11DrawerAppsEnabled(value);
                    com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(context)
                        .refreshAsync(null, null);
                    break;
                default:
                    break;
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            if (key == null) return defValue;
            if (isDisplayKey(key)) return display.keys.get(key).asBoolean().get();
            switch (key) {
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART:
                    return launcher.isX11DisplayAutostartEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_SET_DISPLAY_ENV:
                    return launcher.isX11SetDisplayEnvEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_LEGACY_DRAWING:
                    return launcher.isX11LegacyDrawingEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_FORCE_BGRA:
                    return launcher.isX11ForceBgraEnabled();
                case TermuxPreferenceConstants.TERMUX_APP.KEY_X11_DRAWER_APPS:
                    return launcher.isX11DrawerAppsEnabled();
                default:
                    return defValue;
            }
        }

        /** {@code WIDTHxHEIGHT}, both positive — what the server accepts. */
        static boolean isResolution(@Nullable String value) {
            if (value == null) return false;
            String[] parts = value.trim().split("x");
            if (parts.length != 2) return false;
            try {
                return Integer.parseInt(parts[0]) > 0 && Integer.parseInt(parts[1]) > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
