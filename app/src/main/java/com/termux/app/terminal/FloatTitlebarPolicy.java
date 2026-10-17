package com.termux.app.terminal;

import android.graphics.RectF;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;

/**
 * What a float's titlebar offers and what its buttons do to the float's shape. Pure, so the button
 * set, the slot arithmetic and Maximise's memory are asserted without a window to float.
 *
 * <p>The titlebar is the one floating-pane design (docs/GLOSSARY.md, "Titlebar"): a strip along
 * the float's top carrying the drag handle and the buttons. The host declares which buttons it
 * offers by whether it has somewhere for Minimise to go; Maximise and Close are always there. Dock
 * is not on the strip: it is in the menu a long press on the handle opens ({@link #handleMenu}),
 * so no glyph on the strip reads as Split.
 */
public final class FloatTitlebarPolicy {

    private FloatTitlebarPolicy() {}

    public static final int ACTION_NONE = 0;
    public static final int ACTION_MINIMISE = 1;
    public static final int ACTION_MAXIMISE = 2;
    public static final int ACTION_DOCK = 3;
    public static final int ACTION_CLOSE = 4;

    /** The strip's height: one row of buttons, tall enough to grab with a thumb. */
    public static final int HEIGHT_DP = 32;
    /** The air between the strip and the pane under it, so each reads as its own slab. */
    public static final int GAP_DP = 3;
    /** The width a button takes while the float is wide enough for every button at full size. */
    public static final int BUTTON_DP = 40;
    /** The drag handle's grip bar, drawn in the strip's free span when the span can hold it. */
    public static final int GRIP_WIDTH_DP = 28;

    /**
     * The buttons a titlebar shows, leading to trailing, all at the trailing end with Close
     * outermost as on every desktop. Minimise is listed only when the host has a target for it:
     * a Home float goes into its chip, the scratchpad hides, a window's floating pane has nowhere
     * to go yet.
     */
    @NonNull
    public static int[] buttons(boolean hasMinimiseTarget) {
        return hasMinimiseTarget
            ? new int[]{ACTION_MINIMISE, ACTION_MAXIMISE, ACTION_CLOSE}
            : new int[]{ACTION_MAXIMISE, ACTION_CLOSE};
    }

    /**
     * The rows of the menu a long press on the drag handle opens, top to bottom, under the
     * window's name. Dock is the only row in phase one, on every float.
     */
    @NonNull
    public static int[] handleMenu() {
        return new int[]{ACTION_DOCK};
    }

    /**
     * How wide each button's slot is on a strip {@code stripWidthPx} wide: the preferred width
     * while every button fits, else the strip shared evenly, so a narrow float never loses Close.
     */
    public static int buttonSlotPx(int stripWidthPx, int count, int preferredPx) {
        if (count <= 0 || stripWidthPx <= 0) return 0;
        return Math.max(0, Math.min(preferredPx, stripWidthPx / count));
    }

    /**
     * The colour the strip's glyphs and grip are drawn in.
     *
     * <p>On glass it is the ink the window bar's chip labels use: the theme's neutral on the
     * chrome's one polarity ({@link WindowChipInk#neutralSeed}, the paler of {@code onSurface} and
     * {@code surfaceBase} under pale ink), at full alpha on a focused float and an unfocused one
     * alike. The strip's own tint is not what the eye sees under it, the wallpaper through it is,
     * and {@code polarity} is what has measured that: solved against the tint alone, the glyphs came
     * out near black over a dark wallpaper and read as disabled.
     *
     * <p>On the plain panel, or before any polarity is published, the strip is its own opaque
     * surface ({@code stripSurface}) and the ink is {@code onSurface} made legible on it, a step
     * quieter on an unfocused float so focus stays readable.
     */
    @ColorInt
    public static int glyphInk(boolean glass, @Nullable ChromeInk.Polarity polarity,
                               @ColorInt int onSurface, @ColorInt int surfaceBase,
                               @ColorInt int stripSurface, boolean focused) {
        if (glass && polarity != null) {
            return OnGlass.withAlpha(WindowChipInk.neutralSeed(onSurface, surfaceBase,
                polarity == ChromeInk.Polarity.PALE_INK), 255);
        }
        return GlassInk.legible(stripSurface, onSurface, OnGlass.TARGET_LARGE_TEXT,
            focused ? 255 : 170);
    }

    /** True while {@code restoreFrac} holds the shape Maximise will restore. */
    public static boolean isMaximised(@Nullable RectF restoreFrac) {
        return restoreFrac != null;
    }

    /** The float's shape while maximised: the whole room of its host. */
    @NonNull
    public static RectF fullRoom() {
        return new RectF(0f, 0f, 1f, 1f);
    }

    /**
     * One tap of Maximise. Returns the float's new fractions and what to remember: maximising
     * keeps {@code current} to restore; restoring hands back the remembered shape and forgets it.
     *
     * @param current the float's shape now, as fractions of its host
     * @param restoreFrac the remembered pre-maximise shape, or null while not maximised
     */
    @NonNull
    public static Toggle toggleMaximise(@NonNull RectF current, @Nullable RectF restoreFrac) {
        if (isMaximised(restoreFrac)) return new Toggle(new RectF(restoreFrac), null);
        return new Toggle(fullRoom(), new RectF(current));
    }

    /** What a Maximise tap leaves: the float's fractions and the shape to restore, if any. */
    public static final class Toggle {
        @NonNull public final RectF frac;
        @Nullable public final RectF restoreFrac;

        Toggle(@NonNull RectF frac, @Nullable RectF restoreFrac) {
            this.frac = frac;
            this.restoreFrac = restoreFrac;
        }
    }
}
