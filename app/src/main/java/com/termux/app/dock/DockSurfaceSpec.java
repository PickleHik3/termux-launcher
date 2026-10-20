package com.termux.app.dock;

import androidx.annotation.NonNull;

import com.termux.app.chrome.GlassRefraction;

/**
 * What a dock style asks of the dock's slab: where its page dashes stand, and which of the glass's
 * dock-surface features it wears. One value, so a style is a choice of spec and the geometry
 * ({@link DockLayoutPolicy}), the glass program ({@link GlassRefraction.Program#setDockSurface},
 * {@link GlassRefraction.Program#setReflection}) and the shelf under the slab each read the part
 * that is theirs.
 *
 * <p>Two exist today. {@link #PLAIN} is the dock exactly as it was, which the Workspace's dock and
 * every surface that is not Home's dock keep. {@link #GLASS_TRAY} is Home's default style
 * (project-docs/active/home-dock-styles-2026-10-20.md): one slab, equal air above and below the
 * icons, the dashes floating just above the slab, and the shared glass work every later style
 * builds on. A later style is one more constant here, and the place that picks it.</p>
 */
public final class DockSurfaceSpec {

    /** The dock as it always was: dashes in the row's own air, the narrow rim, no shelf, no reflection. */
    @NonNull public static final DockSurfaceSpec PLAIN =
        new DockSurfaceSpec("plain", false, 0f, 0f, false, false);

    /**
     * Home's Glass tray. Its glass numbers are the program's own constants, so the spec says only
     * that they are on.
     */
    @NonNull public static final DockSurfaceSpec GLASS_TRAY =
        new DockSurfaceSpec("glass_tray", true, GlassRefraction.SOFT_RIM_DP, 1f, true, true);

    /** A name for logs and tests. */
    @NonNull public final String name;
    /**
     * The page dashes stand above the slab with a small gap rather than in its top air, and the
     * air above the icons equals the air below them. Only where the dashes would stand over the
     * icons anyway: the apps row lying on the dock as the band next to the canvas.
     */
    public final boolean dashesAboveSlab;
    /** The soft rim band's width, dp; 0 keeps the narrow ring. */
    public final float softRimDp;
    /** How much of the lower shelf the slab wears, 0..1: the thicker lower edge and its lip. */
    public final float shelf;
    /** The icons are reflected in the glass. */
    public final boolean iconReflections;
    /** A soft contact shadow and a faint glow of light on the wallpaper under a floating slab. */
    public final boolean contactShadow;

    private DockSurfaceSpec(@NonNull String name, boolean dashesAboveSlab, float softRimDp,
                            float shelf, boolean iconReflections, boolean contactShadow) {
        this.name = name;
        this.dashesAboveSlab = dashesAboveSlab;
        this.softRimDp = softRimDp;
        this.shelf = shelf;
        this.iconReflections = iconReflections;
        this.contactShadow = contactShadow;
    }

    /**
     * The spec the dock wears on a page: Home's dock is the Glass tray, every other page's the
     * plain dock. Where a Dock style preference exists, this is where it is read.
     */
    @NonNull
    public static DockSurfaceSpec forDock(boolean homeDock) {
        return homeDock ? GLASS_TRAY : PLAIN;
    }

    /** Whether the glass program has anything of this spec's to draw. */
    public boolean wearsGlassFeatures() {
        return softRimDp > 0f || shelf > 0f || iconReflections;
    }

    @NonNull
    @Override
    public String toString() {
        return "DockSurfaceSpec{" + name + "}";
    }
}
