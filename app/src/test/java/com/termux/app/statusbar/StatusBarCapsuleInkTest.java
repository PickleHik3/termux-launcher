package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.chrome.ChromeInk.Polarity;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.terminal.FloatTitlebarPolicy;
import com.termux.app.terminal.WindowChipInk;
import com.termux.app.theme.SchemeTone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The status bar capsule's ink and outline: the Home float titlebar's rule, and a resting window
 * chip's outline, on the chrome's polarity.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarCapsuleInkTest {

    private static final int LIGHT_ON_SURFACE = 0xFF171C24;
    private static final int LIGHT_SURFACE_BASE = 0xFFF4F6FB;
    private static final int DARK_ON_SURFACE = 0xFFE8EDF7;
    private static final int DARK_SURFACE_BASE = 0xFF0F1218;
    private static final int PANEL = 0xFFEAEFF7;
    /** A dark wallpaper's band under the capsule, as the light theme meets it on Home. */
    private static final int DARK_BAND = 0xFF1B2028;
    private static final int LIGHT_BAND = 0xFFD8DEE8;

    @Test
    public void onGlass_theLightThemeOverADarkWallpaperDrawsPaleInkNotNearBlack() {
        int ink = StatusBarFormPolicy.capsuleInk(true, Polarity.PALE_INK, LIGHT_ON_SURFACE,
            LIGHT_SURFACE_BASE, 0x33F4F6FB);
        assertEquals(LIGHT_SURFACE_BASE, ink);
        assertTrue("pale ink reads on the dark band",
            OnGlass.ratio(ink, DARK_BAND) >= OnGlass.TARGET_BODY_TEXT);
        assertEquals(LIGHT_ON_SURFACE, StatusBarFormPolicy.capsuleInk(true, Polarity.DARK_INK,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, 0x33F4F6FB));
        assertEquals(DARK_ON_SURFACE, StatusBarFormPolicy.capsuleInk(true, Polarity.PALE_INK,
            DARK_ON_SURFACE, DARK_SURFACE_BASE, 0x33101418));
        assertEquals("the dark theme over a light wallpaper", DARK_SURFACE_BASE,
            StatusBarFormPolicy.capsuleInk(true, Polarity.DARK_INK, DARK_ON_SURFACE,
                DARK_SURFACE_BASE, 0x33101418));
    }

    @Test
    public void theInkIsTheHomeFloatTitlebarsRule() {
        Polarity[] polarities = {Polarity.PALE_INK, Polarity.DARK_INK, null};
        for (boolean glass : new boolean[]{true, false}) {
            for (Polarity polarity : polarities) {
                assertEquals(FloatTitlebarPolicy.glyphInk(glass, polarity, LIGHT_ON_SURFACE,
                        LIGHT_SURFACE_BASE, PANEL, true),
                    StatusBarFormPolicy.capsuleInk(glass, polarity, LIGHT_ON_SURFACE,
                        LIGHT_SURFACE_BASE, PANEL));
            }
        }
    }

    @Test
    public void onThePanelOrUnmeasured_theInkIsLegibleOnTheSurface() {
        int legible = GlassInk.legible(PANEL, LIGHT_ON_SURFACE, OnGlass.TARGET_LARGE_TEXT, 255);
        assertEquals(legible, StatusBarFormPolicy.capsuleInk(false, Polarity.PALE_INK,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, PANEL));
        assertEquals(legible, StatusBarFormPolicy.capsuleInk(true, null,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, PANEL));
    }

    @Test
    public void onGlass_theOutlineIsARestingWindowChipsOutline() {
        int accent = 0xFF3D5A98;
        int paleStroke = StatusBarFormPolicy.capsuleStroke(true, Polarity.PALE_INK,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, DARK_BAND, 0);
        assertEquals(WindowChipInk.resolve(DARK_BAND, true,
            WindowChipInk.neutralSeed(LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, true), accent)
            .restingStroke, paleStroke);
        assertTrue("pale on a dark band",
            SchemeTone.tone(paleStroke) > SchemeTone.tone(DARK_BAND));
        assertTrue(OnGlass.ratio(paleStroke, DARK_BAND) >= OnGlass.TARGET_LARGE_TEXT);

        int darkStroke = StatusBarFormPolicy.capsuleStroke(true, Polarity.DARK_INK,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_BAND, 0);
        assertEquals(WindowChipInk.resolve(LIGHT_BAND, false,
            WindowChipInk.neutralSeed(LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, false), accent)
            .restingStroke, darkStroke);
        assertTrue("dark on a light band",
            SchemeTone.tone(darkStroke) < SchemeTone.tone(LIGHT_BAND));
    }

    @Test
    public void onThePanelOrUnmeasured_theOutlineIsTheInkAtAnIdleRimsAlpha() {
        int ink = 0xFF171C24;
        int expected = OnGlass.withAlpha(ink, StatusBarFormPolicy.PLAIN_STROKE_ALPHA);
        assertEquals(expected, StatusBarFormPolicy.capsuleStroke(false, Polarity.PALE_INK,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, PANEL, ink));
        assertEquals(expected, StatusBarFormPolicy.capsuleStroke(true, null,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, PANEL, ink));
    }
}
