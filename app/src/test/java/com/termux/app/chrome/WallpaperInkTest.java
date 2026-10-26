package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.core.graphics.ColorUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Text over the wallpaper follows the wallpaper (editor-redesign §8), per surface: a light
 * wallpaper under a dark theme gives dark ink, a dark wallpaper under a light theme gives pale
 * ink, and a glassed surface keeps the theme's ink.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperInkTest {

    private static final int LIGHT_WALLPAPER = 0xFFF0EEE8;
    private static final int DARK_WALLPAPER = 0xFF15181C;
    private static final int MID_WALLPAPER = 0xFF808080;
    /** The dark theme's glass base and its on-surface, the extra keys' theme ink there. */
    private static final int NIGHT_BASE = 0xFF1C1B1F;
    private static final int NIGHT_ON_SURFACE = 0xFFE6E1E5;
    /** The light theme's glass base and on-surface. */
    private static final int LIGHT_BASE = 0xFFF7F2FA;
    private static final int LIGHT_ON_SURFACE = 0xFF1D1B20;
    private static final int DARK_INK = 0xFF1F1F1F;
    private static final double BODY = OnGlass.TARGET_BODY_TEXT;

    private static boolean pale(int color) {
        return ColorUtils.calculateLuminance(OnGlass.opaque(color)) > 0.5d;
    }

    // ------------------------------------------------------------------ the extra keys

    @Test
    public void extraKeysGlassOffOnALightWallpaperUnderADarkThemeAreDark() {
        int face = WallpaperInk.faceOf(LIGHT_WALLPAPER, Color.TRANSPARENT, NIGHT_BASE, 0f);
        assertEquals("glass off: the face is the wallpaper", LIGHT_WALLPAPER, face);
        int keys = WallpaperInk.stripInk(face, NIGHT_ON_SURFACE, BODY);
        assertFalse(pale(keys));
        assertTrue(OnGlass.ratio(keys, face) >= BODY);
    }

    @Test
    public void extraKeysGlassOffOnADarkWallpaperUnderALightThemeArePale() {
        int face = WallpaperInk.faceOf(DARK_WALLPAPER, Color.TRANSPARENT, LIGHT_BASE, 0f);
        int keys = WallpaperInk.stripInk(face, LIGHT_ON_SURFACE, BODY);
        // The nearest tone of the theme's ink that reads: lighter than the face, at AA.
        assertTrue(GlassInk.isPaleSide(keys, face));
        assertTrue(OnGlass.ratio(keys, face) >= BODY);
    }

    @Test
    public void extraKeysOnVeryThinGlassStillFollowTheWallpaper() {
        int face = WallpaperInk.faceOf(LIGHT_WALLPAPER, Color.TRANSPARENT, NIGHT_BASE, 0.1f);
        assertTrue("a tenth of dark glass over a light wallpaper is still a light face", pale(face));
        assertFalse(pale(WallpaperInk.stripInk(face, NIGHT_ON_SURFACE, BODY)));
    }

    // ------------------------------------------------------------------ the dock's tiles

    /** The folders' shell wash the Drawer and palette tiles wear: 15% white. */
    private static final int SHELL = 0x26FFFFFF;
    private static final double LARGE = OnGlass.TARGET_LARGE_TEXT;

    @Test
    public void aTilesGlyphOnAPaleDockUnderADarkThemeIsDark() {
        // The checker's case: a clear dock over a light wallpaper, the dark theme's pale ink.
        int dock = WallpaperInk.faceOf(LIGHT_WALLPAPER, Color.TRANSPARENT, NIGHT_BASE, 0.1f);
        int glyph = WallpaperInk.tileInk(dock, SHELL, NIGHT_ON_SURFACE, LARGE);
        assertFalse("pale on pale was the bug", pale(glyph));
        int shellFace = OnGlass.opaque(OnGlass.composite(SHELL, dock));
        assertTrue(OnGlass.ratio(glyph, shellFace) >= LARGE);
    }

    @Test
    public void aTilesGlyphOnTheThemesGlassKeepsTheThemesInk() {
        int dock = WallpaperInk.faceOf(LIGHT_WALLPAPER, Color.TRANSPARENT, NIGHT_BASE, 1f);
        assertEquals(NIGHT_ON_SURFACE, WallpaperInk.tileInk(dock, SHELL, NIGHT_ON_SURFACE, LARGE));
    }

    @Test
    public void aTilesGlyphOnADarkDockUnderALightThemeIsPale() {
        int dock = WallpaperInk.faceOf(DARK_WALLPAPER, Color.TRANSPARENT, LIGHT_BASE, 0f);
        int shellFace = OnGlass.opaque(OnGlass.composite(SHELL, dock));
        int glyph = WallpaperInk.tileInk(dock, SHELL, LIGHT_ON_SURFACE, LARGE);
        assertTrue(GlassInk.isPaleSide(glyph, shellFace));
        assertTrue(OnGlass.ratio(glyph, shellFace) >= LARGE);
    }

    @Test
    public void beforeTheDockIsMeasuredATileKeepsTheThemesInk() {
        assertEquals(NIGHT_ON_SURFACE,
            WallpaperInk.tileInk(Color.TRANSPARENT, SHELL, NIGHT_ON_SURFACE, LARGE));
    }

    @Test
    public void extraKeysOnTheThemesGlassKeepTheThemesInk() {
        int night = WallpaperInk.faceOf(LIGHT_WALLPAPER, Color.TRANSPARENT, NIGHT_BASE, 1f);
        assertEquals(NIGHT_ON_SURFACE, WallpaperInk.stripInk(night, NIGHT_ON_SURFACE, BODY));
        int day = WallpaperInk.faceOf(DARK_WALLPAPER, Color.TRANSPARENT, LIGHT_BASE, 1f);
        assertEquals(LIGHT_ON_SURFACE, WallpaperInk.stripInk(day, LIGHT_ON_SURFACE, BODY));
    }

    @Test
    public void theDimDarkensTheFace() {
        int dimmed = WallpaperInk.faceOf(LIGHT_WALLPAPER, 0xCC000000, NIGHT_BASE, 0f);
        assertFalse("a heavy dim over a light wallpaper is a dark face", pale(dimmed));
        int keys = WallpaperInk.stripInk(dimmed, LIGHT_ON_SURFACE, BODY);
        assertTrue(GlassInk.isPaleSide(keys, dimmed));
        assertTrue(OnGlass.ratio(keys, dimmed) >= BODY);
    }

    // ------------------------------------------------------------------ Home's labels

    @Test
    public void homeLabelsFollowTheWallpaperNotTheTheme() {
        assertEquals(DARK_INK, WallpaperInk.bareInk(LIGHT_WALLPAPER, DARK_INK, Color.WHITE));
        assertEquals(Color.WHITE, WallpaperInk.bareInk(DARK_WALLPAPER, DARK_INK, Color.WHITE));
    }

    @Test
    public void theLabelShadowFallsAwayFromTheInk() {
        int underPale = WallpaperInk.labelShadow(Color.WHITE, DARK_WALLPAPER);
        assertFalse("a dark shadow under white writing", pale(underPale));
        int underDark = WallpaperInk.labelShadow(DARK_INK, LIGHT_WALLPAPER);
        assertTrue("a pale glow under dark writing", pale(underDark));
    }

    @Test
    public void aMidToneWallpaperGetsMoreShadow() {
        int midInk = WallpaperInk.bareInk(MID_WALLPAPER, DARK_INK, Color.WHITE);
        int mid = Color.alpha(WallpaperInk.labelShadow(midInk, MID_WALLPAPER));
        int light = Color.alpha(WallpaperInk.labelShadow(DARK_INK, LIGHT_WALLPAPER));
        assertTrue("mid " + mid + " over light " + light, mid > light);
        assertTrue(light >= GlassInk.HALO_ALPHA_FLOOR);
    }

    // ------------------------------------------------------------------ before a sample

    @Test
    public void noDescriptionKeepsTheNominalGlass() {
        assertEquals(Color.TRANSPARENT, WallpaperInk.hintColor(Color.TRANSPARENT, true));
        assertEquals(NIGHT_BASE, WallpaperInk.fallbackWallpaper(NIGHT_BASE, Color.TRANSPARENT));
    }

    @Test
    public void theHintSettlesTheSide() {
        assertEquals(LIGHT_WALLPAPER, WallpaperInk.hintColor(LIGHT_WALLPAPER, true));
        assertEquals(DARK_WALLPAPER, WallpaperInk.hintColor(DARK_WALLPAPER, false));
        assertEquals("a dark primary on a picture the system calls light",
            WallpaperInk.LIGHT_STAND_IN, WallpaperInk.hintColor(DARK_WALLPAPER, true));
        assertEquals(WallpaperInk.DARK_STAND_IN, WallpaperInk.hintColor(LIGHT_WALLPAPER, false));
    }

    /**
     * The reported case end to end: a dark theme, the wallpaper unreadable, the system calling it
     * light. Before, the face fell back to the dark glass and the extra keys kept their pale ink.
     */
    @Test
    public void anUnreadableLightWallpaperUnderADarkThemeStillGivesDarkKeys() {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = NIGHT_BASE;
        ChromeInk ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(rect -> GlassBackdropCache.UNREADABLE);
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 0f);
        Rect keysRect = new Rect(0, 2060, 1080, 2160);

        int before = ink.faceUnder(GlassBackdropCache.Band.DOCK, keysRect);
        assertFalse("no description: the theme's glass stands in", pale(before));

        surfaces.wallpaperHint = WallpaperInk.hintColor(LIGHT_WALLPAPER, true);
        int face = ink.faceUnder(GlassBackdropCache.Band.DOCK, keysRect);
        assertTrue("described light: a light face", pale(face));
        int keys = WallpaperInk.stripInk(face, NIGHT_ON_SURFACE, ink.target(BODY));
        assertFalse(pale(keys));
        assertTrue(OnGlass.ratio(keys, face) >= BODY);
        assertTrue("the page's own sample reads nothing", Color.alpha(
            ink.wallpaperSample(keysRect)) == 0);
        assertTrue("and stands in with the description", pale(ink.wallpaperFallback()));
    }
}
