package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PaneBorderHexTest {

    @Test
    public void sixDigitsAreOpaque() {
        assertEquals(Integer.valueOf(0xFFDC143C), PaneBorderHex.parse("#DC143C"));
        assertEquals(Integer.valueOf(0xFFDC143C), PaneBorderHex.parse("#dc143c"));
        assertEquals(Integer.valueOf(0xFFDC143C), PaneBorderHex.parse(" dc143C "));
    }

    @Test
    public void eightDigitsCarryTheirAlpha() {
        assertEquals(Integer.valueOf(0x80DC143C), PaneBorderHex.parse("#80DC143C"));
        assertEquals(Integer.valueOf(0x80DC143C), PaneBorderHex.parse("#80dc143c"));
    }

    @Test
    public void blackIsOpaqueAndZeroIsNeverAnswered() {
        assertEquals(Integer.valueOf(0xFF000000), PaneBorderHex.parse("#000000"));
        assertNull(PaneBorderHex.parse("#00000000"));
    }

    @Test
    public void garbageIsRefused() {
        assertNull(PaneBorderHex.parse(null));
        assertNull(PaneBorderHex.parse(""));
        assertNull(PaneBorderHex.parse("#"));
        assertNull(PaneBorderHex.parse("#FFF"));
        assertNull(PaneBorderHex.parse("#GG0000"));
        assertNull(PaneBorderHex.parse("#12345"));
        assertNull(PaneBorderHex.parse("#1234567"));
        assertNull(PaneBorderHex.parse("#123456789"));
        assertNull(PaneBorderHex.parse("red"));
    }

    @Test
    public void formatRoundTrips() {
        assertEquals("#DC143C", PaneBorderHex.format(0xFFDC143C));
        assertEquals("#80DC143C", PaneBorderHex.format(0x80DC143C));
        assertEquals("#000000", PaneBorderHex.format(0xFF000000));
        for (int argb : new int[] {0xFFDC143C, 0x80DC143C, 0x01020304, 0xFF000000})
            assertEquals(Integer.valueOf(argb), PaneBorderHex.parse(PaneBorderHex.format(argb)));
    }
}
