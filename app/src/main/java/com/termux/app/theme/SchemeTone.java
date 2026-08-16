package com.termux.app.theme;

import android.graphics.Color;

import androidx.annotation.ColorInt;

import com.google.android.material.color.utilities.Hct;

/**
 * Colour arithmetic shared by the scheme-driven theme.
 *
 * <p>Pure and context free so the whole derivation can be unit tested on the JVM: a terminal colour
 * scheme is sixteen ANSI colours plus a background and a foreground, and every Material role the
 * launcher chrome asks for has to be built out of those without a {@code Resources} anywhere in
 * sight.
 *
 * <p>Tone moves happen in HCT rather than HSV or plain RGB blending. A gruvbox background nudged
 * "6% lighter" in RGB drifts grey; the same move in HCT keeps the hue and the chroma and only
 * changes perceived lightness, which is what a surface ladder is supposed to be.
 */
public final class SchemeTone {

    private SchemeTone() {}

    /** WCAG relative-luminance contrast ratio between two opaque colours. */
    public static double contrastRatio(@ColorInt int first, @ColorInt int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05d) / (Math.min(a, b) + 0.05d);
    }

    /**
     * {@code color} moved along its own tone axis until it clears {@code target} contrast against
     * {@code surface}, or returned unchanged when it already does.
     *
     * <p>The search walks every displayable tone and keeps the nearest qualifying one instead of
     * assuming dark surfaces always want a lighter glyph — light schemes with a dark accent break
     * that assumption immediately.
     */
    @ColorInt
    public static int contrastTone(@ColorInt int color, @ColorInt int surface, double target) {
        if (contrastRatio(color, surface) >= target) return color;
        Hct source = Hct.fromInt(color);
        int best = color;
        double bestDistance = Double.MAX_VALUE;
        for (int tone = 0; tone <= 100; tone++) {
            int candidate = Hct.from(source.getHue(), source.getChroma(), tone).toInt();
            if (contrastRatio(candidate, surface) < target) continue;
            double distance = Math.abs(tone - source.getTone());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** {@code color} pulled back toward {@code surface} until it is no brighter than {@code ceiling}. */
    @ColorInt
    public static int capContrast(@ColorInt int color, @ColorInt int surface, double ceiling) {
        int result = color;
        for (int i = 0; i < 24 && contrastRatio(result, surface) > ceiling; i++) {
            result = blend(result, surface, 0.08f);
        }
        return result;
    }

    /** {@code color} with its HCT tone shifted by {@code delta}, clamped to the displayable range. */
    @ColorInt
    public static int toneShift(@ColorInt int color, double delta) {
        Hct source = Hct.fromInt(color);
        double tone = Math.max(0d, Math.min(100d, source.getTone() + delta));
        return Hct.from(source.getHue(), source.getChroma(), tone).toInt();
    }

    /** HCT tone of {@code color}, 0 (black) to 100 (white). */
    public static double tone(@ColorInt int color) {
        return Hct.fromInt(color).getTone();
    }

    /** Smallest angle in degrees between the HCT hues of two colours, 0-180. */
    public static double hueDistance(@ColorInt int first, @ColorInt int second) {
        double delta = Math.abs(Hct.fromInt(first).getHue() - Hct.fromInt(second).getHue()) % 360d;
        return delta > 180d ? 360d - delta : delta;
    }

    /** HCT chroma of {@code color}; near zero for greys. */
    public static double chroma(@ColorInt int color) {
        return Hct.fromInt(color).getChroma();
    }

    /** Straight RGB interpolation, {@code amount} of the way from {@code from} to {@code to}. */
    @ColorInt
    public static int blend(@ColorInt int from, @ColorInt int to, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * clamped);
        int green = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * clamped);
        int blue = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped);
        int alpha = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped);
        return Color.argb(alpha, red, green, blue);
    }

    /** {@code color} with its alpha channel replaced by {@code fraction} of full opacity. */
    @ColorInt
    public static int withAlpha(@ColorInt int color, float fraction) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, fraction)) * 255f);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Whether a scheme built on this background reads as a dark theme. */
    public static boolean isDark(@ColorInt int background) {
        return perceivedBrightness(background) < 128;
    }

    /** Perceived brightness, 0-255; the same weighting the terminal palette code uses. */
    public static int perceivedBrightness(@ColorInt int color) {
        return (int) Math.floor(Math.sqrt(
            Math.pow(Color.red(color), 2) * 0.241
                + Math.pow(Color.green(color), 2) * 0.691
                + Math.pow(Color.blue(color), 2) * 0.068
        ));
    }

    /** Whichever of {@code first} / {@code second} is more legible on {@code background}. */
    @ColorInt
    public static int mostLegible(@ColorInt int background, @ColorInt int first, @ColorInt int second) {
        return contrastRatio(first, background) >= contrastRatio(second, background) ? first : second;
    }

    private static double luminance(@ColorInt int color) {
        return 0.2126d * linear(Color.red(color) / 255d)
            + 0.7152d * linear(Color.green(color) / 255d)
            + 0.0722d * linear(Color.blue(color) / 255d);
    }

    private static double linear(double channel) {
        return channel <= 0.04045d ? channel / 12.92d
            : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }
}
