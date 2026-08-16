package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Properties;

/**
 * Which properties are allowed to reach the terminal palette.
 *
 * <p>{@code TerminalColorScheme.updateWith()} throws on the first key it does not recognise, and it
 * does so while iterating an unordered map — so one stray line does not get ignored, it leaves the
 * palette half applied and skips the session reset and background update behind it. That fired on
 * every single activity start once {@code contrast_level} was put in the same bag as the colours
 * (issue #11's third symptom), and a hand-written {@code colors.properties} can do the same.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalPaletteSourceTest {

    @Test
    public void colourKeysSurvive() {
        Properties props = new Properties();
        props.setProperty("foreground", "#FFFFFF");
        props.setProperty("background", "#000000");
        props.setProperty("cursor", "#FF0000");
        props.setProperty("color0", "#101010");
        props.setProperty("color15", "#EEEEEE");

        Properties filtered = TermuxTerminalSessionActivityClient.colorKeysOnly(props);

        assertEquals(props.stringPropertyNames(), filtered.stringPropertyNames());
        assertEquals("#FF0000", filtered.getProperty("cursor"));
    }

    @Test
    public void everythingElseIsDropped() {
        Properties props = new Properties();
        props.setProperty("background", "#000000");
        props.setProperty("contrast_level", "harder");
        props.setProperty("colour3", "#123456");
        props.setProperty("color", "#123456");
        props.setProperty("colorX", "#123456");
        props.setProperty("", "#123456");

        Properties filtered = TermuxTerminalSessionActivityClient.colorKeysOnly(props);

        assertEquals(1, filtered.size());
        assertEquals("#000000", filtered.getProperty("background"));
        assertNull(filtered.getProperty("contrast_level"));
    }

    /** The whole point: what survives the filter must not throw on the way in. */
    @Test
    public void theFilteredPaletteIsAcceptedByTheColourScheme() {
        Properties props = new Properties();
        props.setProperty("background", "#000000");
        props.setProperty("foreground", "#FFFFFF");
        props.setProperty("contrast_level", "harder");
        props.setProperty("not a colour at all", "nonsense");

        new com.termux.terminal.TerminalColorScheme()
            .updateWith(TermuxTerminalSessionActivityClient.colorKeysOnly(props));
    }

    /** Double-digit indices are real: color10 through color15 are the bright half of the palette. */
    @Test
    public void multiDigitColourIndicesAreKept() {
        Properties props = new Properties();
        for (int i = 0; i < 16; i++) props.setProperty("color" + i, "#010101");

        assertEquals(16, TermuxTerminalSessionActivityClient.colorKeysOnly(props).size());
    }

    @Test
    public void anEmptyPaletteFiltersToAnEmptyPalette() {
        assertTrue(TermuxTerminalSessionActivityClient.colorKeysOnly(new Properties()).isEmpty());
        assertFalse(TermuxTerminalSessionActivityClient.colorKeysOnly(new Properties())
            .stringPropertyNames().iterator().hasNext());
    }
}
