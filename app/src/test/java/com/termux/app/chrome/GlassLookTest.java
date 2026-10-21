package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The glass look: scheme + hairline is untouched, Obsidian recolours and washes, and the Tint
 * amount mixes the accent into either ink on one axis: 0 is the ink, 35 the Material blend, 100
 * the accent's container.
 */
public class GlassLookTest {

    private static final int CONTAINER = 0xFF4060C0;
    private static final int ACCENT = 0xFF6080FF;

    @Test
    public void theShippedPairIsTheSharedSchemeLookAndNeverChangesAColour() {
        GlassLook look = GlassLook.of("scheme", "hairline");
        assertSame(GlassLook.SCHEME, look);
        assertEquals(0x80123456, look.tintBase(0x80123456));
        assertEquals(0x80123456, look.flatTint(0x80123456));
        assertSame(GlassLook.SCHEME, GlassLook.of("nonsense", null));
        assertSame("the legacy family reads as the scheme ink", GlassLook.SCHEME,
            GlassLook.of("material", "hairline"));
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
    public void theTintLooksAmountIsTheMaterialBlendBetweenSurfaceAndContainerWithTheWash() {
        int surface = 0x80202020;
        GlassLook look = GlassLook.SCHEME.withAmountPercent(GlassLook.TINT_LOOK_PERCENT)
            .withAccentColors(CONTAINER, ACCENT);
        int out = look.tintBase(surface);
        assertEquals("alpha is kept", 0x80, out >>> 24);
        // Blue: 0x20 -> 0x20 + 0.35 * (0xC0 - 0x20) = 88, then 14% toward 0xFF = 111.
        assertEquals(111, out & 0xFF, 1);
        assertEquals(out, look.flatTint(surface));
        assertEquals(0, look.flatTint(0x00123456) >>> 24);
    }

    /** The full amount reaches the container, under the Tint Look's wash and no more of it. */
    @Test
    public void theFullAmountIsTheContainerUnderTheTintLooksWash() {
        int surface = 0x80202020;
        int out = GlassLook.SCHEME.withAmountPercent(100).withAccentColors(CONTAINER, ACCENT)
            .tintBase(surface);
        // Blue: all the way to 0xC0 = 192, then 14% toward 0xFF = 200.8.
        assertEquals(201, out & 0xFF);
        // Red: 0x40 = 64, then 14% toward 0x60 = 68.5.
        assertEquals(68, (out >> 16) & 0xFF, 1);
        assertEquals(0x80, out >>> 24);
    }

    /**
     * Every shipped Look draws the colour it drew before the amount: Clear and Solid at 0 are the
     * scheme ink, Mist at 0 the Obsidian ink with its wash, and Tint at its amount (35 on the
     * stretched scale, 100 before it) the Material blend, channel for channel the old formula's.
     */
    @Test
    public void everyShippedLookRendersAsBefore() {
        int[] bases = {0x80202A44, 0xFF141218, 0x99F2F2F2, 0x2EE6E1E5};
        // What the build before the amount drew, read off its formulas (Obsidian's ink by the
        // scheme's brightness; the Material blend at strength 1, float for float), as literals.
        int[] oldMist = {0x80161822, 0xFF161822, 0x99FFFFFF, 0x2EFFFFFF};
        int[] oldTint = {0x80334684, 0xFF2C396B, 0x99A8B6E5, 0x2EA1ADDE};
        for (int i = 0; i < bases.length; i++) {
            int base = bases[i];
            assertEquals("Clear and Solid", base, GlassLook.SCHEME.withAmountPercent(0)
                .withAccentColors(CONTAINER, ACCENT).tintBase(base));
            GlassLook mist = GlassLook.of("obsidian", "gradient").withAccentColors(CONTAINER, ACCENT);
            assertEquals("Mist", oldMist[i], mist.tintBase(base));
            GlassLook tint = GlassLook.SCHEME.withAmountPercent(GlassLook.TINT_LOOK_PERCENT)
                .withAccentColors(CONTAINER, ACCENT);
            assertEquals("Tint", oldTint[i], tint.tintBase(base));
            assertEquals("Tint's flat tint", oldTint[i], tint.flatTint(base));
        }
    }

    /** The formula the Material family drew at strength {@code s} before the amount existed. */
    private static int oldMaterial(int base, float s) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = (base >> shift) & 0xFF;
            float p = (CONTAINER >> shift) & 0xFF;
            float t = (ACCENT >> shift) & 0xFF;
            float blended = c + (p - c) * (0.35f * s);
            float washed = blended + (t - blended) * (0.14f * s);
            rgb |= Math.round(Math.max(0f, Math.min(255f, washed))) << shift;
        }
        return (base & 0xFF000000) | rgb;
    }

    /**
     * A Material look at strength s is the scheme ink at the stretched amount s x 35 / 100: the
     * migrations' identity. Exact wherever s x 0.35 is a whole percent; within the rounding of
     * one percent of the blend elsewhere.
     */
    @Test
    public void aMaterialStrengthIsTheScaledAmountOnTheSchemeInk() {
        int base = 0xC0202A44;
        for (int percent = 0; percent <= 100; percent += 20) {
            assertEquals(percent + "%", oldMaterial(base, percent / 100f), GlassLook.SCHEME
                .withAmountPercent(GlassLook.amountForLegacyPercent(percent))
                .withAccentColors(CONTAINER, ACCENT).tintBase(base));
        }
        for (int percent = 0; percent <= 100; percent += 5) {
            int old = oldMaterial(base, percent / 100f);
            int now = GlassLook.SCHEME.withAmountPercent(GlassLook.amountForLegacyPercent(percent))
                .withAccentColors(CONTAINER, ACCENT).tintBase(base);
            for (int shift = 16; shift >= 0; shift -= 8)
                assertEquals(percent + "% at " + shift, (old >> shift) & 0xFF, (now >> shift) & 0xFF, 2);
        }
        assertEquals(35, GlassLook.amountForLegacyPercent(100));
        assertEquals(14, GlassLook.amountForLegacyPercent(40));
        assertEquals(0, GlassLook.amountForLegacyPercent(0));
        assertEquals(18, GlassLook.amountForLegacyPercent(50));
    }

    @Test
    public void noAmountOrNoResolvedAccentLeavesTheInk() {
        int surface = 0x80202A44;
        GlassLook none = GlassLook.SCHEME.withAccentColors(CONTAINER, ACCENT);
        assertEquals(surface, none.tintBase(surface));
        assertEquals(surface, none.flatTint(surface));
        GlassLook unresolved = GlassLook.SCHEME.withAmountPercent(70);
        assertTrue(unresolved.needsAccentColors());
        assertFalse(GlassLook.SCHEME.needsAccentColors());
        assertEquals("waiting for its colours, the glass draws the ink", surface,
            unresolved.tintBase(surface));
    }

    /** The slider is alive from every Look: the accent grows with the amount on both inks. */
    @Test
    public void theAccentGrowsWithTheAmountOnEitherInk() {
        int surface = 0xFF202A44;
        for (GlassLook look : new GlassLook[] {GlassLook.SCHEME, GlassLook.of("obsidian", "gradient")}) {
            int previous = look.tintBase(surface) & 0xFF;
            for (int percent = 10; percent <= 100; percent += 10) {
                int blue = look.withAmountPercent(percent).withAccentColors(CONTAINER, ACCENT)
                    .tintBase(surface) & 0xFF;
                assertTrue(percent + "% is no less tinted than the step before", blue >= previous);
                previous = blue;
            }
            assertTrue("the full amount moves the ink visibly", previous - (look.tintBase(surface)
                & 0xFF) >= 30);
        }
        // Mist in a light theme was white with nowhere to go; the accent now reaches it.
        GlassLook lightMist = GlassLook.of("obsidian", "gradient").withAmountPercent(100)
            .withAccentColors(CONTAINER, ACCENT);
        assertNotEquals(0xFFFFFFFF, lightMist.tintBase(0xFFF2F2F2));
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

    @Test
    public void amountAndHueArePartOfALooksIdentity() {
        GlassLook look = GlassLook.of("obsidian", "hairline");
        assertSame(look, look.withAmountPercent(0));
        assertFalse(look.equals(look.withAmountPercent(40)));
        assertEquals(look.withAmountPercent(40), look.withAmountPercent(40));
        assertEquals(0.4f, look.withAmountPercent(40).amount(), 1e-6f);
        assertEquals("clamped", 1f, look.withAmountPercent(400).amount(), 0f);
        assertEquals(GlassLook.Hue.PRIMARY, GlassLook.of("scheme", "hairline", null).hue);
        assertEquals(GlassLook.Hue.SECONDARY, GlassLook.of("scheme", "hairline", "secondary").hue);
        assertEquals(GlassLook.Hue.TERTIARY, GlassLook.of("scheme", "hairline", "tertiary").hue);
        assertEquals(GlassLook.Hue.PRIMARY, GlassLook.Hue.of("magenta"));
        GlassLook resolved = look.withAccentColors(CONTAINER, ACCENT);
        assertFalse("a new hue has to resolve its own colours",
            resolved.withHue(GlassLook.Hue.TERTIARY).accentResolved);
        assertNotEquals(GlassLook.SCHEME, GlassLook.of("scheme", "hairline", "tertiary"));
    }
}
