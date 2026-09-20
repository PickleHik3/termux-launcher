package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.chrome.OnGlass;
import com.termux.app.terminal.WindowChipInk;
import com.termux.app.theme.SchemeTone;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The Display place's accent, which is the place's whole identity: the lens tile's fill, stroke
 * and glyph, the session badge, the window pills and the window column, the display touchpad's
 * accent and the help overlay's topic colour all take this one colour.
 *
 * <p>It used to be the only chrome colour in {@code colors.xml} with no {@code values-night}
 * counterpart, so the place wore the same pale peach in both modes. That reads 1.46:1 on
 * {@code termux_surface_panel_high} — the opaque panel the touchpad and the help overlay stand on
 * in light mode — which is no contrast at all. It is now authored like every other role: an
 * on-light ink in {@code values/} and its on-dark twin in {@code values-night/}. This file is what
 * stops a later edit to either one quietly putting that back.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PlaceDisplayAccentContrastTest {

    /** The opaque panels the place's accent is drawn on off the glass, light mode. */
    private static final int LIGHT_PANEL_HIGH = 0xFFE1E7F2;
    private static final int LIGHT_SURFACE_BASE = 0xFFF4F6FB;
    /** The same two at night. */
    private static final int NIGHT_PANEL_HIGH = 0xFF202837;
    private static final int NIGHT_SURFACE_BASE = 0xFF0F141B;

    /** The bands the chips and the lens ride on, as measured on the reporting device. */
    private static final int GLASS_LIGHT = 0xFF6A5755;
    private static final int GLASS_NIGHT = 0xFF1B1A17;

    private static final int LIGHT_ON_SURFACE = 0xFF171C24;
    private static final int NIGHT_ON_SURFACE = 0xFFE8EDF7;

    private static int accent() {
        return StatusBarLensView.accentFor(RuntimeEnvironment.getApplication(),
            PaneWallPage.DISPLAY);
    }

    // ------------------------------------------------------------------ the two panels

    /**
     * Light mode: the accent is body text and a meaningful graphic on the opaque panels — the
     * touchpad's label and the help overlay's topic heading are both drawn in it.
     */
    @Test
    public void theLightAccentClearsBodyTextOnTheLightPanels() {
        int accent = accent();
        assertTrue("on termux_surface_panel_high: " + OnGlass.ratio(accent, LIGHT_PANEL_HIGH),
            OnGlass.ratio(accent, LIGHT_PANEL_HIGH) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("on termux_surface_base: " + OnGlass.ratio(accent, LIGHT_SURFACE_BASE),
            OnGlass.ratio(accent, LIGHT_SURFACE_BASE) >= OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    @Config(qualifiers = "night")
    public void theNightAccentClearsBodyTextOnTheNightPanels() {
        int accent = accent();
        assertTrue("on termux_surface_panel_high: " + OnGlass.ratio(accent, NIGHT_PANEL_HIGH),
            OnGlass.ratio(accent, NIGHT_PANEL_HIGH) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("on termux_surface_base: " + OnGlass.ratio(accent, NIGHT_SURFACE_BASE),
            OnGlass.ratio(accent, NIGHT_SURFACE_BASE) >= OnGlass.TARGET_BODY_TEXT);
    }

    // ------------------------------------------------------------------ on the glass

    /**
     * And on the bands, where the same colour is a chip's outline and its watermark. The chrome
     * measures and walks it there, so what is pinned is that the walk lands above the graphics
     * floor without the hue leaving the place.
     */
    @Test
    public void theAccentStillCarriesTheChipsOnEitherBand() {
        assertChipsCarry(accent(), GLASS_LIGHT, LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE);
    }

    @Test
    @Config(qualifiers = "night")
    public void theNightAccentStillCarriesTheChipsOnTheNightBand() {
        assertChipsCarry(accent(), GLASS_NIGHT, NIGHT_ON_SURFACE, NIGHT_SURFACE_BASE);
    }

    private static void assertChipsCarry(int accent, int band, int onSurface, int surfaceBase) {
        // Both bands are dark, so the chrome is in its pale ink on either of them.
        WindowChipInk.Palette palette = WindowChipInk.resolve(band, true,
            WindowChipInk.neutralSeed(onSurface, surfaceBase, true), accent);
        assertTrue(OnGlass.ratio(palette.selectedStroke, palette.band) >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.ratio(palette.selectedLabel, palette.selectedGround)
            >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("the place is still warm after the walk",
            SchemeTone.hueDistance(palette.selectedStroke, accent) < 8d);
    }

    // ------------------------------------------------------------------ the identity

    /**
     * The two values are one colour in two modes, not two colours: same hue, the tones a Material
     * light and dark accent take. A future edit that makes them unrelated fails here.
     */
    @Test
    public void theTwoValuesAreTheSameWarmColourAtTwoTones() {
        int light = ContextCompat.getColor(RuntimeEnvironment.getApplication(),
            R.color.termux_place_display);
        int night = nightColor();
        assertNotEquals("the night twin is a colour of its own", light, night);
        assertTrue("the same hue: " + SchemeTone.hueDistance(light, night),
            SchemeTone.hueDistance(light, night) < 8d);
        assertTrue("warm, where the theme's roles are cool", SchemeTone.chroma(light) > 20d);
        assertTrue(SchemeTone.chroma(night) > 20d);
        assertTrue("the light twin is the on-light ink",
            SchemeTone.tone(light) < 50d && SchemeTone.tone(light) > 30d);
        assertTrue("the night twin is the on-dark one",
            SchemeTone.tone(night) > 70d && SchemeTone.tone(night) < 90d);
    }

    /** The night value, pinned: the peach the place has always worn, in the mode it works in. */
    @Test
    @Config(qualifiers = "night")
    public void theNightValueIsThePeachThePlaceAlwaysWore() {
        assertEquals(0xFFF0B48A, accent());
    }

    private static int nightColor() {
        android.content.res.Configuration night = new android.content.res.Configuration(
            RuntimeEnvironment.getApplication().getResources().getConfiguration());
        night.uiMode = (night.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            | android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return ContextCompat.getColor(
            RuntimeEnvironment.getApplication().createConfigurationContext(night),
            R.color.termux_place_display);
    }
}
