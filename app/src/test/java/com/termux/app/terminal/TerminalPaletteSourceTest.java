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

    /**
     * The background day/night push (D2) starts from an exported role palette, not from a terminal
     * one: it has to strip the {@code terminal_} prefix and drop noctalia's aliases, or the first
     * {@code terminal_normal_black} throws out of {@code updateWith} and the sessions keep the
     * palette the wallpaper wore before the flip.
     */
    @Test
    public void theExportedPaletteYieldsTheTerminalColoursAlone() {
        Properties exported = new Properties();
        exported.setProperty("primary", "#4080C0");
        exported.setProperty("mode", "dark");
        exported.setProperty("contrast_level", "default");
        exported.setProperty("terminal_foreground", "#FFFFFF");
        exported.setProperty("terminal_background", "#000000");
        exported.setProperty("terminal_cursor", "#FF0000");
        exported.setProperty("terminal_color0", "#101010");
        exported.setProperty("terminal_color15", "#EEEEEE");
        exported.setProperty("terminal_normal_black", "#101010");
        exported.setProperty("terminal_bright_white", "#EEEEEE");
        exported.setProperty("terminal_selection_bg", "#222222");
        exported.setProperty("terminal_cursor_text", "#000000");

        Properties terminal = TermuxTerminalSessionActivityClient.terminalColorsOf(exported);

        assertEquals(5, terminal.size());
        assertEquals("#FFFFFF", terminal.getProperty("foreground"));
        assertEquals("#101010", terminal.getProperty("color0"));
        assertNull(terminal.getProperty("normal_black"));
        assertNull(terminal.getProperty("primary"));
        assertNull(terminal.getProperty("mode"));
        // The real consumer, which throws on anything it does not recognise.
        new com.termux.terminal.TerminalColorScheme().updateWith(terminal);
    }

    /** A palette with nothing terminal in it is not an empty palette to push, it is no push. */
    @Test
    public void anExportWithoutTerminalKeysYieldsNothing() {
        Properties exported = new Properties();
        exported.setProperty("primary", "#4080C0");
        assertTrue(TermuxTerminalSessionActivityClient.terminalColorsOf(exported).isEmpty());
    }

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
