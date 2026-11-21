package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomePageIndicatorTest {

    @Test
    public void dotsAreTheDefaultAndShowOnlyPastOnePage() {
        assertSame(HomePageIndicator.GLYPHS, HomePageIndicator.DEFAULT);
        assertSame(HomePageIndicator.GLYPHS, HomePageIndicator.fromValue(null));
        assertSame(HomePageIndicator.GLYPHS, HomePageIndicator.fromValue("bars"));
        // The stored value of the old Dots entry reads as Glyphs.
        assertSame(HomePageIndicator.GLYPHS, HomePageIndicator.fromValue("dots"));
        assertEquals("dots", HomePageIndicator.GLYPHS.value);
        // Glyphs show even with one Home page.
        assertTrue(HomePageIndicator.GLYPHS.glyphsShown());
    }

    @Test
    public void offShowsNoDotsHoweverManyPages() {
        assertSame(HomePageIndicator.OFF, HomePageIndicator.fromValue("off"));
        assertFalse(HomePageIndicator.OFF.glyphsShown());
    }
}
