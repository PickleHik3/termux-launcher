package com.termux.app.chrome.wallpaper;

/**
 * What the lock-screen live wallpaper ({@link LockLiveWallpaperService}) draws, frame by frame:
 * the lock slot's background, calmer and dimmed so the clock and notifications stay legible
 * (project-docs/active/animated-wallpaper/lock-live-wallpaper.md, decision 3).
 *
 * <p>Plain Java, so every rule is testable with numbers. It is separate from the in-app
 * {@link WallpaperDirector} and never touches it; it reuses that class's rates and its clock
 * maths (time wraps on whole periods, phase integrates energy, a frame delta is clamped so a
 * resume never jumps).</p>
 *
 * <p>Life of one visible session: {@link #becameVisible} eases energy from the rest pose up to
 * {@link #LOCK_ENERGY} over {@link #WAKE_EASE_MS}; {@link #unlockStarted} eases energy and dim
 * back to 0 over {@link #UNLOCK_SETTLE_MS}, then snaps the phase to 0 (the rest pose, the same
 * picture as the home still), after which {@link #frame} reports fps 0 and the engine stops
 * drawing until it is visible again. Not thread safe: call it from the engine's thread.</p>
 */
final class LockWallpaperDirector {

    /** How far from the rest pose the lock look sits, 0..1 (the launcher plays at 1). */
    static final float LOCK_ENERGY = 0.45f;
    /** The lock look's dim (uDim), about 20 %. */
    static final float LOCK_DIM = 0.2f;
    /** Ease from the rest pose up to the lock look when the lock screen shows. */
    static final long WAKE_EASE_MS = 600;
    /** Ease from the lock look down to the rest pose once an unlock starts. */
    static final long UNLOCK_SETTLE_MS = 250;

    private static final long MAX_DELTA_NANOS = 100_000_000L;
    private static final double DAY_SECONDS = 86_400.0;
    private static final long NANOS_PER_MS = 1_000_000L;

    private static final int HIDDEN = 0;
    private static final int WAKING = 1;
    private static final int CALM = 2;
    private static final int SETTLING = 3;
    private static final int SETTLED = 4;

    /**
     * The rate for the phone's conditions, as the launcher's: 0 (draw the current frame and stop)
     * under battery saver, reduced motion, the kill switch or a phone at thermal moderate or worse;
     * {@link WallpaperDirector#PRESSURE_FPS} when slightly warm or low on battery while
     * discharging; else {@link WallpaperDirector#MAX_FPS}.
     */
    static int fps(WallpaperDirector.Thermal thermal, boolean batteryLowDischarging, boolean powerSave,
                   boolean reducedMotion, boolean killSwitch) {
        if (killSwitch || powerSave || reducedMotion || thermal == WallpaperDirector.Thermal.MODERATE_OR_WORSE) {
            return 0;
        }
        if (thermal == WallpaperDirector.Thermal.LIGHT || batteryLowDischarging) {
            return WallpaperDirector.PRESSURE_FPS;
        }
        return WallpaperDirector.MAX_FPS;
    }

    /**
     * Energy and dim of the lock look at {@code elapsedMs} into a wake ease ({@code settling}
     * false) or an unlock settle ({@code settling} true), as {energy, dim}.
     */
    static float[] look(boolean settling, long elapsedMs) {
        if (settling) {
            float e = 1f - smooth(elapsedMs / (float) UNLOCK_SETTLE_MS);
            return new float[] {LOCK_ENERGY * e, LOCK_DIM * e};
        }
        float e = smooth(elapsedMs / (float) WAKE_EASE_MS);
        // The dim is on from the first frame: the clock is legible at once.
        return new float[] {LOCK_ENERGY * e, LOCK_DIM};
    }

    private double wrapSeconds;
    private int[] palette;
    private int fps = WallpaperDirector.MAX_FPS;

    private double time;
    private double phase;
    private float lastEnergy;
    private long lastFrameNanos = -1;

    private int mode = HIDDEN;
    private long modeStartNanos;

    LockWallpaperDirector(float periodSeconds, int[] palette) {
        setBackground(periodSeconds, palette);
    }

