package com.termux.app.wall;

/**
 * The slide's own clock. A {@code ValueAnimator} reads the wall clock, so a frame that takes 70 ms
 * — the first frame of a slide, which pre-rolls the arriving place's keyboard and lays the page
 * out; the settle frames at its end — moves the curve on by 70 ms and the take-off is simply
 * never drawn. The same 560 ms curve then read as 0.45 s into the terminal and 0.55 s into Home
 * (pong, 2026-09-28), because the two arrivals cost different frames, not because either was
 * given a different way. The slide instead advances by at most {@link #MAX_FRAME_STEP_MS} a frame:
 * a long frame stretches the slide by what it cost over that, and every frame of the curve is
 * drawn whatever the destination.
 *
 * <p>Pure: the layout feeds it the ticker's play time and reads the fraction back.
 */
final class WallSlideClock {

    /**
     * Two frames at 60 Hz. A single dropped frame passes through unclamped, so an ordinary slide
     * under light load runs to time; only a frame that missed two vsyncs is held back.
     */
    static final long MAX_FRAME_STEP_MS = 32L;

    private WallSlideClock() {}

    /** {@code elapsedMs} moved on by one frame of {@code frameDeltaMs}, never by more than the step. */
    static long advance(long elapsedMs, long frameDeltaMs) {
        return elapsedMs + Math.min(Math.max(0L, frameDeltaMs), MAX_FRAME_STEP_MS);
    }

    /** How far along a slide of {@code durationMs} the clock is, 0 to 1. */
    static float fraction(long elapsedMs, long durationMs) {
        if (durationMs <= 0L || elapsedMs >= durationMs) return 1f;
        if (elapsedMs <= 0L) return 0f;
        return elapsedMs / (float) durationMs;
    }
}
