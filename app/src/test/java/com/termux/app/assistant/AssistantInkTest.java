package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The Assistant page's ink, by the legibility contract (ADR 0014), for what is beneath it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AssistantInkTest {

    /** A light theme's roles, roughly Material's baseline. */
    private static AssistantUi.Palette light() {
        AssistantUi.Palette p = new AssistantUi.Palette();
        p.surface = 0xFFFBF8FD;
        p.surfaceHigh = 0xFFEAE7EC;
        p.surfaceHighest = 0xFFE4E1E6;
        p.onSurface = 0xFF1B1B1F;
        p.onSurfaceVariant = 0xFF44474F;
        p.primary = 0xFF3E5F90;
        p.onPrimary = 0xFFFFFFFF;
        p.primaryContainer = 0xFFD6E3FF;
        p.onPrimaryContainer = 0xFF001B3E;
        p.secondaryContainer = 0xFFDAE2F9;
        p.onSecondaryContainer = 0xFF131C2B;
        p.outline = 0xFF74777F;
        p.outlineVariant = 0xFFC4C6CF;
        p.error = 0xFFBA1A1A;
        p.errorContainer = 0xFFFFDAD6;
        p.onErrorContainer = 0xFF410002;
        p.success = 0xFF2E7D32;
        return p;
    }

    /** The emulator's case: a light theme's glass, nearly clear, over a dark wallpaper. */
    private static final int DARK_WALLPAPER = 0xFF15171C;
    private static final int LIGHT_WALLPAPER = 0xFFECEFF3;
    /** The Base Look's 34% glass in the light theme's surfaceContainer. */
    private static final int LIGHT_GLASS = OnGlass.withAlpha(0xFFEFEDF1, 87);

    @Test
    public void paleInkPolarityPutsALightThemeOnTheDarkSide() {
        assertTrue(AssistantInk.darkSide(light(), ChromeInk.Polarity.PALE_INK));
        assertFalse(AssistantInk.darkSide(light(), ChromeInk.Polarity.DARK_INK));
        assertFalse(AssistantInk.darkSide(light(), null));
    }

    @Test
    public void aNightThemeIsAlwaysOnTheDarkSideAndKeptAsItIs() {
        AssistantUi.Palette night = AssistantInk.onSide(light(), true);
        assertTrue(AssistantInk.isNight(night));
        assertTrue(AssistantInk.darkSide(night, ChromeInk.Polarity.DARK_INK));
        assertSame(night, AssistantInk.onSide(night, true));
    }

    @Test
    public void theLightSideKeepsTheThemesRoles() {
        AssistantUi.Palette theme = light();
        assertSame(theme, AssistantInk.onSide(theme, false));
    }

    @Test
    public void theDarkSideTurnsThePanesInkPaleAndKeepsEveryPairReadable() {
        AssistantUi.Palette dark = AssistantInk.onSide(light(), true);
        assertTrue(OnGlass.ratio(dark.onSurface, OnGlass.opaque(dark.surfaceHigh)) >= 4.5);
        assertTrue(OnGlass.ratio(dark.onPrimaryContainer, dark.primaryContainer) >= 4.5);
        assertTrue(OnGlass.ratio(dark.onSurface, dark.surfaceHighest) >= 4.5);
        assertEquals(light().success, dark.success);
    }

    @Test
    public void overADarkWallpaperTheDarkSidesInkReads() {
        AssistantUi.Palette dark = AssistantInk.onSide(light(), true);
        OnGlass.Resolution answer = AssistantInk.resolve(DARK_WALLPAPER, LIGHT_GLASS, dark,
            OnGlass.TARGET_BODY_TEXT, OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.ratio(dark.onSurfaceVariant, answer.surface)
            >= OnGlass.TARGET_BODY_TEXT - 0.01);
        assertTrue(OnGlass.ratio(dark.onSurface, answer.surface)
            >= OnGlass.TARGET_BODY_TEXT - 0.01);
    }

    @Test
    public void theThemesDarkInkOverADarkWallpaperIsHeldByAVeil() {
        // Were the light side worn there anyway, the veil, not the ink, makes it read.
        AssistantUi.Palette theme = light();
        OnGlass.Resolution answer = AssistantInk.resolve(DARK_WALLPAPER, LIGHT_GLASS, theme,
            OnGlass.TARGET_BODY_TEXT, OnGlass.TARGET_LARGE_TEXT);
        assertTrue(Color.alpha(answer.veil) > 0);
        assertTrue(OnGlass.ratio(theme.onSurfaceVariant, answer.surface)
            >= OnGlass.TARGET_BODY_TEXT - 0.01);
    }

    @Test
    public void overALightWallpaperTheLightSideNeedsLittleOrNoVeil() {
        AssistantUi.Palette theme = light();
        OnGlass.Resolution answer = AssistantInk.resolve(LIGHT_WALLPAPER, LIGHT_GLASS, theme,
            OnGlass.TARGET_BODY_TEXT, OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.ratio(theme.onSurfaceVariant, answer.surface)
            >= OnGlass.TARGET_BODY_TEXT - 0.01);
        assertTrue(Color.alpha(answer.veil) < 128);
    }

    @Test
    public void samePalettesCompareByValue() {
        assertTrue(AssistantInk.same(light(), light()));
        AssistantUi.Palette other = light();
        other.primary = 0xFF000000;
        assertFalse(AssistantInk.same(light(), other));
    }
}
