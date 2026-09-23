package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.DockGlassRendering;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * One polarity for the whole chrome, and a veil that survives being drawn.
 *
 * <p>Every fixture is a colour measured on the reporting device or read out of the app's palette,
 * so a failure here is a statement about what the user is looking at: "the white mode lacks
 * contrast for right widgets on statusbar and alphabets row looks fuzzy and the sessions chip has
 * visibility issue".</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeInkTest {

    /** {@code termux_primary}, light: an on-white ink. */
    private static final int LIGHT_INK = 0xFF345CA8;
    /** {@code termux_primary}, night: an on-black ink. */
    private static final int NIGHT_INK = 0xFFB8C7FF;
    /** {@code termux_surface_panel_high}, light: what a light-mode band veils toward. */
    private static final int LIGHT_BASE = 0xFFE1E7F2;

    /** Measured behind the status bar in light mode on the reporting device. */
    private static final int STATUS_GLASS = 0xFF6A5755;
    /** Measured behind the A&ndash;Z strip in light mode: the marginal band. */
    private static final int AZ_GLASS = 0xFF657271;

    /**
     * The band on the user's current wallpaper, measured off the rendered pixels on pong: a bright
     * warm wallpaper leaves the status band mid-tone, Y 0.29. Neither {@link #STATUS_GLASS} nor
     * {@link #AZ_GLASS} reaches this part of the range, and it is the part where a role colour of
     * either polarity misses 4.5:1 on its own.
     */
    private static final int MID_WARM_GLASS = 0xFFA8906C;
    /** The Material dark neutral the chrome's glass base is in dark mode. */
    private static final int NIGHT_BASE = 0xFF1C1B1F;
    /** Material You primary for that wallpaper, night: the warm ink the bar was measured drawing. */
    private static final int WARM_INK_NIGHT = 0xFFFCB46C;
    /** Material You primary for it, light. */
    private static final int WARM_INK_LIGHT = 0xFF784800;
    /** The accent the light model's sheen is drawn in for that wallpaper. */
    private static final int WARM_ACCENT = 0xFFFFB68F;
    /** A status-bar opacity in the middle of the slider's range. */
    private static final float MID_OPACITY = 0.35f;

    private static final Rect STATUS_RECT = new Rect(0, 0, 1080, 96);
    private static final Rect AZ_RECT = new Rect(1020, 300, 1080, 1800);
    private static final Rect WINDOW_RECT = new Rect(0, 96, 1080, 200);

    /** A sampler the test drives per band, standing in for the pre-blurred wallpaper frame. */
    private static final class Wallpaper implements GlassBackdropCache.Sampler {
        int status = STATUS_GLASS;
        int az = AZ_GLASS;
        int other = STATUS_GLASS;

        @Override
        public int sampleWallpaper(@NonNull Rect screenRect) {
            if (screenRect.equals(STATUS_RECT)) return status;
            if (screenRect.equals(AZ_RECT)) return az;
            return other;
        }
    }

    private FakeChromeSurfaces surfaces;
    private WallpaperBlurCache blurCache;
    private ChromeInk ink;
    private Wallpaper wallpaper;
    private int veilChangeNotices;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = LIGHT_BASE;     // light mode: the launcher's own light panel colour
        blurCache = new WallpaperBlurCache(surfaces);
        ink = new ChromeInk(surfaces, blurCache, () -> veilChangeNotices++);
        wallpaper = new Wallpaper();
        ink.backdrops().setSampler(wallpaper);
    }

    // ------------------------------------------------------------------ the coherence rule

    /**
     * The whole point of the rule: on the reporting device's wallpaper the status bar and the
     * A&ndash;Z strip are both dark, so both take the pale ink. Before the rule the arithmetic
     * resolved them apart and could put dark ink on one and pale ink on the other.
     */
    @Test
    public void everyBandTakesTheSameInkPolarity() {
        OnGlass.Resolution status = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        OnGlass.Resolution az = ink.onGlass(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);

        assertEquals(ChromeInk.Polarity.PALE_INK, ink.polarity());
        assertTrue("the status bar reads in the pale ink, bare",
            OnGlass.ratio(status.ink, status.surface) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("and so does the strip a few hundred pixels below it",
            OnGlass.ratio(az.ink, az.surface) >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue("both inks are pale, not one of each",
            isPale(status.ink, status.surface) && isPale(az.ink, az.surface));
    }

    /**
     * The A&ndash;Z strip is the marginal band: the pale ink measures 2.99:1 against its 3.0 target
     * on this wallpaper, so resolving it alone is a coin flip. It must not be the band that decides
     * what colour the rest of the chrome is.
     */
    @Test
    public void theMarginalStripDoesNotDecideTheChromesPolarity() {
        // The strip asks first and alone, then again with the sample one RGB unit either way.
        ink.onGlass(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_LARGE_TEXT);
        ChromeInk.Polarity settled = ink.polarity();

        for (int nudge = -3; nudge <= 3; nudge++) {
            wallpaper.az = shift(AZ_GLASS, nudge);
            ink.invalidate();   // a fresh sample of the same wallpaper, as a rotation would take
            ink.onGlass(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT, LIGHT_INK, NIGHT_INK,
                OnGlass.TARGET_LARGE_TEXT);
            assertEquals("a sample that moves by a unit must not repaint the chrome",
                settled, ink.polarity());
        }
    }

    /**
     * The strip's own answer on the reporting device, on both sides of its 3.0 target. Bare it
     * measures 3.01 — the hundredth the phase-0 author flagged — and a sample three RGB units
     * lighter puts it under. Either way the wallpaper is left alone and the letter stays pale: a
     * veil toward a light base would only make a dark band worse for a pale ink, so the ink moves
     * instead. The coin flip is now about a tone, not about which half of the palette the chrome is
     * drawn from.
     */
    @Test
    public void theMarginalStripKeepsItsAnswerOnEitherSideOfItsTarget() {
        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        OnGlass.Resolution measured = ink.onGlass(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);

        assertTrue("bare, the strip clears 3.0 by a hundredth: " + measured.ratio,
            measured.isBare() && measured.ratio >= OnGlass.TARGET_LARGE_TEXT);
        assertEquals("with the mode's own pale ink, untouched", NIGHT_INK, measured.ink);

        wallpaper.az = shift(AZ_GLASS, 3);   // the same wash, sampled a shade lighter
        ink.invalidate();
        OnGlass.Resolution nudged = ink.onGlass(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);

        assertTrue("the pale ink is now a hundredth short, so something had to move",
            OnGlass.ratio(NIGHT_INK, nudged.surface) < OnGlass.TARGET_LARGE_TEXT);
        assertTrue("but not the wallpaper", nudged.isBare());
        assertTrue("the ink moved, and stayed pale",
            nudged.ratio >= OnGlass.TARGET_LARGE_TEXT && isPale(nudged.ink, nudged.surface));
        assertEquals("and the chrome did not change sides", ChromeInk.Polarity.PALE_INK,
            ink.polarity());
    }

    /**
     * Nothing about the rule freezes the chrome into one answer: a genuinely light wallpaper still
     * gets the dark ink, and the flip is a whole-chrome flip.
     */
    @Test
    public void aLightWallpaperFlipsTheWholeChromeToTheDarkInk() {
        wallpaper.status = 0xFFDDDDDD;
        wallpaper.az = 0xFFDDDDDD;
        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        OnGlass.Resolution az = ink.onGlass(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT);

        assertEquals(ChromeInk.Polarity.DARK_INK, ink.polarity());
        assertFalse(isPale(az.ink, az.surface));
    }

    /** The dead band is symmetric and wide enough that a wash cannot walk across it and back. */
    @Test
    public void polarityDoesNotOscillateAcrossTheCrossover() {
        // A grey a hair either side of the black/white crossover (0.179 relative luminance).
        int justBelow = 0xFF787878;
        int justAbove = 0xFF7C7C7C;
        wallpaper.status = justBelow;
        wallpaper.az = justBelow;
        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        ChromeInk.Polarity settled = ink.polarity();

        for (int i = 0; i < 6; i++) {
            wallpaper.status = (i % 2 == 0) ? justAbove : justBelow;
            ink.invalidate();
            ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
                OnGlass.TARGET_BODY_TEXT);
            assertEquals("four RGB units either side of the crossover is not a repaint",
                settled, ink.polarity());
        }
    }

    // ------------------------------------------------------------------ the veil, as drawn

    /**
     * The promise the whole round rests on: whatever veil was resolved, the surface the band is
     * actually <em>drawn</em> as — base layer, light model with its dark foot, veil — still clears
     * the target at the worst row of the band. Composed here out of the real drawable's own layers.
     */
    @Test
    public void theComposedSurfaceKeepsTheVeilFloorAtTheWorstRow() {
        // A mid-light wallpaper: the light-mode ink is the right one and it cannot read bare.
        wallpaper.status = 0xFF9A9A9A;
        wallpaper.other = 0xFF9A9A9A;
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        float opacity = 0.5f;

        // Frame one: the surface is built, which is what tells the ink what glass this band is.
        glass.surface(opacity, 0f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.STATUS_BAR);
        OnGlass.Resolution resolved = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        assertEquals(ChromeInk.Polarity.DARK_INK, ink.polarity());
        assertFalse("this band cannot read bare, so it must have bought a veil", resolved.isBare());
        assertEquals("the veil is the band's own base colour, never white",
            LIGHT_BASE & 0x00FFFFFF, resolved.veil & 0x00FFFFFF);

        // Frame two: the veil the ink asked for is now a layer of the drawn surface.
        LayerDrawable drawn = (LayerDrawable) glass.surface(opacity, 0f, 1f, true, 0, 0f, false,
            GlassBackdropCache.Band.STATUS_BAR);
        assertEquals("base, light model, veil", 3, drawn.getNumberOfLayers());
        assertEquals(resolved.veil,
            ((GradientDrawable) drawn.getDrawable(2)).getColor().getDefaultColor());

        int composed = composeDrawnBand(drawn, wallpaper.status, opacity, 0f, 1f, resolved.ink);
        assertTrue("the drawn band must keep the promise the measurement made: "
                + OnGlass.ratio(resolved.ink, composed),
            OnGlass.ratio(resolved.ink, composed) >= OnGlass.TARGET_BODY_TEXT);
    }

    /**
     * A band that needs nothing gets nothing: no veil layer, and the wallpaper untouched. This is
     * the reporting device's own status bar — the measurement was taken of the glass as drawn, so
     * the band is modelled here at the opacity that reproduces it.
     */
    @Test
    public void aBandThatReadsBareGrowsNoVeilLayer() {
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        glass.surface(0f, 0f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.STATUS_BAR);
        OnGlass.Resolution resolved = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);

        assertTrue("a dark band takes the pale ink and keeps the user's wallpaper",
            resolved.isBare());
        LayerDrawable drawn = (LayerDrawable) glass.surface(0f, 0f, 1f, true, 0, 0f, false,
            GlassBackdropCache.Band.STATUS_BAR);
        assertEquals("base and light model only", 2, drawn.getNumberOfLayers());
    }

    /** A veil that appears asks for the pass that draws it, and a settled one asks for nothing. */
    @Test
    public void aChangedVeilAsksForTheRenderThatDrawsIt() {
        wallpaper.status = 0xFF9A9A9A;
        new GlassSurfaceFactory(surfaces, ink)
            .surface(0.5f, 0f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.STATUS_BAR);
        veilChangeNotices = 0;

        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        assertEquals(1, veilChangeNotices);

        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        assertEquals("a settled band repaints without asking for another pass",
            1, veilChangeNotices);
    }

    // ------------------------------------------------------ the mid backdrop, both modes

    /**
     * The wallpaper the user actually has now: a bright warm one, whose band lands mid — Y 0.29,
     * measured off the rendered pixels on pong as {@code #A8906C}. Neither original fixture covers
     * it. A mid band is the hard case, because 4.5:1 there needs an ink that is either very dark or
     * very light and the mode's own role colour is neither: dark mode measured 1.73:1 on the CPU
     * label, 1.77 on RAM and on the weather.
     *
     * <p>The arithmetic was never wrong about it — it resolved a 33% veil of the band's own base
     * colour and an ink that reads 4.5 on the result. What was wrong is that the veil was handed
     * from the resolve to the surface builder across a render pass, and that pass declined to run:
     * a follow-up render only happens when the apply left the dirty ledger changed, and a veil
     * dirties nothing the ledger tracks. So the band was resolved every pass and veiled in none,
     * and the ink was toned for a surface that did not exist. This asserts the thing that would
     * have caught it: the ratio on the surface as <em>drawn</em>.</p>
     */
    @Test
    public void aMidBandReachesItsTargetAsDrawn_inBothModes() {
        for (boolean night : new boolean[] {true, false}) {
            for (boolean paleSeed : new boolean[] {true, false}) {
                setUp();
                surfaces.glassBase = night ? NIGHT_BASE : LIGHT_BASE;
                surfaces.accent = WARM_ACCENT;
                wallpaper.status = MID_WARM_GLASS;
                wallpaper.other = MID_WARM_GLASS;
                int seed = paleSeed ? WARM_INK_NIGHT : WARM_INK_LIGHT;

                GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
                // The order the activity really uses: the surface is dressed by the apply, the
                // bar's ink is measured after it. The veil must survive that, whichever way round.
                glass.surface(MID_OPACITY, 0f, 1f, true, 0, 0f, false,
                    GlassBackdropCache.Band.STATUS_BAR);
                OnGlass.Resolution band = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR,
                    STATUS_RECT, seed, seed, OnGlass.TARGET_BODY_TEXT);
                LayerDrawable drawn = (LayerDrawable) glass.surface(MID_OPACITY, 0f, 1f, true, 0,
                    0f, false, GlassBackdropCache.Band.STATUS_BAR);

                String where = (night ? "dark" : "light") + " mode, "
                    + (paleSeed ? "pale" : "dark") + " seed";
                int composed = composeDrawnBand(drawn, MID_WARM_GLASS, MID_OPACITY, 0f, 1f,
                    band.ink);
                assertTrue(where + ": the band promised " + band + " but as drawn the ink reads "
                        + OnGlass.ratio(band.ink, composed),
                    OnGlass.ratio(band.ink, composed) >= OnGlass.TARGET_BODY_TEXT);
                assertTrue(where + ": the band's own claim has to hold too",
                    band.ratio >= OnGlass.TARGET_BODY_TEXT);
            }
        }
    }

    /**
     * The seam the bug lived in. The surface builder and the bar's content run in an order neither
     * of them chooses, and the builder used to draw whatever the last resolve had left behind — so
     * on the first pass of a mid band it drew nothing. The veil is derived on demand now, from the
     * band's standing question, so both sides get the same answer whichever runs first.
     */
    @Test
    public void theSurfaceBuilderDerivesTheVeilRatherThanInheritingIt() {
        surfaces.glassBase = NIGHT_BASE;
        surfaces.accent = WARM_ACCENT;
        wallpaper.status = MID_WARM_GLASS;
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        glass.surface(MID_OPACITY, 0f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.STATUS_BAR);
        OnGlass.Resolution band = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            WARM_INK_NIGHT, WARM_INK_NIGHT, OnGlass.TARGET_BODY_TEXT);
        assertFalse("a mid band in dark mode has to buy a veil", band.isBare());

        // A fresh sample of the same wallpaper — a rotation, a blur frame landing — clears what the
        // last resolve left behind. The very next surface build must still veil, with no resolve in
        // between: on the device, that rebuild is the only one that ever ran.
        ink.invalidate();
        LayerDrawable drawn = (LayerDrawable) glass.surface(MID_OPACITY, 0f, 1f, true, 0, 0f, false,
            GlassBackdropCache.Band.STATUS_BAR);

        assertEquals("base, light model, veil", 3, drawn.getNumberOfLayers());
        assertEquals(band.veil,
            ((GradientDrawable) drawn.getDrawable(2)).getColor().getDefaultColor());
    }

    /** A band nobody has asked an ink for has nothing to veil for, and stays plain glass. */
    @Test
    public void anUnaskedBandIsNotVeiled() {
        surfaces.glassBase = NIGHT_BASE;
        wallpaper.other = MID_WARM_GLASS;
        LayerDrawable drawn = (LayerDrawable) new GlassSurfaceFactory(surfaces, ink)
            .surface(MID_OPACITY, 0f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.DOCK);

        assertEquals("base and light model only", 2, drawn.getNumberOfLayers());
    }

    /**
     * The three labels the user reported, on the band they were reported on. Dark mode, the bright
     * warm wallpaper's own status band: they measured 1.73, 1.77 and 1.77 against a 4.5 floor. The
     * seeds here are the colours that were actually drawn, which is as close to the device's
     * Material You roles as a fixture can get without the device.
     */
    @Test
    public void theReportedStatsReachTheirFloorOnTheUsersOwnBand() {
        surfaces.glassBase = NIGHT_BASE;
        surfaces.accent = WARM_ACCENT;
        wallpaper.status = MID_WARM_GLASS;
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        glass.surface(MID_OPACITY, 0f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.STATUS_BAR);

        // The strip's owner resolves it once, in the hue of its most saturated content.
        OnGlass.Resolution band = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            WARM_INK_NIGHT, WARM_INK_NIGHT, OnGlass.TARGET_BODY_TEXT);
        LayerDrawable drawn = (LayerDrawable) glass.surface(MID_OPACITY, 0f, 1f, true, 0, 0f, false,
            GlassBackdropCache.Band.STATUS_BAR);

        int[] seeds = {WARM_INK_NIGHT, 0xFFE4C084, 0xFFE4C06C};   // CPU, RAM, weather as drawn
        String[] names = {"CPU", "RAM", "weather"};
        for (int i = 0; i < seeds.length; i++) {
            int label = ink.inkOn(band, seeds[i], seeds[i], OnGlass.TARGET_BODY_TEXT);
            int composed = composeDrawnBand(drawn, MID_WARM_GLASS, MID_OPACITY, 0f, 1f, label);
            assertTrue(names[i] + " reads " + OnGlass.ratio(label, composed) + " as drawn",
                OnGlass.ratio(label, composed) >= OnGlass.TARGET_BODY_TEXT);
            assertTrue(names[i] + " stays on the chrome's own side of the band",
                isPale(label, band.surface) == (ink.polarity() == ChromeInk.Polarity.PALE_INK));
        }
    }

    /**
     * A mid band is where an undirected tone walk is a coin flip: the nearest qualifying tone of a
     * pale seed is paler, of a dark seed darker, and the two land on opposite sides of one strip of
     * glass. Three phases had each grown a directed walk of their own before the primitive existed.
     */
    @Test
    public void twoSeedsOnOneMidBandLandOnTheSameSide() {
        surfaces.glassBase = NIGHT_BASE;
        surfaces.accent = WARM_ACCENT;
        wallpaper.status = MID_WARM_GLASS;
        new GlassSurfaceFactory(surfaces, ink)
            .surface(MID_OPACITY, 0f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.STATUS_BAR);
        OnGlass.Resolution band = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            WARM_INK_NIGHT, WARM_INK_NIGHT, OnGlass.TARGET_BODY_TEXT);
        boolean pale = ink.polarity() == ChromeInk.Polarity.PALE_INK;

        int fromPale = ink.inkOn(band, WARM_INK_NIGHT, WARM_INK_NIGHT, OnGlass.TARGET_LARGE_TEXT);
        int fromDark = ink.inkOn(band, WARM_INK_LIGHT, WARM_INK_LIGHT, OnGlass.TARGET_LARGE_TEXT);

        assertEquals("a pale seed and a dark seed on one band come out on one side",
            isPale(fromPale, band.surface), isPale(fromDark, band.surface));
        assertEquals("and that side is the chrome's", pale, isPale(fromPale, band.surface));
        assertTrue(OnGlass.ratio(fromDark, band.surface) >= OnGlass.TARGET_LARGE_TEXT);
    }

    /** Content that draws its own wash stands on the wash, and says so. */
    @Test
    public void anInkOnAWashIsMeasuredAgainstTheWash() {
        OnGlass.Resolution band = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        // A chip's container: the mode's panel colour at a fair alpha, over the band.
        int ground = OnGlass.opaque(OnGlass.composite(OnGlass.withAlpha(LIGHT_BASE, 200),
            band.surface));

        int onWash = ink.inkOn(band, ground, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);

        assertTrue("the label clears its floor on what it actually stands on",
            OnGlass.ratio(onWash, ground) >= OnGlass.TARGET_BODY_TEXT);
        assertNotEquals("which is not the answer the band alone would have given",
            onWash, ink.inkOn(band, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT));
    }

    // ------------------------------------------------------------------ the foot

    /**
     * The foot is folded into the measurement rather than dropped in light mode: the model's dark
     * bottom is the worst row for a dark ink, and that is the row the veil was bought for. Dropping
     * it ({@code withFoot=false}) would have cost the band its slab depth for a promise the
     * measurement can keep anyway.
     */
    @Test
    public void theFootIsMeasuredAtTheRowItDarkens() {
        int under = 0xFF9A9A9A;
        int top = DockGlassRendering.topSheenAlpha(1f);
        int mid = DockGlassRendering.midSheenAlpha(1f);
        int footed = DockGlassRendering.footAlpha(1f, true);
        int footless = DockGlassRendering.footAlpha(1f, false);
        int baseAlpha = ChromePolicy.dockGlassBaseAlpha(1f);

        float worst = DockGlassRendering.worstLightModelStop(LIGHT_INK, under, LIGHT_BASE,
            baseAlpha, surfaces.accentColor(), top, mid, footed, 0f, 1f);
        assertEquals("a dark ink is worst off where the black foot is", 1f, worst, 0f);

        int withFoot = DockGlassRendering.glassTintAt(1f, LIGHT_BASE, baseAlpha,
            surfaces.accentColor(), top, mid, footed);
        int withoutFoot = DockGlassRendering.glassTintAt(1f, LIGHT_BASE, baseAlpha,
            surfaces.accentColor(), top, mid, footless);
        assertNotEquals("the foot is part of the tint that was measured", withFoot, withoutFoot);
        assertTrue("and it darkens it",
            OnGlass.ratio(Color.BLACK, OnGlass.opaque(withFoot))
                < OnGlass.ratio(Color.BLACK, OnGlass.opaque(withoutFoot)));
    }

    /** A band that renders only a slice of the model answers for its own rows and no others. */
    @Test
    public void aSliceIsOnlyMeasuredOverItsOwnRows() {
        int under = 0xFF9A9A9A;
        int top = DockGlassRendering.topSheenAlpha(1f);
        int mid = DockGlassRendering.midSheenAlpha(1f);
        int foot = DockGlassRendering.footAlpha(1f, true);
        int baseAlpha = ChromePolicy.dockGlassBaseAlpha(1f);

        float upper = DockGlassRendering.worstLightModelStop(LIGHT_INK, under, LIGHT_BASE,
            baseAlpha, surfaces.accentColor(), top, mid, foot, 0f, 0.5f);
        assertTrue("the top slice never reaches the foot", upper <= 0.5f);
    }

    // ------------------------------------------------------------------ the sampler

    /** {@code getPixel} throws on a hardware bitmap, so one is never read that way. */
    @Test
    public void aHardwareFrameIsNotReadDirectly() {
        assertFalse(ChromeInk.readableInSoftware(Bitmap.Config.HARDWARE));
        assertTrue(ChromeInk.readableInSoftware(Bitmap.Config.ARGB_8888));
        assertTrue(ChromeInk.readableInSoftware(Bitmap.Config.RGB_565));
        assertFalse("a config Android will not name is not one to gamble getPixel on",
            ChromeInk.readableInSoftware(null));
    }

    /** The frost the backdrop is drawn through, modelled so a sample matches what is on screen. */
    @Test
    public void theFrostFilterIsModelledOnTheSample() {
        int frosted = ChromeInk.frostColor(STATUS_GLASS);

        assertNotEquals("a sample is taken before the filter, so the filter is applied to it",
            STATUS_GLASS, frosted);
        assertTrue("saturation 1.30 pushes the warm channel out",
            Color.red(frosted) > Color.red(STATUS_GLASS));
        assertTrue("and pulls the others in",
            Color.green(frosted) < Color.green(STATUS_GLASS));
        // The whole correction is a few RGB units on a mid-dark wash, which is the error an
        // unmodelled sample would have carried.
        assertTrue(Math.abs(Color.red(frosted) - Color.red(STATUS_GLASS)) <= 12);
        assertEquals("a grey stays grey: saturation has nothing to boost",
            Color.red(ChromeInk.frostColor(0xFF808080)),
            Color.blue(ChromeInk.frostColor(0xFF808080)));
        assertEquals("and it clamps rather than wrapping", 0xFFFFFFFF,
            ChromeInk.frostColor(0xFFFFFFFF));
    }

    /**
     * Nothing readable yet — no wallpaper frame, a blur still decoding, a live wallpaper the app
     * cannot capture. The band must answer with the mode's nominal glass and no crash, and it must
     * ask again rather than remembering the miss.
     */
    @Test
    public void anUnreadableBandFallsBackAndAsksAgain() {
        // No sampler of its own: ChromeInk's real one, over a fake with no wallpaper frame view.
        ChromeInk bare = new ChromeInk(surfaces, blurCache, null);

        OnGlass.Resolution resolution = bare.onGlass(GlassBackdropCache.Band.STATUS_BAR,
            STATUS_RECT, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);

        assertFalse("nothing was measured", bare.backdrops().hasSample(
            GlassBackdropCache.Band.STATUS_BAR));
        assertEquals("so the band stands on the mode's nominal glass, as it did before this round",
            OnGlass.opaque(LIGHT_BASE), resolution.surface);
        assertEquals("which is what the cache was told to assume",
            LIGHT_BASE, bare.backdrops().fallbackWallpaper());

        // And it re-asks: a band that read UNREADABLE is not a band that gives up.
        bare.backdrops().setSampler(wallpaper);
        bare.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        assertTrue(bare.backdrops().hasSample(GlassBackdropCache.Band.STATUS_BAR));
    }

    // ------------------------------------------------------------------ mode changes

    /** A palette or light/dark change nobody remembered to report still reaches the ink. */
    @Test
    public void aPaletteChangeInvalidatesWithoutBeingTold() {
        ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
        assertTrue(ink.backdrops().hasSample(GlassBackdropCache.Band.STATUS_BAR));

        surfaces.glassBase = 0xFF1C1B1F;   // the app went dark
        ink.onGlass(GlassBackdropCache.Band.WINDOW_BAR, WINDOW_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);

        assertEquals("the new mode's glass is what an unmeasured band now assumes",
            0xFF1C1B1F, ink.backdrops().fallbackWallpaper());
    }

    /** The second ink on a settled band never argues with the first about which way is up. */
    @Test
    public void aSecondInkOnTheSameBandKeepsThePolarity() {
        OnGlass.Resolution status = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);

        int dots = ink.inkOn(status, LIGHT_INK, NIGHT_INK, OnGlass.TARGET_DECORATION);

        assertTrue("the separator dots clear their own, looser tier",
            OnGlass.ratio(dots, status.surface) >= OnGlass.TARGET_DECORATION);
        assertTrue("in the same polarity as the label beside them",
            isPale(dots, status.surface));
    }

    /** The cache and the ink go stale together, on the one callback the blur frames already fire. */
    @Test
    public void droppingTheBlurFramesDropsTheSamples() {
        ChromeRenderer renderer = new ChromeRenderer(surfaces);
        assertSame(renderer.ink().backdrops(), renderer.ink().backdrops());
        renderer.ink().backdrops().setSampler(wallpaper);
        renderer.ink().onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, LIGHT_INK,
            NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        assertTrue(renderer.ink().backdrops().hasSample(GlassBackdropCache.Band.STATUS_BAR));

        renderer.blurCache().clear();

        assertFalse("a sample outlives the frame it was read from exactly never",
            renderer.ink().backdrops().hasSample(GlassBackdropCache.Band.STATUS_BAR));
    }

    // --------------------------------------------- one pane, two bands, one veil

    /**
     * The regression this round is about. Every piece of the status strip's content — the CPU/RAM
     * widgets, the weather, the dots, the lens, the sessions chip — is laid out inside the top
     * pane, whose glass is {@code terminal_window_bar_background}. The band used to be measured on
     * {@code terminal_status_bar_background} instead, which is the strip that continues that glass
     * under the system status bar and holds nothing at all: the veil was derived for, and painted
     * on, a strip nobody stands on.
     */
    @Test
    public void theStatusBandIsMeasuredOnThePaneItsContentStandsOn() {
        View strip = new View(RuntimeEnvironment.getApplication());
        strip.layout(0, 0, 1080, 126);                 // the inset strip, on the reporting device
        View pane = new View(RuntimeEnvironment.getApplication());
        pane.layout(0, 0, 1080, 288);                  // the pane the widgets are laid out in
        surfaces.views.put(com.termux.R.id.terminal_status_bar_background, strip);
        surfaces.views.put(com.termux.R.id.terminal_window_bar_background, pane);

        Rect status = new Rect();
        Rect window = new Rect();
        assertTrue(ink.bandRect(GlassBackdropCache.Band.STATUS_BAR, status));
        assertTrue(ink.bandRect(GlassBackdropCache.Band.WINDOW_BAR, window));

        assertEquals("the status content and the chips stand on one pane, so one rect answers both",
            window, status);
        assertEquals("and it is the pane's height, not the system inset's", 288, status.height());

        // And the strip is not consulted at all: it can go away without moving the answer.
        surfaces.views.remove(com.termux.R.id.terminal_status_bar_background);
        Rect again = new Rect();
        assertTrue(ink.bandRect(GlassBackdropCache.Band.STATUS_BAR, again));
        assertEquals(window, again);
    }

    /**
     * The white strip the user reported. The pane wears the veil its content bought; the strip that
     * continues the pane's glass under the system status bar draws the same tint, frost and light
     * model and no veil, so it stays the wallpaper the user chose instead of a whitish wash with a
     * hard edge where the pane begins.
     */
    @Test
    public void theInsetStripDrawsThePanesGlassWithNoVeilOfItsOwn() {
        wallpaper.other = 0xFF9A9A9A;      // mid-light: the light-mode ink cannot read this bare
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        glass.surface(0.5f, 0.3f, 1f, true, 0, 0f, false, GlassBackdropCache.Band.WINDOW_BAR);
        OnGlass.Resolution pane = ink.onGlass(GlassBackdropCache.Band.WINDOW_BAR, WINDOW_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        assertFalse("this pane cannot read bare, so it must have bought a veil", pane.isBare());

        LayerDrawable paneDrawn = (LayerDrawable) glass.surface(0.5f, 0.3f, 1f, true, 0, 0f, false,
            GlassBackdropCache.Band.WINDOW_BAR);
        assertTrue("the pane draws what its content asked for", hasLayer(paneDrawn, pane.veil));

        LayerDrawable bare = (LayerDrawable) glass.statusBarExtensionSurface(0.5f, 0f, 0.3f, null);
        assertFalse("the strip carries no content, so it carries no veil",
            hasLayer(bare, pane.veil));
        assertEquals("and it is still the same glass: tint and light model",
            surfaces.glassBaseColor() & 0x00FFFFFF,
            ((GradientDrawable) bare.getDrawable(0)).getColor().getDefaultColor() & 0x00FFFFFF);

        // Option B is one constant away: the strip named as continuing the pane repeats its veil.
        LayerDrawable seamless = (LayerDrawable) glass.statusBarExtensionSurface(0.5f, 0f, 0.3f,
            GlassBackdropCache.Band.WINDOW_BAR);
        assertTrue(hasLayer(seamless, pane.veil));
        assertEquals("which is the bare strip plus exactly one layer",
            bare.getNumberOfLayers() + 1, seamless.getNumberOfLayers());
    }

    /**
     * Two bands, one sheet of glass. The strip's content asks in the stats' hues at body contrast
     * and the window chips ask in their neutrals; the pane can only wear one veil, so it wears the
     * stronger demand and both bands are answered on that.
     */
    @Test
    public void onePaneWearsTheStrongerOfTheTwoDemands() {
        surfaces.glassBase = NIGHT_BASE;
        surfaces.accent = WARM_ACCENT;
        wallpaper.other = MID_WARM_GLASS;
        GlassSurfaceFactory glass = new GlassSurfaceFactory(surfaces, ink);
        glass.surface(MID_OPACITY, 0.3f, 1f, true, 0, 0f, false,
            GlassBackdropCache.Band.WINDOW_BAR);

        int chipInk = 0xFFE6E1E5;          // the chips' pale neutral
        OnGlass.Resolution status = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, WINDOW_RECT,
            WARM_INK_NIGHT, WARM_INK_NIGHT, OnGlass.TARGET_BODY_TEXT);
        OnGlass.Resolution chips = ink.onGlass(GlassBackdropCache.Band.WINDOW_BAR, WINDOW_RECT,
            chipInk, chipInk, OnGlass.TARGET_BODY_TEXT);
        // The strip's own question is asked again after the chips', as the render order does.
        status = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, WINDOW_RECT, WARM_INK_NIGHT,
            WARM_INK_NIGHT, OnGlass.TARGET_BODY_TEXT);

        int soloStatus = Color.alpha(soloVeil(GlassBackdropCache.Band.STATUS_BAR, WARM_INK_NIGHT));
        int soloChips = Color.alpha(soloVeil(GlassBackdropCache.Band.WINDOW_BAR, chipInk));
        assertTrue("the two demands differ, or this fixture proves nothing",
            soloStatus != soloChips);

        assertEquals("one pane, one veil", status.veil, chips.veil);
        assertEquals("and it is the stronger demand", Math.max(soloStatus, soloChips),
            Color.alpha(status.veil));

        // Both bands are toned on the veil that is drawn, not on the one they asked for alone.
        LayerDrawable drawn = (LayerDrawable) glass.surface(MID_OPACITY, 0.3f, 1f, true, 0, 0f,
            false, GlassBackdropCache.Band.WINDOW_BAR);
        assertTrue("the pane draws the settled veil", hasLayer(drawn, status.veil));
        for (OnGlass.Resolution band : new OnGlass.Resolution[] {status, chips}) {
            int composed = composeDrawnBand(drawn, MID_WARM_GLASS, MID_OPACITY, 0.3f, 1f, band.ink);
            assertTrue("as drawn, " + band + " reads " + OnGlass.ratio(band.ink, composed),
                OnGlass.ratio(band.ink, composed) >= OnGlass.TARGET_BODY_TEXT);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** True when any layer of {@code drawn} is a solid pane of exactly {@code color}. */
    private static boolean hasLayer(@NonNull LayerDrawable drawn, @ColorInt int color) {
        if (Color.alpha(color) == 0) return false;
        for (int i = 0; i < drawn.getNumberOfLayers(); i++) {
            Drawable layer = drawn.getDrawable(i);
            if (!(layer instanceof GradientDrawable)) continue;
            android.content.res.ColorStateList fill = ((GradientDrawable) layer).getColor();
            if (fill != null && fill.getDefaultColor() == color) return true;
        }
        return false;
    }

    /**
     * What one band would have asked for with nobody else on its pane: the same fixture, a fresh
     * ink, one question. The pane's own answer is compared against this.
     */
    @ColorInt
    private int soloVeil(@NonNull GlassBackdropCache.Band band, @ColorInt int seed) {
        ChromeInk alone = new ChromeInk(surfaces, blurCache, null);
        alone.backdrops().setSampler(wallpaper);
        new GlassSurfaceFactory(surfaces, alone).surface(MID_OPACITY, 0.3f, 1f, true, 0, 0f, false,
            GlassBackdropCache.Band.WINDOW_BAR);
        return alone.onGlass(band, WINDOW_RECT, seed, seed, OnGlass.TARGET_BODY_TEXT).veil;
    }

    /** True when {@code ink} is the pale one for {@code surface} — lighter than what it stands on. */
    private static boolean isPale(@ColorInt int ink, @ColorInt int surface) {
        return OnGlass.ratio(ink, Color.BLACK) > OnGlass.ratio(surface, Color.BLACK);
    }

    @ColorInt
    private static int shift(@ColorInt int color, int delta) {
        return Color.rgb(
            Math.max(0, Math.min(255, Color.red(color) + delta)),
            Math.max(0, Math.min(255, Color.green(color) + delta)),
            Math.max(0, Math.min(255, Color.blue(color) + delta)));
    }

    /**
     * The band as the screen composes it at its worst row: the wallpaper, then every layer of the
     * drawable in the order a {@link LayerDrawable} draws them.
     */
    @ColorInt
    private int composeDrawnBand(@NonNull LayerDrawable drawn, @ColorInt int wallpaperUnder,
                                 float opacity, float sliceStart, float sliceEnd,
                                 @ColorInt int bandInk) {
        int baseColor = ((GradientDrawable) drawn.getDrawable(0)).getColor().getDefaultColor();
        int veil = drawn.getNumberOfLayers() > 2
            ? ((GradientDrawable) drawn.getDrawable(2)).getColor().getDefaultColor()
            : Color.TRANSPARENT;
        int top = DockGlassRendering.topSheenAlpha(opacity);
        int mid = DockGlassRendering.midSheenAlpha(opacity);
        int foot = DockGlassRendering.footAlpha(opacity, true);
        int worstSurface = Color.WHITE;
        double worstRatio = Double.MAX_VALUE;
        for (float stop : DockGlassRendering.lightModelStops()) {
            if (stop < sliceStart || stop > sliceEnd) continue;
            int model = DockGlassRendering.lightModelColorAt(stop, surfaces.accentColor(),
                top, mid, foot);
            int surface = OnGlass.opaque(OnGlass.composite(veil,
                OnGlass.composite(model, OnGlass.composite(baseColor, OnGlass.opaque(wallpaperUnder)))));
            double ratio = OnGlass.ratio(bandInk, surface);
            if (ratio < worstRatio) {
                worstRatio = ratio;
                worstSurface = surface;
            }
        }
        return worstSurface;
    }

    /**
     * Every chrome pass asks the bar's ink questions again, several times a page change. The
     * answer depends on the question alone, so a remembered one must be exactly what the walk
     * would give, and asking again must not grow the memory.
     */
    @Test
    public void aRememberedInkIsTheInkTheWalkGives() {
        OnGlass.Resolution band = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        boolean pale = ink.polarity() == ChromeInk.Polarity.PALE_INK;
        int[] grounds = {band.surface, 0xFF202020, 0xFFE0E0E0, 0xFF3355AA};
        double[] targets = {OnGlass.TARGET_BODY_TEXT, OnGlass.TARGET_LARGE_TEXT,
            OnGlass.TARGET_DECORATION};
        for (int round = 0; round < 2; round++) {
            for (int ground : grounds) {
                for (double target : targets) {
                    int opaque = OnGlass.opaque(ground);
                    int preferred = pale ? NIGHT_INK : LIGHT_INK;
                    int walked = OnGlass.inkOnBand(band, opaque, preferred, preferred, target, pale);
                    if (OnGlass.ratio(walked, opaque) < target)
                        walked = OnGlass.inkOnBand(band, opaque, LIGHT_INK, NIGHT_INK, target, null);
                    assertEquals(walked, ink.inkOn(band, ground, LIGHT_INK, NIGHT_INK, target));
                }
            }
        }
        assertEquals("the second round asked nothing new",
            grounds.length * targets.length, ink.inkAnswerCountForTests());
    }

    @Test
    public void theInkMemoryStaysBounded() {
        OnGlass.Resolution band = ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            LIGHT_INK, NIGHT_INK, OnGlass.TARGET_BODY_TEXT);
        for (int i = 0; i < ChromeInk.MAX_INK_ANSWERS * 3; i++) {
            ink.inkOn(band, 0xFF000000 | (i * 2654435), LIGHT_INK, NIGHT_INK,
                OnGlass.TARGET_BODY_TEXT);
        }
        assertTrue(ink.inkAnswerCountForTests() <= ChromeInk.MAX_INK_ANSWERS);
    }
}
