package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * When a live glass surface's picture is drawn in two layers, and when the page layer is
 * recorded again or only re-aimed ({@link LiveBackdrop}). Pure.
 *
 * <p>The picture beneath a live surface is the window's ground (the wallpaper or the scene's
 * frame) and the chrome, which are drawn live on every recording, and the wall's pages, which
 * cost the most to draw again. The page layer holds the pages on its own:</p>
 *
 * <ul>
 *   <li>{@link Mode#LIVE}: one layer, as Live blur always drew it. At rest without a scene, and
 *       whenever the split does not fit or is not safe.</li>
 *   <li>{@link Mode#REFERENCE}: at rest under a live scene. The page layer draws the pages as
 *       they are (their own display lists, on a layer of its own), so a scene frame re-blurs the
 *       ground and the chrome over the page layer's pixels, not the pages themselves.</li>
 *   <li>{@link Mode#FROZEN}: while the wall moves. The page layer is a picture of the pages taken
 *       once as the swipe starts and only moved by the pages' travel each frame; what animates in
 *       place on a page holds still under the glass until the wall settles.</li>
 * </ul>
 *
 * <p>A page layer is kept only while the pages it holds move together: every tracked pose
 * ({@link #POSE_STRIDE} floats: the view's matrix, its left, top and alpha) the same as when it
 * was recorded but for one shared horizontal shift. A pose that moved any other way (a turn, a
 * sink, a page shown or hidden) records it again.</p>
 */
public final class WallSwipeGlassPolicy {

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

    private WallSwipeGlassPolicy() {}

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
        if (recordedPoses != nowPoses || recordedPoses < 0) return Float.NaN;
        float shift = Float.NaN;
        for (int pose = 0; pose < nowPoses; pose++) {
            int base = pose * POSE_STRIDE;
            for (int i = 0; i < POSE_STRIDE; i++) {
                if (i == POSE_TRANS_X) continue;
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
        if (!fits(baseBytes, width, height)) return -1;
        int low = 0;
        int high = Math.max(0, maxPadPx);
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            boolean ok = horizontal ? fits(baseBytes, width + 2 * mid, height)
                : fits(baseBytes, width, height + 2 * mid);
            if (ok) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    /** Whether a page layer of {@code width} x {@code height} fits beside {@code baseBytes}. */
    public static boolean fits(long baseBytes, int width, int height) {
        return baseBytes + LiveBackdrop.layerBytes(width, height) <= LiveBackdrop.MAX_LAYER_BYTES;
    }
}
