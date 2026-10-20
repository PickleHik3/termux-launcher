package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
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
        assertTrue(SchemeTone.contrastRatio(ink.directory, ink.well)
            >= PromptLineInk.MIN_TEXT_CONTRAST);
        assertTrue(SchemeTone.contrastRatio(ink.glyph, ink.well)
            >= PromptLineInk.MIN_TEXT_CONTRAST);
        assertTrue(SchemeTone.contrastRatio(ink.hint, ink.well)
            >= PromptLineInk.MIN_TEXT_CONTRAST);
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
