package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.theme.LauncherThemeTokens;
import com.termux.app.theme.SchemeColors;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.terminal.TerminalColorScheme;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.LinkedHashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class MaterialTerminalColorSchemeTest {

    @Test
    public void everyLevelMeetsItsForegroundAnsiAndCursorTargets() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties palette = MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), level);
            int background = color(palette, "background");
            assertTrue(MaterialTerminalColorScheme.contrastRatio(
                color(palette, "foreground"), background) + .01 >= level.foregroundRatio);
            assertTrue(MaterialTerminalColorScheme.contrastRatio(
                color(palette, "cursor"), background) + .01 >= level.cursorRatio);
            for (int i = 0; i < 16; i++) {
                // Per slot, not per level: 0 and 7 are panel fills and take no floor at all, 8 is
                // dim text and takes a fixed one. See MaterialTerminalColorScheme.ansiFloor.
                double floor = MaterialTerminalColorScheme.ansiFloor(i, level);
                assertTrue("ANSI " + i + " at " + level.value,
                    MaterialTerminalColorScheme.contrastRatio(
                        color(palette, "color" + i), background) + .01 >= floor);
            }
        }
    }

    /**
     * The cheap background helper and the full palette have to agree — the overlay reads one and the
     * terminal reads the other, and a drift between them shows up as a terminal surface that is a
     * slightly different colour from the terminal's own background.
     */
    @Test
    public void theBackgroundHelperMatchesTheFullPaletteAtEveryLevel() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties palette = MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), level);
            assertEquals("level " + level.value, color(palette, "background"),
                MaterialTerminalColorScheme.backgroundColor(
                    ApplicationProvider.getApplicationContext(), level) | 0xFF000000);
        }
    }

    /** A theme cannot draw a filled chip with guaranteed contrast unless both halves are exported. */
    @Test
    public void everyExportedContainerHasItsOnPartner() {
        Properties roles = MaterialTerminalColorScheme.createMaterialRoleProperties(
            ApplicationProvider.getApplicationContext(),
            MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), TerminalContrastLevel.DEFAULT),
            TerminalContrastLevel.DEFAULT);
        for (String container : new String[] {"primary", "secondary", "tertiary", "error",
                "primary_container", "secondary_container", "tertiary_container",
                "error_container"}) {
            assertNotNull(container, roles.getProperty(container));
            assertNotNull("on_" + container, roles.getProperty("on_" + container));
        }
        // Surfaces pair with on_surface / on_surface_variant rather than an on_<name> of their own.
        for (String surface : new String[] {"surface", "surface_variant", "surface_container",
                "surface_container_high", "surface_container_highest", "outline",
                "outline_variant", "on_surface", "on_surface_variant"}) {
            assertNotNull(surface, roles.getProperty(surface));
        }
    }

    /**
     * The fingerprint has to move with any role the export is built from. It used to cover only the
     * six accents, so a wallpaper that shifted the neutral-variant tones — what the bundled prompt
     * fills its slabs with — read as unchanged.
     */
    @Test
    public void theSignatureCoversTheContrastLevel() {
        int softer = MaterialTerminalColorScheme.signature(
            ApplicationProvider.getApplicationContext(), TerminalContrastLevel.SOFTER);
        int dflt = MaterialTerminalColorScheme.signature(
            ApplicationProvider.getApplicationContext(), TerminalContrastLevel.DEFAULT);
        int harder = MaterialTerminalColorScheme.signature(
            ApplicationProvider.getApplicationContext(), TerminalContrastLevel.HARDER);
        assertNotEquals(softer, dflt);
        assertNotEquals(dflt, harder);
        assertNotEquals(softer, harder);
        // Stable for the same inputs, or every resume would look like a change.
        assertEquals(dflt, MaterialTerminalColorScheme.signature(
            ApplicationProvider.getApplicationContext(), TerminalContrastLevel.DEFAULT));
    }

    /**
     * The "IfNeeded" gate ({@code refreshMaterialTerminalColorsIfNeeded}) trusts this fingerprint to
     * notice a day/night flip on its own — nothing else tells it the mode moved. It has to, because
     * the flip is not carried as a bit of its own: the resolved role colours are simply different
     * under {@code values-night}, all the way down to {@code termux_surface_base}, so the same
     * attribute reads that build the signature already see the new theme once the context does.
     *
     * <p>The bare application context {@code ApplicationProvider} hands back here carries none of
     * {@code Theme.TermuxActivity.DayNight.NoActionBar}'s attributes — every {@code MaterialColors}
     * lookup in {@link #signature} would silently return its literal {@code 0} fallback regardless
     * of day or night, which is exactly what this test exists to catch. A {@link ContextThemeWrapper}
     * over the real activity theme is what {@code TermuxActivity} and the background day/night
     * refresh both actually theme their context with, so this wraps one too.
     */
    @Test
    public void theSignatureMovesOnADayNightFlip() {
        int day = themedSignature();
        RuntimeEnvironment.setQualifiers("+night");
        int night = themedSignature();
        assertNotEquals(day, night);
        // Stable while nothing else moved, or every resume in the same mode would look dirty.
        assertEquals(night, themedSignature());
        RuntimeEnvironment.setQualifiers("+notnight");
        assertEquals("flipping back should reproduce the original signature", day, themedSignature());
    }

    private static int themedSignature() {
        Context themed = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        return MaterialTerminalColorScheme.signature(themed, TerminalContrastLevel.DEFAULT);
    }

    /**
     * The generated palette goes straight into {@code TerminalColorScheme.updateWith()}, which throws
     * on the first key it does not recognise — mid-iteration over an unordered map, so the palette is
     * left half applied and the session reset behind it never runs. {@code contrast_level} used to be
     * in here and threw on every single apply.
     */
    @Test
    public void theGeneratedPaletteIsColourKeysOnly() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties palette = MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), level);
            for (String key : palette.stringPropertyNames()) {
                boolean named = "foreground".equals(key) || "background".equals(key)
                    || "cursor".equals(key);
                assertTrue("non-colour key '" + key + "' at " + level.value,
                    named || key.matches("color\\d+"));
            }
            // The real consumer, not a re-statement of the rule above.
            new TerminalColorScheme().updateWith(palette);
        }
    }

    /** The level still has to reach the exported role files; it just travels beside the palette now. */
    @Test
    public void theExportedRolesCarryTheContrastLevel() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties roles = MaterialTerminalColorScheme.createMaterialRoleProperties(
                ApplicationProvider.getApplicationContext(),
                MaterialTerminalColorScheme.create(
                    ApplicationProvider.getApplicationContext(), level),
                level);
            assertEquals(level.value, roles.getProperty("contrast_level"));
        }
    }

    /** All 48 Material 3 role tokens noctalia expects, present and a valid {@code #rrggbb}. */
    @Test
    public void allFortyEightRoleTokensAreValidHexColours() {
        Properties roles = MaterialTerminalColorScheme.createMaterialRoleProperties(
            ApplicationProvider.getApplicationContext(),
            MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), TerminalContrastLevel.DEFAULT),
            TerminalContrastLevel.DEFAULT);
        String[] roleKeys = {
            "primary", "on_primary", "primary_container", "on_primary_container",
            "secondary", "on_secondary", "secondary_container", "on_secondary_container",
            "tertiary", "on_tertiary", "tertiary_container", "on_tertiary_container",
            "error", "on_error", "error_container", "on_error_container",
            "outline", "outline_variant",
            "surface", "surface_variant", "surface_container", "surface_container_high",
            "surface_container_highest", "on_surface", "on_surface_variant",
            "primary_fixed", "primary_fixed_dim", "on_primary_fixed", "on_primary_fixed_variant",
            "secondary_fixed", "secondary_fixed_dim", "on_secondary_fixed", "on_secondary_fixed_variant",
            "tertiary_fixed", "tertiary_fixed_dim", "on_tertiary_fixed", "on_tertiary_fixed_variant",
            "surface_dim", "surface_bright", "surface_container_lowest", "surface_container_low",
            "background", "on_background",
            "inverse_surface", "inverse_on_surface", "inverse_primary",
            "shadow", "scrim",
        };
        assertEquals("token list itself covers 48 roles", 48, roleKeys.length);
        for (String key : roleKeys) {
            String value = roles.getProperty(key);
            assertNotNull(key, value);
            assertTrue(key + " = '" + value + "' is not #rrggbb", value.matches("#[0-9A-Fa-f]{6}"));
        }
    }

    /** noctalia's 22 terminal_* names, byte-identical to the keys they alias. */
    @Test
    public void terminalAliasesAreByteIdenticalToTheirSourceKeys() {
        Properties roles = MaterialTerminalColorScheme.createMaterialRoleProperties(
            ApplicationProvider.getApplicationContext(),
            MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), TerminalContrastLevel.DEFAULT),
            TerminalContrastLevel.DEFAULT);
        String[] ansiNames = {"black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"};
        for (int i = 0; i < ansiNames.length; i++) {
            assertEquals("terminal_normal_" + ansiNames[i],
                roles.getProperty("terminal_color" + i),
                roles.getProperty("terminal_normal_" + ansiNames[i]));
            assertEquals("terminal_bright_" + ansiNames[i],
                roles.getProperty("terminal_color" + (i + 8)),
                roles.getProperty("terminal_bright_" + ansiNames[i]));
        }
        assertEquals(roles.getProperty("terminal_background"), roles.getProperty("terminal_cursor_text"));
        assertEquals(roles.getProperty("on_surface_variant"), roles.getProperty("terminal_selection_fg"));
        assertEquals(roles.getProperty("surface_variant"), roles.getProperty("terminal_selection_bg"));
        // Already existed before this spec; confirm they are still exported under their own names.
        assertNotNull(roles.getProperty("terminal_foreground"));
        assertNotNull(roles.getProperty("terminal_background"));
        assertNotNull(roles.getProperty("terminal_cursor"));
    }

    /** {@code mode} has to agree with the terminal background's own HCT tone, not the theme's. */
    @Test
    public void modeAgreesWithTheTerminalBackgroundTone() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties terminal = MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), level);
            Properties roles = MaterialTerminalColorScheme.createMaterialRoleProperties(
                ApplicationProvider.getApplicationContext(), terminal, level);
            String mode = roles.getProperty("mode");
            assertTrue("mode='" + mode + "'", "dark".equals(mode) || "light".equals(mode));
            double tone = com.google.android.material.color.utilities.Hct
                .fromInt(color(terminal, "background")).getTone();
            assertEquals("level " + level.value, tone < 50 ? "dark" : "light", mode);
        }
    }

    /** The two writers pick up new keys through their existing sorted-key loop, unchanged. */
    @Test
    public void bothWritersIncludeANewKey() {
        Properties roles = MaterialTerminalColorScheme.createMaterialRoleProperties(
            ApplicationProvider.getApplicationContext(),
            MaterialTerminalColorScheme.create(
                ApplicationProvider.getApplicationContext(), TerminalContrastLevel.DEFAULT),
            TerminalContrastLevel.DEFAULT);
        String propertiesText = MaterialTerminalColorScheme.toPropertiesText(roles);
        String shellText = MaterialTerminalColorScheme.toShellExports(roles);
        assertTrue(propertiesText.contains("\nprimary_fixed=" + roles.getProperty("primary_fixed") + "\n"));
        assertTrue(propertiesText.contains("\nmode=" + roles.getProperty("mode") + "\n"));
        assertTrue(shellText.contains("export TERMUX_MATERIAL_PRIMARY_FIXED='" + roles.getProperty("primary_fixed") + "'"));
        assertTrue(shellText.contains("export TERMUX_MATERIAL_MODE='" + roles.getProperty("mode") + "'"));
    }

    // ---------------------------------------------------------------------------------------------
    // The ANSI derivation itself. These drive the pure slot builder rather than a themed Context:
    // the rules are about hue, chroma and tone, and a Robolectric theme can only ever demonstrate
    // one wallpaper.
    // ---------------------------------------------------------------------------------------------

    /** A hue on the far side of the wheel from the theme still has to arrive recognisably. */
    @Test
    public void everySlotHueStaysWithinFifteenDegreesOfItsAnchor() {
        double[] anchors = {25d, 145d, 85d, 255d, 330d, 195d};
        for (double source : new double[] {0d, 60d, 145d, 210d, 300d, 359d}) {
            for (double anchor : anchors) {
                double harmonized = MaterialTerminalColorScheme.harmonizeHue(anchor, source);
                assertTrue("anchor " + anchor + " toward " + source + " landed at " + harmonized,
                    angleBetween(anchor, harmonized) <= 15d + 1e-9);
            }
        }
    }

    /** And it has to actually move toward the theme, not merely stay put. */
    @Test
    public void aFarHueIsPulledTheFullFifteenDegreesTowardTheTheme() {
        // Green's anchor is 145; a theme at 210 is 65° away, so the pull is capped at 15.
        assertEquals(160d, MaterialTerminalColorScheme.harmonizeHue(145d, 210d), 1e-9);
        // The short way round is the way taken, even across 0.
        assertEquals(10d, MaterialTerminalColorScheme.harmonizeHue(25d, 350d), 1e-9);
        // A hue already on the theme does not move.
        assertEquals(145d, MaterialTerminalColorScheme.harmonizeHue(145d, 145d), 1e-9);
    }

    /**
     * The whole point of the rewrite: a muted wallpaper yields a muted palette and a vivid one a
     * vivid palette, both inside one band. Asserted as an identity against the clamp endpoints —
     * reading chroma back off the slots would measure sRGB gamut clipping instead of the rule.
     */
    @Test
    public void chromaIsTheThemesChromaClampedToTheBand() {
        Properties muted = slots(220d, 4d, true);
        Properties vivid = slots(220d, 120d, true);
        assertEquals(slots(220d, 28d, true), muted);
        assertEquals(slots(220d, 52d, true), vivid);
        assertFalse("a muted and a vivid theme cannot produce the same palette", muted.equals(vivid));
        // Mid-band chroma is passed through untouched.
        assertNotEquals(slots(220d, 40d, true), muted);
        assertNotEquals(slots(220d, 40d, true), vivid);
        // Nothing ever exceeds the ceiling; clipping can only take chroma away.
        for (int i = 1; i <= 6; i++) {
            assertTrue("slot " + i, Hct.fromInt(color(vivid, "color" + i)).getChroma() <= 53d);
        }
    }

    /** Normal slots sit one tone band below bright, and both bands flip with the background. */
    @Test
    public void toneBandsFollowTheBackgroundMode() {
        Properties dark = slots(220d, 40d, true);
        Properties light = slots(220d, 40d, false);
        for (int i = 1; i <= 6; i++) {
            assertEquals("dark normal " + i, 80d, tone(dark, "color" + i), 1d);
            assertEquals("dark bright " + i, 90d, tone(dark, "color" + (i + 8)), 1d);
            assertEquals("light normal " + i, 40d, tone(light, "color" + i), 1d);
            assertEquals("light bright " + i, 30d, tone(light, "color" + (i + 8)), 1d);
        }
        assertEquals(25d, tone(dark, "color0"), 1d);
        assertEquals(45d, tone(dark, "color8"), 1d);
        assertEquals(80d, tone(dark, "color7"), 1d);
        assertEquals(96d, tone(dark, "color15"), 1d);
        assertEquals(25d, tone(light, "color0"), 1d);
        assertEquals(50d, tone(light, "color8"), 1d);
        assertEquals(75d, tone(light, "color7"), 1d);
        assertEquals(92d, tone(light, "color15"), 1d);
    }

    /**
     * The neutrals are one ladder in both modes. Bright white used to be tone 10 on a light
     * background — the darkest of the four — so {@code black on brightwhite}, which is how a TUI
     * draws a selected row, put a tone 25 glyph on a tone 10 fill and the row vanished.
     */
    @Test
    public void theNeutralsClimbInToneInBothModes() {
        for (boolean dark : new boolean[] {true, false}) {
            Properties palette = slots(220d, 40d, dark);
            String mode = dark ? "dark" : "light";
            assertTrue(mode + " color0 < color8",
                tone(palette, "color0") < tone(palette, "color8"));
            assertTrue(mode + " color8 < color7",
                tone(palette, "color8") < tone(palette, "color7"));
            assertTrue(mode + " color7 < color15",
                tone(palette, "color7") < tone(palette, "color15"));
        }
    }

    /** Neutrals come off the neutral palette: surface hue, and no more chroma than a neutral has. */
    @Test
    public void neutralSlotsStayNeutral() {
        Properties palette = MaterialTerminalColorScheme.ansiSlots(220d, 48d, 25d, 300d, 90d, true);
        for (String key : new String[] {"color0", "color7", "color8", "color15"}) {
            assertTrue(key + " chroma", Hct.fromInt(color(palette, key)).getChroma() <= 7d);
        }
    }

    /** Red spends the theme's error hue when there is one, and falls back to the anchor when not. */
    @Test
    public void redFollowsTheThemesErrorHue() {
        Properties themed = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 350d, 250d, 4d, true);
        Properties anchored = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 25d, 250d, 4d, true);
        assertNotEquals(anchored.getProperty("color1"), themed.getProperty("color1"));
        assertEquals(MaterialTerminalColorScheme.harmonizeHue(350d, 220d),
            Hct.fromInt(color(themed, "color1")).getHue(), 1.5d);
        // Only red listens to the error role; the other five keep their anchors.
        for (int i = 2; i <= 6; i++) {
            assertEquals("slot " + i, anchored.getProperty("color" + i), themed.getProperty("color" + i));
        }
    }

    /**
     * A {@code colors.properties} scheme reaches this class as the theme attributes it derives, so
     * the derivation has to survive one: gruvbox is a low-chroma, warm-hued source, which is exactly
     * the case the fixed 2014 anchors used to ignore.
     */
    @Test
    public void aSchemeDerivedSourceStillYieldsSixteenValidColours() {
        SchemeColors scheme = SchemeColors.from(gruvboxDark());
        assertNotNull(scheme);
        LinkedHashMap<String, Integer> tokens = LauncherThemeTokens.derive(scheme);
        Hct primary = Hct.fromInt(tokens.get(LauncherThemeTokens.PRIMARY));
        Hct surface = Hct.fromInt(tokens.get(LauncherThemeTokens.SURFACE));
        Hct error = Hct.fromInt(tokens.get(LauncherThemeTokens.ERROR));
        Properties palette = MaterialTerminalColorScheme.ansiSlots(primary.getHue(),
            primary.getChroma(), error.getHue(), surface.getHue(), surface.getChroma(),
            surface.getTone() < 50d);
        assertEquals(16, palette.size());
        for (int i = 0; i < 16; i++) {
            String value = palette.getProperty("color" + i);
            assertNotNull("color" + i, value);
            assertTrue("color" + i + " = '" + value + "'", value.matches("#[0-9A-Fa-f]{6}"));
        }
    }

    /** gruvbox dark hard, as Termux:Styling ships it. */
    private static Properties gruvboxDark() {
        Properties props = new Properties();
        props.setProperty("background", "#1D2021");
        props.setProperty("foreground", "#D4BE98");
        props.setProperty("color1", "#EA6962");
        props.setProperty("color2", "#A9B665");
        props.setProperty("color3", "#D8A657");
        props.setProperty("color4", "#7DAEA3");
        props.setProperty("color5", "#D3869B");
        props.setProperty("color6", "#89B482");
        return props;
    }

    private static Properties slots(double hue, double chroma, boolean dark) {
        return MaterialTerminalColorScheme.ansiSlots(hue, chroma, 25d, hue, 4d, dark);
    }

    private static double tone(Properties palette, String key) {
        return Hct.fromInt(color(palette, key)).getTone();
    }

    private static double angleBetween(double first, double second) {
        return 180d - Math.abs(Math.abs(first - second) - 180d);
    }

    /**
     * The floor used to be the level's ratio for all sixteen, which lifted ANSI black and bright
     * black to the same mid tone — "black" was not dark, and a TUI that fills a panel with it drew
     * the panel in the same grey as its dim text. The neutrals have to stay a ladder at every level.
     */
    @Test
    public void theNeutralLadderSurvivesTheContrastFloor() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties dark = flooredNeutrals(true, level);
            assertTrue("dark color0 must stay darker than color8 at " + level.value,
                tone(dark, "color0") < tone(dark, "color8"));
            assertTrue("dark color8 must stay darker than color7 at " + level.value,
                tone(dark, "color8") < tone(dark, "color7"));

            assertTrue("dark color7 must stay darker than color15 at " + level.value,
                tone(dark, "color7") < tone(dark, "color15"));

            Properties light = flooredNeutrals(false, level);
            assertTrue("light color7 must stay lighter than color8 at " + level.value,
                tone(light, "color7") > tone(light, "color8"));
            assertTrue("light color8 must stay lighter than color0 at " + level.value,
                tone(light, "color8") > tone(light, "color0"));
            assertTrue("light color15 must stay lighter than color7 at " + level.value,
                tone(light, "color15") > tone(light, "color7"));
        }
    }

    /** The exemptions are the rule, so state them once and let the ladder test prove the effect. */
    @Test
    public void onlyTheTextSlotsCarryAFloor() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            assertEquals("color0 at " + level.value, 0d,
                MaterialTerminalColorScheme.ansiFloor(0, level), 0d);
            assertEquals("color7 at " + level.value, 0d,
                MaterialTerminalColorScheme.ansiFloor(7, level), 0d);
            // Bright white is a fill too: a text floor on it is what made it the darkest neutral.
            assertEquals("color15 at " + level.value, 0d,
                MaterialTerminalColorScheme.ansiFloor(15, level), 0d);
            assertEquals("color8 at " + level.value, 3.0d,
                MaterialTerminalColorScheme.ansiFloor(8, level), 0d);
            for (int slot : new int[] {1, 6, 9, 14}) {
                assertEquals("color" + slot + " at " + level.value, level.ansiRatio,
                    MaterialTerminalColorScheme.ansiFloor(slot, level), 0d);
            }
        }
    }

    /** The slots as {@code create} would leave them, for a background of the given mode and level. */
    private static Properties flooredNeutrals(boolean dark, TerminalContrastLevel level) {
        Properties palette = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 25d, 260d, 4d, dark);
        int background = MaterialTerminalColorScheme.surfaceTone(
            Hct.from(260d, 4d, dark ? 10d : 90d).toInt(), level);
        MaterialTerminalColorScheme.applyAnsiContrastFloor(palette, background, level);
        return palette;
    }

    private static int color(Properties properties, String key) {
        return Color.parseColor(properties.getProperty(key));
    }
}
