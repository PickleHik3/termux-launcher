package com.termux.app.terminal;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
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
            assertEquals(level.value, palette.getProperty("contrast_level"));
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

    private static int color(Properties properties, String key) {
        return Color.parseColor(properties.getProperty(key));
    }
}
