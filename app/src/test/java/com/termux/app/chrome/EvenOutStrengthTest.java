package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.ColorUtils;
import com.google.android.material.color.utilities.Hct;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.terminal.PaneGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The terminal Opacity scales the even-out: 0% none, 34% and above full, linear between. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutStrengthTest {

    private static final Rect PANE = new Rect(0, 200, 1080, 1800);
    private static final int FG = 0xFFE6E1E5;
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;

    @Test
    public void strengthFollowsOpacity() {
        assertEquals(0, ChromeInk.evenOutStrengthPercent(0));
        assertEquals(50, ChromeInk.evenOutStrengthPercent(17));
        assertEquals(100, ChromeInk.evenOutStrengthPercent(34));
        assertEquals(100, ChromeInk.evenOutStrengthPercent(84));
    }

    @Test
    public void identityChangesOnlyWithinTheScalingRange() {
        EvenOut.Ceiling[] c = MaterialTerminalColorScheme.evenOutCeilings(
            RuntimeEnvironment.getApplication(), LEVEL);
        assertTrue(policy(c, 84).sameAs(policy(c, 60)));
        assertTrue(!policy(c, 20).sameAs(policy(c, 21)));
        assertTrue(!policy(c, 0).sameAs(policy(c, 84)));
    }

    @Test
    public void halfStrengthLandsHalfwayAndFullIsToday() {
        EvenOut.Ceiling[] c = MaterialTerminalColorScheme.evenOutCeilings(
            RuntimeEnvironment.getApplication(), LEVEL);
        double plain = tone(c, 0);
        double half = tone(c, 17);
        double full = tone(c, 34);
        assertEquals(full, tone(c, 84), 0.001);
        assertEquals((plain + full) / 2d, half, 1d);
        assertTrue("the even-out moves the ground", Math.abs(full - plain) > 2d);
    }

    private static ChromeInk.EvenOutPolicy policy(EvenOut.Ceiling[] c, int opacity) {
        return new ChromeInk.EvenOutPolicy(LEVEL, c[0], c[1], ChromeInk.Polarity.PALE_INK,
            ChromeInk.evenOutStrengthPercent(opacity));
    }

    /** The tone of the band's ground the palette is solved against, at one terminal Opacity. */
    private static double tone(EvenOut.Ceiling[] c, int opacity) {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        ChromeInk ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return 0xFF5A5D62;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                return new GlassBackdropCache.Lightness(13.5d, 39.5d, 67.8d,
                    Hct.from(250d, 6d, 39.5d).toInt());
            }
        });
        ink.setEvenOutPolicy(policy(c, opacity));
        ink.terminalPane(PANE, Color.TRANSPARENT, 0xFF101418, FG, PaneGlass.dimTerminalInk(FG));
        assertNotNull(ink);
        return ColorUtils.lstarFromArgb(ink.terminalPaneGround(Color.TRANSPARENT));
    }
}
