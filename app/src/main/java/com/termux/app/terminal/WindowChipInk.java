package com.termux.app.terminal;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.chrome.OnGlass;
import com.termux.app.theme.SchemeTone;

/**
 * What a window chip is drawn in, once the band under it has been measured.
 *
 * <p>The window chips — the row's pills in {@link TerminalWindowBar} and the column's squares in
 * {@code StatusBarWindowColumn} — were dressed in alphas tuned against a dark bar: a fill at
 * {@code 16/255} of {@code termux_secondary}, a stroke at {@code 34}, a label at {@code 148}. Over
 * the light-mode glass the user reported ({@code #6A5755}) every one of those composites to within
 * one percent of the band itself — the measured label ratio was 1.03:1, which is why the chip
 * labelled {@code herdr} was simply not there. This is the arithmetic that replaces those numbers
 * with colours derived from what the band turned out to be.</p>
 *
 * <h3>Which tier each piece is held to</h3>
 * <ul>
 *   <li><b>The label</b> — {@link OnGlass#TARGET_BODY_TEXT}. It is body-sized text, selected or
 *       not; a resting chip is quieter than the current one because its ink starts muted and
 *       stops as soon as it clears, not because it is allowed to be illegible.</li>
 *   <li><b>The outline</b> — {@link OnGlass#TARGET_LARGE_TEXT}, the WCAG floor for meaningful
 *       graphics. The outline is what makes a chip a chip: it is the only thing that says where
 *       one window ends and the next begins, and on the reporting device it was the one part of
 *       the chip that survived at all. The same tier holds the marks that ride it — the busy
 *       ring, the bell, the tick — since each of them is the only carrier of its own fact.</li>
 *   <li><b>The fills</b> — deliberately <em>no</em> floor, not even
 *       {@link OnGlass#TARGET_DECORATION}. This is arithmetic, not taste: a fill that clears 2.0
 *       against a band at 10.5% luminance lands the chip's surface at roughly 26% luminance, and
 *       no ink at all clears 4.5 against that — a pale one would need a luminance above 1.0 and a
 *       dark one would have to be darker than the band the chrome already decided was too dark for
 *       dark ink. Giving the wash a floor would take the label's away. So a fill is a wash whose
 *       only duty is to not cost the label its tier, which is enforced by measuring the label on
 *       the surface the fill actually produces rather than on the bare band.</li>
 *   <li><b>The watermark glyph</b> — the same, and for the same reason. It is measured <em>into</em>
 *       the label's ground instead of being given a floor of its own.</li>
 * </ul>
 *
 * <h3>Why the ink moves in one direction</h3>
 * <p>{@link OnGlass#inkOn(int, int, double)} finds the <em>nearest</em> qualifying tone, either
 * side of the surface, which is right for a lone ink and wrong here: {@link
 * com.termux.app.chrome.ChromeInk} has already decided one polarity for the whole chrome, and a
 * chip whose outline went pale while its label went dark would break that on its own. Every ink
 * here therefore walks its own HCT tone axis in the polarity's direction only, through
 * {@link SchemeTone#toneShift}, and stops at the first tone that clears. Hue and chroma survive,
 * so the place's accent is still the place's accent.</p>
 *
 * <p>Pure, static and context free, so all of it unit tests on the JVM.</p>
 */
public final class WindowChipInk {

    private WindowChipInk() {}

    /**
     * How strong a resting chip's wash is, out of 255. Unchanged in spirit from the authored
     * {@code 16}: what was wrong with that number was never its size but its colour — at
     * {@code termux_secondary} over a photograph it composited to 1.01:1 of the band. Drawn in an
     * ink that is on the chrome's own side of the band it is a real, quiet lift.
     */
    public static final int RESTING_FILL_ALPHA = 26;

    /**
     * How strong the current chip's wash is. Enough to be seen as a wash of the place's accent
     * without becoming the surface the label is fighting; the outline and the label carry the
     * selection, this only agrees with them.
     */
    public static final int SELECTED_FILL_ALPHA = 64;

    /**
     * How far a resting chip's label starts below the current one's, in HCT tone. Both clear
     * {@link OnGlass#TARGET_BODY_TEXT}; this is what keeps "not the window you are in" legible as
     * a difference once they both do.
     */
    public static final double RESTING_LABEL_TONE_STEP = 18d;

