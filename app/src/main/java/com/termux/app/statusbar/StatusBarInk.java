package com.termux.app.statusbar;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.chrome.OnGlass;

/**
 * How the status bar's own content stays legible on glass, and how its hierarchy survives saying so.
 *
 * <p>{@link OnGlass} answers what one ink has to become on one band. The status bar asks two more
 * questions that are its own, and this is where they are answered — pure, static and context free,
 * so all of it unit tests on the JVM:</p>
 *
 * <h3>Hierarchy without luminance</h3>
 * <p>The trailing stats were three tiers expressed as three role colours at one alpha, and the
 * tiers themselves were read as <em>less</em>: the weather was quieter than the RAM figure because
 * it was dimmer. That budget does not exist in light mode. On the reporting device's own glass
 * ({@code #6A5755}) the CPU label measured 1.03:1, the RAM label 1.10:1 and the weather line
 * 1.11:1 — the hierarchy was intact and nothing in it was readable.</p>
 *
 * <p>So the tiers moved off luminance entirely, first onto weight and size and then — when the
 * bold CPU figure read as the odd one out beside the RAM figure and the temperature — off those
 * as well. Every stat is now drawn at the temperature's own weight and size ({@link #WEIGHT_TERTIARY},
 * {@link #SIZE_TERTIARY_SP}), and <em>every</em> tier is resolved to the same
 * {@link OnGlass#TARGET_BODY_TEXT} floor in its own hue. What tells the three apart is their hue
 * and their order, never a weight, a size, or a distance from the backdrop. {@link #weightFor} and
 * {@link #textSizeSpFor} remain the single place that answer is given, so a tier can be re-weighted
 * in one line if the row ever wants a hierarchy back.</p>
 *
 * <h3>Ink that is drawn at less than full alpha</h3>
 * <p>A contrast ratio is a promise about the pixels that land, and half this bar draws its ink
 * through an alpha — a peeking place mark keeps 70% of its ink, a muted widget fades as its model
 * unloads. {@link OnGlass#inkOn} tones against the surface and knows nothing about that alpha, so a
 * ratio measured on its answer is a ratio nobody sees. {@link #inkAtAlpha} closes that gap: it
 * tones the ink and then finds the smallest alpha at or above the one asked for whose
 * <em>composite</em> clears the target, so what the test measures is what the screen shows.</p>
 *
 * <p>{@link #drain} is the other half of the same idea. Draining a neighbour's colour towards the
 * surface's neutral moved it in luminance, which is exactly the move that costs legibility; drained
 * towards a grey of its <em>own</em> luminance instead, the mark loses its colour and keeps its
 * contrast, which is what the lens always said it was doing.</p>
 */
public final class StatusBarInk {

    private StatusBarInk() {}

    // ---------------------------------------------------------------- the tiers

    /** The weight every stat is drawn at: the temperature's, the one the user asked the rest to match. */
    public static final int WEIGHT_TERTIARY = 400;
    /** The CPU figure's weight — the same as every other tier's; kept as a name so a call site reads. */
    public static final int WEIGHT_PRIMARY = WEIGHT_TERTIARY;
    /** The RAM figure's weight — the same as every other tier's. */
    public static final int WEIGHT_SECONDARY = WEIGHT_TERTIARY;

    /** The size every stat is drawn at, in sp: the temperature's. */
    public static final float SIZE_TERTIARY_SP = 10f;
    /** The CPU figure's size, in sp — the same as every other tier's. */
    public static final float SIZE_PRIMARY_SP = SIZE_TERTIARY_SP;
    /** The RAM figure's size, in sp — the same as every other tier's. */
    public static final float SIZE_SECONDARY_SP = SIZE_TERTIARY_SP;

    /**
     * The alpha a muted widget asks for — the AI glyph's few seconds of afterlife once its model
     * unloaded. It is a request, not a promise: {@link #inkAtAlpha} raises it as far as the
     * backdrop demands, so "was, isn't" never becomes "cannot be read". The state itself is
     * carried by the role change to the surface's neutral, which costs no contrast.
     */
    public static final int MUTED_ALPHA = 160;

