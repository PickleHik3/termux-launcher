package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The cell boxes of {@link GridPixels}: rows and columns that meet on one shared whole pixel, under
 * a grid translation that is itself whole pixels, so cell backgrounds tile with nothing of the
 * ground under the grid showing between them.
 */
public class GridPixelsTest {

    /** Line spacings with and without a fraction; pong's 12dp font is 36 pixels a row. */
    private static final float[] LINE_SPACINGS = { 36f, 35.5f, 17.3f, 41.75f, 9.9f };
    /** Where the first row starts: the ascent slack, with and without a fraction. */
    private static final float[] ORIGINS = { 0f, 8f, 7.4f, 12.5f, -3.6f };
    /** Smooth-scroll and travel offsets a frame can be drawn at. */
    private static final float[] OFFSETS = { 0f, -0.5f, -12.37f, -35.9f, 0.49f, 17.51f, -100.25f };

    @Test
    public void aRowsBottomIsTheNextRowsTop() {
        for (float spacing : LINE_SPACINGS) {
            for (float origin : ORIGINS) {
                for (int row = 0; row < 80; row++) {
                    int top = GridPixels.rowEdge(origin, spacing, row);
                    int bottom = GridPixels.rowEdge(origin, spacing, row + 1);
                    int nextTop = GridPixels.rowEdge(origin, spacing, row + 1);
                    assertEquals(nextTop, bottom);
                    // No row is empty or eats a neighbour: its height is the spacing, rounded.
                    assertTrue(bottom - top >= (int) Math.floor(spacing));
                    assertTrue(bottom - top <= (int) Math.ceil(spacing));
                }
            }
        }
    }

    @Test
    public void aScrollOrTravelOffsetMovesTheGridByWholePixels() {
        for (float offset : OFFSETS) {
            int snapped = GridPixels.snap(offset);
            assertTrue(Math.abs(snapped - offset) <= 0.5f);
            for (float spacing : LINE_SPACINGS) {
                int previousBottom = Integer.MIN_VALUE;
                for (int row = 0; row < 40; row++) {
                    int top = snapped + GridPixels.rowEdge(8f, spacing, row);
                    int bottom = snapped + GridPixels.rowEdge(8f, spacing, row + 1);
                    if (previousBottom != Integer.MIN_VALUE) assertEquals(previousBottom, top);
                    previousBottom = bottom;
                }
            }
        }
    }

    /**
     * Every pixel of the grid is fully covered, even by anti-aliased fills: with every edge a whole
     * pixel, no boundary pixel is shared between two partial covers.
     */
    @Test
    public void snappedRowsLeaveNoPartlyCoveredPixel() {
        for (float offset : OFFSETS) {
            for (float spacing : LINE_SPACINGS) {
                float[] edges = new float[31];
                for (int i = 0; i < edges.length; i++)
                    edges[i] = GridPixels.snap(offset) + GridPixels.rowEdge(8f, spacing, i);
                for (int pixel = (int) edges[0]; pixel < (int) edges[edges.length - 1]; pixel++)
                    assertEquals("offset " + offset + " spacing " + spacing + " pixel " + pixel,
                        1.0, antiAliasedCoverage(edges, pixel), 1e-9);
            }
        }
    }

    /**
     * What the snap is for: the same integer row edges moved by a fractional scroll offset leave
     * each row boundary on a pixel that two fills only partly cover, and their anti-aliased edges
     * composite to less than full coverage there — the hairline of ground between rows.
     */
    @Test
    public void unsnappedOffsetLeavesAHairlineAtEveryRowBoundary() {
        float offset = -12.37f;
        float[] edges = new float[11];
        for (int i = 0; i < edges.length; i++) edges[i] = offset + GridPixels.rowEdge(8f, 36f, i);
        for (int i = 1; i < edges.length - 1; i++) {
            int boundaryPixel = (int) Math.floor(edges[i]);
            assertTrue(antiAliasedCoverage(edges, boundaryPixel) < 0.99);
        }
    }

    /**
     * Background rects of differently coloured runs, laid the way the renderer lays them (one
     * merged rect per run, edges from the shared functions), cover every pixel of the whole cell
     * grid exactly once, for fractional cell widths, line spacings and offsets alike.
     */
    @Test
    public void backgroundRunsCoverTheWholeGridExactlyOnce() {
        final int columns = 23;
        final int rows = 9;
        final float[] widths = { 18f, 17.3f, 11.65f };
        final float[] horizontalOffsets = { 0f, 5.65f, 3.5f };
        for (float width : widths) {
            for (float horizontalOffset : horizontalOffsets) {
                for (float spacing : LINE_SPACINGS) {
                    for (float offset : OFFSETS) {
                        int left = GridPixels.columnEdge(horizontalOffset, width, 0);
                        int right = GridPixels.columnEdge(horizontalOffset, width, columns);
                        int top = GridPixels.snap(offset) + GridPixels.rowEdge(7.4f, spacing, 0);
                        int bottom = GridPixels.snap(offset)
                            + GridPixels.rowEdge(7.4f, spacing, rows);
                        int[][] hits = new int[bottom - top][right - left];
                        for (int row = 0; row < rows; row++) {
                            int rowTop = GridPixels.snap(offset)
                                + GridPixels.rowEdge(7.4f, spacing, row);
                            int rowBottom = GridPixels.snap(offset)
                                + GridPixels.rowEdge(7.4f, spacing, row + 1);
                            // Runs of varying length per row, so run boundaries differ row to row.
                            int start = 0;
                            int run = 1 + row % 4;
                            while (start < columns) {
                                int end = Math.min(columns, start + run);
                                int runLeft = GridPixels.columnEdge(horizontalOffset, width, start);
                                int runRight = GridPixels.columnEdge(horizontalOffset, width, end);
                                for (int y = rowTop; y < rowBottom; y++)
                                    for (int x = runLeft; x < runRight; x++)
                                        hits[y - top][x - left]++;
                                start = end;
                                run = run % 5 + 1;
                            }
                        }
                        for (int[] line : hits)
                            for (int count : line)
                                assertEquals(1, count);
                    }
                }
            }
        }
    }

    /**
     * How much of pixel row {@code pixel} the fills between consecutive {@code edges} cover
     * together, each fill anti-aliased (covering a pixel by the fraction of it inside the fill)
     * and composited source-over: one minus the product of what each leaves uncovered.
     */
    private static double antiAliasedCoverage(float[] edges, int pixel) {
        double uncovered = 1.0;
        for (int i = 0; i < edges.length - 1; i++) {
            double inside = Math.min(edges[i + 1], pixel + 1.0) - Math.max(edges[i], pixel);
            uncovered *= 1.0 - Math.max(0.0, Math.min(1.0, inside));
        }
        return 1.0 - uncovered;
    }
}
