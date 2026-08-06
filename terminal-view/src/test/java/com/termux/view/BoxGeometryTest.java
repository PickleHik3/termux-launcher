package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoxGeometryTest {

    private static final float[] SCALES = BoxGeometry.DEFAULT_THICKNESS_SCALES;

    private static BoxGeometry.Segments fill(int codePoint, int left, int top, int right, int bottom) {
        BoxGeometry.Segments segments = new BoxGeometry.Segments();
        assertTrue("expected " + Integer.toHexString(codePoint) + " to be handled",
            BoxGeometry.fill(codePoint, left, top, right, bottom, SCALES, true, segments));
        return segments;
    }

    /** The rectangle a horizontal arm produced, identified by sitting on the horizontal band. */
    private static int horizontalBandTop(BoxGeometry.Segments segments, int expectedThickness) {
        for (int i = 0; i < segments.rectCount; i++) {
            int top = segments.rects[i * 4 + 1];
            if (segments.rects[i * 4 + 3] - top == expectedThickness) return top;
        }
        throw new AssertionError("no horizontal band of thickness " + expectedThickness);
    }

    private static int verticalBandLeft(BoxGeometry.Segments segments, int expectedThickness) {
        for (int i = 0; i < segments.rectCount; i++) {
            int left = segments.rects[i * 4];
            if (segments.rects[i * 4 + 2] - left == expectedThickness) return left;
        }
        throw new AssertionError("no vertical band of thickness " + expectedThickness);
    }

    @Test
    public void adjacentColumnsShareOneIntegerEdgeAtAnyFractionalCellWidth() {
        for (float cellWidth : new float[]{9.3f, 11.7f, 20.5f}) {
            int previousRight = BoxGeometry.edge(3.25f, cellWidth, 0);
            for (int column = 0; column < 200; column++) {
                int cellLeft = BoxGeometry.edge(3.25f, cellWidth, column);
                int cellRight = BoxGeometry.edge(3.25f, cellWidth, column + 1);
                assertEquals("column " + column + " starts where column " + (column - 1) + " ended",
                    previousRight, cellLeft);
                assertTrue("every cell has width", cellRight > cellLeft);
                previousRight = cellRight;
            }
        }
    }

    @Test
    public void horizontalLinesInAdjacentCellsFormOneContinuousSpan() {
        for (float cellWidth : new float[]{9.3f, 11.7f, 20.5f}) {
            int leftEdge = BoxGeometry.edge(0f, cellWidth, 7);
            int sharedEdge = BoxGeometry.edge(0f, cellWidth, 8);
            int rightEdge = BoxGeometry.edge(0f, cellWidth, 9);
            BoxGeometry.Segments first = fill(0x2500, leftEdge, 0, sharedEdge, 24);
            BoxGeometry.Segments second = fill(0x2500, sharedEdge, 0, rightEdge, 24);
            int thickness = BoxGeometry.thickness(sharedEdge - leftEdge, 24, SCALES,
                BoxGeometry.LIGHT);
            assertEquals(leftEdge, spanStart(first));
            assertEquals("the left cell reaches the shared edge", sharedEdge, spanEnd(first));
            assertEquals("the right cell starts at the shared edge", sharedEdge, spanStart(second));
            assertEquals(rightEdge, spanEnd(second));
            assertEquals("the two cells share one centreline",
                horizontalBandTop(first, thickness), horizontalBandTop(second, thickness));
        }
    }

    @Test
    public void verticalLinesInAdjacentRowsFormOneContinuousSpan() {
        int height = 19;
        int top = BoxGeometry.edge(2f, height, 3);
        int shared = BoxGeometry.edge(2f, height, 4);
        int bottom = BoxGeometry.edge(2f, height, 5);
        BoxGeometry.Segments first = fill(0x2502, 0, top, 11, shared);
        BoxGeometry.Segments second = fill(0x2502, 0, shared, 11, bottom);
        assertEquals(top, verticalSpanStart(first));
        assertEquals("the upper cell reaches the shared edge", shared, verticalSpanEnd(first));
        assertEquals("the lower cell starts at the shared edge", shared, verticalSpanStart(second));
        assertEquals(bottom, verticalSpanEnd(second));
        int thickness = BoxGeometry.thickness(11, shared - top, SCALES, BoxGeometry.LIGHT);
        assertEquals(verticalBandLeft(first, thickness), verticalBandLeft(second, thickness));
    }

    /** The left edge of the union of every rectangle, which for a line is where its ink starts. */
    private static int spanStart(BoxGeometry.Segments segments) {
        int start = Integer.MAX_VALUE;
        for (int i = 0; i < segments.rectCount; i++) start = Math.min(start, segments.rects[i * 4]);
        return start;
    }

    private static int spanEnd(BoxGeometry.Segments segments) {
        int end = Integer.MIN_VALUE;
        for (int i = 0; i < segments.rectCount; i++) end = Math.max(end, segments.rects[i * 4 + 2]);
        return end;
    }

    private static int verticalSpanStart(BoxGeometry.Segments segments) {
        int start = Integer.MAX_VALUE;
        for (int i = 0; i < segments.rectCount; i++)
            start = Math.min(start, segments.rects[i * 4 + 1]);
        return start;
    }

    private static int verticalSpanEnd(BoxGeometry.Segments segments) {
        int end = Integer.MIN_VALUE;
        for (int i = 0; i < segments.rectCount; i++) end = Math.max(end, segments.rects[i * 4 + 3]);
        return end;
    }

    @Test
    public void crossesAndTeesAgreeOnBothCentrelines() {
        int left = 4, top = 9, right = 4 + 11, bottom = 9 + 26;
        int thickness = BoxGeometry.thickness(right - left, bottom - top, SCALES, BoxGeometry.LIGHT);
        int expectedTop = horizontalBandTop(fill(0x2500, left, top, right, bottom), thickness);
        int expectedLeft = verticalBandLeft(fill(0x2502, left, top, right, bottom), thickness);
        for (int codePoint : new int[]{0x2500, 0x253C, 0x251C, 0x2524, 0x252C, 0x2534}) {
            BoxGeometry.Segments segments = fill(codePoint, left, top, right, bottom);
            assertEquals("horizontal centreline of " + Integer.toHexString(codePoint),
                expectedTop, horizontalBandTop(segments, thickness));
        }
        for (int codePoint : new int[]{0x2502, 0x253C, 0x251C, 0x2524, 0x252C, 0x2534}) {
            BoxGeometry.Segments segments = fill(codePoint, left, top, right, bottom);
            assertEquals("vertical centreline of " + Integer.toHexString(codePoint),
                expectedLeft, verticalBandLeft(segments, thickness));
        }
    }

    @Test
    public void continuingArmsReachTheCellEdgeAndStubsStopInside() {
        int left = 0, top = 0, right = 12, bottom = 28;
        BoxGeometry.Segments line = fill(0x2500, left, top, right, bottom);
        assertEquals(left, spanStart(line));
        assertEquals(right, spanEnd(line));
        BoxGeometry.Segments stub = fill(0x2574, left, top, right, bottom);
        assertEquals(1, stub.rectCount);
        assertEquals(left, stub.rects[0]);
        assertTrue("╴ stops inside its cell", stub.rects[2] < right);
        assertTrue("╴ reaches the centre", stub.rects[2] >= (left + right) / 2);
    }

    @Test
    public void thicknessIsQuantizedBoundedAndMonotonic() {
        for (int width = 5; width < 40; width++) {
            for (int height = 8; height < 70; height++) {
                int limit = Math.max(1, Math.min(width, height) / 3);
                int previous = 0;
                for (int weight = BoxGeometry.THIN; weight <= BoxGeometry.VERY_HEAVY; weight++) {
                    int thickness = BoxGeometry.thickness(width, height, SCALES, weight);
                    assertTrue("never zero", thickness >= 1);
                    assertTrue("never more than a third of the cell", thickness <= limit);
                    assertTrue("monotonic across the weight table", thickness >= previous);
                    previous = thickness;
                }
            }
        }
        assertEquals(BoxGeometry.thickness(11, 26, SCALES, BoxGeometry.LIGHT),
            BoxGeometry.thickness(11, 26, null, BoxGeometry.LIGHT));
    }

    @Test
    public void lowerBlocksGrowMonotonicallyAndEighthBandsTileExactly() {
        int left = 3, top = 5, right = 3 + 13, bottom = 5 + 29;
        int previousHeight = 0;
        for (int codePoint = 0x2581; codePoint <= 0x2588; codePoint++) {
            BoxGeometry.Segments segments = fill(codePoint, left, top, right, bottom);
            assertEquals(1, segments.rectCount);
            assertEquals(bottom, segments.rects[3]);
            int height = segments.rects[3] - segments.rects[1];
            assertTrue("block " + Integer.toHexString(codePoint) + " grows",
                height > previousHeight);
            previousHeight = height;
        }
        assertEquals(bottom - top, previousHeight);
        // U+2594 is the first eighth and U+1FB76-U+1FB7B are bands two to seven of the same grid.
        int previousBottom = fill(0x2594, left, top, right, bottom).rects[3];
        assertEquals(top, fill(0x2594, left, top, right, bottom).rects[1]);
        for (int codePoint = 0x1FB76; codePoint <= 0x1FB7B; codePoint++) {
            BoxGeometry.Segments band = fill(codePoint, left, top, right, bottom);
            assertEquals("band " + Integer.toHexString(codePoint) + " tiles with the one above",
                previousBottom, band.rects[1]);
            assertTrue(band.rects[3] > band.rects[1]);
            previousBottom = band.rects[3];
        }
        assertEquals(fill(0x2581, left, top, right, bottom).rects[1], previousBottom);
    }

    @Test
    public void halvesAndQuadrantsSplitAtTheSameRoundedCentre() {
        int left = 2, top = 7, right = 2 + 15, bottom = 7 + 25;
        int midX = fill(0x2590, left, top, right, bottom).rects[0];
        int midY = fill(0x2584, left, top, right, bottom).rects[1];
        assertEquals(fill(0x258C, left, top, right, bottom).rects[2], midX);
        assertEquals(fill(0x2580, left, top, right, bottom).rects[3], midY);
        BoxGeometry.Segments lowerRight = fill(0x2597, left, top, right, bottom);
        assertEquals(midX, lowerRight.rects[0]);
        assertEquals(midY, lowerRight.rects[1]);
        assertEquals(right, lowerRight.rects[2]);
        assertEquals(bottom, lowerRight.rects[3]);
    }

    @Test
    public void shadesReportTheirAlphaAndCoverTheWholeCell() {
        int[] expected = {25, 50, 75};
        for (int i = 0; i < expected.length; i++) {
            BoxGeometry.Segments segments = fill(0x2591 + i, 4, 6, 4 + 12, 6 + 24);
            assertEquals(0, segments.rectCount);
            assertEquals(1, segments.shadeCount);
            assertEquals(expected[i], segments.shadeAlphaPercents[0]);
            assertEquals(4, segments.shadeRects[0]);
            assertEquals(6, segments.shadeRects[1]);
            assertEquals(16, segments.shadeRects[2]);
            assertEquals(30, segments.shadeRects[3]);
        }
    }

    @Test
    public void everyBraillePatternDrawsItsOwnDotsInsideTheCell() {
        int left = 5, top = 11, right = 5 + 12, bottom = 11 + 28;
        for (int codePoint = 0x2800; codePoint <= 0x28FF; codePoint++) {
            BoxGeometry.Segments segments = fill(codePoint, left, top, right, bottom);
            assertEquals("dot count of " + Integer.toHexString(codePoint),
                Integer.bitCount(codePoint - 0x2800), segments.dotCount);
            assertTrue(segments.dotRadius >= 1f);
            for (int i = 0; i < segments.dotCount; i++) {
                float x = segments.dots[i * 2];
                float y = segments.dots[i * 2 + 1];
                assertTrue("dot inside the cell", x > left && x < right && y > top && y < bottom);
            }
        }
        // Dot 1 is the upper left and dot 8 the lower right, per the Unicode dot numbering.
        BoxGeometry.Segments first = fill(0x2801, left, top, right, bottom);
        BoxGeometry.Segments last = fill(0x2880, left, top, right, bottom);
        assertTrue(first.dots[0] < last.dots[0]);
        assertTrue(first.dots[1] < last.dots[1]);
    }

    @Test
    public void doubleLinesUseTwoIntegerRailsThatDoNotOverlap() {
        int left = 0, top = 0, right = 14, bottom = 30;
        BoxGeometry.Segments segments = fill(0x2550, left, top, right, bottom);
        assertEquals(2, segments.rectCount);
        int firstTop = segments.rects[1];
        int firstBottom = segments.rects[3];
        int secondTop = segments.rects[5];
        int secondBottom = segments.rects[7];
        assertTrue("rails have body", firstBottom > firstTop && secondBottom > secondTop);
        assertTrue("rails are separated by a gap", secondTop > firstBottom);
        int rail = firstBottom - firstTop;
        assertEquals("both rails are the same width", rail, secondBottom - secondTop);
        int heavy = BoxGeometry.thickness(right - left, bottom - top, SCALES, BoxGeometry.HEAVY);
        assertEquals("double and heavy occupy the same band", heavy, secondBottom - firstTop);
        assertEquals(left, segments.rects[0]);
        assertEquals(right, segments.rects[2]);
    }

    @Test
    public void everyClaimedRangeIsSynthesizedAndItsNeighboursAreNot() {
        BoxGeometry.Segments segments = new BoxGeometry.Segments();
        int[][] ranges = {
            {0x2500, 0x259F}, {0x25E2, 0x25E5}, {0x2800, 0x28FF},
            {0x1FB00, 0x1FB3B}, {0x1FB70, 0x1FB8F},
        };
        for (int[] range : ranges) {
            assertFalse(Integer.toHexString(range[0] - 1),
                BoxGeometry.isSynthesizable(range[0] - 1, true));
            assertFalse(Integer.toHexString(range[1] + 1),
                BoxGeometry.isSynthesizable(range[1] + 1, true));
            for (int codePoint = range[0]; codePoint <= range[1]; codePoint++) {
                assertTrue(Integer.toHexString(codePoint),
                    BoxGeometry.isSynthesizable(codePoint, false));
                assertTrue("fill must handle every claimed code point "
                        + Integer.toHexString(codePoint),
                    BoxGeometry.fill(codePoint, 0, 0, 13, 27, SCALES, false, segments));
            }
        }
        // The legacy-computing sub-ranges deliberately left to the font.
        for (int codePoint : new int[]{0x1FB3C, 0x1FB4F, 0x1FB6F, 0x1FB90, 0x1FB95, 0x1FBAF,
            0x1FBF0}) {
            assertFalse(Integer.toHexString(codePoint),
                BoxGeometry.isSynthesizable(codePoint, true));
            assertFalse(BoxGeometry.fill(codePoint, 0, 0, 13, 27, SCALES, true, segments));
        }
    }

    @Test
    public void powerlineRangesAreClaimedOnlyWhenEnabled() {
        BoxGeometry.Segments segments = new BoxGeometry.Segments();
        for (int codePoint = 0xE0B0; codePoint <= 0xE0B7; codePoint++) {
            assertTrue(BoxGeometry.isSynthesizable(codePoint, true));
            assertFalse(BoxGeometry.isSynthesizable(codePoint, false));
            assertTrue(BoxGeometry.fill(codePoint, 0, 0, 13, 27, SCALES, true, segments));
            assertFalse(BoxGeometry.fill(codePoint, 0, 0, 13, 27, SCALES, false, segments));
        }
        for (int codePoint = 0xE0BA; codePoint <= 0xE0BD; codePoint++) {
            assertTrue(BoxGeometry.isSynthesizable(codePoint, true));
            assertFalse(BoxGeometry.isSynthesizable(codePoint, false));
            assertTrue(BoxGeometry.fill(codePoint, 0, 0, 13, 27, SCALES, true, segments));
        }
        for (int codePoint : new int[]{0xE0AF, 0xE0B8, 0xE0B9, 0xE0BE, 0xE0BF}) {
            assertFalse(Integer.toHexString(codePoint),
                BoxGeometry.isSynthesizable(codePoint, true));
        }
    }

    @Test
    public void sextantsCoverEveryPatternThatIsNotAlreadyAnotherCharacter() {
        int left = 0, top = 0, right = 12, bottom = 27;
        int midX = fill(0x2590, left, top, right, bottom).rects[0];
        boolean[] seen = new boolean[64];
        for (int codePoint = 0x1FB00; codePoint <= 0x1FB3B; codePoint++) {
            BoxGeometry.Segments segments = fill(codePoint, left, top, right, bottom);
            int pattern = 0;
            for (int i = 0; i < segments.rectCount; i++) {
                int cellLeft = segments.rects[i * 4];
                int column = cellLeft == left ? 0 : 1;
                assertEquals(column == 0 ? midX : right, segments.rects[i * 4 + 2]);
                int row = -1;
                for (int candidate = 0; candidate < 3; candidate++) {
                    if (segments.rects[i * 4 + 1] == Math.round(top + (bottom - top)
                        * candidate / 3f)) row = candidate;
                }
                assertTrue("sextant row lands on a third", row >= 0);
                pattern |= 1 << (row * 2 + column);
            }
            assertFalse("no pattern is encoded twice", seen[pattern]);
            seen[pattern] = true;
        }
        assertFalse("the empty cell is not a sextant", seen[0]);
        assertFalse("the left half block is not a sextant", seen[0b010101]);
        assertFalse("the right half block is not a sextant", seen[0b101010]);
        assertFalse("the full block is not a sextant", seen[0b111111]);
    }

    @Test
    public void dashedVariantsRepeatAStablePeriodAcrossTheCell() {
        int left = 0, right = 24;
        int[] codePoints = {0x2504, 0x2508, 0x254C};
        int[] dashes = {3, 4, 2};
        for (int i = 0; i < codePoints.length; i++) {
            BoxGeometry.Segments segments = fill(codePoints[i], left, 0, right, 28);
            assertEquals(1, segments.dashCount);
            assertEquals(0, segments.rectCount);
            int period = segments.dashRuns[4];
            int onLength = segments.dashRuns[5];
            assertTrue(period >= 2 && onLength >= 1 && onLength < period);
            assertEquals("the period divides the cell span", (right - left) / dashes[i], period);
            assertEquals(left, segments.dashRuns[0]);
            assertEquals(right, segments.dashRuns[2]);
        }
    }
}
