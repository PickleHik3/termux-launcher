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

    /** The settings' keys the windows' look reads start with this (and an app's own opacity's prefix). */
    public static final String PREF_PREFIX = "terl_windows_";

    /** Whether a changed preference {@code key} is one the windows wear. */
    public static boolean readsKey(@Nullable String key) {
        return key != null && (key.startsWith(PREF_PREFIX)
            || key.equals(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_TERL_LINUX_OPACITY)
            || key.startsWith(X11AppSettingsStore.PREFS_KEY_OPACITY_PREFIX));
    }

    /**
     * The live look: one object for the activity to hold. {@code chipAppClass} gives a Linux
     * window's class by its id; {@code onResolved} runs on a worker thread when a class has found its app.
     */
    @NonNull
    public static WindowLookSettings live(@NonNull android.content.Context context,
                                          @NonNull TermuxAppSharedPreferences preferences,
                                          @NonNull TerlLookStore looks,
                                          @NonNull LongFunction<String> chipAppClass,
                                          @NonNull Runnable onResolved) {
        LinuxWindowAppIds ids = LinuxWindowAppIds.live(onResolved);
        return new WindowLookSettings(preferences, looks, new X11AppSettingsStore(context),
            linuxId -> ids.of(chipAppClass.apply(linuxId)));
    }

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

    /** Builds a window's glass over the shared blurred wallpaper; the activity gives it. */
    public interface GlassFactory {
        @Nullable android.graphics.drawable.Drawable build(float radiusPx, @NonNull android.view.View anchor,
                                                           int blurDp, int tintPercent, long windowId,
                                                           boolean floating);
    }

    @Nullable private GlassFactory mGlass;

    public void setGlass(@Nullable GlassFactory glass) {
        mGlass = glass;
    }

    @Override public int glassBlurDp() { return mLooks.value(TerlSurface.WINDOWS, TerlProperty.BLUR); }

    @Nullable @Override
    public android.graphics.drawable.Drawable glass(float radiusPx, @NonNull android.view.View anchor,
                                                    int tintPercent, long windowId, boolean floating) {
        // Asked at no blur too, so the factory lets go of a glass the window wore before.
        return mGlass == null ? null
            : mGlass.build(radiusPx, anchor, Math.max(0, glassBlurDp()), tintPercent, windowId, floating);
    }

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
