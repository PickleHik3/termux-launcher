package com.termux.app.chrome.wallpaper;

/**
 * What a generated background does, frame by frame.
 *
 * <p>The host (the activity and the frame clock) only feeds this class the phone's conditions and
 * the launcher's events, and writes each {@link Frame} to the shader's uniforms. Every rule lives
 * here: whether the picture plays at all, at what rate, how far it sits from its rest pose, when a
 * lock may run, and which short moments are showing. Nothing in it touches a window, so every case
 * is testable with plain numbers.</p>
 *
 * <p>All times are nanoseconds on one monotonic clock (the choreographer's frame time). Not thread
 * safe: call it from the UI thread.</p>
 *
 * <p>Allocation: {@link #frame} allocates one small {@link Frame} and one {@link Moment} per live
 * slot each call (empty slots share {@link Moment#NONE}). The palette array is copied only when the
 * palette is set, and each Frame hands out that same array, which the host must not modify.</p>
 */
public final class WallpaperDirector {

    /** Normal rate. */
    public static final int MAX_FPS = 30;
    /** Rate under light thermal pressure or low battery while discharging. */
    public static final int PRESSURE_FPS = 15;
    /** Moment slots the shader has. */
    public static final int MAX_MOMENTS = 2;

    /** How long the picture eases to its rest pose before a lock may run. */
    public static final long LOCK_SETTLE_MS = 350;
    /** How long the picture takes to come back after an unlock. */
    public static final long UNLOCK_MS = 900;
    /** How long an unlock that arrived while the launcher was hidden may wait to play. */
    public static final long UNLOCK_QUEUE_WINDOW_MS = 1000;
    /** Dim, 0..1, reached at the end of the lock settle. */
    public static final float LOCK_DIM = 0.5f;

    public static final int KIND_NONE = 0;
    public static final int KIND_PANE_OPEN = 1;
    public static final int KIND_PANE_CLOSE = 2;
    public static final int KIND_PAGE_CHANGE = 3;
    public static final int KIND_TOUCH = 4;
    public static final int KIND_BELL = 5;

    /** Moment lengths in milliseconds. */
    public static final long PANE_MS = 700;
    public static final long PAGE_MS = 600;
    public static final long TOUCH_MS = 1200;
    public static final long BELL_MS = 1500;

    /** A single frame delta is clamped to this, so a resume never makes the picture jump. */
    private static final long MAX_DELTA_NANOS = 100_000_000L;
    private static final double DAY_SECONDS = 86_400.0;
    private static final long NANOS_PER_MS = 1_000_000L;

    // Where the picture is between rest and full motion.
    private static final int PLAY = 0;       // energy 1, dim 0
    private static final int LOCKING = 1;    // easing to rest, waiting for the deadline
    private static final int LOCKED = 2;     // at rest and dimmed, the lock has been handed to the host
    private static final int UNLOCK_WAIT = 3; // unlock arrived while paused; rest pose until playing
    private static final int UNLOCKING = 4;  // easing back up

    /** Thermal band, as the host reads it from the system. */
    public enum Thermal { NONE, LIGHT, MODERATE_OR_WORSE }

    /** The phone's conditions. Immutable; build one with {@link #playing()} and set what differs. */
    public static final class Conditions {
        public final boolean fancierGlassActive, sdkSupported, animatedIdStored,
            managedPictureOnScreen, selfDrawnBackdrop;
        public final boolean visible, screenOn, powerSave, batteryLowDischarging, lazyMode,
            reducedMotion, killSwitch, rendererHealthy;
        public final Thermal thermal;

        private Conditions(Builder b) {
            fancierGlassActive = b.fancierGlassActive;
            sdkSupported = b.sdkSupported;
            animatedIdStored = b.animatedIdStored;
            managedPictureOnScreen = b.managedPictureOnScreen;
            selfDrawnBackdrop = b.selfDrawnBackdrop;
            visible = b.visible;
            screenOn = b.screenOn;
            powerSave = b.powerSave;
            batteryLowDischarging = b.batteryLowDischarging;
            lazyMode = b.lazyMode;
            reducedMotion = b.reducedMotion;
            killSwitch = b.killSwitch;
            rendererHealthy = b.rendererHealthy;
            thermal = b.thermal == null ? Thermal.NONE : b.thermal;
        }

        /** A builder starting from conditions under which the background plays at full rate. */
        public static Builder playing() {
            return new Builder();
        }

