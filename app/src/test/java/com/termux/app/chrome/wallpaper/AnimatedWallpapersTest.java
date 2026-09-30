package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class AnimatedWallpapersTest {

    private static final String[] UNIFORMS = {
        "uniform float2 uResolution", "uniform float uTime", "uniform float uEnergy", "uniform float uDim",
        "layout(color) uniform half4 uPalette0", "layout(color) uniform half4 uPalette1",
        "layout(color) uniform half4 uPalette2", "layout(color) uniform half4 uPalette3",
        "uniform float4 uMomentRect0", "uniform float2 uMomentState0",
        "uniform float4 uMomentRect1", "uniform float2 uMomentState1",
        "half4 main(float2 "
    };

    @Test public void idsAreTheFourBuiltInsAndUnique() {
        Set<String> ids = new HashSet<>();
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) assertTrue(w.id(), ids.add(w.id()));
        assertEquals(4, ids.size());
        assertTrue(ids.contains("aurora") && ids.contains("mesh") && ids.contains("tide") && ids.contains("rain"));
    }

    @Test public void lookups() {
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) assertSame(w, AnimatedWallpapers.byId(w.id()));
        assertNull(AnimatedWallpapers.byId("nope"));
        assertNull(AnimatedWallpapers.byId(null));
    }

    @Test public void everyProgramDeclaresTheUniformContract() {
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
            for (String u : UNIFORMS) assertTrue(w.id() + " lacks " + u, w.agsl().contains(u));
            assertTrue(w.id(), w.agsl().contains("float3 scene(float2 p)"));
        }
    }

    @Test public void ownPalettesHaveFourColours() {
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
            assertNotNull(w.label());
            assertEquals(w.id(), 4, w.ownPalette().length);
        }
    }

    @Test public void loopsAreAtLeastAMinute() {
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) assertTrue(w.id(), w.periodSeconds() >= 60f);
    }

    @Test public void sourceAvoidsUnsupportedAgslConstructs() {
        String[] banned = {"uint", "<<", ">>", "#define", "^"};
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
            for (String b : banned) assertFalse(w.id() + " contains " + b, w.agsl().contains(b));
        }
    }
}
