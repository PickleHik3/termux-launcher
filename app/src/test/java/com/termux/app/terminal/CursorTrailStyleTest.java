package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CursorTrailStyleTest {

    @Test
    public void idsAreStable() {
        assertEquals("default", CursorTrailStyle.DEFAULT.id());
        assertEquals("motion_blur", CursorTrailStyle.MOTION_BLUR.id());
        assertEquals("railgun", CursorTrailStyle.RAILGUN.id());
        assertEquals("torpedo", CursorTrailStyle.TORPEDO.id());
        assertEquals("pixiedust", CursorTrailStyle.PIXIEDUST.id());
        assertEquals("comet", CursorTrailStyle.COMET.id());
    }

    @Test
    public void fromIdRoundTripsAndFallsBackToDefault() {
        for (CursorTrailStyle s : CursorTrailStyle.values()) {
            assertEquals(s, CursorTrailStyle.fromId(s.id()));
        }
        assertEquals(CursorTrailStyle.DEFAULT, CursorTrailStyle.fromId(null));
        assertEquals(CursorTrailStyle.DEFAULT, CursorTrailStyle.fromId("nope"));
        assertEquals(CursorTrailStyle.DEFAULT, CursorTrailStyle.fromId(""));
    }

    @Test
    public void kittyShaderNamesMapAndOthersDoNot() {
        assertEquals(CursorTrailStyle.DEFAULT,
            CursorTrailStyle.fromKittyShaderName("cursor-trail-default"));
        assertEquals(CursorTrailStyle.MOTION_BLUR,
            CursorTrailStyle.fromKittyShaderName("cursor-trail-motion-blur"));
        assertEquals(CursorTrailStyle.RAILGUN,
            CursorTrailStyle.fromKittyShaderName("cursor-trail-railgun"));
        assertEquals(CursorTrailStyle.TORPEDO,
            CursorTrailStyle.fromKittyShaderName("cursor-trail-torpedo"));
        assertEquals(CursorTrailStyle.PIXIEDUST,
            CursorTrailStyle.fromKittyShaderName("cursor-trail-pixiedust"));
        assertNull(CursorTrailStyle.fromKittyShaderName("cursor-trail-blaze"));
        assertNull(CursorTrailStyle.fromKittyShaderName("comet"));
        assertNull(CursorTrailStyle.fromKittyShaderName(null));
    }

    /** The AGSL source sticks to the subset every AGSL device compiles, and declares its inputs. */
    @Test
    public void motionBlurShaderUsesOnlyPortableAgsl() {
        String src = CursorTrailMotionBlur.SHADER;
        String[] banned = {"uint", "<<", ">>", "#define", "^", "fwidth", "dFdx"};
        for (String b : banned) assertFalse("contains " + b, src.contains(b));
        String[] uniforms = {"uStartX", "uStartY", "uEndX", "uEndY", "uCursorLo", "uCursorHi",
            "uColor", "uOpacity"};
        for (String u : uniforms) assertTrue("lacks " + u, src.contains("uniform") && src.contains(u));
        assertTrue(src.contains("layout(color) uniform half4 uColor"));
        assertTrue(src.contains("half4 main(float2"));
    }
}
