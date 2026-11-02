package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One short, bounded animation a CRT pane plays when something happens to it: a bell, the pane
 * opening, it taking focus, its process dying. Never on a timer: each runs once, for its own
 * duration, from the moment it was fired, and the pane draws frames only while one is live.
 * Pure arithmetic; {@link RetroEffectBinder} drives the frames and {@link PaneRetroEffect} draws
 * it from {@link RetroUniforms#event}, {@link RetroUniforms#eventT} and {@link RetroUniforms#seed}.
 */
public final class PaneScreenEvent {

    /** How long after a key commit or a scroll no event fires (spec decision 3). */
    public static final long INPUT_QUIET_MS = 1500L;

    /** What happened; {@link #id} is what the shader reads as {@code uEvent}. */
    public enum Kind {
        NONE(0, 0L),
        /** A bell: one horizontal tear band plus a red/blue split. */
        BELL_TEAR(1, 150L),
        /** The pane opened: a bright line widening to the screen. */
        POWER_ON(2, 400L),
        /** The pane closes: the picture squashes into a bright line that fades. */
        POWER_OFF(3, 250L),
        /** The pane took focus: a damped sideways shear. */
        FOCUS_WOBBLE(4, 300L),
        /** The pane's process died: a burst of snow. */
        DEATH_SNOW(5, 300L);

        public final int id;
        public final long durationMs;

        Kind(int id, long durationMs) {
            this.id = id;
            this.durationMs = durationMs;
        }
    }

    @NonNull public final Kind kind;
    public final long startUptime;
    /** 0..1, a random per event: where the tear falls, the snow's pattern. */
    public final float seed;

    public PaneScreenEvent(@NonNull Kind kind, long startUptime, float seed) {
        this.kind = kind;
        this.startUptime = startUptime;
        this.seed = seed;
    }

    /** 0 at the start, 1 at the end and after; 1 for NONE. */
    public float progress(long nowUptime) {
        if (kind.durationMs <= 0L) return 1f;
        float t = (nowUptime - startUptime) / (float) kind.durationMs;
        return t < 0f ? 0f : Math.min(t, 1f);
    }

    /** True once the event has run its full duration (and always for NONE). */
    public boolean done(long nowUptime) {
        return kind == Kind.NONE || nowUptime - startUptime >= kind.durationMs;
    }

    /**
     * Whether an event may start now: only on a CRT pane, only while Android animates, only when
     * the user has neither typed nor scrolled within {@link #INPUT_QUIET_MS}, and only while the
     * Screen events switch is on.
     */
    public static boolean mayFire(boolean crt, boolean animatorsEnabled, boolean inputQuiet,
                                  boolean switchOn) {
        return crt && animatorsEnabled && inputQuiet && switchOn;
    }

    /** One pane's live event: at most one at a time, a new one replacing the old. */
    public static final class Slot {
        @Nullable private PaneScreenEvent mLive;

        public void fire(@NonNull Kind kind, long nowUptime, float seed) {
            mLive = kind == Kind.NONE ? null : new PaneScreenEvent(kind, nowUptime, seed);
        }

        /** The live event at {@code nowUptime}, or null (and the slot cleared) once it is done. */
        @Nullable
        public PaneScreenEvent live(long nowUptime) {
            PaneScreenEvent e = mLive;
            if (e != null && e.done(nowUptime)) {
                mLive = null;
                return null;
            }
            return e;
        }

        public void clear() {
            mLive = null;
        }
    }
}
