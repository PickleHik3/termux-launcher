package com.termux.app.surfaces;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The editor's one form-factor question: only a tablet (smallest width 600dp and up) held in
 * landscape takes the side pane; phones either way up and tablets in portrait keep the sheet.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class EditorFormFactorTest {

    private static Configuration config(int smallestWidthDp, int orientation) {
        Configuration config = new Configuration();
        config.smallestScreenWidthDp = smallestWidthDp;
        config.orientation = orientation;
        return config;
    }

    @Test
    public void onlyATabletInLandscapeTakesTheSidePane() {
        assertTrue(EditorFormFactor.tabletLandscape(
            config(800, Configuration.ORIENTATION_LANDSCAPE)));
        assertTrue("from 600dp", EditorFormFactor.tabletLandscape(
            config(600, Configuration.ORIENTATION_LANDSCAPE)));
        assertFalse("a tablet in portrait", EditorFormFactor.tabletLandscape(
            config(800, Configuration.ORIENTATION_PORTRAIT)));
        assertFalse("a large phone in landscape", EditorFormFactor.tabletLandscape(
            config(599, Configuration.ORIENTATION_LANDSCAPE)));
        assertFalse("a phone in landscape", EditorFormFactor.tabletLandscape(
            config(360, Configuration.ORIENTATION_LANDSCAPE)));
        assertFalse("a phone in portrait", EditorFormFactor.tabletLandscape(
            config(411, Configuration.ORIENTATION_PORTRAIT)));
    }

    @Test
    public void anUnknownConfigurationKeepsTheSheet() {
        assertFalse(EditorFormFactor.tabletLandscape(null));
        assertFalse("width undefined", EditorFormFactor.tabletLandscape(config(
            Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED, Configuration.ORIENTATION_LANDSCAPE)));
        assertFalse("orientation undefined", EditorFormFactor.tabletLandscape(
            config(800, Configuration.ORIENTATION_UNDEFINED)));
    }
}