    /**
     * How finely the chip's decoration is allowed to give way to its label. See
     * {@link #washBudget}: sixteenths is a step of about four units of alpha on the strongest
     * wash, which is below what the eye resolves on a translucent tint and well inside what the
     * arithmetic needs.
     */
    static final int WASH_STEPS = 16;

    /** Everything a window chip needs, with the band it was measured against. */
    public static final class Palette {

        /** The band's own effective opaque surface, as {@link OnGlass.Resolution#surface} gave it. */
        @ColorInt public final int band;

        /** The resting chip's wash, alpha included; drawn by the chip's own drawable. */
        @ColorInt public final int restingFill;
        /** The resting chip's outline, opaque and at {@link OnGlass#TARGET_LARGE_TEXT}. */
        @ColorInt public final int restingStroke;
        /** The current chip's wash, alpha included; drawn by the selection strip beneath the pills. */
        @ColorInt public final int selectedFill;
        /** The current chip's outline, in the place's accent, at {@link OnGlass#TARGET_LARGE_TEXT}. */
        @ColorInt public final int selectedStroke;

        /** The opaque colour a resting chip's content stands on: its wash over the band. */
        @ColorInt public final int restingSurface;
        /**
         * The opaque colour the current chip's content stands on. Both washes: the strip draws the
         * selection under the pill and the pill still draws its own, so they compose.
         */
        @ColorInt public final int selectedSurface;

        /**
         * The worst ground a resting label can land on — its surface, or that surface with the
         * watermark printed on it, whichever is closer to the ink. What {@link #restingLabel} was
         * measured against.
         */
        @ColorInt public final int restingGround;
        /** The same for the current chip, whose watermark is printed harder. */
        @ColorInt public final int selectedGround;

        /** The resting label, clearing {@link OnGlass#TARGET_BODY_TEXT} on {@link #restingGround}. */
        @ColorInt public final int restingLabel;
        /** The current label, clearing {@link OnGlass#TARGET_BODY_TEXT} on {@link #selectedGround}. */
        @ColorInt public final int selectedLabel;

        /** The watermark, opaque; the drawable prints it at the geometry's own alphas. */
        @ColorInt public final int glyph;

        /** The halo a title is shadowed with on each chip: that chip's own surface, near-opaque. */
        @ColorInt public final int restingHalo;
        @ColorInt public final int selectedHalo;

        /** The ring of ground a corner dot is haloed against: the chip it sits on. */
        @ColorInt public final int dotGround;

        Palette(int band, int restingFill, int restingStroke, int selectedFill, int selectedStroke,
                int restingSurface, int selectedSurface, int restingGround, int selectedGround,
                int restingLabel, int selectedLabel, int glyph, int restingHalo, int selectedHalo,
                int dotGround) {
            this.band = band;
            this.restingFill = restingFill;
            this.restingStroke = restingStroke;
            this.selectedFill = selectedFill;
            this.selectedStroke = selectedStroke;
            this.restingSurface = restingSurface;
            this.selectedSurface = selectedSurface;
            this.restingGround = restingGround;
            this.selectedGround = selectedGround;
            this.restingLabel = restingLabel;
            this.selectedLabel = selectedLabel;
            this.glyph = glyph;
            this.restingHalo = restingHalo;
            this.selectedHalo = selectedHalo;
            this.dotGround = dotGround;
        }
    }

