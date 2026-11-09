package com.termux.app.dock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The dock row's still tiles on Lens beads: the Drawer tile (#97) and the Command palette tile
 * are no page's, so a page swipe holds each still on a bead of its own while every app's bead
 * rides with its icon, the staged page's beads standing on its apps around the tiles. Every
 * combination of the two tiles, every place of the Drawer tile, at the swipe's start, middle and
 * end; and the rail, which lays neither.
 */
public class LensBeadStillTilesTest {

    private static final float DENSITY = 2.625f;
    private static final float WIDTH = 411f * DENSITY;
    private static final float ICON = 120f;
    private static final float AIR = 8f * DENSITY;
    private static final int SLOTS = 7;

    private static final DrawerTilePolicy.Position[] DRAWERS = {null,
        DrawerTilePolicy.Position.START, DrawerTilePolicy.Position.MIDDLE,
        DrawerTilePolicy.Position.END};
    private static final float[] PROGRESS = {0f, 0.5f, 1f};

    /** One row as the dock lays it out: seven even columns, the tiles in theirs. */
    private static final class Row {
        final int apps;
        final int columns;
        final int[] still;
        final LensBeadGeometry.Beads rest;
        final float[] stillX;
        final float[] appX;

        Row(@Nullable DrawerTilePolicy.Position drawer, boolean palette) {
            apps = PaletteTilePolicy.appSlots(SLOTS, drawer != null, palette);
            columns = PaletteTilePolicy.columnCount(apps, drawer != null, palette);
            still = PaletteTilePolicy.stillColumns(drawer, palette, apps, false);
            float[] boxes = new float[columns * 4];
            for (int c = 0; c < columns; c++) {
                float cx = centre(c);
                boxes[c * 4] = cx - ICON / 2f;
                boxes[c * 4 + 1] = AIR;
                boxes[c * 4 + 2] = cx + ICON / 2f;
                boxes[c * 4 + 3] = AIR + ICON;
            }
            rest = LensBeadGeometry.compute(boxes, columns, 0f, 0f, WIDTH, AIR + ICON + AIR,
                DENSITY);
            stillX = new float[still.length];
            for (int i = 0; i < still.length; i++) stillX[i] = centre(still[i]);
            appX = new float[apps];
            for (int app = 0; app < apps; app++)
                appX[app] = centre(drawer == null ? app : DrawerTilePolicy.slotOf(app, drawer, apps));
        }

        float centre(int column) {
            return WIDTH * (column + 0.5f) / columns;
        }

        /** The next page's beads where the swipe stages it, a row to the right of the current. */
        float[] neighbours(float offset) {
            float[] out = new float[apps];
            for (int i = 0; i < apps; i++) out[i] = appX[i] + WIDTH + offset;
            return out;
        }

        LensBeadGeometry.Beads swiped(float progress) {
            float offset = -progress * WIDTH;
            float[] neighbours = neighbours(offset);
            return LensBeadGeometry.follow(rest, offset, stillX, stillX.length, neighbours,
                neighbours.length, 0f, WIDTH, 1f);
        }
    }

    private static boolean reaches(float cx, float radius) {
        return cx + radius > 0f && cx - radius < WIDTH;
    }

    private static float[] centres(LensBeadGeometry.Beads beads) {
        float[] out = new float[beads.count()];
        for (int i = 0; i < out.length; i++) out[i] = beads.centerX(i);
        return out;
    }

    @Test
    public void everyTileKeepsItsOwnBeadStillWhileTheAppsBeadsRide() {
        for (DrawerTilePolicy.Position drawer : DRAWERS) {
            for (boolean palette : new boolean[] {false, true}) {
                Row row = new Row(drawer, palette);
                String name = drawer + (palette ? " + palette" : "");
                assertNotNull(name, row.rest);
                assertEquals(name, SLOTS, row.rest.count());
                for (float progress : PROGRESS) {
                    float offset = -progress * WIDTH;
                    LensBeadGeometry.Beads drawn = row.swiped(progress);
                    float r = row.rest.radiusPx;
                    assertEquals(r, drawn.radiusPx, 0f);
                    // Exactly the tiles' beads where they stand, the current page's apps' moved by
                    // the swipe and the staged page's, each where its circle reaches the glass.
                    List<Float> expected = new ArrayList<>();
                    for (float x : row.stillX) expected.add(x);
                    for (float x : row.appX) if (reaches(x + offset, r)) expected.add(x + offset);
                    for (float x : row.neighbours(offset)) if (reaches(x, r)) expected.add(x);
                    float[] want = new float[expected.size()];
                    for (int i = 0; i < want.length; i++) want[i] = expected.get(i);
                    Arrays.sort(want);
                    assertArrayEquals(name + " at " + progress, want, centres(drawn), 1e-3f);
                }
            }
        }
    }

