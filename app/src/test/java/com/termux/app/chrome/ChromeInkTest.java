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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

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

    // ------------------------------------------------------------------ helpers

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
}
