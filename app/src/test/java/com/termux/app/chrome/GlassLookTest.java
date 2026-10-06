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
        assertTrue(look.gradientRim);
        assertEquals(0x99161822, look.tintBase(0x99202020));
        assertEquals(0x99FFFFFF, look.tintBase(0x99F2F2F2));
    }

    @Test
    public void materialTintLiesBetweenSurfaceAndPrimaryContainerWithTheWash() {
        int surface = 0x80202020;
        int container = 0xFF4060C0;
        int surfaceTint = 0xFF6080FF;
        GlassLook look = GlassLook.material(false, container, surfaceTint);
        int out = look.tintBase(surface);
        assertEquals("alpha is kept", 0x80, out >>> 24);
        for (int shift = 16; shift >= 0; shift -= 8) {
            int s = (surface >> shift) & 0xFF;
            int c = (container >> shift) & 0xFF;
            int o = (out >> shift) & 0xFF;
            assertTrue("channel moved toward the container, not past the surface tint",
                o >= Math.min(s, c) && o <= Math.max(Math.max(s, c), (surfaceTint >> shift) & 0xFF));
        }
        // Blue: 0x20 -> 0x20 + 0.35 * (0xC0 - 0x20) = 88, then 14% toward 0xFF = 111.
        assertEquals(111, out & 0xFF, 1);
        assertEquals(out, look.flatTint(surface));
        assertEquals(0, look.flatTint(0x00123456) >>> 24);
        // Unresolved material degrades to the scheme behaviour.
        assertEquals(surface, GlassLook.ofRequested("material", "hairline").tintBase(surface));
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

    // ------------------------------------------------------------------------ the tint strength

    @Test
    public void fullStrengthIsTheLookAsItWas() {
        int surface = 0x80202A44;
        GlassLook material = GlassLook.material(true, 0xFF4060C0, 0xFF6080FF);
        assertSame(material, material.withStrengthPercent(100));
        assertEquals(material.tintBase(surface),
            material.withStrengthPercent(100).tintBase(surface));
        assertEquals(material.flatTint(surface), material.withStrength(1f).flatTint(surface));
        assertSame(GlassLook.SCHEME, GlassLook.SCHEME.withStrengthPercent(100));
        GlassLook obsidian = GlassLook.of("obsidian", "hairline");
        assertEquals(obsidian.tintBase(surface), obsidian.withStrength(1f).tintBase(surface));
        assertEquals(obsidian.flatTint(surface), obsidian.withStrength(1f).flatTint(surface));
    }

    @Test
    public void noStrengthLeavesTheMaterialLookAsTheUntintedBase() {
        int surface = 0x80202A44;
        GlassLook none = GlassLook.material(false, 0xFF4060C0, 0xFF6080FF).withStrengthPercent(0);
        assertEquals(surface, none.tintBase(surface));
        assertEquals(surface, none.flatTint(surface));
        assertEquals(0, none.flatTint(0x00123456) >>> 24);
    }

    @Test
    public void theMaterialTintGrowsWithTheStrength() {
        int surface = 0xFF202A44;
        GlassLook full = GlassLook.material(false, 0xFF4060C0, 0xFF6080FF);
        int previous = surface & 0xFF;
        for (int percent = 10; percent <= 100; percent += 10) {
            int blue = full.withStrengthPercent(percent).tintBase(surface) & 0xFF;
            assertTrue(percent + "% is no less tinted than the step before", blue >= previous);
            previous = blue;
        }
        assertTrue(previous > (surface & 0xFF));
    }

    @Test
    public void aLessStrongSchemeOrObsidianTintMovesTowardItsGrey() {
        int surface = 0x80204080;
        int grey = Math.round(0.299f * 0x20 + 0.587f * 0x40 + 0.114f * 0x80);
        int out = GlassLook.SCHEME.withStrengthPercent(0).tintBase(surface);
        assertEquals("alpha is kept", 0x80, out >>> 24);
        assertEquals(grey, (out >> 16) & 0xFF, 1);
        assertEquals(grey, (out >> 8) & 0xFF, 1);
        assertEquals(grey, out & 0xFF, 1);
        int half = GlassLook.SCHEME.withStrengthPercent(50).tintBase(surface);
        assertTrue((half & 0xFF) < 0x80 && (half & 0xFF) > grey);

        GlassLook obsidian = GlassLook.of("obsidian", "hairline").withStrengthPercent(0);
        int ink = obsidian.tintBase(0x99202020);
        assertEquals((ink >> 16) & 0xFF, ink & 0xFF);
        assertEquals((ink >> 8) & 0xFF, ink & 0xFF);
    }

    @Test
    public void strengthIsPartOfALooksIdentity() {
        GlassLook full = GlassLook.of("obsidian", "hairline");
        assertFalse(full.equals(full.withStrengthPercent(40)));
        assertEquals(full.withStrengthPercent(40), full.withStrengthPercent(40));
        assertEquals(0.4f, full.withStrengthPercent(40).strength(), 1e-6f);
        assertEquals(1f, full.withStrengthPercent(400).strength(), 0f);
    }
}
