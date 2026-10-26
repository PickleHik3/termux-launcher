package com.termux.app.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.material.color.utilities.Hct;

import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class PaneRetroStyleTest {

    @After public void resetMaterialPrimary() {
        PaneRetroStyle.setMaterialPrimary(PaneRetroStyle.BASELINE_PRIMARY);
    }

    @Test public void idsAreUniqueAndRoundTrip() {
        Set<String> ids = new HashSet<>();
        for (PaneRetroStyle s : PaneRetroStyle.values()) {
            assertTrue(s.id(), ids.add(s.id()));
            assertEquals(s, PaneRetroStyle.fromId(s.id()));
        }
        assertEquals(6, ids.size());
        assertTrue(ids.contains("none") && ids.contains("crt") && ids.contains("crt_green")
            && ids.contains("crt_amber") && ids.contains("crt_material") && ids.contains("tft"));
    }

    @Test public void unknownIdsAreNone() {
        assertEquals(PaneRetroStyle.NONE, PaneRetroStyle.fromId(null));
        assertEquals(PaneRetroStyle.NONE, PaneRetroStyle.fromId("nope"));
        assertEquals(PaneRetroStyle.NONE, PaneRetroStyle.fromId("CRT"));
    }

    @Test public void tints() {
        float[] zero = {0f, 0f, 0f, 0f};
        assertArrayEquals(zero, PaneRetroStyle.NONE.tint(), 0f);
        assertArrayEquals(zero, PaneRetroStyle.CRT.tint(), 0f);
        assertArrayEquals(zero, PaneRetroStyle.TFT.tint(), 0f);
        assertArrayEquals(new float[]{0.35f, 1.0f, 0.55f, 0.85f}, PaneRetroStyle.CRT_GREEN.tint(), 0f);
        assertArrayEquals(new float[]{1.0f, 0.68f, 0.25f, 0.85f}, PaneRetroStyle.CRT_AMBER.tint(), 0f);
    }

    @Test public void crtFamily() {
        assertTrue(PaneRetroStyle.CRT.usesCrt());
        assertTrue(PaneRetroStyle.CRT_GREEN.usesCrt());
        assertTrue(PaneRetroStyle.CRT_AMBER.usesCrt());
        assertTrue(PaneRetroStyle.CRT_MATERIAL.usesCrt());
        assertFalse(PaneRetroStyle.TFT.usesCrt());
        assertFalse(PaneRetroStyle.NONE.usesCrt());
    }

    /** Material's phosphor is the theme's primary hue, bright as a phosphor, at Green's strength. */
    @Test public void materialGlowsInTheThemesPrimary() {
        int teal = 0xFF006A6A;
        PaneRetroStyle.setMaterialPrimary(teal);
        float[] tint = PaneRetroStyle.CRT_MATERIAL.tint();
        assertEquals(0.85f, tint[3], 0f);
        int glow = 0xFF000000 | (Math.round(tint[0] * 255) << 16) | (Math.round(tint[1] * 255) << 8)
            | Math.round(tint[2] * 255);
        Hct hct = Hct.fromInt(glow);
        assertEquals("the primary's hue", Hct.fromInt(teal).getHue(), hct.getHue(), 4d);
        assertEquals("a phosphor's tone", 80d, hct.getTone(), 1.5d);
        assertTrue("a colour, not a grey", hct.getChroma() >= 20d);
    }

    /** A theme's deep primary and its pale twin glow alike: the polarity does not move it. */
    @Test public void materialIsTheSameGlowOnALightAndADarkTheme() {
        Hct deep = Hct.from(260d, 50d, 40d);
        Hct pale = Hct.from(260d, 50d, 80d);
        assertArrayEquals(PaneRetroStyle.materialPhosphor(deep.toInt()),
            PaneRetroStyle.materialPhosphor(pale.toInt()), 0.02f);
    }

    /** A near-grey primary is lifted to a colour the scanlines can carry. */
    @Test public void aGreyPrimaryStillGlowsInAColour() {
        float[] tint = PaneRetroStyle.materialPhosphor(0xFF777777);
        float max = Math.max(tint[0], Math.max(tint[1], tint[2]));
        float min = Math.min(tint[0], Math.min(tint[1], tint[2]));
        assertTrue("some colour in it", max - min > 0.1f);
    }

    @Test public void theOtherStylesIgnoreTheMaterialPrimary() {
        PaneRetroStyle.setMaterialPrimary(0xFFB3261E);
        assertArrayEquals(new float[]{0.35f, 1.0f, 0.55f, 0.85f}, PaneRetroStyle.CRT_GREEN.tint(), 0f);
        assertArrayEquals(new float[]{0f, 0f, 0f, 0f}, PaneRetroStyle.CRT.tint(), 0f);
    }
}