    /**
     * The whole chip palette for a band that has been measured.
     *
     * @param band the band's effective opaque surface — {@link OnGlass.Resolution#surface}
     * @param pale whether the chrome is drawing in its pale ink, from
     *     {@link com.termux.app.chrome.ChromeInk#polarity()}. Never re-derived here: one polarity
     *     for the whole chrome is the point of deciding it there.
     * @param neutralSeed the theme's text neutral on the chrome's side — the lighter of
     *     {@code termuxColorOnSurface} and {@code termuxColorSurfaceBase} when the chrome is pale,
     *     the darker of the two when it is dark. Which of the pair is which is a property of the
     *     colours, not of the mode, so nothing here reads the night qualifier.
     * @param accentSeed the place's own colour, whose hue carries meaning and therefore only ever
     *     moves along its own tone axis
     */
    @NonNull
    public static Palette resolve(@ColorInt int band, boolean pale,
                                  @ColorInt int neutralSeed, @ColorInt int accentSeed) {
        int opaqueBand = OnGlass.opaque(band);
        int restingStroke = towardPolarity(opaqueBand, neutralSeed, pale, OnGlass.TARGET_LARGE_TEXT);
        int selectedStroke = towardPolarity(opaqueBand, accentSeed, pale, OnGlass.TARGET_LARGE_TEXT);
        int glyphInk = towardPolarity(opaqueBand, accentSeed, pale, OnGlass.TARGET_LARGE_TEXT);

        int budget = washBudget(opaqueBand, restingStroke, selectedStroke, glyphInk, pale);
        int restingFill = OnGlass.withAlpha(restingStroke, scaled(RESTING_FILL_ALPHA, budget));
        int selectedFill = OnGlass.withAlpha(selectedStroke, scaled(SELECTED_FILL_ALPHA, budget));
        // The glyph's alphas belong to the drawable, which interpolates them across a selection
        // slide, so what gives way here is the colour they are spent on: a watermark pulled back
        // toward the band prints exactly as faintly as a lowered alpha would.
        int glyph = SchemeTone.blend(opaqueBand, glyphInk, budget / (float) WASH_STEPS);

        int restingSurface = OnGlass.opaque(OnGlass.composite(restingFill, opaqueBand));
        // The strip draws the selection under the pill and the pill still draws its own wash on
        // top, so the current chip's surface is both of them, in that order.
        int selectedSurface = OnGlass.opaque(OnGlass.composite(restingFill,
            OnGlass.composite(selectedFill, opaqueBand)));

        int restingGround = worstGround(restingSurface, glyph,
            ChipWatermarkGeometry.GLYPH_ALPHA, pale);
        int selectedGround = worstGround(selectedSurface, glyph,
            ChipWatermarkGeometry.SELECTED_GLYPH_ALPHA, pale);

        int selectedLabel = towardPolarity(selectedGround, neutralSeed, pale,
            OnGlass.TARGET_BODY_TEXT);
        int restingLabel = towardPolarity(restingGround,
            SchemeTone.toneShift(neutralSeed, pale ? -RESTING_LABEL_TONE_STEP : RESTING_LABEL_TONE_STEP),
            pale, OnGlass.TARGET_BODY_TEXT);

        return new Palette(opaqueBand, restingFill, restingStroke, selectedFill, selectedStroke,
            restingSurface, selectedSurface, restingGround, selectedGround,
            restingLabel, selectedLabel, glyph,
            ChipWatermarkGeometry.haloColor(restingSurface),
            ChipWatermarkGeometry.haloColor(selectedSurface),
            restingSurface);
    }

    /**
     * How much of the chip's decoration the band can afford, in sixteenths.
     *
     * <p>A chip's wash and its watermark both stand between the label and the band, and both move
     * the label's ground toward the ink — a pale wash on a dark band lifts what the pale label has
     * to beat. The band itself arrives with no margin to spend: {@link com.termux.app.chrome.ChromeInk}
     * veils it by the <em>smallest</em> amount that gets the chrome's ink to
     * {@link OnGlass#TARGET_BODY_TEXT}, so a band that needed veiling is sitting exactly on 4.5
     * and any decoration at all would push it under.</p>
     *
     * <p>So the decoration is the thing that gives way, never the label. This walks the wash down
     * from full strength and stops at the first step where the polarity's own extreme — white for
     * pale chrome, black for dark — still clears body text on both chips' grounds. Over a generous
     * band nothing is given up; over the band the user reported the chips lose a little; over a
     * band that only just carries the chrome at all they come out as an outline and a title, which
     * is the right answer and not a failure.</p>
     */
    static int washBudget(@ColorInt int band, @ColorInt int restingStroke,
                          @ColorInt int selectedStroke, @ColorInt int glyphInk, boolean pale) {
        int extreme = pale ? Color.WHITE : Color.BLACK;
        for (int step = WASH_STEPS; step > 0; step--) {
            int restingFill = OnGlass.withAlpha(restingStroke, scaled(RESTING_FILL_ALPHA, step));
            int selectedFill = OnGlass.withAlpha(selectedStroke, scaled(SELECTED_FILL_ALPHA, step));
            int glyph = SchemeTone.blend(band, glyphInk, step / (float) WASH_STEPS);
            int restingSurface = OnGlass.opaque(OnGlass.composite(restingFill, band));
            int selectedSurface = OnGlass.opaque(OnGlass.composite(restingFill,
                OnGlass.composite(selectedFill, band)));
            boolean restingHolds = OnGlass.ratio(extreme,
                worstGround(restingSurface, glyph, ChipWatermarkGeometry.GLYPH_ALPHA, pale))
                >= OnGlass.TARGET_BODY_TEXT;
            boolean selectedHolds = OnGlass.ratio(extreme,
                worstGround(selectedSurface, glyph, ChipWatermarkGeometry.SELECTED_GLYPH_ALPHA, pale))
                >= OnGlass.TARGET_BODY_TEXT;
            if (restingHolds && selectedHolds) return step;
        }
        return 0;
    }