    /**
     * At the swipe's start the beads are the rest's, and at its end too: the staged page's apps
     * stand around the still tiles exactly as the current page's did, so the landing changes no
     * bead.
     */
    @Test
    public void theSwipesStartAndEndAreTheRowAtRest() {
        for (DrawerTilePolicy.Position drawer : DRAWERS) {
            for (boolean palette : new boolean[] {false, true}) {
                Row row = new Row(drawer, palette);
                String name = drawer + (palette ? " + palette" : "");
                float[] rest = centres(row.rest);
                for (float progress : new float[] {0f, 1f}) {
                    LensBeadGeometry.Beads drawn = row.swiped(progress);
                    // (a + W) - W need not be a to the last bit, so compare to within a hair.
                    assertArrayEquals(name + " at " + progress, rest, centres(drawn), 1e-3f);
                    assertEquals(row.rest.radiusPx, drawn.radiusPx, 0f);
                    assertEquals(row.rest.centerY, drawn.centerY, 0f);
                }
            }
        }
    }

    @Test
    public void midSwipeTheTilesBeadsAreAtTheirRestAndNoAppsBeadIs() {
        for (DrawerTilePolicy.Position drawer : DRAWERS) {
            for (boolean palette : new boolean[] {false, true}) {
                Row row = new Row(drawer, palette);
                String name = drawer + (palette ? " + palette" : "");
                float[] drawn = centres(row.swiped(0.5f));
                for (float x : row.stillX) {
                    boolean kept = false;
                    for (float d : drawn) kept |= Math.abs(d - x) < 1e-3f;
                    assertTrue(name + ": the tile at " + x + " keeps its bead", kept);
                }
                // Half a row over, every app's bead stands between two rest slots: none is
                // where a tile's bead stands.
                int atRest = 0;
                for (float d : drawn) {
                    for (int c = 0; c < row.columns; c++)
                        if (Math.abs(d - row.centre(c)) < 1e-3f) atRest++;
                }
                assertEquals(name, row.still.length, atRest);
            }
        }
    }

    /** Without the palette in the still set its bead rode off with the page: the bug fixed here. */
    @Test
    public void thePalettesBeadWouldRideWithoutItsPin() {
        Row row = new Row(DrawerTilePolicy.Position.START, true);
        float palette = row.centre(PaletteTilePolicy.column(row.apps, true));
        float[] neighbours = row.neighbours(-WIDTH / 2f);
        float[] drawerOnly = centres(LensBeadGeometry.follow(row.rest, -WIDTH / 2f,
            row.centre(0), neighbours, neighbours.length, 0f, WIDTH, 1f));
        for (float d : drawerOnly) assertTrue(Math.abs(d - palette) > 1f);
        boolean kept = false;
        for (float d : centres(row.swiped(0.5f))) kept |= Math.abs(d - palette) < 1e-3f;
        assertTrue(kept);
    }

    @Test
    public void twoTilesNeverPinOneBead() {
        Row row = new Row(null, false);
        float x = row.centre(3);
        LensBeadGeometry.Beads drawn = LensBeadGeometry.follow(row.rest, -WIDTH / 2f,
            new float[] {x, x + 1f}, 2, new float[0], 0, 0f, WIDTH, 1f);
        int still = 0;
        for (float d : centres(drawn)) if (Math.abs(d - x) < 1e-3f) still++;
        assertEquals(1, still);
        // The second found no other bead within a radius: only one stands still.
        boolean[] pinned = LensBeadGeometry.pinnedBeads(row.rest, new float[] {x, x + 1f}, 2);
        assertNotNull(pinned);
        int count = 0;
        for (boolean p : pinned) if (p) count++;
        assertEquals(1, count);
        // NaN and far-off centres pin nothing.
        assertEquals(null, LensBeadGeometry.pinnedBeads(row.rest,
            new float[] {Float.NaN, -1e4f}, 2));
    }

    @Test
    public void noStillsIsThePlainFollowAndOneIsTheDrawerTilesFollow() {
        Row row = new Row(DrawerTilePolicy.Position.MIDDLE, false);
        float offset = -WIDTH / 3f;
        float[] neighbours = row.neighbours(offset);
        assertEquals(LensBeadGeometry.follow(row.rest, offset, neighbours, neighbours.length, 0f,
                WIDTH, 1f),
            LensBeadGeometry.follow(row.rest, offset, new float[0], 0, neighbours,
                neighbours.length, 0f, WIDTH, 1f));
        assertEquals(LensBeadGeometry.follow(row.rest, offset, row.stillX[0], neighbours,
                neighbours.length, 0f, WIDTH, 1f),
            LensBeadGeometry.follow(row.rest, offset, row.stillX, 1, neighbours,
                neighbours.length, 0f, WIDTH, 1f));
    }

    /** A rail lays neither tile: no still columns, so its beads (were any) are the plain follow. */
    @Test
    public void theRailShowsNeitherTile() {
        for (DrawerTilePolicy.Position drawer : DRAWERS) {
            for (boolean palette : new boolean[] {false, true}) {
                int apps = PaletteTilePolicy.appSlots(SLOTS, drawer != null, palette);
                assertEquals(0, PaletteTilePolicy.stillColumns(drawer, palette, apps, true).length);
            }
        }
    }
}
