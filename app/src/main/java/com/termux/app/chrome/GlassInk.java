package com.termux.app.chrome;

import android.graphics.Color;
import android.graphics.Rect;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.theme.SchemeTone;

/**
 * The one way a launcher colour is made legible on glass, and the one way a glyph gets its halo.
 *
 * <p>{@link OnGlass} answers for a <em>band</em> — what veil it needs and what ink stands on it.
 * This answers the question the launcher's own painted things have: the A&ndash;Z letters, the
 * drawer's A&ndash;Z rope, the gesture orb, the edge bloom, the page ticks and the app-launch
 * ripple are not bands; they are colours drawn <em>on</em> a band that something else measured.
 * Before this class each of them clamped its own brightness up with an HSV floor — {@code
 * hsv[2] = max(0.78f, ...)} and four variations of it — which is the right direction on a dark
 * backdrop and exactly the wrong one on a light one. That is why the light mode's A&ndash;Z letters
 * measured 1.21:1 on the reporting device. There is one contrast call here instead, and every one
 * of those sites goes through it.</p>
 *
 * <h3>The halo</h3>
 * <p>Both A&ndash;Z rails draw every glyph twice: a stroke pass under a fill pass. The stroke was a
 * fixed near-black ({@code #1A1F2A}) at alpha 195 whatever the mode, so in dark mode it melted into
 * the background and read as a clean letter, and in light mode it became the highest-contrast thing
 * on the strip — a hard dark ring around a letter that barely read against the band. An outlined
 * shape filled with something close to its own background is what the eye calls blurry, and that is
 * the user's "the alphabets row looks fuzzy": the halo, not the glyph.</p>
 *
 * <p>The double-draw stays; it is what keeps a glyph readable where the wallpaper changes under it
 * mid-letter. What changes is that the halo is no longer a constant:</p>
 * <ul>
 *   <li><b>It inverts with the glyph.</b> {@link #haloInk} takes the near-black under a pale glyph
 *       and a near-white under a dark one, decided from the glyph and the surface rather than from
 *       the mode, so it follows {@link ChromeInk}'s one polarity without asking it and without
 *       branching on the night theme.</li>
 *   <li><b>Its alpha is the smallest that does the job.</b> {@link #haloAlpha} spends the least
 *       alpha that deepens the glyph's edge by {@link #HALO_EDGE_GAIN} over what the bare band
 *       already gives it — a backdrop close to the glyph needs more, a backdrop far from it needs
 *       almost none — and never more than keeps the halo <em>quieter against the band than the
 *       glyph itself</em>. That ceiling is the anti-smear rule: whatever else happens, the shape the
 *       eye resolves first is the letter and not the ring around it.</li>
 * </ul>
 *
 * <p>Pure, static and context free, so all of it unit tests on the JVM. Tone moves go through
 * {@link SchemeTone#contrastTone} — HCT, keeping hue and chroma — never ad-hoc HSV maths.</p>
 */
public final class GlassInk {

    private GlassInk() {}

    // ---------------------------------------------------------------- the one contrast call

    /**
     * {@code seed} made legible on {@code surface}: its own hue and chroma, moved along the tone
     * axis until it clears {@code target}, and drawn at {@code alpha}.
     *
     * <p>The alpha is part of the arithmetic rather than something applied afterwards. A colour
     * drawn at alpha {@code a} over a surface is not that colour — it is that colour composited
     * with the surface, which is always <em>closer</em> to the surface and therefore always lower
     * contrast. So the ink that has to read is solved for first, and the colour actually drawn is
     * whatever composites to it at this alpha. Where that solve runs out of channel — the wanted
     * ink is further from the surface than this alpha can reach — the alpha gives way and rises,
     * because the alpha is a preference and the contrast is a promise.</p>
     *
     * @param surface the opaque colour this is drawn on, from {@link OnGlass.Resolution#surface}
     * @param seed the colour it wants to be: a role colour, an icon's dominant hue, an accent
     * @param target one of {@link OnGlass#TARGET_BODY_TEXT}, {@link OnGlass#TARGET_LARGE_TEXT},
     *     {@link OnGlass#TARGET_DECORATION}
     * @param alpha the opacity this is drawn at, 0..255; raised only if the target needs it
     */
    @ColorInt
    public static int legible(@ColorInt int surface, @ColorInt int seed, double target, int alpha) {
        int base = OnGlass.opaque(surface);
        int wanted = OnGlass.inkOn(base, seed, target);
        if (OnGlass.ratio(wanted, base) < target) {
            wanted = OnGlass.mostLegibleInk(base, Color.WHITE, Color.BLACK);
        }
        int a = Math.max(1, Math.min(255, alpha));
        if (a >= 255) return OnGlass.withAlpha(wanted, 255);
        int drawn = unpremultiplyOver(wanted, base, a);
        while (a < 255 && OnGlass.ratio(effective(drawn, a, base), base) < target) {
            a = Math.min(255, a + 4);
            drawn = unpremultiplyOver(wanted, base, a);
        }
        return OnGlass.withAlpha(drawn, a);
    }

