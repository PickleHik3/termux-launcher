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
 * {@code ~/.termux/launcher-theme.properties} is the launcher's answer to a colorscheme's highlight
 * links: a handful of hand-picked tokens on top of a derived default set.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherThemeOverridesTest {

    private static SchemeColors scheme() {
        Properties props = new Properties();
        props.setProperty("background", "#1D2021");
        props.setProperty("foreground", "#D4BE98");
        props.setProperty("color3", "#D8A657");
        props.setProperty("color4", "#7DAEA3");
        props.setProperty("color8", "#504945");
        SchemeColors scheme = SchemeColors.from(props);
        assertNotNull(scheme);
        return scheme;
    }

    private static LinkedHashMap<String, Integer> applied(String... lines) {
        SchemeColors scheme = scheme();
        LinkedHashMap<String, Integer> tokens = LauncherThemeTokens.derive(scheme);
        Properties overrides = new Properties();
        for (int i = 0; i < lines.length; i += 2) {
            overrides.setProperty(lines[i], lines[i + 1]);
        }
        LauncherThemeOverrides.apply(tokens, scheme, overrides);
        return tokens;
    }

    @Test
    public void takesLiteralColours() {
        assertEquals(Color.parseColor("#FFD79921"),
            (int) applied(LauncherThemeTokens.PRIMARY, "#d79921").get(LauncherThemeTokens.PRIMARY));
    }

    @Test
    public void takesSchemeKeys() {
        assertEquals(Color.parseColor("#FFD8A657"),
            (int) applied(LauncherThemeTokens.PRIMARY, "color3").get(LauncherThemeTokens.PRIMARY));
        assertEquals(Color.parseColor("#FF1D2021"),
            (int) applied(LauncherThemeTokens.SURFACE, "background").get(LauncherThemeTokens.SURFACE));
    }

    @Test
    public void takesOtherTokens() {
        LinkedHashMap<String, Integer> tokens = applied(LauncherThemeTokens.OUTLINE, "on_surface_variant");
        assertEquals(tokens.get(LauncherThemeTokens.ON_SURFACE_VARIANT),
            tokens.get(LauncherThemeTokens.OUTLINE));
    }

    /** A token pointing at a token the user also overrode follows the override, not the default. */
    @Test
    public void chainsThroughOtherOverrides() {
        LinkedHashMap<String, Integer> tokens = applied(
            LauncherThemeTokens.PRIMARY, "#d79921",
            LauncherThemeTokens.SECONDARY, "primary");
        assertEquals(Color.parseColor("#FFD79921"), (int) tokens.get(LauncherThemeTokens.SECONDARY));
    }

    @Test
    public void appliesFunctions() {
        LinkedHashMap<String, Integer> base = LauncherThemeTokens.derive(scheme());

        int lightened = applied(LauncherThemeTokens.SURFACE_CONTAINER_HIGH, "lighten(surface, 0.2)")
            .get(LauncherThemeTokens.SURFACE_CONTAINER_HIGH);
        assertTrue(SchemeTone.tone(lightened) > SchemeTone.tone(base.get(LauncherThemeTokens.SURFACE)));

        int darkened = applied(LauncherThemeTokens.SURFACE_DIM, "darken(surface, 0.5)")
            .get(LauncherThemeTokens.SURFACE_DIM);
        assertTrue(SchemeTone.tone(darkened) < SchemeTone.tone(base.get(LauncherThemeTokens.SURFACE)));

        int mixed = applied(LauncherThemeTokens.OUTLINE_VARIANT, "mix(on_surface, surface, 0.75)")
            .get(LauncherThemeTokens.OUTLINE_VARIANT);
        assertEquals(SchemeTone.blend(base.get(LauncherThemeTokens.ON_SURFACE),
            base.get(LauncherThemeTokens.SURFACE), 0.75f), mixed);

        int faded = applied(LauncherThemeTokens.SCROLLBAR, "alpha(on_surface, 0.25)")
            .get(LauncherThemeTokens.SCROLLBAR);
        assertEquals(64, Color.alpha(faded));
    }

    @Test
    public void percentagesAreAcceptedForAmounts() {
        assertEquals(applied(LauncherThemeTokens.SCROLLBAR, "alpha(on_surface, 0.5)").get(LauncherThemeTokens.SCROLLBAR),
            applied(LauncherThemeTokens.SCROLLBAR, "alpha(on_surface, 50%)").get(LauncherThemeTokens.SCROLLBAR));
    }

    @Test
    public void trailingCommentsAreNotPartOfTheValue() {
        assertEquals(Color.parseColor("#FFD79921"),
            (int) applied(LauncherThemeTokens.PRIMARY, "#d79921 # the yellow").get(LauncherThemeTokens.PRIMARY));
        assertEquals(Color.parseColor("#FFD8A657"),
            (int) applied(LauncherThemeTokens.PRIMARY, "color3  # scheme yellow").get(LauncherThemeTokens.PRIMARY));
    }

    /** One bad line is cosmetic; it must not cost the other thirty tokens. */
    @Test
    public void unparsableValuesKeepTheDerivedDefault() {
        LinkedHashMap<String, Integer> base = LauncherThemeTokens.derive(scheme());
        LinkedHashMap<String, Integer> tokens = applied(
            LauncherThemeTokens.PRIMARY, "chartreuse",
            LauncherThemeTokens.SECONDARY, "#d79921");
        assertEquals(base.get(LauncherThemeTokens.PRIMARY), tokens.get(LauncherThemeTokens.PRIMARY));
        assertEquals(Color.parseColor("#FFD79921"), (int) tokens.get(LauncherThemeTokens.SECONDARY));
    }

    @Test
    public void mutualReferencesTerminate() {
        LinkedHashMap<String, Integer> tokens = applied(
            LauncherThemeTokens.PRIMARY, "secondary",
            LauncherThemeTokens.SECONDARY, "primary");
        assertNotNull(tokens.get(LauncherThemeTokens.PRIMARY));
        assertNotNull(tokens.get(LauncherThemeTokens.SECONDARY));
    }

    @Test
    public void unknownKeysAreReportable() {
        Properties overrides = new Properties();
        overrides.setProperty("primary", "#d79921");
        overrides.setProperty("dock_background", "#000000");
        assertEquals(1, LauncherThemeOverrides.unknownKeys(overrides).size());
        assertEquals("dock_background", LauncherThemeOverrides.unknownKeys(overrides).get(0));
    }
}
