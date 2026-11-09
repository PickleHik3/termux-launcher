package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;

/** The retired Tonal and Pane directions read as the Numerals choice that matches them. */
public class BuiltinWidgetNumeralsMigrationTest {
    @Test public void aStoredTonalReadsAsSans() {
        assertEquals("sans", TermuxAppSharedPreferences.normaliseBuiltinWidgetNumerals("tonal"));
        assertEquals(BuiltinWidgetStyle.Numerals.SANS, BuiltinWidgetStyle.Numerals.fromKey("tonal"));
    }

    @Test public void aStoredPaneReadsAsMono() {
        assertEquals("mono", TermuxAppSharedPreferences.normaliseBuiltinWidgetNumerals("pane"));
        assertEquals(BuiltinWidgetStyle.Numerals.MONO, BuiltinWidgetStyle.Numerals.fromKey("pane"));
    }

    @Test public void theNewChoicesAreKept() {
        assertEquals("sans", TermuxAppSharedPreferences.normaliseBuiltinWidgetNumerals("sans"));
        assertEquals("mono", TermuxAppSharedPreferences.normaliseBuiltinWidgetNumerals("mono"));
        assertEquals(BuiltinWidgetStyle.Numerals.MONO, BuiltinWidgetStyle.Numerals.fromKey("mono"));
    }

    @Test public void unsetOrUnknownIsSans() {
        assertEquals("sans", TermuxAppSharedPreferences.normaliseBuiltinWidgetNumerals(null));
        assertEquals("sans", TermuxAppSharedPreferences.normaliseBuiltinWidgetNumerals("glass"));
        assertEquals(BuiltinWidgetStyle.Numerals.SANS, BuiltinWidgetStyle.Numerals.fromKey(null));
    }
}
