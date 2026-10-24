package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout;

/**
 * Where the Appearance editor's frame stands and how big its cards are, as pure arithmetic in
 * pixels (appearance-and-settings-rework §7; wireframes 01–04 and their README's measurements).
 *
 * <ul>
 *   <li><b>At rest</b> the live launcher fills the room between the top line (16dp under the
 *   status bar) and the half line over the editor bar, at most {@link #REST_MAX_SCALE}.</li>
 *   <li><b>With a card up</b> the frame shrinks to {@link #HALF_SCALE} at the card's half detent
 *   and {@link #FULL_SCALE} at its full one (never larger than at rest), and moves up when the
 *   selected surface would stand behind the card, so the surface being tuned stays in view. At
 *   the full detent the whole frame stands above the card.</li>
 *   <li>A <b>hidden</b> surface keeps a ghost: a band on the edge it would stand on, so it can
 *   still be tapped and shown again.</li>
 * </ul>
 *
 * <p>The frame scales about its top-centre pivot: a container point {@code y} px down stands at
 * {@code containerTop + translationY + y * scale} on screen.</p>
 */
final class EditorFramePose {

    private EditorFramePose() {}

    /** The resting frame's largest scale (the wireframe's ×0.84). */
    static final float REST_MAX_SCALE = 0.86f;
    /** The frame behind a card at its half detent. */
    static final float HALF_SCALE = 0.6f;
    /** The frame above a card at its full detent: the whole launcher stays visible. */
    static final float FULL_SCALE = 0.44f;
    /** The frame's top line under the status bar, and the air between the frame and what is under it. */
    static final int TOP_GAP_DP = 16;
    static final int GAP_DP = 8;
    /** The half detent's share of the window when tier 1 asks for less, and its ceiling. */
    static final float HALF_MIN_SHARE = 0.45f;
    static final float HALF_MAX_SHARE = 0.62f;
    /** A ghost band's thickness, in dp. */
    static final int GHOST_DP = 40;

    /** The frame's top line in the content view: {@link #TOP_GAP_DP} under the top inset. */
    static int topPx(int topInsetPx, float density) {
        return topInsetPx + Math.round(TOP_GAP_DP * density);
    }

    /**
     * The resting frame's bottom line: over the editor bar ({@code barPx} tall, {@code footGapPx}
     * over the navigation inset), the half line ({@code halfRowPx}) and a gap each side of it.
     */
    static int restBottomPx(int windowHeightPx, int navInsetPx, int barPx, int footGapPx,
                            int halfRowPx, float density) {
        int gap = Math.round(GAP_DP * density);
        return windowHeightPx - navInsetPx - footGapPx - barPx - gap - halfRowPx - gap;
    }

    /** The scale that fits {@code visiblePx} of launcher between two lines, capped. */
    static float fitScale(int visiblePx, int topPx, int bottomPx, float cap) {
        if (visiblePx <= 0 || bottomPx <= topPx)
            return cap;
        return Math.max(0.1f, Math.min(cap, (bottomPx - topPx) / (float) visiblePx));
    }

    /** The frame's scale at rest. */
    static float restScale(int visiblePx, int topPx, int restBottomPx) {
        return fitScale(visiblePx, topPx, restBottomPx, REST_MAX_SCALE);
    }

    /** The frame's scale with a card at {@code detent}: the detent's own, never above rest. */
    static float cardScale(int detent, float restScale) {
        float scale = detent == EditorCardSheet.DETENT_FULL ? FULL_SCALE : HALF_SCALE;
        return Math.min(scale, restScale);
    }

    /**
     * The full detent's height: the window less the frame at {@link #FULL_SCALE} under its top
     * line, the half line and the gaps, so the whole frame stands above the card.
     */
    static int fullSheetPx(int windowHeightPx, int topPx, int visiblePx, int halfRowPx,
                           float density) {
        int gap = Math.round(GAP_DP * density);
        int frame = (int) Math.ceil(visiblePx * FULL_SCALE);
        return Math.max(0, windowHeightPx - topPx - frame - gap - halfRowPx - gap);
    }

    /**
     * The half detent's height: what tier 1 asks for ({@code tierOnePx}, header and foot
     * included), held between {@link #HALF_MIN_SHARE} and {@link #HALF_MAX_SHARE} of the window
     * and never taller than the full detent. A taller tier 1 scrolls.
     */
    static int halfSheetPx(int windowHeightPx, int tierOnePx, int fullPx) {
        int min = Math.round(windowHeightPx * HALF_MIN_SHARE);
        int max = Math.round(windowHeightPx * HALF_MAX_SHARE);
        return Math.min(fullPx, Math.max(min, Math.min(max, tierOnePx)));
    }

    /**
     * The frame's translation down. At rest (and for a frame with nothing selected) the visible
     * top, the band {@code revealTopPx} above the container, stands on the top line. With a card
     * up, the frame moves up just enough that the selected surface ({@code selected}, its
     * {top, bottom} in the container's own px, or null) ends {@code visibleBottomPx} or higher,
     * but never so far that the surface's own top leaves the top line.
     */
    static float translationY(int topPx, int containerTopPx, int revealTopPx, float scale,
                              int visibleBottomPx, @Nullable int[] selected) {
        float rest = topPx + Math.max(0, revealTopPx) * scale - containerTopPx;
        if (selected == null)
            return rest;
        float bottomOnScreen = containerTopPx + rest + selected[1] * scale;
        float ty = rest + Math.min(0f, visibleBottomPx - bottomOnScreen);
        float lowestForTop = topPx - containerTopPx - selected[0] * scale;
        return Math.max(ty, Math.min(rest, lowestForTop));
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
}
