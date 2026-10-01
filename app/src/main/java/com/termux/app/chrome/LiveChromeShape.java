package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.ChromeShape;
import com.termux.app.place.ChromeShape.Card;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.ChromeShapeModel;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import java.util.List;

/**
 * The live chrome's door to the one shape model (ADR 0007): pixels in, the answers an outline
 * provider, a glass edge or a pane needs out. Nothing here decides a corner; it only asks
 * {@link ChromeShapeModel} and translates the answer into a view's own coordinates.
 *
 * <p>A Docked card (the joined frame, or the top or bottom edge card) is clipped once, as a
 * single sheet; its outer corners are square (the screen rounds them) and an edge card's inner
 * corners round. The chrome is several views, so each view clips to its own intersection with
 * that card: the card's rounded rect laid over the view at the offset the model puts it at
 * ({@link #outlineOf}). A view's outline is convex and the card's is too, so the intersection
 * is exactly what one clip would have cut.
 *
 * <p>Pure: no view, no context.
 */
public final class LiveChromeShape {

    private LiveChromeShape() {}

    /** The radius assumed for the device's screen when it reports none (no longer rounds the frame). */
    public static final float FALLBACK_SCREEN_RADIUS_DP = 28f;
    /** The most air between Floating pane cards. */
    public static final float PANE_GAP_CAP_DP = 24f;
    /** The least gap between split panes: the strip the divider is dragged by. */
    public static final float PANE_GAP_FLOOR_DP = 10f;
    /** How thick a Docked divider line draws. */
    public static final float DIVIDER_DP = 1f;

    /** The device's screen radius in px: what it reported, or 28dp when it reports none. */
    public static float screenRadiusPx(float reportedPx, float density) {
        return reportedPx > 0f ? reportedPx : FALLBACK_SCREEN_RADIUS_DP * density;
    }

    /**
     * The shape of the whole chrome for one frame, in pixels.
     *
     * @param layout    already {@code withKeyboardShown} the way the keyboard really stands
     * @param cornersPx Corners: every Floating card's radius, and the Docked insert's
     * @param marginPx  Margin: the air round Floating cards, the gutter round the Docked insert
     */
    @NonNull
    public static ChromeShape of(@NonNull PlaceLayout layout, @NonNull LayoutStyle style,
                                 int widthPx, int heightPx,
                                 @NonNull ChromeShapeModel.Thickness thickness, float cornersPx,
                                 float marginPx, float screenRadiusPx, float density,
                                 int paneCount, @NonNull ChromeShapeModel.SplitAxis axis,
                                 float splitHalfWidthPx,
                                 @Nullable ChromeShape.Box floatingKeyboardBox) {
        return ChromeShapeModel.shape(ChromeShapeModel.input(layout, style, widthPx, heightPx,
                thickness)
            .corners(cornersPx).margin(marginPx).screenRadius(screenRadiusPx)
            .paneGapCap(PANE_GAP_CAP_DP * density)
            .paneGapFloor(PANE_GAP_FLOOR_DP * density).dividerThickness(DIVIDER_DP * density)
            .panes(paneCount, axis).splitHalfWidth(splitHalfWidthPx)
            .floatingKeyboardBox(floatingKeyboardBox).build());
    }

    /**
     * A view's clip: how far the card's round rect reaches past the view on each side, and its
     * radius. The provider lays it over the view's live bounds, so it needs no size here, and the
     * bounds then cut it wherever the card goes on beyond the view.
     */
    public static final class Clip {
        public final int reachLeft;
        public final int reachTop;
        public final int reachRight;
        public final int reachBottom;
        /** The largest of the four corner radii. */
        public final float radius;
        public final float topLeft;
        public final float topRight;
        public final float bottomRight;
        public final float bottomLeft;

        Clip(int reachLeft, int reachTop, int reachRight, int reachBottom, float topLeft,
             float topRight, float bottomRight, float bottomLeft) {
            this.reachLeft = reachLeft;
            this.reachTop = reachTop;
            this.reachRight = reachRight;
            this.reachBottom = reachBottom;
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
            this.radius = Math.max(Math.max(topLeft, topRight), Math.max(bottomRight, bottomLeft));
        }
    }

    /** A card that is the view's own bounds, rounded: what a Floating card's clip comes to. */
    @NonNull
    public static Clip cardClip(float radiusPx) {
        float r = Math.max(0f, radiusPx);
        return new Clip(0, 0, 0, 0, r, r, r, r);
    }

