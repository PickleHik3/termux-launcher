package com.termux.app.glass;

import static org.junit.Assert.assertEquals;

import com.termux.app.DockGlassRendering;
import com.termux.app.chrome.StackMaterial;

import org.junit.Test;

public class ArgbTest {
    private static final int A = 0x10203040;
    private static final int B = 0xF0E0D0C0;

    @Test
    public void endsAreExact() {
        assertEquals(A, Argb.lerp(A, B, 0f));
        assertEquals(B, Argb.lerp(A, B, 1f));
    }

    @Test
    public void midpointBlendsEachChannelIncludingAlpha() {
        assertEquals(0x20406080, Argb.lerp(0x00204060, 0x406080A0, 0.5f));
    }

    @Test
    public void progressOutsideZeroToOneIsClamped() {
        assertEquals(A, Argb.lerp(A, B, -3f));
        assertEquals(B, Argb.lerp(A, B, 7f));
    }

    @Test
    public void nanIsTheFromColour() {
        assertEquals(A, Argb.lerp(A, B, Float.NaN));
    }

    @Test
    public void equalColoursStayPut() {
        assertEquals(A, Argb.lerp(A, A, 0.37f));
    }

    @Test
    public void legibilityBlendMatchesTheOneLerp() {
        for (float t : new float[] {0f, 0.2f, 0.5f, 0.83f, 1f}) {
            assertEquals(Argb.lerp(A, B, t), LegibilityBlend.color(A, B, t));
        }
    }

    @Test
    public void stackMaterialMatchesTheOneLerp() {
        for (float t : new float[] {0f, 0.2f, 0.5f, 0.83f, 1f}) {
            assertEquals(Argb.lerp(A, B, t), StackMaterial.mixArgb(A, B, t));
            assertEquals(Argb.lerp(0, 0xFFFFFFFF, t), StackMaterial.mixArgb(0, 0xFFFFFFFF, t));
        }
    }

    @Test
    public void dockGlassMatchesTheOneLerp() {
        for (float t : new float[] {0f, 0.2f, 0.5f, 0.83f, 1f}) {
            assertEquals(Argb.lerp(A, B, t), DockGlassRendering.lerpArgb(A, B, t));
        }
    }
}