    /** A new background: new loop period and colours; the clock starts from rest. */
    void setBackground(float periodSeconds, int[] palette) {
        wrapSeconds = (periodSeconds > 0f && periodSeconds <= DAY_SECONDS)
            ? Math.floor(DAY_SECONDS / periodSeconds) * periodSeconds
            : DAY_SECONDS;
        this.palette = palette.clone();
        time = 0;
        phase = 0;
    }

    /** The rate for the current conditions; see {@link #fps(WallpaperDirector.Thermal, boolean, boolean, boolean, boolean)}. */
    void setFps(int fps) {
        this.fps = Math.max(0, fps);
    }

    /** The lock screen shows the wallpaper (screen on, keyguard up). */
    void becameVisible(long nowNanos) {
        mode = WAKING;
        modeStartNanos = nowNanos;
        lastFrameNanos = -1;
    }

    /** The wallpaper is hidden (screen off, AOD, an app on top, or unlocked). */
    void becameHidden() {
        if (mode == SETTLING) {
            phase = 0;
        }
        mode = HIDDEN;
        lastFrameNanos = -1;
    }

    /** The unlock started: settle to the rest pose. A second call keeps the first start. */
    void unlockStarted(long nowNanos) {
        if (mode == SETTLING || mode == SETTLED || mode == HIDDEN) return;
        modeStartNanos = nowNanos;
        if (fps == 0) {
            // Paused: no frames to ease over, so the next frame is the rest pose.
            mode = SETTLED;
            phase = 0;
            return;
        }
        mode = SETTLING;
    }

    /** True while an unlock settle is running or done. */
    boolean settling() {
        return mode == SETTLING || mode == SETTLED;
    }

    /**
     * The state to draw at this frame time. {@link WallpaperDirector.Frame#fps} 0 means draw this
     * frame and stop: hidden, settled, or paused by the conditions.
     */
    WallpaperDirector.Frame frame(long frameTimeNanos) {
        boolean moving = mode == WAKING || mode == CALM || mode == SETTLING;
        if (moving && fps > 0 && lastFrameNanos >= 0) {
            long delta = Math.min(frameTimeNanos - lastFrameNanos, MAX_DELTA_NANOS);
            if (delta > 0) {
                time += delta / 1e9;
                if (time >= wrapSeconds) time -= wrapSeconds;
                phase += delta / 1e9 * lastEnergy;
                if (phase >= wrapSeconds) phase -= wrapSeconds;
            }
        }
        lastFrameNanos = frameTimeNanos;

        float energy;
        float dim;
        int outFps = fps;
        long elapsedMs = Math.max(0L, (frameTimeNanos - modeStartNanos) / NANOS_PER_MS);
        switch (mode) {
            case WAKING: {
                if (elapsedMs >= WAKE_EASE_MS) mode = CALM;
                float[] l = look(false, elapsedMs);
                energy = l[0];
                dim = l[1];
                break;
            }
            case CALM:
                energy = LOCK_ENERGY;
                dim = LOCK_DIM;
                break;
            case SETTLING: {
                if (elapsedMs >= UNLOCK_SETTLE_MS) {
                    mode = SETTLED;
                    phase = 0; // the rest pose, as the home still is
                    energy = 0f;
                    dim = 0f;
                    outFps = 0;
                } else {
                    float[] l = look(true, elapsedMs);
                    energy = l[0];
                    dim = l[1];
                }
                break;
            }
            case SETTLED:
                energy = 0f;
                dim = 0f;
                outFps = 0;
                break;
            default: // HIDDEN
                energy = 0f;
                dim = LOCK_DIM;
                outFps = 0;
                break;
        }
        lastEnergy = energy;

        WallpaperDirector.Moment[] moments = new WallpaperDirector.Moment[WallpaperDirector.MAX_MOMENTS];
        for (int i = 0; i < moments.length; i++) moments[i] = WallpaperDirector.Moment.NONE;
        return new WallpaperDirector.Frame(outFps, (float) time, (float) phase, energy, dim, palette,
            moments, false);
    }

    private static float smooth(float f) {
        float x = f < 0f ? 0f : (f > 1f ? 1f : f);
        return x * x * (3f - 2f * x);
    }
}
