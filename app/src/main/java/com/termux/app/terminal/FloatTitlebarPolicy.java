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

    /**
     * The strip's height: one row of the 18dp glyphs with 3dp of air above and below. The only
     * text it carries is the focused Home float's readings ({@link #readingsThatFit}), which give
     * way along the strip rather than ask for more height; a thumb's target is
     * {@link #BUTTON_REACH_DP}, not this.
     */
    public static final int HEIGHT_DP = 24;
    /** The air between the strip and the pane under it, so each reads as its own slab. */
    public static final int GAP_DP = 3;
    /** The width a button takes while the float is wide enough for every button at full size. */
    public static final int BUTTON_DP = 40;
    /** The drag handle's grip bar, drawn in the strip's free span when the span can hold it. */
    public static final int GRIP_WIDTH_DP = 28;
    /** The least air the grip keeps on either side inside its span. */
    public static final int GRIP_AIR_DP = 8;
    /**
     * Where the focused Home float's readings start, in from the strip's leading edge: clear of
     * the strip's rounded corner (at most half its height) once the readings' own padding is
     * counted.
     */
    public static final int READINGS_INSET_DP = 4;
    /**
     * How far down from the strip's top a button still takes a press: a thumb-sized target on a
     * thin strip. Past the strip it reaches over the gap and the pane's top edge, under the
     * buttons only; the rest of the pane's top stays the terminal's.
     */
    public static final int BUTTON_REACH_DP = 40;

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
     * How many of the focused Home float's readings the strip shows, leading to trailing: the
     * strip lays out, from its leading edge, the readings (CPU, then RAM, whichever the status
     * bar's settings show), then the drag handle, then the buttons. The buttons are never given
     * up and the handle keeps at least {@code handleMinPx} (the grip and its air), so readings
     * that do not fit in what is left drop from the trailing end: with both on, RAM goes first,
     * then CPU. A reading is dropped whole, never ellipsized; nothing overlaps the handle or a
     * button. Font scale and density reach this only through the measured widths.
     *
     * @param readingsEndPx where each count of readings ends, from the strip's leading edge:
     *     {@code readingsEndPx[k - 1]} is the end of the first {@code k} readings, inset included
     */
    public static int readingsThatFit(int stripWidthPx, int buttonsPx, int handleMinPx,
                                      @NonNull int[] readingsEndPx) {
        int room = stripWidthPx - Math.max(0, buttonsPx) - Math.max(0, handleMinPx);
        int shown = 0;
        for (int k = 1; k <= readingsEndPx.length; k++) {
            if (readingsEndPx[k - 1] > room) break;
            shown = k;
        }
        return shown;
    }

    /**
     * Whether the readings stand in a strip {@code stripHeightPx} tall at their own height
     * {@code readingHeightPx}, their text at the user's font scale. Taller, they are dropped,
     * never clipped or shrunk, so whatever shows reads exactly as the status bar's. In the 24dp
     * strip the readings fit at font scale 1.3 (about 20dp) and give way near 1.5.
     */
    public static boolean readingsFitHeight(int stripHeightPx, int readingHeightPx) {
        return readingHeightPx > 0 && readingHeightPx <= stripHeightPx;
    }

    /**
     * The drag handle's grip, centred in the span between the readings' end
     * ({@code readingsEndPx}, 0 with none) and the buttons, measured from the leading edge: the
     * grip's centre, or -1 when the span cannot hold {@code gripPx} with {@code airPx} either side.
     */
    public static float gripCenterPx(int stripWidthPx, int buttonsPx, int readingsEndPx,
                                     int gripPx, int airPx) {
        int start = Math.max(0, readingsEndPx);
        int end = stripWidthPx - Math.max(0, buttonsPx);
        if (end - start < gripPx + 2 * airPx) return -1f;
        return (start + end) / 2f;
    }

    /**
     * Whether a press {@code yPx} below the strip's top is within a button's reach: inside the
     * strip, or below it as far as {@code reachPx} from its top. Never above the strip.
     */
    public static boolean inButtonReach(float yPx, int stripHeightPx, int reachPx) {
        return yPx >= 0f && yPx < Math.max(stripHeightPx, reachPx);
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
