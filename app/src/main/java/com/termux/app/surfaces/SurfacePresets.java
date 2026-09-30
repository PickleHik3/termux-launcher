package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
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
 * <p>The Base blur/opacity/grain triple of a preset is computed from its material point via
 * {@link SurfaceMaterials}, so a freshly applied preset shows its material selected rather than
 * "Custom". A preset whose look sits off every curve (Mist) names its triple in its extras, which
 * win over the computed one.
 *
 * <p>Beyond the numbers a preset carries three enum-like strings: the glass tint colour, its rim
 * and its motion profile. Every preset states them (the defaults unless it says otherwise), so
 * applying one always resets the others' choice.
 */
public final class SurfacePresets {

    private SurfacePresets() {}

    /**
     * Bumped when a key's meaning changes; unknown keys are already ignored without it. 2 added the
     * glass tint, rim and motion keys; a stored look without them reads as the defaults.
     */
    public static final int FORMAT_VERSION = 2;

    /** The fifth card: whatever look the user last saved, rather than one this build ships. */
    public static final String CUSTOM_ID = "custom";

    /** Where the version rides in the stored blob; not a look key, so it is stripped on read. */
    private static final String KEY_FORMAT_VERSION = "format_version";

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
        // Clear (id minimal): wallpaper forward, the thinnest glass that still reads as a surface.
        preset("minimal", R.string.termux_surface_preset_minimal,
            TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, 3, 16, 4, 20, 12, 4, 4, 10, 18,
            look -> { }),
        // Mist: Obsidian-Music's glass and motion (Apache-2.0; see
        // project-docs/reference/launcher/mist-preset-obsidian-values.md). Blur 25
        // and opacity 60 are Obsidian's own numbers. Grain 8 is not its 0.08 noise carried over:
        // Haze lays a soft noise tile at 0.08 alpha, ours is full-contrast random alpha at up to
        // 60/255 of the percentage, so a literal match (about 68) would read as sand. 8 is the
        // same faint tooth.
        preset("frost", R.string.termux_surface_preset_frost,
            TERMUX_APP.SURFACE_MATERIAL_FROST, 50, 25, 60, 8, 28, 14, 4, 9, 20, 18,
            look -> {
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_OBSIDIAN);
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_RIM, TERMUX_APP.GLASS_RIM_GRADIENT);
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, TERMUX_APP.GLASS_MOTION_MIST);
            }),
        // Tint (id stock): tinted, low blur, denser; for loud wallpapers and a dark terminal.
        preset("stock", R.string.termux_surface_preset_stock,
            TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, 6, 46, 14, 22, 12, 4, 4, 10, 18,
            look -> look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_OBSIDIAN)),
        // Solid: opaque, no blur cost. Rounded (14), not square: square reads as another app.
        preset("solid", R.string.termux_surface_preset_solid,
            TERMUX_APP.SURFACE_MATERIAL_SOLID, 78, 0, 92, 0, 14, 12, 4, 0, 1, 0,
            look -> { })
    ));

    private interface Extras {
        void addTo(Map<String, Object> look);
    }

    /**
     * Material point, the Base triple, corners (Base and terminal), side gap, pane gap and the
     * three Fancier Glass depth keys; the default glass tint, rim and motion; then the extras,
     * whose puts replace any of those in place. The dock style is deliberately absent: a Look
     * never changes Style.
     */
    private static Preset preset(String id, @StringRes int nameRes, String material,
                                 int intensity, int blur, int opacity, int grain,
                                 int cornerRadius, int sideGap, int paneGap,
                                 int bend, int edgeWidth, int edgeLight, Extras extras) {
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL, material);
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, intensity);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_BLUR, blur);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_OPACITY, opacity);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, grain);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS, cornerRadius);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP, sideGap);
        look.put(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, cornerRadius);
        look.put(TERMUX_APP.KEY_TERMINAL_PANE_GAP, paneGap);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_BEND, bend);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, edgeWidth);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT, edgeLight);
        putGlassDefaults(look);
        extras.addTo(look);
        return new Preset(id, nameRes, look);
    }

    private static void putGlassDefaults(Map<String, Object> look) {
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT);
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_RIM, TERMUX_APP.DEFAULT_SURFACE_GLASS_RIM);
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION);
    }

    @NonNull
    public static List<Preset> presets() {
        return PRESETS;
    }

    // ------------------------------------------------------------------------ the saved look
    //
    // The built-in presets are looks this build ships; Custom is the one the user pinned. It is
    // written only by the editor's save glyph — not by Done, which commits the live preferences
    // and would otherwise overwrite the pin every time someone left the editor — so it stays put
    // until the next deliberate save.

    /**
     * The user's saved look as a preset, or null when they have not saved one.
     *
     * <p>Stored rather than derived, so it survives every later edit: this is what the fifth card
     * applies, and what its mock is drawn from.
     */
    @Nullable
    public static Preset custom(@NonNull TermuxAppSharedPreferences prefs) {
        Map<String, Object> look = deserialize(prefs.getSurfaceCustomPreset());
        return look == null || look.isEmpty()
            ? null : new Preset(CUSTOM_ID, R.string.termux_surface_preset_custom, look);
    }

    /** Pins the look the preferences currently describe as {@link #CUSTOM_ID}. */
    public static void saveCustom(@NonNull TermuxAppSharedPreferences prefs) {
        prefs.setSurfaceCustomPreset(serialize(captureLook(prefs)));
    }

    /**
     * The live look in the preset format: the material point, the Base numbers, the
     * terminal's own three, and every per-surface cell that is currently detached — named cells
     * being exactly what {@link #apply} re-detaches, and what {@link #matches} tests against.
     */
    @NonNull
    public static Map<String, Object> captureLook(@NonNull TermuxAppSharedPreferences prefs) {
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL, prefs.getSurfaceMaterial());
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, prefs.getSurfaceMaterialIntensity());
        // After the material point, never before it: a hand-tuned triple no longer sits on any
        // point of any family's curve, and these three are the numbers that must win on apply.
        for (SurfaceProperty property : SurfaceProperty.values())
            look.put(property.baseKey, prefs.getSurfaceBaseValue(property));
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, prefs.getSurfaceGlassTint());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_RIM, prefs.getSurfaceGlassRim());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, prefs.getSurfaceGlassMotion());
        look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, prefs.isTerminalBorderEnabled());
        look.put(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, prefs.getTerminalCornerRadius());
        look.put(TERMUX_APP.KEY_TERMINAL_PANE_GAP, prefs.getTerminalPaneGap());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_BEND, prefs.getFancierGlassBendDp());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, prefs.getFancierGlassEdgeWidthDp());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT, prefs.getFancierGlassEdgeLightPercent());
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (prefs.isSurfaceInheriting(row.slot, row.property))
                continue;
            look.put(TermuxAppSharedPreferences.surfaceOverrideKey(row.slot, row.property),
                prefs.getSurfaceOverrideValue(row.slot, row.property));
        }
        return look;
    }

    /** The stored form: the look as JSON, with the format version alongside it. */
    @NonNull
    public static String serialize(@NonNull Map<String, Object> look) {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put(KEY_FORMAT_VERSION, FORMAT_VERSION);
            for (Map.Entry<String, Object> entry : look.entrySet())
                json.put(entry.getKey(), entry.getValue());
        } catch (org.json.JSONException e) {
            return "";
        }
        return json.toString();
    }

    /**
     * A stored look, or null when there is none or the blob is unreadable — a look that cannot be
     * parsed is treated as "nothing saved", which is the state the card already renders.
     */
    @Nullable
    public static Map<String, Object> deserialize(@Nullable String stored) {
        if (stored == null || stored.isEmpty())
            return null;
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        try {
            org.json.JSONObject json = new org.json.JSONObject(stored);
            for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (KEY_FORMAT_VERSION.equals(key))
                    continue;
                Object value = json.get(key);
                // JSON widens on the way out; every numeric key in the format is an int.
                look.put(key, value instanceof Number ? ((Number) value).intValue() : value);
            }
        } catch (org.json.JSONException e) {
            return null;
        }
        // A look saved before format 2 knows nothing of tint, rim or motion: it was the scheme
        // tint, the hairline and the classic motion, and applying it must put those back.
        if (!look.isEmpty()) {
            for (Map.Entry<String, Object> glass : glassDefaults().entrySet())
                if (!look.containsKey(glass.getKey())) look.put(glass.getKey(), glass.getValue());
        }
        return look;
    }

    private static Map<String, Object> glassDefaults() {
        LinkedHashMap<String, Object> defaults = new LinkedHashMap<>();
        putGlassDefaults(defaults);
        return defaults;
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
            // Style is never part of a Look: a stored look from before this rule may still carry
            // the key, and it is ignored on apply and in matches().
            case TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE:
                return;
            case TERMUX_APP.KEY_FANCIER_GLASS_BEND:
                prefs.setFancierGlassBendDp(intOf(value));
                return;
            case TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH:
                prefs.setFancierGlassEdgeWidthDp(intOf(value));
                return;
            case TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT:
                prefs.setFancierGlassEdgeLightPercent(intOf(value));
                return;
            case TERMUX_APP.KEY_SURFACE_MATERIAL:
                prefs.setSurfaceMaterial((String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY:
                prefs.setSurfaceMaterialIntensity(intOf(value));
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_TINT:
                prefs.setSurfaceGlassTint((String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_RIM:
                prefs.setSurfaceGlassRim((String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_MOTION:
                prefs.setSurfaceGlassMotion((String) value);
                return;
            case TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED:
                prefs.setTerminalBorderEnabled((Boolean) value);
                return;
            case TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS:
                prefs.setTerminalCornerRadius(intOf(value));
                return;
            case TERMUX_APP.KEY_TERMINAL_PANE_GAP:
                prefs.setTerminalPaneGap(intOf(value));
                return;
        }
        SurfaceProperty baseProperty = SurfaceProperty.forBaseKey(key);
        if (baseProperty != null) {
            prefs.setSurfaceBaseValue(baseProperty, intOf(value));
            return;
        }
        SurfaceEditorRows.Row cell = overrideCellForKey(key);
        if (cell != null)
            prefs.detachSurfaceValue(cell.slot, cell.property, intOf(value));
        // Anything else is a key this build does not know; ignored by design.
    }

    /** Every numeric value in the format is an int; a stored look arrives as whatever JSON kept. */
    private static int intOf(@NonNull Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
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
                return null;
            case TERMUX_APP.KEY_FANCIER_GLASS_BEND:
                return prefs.getFancierGlassBendDp();
            case TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH:
                return prefs.getFancierGlassEdgeWidthDp();
            case TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT:
                return prefs.getFancierGlassEdgeLightPercent();
            case TERMUX_APP.KEY_SURFACE_MATERIAL:
                return prefs.getSurfaceMaterial();
            case TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY:
                return prefs.getSurfaceMaterialIntensity();
            case TERMUX_APP.KEY_SURFACE_GLASS_TINT:
                return prefs.getSurfaceGlassTint();
            case TERMUX_APP.KEY_SURFACE_GLASS_RIM:
                return prefs.getSurfaceGlassRim();
            case TERMUX_APP.KEY_SURFACE_GLASS_MOTION:
                return prefs.getSurfaceGlassMotion();
            case TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED:
                return prefs.isTerminalBorderEnabled();
            case TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS:
                return prefs.getTerminalCornerRadius();
            case TERMUX_APP.KEY_TERMINAL_PANE_GAP:
                return prefs.getTerminalPaneGap();
        }
        SurfaceProperty baseProperty = SurfaceProperty.forBaseKey(key);
        if (baseProperty != null)
            return prefs.getSurfaceBaseValue(baseProperty);
        SurfaceEditorRows.Row cell = overrideCellForKey(key);
        if (cell != null)
            return prefs.getSurfaceOverrideValue(cell.slot, cell.property);
        return null;
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
