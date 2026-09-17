package com.termux.app.chrome;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.theme.SchemeTone;

/**
 * Legibility arithmetic for chrome that draws on glass rather than on a card.
 *
 * <p>The launcher's chrome bands — the status strip, the window bar, the A&ndash;Z scrub rail, the
 * dock — are translucent panes over the wallpaper. Their foreground colours come from the Material
 * role palette, which is authored against an opaque surface: {@code termux_primary} is
 * {@code #345CA8} in {@code values/colors.xml} and {@code #B8C7FF} in {@code values-night}. Those
 * are on-white and on-black inks respectively, and a role colour is only ever checked against the
 * role surface it was authored for.</p>
 *
 * <p>That holds in dark mode by accident: Android dims the wallpaper for a dark theme, so the glass
 * lands near {@code #1B1A17} and a pale ink clears 4.5:1 comfortably. Nothing lightens the
 * wallpaper for a light theme, so in light mode the same glass measured 10.5% relative luminance on
 * the reporting device ({@code #6A5755} behind the status bar, {@code #657271} behind the A&ndash;Z
 * strip) while the ink stayed the dark-on-white {@code #345CA8} — 6.5:1 on a white card, 1.02:1
 * there. Measured on device: CPU label 1.03, RAM 1.10, weather ~1.0, separator dots 1.01, session
 * chip 1.75, window chip ~1.0, A&ndash;Z letter 1.21. Every one of those is under the 4.5:1 floor.</p>
 *
 * <p>This class is the arithmetic half of the fix the user chose: <em>measure the backdrop and
 * adapt, veil only as far as needed</em>. Given what a band is actually drawn over, it answers how
 * much veil that band needs for the mode's own preferred ink to clear a target ratio, and what the
 * ink should become when even a capped veil is not enough. A dark wallpaper in light mode veils
 * barely at all; a bright one veils more; the answer follows the wallpaper instead of being frozen
 * into a resource. The sampling and caching half is {@link GlassBackdropCache}.</p>
 *
 * <p>Pure, static and context free, so all of it unit tests on the JVM. Tone moves go through
 * {@link SchemeTone#contrastTone} — HCT, keeping hue and chroma — never ad-hoc HSV maths.</p>
 */
public final class OnGlass {

    private OnGlass() {}

    /**
     * Contrast a label, a value or any run of body-sized text has to clear: WCAG AA for normal
     * text. The status widgets' CPU/RAM labels, the session chip's label and the window chip's
     * title are all this tier.
     */
    public static final double TARGET_BODY_TEXT = 4.5d;

    /**
     * Contrast large text and icons have to clear: WCAG AA for large text (18.66px bold / 24px
     * regular and up) and AA non-text for meaningful graphics. The A&ndash;Z letters and the
     * status glyphs are this tier.
     */
    public static final double TARGET_LARGE_TEXT = 3.0d;

    /**
     * Contrast pure decoration has to clear. Deliberately its own tier and deliberately below
     * {@link #TARGET_LARGE_TEXT}: the 6&nbsp;px separator dots between status widgets carry no
     * information — remove them and nothing is lost but rhythm — so WCAG does not govern them and
     * the user did not ask for them to be promoted to the graphics floor. 2.0 roughly doubles the
     * luminance distance from the surface, which is where a 6&nbsp;px dot stops being invisible at
     * arm's length (it measured 1.01 on the reporting device), while staying under 3.0 so a
     * separator never out-shouts the label it separates.
     */
    public static final double TARGET_DECORATION = 2.0d;

    /**
     * The most veil any band may lay over the wallpaper, as a fraction of full opacity.
     *
     * <p>A cap is needed because the veil is otherwise unbounded: a wallpaper whose luminance sits
     * exactly where the mode's preferred ink sits can demand an alpha of 1.0, which would replace
     * the glass with an opaque bar and delete the wallpaper the user chose. 0.55 was picked
     * against the reporting device's own worst band ({@code #6A5755}, 10.5% luminance, light mode,
     * {@code #345CA8} ink): at that alpha the band still shows roughly 45% of the wallpaper, so it
     * reads as glass and not as a slab, and the veiled surface is light enough that
     * {@link SchemeTone#contrastTone} can still find a 4.5:1 ink by darkening the blue a few tones.
     * Above the cap the answer is "the wallpaper wins, move the ink instead", which is what
     * {@link #resolve} does.</p>
     */
    public static final float MAX_VEIL_ALPHA = 0.55f;

