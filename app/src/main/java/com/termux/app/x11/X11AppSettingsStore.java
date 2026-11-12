package com.termux.app.x11;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.shared.termux.TermuxConstants;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Per-app settings of Linux apps on the display. Touch mode and scale live here as one JSON blob
 * ({@link X11AppSettings}); drawing in software and hiding from the drawer stay in the stores that
 * already own them, so their launch-time and drawer readers keep working untouched.
 *
 * <p>Read fresh on every call, no cache: the settings page and the display hold separate
 * instances, like {@link X11GpuProfileStore}.
 */
public final class X11AppSettingsStore {

    private static final String PREFS_KEY_V1 = "x11_app_settings_v1";

    @NonNull private final Context context;
    @NonNull private final SharedPreferences sharedPreferences;
    @NonNull private final X11GpuProfileStore gpuProfileStore;

    public X11AppSettingsStore(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = this.context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
        this.gpuProfileStore = new X11GpuProfileStore(this.context);
    }

    /** The app's touch mode, or null to follow the display. */
    @Nullable
    public String touchMode(@NonNull String appId) {
        X11AppSettings.Overrides o = load().get(appId);
        return o == null ? null : o.touchMode;
    }

    /** Null or an unknown mode clears the override. */
    public void setTouchMode(@NonNull String appId, @Nullable String mode) {
        Map<String, X11AppSettings.Overrides> all = load();
        X11AppSettings.Overrides old = all.get(appId);
        put(all, appId, new X11AppSettings.Overrides(mode, old == null ? null : old.scale,
            old == null ? null : old.gpu));
    }

    /** The app's scale, or null to follow the display. */
    @Nullable
    public String scale(@NonNull String appId) {
        X11AppSettings.Overrides o = load().get(appId);
        return o == null ? null : o.scale;
    }

    /** Null or an unknown scale clears the override. */
    public void setScale(@NonNull String appId, @Nullable String scale) {
        Map<String, X11AppSettings.Overrides> all = load();
        X11AppSettings.Overrides old = all.get(appId);
        put(all, appId, new X11AppSettings.Overrides(old == null ? null : old.touchMode, scale,
            old == null ? null : old.gpu));
    }

    /** The app's own GPU profile id (never software, see {@link #drawsInSoftware}), or null to follow. */
    @Nullable
    public String gpuProfile(@NonNull String appId) {
        X11AppSettings.Overrides o = load().get(appId);
        return o == null ? null : o.gpu;
    }

    /** Null, an unknown id or software clears it. */
    public void setGpuProfile(@NonNull String appId, @Nullable String profileId) {
        Map<String, X11AppSettings.Overrides> all = load();
        X11AppSettings.Overrides old = all.get(appId);
        put(all, appId, new X11AppSettings.Overrides(old == null ? null : old.touchMode,
            old == null ? null : old.scale, profileId));
    }

    public boolean drawsInSoftware(@NonNull String appId) {
        return gpuProfileStore.drawsInSoftware(appId);
    }

    public void setDrawsInSoftware(@NonNull String appId, boolean software) {
        gpuProfileStore.setDrawsInSoftware(appId, software);
    }

    public boolean hiddenInDrawer(@NonNull String appId) {
        return LauncherAppDataProvider.getInstance(context).hiddenApps()
            .isHidden(X11Apps.ref(appId).stableId());
    }

    public void setHiddenInDrawer(@NonNull String appId, boolean hidden) {
        LauncherAppDataProvider.getInstance(context).hiddenApps()
            .setHidden(X11Apps.ref(appId).stableId(), hidden);
    }

    /** Apps with a touch mode or scale override (not the software and hidden switches); a copy. */
    @NonNull
    public Set<String> appsWithOverrides() {
        return new TreeSet<>(load().keySet());
    }

    /** Whether any of the four settings differs from what the display does. */
    public boolean differsFromDisplay(@NonNull String appId) {
        return load().containsKey(appId) || drawsInSoftware(appId) || hiddenInDrawer(appId);
    }

    @NonNull
    private Map<String, X11AppSettings.Overrides> load() {
        return X11AppSettings.parse(sharedPreferences.getString(PREFS_KEY_V1, ""));
    }

    private void put(@NonNull Map<String, X11AppSettings.Overrides> all, @NonNull String appId,
                     @NonNull X11AppSettings.Overrides value) {
        Map<String, X11AppSettings.Overrides> next = new LinkedHashMap<>(all);
        if (value.isEmpty()) next.remove(appId); else next.put(appId, value);
        if (next.equals(all)) return;
        if (next.isEmpty()) {
            sharedPreferences.edit().remove(PREFS_KEY_V1).apply();
        } else {
            sharedPreferences.edit().putString(PREFS_KEY_V1, X11AppSettings.encode(next)).apply();
        }
    }
}
