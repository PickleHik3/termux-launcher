package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    /** kitty.conf's id wins; otherwise the preference; otherwise the default. */
    @Test
    public void effectiveStylePrefersKittyThenPreferenceThenDefault() {
        assertEquals(CursorTrailStyle.RAILGUN, CursorTrailStyle.effective("railgun", "comet"));
        assertEquals(CursorTrailStyle.DEFAULT, CursorTrailStyle.effective("default", "comet"));
        assertEquals(CursorTrailStyle.COMET, CursorTrailStyle.effective(null, "comet"));
        assertEquals(CursorTrailStyle.DEFAULT, CursorTrailStyle.effective(null, null));
        assertEquals(CursorTrailStyle.DEFAULT, CursorTrailStyle.effective(null, "bogus"));
    }
}