    /** How heavy a tier's value is drawn; one answer for all three, and no tier is dimmer than another. */
    public static int weightFor(@NonNull StatusBarWidgetView.ColorRole role) {
        switch (role) {
            case PRIMARY: return WEIGHT_PRIMARY;
            case SECONDARY: return WEIGHT_SECONDARY;
            default: return WEIGHT_TERTIARY;
        }
    }

    /** How large a tier's value is drawn, in sp; one answer for all three, same as {@link #weightFor}. */
    public static float textSizeSpFor(@NonNull StatusBarWidgetView.ColorRole role) {
        switch (role) {
            case PRIMARY: return SIZE_PRIMARY_SP;
            case SECONDARY: return SIZE_SECONDARY_SP;
            default: return SIZE_TERTIARY_SP;
        }
    }

    // ---------------------------------------------------------------- ink behind an alpha

    /**
     * An ink that clears {@code target} on {@code surface} <em>after</em> being composited at its
     * own alpha: {@code seed} toned to the target, then carried at the smallest alpha from
     * {@code alpha} upwards whose composite still clears it.
     *
     * <p>Monotonic, so the first qualifying step is the true minimum: the composite walks in a
     * straight line from the surface to the ink as alpha rises, and its distance from the surface
     * rises with it. When even full alpha misses — a surface no tone of this hue can beat — the
     * answer is the toned ink at 255, which is {@link OnGlass#resolveBare}'s own last word.</p>
     *
     * @param surface the opaque colour the ink is drawn on, normally a resolved band's surface
     * @param seed the ink's own colour, whose hue and chroma the toning keeps
     * @param alpha the alpha the caller would like to draw at, 0..255
     * @param target one of {@link OnGlass}'s three tiers
     * @return the ink, alpha included
     */
    @ColorInt
    public static int inkAtAlpha(@ColorInt int surface, @ColorInt int seed, int alpha,
                                 double target) {
        int base = OnGlass.opaque(surface);
        int ink = OnGlass.resolveBare(base, seed, target).ink;
        int from = Math.max(0, Math.min(255, alpha));
        for (int step = from; step <= 255; step++) {
            if (shownRatio(OnGlass.withAlpha(ink, step), base) >= target) {
                return OnGlass.withAlpha(ink, step);
            }
        }
        return OnGlass.withAlpha(ink, 255);
    }

    /**
     * The contrast an ink actually achieves on {@code surface} when it is drawn at its own alpha:
     * the ratio of what lands, not of what was asked for. The measurement every test in this area
     * should make, and the one the reported 1.03:1 and 1.75:1 were.
     */
    public static double shownRatio(@ColorInt int inkWithAlpha, @ColorInt int surface) {
        int base = OnGlass.opaque(surface);
        return OnGlass.ratio(OnGlass.composite(inkWithAlpha, base), base);
    }

    // ---------------------------------------------------------------- draining colour, not ink

    /**
     * The grey that has the same WCAG relative luminance as {@code color} — the colour's own
     * lightness with none of its colour. Draining towards this is a move in chroma alone.
     */
    @ColorInt
    public static int equalLuminanceGrey(@ColorInt int color) {
        double linear = luminance(color);
        double channel = linear <= 0.0031308d
            ? linear * 12.92d
            : 1.055d * Math.pow(linear, 1d / 2.4d) - 0.055d;
        int value = (int) Math.round(Math.max(0d, Math.min(1d, channel)) * 255d);
        return Color.rgb(value, value, value);
    }

    /**
     * {@code color} drained of its colour by {@code amount}, keeping its luminance: at 0 the colour
     * is untouched, at 1 it is the grey of the same lightness.
     *
     * <p>The lens used to drain a neighbouring place's mark towards the surface's neutral to say it
     * is not where you are; since 2026-09-17 it fades the mark the way the extra-keys row fades an
     * unfocused place switch (same colour, {@code PlaceSwitchGlyph.UNFOCUSED_ALPHA}) and no chrome
     * drains any more. Kept for a caller that wants a colour statement with no lightness cost:
     * towards an equal-luminance grey, alpha kept.</p>
     */
    @ColorInt
    public static int drain(@ColorInt int color, float amount) {
        float fraction = amount < 0f ? 0f : (amount > 1f ? 1f : amount);
        if (fraction <= 0f) return color;
        int grey = equalLuminanceGrey(color);
        return Color.argb(Color.alpha(color),
            mix(Color.red(color), Color.red(grey), fraction),
            mix(Color.green(color), Color.green(grey), fraction),
            mix(Color.blue(color), Color.blue(grey), fraction));
    }

