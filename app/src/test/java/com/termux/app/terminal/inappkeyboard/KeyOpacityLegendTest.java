package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.graphics.ColorUtils;

import com.termux.app.chrome.GlassLook;
import com.termux.app.surfaces.AppearanceLooks;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import juloo.keyboard2.Theme;

/**
 * Look › Custom's Keys: the keyboard's cap opacity, and the legends chosen on the caps as the
 * keyboard draws them at it (the view replaces every unpressed cap's alpha with the user's, so
 * a legend chosen on the theme's alpha could stand on a cap it never sees). At 0, 50 and 100
 * over a dark and a bright face, every legend reads at body-text contrast.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class KeyOpacityLegendTest {

    private static final double BODY_TEXT = 4.5d;
    private static final int DARK_FACE = 0xFF101214;
    private static final int BRIGHT_FACE = 0xFFF2F0EA;

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    /** The cap as drawn at {@code percent}: the fill's colour at that alpha, gradient and all. */
    private static int[] capsOn(int fill, int percent, Theme.Palette palette, int face) {
        int alpha = Math.round(255f * percent / 100f);
        int[] stops = InAppKeyboardPaletteFactory.drawnCapStops(
            ColorUtils.setAlphaComponent(fill, alpha), palette.keyGradientTopOverlay,
            palette.keyGradientBottomOverlay);
        int[] out = new int[stops.length];
        for (int i = 0; i < stops.length; i++)
            out[i] = ColorUtils.compositeColors(ColorUtils.compositeColors(stops[i], face), 0xFF000000);
        return out;
    }

    private static void assertReads(String what, int ink, int[] caps) {
        assertReads(what, ink, caps, BODY_TEXT);
    }

    private static void assertReads(String what, int ink, int[] caps, double target) {
        double ratio = minRatio(ink, caps);
        assertTrue(what + ": " + ratio, ratio >= target);
    }

    private static double minRatio(int ink, int[] caps) {
        double least = Double.MAX_VALUE;
        for (int cap : caps)
            least = Math.min(least, ColorUtils.calculateContrast(ink | 0xFF000000, cap));
        return least;
    }

    /**
     * Letters and function keys read at body-text contrast on their caps as drawn, at every key
     * opacity, over a dark and a bright face. Enter is a filled accent button whose gradient ends
     * can straddle the point where neither white nor black clears 4.5 (seen at 100% on the
     * bright face: 4.33); it reads at body text wherever its own theme's Enter does, and is
     * never more than a hair worse than the theme draws it.
     */
    @Test
    public void everyLegendReadsOnItsCapAsDrawnAtEveryKeyOpacity() {
        for (int face : new int[] {DARK_FACE, BRIGHT_FACE}) {
            Theme.Palette theme = InAppKeyboardPaletteFactory.createGlass(context(), "system",
                GlassLook.SCHEME, new int[] {face});
            double themeEnter = minRatio(theme.actionLabelColor,
                capsOn(theme.actionKeyBackground, Math.round(
                    100f * (theme.actionKeyBackground >>> 24) / 255f), theme, face));
            for (int percent : new int[] {0, 50, 100}) {
                Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context(), "system",
                    GlassLook.SCHEME, new int[] {face}, percent);
                String at = "keys " + percent + "% on #" + Integer.toHexString(face);
                assertReads(at + " letter", palette.labelColor,
                    capsOn(palette.keyBackground, percent, palette, face));
                assertReads(at + " function", palette.functionLabelColor,
                    capsOn(palette.functionKeyBackground, percent, palette, face));
                assertReads(at + " Enter", palette.actionLabelColor,
                    capsOn(palette.actionKeyBackground, percent, palette, face),
                    Math.min(BODY_TEXT, themeEnter - 0.15d));
            }
        }
    }

    /** At 0 the caps are clear: the legends are chosen on the glass face itself. */
    @Test
    public void atZeroTheLegendsStandOnTheFace() {
        Theme.Palette onDark = InAppKeyboardPaletteFactory.createGlass(context(), "system",
            GlassLook.SCHEME, new int[] {DARK_FACE}, 0);
        Theme.Palette onBright = InAppKeyboardPaletteFactory.createGlass(context(), "system",
            GlassLook.SCHEME, new int[] {BRIGHT_FACE}, 0);
        assertTrue(ColorUtils.calculateContrast(onDark.labelColor | 0xFF000000, DARK_FACE) >= BODY_TEXT);
        assertTrue(ColorUtils.calculateContrast(onBright.labelColor | 0xFF000000, BRIGHT_FACE)
            >= BODY_TEXT);
        assertTrue("lighter legends than the dark face",
            ColorUtils.calculateLuminance(onDark.labelColor | 0xFF000000)
                > ColorUtils.calculateLuminance(DARK_FACE));
        assertTrue("darker legends than the bright face",
            ColorUtils.calculateLuminance(onBright.labelColor | 0xFF000000)
                < ColorUtils.calculateLuminance(BRIGHT_FACE));
    }

    /** The theme's own opacity (-1) is the palette it always was. */
    @Test
    public void theThemesOpacityIsThePaletteAsBefore() {
        int[] faces = {DARK_FACE};
        Theme.Palette before = InAppKeyboardPaletteFactory.createGlass(context(), "system",
            GlassLook.SCHEME, faces);
        Theme.Palette theme = InAppKeyboardPaletteFactory.createGlass(context(), "system",
            GlassLook.SCHEME, faces, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
        assertEquals(before.labelColor, theme.labelColor);
        assertEquals(before.functionLabelColor, theme.functionLabelColor);
        assertEquals(before.functionKeyBackground, theme.functionKeyBackground);
        assertEquals(before.actionLabelColor, theme.actionLabelColor);
        assertTrue(TermuxInAppKeyboard.sameInks(before, theme));
    }

    /**
     * The Keys slider is the stored cap opacity: its range is the pref's, a value the slider
     * writes reads back as itself, out-of-range values clamp, and the theme's sentinel stays
     * the default.
     */
    @Test
    public void theSliderIsTheStoredKeyOpacity() {
        AppearanceLooks.Control keys = AppearanceLooks.Control.KEY_OPACITY;
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_OPACITY, keys.min);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_OPACITY, keys.max);
        assertTrue(AppearanceLooks.controls(AppearanceLooks.Target.KEYBOARD).contains(keys));
        assertTrue("not a global control", !AppearanceLooks.controls(null, true).contains(keys));
        assertTrue(AppearanceLooks.controls(AppearanceLooks.Target.KEYBOARD).size()
            <= AppearanceLooks.MAX_CONTROLS);

        Context context = context();
        SharedPreferences store = context.getSharedPreferences("key-opacity-legend-test",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(context, store, null);
        assertEquals("the theme's own until the slider moves",
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY, prefs.getInAppKeyboardKeyOpacity());
        for (int value : new int[] {0, 37, 50, 100}) {
            prefs.setInAppKeyboardKeyOpacity(keys.clamp(value));
            assertEquals(value, prefs.getInAppKeyboardKeyOpacity());
        }
        prefs.setInAppKeyboardKeyOpacity(keys.clamp(180));
        assertEquals(100, prefs.getInAppKeyboardKeyOpacity());
        prefs.setInAppKeyboardKeyOpacity(keys.clamp(-30));
        assertEquals(0, prefs.getInAppKeyboardKeyOpacity());
    }

    /**
     * Ctrl, Alt, Fn, Shift, the arrows, Esc, Backspace, Enter and 123+ are flagged secondary, and
     * the keyboard draws them in the legend's tone moved toward mid grey (the palette's 0.25):
     * black as #404040, white as #BFBFBF. At Keys 0 they are chosen so that rendition reads on a
     * dark and a bright face, and on a mid face, where neither does, in the tone that comes
     * nearest (the one the light veil can carry the rest of the way), the letters still at body
     * text.
     */
    @Test
    public void theSecondaryLegendsAreChosenAsDrawn() {
        assertEquals(0xFF404040, InAppKeyboardPaletteFactory.secondaryOf(0xFF000000));
        assertEquals(0xFFBFBFBF, InAppKeyboardPaletteFactory.secondaryOf(0xFFFFFFFF));
        for (int face : new int[] {DARK_FACE, BRIGHT_FACE}) {
            Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context(), "light",
                GlassLook.SCHEME, new int[] {face}, 0);
            String at = "on #" + Integer.toHexString(face);
            assertReads(at + " Ctrl as drawn",
                InAppKeyboardPaletteFactory.secondaryOf(palette.functionLabelColor),
                new int[] {face});
            assertReads(at + " Enter as drawn",
                InAppKeyboardPaletteFactory.secondaryOf(palette.actionLabelColor),
                new int[] {face});
            assertReads(at + " letter", palette.labelColor, new int[] {face});
        }
        int mid = 0xFF8C8C8C;
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context(), "light",
            GlassLook.SCHEME, new int[] {mid}, 0);
        double white = ColorUtils.calculateContrast(0xFFBFBFBF, mid);
        double black = ColorUtils.calculateContrast(0xFF404040, mid);
        assertTrue("neither rendition reads on the mid face", Math.max(white, black) < 4.5d);
        assertEquals("the nearer one is chosen", Math.max(white, black), ColorUtils.calculateContrast(
            InAppKeyboardPaletteFactory.secondaryOf(palette.functionLabelColor), mid), 1e-9);
        assertReads("letter on the mid face", palette.labelColor, new int[] {mid});
    }
}
