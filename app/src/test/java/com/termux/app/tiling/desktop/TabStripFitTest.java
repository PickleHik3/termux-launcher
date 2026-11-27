package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** How a tab strip fits its tabs (#420). */
public class TabStripFitTest {

    @Test
    public void namesWhileTheyFitThenIconsThenAScrollingStrip() {
        float density = 2f;
        // 340dp wide.
        float width = 340f * density;
        assertEquals(TabStripFit.Mode.NAMED, TabStripFit.of(width, 4, density));
        assertEquals(TabStripFit.Mode.ICONS, TabStripFit.of(width, 6, density));
        assertEquals(TabStripFit.Mode.ICONS, TabStripFit.of(width, 10, density));
        assertEquals(TabStripFit.Mode.SCROLLING, TabStripFit.of(width, 12, density));
    }
}
