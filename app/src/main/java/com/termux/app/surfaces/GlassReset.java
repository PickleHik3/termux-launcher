package com.termux.app.surfaces;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.fragments.settings.termux.AppearanceSettingsStore;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;

/**
 * Reset everything (appearance-cards overhaul §6): the glass and the colours back to what ships,
 * then one Look on Customization. Every look-grid cell on every element, page and page kind is
 * dropped, Contrast and Dynamic colour go back to their defaults (Accent is a grid cell), and the
 * Look applies as Customization's Presets row applies one. Layouts, the wallpaper, icon packs,
 * dock apps, terminal text and keyboard layouts are not touched. Mode lives in termux.properties
 * and is the caller's ({@link #NIGHT_MODE_DEFAULT}).
 */
public final class GlassReset {

    private GlassReset() {}

    /** Dynamic colour's key in the appearance settings. */
    static final String KEY_DYNAMIC_COLOUR = "terminal_dynamic_colors_enabled";

    /** The Mode the reset puts back. */
    public static final String NIGHT_MODE_DEFAULT = "system";

    /** The Look the nudge and the menu start on: Clear. */
    public static final int DEFAULT_STOP = 0;

    /**
     * Resets the glass and colours of {@code prefs}, then applies the Look at {@code stop}
     * (Clear, Mist, Tint, Solid).
     */
    public static void reset(@NonNull Context context, @NonNull TermuxAppSharedPreferences prefs,
                             int stop) {
        clearGrid(prefs.getSharedPreferences());
        prefs.setTerminalContrastLevel(TERMUX_APP.DEFAULT_VALUE_TERMINAL_CONTRAST_LEVEL);
        if (AppearanceSettingsStore.getBoolean(context, KEY_DYNAMIC_COLOUR, false))
            AppearanceSettingsStore.putBoolean(context, KEY_DYNAMIC_COLOUR, false);
        SurfacePresets.Preset look = AppearanceLooks.presetForStop(
            SurfacePresets.presets(prefs), stop);
        if (look == null)
            look = AppearanceLooks.presetForStop(SurfacePresets.presets(prefs), DEFAULT_STOP);
        if (look != null)
            SurfacePresets.applyLook(prefs, look);
    }

    /** The Look tile's preview at {@code stop}, as Customization's Presets row draws it. */
    @NonNull
    public static android.view.View lookPreview(@NonNull Context context, int stop) {
        return CardBody.lookPreview(context, stop);
    }

    /** Drops every look-grid cell from {@code store}, in one write. */
    static void clearGrid(@Nullable SharedPreferences store) {
        if (store == null) return;
        List<String> cells = new ArrayList<>();
        for (String key : store.getAll().keySet()) {
            if (LookGrid.parse(key) != null) cells.add(key);
        }
        if (cells.isEmpty()) return;
        SharedPreferences.Editor editor = store.edit();
        for (String key : cells) editor.remove(key);
        editor.commit();
    }
}
