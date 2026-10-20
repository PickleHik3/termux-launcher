package com.termux.app.dock;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.theme.SchemeTone;

/**
 * The Prompt line's colours (spec "Prompt line"): a shell prompt drawn from the terminal's own
 * scheme, {@code ~} in its blue and {@code ❯} in its green, on a well that reads as a darker
 * recess in the slab, the hint in the foreground held back but still legible.
 *
 * <p>On a dark scheme the well is the terminal's background a step darker, laid over the glass at
 * {@link #WELL_ALPHA}, so a share of the glass shows through; the margin above WCAG's 4.5:1 is
 * there for that share. On a light scheme that well would be paler than the light glass around
 * it, a raised patch rather than a recess, so the well is instead a translucent dark tint
 * ({@link #LIGHT_TINT_ALPHA}) over the glass: whatever the glass is, the well is darker than it.
 * Its inks are then measured against the tint as it lies on the dimmest light glass the dock
 * shows ({@link #LIGHT_GLASS_FLOOR_TONE}) under a finger ({@link #pressedWellAlpha}), and as they
 * are dark inks on a light ground, any lighter glass only raises their contrast.</p>
 *
 * <p>Every glyph clears {@link #TEXT_CONTRAST} against {@link #inkSurface}. Pure: the view hands
 * it the scheme's colours.</p>
 */
public final class PromptLineInk {

    /** WCAG's floor for text (R9). */
    public static final double MIN_TEXT_CONTRAST = 4.5d;
    /** What the prompt is held to, against the ink surface: the floor plus room for the glass. */
    public static final double TEXT_CONTRAST = 5.2d;
    /** How opaque the dark scheme's well lies on the slab. */
    public static final float WELL_ALPHA = 0.86f;
    /** How far the dark scheme's well sits below the terminal's background, in HCT tone. */
    static final double WELL_TONE_DROP = 6d;
    /** How much of the way to the well the hint is pulled back from the foreground. */
    static final float HINT_RECEDE = 0.38f;

    /** The light scheme's tint: the background's own hue taken down to this HCT tone. */
    static final double LIGHT_TINT_TONE = 10d;
    /** How opaque the light scheme's tint lies on the glass. */
    public static final float LIGHT_TINT_ALPHA = 0.24f;
    /** How much more of the tint shows under a finger. */
    static final float LIGHT_PRESSED_ALPHA_STEP = 0.06f;
    /**
     * The dimmest the light glass is taken to read, in HCT tone: the light scheme's inks are
     * chosen against the tint over glass this dark, so brighter glass only adds contrast.
     */
    static final double LIGHT_GLASS_FLOOR_TONE = 72d;

    /** ANSI blue and green: the directory and the prompt glyph of the usual shell prompt. */
    public static final int ANSI_BLUE = 4;
    public static final int ANSI_GREEN = 2;

    /** The well's colour, opaque; the view lays it at {@link #wellAlpha}. */
    @ColorInt public final int well;
    /** How opaque the well lies on the glass at rest. */
    public final float wellAlpha;
    /** How opaque it lies under a finger, for a well drawn by its alpha ({@link #tinted}). */
    public final float pressedWellAlpha;
    /** Whether the well is a translucent dark tint over light glass, rather than a solid well. */
    public final boolean tinted;
    /** What the inks are measured against: the well as it reads where they stand. */
    @ColorInt public final int inkSurface;
    @ColorInt public final int directory;
    @ColorInt public final int glyph;
    @ColorInt public final int hint;

    private PromptLineInk(int well, float wellAlpha, float pressedWellAlpha, boolean tinted,
                          int inkSurface, int directory, int glyph, int hint) {
        this.well = well;
        this.wellAlpha = wellAlpha;
        this.pressedWellAlpha = pressedWellAlpha;
        this.tinted = tinted;
        this.inkSurface = inkSurface;
        this.directory = directory;
        this.glyph = glyph;
        this.hint = hint;
    }

    /**
     * The prompt's colours for a terminal scheme: its {@code background}, {@code foreground} and
     * ANSI {@code blue} and {@code green}. Alpha is ignored; the well comes back opaque, with the
     * alpha to lay it at.
     */
    @NonNull
    public static PromptLineInk resolve(@ColorInt int background, @ColorInt int foreground,
                                        @ColorInt int blue, @ColorInt int green) {
        int ground = opaque(background);
        int well;
        float alpha;
        float pressedAlpha;
        boolean tinted = !SchemeTone.isDark(ground);
        int surface;
        if (tinted) {
            well = SchemeTone.toneShift(ground, LIGHT_TINT_TONE - SchemeTone.tone(ground));
            alpha = LIGHT_TINT_ALPHA;
            pressedAlpha = LIGHT_TINT_ALPHA + LIGHT_PRESSED_ALPHA_STEP;
            int glass = SchemeTone.toneShift(ground,
                Math.min(0d, LIGHT_GLASS_FLOOR_TONE - SchemeTone.tone(ground)));
            // The darkest the well reads: pressed, on the dimmest glass.
            surface = SchemeTone.blend(glass, well, pressedAlpha);
        } else {
            well = SchemeTone.toneShift(ground, -WELL_TONE_DROP);
            alpha = WELL_ALPHA;
            pressedAlpha = WELL_ALPHA;
            surface = well;
        }
        int directory = SchemeTone.contrastTone(opaque(blue), surface, TEXT_CONTRAST);
        int glyph = SchemeTone.contrastTone(opaque(green), surface, TEXT_CONTRAST);
        int receded = SchemeTone.blend(opaque(foreground), surface, HINT_RECEDE);
        int hint = SchemeTone.contrastTone(receded, surface, TEXT_CONTRAST);
        return new PromptLineInk(well, alpha, pressedAlpha, tinted, surface, directory, glyph,
            hint);
    }

    /** Whether {@code other} holds the same colours. */
    public boolean sameAs(@androidx.annotation.Nullable PromptLineInk other) {
        return other != null && other.well == well && other.wellAlpha == wellAlpha
            && other.tinted == tinted && other.inkSurface == inkSurface
            && other.directory == directory && other.glyph == glyph && other.hint == hint;
    }

    @ColorInt
    private static int opaque(@ColorInt int color) {
        return color | 0xFF000000;
    }
}
