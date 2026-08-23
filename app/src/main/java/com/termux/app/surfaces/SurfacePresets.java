package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The editor's presets: complete looks, as data.
 *
 * <p>A preset is a versioned, ordered map of preference key → value over the keys the editor
 * owns. The semantics are the theme-export format's: a key that is absent is not "unchanged" but
 * "the default shape of that cell" — applying a preset first reattaches every surface to Base, so
 * an absent per-surface key means that cell follows Base, and a present one is a detached
 * override. Unknown keys are ignored on read, so a preset written by a newer build degrades
 * instead of failing.
 *
 * <p>The Base blur/opacity/grain triple of every preset is computed from its material point via
 * {@link SurfaceMaterials}, never written by hand, so a freshly applied preset always shows its
 * material selected rather than "Custom".
 */
public final class SurfacePresets {

    private SurfacePresets() {}

    /** Bumped when a key's meaning changes; unknown keys are already ignored without it. */
    public static final int FORMAT_VERSION = 1;

    /** One complete look. */
    public static final class Preset {
        @NonNull public final String id;
        @StringRes public final int nameRes;
        /** Ordered and unmodifiable; iteration order is application order. */
        @NonNull public final Map<String, Object> values;

        Preset(@NonNull String id, @StringRes int nameRes, @NonNull Map<String, Object> values) {
            this.id = id;
            this.nameRes = nameRes;
            this.values = Collections.unmodifiableMap(values);
        }
    }

