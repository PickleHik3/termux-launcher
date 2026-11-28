package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;
import com.termux.app.x11.X11AppSettingsStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.function.LongFunction;

/**
 * {@link WindowLook.Source} read from the settings (#423): the Windows surface's Opacity and
 * Global's Corners from the Terl look, the gap, rim, dim and Linux opacity from their
 * declarations, an app's own Opacity from its page. Read fresh on every call.
 */
public final class WindowLookSettings implements WindowLook.Source {

    @NonNull private final TermuxAppSharedPreferences mPreferences;
    @NonNull private final TerlLookStore mLooks;
    @Nullable private final X11AppSettingsStore mApps;
    @NonNull private final LongFunction<String> mAppIdOf;

    /**
     * @param apps per-app settings; null when Linux apps have none
     * @param appIdOf the app id of a Linux window by its id, or null/empty while unknown
     */
    public WindowLookSettings(@NonNull TermuxAppSharedPreferences preferences, @NonNull TerlLookStore looks,
                              @Nullable X11AppSettingsStore apps, @NonNull LongFunction<String> appIdOf) {
        mPreferences = preferences;
        mLooks = looks;
        mApps = apps;
        mAppIdOf = appIdOf;
    }

    @Override public int gapDp() { return mPreferences.getTerlWindowsGapDp(); }

    @Override public int cornerDp() { return mLooks.cornerRadiusDp(); }

    @Override public int glassOpacity() { return mLooks.value(TerlSurface.WINDOWS, TerlProperty.OPACITY); }

    @Override public boolean rimOn() { return mPreferences.isTerlWindowsRimShown(); }

    @NonNull @Override public String rimColour() { return mPreferences.getTerlWindowsRimColour(); }

    @Override public int unfocusedDimPercent() { return mPreferences.getTerlWindowsUnfocusedDim(); }

    @Override
    public int linuxOpacity(@NonNull String appId) {
        Integer own = mApps == null || appId.isEmpty() ? null : mApps.opacity(appId);
        return own != null ? WindowLook.clampOpacity(own) : mPreferences.getTerlLinuxOpacity();
    }

    @NonNull @Override
    public String appIdOf(long linuxId) {
        String id = mAppIdOf.apply(linuxId);
        return id == null ? "" : id;
    }
}
