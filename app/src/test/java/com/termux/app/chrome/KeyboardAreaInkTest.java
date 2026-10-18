package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import juloo.keyboard2.Theme;

/**
 * The keyboard area's labels judged on the glass they stand on (pass 2, 2026-10-17): the A&ndash;Z
 * letters on the dock's glass rather than the wallpaper alone, the extra keys and the in-app
 * keyboard's legends on the measured face rather than the theme's. Over a pale face at opacity
 * 100 and over a dark one, and re-chosen when the opacity moves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class KeyboardAreaInkTest {

    /** The light theme's glass base: white glass at opacity 100 is a pale face. */
    private static final int LIGHT_BASE = 0xFFF7F2FA;
    /** The Material dark neutral. */
    private static final int NIGHT_BASE = 0xFF1C1B1F;
    /** A dark wallpaper, blurred. */
    private static final int DARK_WALLPAPER = 0xFF202226;
    /** The chrome's two inks ({@code termux_primary}, light and night). */
    private static final int DARK_INK = 0xFF345CA8;
    private static final int PALE_INK = 0xFFB8C7FF;
    /** The dark theme's on-surface, the extra keys' theme colour there. */
    private static final int NIGHT_ON_SURFACE = 0xFFE6E1E5;
    /** The light theme's on-surface. */
    private static final int LIGHT_ON_SURFACE = 0xFF1D1B20;

    private static final Rect AZ_RECT = new Rect(0, 2000, 1080, 2060);
    private static final Rect EXTRA_KEYS_RECT = new Rect(0, 2060, 1080, 2160);
    private static final Rect KEYBOARD_RECT = new Rect(0, 1400, 1080, 2000);

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;
    private GlassSurfaceFactory glass;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = LIGHT_BASE;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(rect -> DARK_WALLPAPER);
        glass = new GlassSurfaceFactory(surfaces, ink);
    }

    private static boolean pale(int color) {
        return ColorUtils.calculateLuminance(OnGlass.opaque(color)) > 0.5d;
    }

    @NonNull
    private OnGlass.Resolution azLetters() {
        return GlassInk.bareBandInk(ink, GlassBackdropCache.Band.AZ_STRIP, AZ_RECT,
            Color.TRANSPARENT, DARK_INK, PALE_INK, OnGlass.TARGET_LARGE_TEXT);
    }

    // ------------------------------------------------------------------ the A-Z letters

    @Test
    public void theLettersStandOnTheDocksGlassNotTheWallpaper() {
        OnGlass.Resolution bare = azLetters();
        assertFalse("untold, the strip is the dark wallpaper", pale(bare.surface));

        assertTrue(glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f));
        OnGlass.Resolution onGlass = azLetters();
        assertTrue("a pale face at opacity 100", pale(onGlass.surface));
        assertFalse("dark letters on it", pale(onGlass.ink));
        assertTrue(OnGlass.ratio(onGlass.ink, onGlass.surface) >= OnGlass.TARGET_LARGE_TEXT);
    }

    @Test
    public void theLettersOnADarkFaceArePale() {
        surfaces.glassBase = NIGHT_BASE;
        glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f);
        OnGlass.Resolution letters = azLetters();
        assertFalse(pale(letters.surface));
        assertTrue(pale(letters.ink));
        assertTrue(OnGlass.ratio(letters.ink, letters.surface) >= OnGlass.TARGET_LARGE_TEXT);
    }

    /** The dock's opacity moves: the face is told again and the letters re-chosen on it. */
    @Test
    public void anOpacityChangeReChoosesTheLetters() {
        glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f);
        assertFalse(pale(azLetters().ink));
        assertFalse("the same glass told again is no change",
            glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f));

        assertTrue("a new opacity moves the face",
            glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 0.1f));
        OnGlass.Resolution thin = azLetters();
        assertFalse("thin light glass over a dark wallpaper is a dark face", pale(thin.surface));
        assertTrue("pale letters there", pale(thin.ink));
        assertTrue(OnGlass.ratio(thin.ink, thin.surface) >= OnGlass.TARGET_LARGE_TEXT);
    }

    // ------------------------------------------------------------------ the extra keys

    private int extraKeysInk(int themeInk) {
        int face = ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT);
        return GlassInk.faceInk(face, themeInk, ink.target(OnGlass.TARGET_BODY_TEXT));
    }

    @Test
    public void extraKeysOnAPaleFaceTakeTheDarkSide() {
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 1f);
        int face = ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT);
        assertTrue(pale(face));
        int keys = extraKeysInk(NIGHT_ON_SURFACE);
        assertFalse("the dark theme's pale label moves dark", pale(keys));
        assertTrue(OnGlass.ratio(keys, face) >= OnGlass.TARGET_BODY_TEXT);
        assertEquals("the light theme's own label already reads there: kept", LIGHT_ON_SURFACE,
            extraKeysInk(LIGHT_ON_SURFACE));
    }

    @Test
    public void extraKeysOnADarkFaceTakeThePaleSide() {
        surfaces.glassBase = NIGHT_BASE;
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 1f);
        int face = ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT);
        assertFalse(pale(face));
        assertEquals("the theme's colour where it reads", NIGHT_ON_SURFACE,
            extraKeysInk(NIGHT_ON_SURFACE));
        int keys = extraKeysInk(LIGHT_ON_SURFACE);
        assertTrue("the light theme's dark label moves to the pale side",
            GlassInk.isPaleSide(keys, face));
        assertTrue(OnGlass.ratio(keys, face) >= OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void anOpacityChangeReChoosesTheExtraKeys() {
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 1f);
        int opaque = extraKeysInk(NIGHT_ON_SURFACE);
        assertFalse(pale(opaque));
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 0.1f);
        assertEquals("over the dark wallpaper the theme's pale label is back", NIGHT_ON_SURFACE,
            extraKeysInk(NIGHT_ON_SURFACE));
    }

    // ------------------------------------------------------------------ the keyboard's legends

    private void askKeyboardBand(float opacity) {
        ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, NIGHT_ON_SURFACE,
            NIGHT_ON_SURFACE, Color.TRANSPARENT, OnGlass.TARGET_BODY_TEXT);
        glass.veilOnlyLayer(GlassBackdropCache.Band.KEYBOARD, opacity, 0f, 1f, false);
    }

    private static void assertLegendsRead(@NonNull Theme.Palette palette, int face, String at) {
        int key = ColorUtils.compositeColors(palette.keyBackground, OnGlass.opaque(face));
        assertTrue("label " + at, ColorUtils.calculateContrast(palette.labelColor, key) >= 4.5d);
        assertTrue("sub-label " + at,
            ColorUtils.calculateContrast(palette.subLabelColor, key) >= 4.5d);
        assertTrue("function label " + at + " (" + functionRatio(palette, face) + ")",
            functionRatio(palette, face) >= 4.5d);
    }

    /**
     * The function chip as the keyboard draws it over {@code face}, at each end of its gradient:
     * the fill under the gradient's overlays, with the paint's alpha multiplied in once more.
     */
    @NonNull
    private static int[] drawnFunctionChips(@NonNull Theme.Palette palette, int face) {
        int[] stops = InAppKeyboardPaletteFactory.drawnCapStops(palette.functionKeyBackground,
            palette.keyGradientTopOverlay, palette.keyGradientBottomOverlay);
        int[] chips = new int[stops.length];
        for (int i = 0; i < stops.length; i++)
            chips[i] = ColorUtils.compositeColors(stops[i], OnGlass.opaque(face));
        return chips;
    }

    @Test
    public void theLegendsAreCheckedOnTheMeasuredFace() {
        Context context = RuntimeEnvironment.getApplication();
        for (String variant : new String[] {"system", "light", "dark"}) {
            askKeyboardBand(1f);
            int pale = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
            assertTrue(pale(pale));
            assertLegendsRead(InAppKeyboardPaletteFactory.createGlass(context, variant,
                GlassLook.SCHEME, pale), pale, variant + " on a pale face");

            surfaces.glassBase = NIGHT_BASE;
            askKeyboardBand(1f);
            int dark = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
            assertFalse(pale(dark));
            assertLegendsRead(InAppKeyboardPaletteFactory.createGlass(context, variant,
                GlassLook.SCHEME, dark), dark, variant + " on a dark face");
            surfaces.glassBase = LIGHT_BASE;
        }
    }

    /** Opacity 100 to 10 over a dark wallpaper: the face moves, and the legends follow it. */
    @Test
    public void anOpacityChangeReChoosesTheLegends() {
        Context context = RuntimeEnvironment.getApplication();
        askKeyboardBand(1f);
        int opaque = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
        askKeyboardBand(0.1f);
        int thin = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
        assertNotEquals(opaque, thin);
        assertFalse(pale(thin));
        assertLegendsRead(InAppKeyboardPaletteFactory.createGlass(context, "light",
            GlassLook.SCHEME, thin), thin, "after the opacity change");
    }

    // ------------------------------------------------ see-through light glass, row by row

    /** Bright grass, blurred: what shows through light glass at Opacity 10 near the dock. */
    private static final int BRIGHT_GRASS = 0xFFC8E890;
    /** The near-black blades under the keyboard's bottom row. */
    private static final int DARK_BLADES = 0xFF0A0C06;
    /** The plain sRGB mean of the two, which is what the whole host averages to. */
    private static final int MEAN_GRASS = 0xFF697A4B;
    /** A theme letter cap at its fill and a function chip that lets more glass through. */
    private static final int LETTER_CAP = 0xF0FFFFFF;
    private static final int FUNCTION_CAP = 0x7CE0E0E0;

    /**
     * Bright grass under the top half of the keyboard, black blades under the bottom half; a rect
     * the size of the whole host reads their mean, as a blurred average does.
     */
    private void splitWallpaper() {
        ink.backdrops().setSampler(rect -> rect.height() > KEYBOARD_RECT.height() / 2
            ? MEAN_GRASS : (rect.centerY() < KEYBOARD_RECT.centerY() ? BRIGHT_GRASS : DARK_BLADES));
    }

    /**
     * The keyboard band asked by a dark legend on an opaque cap, which can buy no veil, then its
     * glass told at {@code opacity}: the faces are the glass alone.
     */
    private void askKeyboardGlass(float opacity) {
        ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, LIGHT_ON_SURFACE,
            LIGHT_ON_SURFACE, 0xFFFFFFFF, OnGlass.TARGET_BODY_TEXT);
        glass.veilOnlyLayer(GlassBackdropCache.Band.KEYBOARD, opacity, 0f, 1f, false);
    }

    private static void assertEveryLegendReadsOnEveryRow(@NonNull Theme.Palette palette,
                                                         @NonNull int[] faces, String at) {
        for (int i = 0; i < faces.length; i++) {
            assertLegendsRead(palette, faces[i], at + ", row " + i);
        }
    }

    /** The function legend's contrast on its drawn chip over {@code face}, at its worst end. */
    private static double functionRatio(@NonNull Theme.Palette palette, int face) {
        double worst = Double.MAX_VALUE;
        for (int chip : drawnFunctionChips(palette, face))
            worst = Math.min(worst, ColorUtils.calculateContrast(palette.functionLabelColor, chip));
        return worst;
    }

    /** Light glass at Opacity 10 over bright grass: a pale face, so every legend is dark. */
    @Test
    public void brightGrassUnderSeeThroughGlassTakesDarkLegends() {
        Context context = RuntimeEnvironment.getApplication();
        ink.backdrops().setSampler(rect -> BRIGHT_GRASS);
        askKeyboardGlass(0.1f);
        int[] faces = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        assertEquals(ChromeInk.FACE_ROWS, faces.length);
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, "light",
            GlassLook.SCHEME, faces);
        assertFalse("dark letters", pale(palette.labelColor));
        assertFalse("dark Ctrl and Alt", pale(palette.functionLabelColor));
        assertEveryLegendReadsOnEveryRow(palette, faces, "on bright grass");
    }

    /** The same glass over dark blades: every legend still reads on its chip on every row. */
    @Test
    public void darkBladesUnderSeeThroughGlassKeepEveryLegendLegible() {
        Context context = RuntimeEnvironment.getApplication();
        ink.backdrops().setSampler(rect -> DARK_BLADES);
        askKeyboardGlass(0.1f);
        int[] faces = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        assertFalse(pale(faces[0]));
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, "light",
            GlassLook.SCHEME, faces);
        assertEveryLegendReadsOnEveryRow(palette, faces, "on dark blades");
    }

    /**
     * The dock's labels on see-through light glass: dark over bright grass, pale over the dark
     * blades, whichever theme colour they started from.
     */
    @Test
    public void seeThroughDockLabelsFollowTheWallpaperUnderThem() {
        ink.backdrops().setSampler(rect -> BRIGHT_GRASS);
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 0.1f);
        int onBright = extraKeysInk(NIGHT_ON_SURFACE);
        assertFalse("dark labels on bright grass", pale(onBright));
        assertTrue(OnGlass.ratio(onBright,
            ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT)) >= 4.5d);

        ink.backdrops().setSampler(rect -> DARK_BLADES);
        ink.invalidate();
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 0.1f);
        int onDark = extraKeysInk(LIGHT_ON_SURFACE);
        int darkFace = ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT);
        assertTrue("pale labels on the dark blades", GlassInk.isPaleSide(onDark, darkFace));
        assertTrue(OnGlass.ratio(onDark, darkFace) >= 4.5d);
    }

    /**
     * The device failure: bright grass under the top rows, black blades under Ctrl and Alt. The
     * host's mean passed a dark function legend that the bottom row then could not show; the rows
     * measured on their own put the legend on the side that reads where it is weakest.
     */
    @Test
    public void eachRowIsMeasuredOnItsOwnWallpaper() {
        Context context = RuntimeEnvironment.getApplication();
        splitWallpaper();
        askKeyboardGlass(0.1f);
        int[] faces = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        assertTrue("the top row stands on bright grass", pale(faces[0]));
        assertFalse("the bottom row on the dark blades", pale(faces[faces.length - 1]));
        int bottom = faces[faces.length - 1];

        Theme.Palette onMean = InAppKeyboardPaletteFactory.createGlass(context, "light",
            GlassLook.SCHEME, ink.bandFace(GlassBackdropCache.Band.KEYBOARD));
        Theme.Palette onRows = InAppKeyboardPaletteFactory.createGlass(context, "light",
            GlassLook.SCHEME, faces);
        assertTrue("the function legend reads better on the bottom row than the mean's did",
            functionRatio(onRows, bottom) > functionRatio(onMean, bottom));
        double worst = Double.MAX_VALUE;
        for (int face : faces) worst = Math.min(worst, functionRatio(onRows, face));
        double worstWhite = Double.MAX_VALUE;
        double worstBlack = Double.MAX_VALUE;
        for (int face : faces) {
            for (int chip : drawnFunctionChips(onRows, face)) {
                worstWhite = Math.min(worstWhite, ColorUtils.calculateContrast(Color.WHITE, chip));
                worstBlack = Math.min(worstBlack, ColorUtils.calculateContrast(Color.BLACK, chip));
            }
        }
        assertTrue("4.5 on every row, or the best any tone can do on the worst row",
            worst >= 4.5d || Math.abs(worst - Math.max(worstWhite, worstBlack)) < 1e-6);
    }

    /**
     * A legend that no tone carries on every row: the band's veil is bought on the row where it
     * reads worst, for the function legend on its own chip, so the veil rises.
     */
    @Test
    public void theVeilRisesForTheFunctionLegendOnItsWorstRow() {
        splitWallpaper();
        glass.veilOnlyLayer(GlassBackdropCache.Band.KEYBOARD, 0.1f, 0f, 1f, false);
        OnGlass.Resolution lettersOnly = ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD,
            KEYBOARD_RECT, LIGHT_ON_SURFACE, LIGHT_ON_SURFACE, LETTER_CAP,
            OnGlass.TARGET_BODY_TEXT);
        assertTrue("a near-opaque letter cap needs no veil", lettersOnly.isBare());

        OnGlass.Resolution withFunctions = ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD,
            KEYBOARD_RECT, LIGHT_ON_SURFACE, LETTER_CAP, LIGHT_ON_SURFACE, FUNCTION_CAP,
            OnGlass.TARGET_BODY_TEXT);
        assertFalse("the dark function legend on its thin chip over the black row buys a veil",
            withFunctions.isBare());
        int[] faces = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        int bottomChip = ColorUtils.compositeColors(FUNCTION_CAP, faces[faces.length - 1]);
        assertTrue("the veil is in the face the palette is handed, and it carries the legend",
            ColorUtils.calculateContrast(LIGHT_ON_SURFACE, bottomChip) >= 4.45d);
    }

    /**
     * The editor's Done after a live Opacity drag: the faces handed over follow the glass told on
     * that pass, never a face kept from the preview, and asking again costs no sample.
     */
    @Test
    public void theFacesFollowTheCommittedGlassAndAreSampledOnce() {
        int[] reads = {0};
        ink.backdrops().setSampler(rect -> {
            reads[0]++;
            return BRIGHT_GRASS;
        });
        askKeyboardGlass(1f);
        int[] opaque = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        int afterFirst = reads[0];
        assertTrue(afterFirst > 0);
        assertTrue(java.util.Arrays.equals(opaque,
            ink.bandFaces(GlassBackdropCache.Band.KEYBOARD)));
        assertEquals("asked again on the same rect and wallpaper: no sample", afterFirst, reads[0]);

        askKeyboardGlass(0.1f);
        int[] thin = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        assertFalse("the committed opacity moves every row's face",
            java.util.Arrays.equals(opaque, thin));
        assertEquals("an opacity change is arithmetic, not a sample", afterFirst, reads[0]);

        ink.backdrops().setSampler(rect -> {
            reads[0]++;
            return DARK_BLADES;
        });
        ink.invalidate();
        askKeyboardGlass(0.1f);
        int[] newWallpaper = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        assertTrue("a new wallpaper is sampled again", reads[0] > afterFirst);
        assertFalse(pale(newWallpaper[0]));
    }

    /** Not measured yet: the theme's glass base stands in, exactly as before. */
    @Test
    public void anUnmeasuredFaceIsTheThemesBase() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(Color.TRANSPARENT, ink.bandFace(GlassBackdropCache.Band.KEYBOARD));
        Theme.Palette before = InAppKeyboardPaletteFactory.createGlass(context, "dark");
        Theme.Palette unmeasured = InAppKeyboardPaletteFactory.createGlass(context, "dark",
            GlassLook.SCHEME, Color.TRANSPARENT);
        assertEquals(before.labelColor, unmeasured.labelColor);
        assertEquals(before.subLabelColor, unmeasured.subLabelColor);
        assertEquals(before.functionLabelColor, unmeasured.functionLabelColor);
    }

    // ------------------------------------- see-through light glass over dark grass (Opacity 0-9)

    /** Dark green grass, blurred: luminance about 0.06 and 0.11. */
    private static final int DARK_GRASS = 0xFF3C4A1E;
    private static final int DUSK_GRASS = 0xFF506428;

    /**
     * The chip as drawn, pinned to the device (Android 16, light theme, Opacity 0 and 36 over the
     * same grass): a letter cap of 180/255 reached the screen at about 0.48 of its colour and a
     * function chip of 124/255 at about 0.20 to 0.24, not at their fills' 0.71 and 0.49.
     */
    @Test
    public void aCapReachesTheScreenAtAboutItsAlphaSquared() {
        int white46 = 0x2EFFFFFF;
        int black26 = 0x1A000000;
        int[] function = InAppKeyboardPaletteFactory.drawnCapStops(0x7CECE6F0, white46, black26);
        int[] letter = InAppKeyboardPaletteFactory.drawnCapStops(0xB4FFFFFF, white46, black26);
        assertEquals(2, function.length);
        for (int stop : function) {
            assertTrue("function chip drawn alpha " + Color.alpha(stop),
                Color.alpha(stop) > 55 && Color.alpha(stop) < 80);
        }
        for (int stop : letter) {
            assertTrue("letter cap drawn alpha " + Color.alpha(stop),
                Color.alpha(stop) > 120 && Color.alpha(stop) < 145);
        }
        assertEquals("no gradient, no second alpha", 0x7CECE6F0,
            InAppKeyboardPaletteFactory.drawnCapStops(0x7CECE6F0, 0, 0)[0]);
    }

    /**
     * The device failure at Opacity 0 and 9: Ctrl, Alt, Fn, the gear, Shift, Backspace and the
     * right-hand cluster were dark grey on their drawn chips over dark grass. Their legend is now
     * chosen on the chip as drawn over every row's face, so it reads there (pale, or on a raised
     * chip); the letter caps are left as they were.
     */
    @Test
    public void darkGrassUnderSeeThroughLightGlassKeepsTheFunctionLegendsLegible() {
        Context context = RuntimeEnvironment.getApplication();
        Theme.Palette themeOnly = InAppKeyboardPaletteFactory.createGlass(context, "light");
        for (int grass : new int[] {DARK_GRASS, DUSK_GRASS}) {
            double luminance = ColorUtils.calculateLuminance(grass);
            assertTrue("grass luminance " + luminance, luminance > 0.05d && luminance < 0.12d);
            for (float opacity : new float[] {0f, 0.09f}) {
                ink.backdrops().setSampler(rect -> grass);
                ink.invalidate();
                askKeyboardGlass(opacity);
                int[] faces = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
                assertEquals(ChromeInk.FACE_ROWS, faces.length);
                String at = String.format(java.util.Locale.ROOT, "grass %08X at opacity %.2f",
                    grass, opacity);
                for (String variant : new String[] {"system", "light"}) {
                    Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context,
                        variant, GlassLook.SCHEME, faces);
                    assertEveryLegendReadsOnEveryRow(palette, faces, variant + ", " + at);
                }
                Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, "light",
                    GlassLook.SCHEME, faces);
                assertEquals("letter caps keep their fill, " + at, themeOnly.keyBackground,
                    palette.keyBackground);
                if (grass == DARK_GRASS && opacity == 0f
                        && Color.alpha(palette.functionKeyBackground)
                        == Color.alpha(themeOnly.functionKeyBackground)) {
                    // What the device drew: the theme's dark legend on the drawn chip.
                    double themeLegend = Double.MAX_VALUE;
                    for (int face : faces) {
                        for (int chip : drawnFunctionChips(palette, face)) {
                            themeLegend = Math.min(themeLegend,
                                ColorUtils.calculateContrast(LIGHT_ON_SURFACE, chip));
                        }
                    }
                    assertTrue("a dark legend does not read on the chip as drawn, " + at,
                        themeLegend < 4.5d);
                    assertTrue("so the legend is pale, " + at, pale(palette.functionLabelColor));
                }
            }
        }
    }

    /**
     * Bright grass under the top rows and black blades under the bottom row, light glass at
     * Opacity 9: no one tone reads on the drawn function chip over both, so that chip's fill (and
     * only that one) is raised until one does.
     */
    @Test
    public void aFunctionChipNoToneCarriesIsRaisedForItsLegend() {
        Context context = RuntimeEnvironment.getApplication();
        splitWallpaper();
        askKeyboardGlass(0.09f);
        int[] faces = ink.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        Theme.Palette themeOnly = InAppKeyboardPaletteFactory.createGlass(context, "light");
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, "light",
            GlassLook.SCHEME, faces);
        assertTrue("the function chip is filled more than the theme's",
            Color.alpha(palette.functionKeyBackground)
                > Color.alpha(themeOnly.functionKeyBackground));
        assertEquals("its colour is the theme's", themeOnly.functionKeyBackground & 0xFFFFFF,
            palette.functionKeyBackground & 0xFFFFFF);
        assertEquals("letter caps untouched", themeOnly.keyBackground, palette.keyBackground);
        for (int face : faces)
            assertTrue("function legend " + functionRatio(palette, face),
                functionRatio(palette, face) >= 4.5d);
    }

    // ------------------------------------------- Keys 0 at the keyboard's own Glass (2026-10-17)

    /** Grass lit from above under the keyboard's top row. */
    private static final int LIT_GRASS = 0xFFA8C070;
    /** What the rest of the keyboard stands on at a 25 dp blur, the lit grass mixed through. */
    private static final int MIXED_GRASS = 0xFF8A9C60;

    /**
     * The emulator's wallpaper as each frame shows it: at the keyboard's Glass 20 (7 dp) the top
     * row on lit grass and the rest on the dark grass under it; at 25 dp (Glass 75, and the dock's,
     * whose frame the default sample reads) the lit grass mixed through every row.
     */
    private static final class GrassByBlur implements GlassBackdropCache.Sampler {
        @Override public int sampleWallpaper(@NonNull Rect rect) {
            return sampleWallpaper(rect, 25);
        }

        @Override public int sampleWallpaper(@NonNull Rect rect, int blurRadiusDp) {
            boolean topRow = rect.centerY() < KEYBOARD_RECT.top + KEYBOARD_RECT.height() / 4;
            if (topRow) return LIT_GRASS;
            return blurRadiusDp <= 10 ? DARK_GRASS : MIXED_GRASS;
        }
    }

    /**
     * The launcher's round trip at Keys 0, light glass at Opacity 35 drawn at {@code blurDp}: the
     * palette chosen on the faces, the band asked for its letter legend and its function legend as
     * drawn (secondary), the glass told, the faces measured again; four passes, as the dress
     * passes settle.
     */
    @NonNull
    private int[] keysZeroFaces(@NonNull ChromeInk on, int blurDp) {
        Context context = RuntimeEnvironment.getApplication();
        int[] faces = new int[0];
        for (int pass = 0; pass < 4; pass++) {
            Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, "light",
                GlassLook.SCHEME, faces, 0, on.bandTone(GlassBackdropCache.Band.KEYBOARD));
            askAtKeysZero(on, palette);
            on.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.35f, false, 0f, 1f, LIGHT_BASE);
            on.noteBandBlur(GlassBackdropCache.Band.KEYBOARD, blurDp);
            faces = on.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        }
        return faces;
    }

    /**
     * The launcher's ask at Keys 0 (TermuxActivity#noteInAppKeyboardBand): either tone, each
     * side's extreme, the function legend as drawn secondary, on the caps the palette draws. The
     * legends drawn now are not part of the question.
     */
    private static void askAtKeysZero(@NonNull ChromeInk on, @NonNull Theme.Palette palette) {
        on.onFixedInkEitherTone(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT,
            Color.BLACK, Color.WHITE, palette.keyBackground & 0x00FFFFFF,
            InAppKeyboardPaletteFactory.secondaryOf(Color.BLACK),
            InAppKeyboardPaletteFactory.secondaryOf(Color.WHITE),
            palette.functionKeyBackground & 0x00FFFFFF, OnGlass.TARGET_BODY_TEXT);
    }

    /**
     * The emulator failure (light theme, Look › Custom, keyboard at Glass 20, Opacity 35, Keys 0
     * over dark grass): the legends went dark and Ctrl, Alt, 123+, the arrows and Enter all but
     * vanished, while Glass 75 read. The rows were read off the dock's 25 dp frame, which had
     * mixed the lit grass into the keyboard's bottom rows, and the function legends, drawn
     * secondary (toward mid grey), were never judged as drawn. Now the rows are read off the
     * keyboard's own frame, so the faces are the ones a frame of only that blur gives, and on
     * every row the letter legend and the function legends as drawn read at body text (the veil
     * rising where they would not), at Glass 20 and at Glass 75.
     */
    @Test
    public void keysZeroLegendsReadOnTheKeyboardsOwnGlass() {
        Context context = RuntimeEnvironment.getApplication();
        double dark = ColorUtils.calculateLuminance(DARK_GRASS);
        assertTrue("dark grass " + dark, dark > 0.05d && dark < 0.12d);
        for (int blurDp : new int[] {7, 25}) {
            ink.backdrops().setSampler(new GrassByBlur());
            ink.invalidate();
            int[] faces = keysZeroFaces(ink, blurDp);
            assertEquals(ChromeInk.FACE_ROWS, faces.length);
            Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, "light",
                GlassLook.SCHEME, faces, 0, ink.bandTone(GlassBackdropCache.Band.KEYBOARD));

            // The same glass over a wallpaper that only ever shows this blur's picture.
            ChromeInk truth = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
            GrassByBlur grass = new GrassByBlur();
            truth.backdrops().setSampler(rect -> grass.sampleWallpaper(rect, blurDp));
            askAtKeysZero(truth, palette);
            truth.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.35f, false, 0f, 1f,
                LIGHT_BASE);
            int[] drawn = truth.bandFaces(GlassBackdropCache.Band.KEYBOARD);
            assertEquals("Glass at " + blurDp + " dp: the faces are the drawn ones",
                java.util.Arrays.toString(drawn), java.util.Arrays.toString(faces));

            int functionDrawn = InAppKeyboardPaletteFactory.secondaryOf(palette.functionLabelColor);
            for (int i = 0; i < faces.length; i++) {
                String at = blurDp + " dp, row " + i + " on #" + Integer.toHexString(faces[i]);
                double letter = ColorUtils.calculateContrast(palette.labelColor | 0xFF000000,
                    faces[i]);
                double function = ColorUtils.calculateContrast(functionDrawn, faces[i]);
                assertTrue(at + ": letters " + letter, letter >= 4.5d);
                assertTrue(at + ": Ctrl, Alt, arrows " + function, function >= 4.5d);
            }
        }
    }

    // ------------------------------- the keyboard's own frame landing after a stand-in (2026-10-17)

    /** A grey wallpaper, every row alike. */
    private static int grey(int level) {
        return Color.rgb(level, level, level);
    }

    /**
     * The frame at {@code radius} not resident until it lands: until then the band's rows at that
     * radius are unreadable and are read off the default frame, a bright stand-in.
     */
    private static final class LandingGrass implements GlassBackdropCache.Sampler {
        private final int radius;
        int standIn = LIT_GRASS;
        int real = DARK_GRASS;
        boolean landed;

        LandingGrass(int radius) {
            this.radius = radius;
        }

        @Override public int sampleWallpaper(@NonNull Rect rect) {
            return standIn;
        }

        @Override public int sampleWallpaper(@NonNull Rect rect, int blurRadiusDp) {
            if (blurRadiusDp == radius && !landed) return GlassBackdropCache.UNREADABLE;
            return blurRadiusDp == radius ? real : standIn;
        }
    }

    /**
     * The keyboard's side of a face push, as {@code TermuxInAppKeyboard#setGlassFaces} does it:
     * equal faces and tone cost one comparison, a new palette is only handed to the view (a
     * repaint) when an ink it draws moved.
     */
    private static final class FacedKeyboard {
        @NonNull int[] faces = new int[0];
        int tone;
        Theme.Palette applied;
        int repaints;

        FacedKeyboard(int tone) {
            this.tone = tone;
        }

        void setGlassFaces(@NonNull int[] next, int nextTone) {
            if (java.util.Arrays.equals(next, faces) && nextTone == tone) return;
            faces = next.clone();
            tone = nextTone;
            Theme.Palette palette = paletteOn(faces, tone);
            if (applied != null && sameInks(applied, palette)) return;
            applied = palette;
            repaints++;
        }

        @NonNull Theme.Palette palette() {
            return applied != null ? applied : paletteOn(faces, tone);
        }

        @NonNull
        private static Theme.Palette paletteOn(@NonNull int[] faces, int tone) {
            return InAppKeyboardPaletteFactory.createGlass(RuntimeEnvironment.getApplication(),
                "light", GlassLook.SCHEME, faces, 0, tone);
        }

        private static boolean sameInks(@NonNull Theme.Palette a, @NonNull Theme.Palette b) {
            return a.labelColor == b.labelColor && a.subLabelColor == b.subLabelColor
                && a.functionLabelColor == b.functionLabelColor
                && a.functionKeyBackground == b.functionKeyBackground
                && a.actionLabelColor == b.actionLabelColor
                && a.actionSubLabelColor == b.actionSubLabelColor;
        }
    }

    /** One launcher dress pass at Keys 0, Opacity 10, the keyboard drawn at {@code blurDp}. */
    private static void dressKeysZero(@NonNull ChromeInk on, @NonNull FacedKeyboard keyboard,
                                      int blurDp) {
        on.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.10f, false, 0f, 1f, LIGHT_BASE);
        on.noteBandBlur(GlassBackdropCache.Band.KEYBOARD, blurDp);
        askAtKeysZero(on, keyboard.palette());
        int[] faces = on.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        keyboard.setGlassFaces(faces, on.bandTone(GlassBackdropCache.Band.KEYBOARD));
    }

    private void assertLegendsRead(@NonNull String at, @NonNull FacedKeyboard keyboard) {
        assertLegendsRead(at, keyboard, 4.5d);
    }

    /**
     * The letters at 4.5:1 on every row's face, and Ctrl, Alt and the arrows as drawn (secondary)
     * at {@code functionFloor}: pale secondary legends are #BFBFBF at best, which dark grass holds
     * under 4.5:1 with no veil.
     */
    private void assertLegendsRead(@NonNull String at, @NonNull FacedKeyboard keyboard,
                                   double functionFloor) {
        Theme.Palette palette = keyboard.palette();
        int functionDrawn = InAppKeyboardPaletteFactory.secondaryOf(palette.functionLabelColor);
        for (int i = 0; i < keyboard.faces.length; i++) {
            String row = at + ", row " + i + " on #" + Integer.toHexString(keyboard.faces[i]);
            assertTrue(row + ": letters", ColorUtils.calculateContrast(
                palette.labelColor | 0xFF000000, keyboard.faces[i]) >= 4.5d);
            double function = ColorUtils.calculateContrast(functionDrawn, keyboard.faces[i]);
            assertTrue(row + ": Ctrl, Alt, arrows " + function, function >= functionFloor);
        }
    }

    @NonNull
    private ChromeInk freshInk(@NonNull GlassBackdropCache.Sampler sampler) {
        ChromeInk on = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        on.backdrops().setSampler(sampler);
        return on;
    }

    /**
     * The emulator failure after d866306bc (light theme, keyboard Keys 0, Opacity 10, Tint 0,
     * Glass 20 to 0, 7 dp to 4 dp): the keyboard asked for a radius nothing had baked, its rows
     * were read off a brighter frame meanwhile and the legends chosen dark there. When the
     * keyboard's own frame (dark grass) lands, the ink names the band once, the faces are measured
     * on it and pushed with exactly one repaint: pale legends, no veil, every legend at 4.5:1. A
     * second landing names nothing and repaints nothing.
     */
    @Test
    public void theKeyboardsOwnFrameLandingReJudgesItsLegends() {
        int blurDp = 4;
        LandingGrass grass = new LandingGrass(blurDp);
        ink.backdrops().setSampler(grass);
        ink.invalidate();
        FacedKeyboard keyboard = new FacedKeyboard(0);
        for (int pass = 0; pass < 4; pass++) dressKeysZero(ink, keyboard, blurDp);
        assertTrue("an unrelated frame landing names nothing while the band's is out",
            ink.onBlurFrameReady().isEmpty());
        assertFalse("on the stand-in the legends were chosen dark",
            pale(keyboard.palette().labelColor));
        int before = keyboard.repaints;

        grass.landed = true;
        assertEquals(java.util.EnumSet.of(GlassBackdropCache.Band.KEYBOARD),
            ink.onBlurFrameReady());
        keyboard.setGlassFaces(ink.bandFaces(GlassBackdropCache.Band.KEYBOARD),
            ink.bandTone(GlassBackdropCache.Band.KEYBOARD));
        assertEquals("exactly one repaint", before + 1, keyboard.repaints);
        assertTrue("letters pale", pale(keyboard.palette().labelColor));
        assertTrue("Ctrl, Alt, arrows pale", pale(InAppKeyboardPaletteFactory.secondaryOf(
            keyboard.palette().functionLabelColor)));
        assertEquals("no veil", Color.TRANSPARENT, ink.bandVeil(GlassBackdropCache.Band.KEYBOARD));
        assertLegendsRead("landed", keyboard, 3.9d);

        assertTrue("a second landing names nothing", ink.onBlurFrameReady().isEmpty());
        keyboard.setGlassFaces(ink.bandFaces(GlassBackdropCache.Band.KEYBOARD),
            ink.bandTone(GlassBackdropCache.Band.KEYBOARD));
        assertEquals("and repaints nothing", before + 1, keyboard.repaints);
    }

    /**
     * Uniform dark grass, light base, Opacity 10, Keys 0: pale legends and no veil, whichever
     * legends were drawn before (dark ones from a bright face, pale ones) and whether the band had
     * chosen a tone already or this is a launch: the answer is the bare face's alone.
     */
    @Test
    public void darkGrassTakesPaleLegendsAndNoVeilFromAnyStart() {
        String launched = null;
        for (int start : new int[] {0, ChromeInk.TONE_DARK, ChromeInk.TONE_PALE}) {
            LandingGrass grass = new LandingGrass(-99);
            ChromeInk on = freshInk(grass);
            FacedKeyboard keyboard = new FacedKeyboard(start);
            if (start != 0) {
                // The tone chosen before: dark on a bright face, pale on dark grass.
                grass.standIn = start == ChromeInk.TONE_DARK ? 0xFFE8EEDA : DARK_GRASS;
                for (int pass = 0; pass < 3; pass++) dressKeysZero(on, keyboard, 0);
                assertEquals(start, on.bandTone(GlassBackdropCache.Band.KEYBOARD));
                assertEquals(start == ChromeInk.TONE_PALE, pale(keyboard.palette().labelColor));
            }
            grass.standIn = DARK_GRASS;
            on.invalidate();
            for (int pass = 0; pass < 3; pass++) dressKeysZero(on, keyboard, 0);
            String at = "from " + start;
            assertEquals(at, ChromeInk.TONE_PALE, on.bandTone(GlassBackdropCache.Band.KEYBOARD));
            assertTrue(at + ": letters pale", pale(keyboard.palette().labelColor));
            assertTrue(at + ": Ctrl pale", pale(InAppKeyboardPaletteFactory.secondaryOf(
                keyboard.palette().functionLabelColor)));
            assertEquals(at + ": no veil", Color.TRANSPARENT,
                on.bandVeil(GlassBackdropCache.Band.KEYBOARD));
            assertLegendsRead(at, keyboard, 3.9d);
            String answer = java.util.Arrays.toString(keyboard.faces) + " "
                + keyboard.palette().labelColor + " " + keyboard.palette().functionLabelColor;
            if (launched == null) launched = answer;
            assertEquals(at + ": the launch's answer", launched, answer);
        }
    }

    /** A bright face: dark legends and no veil. */
    @Test
    public void aBrightFaceTakesDarkLegendsAndNoVeil() {
        LandingGrass grass = new LandingGrass(-99);
        grass.standIn = 0xFFE8EEDA;
        ChromeInk on = freshInk(grass);
        FacedKeyboard keyboard = new FacedKeyboard(ChromeInk.TONE_PALE);
        for (int pass = 0; pass < 3; pass++) dressKeysZero(on, keyboard, 0);
        assertEquals(ChromeInk.TONE_DARK, on.bandTone(GlassBackdropCache.Band.KEYBOARD));
        assertFalse(pale(keyboard.palette().labelColor));
        assertEquals("no veil", Color.TRANSPARENT, on.bandVeil(GlassBackdropCache.Band.KEYBOARD));
        assertLegendsRead("bright", keyboard);
    }

    /**
     * A mid face where neither tone reaches 4.5:1 bare (Ctrl, Alt and the arrows, drawn
     * secondary, are the ones that miss): the tone that reads better there, and a veil of the
     * base just big enough to carry it, not a step more.
     */
    @Test
    public void aMidFaceTakesTheBetterToneAndTheLeastVeil() {
        int wallpaper = grey(0x7A);
        LandingGrass grass = new LandingGrass(-99);
        grass.standIn = wallpaper;
        ChromeInk on = freshInk(grass);
        FacedKeyboard keyboard = new FacedKeyboard(0);
        for (int pass = 0; pass < 3; pass++) dressKeysZero(on, keyboard, 0);

        // The same glass bare: a band asked about an ink on an opaque cap buys no veil.
        ChromeInk bare = freshInk(grass);
        bare.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, Color.WHITE, Color.WHITE,
            0xFF000000, OnGlass.TARGET_BODY_TEXT);
        bare.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.10f, false, 0f, 1f, LIGHT_BASE);
        bare.noteBandBlur(GlassBackdropCache.Band.KEYBOARD, 0);
        int[] bareFaces = bare.bandFaces(GlassBackdropCache.Band.KEYBOARD);
        int darkSecondary = InAppKeyboardPaletteFactory.secondaryOf(Color.BLACK);
        int paleSecondary = InAppKeyboardPaletteFactory.secondaryOf(Color.WHITE);
        double darkBare = Double.MAX_VALUE;
        double paleBare = Double.MAX_VALUE;
        for (int face : bareFaces) {
            darkBare = Math.min(darkBare, ColorUtils.calculateContrast(darkSecondary, face));
            paleBare = Math.min(paleBare, ColorUtils.calculateContrast(paleSecondary, face));
        }
        assertTrue("neither tone reads bare: " + darkBare + " " + paleBare,
            darkBare < 4.5d && paleBare < 4.5d);

        int veil = on.bandVeil(GlassBackdropCache.Band.KEYBOARD);
        int tone = on.bandTone(GlassBackdropCache.Band.KEYBOARD);
        assertEquals("the better tone", darkBare > paleBare ? ChromeInk.TONE_DARK
            : ChromeInk.TONE_PALE, tone);
        assertTrue("a veil carries it", Color.alpha(veil) > 0);
        assertLegendsRead("mid", keyboard, 4.49d);
        // One step less veil and the chosen tone's Ctrl misses on some row.
        int less = OnGlass.withAlpha(veil, Color.alpha(veil) - 1);
        double worstLess = Double.MAX_VALUE;
        for (int face : bareFaces)
            worstLess = Math.min(worstLess, ColorUtils.calculateContrast(
                tone == ChromeInk.TONE_DARK ? darkSecondary : paleSecondary,
                OnGlass.opaque(OnGlass.composite(less, face))));
        assertTrue("the least veil: one step less gives " + worstLess, worstLess < 4.6d);
    }

    /**
     * A face at the line between the tones does not flip on every pass: the wallpaper moving by
     * one step either side of where a launch would choose differently keeps the band's tone.
     */
    @Test
    public void aFaceAtTheLineKeepsItsTone() {
        int line = -1;
        int below = 0;
        for (int level = 0x20; level < 0xE0; level++) {
            LandingGrass grass = new LandingGrass(-99);
            grass.standIn = grey(level);
            ChromeInk on = freshInk(grass);
            FacedKeyboard keyboard = new FacedKeyboard(0);
            dressKeysZero(on, keyboard, 0);
            int tone = on.bandTone(GlassBackdropCache.Band.KEYBOARD);
            if (below != 0 && tone != below) {
                line = level;
                break;
            }
            below = tone;
        }
        assertTrue("a launch changes tone somewhere on the grey ramp", line > 0);

        LandingGrass grass = new LandingGrass(-99);
        grass.standIn = grey(line - 1);
        ChromeInk on = freshInk(grass);
        FacedKeyboard keyboard = new FacedKeyboard(0);
        dressKeysZero(on, keyboard, 0);
        int kept = on.bandTone(GlassBackdropCache.Band.KEYBOARD);
        assertEquals(below, kept);
        for (int pass = 0; pass < 6; pass++) {
            grass.standIn = grey(pass % 2 == 0 ? line : line - 1);
            on.invalidate();
            dressKeysZero(on, keyboard, 0);
            assertEquals("pass " + pass, kept, on.bandTone(GlassBackdropCache.Band.KEYBOARD));
            assertEquals("pass " + pass + ": the legends keep their side",
                kept == ChromeInk.TONE_PALE, pale(keyboard.palette().labelColor));
        }
        assertEquals(kept == ChromeInk.TONE_PALE, pale(keyboard.palette().labelColor));
    }
}
