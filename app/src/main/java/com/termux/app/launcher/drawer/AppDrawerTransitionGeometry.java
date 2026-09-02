package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Pure geometry for the app drawer's dock → full-screen transition.
 *
 * <p>Everything here is a function of the drag distance and the two endpoint rectangles. There is
 * no easing in the drag path at all: while a finger is down {@code p} is the raw travelled
 * fraction, so the plane tracks 1:1. Only the release is animated, by {@code Spring}.
 *
 * <p><b>Rect type.</b> Deliberately not {@code android.graphics.Rect}/{@code RectF}: the nested
 * {@link Frame} of plain floats keeps this class (and its test) runnable under bare JUnit with no
 * Robolectric and no native graphics, and it keeps the transition sub-pixel — rounding the plane
 * rect to ints every frame is exactly what makes a 1:1 drag stutter. The controller converts to
 * whatever the view layer wants at the point of application.
 */
public final class AppDrawerTransitionGeometry {

    /** Fraction of the root height the drawer travels over, before clamping. */
    public static final float TRAVEL_FRACTION = 0.30f;
    /**
     * Slop multiple already consumed by {@link AppDrawerGestureArbiter}'s claim test. Subtracting
     * it means progress starts at 0 at the instant of the claim rather than jumping to whatever
     * the finger had already travelled.
     */
    public static final float SLOP_FACTOR = AppDrawerGestureArbiter.DRAWER_SLOP_FACTOR;

    /** Dock lift rises over this ramp… */
    private static final float LIFT_IN_END = 0.16f;
    /** …and is paid back over this one, so the dock is level again by the time the plane is full. */
    private static final float LIFT_OUT_START = 0.28f;

    private AppDrawerTransitionGeometry() {}

    /** An immutable rectangle in host coordinates. */
    public static final class Frame {

        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Frame(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Frame)) return false;
            Frame o = (Frame) other;
            return Float.compare(left, o.left) == 0 && Float.compare(top, o.top) == 0
                && Float.compare(right, o.right) == 0 && Float.compare(bottom, o.bottom) == 0;
        }

        @Override
        public int hashCode() {
            int result = Float.floatToIntBits(left);
            result = 31 * result + Float.floatToIntBits(top);
            result = 31 * result + Float.floatToIntBits(right);
            result = 31 * result + Float.floatToIntBits(bottom);
            return result;
        }

        @NonNull
        @Override
        public String toString() {
            return "Frame(" + left + ", " + top + ", " + right + ", " + bottom + ")";
        }
    }

    /**
     * Distance the finger must travel for a full open.
     *
     * <p>The dp bounds arrive as pixels because this class never sees a {@code Context}; the
     * caller resolves {@code dp(120)} and {@code dp(260)} once.
     */
    public static float resolveOpenTravelPx(float rootHeightPx, float minTravelPx, float maxTravelPx) {
        float travel = TRAVEL_FRACTION * Math.max(0f, rootHeightPx);
        float upper = Math.max(minTravelPx, maxTravelPx);
        return Math.max(minTravelPx, Math.min(upper, travel));
    }

    /** Raw finger fraction, with the claim's slop dead zone removed. */
    public static float progressForDrag(float rawY, float downY, float slopPx, float travelPx) {
        if (travelPx <= 0f) return 0f;
        return clamp01((rawY - downY - (slopPx * SLOP_FACTOR)) / travelPx);
    }

    /** Sub-phase helper: 0 below {@code start}, 1 at or above {@code end}, linear between. */
    public static float ramp(float p, float start, float end) {
        if (end <= start) return p >= end ? 1f : 0f;
        return clamp01((p - start) / (end - start));
    }

    public static float lerp(float from, float to, float t) {
        return from + ((to - from) * t);
    }

    public static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    /**
     * The dock's little hop as the drag starts, paid back before the plane fills the screen: it
     * peaks between the two ramps and is 0 at both ends, so nothing is left translated when the
     * transition settles either way.
     */
    public static float dockLiftFraction(float p) {
        return ramp(p, 0f, LIFT_IN_END) * (1f - ramp(p, LIFT_OUT_START, 1f));
    }

    /**
     * Interpolates the plane rectangle between the dock's on-screen rect and the open rect.
     *
     * <p>{@code liftPx} is the signed dock-lift translation (negative lifts) and applies only to
     * the seed end, which is what keeps the invisible-handoff invariant: at {@code p == 0} with no
     * lift the plane rect <em>is</em> the dock rect, so the cross-fade swaps two identical
     * rectangles.
     */
    @NonNull
    public static Frame resolvePlaneFrame(@NonNull Frame dockRect, @NonNull Frame openRect,
                                          float p, float liftPx) {
        float t = clamp01(p);
        return new Frame(
            lerp(dockRect.left, openRect.left, t),
            lerp(dockRect.top + liftPx, openRect.top, t),
            lerp(dockRect.right, openRect.right, t),
            lerp(dockRect.bottom + liftPx, openRect.bottom, t));
    }

    /**
     * Corner radius between the seed and the open plane. The seed radius is the dock capsule
     * radius in rounded style and 0 in default style; the open radius comes from the drawer
     * corner-radius preference.
     */
    public static float resolveRadiusPx(float seedRadiusPx, float openRadiusPx, float p) {
        return lerp(seedRadiusPx, openRadiusPx, clamp01(p));
    }

    /** Horizontal inset between the dock's inset and the drawer's. */
    public static float resolveInsetPx(float dockInsetPx, float drawerInsetPx, float p) {
        return lerp(dockInsetPx, drawerInsetPx, clamp01(p));
    }

    /**
     * The open plane's bottom edge while the search keyboard is revealed.
     *
     * <p>This is not part of the open transition: the drawer is already open and its rectangle
     * settled when a keystroke brings the keyboard up. Only the bottom edge moves, up to one
     * captured gap above the keyboard's captured top — captured because the accessory stack's layout
     * is frozen while the drawer is engaged, so both numbers are read once at reveal begin and never
     * re-measured per frame. The grid inside is translated to suit; nothing is laid out again.
     *
     * <p>At {@code reveal == 0} the answer is {@code openBottomPx} exactly, so the controller can
     * route the plane's bottom through here on every frame whether or not a keyboard exists.
     *
     * @param openBottomPx the open rect's bottom
     * @param pinTopPx     the in-app keyboard band's captured top
     * @param gapPx        the captured dock↔keyboard gap the rounded style must preserve
     * @param reveal       0 = no keyboard, 1 = keyboard fully revealed
     */
    public static float resolveSearchPlaneBottom(float openBottomPx, float pinTopPx, float gapPx,
                                                 float reveal) {
        return lerp(openBottomPx, pinTopPx - gapPx, clamp01(reveal));
    }

    /**
     * Where the host's scene dim ends while the keyboard is being revealed: the host's own bottom at
     * reveal 0, the keyboard's top at reveal 1. Unlike {@link #resolveSearchPlaneBottom} there is no
     * gap — the strip between plane and keyboard stays dimmed, the keys do not.
     */
    public static float resolveRevealClipBottom(float hostBottomPx, float pinTopPx, float reveal) {
        return lerp(hostBottomPx, pinTopPx, clamp01(reveal));
    }
}
