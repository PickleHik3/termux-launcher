package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


import org.junit.Test;

/**
 * A band that stands alone — the extra-keys row with the dock's rows hidden, or the card under the
 * keyboard — is one short strip of glass, and the Fancier Glass program must draw it as a whole
 * card: no seam along its centre line and no chevron at its ends, whatever its Corners and however
 * deep the slab's lens zone or the dock's soft rim reaches.
 *
 * <p>The numbers are the developer's phone's: bend 21 dp, edge width 14 dp, Corners 3 dp, the dock
 * design's 24 dp soft rim with the whole shelf under Floating.</p>
 */
public class GlassLoneBandTest {

    private static final float D = 2.625f;
    private static final GlassRefraction.Look LOOK = new GlassRefraction.Look(21, 14, 10);
    private static final float DEPTH = LOOK.thicknessPx(D);
    private static final GlassSlab SLAB = GlassSlab.DEFAULT;
    private static final float CORNERS = 3f * D;
    private static final float SOFT = GlassRefraction.SOFT_RIM_DP * D;
    /** The extra-keys row's half width and half height: a 340 x 40 dp strip. */
    private static final float BX = 170f * D;
    private static final float BY = 20f * D;

    @Test
    public void theBendIsGoneOnALoneBandsCentreLine() {
        // The dock's soft rim (24 dp, 32.4 dp on the shelf's lower edge) is deeper than half the
        // 40 dp row: the zone is held at the half side, so on the centre line both halves have
        // bent to nothing, or the upper half's picture and the lower half's meet torn apart there.
        for (float lower : new float[] {0f, 1f}) {
            float w = GlassRefraction.lensZonePx(SLAB, BY, D, 1f, SOFT, 0f, lower);
            float t = GlassRefraction.lensThicknessPx(SLAB, DEPTH, BY, D, 1f, lower);
            assertEquals("soft rim, lower " + lower, BY, w, 1e-3f);
            assertEquals(0f, SLAB.shiftPx(BY, w, t), 0f);
        }
        // The card under the keyboard, a 24 dp row.
        float shortHalf = 12f * D;
        float w = GlassRefraction.lensZonePx(SLAB, shortHalf, D, 1f, 0f, 0f, 0f);
        float t = GlassRefraction.lensThicknessPx(SLAB, DEPTH, shortHalf, D, 1f, 0f);
        assertEquals(0f, SLAB.shiftPx(shortHalf, w, t), 0f);
        // A lean deepens the zone; it is still held at the half side.
        assertEquals(0f, SLAB.shiftPx(shortHalf, Math.min(w * (1f + GlassRefraction.LAG_BAND),
            shortHalf), t * (1f + GlassRefraction.LAG_BEND)), 0f);
    }

    @Test
    public void aZoneIsHeldAtHalfTheShortSideAndNoDeeper() {
        assertEquals(BY, GlassRefraction.lensZonePx(SLAB, BY, D, 1f, SOFT, 0f, 1f), 0f);
        assertEquals("a tall slab keeps its zone", SLAB.lensPx(D),
            GlassRefraction.lensZonePx(SLAB, 200f * D, D, 1f, 0f, 0f, 0f), 1e-4f);
        // And its deepest bend never crosses the centre line, however deep the Depth.
        float t = GlassRefraction.lensThicknessPx(SLAB, 30f * D, BY, D, 1f, 1f);
        assertTrue(SLAB.shiftPx(0f, BY, t) <= BY + 1e-3f);
    }

    @Test
    public void theBendsDirectionTurnsRoundACornerAtLeastAsDeepAsTheZone() {
        // The dock's lone band: the soft rim with the whole shelf reaches the half height, so
        // the direction eases over the whole half height, which leaves no rect for it to snap on.
        float soft = GlassRefraction.bendReachPx(SOFT, 1f, false);
        assertEquals(SOFT * (1f + GlassRefraction.SHELF_THICKEN), soft, 1e-3f);
        assertEquals(BY, GlassRefraction.bendNormalRadiusPx(CORNERS, soft, BY), 0f);
        // A tall surface's 3 dp corner under the 16 dp zone: the zone's depth.
        float ring = GlassRefraction.bendReachPx(SLAB.lensPx(D), 0f, false);
        assertEquals(SLAB.lensPx(D), GlassRefraction.bendNormalRadiusPx(CORNERS, ring, 200f * D), 1e-4f);
        // A live slab's zone leans deeper, and so does the direction's arc.
        assertEquals(SLAB.lensPx(D) * (1f + GlassRefraction.LAG_BAND),
            GlassRefraction.bendReachPx(SLAB.lensPx(D), 0f, true), 1e-3f);
        // A corner rounder than the zone is the drawn arc exactly, held at the half side.
        assertEquals(40f * D, GlassRefraction.bendNormalRadiusPx(40f * D, ring, 200f * D), 1e-3f);
        assertEquals(BY, GlassRefraction.bendNormalRadiusPx(BY, ring, BY), 0f);
    }

