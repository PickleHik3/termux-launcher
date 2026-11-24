package com.termux.launcherctl;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.place.layout.LayoutStore;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.registry.SettingAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings registry's adapters over the stores the {@code look} and {@code layout} verbs use:
 * the look grid and the layout documents, on the device. Their re-dress is the verbs' own.
 */
public final class LauncherCtlAdapters {

    private LauncherCtlAdapters() {}

    /** The device's adapters; nothing is read until an id is asked for. */
    @NonNull
    public static List<SettingAdapter> forDevice(@NonNull Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext()
            : context;
        List<SettingAdapter> adapters = new ArrayList<>();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        if (preferences != null) {
            adapters.add(new LookSettingsAdapter(preferences.lookGrid(),
                DeviceControlRoutes.terminalBackground(preferences),
                () -> TerminalActionDispatcher.getInstance().redressChrome()));
        }
        adapters.add(new LayoutSettingsAdapter(LayoutStore.shared(app), LayoutSettingsAdapter.DEVICE,
            () -> TerminalActionDispatcher.getInstance().redressChrome()));
        return adapters;
    }
}
