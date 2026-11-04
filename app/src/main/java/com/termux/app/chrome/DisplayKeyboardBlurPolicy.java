package com.termux.app.chrome;

/**
 * How the keyboard frosts a Display page: the compositor's blur radius under the keyboard's bed,
 * or none, in which case the bed is plain see-through over the live display.
 *
 * <p>Over a Display page the keyboard wears its Display-pages look (Opacity, Tint, Frost). The
 * bed's Opacity and Tint are drawn by the keyboard host either way; only the frost needs Android:
 * a blur layer under the bed that the compositor fills with the display behind it, live. Where
 * Android has turned window blur off (below API 31, {@code isCrossWindowBlurEnabled()} false,
 * battery saver) or the look's Frost is 0, the radius is 0 and nothing is blurred, so the
 * fallback differs from the blur by the frost alone.
 *
 * <p>Pure, so the answer can be read and tested without a window; {@link DisplayKeyboardBlur}
 * applies it.
 */
public final class DisplayKeyboardBlurPolicy {

    private DisplayKeyboardBlurPolicy() {}

    /** The first API level whose compositor blurs a layer's background (Android 12). */
    public static final int MIN_SDK = 31;

    /**
     * The blur radius, in pixels, the compositor draws under the keyboard's bed; 0 for none.
     *
     * @param sdk                    {@code Build.VERSION.SDK_INT}
     * @param crossWindowBlurEnabled {@code WindowManager.isCrossWindowBlurEnabled()}, as its
     *                               listener last reported it
     * @param powerSave              {@code PowerManager.isPowerSaveMode()}
     * @param frostDp                the keyboard look's Frost on the Display page, in dp
     * @param density                display density, pixels per dp
     */
    public static int blurRadiusPx(int sdk, boolean crossWindowBlurEnabled, boolean powerSave,
                                   int frostDp, float density) {
        if (sdk < MIN_SDK || !crossWindowBlurEnabled || powerSave) return 0;
        if (frostDp <= 0 || !(density > 0f)) return 0;
        return Math.round(frostDp * density);
    }

    /**
     * The one corner radius of the blur layer under a docked keyboard's slice, whose outline may
     * round some corners and not others: the largest, so the frost never shows outside the
     * outline. A square corner then leaves a sliver of the bed unfrosted, which reads as the
     * see-through bed it is.
     */
    public static float cornerPx(float topLeft, float topRight, float bottomRight,
                                 float bottomLeft) {
        float max = Math.max(Math.max(topLeft, topRight), Math.max(bottomRight, bottomLeft));
        return max > 0f ? max : 0f;
    }

    /** Whether the bed frosts the display behind it, or is plain see-through. */
    public static boolean frosts(int sdk, boolean crossWindowBlurEnabled, boolean powerSave,
                                 int frostDp, float density) {
        return blurRadiusPx(sdk, crossWindowBlurEnabled, powerSave, frostDp, density) > 0;
    }
}
