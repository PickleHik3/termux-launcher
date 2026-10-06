package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * <p>Corners and margins are not a look's (developer, 2026-10-01): they are the global shape
 * Layout mode's Corners and Margin set, and switching Looks keeps them. No preset names them, a
 * saved Custom no longer captures them, and a stored look that still carries them (written before
 * that rule) has them ignored on apply and in {@link #matches}. See {@link #isLayoutOwned}.
 */
public final class SurfacePresets {

    private SurfacePresets() {}

    /**
     * Bumped when a key's meaning changes; unknown keys are already ignored without it. 2 added the
     * glass tint, rim and motion keys; a stored look without them reads as the defaults. 3 added
     * the glass edge's specular and dispersion keys, which a stored look without them reads as 0.
     */
    public static final int FORMAT_VERSION = 3;

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
        // Clear (id minimal): the most transparent glass, wallpaper forward. Blur 4, opacity 10 on
        // every surface (the terminal follows Base, no detached override), grain 4, a subtle
        // depth (bend 4, edge 10, light 18), no specular and no dispersion. Scheme tint,
        // hairline rim, classic motion.
        preset("minimal", R.string.termux_surface_preset_minimal,
            TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, 4, 10, 4, 4, 10, 18, 0, 0,
            look -> { }),
        // Mist: Obsidian-Music's glass and motion (Apache-2.0; see
        // project-docs/reference/launcher/mist-preset-obsidian-values.md). Blur 25
        // and opacity 60 are Obsidian's own numbers. Grain 8 is not its 0.08 noise carried over:
        // Haze lays a soft noise tile at 0.08 alpha, ours is full-contrast random alpha at up to
        // 60/255 of the percentage, so a literal match (about 68) would read as sand. 8 is the
        // same faint tooth.
        preset("frost", R.string.termux_surface_preset_frost,
            TERMUX_APP.SURFACE_MATERIAL_FROST, 50, 25, 60, 8, 9, 20, 18, 0, 0,
            look -> {
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_OBSIDIAN);
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_RIM, TERMUX_APP.GLASS_RIM_GRADIENT);
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, TERMUX_APP.GLASS_MOTION_MIST);
            }),
        // Tint (id stock): glass in Material colours (surface tint toward primary container).
        preset("stock", R.string.termux_surface_preset_stock,
            TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, 10, 46, 10, 4, 10, 18, 0, 0,
            look -> look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_MATERIAL)),
        // Solid: opaque, no blur cost.
        preset("solid", R.string.termux_surface_preset_solid,
            TERMUX_APP.SURFACE_MATERIAL_SOLID, 78, 0, 92, 0, 0, 1, 0, 0, 0,
            look -> { })
    ));

    private interface Extras {
        void addTo(Map<String, Object> look);
    }

    /**
     * Material point, the Base triple and the five Fancier Glass keys; the default glass
     * tint, rim and motion; then the extras, whose puts replace any of those in place. The dock
     * style is deliberately absent — a Look never changes Style — and so are corners and margins,
     * which are Layout's ({@link #isLayoutOwned}).
     */
    private static Preset preset(String id, @StringRes int nameRes, String material,
                                 int intensity, int blur, int opacity, int grain,
                                 int bend, int edgeWidth, int edgeLight,
                                 int specular, int dispersion, Extras extras) {
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL, material);
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, intensity);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_BLUR, blur);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_OPACITY, opacity);
        look.put(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, grain);
        // Every Look wears the full Material tint; a Look never dims it.
        look.put(TERMUX_APP.KEY_SURFACE_BASE_TINT, TERMUX_APP.DEFAULT_SURFACE_BASE_TINT);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_BEND, bend);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, edgeWidth);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT, edgeLight);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR, specular);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, dispersion);
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

    // ------------------------------------------------------------------ Layout's global shape

    /**
     * Whether a property is Layout's rather than a look's: the corner radius and the side gap,
     * at Base and on every surface.
     */
    public static boolean isLayoutOwned(@NonNull SurfaceProperty property) {
        return property == SurfaceProperty.CORNER_RADIUS || property == SurfaceProperty.SIDE_GAP;
    }

    /**
     * Whether a look key is Layout's: Base corners and side gap, the terminal's own corners and
     * margin (its pane gap), and any per-surface corner or side-gap override (a dock's horizontal
     * inset, say). A look never writes or tests these.
     */
    public static boolean isLayoutOwned(@NonNull String key) {
        switch (key) {
            case TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS:
            case TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP:
            case TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS:
            case TERMUX_APP.KEY_TERMINAL_PANE_GAP:
                return true;
        }
        SurfaceEditorRows.Row cell = overrideCellForKey(key);
        return cell != null && isLayoutOwned(cell.property);
    }

    // ------------------------------------------------------------------------ the saved look
    //
    // The built-in presets are looks this build ships; Custom is the one the user tuned. It is
    // written when the editor is left with Done at the Custom stop — not by Done at a Look, which
    // would overwrite the user's own look with a shipped one — so the Custom stop always brings
    // back the last look the user made for themselves.

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
     * The live look in the preset format: the material point, the Base numbers, the terminal's
     * border, and every per-surface cell that is currently detached — named cells being exactly
     * what {@link #apply} re-detaches, and what {@link #matches} tests against. Corners and margins
     * are left out: they are Layout's ({@link #isLayoutOwned}).
     */
    @NonNull
    public static Map<String, Object> captureLook(@NonNull TermuxAppSharedPreferences prefs) {
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL, prefs.getSurfaceMaterial());
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, prefs.getSurfaceMaterialIntensity());
        // After the material point, never before it: a hand-tuned triple no longer sits on any
        // point of any family's curve, and these three are the numbers that must win on apply.
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (!isLayoutOwned(property))
                look.put(property.baseKey, prefs.getSurfaceBaseValue(property));
        }
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, prefs.getSurfaceGlassTint());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_RIM, prefs.getSurfaceGlassRim());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, prefs.getSurfaceGlassMotion());
        look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, prefs.isTerminalBorderEnabled());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_BEND, prefs.getFancierGlassBendDp());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, prefs.getFancierGlassEdgeWidthDp());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT, prefs.getFancierGlassEdgeLightPercent());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR, prefs.getFancierGlassSpecularPercent());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, prefs.getFancierGlassDispersionPercent());
        putCustomOnly(prefs, look);
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (isLayoutOwned(row.property) || prefs.isSurfaceInheriting(row.slot, row.property))
                continue;
            look.put(TermuxAppSharedPreferences.surfaceOverrideKey(row.slot, row.property),
                prefs.getSurfaceOverrideValue(row.slot, row.property));
        }
        return look;
    }

    /**
     * The values only Custom saves and no Look names: the key caps (Key corners, and the opacity
     * the old Keys slider moved with it), the wallpaper's dim and Soft wallpaper. Saved with
     * Custom so its stop comes back whole; a Look leaves them where they are.
     */
    private static void putCustomOnly(@NonNull TermuxAppSharedPreferences prefs,
                                      @NonNull Map<String, Object> look) {
        look.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY, prefs.getInAppKeyboardKeyOpacity());
        look.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            Math.round(prefs.getInAppKeyboardKeyCornerRadiusDp()));
        look.put(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, prefs.getWallpaperBackdropDim());
        look.put(SoftWallpaper.KEY_WALLPAPER_SOFT, SoftWallpaper.isOn(prefs));
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
            // Format 3 added the edge's specular and dispersion; before it there were none.
            if (!look.containsKey(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR))
                look.put(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR, 0);
            if (!look.containsKey(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION))
                look.put(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, 0);
            // The tint strength came later still; before it every look wore the full tint.
            if (!look.containsKey(TERMUX_APP.KEY_SURFACE_BASE_TINT))
                look.put(TERMUX_APP.KEY_SURFACE_BASE_TINT, TERMUX_APP.DEFAULT_SURFACE_BASE_TINT);
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
     * with per-surface keys landing as fresh detaches. Corners and margins are not touched, links
     * included: they are Layout's, and a stored look that still names them is not obeyed. The
     * caller owns offering the Undo.
     *
     * <p>The fifty-odd writes land as one editor: the setters write into a {@link Batch} over the
     * store, which then applies everything at once — one in-memory commit, one disk write, and
     * listeners that see the finished look rather than each step towards it. The keys and values
     * are exactly those {@link #applyEach} writes one by one.
     */
    public static void apply(@NonNull TermuxAppSharedPreferences prefs, @NonNull Preset preset) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null) {
            applyEach(prefs, preset);
            return;
        }
        Batch batch = new Batch(store);
        applyEach(new TermuxAppSharedPreferences(prefs.getContext(), batch,
            prefs.getMultiProcessSharedPreferences()), preset);
        batch.applyToStore();
    }

    /** {@link #apply}, one setter and one {@code apply()} at a time: the reference it must match. */
    @VisibleForTesting
    static void applyEach(@NonNull TermuxAppSharedPreferences prefs, @NonNull Preset preset) {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!isLayoutOwned(property))
                    prefs.setSurfaceInheriting(slot, property, true);
            }
        }
        for (Map.Entry<String, Object> entry : preset.values.entrySet()) {
            if (!isLayoutOwned(entry.getKey()))
                applyOne(prefs, entry.getKey(), entry.getValue());
        }
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
            case TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR:
                prefs.setFancierGlassSpecularPercent(intOf(value));
                return;
            case TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION:
                prefs.setFancierGlassDispersionPercent(intOf(value));
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
            case TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY:
                prefs.setInAppKeyboardKeyOpacity(intOf(value));
                return;
            case TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP:
                prefs.setInAppKeyboardKeyCornerRadiusDp(intOf(value));
                return;
            case TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM:
                prefs.setWallpaperBackdropDim(intOf(value));
                return;
            case SoftWallpaper.KEY_WALLPAPER_SOFT:
                SoftWallpaper.set(prefs, Boolean.TRUE.equals(value));
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
     * Corners and margins are Layout's and never decide a match.
     */
    public static boolean matches(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull Preset preset) {
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (isLayoutOwned(row.property))
                continue;
            String key = TermuxAppSharedPreferences.surfaceOverrideKey(row.slot, row.property);
            if (prefs.isSurfaceInheriting(row.slot, row.property) == preset.values.containsKey(key))
                return false;
        }
        for (Map.Entry<String, Object> entry : preset.values.entrySet()) {
            if (isLayoutOwned(entry.getKey()))
                continue;
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
            case TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR:
                return prefs.getFancierGlassSpecularPercent();
            case TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION:
                return prefs.getFancierGlassDispersionPercent();
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
            case TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY:
                return prefs.getInAppKeyboardKeyOpacity();
            case TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP:
                return Math.round(prefs.getInAppKeyboardKeyCornerRadiusDp());
            case TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM:
                return prefs.getWallpaperBackdropDim();
            case SoftWallpaper.KEY_WALLPAPER_SOFT:
                return SoftWallpaper.isOn(prefs);
        }
        SurfaceProperty baseProperty = SurfaceProperty.forBaseKey(key);
        if (baseProperty != null)
            return prefs.getSurfaceBaseValue(baseProperty);
        SurfaceEditorRows.Row cell = overrideCellForKey(key);
        if (cell != null)
            return prefs.getSurfaceOverrideValue(cell.slot, cell.property);
        return null;
    }

    /**
     * A {@link SharedPreferences} that reads through to a store but holds every write until
     * {@link #applyToStore}: reads see the held writes, so a setter that reads what an earlier one
     * wrote behaves exactly as it would against the store. Only {@link #apply} uses it.
     */
    @VisibleForTesting
    static final class Batch implements SharedPreferences {
        /** Marks a held removal. */
        private static final Object REMOVED = new Object();

        private final SharedPreferences mStore;
        /** Held writes in the order made; {@link #REMOVED} for a removal. */
        private final LinkedHashMap<String, Object> mPending = new LinkedHashMap<>();
        private boolean mCleared;

        Batch(@NonNull SharedPreferences store) {
            mStore = store;
        }

        /** Writes everything held to the store as one editor, then forgets it. */
        void applyToStore() {
            if (!mCleared && mPending.isEmpty())
                return;
            SharedPreferences.Editor editor = mStore.edit();
            if (mCleared)
                editor.clear();
            for (Map.Entry<String, Object> entry : mPending.entrySet())
                put(editor, entry.getKey(), entry.getValue());
            editor.apply();
            mPending.clear();
            mCleared = false;
        }

        @SuppressWarnings("unchecked")
        private static void put(SharedPreferences.Editor editor, String key, Object value) {
            if (value == REMOVED) editor.remove(key);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }

        /** The held value, {@link #REMOVED}, or null when this batch has not touched the key. */
        @Nullable
        private Object held(String key) {
            Object value = mPending.get(key);
            if (value != null) return value;
            return mCleared ? REMOVED : null;
        }

        @Override
        public Map<String, ?> getAll() {
            HashMap<String, Object> all = mCleared ? new HashMap<>() : new HashMap<>(mStore.getAll());
            for (Map.Entry<String, Object> entry : mPending.entrySet()) {
                if (entry.getValue() == REMOVED) all.remove(entry.getKey());
                else all.put(entry.getKey(), entry.getValue());
            }
            return all;
        }

        @Nullable
        @Override
        public String getString(String key, @Nullable String defValue) {
            Object value = held(key);
            if (value == null) return mStore.getString(key, defValue);
            return value == REMOVED ? defValue : (String) value;
        }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
            Object value = held(key);
            if (value == null) return mStore.getStringSet(key, defValues);
            return value == REMOVED ? defValues : (Set<String>) value;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = held(key);
            if (value == null) return mStore.getInt(key, defValue);
            return value == REMOVED ? defValue : (Integer) value;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = held(key);
            if (value == null) return mStore.getLong(key, defValue);
            return value == REMOVED ? defValue : (Long) value;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = held(key);
            if (value == null) return mStore.getFloat(key, defValue);
            return value == REMOVED ? defValue : (Float) value;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = held(key);
            if (value == null) return mStore.getBoolean(key, defValue);
            return value == REMOVED ? defValue : (Boolean) value;
        }

        @Override
        public boolean contains(String key) {
            Object value = held(key);
            if (value == null) return mStore.contains(key);
            return value != REMOVED;
        }

        @Override
        public SharedPreferences.Editor edit() {
            return new BatchEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            mStore.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            mStore.unregisterOnSharedPreferenceChangeListener(listener);
        }

        /** Collects one editor's changes and folds them into the batch on apply or commit. */
        private final class BatchEditor implements SharedPreferences.Editor {
            private final LinkedHashMap<String, Object> mChanges = new LinkedHashMap<>();
            private boolean mClear;

            private SharedPreferences.Editor change(String key, @Nullable Object value) {
                // A null value removes the key, as it does on a real editor.
                mChanges.put(key, value == null ? REMOVED : value);
                return this;
            }

            @Override public SharedPreferences.Editor putString(String key, @Nullable String value) { return change(key, value); }
            @Override public SharedPreferences.Editor putStringSet(String key, @Nullable Set<String> values) { return change(key, values); }
            @Override public SharedPreferences.Editor putInt(String key, int value) { return change(key, value); }
            @Override public SharedPreferences.Editor putLong(String key, long value) { return change(key, value); }
            @Override public SharedPreferences.Editor putFloat(String key, float value) { return change(key, value); }
            @Override public SharedPreferences.Editor putBoolean(String key, boolean value) { return change(key, value); }
            @Override public SharedPreferences.Editor remove(String key) { return change(key, null); }

            @Override
            public SharedPreferences.Editor clear() {
                mClear = true;
                return this;
            }

            @Override
            public boolean commit() {
                // As on a real editor, a clear empties the store before this editor's own changes.
                if (mClear) {
                    mPending.clear();
                    mCleared = true;
                }
                for (Map.Entry<String, Object> entry : mChanges.entrySet()) {
                    // Re-inserted so the held order follows the latest write.
                    mPending.remove(entry.getKey());
                    mPending.put(entry.getKey(), entry.getValue());
                }
                mChanges.clear();
                mClear = false;
                return true;
            }

            @Override
            public void apply() {
                commit();
            }
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
