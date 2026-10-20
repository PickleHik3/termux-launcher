package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The Lens beads' geometry: one bead per icon, about 1.2 of it, centred on it in the row's own
 * order, never into the neighbour's gap or past the glass, and no beads where they would not
 * cover their icons.
 */
public class LensBeadGeometryTest {

    private static final float DENSITY = 2.75f;

    /** Five 58 dp icons on a 411 dp phone's row, the slab 6.5 dp of air above and below. */
    private static float[] fiveIcons(float pitchDp, float iconDp, float topDp) {
        float[] boxes = new float[5 * 4];
        float start = 14f + pitchDp / 2f;
        for (int i = 0; i < 5; i++) {
            float cx = (start + i * pitchDp) * DENSITY;
            float half = iconDp * DENSITY / 2f;
            boxes[i * 4] = cx - half;
            boxes[i * 4 + 1] = topDp * DENSITY;
            boxes[i * 4 + 2] = cx + half;
            boxes[i * 4 + 3] = (topDp + iconDp) * DENSITY;
        }
        return boxes;
    }

    private static LensBeadGeometry.Beads phone() {
        // 411 dp less 2 x 14 dp of inset is 383 dp for five slots: 76.6 dp a slot.
        return LensBeadGeometry.compute(fiveIcons(76.6f, 58f, 6.5f), 5, 0f, 0f,
            411f * DENSITY, (6.5f + 58f + 6.5f) * DENSITY, DENSITY);
    }

    @Test
    public void aBeadIsAboutOneAndAFifthOfItsIcon() {
        LensBeadGeometry.Beads beads = phone();
        assertNotNull(beads);
        assertEquals(5, beads.count());
        float diameterDp = 2f * beads.radiusPx / DENSITY;
        // 58 dp x 1.2 is 69.6 dp; the slab's 6.5 dp of air holds it to 69 dp less the clearance.
        assertTrue("diameter " + diameterDp, diameterDp >= 66f && diameterDp <= 69.6f + 1e-3f);
        assertTrue(diameterDp > 58f);
        assertEquals(29f * DENSITY, beads.iconHalfPx, 1e-3f);
    }

    @Test
    public void eachBeadIsCentredOnItsIconInTheRowsOwnOrder() {
        float[] boxes = fiveIcons(76.6f, 58f, 6.5f);
        // The user's order is the row's: the beads are listed as the row gave the icons, never sorted.
        float[] shuffled = new float[boxes.length];
        int[] order = {3, 0, 4, 1, 2};
        for (int i = 0; i < 5; i++) System.arraycopy(boxes, order[i] * 4, shuffled, i * 4, 4);
        LensBeadGeometry.Beads beads = LensBeadGeometry.compute(shuffled, 5, 0f, 0f,
            411f * DENSITY, 71f * DENSITY, DENSITY);
        assertNotNull(beads);
        for (int i = 0; i < 5; i++) {
            float cx = (shuffled[i * 4] + shuffled[i * 4 + 2]) / 2f;
            assertEquals(cx, beads.centerX(i), 1e-3f);
        }
        assertEquals((6.5f + 29f) * DENSITY, beads.centerY, 1e-3f);
    }

    @Test
    public void beadsKeepAGapToTheirNeighbours() {
        // A tight row: 68 dp a slot (58 dp tiles, 10 dp gaps). 1.2 x 58 would overlap.
        LensBeadGeometry.Beads beads = LensBeadGeometry.compute(fiveIcons(68f, 58f, 8f), 5, 0f, 0f,
            411f * DENSITY, 74f * DENSITY, DENSITY);
        assertNotNull(beads);
        float gap = 68f * DENSITY - 2f * beads.radiusPx;
        assertTrue("gap " + gap / DENSITY, gap >= LensBeadGeometry.MIN_GAP_DP * DENSITY - 1e-3f);
    }

    @Test
    public void beadsStayInsideTheGlass() {
        LensBeadGeometry.Beads beads = phone();
        assertNotNull(beads);
        float clearance = LensBeadGeometry.EDGE_CLEARANCE_DP * DENSITY;
        assertTrue(beads.centerY - beads.radiusPx >= clearance - 1e-3f);
        assertTrue(beads.centerY + beads.radiusPx <= 71f * DENSITY - clearance + 1e-3f);
        assertTrue(beads.centerX(0) - beads.radiusPx >= clearance);
        assertTrue(beads.centerX(4) + beads.radiusPx <= 411f * DENSITY - clearance);
    }

    @Test
    public void noBeadsWhereABeadWouldNotCoverItsIcon() {
        // The slab is no taller than the icon: a bead would be smaller than it.
        assertNull(LensBeadGeometry.compute(fiveIcons(76.6f, 58f, 0f), 5, 0f, 0f,
            411f * DENSITY, 58f * DENSITY, DENSITY));
        // Nothing to draw.
        assertNull(LensBeadGeometry.compute(new float[0], 0, 0f, 0f, 100f, 100f, DENSITY));
        // More icons than the program holds.
        int many = LensBeadGeometry.MAX_BEADS + 1;
        float[] boxes = new float[many * 4];
        for (int i = 0; i < many; i++) {
            boxes[i * 4] = i * 100f;
            boxes[i * 4 + 1] = 10f;
            boxes[i * 4 + 2] = i * 100f + 50f;
            boxes[i * 4 + 3] = 60f;
        }
        assertNull(LensBeadGeometry.compute(boxes, many, 0f, 0f, many * 100f, 70f, DENSITY));
        // An icon not laid out yet.
        assertNull(LensBeadGeometry.compute(new float[] {10f, 10f, 10f, 60f}, 1, 0f, 0f,
            100f, 70f, DENSITY));
    }

    @Test
    public void aLoneIconIsHeldOnlyByTheGlass() {
        LensBeadGeometry.Beads beads = LensBeadGeometry.compute(
            new float[] {100f, 20f, 200f, 120f}, 1, 0f, 0f, 400f, 200f, DENSITY);
        assertNotNull(beads);
        assertEquals(60f, beads.radiusPx, 1e-3f);
    }

    @Test
    public void beadsCompareByValueAndMoveWithTheirOrigin() {
        LensBeadGeometry.Beads a = phone();
        LensBeadGeometry.Beads b = phone();
        assertNotNull(a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        LensBeadGeometry.Beads moved = a.offset(12f, -3f);
        assertNotEquals(a, moved);
        assertEquals(a.centerX(2) + 12f, moved.centerX(2), 0f);
        assertEquals(a.centerY - 3f, moved.centerY, 0f);
        assertEquals(a.radiusPx, moved.radiusPx, 0f);
    }
}