        /** Plain mutable fields; {@link #build()} freezes them. */
        public static final class Builder {
            public boolean fancierGlassActive = true, sdkSupported = true, animatedIdStored = true,
                managedPictureOnScreen = true, selfDrawnBackdrop = true;
            public boolean visible = true, screenOn = true, powerSave = false,
                batteryLowDischarging = false, lazyMode = false, reducedMotion = false,
                killSwitch = false, rendererHealthy = true;
            public Thermal thermal = Thermal.NONE;

            public Conditions build() {
                return new Conditions(this);
            }
        }
    }

    /** One short event drawn inside the shader. Kind {@link #KIND_NONE} means the slot is empty. */
    public static final class Moment {
        static final Moment NONE = new Moment(KIND_NONE, 0f, 0f, 0f, 0f, 0f);

        public final int kind;
        /** A rect (x, y, w, h) in shared-frame pixels; a point for touch (w = h = 0); x = direction for a page change. */
        public final float x, y, w, h;
        /** 0..1 over the moment's duration. */
        public final float progress;

        Moment(int kind, float x, float y, float w, float h, float progress) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.progress = progress;
        }
    }

    /** Everything the host writes to the shader for one frame. */
    public static final class Frame {
        /** 0 means paused: draw nothing new and stop the clock. */
        public final int fps;
        public final float timeSeconds;
        /**
         * Shader time integrated over energy: it slows to a stop as energy falls and picks up
         * again without a jump, and is 0 once a lock settles. For one-way motion (scrolls, rises).
         */
        public final float phaseSeconds;
        /** 0..1: how far the picture is from its rest pose. */
        public final float energy;
        /** 0..1. */
        public final float dim;
        /** Four ARGB colours; shared with the Director, do not modify. */
        public final int[] palette;
        /** Always {@link #MAX_MOMENTS} long. */
        public final Moment[] moments;
        /** True on exactly one frame per lock: the host should run the lock now. */
        public final boolean lockDue;

        Frame(int fps, float timeSeconds, float phaseSeconds, float energy, float dim, int[] palette,
              Moment[] moments, boolean lockDue) {
            this.fps = fps;
            this.timeSeconds = timeSeconds;
            this.phaseSeconds = phaseSeconds;
            this.energy = energy;
            this.dim = dim;
            this.palette = palette;
            this.moments = moments;
            this.lockDue = lockDue;
        }
    }

    private final double wrapSeconds;
    private int[] palette;
    private Conditions cond;

    private double time;
    private double phase;
    private float lastEnergy = 1f;
    private long lastFrameNanos = -1;
    private boolean lastFramePlayed;

    private int mode = PLAY;
    private long lockStartNanos;
    private boolean lockForced;
    private long unlockStartNanos;
    private long unlockQueuedNanos;
    private float unlockFromDim;
    private float lastDim;

    // Moment slots. Parallel arrays so an event allocates nothing.
    private final int[] slotKind = new int[MAX_MOMENTS];
    private final float[] slotX = new float[MAX_MOMENTS];
    private final float[] slotY = new float[MAX_MOMENTS];
    private final float[] slotW = new float[MAX_MOMENTS];
    private final float[] slotH = new float[MAX_MOMENTS];
    private final long[] slotStart = new long[MAX_MOMENTS];

    /**
     * @param periodSeconds the background's loop period; the clock wraps on a whole number of
     *                      periods no longer than a day, so the wrap is seamless
     * @param palette       the four ARGB colours captured when the background was chosen
     */
    public WallpaperDirector(float periodSeconds, int[] palette) {
        wrapSeconds = (periodSeconds > 0f && periodSeconds <= DAY_SECONDS)
            ? Math.floor(DAY_SECONDS / periodSeconds) * periodSeconds
            : DAY_SECONDS;
        setPalette(palette);
    }

    // --- inputs ---

    /** Sets the phone's conditions; call whenever any of them changes. */
    public void setConditions(Conditions c, long nowNanos) {
        cond = c;
    }

    /** Replaces the palette. Only the host calls this; the Director never changes it itself. */
    public void setPalette(int[] argb4) {
        palette = argb4.clone();
    }

    // --- events ---

    /**
     * The screen was unlocked. Plays at once when the background is playing; when it is paused only
     * because the launcher is not visible or the screen is off, it waits and starts on the first
     * playing frame within {@link #UNLOCK_QUEUE_WINDOW_MS}, else it expires. In any other state it
     * is dropped. A dropped or expired unlock still leaves the lock rest pose for normal play.
     */
    public void unlock(long nowNanos) {
        if (cond == null) return;
        if (fps(cond) > 0) {
            beginUnlock(nowNanos);
        } else if (gateOpen(cond) && (!cond.visible || !cond.screenOn)) {
            unlockFromDim = lastDim;
            unlockQueuedNanos = nowNanos;
            mode = UNLOCK_WAIT;
            lockForced = false;
        } else if (mode == LOCKED || mode == LOCKING) {
            mode = PLAY;
            lockForced = false;
        }
    }

    /**
     * The user asked to lock. Returns the delay in milliseconds before the lock should run: the
     * settle time while playing (the picture eases to rest meanwhile and {@link Frame#lockDue}
     * follows), 0 when paused, under reduced motion or outside the gate, where the host locks at once.
     */
    public long lockRequested(long nowNanos) {
        if (cond == null || fps(cond) == 0) return 0L;
        if (mode == LOCKING) {
            long left = LOCK_SETTLE_MS - (nowNanos - lockStartNanos) / NANOS_PER_MS;
            return Math.max(0L, left);
        }
        if (mode == LOCKED) return 0L;
        mode = LOCKING;
        lockStartNanos = nowNanos;
        lockForced = false;
        return LOCK_SETTLE_MS;
    }

    /** A second double tap: a pending lock becomes due on the next frame. */
    public void lockNow(long nowNanos) {
        if (mode == LOCKING) lockForced = true;
    }

    public void paneOpened(float l, float t, float r, float b, long nowNanos) {
        addMoment(KIND_PANE_OPEN, l, t, r - l, b - t, nowNanos);
    }

    public void paneClosed(float l, float t, float r, float b, long nowNanos) {
        addMoment(KIND_PANE_CLOSE, l, t, r - l, b - t, nowNanos);
    }

    /** @param direction -1 or +1; carried in the moment's x */
    public void pageChanged(int direction, long nowNanos) {
        addMoment(KIND_PAGE_CHANGE, direction, 0f, 0f, 0f, nowNanos);
    }

    public void touch(float x, float y, long nowNanos) {
        addMoment(KIND_TOUCH, x, y, 0f, 0f, nowNanos);
    }

    public void bell(float l, float t, float r, float b, long nowNanos) {
        addMoment(KIND_BELL, l, t, r - l, b - t, nowNanos);
    }

    // --- output ---

    /**
     * The state to draw at this frame time. Shader time advances by the real delta since the last
     * frame, only when both frames were playing and clamped to 100 ms, so it is kept across pauses
     * and a resume never jumps. Eases (unlock, lock) use smoothstep, 3f^2 - 2f^3.
     */
    public Frame frame(long frameTimeNanos) {
        int fps = cond == null ? 0 : fps(cond);
        boolean playing = fps > 0;

        if (playing && lastFramePlayed && lastFrameNanos >= 0) {
            long delta = Math.min(frameTimeNanos - lastFrameNanos, MAX_DELTA_NANOS);
            if (delta > 0) {
                time += delta / 1e9;
                if (time >= wrapSeconds) time -= wrapSeconds;
                // Phase advances at the previous frame's energy; a one-frame lag is invisible.
                phase += delta / 1e9 * lastEnergy;
                if (phase >= wrapSeconds) phase -= wrapSeconds;
            }
        }
        lastFramePlayed = playing;
        lastFrameNanos = frameTimeNanos;

        if (mode == UNLOCK_WAIT) {
            if (frameTimeNanos - unlockQueuedNanos > UNLOCK_QUEUE_WINDOW_MS * NANOS_PER_MS) {
                mode = PLAY;
            } else if (playing) {
                beginUnlock(frameTimeNanos);
            }
        }

        float energy = 1f;
        float dim = 0f;
        boolean lockDue = false;
        switch (mode) {
            case LOCKING: {
                long elapsed = frameTimeNanos - lockStartNanos;
                if (lockForced || elapsed >= LOCK_SETTLE_MS * NANOS_PER_MS) {
                    lockDue = true;
                    mode = LOCKED;
                    phase = 0; // the locked frame is the rest pose, as the system's still is
                    lockForced = false;
                    energy = 0f;
                    dim = LOCK_DIM;
                } else {
                    float e = smooth(elapsed / (float) (LOCK_SETTLE_MS * NANOS_PER_MS));
                    energy = 1f - e;
                    dim = LOCK_DIM * e;
                }
                break;
            }
            case LOCKED:
                energy = 0f;
                dim = LOCK_DIM;
                break;
            case UNLOCK_WAIT:
                energy = 0f;
                dim = unlockFromDim;
                break;
            case UNLOCKING: {
                long elapsed = frameTimeNanos - unlockStartNanos;
                if (elapsed >= UNLOCK_MS * NANOS_PER_MS) {
                    mode = PLAY;
                } else {
                    float e = smooth(Math.max(0f, elapsed) / (float) (UNLOCK_MS * NANOS_PER_MS));
                    energy = e;
                    dim = unlockFromDim * (1f - e);
                }
                break;
            }
            default:
                break;
        }
        lastDim = dim;
        lastEnergy = energy;

        Moment[] moments = new Moment[MAX_MOMENTS];
        for (int i = 0; i < MAX_MOMENTS; i++) {
            moments[i] = Moment.NONE;
            if (slotKind[i] == KIND_NONE) continue;
            if (!playing) {
                slotKind[i] = KIND_NONE;
                continue;
            }
            long dur = durationMs(slotKind[i]) * NANOS_PER_MS;
            long elapsed = frameTimeNanos - slotStart[i];
            if (elapsed >= dur) {
                slotKind[i] = KIND_NONE;
                continue;
            }
            float p = elapsed <= 0 ? 0f : elapsed / (float) dur;
            moments[i] = new Moment(slotKind[i], slotX[i], slotY[i], slotW[i], slotH[i], p);
        }

        return new Frame(fps, (float) time, (float) phase, energy, dim, palette, moments, lockDue);
    }

    /**
     * Why the background is not playing, or null when it is: "api", "fancier_glass_off", "killed"
     * (kill switch or unhealthy renderer), "inactive" (no id stored, managed picture not on screen
     * or backdrop not self-drawn), "paused" (any pause row).
     */
    public String reason() {
        if (cond == null) return "inactive";
        if (!cond.sdkSupported) return "api";
        if (!cond.fancierGlassActive) return "fancier_glass_off";
        if (cond.killSwitch || !cond.rendererHealthy) return "killed";
        if (!gateOpen(cond)) return "inactive";
        return fps(cond) == 0 ? "paused" : null;
    }

    // --- rules ---

    private static boolean gateOpen(Conditions c) {
        return c.fancierGlassActive && c.sdkSupported && c.animatedIdStored
            && c.managedPictureOnScreen && c.selfDrawnBackdrop;
    }

    /** 0 when the gate is closed or any pause row holds, else the rate for the current pressure. */
    private static int fps(Conditions c) {
        if (!gateOpen(c)) return 0;
        if (c.killSwitch || !c.rendererHealthy || !c.visible || !c.screenOn || c.lazyMode
            || c.powerSave || c.reducedMotion || c.thermal == Thermal.MODERATE_OR_WORSE) {
            return 0;
        }
        if (c.thermal == Thermal.LIGHT || c.batteryLowDischarging) return PRESSURE_FPS;
        return MAX_FPS;
    }

    private void beginUnlock(long startNanos) {
        unlockFromDim = mode == UNLOCK_WAIT ? unlockFromDim : lastDim;
        unlockStartNanos = startNanos;
        lockForced = false;
        mode = UNLOCKING;
    }

    private void addMoment(int kind, float x, float y, float w, float h, long nowNanos) {
        if (cond == null || fps(cond) == 0) return;
        int slot = -1;
        for (int i = 0; i < MAX_MOMENTS; i++) {
            boolean free = slotKind[i] == KIND_NONE
                || nowNanos - slotStart[i] >= durationMs(slotKind[i]) * NANOS_PER_MS;
            if (free) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            slot = 0;
            for (int i = 1; i < MAX_MOMENTS; i++) {
                if (slotStart[i] < slotStart[slot]) slot = i;
            }
        }
        slotKind[slot] = kind;
        slotX[slot] = x;
        slotY[slot] = y;
        slotW[slot] = w;
        slotH[slot] = h;
        slotStart[slot] = nowNanos;
    }

    private static long durationMs(int kind) {
        switch (kind) {
            case KIND_PANE_OPEN:
            case KIND_PANE_CLOSE:
                return PANE_MS;
            case KIND_PAGE_CHANGE:
                return PAGE_MS;
            case KIND_TOUCH:
                return TOUCH_MS;
            case KIND_BELL:
                return BELL_MS;
            default:
                return 0L;
        }
    }

    private static float smooth(float f) {
        float x = f < 0f ? 0f : (f > 1f ? 1f : f);
        return x * x * (3f - 2f * x);
    }
}
