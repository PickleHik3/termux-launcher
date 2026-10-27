package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
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
 * One ink polarity for the whole pane band, reachability counted with the band's width (pong
 * 2026-10-27: dark was "reachable" in a sub-tone sliver, the wallpaper was pushed to 96..100 and
 * the palette solved on a stale pane's ground).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutBandPolarityTest {

    private static final Rect TALL = new Rect(8, 218, 1072, 1671);
    private static final Rect SHORT = new Rect(8, 218, 1072, 1573);
    private static final int FG = 0xFFE6E1E5;
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;
    private static final int TINT = 0x75213F5F;
    private static final int MEAN = 0xFFB7C9E9;
    private static final double TOLERANCE = 0.75d;

    private ChromeInk ink;
    private EvenOut.Ceiling[] ceilings;

    @Before
    public void setUp() {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = Color.TRANSPARENT;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return MEAN;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                return new GlassBackdropCache.Lightness(57.86d, 80.58d, 94.69d, MEAN);
            }
        });
        ceilings = MaterialTerminalColorScheme.evenOutCeilings(RuntimeEnvironment.getApplication(),
            LEVEL);
        ink.setEvenOutPolicy(new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0], ceilings[1],
            ChromeInk.Polarity.PALE_INK));
    }

    private void measure(Rect rect) {
        ink.terminalPane(rect, TINT, 0xFF101418, FG, PaneGlass.dimTerminalInk(FG));
    }

    private double drawn(@Nullable ColorMatrix matrix, double tone) {
        Hct m = Hct.fromInt(MEAN);
        int pixel = Hct.from(m.getHue(), m.getChroma(), tone).toInt();
        return ColorUtils.lstarFromArgb(OnGlass.backdrop(
            ChromeInk.frostColor(EvenOutMatrix.applyTo(matrix, pixel)), Color.TRANSPARENT, TINT));
    }

    @Test
    public void theLoggedPongCaseKeepsPaleInkAndABandWithRoomUnderTheCeiling() {
        measure(TALL);
        ColorMatrix matrix = ink.evenOutMatrix(TALL);
        assertNotNull(matrix);
        EvenOut.Chain chain = ChromeInk.compositeChain(MEAN, Color.TRANSPARENT, TINT);
        double lo = drawn(matrix, 57.86d);
        double hi = drawn(matrix, 94.69d);
        double limit = ceilings[0].tone - EvenOut.BAND_MARGIN;
        double span = chain.composited(94.69d) - chain.composited(57.86d);
        assertTrue("band top " + hi + " under " + limit, hi <= limit + TOLERANCE);
        assertTrue("band " + lo + ".." + hi + " keeps its width",
            hi - lo >= Math.min(LEVEL.evenOutWidth, span) - TOLERANCE);
        assertTrue("band floor " + lo + " is not under what the chain reaches",
            lo >= chain.composited(0d) - TOLERANCE);
        assertEquals(ChromeInk.Polarity.PALE_INK, ink.bandPolarityForTests());
    }

    @Test
    public void aResizedPaneReplacesItsStaleAnswerAndThePaletteGroundIsTheLiveOnes() {
        measure(TALL);
        measure(SHORT);
        assertEquals("the stale rect is pruned", 1, ink.paneAnswerCountForTests());
        assertNull(ink.evenOutMatrix(TALL));
        ColorMatrix live = ink.evenOutMatrix(SHORT);
        assertNotNull(live);
        double worstEnd = Math.max(drawn(live, 57.86d), drawn(live, 94.69d));
        assertEquals(worstEnd, ColorUtils.lstarFromArgb(ink.terminalPaneGround(TINT)), TOLERANCE);
    }
}