    /** {@link #legible(int, int, double, int)} at full opacity. */
    @ColorInt
    public static int legible(@ColorInt int surface, @ColorInt int seed, double target) {
        return legible(surface, seed, target, 255);
    }

    /**
     * {@link #legible} with the side settled first: the answer is always paler than the surface, or
     * always darker than it, whichever the chrome is drawing from.
     *
     * <p>{@link SchemeTone#contrastTone} keeps the <em>nearest</em> tone that clears the target,
     * which is the right answer for one colour alone and the wrong one for a set of them. Seeded
     * with a near-black on-surface colour it darkens; seeded with a pale accent it lightens; and
     * the launcher's A&ndash;Z row, the drawer's A&ndash;Z rope, the ticks and the orb are seeded
     * from different role colours and drawn a row apart. Resolved independently they can come out
     * on opposite sides of the same band — a dark rope beside a pale rail — which is worse than
     * either of them being wrong. So the side is an input, taken from {@link #isPaleSide} on the
     * one ink {@link ChromeInk} already settled, and the search runs outward from the band on that
     * side only.</p>
     */
    @ColorInt
    public static int legibleOn(@ColorInt int surface, @ColorInt int seed, boolean paleSide,
                                double target, int alpha) {
        int base = OnGlass.opaque(surface);
        double band = SchemeTone.tone(base);
        double seedTone = SchemeTone.tone(seed);
        boolean onSide = paleSide ? seedTone >= band : seedTone <= band;
        if (onSide && OnGlass.ratio(seed, base) >= target) return legible(base, seed, target, alpha);
        double from = paleSide ? Math.max(band, seedTone) : Math.min(band, seedTone);
        for (double step = 0d; step <= 100d; step += 1d) {
            double tone = paleSide ? from + step : from - step;
            if (tone < 0d || tone > 100d) break;
            int candidate = SchemeTone.toneShift(seed, tone - seedTone);
            if (OnGlass.ratio(candidate, base) >= target) {
                return legible(base, candidate, target, alpha);
            }
        }
        // No tone of this hue works on that side of this band at all — a near-white band asked for
        // a paler ink, say. The side was a preference; the contrast is not.
        return legible(base, seed, target, alpha);
    }

    /** {@link #legibleOn} at full opacity. */
    @ColorInt
    public static int legibleOn(@ColorInt int surface, @ColorInt int seed, boolean paleSide,
                                double target) {
        return legibleOn(surface, seed, paleSide, target, 255);
    }

    /**
     * Which side of its band an ink is on: true when it is the paler of the two.
     *
     * <p>How everything downstream of a resolved ink learns the chrome's polarity without being
     * handed it and without asking the mode. {@link ChromeInk#polarity()} decided it; an ink and
     * the surface it was resolved against carry the decision.</p>
     */
    public static boolean isPaleSide(@ColorInt int ink, @ColorInt int surface) {
        return SchemeTone.tone(ink) >= SchemeTone.tone(OnGlass.opaque(surface));
    }

    /**
     * What {@code color} at {@code alpha} actually looks like once it is drawn on {@code surface}:
     * the opaque colour a contrast ratio should be measured against. Every assertion about a
     * translucent thing on glass is an assertion about this, not about the colour in the paint.
     */
    @ColorInt
    public static int effective(@ColorInt int color, int alpha, @ColorInt int surface) {
        return OnGlass.opaque(OnGlass.composite(OnGlass.withAlpha(color, alpha),
            OnGlass.opaque(surface)));
    }

    /** {@link #effective(int, int, int)} using the colour's own alpha channel. */
    @ColorInt
    public static int effective(@ColorInt int color, @ColorInt int surface) {
        return effective(color, Color.alpha(color), surface);
    }

    /**
     * The colour that, drawn at {@code alpha} over {@code surface}, composites to {@code wanted}.
     * Clamped per channel, so a {@code wanted} further from the surface than this alpha can reach
     * comes back as the furthest it can go — which is what makes {@link #legible} raise the alpha.
     */
    @ColorInt
    private static int unpremultiplyOver(@ColorInt int wanted, @ColorInt int surface, int alpha) {
        float scale = 255f / Math.max(1, alpha);
        return Color.rgb(
            channelBack(Color.red(wanted), Color.red(surface), scale),
            channelBack(Color.green(wanted), Color.green(surface), scale),
            channelBack(Color.blue(wanted), Color.blue(surface), scale));
    }

