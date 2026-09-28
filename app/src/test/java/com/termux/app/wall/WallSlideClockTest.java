package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The slide's clock: every frame moves it on by what the frame took, up to a bounded step, so a
 * long frame stretches the slide instead of skipping part of its curve — and a slide into the
 * terminal, whose first frame pre-rolls the keyboard, plays the same way as one into Home.
 */
public class WallSlideClockTest {

    private static final float EPS = 1e-4f;

    @Test
    public void ordinaryFramesAdvanceTheClockByWhatTheyTook() {
        assertEquals(16L, WallSlideClock.advance(0L, 16L));
        assertEquals(41L, WallSlideClock.advance(33L, 8L));
        // One dropped frame at 60 Hz passes through whole.
        assertEquals(32L, WallSlideClock.advance(0L, 32L));
    }

    @Test
    public void aLongFrameIsHeldToTheStep() {
        assertEquals(WallSlideClock.MAX_FRAME_STEP_MS, WallSlideClock.advance(0L, 77L));
        assertEquals(100L + WallSlideClock.MAX_FRAME_STEP_MS, WallSlideClock.advance(100L, 400L));
    }

    @Test
    public void aClockNeverRunsBackwards() {
        assertEquals(50L, WallSlideClock.advance(50L, 0L));
        assertEquals(50L, WallSlideClock.advance(50L, -20L));
    }

    @Test
    public void theFractionIsTheClockOverTheWayAndClampsAtBothEnds() {
        assertEquals(0f, WallSlideClock.fraction(0L, 560L), EPS);
        assertEquals(0.5f, WallSlideClock.fraction(280L, 560L), EPS);
        assertEquals(1f, WallSlideClock.fraction(560L, 560L), EPS);
        assertEquals(1f, WallSlideClock.fraction(900L, 560L), EPS);
        assertEquals(0f, WallSlideClock.fraction(-5L, 560L), EPS);
        // No way at all is already there.
        assertEquals(1f, WallSlideClock.fraction(0L, 0L), EPS);
    }

    @Test
    public void twoSlidesWithDifferentFrameCostsDrawTheSameCurve() {
        // Into Home: every frame on time. Into the terminal: a 77 ms pre-roll frame first, then
        // a run of ordinary frames. Both clocks pass through the same fractions; the second one
        // simply takes one more frame's worth of wall-clock time to get there.
        long duration = 560L;
        long home = 0L;
        long terminal = 0L;
        home = WallSlideClock.advance(home, 16L);
        terminal = WallSlideClock.advance(terminal, 77L);
        // The pre-roll frame moved the terminal's slide on by the step, not by 77 ms.
        assertEquals(WallSlideClock.MAX_FRAME_STEP_MS, terminal);
        for (int i = 0; i < 20; i++) {
            home = WallSlideClock.advance(home, 16L);
            terminal = WallSlideClock.advance(terminal, 16L);
        }
        long lag = WallSlideClock.MAX_FRAME_STEP_MS - 16L;
        assertEquals(home + lag, terminal);
        assertEquals(WallSlideClock.fraction(home, duration) + lag / (float) duration,
            WallSlideClock.fraction(terminal, duration), EPS);
        // Neither has been thrown to the end by the long frame.
        assertEquals(336L, home);
    }
}
