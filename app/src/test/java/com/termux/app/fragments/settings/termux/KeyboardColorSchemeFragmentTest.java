package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KeyboardColorSchemeFragmentTest {

    @Test
    public void parsesOpaqueAndArgbHexColors() {
        assertEquals(Integer.valueOf(0xFF12ABEF),
            KeyboardColorSchemeFragment.parseHexColor("#12abef"));
        assertEquals(Integer.valueOf(0x8012ABEF),
            KeyboardColorSchemeFragment.parseHexColor("8012ABEF"));
    }

    @Test
    public void rejectsInvalidHexColors() {
        assertNull(KeyboardColorSchemeFragment.parseHexColor("#12345"));
        assertNull(KeyboardColorSchemeFragment.parseHexColor("#hello!"));
    }
}
