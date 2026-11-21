package com.termux.terminal;

import junit.framework.TestCase;

/** {@link WcWidth} must answer exactly what the frozen {@link LegacyWcWidth} answers. */
public class WcWidthDifferentialTest extends TestCase {

    public void testEveryCodePointMatchesLegacy() {
        for (int cp = -0x1000; cp <= 0x110000 + 0x1000; cp++) {
            assertEquals("U+" + Integer.toHexString(cp), LegacyWcWidth.width(cp), WcWidth.width(cp));
        }
        assertEquals(LegacyWcWidth.width(Integer.MIN_VALUE), WcWidth.width(Integer.MIN_VALUE));
        assertEquals(LegacyWcWidth.width(Integer.MAX_VALUE), WcWidth.width(Integer.MAX_VALUE));
    }

    public void testAsciiShapes() {
        for (int cp = 0; cp < 0x100; cp++) {
            int expected = (cp >= 0x20 && cp <= 0x7E) ? 1 : (cp < 0xA0 ? 0 : 1);
            assertEquals("U+" + Integer.toHexString(cp), expected, WcWidth.width(cp));
        }
    }

    public void testCharArrayVariantMatchesLegacy() {
        char[] chars = new char[0x10000 + 2];
        for (int i = 0; i < 0x10000; i++) chars[i] = (char) i;
        for (int i = 0; i < 0xFFFF; i++) {
            if (Character.isHighSurrogate(chars[i])) continue;
            assertEquals(LegacyWcWidth.width(chars, i), WcWidth.width(chars, i));
        }
    }
}
