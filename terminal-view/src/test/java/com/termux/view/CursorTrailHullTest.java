package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The shape of the cursor streak.
 * <p>
 * The first version drew the rectangle bounding the old and new cursor cells, which on a diagonal
 * move is a block covering every cell between them and tints all the text inside it. These tests pin
 * the hull that replaced it: a band one cell wide along the direction of travel.
 * </p>
 */
public class CursorTrailHullTest {

    private static final float W = 10f;

    private static final float H = 20f;

    /** Corners of two cells, tail first, in the order {@code CursorTrail} builds them. */
    private static float[] cells(float tailX, float tailY, float headX, float headY) {
        return new float[]{
            tailX, tailY, tailX + W, tailY, tailX + W, tailY + H, tailX, tailY + H,
            headX, headY, headX + W, headY, headX + W, headY + H, headX, headY + H,
        };
    }

    private static float area(float[] corners, int[] hull, int count) {
        float sum = 0f;
        for (int i = 0; i < count; i++) {
            int a = hull[i] * 2;
            int b = hull[(i + 1) % count] * 2;
            sum += corners[a] * corners[b + 1] - corners[b] * corners[a + 1];
        }
        return Math.abs(sum) / 2f;
    }

    private static int hullOf(float[] corners, int[] hull) {
        int count = CursorTrail.convexHull(corners, hull);
        assertTrue("hull cannot exceed its input", count <= 8);
        assertTrue("a hull needs at least a triangle", count >= 3);
        return count;
    }

    @Test
    public void aHorizontalMoveIsARectangle() {
        float[] corners = cells(0f, 0f, 50f, 0f);
        int[] hull = new int[9];
        int count = hullOf(corners, hull);
        assertEquals(4, count);
        assertEquals((50f + W) * H, area(corners, hull, count), 0.01f);
    }

    @Test
    public void aVerticalMoveIsARectangle() {
        float[] corners = cells(0f, 0f, 0f, 60f);
        int[] hull = new int[9];
        int count = hullOf(corners, hull);
        assertEquals(4, count);
        assertEquals(W * (60f + H), area(corners, hull, count), 0.01f);
    }

    @Test
    public void aStationaryCursorIsOneCell() {
        float[] corners = cells(30f, 40f, 30f, 40f);
        int[] hull = new int[9];
        int count = hullOf(corners, hull);
        assertEquals(4, count);
        assertEquals(W * H, area(corners, hull, count), 0.01f);
    }

    /** The case the bounding box got wrong: the hull must be a fraction of the block. */
    @Test
    public void aDiagonalMoveIsABandNotABlock() {
        // Nine columns across and five rows down, the jump in the reported screenshot.
        float[] corners = cells(0f, 0f, 9f * W, 5f * H);
        int[] hull = new int[9];
        int count = hullOf(corners, hull);
        assertEquals("a diagonal hull of two rectangles is a hexagon", 6, count);
        float boundingBox = (9f * W + W) * (5f * H + H);
        float hullArea = area(corners, hull, count);
        assertTrue("hull " + hullArea + " must be well inside the block " + boundingBox,
            hullArea < boundingBox * 0.55f);
    }

    @Test
    public void bothDiagonalDirectionsAreHandled() {
        int[] hull = new int[9];
        float[] downRight = cells(0f, 0f, 40f, 40f);
        float[] downLeft = cells(40f, 0f, 0f, 40f);
        int downRightCount = hullOf(downRight, hull);
        float downRightArea = area(downRight, hull, downRightCount);
        int downLeftCount = hullOf(downLeft, hull);
        float downLeftArea = area(downLeft, hull, downLeftCount);
        assertEquals(downRightCount, downLeftCount);
        assertEquals("mirrored moves must smear the same amount", downRightArea, downLeftArea, 0.01f);
    }

    /** Every corner of both cells has to be inside the hull, or the cursor would poke out of it. */
    @Test
    public void theHullContainsBothCells() {
        float[] corners = cells(0f, 0f, 70f, 30f);
        int[] hull = new int[9];
        int count = hullOf(corners, hull);
        for (int point = 0; point < 8; point++) {
            assertTrue("corner " + point + " outside the hull", insideOrOn(corners, hull, count, point));
        }
    }

    private static boolean insideOrOn(float[] corners, int[] hull, int count, int point) {
        float px = corners[point * 2], py = corners[point * 2 + 1];
        // The chain emits its points in one consistent winding, so every edge must keep the point on
        // the same side.
        int sign = 0;
        for (int i = 0; i < count; i++) {
            int a = hull[i] * 2;
            int b = hull[(i + 1) % count] * 2;
            float cross = (corners[b] - corners[a]) * (py - corners[a + 1])
                - (corners[b + 1] - corners[a + 1]) * (px - corners[a]);
            if (Math.abs(cross) < 0.001f)
                continue;
            int thisSign = cross > 0 ? 1 : -1;
            if (sign == 0) {
                sign = thisSign;
            } else if (sign != thisSign) {
                return false;
            }
        }
        return true;
    }
}
