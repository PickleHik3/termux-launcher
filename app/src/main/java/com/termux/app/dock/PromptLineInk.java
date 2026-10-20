package com.termux.app.dock;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.theme.SchemeTone;

/**
 * The Prompt line's colours (spec "Prompt line"): a shell prompt drawn from the terminal's own
 * scheme, {@code ~} in its blue and {@code ❯} in its green, on a well a little darker than the
 * terminal's background, the hint in the foreground held back but still legible.
 *
 * <p>Every glyph clears {@link #TEXT_CONTRAST} against the well. The view lays the well over the
 * glass at {@link #WELL_ALPHA}, so a share of the glass shows through; the margin above WCAG's
 * 4.5:1 is there for that share. Pure: the view hands it the scheme's colours.</p>
 */
public final class PromptLineInk {

    /** WCAG's floor for text (R9). */
    public static final double MIN_TEXT_CONTRAST = 4.5d;
    /** What the prompt is held to, against the opaque well: the floor plus room for the glass. */
    public static final double TEXT_CONTRAST = 5.2d;
    /** How opaque the well lies on the slab. */
    public static final float WELL_ALPHA = 0.86f;
    /** How far the well sits below the terminal's background, in HCT tone. */
    static final double WELL_TONE_DROP = 6d;
    /** How much of the way to the well the hint is pulled back from the foreground. */
    static final float HINT_RECEDE = 0.38f;

    /** ANSI blue and green: the directory and the prompt glyph of the usual shell prompt. */
    public static final int ANSI_BLUE = 4;
    public static final int ANSI_GREEN = 2;

    @ColorInt public final int well;
    @ColorInt public final int directory;
    @ColorInt public final int glyph;
    @ColorInt public final int hint;

    private PromptLineInk(int well, int directory, int glyph, int hint) {
        this.well = well;
        this.directory = directory;
        this.glyph = glyph;
        this.hint = hint;
    }

    /**
     * The prompt's colours for a terminal scheme: its {@code background}, {@code foreground} and
     * ANSI {@code blue} and {@code green}. Alpha is ignored; the well comes back opaque.
     */
    @NonNull
    public static PromptLineInk resolve(@ColorInt int background, @ColorInt int foreground,
                                        @ColorInt int blue, @ColorInt int green) {
        int well = SchemeTone.toneShift(opaque(background), -WELL_TONE_DROP);
        int directory = SchemeTone.contrastTone(opaque(blue), well, TEXT_CONTRAST);
        int glyph = SchemeTone.contrastTone(opaque(green), well, TEXT_CONTRAST);
        int receded = SchemeTone.blend(opaque(foreground), well, HINT_RECEDE);
        int hint = SchemeTone.contrastTone(receded, well, TEXT_CONTRAST);
        return new PromptLineInk(well, directory, glyph, hint);
    }

    @ColorInt
    private static int opaque(@ColorInt int color) {
        return color | 0xFF000000;
    }
}
