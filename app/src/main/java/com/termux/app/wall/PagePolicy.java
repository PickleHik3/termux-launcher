package com.termux.app.wall;

/**
 * Where a released page drag lands, and when a finger has set off along the page's axis: one rule
 * for the wall, the Home pages and the app drawer (#152), Launcher3's, so a turn feels the same
 * wherever a thumb makes it. Pure; each pane reads its finger and applies the answer.
 *
 * <p>The rule, read in this order:
 * <ul>
 *   <li>A fling decides first: a release at {@link #FLING_DP_PER_SEC} goes the way it was thrown
 *   whatever the distance, and once the drag is past {@link #RETURN_FRACTION} of the page an
 *   {@link #EASY_FLING_DP_PER_SEC} flick on is enough.</li>
 *   <li>A reversal past {@link #RETURN_FRACTION}: a finger moving back against its own travel at
 *   {@link #MIN_FLING_DP_PER_SEC} or more returns to where it started, rather than committing the
 *   page the drag was heading for or flying on to the page beyond.</li>
 *   <li>Otherwise position: past {@link #COMMIT_FRACTION} of the page commits, short of it
 *   returns.</li>
 * </ul>
 * Speeds are in dp, so a page turns at the same thumb speed on every density.
 */
public final class PagePolicy {

    /** Fraction of a page past which a released drag commits (Launcher3's significant move). */
    public static final float COMMIT_FRACTION = 0.4f;
    /**
     * Fraction of a page past which a finger that reverses returns to the original page, and
     * past which an easy flick on commits (Launcher3's return-to-original threshold).
     */
    public static final float RETURN_FRACTION = 0.33f;
    /** Release speed, in dp per second, that commits regardless of distance. */
    public static final float FLING_DP_PER_SEC = 500f;
    /** The flick on that commits once the drag is past {@link #RETURN_FRACTION}, dp per second. */
    public static final float EASY_FLING_DP_PER_SEC = 400f;
    /**
     * The least speed back against the travel, dp per second, that reads as a reversal: under
     * it a lift's wobble is no change of mind, and position decides.
     */
    public static final float MIN_FLING_DP_PER_SEC = 250f;

    /** The angle off the page's axis, in degrees, past which the touch slop is damped. */
    public static final float SLOP_DAMPING_ANGLE_DEG = 30f;
    /** How many slops a swipe more than {@link #SLOP_DAMPING_ANGLE_DEG} off the axis must go. */
    public static final float SLOP_DAMPING = 4f;
    private static final float SLOP_DAMPING_TAN =
        (float) Math.tan(Math.toRadians(SLOP_DAMPING_ANGLE_DEG));

    private PagePolicy() {}

    /**
     * {@link #turn(float, float, float, float, float, float)} with no least distance for a fling.
     */
    public static int turn(float travel, float velocityPxPerSec, float page, float density) {
        return turn(travel, travel, velocityPxPerSec, page, density, 0f);
    }

    /**
     * {@link #turn(float, float, float, float, float, float)} for a pane whose fling's least
     * distance is read on the page's own travel.
     */
    public static int turn(float travel, float velocityPxPerSec, float page, float density,
                           float flingLeastTravel) {
        return turn(travel, travel, velocityPxPerSec, page, density, flingLeastTravel);
    }

    /**
     * Where a released drag lands: {@code +1} on in the way {@code travel} is positive, {@code -1}
     * on in the way it is negative, {@code 0} back to the page it started on. Whether a page
     * stands that way is the pane's to know.
     *
     * @param travel            how far the page stands off its rest, signed, in any unit
     *                          {@code page} is in (px for a pager, progress for the drawer)
     * @param flingTravel       the travel the fling's least distance is read on, in the same unit:
     *                          {@code travel} itself, or the finger's own where the pane claimed
     *                          the drag late (the wall's slop lead)
     * @param velocityPxPerSec  release speed along the axis, px per second, signed as travel
     * @param page              the page's length along the axis, in travel's unit
     * @param density           px per dp, which the speeds are read in
     * @param flingLeastTravel  how far a fling must have gone to count, in travel's unit: the
     *                          pane's guard against a twitch (the wall's 25 dp, the drawer's
     *                          progress floor); 0 for none
     */
    public static int turn(float travel, float flingTravel, float velocityPxPerSec, float page,
                           float density, float flingLeastTravel) {
        if (!(page > 0f) || Float.isNaN(travel)) return 0;
        float v = Float.isNaN(velocityPxPerSec) ? 0f : velocityPxPerSec;
        float d = Math.max(0f, density);
        float way = Math.abs(travel) / page;
        boolean pastReturn = way >= RETURN_FRACTION;
        int travelSign = travel > 0f ? 1 : travel < 0f ? -1 : 0;
        int flingSign = v > 0f ? 1 : v < 0f ? -1 : 0;
        boolean reversing = travelSign != 0 && flingSign == -travelSign;
        float speed = Math.abs(v);
        boolean reaches = !(Math.abs(flingTravel) < flingLeastTravel);
        boolean flung = reaches && speed > 0f
            && (speed >= FLING_DP_PER_SEC * d
                || (pastReturn && !reversing && speed >= EASY_FLING_DP_PER_SEC * d));
        // Past a third of the page a finger turning back means it: home, never the page beyond.
        if (pastReturn && reversing && speed >= MIN_FLING_DP_PER_SEC * d) return 0;
        if (flung) return flingSign;
        if (way >= COMMIT_FRACTION) return travelSign;
        return 0;
    }

    /**
     * The touch slop a finger that has gone {@code along} the page's axis and {@code across} it
     * must pass along the axis before the swipe is the page's: the plain slop up to
     * {@link #SLOP_DAMPING_ANGLE_DEG} off the axis, {@link #SLOP_DAMPING} of it past that, so a
     * diagonal thumb scrolls what is under it rather than turning the page.
     */
    public static float slopPx(float along, float across, float slopPx) {
        float slop = Math.max(0f, slopPx);
        return Math.abs(across) > Math.abs(along) * SLOP_DAMPING_TAN ? slop * SLOP_DAMPING : slop;
    }

    /** Whether a finger at ({@code along}, {@code across}) is past {@link #slopPx} on the axis. */
    public static boolean pastSlop(float along, float across, float slopPx) {
        if (Float.isNaN(along) || Float.isNaN(across)) return false;
        return Math.abs(along) > slopPx(along, across, slopPx);
    }
}