    private static int scaled(int alpha, int budget) {
        return Math.round(alpha * budget / (float) WASH_STEPS);
    }

    /**
     * {@code seed}'s hue and chroma at the first tone, walking in {@code pale}'s direction only,
     * that clears {@code target} against {@code surface}.
     *
     * <p>The directed half of {@link OnGlass#inkOn(int, int, double)}, which is otherwise the
     * right tool: that one keeps the <em>nearest</em> qualifying tone, and on a mid band the
     * nearest is a coin flip between a light answer and a dark one. The chrome has already spent a
     * whole decision on having one polarity (see {@link com.termux.app.chrome.ChromeInk}), so a
     * chip may not re-open it — least of all per element, which is how an outline and the label
     * inside it end up disagreeing.</p>
     *
     * <p>Walks in {@link SchemeTone#toneShift} steps, which is HCT: the hue and the chroma of the
     * place's accent survive the walk, so a warm place stays warm however far the ink has to
     * travel. Ends at white or black for a surface no tone of this hue can beat.</p>
     */
    @ColorInt
    public static int towardPolarity(@ColorInt int surface, @ColorInt int seed, boolean pale,
                                     double target) {
        int base = OnGlass.opaque(surface);
        if (onPolaritySide(base, seed, pale) && OnGlass.ratio(seed, base) >= target) return seed;
        for (int step = 1; step <= 50; step++) {
            int candidate = SchemeTone.toneShift(seed, pale ? 2d * step : -2d * step);
            if (onPolaritySide(base, candidate, pale) && OnGlass.ratio(candidate, base) >= target) {
                return candidate;
            }
        }
        return pale ? Color.WHITE : Color.BLACK;
    }

    /** Whether {@code ink} stands on the chrome's own side of {@code surface}. */
    public static boolean onPolaritySide(@ColorInt int surface, @ColorInt int ink, boolean pale) {
        double inkTone = SchemeTone.tone(ink);
        double surfaceTone = SchemeTone.tone(surface);
        return pale ? inkTone > surfaceTone : inkTone < surfaceTone;
    }

    /**
     * The ground a label on {@code surface} really has to clear: the surface itself, or the
     * surface with the watermark printed over it at {@code glyphAlpha} — whichever of the two is
     * closer to the ink, since the ink has to read on both.
     *
     * <p>The watermark is not given a contrast floor of its own anywhere. A floor would fight the
     * label's (see the class doc) and lose, so instead the glyph is folded into what the label is
     * measured against. The title's halo then pulls that ground back toward the plain surface,
     * which can only help — the promise was made without it.</p>
     */
    @ColorInt
    public static int worstGround(@ColorInt int surface, @ColorInt int glyph, int glyphAlpha,
                                  boolean pale) {
        int printed = OnGlass.opaque(
            OnGlass.composite(OnGlass.withAlpha(glyph, glyphAlpha), OnGlass.opaque(surface)));
        boolean printedIsWorse = pale
            ? SchemeTone.tone(printed) > SchemeTone.tone(surface)
            : SchemeTone.tone(printed) < SchemeTone.tone(surface);
        return printedIsWorse ? printed : OnGlass.opaque(surface);
    }

    /**
     * Whichever of a role's two authored colours is the chrome's own ink right now.
     *
     * <p>A role like "text on a surface" is declared twice — {@code termuxColorOnSurface} is
     * {@code #171C24} in {@code values/} and {@code #E8EDF7} in {@code values-night/} — but only
     * the running mode's copy is reachable through the theme. Its opposite is reachable all the
     * same: the surface the role was authored against is the same pair upside down, so
     * {@code onSurface} and {@code surfaceBase} together are the dark ink and the pale one in
     * either mode. Which is which is read off the colours, never off the night qualifier.</p>
     */
    @ColorInt
    public static int neutralSeed(@ColorInt int onSurface, @ColorInt int surfaceBase, boolean pale) {
        boolean onSurfaceIsPaler = SchemeTone.tone(onSurface) >= SchemeTone.tone(surfaceBase);
        if (pale) return onSurfaceIsPaler ? onSurface : surfaceBase;
        return onSurfaceIsPaler ? surfaceBase : onSurface;
    }
}
