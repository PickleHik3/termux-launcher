package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where the control bar stands (#424, #427), pure, the one owner of its bottom number: the bar sits
 * on the accessory stack (the keyboard, when it is up), and on Android's navigation (the gesture
 * area, or the three buttons) wherever the stack does not already reach over it. The tiling
 * desktop's Workspaces end above the bar (beside the rail) by layout, so they read this number only
 * through the bar.
 *
 * <p>The stack's drawn top is the stack's own number: the stack stays laid out as tall as the
 * keyboard-up stack while the keyboard is down, and its one writer translates it down
 * ({@code StackScene#keyboardTranslationPx}, written as the container's translation). The bar is laid
 * out at the rest's answer ({@link #settledBottomPx}) and, on every frame the stack is drawn, moved
 * by {@link #rideTyPx} from that same translation, so it rides on the keyboard with no lag and
 * nothing is laid out while the keyboard moves.
 */
public final class ControlBarPlacement {

    /** One row's height, in dp. */
    public static final float ROW_DP = 44f;
    /** The card's padding above the top row and below the bottom one, in dp. */
    public static final float PAD_DP = 4f;
    /** The bar's height: two rows and its padding, in dp. */
    public static final float HEIGHT_DP = 2 * ROW_DP + 2 * PAD_DP;
    /** The margin's default, in dp: the air between the bar and the screen's sides and what it stands on. */
    public static final int DEFAULT_MARGIN_DP = 6;
    /** The margin's range, in dp ({@code bar.margin}). */
    public static final int MIN_MARGIN_DP = 0;
    public static final int MAX_MARGIN_DP = 24;

    /** One row and its padding, in dp: the one-row bar's height and the rail's width. */
    public static final float ONE_ROW_DP = ROW_DP + 2 * PAD_DP;

    private ControlBarPlacement() {}

    /** The side the rail stands down in landscape ({@code bar.rail_side}). */
    public enum RailSide {
        RIGHT,
        LEFT;

        /** The stored value, read leniently: anything but {@code left} is the right side. */
        @NonNull
        public static RailSide fromPreference(@Nullable String value) {
            return "left".equals(value) ? LEFT : RIGHT;
        }
    }

    /** {@code marginDp} held inside its range. */
    public static int clampMarginDp(int marginDp) {
        return Math.max(MIN_MARGIN_DP, Math.min(MAX_MARGIN_DP, marginDp));
    }

    /**
     * Whether a margin stands the bar flush: full width, on Android's navigation, with square
     * bottom corners.
     */
    public static boolean flush(int marginDp) {
        return clampMarginDp(marginDp) == 0;
    }

    /** How thick {@code form} stands: the rows' height, or the rail's width, in dp. */
    public static float thicknessDp(@NonNull ControlBarForm form) {
        return form == ControlBarForm.TWO_ROWS ? HEIGHT_DP : ONE_ROW_DP;
    }

    /**
     * The A–Z letters' row in the card, in dp: Home's A–Z placed at the bottom under Terl joins
     * the bar's glass as a row under its tools (Terl decisions 2026-11-28), inside its padding.
     */
    public static final float AZ_ROW_DP = 24f;

    /**
     * How thick {@code form} stands when it carries Home's A–Z as a row of its card
     * ({@code azRow}): a row's form grows by the letters' row; a rail never carries it.
     */
    public static float thicknessDp(@NonNull ControlBarForm form, boolean azRow) {
        return thicknessDp(form) + (carriesAzRow(form, azRow) ? AZ_ROW_DP : 0f);
    }

    /** Whether {@code form} carries the letters' row it is asked to: a rail stands on a side, never. */
    public static boolean carriesAzRow(@NonNull ControlBarForm form, boolean azRow) {
        return azRow && !form.isRail();
    }

    /**
     * How tall the stack stands drawn above the screen's bottom: its laid-out height less how far
     * its writer translates it down (negative: lifted, as the phone's keyboard lifts it).
     */
    public static float drawnStackPx(int laidOutStackPx, float translationPx) {
        return Math.max(0f, Math.max(0, laidOutStackPx) - translationPx);
    }

    /** The bar's bottom above the screen's bottom on a stack drawn {@code drawnStackPx} tall. */
    public static float bottomAbovePx(int navigationPx, float drawnStackPx, int gapPx) {
        float stack = Math.max(0f, drawnStackPx);
        return stack + Math.max(0, gapPx) + Math.max(0f, Math.max(0, navigationPx) - stack);
    }

    /**
     * Where the bar is laid out: its bottom margin over the screen's bottom on the stack at its
     * rest ({@code restTranslationPx}, the stack's writer's translation at rest: the down
     * keyboard's share, else 0).
     */
    public static int settledBottomPx(int navigationPx, int laidOutStackPx, float restTranslationPx,
                                      int gapPx) {
        return Math.round(bottomAbovePx(navigationPx, drawnStackPx(laidOutStackPx, restTranslationPx),
            gapPx));
    }

    /**
     * The bar's translation on a frame whose stack is translated {@code translationPx}: from where it
     * is laid out ({@code laidOutBottomPx} above the screen's bottom) to where that stack carries
     * it. 0 at the rest it was laid out for.
     */
    public static float rideTyPx(int laidOutBottomPx, int navigationPx, int laidOutStackPx,
                                 float translationPx, int gapPx) {
        return laidOutBottomPx - bottomAbovePx(navigationPx, drawnStackPx(laidOutStackPx,
            translationPx), gapPx);
    }

    /**
     * The camera's keep-out on the rail's edge: Automatic keeps what Android reports and the room
     * around it ({@code cutout.inset}); Custom keeps that room even where Android reports nothing;
     * Off keeps nothing.
     *
     * @param cutoutPx how far Android's cutout reaches in from that edge
     */
    public static int cutoutClearPx(boolean off, boolean custom, int cutoutPx, int roomPx) {
        if (off) return 0;
        int cutout = Math.max(0, cutoutPx);
        int room = Math.max(0, roomPx);
        if (cutout > 0) return cutout + room;
        return custom ? room : 0;
    }

    /**
     * The rail's margin from its screen edge: its own air, plus whatever of Android's navigation
     * and the camera's keep-out that edge holds which the host does not already stand clear of.
     *
     * @param navigationPx Android's navigation on that edge (three buttons in landscape)
     * @param hostEdgeGapPx how far the host's edge already stands in from the screen's
     */
    public static int railEdgeMarginPx(int sidePx, int navigationPx, int cutoutClearPx,
                                       int hostEdgeGapPx) {
        int keepOut = Math.max(Math.max(0, navigationPx), Math.max(0, cutoutClearPx));
        return Math.max(0, sidePx) + Math.max(0, keepOut - Math.max(0, hostEdgeGapPx));
    }

    /**
     * Where the Workspaces stop drawing while edit mode lifts the bar {@code editLiftPx} above its
     * strip (a translation the layout does not see): their own height less the lift, so no
     * window shows through the lifted bar's glass. The windows keep their size (nothing is laid
     * out again, no terminal resizes); 0 for no lift means no clip ({@code -1}).
     */
    public static int liftedClipBottomPx(int workspacesHeightPx, int editLiftPx) {
        if (editLiftPx <= 0) return -1;
        return Math.max(0, workspacesHeightPx - editLiftPx);
    }

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

    /**
     * How far a stack {@code heightPx} tall keeps room above what it draws: the top of its highest
     * shown content, the rows over the keyboard ({@code rowsTopPx}) or the keyboard's column
     * ({@code keyboardTopPx}), each in the stack's own coordinates as drawn
     * ({@link StackDrawnTop}) and negative when not shown, held inside the stack. Rows drawn at or
     * below the keyboard's top are under it (Home's keyboard cover), so the bar stands on the
     * keyboard and never on their room. 0 when nothing is shown, so the bar stands on the stack's
     * top. Read on every frame the bar rides: it allocates nothing.
     */
    public static int emptyTopPx(int heightPx, int rowsTopPx, int keyboardTopPx) {
        int top = rowsTopPx < 0 ? keyboardTopPx
            : keyboardTopPx < 0 ? rowsTopPx : Math.min(rowsTopPx, keyboardTopPx);
        if (top < 0) return 0;
        return Math.min(Math.max(0, heightPx), top);
    }

    /** A page's bottom inset {@code pageInsetPx} with the bar standing in it: never under the bar. */
    public static int pageInsetPx(int pageInsetPx, int barTopInsetPx) {
        return Math.max(Math.max(0, pageInsetPx), Math.max(0, barTopInsetPx));
    }
}
