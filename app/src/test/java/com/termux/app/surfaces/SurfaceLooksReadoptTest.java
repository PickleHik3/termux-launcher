package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** An install still on a shipped Look whose edge has since moved takes the Look as it ships now. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceLooksReadoptTest {

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("surface-looks-readopt-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    /** The store's contents without the generation marker. */
    private Map<String, ?> looks() {
        HashMap<String, Object> all = new HashMap<>(store.getAll());
        all.remove(SurfacePresets.KEY_LOOKS_GENERATION);
        return all;
    }

    @Test
    public void anOldClearReadsAsTheNewClear() {
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {6, 14, 40, 0, 0, 70}));
        assertFalse(SurfacePresets.matches(preferences, preset("minimal")));

        SurfacePresets.readoptMovedLooks(preferences);

        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        // Clear's bend is one and a half bands since the hairline rework (was 6).
        assertEquals(27, preferences.getFancierGlassBendDp());
        assertEquals(18, preferences.getFancierGlassEdgeWidthDp());
        assertEquals(76, preferences.getFancierGlassClarityPercent());
        assertEquals(SurfacePresets.LOOKS_GENERATION,
            store.getInt(SurfacePresets.KEY_LOOKS_GENERATION, 0));
    }

    @Test
    public void anInstallAlreadyOnGenerationOneTakesTheReworkedEdge() {
        // Generation 1 brought it to 11/28/70/0/25/100; the lobe rework, the lit band and the
        // hairline move it on.
        store.edit().putInt(SurfacePresets.KEY_LOOKS_GENERATION, 1).commit();
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {11, 28, 70, 0, 25, 100}));

        SurfacePresets.readoptMovedLooks(preferences);

        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        assertEquals(10, preferences.getFancierGlassEdgeLightPercent());
        assertEquals(SurfacePresets.LOOKS_GENERATION,
            store.getInt(SurfacePresets.KEY_LOOKS_GENERATION, 0));
    }

    @Test
    public void mistAndTintOnGenerationTwoTakeTheirEdgesOnTheDepthCurve() {
        // Generation 2 left Mist at 24/30 and Tint at 20/26; generation 5 puts them on the Depth
        // curve at 24/16 and 21/14, and nothing else about either Look moves.
        String[] ids = {"frost", "stock"};
        int[][] generationTwo = {{24, 30, 28, 0, 30, 90}, {20, 26, 25, 0, 0, 85}};
        int[] width = {16, 14};
        for (int look = 0; look < ids.length; look++) {
            store.edit().clear().commit();
            store.edit().putInt(SurfacePresets.KEY_LOOKS_GENERATION, 2).commit();
            SurfacePresets.apply(preferences,
                SurfacePresets.withEdges(preset(ids[look]), generationTwo[look]));
            assertFalse(ids[look], SurfacePresets.matches(preferences, preset(ids[look])));

            SurfacePresets.readoptMovedLooks(preferences);

            assertTrue(ids[look], SurfacePresets.matches(preferences, preset(ids[look])));
            assertEquals(ids[look], width[look], preferences.getFancierGlassEdgeWidthDp());
            assertEquals(SurfacePresets.LOOKS_GENERATION,
                store.getInt(SurfacePresets.KEY_LOOKS_GENERATION, 0));
        }
    }

    @Test
    public void anOlderGenerationAlreadyOnTheNewClearIsUntouched() {
        store.edit().putInt(SurfacePresets.KEY_LOOKS_GENERATION, 2).commit();
        SurfacePresets.apply(preferences, preset("minimal"));
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
        assertEquals(18, preferences.getFancierGlassEdgeWidthDp());
    }

    @Test
    public void everyLookOnGenerationFourTakesTheHairlineEdge() {
        // Generation 4 lit the 4 dp band at 50, 45 and 40; generation 5 is the hairline's edges.
        String[] ids = {"minimal", "frost", "stock"};
        int[][] generationFour = {{26, 26, 50, 0, 35, 100}, {24, 24, 45, 0, 30, 90},
            {20, 22, 40, 0, 0, 85}};
        int[][] now = {{27, 18, 10, 0, 40, 76}, {24, 16, 10, 0, 0, 0}, {21, 14, 10, 0, 0, 0}};
        for (int look = 0; look < ids.length; look++) {
            store.edit().clear().commit();
            store.edit().putInt(SurfacePresets.KEY_LOOKS_GENERATION, 4).commit();
            SurfacePresets.apply(preferences,
                SurfacePresets.withEdges(preset(ids[look]), generationFour[look]));
            assertFalse(ids[look], SurfacePresets.matches(preferences, preset(ids[look])));

            SurfacePresets.readoptMovedLooks(preferences);

            assertTrue(ids[look], SurfacePresets.matches(preferences, preset(ids[look])));
            int[] edge = now[look];
            assertEquals(ids[look], edge[0], preferences.getFancierGlassBendDp());
            assertEquals(ids[look], edge[1], preferences.getFancierGlassEdgeWidthDp());
            assertEquals(ids[look], edge[2], preferences.getFancierGlassEdgeLightPercent());
            assertEquals(ids[look], edge[3], preferences.getFancierGlassSpecularPercent());
            assertEquals(ids[look], edge[4], preferences.getFancierGlassDispersionPercent());
            assertEquals(ids[look], edge[5], preferences.getFancierGlassClarityPercent());
            assertEquals(5, store.getInt(SurfacePresets.KEY_LOOKS_GENERATION, 0));
        }
    }

    @Test
    public void everyEarlierEdgeOfEveryMovedLookIsReadopted() {
        int[][][] earlier = {
            {{26, 26, 50, 0, 35, 100}, {26, 26, 30, 0, 35, 100}, {11, 28, 70, 0, 25, 100}, {6, 14, 40, 0, 0, 70},
                {6, 14, 24, 0, 0, 70}, {4, 10, 18, 0, 0, 0}},
            {{24, 24, 45, 0, 30, 90}, {24, 24, 28, 0, 30, 90}, {24, 30, 28, 0, 30, 90}, {13, 32, 60, 0, 20, 90},
                {9, 20, 30, 0, 0, 60}, {9, 20, 18, 0, 0, 45}, {9, 20, 18, 20, 0, 0}, {9, 20, 18, 0, 0, 0}},
            {{20, 22, 40, 0, 0, 85}, {20, 22, 25, 0, 0, 85}, {20, 26, 25, 0, 0, 85}, {10, 26, 60, 0, 0, 85},
                {6, 14, 36, 0, 0, 55}, {6, 14, 22, 0, 0, 55}, {4, 10, 18, 0, 0, 0}},
        };
        String[] ids = {"minimal", "frost", "stock"};
        for (int look = 0; look < ids.length; look++) {
            for (int[] edges : earlier[look]) {
                store.edit().clear().commit();
                SurfacePresets.apply(preferences, SurfacePresets.withEdges(preset(ids[look]), edges));
                SurfacePresets.readoptMovedLooks(preferences);
                // Clear and Tint once shared 4/10/18: the rest of the look tells them apart.
                assertTrue(ids[look] + " " + java.util.Arrays.toString(edges),
                    SurfacePresets.matches(preferences, preset(ids[look])));
            }
        }
    }

    @Test
    public void aCustomLookIsUntouched() {
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {7, 14, 40, 0, 0, 70}));
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
        assertEquals(7, preferences.getFancierGlassBendDp());
    }

    @Test
    public void untouchedDefaultEdgesOffEveryLookAreUntouched() {
        // Mist's oldest edge is the defaults; without Mist's surface values it is not Mist.
        SurfacePresets.apply(preferences, preset("minimal"));
        for (String key : new String[] {
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_BEND,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_CLARITY,
        })
            store.edit().remove(key).commit();
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
    }

    @Test
    public void aStoreAlreadyOnTheNewClearIsUnchanged() {
        SurfacePresets.apply(preferences, preset("minimal"));
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
    }

    @Test
    public void itRunsOncePerGeneration() {
        SurfacePresets.readoptMovedLooks(preferences);
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {6, 14, 40, 0, 0, 70}));
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
    }
}
