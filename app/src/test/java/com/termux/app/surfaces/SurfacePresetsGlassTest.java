package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** What a preset carries beyond the numbers: the glass tint, rim and motion. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfacePresetsGlassTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store =
            context.getSharedPreferences("surface-presets-glass-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    @Test
    public void mistIsTheObsidianLookAndItAppliesAndMatches() {
        SurfacePresets.Preset mist = preset("frost");
        SurfacePresets.apply(preferences, mist);

        assertEquals(25, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(60, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertEquals(8, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_RIM_GRADIENT, preferences.getSurfaceGlassRim());
        assertEquals(TERMUX_APP.GLASS_MOTION_MIST, preferences.getSurfaceGlassMotion());
        assertTrue(SurfacePresets.matches(preferences, mist));

        // Undoing one of the three un-matches it: the ring reads the new keys too.
        preferences.setSurfaceGlassRim(TERMUX_APP.GLASS_RIM_HAIRLINE);
        assertFalse(SurfacePresets.matches(preferences, mist));
    }

    @Test
    public void solidStatesTheDefaultsForTheNewKeys() {
        for (String id : new String[] {"solid"}) {
            SurfacePresets.Preset preset = preset(id);
            assertEquals(id, TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT,
                preset.values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
            assertEquals(id, TERMUX_APP.DEFAULT_SURFACE_GLASS_RIM,
                preset.values.get(TERMUX_APP.KEY_SURFACE_GLASS_RIM));
            assertEquals(id, TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION,
                preset.values.get(TERMUX_APP.KEY_SURFACE_GLASS_MOTION));
        }
    }

    @Test
    public void clearTintAndSolidCarryTheRecipesOfTheSpec() {
        assertRecipe(preset("minimal"), 44, 2, 14, 28, 32, 85);
        assertRecipe(preset("stock"), 6, 46, 14, 4, 10, 18);
        assertRecipe(preset("solid"), 0, 92, 0, 0, 1, 0);
        assertRecipe(preset("frost"), 25, 60, 8, 9, 20, 18);
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN,
            preset("stock").values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));

        // Leaving Mist for Tint hands rim and motion back.
        SurfacePresets.apply(preferences, preset("frost"));
        SurfacePresets.apply(preferences, preset("stock"));
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_RIM_HAIRLINE, preferences.getSurfaceGlassRim());
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC, preferences.getSurfaceGlassMotion());
        assertEquals(10, preferences.getFancierGlassEdgeWidthDp());
        assertTrue(SurfacePresets.matches(preferences, preset("stock")));
    }

    @Test
    public void clearIsTheFanciestGlassWithTheCanvasDetachedAtEight() {
        SurfacePresets.Preset clear = preset("minimal");
        assertEquals(45, clear.values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(40, clear.values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals("a sharp, even hairline, no gradient wash into the pane",
            TERMUX_APP.GLASS_RIM_HAIRLINE, clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_RIM));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC,
            clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_MOTION));
        assertEquals(8, clear.values.get(TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY));

        SurfacePresets.apply(preferences, clear);
        assertEquals(2, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertEquals(8, preferences.getTerminalBackgroundOpacity());
        assertEquals(45, preferences.getFancierGlassSpecularPercent());
        assertEquals(40, preferences.getFancierGlassDispersionPercent());
        assertTrue(SurfacePresets.matches(preferences, clear));
    }

    @Test
    public void everyOtherLookHasNoDispersionAndOnlyMistAnySpecular() {
        assertEquals(0, preset("stock").values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, preset("solid").values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, preset("frost").values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(0, preset("stock").values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(0, preset("solid").values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_SPECULAR,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_DISPERSION);
        assertEquals(0, TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_SPECULAR);
    }

    @Test
    public void aLookStoredBeforeFormatThreeReadsAsNoSpecularOrDispersion() {
        assertEquals(3, SurfacePresets.FORMAT_VERSION);
        Map<String, Object> look = SurfacePresets.deserialize(
            "{\"format_version\":2,\"surface_base_blur\":21,\"surface_material\":\"glass\"}");
        assertNotNull(look);
        assertEquals(0, look.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, look.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
    }

    private static void assertRecipe(SurfacePresets.Preset preset, int blur, int opacity,
                                     int grain, int bend, int edgeWidth, int edgeLight) {
        assertEquals(preset.id, blur, preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(preset.id, opacity, preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY));
        assertEquals(preset.id, grain, preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
        // Corners and margins are Layout's (2026-10-01).
        assertFalse(preset.id,
            preset.values.containsKey(TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS));
        assertFalse(preset.id, preset.values.containsKey(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS));
        assertFalse(preset.id, preset.values.containsKey(TERMUX_APP.KEY_TERMINAL_PANE_GAP));
        assertEquals(preset.id, bend, preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_BEND));
        assertEquals(preset.id, edgeWidth,
            preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH));
        assertEquals(preset.id, edgeLight,
            preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT));
        assertFalse(preset.id, preset.values.containsKey(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE));
    }

    @Test
    public void customRoundTripsTheNewKeys() {
        SurfacePresets.apply(preferences, preset("frost"));
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.apply(preferences, preset("minimal"));

        SurfacePresets.Preset custom = SurfacePresets.custom(preferences);
        assertNotNull(custom);
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN,
            custom.values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        SurfacePresets.apply(preferences, custom);
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_RIM_GRADIENT, preferences.getSurfaceGlassRim());
        assertEquals(TERMUX_APP.GLASS_MOTION_MIST, preferences.getSurfaceGlassMotion());
        assertTrue(SurfacePresets.matches(preferences, custom));
    }

    @Test
    public void aLookStoredBeforeFormatTwoReadsAsClassicSchemeHairline() {
        String old = "{\"format_version\":1,\"surface_base_blur\":21,\"surface_material\":\"glass\"}";
        Map<String, Object> look = SurfacePresets.deserialize(old);
        assertNotNull(look);
        assertEquals(21, look.get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, look.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(TERMUX_APP.GLASS_RIM_HAIRLINE, look.get(TERMUX_APP.KEY_SURFACE_GLASS_RIM));
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC,
            look.get(TERMUX_APP.KEY_SURFACE_GLASS_MOTION));

        // And applying it over Mist puts the three back.
        preferences.setSurfaceCustomPreset(old);
        SurfacePresets.apply(preferences, preset("frost"));
        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC, preferences.getSurfaceGlassMotion());
        assertEquals(3, SurfacePresets.FORMAT_VERSION);
    }

    @Test
    public void anUnknownStoredValueReadsAsTheDefault() {
        preferences.setSurfaceGlassTint("neon");
        preferences.setSurfaceGlassMotion("bouncy");
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION, preferences.getSurfaceGlassMotion());
    }
}
