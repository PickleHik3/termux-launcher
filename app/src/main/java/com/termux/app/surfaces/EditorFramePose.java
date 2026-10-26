package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout;

/**
 * Where the Appearance editor's frame stands, where the half line under it stands, and how tall
 * the card's two detents are, as pure arithmetic in pixels of one window (appearance-and-settings-
 * rework §7; wireframes 01 and 02). Everything is a function of the window, its insets, the font
 * scale (through the half line's height) and where the card's top edge is; the editor applies the
 * answer as a scale and a translation, so a card moving never lays anything out.
 *
 * <ul>
 *   <li><b>At rest</b> the whole launcher fits between the top line ({@link #TOP_GAP_DP} under
 *   the status bar) and the half line, which sits over the editor bar with a gap each side, at
 *   most {@link #REST_MAX_SCALE}.</li>
 *   <li><b>With a card up</b> the whole launcher fits between the top line and the half line over
 *   the card: the frame never slides under the status bar or behind the card, and the half line
 *   never crosses it. The detents are sized from the scale they leave the frame:
 *   {@link #HALF_SCALE} at the half detent, {@link #FULL_SCALE} at the full one. A taller card
 *   (a short window, a large font) shrinks the frame instead of hiding part of it.</li>
 *   <li>A <b>hidden</b> surface keeps a ghost: a band on the edge it would stand on, so it can
 *   still be tapped and shown again.</li>
 * </ul>
 *
 * <p>The frame scales about its top-centre pivot: a container point {@code y} px down stands at
 * {@code containerTop + translationY + y * scale} on screen. "Visible" heights include the bands
 * of the display the frame shows round the container (the status band it reveals, the navigation
 * band under it).</p>
 */
final class EditorFramePose {

    private EditorFramePose() {}

    /** The resting frame's largest scale (wireframe 01: ×0.84). */
    static final float REST_MAX_SCALE = 0.84f;
    /** The frame over a card at its half detent. */
    static final float HALF_SCALE = 0.6f;
    /** The frame over a card at its full detent. */
    static final float FULL_SCALE = 0.44f;
    /** The frame's top line under the status bar, and the air round the half line. */
    static final int TOP_GAP_DP = 16;
    static final int GAP_DP = 8;
    /** The half line's least height, and its label's line height at font scale 1 (label large). */
    static final int HALF_ROW_DP = 16;
    static final int HALF_LABEL_LINE_SP = 20;
    /** The smallest scale the frame is ever given, however short the window. */
    static final float MIN_SCALE = 0.2f;
    /** A ghost band's thickness, in dp. */
    static final int GHOST_DP = 40;

    /** The window round the frame, in px: what every pose is computed from. */
    static final class Window {
        final int heightPx;
        final int topInsetPx;
        final int navInsetPx;
        final float density;
        final float fontScale;
        /** The container's unscaled top in the content view. */
        final int containerTopPx;
        /** The container plus the display bands the frame shows round it. */
        final int visibleHeightPx;
        /** The band above the container the frame shows. */
        final int revealTopPx;
        /** The editor bar's height and its gap over the navigation inset. */
        final int barPx;
        final int footGapPx;

        Window(int heightPx, int topInsetPx, int navInsetPx, float density, float fontScale,
               int containerTopPx, int visibleHeightPx, int revealTopPx, int barPx,
               int footGapPx) {
            this.heightPx = heightPx;
            this.topInsetPx = topInsetPx;
            this.navInsetPx = navInsetPx;
            this.density = density;
            this.fontScale = fontScale;
            this.containerTopPx = containerTopPx;
            this.visibleHeightPx = visibleHeightPx;
            this.revealTopPx = Math.max(0, revealTopPx);
            this.barPx = barPx;
            this.footGapPx = footGapPx;
        }

        int gapPx() {
            return Math.round(GAP_DP * density);
        }

        int halfRowPx() {
            return EditorFramePose.halfRowPx(density, fontScale);
        }

        int topPx() {
            return EditorFramePose.topPx(topInsetPx, density);
        }