    @Test
    public void aLoneDockBandHasNoSeamAndNoChevron() {
        assertNoTear(BX, BY, CORNERS, SOFT, 1f);
    }

    @Test
    public void aLoneDockBandDockedHasNoSeamAndNoChevron() {
        // Docked writes no shelf.
        assertNoTear(BX, BY, CORNERS, SOFT, 0f);
    }

    @Test
    public void aLoneNarrowRingBandHasNoSeamAndNoChevron() {
        // The card under the keyboard wears the narrow ring; a 24 dp row is shorter than the ring.
        assertNoTear(BX, 12f * D, CORNERS, 0f, 0f);
        assertNoTear(BX, BY, CORNERS, 0f, 0f);
    }

    @Test
    public void aCapsuleBandStaysWhole() {
        assertNoTear(BX, BY, BY, SOFT, 1f);
    }

    @Test
    public void theProgramHoldsEveryZoneAndEasesTheBendsDirection() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("    float hs = min(b.x, b.y);\n"));
        assertTrue(agsl.contains("        float wr = min(w * thick, hs);\n"));
        assertTrue(agsl.contains("        float slabT = min(uDepth * sizeK * sizeShare * thick, hs * kSlabHold);\n"));
        assertTrue(agsl.contains("    float reach = w * (1.0 + kShelfThick * uShelf) * (live ? 1.0 + kLagBand : 1.0);\n"));
        assertTrue(agsl.contains("        nb = rectNormal(p, b, min(max(cr, reach), hs));\n"));
        assertTrue(agsl.contains("            wl = min(wr * (1.0 + kLagBand * lean), hs);\n"));
        // The shelf turns with the bend's direction, so it does not mitre at a small corner.
        assertTrue(agsl.contains("float lower = uShelf > 0.0 ? max(nb.y, 0.0) * uShelf : 0.0;"));
        assertTrue(agsl.indexOf("nb = rectNormal(") < agsl.indexOf("float lower = "));
        assertTrue(agsl.contains("float2 sampleCoord = fragCoord - nb * shiftG;"));
        // live is known before the zone's reach is.
        assertTrue(agsl.indexOf("bool live = ") < agsl.indexOf("float reach = "));
        // The light keeps its normal round the drawn corner.
        assertTrue(agsl.contains("float2 n = rectNormal(p, b, min(cr, min(b.x, b.y)));"));
        assertTrue(agsl.contains("float rc = dot(n, kKeyLight);"));
        assertTrue(agsl.contains("float nh = max(dot(float3(n * slope, 1.0) * cv, kHalf), 0.0);"));
    }

    /**
     * Walks the two places a short band tore: across its centre line, and across the 45 degree
     * line in from each corner, comparing each pair of pixels a pixel apart that lie equally far
     * in. Their samples may only differ by the bend's direction's gentle turn, never by a bend's
     * worth: the snapped normal tore these pairs by 8 to 40 px, and the shelf's lower half
     * against its upper by about 14 px on the centre line.
     */
    private static void assertNoTear(float bx, float by, float cr, float softRimPx, float shelf) {
        float[] a = new float[2], b = new float[2];
        float worst = 0f;
        String where = "";
        // The centre line, end to end.
        for (float x = -bx + 0.5f; x < bx; x += 1f) {
            offset(a, x, 0.5f, bx, by, cr, softRimPx, shelf);
            offset(b, x, -0.5f, bx, by, cr, softRimPx, shelf);
            float tear = distance(a, b);
            if (tear > worst) {
                worst = tear;
                where = "centre line at x " + x;
            }
        }
        // The line in from each of the four corners, as far as the half height.
        for (float d = 0.5f; d < by; d += 1f) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    offset(a, sx * (bx - (d - 0.5f)), sy * (by - (d + 0.5f)), bx, by, cr, softRimPx, shelf);
                    offset(b, sx * (bx - (d + 0.5f)), sy * (by - (d - 0.5f)), bx, by, cr, softRimPx, shelf);
                    float tear = distance(a, b);
                    if (tear > worst) {
                        worst = tear;
                        where = "corner " + sx + "," + sy + " at " + d;
                    }
                }
            }
        }
        assertTrue("torn by " + worst + " px at " + where, worst < 3f);
    }

    private static void offset(float[] out, float x, float y, float bx, float by, float cr,
                               float softRimPx, float shelf) {
        GlassRefraction.restBendOffset(out, x, y, bx, by, cr, DEPTH, softRimPx, shelf, D);
    }

    private static float distance(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
