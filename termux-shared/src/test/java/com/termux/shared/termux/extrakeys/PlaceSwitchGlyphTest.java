package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.android.material.color.utilities.Hct;

import org.junit.Test;

public class PlaceSwitchGlyphTest {

    private static final int PRIMARY = 0xFF415F91;
    private static final int SECONDARY = Hct.from(Hct.fromInt(PRIMARY).getHue(), 16, 45).toInt();
    private static final int TERTIARY = Hct.from(15, 30, 45).toInt();
    private static final int ERROR = 0xFFBA1A1A;

    private static int[] emulatorRow() {
        return new int[] {PRIMARY, SECONDARY, TERTIARY, ERROR};
    }

    private static boolean inBand(double hue) {
        return hue >= PlaceSwitchGlyph.MUDDY_HUE_START && hue < PlaceSwitchGlyph.MUDDY_HUE_END;
    }

    private static String hex(int[] row) {
        StringBuilder sb = new StringBuilder();
        for (int c : row) sb.append(String.format("#%06X ", c & 0xFFFFFF));
        return sb.toString();
    }

    @Test
    public void emulatorRowOnLightGlassAvoidsMuddyBand() {
        int[] out = PlaceSwitchGlyph.vividRow(emulatorRow(), false);
        System.out.println("HUEROW light " + hex(out));
        for (int c : out) {
            assertTrue("muddy hue " + Hct.fromInt(c).getHue(), !inBand(Hct.fromInt(c).getHue()));
            assertTrue(Hct.fromInt(c).getChroma() >= 35d);
        }
        for (int i = 0; i < out.length; i++)
            for (int j = i + 1; j < out.length; j++)
                assertTrue(PlaceSwitchGlyph.hueDistance(
                    Hct.fromInt(out[i]).getHue(), Hct.fromInt(out[j]).getHue()) >= 45d);
    }

    @Test
    public void emulatorRowOnDarkGlassIsStillSpread() {
        int[] out = PlaceSwitchGlyph.vividRow(emulatorRow(), true);
        System.out.println("HUEROW dark " + hex(out));
        for (int c : out) assertTrue(Hct.fromInt(c).getChroma() >= 35d);
    }

    @Test
    public void rowWithoutClashIsUntouched() {
        int[] row = {PRIMARY, Hct.from(140, 50, 45).toInt(), Hct.from(20, 50, 45).toInt()};
        int[] expected = new int[row.length];
        for (int i = 0; i < row.length; i++)
            expected[i] = PlaceSwitchGlyph.vividFor(row[i], false);
        assertArrayEquals(expected, PlaceSwitchGlyph.vividRow(row, false));
    }

    @Test
    public void ownMuddyHueWithoutClashIsKept() {
        int yellow = Hct.from(90, 50, 45).toInt();
        int[] out = PlaceSwitchGlyph.vividRow(new int[] {PRIMARY, yellow}, false);
        assertEquals(PlaceSwitchGlyph.vividFor(yellow, false), out[1]);
    }

    @Test
    public void firstSwitchKeepsItsHueAndNeutralsPass() {
        int[] out = PlaceSwitchGlyph.vividRow(new int[] {0xFF000000, PRIMARY, SECONDARY}, false);
        assertEquals(0xFF000000, out[0]);
        assertEquals(PlaceSwitchGlyph.vividFor(PRIMARY, false), out[1]);
    }
}
