package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The glass look: scheme + hairline is untouched, Obsidian tint recolours and washes. */
public class GlassLookTest {

    @Test
    public void theShippedPairIsTheSharedSchemeLookAndNeverChangesAColour() {
        GlassLook look = GlassLook.of("scheme", "hairline");
        assertSame(GlassLook.SCHEME, look);
        assertEquals(0x80123456, look.tintBase(0x80123456));
        assertEquals(0x80123456, look.flatTint(0x80123456));
        assertSame(GlassLook.SCHEME, GlassLook.of("nonsense", null));
    }

    @Test
    public void obsidianKeepsTheAlphaAndPicksInkOrWhiteByTheScheme() {
        GlassLook look = GlassLook.of("obsidian", "gradient");
        assertTrue(look.obsidianTint);
        assertEquals(0x99161822, look.tintBase(0x99202020));
        assertEquals(0x99FFFFFF, look.tintBase(0x99F2F2F2));
    }

    @Test
    public void aFlatTintIsTheWashOverTheInk() {
        GlassLook look = GlassLook.of("obsidian", "hairline");
        int flat = look.flatTint(0x99202020);
        int alpha = flat >>> 24;
        // 0.60 opacity with a 0.05 wash over it: 0.05 + 0.60 * 0.95 = 0.62.
        assertEquals(158, alpha, 1);
        // The wash lifts the ink slightly toward white; it stays near #161822.
        assertTrue(((flat >> 16) & 0xFF) >= 0x16 && ((flat >> 16) & 0xFF) < 0x30);
        assertFalse(GlassLook.of("obsidian", "hairline").equals(GlassLook.SCHEME));
        assertEquals(0, look.flatTint(0x00202020) >>> 24);
    }
}
