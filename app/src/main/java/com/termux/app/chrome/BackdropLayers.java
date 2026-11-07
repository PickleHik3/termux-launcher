package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * A Live surface's backdrop layers while the wall of pages moves ({@link LiveBackdrop}). Pure.
 *
 * <p>At rest a Live surface records everything beneath it as one picture and blurs it. During a
 * swipe that picture would be recorded and blurred again on every frame, because the surface and
 * what is beneath it move apart. So while the wall moves the picture is split into layers that each
 * move on their own and are composited in the glass's one draw, bottom first:</p>
 *
 * <ul>
 *   <li>{@link Layer#GROUND}: the wallpaper as the stored pre-blurred frame, sampled where the
 *       surface stands and at the parallax offset ({@link Motion#PARALLAX}); over a live scene the
 *       scene's frame, new at the scene's glass rate ({@link Motion#SCENE}). A move never blurs it
 *       again.</li>
 *   <li>{@link Layer#PAGES}: the wall's pages beneath a surface that does not ride them (the dock,
 *       a float on the pinned layer), captured and blurred once as the swipe starts and moved by
 *       the pages' travel ({@link Motion#TRAVEL}). Each page is pictured on its own at full
 *       opacity and composited at its alpha now ({@link #pageAlpha}), so a page that fades as
 *       the pages slide (Fade) costs nothing. A surface on a page has no pages layer: lower
 *       pages that ride with it are in its local layer.</li>
 *   <li>{@link Layer#LOCAL}: what lies beneath the surface on its own page or float, or the chrome
 *       beneath the dock, recorded and blurred once ({@link Motion#FIXED}). Under a surface on a
 *       page it also holds the wall's ground strip, whose band there is the page's own ground
 *       and travels with it ({@link #holder}).</li>
 * </ul>
 *
 * <p>{@link #plan} says, for one frame, whether the surface draws its layers or the one picture
 * as at rest, and which layer is stale and recorded again. The first and the landed frame of a
 * swipe are always the one picture, so neither end differs from the resting glass. A layer that
 * cannot be held (pages that turn, fade or overlap, a capture still rendering, no stored frame)
 * sends the surface back to the one picture, recorded live, for those frames.</p>
 *
 * <p>The pages layer is §2a's page layer ({@link Mode}): the pages captured once and only moved,
 * kept while every tracked pose ({@link #POSE_STRIDE} floats: the view's matrix, its left, top and
 * alpha) is the same as when it was recorded but for one shared horizontal shift. At rest under a
 * live scene the one picture draws the pages on a cached layer ({@link Mode#REFERENCE}).</p>
 *
 * <p>{@link #drawsStored} is the "See through" rule, at rest too: with {@link SeeThrough#ONE} a
 * glass with another glass above it draws the stored frame, so the one above sees it but not
 * through it.</p>
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

    /** "See through": every glass layer beneath, or only what is directly beneath the topmost. */
    public enum SeeThrough { ALL, ONE }

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

    /** The pages layer of a surface that does not ride the pages (§2a). */
    public enum Mode { LIVE, REFERENCE, FROZEN }

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
     * beneath it, opaque or gone, and the two layers fit the surface's budget).
     */
    @NonNull
    public static Mode mode(boolean splittable, boolean wallMoving, boolean sceneLive) {
        if (!splittable) return Mode.LIVE;
        if (wallMoving) return Mode.FROZEN;
        return sceneLive ? Mode.REFERENCE : Mode.LIVE;
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
     * Whether the page layer is recorded again this frame: the mode changed, the pages drawn are
     * not the ones recorded, they did not move together ({@code shift} NaN), they moved at rest,
     * or the surface now reaches past what the layer holds.
     */
    public static boolean needsRecord(@NonNull Mode mode, @NonNull Mode recorded,
                                      boolean samePages, float shift, boolean covered) {
        if (mode == Mode.LIVE) return false;
        if (mode != recorded || !samePages || Float.isNaN(shift) || !covered) return true;
        return mode == Mode.REFERENCE && shift != 0f;
    }

    /**
     * How far the page layer is moved, in px: the pages' shift while frozen; a reference layer
     * draws the pages where they stand, so it never moves.
     */
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
     * One frame's plan for a Live surface: whether it draws its {@link #LAYERS} (and which) or the
     * one picture, and which layers are stale and recorded again.
     *
     * @param place         where the surface stands against the wall
     * @param wallMoving    the wall is anywhere but at rest
     * @param displaced     something beneath has moved against the surface since the wall left
     *                      rest: the pages' shift, the surface's own place, the parallax offset
     * @param groundReady   the ground's frame is at hand (the stored frame, or the scene's)
     * @param sceneLive     a live scene is the ground
     * @param sceneNewFrame the scene published a frame the ground does not hold yet
     * @param pagesBeneath  wall pages lie beneath the surface outside its own page
     * @param pagesMove     how those pages moved since the pages layer was recorded; for a
     *                      {@link Place#PAGE} surface, against the surface's own page
     * @param pagesReady    the pages layer has landed (its capture is not still rendering)
     * @param localChanged  the views in the local layer are not the ones it was recorded from
     * @param fixedBeneath  views beneath that no layer holds: chrome under a surface on a page, or
     *                      a ground strip whose seams cross a surface that stays ({@link #holder})
     */
    public static int plan(@NonNull Place place, boolean wallMoving, boolean displaced,
                           boolean groundReady, boolean sceneLive, boolean sceneNewFrame,
                           boolean pagesBeneath, @NonNull Move pagesMove, boolean pagesReady,
                           boolean localChanged, boolean fixedBeneath) {
        // The first frame of a swipe and the landed one are the resting picture: no pop.
        if (!wallMoving || !displaced) return 0;
        if (!groundReady || fixedBeneath) return 0;
        int plan = LAYERS | GROUND | LOCAL;
        if (sceneLive && sceneNewFrame) plan |= STALE_GROUND;
        if (localChanged) plan |= STALE_LOCAL;
        if (pagesBeneath) {
            // Off a page the frozen layer holds each page on its own at full opacity: a slide
            // moves it, a fade only changes the alphas it is composited at (pageAlpha).
            boolean stale = place == Place.PAGE ? pagesMove != Move.STILL
                : pagesMove == Move.TURN;
            // A pages layer recorded again is a capture that lands frames later: the one
            // picture, recorded live, until it has.
            if (stale) return STALE_PAGES;
            if (!pagesReady) return 0;
            // On a page, lower pages that ride with it are part of its local layer.
            if (place != Place.PAGE) plan |= PAGES;
        }
        return plan;
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

    /** Which layer holds a view drawn beneath a Live surface while the wall moves. */
    public enum Holder {
        /** The ground layer draws it: the wallpaper or the scene. */
        GROUND,
        /** The pages layer: the view that holds the wall, beneath a surface that does not ride it. */
        PAGES,
        /** The local layer, recorded once and moved with the surface. */
        LOCAL,
        /** Stands still where a surface on a page sweeps: no layer holds it. */
        FIXED,
        /** Draws nothing the surface sweeps: no layer needs it. */
        NONE
    }

    /**
     * Which layer holds a view drawn beneath a surface at {@code place} while the wall moves.
     * {@link Holder#FIXED} sends the surface back to the one picture ({@link #plan}'s
     * {@code fixedBeneath}); {@link Holder#PAGES} on a page sets its {@code pagesBeneath}.
     *
     * @param tier      where the view stands against the wall; ignored off a page
     * @param ground    the view is the wallpaper or the scene
     * @param holdsWall the view is the wall, or holds it, beneath a surface off the wall
     * @param ridesWall the view paints each page's ground where that page stands (the ground
     *                  strip), so its drawing travels with the pages
     * @param inBand    the view crosses the band a surface on a page sweeps
     * @param overlaps  the lower page lies under the surface
     */
    @NonNull
    public static Holder holder(@NonNull Place place, @NonNull Tier tier, boolean ground,
                                boolean holdsWall, boolean ridesWall, boolean inBand,
                                boolean overlaps) {
        return holder(place, tier, ground, holdsWall, ridesWall, false, inBand, overlaps);
    }

    /**
     * {@link #holder}, where {@code seams} says the strip's grounds differ from page to page. Then
     * under a surface that stays still (the dock, a bar, a float) a seam travels across it while
     * the pages move: no layer holds that, so it is {@link Holder#FIXED} and the surface keeps
     * the one picture, rather than holding the ground it started over and changing at landing.
     */
    @NonNull
    public static Holder holder(@NonNull Place place, @NonNull Tier tier, boolean ground,
                                boolean holdsWall, boolean ridesWall, boolean seams,
                                boolean inBand, boolean overlaps) {
        if (ground) return Holder.GROUND;
        if (place != Place.PAGE) {
            if (holdsWall) return Holder.PAGES;
            return ridesWall && seams ? Holder.FIXED : Holder.LOCAL;
        }
        switch (tier) {
            case ON_PAGE:
                return Holder.LOCAL;
            case PAGE:
                return overlaps ? Holder.PAGES : Holder.NONE;
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

    /**
     * Whether a Live surface draws as the Wallpaper kind (the stored frame at its offset) instead
     * of recording what is beneath it. With {@link SeeThrough#ONE} a surface another Live glass
     * stands over does: the one above records it, content and all, like anything beneath, but no
     * longer sees through it to what lies under both.
     */
    public static boolean drawsStored(@NonNull SeeThrough seeThrough, boolean glassAbove) {
        return seeThrough == SeeThrough.ONE && glassAbove;
    }
}
