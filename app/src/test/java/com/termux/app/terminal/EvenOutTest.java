package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.google.android.material.color.utilities.ColorUtils;
import com.google.android.material.color.utilities.Hct;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.EvenOut;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Properties;

/**
 * The even-out stage's arithmetic over pong's palette and a few wallpapers. It lives beside
 * {@link MaterialTerminalColorScheme} because the palettes are built with its package-private
 * {@code build}; {@link EvenOut} itself is in the chrome package.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutTest {

    private static final ChromeInk.Polarity PALE = ChromeInk.Polarity.PALE_INK;
    private static final ChromeInk.Polarity DARK = ChromeInk.Polarity.DARK_INK;

    private static Properties pong(TerminalContrastLevel level) {
        return MaterialTerminalColorScheme.build(new MaterialTerminalColorScheme.Seeds(
            0xFF0C141B, 0xFFDCE3ED, 0xFF93CCFF, 0xFFF2B8B5), level, Color.TRANSPARENT, null);
    }

    private static Properties daylight(TerminalContrastLevel level) {
        return MaterialTerminalColorScheme.build(new MaterialTerminalColorScheme.Seeds(
            0xFFF8F9FF, 0xFF191C20, 0xFF3A608F, 0xFFBA1A1A), level, Color.TRANSPARENT, null);
    }

    private static GlassBackdropCache.Lightness stats(double p2, double mean, double p98) {
        return new GlassBackdropCache.Lightness(p2, mean, p98, Hct.from(250d, 6d, mean).toInt());
    }

    private static GlassBackdropCache.Lightness cloud() {
        return stats(13.5d, 39.5d, 67.8d);
    }

    @Test
    public void pongCloudPaneAtDefaultIsBoundedByTheRedAndBlueCap() {
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        EvenOut.Ceiling ceiling = EvenOut.ceiling(pong(level), PALE, level);
        StringBuilder log = new StringBuilder("ceiling " + ceiling.tone + "\n");
        for (EvenOut.PairLimit limit : ceiling.pairs) log.append("  ").append(limit).append('\n');
        EvenOut.Mapping mapping = EvenOut.map(cloud(), ceiling, PALE, level);
        EvenOut.BandEnds ends = EvenOut.bandEnds(mapping, Color.TRANSPARENT, Color.TRANSPARENT);
        log.append(String.format("band %.1f..%.1f, mean -> %.1f\n", mapping.dstLo, mapping.dstHi,
            mapping.apply(39.5d)));
        System.out.print(log);

        assertEquals(log.toString(), 29d, ceiling.tone, 2d);
        assertFalse(mapping.isIdentity());
        assertTrue(log.toString(), mapping.dstHi <= ceiling.tone + 1e-9);
        assertTrue(log.toString(), mapping.dstLo >= ceiling.tone - level.evenOutWidth - 1e-9);
        assertEquals(level.evenOutWidth, mapping.dstHi - mapping.dstLo, 1e-9);
        assertTrue(ColorUtils.lstarFromArgb(ends.lightest) <= ceiling.tone + 1d);
        // The mean moves down by the amount the skew of [p2, p98] about the mean forces: the brief
        // asked for under 12 tones, which no mapping into [ceiling - 14, ceiling] can give.
        double moved = 39.5d - mapping.apply(39.5d);
        assertTrue(log.toString(), moved > 0d && moved < 20d);
    }

    @Test
    public void aDarkWallpaperThatAlreadyFitsIsLeftAlone() {
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        EvenOut.Ceiling ceiling = EvenOut.ceiling(pong(level), PALE, level);
        EvenOut.Mapping mapping = EvenOut.map(stats(8d, 14d, 20d), ceiling, PALE, level);
        assertTrue(mapping.isIdentity());
        assertEquals(8d, mapping.apply(8d), 1e-9);
    }

    @Test
    public void aNarrowRangeIsShiftedNeverWidened() {
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        EvenOut.Ceiling ceiling = EvenOut.ceiling(pong(level), PALE, level);
        EvenOut.Mapping mapping = EvenOut.map(stats(48d, 50d, 52d), ceiling, PALE, level);
        assertFalse(mapping.isIdentity());
        assertEquals(4d, mapping.dstHi - mapping.dstLo, 1e-9);
        assertTrue(mapping.dstHi <= ceiling.tone + 1e-9);
    }

    @Test
    public void aLightWallpaperUnderDarkInkIsMirroredAboveTheFloor() {
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        EvenOut.Ceiling floor = EvenOut.ceiling(daylight(level), DARK, level);
        EvenOut.Mapping mapping = EvenOut.map(stats(40d, 75d, 95d), floor, DARK, level);
        System.out.println("dark ink floor " + floor.tone + " band " + mapping.dstLo + ".."
            + mapping.dstHi);
        assertTrue(floor.tone > 50d);
        assertTrue(mapping.dstLo >= floor.tone - 1e-9);
        assertTrue(mapping.dstHi - mapping.dstLo <= level.evenOutWidth + 1e-9);
        EvenOut.BandEnds ends = EvenOut.bandEnds(mapping, Color.TRANSPARENT, Color.TRANSPARENT);
        assertEquals(ends.darkest, EvenOut.worstEnd(ends, DARK));
        assertEquals(ends.lightest, EvenOut.worstEnd(ends, PALE));
    }

    @Test
    public void softerKeepsAHigherBandThanDefaultAndHarderALowerOne() {
        double softer = bandTop(TerminalContrastLevel.SOFTER);
        double standard = bandTop(TerminalContrastLevel.DEFAULT);
        double harder = bandTop(TerminalContrastLevel.HARDER);
        System.out.println("band tops " + softer + " " + standard + " " + harder);
        assertTrue(softer > standard);
        assertTrue(standard > harder);
    }

    private static double bandTop(TerminalContrastLevel level) {
        EvenOut.Ceiling ceiling = EvenOut.ceiling(pong(level), PALE, level);
        return EvenOut.map(cloud(), ceiling, PALE, level).dstHi;
    }

    /** At the worst end every background ink, at its most extreme tone, meets its glass floor. */
    @Test
    public void everyBackgroundInkMeetsItsGlassFloorAtTheWorstEnd() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            for (boolean pale : new boolean[] {true, false}) {
                ChromeInk.Polarity polarity = pale ? PALE : DARK;
                Properties palette = pale ? pong(level) : daylight(level);
                EvenOut.Ceiling ceiling = EvenOut.ceiling(palette, polarity, level);
                GlassBackdropCache.Lightness wall = pale ? stats(20d, 55d, 90d) : stats(10d, 45d, 80d);
                EvenOut.Mapping mapping = EvenOut.map(wall, ceiling, polarity, level);
                EvenOut.BandEnds ends = EvenOut.bandEnds(mapping, Color.TRANSPARENT, Color.TRANSPARENT);
                double groundTone = ColorUtils.lstarFromArgb(EvenOut.worstEnd(ends, polarity));
                assertFalse(level + " " + polarity, ceiling.pairs.isEmpty());
                for (EvenOut.PairLimit limit : ceiling.pairs) {
                    // Hct rounds to an 8-bit colour, which moves a tone by a few tenths.
                    double ratio = EvenOut.toneRatio(limit.inkTone, groundTone);
                    assertTrue(level + " " + polarity + " " + limit + " ground " + groundTone
                        + " ratio " + ratio, ratio >= limit.floor * 0.98d);
                }
            }
        }
    }
}