    /** {@link #MAX_VEIL_ALPHA} as an 8-bit alpha channel, the resolution the search works in. */
    public static final int MAX_VEIL_ALPHA_255 = Math.round(MAX_VEIL_ALPHA * 255f);

    /**
     * What one band needs in order to be legible over what it is actually drawn on.
     *
     * <p>Immutable and cheap to hold; {@link GlassBackdropCache} memoises one of these per band so
     * the search behind it runs on a wallpaper, palette or mode change rather than per frame.</p>
     */
    public static final class Resolution {

        /**
         * The veil to draw over the band, under its content: the mode's surface colour at the
         * smallest alpha that does the job, or {@link Color#TRANSPARENT} when the backdrop needed
         * no help at all.
         */
        @ColorInt public final int veil;

        /** The opaque colour the band's content is effectively drawn on: {@link #veil} over the backdrop. */
        @ColorInt public final int surface;

        /** The colour to draw the content in: the preferred ink, or a tone of it that clears the target. */
        @ColorInt public final int ink;

        /** The contrast {@link #ink} actually achieves against {@link #surface}. */
        public final double ratio;

        /** The target that was asked for; {@link #ratio} is {@code >=} it unless {@link #shortfall} is true. */
        public final double target;

        /**
         * True when the veil ran into {@link #MAX_VEIL_ALPHA} and the ink had to move to make up
         * the difference. Not a failure — it is the intended behaviour over a hostile wallpaper —
         * but a caller that wants to, say, drop a decoration entirely can read it.
         */
        public final boolean veilCapped;

        /**
         * True when the alternate ink won — the band is drawn in the other mode's colour because
         * the backdrop under it belongs to the other mode. A caller that tints an icon alongside
         * its label reads this so the two flip together.
         */
        public final boolean inkFlipped;

        /** True when even a capped veil and a re-toned ink could not reach {@link #target}. */
        public final boolean shortfall;

        Resolution(@ColorInt int veil, @ColorInt int surface, @ColorInt int ink,
                   double ratio, double target, boolean veilCapped, boolean inkFlipped,
                   boolean shortfall) {
            this.veil = veil;
            this.surface = surface;
            this.ink = ink;
            this.ratio = ratio;
            this.target = target;
            this.veilCapped = veilCapped;
            this.inkFlipped = inkFlipped;
            this.shortfall = shortfall;
        }

        /** The veil's alpha as a 0..1 fraction; 0 when no veil is needed. */
        public float veilAlpha() {
            return Color.alpha(veil) / 255f;
        }

        /** True when the band needs no veil at all — the common case over a dark wallpaper. */
        public boolean isBare() {
            return Color.alpha(veil) == 0;
        }

        @NonNull
        @Override
        public String toString() {
            return "Resolution{veil=#" + hex(veil) + ", surface=#" + hex(surface)
                + ", ink=#" + hex(ink) + ", ratio=" + String.format("%.2f", ratio)
                + "/" + target + (veilCapped ? ", capped" : "") + (inkFlipped ? ", flipped" : "")
                + (shortfall ? ", SHORTFALL" : "") + "}";
        }

        private static String hex(@ColorInt int color) {
            return String.format("%08X", color);
        }
    }

    // ---------------------------------------------------------------- backdrop

    /**
     * Source-over composite of {@code over} onto {@code under}, both straight (non-premultiplied)
     * ARGB. A fully transparent {@code over} returns {@code under} untouched.
     */
    @ColorInt
    public static int composite(@ColorInt int over, @ColorInt int under) {
        float sa = Color.alpha(over) / 255f;
        if (sa <= 0f) return under;
        if (sa >= 1f) return over;
        float da = Color.alpha(under) / 255f;
        float outA = sa + da * (1f - sa);
        if (outA <= 0f) return Color.TRANSPARENT;
        int red = channel(Color.red(over), sa, Color.red(under), da, outA);
        int green = channel(Color.green(over), sa, Color.green(under), da, outA);
        int blue = channel(Color.blue(over), sa, Color.blue(under), da, outA);
        return Color.argb(Math.round(outA * 255f), red, green, blue);
    }

