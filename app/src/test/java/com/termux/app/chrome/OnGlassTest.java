package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.theme.SchemeTone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The light-mode contrast bug, as arithmetic.
 *
 * <p>Every fixture here is a colour measured on the reporting device or read out of the app's own
 * palette, so a failure in this file is a statement about what the user is looking at: "the white
 * mode lacks contrast for right widgets on statusbar and alphabets row looks fuzzy and the sessions
 * chip has visibility issue".</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class OnGlassTest {

    /** {@code termux_primary} in {@code values/colors.xml} — an on-white ink. */
    private static final int LIGHT_INK = 0xFF345CA8;
    /** {@code termux_primary} in {@code values-night/colors.xml} — an on-black ink. */
    private static final int NIGHT_INK = 0xFFB8C7FF;
    /** {@code termux_surface_base}, light: what a light-mode veil moves toward. */
    private static final int LIGHT_SURFACE = 0xFFF4F6FB;
    /** {@code termux_surface_base}, night. */
    private static final int NIGHT_SURFACE = 0xFF0F141B;

    /** Measured behind the status bar in light mode: the glass over the user's wallpaper. */
    private static final int STATUS_GLASS_LIGHT = 0xFF6A5755;
    /** Measured behind the A&ndash;Z strip in light mode. */
    private static final int AZ_GLASS_LIGHT = 0xFF657271;
    /** The same wallpaper measured at night, where Android's own dark dimming does the work. */
    private static final int GLASS_NIGHT = 0xFF1B1A17;
    /** A pale wallpaper: bright enough that a dark ink reads on it unaided. */
    private static final int VERY_BRIGHT_GLASS = 0xFFE8E4DC;
    /** A mid-bright wallpaper: too light to flip the ink, too dark to carry it. */
    private static final int BRIGHT_GLASS = 0xFFCDC7BC;

    // ------------------------------------------------------------------ the bug

    @Test
    public void theReportedGlassIsWhereTheLightInkDies() {
        // 6.5:1 on the white card it was authored for (the round's brief said 7.5)...
        assertTrue(OnGlass.ratio(LIGHT_INK, Color.WHITE) > 6d);
        // ...and nothing at all on the glass the chrome actually draws on.
        assertTrue(OnGlass.ratio(LIGHT_INK, STATUS_GLASS_LIGHT) < 1.3d);
        assertTrue(OnGlass.ratio(LIGHT_INK, AZ_GLASS_LIGHT) < 1.6d);
        // Dark mode reads only because the wallpaper is dimmed for it.
        assertTrue(OnGlass.ratio(NIGHT_INK, GLASS_NIGHT) > 10d);
    }

    // ------------------------------------------------------------------ backdrop

    @Test
    public void theBackdropIsTheWallpaperUnderTheDimAndTheTint() {
        // No dim, no tint: the wallpaper itself, forced opaque.
        assertEquals(STATUS_GLASS_LIGHT,
            OnGlass.backdrop(STATUS_GLASS_LIGHT, Color.TRANSPARENT, Color.TRANSPARENT));
        // The launcher's dim is black at the slider's alpha and can only darken.
        int dimmed = OnGlass.backdrop(Color.WHITE, 0x80000000, Color.TRANSPARENT);
        assertEquals(0xFF, Color.alpha(dimmed));
        assertTrue(Color.red(dimmed) < 140 && Color.red(dimmed) > 120);
        assertTrue(OnGlass.ratio(dimmed, Color.WHITE) > OnGlass.ratio(Color.WHITE, Color.WHITE));
        // A glass tint lands on top of both.
        int tinted = OnGlass.backdrop(Color.BLACK, Color.TRANSPARENT, 0x80FFFFFF);
        assertTrue(Color.red(tinted) > 120 && Color.red(tinted) < 140);
    }

    @Test
    public void aBackdropIsAlwaysOpaqueHoweverTranslucentItsParts() {
        assertEquals(0xFF, Color.alpha(
            OnGlass.backdrop(0x40FF0000, 0x20000000, 0x10FFFFFF)));
    }

    // ------------------------------------------------------------------ dark backdrop

    @Test
    public void aDarkBackdropInLightModeFlipsTheInkRatherThanVeilingTheWallpaperAway() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            GLASS_NIGHT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertTrue("a dark band needs no veil, whatever mode the app is in", resolution.isBare());
        assertTrue(resolution.inkFlipped);
        assertEquals(NIGHT_INK, resolution.ink);
        assertEquals(GLASS_NIGHT, resolution.surface);
        assertTrue(SchemeTone.contrastRatio(resolution.ink, resolution.surface)
            >= OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void aDarkBackdropInDarkModeIsAlreadyRightAndIsLeftAlone() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            GLASS_NIGHT, NIGHT_INK, LIGHT_INK, NIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertTrue(resolution.isBare());
        assertFalse(resolution.inkFlipped);
        assertEquals(NIGHT_INK, resolution.ink);
    }

    @Test
    public void aDarkBackdropWithTheFlipForbiddenTonesTheInkInstead() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            GLASS_NIGHT, LIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertFalse(resolution.inkFlipped);
        assertNotEquals(LIGHT_INK, resolution.ink);
        assertTrue(SchemeTone.contrastRatio(resolution.ink, resolution.surface)
            >= OnGlass.TARGET_BODY_TEXT);
    }

    // ------------------------------------------------------------------ bright backdrop

    @Test
    public void aVeryBrightBackdropInLightModeNeedsNothingAtAll() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            VERY_BRIGHT_GLASS, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertTrue(resolution.isBare());
        assertFalse(resolution.inkFlipped);
        assertEquals(LIGHT_INK, resolution.ink);
    }

    @Test
    public void aBrightBackdropInLightModeVeilsALittleAndKeepsTheModesInk() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            BRIGHT_GLASS, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertFalse("the veil has to engage here", resolution.isBare());
        assertFalse("and it has to be nowhere near the cap", resolution.veilCapped);
        assertEquals("the mode's own ink survives a light veil", LIGHT_INK, resolution.ink);
        assertTrue(resolution.veilAlpha() < OnGlass.MAX_VEIL_ALPHA);
        assertTrue(SchemeTone.contrastRatio(resolution.ink, resolution.surface)
            >= OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void theVeilIsTheSmallestOneThatWorks() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            BRIGHT_GLASS, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        int alpha = Color.alpha(resolution.veil);
        assertTrue(alpha > 0);
        int oneStepLess = OnGlass.composite(
            OnGlass.withAlpha(LIGHT_SURFACE, alpha - 1), BRIGHT_GLASS);
        assertTrue("one alpha step less must fail, or the search is not minimal",
            SchemeTone.contrastRatio(LIGHT_INK, oneStepLess) < OnGlass.TARGET_BODY_TEXT);
    }

    // ------------------------------------------------------------------ the hard case

    @Test
    public void theUsersOwnStatusGlassTakesTheCappedVeilAndADarkerInk() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            STATUS_GLASS_LIGHT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        // Neither ink reads on 10.5% luminance glass, so the veil runs all the way to the cap...
        assertTrue(resolution.veilCapped);
        assertEquals(OnGlass.MAX_VEIL_ALPHA_255, Color.alpha(resolution.veil));
        // ...and the ink still has to move, staying a blue rather than becoming grey.
        assertNotEquals(LIGHT_INK, resolution.ink);
        assertTrue("the ink darkens to meet a lightened surface",
            SchemeTone.tone(resolution.ink) < SchemeTone.tone(LIGHT_INK));
        assertTrue(SchemeTone.hueDistance(resolution.ink, LIGHT_INK) < 15d);
        assertFalse(resolution.shortfall);
        assertTrue(SchemeTone.contrastRatio(resolution.ink, resolution.surface)
            >= OnGlass.TARGET_BODY_TEXT);
        // And 45% of the wallpaper is still visible through the band.
        assertTrue(resolution.veilAlpha() <= OnGlass.MAX_VEIL_ALPHA);
    }

    @Test
    public void theAzStripsOwnGlassIsFixedToo() {
        OnGlass.Resolution resolution = OnGlass.resolve(
            AZ_GLASS_LIGHT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_LARGE_TEXT);
        assertFalse(resolution.shortfall);
        assertTrue("the letters measured 1.21 and have to clear 3.0",
            SchemeTone.contrastRatio(resolution.ink, resolution.surface)
                >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue(resolution.veilAlpha() <= OnGlass.MAX_VEIL_ALPHA);
    }

    @Test
    public void aBandThatOnlyNeedsLargeTextContrastGetsOffLighterThanOneThatNeedsBodyContrast() {
        OnGlass.Resolution body = OnGlass.resolve(
            STATUS_GLASS_LIGHT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        OnGlass.Resolution large = OnGlass.resolve(
            STATUS_GLASS_LIGHT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_LARGE_TEXT);
        assertTrue(large.veilAlpha() <= body.veilAlpha());
    }

    // ------------------------------------------------------------------ the cap

    @Test
    public void noWallpaperCanVeilItselfAway() {
        for (int grey = 0; grey <= 255; grey += 1) {
            int backdrop = Color.rgb(grey, grey, grey);
            for (double target : new double[] {
                OnGlass.TARGET_DECORATION, OnGlass.TARGET_LARGE_TEXT, OnGlass.TARGET_BODY_TEXT}) {
                OnGlass.Resolution light = OnGlass.resolve(
                    backdrop, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, target);
                OnGlass.Resolution night = OnGlass.resolve(
                    backdrop, NIGHT_INK, LIGHT_INK, NIGHT_SURFACE, target);
                assertTrue("grey " + grey, light.veilAlpha() <= OnGlass.MAX_VEIL_ALPHA);
                assertTrue("grey " + grey, night.veilAlpha() <= OnGlass.MAX_VEIL_ALPHA);
            }
        }
    }

    @Test
    public void theWorstPossibleBackdropStillEndsUpLegible() {
        // A backdrop sitting exactly on the ink's own luminance is the pathological case: no veil
        // within the cap can save the preferred ink, so the ink is the thing that has to move.
        OnGlass.Resolution resolution = OnGlass.resolve(
            LIGHT_INK, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertEquals(1d, OnGlass.ratio(LIGHT_INK, LIGHT_INK), 0.0001d);
        assertTrue(resolution.veilCapped);
        assertFalse(resolution.shortfall);
        assertTrue(SchemeTone.contrastRatio(resolution.ink, resolution.surface)
            >= OnGlass.TARGET_BODY_TEXT);
    }

    // ------------------------------------------------------------------ targets

    @Test
    public void decorationKeepsItsOwnTierAndIsNotQuietlyPromoted() {
        assertTrue(OnGlass.TARGET_DECORATION < OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.TARGET_LARGE_TEXT < OnGlass.TARGET_BODY_TEXT);
        assertEquals(4.5d, OnGlass.TARGET_BODY_TEXT, 0d);
        assertEquals(3.0d, OnGlass.TARGET_LARGE_TEXT, 0d);
        // The separator dots measured 1.01 on the user's glass; at their own tier they become
        // visible without asking for as much veil as a label does.
        OnGlass.Resolution dots = OnGlass.resolve(
            STATUS_GLASS_LIGHT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_DECORATION);
        OnGlass.Resolution label = OnGlass.resolve(
            STATUS_GLASS_LIGHT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertTrue(SchemeTone.contrastRatio(dots.ink, dots.surface) >= OnGlass.TARGET_DECORATION);
        assertTrue(dots.veilAlpha() < label.veilAlpha());
    }

    // ------------------------------------------------------------------ ink helpers

    @Test
    public void inkOnKeepsTheHueAndOnlyMovesTheTone() {
        int ink = OnGlass.inkOn(STATUS_GLASS_LIGHT, LIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        assertTrue(SchemeTone.contrastRatio(ink, STATUS_GLASS_LIGHT) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(SchemeTone.hueDistance(ink, LIGHT_INK) < 15d);
    }

    @Test
    public void mostLegibleInkPicksWhicheverSideWins() {
        assertEquals(NIGHT_INK, OnGlass.mostLegibleInk(GLASS_NIGHT, LIGHT_INK, NIGHT_INK));
        assertEquals(LIGHT_INK, OnGlass.mostLegibleInk(Color.WHITE, LIGHT_INK, NIGHT_INK));
    }

    // ------------------------------------------------------------------ the contract itself

    @Test
    public void everyResolutionClearsTheRatioItWasAskedFor() {
        int[] backdrops = new int[] {
            STATUS_GLASS_LIGHT, AZ_GLASS_LIGHT, GLASS_NIGHT, BRIGHT_GLASS, VERY_BRIGHT_GLASS,
            Color.BLACK, Color.WHITE, 0xFF808080, 0xFF7F6E5D, 0xFF2E4F8A, 0xFFA0C0FF, LIGHT_INK,
        };
        double[] targets = new double[] {
            OnGlass.TARGET_DECORATION, OnGlass.TARGET_LARGE_TEXT, OnGlass.TARGET_BODY_TEXT};
        for (int backdrop : backdrops) {
            for (double target : targets) {
                assertResolves(backdrop, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, target);
                assertResolves(backdrop, NIGHT_INK, LIGHT_INK, NIGHT_SURFACE, target);
                assertResolves(backdrop, LIGHT_INK, LIGHT_INK, LIGHT_SURFACE, target);
            }
        }
    }

    @Test
    public void everyGreyBackdropResolvesForBodyTextInBothModes() {
        for (int grey = 0; grey <= 255; grey += 5) {
            int backdrop = Color.rgb(grey, grey, grey);
            assertResolves(backdrop, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
            assertResolves(backdrop, NIGHT_INK, LIGHT_INK, NIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        }
    }

    /** One resolution, checked against {@link SchemeTone#contrastRatio} rather than its own word. */
    private static void assertResolves(int backdrop, int preferred, int alternate, int veilColor,
                                       double target) {
        OnGlass.Resolution resolution =
            OnGlass.resolve(backdrop, preferred, alternate, veilColor, target);
        String where = "backdrop #" + Integer.toHexString(backdrop) + " ink #"
            + Integer.toHexString(preferred) + " target " + target + " -> " + resolution;
        // The surface it reports is the surface its own veil produces.
        assertEquals(where, OnGlass.opaque(OnGlass.composite(resolution.veil, OnGlass.opaque(backdrop))),
            resolution.surface);
        // The veil never erases the wallpaper.
        assertTrue(where, resolution.veilAlpha() <= OnGlass.MAX_VEIL_ALPHA);
        // The pair it hands back really does clear the ratio it was asked for.
        double measured = SchemeTone.contrastRatio(resolution.ink, resolution.surface);
        assertEquals(where, measured, resolution.ratio, 0.0001d);
        assertEquals(where, measured < target, resolution.shortfall);
        assertFalse("nothing in this sweep should be unsolvable: " + where, resolution.shortfall);
    }
}
