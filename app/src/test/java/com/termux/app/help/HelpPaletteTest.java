package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

/** One colour per hint, and every hint's colour is its own. */
@RunWith(RobolectricTestRunner.class)
public class HelpPaletteTest {

    @Test public void twelveHintsGetTwelveColours() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 12; i++) seen.add(HelpPalette.boxColor(Color.rgb(0x0b, 0x1f, 0x44), i, 12));
        assertEquals(12, seen.size());
    }

    @Test public void huesStartFromTheAccentAndSpreadEvenly() {
        int accent = Color.rgb(0xf0, 0xb4, 0x8a);
        float first = HelpPalette.hue(accent, 0, 4);
        assertEquals(first, HelpPalette.hue(accent, 4, 4), 0.01f);
        assertEquals((first + 90f) % 360f, HelpPalette.hue(accent, 1, 4), 0.01f);
    }

    @Test public void aGreyAccentStillYieldsColour() {
        assertNotEquals(HelpPalette.boxColor(Color.GRAY, 0, 3), HelpPalette.boxColor(Color.GRAY, 1, 3));
    }

    @Test public void boxesDeepenOnALightWashAndStayBrightOnADarkOne() {
        int accent = Color.rgb(0x0b, 0x1f, 0x44);
        float[] light = new float[3], dark = new float[3];
        Color.colorToHSV(HelpPalette.boxColor(accent, 1, 4, true), light);
        Color.colorToHSV(HelpPalette.boxColor(accent, 1, 4, false), dark);
        // A degree of slack: the two go through different 8-bit RGB values and back.
        assertEquals(light[0], dark[0], 1f);
        assertTrue(light[2] < dark[2]);
        assertTrue(light[1] > dark[1]);
        // The dark wash is what the one-argument call has always meant.
        assertEquals(HelpPalette.boxColor(accent, 1, 4), HelpPalette.boxColor(accent, 1, 4, false));
    }

    @Test public void titlesDeepenOnALightCardAndBrightenOnADarkOne() {
        int accent = Color.rgb(0x0b, 0x1f, 0x44);
        float[] light = new float[3], dark = new float[3];
        Color.colorToHSV(HelpPalette.titleColor(accent, 0, 3, Color.WHITE), light);
        Color.colorToHSV(HelpPalette.titleColor(accent, 0, 3, Color.BLACK), dark);
        assertTrue(light[2] < dark[2]);
        assertTrue(HelpPalette.lightSurface(Color.WHITE));
        assertTrue(!HelpPalette.lightSurface(Color.BLACK));
    }
}
