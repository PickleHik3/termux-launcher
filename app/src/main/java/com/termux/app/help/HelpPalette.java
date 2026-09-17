package com.termux.app.help;

import android.graphics.Color;

/**
 * One colour per hint, shared by the box around the control and the border and title of the
 * card that explains it. Hues are spread evenly round the wheel from the place accent's, so
 * neighbours differ as much as the count allows; the box's colour is bright, for the dimmed
 * screen, and the title's is deepened on a light card surface so it still reads.
 */
public final class HelpPalette {
    private HelpPalette() {}

    /** The bright colour for the dashed box, index {@code i} of {@code count}. */
    public static int boxColor(int accent, int i, int count) {
        return boxColor(accent, i, count, false);
    }

    /**
     * The dashed box's colour for the wash help is drawn over: bright on the dark wash, and
     * deepened on the light one, where a pale dash would disappear into the screen behind it.
     */
    public static int boxColor(int accent, int i, int count, boolean lightMode) {
        float h = hue(accent, i, count);
        return lightMode ? hsv(h, 0.85f, 0.55f) : hsv(h, 0.55f, 0.97f);
    }

    /** The title colour for the card, readable against a light or a dark card surface. */
    public static int titleColor(int accent, int i, int count, int cardFill) {
        float h = hue(accent, i, count);
        return lightSurface(cardFill) ? hsv(h, 0.85f, 0.55f) : hsv(h, 0.5f, 0.97f);
    }

    static float hue(int accent, int i, int count) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        float base = hsv[1] < 0.15f ? 210f : hsv[0];
        return (base + 360f * i / Math.max(1, count)) % 360f;
    }

    static boolean lightSurface(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) > 140;
    }

    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[] {h, s, v});
    }
}
