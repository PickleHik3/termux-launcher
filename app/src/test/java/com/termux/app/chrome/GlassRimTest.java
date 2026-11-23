package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The rim's numbers, their property and the line's profile and light. */
public class GlassRimTest {

    /** Pong (420 dpi) and an mdpi screen. */
    private static final float PONG = 2.625f;
    private static final float MDPI = 1f;

    @Test
    public void theDefaultsAreAHardLineAsTunedOnPong20261123() {
        GlassRim rim = GlassRim.DEFAULT;
        assertEquals(0.5f, rim.lineDp, 0f);
        assertEquals(0.2f, rim.softDp, 0f);
        assertEquals(0f, rim.halo, 0f);
        assertEquals(0.75f, rim.haloDp, 0f);
        assertEquals(0.18f, rim.base, 0f);
        // No key-light white: the glass has no fixed light (the developer, 2026-11-23).
        assertEquals(0f, rim.spec, 0f);
        assertEquals(0.3f, rim.dark, 0f);
        assertEquals(0.35f, rim.darkAll, 0f);
    }

    @Test
    public void unsetOrBlankIsTheDefaults() {
        assertSame(GlassRim.DEFAULT, GlassRim.parse(null));
        assertSame(GlassRim.DEFAULT, GlassRim.parse(""));
        assertSame(GlassRim.DEFAULT, GlassRim.parse("   "));
        assertSame("the defaults spelled out", GlassRim.DEFAULT,
            GlassRim.parse(GlassRim.DEFAULT.toString()));
    }

    @Test
    public void namedKeysOverrideAndTheRestKeepTheirDefaults() {
        GlassRim rim = GlassRim.parse(" line=0.4, SOFT=0.8 ,base=0.12,halo_dp=1.5,dark_all=0.5,");
        assertEquals(0.4f, rim.lineDp, 0f);
        assertEquals(0.8f, rim.softDp, 0f);
        assertEquals(0.12f, rim.base, 0f);
        assertEquals(1.5f, rim.haloDp, 0f);
        assertEquals(0.5f, rim.darkAll, 0f);
        assertEquals(GlassRim.HALO, rim.halo, 0f);
        assertEquals(GlassRim.SPEC, rim.spec, 0f);
        assertEquals(GlassRim.DARK, rim.dark, 0f);
        GlassRim all = GlassRim.parse("line=0,soft=4,halo=0,halo_dp=0.1,base=1,spec=0,dark=1,dark_all=0");
        assertEquals(0f, all.lineDp, 0f);
        assertEquals(4f, all.softDp, 0f);
        assertEquals(1f, all.base, 0f);
        assertEquals(1f, all.dark, 0f);
        // What a parse writes reads back the same.
        assertEquals(rim, GlassRim.parse(rim.toString()));
    }

    @Test
    public void badInputFallsBackToTheDefaults() {
        String[] bad = {"line", "=0.4", "line=", "line=abc", "line=NaN", "line=Infinity",
            "line=-0.1", "line=5", "soft=0", "halo_dp=0", "halo=1.5", "base=-1", "spec=2",
            "dark=1.01", "dark_all=-0.5", "width=1", "line=0.4,bogus=1", "line=0.4;soft=1",
            "line=0.4,soft=x"};
        for (String value : bad) {
            assertSame(value, GlassRim.DEFAULT, GlassRim.parse(value));
        }
    }

    @Test
    public void withNoPropertyReadableTheActiveRimIsTheDefault() {
        // The JVM has no android.os.SystemProperties: the reader returns null.
        assertSame(GlassRim.DEFAULT, GlassRim.active());
    }

    @Test
    public void onPongTheLineIsAFewPxWide() {
        GlassRim rim = GlassRim.DEFAULT;
        assertEquals(1f, rim.weight(0f, PONG), 0f);
        assertEquals(1f, rim.weight(rim.corePx(PONG), PONG), 0f);
        // Whole for about a px and a third, gone by about 2.3 px: the 0.2 dp fade is under the
        // fade's one-px floor.
        assertEquals(0.5f * PONG, rim.corePx(PONG), 1e-6f);
        float end = rim.corePx(PONG) + rim.softPx(PONG);
        assertEquals(2.3125f, end, 0.01f);
        // No halo: past the fade nothing is left.
        assertEquals(rim.halo * (float) Math.exp(-rim.softPx(PONG) / rim.haloPx(PONG)),
            rim.weight(end, PONG), 1e-6f);
        assertTrue(rim.weight(end, PONG) < 0.1f);
        // Visible width: under a thirtieth of the line past 6 px.
        assertTrue(rim.weight(6f, PONG) < 1f / 30f);
        // The old light was still a fifth strong 10 px in; this reaches nothing by 10 px.
        assertTrue(rim.reachPx(PONG) < 10f);
        assertEquals(0f, rim.weight(rim.reachPx(PONG), PONG), 0f);
        assertTrue("under an 8-bit step at the reach",
            rim.weight(rim.reachPx(PONG) - 1e-3f, PONG) <= 1f / 255f + 1e-4f);
        // Never rises inward.
        float last = 2f;
        for (float d = -2f; d < rim.reachPx(PONG) + 2f; d += 0.05f) {
            float w = rim.weight(d, PONG);
            assertTrue("at " + d, w <= last + 1e-6f);
            last = w;
        }
    }

