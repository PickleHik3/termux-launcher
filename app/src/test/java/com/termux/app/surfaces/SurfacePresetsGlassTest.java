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
        // Clear, Mist and Tint sit on the Depth curve at 90, 80 and 70, each bend one and a half
        // bands; light 10 is stored for older builds only, the hairline is fixed in the program.
        assertRecipe(preset("minimal"), 4, 10, 4, 27, 18, 10, 40, 76);
        assertRecipe(preset("stock"), 10, 46, 10, 21, 14, 10, 0, 0);
        assertRecipe(preset("solid"), 0, 92, 0, 0, 1, 0, 0, 0);
        assertRecipe(preset("frost"), 25, 60, 8, 24, 16, 10, 0, 0);
        assertEquals(TERMUX_APP.GLASS_TINT_MATERIAL,
            preset("stock").values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));

        // Leaving Mist for Tint hands rim and motion back.
        SurfacePresets.apply(preferences, preset("frost"));
        SurfacePresets.apply(preferences, preset("stock"));
        assertEquals(TERMUX_APP.GLASS_TINT_MATERIAL, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_RIM_HAIRLINE, preferences.getSurfaceGlassRim());
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC, preferences.getSurfaceGlassMotion());
        assertEquals(14, preferences.getFancierGlassEdgeWidthDp());
        assertEquals(0, preferences.getFancierGlassClarityPercent());
        assertTrue(SurfacePresets.matches(preferences, preset("stock")));
    }

    @Test
    public void clearIsTheMostTransparentGlassWithNoDetachedTerminalOpacity() {
        SurfacePresets.Preset clear = preset("minimal");
        assertEquals("one hairline, no second line inside it", 0, clear.values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals("Haze Clear's corner glint", 40, clear.values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals("a sharp, even hairline, no gradient wash into the pane",
            TERMUX_APP.GLASS_RIM_HAIRLINE, clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_RIM));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC,
            clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_MOTION));
        assertFalse(clear.values.containsKey(TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY));

        SurfacePresets.apply(preferences, clear);
        assertEquals(10, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertEquals(0, preferences.getFancierGlassSpecularPercent());
        assertEquals(40, preferences.getFancierGlassDispersionPercent());
        assertTrue(SurfacePresets.matches(preferences, clear));
    }

    @Test
    public void onlyClearCarriesAFringeAndNoLookAnySpecular() {
        assertEquals(0, preset("stock").values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, preset("solid").values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, preset("frost").values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, preset("frost").values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(0, preset("stock").values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(0, preset("solid").values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_SPECULAR,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_DISPERSION);
        assertEquals(0, TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_SPECULAR);
    }

    @Test
    public void aLookStoredBeforeFormatFourReadsAsNoSpecularDispersionOrClarity() {
        assertEquals(4, SurfacePresets.FORMAT_VERSION);
        Map<String, Object> look = SurfacePresets.deserialize(
            "{\"format_version\":2,\"surface_base_blur\":21,\"surface_material\":\"glass\"}");
        assertNotNull(look);
        assertEquals(0, look.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, look.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(0, look.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));

        // A version 3 look, with its specular and dispersion, still gains no clarity.
        Map<String, Object> three = SurfacePresets.deserialize("{\"format_version\":3,"
            + "\"surface_base_blur\":21,\"fancier_glass_specular\":30,"
            + "\"fancier_glass_dispersion\":20}");
        assertNotNull(three);
        assertEquals(30, three.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(0, three.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));

        // Applied over Clear, it hands the edge back to the frost.
        preferences.setSurfaceCustomPreset("{\"format_version\":3,\"surface_base_blur\":21}");
        SurfacePresets.apply(preferences, preset("minimal"));
        assertEquals(76, preferences.getFancierGlassClarityPercent());
        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(0, preferences.getFancierGlassClarityPercent());
    }

    @Test
    public void onlyClearCarriesClarity() {
        // Haze's sharp edge is a per-style constant: Clear 0.76, Regular 0.
        assertEquals(76, preset("minimal").values.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
        assertEquals(0, preset("frost").values.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
        assertEquals(0, preset("stock").values.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
        assertEquals(0, preset("solid").values.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));

        for (String id : new String[] {"minimal", "frost", "stock", "solid"}) {
            SurfacePresets.Preset preset = preset(id);
            SurfacePresets.apply(preferences, preset);
            assertEquals(id, preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY),
                preferences.getFancierGlassClarityPercent());
            assertTrue(id, SurfacePresets.matches(preferences, preset));
            // Clarity is part of the look: moving it alone un-matches the Look.
            preferences.setFancierGlassClarityPercent(
                preferences.getFancierGlassClarityPercent() == 0 ? 10 : 0);
            assertFalse(id, SurfacePresets.matches(preferences, preset));
        }

        // A saved Custom keeps it.
        SurfacePresets.apply(preferences, preset("minimal"));
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.apply(preferences, preset("solid"));
        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(76, preferences.getFancierGlassClarityPercent());
    }

    private static void assertRecipe(SurfacePresets.Preset preset, int blur, int opacity,
                                     int grain, int bend, int edgeWidth, int edgeLight,
                                     int dispersion, int clarity) {
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
        assertEquals(preset.id, 0, preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR));
        assertEquals(preset.id, dispersion,
            preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertEquals(preset.id, clarity, preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
        assertFalse(preset.id, preset.values.containsKey(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE));
    }

    @Test
    public void aFreshInstallOpensExactlyOnClear() {
        // The store is empty, as on a first run; adoption writes the shipped look into it.
        preferences.migrateSurfaceInheritance();
        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        for (String id : new String[] {"frost", "stock", "solid"})
            assertFalse(id, SurfacePresets.matches(preferences, preset(id)));
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
        assertEquals(4, SurfacePresets.FORMAT_VERSION);
    }

    @Test
    public void anUnknownStoredValueReadsAsTheDefault() {
        preferences.setSurfaceGlassTint("neon");
        preferences.setSurfaceGlassMotion("bouncy");
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION, preferences.getSurfaceGlassMotion());
    }
}