    private static int channel(int sc, float sa, int dc, float da, float outA) {
        float value = (sc * sa + dc * da * (1f - sa)) / outA;
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    /** {@code color} forced opaque; the backdrop model only ever deals in opaque results. */
    @ColorInt
    public static int opaque(@ColorInt int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * The effective, opaque colour a band's content is drawn on, before any veil: the wallpaper as
     * sampled, then the launcher's own wallpaper dim, then whatever glass tint the band already
     * carries.
     *
     * <p>That is the real paint order — the root view carries the dim (black at the user's slider
     * percentage, see {@code TermuxActivity#resolveWallpaperBackdropDimColor}) and each glass
     * surface lays its tint on top of the blurred wallpaper. The dim can only darken and its
     * meaning is not changed here; it is an input, not something to adjust.</p>
     *
     * @param wallpaperSample an opaque sample of the wallpaper (or its frost crop) under the band
     * @param dim the launcher's wallpaper dim, black at the slider's alpha; may be transparent
     * @param glassTint the band's own glass tint, alpha included; may be transparent
     */
    @ColorInt
    public static int backdrop(@ColorInt int wallpaperSample, @ColorInt int dim, @ColorInt int glassTint) {
        return opaque(composite(glassTint, composite(dim, opaque(wallpaperSample))));
    }

    // ---------------------------------------------------------------- ink

    /** WCAG contrast ratio between an ink and a surface; a delegate so callers stay in this class. */
    public static double ratio(@ColorInt int ink, @ColorInt int surface) {
        return SchemeTone.contrastRatio(ink, surface);
    }

    /**
     * {@code seed} moved along its own HCT tone axis until it clears {@code target} against
     * {@code surface}, or returned unchanged when it already does. Hue and chroma survive, so a
     * branded blue stays a blue.
     */
    @ColorInt
    public static int inkOn(@ColorInt int surface, @ColorInt int seed, double target) {
        return SchemeTone.contrastTone(seed, surface, target);
    }

    /**
     * Whichever of two candidate inks reads better on {@code surface} — the "let the backdrop pick,
     * light or dark" entry point. Typically the mode's preferred ink versus its opposite.
     */
    @ColorInt
    public static int mostLegibleInk(@ColorInt int surface, @ColorInt int first, @ColorInt int second) {
        return SchemeTone.mostLegible(surface, first, second);
    }

    // ---------------------------------------------------------------- veil

    /**
     * The smallest 8-bit alpha of {@code veilColor} that, laid over {@code backdrop}, lets
     * {@code ink} clear {@code target} — or {@link #MAX_VEIL_ALPHA_255} when the cap is reached
     * first, or 0 when no veil is needed.
     *
     * <p>Searched one alpha step at a time rather than bisected: contrast against a composited
     * surface is not guaranteed monotonic in alpha for an arbitrary veil colour (a veil that passes
     * the ink's own luminance on the way makes the ratio dip and rise again), and the first
     * qualifying step is then the true minimum. At most {@link #MAX_VEIL_ALPHA_255} evaluations, and it runs when the
     * wallpaper, palette or mode changes, not per frame — {@link GlassBackdropCache} memoises the
     * result.</p>
     */
    public static int veilAlphaFor(@ColorInt int backdrop, @ColorInt int ink,
                                   @ColorInt int veilColor, double target) {
        int opaqueBackdrop = opaque(backdrop);
        if (ratio(ink, opaqueBackdrop) >= target) return 0;
        for (int alpha = 1; alpha <= MAX_VEIL_ALPHA_255; alpha++) {
            int surface = composite(withAlpha(veilColor, alpha), opaqueBackdrop);
            if (ratio(ink, surface) >= target) return alpha;
        }
        return MAX_VEIL_ALPHA_255;
    }

    /** {@code color} with its alpha channel replaced by an 8-bit {@code alpha}. */
    @ColorInt
    public static int withAlpha(@ColorInt int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
            Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * {@link #resolve(int, int, int, int, double)} with no alternate ink: the band keeps its mode's
     * colour or a tone of it, and never flips. For content whose colour carries meaning of its own.
     */
    @NonNull
    public static Resolution resolve(@ColorInt int backdrop, @ColorInt int preferredInk,
                                     @ColorInt int veilColor, double target) {
        return resolve(backdrop, preferredInk, preferredInk, veilColor, target);
    }

    /**
     * The whole answer for one band: how much veil it needs, what surface that leaves, and what ink
     * to draw on it.
     *
     * <p>In order, cheapest intervention first:</p>
     * <ol>
     *   <li>{@code preferredInk} already clears {@code target} on the bare backdrop — no veil,
     *       nothing changes. The wallpaper is untouched and this is the common case in dark mode.</li>
     *   <li>{@code alternateInk} clears it — no veil, the ink flips. This is the dark-wallpaper /
     *       light-mode case: the band is genuinely dark, so it takes the pale ink and the user keeps
     *       their wallpaper rather than a light slab over it. {@link Resolution#inkFlipped} is set.</li>
     *   <li>Neither reads: the least veil that gets the <em>preferred</em> ink to the target, capped
     *       at {@link #MAX_VEIL_ALPHA}. Veiling only ever moves toward the mode's own surface, so it
     *       only ever helps the mode's own ink — there is no point veiling for the alternate.</li>
     *   <li>The cap was not enough: the ink moves instead, {@link SchemeTone#contrastTone} on the
     *       capped surface, preferred hue first and the alternate's after it.</li>
     *   <li>Still short — a surface where no tone of either hue works: the more legible of black and
     *       white, with {@link Resolution#shortfall} set if even that misses.</li>
     * </ol>
     *
     * @param backdrop what the band is drawn on, from {@link #backdrop}; alpha is ignored
     * @param preferredInk the mode's own role colour for this content
     * @param alternateInk the other mode's colour for it, allowed to win when the backdrop belongs
     *     to the other mode; pass {@code preferredInk} to forbid the flip
     * @param veilColor the mode's surface colour to veil toward; its alpha is ignored
     * @param target one of {@link #TARGET_BODY_TEXT}, {@link #TARGET_LARGE_TEXT},
     *     {@link #TARGET_DECORATION}
     */
    @NonNull
    public static Resolution resolve(@ColorInt int backdrop, @ColorInt int preferredInk,
                                     @ColorInt int alternateInk, @ColorInt int veilColor,
                                     double target) {
        int base = opaque(backdrop);
        double bare = ratio(preferredInk, base);
        if (bare >= target) {
            return new Resolution(Color.TRANSPARENT, base, preferredInk, bare, target,
                false, false, false);
        }
        double bareAlternate = ratio(alternateInk, base);
        if (bareAlternate >= target) {
            return new Resolution(Color.TRANSPARENT, base, alternateInk, bareAlternate, target,
                false, true, false);
        }
        int alpha = veilAlphaFor(base, preferredInk, veilColor, target);
        int veil = alpha <= 0 ? Color.TRANSPARENT : withAlpha(veilColor, alpha);
        int surface = alpha <= 0 ? base : opaque(composite(veil, base));
        double achieved = ratio(preferredInk, surface);
        if (achieved >= target) {
            return new Resolution(veil, surface, preferredInk, achieved, target,
                alpha >= MAX_VEIL_ALPHA_255, false, false);
        }
        int toned = inkOn(surface, preferredInk, target);
        double tonedRatio = ratio(toned, surface);
        if (tonedRatio >= target) {
            return new Resolution(veil, surface, toned, tonedRatio, target, true, false, false);
        }
        int tonedAlternate = inkOn(surface, alternateInk, target);
        double tonedAlternateRatio = ratio(tonedAlternate, surface);
        if (tonedAlternateRatio >= target) {
            return new Resolution(veil, surface, tonedAlternate, tonedAlternateRatio, target,
                true, true, false);
        }
        int extreme = mostLegibleInk(surface, Color.WHITE, Color.BLACK);
        double extremeRatio = ratio(extreme, surface);
        if (extremeRatio >= Math.max(tonedRatio, tonedAlternateRatio)) {
            return new Resolution(veil, surface, extreme, extremeRatio, target, true, false,
                extremeRatio < target);
        }
        return new Resolution(veil, surface, toned, tonedRatio, target, true, false, true);
    }
}
