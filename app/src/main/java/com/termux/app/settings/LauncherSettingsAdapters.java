package com.termux.app.settings;

import com.termux.app.leader.LeaderChordSettingAdapter;
import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.place.layout.LayoutStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.registry.FontSizeSettingsAdapter;
import com.termux.shared.termux.settings.registry.SettingAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings registry's adapters over the stores the {@code look} and {@code layout} verbs use:
 * the look grid and the layout documents, on the device. Every door that builds the registry
 * (launcherctl, the Settings rows) passes {@link #forDevice}, so all of them see the same ids. The
 * chrome re-dress is the door's own, handed in as {@code redress}.
 */
public final class LauncherSettingsAdapters {

    private LauncherSettingsAdapters() {}

    /** The device's adapters; nothing is read until an id is asked for. */
    @NonNull
    public static List<SettingAdapter> forDevice(@NonNull Context context,
                                                 @NonNull Runnable redress) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext()
            : context;
        List<SettingAdapter> adapters = new ArrayList<>();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        if (preferences != null) {
            boolean terl = preferences.isTerlDesktopEnabled();
            if (terl) {
                // Terl's look answers first, for the ids it shares with v1's grid.
                com.termux.app.look.PrefsLookStore backing = new com.termux.app.look.PrefsLookStore(
                    preferences.getSharedPreferences());
                adapters.add(new TerlLookSettingsAdapter(new com.termux.app.look.StoredTerlLookStore(
                    backing, java.util.Collections.singletonList(com.termux.app.look.TerlLooks.shipped()),
                    com.termux.app.look.StoredTerlLookStore.physicalOver(preferences))));
            }
            adapters.add(new LookSettingsAdapter(preferences.lookGrid(),
                terminalBackground(preferences), redress,
                id -> terl && TerlLookSettingsAdapter.claims(id)));
            adapters.add(new LowMemoryPresetAdapter(LowMemoryPresetAdapter.over(preferences)));
            adapters.add(new LiveBlurSettingAdapter(LiveBlurSettingAdapter.over(app, preferences)));
            // launcherctl config addresses the default display: the bare keys.
            adapters.add(new FontSizeSettingsAdapter(
                LauncherSettings.preferencesStore(preferences.getSharedPreferences()),
                app.getResources().getDisplayMetrics().density, ""));
            adapters.add(new LeaderChordSettingAdapter(
                LauncherSettings.preferencesStore(preferences.getSharedPreferences())));
            adapters.add(TerminalColoursAdapter.forDevice(preferences));
            adapters.add(new KeySpacingAdapter(KeySpacingAdapter.over(preferences)));
            adapters.add(new InputMethodAdapter(InputMethodAdapter.over(preferences)));
        }
        adapters.add(new LayoutSettingsAdapter(LayoutStore.shared(app),
            LayoutSettingsAdapter.DEVICE, redress));
        adapters.add(new UseAsAdapter(UseAsAdapter.over(app)));
        return adapters;
    }

    /** The Terminal's Background, over its own preferences. */
    @NonNull
    public static LookCommand.TerminalBackground terminalBackground(
            @NonNull TermuxAppSharedPreferences preferences) {
        return new LookCommand.TerminalBackground() {
            @Override
            public int get(@NonNull LookCommand.Setting setting) {
                switch (setting) {
                    case OPACITY: return preferences.getTerminalBackgroundOpacity();
                    case BLUR: return preferences.getTerminalGlassBlurRadius();
                    default: return preferences.getTerminalWallpaperDim();
                }
            }

            @Override
            public void set(@NonNull LookCommand.Setting setting, int value) {
                switch (setting) {
                    case OPACITY: preferences.setTerminalBackgroundOpacity(value); break;
                    case BLUR: preferences.setTerminalGlassBlurRadius(value); break;
                    default: preferences.setTerminalWallpaperDim(value);
                }
            }
        };
    }
}
