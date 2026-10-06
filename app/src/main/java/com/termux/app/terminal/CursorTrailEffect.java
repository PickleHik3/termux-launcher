package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.termux.view.KittyCursorTrail;

/**
 * A cursor trail style that draws the move the trail just accepted on its own clock, in place of
 * kitty's quad: Motion blur, Railgun, Torpedo and Comet. {@link PaneMotionOverlayView} starts one
 * on every accepted move, asks for frames while it is {@link #alive}, and repaints its
 * {@link #bounds}; each one ends on its own, so a settled trail asks for nothing.
 */
interface CursorTrailEffect {

    /** Drop whatever is in flight. */
    void reset();

    /**
     * A move the trail accepted at {@code nowMs}: the cursor's rect before and after it, in the
     * overlay's y-down pixels. Starts a new effect in place of any still showing. {@code config}
     * carries the user's {@code cursor_trail_decay}, which sets how long an effect lasts.
     */
    void start(float fromL, float fromT, float fromR, float fromB,
               float toL, float toT, float toR, float toB, long nowMs,
               @NonNull KittyCursorTrail.Config config);

    /** Whether an effect was started and has not yet ended; clears itself once it has. */
    boolean alive(long nowMs);

    /** The box the last started effect can paint inside. */
    @NonNull
    RectF bounds();

    /**
     * Paints the effect as it stands at {@code nowMs}; the caller has already masked the cursor
     * cell out.
     *
     * @param color    trail colour; its alpha channel, when set, scales everything
     * @param strength the trail's own opacity, 0..1 (it fades while the cursor is hidden)
     */
    void draw(@NonNull Canvas canvas, int color, float strength, long nowMs);
}
