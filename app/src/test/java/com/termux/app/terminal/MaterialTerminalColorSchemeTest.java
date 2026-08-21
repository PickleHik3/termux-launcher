package com.termux.app.terminal;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.terminal.TerminalColorScheme;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
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

    private static int color(Properties properties, String key) {
        return Color.parseColor(properties.getProperty(key));
    }
}