    private static int channelBack(int wanted, int surface, float scale) {
        int value = Math.round(surface + (wanted - surface) * scale);
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    /**
     * {@code color} turned {@code degrees} around the hue wheel, with nothing else touched.
     *
     * <p>The edge bloom is deliberately a neighbour of the accent rather than the accent — that is
     * an identity choice and it survives. What does not survive is the value floor that used to
     * come with it; the hue moves here and {@link #legible} decides how bright the result has to
     * be for where it is actually drawn.</p>
     */
    @ColorInt
    public static int hueRotated(@ColorInt int color, float degrees) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + degrees % 360f + 360f) % 360f;
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    /**
     * Of two candidate role colours, the darker one — the on-light ink. Its partner is
     * {@link #paleOf}. Which of a theme's two primaries is the dark one is a fact about the
     * colours, so it is read off them rather than off {@code isNightThemeActive()}: the mode does
     * not choose the ink here, {@link ChromeInk#polarity()} does, and it needs both candidates
     * labelled the same way in either mode.
     */
    @ColorInt
    public static int darkOf(@ColorInt int first, @ColorInt int second) {
        return SchemeTone.tone(first) <= SchemeTone.tone(second) ? first : second;
    }

    /** Of two candidate role colours, the paler one — the on-dark ink. See {@link #darkOf}. */
    @ColorInt
    public static int paleOf(@ColorInt int first, @ColorInt int second) {
        return SchemeTone.tone(first) <= SchemeTone.tone(second) ? second : first;
    }

    // ---------------------------------------------------------------- a band that draws no glass

    /**
     * The ink for content on a band that has no glass surface of its own.
     *
     * <p>The A&ndash;Z strip is one: its view's background is {@link Color#TRANSPARENT} and the
     * letters stand straight on whatever is behind them. {@link ChromeInk#onGlass} may answer such
     * a band with a veil — the arithmetic does not know the band has nowhere to draw one — and the
     * ink it returns would then be resolved against a surface that never happens. So the band is
     * asked anyway, because that is what casts its vote in the chrome's one polarity, and its
     * answer is taken only when it is bare; otherwise the same polarity's ink is re-resolved
     * against the backdrop as it really is, with {@link OnGlass#resolveBare}, and the ink moves
     * instead of the wallpaper.</p>
     *
     * @param dim the launcher's wallpaper dim, as {@link ChromeRenderer.Surfaces#wallpaperDimColor}
     */
    @NonNull
    public static OnGlass.Resolution bareBandInk(@NonNull ChromeInk ink,
                                                 @NonNull GlassBackdropCache.Band band,
                                                 @NonNull Rect screenRect, @ColorInt int dim,
                                                 @ColorInt int darkInk, @ColorInt int paleInk,
                                                 double target) {
        OnGlass.Resolution voted = ink.onGlass(band, screenRect, darkInk, paleInk, target);
        if (voted.isBare()) return voted;
        int seed = ink.polarity() == ChromeInk.Polarity.DARK_INK ? darkInk : paleInk;
        int backdrop = ink.backdrops().backdropUnder(band, screenRect, dim, Color.TRANSPARENT);
        return OnGlass.resolveBare(backdrop, seed, target);
    }

    // ---------------------------------------------------------------- the halo

    /** The halo under a pale glyph: the desaturated near-black both rails have always stroked with. */
    public static final int HALO_DARK = 0xFF1A1F2A;

    /**
     * The halo under a dark glyph: {@link #HALO_DARK}'s mirror, the same faint blue cast so the two
     * are the same material seen from either side rather than one designed colour and one white.
     */
    public static final int HALO_PALE = 0xFFF1F4FA;

    /**
     * The halo is never dropped, whatever the arithmetic says — the user chose to keep the double
     * draw and invert it, not to lose it — so this is the least it is ever drawn at. Low enough
     * that at this alpha it cannot become the shape the eye resolves first.
     */
    public static final int HALO_ALPHA_FLOOR = 48;

    /** The most halo any glyph gets: the alpha both rails used to draw unconditionally. */
    public static final int HALO_ALPHA_CEILING = 195;

    /**
     * The headroom, as a multiple of the glyph's own contrast target, at which the halo stops being
     * needed at all and falls to {@link #HALO_ALPHA_FLOOR}.
     *
     * <p>A glyph sitting exactly on its target has nothing in hand: the band it is on is a mean, the
     * wallpaper under any one letter can be a good deal closer than the mean, and the halo is the
     * guarantee that the letter still has an edge where that happens. Twice the target is where
     * that stops mattering — a letter at 6:1 on a band whose local swing is a few percent of
     * luminance keeps its shape without help — and between the two the halo fades out linearly.
     * This is what "the alpha follows how much the backdrop needs it" means, and it is why the
     * reporting device's own strip, where the letters land within a hundredth of 3.0, still gets a
     * full halo while a letter on a near-black band gets almost none.</p>
     */
    public static final double HALO_RELIEF = 2.0d;

