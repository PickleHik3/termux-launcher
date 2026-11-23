package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.GlassLook;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The glass controls' one-time migration: a shipped Look stored at its old values becomes the
 * Look as it ships, drawing the same colour; any other look keeps its colour under the new Tint
 * amount and has its blur and grain moved onto the Glass curve; the saved Custom is rewritten.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassControlsMigrationTest {

    private static final int BASE = 0xFF141218;
    private static final int CONTAINER = 0xFF4F378B;
    private static final int ACCENT = 0xFFD0BCFF;

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("glass-controls-migration-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    /** {@code id} as a build before the glass controls stored it. */
    private void storeOldLook(String id) {
        SurfacePresets.apply(preferences, preset(id));
        com.termux.shared.termux.settings.preferences.LookGrid grid = preferences.lookGrid();
        grid.clear(com.termux.shared.termux.settings.preferences.LookGrid.Property.TINT_HUE, null);
        grid.clear(com.termux.shared.termux.settings.preferences.LookGrid.Property.VIBRANCY, null);
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 100);
        switch (id) {
            case "minimal":
                preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 4);
                preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 4);
                break;
            case "frost":
                preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 8);
                break;
            case "stock":
                preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 10);
                preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 10);
                store.edit().putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT,
                    TERMUX_APP.GLASS_TINT_MATERIAL).commit();
                break;
            default:
                break;
        }
    }

    /** The base colour the dock's tint is drawn in now, its accent resolved. */
    private int dockColourNow() {
        return GlassLook.of(preferences).withAmountPercent(preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()))
            .withAccentColors(CONTAINER, ACCENT).tintBase(BASE);
    }

    /**
     * What the old build drew for each Look at the full strength, as literals read off its
     * formulas: its ink, or (Tint) the Material blend of BASE toward CONTAINER and ACCENT.
     */
    private static int oldColour(String id) {
        switch (id) {
            case "frost":
                return 0xFF161822;
            case "stock":
                return 0xFF40355B;
            default:
                return BASE;
        }
    }

    @Test
    public void aMaterialLookKeepsItsColourAsScaledAmountsOnTheSchemeInk() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 60);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT, 30);
        // The old Material blend at strength 0.6, read off the old formula.
        int before = 0xFF2F2742;

        foldThenRun();

        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals("scaled onto the stretched Tint", 21, preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(11, preferences.getStatusBarTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.TINT));
        assertEquals("the same colour", before, dockColourNow());
    }

    @Test
    public void aSchemeOrObsidianLookWearsItsOwnColour() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_OBSIDIAN);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 40);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, 70);

        foldThenRun();

        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(0, preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(0, preferences.getInAppKeyboardTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals("Obsidian's ink, untouched", 0xFF161822, dockColourNow());
    }

    /** A store that never wrote a tint read the old default, the full strength. */
    @Test
    public void aTintNeverStoredIsTheOldFullStrength() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 17);
        assertFalse(store.contains(TERMUX_APP.KEY_SURFACE_GLOBAL_TINT));

        foldThenRun();

        assertEquals(GlassLook.TINT_LOOK_PERCENT, preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(GlassLook.TINT_LOOK_PERCENT, preferences.getTerminalTintStrength());
    }

    @Test
    public void blurAndGrainMoveToTheNearestGlassAndASurfaceTakesBoth() {
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 10);
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 10);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 20);

        foldThenRun();

        assertEquals(12, preferences.getSurfaceGlobalValue(SurfaceProperty.BLUR));
        assertEquals(5, preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN));
        int[] dock = GlassControls.snapped(20, 10);
        assertEquals(com.termux.shared.termux.settings.preferences.FrostStops.blurDpFor(com.termux.shared.termux.settings.preferences.FrostStops.nearestStopByBlur(dock[0])), preferences.getExtraKeysBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(dock[1], preferences.getDockGlassGrain(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.GRAIN));
        assertTrue("an untouched surface still follows Global",
            preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
    }

    @Test
    public void noFrostStaysNoFrost() {
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 0);
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 0);
        preferences.setSurfaceGlobalValue(SurfaceProperty.OPACITY, 80);
        foldThenRun();
        assertEquals(0, preferences.getSurfaceGlobalValue(SurfaceProperty.BLUR));
        assertEquals(0, preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN));
    }

    @Test
    public void theSavedCustomIsRewrittenInTheCurrentFormat() throws Exception {
        preferences.setSurfaceCustomPreset("{\"format_version\":4,"
            + "\"surface_glass_tint\":\"material\",\"surface_base_tint\":80,"
            + "\"surface_base_blur\":4,\"surface_base_grain\":4,\"surface_base_opacity\":30}");

        foldThenRun();

        JSONObject saved = new JSONObject(preferences.getSurfaceCustomPreset());
        assertEquals(SurfacePresets.FORMAT_VERSION, saved.getInt("format_version"));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, saved.getString(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(28, saved.getInt("look.tint"));
        assertEquals(com.termux.shared.termux.settings.preferences.FrostStops.nearestStopByBlur(5), saved.getInt("look.frost"));
        assertEquals(3, saved.getInt("look.grain"));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT_HUE,
            saved.getString(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE));
        assertFalse("the retired Shade is not written", saved.has(SurfacePresets.RETIRED_SHADE_KEY));
        assertEquals(100, saved.getInt(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY));
    }

    /** An older blob read on its own (a theme import) is carried across the same way. */
    @Test
    public void anOldLookReadAnywhereIsCarriedAcross() {
        java.util.Map<String, Object> look = SurfacePresets.deserialize("{\"format_version\":4,"
            + "\"surface_glass_tint\":\"scheme\",\"surface_base_tint\":55,"
            + "\"surface_base_blur\":25,\"surface_base_grain\":8,\"status_bar_tint_strength\":20}");
        assertEquals(0, look.get(SurfacePresets.cellKey(null, SurfaceProperty.TINT)));
        assertEquals(0, look.get(SurfacePresets.cellKey(SurfaceSlot.STATUS, SurfaceProperty.TINT)));
        assertEquals(com.termux.shared.termux.settings.preferences.FrostStops.nearestStopByBlur(25),
            look.get(SurfacePresets.cellKey(null, SurfaceProperty.BLUR)));
        assertEquals(7, look.get(SurfacePresets.cellKey(null, SurfaceProperty.GRAIN)));
        java.util.Map<String, Object> five = SurfacePresets.deserialize(
            "{\"format_version\":5,\"surface_base_tint\":55,\"surface_base_grain\":8,"
                + "\"surface_base_blur\":25,\"status_bar_tint_strength\":100}");
        assertEquals("a format 5 look has its Tint scaled", 19,
            five.get(SurfacePresets.cellKey(null, SurfaceProperty.TINT)));
        assertEquals("cell by cell", GlassLook.TINT_LOOK_PERCENT,
            five.get(SurfacePresets.cellKey(SurfaceSlot.STATUS, SurfaceProperty.TINT)));
        assertEquals("and nothing else", 8, five.get(SurfacePresets.cellKey(null, SurfaceProperty.GRAIN)));
        java.util.Map<String, Object> current = SurfacePresets.deserialize(
            "{\"format_version\":6,\"surface_base_tint\":55,\"surface_base_grain\":8,"
                + "\"surface_base_blur\":25}");
        assertEquals("a format 6 look is read as it is", 55,
            current.get(SurfacePresets.cellKey(null, SurfaceProperty.TINT)));
        assertEquals(8, current.get(SurfacePresets.cellKey(null, SurfaceProperty.GRAIN)));
    }

    // ------------------------------------------------------------ format 2: the stretched Tint

    /** A store the first glass controls migration left: its amounts on the old scale. */
    private void storeFormatOne() {
        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 1).commit();
    }

    /**
     * An install the unification already migrated, on the Tint Look at the amount 100 it shipped
     * at: every stored amount is scaled, so the Tint Look is the Look as it ships now (35), its
     * colour channel for channel the same, and the saved Custom is scaled too.
     */
    @Test
    public void formatOneAmountsAreScaledEverywhereTheyAreStored() throws Exception {
        java.util.LinkedHashMap<String, Object> oldTint = new java.util.LinkedHashMap<>(
            preset("stock").values);
        oldTint.put(SurfacePresets.cellKey(null, SurfaceProperty.TINT), 100);
        SurfacePresets.apply(preferences, new SurfacePresets.Preset("stock", 0, oldTint));
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, 40);
        preferences.setSurfaceCustomPreset("{\"format_version\":5,\"surface_base_tint\":100,"
            + "\"in_app_keyboard_tint_strength\":60}");
        storeFormatOne();
        // What format 1 drew at its amount 100: the old Material blend, read off the old formula.
        int before = oldColour("stock");
        assertFalse(SurfacePresets.matches(preferences, preset("stock")));

        foldThenRun();

        assertEquals(GlassLook.TINT_LOOK_PERCENT, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
        assertEquals("a detached cell", 14, preferences.getInAppKeyboardTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT));
        assertEquals("the same colour", before, dockColourNow());
        preferences.setSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, true);
        assertTrue("the Tint Look's stop", SurfacePresets.matches(preferences, preset("stock")));
        JSONObject saved = new JSONObject(preferences.getSurfaceCustomPreset());
        assertEquals(SurfacePresets.FORMAT_VERSION, saved.getInt("format_version"));
        assertEquals(GlassLook.TINT_LOOK_PERCENT, saved.getInt("look.tint"));
        assertEquals(21, saved.getInt("look.tint.keyboard"));
        assertEquals(GlassControlsMigration.FORMAT,
            store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
    }

    /** Clear, Mist and Solid at 0 stay at 0, untouched; the scale runs once. */
    @Test
    public void formatOneAtTintZeroStaysAndTheScaleRunsOnce() {
        SurfacePresets.apply(preferences, preset("minimal"));
        storeFormatOne();
        foldThenRun();
        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        assertEquals(0, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 100);
        foldThenRun();
        assertEquals("the user's 100 after the scale is left alone", 100,
            preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
    }

    @Test
    public void itRunsOnce() {
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 17);
        foldThenRun();
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 45);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 10);
        foldThenRun();
        assertEquals(45, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
        // Blur 10 wears the nearest Frost stop's 12.
        assertEquals(12, preferences.getSurfaceGlobalValue(SurfaceProperty.BLUR));
    }

    /** The new knobs are clamped on the way in and out, and default to today's glass. */
    @Test
    public void theNewKnobsAreClampedAndDefaultToToday() {
        assertEquals(100, preferences.getSurfaceGlassVibrancy());
        assertEquals(TERMUX_APP.GLASS_TINT_HUE_PRIMARY, preferences.getSurfaceGlassTintHue());
        preferences.setSurfaceGlassVibrancy(400);
        assertEquals(150, preferences.getSurfaceGlassVibrancy());
        preferences.setSurfaceGlassVibrancy(10);
        assertEquals(70, preferences.getSurfaceGlassVibrancy());
        store.edit().putInt("look.vibrancy", 999)
            .commit();
        assertEquals("clamped on read", 150, preferences.getSurfaceGlassVibrancy());
        preferences.setSurfaceGlassTintHue("tertiary");
        assertEquals(TERMUX_APP.GLASS_TINT_HUE_TERTIARY, preferences.getSurfaceGlassTintHue());
        preferences.setSurfaceGlassTintHue("ultraviolet");
        assertEquals(TERMUX_APP.GLASS_TINT_HUE_PRIMARY, preferences.getSurfaceGlassTintHue());
        assertEquals("no position stored", -1, preferences.getSurfaceGlassPosition(null));
        preferences.setSurfaceGlassPosition(null, 250);
        assertEquals(100, preferences.getSurfaceGlassPosition(null));
        preferences.setSurfaceGlassPosition(SurfaceSlot.DOCK, 5);
        assertEquals(5, preferences.getSurfaceGlassPosition(SurfaceSlot.DOCK));
        preferences.setSurfaceGlassPosition(SurfaceSlot.DOCK, -1);
        assertEquals("forgotten", -1, preferences.getSurfaceGlassPosition(SurfaceSlot.DOCK));
    }

    // --------------------------------------------------------------------- one editor, once

    /** A store that counts the editors applied to it, and can die on the next one. */
    private static final class CountingStore implements SharedPreferences {
        final SharedPreferences store;
        int applied;
        boolean dies;

        CountingStore(SharedPreferences store) {
            this.store = store;
        }

        @Override public java.util.Map<String, ?> getAll() { return store.getAll(); }
        @Override public String getString(String key, String defValue) { return store.getString(key, defValue); }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return store.getStringSet(key, defValues); }
        @Override public int getInt(String key, int defValue) { return store.getInt(key, defValue); }
        @Override public long getLong(String key, long defValue) { return store.getLong(key, defValue); }
        @Override public float getFloat(String key, float defValue) { return store.getFloat(key, defValue); }
        @Override public boolean getBoolean(String key, boolean defValue) { return store.getBoolean(key, defValue); }
        @Override public boolean contains(String key) { return store.contains(key); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { store.registerOnSharedPreferenceChangeListener(l); }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { store.unregisterOnSharedPreferenceChangeListener(l); }

        @Override public Editor edit() {
            final Editor editor = store.edit();
            return new Editor() {
                @Override public Editor putString(String k, String v) { editor.putString(k, v); return this; }
                @Override public Editor putStringSet(String k, java.util.Set<String> v) { editor.putStringSet(k, v); return this; }
                @Override public Editor putInt(String k, int v) { editor.putInt(k, v); return this; }
                @Override public Editor putLong(String k, long v) { editor.putLong(k, v); return this; }
                @Override public Editor putFloat(String k, float v) { editor.putFloat(k, v); return this; }
                @Override public Editor putBoolean(String k, boolean v) { editor.putBoolean(k, v); return this; }
                @Override public Editor remove(String k) { editor.remove(k); return this; }
                @Override public Editor clear() { editor.clear(); return this; }
                @Override public boolean commit() { apply(); return true; }
                @Override public void apply() {
                    if (dies) throw new IllegalStateException("the process died");
                    applied++;
                    editor.commit();
                }
            };
        }
    }

    /** Every write lands in one editor, the format marker with them. */
    @Test
    public void everyWriteLandsInOneEditor() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceCustomPreset("{\"format_version\":4,\"surface_base_blur\":4,"
            + "\"surface_base_grain\":4}");
        CountingStore counting = new CountingStore(store);
        TermuxAppSharedPreferences over = preferences.over(counting);

        GlassControlsMigration.run(over);

        assertEquals("one editor", 1, counting.applied);
        assertEquals(GlassControlsMigration.FORMAT,
            store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
    }

    /**
     * A process killed before the one editor lands leaves the store exactly as it was: Tint's
     * family still material with its strength, the marker unset. The next start migrates it
     * whole, colour kept.
     */
    @Test
    public void aMigrationKilledHalfWayLeavesTheStoreAsItWas() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 70);
        java.util.Map<String, ?> before = new java.util.HashMap<>(store.getAll());
        CountingStore dying = new CountingStore(store);
        dying.dies = true;
        try {
            GlassControlsMigration.run(preferences.over(dying));
            throw new AssertionError("the store's editor was never applied");
        } catch (IllegalStateException expected) {
            // the process died
        }
        assertEquals("nothing written", before, store.getAll());

        foldThenRun();
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals("the colour is kept", 25, preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
    }

    /** The blur wallpaper mode puts back when it is turned on again lands on the curve too. */
    @Test
    public void theBlurWallpaperModeRemembersMovesOntoTheCurve() {
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 10);
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 10);
        preferences.setWallpaperEnabledExtraKeysBlurRadius(10);

        foldThenRun();

        int grain = preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN);
        int remembered = preferences.getWallpaperEnabledExtraKeysBlurRadius();
        assertTrue("on the curve with Global's grain: " + remembered + "/" + grain,
            GlassControls.onCurve(remembered, grain));
    }

    /** The Glass slider's position is written for Global and each detached surface. */
    @Test
    public void theGlassPositionsAreWritten() {
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 10);
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 10);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 30);
        foldThenRun();
        int global = preferences.getSurfaceGlassPosition(null);
        assertEquals(preferences.getSurfaceGlobalValue(SurfaceProperty.BLUR),
            GlassControls.blurForGlass(global));
        assertTrue(preferences.getSurfaceGlassPosition(SurfaceSlot.STATUS) >= 0);
        assertEquals("a surface that follows Global has none", -1,
            preferences.getSurfaceGlassPosition(SurfaceSlot.DOCK));
    }

    // ------------------------------------------------------------- format 3: Shade is retired

    /**
     * A store at format 2 with a Shade set: the value is dropped, the marker moves to 3, and
     * nothing else is touched (the Tint already on its scale stays as it is).
     */
    @Test
    public void formatThreeDropsTheRetiredShadeAndNothingElse() {
        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 2)
            .putInt(SurfacePresets.RETIRED_SHADE_KEY, 60).commit();
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 50);

        foldThenRun();

        assertFalse("the Shade is gone from the store", store.contains(SurfacePresets.RETIRED_SHADE_KEY));
        assertEquals(GlassControlsMigration.FORMAT, store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
        assertEquals(50, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
    }

    /** A store from before the controls takes the drop in the same edit as the rest. */
    @Test
    public void anOlderStoreDropsTheShadeInTheSameEdit() {
        storeOldLook("frost");
        store.edit().putInt(SurfacePresets.RETIRED_SHADE_KEY, 100).commit();
        foldThenRun();
        assertFalse(store.contains(SurfacePresets.RETIRED_SHADE_KEY));
    }

    /** A saved look that names a Shade sheds it on read and on the rewrite, so it applies as Shade 0. */
    @Test
    public void aSavedLookWithShadeShedsIt() throws Exception {
        java.util.Map<String, Object> look = SurfacePresets.deserialize("{\"format_version\":6,"
            + "\"surface_glass_shade\":80,\"surface_base_opacity\":30}");
        assertFalse(look.containsKey(SurfacePresets.RETIRED_SHADE_KEY));
        assertEquals(30, look.get(SurfacePresets.cellKey(null, SurfaceProperty.OPACITY)));

        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 2).commit();
        preferences.setSurfaceCustomPreset("{\"format_version\":6,\"surface_glass_shade\":80,"
            + "\"surface_base_opacity\":30}");
        foldThenRun();
        JSONObject saved = new JSONObject(preferences.getSurfaceCustomPreset());
        assertFalse(saved.has(SurfacePresets.RETIRED_SHADE_KEY));
        assertEquals(30, saved.getInt("look.opacity"));
    }

    /** L5: the Terminal page's own tint the half upgrade wrote survives this migration. */
    @Test
    public void theTerminalPagesOwnValuesSurviveTheMigration() {
        String tint = TermuxAppSharedPreferences.surfaceOverrideKey(SurfaceSlot.DOCK,
            SurfaceProperty.TINT);
        String own = com.termux.shared.termux.settings.preferences.PageLookKeys.pageKey(tint,
            com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID);
        store.edit().putInt(own, 23).commit();
        foldThenRun(com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID);
        // The page's own value is the dock's cell on that page now.
        assertEquals(23, store.getInt("look.tint.dock@"
            + com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID, -1));
    }

    /** The start-up order: the grid upgrade, then this one. */
    private void foldThenRun(Integer... pageIds) {
        com.termux.shared.termux.settings.preferences.LookGridMigration.run(
            new com.termux.shared.termux.settings.preferences.LookGrid.PrefsStore(
                preferences.getSharedPreferences()), java.util.Arrays.asList(pageIds));
        GlassControlsMigration.run(preferences);
    }


    // ------------------------------------------------- format 4: Frost, Depth and Shine

    @Test
    public void formatFourMapsRimAndEdgeToShineOnceAndDropsTheRetiredKeys() {
        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 3)
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, 18)
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY, 76).commit();
        preferences.lookGrid().write(
            com.termux.shared.termux.settings.preferences.LookGrid.Property.RIM, null,
            com.termux.shared.termux.settings.preferences.LookGrid.RIM_GRADIENT);
        preferences.lookGrid().write(
            com.termux.shared.termux.settings.preferences.LookGrid.Property.EDGE, null, 100);

        GlassControlsMigration.run(preferences);

        assertEquals(100, preferences.getGlassShine());
        assertFalse(preferences.lookGrid().has(
            com.termux.shared.termux.settings.preferences.LookGrid.Property.RIM, null));
        assertFalse(store.contains(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH));
        assertFalse(store.contains(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
        assertEquals(GlassControlsMigration.FORMAT,
            store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));

        // Once: a later start-up changes nothing.
        preferences.setGlassShine(33);
        GlassControlsMigration.run(preferences);
        assertEquals(33, preferences.getGlassShine());
    }

    @Test
    public void formatFourNeverTouchesAFreshInstall() {
        preferences.setGlassShine(77);
        preferences.lookGrid().write(
            com.termux.shared.termux.settings.preferences.LookGrid.Property.EDGE, null, 100);

        GlassControlsMigration.run(preferences, true);

        assertEquals(77, preferences.getGlassShine());
        assertEquals(GlassControlsMigration.FORMAT,
            store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
    }
}
