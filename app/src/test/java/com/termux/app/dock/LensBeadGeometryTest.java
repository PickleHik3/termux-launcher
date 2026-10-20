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

    @Test
    public void midSwipeEachBeadRidesUnderItsIcon() {
        LensBeadGeometry.Beads rest = phone();
        assertNotNull(rest);
        float width = 411f * DENSITY;
        float pitch = 76.6f * DENSITY;
        // A third of the way to the next page: the row's icons are a third of a row to the left,
        // and the next page's first two stand where the swipe draws them at the right.
        float offset = -width / 3f;
        float[] neighbours = {width + offset + 14f * DENSITY + pitch / 2f,
            width + offset + 14f * DENSITY + 1.5f * pitch};
        LensBeadGeometry.Beads drawn = LensBeadGeometry.follow(rest, offset, neighbours, 2,
            0f, width, 1f);
        // Every bead is a drawn icon's, at its centre, and the same size as at rest.
        assertEquals(rest.radiusPx, drawn.radiusPx, 0f);
        assertEquals(rest.centerY, drawn.centerY, 0f);
        for (int i = 0; i < drawn.count(); i++) {
            float x = drawn.centerX(i);
            boolean underAnIcon = false;
            for (int j = 0; j < rest.count(); j++)
                underAnIcon |= Math.abs(x - (rest.centerX(j) + offset)) < 1e-3f;
            for (float n : neighbours) underAnIcon |= Math.abs(x - n) < 1e-3f;
            assertTrue("bead at " + x + " has its icon", underAnIcon);
            if (i > 0) assertTrue("left to right", drawn.centerX(i) > drawn.centerX(i - 1));
        }
        // The first icon slid off the glass and took its bead along; the rest and both new ones show.
        assertEquals(rest.count() - 1 + 2, drawn.count());
    }

    @Test
    public void aBeadLeavesWithItsIconAndNoneMeansNothingDrawnNotTheSlab() {
        LensBeadGeometry.Beads rest = phone();
        assertNotNull(rest);
        float width = 411f * DENSITY;
        // A whole row away and nothing staged: no bead is in reach, one is parked far off with
        // the rest's radius, so the program keeps beads (nothing to draw) instead of the slab.
        LensBeadGeometry.Beads gone = LensBeadGeometry.follow(rest, -2f * width, new float[0], 0,
            0f, width, 1f);
        assertEquals(1, gone.count());
        assertTrue(gone.centerX(0) < -1e5f);
        assertTrue(gone.radiusPx > 0f);
        // A bead whose circle still reaches the glass is kept, one wholly past it is not.
        float r = rest.radiusPx;
        float first = rest.centerX(0);
        assertEquals(rest.count(), LensBeadGeometry.follow(rest, -(first + r) + 1f, new float[0],
            0, 0f, width, 1f).count());
        assertEquals(rest.count() - 1, LensBeadGeometry.follow(rest, -(first + r) - 1f,
            new float[0], 0, 0f, width, 1f).count());
    }

    @Test
    public void aSwitchsFadeShrinksTheBeadsWithTheirIconsButNeverToTheSlab() {
        LensBeadGeometry.Beads rest = phone();
        assertNotNull(rest);
        float width = 411f * DENSITY;
        LensBeadGeometry.Beads half = LensBeadGeometry.follow(rest, 0f, new float[0], 0, 0f,
            width, 0.5f);
        assertEquals(rest.radiusPx * 0.5f, half.radiusPx, 1e-3f);
        LensBeadGeometry.Beads none = LensBeadGeometry.follow(rest, 0f, new float[0], 0, 0f,
            width, 0f);
        assertTrue("a radius of 0 would be the slab", none.radiusPx > 0f);
        // At rest, unmoved and unfaded, follow is the rest's own beads.
        assertEquals(rest, LensBeadGeometry.follow(rest, 0f, new float[0], 0, 0f, width, 1f));
    }

    /**
     * Seven 120 px icons on a 411 dp phone at 2.625 (the default page), the row edge to edge but
     * for {@code insetPx} a side, its slots even from its own edges, on a full-width host.
     */
    private static LensBeadGeometry.Beads sevenOnAPhone(float insetPx) {
        float density = 2.625f;
        float width = 411f * density;
        float icon = 120f;
        float air = 6.5f * density;
        float slot = (width - 2f * insetPx) / 7f;
        float[] boxes = new float[7 * 4];
        for (int i = 0; i < 7; i++) {
            float cx = insetPx + slot * (i + 0.5f);
            boxes[i * 4] = cx - icon / 2f;
            boxes[i * 4 + 1] = air;
            boxes[i * 4 + 2] = cx + icon / 2f;
            boxes[i * 4 + 3] = air + icon;
        }
        return LensBeadGeometry.compute(boxes, 7, 0f, 0f, width, air + icon + air, density);
    }

    @Test
    public void theRowInsetFitsSevenBeadsClearOfTheScreensEdges() {
        float density = 2.625f;
        // Edge to edge the end beads reach the screen's edge.
        LensBeadGeometry.Beads bare = sevenOnAPhone(0f);
        assertNotNull(bare);
        assertTrue("bare margin " + (bare.centerX(0) - bare.radiusPx) / density,
            (bare.centerX(0) - bare.radiusPx) / density < 4f);
        // Inset 9 dp a side, each bead is about 1.1 of its icon and 12 dp from the edges.
        LensBeadGeometry.Beads beads = sevenOnAPhone(LensBeadGeometry.rowSideInsetPx(density));
        assertNotNull(beads);
        assertEquals(7, beads.count());
        float ratio = 2f * beads.radiusPx / 120f;
        assertTrue("ratio " + ratio, ratio >= 1.08f && ratio <= 1.12f);
        float left = (beads.centerX(0) - beads.radiusPx) / density;
        float right = (411f * density - beads.centerX(6) - beads.radiusPx) / density;
        assertTrue("left " + left, left >= LensBeadGeometry.MIN_SCREEN_EDGE_DP - 0.05f);
        assertTrue("right " + right, right >= LensBeadGeometry.MIN_SCREEN_EDGE_DP - 0.05f);
        assertEquals(12f, LensBeadGeometry.MIN_SCREEN_EDGE_DP, 0f);
    }

    @Test
    public void aSwipeNeverDrawsMoreBeadsThanTheProgramHolds() {
        // Eight icons at rest and eight staged: mid-swipe nine reach the glass, past the eight
        // a row at rest may hold.
        float[] boxes = new float[8 * 4];
        for (int i = 0; i < 8; i++) {
            boxes[i * 4] = 10f + i * 100f;
            boxes[i * 4 + 1] = 10f;
            boxes[i * 4 + 2] = 90f + i * 100f;
            boxes[i * 4 + 3] = 90f;
        }
        LensBeadGeometry.Beads rest = LensBeadGeometry.compute(boxes, 8, 0f, 0f, 800f, 100f, 1f);
        assertNotNull(rest);
        float[] neighbours = new float[8];
        for (int i = 0; i < 8; i++) neighbours[i] = 850f - 450f + i * 100f;
        LensBeadGeometry.Beads drawn = LensBeadGeometry.follow(rest, -450f, neighbours, 8,
            0f, 800f, 1f);
        assertEquals(9, drawn.count());
        assertTrue(drawn.count() <= LensBeadGeometry.MAX_DRAWN_BEADS);
        assertEquals(com.termux.app.chrome.GlassRefraction.BEAD_SLOTS,
            LensBeadGeometry.MAX_DRAWN_BEADS);
        assertTrue(LensBeadGeometry.MAX_BEADS + 2 <= LensBeadGeometry.MAX_DRAWN_BEADS);
    }
}
