package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.chrome.OnGlass;
import com.termux.app.theme.SchemeTone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The window chips' half of the light-mode contrast round, as arithmetic.
 *
 * <p>Every band here is a colour measured on the reporting device and every ink is read out of the
 * app's own palette, so a failure in this file is a statement about what the user is looking at:
 * "the sessions chip has visibility issue", and beside it a window chip labelled {@code herdr}
 * whose label had no pixel cluster more than 4% of a luminance away from the band behind it.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WindowChipInkTest {

    /** Measured behind the status bar and the window bar in light mode. */
    private static final int GLASS_LIGHT = 0xFF6A5755;
    /** Measured behind the A&ndash;Z strip: the same wallpaper, brighter, and the harder band. */
    private static final int GLASS_LIGHT_BRIGHT = 0xFF657271;
    /** The same wallpaper at night, where Android's own dark dimming does the work. */
    private static final int GLASS_NIGHT = 0xFF1B1A17;
    /** A genuinely pale wallpaper: light mode with the chrome in its dark ink. */
    private static final int GLASS_PALE = 0xFFE8E4DC;

    private static final int LIGHT_ON_SURFACE = 0xFF171C24;
    private static final int LIGHT_SURFACE_BASE = 0xFFF4F6FB;
    private static final int LIGHT_SECONDARY = 0xFF556070;
    private static final int LIGHT_ON_SURFACE_VARIANT = 0xFF586171;
    private static final int LIGHT_PRIMARY = 0xFF345CA8;
    private static final int NIGHT_ON_SURFACE = 0xFFE8EDF7;
    private static final int NIGHT_SURFACE_BASE = 0xFF0F141B;
    private static final int NIGHT_PRIMARY = 0xFFB8C7FF;

    private static WindowChipInk.Palette palette(int band, boolean pale, int onSurface,
                                                 int surfaceBase, int accent) {
        return WindowChipInk.resolve(band, pale,
            WindowChipInk.neutralSeed(onSurface, surfaceBase, pale), accent);
    }

    private static WindowChipInk.Palette lightOnReportedGlass() {
        return palette(GLASS_LIGHT, true, LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_PRIMARY);
    }

    private static WindowChipInk.Palette nightOnReportedGlass() {
        return palette(GLASS_NIGHT, true, NIGHT_ON_SURFACE, NIGHT_SURFACE_BASE, NIGHT_PRIMARY);
    }

    // ------------------------------------------------------------------ the bug

    /**
     * What the user saw, before anything measured the band: the authored chip palette composited
     * onto the reporting device's own glass and came out as the glass.
     */
    @Test
    public void theAuthoredChipPaletteVanishesOnTheReportedGlass() {
        int label = OnGlass.composite(OnGlass.withAlpha(
            SchemeTone.blend(LIGHT_ON_SURFACE_VARIANT, LIGHT_SECONDARY, .18f), 148), GLASS_LIGHT);
        int fill = OnGlass.composite(OnGlass.withAlpha(LIGHT_SECONDARY, 16), GLASS_LIGHT);
        int stroke = OnGlass.composite(OnGlass.withAlpha(LIGHT_SECONDARY, 34), GLASS_LIGHT);
        int selectedFill = OnGlass.composite(OnGlass.withAlpha(LIGHT_PRIMARY, 58), GLASS_LIGHT);
        int selectedStroke = OnGlass.composite(OnGlass.withAlpha(LIGHT_PRIMARY, 112), GLASS_LIGHT);
        // None of them differs from the band by more than a handful of percent of a luminance.
        assertTrue(OnGlass.ratio(label, GLASS_LIGHT) < 1.05d);
        assertTrue(OnGlass.ratio(fill, GLASS_LIGHT) < 1.05d);
        assertTrue(OnGlass.ratio(stroke, GLASS_LIGHT) < 1.05d);
        assertTrue(OnGlass.ratio(selectedFill, GLASS_LIGHT) < 1.05d);
        assertTrue(OnGlass.ratio(selectedStroke, GLASS_LIGHT) < 1.05d);
        // And the selected label, the one colour that was not spent on an alpha, is not enough
        // either: onSurface on the fill it stands on is 2.5:1 where body text wants 4.5.
        assertTrue(OnGlass.ratio(LIGHT_ON_SURFACE, selectedFill) < 3d);
    }

    // ------------------------------------------------------------------ the labels

    /** Both labels, both modes, on the ground each one really stands on. */
    @Test
    public void bothLabelsClearBodyTextOnEveryMeasuredBand() {
        assertLabels("light, the reported glass", lightOnReportedGlass());
        assertLabels("light, the brighter band", palette(GLASS_LIGHT_BRIGHT, true,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_PRIMARY));
        assertLabels("light, a pale wallpaper", palette(GLASS_PALE, false,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_PRIMARY));
        assertLabels("night, the reported glass", nightOnReportedGlass());
    }

    private static void assertLabels(String where, WindowChipInk.Palette palette) {
        assertTrue(where + ": the resting label on its ground",
            OnGlass.ratio(palette.restingLabel, palette.restingGround) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(where + ": the resting label on its plain surface",
            OnGlass.ratio(palette.restingLabel, palette.restingSurface) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(where + ": the selected label on its ground",
            OnGlass.ratio(palette.selectedLabel, palette.selectedGround) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(where + ": the selected label on its plain surface",
            OnGlass.ratio(palette.selectedLabel, palette.selectedSurface) >= OnGlass.TARGET_BODY_TEXT);
    }

    /**
     * The measured answer for the band the user reported, pinned. 1.03:1 was the bug; these are
     * the numbers that replaced it, and a change that moves them is a change to what is on screen.
     */
    @Test
    public void theReportedGlassGetsItsChipsBack() {
        WindowChipInk.Palette palette = lightOnReportedGlass();
        assertEquals(4.53d, OnGlass.ratio(palette.restingLabel, palette.restingGround), .05d);
        // 4.56 rather than the 4.51 this pinned before: the directed tone walk moved into
        // OnGlass.tonedToward and steps one tone at a time from the band's own tone, where this
        // class stepped two at a time from the seed's. Same side, same floor, one step finer.
        assertEquals(4.56d, OnGlass.ratio(palette.selectedLabel, palette.selectedGround), .05d);
        assertEquals(6.25d, OnGlass.ratio(palette.restingStroke, palette.band), .05d);
        // 3.04 rather than 3.12, for the same reason: a one-tone walk stops at the first tone that
        // clears the graphics floor instead of overshooting it by a step.
        assertEquals(3.04d, OnGlass.ratio(palette.selectedStroke, palette.band), .05d);
    }

    /** The current chip is not merely legible, it is the brighter of the two. */
    @Test
    public void theCurrentChipReadsLouderThanARestingOne() {
        for (WindowChipInk.Palette palette : new WindowChipInk.Palette[] {
            lightOnReportedGlass(), nightOnReportedGlass()}) {
            assertNotEquals("the two labels differ", palette.restingLabel, palette.selectedLabel);
            assertTrue("the current label is further from the band",
                OnGlass.ratio(palette.selectedLabel, palette.band)
                    >= OnGlass.ratio(palette.restingLabel, palette.band));
            assertNotEquals("the two chips' washes differ",
                palette.restingSurface, palette.selectedSurface);
        }
    }

    // ------------------------------------------------------------------ the outline

    /**
     * The outline is the one part of a chip that survived on the phone — "only its outline
     * survives, in pink, over pink wallpaper" — and it is what says where one window ends and the
     * next begins, so it is held to the WCAG floor for meaningful graphics rather than to the
     * decoration tier.
     */
    @Test
    public void bothOutlinesClearTheGraphicsFloorOnEveryMeasuredBand() {
        for (WindowChipInk.Palette palette : new WindowChipInk.Palette[] {
            lightOnReportedGlass(),
            palette(GLASS_LIGHT_BRIGHT, true, LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_PRIMARY),
            palette(GLASS_PALE, false, LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_PRIMARY),
            nightOnReportedGlass()}) {
            assertTrue(OnGlass.ratio(palette.restingStroke, palette.band)
                >= OnGlass.TARGET_LARGE_TEXT);
            assertTrue(OnGlass.ratio(palette.selectedStroke, palette.band)
                >= OnGlass.TARGET_LARGE_TEXT);
            assertEquals("an outline is drawn solid; its alpha is not where the contrast went",
                255, Color.alpha(palette.restingStroke));
            assertEquals(255, Color.alpha(palette.selectedStroke));
        }
    }

    /** The current chip's outline is the place's own colour, not a neutral. */
    @Test
    public void theCurrentOutlineKeepsThePlacesHue() {
        int warm = 0xFF8D4E19;
        WindowChipInk.Palette palette = palette(GLASS_LIGHT, true,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, warm);
        assertTrue("the accent's hue survives the tone walk",
            SchemeTone.hueDistance(palette.selectedStroke, warm) < 5d);
        assertTrue(SchemeTone.hueDistance(palette.glyph, warm) < 12d);
    }

    // ------------------------------------------------------------------ the washes

    /**
     * The washes have no contrast floor, and that is arithmetic rather than taste: a wash that
     * cleared even the decoration tier on the reported band would put the chip's surface where no
     * ink can clear body text.
     */
    @Test
    public void aWashThatClearedTheDecorationTierWouldCostTheLabelItsTier() {
        int tint = WindowChipInk.towardPolarity(GLASS_LIGHT, LIGHT_SURFACE_BASE, true,
            OnGlass.TARGET_LARGE_TEXT);
        int surface = GLASS_LIGHT;
        for (int alpha = 1; alpha <= 255; alpha++) {
            surface = OnGlass.opaque(
                OnGlass.composite(OnGlass.withAlpha(tint, alpha), GLASS_LIGHT));
            if (OnGlass.ratio(surface, GLASS_LIGHT) >= OnGlass.TARGET_DECORATION) break;
        }
        assertTrue("a wash at the decoration tier exists at all",
            OnGlass.ratio(surface, GLASS_LIGHT) >= OnGlass.TARGET_DECORATION);
        // ...and on the surface it leaves, the chrome's own ink cannot reach body text. White is
        // as far as a pale ink goes, and it is not far enough.
        assertTrue(OnGlass.ratio(Color.WHITE, surface) < OnGlass.TARGET_BODY_TEXT);
        // The way out would be to ink this one chip dark, and that is not available: the chrome
        // chose one polarity from this very band, where a dark ink reads 3.11:1 — under the floor
        // and under the pale ink's own 6.76:1. A chip in the opposite ink is the incoherence the
        // polarity decision exists to prevent.
        assertTrue(OnGlass.ratio(Color.BLACK, GLASS_LIGHT) < OnGlass.TARGET_BODY_TEXT);
        assertTrue(OnGlass.ratio(Color.BLACK, GLASS_LIGHT)
            < OnGlass.ratio(Color.WHITE, GLASS_LIGHT));
    }

    /**
     * So the wash is what gives way. On a band with room it keeps its full strength; on the band
     * the user reported it spends part of it; on a band that only just carries the chrome at all
     * the chips come out as an outline and a title.
     */
    @Test
    public void theWashGivesWayToTheLabelAndNotTheOtherWayRound() {
        int full = WindowChipInk.washBudget(GLASS_NIGHT,
            WindowChipInk.towardPolarity(GLASS_NIGHT, NIGHT_ON_SURFACE, true, OnGlass.TARGET_LARGE_TEXT),
            WindowChipInk.towardPolarity(GLASS_NIGHT, NIGHT_PRIMARY, true, OnGlass.TARGET_LARGE_TEXT),
            WindowChipInk.towardPolarity(GLASS_NIGHT, NIGHT_PRIMARY, true, OnGlass.TARGET_LARGE_TEXT),
            true);
        assertEquals("a dark band can afford the whole wash", 16, full);

        WindowChipInk.Palette reported = lightOnReportedGlass();
        WindowChipInk.Palette harder = palette(GLASS_LIGHT_BRIGHT, true,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_PRIMARY);
        assertTrue("the reported band spends some of it",
            Color.alpha(reported.selectedFill) < WindowChipInk.SELECTED_FILL_ALPHA);
        assertTrue("a brighter band spends more",
            Color.alpha(harder.selectedFill) < Color.alpha(reported.selectedFill));
        // Whatever is left, both labels still read: that is the whole point of spending it.
        assertLabels("the hardest band", harder);
    }

    /**
     * The halo is the chip's own surface now, not the wash's hue at an alpha of its own, and the
     * label's promise was made without it: pulling the ground back toward the plain surface can
     * only ever raise the ratio the label already cleared.
     */
    @Test
    public void theTitleHaloCanOnlyHelp() {
        WindowChipInk.Palette palette = lightOnReportedGlass();
        assertEquals(palette.restingSurface & 0x00FFFFFF, palette.restingHalo & 0x00FFFFFF);
        assertEquals(palette.selectedSurface & 0x00FFFFFF, palette.selectedHalo & 0x00FFFFFF);
        assertEquals(ChipWatermarkGeometry.TITLE_HALO_ALPHA, Color.alpha(palette.restingHalo));
        for (int applied = 0; applied <= 255; applied += 15) {
            int ground = OnGlass.opaque(OnGlass.composite(
                OnGlass.withAlpha(palette.restingHalo, applied), palette.restingGround));
            assertTrue("the halo at " + applied + "/255 never costs the label its tier",
                OnGlass.ratio(palette.restingLabel, ground) >= OnGlass.TARGET_BODY_TEXT);
        }
    }

    // ------------------------------------------------------------------ the polarity

    /** Every ink a chip draws stands on the same side of the band. One chrome, one polarity. */
    @Test
    public void everyChipInkObeysTheChromesPolarity() {
        WindowChipInk.Palette pale = lightOnReportedGlass();
        for (int ink : new int[] {pale.restingStroke, pale.selectedStroke, pale.restingLabel,
            pale.selectedLabel, pale.glyph}) {
            assertTrue(WindowChipInk.onPolaritySide(pale.band, ink, true));
        }
        WindowChipInk.Palette dark = palette(GLASS_PALE, false,
            LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, LIGHT_PRIMARY);
        for (int ink : new int[] {dark.restingStroke, dark.selectedStroke, dark.restingLabel,
            dark.selectedLabel, dark.glyph}) {
            assertTrue(WindowChipInk.onPolaritySide(dark.band, ink, false));
        }
    }

    /**
     * The pair a neutral role is read as is a property of the two colours, never of the night
     * qualifier: {@code onSurface} and {@code surfaceBase} are the same pair upside down in the
     * two modes, and this picks by tone.
     */
    @Test
    public void theNeutralPairIsReadOffTheColoursAndNotOffTheMode() {
        assertEquals(LIGHT_SURFACE_BASE,
            WindowChipInk.neutralSeed(LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, true));
        assertEquals(LIGHT_ON_SURFACE,
            WindowChipInk.neutralSeed(LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, false));
        assertEquals(NIGHT_ON_SURFACE,
            WindowChipInk.neutralSeed(NIGHT_ON_SURFACE, NIGHT_SURFACE_BASE, true));
        assertEquals(NIGHT_SURFACE_BASE,
            WindowChipInk.neutralSeed(NIGHT_ON_SURFACE, NIGHT_SURFACE_BASE, false));
    }

    /**
     * A palette says what it was resolved from, so a caller re-dressing itself for an unchanged
     * band — which is every window-label poll — can keep the one it has.
     */
    @Test
    public void aPaletteKnowsWhetherItStillAnswersTheQuestion() {
        WindowChipInk.Palette palette = lightOnReportedGlass();
        int neutral = WindowChipInk.neutralSeed(LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, true);
        assertTrue(palette.matches(GLASS_LIGHT, true, neutral, LIGHT_PRIMARY));
        assertTrue("alpha on the band is not part of the question",
            palette.matches(0x806A5755, true, neutral, LIGHT_PRIMARY));
        assertFalse(palette.matches(GLASS_NIGHT, true, neutral, LIGHT_PRIMARY));
        assertFalse(palette.matches(GLASS_LIGHT, false, neutral, LIGHT_PRIMARY));
        assertFalse(palette.matches(GLASS_LIGHT, true, LIGHT_ON_SURFACE, LIGHT_PRIMARY));
        assertFalse("a new place is a new palette",
            palette.matches(GLASS_LIGHT, true, neutral, 0xFF8D4E19));
    }

    /** A tone walk that runs out of room ends at the extreme rather than at a colour that fails. */
    @Test
    public void aHopelessSurfaceEndsAtTheExtreme() {
        assertEquals(Color.WHITE, WindowChipInk.towardPolarity(Color.WHITE, 0xFF345CA8, true, 4.5d));
        assertEquals(Color.BLACK, WindowChipInk.towardPolarity(Color.BLACK, 0xFF345CA8, false, 4.5d));
    }

    // ------------------------------------------------------------------ the column's marks

    /**
     * The status column's chips have no room for the row's corner dots, so working, asking and
     * finished are the rim's colour and the mark's own glyph. The rim is the only carrier of its
     * fact — the graphics floor — and the glyph is 11sp text.
     */
    @Test
    public void theColumnsMarksClearTheirTiersInBothModes() {
        int[] lightMarks = {0xFFBA1A1A, LIGHT_PRIMARY, LIGHT_ON_SURFACE_VARIANT, 0xFF1B6B3A};
        WindowChipInk.Palette light = lightOnReportedGlass();
        for (int mark : lightMarks) {
            assertTrue(OnGlass.ratio(
                WindowChipInk.towardPolarity(light.band, mark, true, OnGlass.TARGET_LARGE_TEXT),
                light.band) >= OnGlass.TARGET_LARGE_TEXT);
            assertTrue(OnGlass.ratio(
                WindowChipInk.towardPolarity(light.restingSurface, mark, true,
                    OnGlass.TARGET_BODY_TEXT), light.restingSurface) >= OnGlass.TARGET_BODY_TEXT);
        }
        int[] nightMarks = {0xFFFFB4AB, NIGHT_PRIMARY, 0xFFA9B4C5, 0xFF7FD69B};
        WindowChipInk.Palette night = nightOnReportedGlass();
        for (int mark : nightMarks) {
            assertTrue(OnGlass.ratio(
                WindowChipInk.towardPolarity(night.band, mark, true, OnGlass.TARGET_LARGE_TEXT),
                night.band) >= OnGlass.TARGET_LARGE_TEXT);
            assertTrue(OnGlass.ratio(
                WindowChipInk.towardPolarity(night.restingSurface, mark, true,
                    OnGlass.TARGET_BODY_TEXT), night.restingSurface) >= OnGlass.TARGET_BODY_TEXT);
        }
    }

    /** The marks keep their hues: a bell must not come out the colour of a finished command. */
    @Test
    public void theMarksKeepTheirHuesThroughTheToneWalk() {
        int error = 0xFFBA1A1A;
        int done = 0xFF1B6B3A;
        int walkedError = WindowChipInk.towardPolarity(GLASS_LIGHT, error, true,
            OnGlass.TARGET_LARGE_TEXT);
        int walkedDone = WindowChipInk.towardPolarity(GLASS_LIGHT, done, true,
            OnGlass.TARGET_LARGE_TEXT);
        assertTrue(SchemeTone.hueDistance(walkedError, error) < 8d);
        assertTrue(SchemeTone.hueDistance(walkedDone, done) < 8d);
        assertTrue("and they are still told apart",
            SchemeTone.hueDistance(walkedError, walkedDone) > 60d);
    }
}
