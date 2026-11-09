package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * ADR 0014 wave 2c (decided 2026-10-28): which even-out a chrome band draws with. By default the
 * terminal pane's own stage; a band with an Opacity of its own that no longer follows Base, its
 * own text target at its own strength; the switch off, none at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutBandPolicyTest {

    private static ChromeInk.EvenOutPolicy terminal(TerminalContrastLevel level, int strength) {
        // Stand-in palette ceilings; what matters is that the default hands these back unchanged.
        return new ChromeInk.EvenOutPolicy(level,
            EvenOut.ceilingForTarget(7d, ChromeInk.Polarity.PALE_INK),
            EvenOut.ceilingForTarget(7d, ChromeInk.Polarity.DARK_INK),
            ChromeInk.Polarity.PALE_INK, strength);
    }

    @Test
    public void switchOffIsNoneForEveryBand() {
        for (GlassBackdropCache.Band band : GlassBackdropCache.Band.values()) {
            assertNull(ChromeInk.bandEvenOutPolicy(band, true, 34, null));
            assertNull(ChromeInk.bandEvenOutPolicy(band, false, 34, null));
        }
        assertNull(ChromeInk.bandEvenOutPolicy(null, true, 34, null));
    }

    @Test
    public void inheritingBandCopiesTheTerminalPolicy() {
        ChromeInk.EvenOutPolicy stage = terminal(TerminalContrastLevel.DEFAULT, 100);
        assertSame(stage, ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.DOCK, true, 5, stage));
        assertSame(stage,
            ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.KEYBOARD, true, 0, stage));
        assertSame(stage,
            ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.STATUS_BAR, true, 90, stage));
    }

    @Test
    public void bandsWithoutASlotAlwaysTakeTheDefault() {
        ChromeInk.EvenOutPolicy stage = terminal(TerminalContrastLevel.DEFAULT, 60);
        // Card widgets and the nav strip (no band) have no Opacity of their own to override with.
        assertSame(stage,
            ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.WIDGET, false, 0, stage));
        assertSame(stage, ChromeInk.bandEvenOutPolicy(null, false, 0, stage));
    }

    @Test
    public void terminalAtZeroOpacityTurnsTheDefaultOffButNotAnOverride() {
        ChromeInk.EvenOutPolicy stage = terminal(TerminalContrastLevel.DEFAULT, 0);
        assertNull(ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.DOCK, true, 34, stage));
        assertNull(ChromeInk.bandEvenOutPolicy(null, false, 34, stage));
        ChromeInk.EvenOutPolicy own =
            ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.DOCK, false, 34, stage);
        assertNotNull(own);
        assertEquals(100, own.strengthPercent);
    }

    @Test
    public void overrideStrengthFollowsTheBandsOwnOpacity() {
        ChromeInk.EvenOutPolicy stage = terminal(TerminalContrastLevel.DEFAULT, 100);
        assertNull(ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.KEYBOARD, false, 0, stage));
        ChromeInk.EvenOutPolicy half =
            ChromeInk.bandEvenOutPolicy(GlassBackdropCache.Band.KEYBOARD, false, 17, stage);
        assertNotNull(half);
        assertEquals(ChromeInk.evenOutStrengthPercent(17), half.strengthPercent);
        assertEquals(50, half.strengthPercent);
        assertSame(stage.level, half.level);
        assertSame(stage.current, half.current);
    }

    @Test
    public void overrideCeilingIsTheBandsOwnBodyTargetAtTheLevel() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            ChromeInk.EvenOutPolicy own = ChromeInk.bandEvenOutPolicy(
                GlassBackdropCache.Band.DOCK, false, 50, terminal(level, 100));
            assertNotNull(own);
            double target = LegibilityLevel.of(level).target(OnGlass.TARGET_BODY_TEXT);
            // White on the pale ceiling and black on the dark floor meet the target exactly.
            assertEquals(target, EvenOut.toneRatio(100d, own.pale.tone), 1e-6);
            assertEquals(target, EvenOut.toneRatio(0d, own.dark.tone), 1e-6);
        }
    }

    @Test
    public void bandPolarityKeepsItsSideWhileThatReaches() {
        ChromeInk.Polarity pale = ChromeInk.Polarity.PALE_INK;
        ChromeInk.Polarity dark = ChromeInk.Polarity.DARK_INK;
        assertSame(pale, ChromeInk.onePolarity(pale, 0d, 0d));
        assertSame(pale, ChromeInk.onePolarity(pale, 0d, 5d));
        assertSame(dark, ChromeInk.onePolarity(pale, 3d, 0d));
        assertSame(dark, ChromeInk.onePolarity(pale, 3d, 2d));
        assertSame(pale, ChromeInk.onePolarity(pale, 3d, 3d));
        assertSame(dark, ChromeInk.onePolarity(dark, 4d, 1d));
    }
}