    private static int mix(int from, int to, float fraction) {
        return Math.round(from + (to - from) * fraction);
    }

    // ---------------------------------------------------------------- the session chip

    /** A chip that carries its own surface: what to fill it with, what to write on it, its rim. */
    public static final class Chip {

        /** The container fill, alpha included; drawn over whatever band the chip sits on. */
        @ColorInt public final int container;
        /** The opaque colour the label is really standing on: {@link #container} over the band. */
        @ColorInt public final int surface;
        /** The label's ink, resolved on {@link #surface} at {@link OnGlass#TARGET_BODY_TEXT}. */
        @ColorInt public final int label;
        /** The rim, the place's own colour toned to {@link OnGlass#TARGET_LARGE_TEXT}. */
        @ColorInt public final int stroke;
        /** What {@link #label} achieves on {@link #surface}. */
        public final double labelRatio;
        /** What {@link #stroke} achieves on {@link #surface}. */
        public final double strokeRatio;

        Chip(int container, int surface, int label, int stroke, double labelRatio,
             double strokeRatio) {
            this.container = container;
            this.surface = surface;
            this.label = label;
            this.stroke = stroke;
            this.labelRatio = labelRatio;
            this.strokeRatio = strokeRatio;
        }

        @NonNull
        @Override
        public String toString() {
            return "Chip{container=#" + String.format("%08X", container)
                + ", surface=#" + String.format("%08X", surface)
                + ", label=#" + String.format("%08X", label)
                + " @" + String.format("%.2f", labelRatio)
                + ", stroke=#" + String.format("%08X", stroke)
                + " @" + String.format("%.2f", strokeRatio) + "}";
        }
    }

    /**
     * The session chip, resolved the way the user asked for: <em>the chip follows the mode, and the
     * place moves to the rim</em>.
     *
     * <p>What it replaces blended the place's accent 55% towards black for the container and the
     * label 35% towards the accent, in both modes. In light mode that put a dark label on a fill
     * forced dark — 1.75:1 on the reporting device — and in dark mode it measured 3.90:1, under the
     * floor as well. Both are gone: the container is the mode's own panel colour, so it is light in
     * light mode and dark in dark mode with nothing branching on the mode to say so, the label is
     * the mode's on-surface ink toned on what the container really leaves, and the accent — which
     * is the only thing that said <em>which place this chip belongs to</em> — moves to the rim,
     * where it is a mark rather than a background.</p>
     *
     * @param bandSurface the opaque colour of the band the chip sits on, from a resolved
     *     {@code OnGlass.Resolution#surface}; the chip's fill is translucent, so what is under it
     *     is part of what the label stands on
     * @param containerSeed the mode's panel colour, the chip's fill before its alpha
     * @param containerAlpha how much of that fill the chip carries, 0..255
     * @param labelSeed the mode's on-surface ink; its hue survives the toning
     * @param strokeSeed the place's accent
     */
    @NonNull
    public static Chip chip(@ColorInt int bandSurface, @ColorInt int containerSeed,
                            int containerAlpha, @ColorInt int labelSeed, @ColorInt int strokeSeed) {
        int container = OnGlass.withAlpha(containerSeed, containerAlpha);
        int surface = OnGlass.opaque(OnGlass.composite(container, OnGlass.opaque(bandSurface)));
        int label = OnGlass.resolveBare(surface, labelSeed, OnGlass.TARGET_BODY_TEXT).ink;
        int stroke = OnGlass.resolveBare(surface, strokeSeed, OnGlass.TARGET_LARGE_TEXT).ink;
        return new Chip(container, surface, label, stroke,
            OnGlass.ratio(label, surface), OnGlass.ratio(stroke, surface));
    }

    // ---------------------------------------------------------------- luminance

    /** WCAG relative luminance; the same arithmetic {@code SchemeTone} measures contrast with. */
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
