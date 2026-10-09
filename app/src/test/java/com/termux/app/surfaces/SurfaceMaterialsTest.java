package com.termux.app.surfaces;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the one fact the material macro's compatibility rests on: the default point - glass at 50 -
 * reproduces the shipped Base triple exactly, so a fresh install (and Reset) reads as a point on
 * the default curve rather than as "Custom".
 */
public class SurfaceMaterialsTest {

    @Test
    public void glassAtFiftyIsTheShippedTriple() {
        assertArrayEquals(new int[] {
            TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY,
            TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN,
        }, SurfaceMaterials.triple(TERMUX_APP.DEFAULT_SURFACE_MATERIAL,
            TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY));
    }

    @Test
    public void everyFamilyStaysUnderTheSharedBlurCap() {
        for (String family : new String[] {TERMUX_APP.SURFACE_MATERIAL_GLASS,
            TERMUX_APP.SURFACE_MATERIAL_FROST, TERMUX_APP.SURFACE_MATERIAL_SOLID}) {
            for (int intensity = 0; intensity <= 100; intensity += 10) {
                int blur = SurfaceMaterials.triple(family, intensity)[SurfaceMaterials.BLUR];
                assertTrue(family + " " + intensity, blur >= 0 && blur <= AppearanceLooks.BLUR_MAX_DP);
            }
        }
        assertEquals(48, AppearanceLooks.BLUR_MAX_DP);
    }
}