    /**
     * The clip for a view standing where {@code ids} stand in the model: its pieces' union, with
     * their card's box and corners around it. Null when none of them is shown. Under Docked the
     * card is the joined frame (square) or an edge card (square at the screen, rounded inside),
     * so every view's clip is its slice of that card; under
     * Floating it is the view's own card, and the clip is the view's bounds rounded.
     */
    @Nullable
    public static Clip outlineOf(@NonNull ChromeShape shape, @NonNull List<PieceId> ids) {
        Piece first = null;
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (PieceId id : ids) {
            Piece piece = shape.piece(id);
            if (piece == null) continue;
            if (first == null) first = piece;
            l = Math.min(l, piece.box.left);
            t = Math.min(t, piece.box.top);
            r = Math.max(r, piece.box.right);
            b = Math.max(b, piece.box.bottom);
        }
        if (first == null) return null;
        Card card = shape.cardOf(first);
        if (card == null) return null;
        return new Clip(Math.round(l - card.box.left), Math.round(t - card.box.top),
            Math.round(card.box.right - r), Math.round(card.box.bottom - b),
            card.corners.topLeft, card.corners.topRight, card.corners.bottomRight,
            card.corners.bottomLeft);
    }

    /**
     * The edges of the views standing where {@code ids} stand that draw rim light, the bend and
     * the containing stroke, as {@link ChromeEdgeRule} bits. Under a joined Docked frame none does:
     * joins, screen edges and the edges facing the gutter are plain, and the insert's own rounded
     * edge wears the rim. Under Docked edge cards the edge facing the insert does, and under
     * Floating every edge a card shows.
     * Each edge is the outermost piece's own answer, so a join inside the set never counts.
     */
    public static int rimEdges(@NonNull ChromeShape shape, @NonNull List<PieceId> ids) {
        int bits = ChromeEdgeRule.NONE;
        for (Edge edge : new Edge[] {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT}) {
            Piece outer = null;
            for (PieceId id : ids) {
                Piece piece = shape.piece(id);
                if (piece == null) continue;
                if (outer == null || isOuter(piece, outer, edge)) outer = piece;
            }
            if (outer != null && outer.drawsRim(edge)) bits |= ChromeEdgeRule.edgeBit(edge);
        }
        return bits;
    }

    /**
     * How much air the opening keeps from whatever lies across {@code edge} of it: the nearest
     * piece's edge facing it, or the screen's edge where none stands. Under both Styles it is
     * Margin: the Floating pane card's inset, the Docked insert's gutter. {@code widthPx} and
     * {@code heightPx} are the frame the shape was made for.
     */
    public static float openingInsetPx(@NonNull ChromeShape shape, @NonNull Edge edge,
                                       float widthPx, float heightPx) {
        if (!shape.opening().ownsRim) return 0f;
        ChromeShape.Box open = shape.opening().box;
        float eps = 1e-3f;
        boolean vertical = !edge.isOnSide();
        float neighbour = edge == Edge.TOP || edge == Edge.LEFT ? 0f
            : (vertical ? heightPx : widthPx);
        for (Piece piece : shape.pieces()) {
            if (piece.overlay) continue;
            ChromeShape.Box b = piece.box;
            boolean along = vertical
                ? b.right > open.left + eps && b.left < open.right - eps
                : b.bottom > open.top + eps && b.top < open.bottom - eps;
            if (!along) continue;
            switch (edge) {
                case TOP:
                    if (b.bottom <= open.top + eps) neighbour = Math.max(neighbour, b.bottom);
                    break;
                case BOTTOM:
                    if (b.top >= open.bottom - eps) neighbour = Math.min(neighbour, b.top);
                    break;
                case LEFT:
                    if (b.right <= open.left + eps) neighbour = Math.max(neighbour, b.right);
                    break;
                default:
                    if (b.left >= open.right - eps) neighbour = Math.min(neighbour, b.left);
                    break;
            }
        }
        switch (edge) {
            case TOP: return Math.max(0f, open.top - neighbour);
            case BOTTOM: return Math.max(0f, neighbour - open.bottom);
            case LEFT: return Math.max(0f, open.left - neighbour);
            default: return Math.max(0f, neighbour - open.right);
        }
    }

    /** The refraction seams of the same views: every edge that is not a rim. */
    public static int seamEdges(@NonNull ChromeShape shape, @NonNull List<PieceId> ids) {
        return ChromeEdgeRule.ALL & ~rimEdges(shape, ids);
    }

    private static boolean isOuter(@NonNull Piece piece, @NonNull Piece than, @NonNull Edge edge) {
        switch (edge) {
            case TOP: return piece.box.top < than.box.top;
            case BOTTOM: return piece.box.bottom > than.box.bottom;
            case LEFT: return piece.box.left < than.box.left;
            case RIGHT:
            default: return piece.box.right > than.box.right;
        }
    }
}
