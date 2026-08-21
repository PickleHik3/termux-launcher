package com.termux.app.theme;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedHashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The scheme is the anchor: a launcher themed from gruvbox has to come out gruvbox-coloured, not
 * gruvbox-flavoured, and every derived tone still has to be legible on it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherThemeTokensTest {

    /** gruvbox dark hard, as Termux:Styling ships it. */
    private static Properties gruvboxDark() {
        Properties props = new Properties();
        props.setProperty("background", "#1D2021");
        props.setProperty("foreground", "#D4BE98");
        props.setProperty("cursor", "#D4BE98");
        props.setProperty("color0", "#32302F");
        props.setProperty("color1", "#EA6962");
        props.setProperty("color2", "#A9B665");
        props.setProperty("color3", "#D8A657");
        props.setProperty("color4", "#7DAEA3");
        props.setProperty("color5", "#D3869B");
        props.setProperty("color6", "#89B482");
        props.setProperty("color7", "#D4BE98");
        props.setProperty("color8", "#504945");
        return props;
    }

    private static Properties solarizedLight() {
        Properties props = new Properties();
        props.setProperty("background", "#FDF6E3");
        props.setProperty("foreground", "#657B83");
        props.setProperty("cursor", "#93A1A1");
        props.setProperty("color0", "#073642");
        props.setProperty("color1", "#DC322F");
        props.setProperty("color4", "#268BD2");
        props.setProperty("color5", "#D33682");
        props.setProperty("color6", "#2AA198");
        props.setProperty("color8", "#93A1A1");
        return props;
    }

    private static LinkedHashMap<String, Integer> derive(Properties props) {
        SchemeColors scheme = SchemeColors.from(props);
        assertNotNull(scheme);
        return LauncherThemeTokens.derive(scheme);
    }

    @Test
    public void surfaceAndTextComeStraightFromTheScheme() {
        LinkedHashMap<String, Integer> tokens = derive(gruvboxDark());
        assertEquals(Color.parseColor("#1D2021"),
            (int) tokens.get(LauncherThemeTokens.SURFACE));
        // The foreground already clears 4.5:1 on that background, so it is passed through untouched.
        assertEquals(Color.parseColor("#D4BE98"),
            (int) tokens.get(LauncherThemeTokens.ON_SURFACE));
    }

    /** A cursor that merely repeats the foreground is not an accent; blue takes the role. */
    @Test
    public void accentFallsBackToBlueWhenTheCursorIsTheForeground() {
        LinkedHashMap<String, Integer> tokens = derive(gruvboxDark());
        int primary = tokens.get(LauncherThemeTokens.PRIMARY);
        assertTrue("primary should stay in the scheme's blue hue",
            distance(primary, Color.parseColor("#7DAEA3")) < 40);
    }

    @Test
    public void aChromaticCursorBecomesTheAccent() {
        Properties props = gruvboxDark();
        props.setProperty("cursor", "#D8A657");
        LinkedHashMap<String, Integer> tokens = derive(props);
        assertTrue("primary should follow the cursor",
            distance(tokens.get(LauncherThemeTokens.PRIMARY), Color.parseColor("#D8A657")) < 40);
    }

    /** Containers are an elevation ladder: away from the surface, in order, in the right direction. */
    @Test
    public void containersClimbAwayFromTheSurfaceInDarkSchemes() {
        LinkedHashMap<String, Integer> tokens = derive(gruvboxDark());
        double surface = SchemeTone.tone(tokens.get(LauncherThemeTokens.SURFACE));
        double low = SchemeTone.tone(tokens.get(LauncherThemeTokens.SURFACE_CONTAINER_LOW));
        double mid = SchemeTone.tone(tokens.get(LauncherThemeTokens.SURFACE_CONTAINER));
        double high = SchemeTone.tone(tokens.get(LauncherThemeTokens.SURFACE_CONTAINER_HIGH));
        double highest = SchemeTone.tone(tokens.get(LauncherThemeTokens.SURFACE_CONTAINER_HIGHEST));
        assertTrue(surface < low);
        assertTrue(low < mid);
        assertTrue(mid < high);
        assertTrue(high < highest);
    }

    @Test
    public void containersDescendFromTheSurfaceInLightSchemes() {
        LinkedHashMap<String, Integer> tokens = derive(solarizedLight());
        double surface = SchemeTone.tone(tokens.get(LauncherThemeTokens.SURFACE));
        double highest = SchemeTone.tone(tokens.get(LauncherThemeTokens.SURFACE_CONTAINER_HIGHEST));
        assertTrue("a light scheme elevates by getting darker", highest < surface);
    }

    /** Whatever the scheme, chrome text has to be readable on the surface it sits on. */
    @Test
    public void everyTextRoleClearsItsContrastFloor() {
        for (Properties props : new Properties[] {gruvboxDark(), solarizedLight()}) {
            LinkedHashMap<String, Integer> tokens = derive(props);
            int surface = tokens.get(LauncherThemeTokens.SURFACE);
            assertContrast(tokens.get(LauncherThemeTokens.ON_SURFACE), surface, 4.5d);
            assertContrast(tokens.get(LauncherThemeTokens.ON_SURFACE_VARIANT), surface, 4.5d);
            assertContrast(tokens.get(LauncherThemeTokens.PRIMARY), surface, 3.0d);
            assertContrast(tokens.get(LauncherThemeTokens.SECONDARY), surface, 3.0d);
            assertContrast(tokens.get(LauncherThemeTokens.TERTIARY), surface, 3.0d);
            assertContrast(tokens.get(LauncherThemeTokens.ERROR), surface, 3.0d);

            assertContrast(tokens.get(LauncherThemeTokens.ON_PRIMARY),
                tokens.get(LauncherThemeTokens.PRIMARY), 4.5d);
            assertContrast(tokens.get(LauncherThemeTokens.ON_PRIMARY_CONTAINER),
                tokens.get(LauncherThemeTokens.PRIMARY_CONTAINER), 4.5d);
            assertContrast(tokens.get(LauncherThemeTokens.ON_ERROR_CONTAINER),
                tokens.get(LauncherThemeTokens.ERROR_CONTAINER), 4.5d);
            assertContrast(tokens.get(LauncherThemeTokens.INVERSE_ON_SURFACE),
                tokens.get(LauncherThemeTokens.INVERSE_SURFACE), 4.5d);
        }
    }

    /** Dividers: visible, never a border. */
    @Test
    public void outlineVariantStaysInTheHairlineBand() {
        for (Properties props : new Properties[] {gruvboxDark(), solarizedLight()}) {
            LinkedHashMap<String, Integer> tokens = derive(props);
            double ratio = SchemeTone.contrastRatio(
                tokens.get(LauncherThemeTokens.OUTLINE_VARIANT), tokens.get(LauncherThemeTokens.SURFACE));
            assertTrue("too faint: " + ratio, ratio >= 1.25d);
            assertTrue("too loud: " + ratio, ratio <= 2.7d);
        }
    }

    @Test
    public void everyTokenIsProduced() {
        LinkedHashMap<String, Integer> tokens = derive(gruvboxDark());
        for (String name : LauncherThemeTokens.NAMES) {
            assertNotNull("missing token " + name, tokens.get(name));
        }
        assertEquals(LauncherThemeTokens.NAMES.size(), tokens.size());
    }

    /** The scrollbar is drawn over content, so it keeps an alpha rather than being solid. */
    @Test
    public void scrollbarIsTranslucent() {
        int scrollbar = derive(gruvboxDark()).get(LauncherThemeTokens.SCROLLBAR);
        assertTrue(Color.alpha(scrollbar) > 0 && Color.alpha(scrollbar) < 255);
    }

    private static void assertContrast(int foreground, int background, double target) {
        double ratio = SchemeTone.contrastRatio(foreground, background);
        assertTrue("expected " + target + ":1, got " + ratio, ratio >= target - 0.05d);
    }

    private static int distance(int first, int second) {
        return Math.abs(Color.red(first) - Color.red(second))
            + Math.abs(Color.green(first) - Color.green(second))
            + Math.abs(Color.blue(first) - Color.blue(second));
    }
}
