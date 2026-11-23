package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.LookGrid;

/**
 * The Appearance block every glass card wears (appearance-cards overhaul §2). Also the rows that read and write the chips' grid cell directly
 * and their units. Pure: no views, no Context.
 */
public final class GlassRows {

    private GlassRows() {}

    /**
     * The Appearance section every glass card wears (appearance-cards overhaul §2), under its
     * heading, in {@code tier}: Presets, Opacity, Frost, Tint with its colour swatches
     * under it, Corner radius, Depth and Shine. Depth and Shine are always listed;
     * where the phone cannot draw them they read "Not on this phone." and stay put.
     */
    @NonNull
    public static EditorRows.CardRows appearance(@NonNull EditorRows.CardRows rows,
                                                 @NonNull Offers offers, int tier) {
        rows.tier(tier, Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.FROST,
            Row.TINT, Row.TINT_HUE, Row.CORNERS, Row.DEPTH, Row.SHINE);
        // Out for a setting the user can change: the way to it stands under them.
        return offers.refractionLink ? rows.tier(tier, Row.LINK_GLASS_REFRACTION) : rows;
    }

    /** Whether {@code row} is drawn only where the phone draws the glass edge. */
    public static boolean needsGlassEdge(@NonNull Row row) {
        return row == Row.SHINE || row == Row.DEPTH;
    }

    /**
     * The grid property a row reads and writes at the chips' cell, or null for a row that keeps
     * its own store. Frost, Opacity and Tint go through the surface properties instead.
     */
    @Nullable
    public static LookGrid.Property cellProperty(@NonNull Row row) {
        switch (row) {
            case CORNERS: return LookGrid.Property.CORNERS;
            case TINT_HUE: return LookGrid.Property.TINT_HUE;
            case DEPTH: return LookGrid.Property.DEPTH;
            case SHINE: return LookGrid.Property.SHINE;
            case MARGIN: return LookGrid.Property.MARGIN;
            default: return null;
        }
    }

    /**
     * Whether a row is set for all glass whatever the What chip names: Margin, Depth and Shine have
     * no element column, so they read and write the Where chip's all-glass cell and say so.
     */
    public static boolean allGlassOnly(@NonNull Row row) {
        return row == Row.MARGIN || row == Row.DEPTH || row == Row.SHINE;
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

    /** The Depth position that writes {@code bend}, as the editor's own Depth reads it. */
    static int depthForBend(int bend) {
        return GlassEdgeControls.depthFor(bend);
    }

    // ------------------------------------------------------------------ a Look at one cell

    /**
     * The Frost stop, Opacity and Tint a Look stop writes into the chips' cell, read from the
     * shipped Looks ({@link SurfacePresets#shippedLook}, one owner); null for Custom.
     */
    @Nullable
    public static int[] lookCell(int stop) {
        SurfacePresets.ShippedLook look = SurfacePresets.shippedLook(stop);
        return look == null ? null : new int[] {look.frostStop, look.opacity, look.tint};
    }

    /** The Look stop a cell's Frost stop, Opacity and Tint wear; Custom where none matches. */
    public static int lookStopFor(int frostStop, int opacity, int tint) {
        for (int i = 0; i < AppearanceLooks.CUSTOM_STOP; i++) {
            int[] cell = lookCell(i);
            if (cell != null && cell[0] == frostStop && cell[1] == opacity && cell[2] == tint) return i;
        }
        return AppearanceLooks.CUSTOM_STOP;
    }
}
