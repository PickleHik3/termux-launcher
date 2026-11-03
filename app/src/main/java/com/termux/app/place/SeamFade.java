package com.termux.app.place;

/**
 * How a surface that stays where it is on the screen while the wall crosses between Home and the
 * Workspace (the navigation strip under the pill) goes from the committed half's dress to the
 * other half's: by alpha alone, following the finger, over the middle of the way, so no frame
 * switches it whole. At either end of the way it wears exactly that end's dress, so the settle
 * shows nothing new. Builds nothing.
 */
public final class SeamFade {

    /** Where along the way to the other half the fade starts and ends. */
    public static final float START = 0.2f;
    public static final float END = 0.8f;

    private SeamFade() {}

    /**
     * How much of the other half's dress shows at {@code progress} (0 to 1) into the other half:
     * 0 up to {@link #START}, 1 from {@link #END}, a smoothstep between. A drag that reverses reads
     * it back down the same curve.
     */
    public static float share(float progress) {
        if (Float.isNaN(progress) || progress <= START) return 0f;
        if (progress >= END) return 1f;
        float t = (progress - START) / (END - START);
        return t * t * (3f - 2f * t);
    }

    /**
     * How much of the surface shows at {@code share} when it stands on one end only or on both:
     * fading in toward a half that has it, out toward one that has none, whole where both do.
     */
    public static float presence(boolean hereShown, boolean thereShown, float share) {
        float s = share < 0f ? 0f : share > 1f ? 1f : share;
        return (hereShown ? 1f - s : 0f) + (thereShown ? s : 0f);
    }
}
