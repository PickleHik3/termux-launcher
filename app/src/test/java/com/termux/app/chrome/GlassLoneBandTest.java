package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A band that stands alone — the extra-keys row with the dock's rows hidden, or the card under the
 * keyboard — is one short strip of glass, and the Fancier Glass program must draw it as a whole
 * card: no seam along its centre line and no chevron at its ends, whatever its Corners and however
 * deep the look's Edge width or the dock's soft rim reaches.
 *
 * <p>The numbers are the developer's phone's: bend 21 dp, edge width 14 dp, Corners 3 dp, the dock
 * design's 24 dp soft rim with the whole shelf under Floating.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassLoneBandTest {

    private static final float D = 2.625f;
    private static final GlassRefraction.Look LOOK = new GlassRefraction.Look(21, 14, 10);
    private static final float BAND = LOOK.bandPx(D);
    private static final float STRENGTH = LOOK.strengthPx(D);
    private static final float RIM_W = GlassRefraction.Look.rimWidthPx(D);
    private static final float CORNERS = 3f * D;
    private static final float SOFT = GlassRefraction.SOFT_RIM_DP * D;
    /** The extra-keys row's half width and half height: a 340 x 40 dp strip. */
    private static final float BX = 170f * D;
    private static final float BY = 20f * D;

    @Test
    public void theBendIsGoneOnALoneBandsCentreLine() {
        // The dock's soft rim (24 dp, 32.4 dp on the shelf's lower edge) is deeper than half the
        // 40 dp row: on the centre line both halves must have bent to nothing, or the upper
        // half's picture and the lower half's meet torn apart there.
        for (float lower : new float[] {0f, 1f}) {
            assertEquals("soft rim, lower " + lower, 0f,
                GlassRefraction.softPullPx(BY, BAND, SOFT, STRENGTH, BY, RIM_W, lower), 1e-3f);
        }
        // The narrow ring of the card under the keyboard, on a 24 dp row under a 14 dp Edge width.
        float shortHalf = 12f * D;
        assertEquals(0f, GlassRefraction.pullPx(shortHalf, BAND, STRENGTH, shortHalf, RIM_W), 1e-3f);
        // A lean deepens the band; it is still held at the half side.
        assertEquals(0f, GlassRefraction.livePullPx(shortHalf, BAND, STRENGTH, shortHalf, RIM_W, 1f),
            1e-3f);
    }

    @Test
    public void aBandIsHeldAtHalfTheShortSideAndNoDeeper() {
        assertEquals(BY, GlassRefraction.heldBandPx(SOFT * 1.35f, BY), 0f);
        assertEquals("a tall slab keeps its band", BAND, GlassRefraction.heldBandPx(BAND, 200f * D), 0f);
    }

    @Test
    public void theBendsNormalTurnsRoundACornerAtLeastAsDeepAsTheBand() {
        // The dock's lone band: the soft rim with the whole shelf reaches past the half height, so
        // the normal eases over the whole half height, which leaves no rect for it to snap on.
        float soft = GlassRefraction.bendReachPx(BAND, SOFT, 1f, false);
        assertEquals(SOFT * (1f + GlassRefraction.SHELF_THICKEN), soft, 1e-3f);
        assertEquals(BY, GlassRefraction.bendNormalRadiusPx(CORNERS, soft, BY), 0f);
        // A tall surface's corner: the band's depth, not half again the 3 dp drawn corner.
        float ring = GlassRefraction.bendReachPx(BAND, 0f, 0f, false);
        assertEquals(BAND, ring, 0f);
        assertEquals(BAND, GlassRefraction.bendNormalRadiusPx(CORNERS, ring, 200f * D), 0f);
        // A live slab's band leans deeper, and so does the normal's arc.
        assertEquals(BAND * (1f + GlassRefraction.LAG_BAND),
            GlassRefraction.bendReachPx(BAND, 0f, 0f, true), 1e-3f);
        // A corner already rounder than the band keeps its own easing, held at the half side.
        assertEquals(1.5f * 40f * D, GlassRefraction.bendNormalRadiusPx(40f * D, ring, 200f * D), 1e-3f);
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
    public void theProgramHoldsEveryBandAndEasesTheBendsNormal() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("    float hs = min(b.x, b.y);\n"));
        assertTrue(agsl.contains("    float reach = max(uBand, uSoftRim) * (1.0 + kShelfThick * uShelf)"
            + " * (live ? 1.0 + kLagBand : 1.0);\n"));
        assertTrue(agsl.contains("    float2 nb = rectNormal(p, b, min(max(cr * 1.5, reach), hs));\n"));
        assertTrue(agsl.contains("    float t = clamp(inside / min(uBand, hs), 0.0, 1.0);\n"));
        assertTrue(agsl.contains("        softB = min(softB, hs);\n"));
        assertTrue(agsl.contains("float tl = clamp(inside / min(uBand * (1.0 + kLagBand * lean), hs), 0.0, 1.0);"));
        assertTrue(agsl.contains("float sl = min(softB * (1.0 + kLagBand * lean), hs);"));
        assertTrue(agsl.contains("float lower = uShelf > 0.0 ? max(nb.y, 0.0) * uShelf : 0.0;"));
        assertTrue(agsl.contains("float2 sampleCoord = fragCoord - nb * pull;"));
        // live is known before the bend's normal is.
        assertTrue(agsl.indexOf("bool live = ") < agsl.indexOf("float reach = "));
        // The gathered light keeps its normal round the drawn corner.
        assertTrue(agsl.contains("float2 n = rectNormal(p, b, min(cr * 1.5, min(b.x, b.y)));"));
        assertTrue(agsl.contains("float k = glow * (gatherShare(n) + 1.5 * touchG)"));
    }

    /**
     * Walks the two places a short band tore: across its centre line, and across the 45 degree
     * line in from each corner, comparing each pair of pixels a pixel apart that lie equally far
     * in. Their samples may only differ by the normal's gentle turn (about a px under the
     * narrow ring's steep rim), never by a pull's worth: the snapped normal tore these pairs by
     * 8 to 40 px, and the shelf's lower half against its upper by about 14 px on the centre line.
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
        GlassRefraction.restBendOffset(out, x, y, bx, by, cr, BAND, STRENGTH, softRimPx, shelf, RIM_W);
    }

    private static float distance(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
