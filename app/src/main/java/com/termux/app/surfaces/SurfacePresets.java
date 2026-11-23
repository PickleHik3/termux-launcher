package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.place.SeamDress;
import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.GlassMaterial;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGridMigration;
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
 * <p>A preset is a versioned, ordered map of key → value over the keys the editor owns. A glass
 * cell is named by its grid key (ADR 0017): Global as {@code look.<property>}, a detached surface
 * as {@code look.<property>.<element>}, Frost as its stop; every other value by its preference
 * key. The semantics are the theme-export format's: a key that is absent is not "unchanged" but
 * "the default shape of that cell" — applying a preset first reattaches every surface to Global, so
 * an absent per-surface key means that cell follows Global, and a present one is a detached
 * override. Unknown keys are ignored on read, so a preset written by a newer build degrades
 * instead of failing.
 *
 * <p>The Global blur/opacity/grain triple of a preset is computed from its material point via
 * {@link SurfaceMaterials}, so a freshly applied preset shows its material selected rather than
 * "Custom". A preset whose look sits off every curve (Mist) names its triple in its extras, which
 * win over the computed one.
 *
 * <p>Beyond the numbers a preset carries four enum-like strings: the glass's ink, the accent hue
 * its Tint amount mixes in, its rim and its motion profile; and one glass number no per-surface
 * row holds, Vibrancy. Every preset states them (the defaults unless it says otherwise),
 * so applying one always resets the others' choice.
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
     * 4 added the edge's clarity key, which a stored look without it reads as 0. 5 is the glass
     * controls' unification: the Tint values became an accent amount (0 the Look's own colour,
     * 100 the Material blend) where they had been a strength of the Look's tint family, the
     * {@code material} family became {@code scheme} at its amount, blur and grain moved onto the
     * Glass slider's curve, and the hue, Shade and Vibrancy keys arrived. A stored look older
     * than 5 is carried across on read ({@link #fromLegacy}), so it applies as it looked. 6
     * stretched the Tint amount: 100 now moves the ink all the way to the accent's container, and
     * the Material blend is 35 ({@code GlassLook.TINT_LOOK_PERCENT}); a look older than 6 has
     * every Tint value scaled on read ({@link #rescaleTintAmounts}). Shade, which 5 brought, was
     * retired (2026-10-17): a look that still names it has the key dropped on read
     * ({@link #RETIRED_SHADE_KEY}), so it applies, and draws, as Shade 0. 7 is Frost, Depth and
     * Shine: Shine ({@link TERMUX_APP#KEY_SURFACE_GLASS_SHINE}) replaces the rim string, the
     * dispersion and the four edge keys, a look older than 7 has its rim and dispersion folded
     * into one on read ({@link GlassMaterial#shineForRimAndEdge}), and the retired keys are dropped.
     */
    public static final int FORMAT_VERSION = 7;

    /**
     * The retired Shade's key, kept only so a stored look and the store itself shed it
     * ({@link #deserialize}, {@link GlassControlsMigration}). Nothing reads its value.
     */
    static final String RETIRED_SHADE_KEY = "surface_glass_shade";

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

    /** The keys a look saved before format 7 carries and the app now drops. */
    private static final String[] RETIRED_GLASS_KEYS = {
        TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT,
        TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR, TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION,
        TERMUX_APP.KEY_FANCIER_GLASS_CLARITY, TERMUX_APP.KEY_FANCIER_GLASS_FACE_CLARITY,
        TERMUX_APP.KEY_FANCIER_GLASS_DIM, TERMUX_APP.KEY_FANCIER_GLASS_SIZE_LENS,
        TERMUX_APP.KEY_SURFACE_GLASS_RIM,
    };

    private static final List<Preset> PRESETS = Collections.unmodifiableList(Arrays.asList(
        // Clear (id minimal): the most transparent glass, wallpaper forward. Glass 20 (blur 7,
        // grain 0), opacity 10, its own colour (Tint 0), the realistic slab (Depth 60, 27 dp) and
        // the room light as it is (Shine 50). Its face is nearly the wallpaper itself, so the lens
        // edge carries the glass.
        preset("minimal", R.string.termux_surface_preset_minimal,
            TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, GlassControls.blurForGlass(GlassControls.CLEAR),
            10, 0, 0, GlassMaterial.REALISTIC_BEND_DP, GlassMaterial.SHINE_DEFAULT,
            look -> { }),
        // Frosted (id frost; Mist before): a blurred face on Clear's edge. Obsidian-Music's glass
        // and motion (Apache-2.0; see
        // project-docs/reference/launcher/mist-preset-obsidian-values.md): Glass 75 (blur 25,
        // grain 7), opacity 60 and Obsidian's ink, untinted (Tint 0). On light glass (the white
        // Obsidian ink) its opacity is 35, not 60, which reads as milk there
        // (MIST_LIGHT_OPACITY, presets(boolean)).
        preset("frost", R.string.termux_surface_preset_frost,
            TERMUX_APP.SURFACE_MATERIAL_FROST, 50, GlassControls.blurForGlass(GlassControls.MIST),
            60, GlassControls.grainForGlass(GlassControls.MIST), 0,
            GlassMaterial.REALISTIC_BEND_DP, GlassMaterial.SHINE_DEFAULT,
            look -> {
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_OBSIDIAN);
                look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, TERMUX_APP.GLASS_MOTION_MIST);
            }),
        // Tinted (id stock; Tint before): glass in Material colours, the scheme's ink at the
        // Material blend (Tint 35: 0.35 toward the primary container, a 0.14 wash toward the
        // primary), Glass 40 (blur 12, grain 4), with the thinnest pane of the Looks (21 dp). At
        // opacity 46 the face carries the accent alone, so the frame takes no tint.
        preset("stock", R.string.termux_surface_preset_stock,
            TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, GlassControls.blurForGlass(GlassControls.TINT),
            46, GlassControls.grainForGlass(GlassControls.TINT),
            com.termux.app.chrome.GlassLook.TINT_LOOK_PERCENT, 21, GlassMaterial.SHINE_DEFAULT,
            look -> { }),
        // Solid: opaque, no blur cost, and no lens to show: a flat slab with a quiet rim. Blur 0 and
        // grain 0 sit off the Glass curve: no frost at all.
        preset("solid", R.string.termux_surface_preset_solid,
            TERMUX_APP.SURFACE_MATERIAL_SOLID, 78, 0, 92, 0, 0, 0, 30,
            look -> { }),
        // Liquid: the dramatic one. Clear's face under a thick lens (Depth 90, a 41 dp slab of a
        // higher index), strong light (Shine 85) and a touch of colour split.
        preset("liquid", R.string.termux_surface_preset_liquid,
            TERMUX_APP.SURFACE_MATERIAL_GLASS, 50, GlassControls.blurForGlass(GlassControls.CLEAR),
            8, 0, 0, GlassMaterial.bendForDepth(90), 85,
            look -> { })
    ));

    private interface Extras {
        void addTo(Map<String, Object> look);
    }

    /** The generation an install's Looks were last brought up to; absent is 0. Only the reset names it. */
    static final String KEY_LOOKS_GENERATION = "surface_looks_generation";

    /**
     * Material point, the Global triple, Depth's thickness and Shine; the default glass
     * tint and motion; then the extras, whose puts replace any of those in place. The dock
     * style is deliberately absent — a Look never changes Style — and so are corners and margins,
     * which are Layout's ({@link #isLayoutOwned}).
     */
    private static Preset preset(String id, @StringRes int nameRes, String material,
                                 int intensity, int blur, int opacity, int grain, int tint,
                                 int bend, int shine, Extras extras) {
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL, material);
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, intensity);
        look.put(cellKey(null, SurfaceProperty.BLUR), FrostStops.nearestStopByBlur(blur));
        look.put(cellKey(null, SurfaceProperty.OPACITY), opacity);
        look.put(cellKey(null, SurfaceProperty.GRAIN), grain);
        // The accent amount: 0 is the Look's own colour, 35 the Material blend (Tint's), 100 the
        // accent's container.
        look.put(cellKey(null, SurfaceProperty.TINT), tint);
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_BEND, bend);
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_SHINE, shine);
        putGlassDefaults(look);
        extras.addTo(look);
        return new Preset(id, nameRes, look);
    }

    private static void putGlassDefaults(Map<String, Object> look) {
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT);
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION);
        putGlassControlDefaults(look);
    }

    /** The hue and Vibrancy every Look ships with (format 5): primary, as today. */
    private static void putGlassControlDefaults(Map<String, Object> look) {
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE, TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT_HUE);
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY,
            TERMUX_APP.DEFAULT_SURFACE_GLASS_VIBRANCY);
    }

    /** A shipped Look's Global numbers the editor shows: its Frost stop, opacity and tint. */
    public static final class ShippedLook {
        public final int frostStop;
        public final int opacity;
        public final int tint;

        ShippedLook(int frostStop, int opacity, int tint) {
            this.frostStop = frostStop;
            this.opacity = opacity;
            this.tint = tint;
        }
    }

    /**
     * The shipped Look the Look slider's {@code stop} names (Clear, Mist, Tint, Solid; see
     * {@link AppearanceLooks#presetIdForStop}) as its Frost stop, opacity and tint on dark glass,
     * or null at Custom. Read-only; the editor shows these without applying the Look.
     */
    @Nullable
    public static ShippedLook shippedLook(int stop) {
        Preset preset = AppearanceLooks.presetForStop(PRESETS, stop);
        if (preset == null) return null;
        return new ShippedLook(
            intOf(preset.values.get(cellKey(null, SurfaceProperty.BLUR))),
            intOf(preset.values.get(cellKey(null, SurfaceProperty.OPACITY))),
            intOf(preset.values.get(cellKey(null, SurfaceProperty.TINT))));
    }

    /** The Looks as they ship on dark glass; {@link #presets(boolean)} for the glass on screen. */
    @NonNull
    public static List<Preset> presets() {
        return PRESETS;
    }

    // --------------------------------------------------------------- Mist on light glass

    /**
     * Mist's Global opacity where its glass is light, the white Obsidian ink: sixty of white over the
     * wallpaper reads as milk (pass 2, 2026-10-17). Every other value of Mist, and every other
     * Look, is the same on either glass.
     */
    static final int MIST_LIGHT_OPACITY = 35;

    /**
     * Whether the glass the Looks were last written for is light; absent is dark, the one glass
     * Mist had before {@link #MIST_LIGHT_OPACITY}. Moved by {@link #followGlassTheme} only.
     */
    static final String KEY_LOOKS_LIGHT_GLASS = "surface_looks_light_glass";

    private static final List<Preset> LIGHT_PRESETS = Collections.unmodifiableList(lightPresets());

    private static List<Preset> lightPresets() {
        java.util.ArrayList<Preset> light = new java.util.ArrayList<>(PRESETS.size());
        for (Preset preset : PRESETS) {
            if (!"frost".equals(preset.id)) {
                light.add(preset);
                continue;
            }
            LinkedHashMap<String, Object> values = new LinkedHashMap<>(preset.values);
            values.put(cellKey(null, SurfaceProperty.OPACITY), MIST_LIGHT_OPACITY);
            light.add(new Preset(preset.id, preset.nameRes, values));
        }
        return light;
    }

    /** The Looks as they ship on light glass ({@code lightGlass}) or on dark. */
    @NonNull
    public static List<Preset> presets(boolean lightGlass) {
        return lightGlass ? LIGHT_PRESETS : PRESETS;
    }

    /**
     * The Looks for the glass the store was last brought to ({@link #followGlassTheme}): what the
     * editor applies a Look from and recognises one by, so Mist reads as Mist on either glass.
     */
    @NonNull
    public static List<Preset> presets(@NonNull TermuxAppSharedPreferences prefs) {
        return presets(isLightGlass(prefs));
    }

    /** Whether the store's Looks were last written for light glass. */
    static boolean isLightGlass(@NonNull TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        return store != null && store.getBoolean(KEY_LOOKS_LIGHT_GLASS, false);
    }

    /**
     * The glass went light or dark (a theme, scheme or wallpaper-mode change; or the first pass
     * after an update, for a store never told): a live look that is the shipped Mist as it stands
     * on the glass it was written for takes Mist as it ships on this one, so its stop stays Mist.
     * Any other look, Custom and the stored Custom copy included, is left as it is. Cheap when
     * nothing moved, one stored boolean compared, so it runs on every chrome pass.
     *
     * @return true when the store was written
     */
    public static boolean followGlassTheme(@NonNull TermuxAppSharedPreferences prefs,
                                           boolean lightGlass) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null)
            return false;
        boolean was = store.getBoolean(KEY_LOOKS_LIGHT_GLASS, false);
        if (was == lightGlass)
            return false;
        Batch batch = new Batch(store);
        TermuxAppSharedPreferences into = writingInto(prefs, batch);
        Preset mistBefore = byId(presets(was), "frost");
        if (mistBefore != null && matches(into, mistBefore)) {
            Preset mistNow = byId(presets(lightGlass), "frost");
            if (mistNow != null)
                apply(into, mistNow);
        }
        batch.edit().putBoolean(KEY_LOOKS_LIGHT_GLASS, lightGlass).apply();
        batch.applyToStore();
        return true;
    }

    @Nullable
    private static Preset byId(@NonNull List<Preset> presets, @NonNull String id) {
        for (Preset preset : presets) {
            if (preset.id.equals(id))
                return preset;
        }
        return null;
    }

    // ------------------------------------------------------------------ Layout's global shape

    /**
     * Whether a property is Layout's rather than a look's: the corner radius and the side gap,
     * at Global and on every surface.
     */
    public static boolean isLayoutOwned(@NonNull SurfaceProperty property) {
        return property == SurfaceProperty.CORNER_RADIUS || property == SurfaceProperty.SIDE_GAP;
    }

    /**
     * Whether a look key is Layout's: Global corners and side gap, the terminal's own corners and
     * margin (its pane gap), and any per-surface corner or side-gap override (a dock's horizontal
     * inset, say). A look never writes or tests these.
     */
    public static boolean isLayoutOwned(@NonNull String key) {
        switch (key) {
            case TERMUX_APP.KEY_SURFACE_GLOBAL_CORNER_RADIUS:
            case TERMUX_APP.KEY_SURFACE_GLOBAL_SIDE_GAP:
            case TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS:
            case TERMUX_APP.KEY_TERMINAL_PANE_GAP:
                return true;
        }
        LookGrid.Key cell = LookGrid.parse(key);
        if (cell != null)
            return cell.property == LookGrid.Property.CORNERS
                || cell.property == LookGrid.Property.MARGIN;
        // A look saved before the grid may still name a surface's own corner or gap by its old key.
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (isLayoutOwned(property)
                    && key.equals(TermuxAppSharedPreferences.surfaceOverrideKey(slot, property)))
                    return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------ the saved look
    //
    // The built-in presets are looks this build ships; Custom is the one the user tuned. It is
    // written when the editor opens on a Custom, when its slider leaves a Custom the user touched,
    // and by Done at the Custom stop — never when a Look is applied, which would overwrite the
    // user's own look with a shipped one, and never dropped by Done on a Look — so the Custom stop
    // always brings back the last look the user made for themselves, across editor visits and
    // restarts.

    /**
     * The user's saved look as a preset, or null when they have not saved one.
     *
     * <p>Stored rather than derived, so it survives every later edit and every Look chosen after
     * it. The Look slider's move to Custom applies it whole ({@link #applyCustom}) when the visit
     * has no Custom values of its own; with none saved, Custom starts from the Look on screen.
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
     * The live look in the preset format: the material point, the Global numbers, the terminal's
     * border, and every per-surface cell that is currently detached — named cells being exactly
     * what {@link #apply} re-detaches, and what {@link #matches} tests against. Corners and margins
     * are left out: they are Layout's ({@link #isLayoutOwned}).
     */
    @NonNull
    public static Map<String, Object> captureLook(@NonNull TermuxAppSharedPreferences prefs) {
        Map<String, Object> look = captureLook(prefs, PageKind.TERMINAL);
        Map<String, Object> home = captureLook(prefs, PageKind.HOME);
        if (!halfCells(home).equals(halfCells(look))) {
            look.put(KEY_HOME_LOOK, Boolean.TRUE);
            for (Map.Entry<String, Object> entry : halfCells(home).entrySet())
                look.put(entry.getKey() + HOME_TWIN_SUFFIX, entry.getValue());
        }
        return look;
    }

    /**
     * One half's look in the preset format, under the plain keys: what a look saved from that half
     * alone carries, and applies to both halves when loaded ({@link #apply}).
     */
    @NonNull
    public static Map<String, Object> captureLook(@NonNull TermuxAppSharedPreferences prefs,
                                                  @NonNull PageKind half) {
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL, prefs.getSurfaceMaterial(pageOf(half)));
        look.put(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, prefs.getSurfaceMaterialIntensity(pageOf(half)));
        // After the material point, never before it: a hand-tuned triple no longer sits on any
        // point of any family's curve, and these three are the numbers that must win on apply.
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (!isLayoutOwned(property))
                look.put(cellKey(null, property), cellValue(prefs, null, property, pageOf(half)));
        }
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, prefs.getSurfaceGlassTint());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, prefs.getSurfaceGlassMotion());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE, prefs.getSurfaceGlassTintHue());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, prefs.getSurfaceGlassVibrancy());
        look.put(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, prefs.isTerminalBorderEnabled());
        look.put(TERMUX_APP.KEY_FANCIER_GLASS_BEND, prefs.getFancierGlassBendDp());
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_SHINE, prefs.getGlassShine());
        putCustomOnly(prefs, look);
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (isLayoutOwned(row.property)
                || prefs.isSurfaceInheriting(row.slot, row.property, pageOf(half)))
                continue;
            look.put(cellKey(row.slot, row.property),
                cellValue(prefs, row.slot, row.property, pageOf(half)));
        }
        return look;
    }

    // ------------------------------------------------------------------ the two halves (ADR 0013)
    //
    // A look carries the Terminal's values under the plain keys. Where Home's differ it also
    // carries Home's per-half cells (its material point, Global numbers and detached surface
    // values) under each key's "_home" twin, with KEY_HOME_LOOK marking that Home's cells are all
    // named: a per-half cell without its twin then follows Home's Global. A look without the marker
    // (every shipped Look, and a look saved from one half) is one look for both halves. A build
    // before the halves ignores the twins and the marker as unknown keys.

    /** The preset format's suffix for Home's twin of a key; the format predates the page keys. */
    static final String HOME_TWIN_SUFFIX = "_home";

    /** Marks a look that names Home's per-half cells apart from the Terminal's. */
    static final String KEY_HOME_LOOK = "surface_look_home";

    /** Whether a look holds Home's values apart from the Terminal's. */
    public static boolean hasHomeLook(@NonNull Preset preset) {
        return Boolean.TRUE.equals(preset.values.get(KEY_HOME_LOOK));
    }

    /**
     * The page id a screen kind's look values are read and written under: the wall for Home's, the
     * Terminal page for any other, and "no page" (all) for none.
     */
    static int pageOf(@Nullable PageKind screen) {
        return screen == null ? TermuxAppSharedPreferences.PAGE_ALL
            : SeamDress.lookPageId(screen, TermuxAppSharedPreferences.terminalPageId());
    }

    /**
     * Whether a plain look key is one a half holds its own value for: the material point, a
     * per-half Global number, or a per-half surface cell.
     */
    static boolean isHalfKey(@NonNull String key) {
        if (TERMUX_APP.KEY_SURFACE_MATERIAL.equals(key)
            || TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY.equals(key))
            return true;
        SurfaceProperty global = globalOfKey(key);
        if (global != null)
            return TermuxAppSharedPreferences.isPerPageProperty(global);
        SurfaceEditorRows.Row cell = rowOfKey(key);
        return cell != null && TermuxAppSharedPreferences.isPerPage(cell.slot, cell.property);
    }

    /** The per-half cells of a one-half look, in order. */
    @NonNull
    private static Map<String, Object> halfCells(@NonNull Map<String, Object> look) {
        LinkedHashMap<String, Object> cells = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : look.entrySet()) {
            if (isHalfKey(entry.getKey()))
                cells.put(entry.getKey(), entry.getValue());
        }
        return cells;
    }

    /** Whether a key is a Home twin or the marker, which only {@link #halfOf} reads. */
    private static boolean isHomeOnlyKey(@NonNull String key) {
        return KEY_HOME_LOOK.equals(key) || homeTwinOf(key) != null;
    }

    /** The plain key a Home twin stands for, or null when {@code key} is not one. */
    @Nullable
    private static String homeTwinOf(@NonNull String key) {
        String suffix = HOME_TWIN_SUFFIX;
        if (!key.endsWith(suffix))
            return null;
        String plain = key.substring(0, key.length() - suffix.length());
        return isHalfKey(plain) ? plain : null;
    }

    /**
     * One half's look out of {@code preset}, under the plain keys: the look itself for the
     * Terminal or for a look without Home's own; for Home, the shared keys with Home's per-half
     * cells in place of the Terminal's.
     */
    @NonNull
    static Preset halfOf(@NonNull Preset preset, @NonNull PageKind half) {
        // The chrome wears one look on every page (ADR 0031): a stored look's Home twins are
        // not read, whichever half asks.
        boolean home = false;
        LinkedHashMap<String, Object> look = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : preset.values.entrySet()) {
            String key = entry.getKey();
            if (isHomeOnlyKey(key) || (home && isHalfKey(key)))
                continue;
            look.put(key, entry.getValue());
        }
        if (home) {
            for (Map.Entry<String, Object> entry : preset.values.entrySet()) {
                String plain = homeTwinOf(entry.getKey());
                if (plain != null)
                    look.put(plain, entry.getValue());
            }
        }
        return new Preset(preset.id, preset.nameRes, look);
    }

    /**
     * The values only Custom saves and no Look names: the key caps (Key corners, once set, and
     * the cap opacity Keys moves), the wallpaper's dim and Soft wallpaper, and where Global's and
     * each surface's Glass slider stood (only those stored; an absent one is forgotten on apply,
     * as a Look forgets it). Saved with Custom so its stop comes back whole. A Look leaves the
     * caps' corners, dim and Soft wallpaper where they are; Keys and the Glass positions choosing
     * a Look on the editor's slider puts back ({@link #applyLook}).
     */
    private static void putCustomOnly(@NonNull TermuxAppSharedPreferences prefs,
                                      @NonNull Map<String, Object> look) {
        look.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY, prefs.getInAppKeyboardKeyOpacity());
        // Only a radius the user set: one still the Style's default (the key absent) stays
        // absent, so putting Custom back does not pin today's default.
        SharedPreferences reads = com.termux.shared.settings.preferences.SharedPreferencesPreview
            .readsFor(prefs.getSharedPreferences());
        if (reads == null || reads.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP))
            look.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
                Math.round(prefs.getInAppKeyboardKeyCornerRadiusDp()));
        look.put(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, prefs.getWallpaperBackdropDim());
        look.put(SoftWallpaper.KEY_WALLPAPER_SOFT, SoftWallpaper.isOn(prefs));
        putGlassPosition(prefs, look, null);
        for (SurfaceSlot slot : SurfaceSlot.values())
            putGlassPosition(prefs, look, slot);
    }

    private static void putGlassPosition(@NonNull TermuxAppSharedPreferences prefs,
                                         @NonNull Map<String, Object> look,
                                         @Nullable SurfaceSlot slot) {
        int position = prefs.getSurfaceGlassPosition(slot);
        if (position >= 0)
            look.put(TermuxAppSharedPreferences.surfaceGlassPositionKey(slot), position);
    }

    /** The surface (null for Global) whose Glass slider position a key stores, if it is one. */
    @Nullable
    private static GlassPositionKey glassPositionKey(@NonNull String key) {
        if (key.equals(TermuxAppSharedPreferences.surfaceGlassPositionKey(null)))
            return new GlassPositionKey(null);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (key.equals(TermuxAppSharedPreferences.surfaceGlassPositionKey(slot)))
                return new GlassPositionKey(slot);
        }
        return null;
    }

    /** A Glass slider position key's surface; {@link #slot} null for Global. */
    private static final class GlassPositionKey {
        @Nullable final SurfaceSlot slot;

        GlassPositionKey(@Nullable SurfaceSlot slot) {
            this.slot = slot;
        }
    }

    // ------------------------------------------------------------------------- grid cells

    /** The element column a surface's property is held in, or null (Global, or a Where-level property). */
    @Nullable
    private static LookGrid.Element elementOf(@Nullable SurfaceSlot slot,
                                              @NonNull SurfaceProperty property) {
        return slot != null && property.grid.perElement ? slot.element : null;
    }

    /** The grid key a look names a cell by: Global for a null surface, the surface's own cell else. */
    @NonNull
    static String cellKey(@Nullable SurfaceSlot slot, @NonNull SurfaceProperty property) {
        return LookGrid.key(property.grid, elementOf(slot, property), LookGrid.NO_PAGE);
    }

    /** The property a grid key names at Global, or null for any other key. */
    @Nullable
    private static SurfaceProperty globalOfKey(@NonNull String key) {
        LookGrid.Key cell = LookGrid.parse(key);
        return cell == null || cell.element != null || cell.page >= 0 || cell.kind != null ? null
            : propertyOf(cell.property);
    }

    /** The (surface, property) row an element's grid key names, or null for any other key. */
    @Nullable
    private static SurfaceEditorRows.Row rowOfKey(@NonNull String key) {
        LookGrid.Key cell = LookGrid.parse(key);
        if (cell == null || cell.element == null || cell.page >= 0 || cell.kind != null) return null;
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (row.slot.element == cell.element && row.property.grid == cell.property)
                return row;
        }
        return null;
    }

    @Nullable
    private static SurfaceProperty propertyOf(@NonNull LookGrid.Property grid) {
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (property.grid == grid) return property;
        }
        return null;
    }

    /**
     * A surface's (null for Global) value as the grid resolves it, in the grid's unit; the same on
     * every page (ADR 0031), whatever {@code pageId}.
     */
    private static int cellValue(@NonNull TermuxAppSharedPreferences prefs,
                                 @Nullable SurfaceSlot slot, @NonNull SurfaceProperty property,
                                 int pageId) {
        return prefs.lookGrid().value(property.grid, elementOf(slot, property));
    }

    /**
     * Writes a look's number into a cell: Global for a null surface, else the surface's own cell
     * (the same on every page, ADR 0031). A surface without the property is left alone.
     */
    private static void writeCell(@NonNull TermuxAppSharedPreferences prefs,
                                  @Nullable SurfaceSlot slot, @NonNull SurfaceProperty property,
                                  int pageId, int value) {
        LookGrid grid = prefs.lookGrid();
        if (slot == null) {
            grid.write(property.grid, null, value);
            return;
        }
        LookGrid.Element element = elementOf(slot, property);
        if (element == null || !TermuxAppSharedPreferences.hasSurfaceProperty(slot, property))
            return;
        grid.write(property.grid, element, value);
    }

    /** Puts a surface's cell back on Global. */
    private static void clearCell(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull SurfaceSlot slot, @NonNull SurfaceProperty property,
                                  int pageId) {
        LookGrid.Element element = elementOf(slot, property);
        if (element == null || !TermuxAppSharedPreferences.hasSurfaceProperty(slot, property))
            return;
        prefs.lookGrid().clear(property.grid, element);
    }

    /**
     * The grid key and value a stored look's old cell name stands for, {@code _home} twins carried
     * across and a blur's dp as its Frost stop; null for every other key. The old names are the
     * table the upgrade reads ({@code LookGridMigration}): {@link SurfaceProperty#globalKey} and
     * {@link TermuxAppSharedPreferences#surfaceOverrideKey}. Their one use here is a look saved
     * before the grid.
     */
    @Nullable
    private static Map.Entry<String, Object> gridCellOfOld(@NonNull String key,
                                                           @Nullable Object value) {
        boolean twin = key.endsWith(HOME_TWIN_SUFFIX);
        String old = twin ? key.substring(0, key.length() - HOME_TWIN_SUFFIX.length()) : key;
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (isLayoutOwned(property)) continue;
            SurfaceSlot named = null;
            boolean found = property.globalKey.equals(old);
            if (!found) {
                for (SurfaceSlot slot : SurfaceSlot.values()) {
                    if (old.equals(TermuxAppSharedPreferences.surfaceOverrideKey(slot, property))) {
                        named = slot;
                        found = true;
                    }
                }
            }
            if (!found) continue;
            Object grid = value;
            if (value instanceof Integer && property == SurfaceProperty.BLUR)
                grid = FrostStops.nearestStopByBlur((Integer) value);
            return new java.util.AbstractMap.SimpleEntry<>(
                cellKey(named, property) + (twin ? HOME_TWIN_SUFFIX : ""), grid);
        }
        return null;
    }

    /** {@code look} with every old cell name replaced by its grid key, in place and in order. */
    private static void toGridKeys(@NonNull Map<String, Object> look) {
        LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : look.entrySet()) {
            Map.Entry<String, Object> cell = gridCellOfOld(entry.getKey(), entry.getValue());
            if (cell != null) converted.put(cell.getKey(), cell.getValue());
            else converted.put(entry.getKey(), entry.getValue());
        }
        look.clear();
        look.putAll(converted);
    }

    /**
     * Rewrites a saved Custom look still naming its cells by the old keys under the grid keys,
     * once: a blob with no old cell name is left as it is.
     */
    public static void rewriteStoredCustom(@NonNull TermuxAppSharedPreferences prefs) {
        String stored = prefs.getSurfaceCustomPreset();
        if (stored.isEmpty()) return;
        try {
            org.json.JSONObject json = new org.json.JSONObject(stored);
            boolean old = false;
            for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); )
                if (gridCellOfOld(keys.next(), 0) != null) old = true;
            if (!old) return;
        } catch (org.json.JSONException e) {
            return;
        }
        Map<String, Object> look = deserialize(stored);
        if (look != null && !look.isEmpty())
            prefs.setSurfaceCustomPreset(serialize(look));
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
        int format;
        try {
            org.json.JSONObject json = new org.json.JSONObject(stored);
            // A blob without a version predates versioning: the oldest format.
            format = json.optInt(KEY_FORMAT_VERSION, 1);
            for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (KEY_FORMAT_VERSION.equals(key))
                    continue;
                Object value = json.get(key);
                // JSON widens on the way out; every numeric key in the format is an int.
                if (value instanceof Number) value = ((Number) value).intValue();
                look.put(key, value);
            }
        } catch (org.json.JSONException e) {
            return null;
        }
        // A look saved before format 2 knows nothing of tint, rim or motion: it was the scheme
        // tint, the hairline and the classic motion, and applying it must put those back. One
        // saved before 5 knows nothing of the hue or Vibrancy: primary and as today. The retired
        // Shade is dropped, so the look applies as Shade 0.
        look.remove(RETIRED_SHADE_KEY);
        if (!look.isEmpty()) {
            for (Map.Entry<String, Object> glass : glassDefaults().entrySet())
                if (!look.containsKey(glass.getKey())) look.put(glass.getKey(), glass.getValue());
            if (format < 5) {
                // The tint strength came later still; before it every look wore the full tint.
                if (!look.containsKey(TERMUX_APP.KEY_SURFACE_GLOBAL_TINT))
                    look.put(TERMUX_APP.KEY_SURFACE_GLOBAL_TINT, LEGACY_TINT_STRENGTH);
                fromLegacy(look);
            }
            // Format 6 stretched the Tint scale; a look older than it has its amounts scaled.
            if (format < 6)
                rescaleTintAmounts(look);
            // Format 7: Shine takes the rim and the dispersion; the retired keys go.
            if (format < 7)
                foldIntoShine(look);
            for (String key : RETIRED_GLASS_KEYS)
                look.remove(key);
            // A look saved before the grid names its cells by the old keys; they read as grid cells.
            toGridKeys(look);
            if (!look.containsKey(cellKey(null, SurfaceProperty.TINT)))
                look.put(cellKey(null, SurfaceProperty.TINT), TERMUX_APP.DEFAULT_SURFACE_GLOBAL_TINT);
        }
        return look;
    }

    /** Format 7: {@code look}'s rim string and dispersion as its Shine, in place. */
    static void foldIntoShine(@NonNull Map<String, Object> look) {
        if (look.containsKey(TERMUX_APP.KEY_SURFACE_GLASS_SHINE))
            return;
        Object rim = look.get(TERMUX_APP.KEY_SURFACE_GLASS_RIM);
        Object edge = look.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION);
        int rimStop = rim instanceof String
            ? LookGridMigration.rimOf((String) rim) : LookGrid.RIM_HAIRLINE;
        look.put(TERMUX_APP.KEY_SURFACE_GLASS_SHINE, GlassMaterial.shineForRimAndEdge(rimStop,
            edge instanceof Number ? ((Number) edge).intValue() : 0));
    }

    /**
     * A look's Tint amounts, Global and every per-surface cell it names, from format 5's scale
     * to format 6's, in place: each amount draws the colour it drew
     * ({@link com.termux.app.chrome.GlassLook#amountForLegacyPercent}), so 100, the Material blend
     * then, is 35.
     */
    static void rescaleTintAmounts(@NonNull Map<String, Object> look) {
        rescaleTintAmount(look, TERMUX_APP.KEY_SURFACE_GLOBAL_TINT);
        for (SurfaceSlot slot : SurfaceSlot.values())
            rescaleTintAmount(look,
                TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.TINT));
    }

    private static void rescaleTintAmount(@NonNull Map<String, Object> look, @Nullable String key) {
        if (key == null || !(look.get(key) instanceof Number))
            return;
        look.put(key, com.termux.app.chrome.GlassLook.amountForLegacyPercent(intOf(look.get(key))));
    }

    /** The tint strength every look wore before format 5 when it named none: all of it. */
    static final int LEGACY_TINT_STRENGTH = 100;

    /**
     * A look stored before format 5 in format 5's terms, in place, so it applies as it looked.
     *
     * <p>Tint: a {@code material} look becomes {@code scheme}, and its strengths (Global and
     * every per-surface cell) are its amounts, since the Material blend at strength s is the
     * scheme ink at amount s. Any other family wore its own colour and becomes amount 0 wherever a
     * tint is named; only the desaturation a strength under 100 gave a scheme or Obsidian look is
     * lost, a few levels at most.</p>
     *
     * <p>Blur and grain: the Global pair where the look names both, and each surface's pair where
     * either is named, move to the nearest Glass position's pair ({@link GlassControls#snapped});
     * a pair with no blur (Solid's) stays. A surface naming one of the two takes both, as the
     * Glass slider writes them, the other read from the look's Global (or its default).</p>
     */
    static void fromLegacy(@NonNull Map<String, Object> look) {
        boolean material = TERMUX_APP.GLASS_TINT_MATERIAL.equals(
            look.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        if (material)
            look.put(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_SCHEME);
        if (!material) {
            if (look.containsKey(TERMUX_APP.KEY_SURFACE_GLOBAL_TINT))
                look.put(TERMUX_APP.KEY_SURFACE_GLOBAL_TINT, 0);
            for (SurfaceSlot slot : SurfaceSlot.values()) {
                String key = TermuxAppSharedPreferences.surfaceOverrideKey(slot,
                    SurfaceProperty.TINT);
                if (key != null && look.containsKey(key)) look.put(key, 0);
            }
        }
        Object globalBlur = look.get(TERMUX_APP.KEY_SURFACE_GLOBAL_BLUR);
        Object globalGrain = look.get(TERMUX_APP.KEY_SURFACE_GLOBAL_GRAIN);
        int blur = globalBlur != null ? intOf(globalBlur) : TERMUX_APP.DEFAULT_SURFACE_GLOBAL_BLUR;
        int grain = globalGrain != null ? intOf(globalGrain) : TERMUX_APP.DEFAULT_SURFACE_GLOBAL_GRAIN;
        if (globalBlur != null && globalGrain != null) {
            int[] pair = GlassControls.snapped(blur, grain);
            look.put(TERMUX_APP.KEY_SURFACE_GLOBAL_BLUR, pair[0]);
            look.put(TERMUX_APP.KEY_SURFACE_GLOBAL_GRAIN, pair[1]);
        }
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            String blurKey = TermuxAppSharedPreferences.surfaceOverrideKey(slot,
                SurfaceProperty.BLUR);
            String grainKey = TermuxAppSharedPreferences.surfaceOverrideKey(slot,
                SurfaceProperty.GRAIN);
            if (blurKey == null || grainKey == null) continue;
            boolean namesBlur = look.containsKey(blurKey);
            boolean namesGrain = look.containsKey(grainKey);
            if (!namesBlur && !namesGrain) continue;
            int[] pair = GlassControls.snapped(namesBlur ? intOf(look.get(blurKey)) : blur,
                namesGrain ? intOf(look.get(grainKey)) : grain);
            look.put(blurKey, pair[0]);
            look.put(grainKey, pair[1]);
        }
    }



    private static Map<String, Object> glassDefaults() {
        LinkedHashMap<String, Object> defaults = new LinkedHashMap<>();
        putGlassDefaults(defaults);
        return defaults;
    }

    /**
     * Applies a preset in full: every surface back on Global first — a preset is a complete look,
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
        applyEach(writingInto(prefs, batch), preset);
        batch.applyToStore();
    }

    /**
     * A Look chosen on the editor's slider: {@link #apply} and, in the same one editor, every
     * per-surface glass value no Look names put back to what a Look stands for
     * ({@link #resetSurfaceExtras}). Choosing a Look resets every surface to it, the keyboard's
     * Keys and each surface's Glass position included (developer, 2026-10-17); the migrations keep
     * calling {@link #apply}, which leaves them alone.
     */
    public static void applyLook(@NonNull TermuxAppSharedPreferences prefs,
                                 @NonNull Preset preset) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null) {
            applyEach(prefs, preset);
            resetSurfaceExtras(prefs);
            return;
        }
        Batch batch = new Batch(store);
        TermuxAppSharedPreferences into = writingInto(prefs, batch);
        applyEach(into, preset);
        resetSurfaceExtras(into);
        batch.applyToStore();
    }

    /**
     * The user's saved Custom ({@link #custom}) put back whole, as one editor: what a Look would
     * leave behind is cleared first (Keys back to the theme's, every Glass position forgotten),
     * then the saved look applies as {@link #apply} applies one — every surface back on Global, each
     * cell it names detached again, its Keys and Glass positions where it saved them — and, where
     * it leaves the keyboard's blur on Global, that raw key goes back to "follow". A blob saved
     * before Keys or the Glass positions were part of it reads them as the theme's and forgotten.
     * The caller owns offering the Undo.
     */
    public static void applyCustom(@NonNull TermuxAppSharedPreferences prefs,
                                   @NonNull Preset custom) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null) {
            forgetSurfaceExtras(prefs);
            applyEach(prefs, custom);
            keyboardBlurFollowsWhereLinked(prefs);
            return;
        }
        Batch batch = new Batch(store);
        TermuxAppSharedPreferences into = writingInto(prefs, batch);
        forgetSurfaceExtras(into);
        applyEach(into, custom);
        keyboardBlurFollowsWhereLinked(into);
        batch.applyToStore();
    }

    /**
     * {@link #applyCustom(TermuxAppSharedPreferences, Preset)} for one half of the wall (ADR 0013):
     * the half's own look out of {@code custom} ({@link #halfOf}) lands on {@code half} alone, so
     * the Custom stop on one half never writes the other half's values. The values no half holds
     * (the glass ink, rim, motion, hue, vibrancy and edge) are one for the wall, as a Look's are.
     */
    public static void applyCustom(@NonNull TermuxAppSharedPreferences prefs,
                                   @NonNull Preset custom, @NonNull PageKind half) {
        SharedPreferences store = prefs.getSharedPreferences();
        Batch batch = store == null ? null : new Batch(store);
        TermuxAppSharedPreferences into = batch == null ? prefs : writingInto(prefs, batch);
        forgetSurfaceExtras(into);
        applyHalf(into, halfOf(custom, half), half, false, true);
        keyboardBlurFollowsWhereLinked(into);
        if (batch != null)
            batch.applyToStore();
    }

    /** One half's live look as a Custom, under the plain keys ({@link #captureLook}). */
    @NonNull
    static Preset customOf(@NonNull TermuxAppSharedPreferences prefs, @NonNull PageKind half) {
        return new Preset(CUSTOM_ID, R.string.termux_surface_preset_custom,
            captureLook(prefs, half));
    }

    /**
     * The per-surface glass values outside the preset format, as every Look has them: the
     * keyboard's Keys back to the theme's (-1), every Glass slider position forgotten (Global's and
     * each surface's, so each reads back from the glass it now draws), and, where the Look left the
     * keyboard's blur on Global, its raw key back to "follow", as the shared Glass writes it
     * ({@link GlobalGlass#write}).
     */
    static void resetSurfaceExtras(@NonNull TermuxAppSharedPreferences prefs) {
        forgetSurfaceExtras(prefs);
        keyboardBlurFollowsWhereLinked(prefs);
    }

    /** Keys back to the theme's, and every Glass slider position forgotten. */
    private static void forgetSurfaceExtras(@NonNull TermuxAppSharedPreferences prefs) {
        prefs.setInAppKeyboardKeyOpacity(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
        prefs.setSurfaceGlassPosition(null, -1);
        for (SurfaceSlot slot : SurfaceSlot.values())
            prefs.setSurfaceGlassPosition(slot, -1);
    }

    /** The keyboard's raw blur key back to "follow" while its blur is on Global. */
    private static void keyboardBlurFollowsWhereLinked(@NonNull TermuxAppSharedPreferences prefs) {
        if (prefs.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR))
            prefs.setInAppKeyboardBlurRadiusRaw(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BLUR_RADIUS);
    }

    /** {@code prefs}' setters and getters over {@code batch}: what they write, it holds. */
    @NonNull
    static TermuxAppSharedPreferences writingInto(@NonNull TermuxAppSharedPreferences prefs,
                                                  @NonNull Batch batch) {
        return prefs.over(batch);
    }

    /** {@link #apply}, one setter and one {@code apply()} at a time: the reference it must match. */
    @VisibleForTesting
    static void applyEach(@NonNull TermuxAppSharedPreferences prefs, @NonNull Preset preset) {
        // The chrome wears one look on every page (ADR 0031): the look lands once, everywhere.
        applyHalf(prefs, halfOf(preset, PageKind.TERMINAL), null, false);
    }

    /**
     * A Look chosen on one half of the wall, as {@link #applyLook} chooses one for both: its
     * per-half cells (material point, Global numbers, the dock's, status bar's and keyboard's own
     * values) land on {@code half} alone. The values a Look sets that are not held per half (the
     * glass ink, rim, motion, hue, vibrancy and edge, the terminal's own cells) have one value for
     * the whole wall and are written for both, as is the reset of the per-surface extras.
     */
    public static void applyLook(@NonNull TermuxAppSharedPreferences prefs,
                                 @NonNull Preset preset, @NonNull PageKind half) {
        SharedPreferences store = prefs.getSharedPreferences();
        Batch batch = store == null ? null : new Batch(store);
        TermuxAppSharedPreferences into = batch == null ? prefs : writingInto(prefs, batch);
        Preset look = halfOf(preset, half);
        applyAtHalf(into, look, half);
        resetSurfaceExtras(into);
        if (batch != null)
            batch.applyToStore();
    }

    /**
     * A Look picked on the Global card: {@link #applyLook(TermuxAppSharedPreferences, Preset,
     * PageKind)} in one editor. A surface's own Opacity and Blur stay (own stays, appearance-one-
     * look spec §1): every surface that follows Global wears the Look.
     */
    public static void applyGlobalLook(@NonNull TermuxAppSharedPreferences prefs,
                                       @NonNull Preset preset, @NonNull PageKind half) {
        SharedPreferences store = prefs.getSharedPreferences();
        Batch batch = store == null ? null : new Batch(store);
        TermuxAppSharedPreferences into = batch == null ? prefs : writingInto(prefs, batch);
        applyHalf(into, halfOf(preset, half), half, false, true);
        resetSurfaceExtras(into);
        if (batch != null)
            batch.applyToStore();
    }

    /** A look, the same on every page (ADR 0031): {@link #applyHalf} for the wall. */
    private static void applyAtHalf(@NonNull TermuxAppSharedPreferences prefs,
                                    @NonNull Preset look, @NonNull PageKind half) {
        applyHalf(prefs, look, half, false, false);
    }

    /** The material point and its intensity: held per page as strings and numbers of their own. */
    private static boolean isMaterialKey(@NonNull String key) {
        return TERMUX_APP.KEY_SURFACE_MATERIAL.equals(key)
            || TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY.equals(key);
    }

    /**
     * The number a look gives a surface's property, in the grid's unit (a blur as its Frost stop):
     * its own cell, else the look's Global, else the wall's Global as it is.
     */
    private static int lookCellValue(@NonNull TermuxAppSharedPreferences prefs,
                                     @NonNull Preset look, @NonNull SurfaceSlot slot,
                                     @NonNull SurfaceProperty property) {
        Object own = look.values.get(cellKey(slot, property));
        if (own != null) return intOf(own);
        Object global = look.values.get(cellKey(null, property));
        return global != null ? intOf(global)
            : cellValue(prefs, null, property, pageOf(PageKind.HOME));
    }

    /**
     * Every cell back on Global and each named value written, for one half or (null) both. With
     * {@code cellsOnly} only the per-half cells are touched; otherwise a half write still writes
     * the values with no half for both, and leaves the terminal's cells (the Terminal's alone)
     * to a Terminal or both-halves write.
     */
    private static void applyHalf(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull Preset preset, @Nullable PageKind half,
                                  boolean cellsOnly) {
        applyHalf(prefs, preset, half, cellsOnly, false);
    }

    /**
     * {@link #applyHalf(TermuxAppSharedPreferences, Preset, PageKind, boolean)}; with
     * {@code keepOwn} (a Look picked on Global) the status bar's, the dock's and the keyboard's
     * own Opacity and Blur stay (own stays, appearance-one-look spec §1).
     */
    private static void applyHalf(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull Preset preset, @Nullable PageKind half,
                                  boolean cellsOnly, boolean keepOwn) {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (isLayoutOwned(property) || !touchesCell(slot, property, half, cellsOnly))
                    continue;
                if (keepOwn && GlobalGlass.ownsStay(slot) && ownable(property))
                    continue;
                clearCell(prefs, slot, property, pageOf(half));
            }
        }
        for (Map.Entry<String, Object> entry : preset.values.entrySet()) {
            String key = entry.getKey();
            if (isLayoutOwned(key) || (cellsOnly && !isHalfKey(key)))
                continue;
            applyOne(prefs, key, entry.getValue(), half);
        }
    }

    /** The properties a surface may own: Opacity, and Blur with the grain it brings. */
    private static boolean ownable(@NonNull SurfaceProperty property) {
        return property == SurfaceProperty.OPACITY || property == SurfaceProperty.BLUR
            || property == SurfaceProperty.GRAIN;
    }

    /** Whether a write for {@code half} touches a surface cell; see {@link #applyHalf}. */
    private static boolean touchesCell(@NonNull SurfaceSlot slot,
                                       @NonNull SurfaceProperty property,
                                       @Nullable PageKind half, boolean cellsOnly) {
        if (TermuxAppSharedPreferences.isPerPage(slot, property))
            return true;
        return !cellsOnly && half != PageKind.HOME;
    }

    private static void applyOne(@NonNull TermuxAppSharedPreferences prefs, @NonNull String key,
                                 @NonNull Object value) {
        applyOne(prefs, key, value, null);
    }

    private static void applyOne(@NonNull TermuxAppSharedPreferences prefs, @NonNull String key,
                                 @NonNull Object value, @Nullable PageKind half) {
        switch (key) {
            // Style is never part of a Look: a stored look from before this rule may still carry
            // the key, and it is ignored on apply and in matches().
            case TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE:
                return;
            case TERMUX_APP.KEY_FANCIER_GLASS_BEND:
                prefs.setFancierGlassBendDp(intOf(value));
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_SHINE:
                prefs.setGlassShine(intOf(value));
                return;
            // Keys a look saved before format 7 carried: Shine took their place.
            case TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH:
            case TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT:
            case TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR:
            case TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION:
            case TERMUX_APP.KEY_FANCIER_GLASS_CLARITY:
            case TERMUX_APP.KEY_FANCIER_GLASS_FACE_CLARITY:
            case TERMUX_APP.KEY_FANCIER_GLASS_DIM:
            case TERMUX_APP.KEY_FANCIER_GLASS_SIZE_LENS:
            case TERMUX_APP.KEY_SURFACE_GLASS_RIM:
                return;
            case TERMUX_APP.KEY_SURFACE_MATERIAL:
                prefs.setSurfaceMaterial(pageOf(half), (String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY:
                prefs.setSurfaceMaterialIntensity(pageOf(half), intOf(value));
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_TINT:
                prefs.setSurfaceGlassTint((String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_MOTION:
                prefs.setSurfaceGlassMotion((String) value);
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE:
                prefs.setSurfaceGlassTintHue(value instanceof String ? (String) value : null);
                return;
            case TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY:
                prefs.setSurfaceGlassVibrancy(intOf(value));
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
        SurfaceProperty globalProperty = globalOfKey(key);
        if (globalProperty != null) {
            writeCell(prefs, null, globalProperty, pageOf(half), intOf(value));
            return;
        }
        SurfaceEditorRows.Row cell = rowOfKey(key);
        if (cell != null) {
            if (half != PageKind.HOME || TermuxAppSharedPreferences.isPerPage(cell.slot,
                    cell.property))
                writeCell(prefs, cell.slot, cell.property, pageOf(half), intOf(value));
            return;
        }
        // A saved Custom's Glass slider positions; never part of a match ({@link #currentOne}).
        GlassPositionKey position = glassPositionKey(key);
        if (position != null)
            prefs.setSurfaceGlassPosition(position.slot, intOf(value));
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
        return matches(prefs, preset, PageKind.TERMINAL);
    }

    /**
     * Whether one half of the wall wears exactly this preset, as {@link #matches(
     * TermuxAppSharedPreferences, Preset)} asks it of the Terminal: the stop the Look slider
     * shows on that half. Values not held per half are compared as they are for both.
     */
    public static boolean matches(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull Preset preset, @NonNull PageKind half) {
        Preset look = halfOf(preset, half);
        boolean scoped = false;
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (isLayoutOwned(row.property))
                continue;
            if (scoped && TermuxAppSharedPreferences.isPerPage(row.slot, row.property)) {
                if (cellValue(prefs, row.slot, row.property, pageOf(half))
                        != lookCellValue(prefs, look, row.slot, row.property))
                    return false;
                continue;
            }
            if (prefs.isSurfaceInheriting(row.slot, row.property, pageOf(half))
                    == look.values.containsKey(cellKey(row.slot, row.property)))
                return false;
        }
        for (Map.Entry<String, Object> entry : look.values.entrySet()) {
            if (isLayoutOwned(entry.getKey()) || (scoped && isHalfKey(entry.getKey())
                    && !isMaterialKey(entry.getKey())))
                continue;
            Object current = currentOne(prefs, entry.getKey(), half);
            if (current != null && !current.equals(entry.getValue()))
                return false;
        }
        return true;
    }

    /**
     * Whether one screen wears this preset in what it holds itself: {@link #matches(
     * TermuxAppSharedPreferences, Preset, PageKind)} asked of that half's own values only — its
     * per-half cells and numbers, and on the Terminal the terminal's cells, which are the
     * Terminal's alone. The values held once for the wall (the glass ink, rim, motion, hue,
     * vibrancy and edge) are left out: a Look picked on the other screen writes them for both,
     * and must not move this screen off its own Look (editor-redesign §1: each screen stores its
     * own Look and glass; the two meet only through the Match rows).
     */
    public static boolean wornOn(@NonNull TermuxAppSharedPreferences prefs,
                                 @NonNull Preset preset, @NonNull PageKind half) {
        return wornOn(prefs, preset, half, false);
    }

    /**
     * {@link #wornOn(TermuxAppSharedPreferences, Preset, PageKind)} as a Look picked on Global
     * writes it ({@link #applyGlobalLook}): the status bar's, the dock's and the keyboard's own
     * Opacity, Blur and Grain are left out, as the write leaves them, so a Look read back is the
     * Look that was picked while those surfaces keep their own values.
     */
    public static boolean wornOnGlobal(@NonNull TermuxAppSharedPreferences prefs,
                                       @NonNull Preset preset, @NonNull PageKind half) {
        return wornOn(prefs, preset, half, true);
    }

    private static boolean wornOn(@NonNull TermuxAppSharedPreferences prefs,
                                  @NonNull Preset preset, @NonNull PageKind half,
                                  boolean keepOwn) {
        Preset look = halfOf(preset, half);
        boolean scoped = false;
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            if (isLayoutOwned(row.property) || !touchesCell(row.slot, row.property, half, false))
                continue;
            if (keepOwn && GlobalGlass.ownsStay(row.slot) && ownable(row.property))
                continue;
            if (scoped && TermuxAppSharedPreferences.isPerPage(row.slot, row.property)) {
                if (cellValue(prefs, row.slot, row.property, pageOf(half))
                        != lookCellValue(prefs, look, row.slot, row.property))
                    return false;
                continue;
            }
            if (prefs.isSurfaceInheriting(row.slot, row.property, pageOf(half))
                    == look.values.containsKey(cellKey(row.slot, row.property)))
                return false;
        }
        for (Map.Entry<String, Object> entry : look.values.entrySet()) {
            String key = entry.getKey();
            if (isLayoutOwned(key) || !isOwnKey(key, half)
                    || (scoped && isHalfKey(key) && !isMaterialKey(key)))
                continue;
            Object current = currentOne(prefs, key, half);
            if (current != null && !current.equals(entry.getValue()))
                return false;
        }
        return true;
    }

    /** Whether a plain preset key is one {@code half} holds itself; see {@link #wornOn}. */
    private static boolean isOwnKey(@NonNull String key, @NonNull PageKind half) {
        if (isHalfKey(key))
            return true;
        SurfaceEditorRows.Row cell = rowOfKey(key);
        return cell != null && touchesCell(cell.slot, cell.property, half, false);
    }

    /** The live value behind a preset key, or null for a key this build does not know. */
    @Nullable
    private static Object currentOne(@NonNull TermuxAppSharedPreferences prefs,
                                     @NonNull String key, @NonNull PageKind half) {
        switch (key) {
            case TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE:
                return null;
            case TERMUX_APP.KEY_FANCIER_GLASS_BEND:
                return prefs.getFancierGlassBendDp();
            case TERMUX_APP.KEY_SURFACE_GLASS_SHINE:
                return prefs.getGlassShine();
            case TERMUX_APP.KEY_SURFACE_MATERIAL:
                return prefs.getSurfaceMaterial(pageOf(half));
            case TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY:
                return prefs.getSurfaceMaterialIntensity(pageOf(half));
            case TERMUX_APP.KEY_SURFACE_GLASS_TINT:
                return prefs.getSurfaceGlassTint();
            case TERMUX_APP.KEY_SURFACE_GLASS_MOTION:
                return prefs.getSurfaceGlassMotion();
            case TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE:
                return prefs.getSurfaceGlassTintHue();
            case TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY:
                return prefs.getSurfaceGlassVibrancy();
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
        SurfaceProperty globalProperty = globalOfKey(key);
        if (globalProperty != null)
            return cellValue(prefs, null, globalProperty, pageOf(half));
        SurfaceEditorRows.Row cell = rowOfKey(key);
        if (cell != null)
            return cellValue(prefs, cell.slot, cell.property, pageOf(half));
        return null;
    }

    /**
     * A {@link SharedPreferences} that reads through to a store but holds every write until
     * {@link #applyToStore}: reads see the held writes, so a setter that reads what an earlier one
     * wrote behaves exactly as it would against the store. {@link #apply} lands a Look through one;
     * the editor's Look slider holds a drag's stops in one until the finger lifts.
     */
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

        /** Whether anything is held: a write, a removal or a clear. */
        boolean isEmpty() {
            return !mCleared && mPending.isEmpty();
        }

        /**
         * A copy of what is held now, over the same store, that nothing writes to again: what a
         * {@link com.termux.shared.settings.preferences.SharedPreferencesPreview} may be shown
         * with while this batch goes on collecting.
         */
        @NonNull
        Batch frozen() {
            Batch copy = new Batch(mStore);
            copy.mPending.putAll(mPending);
            copy.mCleared = mCleared;
            return copy;
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
}
