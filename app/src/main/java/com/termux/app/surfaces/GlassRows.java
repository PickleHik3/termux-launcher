package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;

/**
 * The one row set every glass card wears (ADR 0017, the editor-cards spec §3), in touch order:
 * Look, Frost, Opacity, Tint, Colour, Corners, Depth, Edge; then, under More, Grain and Rim
 * before the card's own rows. Also the rows that read and write the chips' grid cell directly
 * and their units. Pure: no views, no Context.
 */
public final class GlassRows {

    private GlassRows() {}

    /** The glass rows over More, Depth and Edge only where the device draws the glass edge. */
    @NonNull
    public static EditorRows.CardRows glass(@NonNull EditorRows.CardRows rows,
                                            @NonNull Offers offers) {
        rows.tier(1, Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT, Row.TINT_HUE, Row.CORNERS);
        if (offers.glassEdge)
            rows.tier(1, Row.DEPTH, Row.GLASS_EDGE);
        return rows.tier(3, Row.SECTION_MORE, Row.GRAIN, Row.RIM);
    }

    /** A glass card: the row set, then {@code own} under More. */
    @NonNull
    public static EditorRows.CardRows card(@NonNull Offers offers, @NonNull Row... own) {
        return glass(new EditorRows.CardRows(), offers).tier(3, own);
    }

    /**
     * The grid property a row reads and writes at the chips' cell, or null for a row that keeps
     * its own store. Frost, Opacity and Tint go through the surface properties instead.
     */
    @Nullable
    public static LookGrid.Property cellProperty(@NonNull Row row) {
        switch (row) {
            case CORNERS: return LookGrid.Property.CORNERS;
            case DEPTH: return LookGrid.Property.DEPTH;
            case GLASS_EDGE: return LookGrid.Property.EDGE;
            case GRAIN: return LookGrid.Property.GRAIN;
            case RIM: return LookGrid.Property.RIM;
            case VIBRANCY: return LookGrid.Property.VIBRANCY;
            case MARGIN: return LookGrid.Property.MARGIN;
            default: return null;
        }
    }

    /**
     * Whether a row is set for all glass whatever the What chip names: Margin and Vibrancy have
     * no element column, so they read and write the Where chip's all-glass cell and say so.
     */
    public static boolean allGlassOnly(@NonNull Row row) {
        return row == Row.MARGIN || row == Row.VIBRANCY;
    }

    /** The grid property whose cell a row's dot and Reset this row speak of, or null. */
    @Nullable
    public static LookGrid.Property gridProperty(@NonNull Row row) {
        switch (row) {
            case FROST: return LookGrid.Property.FROST;
            case OPACITY: return LookGrid.Property.OPACITY;
            case TINT: return LookGrid.Property.TINT;
            default: return cellProperty(row);
        }
    }

    /** A cell's value as the row shows it: Depth's bend as its slider position. */
    public static int toRow(@NonNull Row row, int cell) {
        switch (row) {
            case DEPTH: return depthForBend(cell);
            case CORNERS: return Math.max(0, cell);
            default: return cell;
        }
    }

    /** A row's value as the cell holds it: Depth's slider position as its bend. */
    public static int toCell(@NonNull Row row, int value) {
        return row == Row.DEPTH ? GlassEdgeControls.bendForDepth(value) : value;
    }

    /** The Depth position that writes {@code bend}: the lowest, as a Depth move would land. */
    static int depthForBend(int bend) {
        for (int t = GlassEdgeControls.MIN; t <= GlassEdgeControls.MAX; t++)
            if (GlassEdgeControls.bendForDepth(t) >= bend) return t;
        return GlassEdgeControls.MAX;
    }

    // ------------------------------------------------------------------ a Look at one cell

    /**
     * Each Look's Frost (blur dp), Opacity and Tint, in the slider's order (Clear, Mist, Tint,
     * Solid): what picking it writes into the chips' cell on an element's card.
     */
    static final int[][] LOOK_CELLS = {{2, 14, 0}, {8, 34, 0}, {8, 34, 40}, {0, 78, 0}};

    /** The Frost stop, Opacity and Tint a Look stop writes; null for Custom. */
    @Nullable
    public static int[] lookCell(int stop) {
        if (stop < 0 || stop >= LOOK_CELLS.length) return null;
        int[] look = LOOK_CELLS[stop];
        return new int[] {FrostStops.nearestStopByBlur(look[0]), look[1], look[2]};
    }

    /** The Look stop a cell's Frost stop, Opacity and Tint wear; Custom where none matches. */
    public static int lookStopFor(int frostStop, int opacity, int tint) {
        for (int i = 0; i < LOOK_CELLS.length; i++) {
            int[] cell = lookCell(i);
            if (cell[0] == frostStop && cell[1] == opacity && cell[2] == tint) return i;
        }
        return AppearanceLooks.CUSTOM_STOP;
    }
}
