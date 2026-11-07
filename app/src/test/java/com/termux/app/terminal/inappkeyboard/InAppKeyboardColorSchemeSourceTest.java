package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InAppKeyboardColorSchemeSourceTest {

    private static final int[] DEFAULTS = {0xFF101010, 0xFF202020, 0xFF303030};

    @Test
    public void sameJsonAndDefaultsKeepTheParse() {
        assertTrue(InAppKeyboardColorScheme.sameSource("{\"a\":1}", DEFAULTS.clone(),
            "{\"a\":1}", DEFAULTS.clone()));
        assertTrue(InAppKeyboardColorScheme.sameSource(null, DEFAULTS, null, DEFAULTS));
    }

    @Test
    public void anotherJsonParsesAgain() {
        assertFalse(InAppKeyboardColorScheme.sameSource("{\"a\":1}", DEFAULTS, "{\"a\":2}", DEFAULTS));
        assertFalse(InAppKeyboardColorScheme.sameSource(null, DEFAULTS, "", DEFAULTS));
    }

    @Test
    public void movedMaterialDefaultsParseAgain() {
        // A theme or Material You change moves the swatches every dynamic slot is seeded from.
        int[] moved = DEFAULTS.clone();
        moved[1] = 0xFF212121;
        assertFalse(InAppKeyboardColorScheme.sameSource("{}", DEFAULTS, "{}", moved));
        assertFalse(InAppKeyboardColorScheme.sameSource("{}", null, "{}", DEFAULTS));
    }
}