    private static final List<Preset> PRESETS = Collections.unmodifiableList(Arrays.asList(
        // The shipped look: Docked, the default glass, and the one asymmetry a default cannot
        // express - the dock sitting a few points denser than the surfaces behind it.
        preset("stock", R.string.termux_surface_preset_stock,
            SegmentedPillPreference.VALUE_DEFAULT, TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, 24, 12,
            look -> {
                look.put(TermuxAppSharedPreferences.surfaceOverrideKey(
                    SurfaceSlot.DOCK, SurfaceProperty.OPACITY),
                    TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY);
                look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, Boolean.TRUE);
                look.put(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS,
                    TERMUX_APP.DEFAULT_TERMINAL_CORNER_RADIUS);
                look.put(TERMUX_APP.KEY_TERMINAL_PANE_GAP, TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP);
            }),
        preset("glass", R.string.termux_surface_preset_glass,
            SegmentedPillPreference.VALUE_ROUNDED, TERMUX_APP.SURFACE_MATERIAL_GLASS, 75, 28, 16,
            look -> look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, Boolean.TRUE)),
        preset("frost", R.string.termux_surface_preset_frost,
            SegmentedPillPreference.VALUE_ROUNDED, TERMUX_APP.SURFACE_MATERIAL_FROST, 50, 28, 14,
            look -> look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, Boolean.TRUE)),
        preset("solid", R.string.termux_surface_preset_solid,
            SegmentedPillPreference.VALUE_DEFAULT, TERMUX_APP.SURFACE_MATERIAL_SOLID, 78, 0, 12,
            look -> {
                look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, Boolean.TRUE);
                look.put(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, 0);
                look.put(TERMUX_APP.KEY_TERMINAL_PANE_GAP, TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP);
            }),
        preset("minimal", R.string.termux_surface_preset_minimal,
            SegmentedPillPreference.VALUE_ROUNDED, TERMUX_APP.SURFACE_MATERIAL_GLASS, 0, 20, 12,
            look -> look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, Boolean.FALSE))
    ));

    private interface Extras {
        void addTo(Map<String, Object> look);
    }

    /** Shape, material point (the triple falls out of it), radius and margin; then the extras. */
    private static Preset preset(String id, @StringRes int nameRes, String dockStyle,
                                 String material, int intensity, int cornerRadius, int sideGap,
                                 Extras extras) {
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        look.put(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, dockStyle);
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL, material);
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, intensity);
        int[] triple = SurfaceMaterials.triple(material, intensity);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_BLUR, triple[SurfaceMaterials.BLUR]);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_OPACITY, triple[SurfaceMaterials.OPACITY]);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, triple[SurfaceMaterials.GRAIN]);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS, cornerRadius);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP, sideGap);
        extras.addTo(look);
        return new Preset(id, nameRes, look);
    }

    @NonNull
    public static List<Preset> presets() {
        return PRESETS;
    }

    /**
     * Applies a preset in full: every surface back on Base first — a preset is a complete look,
     * so it overwrites detached overrides rather than working around them — then each named value,
     * with per-surface keys landing as fresh detaches. The caller owns offering the Undo.
     */
    public static void apply(@NonNull TermuxAppSharedPreferences prefs, @NonNull Preset preset) {
        for (SurfaceSlot slot : SurfaceSlot.values())
            prefs.reattachSurface(slot);
        for (Map.Entry<String, Object> entry : preset.values.entrySet())
            applyOne(prefs, entry.getKey(), entry.getValue());
    }

    private static void applyOne(@NonNull TermuxAppSharedPreferences prefs, @NonNull String key,
                                 @NonNull Object value) {
        switch (key) {
            case TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE:
                prefs.setAppLauncherDockStyle((String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_MATERIAL:
                prefs.setSurfaceMaterial((String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY:
                prefs.setSurfaceMaterialIntensity((Integer) value);
                return;
            case TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED:
                prefs.setTerminalBorderEnabled((Boolean) value);
                return;
            case TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS:
                prefs.setTerminalCornerRadius((Integer) value);
                return;
            case TERMUX_APP.KEY_TERMINAL_PANE_GAP:
                prefs.setTerminalPaneGap((Integer) value);
                return;
        }
        SurfaceProperty baseProperty = basePropertyForKey(key);
        if (baseProperty != null) {
            prefs.setSurfaceBaseValue(baseProperty, (Integer) value);
            return;
        }
        SurfaceEditorRows.Row cell = overrideCellForKey(key);
        if (cell != null)
            prefs.detachSurfaceValue(cell.slot, cell.property, (Integer) value);
        // Anything else is a key this build does not know; ignored by design.
    }

    /**
     * Whether the current preferences are exactly this preset: every named value in place, and
     * every per-surface cell detached if and only if the preset names it. That second half is what
     * keeps the ring honest — hand-detaching a row un-matches the preset even at the same numbers.
     */
    public static boolean matches(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull Preset preset) {
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            String key = TermuxAppSharedPreferences.surfaceOverrideKey(row.slot, row.property);
            if (prefs.isSurfaceInheriting(row.slot, row.property) == preset.values.containsKey(key))
                return false;
        }
        for (Map.Entry<String, Object> entry : preset.values.entrySet()) {
            Object current = currentOne(prefs, entry.getKey());
            if (current != null && !current.equals(entry.getValue()))
                return false;
        }
        return true;
    }

    /** The live value behind a preset key, or null for a key this build does not know. */
    @Nullable
    private static Object currentOne(@NonNull TermuxAppSharedPreferences prefs,
                                     @NonNull String key) {
        switch (key) {
            case TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE:
                return prefs.getAppLauncherDockStyle();
            case TERMUX_APP.KEY_SURFACE_MATERIAL:
                return prefs.getSurfaceMaterial();
            case TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY:
                return prefs.getSurfaceMaterialIntensity();
            case TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED:
                return prefs.isTerminalBorderEnabled();
            case TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS:
                return prefs.getTerminalCornerRadius();
            case TERMUX_APP.KEY_TERMINAL_PANE_GAP:
                return prefs.getTerminalPaneGap();
        }
        SurfaceProperty baseProperty = basePropertyForKey(key);
        if (baseProperty != null)
            return prefs.getSurfaceBaseValue(baseProperty);
        SurfaceEditorRows.Row cell = overrideCellForKey(key);
        if (cell != null)
            return prefs.getSurfaceOverrideValue(cell.slot, cell.property);
        return null;
    }

    @Nullable
    private static SurfaceProperty basePropertyForKey(@NonNull String key) {
        switch (key) {
            case TERMUX_APP.KEY_SURFACE_BASE_BLUR: return SurfaceProperty.BLUR;
            case TERMUX_APP.KEY_SURFACE_BASE_OPACITY: return SurfaceProperty.OPACITY;
            case TERMUX_APP.KEY_SURFACE_BASE_GRAIN: return SurfaceProperty.GRAIN;
            case TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS: return SurfaceProperty.CORNER_RADIUS;
            case TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP: return SurfaceProperty.SIDE_GAP;
            default: return null;
        }
    }

    /** The (surface, property) cell a legacy per-surface key names, via the editor's row table. */
    @Nullable
    private static SurfaceEditorRows.Row overrideCellForKey(@NonNull String key) {
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (key.equals(TermuxAppSharedPreferences.surfaceOverrideKey(row.slot, row.property)))
                return row;
        }
        return null;
    }
}
