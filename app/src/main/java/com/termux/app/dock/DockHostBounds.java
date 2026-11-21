package com.termux.app.dock;

/**
 * The dock's glass host's own numbers inside the accessory stack, as the activity lays the host
 * out: how far its sheet starts below the stack's top, and the margin it keeps from each side.
 * Pure, so the activity and the per-rest answer ({@code RestCards}) read one formula.
 */
public final class DockHostBounds {

    private DockHostBounds() {}

    /**
     * How far the dock's sheet starts below the top of the accessory stack: the band of a bottom
     * status bar that keeps a sheet of its own ({@code ownSheetStatusBandPx}, 0 while the bar is
     * a band of the dock's plank), the band of page dashes a design floats above its slab
     * ({@link DockLayout#slabTopInsetPx}), and the lane slack the stack is laid out taller by.
     */
    public static int topInsetPx(int ownSheetStatusBandPx, int slabTopInsetPx, int laneSlackPx) {
        return ownSheetStatusBandPx + slabTopInsetPx + laneSlackPx;
    }

    /**
     * The margin the host keeps from each side of the stack: the Floating card's inset while the
     * dock is placed as the card ({@link DockPlacement#floats}), none while it is flush.
     */
    public static int sideMarginPx(boolean dockFloats, int horizontalInsetPx) {
        return dockFloats ? horizontalInsetPx : 0;
    }
}
