package com.termux.app.chrome.scene;

/**
 * Pure rules of the wallpaper scene prototype: how the three debug properties read, and the
 * ambient frame-skip gate the layer's Choreographer callback asks on every vsync.
 *
 * <p>{@code debug.tl.scene}: unset or {@code off} is today's launcher, {@code contour} the scene.
 * {@code debug.tl.scene_rate}: ambient frames per second when nothing is happening, {@code 30}
 * (default), {@code 90}, or {@code 0} for no ambient motion. {@code debug.tl.scene_glass}:
 * {@code lowres} (default) or {@code still}.</p>
 */
public final class SceneFramePolicy {

    public static final String PROP_SCENE = "debug.tl.scene";
    public static final String PROP_RATE = "debug.tl.scene_rate";
    public static final String PROP_GLASS = "debug.tl.scene_glass";

    public static final int DEFAULT_RATE_HZ = 30;
    /** A callback may land this much before the period is up and still draw: vsync jitter. */
    static final long SLACK_NS = 4_000_000L;

    private SceneFramePolicy() {}

    public static boolean sceneOn(String value) {
        return "contour".equals(value);
    }

    /** 0, 30 or 90 (anything else reads as 30), the ambient rate in Hz. */
    public static int parseRate(String value) {
        if ("0".equals(value)) return 0;
        if ("90".equals(value)) return 90;
        return DEFAULT_RATE_HZ;
    }

    /** True for {@code lowres} and anything unrecognised; false only for {@code still}. */
    public static boolean glassLowRes(String value) {
        return !"still".equals(value);
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
}
