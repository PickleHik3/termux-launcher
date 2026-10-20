package com.termux.view;

/**
 * Where the grid's cell boxes land on the pixel grid, the way kitty lays them: every edge is a
 * whole pixel, the edge between two rows (or two columns) is one number both of them use, and a
 * move of the whole grid is a whole number of pixels.
 *
 * <p>A cell background is a solid fill that has to tile with its neighbours. Over the glass panes
 * whatever a fill leaves uncovered is not a slightly lighter shade of the terminal's own ground but
 * the wallpaper, so a boundary pixel that two fills each only partly cover — two anti-aliased edges
 * meeting on a fraction, which is what an independently rounded edge or a fractional translation of
 * the grid makes — composites to less than full coverage and reads as a coloured hairline between
 * rows. Edges from {@link #rowEdge} under a translation from {@link #snap} never meet on a fraction.
 */
final class GridPixels {

    private GridPixels() {
    }

    /**
     * A translation of the whole grid — a smooth scroll's pixel offset, a travel's displacement —
     * on whole pixels. The grid moves a pixel at a time, which the eye cannot tell from a smooth
     * move, and every cell edge it carries stays on the pixel it was computed for.
     */
    static int snap(float offsetPx) {
        return Math.round(offsetPx);
    }

    /**
     * The top of row {@code row} of a grid whose first row starts at {@code originPx}, and so also
     * the bottom of the row above it: one expression for both, so row n's bottom is exactly row
     * n+1's top whatever fraction {@code lineSpacingPx} or the origin carries.
     */
    static int rowEdge(float originPx, float lineSpacingPx, int row) {
        return Math.round(originPx + row * lineSpacingPx);
    }

    /** The left edge of column {@code column}, and the right edge of the one before it. */
    static int columnEdge(float originPx, float cellWidthPx, int column) {
        return BoxGeometry.edge(originPx, cellWidthPx, column);
    }
}
