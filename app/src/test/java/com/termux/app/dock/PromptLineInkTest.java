package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.theme.SchemeTone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Prompt line's colours: from the terminal's scheme, each glyph legible on the well (4.5:1
 * with room for the glass), the well darker than the terminal's background, on dark and light
 * schemes and on a scheme whose blue and green would vanish on its own background.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PromptLineInkTest {

    private static void assertLegible(PromptLineInk ink) {
        assertLegibleOn(ink, ink.inkSurface);
    }

    private static void assertLegibleOn(PromptLineInk ink, int surface) {
        assertTrue(SchemeTone.contrastRatio(ink.directory, surface)
            >= PromptLineInk.MIN_TEXT_CONTRAST);
        assertTrue(SchemeTone.contrastRatio(ink.glyph, surface)
            >= PromptLineInk.MIN_TEXT_CONTRAST);
        assertTrue(SchemeTone.contrastRatio(ink.hint, surface)
            >= PromptLineInk.MIN_TEXT_CONTRAST);
    }

    /** The well as it reads over {@code glass}: its colour laid at {@code alpha}. */
    private static int over(PromptLineInk ink, int glass, float alpha) {
        return SchemeTone.blend(glass, ink.well, alpha);
    }

    /** A light well is a translucent dark tint, darker than any glass it lies on, legible there. */
    private static void assertDarkTintOnLightGlass(PromptLineInk ink, int background) {
        assertTrue(ink.tinted);
        assertTrue(SchemeTone.tone(ink.well) < 50d);
        assertTrue(ink.wellAlpha < 0.5f);
        assertTrue(ink.pressedWellAlpha > ink.wellAlpha);
        assertTrue(SchemeTone.tone(ink.inkSurface) < SchemeTone.tone(background));
        // Legible on the dimmest light glass pressed, and on white or the scheme's own ground.
        assertLegible(ink);
        for (int glass : new int[]{0xFFFFFFFF, background | 0xFF000000, 0xFFB8B8B8}) {
            int rest = over(ink, glass, ink.wellAlpha);
            assertTrue(SchemeTone.tone(rest) < SchemeTone.tone(glass));
            assertLegibleOn(ink, rest);
            assertLegibleOn(ink, over(ink, glass, ink.pressedWellAlpha));
        }
    }

    @Test
    public void darkScheme_wellIsUnchanged_aSolidStepBelowTheBackground() {
        int background = 0xFF1E1E2E;
        PromptLineInk ink = PromptLineInk.resolve(background, 0xFFCDD6F4, 0xFF89B4FA, 0xFFA6E3A1);
        assertFalse(ink.tinted);
        assertEquals(SchemeTone.toneShift(background, -PromptLineInk.WELL_TONE_DROP), ink.well);
        assertEquals(PromptLineInk.WELL_ALPHA, ink.wellAlpha, 0f);
        assertEquals(ink.well, ink.inkSurface);
    }

    @Test
    public void lightScheme_wellIsADarkTint_darkerThanTheGlass_andLegible() {
        int background = 0xFFFAFAFA;
        PromptLineInk ink = PromptLineInk.resolve(background, 0xFF383A42, 0xFF4078F2, 0xFF50A14F);
        assertDarkTintOnLightGlass(ink, background);
    }

    @Test
    public void materialYouLight_wellIsADarkTint_andLegible() {
        // A dynamic light scheme: tinted near-white ground, its primary and tertiary as accents.
        int background = 0xFFFEF7FF;
        PromptLineInk ink = PromptLineInk.resolve(background, 0xFF1D1B20, 0xFF65558F, 0xFF7D5260);
        assertDarkTintOnLightGlass(ink, background);
        // A greener dynamic palette, its accents pale.
        int green = 0xFFF8FAF0;
        assertDarkTintOnLightGlass(
            PromptLineInk.resolve(green, 0xFF191D16, 0xFFB7D0FF, 0xFFB1D18A), green);
    }

    @Test
    public void materialYouDark_keepsTheSolidWell_andIsLegible() {
        int background = 0xFF141218;
        PromptLineInk ink = PromptLineInk.resolve(background, 0xFFE6E0E9, 0xFFCFBDFE, 0xFFB1D18A);
        assertFalse(ink.tinted);
        assertLegible(ink);
        assertTrue(SchemeTone.tone(ink.well) < SchemeTone.tone(background));
    }

    @Test
    public void darkScheme_isLegible_onADarkerWell() {
        int background = 0xFF1E1E2E;
        PromptLineInk ink = PromptLineInk.resolve(background, 0xFFCDD6F4, 0xFF89B4FA, 0xFFA6E3A1);
        assertLegible(ink);
        assertTrue(SchemeTone.tone(ink.well) < SchemeTone.tone(background));
        assertEquals(0xFF, ink.well >>> 24);
    }

    @Test
    public void lightScheme_isLegible() {
        PromptLineInk ink = PromptLineInk.resolve(0xFFFAFAFA, 0xFF383A42, 0xFF4078F2, 0xFF50A14F);
        assertLegible(ink);
    }

    @Test
    public void accentsThatVanishOnTheBackground_areMovedUntilLegible() {
        PromptLineInk ink = PromptLineInk.resolve(0xFF202020, 0xFF2A2A2A, 0xFF202040, 0xFF203020);
        assertLegible(ink);
    }

    @Test
    public void legibleAccents_keepTheirOwnColour() {
        int blue = 0xFF89B4FA;
        PromptLineInk ink = PromptLineInk.resolve(0xFF000000, 0xFFFFFFFF, blue, 0xFFA6E3A1);
        assertEquals(blue, ink.directory);
    }

    @Test
    public void hint_recedesFromTheForeground() {
        int foreground = 0xFFCDD6F4;
        PromptLineInk ink = PromptLineInk.resolve(0xFF1E1E2E, foreground, 0xFF89B4FA, 0xFFA6E3A1);
        assertTrue(SchemeTone.contrastRatio(ink.hint, ink.well)
            < SchemeTone.contrastRatio(foreground, ink.well));
    }
}
