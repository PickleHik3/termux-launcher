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
        assertEquals(28, preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        assertEquals(14, preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP));
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_RIM_GRADIENT, preferences.getSurfaceGlassRim());
        assertEquals(TERMUX_APP.GLASS_MOTION_MIST, preferences.getSurfaceGlassMotion());
        assertTrue(SurfacePresets.matches(preferences, mist));

        // Undoing one of the three un-matches it: the ring reads the new keys too.
        preferences.setSurfaceGlassRim(TERMUX_APP.GLASS_RIM_HAIRLINE);
        assertFalse(SurfacePresets.matches(preferences, mist));
    }

    @Test
    public void theOtherThreePresetsStateTheDefaultsForTheNewKeys() {
        for (String id : new String[] {"stock", "solid", "minimal"}) {
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
    public void classicSlateAndBareApplyTheNumbersTheyAlwaysDid() {
        // Blur / opacity / grain come from the material curves, as before.
        int[] stock = SurfaceMaterials.triple(TERMUX_APP.SURFACE_MATERIAL_GLASS, 50);
        int[] solid = SurfaceMaterials.triple(TERMUX_APP.SURFACE_MATERIAL_SOLID, 78);
        int[] bare = SurfaceMaterials.triple(TERMUX_APP.SURFACE_MATERIAL_GLASS, 0);
        assertTriple(preset("stock"), stock);
        assertTriple(preset("solid"), solid);
        assertTriple(preset("minimal"), bare);

        // Leaving Mist for Classic hands the three glass keys back.
        SurfacePresets.apply(preferences, preset("frost"));
        SurfacePresets.apply(preferences, preset("stock"));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.GLASS_RIM_HAIRLINE, preferences.getSurfaceGlassRim());
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC, preferences.getSurfaceGlassMotion());
        assertTrue(SurfacePresets.matches(preferences, preset("stock")));
    }

    private static void assertTriple(SurfacePresets.Preset preset, int[] triple) {
        assertEquals(preset.id, triple[SurfaceMaterials.BLUR],
            preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(preset.id, triple[SurfaceMaterials.OPACITY],
            preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY));
        assertEquals(preset.id, triple[SurfaceMaterials.GRAIN],
            preset.values.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
    }

    @Test
    public void customRoundTripsTheNewKeys() {
        SurfacePresets.apply(preferences, preset("frost"));
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.apply(preferences, preset("stock"));

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
        assertEquals(SurfacePresets.FORMAT_VERSION, 2);
    }

    @Test
    public void anUnknownStoredValueReadsAsTheDefault() {
        preferences.setSurfaceGlassTint("neon");
        preferences.setSurfaceGlassMotion("bouncy");
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT, preferences.getSurfaceGlassTint());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_MOTION, preferences.getSurfaceGlassMotion());
    }
}
