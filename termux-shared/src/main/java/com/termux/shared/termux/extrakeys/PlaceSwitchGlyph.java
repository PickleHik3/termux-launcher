package com.termux.shared.termux.extrakeys;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.color.utilities.Hct;

/**
 * How the place switches — Home, Terminal, Display — are painted on the row's glass.
 *
 * <p>They wear no cap, so a role has to read off a glyph a few pixels wide, and Material's own role
 * colours are not built for that. A scheme draws primary, secondary and tertiary from one seed:
 * secondary is <em>the same hue as primary</em> at a fifth of the chroma, and tertiary sits 60°
 * along. Measured on the baseline scheme, the three defaults are hue 299/300/359 at chroma
 * 48/16/24 — two of them are literally the same colour, one washed out, which is why three tinted
 * glyphs read as three shades of one thing.
 *
 * <p>So a glyph is painted in a vivid derivative of its role: the hue is kept and the chroma is
 * pushed to the most that hue can hold at a tone set for the glass. Where the palette put two
 * switches on the same hue and there is therefore no hue to tell them apart by, the later one is
 * turned away by {@link #MIN_HUE_SEPARATION} — the smallest move that makes two glyphs read as two
 * colours. A switch whose role already has a hue of its own is never moved.
 *
 * <p>Pure and context free, so the whole rule is unit testable on the JVM. Nothing here touches any
 * key but the place switches: every other coloured key keeps its role's exact cap colour.
 */
public final class PlaceSwitchGlyph {

    private PlaceSwitchGlyph() {}

    /**
     * The chroma a place glyph aims for. sRGB cannot hold anything like this at a legible tone —
     * most hues top out between 40 and 60 — so in practice this asks for the hue's maximum and HCT
     * gamut-maps down to it rather than letting the colour drift off its hue.
     */
    public static final double VIVID_CHROMA = 80d;

    /**
     * The tone a place glyph takes on dark glass. The bottom of the legible band rather than the
     * top: every step of tone above this costs chroma fast (purple holds 44 at 75 and only 29 at
     * 85), and chroma is the whole point of the exercise.
     */
    public static final double TONE_ON_DARK_GLASS = 75d;

    /**
     * The tone on light glass: the same distance from the mid point as {@link #TONE_ON_DARK_GLASS},
     * measured the other way, so the glyph darkens into a light surface by as much as it lightens
     * out of a dark one. Chroma survives the move — most hues hold more of it below the mid point
     * than above.
     */
    public static final double TONE_ON_LIGHT_GLASS = 45d;

    /**
     * How far apart two place glyphs' hues have to be to read as two colours. 60° is the step
     * Material itself uses between primary and tertiary, so a spread row lands on hues the scheme
     * would have been willing to produce.
     */
    public static final double MIN_HUE_SEPARATION = 60d;

    /**
     * Below this chroma a colour has no hue worth raising — the two fixed roles, black and white,
     * and any near-neutral. Those are returned exactly as given: a user who asked for black asked
     * for black, and inventing a hue for it would be inventing a colour they never chose. They take
     * no part in the spread either, having no hue to collide with.
     */
    private static final double NEUTRAL_CHROMA = 4d;

    /**
     * How bright a place switch is while its place is not the one in front: its own colour held
     * back to 57%. Alpha rather than a dimmer tone of the role, because a role can be any of them —
     * including the two fixed ones, which have no tonal palette to step down — and alpha is the one
     * dimming that keeps every one of them on its own hue.
     *
     * <p>The status bar's lens fades its two peeking place marks by the same share, so a place is
     * the same colour at the same strength wherever it is drawn.
     */
    public static final int UNFOCUSED_ALPHA = 145;

    /**
     * The radius of the focused glyph's resting halo, in dp: a neon tube's spread, not a smudge.
     * The halo used to be 7 dp at 60% of the glyph's own colour, which around a glyph a few pixels
     * wide read as a smear; the user's word for what they wanted instead was "a neon light's
     * spread" — soft, dim, close to the stroke (decision 2026-09-17). {@code 0f} turns it off, and
     * the focus then reads by colour alone: full strength between two faded neighbours.
     */
    public static final float GLOW_RADIUS_DP = 3f;

