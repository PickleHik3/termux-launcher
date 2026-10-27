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
    public void pongCloudPaneAtDefaultIsBoundedByTheGreyAndNormalAccents() {
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        EvenOut.Ceiling ceiling = EvenOut.ceiling(pong(level), PALE, level);
        StringBuilder log = new StringBuilder("ceiling " + ceiling.tone + "\n");
        for (EvenOut.PairLimit limit : ceiling.pairs) log.append("  ").append(limit).append('\n');
        EvenOut.Mapping mapping = EvenOut.map(cloud(), ceiling, PALE, level);
        EvenOut.BandEnds ends = EvenOut.bandEnds(mapping, Color.TRANSPARENT, Color.TRANSPARENT);
        log.append(String.format("band %.1f..%.1f, mean -> %.1f\n", mapping.dstLo, mapping.dstHi,
            mapping.apply(39.5d)));
        System.out.print(log);

        assertEquals(log.toString(), 34d, ceiling.tone, 1d);
        assertFalse(mapping.isIdentity());
        assertTrue(log.toString(), mapping.dstHi <= ceiling.tone + 1e-9);
        assertTrue(log.toString(), mapping.dstLo >= ceiling.tone - level.evenOutWidth - 1.0d - 1e-9);
        assertEquals(level.evenOutWidth, mapping.dstHi - mapping.dstLo, 1e-9);
        assertTrue(ColorUtils.lstarFromArgb(ends.lightest) <= ceiling.tone + 1d);
        // The mean sits 48% of the way up [p2, p98], so it maps to about 26.7 in a band of
        // [20, 34]: a move of about 12.8 tones, hence the bound of 14 rather than 12.
        double moved = 39.5d - mapping.apply(39.5d);
        assertTrue(log.toString(), moved > 0d && moved < 14d);
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
    public void aDarkButWideRangeThatFitsIsLeftAloneToo() {
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        EvenOut.Ceiling ceiling = EvenOut.ceiling(pong(level), PALE, level);
        EvenOut.Mapping mapping = EvenOut.map(stats(2d, 15d, 30d), ceiling, PALE, level);
        assertTrue(mapping.isIdentity());
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

    /**
     * The polarity ChromeInk picks from the pane's mean (inkPolarity on the mean flat glass) is the
     * one build derives from the band's worst end, for a pale mean, a dark mean and one at the
     * crossover (tone about 50, where both inks read alike), whichever polarity the palette wears.
     */
    @Test
    public void thePolarityFromTheMeanIsThePolarityBuildDerivesFromTheWorstEnd() {
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        MaterialTerminalColorScheme.Seeds seeds = new MaterialTerminalColorScheme.Seeds(
            0xFF0C141B, 0xFFDCE3ED, 0xFF93CCFF, 0xFFF2B8B5);
        for (double mean : new double[] {39.5d, 75d, 50d}) {
            for (ChromeInk.Polarity current : new ChromeInk.Polarity[] {PALE, DARK}) {
                GlassBackdropCache.Lightness wall = stats(mean - 25d, mean, mean + 25d);
                ChromeInk.Polarity chosen = MaterialTerminalColorScheme.inkPolarity(
                    wall.meanColor, level.bodyTarget, current);
                boolean pale = chosen == PALE;
                Properties recipe = MaterialTerminalColorScheme.build(seeds, level,
                    pale ? 0xFF0A0A0A : 0xFFF5F5F5, null);
                EvenOut.Mapping mapping = EvenOut.map(wall, EvenOut.ceiling(recipe, chosen, level),
                    chosen, level);
                int worst = EvenOut.worstEnd(
                    EvenOut.bandEnds(mapping, Color.TRANSPARENT, Color.TRANSPARENT), chosen);
                Properties built = MaterialTerminalColorScheme.build(seeds, level, worst, current);
                assertEquals("mean " + mean + " current " + current + " worst "
                    + Integer.toHexString(worst), chosen,
                    MaterialTerminalColorScheme.polarityOf(built));
            }
        }
    }

    @Test
    public void softerKeepsAHigherBandThanDefaultAndHarderALowerOne() {
        double softer = bandTop(TerminalContrastLevel.SOFTER);
        double standard = bandTop(TerminalContrastLevel.DEFAULT);
        double harder = bandTop(TerminalContrastLevel.HARDER);
        System.out.println("band tops " + softer + " " + standard + " " + harder);
        // On glass every level's ink reaches at least 10 tones (glassToneReach), so Softer's
        // pastel recipe no longer lowers its ceiling below Default's.
        assertTrue(softer > standard);
        assertTrue(standard > harder);
        assertTrue(widthOf(TerminalContrastLevel.SOFTER) > widthOf(TerminalContrastLevel.DEFAULT));
        assertTrue(widthOf(TerminalContrastLevel.DEFAULT) > widthOf(TerminalContrastLevel.HARDER));
    }

    private static double widthOf(TerminalContrastLevel level) {
        EvenOut.Ceiling ceiling = EvenOut.ceiling(pong(level), PALE, level);
        EvenOut.Mapping mapping = EvenOut.map(cloud(), ceiling, PALE, level);
        return mapping.dstHi - mapping.dstLo;
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
                    // Red and blue are held by the panel pair only on glass (ADR 0014, decided
                    // 2026-10-27); they do not set the ceiling and may miss the ground floor.
                    if (!limit.limiting) continue;
                    // Hct rounds to an 8-bit colour, which moves a tone by a few tenths.
                    double ratio = EvenOut.toneRatio(limit.inkTone, groundTone);
                    assertTrue(level + " " + polarity + " " + limit + " ground " + groundTone
                        + " ratio " + ratio, ratio >= limit.floor);
                }
            }
        }
    }
}