        /** The lowest the frame may reach with the half line over the editor bar under it. */
        int restBottomPx() {
            return heightPx - navInsetPx - footGapPx - barPx - gapPx() - halfRowPx() - gapPx();
        }

        /** The lowest the frame may reach with the half line over a card whose top is here. */
        float bottomOverCardPx(float cardTopPx) {
            return cardTopPx - gapPx() - halfRowPx() - gapPx();
        }
    }

    /** A pose: the frame's scale and translation, and the half line's top in the content view. */
    static final class Pose {
        final float scale;
        final float translationX;
        final float translationY;
        final float halfLineTopPx;

        Pose(float scale, float translationY, float halfLineTopPx) {
            this(scale, 0f, translationY, halfLineTopPx);
        }

        Pose(float scale, float translationX, float translationY, float halfLineTopPx) {
            this.scale = scale;
            this.translationX = translationX;
            this.translationY = translationY;
            this.halfLineTopPx = halfLineTopPx;
        }

        /** The frame's visible bottom edge in the content view. */
        float frameBottomPx(@NonNull Window window) {
            return window.topPx() + window.visibleHeightPx * scale;
        }
    }

    /** The half line's height: 16dp, or its label's line where the font scale makes that taller. */
    static int halfRowPx(float density, float fontScale) {
        float dp = Math.max(HALF_ROW_DP, HALF_LABEL_LINE_SP * Math.max(1f, fontScale));
        return (int) Math.ceil(dp * density);
    }

    /** The frame's top line in the content view: {@link #TOP_GAP_DP} under the top inset. */
    static int topPx(int topInsetPx, float density) {
        return topInsetPx + Math.round(TOP_GAP_DP * density);
    }

    /** The scale that fits {@code visiblePx} of launcher between two lines, capped. */
    static float fitScale(int visiblePx, int topPx, float bottomPx, float cap) {
        if (visiblePx <= 0)
            return cap;
        return Math.max(MIN_SCALE, Math.min(cap, (bottomPx - topPx) / (float) visiblePx));
    }

    /** The frame's scale at rest. */
    static float restScale(@NonNull Window window) {
        return fitScale(window.visibleHeightPx, window.topPx(), window.restBottomPx(),
            REST_MAX_SCALE);
    }

    /**
     * The pose with the card's top edge at {@code cardTopPx} (the window's height, or more, for
     * no card): the whole launcher between the top line and the half line, the half line over the
     * card or, with the card low enough, over the editor bar; never larger than at rest.
     */
    @NonNull
    static Pose pose(@NonNull Window window, float cardTopPx) {
        int top = window.topPx();
        float bottom = Math.min(window.restBottomPx(), window.bottomOverCardPx(cardTopPx));
        float scale = fitScale(window.visibleHeightPx, top, bottom, restScale(window));
        float ty = top + window.revealTopPx * scale - window.containerTopPx;
        float halfLineTop = top + window.visibleHeightPx * scale + window.gapPx();
        return new Pose(scale, 0f, ty, halfLineTop);
    }

    /**
     * The top the poses read for a card at its peek detent, whose top edge is at
     * {@code sheetTopPx}: the editor bar stands on the card's header there, so the frame and the
     * half line keep clear of the bar as well (the bar's foot gap and its height above the card).
     */
    static float topOverBar(@NonNull Window window, float sheetTopPx) {
        return sheetTopPx - window.footGapPx - window.barPx;
    }

    /** The pose at rest: no card. */
    @NonNull
    static Pose restPose(@NonNull Window window) {
        return pose(window, Float.MAX_VALUE);
    }

    /**
     * The card's height that leaves the whole frame at {@code scale} above it, the half line and
     * its gaps between them.
     */
    static int sheetPxFor(@NonNull Window window, float scale) {
        int frame = (int) Math.ceil(window.visibleHeightPx * scale);
        return Math.max(0, window.heightPx - window.topPx() - frame - window.gapPx()
            - window.halfRowPx() - window.gapPx());
    }

