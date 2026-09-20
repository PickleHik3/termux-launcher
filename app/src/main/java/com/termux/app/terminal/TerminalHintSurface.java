package com.termux.app.terminal;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The material every hint that hangs off the terminal is drawn in.
 *
 * <p>One dress, so the mode legends (copy mode, scrollback search) and the keybind hints read as
 * the same kind of thing arriving from the same place. Flat — no elevation, no shadow — and a
 * little transparent, because all of them sit over live output that has to stay readable
 * underneath, and a shadowed opaque panel there reads as a dialog demanding an answer.
 *
 * <p>Square where it meets the terminal's top edge and rounded where it leaves it: the corners are
 * what make a hint read as an extension of the terminal window rather than as a card floating on
 * top of one, so they are given per corner rather than as one radius.
 */
public final class TerminalHintSurface {

    private TerminalHintSurface() {}

    /** Alpha a hint settles at: enough to read, not enough to hide the transcript behind it. */
    public static final float REST_ALPHA = 0.94f;
    /**
     * Flat material, so the fill and the hairline are the whole surface. Near opaque: at 224 the
     * transcript's glyphs still read through the card beside its own words, and a line of the
     * legend with a URL showing between its letters looked like two things drawn on one spot.
     */
    private static final int SURFACE_ALPHA = 246;
    /**
     * The same material for a panel that is worked in rather than read past: the session prompts and
     * the search bar carry a field, a list and the answer the user is about to give, and at a hint's
     * alpha the transcript's own glyphs came through the rows and made both hard to read.
     */
    private static final int WORKING_SURFACE_ALPHA = 250;
    private static final int OUTLINE_ALPHA = 70;
    /**
     * The softening a hint keeps where the terminal has no radius of its own to lend it: a flush
     * full-bleed terminal is square, but a hint with literally square free corners reads as a torn
     * rectangle rather than as a tab — the same 4dp the glass slabs fall back to for that reason.
     */
    private static final float MIN_FREE_CORNER_DP = 4f;
    /**
     * And the most a free corner rounds. Past this the notch a free corner leaves over the
     * transcript is deep enough to show a word of it right against the legend's own first
     * glyph - two texts on one spot - so the free corners follow the knob only this far while
     * the corner that sits on the terminal's still takes the terminal's radius whole.
     */
    private static final float MAX_FREE_CORNER_DP = 8f;

    /**
     * The radius a hint's free corners take.
     *
     * <p>The terminal's own, so the whole shape moves with the corner-radius knob instead of only
     * the one corner that physically sits on the terminal's. A fixed radius here was the reason a
     * hint stopped agreeing with its window the moment the knob left the middle of its range: at 40
     * the terminal's arc dwarfed the hint's, at 4 the hint was the rounder of the two.
     *
     * @param heightPx the hint's measured height, or 0 before it has one. A hint is short and the
     *     knob reaches 40dp, so the raw radius would round a two-line strip past a pill; half the
     *     height is where a corner stops being a corner.
     */
    public static float freeCornerRadiusPx(@NonNull Context context, float terminalRadiusPx,
                                           int heightPx) {
        float density = context.getResources().getDisplayMetrics().density;
        float minimum = MIN_FREE_CORNER_DP * density;
        float free = Math.min(Math.max(minimum, terminalRadiusPx), MAX_FREE_CORNER_DP * density);
        return heightPx > 0 ? Math.min(free, heightPx / 2f) : free;
    }

    /**
     * @param topStartRadiusPx the terminal's own radius where the hint's leading top corner sits on
     *     it, or 0 where the hint hangs clear of that corner.
     * @param topEndRadiusPx the same for the trailing top corner.
     * @param free where the hint leaves the terminal's edge, from
     *     {@link #freeCornerRadiusPx}.
     */
    @NonNull
    public static GradientDrawable background(@NonNull Context context, float topStartRadiusPx,
                                              float topEndRadiusPx, float free) {
        // top-left, top-right, bottom-right, bottom-left (x and y per corner)
        return shape(context, SURFACE_ALPHA, new float[]{
            topStartRadiusPx, topStartRadiusPx,
            topEndRadiusPx, topEndRadiusPx,
            free, free,
            free, free});
    }

    /**
     * The same dress for a panel that rises from the terminal's <em>bottom</em> edge — the workspace
     * prompts and the scrollback search bar — so the whole family reads as part of the terminal
     * window wherever it arrives from. The corners are simply the other way up: the terminal's own
     * radius where the panel sits in its bottom corners, the free radius where it leaves the edge.
     *
     * @param bottomRadiusPx the terminal's own corner radius.
     * @param free where the panel leaves the terminal's edge, from {@link #freeCornerRadiusPx}.
     */
    @NonNull
    public static GradientDrawable footBackground(@NonNull Context context, float bottomRadiusPx,
                                                  float free) {
        return shape(context, WORKING_SURFACE_ALPHA, new float[]{
            free, free,
            free, free,
            bottomRadiusPx, bottomRadiusPx,
            bottomRadiusPx, bottomRadiusPx});
    }

    @NonNull
    private static GradientDrawable shape(@NonNull Context context, int surfaceAlpha,
                                          @NonNull float[] radii) {
        float density = context.getResources().getDisplayMetrics().density;
        int surface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                ContextCompat.getColor(context, R.color.termux_surface_panel_high)));
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(surface, surfaceAlpha));
        shape.setStroke(Math.max(1, Math.round(density)),
            ColorUtils.setAlphaComponent(outline, OUTLINE_ALPHA));
        shape.setCornerRadii(radii);
        return shape;
    }
}
