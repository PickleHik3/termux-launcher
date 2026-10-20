package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;

/**
 * Mist's opacity by glass (pass 2, 2026-10-17): 60 on dark glass, 35 on light, where sixty of the
 * white Obsidian ink reads as milk. Mist reads as Mist on either glass, a store on the shipped
 * Mist follows the glass when it turns, and any other look (a stored Custom copy included) keeps
 * what the user set.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class MistLightGlassTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store =
            context.getSharedPreferences("mist-light-glass-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @NonNull
    private static SurfacePresets.Preset mist(boolean lightGlass) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets(lightGlass))
            if ("frost".equals(preset.id)) return preset;
        throw new AssertionError("Mist is a shipped Look");
    }

    /** The Look the editor's stop stands on, from the store's own glass; null for Custom. */
    @Nullable
    private String matchedId() {
        for (SurfacePresets.Preset preset : SurfacePresets.presets(preferences))
            if (SurfacePresets.matches(preferences, preset)) return preset.id;
        return null;
    }

    private int opacity() {
        return preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY);
    }

    @Test
    public void mistIs35OnLightGlassAnd60OnDarkAndNothingElseMoves() {
        assertEquals(60, mist(false).values.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY));
        assertEquals(35, mist(true).values.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY));
        assertEquals(35, SurfacePresets.MIST_LIGHT_OPACITY);
        Map<String, Object> dark = mist(false).values;
        Map<String, Object> light = mist(true).values;
        assertEquals("the same keys, in the same order",
            new java.util.ArrayList<>(dark.keySet()), new java.util.ArrayList<>(light.keySet()));
        for (Map.Entry<String, Object> entry : dark.entrySet()) {
            if (TERMUX_APP.KEY_SURFACE_BASE_OPACITY.equals(entry.getKey())) continue;
            assertEquals(entry.getKey(), entry.getValue(), light.get(entry.getKey()));
        }
        assertEquals("Mist's Glass is 75 on either glass",
            GlassControls.blurForGlass(GlassControls.MIST),
            light.get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, light.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        List<SurfacePresets.Preset> darkLooks = SurfacePresets.presets(false);
        List<SurfacePresets.Preset> lightLooks = SurfacePresets.presets(true);
        assertSame(SurfacePresets.presets(), darkLooks);
        assertEquals(darkLooks.size(), lightLooks.size());
        for (int i = 0; i < darkLooks.size(); i++) {
            assertEquals(darkLooks.get(i).id, lightLooks.get(i).id);
            if (!"frost".equals(darkLooks.get(i).id))
                assertSame("only Mist differs", darkLooks.get(i), lightLooks.get(i));
        }
    }

    /** An untold store is dark glass: Mist at 60 is Mist, and 35 is Custom there. */
    @Test
    public void anUntoldStoreIsOnDarkGlass() {
        assertFalse(SurfacePresets.isLightGlass(preferences));
        SurfacePresets.apply(preferences, mist(false));
        assertEquals("frost", matchedId());
        SurfacePresets.apply(preferences, mist(true));
        assertEquals("35 on dark glass is Custom", null, matchedId());
    }

    /** Light glass: Mist applied there is 35 and reads as Mist; 60 there is Custom. */
    @Test
    public void onLightGlassMistIs35AndReadsAsMist() {
        assertTrue(SurfacePresets.followGlassTheme(preferences, true));
        assertTrue(SurfacePresets.isLightGlass(preferences));
        SurfacePresets.apply(preferences, mist(SurfacePresets.isLightGlass(preferences)));
        assertEquals(35, opacity());
        assertEquals("frost", matchedId());
        SurfacePresets.apply(preferences, mist(false));
        assertEquals("60 on light glass is Custom", null, matchedId());
    }

    /** The glass turns: a store on the shipped Mist takes the new glass's opacity, both ways. */
    @Test
    public void mistFollowsTheGlassBothWays() {
        SurfacePresets.apply(preferences, mist(false));
        assertTrue(SurfacePresets.followGlassTheme(preferences, true));
        assertEquals(35, opacity());
        assertEquals("frost", matchedId());

        assertFalse("told again, nothing to write", SurfacePresets.followGlassTheme(preferences, true));
        assertEquals(35, opacity());

        assertTrue(SurfacePresets.followGlassTheme(preferences, false));
        assertEquals(60, opacity());
        assertEquals("frost", matchedId());
    }

    /**
     * An install on Mist before this build (60, never told its glass) on a light glass: the first
     * pass brings it to 35, still Mist.
     */
    @Test
    public void aStoreOnMistBeforeThisBuildTakes35OnLightGlass() {
        SurfacePresets.apply(preferences, mist(false));
        preferences.getSharedPreferences().edit()
            .remove(SurfacePresets.KEY_LOOKS_LIGHT_GLASS).commit();
        SurfacePresets.followGlassTheme(preferences, true);
        assertEquals(35, opacity());
        assertEquals("frost", matchedId());
    }

    /** A hand-tuned look, and the stored Custom copy, keep what the user set when the glass turns. */
    @Test
    public void customKeepsItsOpacityWhenTheGlassTurns() {
        SurfacePresets.apply(preferences, mist(false));
        SurfacePresets.saveCustom(preferences);
        String savedBefore = preferences.getSurfaceCustomPreset();
        GlobalGlass.write(preferences, SurfaceProperty.OPACITY, 50);
        assertEquals(null, matchedId());

        assertTrue(SurfacePresets.followGlassTheme(preferences, true));
        assertEquals("Custom's 50 stays", 50, opacity());
        assertEquals(null, matchedId());
        assertEquals("the stored Custom copy is not rewritten", savedBefore,
            preferences.getSurfaceCustomPreset());
        SurfacePresets.Preset saved = SurfacePresets.custom(preferences);
        assertNotNull(saved);
        assertEquals(60, saved.values.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY));
    }

    /** The other Looks are the same on either glass, so a flip leaves them where they are. */
    @Test
    public void theOtherLooksDoNotMoveWithTheGlass() {
        for (SurfacePresets.Preset look : SurfacePresets.presets(false)) {
            if ("frost".equals(look.id)) continue;
            SurfacePresets.apply(preferences, look);
            int before = opacity();
            SurfacePresets.followGlassTheme(preferences, !SurfacePresets.isLightGlass(preferences));
            assertEquals(look.id, before, opacity());
            assertEquals(look.id, matchedId());
        }
    }
}
