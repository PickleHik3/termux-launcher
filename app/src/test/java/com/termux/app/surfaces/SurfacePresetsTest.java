package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the preset round-trip: a Look applied to any state is its recipe, detaches nothing, never
 * touches Style, and the selection ring's match test agrees. Plus the fifth
 * card, which is the user's own saved look rather than one this build ships.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfacePresetsTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store =
            context.getSharedPreferences("surface-presets-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    @Test
    public void theLooksAreOrderedClearMistTintSolid() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            ids.add(preset.id);
        assertEquals(java.util.Arrays.asList("minimal", "frost", "stock", "solid"), ids);
    }

    @Test
    public void applyingTintWritesItsRecipeDetachesNothingAndLeavesStyleAlone() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 25);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, 77);
        preferences.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED);

        SurfacePresets.Preset tint = preset("stock");
        SurfacePresets.apply(preferences, tint);

        assertEquals(6, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(46, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertEquals(14, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        // A Look never detaches a surface and never touches Style.
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows())
            assertTrue(row.slot + "/" + row.property,
                preferences.isSurfaceInheriting(row.slot, row.property));
        assertEquals(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED,
            preferences.getAppLauncherDockStyle());

        assertTrue(SurfacePresets.matches(preferences, tint));
        assertFalse(SurfacePresets.matches(preferences, preset("frost")));

        // The match ignores Style.
        preferences.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT);
        assertTrue(SurfacePresets.matches(preferences, tint));
    }

    /**
     * The saved look is a pin, not a snapshot of "now": it has to survive every later edit, which
     * is the whole difference between the Custom card and simply leaving the editor alone.
     */
    @Test
    public void savedCustomLookOutlivesLaterEdits() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 21);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, 63);
        SurfacePresets.saveCustom(preferences);

        SurfacePresets.Preset custom = SurfacePresets.custom(preferences);
        assertNotNull(custom);
        assertEquals(SurfacePresets.CUSTOM_ID, custom.id);
        assertTrue(SurfacePresets.matches(preferences, custom));

        // Wander off, then come back through the card.
        SurfacePresets.apply(preferences, preset("stock"));
        assertFalse(SurfacePresets.matches(preferences, custom));
        assertEquals(21, SurfacePresets.custom(preferences).values
            .get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));

        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(21, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.GRAIN));
        assertEquals(63,
            preferences.getSurfaceOverrideValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN));
    }

    /** Nothing saved is not an empty look: the card has to be able to tell those apart. */
    @Test
    public void thereIsNoCustomPresetUntilOneIsSaved() {
        assertNull(SurfacePresets.custom(preferences));
        assertNull(SurfacePresets.deserialize(""));
        assertNull(SurfacePresets.deserialize("not json"));
    }

    /** JSON widens ints on the way out; a look that read back as Long would not apply. */
    @Test
    public void aStoredLookReadsBackAsTheTypesTheFormatUses() {
        SurfacePresets.saveCustom(preferences);
        java.util.Map<String, Object> look =
            SurfacePresets.deserialize(preferences.getSurfaceCustomPreset());
        assertNotNull(look);
        assertTrue(look.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY) instanceof Integer);
        assertTrue(look.get(TERMUX_APP.KEY_SURFACE_MATERIAL) instanceof String);
        assertFalse(look.containsKey(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE));
    }

    @Test
    public void applyingCustomNeverChangesTheStyle() {
        String stored = "{\"format_version\":2,\"app_launcher_dock_style\":\"rounded\","
            + "\"surface_base_blur\":21}";
        preferences.setSurfaceCustomPreset(stored);
        preferences.setAppLauncherDockStyle("docked");
        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(21, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals("docked", preferences.getAppLauncherDockStyle());
        assertTrue(SurfacePresets.matches(preferences, SurfacePresets.custom(preferences)));
    }

    /**
     * Custom's row 2 sets values no Look names — the key caps, the wallpaper's dim and Soft — and
     * the Custom stop has to bring them back with the rest.
     */
    @Test
    public void customCarriesWhatRowTwoSets() {
        preferences.setInAppKeyboardKeyOpacity(40);
        preferences.setInAppKeyboardKeyCornerRadiusDp(9f);
        preferences.setWallpaperBackdropDim(35);
        SoftWallpaper.set(preferences, true);
        SurfacePresets.saveCustom(preferences);

        preferences.setInAppKeyboardKeyOpacity(90);
        preferences.setInAppKeyboardKeyCornerRadiusDp(15f);
        preferences.setWallpaperBackdropDim(0);
        SoftWallpaper.set(preferences, false);
        assertFalse(SurfacePresets.matches(preferences, SurfacePresets.custom(preferences)));

        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(40, preferences.getInAppKeyboardKeyOpacity());
        assertEquals(9f, preferences.getInAppKeyboardKeyCornerRadiusDp(), 0.001f);
        assertEquals(35, preferences.getWallpaperBackdropDim());
        assertTrue(SoftWallpaper.isOn(preferences));
        assertTrue(SurfacePresets.matches(preferences, SurfacePresets.custom(preferences)));
    }

    /** A Look leaves the Custom-only values where they are; it names none of them. */
    @Test
    public void aLookNamesNoneOfTheCustomOnlyValues() {
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            assertFalse(preset.values.containsKey(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY));
            assertFalse(preset.values.containsKey(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM));
            assertFalse(preset.values.containsKey(SoftWallpaper.KEY_WALLPAPER_SOFT));
        }
    }

    @Test
    public void everyPresetCarriesItsGlass() {
        String[] glass = {
            TERMUX_APP.KEY_SURFACE_BASE_BLUR,
            TERMUX_APP.KEY_SURFACE_BASE_OPACITY,
            TERMUX_APP.KEY_SURFACE_BASE_GRAIN,
        };
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            for (String key : glass)
                assertTrue(preset.id + " misses " + key, preset.values.containsKey(key));
        }
    }

    // ------------------------------------------------------------- corners and margins (Layout)

    /** Corners and margins are Layout's (2026-10-01): no Look names any of them. */
    @Test
    public void aLookNamesNoCornerOrMargin() {
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            for (String key : preset.values.keySet())
                assertFalse(preset.id + " names " + key, SurfacePresets.isLayoutOwned(key));
            assertFalse(preset.values.containsKey(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS));
            assertFalse(preset.values.containsKey(TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP));
            assertFalse(preset.values.containsKey(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS));
            assertFalse(preset.values.containsKey(TERMUX_APP.KEY_TERMINAL_PANE_GAP));
        }
    }

    @Test
    public void theLayoutOwnedKeysAreCornersSideGapsAndTheTerminalsMargin() {
        assertTrue(SurfacePresets.isLayoutOwned(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS));
        assertTrue(SurfacePresets.isLayoutOwned(TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP));
        assertTrue(SurfacePresets.isLayoutOwned(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS));
        assertTrue(SurfacePresets.isLayoutOwned(TERMUX_APP.KEY_TERMINAL_PANE_GAP));
        assertTrue(SurfacePresets.isLayoutOwned(TermuxAppSharedPreferences.surfaceOverrideKey(
            SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS)));
        assertTrue(SurfacePresets.isLayoutOwned(TermuxAppSharedPreferences.surfaceOverrideKey(
            SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP)));
        assertFalse(SurfacePresets.isLayoutOwned(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertFalse(SurfacePresets.isLayoutOwned(TermuxAppSharedPreferences.surfaceOverrideKey(
            SurfaceSlot.DOCK, SurfaceProperty.GRAIN)));
        // The key caps' radius is the keyboard's Key corners, not the global shape.
        assertFalse(SurfacePresets.isLayoutOwned(
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP));
    }

    /** Switching Looks keeps the user's Layout corners and margin, one value each. */
    @Test
    public void switchingLooksKeepsCornersAndMargins() {
        preferences.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, 33);
        preferences.setTerminalCornerRadius(33);
        preferences.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 21);
        preferences.setTerminalPaneGap(9);

        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            SurfacePresets.apply(preferences, preset);
            assertEquals(preset.id, 33,
                preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
            assertEquals(preset.id, 33, preferences.getTerminalCornerRadius());
            assertEquals(preset.id, 21, preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP));
            assertEquals(preset.id, 9, preferences.getTerminalPaneGap());
            // The Look still reads as applied: its ring does not care about the shape.
            assertTrue(preset.id, SurfacePresets.matches(preferences, preset));
        }
    }

    /**
     * A Custom saved before the rule still carries corners and margins: they stay in the blob for
     * migration, but applying it obeys none of them, and a fresh save leaves them out.
     */
    @Test
    public void aStoredCustomsCornersAndMarginsAreIgnored() {
        preferences.setSurfaceCustomPreset("{\"format_version\":2,\"surface_base_blur\":21,"
            + "\"" + TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS + "\":2,"
            + "\"" + TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP + "\":3,"
            + "\"" + TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS + "\":2,"
            + "\"" + TERMUX_APP.KEY_TERMINAL_PANE_GAP + "\":1}");
        preferences.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, 30);
        preferences.setTerminalCornerRadius(30);
        preferences.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 16);
        preferences.setTerminalPaneGap(6);

        SurfacePresets.Preset custom = SurfacePresets.custom(preferences);
        assertNotNull(custom);
        SurfacePresets.apply(preferences, custom);
        assertEquals(21, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(30, preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        assertEquals(30, preferences.getTerminalCornerRadius());
        assertEquals(16, preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP));
        assertEquals(6, preferences.getTerminalPaneGap());
        assertTrue(SurfacePresets.matches(preferences, custom));

        SurfacePresets.saveCustom(preferences);
        for (String key : SurfacePresets.custom(preferences).values.keySet())
            assertFalse(key, SurfacePresets.isLayoutOwned(key));
    }
}
