package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.theme.LauncherThemeTokens;
import com.termux.app.theme.templates.PaletteSet;
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
                color(palette, "foreground"), background) + .01 >= level.bodyTarget);
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
     * The pane's glass is tinted from Default's background at every level: Terminal contrast moves
     * the text, never the glass. The overlay used to read the level's own background, so Harder
     * darkened the pane and Softer lifted it — and a palette flipped to light by a bright wallpaper
     * would have turned the pane into a light slab. The helper still agrees with the Default
     * palette, so an opaque Default terminal and its glass are one colour.
     */
    @Test
    public void thePaneOverlayBaseIsTheSameAtEveryLevel() {
        Context context = ApplicationProvider.getApplicationContext();
        int overlay = MaterialTerminalColorScheme.overlayBaseColor(context) | 0xFF000000;
        assertEquals(color(MaterialTerminalColorScheme.create(context,
            TerminalContrastLevel.DEFAULT), "background"), overlay);

        int pongOverlay = MaterialTerminalColorScheme.overlayBase(PONG.surface);
        assertEquals(color(MaterialTerminalColorScheme.build(PONG, TerminalContrastLevel.DEFAULT,
            Color.TRANSPARENT, null), "background"), pongOverlay);
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            for (int ground : new int[] {Color.TRANSPARENT, SKY, DUSK}) {
                Properties palette = MaterialTerminalColorScheme.build(PONG, level, ground, null);
                // The palette is free to move; the overlay is not an input it can move.
                assertEquals(level.value + " on " + Integer.toHexString(ground), pongOverlay,
                    MaterialTerminalColorScheme.overlayBase(PONG.surface));
                if (level != TerminalContrastLevel.DEFAULT || ground == SKY) {
                    assertNotEquals("the palette background is what moves, at " + level.value,
                        pongOverlay, color(palette, "background"));
                }
            }
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

    // ---------------------------------------------------------------------------------------------
    // Both palettes per pass (D1). Dynamic colours are pure resource qualifiers, so forcing the
    // night bits on a configuration context resolves the other mode's roles with no activity in
    // sight — which is what lets a template carry both tables.
    // ---------------------------------------------------------------------------------------------

    /**
     * The forced-mode derivation has to agree with the ordinary one for the mode the phone is
     * actually in, or the active palette and the mode file describing it would disagree.
     */
    @Test
    public void theForcedModePaletteMatchesThePlainThemedContext() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Context themed = themedContext();
            Properties plain = MaterialTerminalColorScheme.createMaterialRoleProperties(themed,
                MaterialTerminalColorScheme.create(themed, level), level);
            PaletteSet palettes = MaterialTerminalColorScheme.createPaletteSet(themed, level);
            assertEquals("active at " + level.value, plain, palettes.active());
            assertTrue("dark at " + level.value, palettes.hasDark());
            assertTrue("light at " + level.value, palettes.hasLight());
            Properties current = "dark".equals(plain.getProperty("mode"))
                ? palettes.dark() : palettes.light();
            assertEquals("current mode at " + level.value, plain, current);
        }
    }

    /** And the two halves have to be genuinely different palettes, whichever mode is on. */
    @Test
    public void bothHalvesAreDerivedWhicheverModeThePhoneIsIn() {
        try {
            for (String qualifier : new String[] {"+notnight", "+night"}) {
                RuntimeEnvironment.setQualifiers(qualifier);
                PaletteSet palettes = MaterialTerminalColorScheme.createPaletteSet(
                    themedContext(), TerminalContrastLevel.DEFAULT);
                assertEquals(qualifier + " dark palette", "dark", palettes.dark().getProperty("mode"));
                assertEquals(qualifier + " light palette", "light", palettes.light().getProperty("mode"));
                assertNotEquals(qualifier, palettes.dark(), palettes.light());
                // The active palette is one of the two, not a third derivation of its own.
                assertEquals(qualifier, "night".equals(qualifier.substring(1))
                        ? palettes.dark() : palettes.light(), palettes.active());
            }
        } finally {
            RuntimeEnvironment.setQualifiers("+notnight");
        }
    }

    /** A set carrying only the active palette answers for all three modes with it. */
    @Test
    public void aSinglePaletteSetStandsInForBothModes() {
        Properties only = MaterialTerminalColorScheme.createMaterialRoleProperties(
            themedContext(),
            MaterialTerminalColorScheme.create(themedContext(), TerminalContrastLevel.DEFAULT),
            TerminalContrastLevel.DEFAULT);
        PaletteSet palettes = PaletteSet.of(only);
        assertFalse(palettes.hasDark());
        assertFalse(palettes.hasLight());
        assertSame(only, palettes.dark());
        assertSame(only, palettes.light());
        assertSame(only, palettes.forMode("default"));
        assertNull(palettes.forMode("sepia"));
    }

    private static Context themedContext() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
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

    /**
     * The mode files are the same format as the two the shells have always read — same header, same
     * sorted keys, same {@code TERMUX_MATERIAL_*} exports — with {@code mode} stated rather than
     * derived, and the palette they were built from left alone.
     */
    @Test
    public void theModeFilesAreTheSameFormatWithTheirModeFixed() {
        Context themed = themedContext();
        PaletteSet palettes = MaterialTerminalColorScheme.createPaletteSet(
            themed, TerminalContrastLevel.DEFAULT);
        Properties light = palettes.light();
        Properties darkFile = MaterialTerminalColorScheme.withMode(palettes.dark(), "dark");
        Properties lightFile = MaterialTerminalColorScheme.withMode(light, "light");

        assertEquals("dark", darkFile.getProperty("mode"));
        assertEquals("light", lightFile.getProperty("mode"));
        // Stating the mode must not edit the palette the templates are being rendered from.
        assertNotSame(light, lightFile);
        assertEquals(light.stringPropertyNames(), lightFile.stringPropertyNames());
        for (String key : light.stringPropertyNames()) {
            if ("mode".equals(key)) continue;
            assertEquals(key, light.getProperty(key), lightFile.getProperty(key));
        }

        String text = MaterialTerminalColorScheme.toPropertiesText(darkFile);
        assertTrue(text.startsWith("# Generated by Termux. Do not edit.\n"));
        assertTrue(text.contains("\nmode=dark\n"));
        assertTrue(MaterialTerminalColorScheme.toShellExports(lightFile)
            .contains("export TERMUX_MATERIAL_MODE='light'"));
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

    /** Neutrals come off the neutral palette: no more chroma than a neutral has, however vivid the theme. */
    @Test
    public void neutralSlotsStayNeutral() {
        Properties palette = MaterialTerminalColorScheme.ansiSlots(220d, 48d, 25d, 300d, 90d, true);
        for (String key : new String[] {"color0", "color7", "color8", "color15"}) {
            // The ceiling is 12 (D8 raised it from 6 with the warm nudge); a tone at the top of the
            // ladder cannot always hold that much chroma in sRGB, so allow for the gamut mapping.
            assertTrue(key + " chroma", Hct.fromInt(color(palette, key)).getChroma() <= 13d);
        }
    }

    /**
     * D8: the neutrals lean warm rather than sitting on the surface's own hue, which is what the
     * retired {@code material-terminal-white.fish} was doing by hand on the phone. Halfway to 75°,
     * never more than 20° of it, and never past it.
     */
    @Test
    public void theNeutralHueLeansWarmWithoutPassingTheTarget() {
        for (double surfaceHue : new double[] {0d, 40d, 110d, 210d, 260d, 300d, 359d}) {
            double warm = MaterialTerminalColorScheme.warmNeutralHue(surfaceHue);
            double distance = angleBetween(surfaceHue, 75d);
            assertEquals("surface " + surfaceHue + " moved the wrong distance",
                Math.min(distance * 0.5d, 20d), angleBetween(surfaceHue, warm), 1e-9);
            assertTrue("surface " + surfaceHue + " landed at " + warm + ", past the target",
                angleBetween(warm, 75d) <= distance + 1e-9);
        }
    }

    /** A theme already at the warm hue has nowhere to go, and must not be rotated off it. */
    @Test
    public void aSurfaceAlreadyWarmIsNotRotated() {
        assertEquals(75d, MaterialTerminalColorScheme.warmNeutralHue(75d), 1e-9);
        Properties palette = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 25d, 75d, 4d, true);
        for (String key : new String[] {"color0", "color7", "color8", "color15"}) {
            assertEquals(key, 75d, Hct.fromInt(color(palette, key)).getHue(), 8d);
        }
    }

    /** The nudge is a colour move, not a tone move: the ladder keeps every tone it had. */
    @Test
    public void theWarmNudgeLeavesTheSlotTonesWhereTheyWere() {
        for (boolean dark : new boolean[] {true, false}) {
            Properties palette = slots(220d, 40d, dark);
            assertEquals(25d, tone(palette, "color0"), 1d);
            assertEquals(dark ? 45d : 50d, tone(palette, "color8"), 1d);
            assertEquals(dark ? 80d : 75d, tone(palette, "color7"), 1d);
            assertEquals(dark ? 96d : 92d, tone(palette, "color15"), 1d);
            for (String key : new String[] {"color0", "color7", "color8", "color15"}) {
                // Wide, deliberately: at neutral chroma the sRGB round trip moves a hue by a few
                // degrees on its own. What is being asserted is the lean, not a number.
                assertEquals(key + " hue", MaterialTerminalColorScheme.warmNeutralHue(220d),
                    Hct.fromInt(color(palette, key)).getHue(), 8d);
            }
        }
    }

    /** A near-grey theme has to be lifted to the floor, a vivid one held at the ceiling. */
    @Test
    public void theNeutralChromaStaysInsideItsBand() {
        assertEquals(8d, MaterialTerminalColorScheme.warmNeutralChroma(0d), 1e-9);
        assertEquals(8d, MaterialTerminalColorScheme.warmNeutralChroma(7.9d), 1e-9);
        assertEquals(10d, MaterialTerminalColorScheme.warmNeutralChroma(10d), 1e-9);
        assertEquals(12d, MaterialTerminalColorScheme.warmNeutralChroma(90d), 1e-9);
        Properties grey = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 25d, 220d, 0d, true);
        for (String key : new String[] {"color0", "color7", "color8", "color15"}) {
            assertTrue(key + " must not be a pure grey",
                Hct.fromInt(color(grey, key)).getChroma() >= 5d);
        }
    }

    /**
     * The foreground is a neutral as much as slots 0/7/8/15 are, and gets the same nudge — with the
     * legibility floor still met afterwards, since the search moves tone only.
     */
    @Test
    public void theForegroundIsWarmedToo() {
        Context themed = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties palette = MaterialTerminalColorScheme.create(themed, level);
            Hct foreground = Hct.fromInt(color(palette, "foreground"));
            // The theme's own surface, which is what the derivation takes the neutral hue from —
            // not the generated background, whose tone move can leave it too grey to have a hue.
            Hct surface = Hct.fromInt(com.google.android.material.color.MaterialColors.getColor(
                themed, com.google.android.material.R.attr.colorSurface, 0) | 0xFF000000);
            // Harder's light foreground sits at tone 2, where sRGB holds almost no chroma and the
            // round trip leaves no hue worth measuring; the lean is asserted where there is one.
            if (foreground.getChroma() >= 4d) assertEquals("foreground hue at " + level.value,
                MaterialTerminalColorScheme.warmNeutralHue(surface.getHue()),
                foreground.getHue(), 5d);
            assertTrue("foreground chroma at " + level.value, foreground.getChroma() <= 13d);
            assertTrue("foreground ratio at " + level.value,
                MaterialTerminalColorScheme.contrastRatio(
                    color(palette, "foreground"), color(palette, "background"))
                    + .01 >= level.bodyTarget);
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

    private static double meanAccentChroma(Properties p) {
        double sum = 0d;
        for (int slot = 1; slot <= 6; slot++) sum += Hct.fromInt(color(p, "color" + slot)).getChroma();
        return sum / 6d;
    }

    /** Softer is pastel, Harder punchier: the recipe's chroma band orders the accent slots. */
    @Test
    public void accentChromaOrdersSofterDefaultHarder() {
        Properties softer = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 25d, 260d, 4d, true,
            TerminalContrastLevel.SOFTER);
        Properties dflt = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 25d, 260d, 4d, true,
            TerminalContrastLevel.DEFAULT);
        Properties harder = MaterialTerminalColorScheme.ansiSlots(220d, 40d, 25d, 260d, 4d, true,
            TerminalContrastLevel.HARDER);
        assertTrue(meanAccentChroma(softer) < meanAccentChroma(dflt));
        assertTrue(meanAccentChroma(dflt) < meanAccentChroma(harder));
    }

    /**
     * The background tone is the recipe's, and the levels order it: Softer lifts the ground,
     * Harder takes it to the end of the scale in both modes.
     */
    @Test
    public void surfaceToneFollowsTheRecipe() {
        int dark = Hct.from(260d, 4d, 10d).toInt();
        int light = Hct.from(260d, 4d, 90d).toInt();
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            assertEquals(level.bgToneDark,
                Hct.fromInt(MaterialTerminalColorScheme.surfaceTone(dark, level)).getTone(), 1.0d);
            assertEquals(level.bgToneLight,
                Hct.fromInt(MaterialTerminalColorScheme.surfaceTone(light, level)).getTone(), 1.0d);
            // The polarity can be asked for explicitly, which is how a flipped palette gets the
            // other mode's ground from the theme's own surface.
            assertEquals(level.bgToneLight, Hct.fromInt(
                MaterialTerminalColorScheme.surfaceTone(dark, level, false)).getTone(), 1.0d);
        }
        assertTrue(TerminalContrastLevel.SOFTER.bgToneDark > TerminalContrastLevel.DEFAULT.bgToneDark);
        assertTrue(TerminalContrastLevel.DEFAULT.bgToneDark > TerminalContrastLevel.HARDER.bgToneDark);
        assertTrue(TerminalContrastLevel.SOFTER.bgToneLight < TerminalContrastLevel.DEFAULT.bgToneLight);
        assertTrue(TerminalContrastLevel.DEFAULT.bgToneLight < TerminalContrastLevel.HARDER.bgToneLight);
    }

    // ---------------------------------------------------------------------------------------------
    // The level owns the text, measured against what the text stands on. pong's seeds (dark
    // mode) and the grounds measured on it: a transparent pane over a sky wallpaper, and a dark
    // one. These are the regression for "Harder looks the same as Default": the floors never bound
    // on the palette's own background, and on glass they were measured against a background the
    // pane never shows.
    // ---------------------------------------------------------------------------------------------

    /** pong: surface, on_surface, primary, error. */
    private static final MaterialTerminalColorScheme.Seeds PONG = new MaterialTerminalColorScheme.Seeds(
        0xFF0C141B, 0xFFDCE3ED, 0xFF93CCFF, 0xFFF2B8B5);
    /** A light theme's seeds, for the other polarity of the opaque recipe. */
    private static final MaterialTerminalColorScheme.Seeds DAYLIGHT = new MaterialTerminalColorScheme.Seeds(
        0xFFF8F9FF, 0xFF191C20, 0xFF3A608F, 0xFFBA1A1A);
    /** The ground behind pong's prompt, transparent pane over a sky wallpaper: L about 0.30. */
    private static final int SKY = 0xFF569CC3;
    /** A dark wallpaper's ground. */
    private static final int DUSK = 0xFF1B1A17;
    /** A grey just past the black/white crossover (0.179): dark ink reads a shade better. */
    private static final int CROSSOVER = 0xFF797979;

    private static double ratio(int ink, int ground) {
        return MaterialTerminalColorScheme.contrastRatio(ink, ground);
    }

    private static double bodyRatio(Properties palette, int ground) {
        return ratio(color(palette, "foreground"), ground);
    }

    /**
     * (a) On the terminal's own background every step is visible: the foreground and background
     * tones are the level's, not floors under the theme's, so body text moves by at least 15% per
     * step. Default to Harder used to move it by 4%.
     */
    @Test
    public void eachLevelStepMovesBodyTextOnAnOpaqueGround() {
        for (MaterialTerminalColorScheme.Seeds seeds : new MaterialTerminalColorScheme.Seeds[] {PONG, DAYLIGHT}) {
            double previous = 0d;
            for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
                Properties palette = MaterialTerminalColorScheme.build(seeds, level,
                    Color.TRANSPARENT, null);
                double body = bodyRatio(palette, color(palette, "background"));
                assertTrue(level.value + " body " + body + " over " + previous,
                    body >= previous * 1.15d);
                assertTrue(level.value + " meets its target", body + .01 >= level.bodyTarget);
                previous = body;
            }
        }
    }

    /**
     * (b) Over the sky ground no pale ink reaches the body target — white is 3.03:1 — so the
     * palette flips to dark ink, takes the light recipe and reports light. Measured against the
     * ground, not the palette's own background. Harder's 7:1 is out of reach of any ink on this
     * ground (black is 6.94:1); it gets as close as the gamut allows.
     */
    @Test
    public void aSkyGroundFlipsThePaletteToDarkInkAndMeetsTheLevel() {
        Context context = ApplicationProvider.getApplicationContext();
        double black = ratio(Color.BLACK, SKY);
        assertTrue("black is the most any ink reads on the sky", black < 7.0d);
        double previous = 0d;
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties palette = MaterialTerminalColorScheme.build(PONG, level, SKY,
                com.termux.app.chrome.ChromeInk.Polarity.PALE_INK);
            assertSame(level.value, com.termux.app.chrome.ChromeInk.Polarity.DARK_INK,
                MaterialTerminalColorScheme.polarityOf(palette));
            assertTrue(level.value + " background is light",
                Hct.fromInt(color(palette, "background")).getTone() >= 50d);
            assertEquals(level.value + " reports light", "light",
                MaterialTerminalColorScheme.createMaterialRoleProperties(context, palette, level)
                    .getProperty("mode"));
            double body = bodyRatio(palette, SKY);
            // Text stops short of pure black, so Harder's 7:1 tops out a little under black's.
            assertTrue(level.value + " body " + body,
                body + .01 >= Math.min(level.bodyTarget, 6.0d));
            assertTrue(level.value + " is more legible than the level below", body > previous);
            previous = body;
            // The ANSI floors are out of reach on this ground for any colour that keeps its hue;
            // noLevelCollapsesThePaletteToBlackOrWhite holds them to their colour instead.
            assertTrue("cursor at " + level.value,
                ratio(color(palette, "cursor"), SKY) + .01 >= level.cursorRatio);
        }
        assertTrue("Softer reaches 3.0", bodyRatio(MaterialTerminalColorScheme.build(PONG,
            TerminalContrastLevel.SOFTER, SKY, null), SKY) + .01 >= 3.0d);
        assertTrue("Default reaches 4.5", bodyRatio(MaterialTerminalColorScheme.build(PONG,
            TerminalContrastLevel.DEFAULT, SKY, null), SKY) + .01 >= 4.5d);
        double harder = bodyRatio(MaterialTerminalColorScheme.build(PONG,
            TerminalContrastLevel.HARDER, SKY, null), SKY);
        assertTrue("Harder reads closest to black without being black: " + harder,
            harder > 6.0d && harder < black);
    }

    /** (c) A dark wallpaper keeps the theme's pale ink, and every level still meets its target. */
    @Test
    public void aDarkGroundKeepsPaleInkAndMeetsTheLevel() {
        double previous = 0d;
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties palette = MaterialTerminalColorScheme.build(PONG, level, DUSK, null);
            assertSame(level.value, com.termux.app.chrome.ChromeInk.Polarity.PALE_INK,
                MaterialTerminalColorScheme.polarityOf(palette));
            double body = bodyRatio(palette, DUSK);
            assertTrue(level.value + " body " + body, body + .01 >= level.bodyTarget);
            assertTrue(level.value + " is more legible than the level below", body > previous);
            previous = body;
            for (int i = 0; i < 16; i++) {
                assertTrue("ANSI " + i + " at " + level.value,
                    ratio(color(palette, "color" + i), DUSK) + .01
                        >= MaterialTerminalColorScheme.ansiFloor(i, level));
            }
        }
    }

    /**
     * (d) Near the crossover both inks read about the same, and the palette keeps whichever it
     * wears: a re-sampled wallpaper there must not flap the terminal between light and dark.
     */
    @Test
    public void aGroundNearTheCrossoverKeepsTheCurrentInk() {
        com.termux.app.chrome.ChromeInk.Polarity pale = com.termux.app.chrome.ChromeInk.Polarity.PALE_INK;
        com.termux.app.chrome.ChromeInk.Polarity dark = com.termux.app.chrome.ChromeInk.Polarity.DARK_INK;
        assertTrue("dark ink reads a shade better here",
            ratio(Color.BLACK, CROSSOVER) > ratio(Color.WHITE, CROSSOVER));
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            assertSame(level.value, pale,
                MaterialTerminalColorScheme.inkPolarity(CROSSOVER, level.bodyTarget, pale));
            assertSame(level.value, dark,
                MaterialTerminalColorScheme.inkPolarity(CROSSOVER, level.bodyTarget, dark));
            // Far from it the polarity does follow the ground, whatever it wore.
            assertSame(level.value, dark,
                MaterialTerminalColorScheme.inkPolarity(Color.WHITE, level.bodyTarget, pale));
            assertSame(level.value, pale,
                MaterialTerminalColorScheme.inkPolarity(Color.BLACK, level.bodyTarget, dark));
            // And an answer is stable when asked again with itself as the current ink.
            for (int ground : new int[] {SKY, DUSK, CROSSOVER}) {
                for (com.termux.app.chrome.ChromeInk.Polarity current : new com.termux.app.chrome.ChromeInk.Polarity[] {pale, dark}) {
                    com.termux.app.chrome.ChromeInk.Polarity answer =
                        MaterialTerminalColorScheme.inkPolarity(ground, level.bodyTarget, current);
                    assertSame(answer,
                        MaterialTerminalColorScheme.inkPolarity(ground, level.bodyTarget, answer));
                }
            }
        }
        assertSame(pale, MaterialTerminalColorScheme.polarityOf(MaterialTerminalColorScheme.build(
            PONG, TerminalContrastLevel.DEFAULT, CROSSOVER, pale)));
        assertSame(dark, MaterialTerminalColorScheme.polarityOf(MaterialTerminalColorScheme.build(
            PONG, TerminalContrastLevel.DEFAULT, CROSSOVER, dark)));
    }

    /**
     * A palette the sky flipped to light reports light and hands tools light roles to go with it:
     * the active roles are the light mode's, around the active terminal keys. They used to stay the
     * dark theme's, so a template pairing {@code on_surface} with {@code terminal_background} drew
     * pale text on a light terminal.
     */
    @Test
    public void aFlippedPaletteExportsTheOtherModesRoles() throws Exception {
        try {
            RuntimeEnvironment.setQualifiers("+night");
            Context themed = themedContext();
            TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
            Properties flipped = MaterialTerminalColorScheme.create(themed, level, SKY, null);
            assertSame(com.termux.app.chrome.ChromeInk.Polarity.DARK_INK,
                MaterialTerminalColorScheme.polarityOf(flipped));
            PaletteSet[] sets = {
                MaterialTerminalColorScheme.createPaletteSet(themed, level, flipped),
                MaterialTerminalColorScheme.paletteSetSource(themed, level, flipped).call(),
            };
            for (PaletteSet palettes : sets) {
                Properties active = palettes.active();
                assertEquals("light", active.getProperty("mode"));
                assertEquals(palettes.light().getProperty("on_surface"),
                    active.getProperty("on_surface"));
                assertEquals(palettes.light().getProperty("surface"), active.getProperty("surface"));
                assertNotEquals(palettes.dark().getProperty("on_surface"),
                    active.getProperty("on_surface"));
                assertTrue("on_surface is a dark role", tone(active, "on_surface") < 50d);
                assertEquals(flipped.getProperty("foreground"),
                    active.getProperty("terminal_foreground"));
                assertEquals(flipped.getProperty("background"),
                    active.getProperty("terminal_background"));
            }
            // A palette that keeps the theme's polarity keeps the theme's roles.
            PaletteSet own = MaterialTerminalColorScheme.createPaletteSet(themed, level,
                MaterialTerminalColorScheme.create(themed, level, DUSK, null));
            assertEquals("dark", own.active().getProperty("mode"));
            assertEquals(own.dark().getProperty("on_surface"),
                own.active().getProperty("on_surface"));
        } finally {
            RuntimeEnvironment.setQualifiers("+notnight");
        }
    }

    /**
     * At full opacity in wallpaper mode the pane's tint is opaque, so the measured ground is the
     * tint whatever the wallpaper is: a dark theme over a white wallpaper stays pale ink, and the
     * floors are measured on the overlay base the pane really shows.
     */
    @Test
    public void anOpaqueTintOverABrightWallpaperDoesNotFlipADarkTheme() {
        int tint = MaterialTerminalColorScheme.overlayBase(PONG.surface);
        int ground = com.termux.app.chrome.OnGlass.backdrop(Color.WHITE, 0x33000000, tint);
        assertEquals(tint, ground);
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            Properties palette = MaterialTerminalColorScheme.build(PONG, level, ground, null);
            assertSame(level.value, com.termux.app.chrome.ChromeInk.Polarity.PALE_INK,
                MaterialTerminalColorScheme.polarityOf(palette));
            assertTrue(level.value, bodyRatio(palette, ground) + .01 >= level.bodyTarget);
        }
    }

    /**
     * The ground and its ink outlive the process, so a cold start builds its first palette on
     * what the last run measured rather than on the nominal glass, and the night-flip export
     * builds on it too. Nothing stored reads as transparent: the nominal glass then stands in.
     */
    @Test
    public void theLastGroundSurvivesARestart() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("terminal_ground", Context.MODE_PRIVATE).edit().clear().commit();
        MaterialTerminalColorScheme.forgetGroundInMemory();
        try {
            assertEquals(Color.TRANSPARENT, MaterialTerminalColorScheme.lastGroundColor(context));
            assertNull(MaterialTerminalColorScheme.lastGroundPolarity(context));

            MaterialTerminalColorScheme.rememberGround(context, SKY,
                com.termux.app.chrome.ChromeInk.Polarity.DARK_INK);
            MaterialTerminalColorScheme.forgetGroundInMemory();
            assertEquals(SKY, MaterialTerminalColorScheme.lastGroundColor(context));
            assertSame(com.termux.app.chrome.ChromeInk.Polarity.DARK_INK,
                MaterialTerminalColorScheme.lastGroundPolarity(context));

            // The export with no activity builds on it: the sky's flip, light roles and all.
            RuntimeEnvironment.setQualifiers("+night");
            PaletteSet palettes = MaterialTerminalColorScheme.createPaletteSetOnLastGround(
                themedContext(), TerminalContrastLevel.DEFAULT);
            assertEquals("light", palettes.active().getProperty("mode"));
        } finally {
            RuntimeEnvironment.setQualifiers("+notnight");
            context.getSharedPreferences("terminal_ground", Context.MODE_PRIVATE).edit().clear()
                .commit();
            MaterialTerminalColorScheme.forgetGroundInMemory();
        }
    }

    /** An opaque terminal never flips, whatever ink it was last told it wore. */
    @Test
    public void anOpaqueTerminalKeepsTheThemesPolarity() {
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
            assertSame(com.termux.app.chrome.ChromeInk.Polarity.PALE_INK,
                MaterialTerminalColorScheme.polarityOf(MaterialTerminalColorScheme.build(PONG,
                    level, Color.TRANSPARENT, com.termux.app.chrome.ChromeInk.Polarity.DARK_INK)));
            assertSame(com.termux.app.chrome.ChromeInk.Polarity.DARK_INK,
                MaterialTerminalColorScheme.polarityOf(MaterialTerminalColorScheme.build(DAYLIGHT,
                    level, Color.TRANSPARENT, com.termux.app.chrome.ChromeInk.Polarity.PALE_INK)));
        }
    }

    /**
     * (f) The contrast search stays inside its window: a target out of reach takes the best tone
     * the window allows, not black or white. Chasing it all the way is what turned every colour on
     * pong's sky into pure black (Harder) or pure white (Default).
     */
    @Test
    public void contrastToneStaysInsideItsWindow() {
        int pale = Hct.from(250d, 10d, 85d).toInt();
        int moved = MaterialTerminalColorScheme.contrastTone(pale, SKY, 7.0d, 10d, 20d, 90d, 0d);
        double tone = Hct.fromInt(moved).getTone();
        assertTrue("tone " + tone + " stays in the window", tone >= 74.5d && tone <= 90.5d);
        assertNotEquals(Color.BLACK, moved);
        assertNotEquals(Color.WHITE, moved);
        assertTrue("never reads worse than it did", ratio(moved, SKY) >= ratio(pale, SKY));
        // A target the window can reach is met at the nearest tone that meets it.
        assertTrue(ratio(MaterialTerminalColorScheme.contrastTone(pale, SKY, 2.3d, 10d, 20d, 90d, 0d),
            SKY) >= 2.3d);
        // And a colour already past the target is left alone, window or not.
        assertEquals(Color.BLACK,
            MaterialTerminalColorScheme.contrastTone(Color.BLACK, SKY, 3.0d, 10d, 20d, 90d, 0d));
    }

    /** pong's pane, transparent over the sky, as the chrome measured it: the mean under the pane. */
    private static final int SKY_PANE = 0xFF1A7BC4;

    /**
     * The reported symptom: on pong's sky every colour went pure black at Harder and pure white at
     * Default. Over any ground, at every level and whichever ink the palette wears, the text stops
     * short of pure black and white and every ANSI accent keeps its hue and most of its colour.
     */
    @Test
    public void noLevelCollapsesThePaletteToBlackOrWhite() {
        for (int ground : new int[] {SKY, SKY_PANE, DUSK, CROSSOVER, Color.WHITE}) {
            for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
                for (com.termux.app.chrome.ChromeInk.Polarity current
                    : com.termux.app.chrome.ChromeInk.Polarity.values()) {
                    Properties palette = MaterialTerminalColorScheme.build(PONG, level, ground,
                        current);
                    String at = level.value + " on " + Integer.toHexString(ground) + " from "
                        + current;
                    int foreground = color(palette, "foreground");
                    assertNotEquals(at, Color.BLACK, foreground | 0xFF000000);
                    assertNotEquals(at, Color.WHITE, foreground | 0xFF000000);
                    Hct primary = Hct.fromInt(PONG.primary);
                    Properties recipe = MaterialTerminalColorScheme.ansiSlots(primary.getHue(),
                        primary.getChroma(), Hct.fromInt(PONG.error).getHue(),
                        Hct.fromInt(PONG.surface).getHue(), Hct.fromInt(PONG.surface).getChroma(),
                        MaterialTerminalColorScheme.polarityOf(palette)
                            == com.termux.app.chrome.ChromeInk.Polarity.PALE_INK, level);
                    java.util.Set<Integer> seen = new java.util.HashSet<>();
                    for (int slot : new int[] {1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14}) {
                        Hct built = Hct.fromInt(color(palette, "color" + slot));
                        Hct asked = Hct.fromInt(color(recipe, "color" + slot));
                        assertTrue(at + " color" + slot + " keeps its colour: chroma "
                                + built.getChroma() + " of " + asked.getChroma(),
                            built.getChroma() >= Math.min(15d, asked.getChroma() * 0.6d));
                        double hueDrift = Math.abs(built.getHue() - asked.getHue()) % 360d;
                        assertTrue(at + " color" + slot + " keeps its hue",
                            Math.min(hueDrift, 360d - hueDrift) <= 20d);
                        seen.add(color(palette, "color" + slot));
                    }
                    assertTrue(at + ": the normal accents stay six distinct colours",
                        seen.size() >= 6);
                }
            }
        }
    }

    /**
     * The fingerprint follows the ground the text stands on: an opaque terminal adds nothing, a
     * re-sample one RGB unit off stays put, a different wallpaper moves it.
     */
    @Test
    public void theSignatureFollowsTheGround() {
        Context context = themedContext();
        TerminalContrastLevel level = TerminalContrastLevel.DEFAULT;
        int opaque = MaterialTerminalColorScheme.signature(context, level);
        assertEquals(opaque, MaterialTerminalColorScheme.signature(context, level,
            Color.TRANSPARENT, com.termux.app.chrome.ChromeInk.Polarity.DARK_INK));
        int sky = MaterialTerminalColorScheme.signature(context, level, SKY, null);
        assertNotEquals(opaque, sky);
        assertEquals(sky, MaterialTerminalColorScheme.signature(context, level, SKY + 1, null));
        assertNotEquals(sky, MaterialTerminalColorScheme.signature(context, level, DUSK, null));
        assertEquals(MaterialTerminalColorScheme.groundToneBucket(SKY),
            MaterialTerminalColorScheme.groundToneBucket(SKY + 1));
    }

    private static int color(Properties properties, String key) {
        return Color.parseColor(properties.getProperty(key));
    }
}
