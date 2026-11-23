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
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
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

        // Glass 75.
        assertEquals(25, preferences.getSurfaceGlobalValue(SurfaceProperty.BLUR));
        assertEquals(60, preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
        assertEquals(7, preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN));
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_MOTION_MIST, preferences.getSurfaceGlassMotion());
        assertEquals(27, preferences.getFancierGlassBendDp());
        assertEquals(50, preferences.getGlassShine());
        assertTrue(SurfacePresets.matches(preferences, mist));

        // Undoing one of the three un-matches it: the ring reads the new keys too.
        preferences.setSurfaceGlassMotion(TERMUX_APP.GLASS_MOTION_CLASSIC);
        assertFalse(SurfacePresets.matches(preferences, mist));
    }

    @Test
    public void solidStatesTheDefaultsForTheNewKeys() {
        for (String id : new String[] {"solid"}) {
            SurfacePresets.Preset preset = preset(id);
            assertEquals(id, TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT,
                preset.values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
            assertEquals(id, TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION,
                preset.values.get(TERMUX_APP.KEY_SURFACE_GLASS_MOTION));
        }
    }

    @Test
    public void theLooksCarryTheRecipesOfTheSpec() {
        // Depth is the stored thickness (dp), Shine the light: Clear, Frosted and Tinted differ in
        // the face; Clear and Frosted share the realistic 27 dp slab and Shine 50, Tinted is the
        // thinnest pane, Solid a flat slab with a quiet rim, Liquid the dramatic one.
        assertRecipe(preset("minimal"), 7, 10, 0, 27, 50);
        assertRecipe(preset("stock"), 12, 46, 4, 21, 50);
        assertRecipe(preset("solid"), 0, 92, 0, 0, 30);
        assertRecipe(preset("frost"), 25, 60, 7, 27, 50);
        assertRecipe(preset("liquid"), 7, 8, 0, 41, 85);
    }

    @Test
    public void liquidIsPastRealismAndSolidAndTheOthersAreNot() {
        assertTrue((Integer) preset("liquid").values.get(TERMUX_APP.KEY_FANCIER_GLASS_BEND)
            > com.termux.shared.termux.settings.preferences.GlassMaterial.REALISTIC_BEND_DP);
        assertTrue((Integer) preset("liquid").values.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE)
            > com.termux.shared.termux.settings.preferences.GlassMaterial.SHINE_DEFAULT);
        for (String id : new String[] {"minimal", "frost"})
            assertEquals(id, com.termux.shared.termux.settings.preferences.GlassMaterial.SHINE_DEFAULT,
                preset(id).values.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE));
    }

    @Test
    public void clearIsTheMostTransparentGlassWithNoDetachedTerminalOpacity() {
        SurfacePresets.Preset clear = preset("minimal");
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC,
            clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_MOTION));
        assertFalse(clear.values.containsKey(TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY));
        // The retired keys are in no Look.
        assertFalse(clear.values.containsKey(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertFalse(clear.values.containsKey(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
        assertFalse(clear.values.containsKey(TERMUX_APP.KEY_SURFACE_GLASS_RIM));

        SurfacePresets.apply(preferences, clear);
        assertEquals(10, preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
        assertEquals(50, preferences.getGlassShine());
        assertTrue(SurfacePresets.matches(preferences, clear));
    }

    @Test
    public void aLookStoredBeforeFormatSevenFoldsItsRimAndEdgeIntoShine() {
        assertEquals(7, SurfacePresets.FORMAT_VERSION);
        // Hairline with Clear's glint: the default Shine; the retired keys are gone.
        Map<String, Object> clear = SurfacePresets.deserialize("{\"format_version\":6,"
            + "\"surface_glass_rim\":\"hairline\",\"fancier_glass_dispersion\":40,"
            + "\"fancier_glass_clarity\":76,\"fancier_glass_bend\":27}");
        assertNotNull(clear);
        assertEquals(50, clear.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE));
        assertFalse(clear.containsKey(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION));
        assertFalse(clear.containsKey(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
        assertFalse(clear.containsKey(TERMUX_APP.KEY_SURFACE_GLASS_RIM));
        assertEquals(27, clear.get(TERMUX_APP.KEY_FANCIER_GLASS_BEND));
        // A gradient rim is a higher Shine, a bold Edge raises it more, and none lowers it.
        Map<String, Object> bold = SurfacePresets.deserialize("{\"format_version\":6,"
            + "\"surface_glass_rim\":\"gradient\",\"fancier_glass_dispersion\":100}");
        assertNotNull(bold);
        assertEquals(100, bold.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE));
        // A rim that is off is a low Shine.
        Map<String, Object> off = SurfacePresets.deserialize("{\"format_version\":6,"
            + "\"surface_glass_rim\":\"off\"}");
        assertNotNull(off);
        assertTrue((Integer) off.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE) < 20);
    }

    @Test
    public void everyLookAppliesAndMatchesAndShineIsPartOfTheLook() {
        for (String id : new String[] {"minimal", "frost", "stock", "solid", "liquid"}) {
            SurfacePresets.Preset preset = preset(id);
            SurfacePresets.apply(preferences, preset);
            assertEquals(id, preset.values.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE),
                preferences.getGlassShine());
            assertTrue(id, SurfacePresets.matches(preferences, preset));
            preferences.setGlassShine(preferences.getGlassShine() == 0 ? 10 : 0);
            assertFalse(id, SurfacePresets.matches(preferences, preset));
        }
        // A saved Custom keeps it.
        SurfacePresets.apply(preferences, preset("liquid"));
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.apply(preferences, preset("solid"));
        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(85, preferences.getGlassShine());
    }

    private static void assertRecipe(SurfacePresets.Preset preset, int blur, int opacity,
                                     int grain, int bend, int shine) {
        assertEquals(preset.id, com.termux.shared.termux.settings.preferences.FrostStops.nearestStopByBlur(blur),
            preset.values.get(SurfacePresets.cellKey(null, SurfaceProperty.BLUR)));
        assertEquals(preset.id, opacity, preset.values.get(SurfacePresets.cellKey(null, SurfaceProperty.OPACITY)));
        assertEquals(preset.id, grain, preset.values.get(SurfacePresets.cellKey(null, SurfaceProperty.GRAIN)));
        // Corners and margins are Layout's (2026-10-01).
        assertFalse(preset.id,
            preset.values.containsKey(SurfacePresets.cellKey(null, SurfaceProperty.CORNER_RADIUS)));
        assertFalse(preset.id, preset.values.containsKey(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS));
        assertFalse(preset.id, preset.values.containsKey(TERMUX_APP.KEY_TERMINAL_PANE_GAP));
        assertEquals(preset.id, bend, preset.values.get(TERMUX_APP.KEY_FANCIER_GLASS_BEND));
        assertEquals(preset.id, shine, preset.values.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE));
        assertFalse(preset.id, preset.values.containsKey(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE));
    }

    @Test
    public void aFreshInstallOpensExactlyOnFrosted() {
        // The store is empty, as on a first run; adoption writes the shipped look into it.
        // (The fixture's Terminal page id is a stored key, so it is taken out for the first run
        // and given back after adoption.)
        preferences.getSharedPreferences().edit()
            .remove(com.termux.shared.termux.settings.preferences.PageLookKeys
                .KEY_TERMINAL_PAGE_ID).commit();
        preferences.migrateLookGrid(java.util.Collections.emptyList());
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        assertTrue(SurfacePresets.matches(preferences, preset("frost")));
        for (String id : new String[] {"minimal", "stock", "solid", "liquid"})
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
        assertEquals(TERMUX_APP.GLASS_MOTION_MIST, preferences.getSurfaceGlassMotion());
        assertTrue(SurfacePresets.matches(preferences, custom));
    }

    @Test
    public void aLookStoredBeforeFormatTwoReadsAsClassicSchemeHairline() {
        String old = "{\"format_version\":1,\"surface_base_blur\":21,\"surface_material\":\"glass\"}";
        Map<String, Object> look = SurfacePresets.deserialize(old);
        assertNotNull(look);
        assertEquals(com.termux.shared.termux.settings.preferences.FrostStops.nearestStopByBlur(21),
            look.get(SurfacePresets.cellKey(null, SurfaceProperty.BLUR)));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, look.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(50, look.get(TERMUX_APP.KEY_SURFACE_GLASS_SHINE));
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC,
            look.get(TERMUX_APP.KEY_SURFACE_GLASS_MOTION));

        // And applying it over Mist puts the three back.
        preferences.setSurfaceCustomPreset(old);
        SurfacePresets.apply(preferences, preset("frost"));
        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC, preferences.getSurfaceGlassMotion());
        assertEquals(7, SurfacePresets.FORMAT_VERSION);
    }

    @Test
    public void anUnknownStoredValueReadsAsTheDefault() {
        preferences.setSurfaceGlassTint("neon");
        preferences.setSurfaceGlassMotion("bouncy");
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION, preferences.getSurfaceGlassMotion());
    }

}
