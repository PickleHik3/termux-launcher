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

    /**
     * The Drawer tile on Lens beads (#97) stands on a bead of its own, one of the row's, and
     * stands still while a swipe carries the pages: its bead stays under it, every other one
     * rides with its icon.
     */
    @Test
    public void midSwipeTheDrawerTilesBeadStaysUnderTheStillTile() {
        LensBeadGeometry.Beads rest = phone();
        assertNotNull(rest);
        float width = 411f * DENSITY;
        float offset = -width / 3f;
        // The tile at the row's end, a hair off its bead's centre as a laid-out view may be.
        float tile = rest.centerX(4) + 0.4f;
        LensBeadGeometry.Beads drawn = LensBeadGeometry.follow(rest, offset, tile, new float[0],
            0, 0f, width, 1f);
        boolean stillUnderTheTile = false;
        for (int i = 0; i < drawn.count(); i++) {
            float x = drawn.centerX(i);
            if (Math.abs(x - rest.centerX(4)) < 1e-3f) {
                stillUnderTheTile = true;
                continue;
            }
            boolean rides = false;
            for (int j = 0; j < 4; j++) rides |= Math.abs(x - (rest.centerX(j) + offset)) < 1e-3f;
            assertTrue("bead at " + x + " rides with its icon", rides);
        }
        assertTrue("the tile keeps its bead", stillUnderTheTile);
        // The first icon slid off; the other three ride, and the tile's bead stays.
        assertEquals(4, drawn.count());

        // No tile, or one nowhere near a bead, is the plain follow.
        assertEquals(LensBeadGeometry.follow(rest, offset, new float[0], 0, 0f, width, 1f),
            LensBeadGeometry.follow(rest, offset, Float.NaN, new float[0], 0, 0f, width, 1f));
        assertEquals(LensBeadGeometry.follow(rest, offset, new float[0], 0, 0f, width, 1f),
            LensBeadGeometry.follow(rest, offset, -1e4f, new float[0], 0, 0f, width, 1f));
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

    /** Seven icons of {@code iconPx} in even slots of a row inset {@code insetPx} a side, 411 dp at 2.625. */
    private static LensBeadGeometry.Beads sevenOfSize(float insetPx, float iconPx) {
        float density = 2.625f;
        float width = 411f * density;
        float air = 8f * density;
        float slot = (width - 2f * insetPx) / 7f;
        float[] boxes = new float[7 * 4];
        for (int i = 0; i < 7; i++) {
            float cx = insetPx + slot * (i + 0.5f);
            boxes[i * 4] = cx - iconPx / 2f;
            boxes[i * 4 + 1] = air;
            boxes[i * 4 + 2] = cx + iconPx / 2f;
            boxes[i * 4 + 3] = air + iconPx;
        }
        return LensBeadGeometry.compute(boxes, 7, 0f, 0f, width, air + iconPx + air, density);
    }

    @Test
    public void theDocksOwnIconsInTheInsetRowAreHeldSoTheyKeepTheirBeads() {
        // Emulator pass 2: the dock hands the row 51.5 dp icons (135 px at 2.625), held only to
        // the slot less 4 dp. With the 9 dp inset seven slots are 56.2 dp, so a bead with its
        // 6 dp gap is under 51 dp and every bead fell back to the slab.
        float density = 2.625f;
        float inset = LensBeadGeometry.rowSideInsetPx(density);
        int rowWidth = Math.round(411f * density - 2f * inset);
        int rowFit = DockLayoutPolicy.slotFitIconPx(135, rowWidth, 7, density);
        assertNull("the row's own fit loses the beads", sevenOfSize(inset, rowFit));
        // Held to the beads' fit, the same row wears beads about 1.1 of the icon.
        int beadFit = LensBeadGeometry.fitIconPx(rowFit, rowWidth / 7f, density);
        assertTrue(beadFit < rowFit);
        LensBeadGeometry.Beads beads = sevenOfSize(inset, beadFit);
        assertNotNull(beads);
        assertEquals(7, beads.count());
        assertTrue("ratio " + 2f * beads.radiusPx / beadFit,
            2f * beads.radiusPx >= LensBeadGeometry.MIN_FIT_SCALE * beadFit - 1e-3f);
        // An icon that already fits is left as it is.
        assertEquals(100, LensBeadGeometry.fitIconPx(100, rowWidth / 7f, density));
    }

    private static com.termux.app.place.PlaceLayout homeLayout(
            com.termux.app.place.PlaceLayout.RowPlacement apps, boolean azShown,
            com.termux.app.place.PlaceLayout.Edge azEdge) {
        return new com.termux.app.place.PlaceLayout(com.termux.app.place.PlaceLayout.Edge.TOP,
            apps, azShown, azEdge, com.termux.app.place.PlaceLayout.RowPlacement.HIDDEN,
            com.termux.app.place.PlaceLayout.KeyboardMode.RESIZE,
            com.termux.app.place.PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    }

    @Test
    public void theRowWearsBeadsOnlyAloneOnTheDocksGlass() {
        com.termux.app.place.PlaceLayout.Edge bottom = com.termux.app.place.PlaceLayout.Edge.BOTTOM;
        com.termux.app.place.PlaceLayout.RowPlacement row =
            com.termux.app.place.PlaceLayout.RowPlacement.BOTTOM;
        // The A–Z row hidden, its edge still reading the bottom (pass 2's prefs): beads.
        assertTrue(LensBeadGeometry.rowWearsBeads(homeLayout(row, false, bottom), false, false));
        // The A–Z row on the dock: the slab.
        assertTrue(!LensBeadGeometry.rowWearsBeads(homeLayout(row, true, bottom), false, false));
        // The A–Z row on another edge leaves the row alone: beads.
        assertTrue(LensBeadGeometry.rowWearsBeads(
            homeLayout(row, true, com.termux.app.place.PlaceLayout.Edge.TOP), false, false));
        // A rail, no row, the frame's glass or the keyboard's: the slab.
        assertTrue(!LensBeadGeometry.rowWearsBeads(homeLayout(
            com.termux.app.place.PlaceLayout.RowPlacement.LEFT, false, bottom), false, false));
        assertTrue(!LensBeadGeometry.rowWearsBeads(homeLayout(
            com.termux.app.place.PlaceLayout.RowPlacement.HIDDEN, false, bottom), false, false));
        assertTrue(!LensBeadGeometry.rowWearsBeads(homeLayout(row, false, bottom), true, false));
        assertTrue(!LensBeadGeometry.rowWearsBeads(homeLayout(row, false, bottom), false, true));
    }

    // ---------------------------------------------------------------- melt across the seam

    /** Every point of the slab lies inside the bead nearest it, at the cover radius. */
    @Test
    public void theCoverRadiusCoversTheWholeSlab() {
        LensBeadGeometry.Beads beads = phone();
        assertNotNull(beads);
        float width = 411f * DENSITY;
        float height = (6.5f + 58f + 6.5f) * DENSITY;
        float cover = LensBeadGeometry.coverRadiusPx(beads, width, height);
        assertTrue(cover > beads.radiusPx);
        for (float x = 0f; x <= width; x += 3f) {
            for (float y : new float[] {0f, height / 2f, height}) {
                float nearest = Float.MAX_VALUE;
                for (int i = 0; i < beads.count(); i++) {
                    float dx = x - beads.centerX(i);
                    float dy = y - beads.centerY;
                    nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy));
                }
                assertTrue("(" + x + ", " + y + ") is outside every bead", nearest <= cover);
            }
        }
    }

    /** Home's share 1 is the beads' own size; they swell as it falls and cover by its last part. */
    @Test
    public void theBeadsSwellWithHomesShareAndCoverBeforeTheFarSide() {
        float rest = 80f;
        float cover = 300f;
        assertEquals(rest, LensBeadGeometry.meltRadiusPx(rest, cover, 1f), 0.001f);
        assertEquals(rest, LensBeadGeometry.meltRadiusPx(rest, cover, Float.NaN), 0.001f);
        assertEquals(cover, LensBeadGeometry.meltRadiusPx(rest, cover,
            LensBeadGeometry.MELT_COVERED_SHARE), 0.001f);
        assertEquals(cover, LensBeadGeometry.meltRadiusPx(rest, cover, 0f), 0.001f);
        float last = rest;
        for (float share = 1f; share >= 0f; share -= 0.05f) {
            float r = LensBeadGeometry.meltRadiusPx(rest, cover, share);
            assertTrue("the beads shrank as Home slid away", r >= last - 0.001f);
            last = r;
        }
        // Eased in: the first stretch of the slide barely moves them.
        assertTrue(LensBeadGeometry.meltRadiusPx(rest, cover, 0.95f) - rest < 0.02f * (cover - rest));
    }

    /** Leaving Home with its rows: the melt runs over the lead-in and is done at its end. */
    @Test
    public void leavingBeadsMeltOverTheLeadIn() {
        float leadIn = 0.08f;
        assertEquals(1f, LensBeadGeometry.leavingMeltShare(0f, leadIn), 0f);
        assertEquals(1f, LensBeadGeometry.leavingMeltShare(Float.NaN, leadIn), 0f);
        assertEquals(0.5f, LensBeadGeometry.leavingMeltShare(0.04f, leadIn), 0.0001f);
        assertEquals(0f, LensBeadGeometry.leavingMeltShare(leadIn, leadIn), 0f);
        assertEquals(0f, LensBeadGeometry.leavingMeltShare(0.6f, leadIn), 0f);
        float last = 1f;
        for (float away = 0f; away <= 1f; away += 0.005f) {
            float share = LensBeadGeometry.leavingMeltShare(away, leadIn);
            assertTrue("Home's share rose as the wall went away", share <= last + 1e-6f);
            last = share;
        }
    }

    /**
     * The beads are the slab, with no shadow or glow of their own left, on every frame where the
     * stack has started to sink below its bottom edge (pass 6: beads cut flat by it, and a pale
     * line of their glow left behind). Home → Workspace with different rows: a dip.
     */
    @Test
    public void leavingBeadsAreTheSlabBeforeTheStackSinks() {
        java.util.List<com.termux.app.wall.PaneWallPage> row = java.util.Arrays.asList(
            com.termux.app.wall.PaneWallPage.WIDGETS, com.termux.app.wall.PaneWallPage.TERMINAL);
        com.termux.app.place.PlaceChromeTravel.States states = place ->
            new com.termux.app.place.PlaceChromeTravel.Rest(false, false,
                place != com.termux.app.wall.PaneWallPage.WIDGETS);
        float rest = 80f;
        float cover = 300f;
        int width = 1000;
        boolean melting = false;
        for (int px = 0; px <= width; px += 2) {
            com.termux.app.place.PlaceChromeTravel.Frame frame =
                com.termux.app.place.PlaceChromeTravel.at(row,
                    com.termux.app.wall.PaneWallPage.WIDGETS, -px, width, states);
            float share = LensBeadGeometry.leavingMeltShare(
                com.termux.app.place.SeamTravel.otherHalfFraction(frame,
                    com.termux.app.wall.PaneWallPage.WIDGETS),
                com.termux.app.place.PlaceChromeTravel.LANDING);
            float dockReveal = com.termux.app.place.SeamTravel.dockReveal(
                com.termux.app.place.SeamTravel.DockPlan.DIP,
                com.termux.app.place.SeamTravel.otherHalfProgress(frame,
                    com.termux.app.wall.PaneWallPage.WIDGETS));
            float radius = LensBeadGeometry.meltRadiusPx(rest, cover, share);
            if (radius > rest) melting = true;
            if (dockReveal < 1f) {
                assertEquals("beads still melting while the stack sinks, at " + px + " px",
                    cover, radius, 0.001f);
                assertEquals(0f, LensBeadGeometry.beadShelfAlpha(share), 0f);
            }
        }
        assertTrue("the beads never swelled", melting);
    }

    /** The beads' shadows and glow: whole at rest, gone once the beads cover the slab. */
    @Test
    public void theBeadShelfFadesAsTheBeadsMelt() {
        assertEquals(1f, LensBeadGeometry.beadShelfAlpha(1f), 0f);
        assertEquals(1f, LensBeadGeometry.beadShelfAlpha(Float.NaN), 0f);
        assertEquals(0f, LensBeadGeometry.beadShelfAlpha(LensBeadGeometry.MELT_COVERED_SHARE),
            0.0001f);
        assertEquals(0f, LensBeadGeometry.beadShelfAlpha(0f), 0f);
        float last = 1f;
        for (float share = 1f; share >= 0f; share -= 0.05f) {
            float alpha = LensBeadGeometry.beadShelfAlpha(share);
            assertTrue(alpha <= last + 1e-6f);
            last = alpha;
        }
    }

    @Test
    public void withRadiusKeepsTheBeadsWhereTheyStand() {
        LensBeadGeometry.Beads beads = phone();
        assertNotNull(beads);
        LensBeadGeometry.Beads swollen = beads.withRadius(beads.radiusPx * 2f);
        assertEquals(beads.count(), swollen.count());
        assertEquals(beads.centerX(2), swollen.centerX(2), 0f);
        assertEquals(beads.centerY, swollen.centerY, 0f);
        assertEquals(beads.radiusPx * 2f, swollen.radiusPx, 0f);
        assertTrue(beads.withRadius(beads.radiusPx) == beads);
    }
}
