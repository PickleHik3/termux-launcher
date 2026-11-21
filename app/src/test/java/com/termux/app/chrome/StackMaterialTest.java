package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.StackMaterial.RegionLook;

import org.junit.Test;

/** The stack's material as values: lerp's endpoints, the panel blend and the Display mapping. */
public class StackMaterialTest {

    private static final int TINT = 0xFF203040;
    private static final int PANEL = 0xFF2A2B2C;

    private static RegionLook glass(float blur, float opacity) {
        RegionLook look = new RegionLook();
        StackMaterial.glass(blur, opacity, TINT, 0.1f, PANEL, look);
        return look;
    }

    private static RegionLook solid() {
        RegionLook look = new RegionLook();
        StackMaterial.solid(PANEL, look);
        return look;
    }

    @Test public void lerpIsExactlyEachEndAtZeroAndOne() {
        RegionLook a = glass(25f, 0.6f);
        RegionLook b = solid();
        RegionLook out = new RegionLook();
        StackMaterial.lerp(a, b, 0f, out);
        assertTrue(out.sameAs(a));
        StackMaterial.lerp(a, b, 1f, out);
        assertTrue(out.sameAs(b));
        StackMaterial.lerp(a, b, -0.5f, out);
        assertTrue(out.sameAs(a));
        StackMaterial.lerp(a, b, 1.5f, out);
        assertTrue(out.sameAs(b));
    }

    @Test public void aLookBlendedWithItselfIsItselfAtEveryShare() {
        RegionLook a = glass(25f, 0.6f);
        RegionLook out = new RegionLook();
        for (int i = 0; i <= 64; i++) {
            StackMaterial.lerp(a, a, i / 64f, out);
            assertTrue("t=" + i / 64f, out.sameAs(a));
        }
    }

    @Test public void blurCrossfadesTwoFramesRatherThanInterpolatingTheRadius() {
        RegionLook out = new RegionLook();
        StackMaterial.lerp(glass(25f, 0.6f), glass(40f, 0.6f), 0.3f, out);
        assertEquals(25f, out.blurDp, 0f);
        assertEquals(40f, out.blurDpOther, 0f);
        assertEquals(0.3f, out.blurMix, 0f);
    }

    /** GLASS toward SOLID: the panel's alpha is KeyboardMaterialPolicy's travel solidness. */
    @Test public void glassTowardSolidMatchesTravelSolidness() {
        RegionLook out = new RegionLook();
        for (int i = 0; i <= 64; i++) {
            float t = i / 64f;
            StackMaterial.lerp(glass(25f, 0.6f), solid(), t, out);
            float expected = KeyboardMaterialPolicy.travelSolidness(
                KeyboardMaterialPolicy.Material.GLASS, KeyboardMaterialPolicy.Material.SOLID,
                true, true, t);
            assertEquals("t=" + t, expected, out.solidness(), 1f / 255f);
            assertEquals("panel colour holds", PANEL & 0xFFFFFF, out.solidArgb & 0xFFFFFF);
            StackMaterial.lerp(solid(), glass(25f, 0.6f), t, out);
            expected = KeyboardMaterialPolicy.travelSolidness(
                KeyboardMaterialPolicy.Material.SOLID, KeyboardMaterialPolicy.Material.GLASS,
                true, true, t);
            assertEquals("t=" + t, expected, out.solidness(), 1f / 255f);
        }
    }

    @Test public void theDisplayPagesSeeThroughGlassHasNoWallpaperFrame() {
        RegionLook out = new RegionLook();
        assertTrue(StackMaterial.forKeyboard(KeyboardMaterialPolicy.Material.GLASS, true, 25f,
            0.6f, TINT, 0.1f, PANEL, 30f, out));
        assertEquals(0f, out.frameAlpha, 0f);
        assertEquals(30f, out.compositorBlurPx, 0f);
        assertEquals(0f, out.solidness(), 0f);
        assertEquals(0.6f, out.tintAlpha, 0f);

        assertTrue(StackMaterial.forKeyboard(KeyboardMaterialPolicy.Material.GLASS, false, 25f,
            0.6f, TINT, 0.1f, PANEL, 30f, out));
        assertEquals(1f, out.frameAlpha, 0f);
        assertEquals(0f, out.compositorBlurPx, 0f);
        assertEquals(25f, out.blurDp, 0f);

        assertTrue(StackMaterial.forKeyboard(KeyboardMaterialPolicy.Material.SOLID, false, 25f,
            0.6f, TINT, 0.1f, PANEL, 30f, out));
        assertEquals(1f, out.solidness(), 0f);

        assertFalse(StackMaterial.forKeyboard(KeyboardMaterialPolicy.Material.NONE, false, 25f,
            0.6f, TINT, 0.1f, PANEL, 30f, out));
    }
}