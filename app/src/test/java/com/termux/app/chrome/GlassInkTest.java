package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.theme.SchemeTone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The A&ndash;Z rails, the ticks and the launch wave, on the glass they are really drawn on.
 *
 * <p>Every fixture is a colour measured on the reporting device or read out of the app's palette,
 * so a failure here is a statement about what the user is looking at: "the alphabets row looks
 * fuzzy". It was not the glyphs. In light mode the letters were {@code #0C84C0} on a
 * {@code #657271} strip — 1.21:1 — and each one was ringed by a near-black stroke at alpha 195,
 * which measured 2.9:1 against the same strip. The ring was the only thing on the row with any
 * contrast, and a hollow outline filled with something close to its own background is what the eye
 * calls blurry.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassInkTest {

    /** {@code termux_primary}, light: an on-white ink. */
    private static final int LIGHT_INK = 0xFF345CA8;
    /** {@code termux_primary}, night: an on-black ink. */
    private static final int NIGHT_INK = 0xFFB8C7FF;
    /** {@code termux_on_surface}, light and night: what the drawer's rope seeds its letters from. */
    private static final int ON_SURFACE_LIGHT = 0xFF171C24;
    private static final int ON_SURFACE_NIGHT = 0xFFE8EDF7;
    /** {@code termux_surface_panel_high}, light: what a light-mode band veils toward. */
    private static final int LIGHT_BASE = 0xFFE1E7F2;

    /** Measured behind the A&ndash;Z strip in light mode on the reporting device. */
    private static final int AZ_GLASS = 0xFF657271;
    /** Measured behind the status bar in light mode on the same device. */
    private static final int BAR_GLASS = 0xFF6A5755;
    /** A genuinely pale wallpaper: the band that takes the dark ink in either mode. */
    private static final int PALE_GLASS = 0xFFDDDDDD;
    /** A dark-theme band: Android dims the wallpaper for a dark theme and the glass lands here. */
    private static final int NIGHT_GLASS = 0xFF1B1A17;

    /** What the row shipped with in light mode: the accent with its value floored to 0.78. */
    private static final int SHIPPED_LETTER = 0xFF0C84C0;
    /** What both rails stroked with, unconditionally, in either mode. */
    private static final int SHIPPED_HALO = 0xFF1A1F2A;
    private static final int SHIPPED_HALO_ALPHA = 195;

    private static final Rect AZ_RECT = new Rect(1020, 300, 1080, 1800);

    // ------------------------------------------------------------------ what the fuzz was

    /**
     * The fault, stated as arithmetic: the ring read better against the strip than the letter
     * inside it did. Nothing about that is a blur, and no amount of stroke-width tuning would have
     * helped — the eye was resolving the outline because the outline was the only thing there.
     */
    @Test
    public void theShippedHaloOutShoutedTheShippedLetter() {
        double letter = OnGlass.ratio(SHIPPED_LETTER, AZ_GLASS);
        double halo = OnGlass.ratio(
            GlassInk.effective(SHIPPED_HALO, SHIPPED_HALO_ALPHA, AZ_GLASS), AZ_GLASS);

        assertTrue("the letter measured 1.21:1 on the strip, not 3.0: " + letter,
            letter < 1.3d && letter < OnGlass.TARGET_LARGE_TEXT);
        assertTrue("and its own halo measured " + halo + ", which is more than twice as much",
            halo > letter * 2d);
    }

    /**
     * The same strip, the same wallpaper, after the round: the letter carries the contrast and the
     * halo is quieter than the letter. That inequality is the user's complaint, inverted.
     */
    @Test
    public void theLetterNowCarriesTheStripAndTheHaloStaysUnderIt() {
        int letter = OnGlass.resolveBare(AZ_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
        int halo = GlassInk.halo(letter, AZ_GLASS, false);

        double letterRatio = OnGlass.ratio(letter, AZ_GLASS);
        double haloRatio = OnGlass.ratio(GlassInk.effective(halo, AZ_GLASS), AZ_GLASS);

        assertTrue("the letter clears the large-text floor: " + letterRatio,
            letterRatio >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue("and the ring around it reads less than it does: " + haloRatio,
            haloRatio <= letterRatio);
        assertTrue("with less ink than the 195 it used to take unconditionally: "
            + Color.alpha(halo), Color.alpha(halo) <= GlassInk.HALO_ALPHA_CEILING);
    }

    // ------------------------------------------------------------------ the inversion

    /**
     * The decision, as a property: the halo is on the far side of the band from the glyph, whichever
     * side that is. A pale letter keeps the near-black stroke it always had; a dark letter — the
     * light theme standing on a genuinely light wallpaper — gets a near-white one instead of the
     * ring that used to be drawn round it regardless.
     */
    @Test
    public void theHaloInvertsWithTheGlyph() {
        for (int band : new int[] {AZ_GLASS, BAR_GLASS}) {
            int paleGlyph = OnGlass.resolveBare(band, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
            assertTrue(hex(band) + ": a pale glyph is paler than the band it is on",
                SchemeTone.tone(paleGlyph) > SchemeTone.tone(band));
            assertTrue(hex(band) + ": so its halo goes dark, below the band and not just below it",
                SchemeTone.tone(GlassInk.halo(paleGlyph, band, false)) < SchemeTone.tone(band));
            assertEquals(hex(band) + ": which is the stroke both rails have always drawn",
                GlassInk.HALO_DARK & 0x00FFFFFF, GlassInk.halo(paleGlyph, band, false) & 0x00FFFFFF);
        }
        // A dark-theme band is darker than the near-black itself, so the constant would move the
        // halo towards the glyph. Beyond the constant there is only the extreme.
        int onNight = OnGlass.resolveBare(NIGHT_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
        assertEquals("a band past the constant strokes with black",
            Color.BLACK & 0x00FFFFFF, GlassInk.halo(onNight, NIGHT_GLASS, false) & 0x00FFFFFF);
        for (int band : new int[] {PALE_GLASS, LIGHT_BASE}) {
            int darkGlyph = OnGlass.resolveBare(band, LIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
            assertTrue(hex(band) + ": a dark glyph is darker than the band it is on",
                SchemeTone.tone(darkGlyph) < SchemeTone.tone(band));
            assertTrue(hex(band) + ": so its halo goes pale — the inversion",
                SchemeTone.tone(GlassInk.halo(darkGlyph, band, false)) > SchemeTone.tone(darkGlyph));
            assertEquals(hex(band) + ": the near-black's mirror, not white",
                GlassInk.HALO_PALE & 0x00FFFFFF, GlassInk.halo(darkGlyph, band, false) & 0x00FFFFFF);
        }
    }

    /**
     * The property the user actually complained about, as an assertion rather than a hope: the halo
     * has to deepen the glyph's edge — it is there to separate the letter from the band — and it
     * has to stay quieter against the band than the letter is, or it becomes the shape and the
     * letter becomes its filling. Checked on every band and both polarities.
     */
    @Test
    public void theHaloReadsAsAnEdgeAndNeverAsTheShape() {
        int[] bands = {AZ_GLASS, BAR_GLASS, NIGHT_GLASS, PALE_GLASS, LIGHT_BASE, 0xFF787878};
        for (int band : bands) {
            for (int seed : new int[] {LIGHT_INK, NIGHT_INK, ON_SURFACE_LIGHT, ON_SURFACE_NIGHT}) {
                int glyph = OnGlass.resolveBare(band, seed, OnGlass.TARGET_LARGE_TEXT).ink;
                for (boolean focused : new boolean[] {false, true}) {
                    int halo = GlassInk.halo(glyph, band, focused);
                    int seen = GlassInk.effective(halo, band);
                    String where = hex(band) + "/" + hex(seed) + (focused ? "/focused" : "");

                    double bare = OnGlass.ratio(glyph, band);
                    double edged = OnGlass.ratio(glyph, seen);
                    assertTrue(where + ": the halo deepens the glyph's edge rather than filling it"
                        + " in — " + bare + " bare vs " + edged + " against the halo",
                        edged > bare);
                    assertTrue(where + ": and it must not out-shout the glyph — "
                        + OnGlass.ratio(seen, band) + " vs " + bare,
                        OnGlass.ratio(seen, band) <= bare);
                    assertTrue(where + ": the halo is never dropped",
                        Color.alpha(halo) >= GlassInk.HALO_ALPHA_FLOOR);
                    assertTrue(where + ": and never heavier than the shipped stroke",
                        Color.alpha(halo) <= GlassInk.HALO_ALPHA_CEILING);
                }
            }
        }
    }

    /** The focused letter keeps the heavier halo it always had, and keeps it inside the ceiling. */
    @Test
    public void theFocusedLettersHaloIsTheHeavierOne() {
        int glyph = OnGlass.resolveBare(AZ_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
        assertTrue(Color.alpha(GlassInk.halo(glyph, AZ_GLASS, true))
            >= Color.alpha(GlassInk.halo(glyph, AZ_GLASS, false)));
        assertTrue(Color.alpha(GlassInk.halo(glyph, AZ_GLASS, true))
            <= GlassInk.HALO_ALPHA_CEILING);
    }

    /**
     * The halo's alpha follows the band rather than being a constant: a band close to the glyph
     * needs a real edge, and a band already far from it needs almost none.
     */
    @Test
    public void theHalosAlphaFollowsHowMuchTheBandNeedsIt() {
        int glyph = 0xFFFFFFFF;
        int close = 0xFF8A8A8A;      // a band the glyph only just clears: it needs a real edge
        int far = 0xFF101010;        // a band nothing like it: the glyph stands on its own
        assertTrue("a near band asks for more halo than a far one: "
                + Color.alpha(GlassInk.halo(glyph, close, false)) + " vs "
                + Color.alpha(GlassInk.halo(glyph, far, false)),
            Color.alpha(GlassInk.halo(glyph, close, false))
                > Color.alpha(GlassInk.halo(glyph, far, false)));
        assertEquals("a glyph with headroom to spare keeps only the floor",
            GlassInk.HALO_ALPHA_FLOOR, Color.alpha(GlassInk.halo(glyph, far, false)));

        // And it is monotone: walking a band away from the glyph never asks for more halo.
        int previous = 256;
        for (int level = 0x90; level >= 0x00; level -= 8) {
            int band = Color.rgb(level, level, level);
            int alpha = Color.alpha(GlassInk.halo(glyph, band, false));
            assertTrue("halo grew as the band moved away from the glyph at " + hex(band)
                + ": " + alpha + " after " + previous, alpha <= previous);
            previous = alpha;
        }
    }

    /**
     * The reporting device's own strip is the hard case — the letters land within a hundredth of
     * their 3.0 target — so it is where the halo is most needed and where the ceiling has to do its
     * work. Both at once: a full-strength halo, and still quieter than the letter it rings.
     */
    @Test
    public void theHardestBandGetsTheMostHaloAndStillNotTooMuch() {
        int letter = OnGlass.resolveBare(AZ_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
        int halo = GlassInk.halo(letter, AZ_GLASS, false);
        assertTrue("a letter with no headroom keeps a real halo: " + Color.alpha(halo),
            Color.alpha(halo) > GlassInk.HALO_ALPHA_FLOOR * 2);
        assertTrue("and it is still under the letter",
            OnGlass.ratio(GlassInk.effective(halo, AZ_GLASS), AZ_GLASS)
                <= OnGlass.ratio(letter, AZ_GLASS));
    }

    // ------------------------------------------------------------------ the one contrast call

    /**
     * {@link GlassInk#legible} is what the four HSV floors became, and the promise is the one they
     * could not make: whatever the band, whatever the alpha, the result clears its target where it
     * is drawn.
     */
    @Test
    public void everythingResolvedOnGlassClearsItsTarget() {
        int[] bands = {AZ_GLASS, BAR_GLASS, NIGHT_GLASS, PALE_GLASS, LIGHT_BASE, 0xFF787878};
        double[] targets = {OnGlass.TARGET_BODY_TEXT, OnGlass.TARGET_LARGE_TEXT,
            OnGlass.TARGET_DECORATION};
        int[] alphas = {102, 0xE0, 0xE8, 0xF6, 255};
        for (int band : bands) {
            for (double target : targets) {
                for (int alpha : alphas) {
                    int ink = GlassInk.legible(band, LIGHT_INK, target, alpha);
                    double achieved = OnGlass.ratio(GlassInk.effective(ink, band), band);
                    assertTrue(hex(band) + " @" + target + "/" + alpha + ": " + achieved,
                        achieved >= target - 0.001d);
                }
            }
        }
    }

    /**
     * And the direction, which is the whole bug: on a pale band the answer has to be a darker
     * colour. Every one of the four floors could only ever return a brighter one.
     */
    @Test
    public void aPaleBandMovesTheInkDownwardsNotUpwards() {
        int onPale = GlassInk.legible(PALE_GLASS, LIGHT_INK, OnGlass.TARGET_LARGE_TEXT);
        assertTrue("a pale band darkens the ink",
            SchemeTone.tone(onPale) < SchemeTone.tone(PALE_GLASS));
        int onDark = GlassInk.legible(NIGHT_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);
        assertTrue("and a dark one still lightens it",
            SchemeTone.tone(onDark) > SchemeTone.tone(NIGHT_GLASS));
    }

    /** The hue of a branded colour survives the move; only its tone is anyone's business here. */
    @Test
    public void theAccentStaysTheAccent() {
        int resolved = GlassInk.legible(AZ_GLASS, LIGHT_INK, OnGlass.TARGET_LARGE_TEXT);
        assertTrue("a blue stays a blue: " + SchemeTone.hueDistance(resolved, LIGHT_INK),
            SchemeTone.hueDistance(resolved, LIGHT_INK) < 15d);
    }

    // ------------------------------------------------------------------ the rest of the phase

    /**
     * The drawer's A&ndash;Z rope draws the same letters over the same glass one plane away, so it
     * takes the same answer. Two rails that disagreed about which way the light goes would be worse
     * than either of them being wrong.
     */
    @Test
    public void theDrawersRopeAgreesWithTheDocksRail() {
        for (int band : new int[] {AZ_GLASS, BAR_GLASS, NIGHT_GLASS, PALE_GLASS}) {
            // What the dock's own rail settled on, and what the rope makes of the same band from
            // its own seed — colorOnSurface, which is a near-black in light mode and so resolves
            // the other way if it is left to itself.
            int railLetter = OnGlass.resolveBare(band,
                SchemeTone.tone(band) > 50d ? LIGHT_INK : NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
            boolean pale = GlassInk.isPaleSide(railLetter, band);
            int ropeLetter = GlassInk.legibleOn(band, ON_SURFACE_LIGHT, pale,
                OnGlass.TARGET_LARGE_TEXT);
            assertEquals(hex(band) + ": both rails face the same way",
                pale, GlassInk.isPaleSide(ropeLetter, band));
            assertTrue(hex(band) + ": the rope's letter reads",
                OnGlass.ratio(ropeLetter, band) >= OnGlass.TARGET_LARGE_TEXT - 0.001d);
            assertEquals(hex(band) + ": and both rails' halos go the same way",
                GlassInk.haloInk(railLetter, band), GlassInk.haloInk(ropeLetter, band));
            int halo = GlassInk.halo(ropeLetter, band, false);
            assertTrue(hex(band) + ": the rope's halo stays under its letter",
                OnGlass.ratio(GlassInk.effective(halo, band), band)
                    <= OnGlass.ratio(ropeLetter, band));
        }
    }

    /**
     * The page ticks, both of them. The tick under the page you are on is a graphic that tells you
     * something, so it takes the large-text floor; a tick at rest only says there is another page
     * there, so it takes the decoration floor — and it keeps its muted look by asking for it as an
     * alpha, which is raised only where 40% of a tick cannot clear 2.0 on that band.
     */
    @Test
    public void bothPageTicksClearTheirOwnFloor() {
        int restingAlpha = Math.round(255f * 0.40f);
        for (int band : new int[] {AZ_GLASS, BAR_GLASS, NIGHT_GLASS, PALE_GLASS}) {
            boolean pale = SchemeTone.tone(band) < 50d;
            int active = GlassInk.legibleOn(band, LIGHT_INK, pale, OnGlass.TARGET_LARGE_TEXT, 0xE8);
            int resting = GlassInk.legibleOn(band, LIGHT_INK, pale, OnGlass.TARGET_DECORATION,
                restingAlpha);
            assertTrue(hex(band) + ": the page being shown, "
                    + OnGlass.ratio(GlassInk.effective(active, band), band),
                OnGlass.ratio(GlassInk.effective(active, band), band)
                    >= OnGlass.TARGET_LARGE_TEXT - 0.001d);
            assertTrue(hex(band) + ": and the ones at rest, "
                    + OnGlass.ratio(GlassInk.effective(resting, band), band),
                OnGlass.ratio(GlassInk.effective(resting, band), band)
                    >= OnGlass.TARGET_DECORATION - 0.001d);
            assertTrue(hex(band) + ": the active tick still out-reads the resting one",
                OnGlass.ratio(GlassInk.effective(active, band), band)
                    > OnGlass.ratio(GlassInk.effective(resting, band), band));
        }
    }

    /**
     * The app-launch wave over the dock's glass. It is decoration — it says a thing was launched,
     * and it is gone in half a second — so it takes the decoration floor, and on a pale band it now
     * breaks darker instead of being floored bright.
     */
    @Test
    public void theLaunchRippleBreaksAgainstTheBandItIsOn() {
        int[] iconHues = {0xFF2E7D32, 0xFFFFD54F, 0xFF9E9E9E};   // a green, a pale amber, a grey
        for (int band : new int[] {AZ_GLASS, NIGHT_GLASS, PALE_GLASS}) {
            boolean pale = SchemeTone.tone(band) < 50d;
            for (int iconHue : iconHues) {
                int wave = GlassInk.legibleOn(band, iconHue, pale, OnGlass.TARGET_DECORATION, 255);
                assertTrue(hex(band) + "/" + hex(iconHue) + ": " + OnGlass.ratio(wave, band),
                    OnGlass.ratio(wave, band) >= OnGlass.TARGET_DECORATION - 0.001d);
            }
        }
        // The floors could only ever return a brighter colour. On a pale band a pale icon now
        // breaks darker instead, which is the direction the whole round is about.
        int amberOnPale = GlassInk.legibleOn(PALE_GLASS, 0xFFFFD54F, false,
            OnGlass.TARGET_DECORATION, 255);
        assertTrue("a pale band darkens the wave rather than brightening it",
            SchemeTone.tone(amberOnPale) < SchemeTone.tone(0xFFFFD54F));
    }

    // ------------------------------------------------------------------ the band with no glass

    /**
     * The A&ndash;Z strip's own answer, through the accessor the activity uses. The strip's view
     * background is transparent, so a veil it asked for would be drawn by nobody: the ink has to
     * move instead, and {@link GlassInk#bareBandInk} is where that is decided. It still votes, so
     * the strip counts towards the chrome's one polarity.
     */
    @Test
    public void theStripResolvesBareBecauseItHasNoGlassToVeil() {
        for (int wallpaper : new int[] {AZ_GLASS, BAR_GLASS, PALE_GLASS, 0xFF9AA0A6}) {
            ChromeInk ink = inkOver(wallpaper);
            OnGlass.Resolution letters = GlassInk.bareBandInk(ink,
                GlassBackdropCache.Band.AZ_STRIP, AZ_RECT, Color.TRANSPARENT,
                LIGHT_INK, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);

            assertTrue(hex(wallpaper) + ": nothing is spent on the wallpaper", letters.isBare());
            assertTrue(hex(wallpaper) + ": and the letters read anyway — " + letters.ratio,
                letters.ratio >= OnGlass.TARGET_LARGE_TEXT);

            int halo = GlassInk.halo(letters.ink, letters.surface, false);
            assertTrue(hex(wallpaper) + ": with a halo that stays under them",
                OnGlass.ratio(GlassInk.effective(halo, letters.surface), letters.surface)
                    <= letters.ratio);
            assertEquals(hex(wallpaper) + ": and the halo follows the chrome's polarity",
                ink.polarity() == ChromeInk.Polarity.PALE_INK
                    ? GlassInk.HALO_DARK & 0x00FFFFFF : GlassInk.HALO_PALE & 0x00FFFFFF,
                halo & 0x00FFFFFF);
        }
    }

    /** On the reporting device's own strip the chrome is in the pale ink, and the letters clear 3.0. */
    @Test
    public void theReportingDevicesStripReadsAfterTheRound() {
        ChromeInk ink = inkOver(AZ_GLASS);
        OnGlass.Resolution letters = GlassInk.bareBandInk(ink, GlassBackdropCache.Band.AZ_STRIP,
            AZ_RECT, Color.TRANSPARENT, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);

        assertEquals(ChromeInk.Polarity.PALE_INK, ink.polarity());
        assertTrue("1.21:1 before, " + letters.ratio + " after",
            letters.ratio >= OnGlass.TARGET_LARGE_TEXT);
        assertFalse("and the letter is no longer the floored accent",
            letters.ink == SHIPPED_LETTER);
    }

    // ------------------------------------------------------------------ fixtures

    private ChromeInk inkOver(@ColorInt int wallpaper) {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = LIGHT_BASE;
        ChromeInk ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), null);
        ink.backdrops().setSampler(rect -> wallpaper);
        return ink;
    }

    @NonNull
    private static String hex(@ColorInt int color) {
        return "#" + String.format("%06X", color & 0x00FFFFFF);
    }

    @Before
    public void setUp() {
        // Robolectric's Color needs an application; the fixtures above are all it takes.
    }
}