    /** The focused letter's halo, as a fraction of the resting one: the old 215/195, kept. */
    public static final float HALO_FOCUS_GAIN = 215f / 195f;

    /**
     * Which way the halo goes: away from the glyph, whichever side of the surface the glyph is on.
     *
     * <p>This is the inversion, and it is read off the two colours rather than off the mode, so it
     * cannot disagree with {@link ChromeInk#polarity()} — the glyph came from the polarity, and the
     * halo is simply the other side of it. A pale glyph gets {@link #HALO_DARK}; a dark glyph gets
     * {@link #HALO_PALE}.</p>
     */
    @ColorInt
    public static int haloInk(@ColorInt int glyphInk, @ColorInt int surface) {
        double band = SchemeTone.tone(OnGlass.opaque(surface));
        boolean paleGlyph = SchemeTone.tone(glyphInk) >= band;
        int seed = paleGlyph ? HALO_DARK : HALO_PALE;
        double seedTone = SchemeTone.tone(seed);
        if (paleGlyph ? seedTone < band : seedTone > band) return seed;
        // The band is already past the constant — a dark-theme glass at #1B1A17 is darker than the
        // near-black itself — so stroking with it would move the halo towards the glyph rather than
        // away from it. Beyond the constant there is only the extreme.
        return paleGlyph ? Color.BLACK : Color.WHITE;
    }

    /**
     * The alpha that halo is drawn at: as much as the band's closeness to the glyph asks for, never
     * more than keeps the halo quieter against the band than the glyph itself, never less than
     * {@link #HALO_ALPHA_FLOOR}.
     *
     * <p>Two numbers decide it. The <em>need</em> is how little headroom the glyph has over
     * {@code target} on this band — full halo at the target itself, none at {@link #HALO_RELIEF}
     * times it — which is the part that follows the wallpaper. The <em>ceiling</em> is the last
     * alpha at which the halo still reads as less against the band than the glyph does, and it is
     * the part that answers the complaint: past it the ring is the shape and the letter is its
     * filling, which is what the shipped constant 195 did to a letter that measured 1.21:1.</p>
     *
     * <p>The ceiling is searched a step at a time like {@link OnGlass#veilAlphaFor}, and for the
     * same reason: it runs when a colour or the wallpaper moves, not per glyph and not per frame.
     * The caller caches the answer beside the ink it belongs to.</p>
     */
    public static int haloAlpha(@ColorInt int glyphInk, @ColorInt int surface, boolean focused,
                                double target) {
        int base = OnGlass.opaque(surface);
        int halo = haloInk(glyphInk, base);
        double glyphOnBand = OnGlass.ratio(glyphInk, base);
        double headroom = glyphOnBand / Math.max(1d, target);
        double need = (HALO_RELIEF - headroom) / (HALO_RELIEF - 1d);
        need = need < 0d ? 0d : (need > 1d ? 1d : need);
        int wanted = HALO_ALPHA_FLOOR
            + Math.round((float) ((HALO_ALPHA_CEILING - HALO_ALPHA_FLOOR) * need));
        if (focused) wanted = Math.round(wanted * HALO_FOCUS_GAIN);
        wanted = Math.min(wanted, HALO_ALPHA_CEILING);

        int ceiling = 0;
        for (int alpha = 1; alpha <= HALO_ALPHA_CEILING; alpha++) {
            if (OnGlass.ratio(effective(halo, alpha, base), base) > glyphOnBand) break;
            ceiling = alpha;
        }
        // The floor outranks the ceiling: the halo is kept even where the band leaves no room for
        // it, because dropping it was the alternative the user did not take.
        return Math.max(HALO_ALPHA_FLOOR, Math.min(wanted, Math.max(ceiling, HALO_ALPHA_FLOOR)));
    }

    /** {@link #haloAlpha(int, int, boolean, double)} at the tier both A&ndash;Z rails belong to. */
    public static int haloAlpha(@ColorInt int glyphInk, @ColorInt int surface, boolean focused) {
        return haloAlpha(glyphInk, surface, focused, OnGlass.TARGET_LARGE_TEXT);
    }

    /**
     * The whole halo for a glyph: {@link #haloInk} at {@link #haloAlpha}, ready for
     * {@code Paint.setColor} on the stroke pass.
     */
    @ColorInt
    public static int halo(@ColorInt int glyphInk, @ColorInt int surface, boolean focused) {
        int base = OnGlass.opaque(surface);
        return OnGlass.withAlpha(haloInk(glyphInk, base), haloAlpha(glyphInk, base, focused));
    }
}
