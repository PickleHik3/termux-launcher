package com.termux.app.chrome.scene;

/**
 * Pure rules of the moving wallpaper's layer: how the debug properties read, and the frame-skip
 * gate the layer's Choreographer callback asks on every vsync.
 *
 * <p>{@code debug.tl.shader_file}: an absolute path; when set, that file runs as the active shader
 * (over the user's choice) and is reloaded when its time stamp moves.
 * {@code debug.tl.shader_guard}: {@code on} (default) or {@code off}; off turns off the heavy-frame
 * quality step-down and the hold at Quarter (context-loss handling and the pending marker stay on).
 * For emulators, whose GL runs on the CPU and always reads as heavy.
 * {@code debug.tl.scene_rate}: frames per second, overriding the shader's rate: {@code 15},
 * {@code 30}, {@code 60}, {@code 90} (every vsync), or {@code 0} for no motion. {@code debug.tl.scene_glass}:
 * {@code lowres} (default) or {@code still}. {@code debug.tl.scene_glass_rate}: the low-res
 * pass's frames per second, half the scene's ambient rate by default (15 at 30, 45 at 90, 0 when
 * the scene is still), 0 for none. {@code debug.tl.scene_glass_div}: the low-res pass's divisor of the
 * screen, 2, 4 (default) or 8.</p>
 */
public final class SceneFramePolicy {

    public static final String PROP_SHADER_FILE = "debug.tl.shader_file";
    public static final String PROP_SHADER_GUARD = "debug.tl.shader_guard";
    public static final String PROP_RATE = "debug.tl.scene_rate";
    public static final String PROP_GLASS = "debug.tl.scene_glass";
    public static final String PROP_GLASS_RATE = "debug.tl.scene_glass_rate";

    public static final int DEFAULT_RATE_HZ = 30;
    /** The glass rate at the default scene rate: half of it. */
    public static final int DEFAULT_GLASS_RATE_HZ = DEFAULT_RATE_HZ / 2;
    public static final String PROP_GLASS_DIV = "debug.tl.scene_glass_div";
    public static final int DEFAULT_GLASS_DIV = 4;
    /** A callback may land this much before the period is up and still draw: vsync jitter. */
    static final long SLACK_NS = 4_000_000L;

    private SceneFramePolicy() {}

    /** 0, 15, 30, 60 or 90 (anything else reads as 30), the ambient rate in Hz. */
    public static int parseRate(String value) {
        if (value != null) {
            switch (value.trim()) {
                case "0": return 0;
                case "15": return 15;
                case "60": return 60;
                case "90": return 90;
                default: break;
            }
        }
        return DEFAULT_RATE_HZ;
    }

    /** {@code PowerManager.THERMAL_STATUS_SEVERE}: at this status and above the scene holds still. */
    static final int THERMAL_SEVERE = 3;

    /**
     * Whether the scene stops animating and holds its last frame: battery saver is on, or the
     * device's thermal status is severe or worse. When neither holds, it goes on from where it stopped.
     */
    public static boolean holdAtStill(boolean powerSaveMode, int thermalStatus) {
        return powerSaveMode || thermalStatus >= THERMAL_SEVERE;
    }

    /** {@code debug.tl.shader_guard}: on unless the value is {@code off}. */
    public static boolean shaderGuard(String value) {
        return value == null || !"off".equals(value.trim());
    }

    /** True for {@code lowres} and anything unrecognised; false only for {@code still}. */
    public static boolean glassLowRes(String value) {
        return !"still".equals(value);
    }

    /** Half the scene's ambient rate: the glass refresh when no property sets it. */
    public static int defaultGlassRate(int sceneRateHz) {
        return Math.max(0, sceneRateHz) / 2;
    }

    /**
     * The low-resolution glass pass's own rate: 0..90; unset, empty or unreadable (or negative)
     * reads as half {@code sceneRateHz}.
     */
    public static int parseGlassRate(String value, int sceneRateHz) {
        int fallback = defaultGlassRate(sceneRateHz);
        if (value == null) return fallback;
        try {
            int hz = Integer.parseInt(value.trim());
            return hz < 0 ? fallback : Math.min(hz, 90);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The low-res pass's divisor of the screen: 2, 4 or 8; anything else reads as 4. */
    public static int parseGlassDiv(String value) {
        if (value != null) {
            String v = value.trim();
            if ("2".equals(v)) return 2;
            if ("8".equals(v)) return 8;
        }
        return DEFAULT_GLASS_DIV;
    }

    /** The ambient period: 0 draws on every vsync, {@link Long#MAX_VALUE} never. */
    public static long ambientPeriodNs(int rateHz) {
        if (rateHz <= 0) return Long.MAX_VALUE;
        if (rateHz >= 90) return 0L;
        return 1_000_000_000L / rateHz;
    }

    /**
     * Whether this vsync draws: always while a reaction is still animating, otherwise only when
     * the ambient period has run out since the last draw.
     */
    public static boolean shouldDraw(boolean eventsActive, long ambientPeriodNs,
                                     long lastDrawNs, long nowNs) {
        if (eventsActive) return true;
        if (ambientPeriodNs == Long.MAX_VALUE) return false;
        if (ambientPeriodNs == 0L) return true;
        return nowNs - lastDrawNs >= ambientPeriodNs - SLACK_NS;
    }

    /**
     * How long the next frame callback may sleep: 0 while a reaction animates or the ambient rate
     * is every vsync, otherwise the time until the next ambient frame is due (less the vsync
     * slack), rounded up to whole milliseconds. Never negative.
     */
    public static long nextCallbackDelayMs(boolean eventsActive, long ambientPeriodNs,
                                           long lastDrawNs, long nowNs) {
        if (eventsActive || ambientPeriodNs <= 0L || ambientPeriodNs == Long.MAX_VALUE) return 0L;
        long waitNs = lastDrawNs + ambientPeriodNs - SLACK_NS - nowNs;
        return waitNs <= 0L ? 0L : (waitNs + 999_999L) / 1_000_000L;
    }
}
