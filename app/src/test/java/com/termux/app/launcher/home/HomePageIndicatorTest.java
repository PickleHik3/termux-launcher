package com.termux.app.launcher.home;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomePageIndicatorTest {

    @Test
    public void dotsAreTheDefaultAndShowOnlyPastOnePage() {
        assertSame(HomePageIndicator.DOTS, HomePageIndicator.DEFAULT);
        assertSame(HomePageIndicator.DOTS, HomePageIndicator.fromValue(null));
        assertSame(HomePageIndicator.DOTS, HomePageIndicator.fromValue("bars"));
        assertFalse(HomePageIndicator.DOTS.dotsShown(1));
        assertTrue(HomePageIndicator.DOTS.dotsShown(2));
    }

    @Test
    public void offShowsNoDotsHoweverManyPages() {
        assertSame(HomePageIndicator.OFF, HomePageIndicator.fromValue("off"));
        assertFalse(HomePageIndicator.OFF.dotsShown(1));
        assertFalse(HomePageIndicator.OFF.dotsShown(5));
    }
}
