package com.termux.app.controlbar;

/**
 * Where the control bar stands (#424), pure, the one owner of its bottom number: the bar sits on
 * the accessory stack (the keyboard, when it is up), and on Android's navigation (the gesture area,
 * or the three buttons) wherever the stack does not already reach over it. The tiling desktop's
 * Workspaces end above the bar by layout, so they read this number only through the bar.
 */
public final class ControlBarPlacement {

    /** One row's height, in dp. */
    public static final float ROW_DP = 44f;
    /** The card's padding above the top row and below the bottom one, in dp. */
    public static final float PAD_DP = 4f;
    /** The bar's height: two rows and its padding, in dp. */
    public static final float HEIGHT_DP = 2 * ROW_DP + 2 * PAD_DP;
    /** The air between the bar and the screen's sides, in dp. */
    public static final float SIDE_DP = 6f;
    /** The air between the bar and what it stands on, in dp. */
    public static final float GAP_DP = 6f;

    private ControlBarPlacement() {}

    /**
     * The bar's bottom margin over the accessory stack's top.
     *
     * @param navigationPx how far Android's navigation reaches up from the screen's bottom
     * @param stackPx the accessory stack's height under the bar (0 when it is gone)
     * @param gapPx the bar's own air below it
     */
    public static int bottomGapPx(int navigationPx, int stackPx, int gapPx) {
        return Math.max(0, gapPx) + Math.max(0, Math.max(0, navigationPx) - Math.max(0, stackPx));
    }

    /**
     * How far the bar's top stands above the screen's bottom: the stack, the bar's bottom gap
     * ({@link #bottomGapPx}) and the bar. A page under the bar (Home's grid, any wall page) ends
     * at least this far up, so nothing tappable lies under it.
     */
    public static int topInsetPx(int navigationPx, int stackPx, int gapPx, int heightPx) {
        return Math.max(0, stackPx) + bottomGapPx(navigationPx, stackPx, gapPx) + Math.max(0, heightPx);
    }

    /** A page's bottom inset {@code pageInsetPx} with the bar standing in it: never under the bar. */
    public static int pageInsetPx(int pageInsetPx, int barTopInsetPx) {
        return Math.max(Math.max(0, pageInsetPx), Math.max(0, barTopInsetPx));
    }
}
