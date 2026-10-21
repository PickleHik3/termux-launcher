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
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    /** {@code id} as a build before the glass controls stored it. */
    private void storeOldLook(String id) {
        SurfacePresets.apply(preferences, preset(id));
        SharedPreferences.Editor editor = store.edit()
            .remove(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE)
            .remove(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY)
            .putInt(TERMUX_APP.KEY_SURFACE_BASE_TINT, 100);
        switch (id) {
            case "minimal":
                editor.putInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR, 4)
                    .putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 4);
                break;
            case "frost":
                editor.putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 8);
                break;
            case "stock":
                editor.putInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR, 10)
                    .putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 10)
                    .putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_MATERIAL);
                break;
            default:
                break;
        }
        editor.commit();
    }

    /** The base colour the dock's tint is drawn in now, its accent resolved. */
    private int dockColourNow() {
        return GlassLook.of(preferences).withAmountPercent(preferences.getDockTintStrength())
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
    public void everyShippedLookStoredBeforeIsTheLookAsItShipsAndDrawsTheSameColour() {
        for (String id : new String[] {"minimal", "frost", "stock", "solid"}) {
            store.edit().clear().commit();
            storeOldLook(id);
            assertFalse(id, SurfacePresets.matches(preferences, preset(id)));

            GlassControlsMigration.run(preferences);

            assertTrue(id + " keeps its stop", SurfacePresets.matches(preferences, preset(id)));
            assertEquals(id + " draws the colour it drew", oldColour(id), dockColourNow());
            assertEquals(GlassControlsMigration.FORMAT,
                store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
        }
    }

    /** A Look at an earlier generation's edge is recognised too, and readopted whole. */
    @Test
    public void anOldClearAtAnEarlierEdgeIsStillClear() {
        storeOldLook("minimal");
        preferences.setFancierGlassBendDp(26);
        preferences.setFancierGlassEdgeWidthDp(26);
        preferences.setFancierGlassEdgeLightPercent(50);
        preferences.setFancierGlassDispersionPercent(35);
        preferences.setFancierGlassClarityPercent(100);
        GlassControlsMigration.run(preferences);
        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
    }

    @Test
    public void aMaterialLookKeepsItsColourAsScaledAmountsOnTheSchemeInk() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 60);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT, 30);
        // The old Material blend at strength 0.6, read off the old formula.
        int before = 0xFF2F2742;

        GlassControlsMigration.run(preferences);

        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals("scaled onto the stretched Tint", 21, preferences.getDockTintStrength());
        assertEquals(11, preferences.getStatusBarTintStrength());
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.TINT));
        assertEquals("the same colour", before, dockColourNow());
    }

    @Test
    public void aSchemeOrObsidianLookWearsItsOwnColour() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_OBSIDIAN);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 40);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, 70);

        GlassControlsMigration.run(preferences);

        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(0, preferences.getDockTintStrength());
        assertEquals(0, preferences.getInAppKeyboardTintStrength());
        assertEquals("Obsidian's ink, untouched", 0xFF161822, dockColourNow());
    }

    /** A store that never wrote a tint read the old default, the full strength. */
    @Test
    public void aTintNeverStoredIsTheOldFullStrength() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        assertFalse(store.contains(TERMUX_APP.KEY_SURFACE_BASE_TINT));

        GlassControlsMigration.run(preferences);

        assertEquals(GlassLook.TINT_LOOK_PERCENT, preferences.getDockTintStrength());
        assertEquals(GlassLook.TINT_LOOK_PERCENT, preferences.getTerminalTintStrength());
    }

    @Test
    public void blurAndGrainMoveToTheNearestGlassAndASurfaceTakesBoth() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 10);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 20);

        GlassControlsMigration.run(preferences);

        assertEquals(13, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(5, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        int[] dock = GlassControls.snapped(20, 10);
        assertEquals(dock[0], preferences.getExtraKeysBlurRadius());
        assertEquals(dock[1], preferences.getDockGlassGrain());
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.GRAIN));
        assertTrue("an untouched surface still follows Base",
            preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
    }

    @Test
    public void noFrostStaysNoFrost() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 80);
        GlassControlsMigration.run(preferences);
        assertEquals(0, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(0, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
    }

    @Test
    public void theSavedCustomIsRewrittenInTheCurrentFormat() throws Exception {
        preferences.setSurfaceCustomPreset("{\"format_version\":4,"
            + "\"surface_glass_tint\":\"material\",\"surface_base_tint\":80,"
            + "\"surface_base_blur\":4,\"surface_base_grain\":4,\"surface_base_opacity\":30}");

        GlassControlsMigration.run(preferences);

        JSONObject saved = new JSONObject(preferences.getSurfaceCustomPreset());
        assertEquals(SurfacePresets.FORMAT_VERSION, saved.getInt("format_version"));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, saved.getString(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(28, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals(5, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(3, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
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
        assertEquals(0, look.get(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals(0, look.get(TERMUX_APP.KEY_STATUS_BAR_TINT_STRENGTH));
        assertEquals(25, look.get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(7, look.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
        java.util.Map<String, Object> five = SurfacePresets.deserialize(
            "{\"format_version\":5,\"surface_base_tint\":55,\"surface_base_grain\":8,"
                + "\"surface_base_blur\":25,\"status_bar_tint_strength\":100}");
        assertEquals("a format 5 look has its Tint scaled", 19,
            five.get(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals("cell by cell", GlassLook.TINT_LOOK_PERCENT,
            five.get(TERMUX_APP.KEY_STATUS_BAR_TINT_STRENGTH));
        assertEquals("and nothing else", 8, five.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
        java.util.Map<String, Object> current = SurfacePresets.deserialize(
            "{\"format_version\":6,\"surface_base_tint\":55,\"surface_base_grain\":8,"
                + "\"surface_base_blur\":25}");
        assertEquals("a format 6 look is read as it is", 55,
            current.get(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals(8, current.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
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
        oldTint.put(TERMUX_APP.KEY_SURFACE_BASE_TINT, 100);
        SurfacePresets.apply(preferences, new SurfacePresets.Preset("stock", 0, oldTint));
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, 40);
        preferences.setSurfaceCustomPreset("{\"format_version\":5,\"surface_base_tint\":100,"
            + "\"in_app_keyboard_tint_strength\":60}");
        storeFormatOne();
        // What format 1 drew at its amount 100: the old Material blend, read off the old formula.
        int before = oldColour("stock");
        assertFalse(SurfacePresets.matches(preferences, preset("stock")));

        GlassControlsMigration.run(preferences);

        assertEquals(GlassLook.TINT_LOOK_PERCENT, preferences.getSurfaceBaseValue(SurfaceProperty.TINT));
        assertEquals("a detached cell", 14, preferences.getInAppKeyboardTintStrength());
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT));
        assertEquals("the same colour", before, dockColourNow());
        preferences.setSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, true);
        assertTrue("the Tint Look's stop", SurfacePresets.matches(preferences, preset("stock")));
        JSONObject saved = new JSONObject(preferences.getSurfaceCustomPreset());
        assertEquals(SurfacePresets.FORMAT_VERSION, saved.getInt("format_version"));
        assertEquals(GlassLook.TINT_LOOK_PERCENT, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals(21, saved.getInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_TINT_STRENGTH));
        assertEquals(GlassControlsMigration.FORMAT,
            store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
    }

    /** Clear, Mist and Solid at 0 stay at 0, untouched; the scale runs once. */
    @Test
    public void formatOneAtTintZeroStaysAndTheScaleRunsOnce() {
        SurfacePresets.apply(preferences, preset("minimal"));
        storeFormatOne();
        GlassControlsMigration.run(preferences);
        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        assertEquals(0, preferences.getSurfaceBaseValue(SurfaceProperty.TINT));
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 100);
        GlassControlsMigration.run(preferences);
        assertEquals("the user's 100 after the scale is left alone", 100,
            preferences.getSurfaceBaseValue(SurfaceProperty.TINT));
    }

    @Test
    public void itRunsOnce() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        GlassControlsMigration.run(preferences);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 45);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10);
        GlassControlsMigration.run(preferences);
        assertEquals(45, preferences.getSurfaceBaseValue(SurfaceProperty.TINT));
        assertEquals(10, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
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
        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, 999)
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
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
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
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 70);
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

        GlassControlsMigration.run(preferences);
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals("the colour is kept", 25, preferences.getDockTintStrength());
    }

    /** The blur wallpaper mode puts back when it is turned on again lands on the curve too. */
    @Test
    public void theBlurWallpaperModeRemembersMovesOntoTheCurve() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 10);
        preferences.setWallpaperEnabledExtraKeysBlurRadius(10);

        GlassControlsMigration.run(preferences);

        int grain = preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN);
        int remembered = preferences.getWallpaperEnabledExtraKeysBlurRadius();
        assertTrue("on the curve with Base's grain: " + remembered + "/" + grain,
            GlassControls.onCurve(remembered, grain));
    }

    /** The Glass slider's position is written for Base and each detached surface. */
    @Test
    public void theGlassPositionsAreWritten() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 10);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 30);
        GlassControlsMigration.run(preferences);
        int base = preferences.getSurfaceGlassPosition(null);
        assertEquals(preferences.getSurfaceBaseValue(SurfaceProperty.BLUR),
            GlassControls.blurForGlass(base));
        assertTrue(preferences.getSurfaceGlassPosition(SurfaceSlot.STATUS) >= 0);
        assertEquals("a surface that follows Base has none", -1,
            preferences.getSurfaceGlassPosition(SurfaceSlot.DOCK));
    }

    /**
     * At start-up the migration runs, then the moved-Looks re-adoption: an install on Clear as two
     * builds ago left it (its old blur and grain, the full tint, generation 1's edge) comes up
     * on Clear as it ships, with both markers written.
     */
    @Test
    public void atStartUpTheMigrationRunsBeforeTheLooksAreReadopted() {
        storeOldLook("minimal");
        preferences.setFancierGlassBendDp(11);
        preferences.setFancierGlassEdgeWidthDp(28);
        preferences.setFancierGlassEdgeLightPercent(70);
        preferences.setFancierGlassDispersionPercent(25);
        preferences.setFancierGlassClarityPercent(100);
        store.edit().remove(SurfacePresets.KEY_LOOKS_GENERATION).commit();

        GlassControlsMigration.runAtStartUp(preferences);

        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        assertEquals(GlassControlsMigration.FORMAT,
            store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
        assertEquals(SurfacePresets.LOOKS_GENERATION,
            store.getInt(SurfacePresets.KEY_LOOKS_GENERATION, 0));
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
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 50);
        preferences.setFancierGlassDispersionPercent(80);

        GlassControlsMigration.run(preferences);

        assertFalse("the Shade is gone from the store", store.contains(SurfacePresets.RETIRED_SHADE_KEY));
        assertEquals(3, store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
        assertEquals(50, preferences.getSurfaceBaseValue(SurfaceProperty.TINT));
        assertEquals("Edge keeps its stored value", 80, preferences.getFancierGlassDispersionPercent());
    }

    /** A store from before the controls takes the drop in the same edit as the rest. */
    @Test
    public void anOlderStoreDropsTheShadeInTheSameEdit() {
        storeOldLook("frost");
        store.edit().putInt(SurfacePresets.RETIRED_SHADE_KEY, 100).commit();
        GlassControlsMigration.run(preferences);
        assertFalse(store.contains(SurfacePresets.RETIRED_SHADE_KEY));
        assertTrue(SurfacePresets.matches(preferences, preset("frost")));
    }

    /** A saved look that names a Shade sheds it on read and on the rewrite, so it applies as Shade 0. */
    @Test
    public void aSavedLookWithShadeShedsIt() throws Exception {
        java.util.Map<String, Object> look = SurfacePresets.deserialize("{\"format_version\":6,"
            + "\"surface_glass_shade\":80,\"surface_base_opacity\":30}");
        assertFalse(look.containsKey(SurfacePresets.RETIRED_SHADE_KEY));
        assertEquals(30, look.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY));

        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 2).commit();
        preferences.setSurfaceCustomPreset("{\"format_version\":6,\"surface_glass_shade\":80,"
            + "\"surface_base_opacity\":30}");
        GlassControlsMigration.run(preferences);
        JSONObject saved = new JSONObject(preferences.getSurfaceCustomPreset());
        assertFalse(saved.has(SurfacePresets.RETIRED_SHADE_KEY));
        assertEquals(30, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_OPACITY));
    }
}
