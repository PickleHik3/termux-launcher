package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;

/** The one legibility control: the "Terminal contrast" choice as a multiplier on every tier. */
public class LegibilityLevelTest {

    private static final double EPS = 1e-9;

    @Test
    public void bodyTextIsThreeFourAndAHalfOrSeven() {
        assertEquals(3.0d, LegibilityLevel.SOFTER.target(OnGlass.TARGET_BODY_TEXT), EPS);
        assertEquals(4.5d, LegibilityLevel.DEFAULT.target(OnGlass.TARGET_BODY_TEXT), EPS);
        assertEquals(7.0d, LegibilityLevel.HARDER.target(OnGlass.TARGET_BODY_TEXT), EPS);
    }

    /** Default changes nothing, so every band that predates the control keeps its answer. */
    @Test
    public void defaultLeavesEveryTierAlone() {
        for (double tier : new double[] {OnGlass.TARGET_BODY_TEXT, OnGlass.TARGET_LARGE_TEXT,
                OnGlass.TARGET_DECORATION, 1.5d}) {
            assertEquals(tier, LegibilityLevel.DEFAULT.target(tier), 0d);
        }
    }

    /** The other tiers move proportionally with body text. */
    @Test
    public void largeTextAndDecorationScaleProportionally() {
        assertEquals(3.0d * 7.0d / 4.5d,
            LegibilityLevel.HARDER.target(OnGlass.TARGET_LARGE_TEXT), EPS);
        assertEquals(2.0d * 7.0d / 4.5d,
            LegibilityLevel.HARDER.target(OnGlass.TARGET_DECORATION), EPS);
        assertEquals(2.0d, LegibilityLevel.SOFTER.target(OnGlass.TARGET_LARGE_TEXT), EPS);
    }

    /** Softer would put the separator dots at 1.33:1, where they measured invisible; it holds at 2. */
    @Test
    public void decorationNeverDropsBelowTwo() {
        assertEquals(OnGlass.TARGET_DECORATION,
            LegibilityLevel.SOFTER.target(OnGlass.TARGET_DECORATION), EPS);
    }

    /** The palette and the bands read one control: no second key. */
    @Test
    public void theLevelIsThePalettesContrastChoice() {
        assertSame(LegibilityLevel.SOFTER, LegibilityLevel.of(TerminalContrastLevel.SOFTER));
        assertSame(LegibilityLevel.DEFAULT, LegibilityLevel.of(TerminalContrastLevel.DEFAULT));
        assertSame(LegibilityLevel.HARDER, LegibilityLevel.of(TerminalContrastLevel.HARDER));
        assertSame(LegibilityLevel.DEFAULT, LegibilityLevel.of((TerminalContrastLevel) null));
        for (LegibilityLevel level : LegibilityLevel.values()) {
            assertSame(level, LegibilityLevel.of(level.terminalContrast));
        }
        assertSame(TerminalContrastLevel.DEFAULT, LegibilityLevel.terminalContrast(null));
    }
}
