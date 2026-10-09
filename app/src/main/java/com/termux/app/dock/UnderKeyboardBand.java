package com.termux.app.dock;

/**
 * The sheet the bands a place stands under the keyboard wear ({@code accessory_under_keyboard_stack}):
 * the dock's own material, whether the keyboard is up, down or switched off.
 *
 * <p>Under Floating it is a card of its own below the keyboard: it takes the Corners radius, the
 * dock's side inset, and the air a capsule keeps above the navigation area — the gesture pill or
 * the button bar, which the root's own inset already lays the whole stack above. Under Docked it
 * joins the frame flush below the keyboard (SPEC 3.7): no air above it, none under it, no side
 * inset and square corners, so every figure but the height is zero.
 *
 * <p>Every figure here is added to the accessory stack's reserved height as well as drawn: the band
 * is exactly as tall as its rows, and the air is the stack's, so the dock's rows over the keyboard
 * are never squeezed by a sheet taller than the room counted for it. Pure arithmetic on a
 * {@link DockLayout}, so it is testable without a view.
 */
public final class UnderKeyboardBand {

    /**
     * The gap between the keyboard and the card under it, in dp: the same gap the Floating
     * keyboard keeps from the dock above it, so the three read as one evenly spaced column.
     */
    public static final int KEYBOARD_GAP_DP = 4;

    private UnderKeyboardBand() {}

    /** {@link #KEYBOARD_GAP_DP} in pixels. */
    public static int keyboardGapPx(float density) {
        return Math.round(Math.max(0f, density) * KEYBOARD_GAP_DP);
    }

    /** The air above the card: between it and the keyboard, or the dock's rows with it down. */
    public static int topGapPx(DockLayout dock) {
        return dock.capsule ? keyboardGapPx(dock.density) : 0;
    }

    /**
     * How far the card's bottom stands clear of the navigation area: the gap a floating capsule
     * keeps under itself ({@link DockLayout#capsuleBottomGapPx}) under Floating. Docked it runs
     * flush to the edge and keeps none.
     */
    public static int navClearancePx(DockLayout dock) {
        return dock.capsule ? Math.max(0, dock.capsuleBottomGapPx) : 0;
    }

    /**
     * The part of {@link #navClearancePx} the card keeps under itself, given the gap the stack
     * already keeps under everything it holds ({@code ChromePolicy.bottomEdgeGapPx}). Floating's
     * stack keeps the capsule gap as its own margin, so the card adds nothing; Docked's stands flush
     * on the edge, so the card carries the whole of it. Never both, never neither.
     */
    public static int bottomAirPx(DockLayout dock, int stackEdgeGapPx) {
        return Math.max(0, navClearancePx(dock) - Math.max(0, stackEdgeGapPx));
    }

    /**
     * All the air the card adds to the stack's height: above it and under it. Zero while no band
     * stands under the keyboard, which is every arrangement that shipped before the slot.
     */
    public static int airPx(DockLayout dock, int stackEdgeGapPx, boolean bandsUnderKeyboard) {
        if (!bandsUnderKeyboard) return 0;
        return topGapPx(dock) + bottomAirPx(dock, stackEdgeGapPx);
    }

    /** The card's sides: the dock's own inset in the active style. */
    public static int sideInsetPx(DockLayout dock) {
        return Math.max(0, dock.horizontalInsetPx);
    }

    /**
     * The card's corners: the dock's capsule radius — the configured Corners value, or the
     * follow-the-style radius — clamped to a true half-capsule of the card's own height.
     */
    public static float cornerRadiusPx(DockLayout dock, int sheetHeightPx) {
        return dock.capsule ? dock.capsuleCornerRadiusPx(Math.max(0, sheetHeightPx)) : 0f;
    }
}
