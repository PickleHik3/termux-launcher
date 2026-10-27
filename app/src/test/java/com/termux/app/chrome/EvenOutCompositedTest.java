package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.ColorUtils;
import com.google.android.material.color.utilities.Hct;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.terminal.PaneGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The even-out limit is applied where the text stands: on the ground composited after the matrix,
 * the frost filter, the launcher's dim and the pane's tint (pong 2026-10-27: the band was placed
 * in the wallpaper's own tone domain and frost + dim + tint lifted it from ~25 to ~59, the dead
 * zone). And a policy change re-dresses a laid-out pane without it moving.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutCompositedTest {

    private static final Rect PANE = new Rect(8, 218, 1072, 2341);
    private static final int FG = 0xFFE6E1E5;
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;
    /** Half a tone for the matrix's affine-in-sRGB error, plus rounding. */
    private static final double TOLERANCE = 0.75d;

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;
    private EvenOut.Ceiling[] ceilings;
    private final int mean = Hct.from(250d, 16d, 63.8d).toInt();

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return mean;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                return new GlassBackdropCache.Lightness(13.4d, 63.8d, 94.7d, mean);
            }
        });
        ceilings = MaterialTerminalColorScheme.evenOutCeilings(RuntimeEnvironment.getApplication(),
            LEVEL);
    }

    private ChromeInk.EvenOutPolicy policy() {
        return new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0], ceilings[1],
            ChromeInk.Polarity.PALE_INK);
    }

    private void measure(int tint) {
        ink.terminalPane(PANE, tint, 0xFF101418, FG, PaneGlass.dimTerminalInk(FG));
    }

    /** The composited tone of the wallpaper tone {@code tone} as the pane draws it. */
    private double drawnTone(@Nullable ColorMatrix matrix, double tone, int dim, int tint) {
        Hct m = Hct.fromInt(mean);
        int pixel = Hct.from(m.getHue(), m.getChroma(), tone).toInt();
        return ColorUtils.lstarFromArgb(OnGlass.backdrop(
            ChromeInk.frostColor(EvenOutMatrix.applyTo(matrix, pixel)), dim, tint));
    }

    private void assertBandOnTheInksSide(int dim, int tint) {
        surfaces.dim = dim;
        ink.setEvenOutPolicy(policy());
        measure(tint);
        ColorMatrix matrix = ink.evenOutMatrix(PANE);
        assertNotNull(matrix);
        double lo = drawnTone(matrix, 13.4d, dim, tint);
        double hi = drawnTone(matrix, 94.7d, dim, tint);
        double paleLimit = ceilings[0].tone - EvenOut.BAND_MARGIN;
        double darkLimit = ceilings[1].tone + EvenOut.BAND_MARGIN;
        System.out.println("composited band " + lo + ".." + hi + " pale<=" + paleLimit
            + " dark>=" + darkLimit);
        boolean paleOk = hi <= paleLimit + TOLERANCE;
        boolean darkOk = lo >= darkLimit - TOLERANCE;
        assertTrue("composited band " + lo + ".." + hi + " is on neither ink's side ("
            + paleLimit + " / " + darkLimit + ")", paleOk || darkOk);
    }

    @Test
    public void theCompositedBandSitsUnderThePaleCeilingForPongLikeStatsAndATintOverIt() {
        // Frost's -6 and 6% contrast, a 30% black dim and the terminal's own 34% tint.
        assertBandOnTheInksSide(0x4D000000, 0x57101418);
        ColorMatrix matrix = ink.evenOutMatrix(PANE);
        double hi = drawnTone(matrix, 94.7d, 0x4D000000, 0x57101418);
        assertTrue("pale ink's band top " + hi, hi <= ceilings[0].tone - EvenOut.BAND_MARGIN
            + TOLERANCE);
    }

    @Test
    public void aLightTintThatKeepsTheGroundAboveThePaleCeilingMovesTheBandToTheDarkSide() {
        // The tint alone holds the ground above the pale ceiling: pale ink is unreachable.
        int dim = Color.TRANSPARENT;
        int tint = 0x80E8ECF2;
        assertBandOnTheInksSide(dim, tint);
        ColorMatrix matrix = ink.evenOutMatrix(PANE);
        double lo = drawnTone(matrix, 13.4d, dim, tint);
        assertTrue("dark ink's band floor " + lo,
            lo >= ceilings[1].tone + EvenOut.BAND_MARGIN - TOLERANCE);
    }

    @Test
    public void aPolicyChangeMakesALaidOutPanesFilterChangeWithoutThePaneMoving() {
        surfaces.dim = 0x4D000000;
        int tint = 0x57101418;
        measure(tint);
        assertNull("switch off: no matrix", ink.evenOutMatrix(PANE));
        int off = ink.evenOutGeneration();

        ink.setEvenOutPolicy(policy());
        assertNotEquals("the pane polls this to re-dress", off, ink.evenOutGeneration());
        measure(tint);
        ColorMatrix on = ink.evenOutMatrix(PANE);
        assertNotNull("switch on: the same rect now has a matrix", on);
        int onGeneration = ink.evenOutGeneration();

        measure(tint);
        assertEquals("a memoised ask moves nothing", onGeneration, ink.evenOutGeneration());

        ink.setEvenOutPolicy(null);
        assertNotEquals(onGeneration, ink.evenOutGeneration());
        measure(tint);
        assertNull("switch off again", ink.evenOutMatrix(PANE));
    }
}
