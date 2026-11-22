package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.terminal.PaneGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The pane band's ink is a function of what is drawn, never of the palette worn at the moment
 * (#228: on the same wallpaper and look the ink flipped pale/dark on every cold start, because
 * both sides read a shortfall of 0 and the tie went to the scheme's current polarity, which
 * depends on whether the scheme loaded before the first pane was measured).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutPaneDecisionTest {

    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;
    private static final Rect PANE = new Rect(8, 218, 1072, 1671);
    private static final Rect OTHER = new Rect(8, 1700, 1072, 2200);
    private static final int PALE_FG = 0xFFE6E1E5;
    private static final int PALE_BG = 0xFF101418;
    /** A blue/cream wallpaper under a light, thin tint: both inks have room (the #228 case). */
    private static final int CREAM = 0xFFD9D2BE;
    private static final GlassBackdropCache.Lightness CREAM_STATS =
        new GlassBackdropCache.Lightness(35d, 70d, 92d, CREAM);
    private static final int LIGHT_TINT = 0x1AF5F5F5;

    private static EvenOut.Ceiling[] ceilings() {
        return MaterialTerminalColorScheme.evenOutCeilings(RuntimeEnvironment.getApplication(),
            LEVEL);
    }

    private static ChromeInk inkOver(@NonNull GlassBackdropCache.Lightness stats,
                                     @NonNull ChromeInk.Polarity current, int strength,
                                     @Nullable Rect unreadable) {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = Color.TRANSPARENT;
        ChromeInk ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                if (unreadable != null && Rect.intersects(screenRect, unreadable)) {
                    return Color.TRANSPARENT;
                }
                return stats.meanColor;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                if (unreadable != null && Rect.intersects(screenRect, unreadable)) return null;
                return stats;
            }
        });
        EvenOut.Ceiling[] c = ceilings();
        ink.setEvenOutPolicy(new ChromeInk.EvenOutPolicy(LEVEL, c[0], c[1], current, strength));
        return ink;
    }

    @Test
    public void theSameWallpaperGivesTheSamePolarityWhateverThePaletteWearsNow() {
        ChromeInk afterPale = inkOver(CREAM_STATS, ChromeInk.Polarity.PALE_INK, 100, null);
        ChromeInk afterDark = inkOver(CREAM_STATS, ChromeInk.Polarity.DARK_INK, 100, null);
        Object pane = new Object();
        afterPale.terminalPane(PANE, pane, LIGHT_TINT, PALE_BG, PALE_FG,
            PaneGlass.dimTerminalInk(PALE_FG));
        afterDark.terminalPane(PANE, pane, LIGHT_TINT, PALE_BG, PALE_FG,
            PaneGlass.dimTerminalInk(PALE_FG));
        assertNotNull(afterPale.bandPolarityForTests());
        assertEquals(afterPale.bandPolarityForTests(), afterDark.bandPolarityForTests());
        assertEquals(afterPale.terminalPaneGround(LIGHT_TINT),
            afterDark.terminalPaneGround(LIGHT_TINT));
    }

    @Test
    public void theSchemeLoadingAfterTheFirstPaneDoesNotMoveThePolarity() {
        ChromeInk ink = inkOver(CREAM_STATS, ChromeInk.Polarity.PALE_INK, 100, null);
        Object pane = new Object();
        ink.terminalPane(PANE, pane, LIGHT_TINT, PALE_BG, PALE_FG,
            PaneGlass.dimTerminalInk(PALE_FG));
        ChromeInk.Polarity first = ink.bandPolarityForTests();
        int ground = ink.terminalPaneGround(LIGHT_TINT);
        // The scheme arrives: only the current polarity of the policy changes.
        EvenOut.Ceiling[] c = ceilings();
        ink.setEvenOutPolicy(new ChromeInk.EvenOutPolicy(LEVEL, c[0], c[1],
            first == ChromeInk.Polarity.PALE_INK ? ChromeInk.Polarity.DARK_INK
                : ChromeInk.Polarity.PALE_INK, 100));
        ink.terminalPane(PANE, pane, LIGHT_TINT, PALE_BG, PALE_FG,
            PaneGlass.dimTerminalInk(PALE_FG));
        assertEquals(first, ink.bandPolarityForTests());
        assertEquals(ground, ink.terminalPaneGround(LIGHT_TINT));
    }

    @Test
    public void anUnmeasuredPaneDoesNotVote() {
        ChromeInk alone = inkOver(CREAM_STATS, ChromeInk.Polarity.DARK_INK, 100, OTHER);
        alone.terminalPane(PANE, new Object(), LIGHT_TINT, PALE_BG, PALE_FG,
            PaneGlass.dimTerminalInk(PALE_FG));
        ChromeInk.Polarity measuredOnly = alone.bandPolarityForTests();

        ChromeInk both = inkOver(CREAM_STATS, ChromeInk.Polarity.DARK_INK, 100, OTHER);
        Object unread = new Object();
        both.terminalPane(OTHER, unread, LIGHT_TINT, PALE_BG, PALE_FG,
            PaneGlass.dimTerminalInk(PALE_FG));
        assertNull("a pane that cannot be read decides nothing", both.bandPolarityForTests());
        both.terminalPane(PANE, new Object(), LIGHT_TINT, PALE_BG, PALE_FG,
            PaneGlass.dimTerminalInk(PALE_FG));
        assertEquals(measuredOnly, both.bandPolarityForTests());
        assertNull(both.evenOutMatrix(OTHER, unread));
    }

    @Test
    public void unreadableDarkInkIsNotChosenOverPaleInkThatMeetsTheTarget() {
        // A dark wallpaper under a thin terminal (Opacity 10%: strength 29%). Pale ink reads on it
        // as it is; dark ink's band can be reached in tone but is drawn at 29% of the way, so the
        // ground it would stand on stays dark. The palette wears dark now (current).
        GlassBackdropCache.Lightness dark = new GlassBackdropCache.Lightness(10d, 20d, 30d,
            0xFF1E2A44);
        ChromeInk ink = inkOver(dark, ChromeInk.Polarity.DARK_INK,
            ChromeInk.evenOutStrengthPercent(10), null);
        OnGlass.Resolution answer = ink.terminalPane(PANE, new Object(), Color.TRANSPARENT,
            PALE_BG, PALE_FG, PaneGlass.dimTerminalInk(PALE_FG));
        assertEquals(ChromeInk.Polarity.PALE_INK, ink.bandPolarityForTests());
        assertTrue("pale ink reads at " + answer.ratio, !answer.shortfall);
        // The side it was not given misses as drawn, though a band with room could be placed.
        EvenOut.Ceiling[] c = ceilings();
        ChromeInk.PaneSide darkSide = ChromeInk.paneSide(dark,
            new ChromeInk.EvenOutPolicy(LEVEL, c[0], c[1], ChromeInk.Polarity.DARK_INK,
                ChromeInk.evenOutStrengthPercent(10)),
            ChromeInk.Polarity.DARK_INK, Color.TRANSPARENT, Color.TRANSPARENT,
            OnGlass.TARGET_BODY_TEXT, OnGlass.TARGET_LARGE_TEXT, 0);
        assertEquals(0d, darkSide.placement, 0d);
        assertTrue("dark ink's drawn ground misses", darkSide.shortfall > 0d);
        assertTrue(darkSide.reference.shortfall);
    }

    @Test
    public void thePureDecisionIgnoresHistoryAndBreaksTiesOnContrastThenPale() {
        ChromeInk.Polarity pale = ChromeInk.Polarity.PALE_INK;
        ChromeInk.Polarity dark = ChromeInk.Polarity.DARK_INK;
        // Only one side reaches as drawn: that side, whatever the totals.
        assertEquals(pale, ChromeInk.decideBand(0d, 2d, 0d, 2d, 1.1d, 0.3d));
        assertEquals(dark, ChromeInk.decideBand(3d, 0d, 3d, 0d, 0.3d, 1.1d));
        // Both reach: the better-reading side; equal reading: pale.
        assertEquals(dark, ChromeInk.decideBand(0d, 0d, 0d, 0d, 1.2d, 2.0d));
        assertEquals(pale, ChromeInk.decideBand(0d, 0d, 0d, 0d, 2.0d, 1.2d));
        assertEquals(pale, ChromeInk.decideBand(0d, 0d, 0d, 0d, 1.5d, 1.5d));
        // Neither reaches: the least total shortfall, then the better-reading side.
        assertEquals(dark, ChromeInk.decideBand(4d, 5d, 6d, 5d, 0.5d, 0.5d));
        assertEquals(dark, ChromeInk.decideBand(4d, 4d, 4d, 4d, 0.5d, 0.6d));
    }

    @Test
    public void aSideMissesAsDrawnExactlyWhenItsInkMissesItsTargetThere() {
        // With a band-text ceiling (white or black at the body target) the drawn shortfall and
        // the resolved ratio are the same promise: one misses exactly when the other does.
        double target = OnGlass.TARGET_BODY_TEXT;
        EvenOut.Ceiling pale = EvenOut.ceilingForTarget(target, ChromeInk.Polarity.PALE_INK);
        EvenOut.Ceiling dark = EvenOut.ceilingForTarget(target, ChromeInk.Polarity.DARK_INK);
        int[] tints = {Color.TRANSPARENT, LIGHT_TINT, 0x57101418, 0x80E8ECF2};
        int[] strengths = {0, 29, 59, 100};
        int checked = 0;
        for (int tint : tints) {
            for (int strength : strengths) {
                ChromeInk.EvenOutPolicy policy = new ChromeInk.EvenOutPolicy(LEVEL, pale, dark,
                    ChromeInk.Polarity.PALE_INK, strength);
                for (double lo = 5d; lo <= 85d; lo += 10d) {
                    for (double span = 0d; span <= 40d; span += 10d) {
                        double hi = Math.min(99d, lo + span);
                        GlassBackdropCache.Lightness stats = new GlassBackdropCache.Lightness(lo,
                            (lo + hi) / 2d, hi, CREAM);
                        for (ChromeInk.Polarity side : ChromeInk.Polarity.values()) {
                            ChromeInk.PaneSide s = ChromeInk.paneSide(stats, policy, side,
                                Color.TRANSPARENT, tint, target, OnGlass.TARGET_LARGE_TEXT, 0);
                            double tone = com.google.android.material.color.utilities.ColorUtils
                                .lstarFromArgb(s.ground);
                            double limit = side == ChromeInk.Polarity.PALE_INK
                                ? pale.tone : dark.tone;
                            if (Math.abs(tone - limit) < 0.5d) continue;
                            assertEquals(side + " at " + lo + ".." + hi + " tint "
                                    + Integer.toHexString(tint) + " strength " + strength
                                    + ": ground " + tone + " ratio " + s.reference.ratio,
                                s.overshoot > 0d, s.reference.shortfall);
                            assertEquals(s.reference.ratio / s.reference.target, s.margin, 1e-9);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 200);
    }

    @Test
    public void thePaneReportsTheRatioOfTheGroundItsSideWasChosenOn() {
        ChromeInk ink = inkOver(CREAM_STATS, ChromeInk.Polarity.DARK_INK, 100, null);
        int dimInk = PaneGlass.dimTerminalInk(PALE_FG);
        OnGlass.Resolution answer = ink.terminalPane(PANE, new Object(), LIGHT_TINT, PALE_BG,
            PALE_FG, dimInk);
        EvenOut.Ceiling[] c = ceilings();
        ChromeInk.PaneSide side = ChromeInk.paneSide(CREAM_STATS,
            new ChromeInk.EvenOutPolicy(LEVEL, c[0], c[1], ChromeInk.Polarity.PALE_INK, 100),
            ink.bandPolarityForTests(), Color.TRANSPARENT, LIGHT_TINT, OnGlass.TARGET_BODY_TEXT,
            OnGlass.TARGET_LARGE_TEXT, 0);
        assertEquals(side.ground, ink.terminalPaneGround(LIGHT_TINT));
        OnGlass.Resolution same = ChromeInk.resolvePane(side.ground, PALE_FG,
            OnGlass.TARGET_BODY_TEXT, dimInk, OnGlass.TARGET_LARGE_TEXT, PALE_BG, 0);
        assertEquals(same.ratio, answer.ratio, 0d);
        assertEquals(same.shortfall, answer.shortfall);
    }
}