    /** How strong that halo is: the glyph's own vivid colour at about a quarter, so it glows, not blooms. */
    public static final int GLOW_ALPHA = 60;

    /**
     * The vivid colour one role is drawn in, on its own hue.
     *
     * @param color the role's own colour, as any other key would wear it.
     * @param onDarkGlass whether the row's glass is dark, which decides which way the tone goes.
     * @return an opaque colour on {@code color}'s hue, carrying as much chroma as that hue holds at
     * the glyph tone; {@code color} itself when it has no hue to raise.
     */
    @ColorInt
    public static int vividFor(@ColorInt int color, boolean onDarkGlass) {
        int opaque = color | 0xFF000000;
        Hct source = Hct.fromInt(opaque);
        if (source.getChroma() < NEUTRAL_CHROMA)
            return opaque;
        return vivid(source.getHue(), source.getChroma(), onDarkGlass);
    }

    /**
     * The colours a whole row of place switches is drawn in, in row order.
     *
     * <p>Each is {@link #vividFor} of its role, except where the palette has already spent that hue
     * on an earlier switch: then this one is turned to the first free hue {@link
     * #MIN_HUE_SEPARATION} beyond the switch it clashed with. The first switch therefore always
     * keeps its role's hue exactly, and a row whose roles already sit on different hues comes back
     * untouched.
     *
     * @param colors each switch's role colour, in the order the row draws them.
     * @param onDarkGlass whether the row's glass is dark.
     */
    @NonNull
    public static int[] vividRow(@NonNull int[] colors, boolean onDarkGlass) {
        int[] out = new int[colors.length];
        double[] taken = new double[colors.length];
        int takenCount = 0;
        for (int i = 0; i < colors.length; i++) {
            int opaque = colors[i] | 0xFF000000;
            Hct source = Hct.fromInt(opaque);
            if (source.getChroma() < NEUTRAL_CHROMA) {
                out[i] = opaque;
                continue;
            }
            double hue = source.getHue();
            // One pass per hue already taken is enough to find a free one when there is one, and
            // bounds the search when there is not: past six switches the circle has no room left
            // for another 60° slot, and the switch simply keeps the hue its role gave it.
            for (int attempt = 0; attempt <= takenCount; attempt++) {
                double clash = clashingHue(hue, taken, takenCount);
                if (Double.isNaN(clash))
                    break;
                hue = normalizeHue(clash + MIN_HUE_SEPARATION);
            }
            taken[takenCount++] = hue;
            out[i] = vivid(hue, source.getChroma(), onDarkGlass);
        }
        return out;
    }

    /**
     * Which way the row's glass runs, read off the colour the row paints its own plain glyphs in.
     * The glass itself is usually transparent — it is the wallpaper showing through — so it says
     * nothing; the label colour the theme handed the row is the statement the theme already made
     * about what is behind it. A light label means dark glass.
     */
    public static boolean isDarkGlass(@ColorInt int rowLabelColor) {
        return Hct.fromInt(rowLabelColor | 0xFF000000).getTone() >= 50d;
    }

    /** The shortest angle between two hues, 0–180. */
    public static double hueDistance(double first, double second) {
        double delta = Math.abs(normalizeHue(first) - normalizeHue(second));
        return delta > 180d ? 360d - delta : delta;
    }

    @ColorInt
    private static int vivid(double hue, double sourceChroma, boolean onDarkGlass) {
        double chroma = Math.max(sourceChroma, VIVID_CHROMA);
        double tone = onDarkGlass ? TONE_ON_DARK_GLASS : TONE_ON_LIGHT_GLASS;
        return Hct.from(hue, chroma, tone).toInt() | 0xFF000000;
    }

    /** The taken hue {@code hue} sits too close to, or NaN when it is clear of all of them. */
    private static double clashingHue(double hue, @NonNull double[] taken, int count) {
        for (int i = 0; i < count; i++) {
            if (hueDistance(hue, taken[i]) < MIN_HUE_SEPARATION)
                return taken[i];
        }
        return Double.NaN;
    }

    private static double normalizeHue(double hue) {
        double wrapped = hue % 360d;
        return wrapped < 0d ? wrapped + 360d : wrapped;
    }
}
