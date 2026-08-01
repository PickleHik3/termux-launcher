package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WindowSessionNameTest {

    @Test
    public void normalize_trimsAndCapsNamesAtFiveCodePoints() {
        assertNull(WindowSessionName.normalize(null));
        assertNull(WindowSessionName.normalize("   "));
        assertEquals("work", WindowSessionName.normalize(" work "));
        assertEquals("abcde", WindowSessionName.normalize("abcdef"));
        assertEquals("12\uD83D\uDE8034", WindowSessionName.normalize("12\uD83D\uDE803456"));
    }
}
