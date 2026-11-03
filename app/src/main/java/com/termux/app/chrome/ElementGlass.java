package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * One glass element's look on one page, read from the look grid (ADR 0017): the numbers a
 * {@link GlassStack.Spec} is built from, plus the refraction, tint colour and rim the element
 * holds of its own. Every element reads the same four cells in the same order, so a surface that
 * borrowed another element's glass (the drawer the dock's, say) now names its own element.
 *
 * <p>Pure: {@link #resolve} reads a {@link LookGrid} and nothing else. It runs where a surface is
 * built or dressed, the same moments the named getters were read, never per frame.</p>
 *
 * <p>Parity: Depth, Edge, the tint colour and the rim are global numbers the renderer already
 * holds (the Fancier Glass look, {@link GlassLook#of}). An element only replaces them where a cell
 * of its own, or a page's, holds a value ({@link #ownCell}); following Base it hands the global
 * look back untouched, so a grid holding only Base draws exactly what it did.</p>
 */
public final class ElementGlass {

    /** The blur ceiling the getters clamp to (the terminal's). */
    static final int MAX_BLUR_DP = 48;

    @NonNull public final LookGrid.Element element;
    public final int blurDp;
    public final int opacityPercent;
    public final int grainPercent;
    public final int tintPercent;
    /** Corners as stored: dp, or {@link LookGrid#CORNERS_AUTO}. */
    public final int cornersDp;
    /** The tint colour this element holds of its own, or null while it follows Base. */
    @Nullable public final GlassLook.Hue ownHue;
    /** The rim ({@link LookGrid#RIM_OFF} and friends) of its own, or null while it follows Base. */
    @Nullable public final Integer ownRim;
    /** The refraction bend in dp of its own, or null while it follows Base. */
    @Nullable public final Integer ownDepthDp;
    /** The dispersion of its own, or null while it follows Base. */
    @Nullable public final Integer ownEdgePercent;

    private ElementGlass(@NonNull LookGrid.Element element, int blurDp, int opacityPercent,
                         int grainPercent, int tintPercent, int cornersDp,
                         @Nullable GlassLook.Hue ownHue, @Nullable Integer ownRim,
                         @Nullable Integer ownDepthDp, @Nullable Integer ownEdgePercent) {
        this.element = element;
        this.blurDp = blurDp;
        this.opacityPercent = opacityPercent;
        this.grainPercent = grainPercent;
        this.tintPercent = tintPercent;
        this.cornersDp = cornersDp;
        this.ownHue = ownHue;
        this.ownRim = ownRim;
        this.ownDepthDp = ownDepthDp;
        this.ownEdgePercent = ownEdgePercent;
    }

    /** {@code element} on {@code pageId} (a negative id: everywhere), read from {@code grid}. */
    @NonNull
    public static ElementGlass resolve(@NonNull LookGrid grid, @NonNull LookGrid.Element element,
                                       int pageId) {
        int page = pageId >= 0 ? pageId : LookGrid.NO_PAGE;
        LookGrid.Resolved hue = grid.resolve(LookGrid.Property.TINT_HUE, element, page);
        LookGrid.Resolved rim = grid.resolve(LookGrid.Property.RIM, element, page);
        LookGrid.Resolved depth = grid.resolve(LookGrid.Property.DEPTH, element, page);
        LookGrid.Resolved edge = grid.resolve(LookGrid.Property.EDGE, element, page);
        return new ElementGlass(element,
            clamp(FrostStops.blurDpFor(grid.value(LookGrid.Property.FROST, element, page)),
                0, MAX_BLUR_DP),
            clamp(grid.value(LookGrid.Property.OPACITY, element, page), 0, 100),
            clamp(grid.value(LookGrid.Property.GRAIN, element, page), 0, 100),
            clamp(grid.value(LookGrid.Property.TINT, element, page), 0, 100),
            grid.value(LookGrid.Property.CORNERS, element, page),
            ownCell(hue) ? hueOf(hue.value) : null,
            ownCell(rim) ? rim.value : null,
            ownCell(depth) ? depth.value : null,
            ownCell(edge) ? edge.value : null);
    }

    /** {@link #resolve} over the live store; the shipped look when there is none. */
    @NonNull
    public static ElementGlass of(@Nullable TermuxAppSharedPreferences preferences,
                                  @NonNull LookGrid.Element element, int pageId) {
        LookGrid grid = preferences != null ? preferences.lookGrid()
            : new LookGrid(new LookGrid.MemoryStore());
        return resolve(grid, element, pageId);
    }

    /**
     * Whether a value is this element's or this page's own rather than the wall-wide one the
     * renderer's global look already carries.
     */
    static boolean ownCell(@NonNull LookGrid.Resolved resolved) {
        return resolved.cell != LookGrid.Cell.BASE && resolved.cell != LookGrid.Cell.SHIPPED;
    }

    @NonNull
    static GlassLook.Hue hueOf(int stored) {
        switch (stored) {
            case LookGrid.TINT_HUE_SECONDARY: return GlassLook.Hue.SECONDARY;
            case LookGrid.TINT_HUE_TERTIARY: return GlassLook.Hue.TERTIARY;
            default: return GlassLook.Hue.PRIMARY;
        }
    }

    /** The tint's opacity, 0..1: what a {@link GlassStack.Spec} calls tintAlpha. */
    public float tintAlpha() {
        return opacityPercent / 100f;
    }

    /**
     * The refraction this element draws with: {@code global} (the Fancier Glass look, null while
     * it is off) with the element's own Depth and Edge in place of the wall's. Off stays off.
     */
    @Nullable
    public GlassRefraction.Look refraction(@Nullable GlassRefraction.Look global) {
        return withOwn(global, ownDepthDp, ownEdgePercent);
    }

    /**
     * {@link #refraction} for {@code element} on {@code pageId} straight from the store, reading
     * only Depth and Edge, and nothing at all while Fancier Glass is off: the path a surface that
     * re-asks for its refraction on every dress pass takes.
     */
    @Nullable
    public static GlassRefraction.Look refraction(@Nullable TermuxAppSharedPreferences preferences,
                                                  @NonNull LookGrid.Element element, int pageId,
                                                  @Nullable GlassRefraction.Look global) {
        if (global == null || preferences == null) return global;
        LookGrid grid = preferences.lookGrid();
        int page = pageId >= 0 ? pageId : LookGrid.NO_PAGE;
        LookGrid.Resolved depth = grid.resolve(LookGrid.Property.DEPTH, element, page);
        LookGrid.Resolved edge = grid.resolve(LookGrid.Property.EDGE, element, page);
        return withOwn(global, ownCell(depth) ? depth.value : null,
            ownCell(edge) ? edge.value : null);
    }

    @Nullable
    private static GlassRefraction.Look withOwn(@Nullable GlassRefraction.Look global,
                                                @Nullable Integer depthDp,
                                                @Nullable Integer edgePercent) {
        if (global == null || (depthDp == null && edgePercent == null)) return global;
        return new GlassRefraction.Look(depthDp != null ? depthDp : global.bendDp,
            global.edgeWidthDp, global.edgeLightPercent, global.specularPercent,
            edgePercent != null ? edgePercent : global.dispersionPercent,
            global.clarityPercent);
    }

    /** {@code base} (the wall's tint colour and rim style) as this element wears it. */
    @NonNull
    public GlassLook look(@NonNull GlassLook base) {
        GlassLook look = ownHue != null ? base.withHue(ownHue) : base;
        if (ownRim != null) look = look.withGradientRim(ownRim == LookGrid.RIM_GRADIENT);
        return look;
    }

    /** Whether the element turned its rim off in a cell of its own; Base never does. */
    public boolean rimOff() {
        return ownRim != null && ownRim == LookGrid.RIM_OFF;
    }

    /**
     * This element's glass stack spec at {@code cornerRadiusPx}: blur, opacity, grain, tint and
     * refraction from its cells. The caller adds what is structural to its surface (seams, slice,
     * band, a cap on the opacity, its rim, which it gates on {@link #rimOff}), named where it
     * adds it.
     */
    @NonNull
    public GlassStack.Spec spec(float cornerRadiusPx, @Nullable GlassRefraction.Look global) {
        return GlassStack.Spec.of(blurDp, tintAlpha(), grainPercent,
            cornerRadiusPx, refraction(global)).withTintStrength(tintPercent);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