    /** The full detent: the frame at {@link #FULL_SCALE} above it. */
    static int fullSheetPx(@NonNull Window window) {
        return sheetPxFor(window, FULL_SCALE);
    }

    /**
     * The half detent: the frame at {@link #HALF_SCALE} above it, but never less than
     * {@code minPx} (the card's header and its first row over the inset; the frame then shrinks
     * to fit) and never taller than the full detent. What tier 1 does not show scrolls.
     */
    static int halfSheetPx(@NonNull Window window, int minPx, int fullPx) {
        return Math.min(fullPx, Math.max(minPx, sheetPxFor(window, HALF_SCALE)));
    }

    /**
     * A surface that still stands while one of its rows is put away (the dock showing its A–Z
     * index with its pinned apps hidden), with the hidden row's ghost {@code band} (as
     * {@link #ghostRect} gives it) moved against the surface's inner side: the surface's rect
     * grown by the band's thickness towards the middle of the frame, so the row can be tapped
     * where it would stand.
     */
    @NonNull
    static int[] ghostBeside(@NonNull int[] surface, @NonNull int[] band, int heightPx) {
        int thickness = Math.max(0, band[3] - band[1]);
        boolean bottomEdge = band[1] + band[3] > heightPx;
        if (bottomEdge)
            return new int[] {surface[0], Math.max(0, surface[1] - thickness), surface[2],
                surface[3]};
        return new int[] {surface[0], surface[1], surface[2],
            Math.min(heightPx, surface[3] + thickness)};
    }

    /**
     * A hidden surface's ghost in the container's own px, {left, top, right, bottom}: a band
     * {@code bandPx} thick on {@code edge}, {@code marginPx} in from the container's sides, and
     * {@code stack} bands in from the edge where several ghosts share it.
     */
    @NonNull
    static int[] ghostRect(@NonNull PlaceLayout.Edge edge, int stack, int widthPx, int heightPx,
                           int bandPx, int marginPx) {
        int offset = marginPx + Math.max(0, stack) * bandPx;
        switch (edge) {
            case TOP:
                return new int[] {marginPx, offset, widthPx - marginPx, offset + bandPx};
            case LEFT:
                return new int[] {offset, marginPx, offset + bandPx, heightPx - marginPx};
            case RIGHT:
                return new int[] {widthPx - offset - bandPx, marginPx, widthPx - offset,
                    heightPx - marginPx};
            case BOTTOM:
            default:
                return new int[] {marginPx, heightPx - offset - bandPx, widthPx - marginPx,
                    heightPx - offset};
        }
    }

    /**
     * How far {@code rect} reaches in from {@code edge} of a container {@code widthPx} by
     * {@code heightPx}: the depth a surface standing on that edge takes up, which a ghost on the
     * same edge has to clear.
     */
    static int depthFrom(@NonNull PlaceLayout.Edge edge, @NonNull int[] rect, int widthPx,
                         int heightPx) {
        switch (edge) {
            case TOP: return Math.max(0, rect[3]);
            case LEFT: return Math.max(0, rect[2]);
            case RIGHT: return Math.max(0, widthPx - rect[0]);
            case BOTTOM:
            default: return Math.max(0, heightPx - rect[1]);
        }
    }

    /**
     * A ghost {@code band} on {@code edge} moved {@code px} further in, past the surfaces still
     * standing on that edge, so it never lies under another surface's tap target.
     */
    @NonNull
    static int[] shiftInward(@NonNull int[] band, @NonNull PlaceLayout.Edge edge, int px) {
        int by = Math.max(0, px);
        switch (edge) {
            case TOP: return new int[] {band[0], band[1] + by, band[2], band[3] + by};
            case LEFT: return new int[] {band[0] + by, band[1], band[2] + by, band[3]};
            case RIGHT: return new int[] {band[0] - by, band[1], band[2] - by, band[3]};
            case BOTTOM:
            default: return new int[] {band[0], band[1] - by, band[2], band[3] - by};
        }
    }
}
