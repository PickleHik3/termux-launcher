package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One short, bounded animation a CRT pane plays when something happens to it: a bell, the pane
 * opening, it taking focus, its process dying. Never on a timer: each runs once, for its own
 * duration, from the moment it was fired, and the pane draws frames only while one is live.
 * Events the user or their shell caused play at once; only the ambient tear waits out the typing
 * hold ({@link Kind#waitsForQuiet}).
 * Pure arithmetic; {@link RetroEffectBinder} drives the frames and {@link PaneRetroEffect} draws
 * it from {@link RetroUniforms#event}, {@link RetroUniforms#eventT} and {@link RetroUniforms#seed}.
 */
public final class PaneScreenEvent {

    /** How long after a key commit or a scroll no ambient event fires (spec decision 3). */
    public static final long INPUT_QUIET_MS = 1500L;

    /** What happened; {@link #id} is what the shader reads as {@code uEvent}. */
    public enum Kind {
        NONE(0, 0L, false),
        /** A bell: one horizontal tear band plus a red/blue split. */
        BELL_TEAR(1, 150L, false),
        /** The pane opened: a bright line widening to the screen. */
        POWER_ON(2, 400L, false),
        /** The pane closes: the picture squashes into a bright line that fades. */
        POWER_OFF(3, 250L, false),
        /** The pane took focus: a damped sideways shear. */
        FOCUS_WOBBLE(4, 300L, false),
        /** The pane's process died: a burst of snow. */
        DEATH_SNOW(5, 300L, false),
        /**
         * An ambient glitch under Idle animations ({@link PaneAmbient}): the bell tear at half its
         * shift, at the Idle glitch rate.
         */
        AMBIENT_TEAR(6, 200L, true);

        public final int id;
        public final long durationMs;
        /**
         * Whether it holds within {@link #INPUT_QUIET_MS} of a key or a scroll: only the ambient
         * parts do; an event answering something the user or their shell did plays at once.
         */
        public final boolean waitsForQuiet;

        Kind(int id, long durationMs, boolean waitsForQuiet) {
            this.id = id;
            this.durationMs = durationMs;
            this.waitsForQuiet = waitsForQuiet;
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
     * Whether an event of {@code kind} may start now: only on a CRT pane, only while Android
     * animates, only while the Screen events switch is on, and, for a kind that
     * {@link Kind#waitsForQuiet}, only when the user has neither typed nor scrolled within
     * {@link #INPUT_QUIET_MS}.
     */
    public static boolean mayFire(@NonNull Kind kind, boolean crt, boolean animatorsEnabled,
                                  boolean inputQuiet, boolean switchOn) {
        return crt && animatorsEnabled && switchOn && (inputQuiet || !kind.waitsForQuiet);
    }

    /**
     * One pane's live event: at most one at a time. A different kind replaces the live one; the
     * same kind while one is live is dropped, so a flood of bells restarts nothing.
     */
    public static final class Slot {
        @Nullable private PaneScreenEvent mLive;

        /** @return whether the event started (false for NONE, which empties the slot) */
        public boolean fire(@NonNull Kind kind, long nowUptime, float seed) {
            if (kind == Kind.NONE) {
                mLive = null;
                return false;
            }
            PaneScreenEvent live = mLive;
            if (live != null && live.kind == kind && !live.done(nowUptime)) return false;
            mLive = new PaneScreenEvent(kind, nowUptime, seed);
            return true;
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
