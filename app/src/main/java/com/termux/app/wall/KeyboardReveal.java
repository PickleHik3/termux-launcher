package com.termux.app.wall;

/**
 * The keyboard swipe tied to the finger: while it holds the swipe, how much of the keyboard shows
 * follows how far the finger has gone, and its lift settles the keyboard up or down on a spring
 * ({@link SettleSpring}) from where the finger left it. One number carries it, the reveal: 0 with
 * the keyboard down, 1 with it all the way up.
 *
 * <p>The finger maps one to one: a keyboard's height of travel is the whole way, so the keyboard
 * rises under a finger moving up as far as the finger has moved, and goes down with one moving
 * down. Where it lands is the release's: past {@link #COMMIT_FRACTION} of the way from where it
 * started it goes on to the other state, short of it back; a fling of {@link #FLING_DP_PER_SEC}
 * decides on its own, on in the direction it was moving and back against it. So a short, quick
 * flick down closes the keyboard, and a slow drag let go early springs back.
 *
 * <p>Pure, so the mapping and the rule are tested as arithmetic; the wall reads the finger and
 * the activity moves the keyboard.
 */
public final class KeyboardReveal {

    /** The share of the keyboard's height past which a release goes on to the other state. */
    public static final float COMMIT_FRACTION = 1f / 3f;

    /**
     * The release speed, in dp per second, that decides on its own whatever the distance: on to
     * the other state moving away from where it started, back to it moving the other way. A
     * little under the pager's ({@link PaneWallPolicy#DRAG_COMMIT_VELOCITY_DP_PER_SEC}): a
     * keyboard's height is a shorter way than a page's width, and a flick over it is shorter.
     */
    public static final float FLING_DP_PER_SEC = 350f;

    /**
     * The settle's spring, per second: about 450 ms for a whole keyboard's height from still,
     * quicker for anything thrown. Stiffer than the wall's, for the shorter way.
     */
    public static final float SETTLE_OMEGA = 22f;
    public static final float SETTLE_MAX_OMEGA = 90f;

    /** How close, in px, the settle counts as landed. */
    public static final float SETTLE_REST_PX = 0.5f;

    private KeyboardReveal() {}

    /**
     * The reveal for a finger that has moved {@code dyPx} (positive down) since the reveal stood
     * at {@code startReveal}, over a keyboard {@code travelPx} tall, held to 0 and 1.
     */
    public static float reveal(float startReveal, float dyPx, int travelPx) {
        if (travelPx <= 0 || Float.isNaN(dyPx)) return clamp01(startReveal);
        return clamp01(startReveal - dyPx / travelPx);
    }

    /** A finger's vertical speed in px per second (positive down) as the reveal's, per second. */
    public static float revealVelocity(float velocityYPxPerSec, int travelPx) {
        if (travelPx <= 0 || Float.isNaN(velocityYPxPerSec)) return 0f;
        return -velocityYPxPerSec / travelPx;
    }

    /**
     * Where a release lands: true for the keyboard up.
     *
     * @param fromOpen             whether the keyboard was up when the swipe began
     * @param reveal               where the finger left it
     * @param revealVelocityPerSec its speed at the release, positive rising
     * @param flingPerSec          the fling that decides alone, in the reveal's own units
     */
    public static boolean settlesOpen(boolean fromOpen, float reveal, float revealVelocityPerSec,
                                      float flingPerSec) {
        // The speed away from where the swipe started: positive carries it on.
        float onward = fromOpen ? -revealVelocityPerSec : revealVelocityPerSec;
        if (flingPerSec > 0f && !Float.isNaN(onward)) {
            if (onward >= flingPerSec) return !fromOpen;
            if (onward <= -flingPerSec) return fromOpen;
        }
        float travelled = fromOpen ? 1f - clamp01(reveal) : clamp01(reveal);
        return travelled >= COMMIT_FRACTION ? !fromOpen : fromOpen;
    }

    /** The reveal a position-only release changes its mind at, for a swipe that began there. */
    public static float commitPoint(boolean fromOpen) {
        return fromOpen ? 1f - COMMIT_FRACTION : COMMIT_FRACTION;
    }

    /**
     * Whether a finger moving the reveal from {@code before} to {@code after} crossed the commit
     * point, either way: the moment the tick is felt.
     */
    public static boolean crossesCommit(boolean fromOpen, float before, float after) {
        float point = commitPoint(fromOpen);
        return (before < point) != (after < point);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) return 0f;
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
