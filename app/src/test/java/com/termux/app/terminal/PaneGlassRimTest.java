package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which rim a pane wears: none under the hairline look (as before), the slab's corner under gradient. */
public class PaneGlassRimTest {

    @Test
    public void hairlineDrawsNoPaneRim() {
        assertEquals(PaneGlass.NO_RIM, PaneGlass.rimRadiusPx(false, 26f), 0f);
    }

    @Test
    public void gradientRimFollowsThePanesOwnCorner() {
        assertEquals(26f, PaneGlass.rimRadiusPx(true, 26f), 0f);
        assertEquals("a square pane still has a rim", 0f, PaneGlass.rimRadiusPx(true, 0f), 0f);
        assertEquals(0f, PaneGlass.rimRadiusPx(true, -3f), 0f);
    }
}
