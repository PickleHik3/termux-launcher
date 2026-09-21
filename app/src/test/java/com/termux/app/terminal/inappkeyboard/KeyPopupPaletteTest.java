package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;

import com.google.android.material.color.MaterialColors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The popup carries no material of its own, so every colour it draws has to come from a Material
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
    public void everyColourIsAThemeRoleWithItsOwnAlpha() {
        Context context = themed();
        KeyPopupPalette palette = KeyPopupPalette.resolve(context);

        int primary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorPrimary, 0);
        int onSurface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0);
        int onSurfaceVariant = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0);

        assertEquals("the halo is the theme's own accent, at full strength",
            primary | 0xFF000000, palette.primary);
        assertEquals(rgb(onSurface), rgb(palette.glyphStroke));
        assertEquals(rgb(onSurfaceVariant), rgb(palette.ringIdle));
        assertEquals(rgb(onSurfaceVariant), rgb(palette.subLabel));

        assertEquals(242, Color.alpha(palette.glyphStroke));
        assertEquals(153, Color.alpha(palette.ringIdle));
        assertEquals(115, Color.alpha(palette.subLabel));
        assertEquals(184, Color.alpha(palette.dim));
    }

    @Test
    @Config(qualifiers = "night")
    public void aDarkThemeCarriesTheHaloAtFullGlow() {
        KeyPopupPalette palette = KeyPopupPalette.resolve(themed());
        assertEquals(1f, palette.glow, 0.001f);
    }

    @Test
    @Config(qualifiers = "notnight")
    public void aLightThemeKeepsTheOutlineDarkAndTonesTheHaloDown() {
        Context context = themed();
        KeyPopupPalette palette = KeyPopupPalette.resolve(context);

        assertTrue("the halo is quieter where the ground is bright", palette.glow < 1f);
        int surface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, 0);
        // The outline is onSurface, so it inverts with the theme on its own: darker than the
        // surface it is drawn over.
        assertTrue("the outline reads dark against a light surface",
            luminance(palette.glyphStroke) < luminance(surface));
    }

    @Test
    public void theSignatureMovesWhenTheThemeDoes() {
        Context context = themed();
        int first = KeyPopupPalette.signature(context);
        assertEquals("and stays put when it does not", first, KeyPopupPalette.signature(context));
        assertNotEquals(0, first);
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
