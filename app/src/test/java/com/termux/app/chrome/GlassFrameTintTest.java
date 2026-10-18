package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The Tint amount behind see-through glass: a hue shift baked into the shared frame, held to the
 * pixel's luminance and capped, none at Tint 0 or where the face carries the accent alone, and
 * plainly visible at Tint 100 on Clear.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassFrameTintTest {

    /** M3 baseline purple: dark and light glass base, container and role. */
    private static final int DARK_BASE = 0xFF141218;
    private static final int DARK_CONTAINER = 0xFF4F378B;
    private static final int DARK_ACCENT = 0xFFD0BCFF;
    private static final int LIGHT_BASE = 0xFFFEF7FF;
    private static final int LIGHT_CONTAINER = 0xFFEADDFF;
    private static final int LIGHT_ACCENT = 0xFF6750A4;
    private static final int[][] THEMES = {
        {DARK_BASE, DARK_CONTAINER, DARK_ACCENT},
        {LIGHT_BASE, LIGHT_CONTAINER, LIGHT_ACCENT},
    };
    /** Clear's opacity. */
    private static final int CLEAR = 10;

    @Test
    public void tintZeroAndTheTintLookBakeNothing() {
        assertEquals(GlassFrameTint.NONE, GlassFrameTint.of(0, CLEAR, DARK_CONTAINER, DARK_ACCENT));
        assertEquals("the Tint Look at its opacity: the face carries it", GlassFrameTint.NONE,
            GlassFrameTint.of(GlassLook.TINT_LOOK_PERCENT, 46, DARK_CONTAINER, DARK_ACCENT));
        assertEquals(GlassFrameTint.NONE,
            GlassFrameTint.of(100, GlassFrameTint.FACE_CARRIES_OPACITY, DARK_CONTAINER, DARK_ACCENT));
        assertEquals("a grey accent has no hue to give", GlassFrameTint.NONE,
            GlassFrameTint.of(100, 0, 0xFF808080, 0xFF404040));
        GlassLook tintLook = GlassLook.SCHEME.withAmountPercent(GlassLook.TINT_LOOK_PERCENT)
            .withAccentColors(DARK_CONTAINER, DARK_ACCENT);
        assertEquals(GlassFrameTint.NONE, tintLook.frameTint(46));
        assertEquals(GlassFrameTint.NONE, GlassLook.SCHEME.withAccentColors(DARK_CONTAINER,
            DARK_ACCENT).frameTint(CLEAR));
        assertEquals("unresolved, nothing", GlassFrameTint.NONE,
            GlassLook.SCHEME.withAmountPercent(100).frameTint(CLEAR));
        int tint = GlassLook.SCHEME.withAmountPercent(100)
            .withAccentColors(DARK_CONTAINER, DARK_ACCENT).frameTint(CLEAR);
        assertNotEquals(GlassFrameTint.NONE, tint);
        assertFalse("never the bare wallpaper", GlassFrameTint.bakes(0, tint));
        assertTrue(GlassFrameTint.bakes(7, tint));
        assertEquals(0x80123456, GlassFrameTint.apply(GlassFrameTint.NONE, 0x80123456));
    }

    @Test
    public void theStrengthGrowsWithTheAmountAndFallsAsTheFaceGrowsOpaque() {
        assertEquals(GlassFrameTint.MAX_STRENGTH, GlassFrameTint.strength(100, 0), 1e-6f);
        float previous = 0f;
        for (int amount = 10; amount <= 100; amount += 10) {
            float s = GlassFrameTint.strength(amount, CLEAR);
            assertTrue(amount + "", s > previous);
            previous = s;
        }
        previous = Float.MAX_VALUE;
        for (int opacity = 0; opacity < GlassFrameTint.FACE_CARRIES_OPACITY; opacity += 5) {
            float s = GlassFrameTint.strength(100, opacity);
            assertTrue(opacity + "", s < previous);
            previous = s;
        }
        assertEquals(0f, GlassFrameTint.strength(100, 92), 0f);
        assertEquals("clamped", GlassFrameTint.strength(100, 0), GlassFrameTint.strength(400, -9), 0f);
    }

    /** No milk: the frame's luminance holds, and only a clipped channel can move it, downward. */
    @Test
    public void theLuminanceHoldsAtTheFullStrength() {
        for (int[] theme : THEMES) {
            int tint = GlassFrameTint.of(100, 0, theme[1], theme[2]);
            assertEquals(GlassFrameTint.MAX_STRENGTH, GlassFrameTint.strengthOf(tint), 1f / 255f);
            for (int r = 0; r <= 255; r += 51)
                for (int g = 0; g <= 255; g += 51)
                    for (int b = 0; b <= 255; b += 51) {
                        int in = 0xFF000000 | (r << 16) | (g << 8) | b;
                        int out = GlassFrameTint.apply(tint, in);
                        float before = GlassFrameTint.luma(in);
                        float after = GlassFrameTint.luma(out);
                        assertTrue(Integer.toHexString(in) + ": never lighter, " + before + " -> "
                            + after, after <= before + 1f);
                        if (Math.max(r, Math.max(g, b)) <= 153)
                            assertEquals(Integer.toHexString(in) + " unclipped", before, after, 1f);
                        assertEquals("alpha kept", 0xFF, out >>> 24);
                    }
        }
    }

    /** Never garish: a grey moves at most MAX_STRENGTH x MAX_CHROMA of itself on any channel. */
    @Test
    public void theShiftIsCapped() {
        float cap = GlassFrameTint.MAX_STRENGTH * GlassFrameTint.MAX_CHROMA;
        for (int[] theme : THEMES) {
            int tint = GlassFrameTint.of(100, 0, theme[1], theme[2]);
            for (int grey = 0; grey <= 255; grey += 5) {
                int in = 0xFF000000 | (grey << 16) | (grey << 8) | grey;
                int out = GlassFrameTint.apply(tint, in);
                for (int shift = 16; shift >= 0; shift -= 8)
                    assertTrue(grey + " at " + shift, Math.abs(((out >> shift) & 0xFF) - grey)
                        <= cap * grey + 1f);
            }
        }
    }

    /**
     * The developer's report: Tint 0 against 100 on Clear (opacity 10) was barely perceptible.
     * The face (the tinted base at 10%) over the tinted frame now moves a mid or light grey
     * wallpaper by at least 10 CIE76 units (calc: about 20 dark, 17 light), a dark one by at least
     * 5, with its luma within 8 levels: coloured, not lifted.
     */
    @Test
    public void tintOneHundredOnClearIsPlainlyVisibleAndNotMilky() {
        for (int[] theme : THEMES) {
            GlassLook full = GlassLook.SCHEME.withAmountPercent(100)
                .withAccentColors(theme[1], theme[2]);
            GlassLook none = GlassLook.SCHEME.withAccentColors(theme[1], theme[2]);
            int frameTint = full.frameTint(CLEAR);
            int[] walls = {0xFF404040, 0xFF808080, 0xFFC0C0C0};
            float[] floors = {5f, 10f, 10f};
            for (int i = 0; i < walls.length; i++) {
                int before = composed(none.tintBase(theme[0]), CLEAR, walls[i]);
                int after = composed(full.tintBase(theme[0]), CLEAR,
                    GlassFrameTint.apply(frameTint, walls[i]));
                String at = Integer.toHexString(theme[0]) + " over " + Integer.toHexString(walls[i]);
                float delta = deltaE(before, after);
                assertTrue(at + ": " + delta, delta >= floors[i]);
                assertEquals(at + " luma", GlassFrameTint.luma(before), GlassFrameTint.luma(after), 8f);
            }
        }
    }

    /** Tint 0 draws exactly what it drew: the face is the ink, the frame untouched. */
    @Test
    public void tintZeroComposesExactlyAsBefore() {
        GlassLook none = GlassLook.SCHEME.withAmountPercent(0)
            .withAccentColors(DARK_CONTAINER, DARK_ACCENT);
        assertEquals(DARK_BASE, none.tintBase(DARK_BASE));
        assertEquals(0xFF808080, GlassFrameTint.apply(none.frameTint(CLEAR), 0xFF808080));
    }

    /** The gathered edge's light is held under the tinted sample's headroom: nothing clips. */
    @Test
    public void theGatheredEdgeStillNeverClipsOnATintedFrame() {
        float density = 2.625f;
        float nx = GlassRefraction.KEY_LIGHT_X;
        float ny = GlassRefraction.KEY_LIGHT_Y;
        float[] out = new float[3];
        for (int[] theme : THEMES) {
            int tint = GlassFrameTint.of(100, 0, theme[1], theme[2]);
            for (int color : new int[] {0xFFFFFFFF, 0xFFF0F0F0, 0xFFC0C0C0, 0xFF80C0FF, 0xFFE0E0FF}) {
                int sample = GlassFrameTint.apply(tint, color);
                float r = ((sample >> 16) & 0xFF) / 255f;
                float g = ((sample >> 8) & 0xFF) / 255f;
                float b = (sample & 0xFF) / 255f;
                for (int i = 0; i <= 20; i++) {
                    GlassRefraction.gatheredLit(r, g, b, 1f, i * 0.25f * density, density, nx, ny,
                        out);
                    for (float channel : out)
                        assertTrue(Integer.toHexString(sample) + ": " + channel, channel <= 1f + 1e-6f);
                }
                GlassRefraction.gatheredLit(r * 0.5f, g * 0.5f, b * 0.5f, 0.5f, 0f, density, nx, ny,
                    out);
                for (float channel : out) assertTrue(channel <= 0.5f + 1e-6f);
            }
        }
    }

    /**
     * The cache keys on the frame tint as on Vibrancy: a new one re-bakes every frame, a held one
     * waits for the release (a radius filled meanwhile takes the old tint), and the bare
     * wallpaper is never baked.
     */
    @Test
    public void aNewFrameTintRebakesAndAHeldOneWaits() {
        FakeWallpaperBlurSource source = new FakeWallpaperBlurSource();
        WallpaperBlurCache cache = new WallpaperBlurCache(source);
        View wallpaperFrame = new View(RuntimeEnvironment.getApplication());
        Bitmap plain = cache.obtain(12, wallpaperFrame);
        assertEquals(1, source.captureCount);

        source.frameTint = GlassFrameTint.of(100, CLEAR, DARK_CONTAINER, DARK_ACCENT);
        Bitmap tinted = cache.obtain(12, wallpaperFrame);
        assertNotSame(plain, tinted);
        assertEquals("re-baked", 2, source.captureCount);
        assertSame(tinted, cache.obtain(12, wallpaperFrame));

        cache.holdFrameBakes(true);
        source.frameTint = GlassFrameTint.of(60, CLEAR, DARK_CONTAINER, DARK_ACCENT);
        assertSame("held: the drag's ticks bake nothing", tinted, cache.obtain(12, wallpaperFrame));
        // The radius is held too: a radius with no frame is stood in for by the nearest resident.
        assertSame("held: a new radius bakes nothing either", tinted,
            cache.obtain(30, wallpaperFrame));
        assertEquals("held: nothing captured", 2, source.captureCount);
        assertSame(tinted, cache.obtain(12, wallpaperFrame));

        cache.holdFrameBakes(false);
        assertNotSame("the release bakes once", tinted, cache.obtain(12, wallpaperFrame));
        assertEquals(3, source.captureCount);

        Bitmap wall = cache.obtain(0, wallpaperFrame);
        assertSame("radius 0 is the wallpaper itself",
            source.captured.get(source.captured.size() - 1), wall);
    }

    // ------------------------------------------------------------------------------ helpers

    /** {@code face} at {@code opacityPercent} over the opaque {@code backdrop}. */
    private static int composed(int face, int opacityPercent, int backdrop) {
        float a = opacityPercent / 100f;
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = a * ((face >> shift) & 0xFF) + (1f - a) * ((backdrop >> shift) & 0xFF);
            out |= Math.round(c) << shift;
        }
        return out;
    }

    /** CIE76 between two sRGB colours. */
    private static float deltaE(int one, int two) {
        double[] a = lab(one);
        double[] b = lab(two);
        return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1])
            + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static double[] lab(int color) {
        double r = linear((color >> 16) & 0xFF);
        double g = linear((color >> 8) & 0xFF);
        double b = linear(color & 0xFF);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int channel) {
        double v = channel / 255d;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16d / 116d;
    }
}
