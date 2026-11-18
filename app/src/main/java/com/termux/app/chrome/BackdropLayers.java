package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * A Live surface's backdrop layers (ADR 0029 section 3), at rest and while the wall of pages moves
 * ({@link LiveBackdrop}). Pure.
 *
 * <p>A Live surface always draws its layers, composited in the glass's one draw, bottom first, so
 * the first frame of a swipe, every frame of it and the landed frame are one composition and a
 * landing has nothing to swap:</p>
 *
 * <ul>
 *   <li>{@link Layer#GROUND}: the wallpaper as the stored pre-blurred frame, sampled where the
 *       surface stands and at the parallax offset ({@link Motion#PARALLAX}); over a live scene the
 *       scene's frame, new at the scene's glass rate ({@link Motion#SCENE}). Never blurred again.
 *       Until the stored frame is resident the wallpaper is drawn in the local layer instead.</li>
 *   <li>{@link Layer#PAGES}: the wall's pages beneath a surface that does not ride them (the
 *       dock, a float on the pinned layer), captured and blurred once, held at rest and while the
 *       wall moves, moved by the pages' travel ({@link Motion#TRAVEL}) and taken again behind the
 *       old picture when the pages change ({@link PageLayerRefresh}). Each page is pictured on its
 *       own at full opacity and composited at its alpha now ({@link #pageAlpha}). Until the first
 *       capture lands, and whenever that picture cannot be held (pages that turn, until their
 *       slots follow them), the pages are in the local layer, drawn as they are.</li>
 *   <li>{@link Layer#LOCAL}: everything else beneath the surface, drawn from the views' own
 *       display lists and blurred, so whatever redraws beneath (a terminal printing, a clock)
 *       reaches the glass without a new recording. It is recorded again only when its views
 *       change or the surface moves against a view that stands still ({@link Holder#FIXED}).</li>
 * </ul>
 *
 * <p>Each layer keeps its own refresh trigger ({@link #plan}); there is no other picture to fall
 * back to. The pages layer is §2a's page layer ({@link Mode}): the pages captured once and only
 * moved, kept while every tracked pose ({@link #POSE_STRIDE} floats: the view's matrix, its left,
 * top and alpha) is the same as when it was recorded but for one shared horizontal shift.</p>
 */
public final class BackdropLayers {

    /** The layers, bottom first. */
    public enum Layer { GROUND, PAGES, LOCAL }

    /** How a layer moves against the surface between recordings. */
    public enum Motion {
        /** Sampled where the surface stands now, less the wallpaper's parallax offset. */
        PARALLAX,
        /** As {@link #PARALLAX} without parallax, with a new frame at the scene's glass rate. */
        SCENE,
        /** Moved by the pages' shared shift. */
        TRAVEL,
        /** Fixed to the surface. */
        FIXED
    }

    /** Where a Live surface stands against the wall. */
    public enum Place {
        /** Beneath it holds the wall, and it stays while the pages move: the dock, the nav strip. */
        CHROME,
        /** A floating window on the pinned layer: stays while the pages move; lower floats are local. */
        FLOAT,
        /** On a page: rides it, so the ground moves against it and its page's views do not. */
        PAGE
    }

    /** How the pages beneath moved since their layer was recorded ({@link #move}). */
    public enum Move {
        /** Not at all. */
        STILL,
        /** Together, by one horizontal shift: a slide. */
        SHIFT,
        /** Any other way: a plank's tilt, a sink, Depth, Carousel, Stack, a page shown or hidden. */
        TURN,
        /** A page's alpha moved: Fade. */
        FADE
    }

    /** {@link #plan}: the surface draws its layers this frame; without it, the one picture. */
    public static final int LAYERS = 1;
    /** {@link #plan}: the layers it draws. */
    public static final int GROUND = 1 << 1;
    public static final int PAGES = 1 << 2;
    public static final int LOCAL = 1 << 3;
    /** {@link #plan}: the layers recorded again this frame. */
    public static final int STALE_GROUND = 1 << 4;
    public static final int STALE_PAGES = 1 << 5;
    public static final int STALE_LOCAL = 1 << 6;

    /**
     * Where a surface's pages are (§2a): drawn as they are in the local layer, or frozen in a
     * pages layer of their own while the wall moves.
     */
    public enum Mode { LIVE, FROZEN }

    /** Floats per tracked pose: the 3x3 matrix, then left, top and alpha. */
    public static final int POSE_STRIDE = 12;
    static final int POSE_TRANS_X = 2;
    static final int POSE_LEFT = 9;
    static final int POSE_TOP = 10;
    static final int POSE_ALPHA = 11;

    /** How far two pose values may differ and still be the same pose. */
    static final float SAME_EPSILON = 1e-3f;
    /** How far two pages' shifts may differ and still be one travel, in px. */
    static final float SHIFT_EPSILON_PX = 0.05f;

    private BackdropLayers() {}

    /**
     * The mode for a surface whose beneath can be split ({@code splittable}: the wall's pages are
     * beneath it, opaque or gone, and the layers fit the surface's budget): frozen, at rest and
     * while the wall moves, so a surface keeps one picture of its pages and nothing swaps when
     * the wall starts or stops (ADR 0029 section 3). {@code wallMoving} does not matter.
     */
    @NonNull
    public static Mode mode(boolean splittable, boolean wallMoving) {
        return splittable ? Mode.FROZEN : Mode.LIVE;
    }

    /**
     * The one horizontal shift every pose in {@code now} has made since {@code recorded}, in px;
     * NaN when the counts differ or any pose moved any other way. 0 for no poses.
     */
    public static float shift(@NonNull float[] recorded, int recordedPoses,
                              @NonNull float[] now, int nowPoses) {
        return shift(recorded, recordedPoses, now, nowPoses, false);
    }

    /**
     * {@link #shift}, with each pose's alpha left out when {@code alphaFree}: the frozen pages
     * layer holds every page at full opacity and composites each at its alpha now
     * ({@link #pageAlpha}), so a page that fades as the pages slide is still one travel.
     */
    public static float shift(@NonNull float[] recorded, int recordedPoses,
                              @NonNull float[] now, int nowPoses, boolean alphaFree) {
        if (recordedPoses != nowPoses || recordedPoses < 0) return Float.NaN;
        float shift = Float.NaN;
        for (int pose = 0; pose < nowPoses; pose++) {
            int base = pose * POSE_STRIDE;
            for (int i = 0; i < POSE_STRIDE; i++) {
                if (i == POSE_TRANS_X || (alphaFree && i == POSE_ALPHA)) continue;
                if (Math.abs(now[base + i] - recorded[base + i]) > SAME_EPSILON) return Float.NaN;
            }
            float dx = now[base + POSE_TRANS_X] - recorded[base + POSE_TRANS_X];
            if (Float.isNaN(shift)) shift = dx;
            else if (Math.abs(dx - shift) > SHIFT_EPSILON_PX) return Float.NaN;
        }
        return Float.isNaN(shift) ? 0f : shift;
    }

    /**
     * Whether the page layer is captured again this frame: the mode changed, the pages drawn are
     * not the ones recorded, they did not move together ({@code shift} NaN), or the surface now
     * reaches past what the layer holds.
     */
    public static boolean needsRecord(@NonNull Mode mode, @NonNull Mode recorded,
                                      boolean samePages, float shift, boolean covered) {
        if (mode == Mode.LIVE) return false;
        return mode != recorded || !samePages || Float.isNaN(shift) || !covered;
    }

    /** How far the page layer is moved, in px: the pages' shift while frozen. */
    public static float aim(@NonNull Mode mode, float shift) {
        return mode == Mode.FROZEN && !Float.isNaN(shift) ? shift : 0f;
    }

    /**
     * Whether a layer recorded over {@code reach} (left, top, right, bottom) still holds what a
     * surface needs ({@code needed}) once the layer has moved by {@code shiftPx}.
     */
    public static boolean covers(float reachLeft, float reachTop, float reachRight,
                                 float reachBottom, float neededLeft, float neededTop,
                                 float neededRight, float neededBottom, float shiftPx) {
        return neededLeft - shiftPx >= reachLeft - SHIFT_EPSILON_PX
            && neededRight - shiftPx <= reachRight + SHIFT_EPSILON_PX
            && neededTop >= reachTop - SHIFT_EPSILON_PX
            && neededBottom <= reachBottom + SHIFT_EPSILON_PX;
    }

    /**
     * The widest pad, from 0 to {@code maxPadPx}, a page layer of {@code width} x {@code height}
     * can take on both sides ({@code horizontal}, or top and bottom) and still fit, with a
     * surface's own layer of {@code baseBytes}, in one picture's share of the budget
     * ({@link LiveBackdrop#MAX_LAYER_BYTES}); -1 when it does not fit even unpadded.
     */
    public static int pad(long baseBytes, int width, int height, boolean horizontal, int maxPadPx) {
        return pad(baseBytes, width, height, horizontal, maxPadPx, 1);
    }

    /**
     * {@link #pad} for a pages layer held as {@code slots} pictures side by side, each at most
     * the layer's size: a frozen layer keeps each page, and the wall's decor, on its own
     * ({@link #frozenSlots}).
     */
    public static int pad(long baseBytes, int width, int height, boolean horizontal, int maxPadPx,
                          int slots) {
        int n = Math.max(1, slots);
        if (!fits(baseBytes, n * width, height)) return -1;
        int low = 0;
        int high = Math.max(0, maxPadPx);
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            boolean ok = horizontal ? fits(baseBytes, n * (width + 2 * mid), height)
                : fits(baseBytes, n * width, height + 2 * mid);
            if (ok) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    /** The pictures a frozen pages layer of {@code pages} pages holds: one each, and the decor. */
    public static int frozenSlots(int pages) {
        return Math.max(0, pages) + 1;
    }

    /**
     * The alpha a frozen page's picture is composited at: the page's alpha now, from
     * {@code poses}. Each page is pictured at full opacity, so a fade only changes this number;
     * the first and the landed frame, at the alpha the page rests at, match the one picture.
     */
    public static float pageAlpha(@NonNull float[] poses, int page) {
        float alpha = poses[page * POSE_STRIDE + POSE_ALPHA];
        return Math.max(0f, Math.min(1f, alpha));
    }

    /** Whether a page layer of {@code width} x {@code height} fits beside {@code baseBytes}. */
    public static boolean fits(long baseBytes, int width, int height) {
        return baseBytes + LiveBackdrop.layerBytes(width, height) <= LiveBackdrop.MAX_LAYER_BYTES;
    }

    // ---- Backdrop layers -------------------------------------------------------------------

    /**
     * One frame's plan for a Live surface: the layers it draws (always {@link #LAYERS} and
     * {@link #LOCAL}) and which are recorded again. There is no resting picture of another kind:
     * at rest and moving the surface draws the same layers.
     *
     * @param groundReady   the ground's frame is at hand (the stored frame, or the scene's); without
     *                      it the wallpaper is drawn in the local layer
     * @param sceneLive     a live scene is the ground
     * @param sceneNewFrame the scene published a frame the ground does not hold yet
     * @param pagesHeld     a frozen pages layer is held this frame ({@link #holdsPages})
     * @param localChanged  the views in the local layer are not the ones it was recorded from, or
     *                      its size or what it holds of the ground changed
     * @param fixedBeneath  the local layer holds a view that stands still while the surface may
     *                      move against it ({@link Holder#FIXED})
     * @param displaced     the surface stands elsewhere against the window than when its local
     *                      layer was recorded
     */
    public static int plan(boolean groundReady, boolean sceneLive, boolean sceneNewFrame,
                           boolean pagesHeld, boolean localChanged, boolean fixedBeneath,
                           boolean displaced) {
        int plan = LAYERS | LOCAL;
        if (groundReady) {
            plan |= GROUND;
            if (sceneLive && sceneNewFrame) plan |= STALE_GROUND;
            if (pagesHeld) plan |= PAGES;
        }
        if (localChanged || (fixedBeneath && displaced)) plan |= STALE_LOCAL;
        return plan;
    }

    /**
     * Whether a surface at {@code place} draws a frozen pages layer this frame: only off the wall,
     * only for pages that moved by one travel ({@link Move#SHIFT}, {@link Move#FADE} or
     * {@link Move#STILL}) into a picture that has landed, and not over a ground strip whose
     * seams would then draw above the pages they lie under. Otherwise the pages are in the local
     * layer, drawn as they are.
     */
    public static boolean holdsPages(@NonNull Place place, @NonNull Mode mode,
                                     @NonNull Move pagesMove, boolean frozenReady,
                                     boolean seamsBeneath) {
        return place != Place.PAGE && mode == Mode.FROZEN && pagesMove != Move.TURN
            && frozenReady && !seamsBeneath;
    }

    /** Where a view drawn beneath a Live surface stands against the wall ({@link #holder}). */
    public enum Tier {
        /** Drawn by one of the wall's ancestors, beneath the wall: chrome, the ground strip. */
        BENEATH_WALL,
        /** One of the wall's pages, drawn before the surface's own. */
        PAGE,
        /** Inside the surface's own page, beneath the surface. */
        ON_PAGE
    }

    /** Which layer holds a view drawn beneath a Live surface. */
    public enum Holder {
        /** The ground layer draws it: the wallpaper or the scene. */
        GROUND,
        /** The pages layer: the view that holds the wall, beneath a surface that does not ride it. */
        PAGES,
        /** The local layer, recorded once and moved with the surface. */
        LOCAL,
        /**
         * The local layer too, but it stands still where the surface may move against it: the
         * local layer is recorded again whenever the surface is displaced.
         */
        FIXED,
        /** Draws nothing the surface sweeps: no layer needs it. */
        NONE
    }

    /**
     * Which layer holds a view drawn beneath a surface at {@code place}.
     *
     * @param tier        where the view stands against the wall; ignored off a page
     * @param ground      the view is the wallpaper or the scene
     * @param groundReady the ground layer is at hand; without it the ground is local
     * @param holdsWall   the view is the wall, or holds it, beneath a surface off the wall
     * @param pagesHeld   a frozen pages layer is held this frame ({@link #holdsPages})
     * @param ridesWall   the view paints each page's ground where that page stands (the ground
     *                    strip), so its drawing travels with the pages
     * @param inBand      the view crosses the band a surface on a page sweeps
     * @param overlaps    the lower page lies under the surface
     */
    @NonNull
    public static Holder holder(@NonNull Place place, @NonNull Tier tier, boolean ground,
                                boolean groundReady, boolean holdsWall, boolean pagesHeld,
                                boolean ridesWall, boolean inBand, boolean overlaps) {
        if (ground) {
            if (groundReady) return Holder.GROUND;
            // Drawn in the local layer, the wallpaper stands still where a surface on a page moves.
            return place == Place.PAGE ? Holder.FIXED : Holder.LOCAL;
        }
        if (place != Place.PAGE) return holdsWall && pagesHeld ? Holder.PAGES : Holder.LOCAL;
        switch (tier) {
            case ON_PAGE:
                return Holder.LOCAL;
            case PAGE:
                // A lower page under the surface moves with it in a slide but not in a turn.
                return overlaps ? Holder.FIXED : Holder.NONE;
            default:
                // The ground strip's band under a surface on a page is that page's own ground, and
                // it moves as the page does: it rides with the surface. Anything else stands still.
                if (ridesWall) return Holder.LOCAL;
                return inBand ? Holder.FIXED : Holder.NONE;
        }
    }

    /** How {@code layer} moves against a surface at {@code place}. */
    @NonNull
    public static Motion motion(@NonNull Layer layer, @NonNull Place place, boolean sceneLive) {
        switch (layer) {
            case GROUND:
                return sceneLive ? Motion.SCENE : Motion.PARALLAX;
            case PAGES:
                return place == Place.PAGE ? Motion.FIXED : Motion.TRAVEL;
            default:
                return Motion.FIXED;
        }
    }

    /**
     * How the poses in {@code now} moved since {@code recorded} ({@link #POSE_STRIDE} floats each):
     * {@link Move#TURN} for any change but one shared horizontal shift and the alphas (and for
     * counts that differ), else {@link Move#FADE} when any alpha moved, {@link Move#SHIFT} for
     * the shift alone, else {@link Move#STILL}.
     */
    @NonNull
    public static Move move(@NonNull float[] recorded, int recordedPoses, @NonNull float[] now,
                            int nowPoses) {
        if (recordedPoses != nowPoses || recordedPoses < 0) return Move.TURN;
        float shift = shift(recorded, recordedPoses, now, nowPoses, true);
        if (Float.isNaN(shift)) return Move.TURN;
        for (int pose = 0; pose < nowPoses; pose++) {
            int alpha = pose * POSE_STRIDE + POSE_ALPHA;
            if (Math.abs(now[alpha] - recorded[alpha]) > SAME_EPSILON) return Move.FADE;
        }
        return Math.abs(shift) > SHIFT_EPSILON_PX ? Move.SHIFT : Move.STILL;
    }
}