    @Test
    public void onALowDensityScreenTheLineKeepsAPxAndAnAntiAliasedFade() {
        GlassRim rim = GlassRim.DEFAULT;
        assertEquals(GlassRim.MIN_LINE_PX, rim.corePx(MDPI), 0f);
        assertEquals(GlassRim.MIN_SOFT_PX, rim.softPx(MDPI), 0f);
        assertEquals(1f, rim.weight(0.5f, MDPI), 0f);
        assertTrue(rim.weight(1.25f, MDPI) > 0.4f && rim.weight(1.25f, MDPI) < 0.8f);
        assertTrue("a few px at most", rim.weight(3f, MDPI) < 0.1f);
        assertTrue(rim.reachPx(MDPI) < 6f);
    }

    @Test
    public void theBaselineIsEqualOnEverySideAndCorner() {
        GlassRim rim = GlassRim.DEFAULT;
        float least = Float.MAX_VALUE, most = 0f;
        for (int deg = 0; deg < 360; deg++) {
            float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
            float lobe = GlassRefraction.rimLobe(nx, ny);
            float white = rim.white(lobe);
            least = Math.min(least, white);
            most = Math.max(most, white);
            assertTrue("every side keeps the base white at " + deg, white >= rim.base);
            float dark = rim.darkening(GlassRefraction.rimAway(nx, ny));
            assertTrue("every side keeps the base dark at " + deg, dark >= rim.dark * rim.darkAll - 1e-6f);
        }
        assertEquals("bottom right keeps exactly the baseline", rim.base, least, 0f);
        assertEquals("facing the light squarely", rim.base + rim.spec, most, 1e-3f);
        assertEquals("with no key light the white is even all round", least, most, 0f);
        // Top, bottom, left, right and the four corners: each at least the baseline, and the
        // line's width the same (it is one distance field, no side or corner branch).
        float[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-0.7071f, -0.7071f},
            {0.7071f, -0.7071f}, {-0.7071f, 0.7071f}, {0.7071f, 0.7071f}};
        for (float[] n : sides) {
            assertTrue(rim.white(GlassRefraction.rimLobe(n[0], n[1])) >= rim.base);
        }
    }

    @Test
    public void theLineAddsNoColourAndStaysUnderTheAlpha() {
        GlassRim rim = GlassRim.DEFAULT;
        float[] out = new float[3];
        float[][] colours = {{0.8f, 0.1f, 0.7f}, {0.2f, 0.3f, 0.8f}, {1f, 1f, 1f}, {0f, 0f, 0f},
            {0.05f, 0.4f, 0.1f}};
        float white = rim.white(1f), dark = rim.darkening(0f);
        for (float[] c : colours) {
            for (float a : new float[] {1f, 0.5f}) {
                float r = c[0] * a, g = c[1] * a, b = c[2] * a;
                GlassRim.lit(r, g, b, a, 1f, white, dark, out);
                for (int ch = 0; ch < 3; ch++) assertTrue(out[ch] <= a + 1e-6f);
                // No more saturated: the spread between channels never grows.
                float before = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                float after = Math.max(out[0], Math.max(out[1], out[2]))
                    - Math.min(out[0], Math.min(out[1], out[2]));
                assertTrue(after <= before + 1e-5f);
            }
        }
        // Over black the line is a grey: the base white shows.
        GlassRim.lit(0f, 0f, 0f, 1f, 1f, rim.white(0f), rim.darkening(1f), out);
        assertEquals(out[0], out[1], 0f);
        assertEquals(out[1], out[2], 0f);
        assertEquals(GlassRim.fromLinear(rim.base), out[0], 1e-6f);
        // Off the line nothing changes.
        GlassRim.lit(0.3f, 0.2f, 0.1f, 1f, 0f, white, dark, out);
        assertEquals(0.3f, out[0], 0f);
        // The transfer functions round-trip.
        for (int i = 0; i <= 100; i++) {
            float v = i / 100f;
            assertEquals(v, GlassRim.fromLinear(GlassRim.toLinear(v)), 1e-5f);
        }
    }
}
