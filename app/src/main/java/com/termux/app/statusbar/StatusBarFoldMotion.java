package com.termux.app.statusbar;

/**
 * The arithmetic of the bar's fold: how far a finger has it open, how long the release takes to
 * land, and when the open bar's content is allowed to show.
 *
 * <p>The fold used to run 260 ms on a curve that spent a quarter of the way in its first frame
 * and had the content's alpha ride the height, so the open bar was all but drawn 40 ms after the
 * swipe (pong recording 2026-09-28, 16.96 to 17.00 s): a jump, however long the animator said it
 * ran. The fold is now the finger's while the finger is down, lands on the shared settle curve
 * ({@code Motion.settle}) for a time set by what is left of the way and how fast the finger let
 * go, and shows the clock and the cards only over the last stretch of the opening, so half-drawn
 * content is never on screen. Every number is a function of the bar's height, so a drag, a
 * release, a reversal and a takeover all read the same one.
 *
 * <p>Pure: no views, so it is read and tested as arithmetic.
 */
public final class StatusBarFoldMotion {

    private StatusBarFoldMotion() {}

    /** The whole way, compact to open, on the settle curve. */
    public static final long FULL_MS = 280L;
    /** A release with next to nothing left to travel still eases in rather than snapping. */
    public static final long MIN_MS = 120L;
    /**
     * The expansion the open bar's content starts to show at: it rises and fades in over the last
     * two fifths of the opening, once the bar has the height to hold it (SPEC §6 M3).
     */
    public static final float CONTENT_REVEAL_START = 0.6f;

    /** The bar's height under a finger that has travelled {@code towardOpenPx} since it went down. */
    public static int heightForDrag(int startHeightPx, float towardOpenPx, int collapsedHeightPx,
                                    int expandedHeightPx) {
        int low = Math.min(collapsedHeightPx, expandedHeightPx);
        int high = Math.max(collapsedHeightPx, expandedHeightPx);
        float height = startHeightPx + (Float.isNaN(towardOpenPx) ? 0f : towardOpenPx);
        return Math.round(Math.max(low, Math.min(high, height)));
    }

    /** How far open the bar is at {@code heightPx}, 0 compact to 1 expanded. */
    public static float expansion(int heightPx, int collapsedHeightPx, int expandedHeightPx) {
        if (expandedHeightPx == collapsedHeightPx) return 0f;
        return clamp01((heightPx - collapsedHeightPx)
            / (float) (expandedHeightPx - collapsedHeightPx));
    }

    /**
     * How much of the open bar's content shows at {@code expansion}: none until
     * {@link #CONTENT_REVEAL_START}, all of it at 1. Its rise reads the same number, as
     * {@code 1 - contentAlpha}.
     */
    public static float contentAlpha(float expansion) {
        return clamp01((clamp01(expansion) - CONTENT_REVEAL_START) / (1f - CONTENT_REVEAL_START));
    }

    /**
     * How long the release takes to land, from {@code remainingFraction} of the whole way still to
     * go, at {@code velocityTowardTarget} whole ways per second (0 or less for a release that was
     * not moving toward the target). By distance alone it is the whole time for the whole way and
     * proportionally less for less; a flick that would cover the rest sooner is honoured down to
     * {@link #MIN_MS}, so a fast swipe lands fast and a slow one glides.
     */
    public static long durationMs(float remainingFraction, float velocityTowardTarget) {
        float remaining = clamp01(Float.isNaN(remainingFraction) ? 1f : remainingFraction);
        long byDistance = Math.max(MIN_MS, Math.round(FULL_MS * remaining));
        if (!(velocityTowardTarget > 0f) || Float.isInfinite(velocityTowardTarget)) {
            return byDistance;
        }
        long byVelocity = Math.round(remaining / velocityTowardTarget * 1000f);
        return Math.max(MIN_MS, Math.min(byDistance, byVelocity));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
