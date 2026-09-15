package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * One radius for every pane. The shape used to be chosen three ways — the glass slab's radius,
 * a hardcoded 6dp for anything sharing the wall, a straight edge for a lone docked pane — so the
 * Appearance editor's number reached some panes and not others, and splitting a window changed its
 * corners. These are the cases that rule has to answer the same way.
 */
public class PaneCornerRadiusTest {

    private static final float DENSITY = 2.625f;
    private static final float STYLE_RADIUS_PX = 26.25f;
    private static final float EPS = .001f;

    @Test
    public void theSettingIsTheRadiusWhateverThePaneIsDoing() {
        // The function takes no mode at all, which is the point: glass, float, split and
        // maximised cannot be told apart here, so they cannot be rounded differently.
        assertEquals(24 * DENSITY, radius(24), EPS);
        assertEquals(6 * DENSITY, radius(6), EPS);
    }

    @Test
    public void aStoredZeroIsSquareCornersNotAnAbsentAnswer() {
        // The old fallbacks read 0 as "no opinion" and rounded anyway — the glass slab at 10dp,
        // a docked slab at 4 — so a pane set to 0 still paid the arc its clearance in columns.
        assertEquals(0f, radius(0), EPS);
        assertEquals("and there is no clearance left to spend on a square pane",
            0, PaneShape.contentInsetPx(radius(0)));
    }

    @Test
    public void theSentinelFollowsTheStylesOwnShape() {
        assertEquals(STYLE_RADIUS_PX, radius(-1), EPS);
    }

    @Test
    public void nothingEverRoundsOutwards() {
        assertEquals("a style with a nonsense radius is no arc, not a negative one",
            0f, PaneCornerRadius.radiusPx(-1, -8f, DENSITY), EPS);
    }

    private static float radius(int settingDp) {
        return PaneCornerRadius.radiusPx(settingDp, STYLE_RADIUS_PX, DENSITY);
    }
}
