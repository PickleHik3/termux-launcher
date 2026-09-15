package com.termux.app.terminal;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.terminal.TerminalColorScheme;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
                assertTrue("ANSI " + i + " at " + level.value,
                    MaterialTerminalColorScheme.contrastRatio(
                        color(palette, "color" + i), background) + .01 >= level.ansiRatio);
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

    private static int color(Properties properties, String key) {
        return Color.parseColor(properties.getProperty(key));
    }
}
