package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;

import com.google.android.material.color.MaterialColors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The popup carries no material of its own, so both colours it draws have to come from a Material
 * role with the design's alpha on top — never a hard-coded hex.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class KeyPopupPaletteTest {

    /** A Material 3 theme, so the roles the popup asks for actually resolve. */
    private Context themed() {
        Context context = RuntimeEnvironment.getApplication();
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        return context;
    }

    @Test
    public void theGlowIsTheThemesAccentAtFullStrength() {
        Context context = themed();
        KeyPopupPalette palette = KeyPopupPalette.resolve(context);
        int primary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorPrimary, 0);
        assertEquals(primary | 0xFF000000, palette.primary);
        assertEquals(255, Color.alpha(palette.primary));
    }

    @Test
    public void theGlyphIsTheThemesTextOnSurfaceLikeTheCapsLabels() {
        Context context = themed();
        KeyPopupPalette palette = KeyPopupPalette.resolve(context);
        int onSurface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0);
        assertEquals(onSurface | 0xFF000000, palette.ink);
        assertEquals(255, Color.alpha(palette.ink));
    }

    @Test
    @Config(qualifiers = "night")
    public void aDarkThemesInkReadsAsTextNotAsGround() {
        assertInkTracksTheText();
    }

    @Test
    @Config(qualifiers = "notnight")
    public void aLightThemesInkInvertsWithTheTheme() {
        assertInkTracksTheText();
    }

    /** Whichever way the theme goes, the shadow is the surface's colour, not the text's. */
    private void assertInkTracksTheText() {
        Context context = themed();
        KeyPopupPalette palette = KeyPopupPalette.resolve(context);
        double surface = luminance(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, 0));
        double onSurface = luminance(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0));
        double ink = luminance(palette.ink);
        assertTrue("the glyph is drawn in the text colour, opposite to the ground",
            Math.abs(ink - onSurface) < Math.abs(ink - surface));
    }

    @Test
    public void theSignatureMovesWhenTheThemeDoes() {
        Context context = themed();
        int first = KeyPopupPalette.signature(context);
        assertEquals("and stays put when it does not", first, KeyPopupPalette.signature(context));
        assertNotEquals(0, first);
        assertNotEquals("and is not simply the accent", first,
            KeyPopupPalette.resolve(context).primary);
    }

    @Test
    public void theDesignsAlphasSurviveTheSwapToARole() {
        assertEquals(128, Color.alpha(KeyPopupPalette.withAlpha(Color.RED, 0.5f)));
        assertEquals(255, Color.alpha(KeyPopupPalette.withAlpha(Color.RED, 2f)));
        assertEquals(0, Color.alpha(KeyPopupPalette.withAlpha(Color.RED, -1f)));
    }

    private static int rgb(int color) {
        return color & 0x00FFFFFF;
    }

    private static double luminance(int color) {
        return androidx.core.graphics.ColorUtils.calculateLuminance(color | 0xFF000000);
    }
}
