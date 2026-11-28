package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.color.utilities.ColorUtils;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.TerminalSurfacePolicy;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Properties;

/**
 * Round 5 review P2-1: Terl's windows in the dark theme take pale ink over a bright wallpaper. The
 * pane band is held to that ink, so it evens out and publishes its ground for pale ink (the
 * brightest pane's), and the palette's pale text is solved there: measured against what the band
 * says the window's text stands on, not the palette's own background (which wallpaper mode never
 * paints).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerlPaleInkBandTest {

    private static final Rect LEFT = new Rect(8, 218, 530, 1671);
    private static final Rect RIGHT = new Rect(550, 218, 1072, 1671);
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;
    /** A thin, light window tint: the case where the band votes dark ink on its own. */
    private static final int TINT = 0x30F0F0F0;
    /** A very bright, pastel wallpaper (the emulator's pink and green, lifted). */
    private static final int BRIGHT = 0xFFFBEFF7;
    private static final int DIMMER = 0xFFEAF6EC;
    private static final int FG = 0xFFE6E1E5;

    private ChromeInk ink;
    private final Object left = new Object();
    private final Object right = new Object();

    @Before
    public void setUp() {
        RuntimeEnvironment.setQualifiers("+night");
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = Color.TRANSPARENT;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return screenRect.left < 540 ? BRIGHT : DIMMER;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                return screenRect.left < 540
                    ? new GlassBackdropCache.Lightness(92d, 96d, 99d, BRIGHT)
                    : new GlassBackdropCache.Lightness(90d, 94d, 97d, DIMMER);
            }
        });
        EvenOut.Ceiling[] ceilings = MaterialTerminalColorScheme.evenOutCeilings(
            RuntimeEnvironment.getApplication(), LEVEL);
        ink.setEvenOutPolicy(new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0], ceilings[1],
            ChromeInk.Polarity.PALE_INK));
    }

    @After
    public void tearDown() {
        RuntimeEnvironment.setQualifiers("+notnight");
    }

    private void measureBoth() {
        ink.terminalPane(LEFT, left, TINT, 0xFF101418, FG, PaneGlass.dimTerminalInk(FG));
        ink.terminalPane(RIGHT, right, TINT, 0xFF101418, FG, PaneGlass.dimTerminalInk(FG));
    }

    private static Context night() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
    }

    @Test
    public void heldToPaleInkTheBandSolvesTheWindowsTextOnItsBrightestGround() {
        measureBoth();
        assertEquals("a bright wallpaper votes dark ink on its own",
            ChromeInk.Polarity.DARK_INK, ink.bandPolarity());

        ink.setForcedPaneInk(TerminalSurfacePolicy.terlWindowInk(true, true, ink.bandPolarity()));
        measureBoth();
        assertEquals(ChromeInk.Polarity.PALE_INK, ink.bandPolarity());
        int ground = ink.terminalPaneGround(TINT);
        // The ground handed to the palette is the pale ink's worst end: the brighter pane's.
        double leftL = ColorUtils.lstarFromArgb(OnGlass.opaque(ground));
        assertTrue("a ground was published", Color.alpha(ground) != 0);

        Properties palette = MaterialTerminalColorScheme.create(night(), LEVEL, ground,
            ink.bandPolarity(), true);
        assertEquals(ChromeInk.Polarity.PALE_INK, MaterialTerminalColorScheme.polarityOf(palette));
        int foreground = Color.parseColor(palette.getProperty("foreground"));
        double contrast = androidx.core.graphics.ColorUtils.calculateContrast(foreground,
            OnGlass.opaque(ground));
        assertTrue("pale text reads on the window's brightest ground (L* " + leftL + "): " + contrast,
            contrast >= OnGlass.TARGET_BODY_TEXT - 0.05d);
    }

    @Test
    public void letGoTheBandVotesAgain() {
        ink.setForcedPaneInk(ChromeInk.Polarity.PALE_INK);
        measureBoth();
        assertEquals(ChromeInk.Polarity.PALE_INK, ink.bandPolarity());
        ink.setForcedPaneInk(null);
        measureBoth();
        assertEquals(ChromeInk.Polarity.DARK_INK, ink.bandPolarity());
    }
}
