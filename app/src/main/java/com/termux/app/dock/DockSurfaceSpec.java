package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.GlassRefraction;

/**
 * What a dock style asks of the dock's slab: where its page dashes stand, and which of the glass's
 * dock-surface features it wears. One value, so a style is a choice of spec and the geometry
 * ({@link DockLayoutPolicy}), the glass program ({@link GlassRefraction.Program#setDockSurface}) and the shelf under the slab each read the part
 * that is theirs.
 *
 * <p>{@link #PLAIN} is the dock exactly as it was, which a layout pass that names no surface
 * keeps; every page's dock, Home's and the Workspace's alike, wears its Dock design's spec (#93).
 * {@link #GLASS_TRAY} is the default design
 * (project-docs/active/home-dock-styles-2026-10-20.md): one slab, equal air above and below the
 * icons, the dashes floating just above the slab, and the shared glass work every later style
 * builds on. {@link #LENS_BEADS} keeps the tray's geometry but draws no slab: each icon sits on
 * a round bead of glass ({@link LensBeadGeometry}). {@link #WALL_STRIP} is the tray made
 * the expanded dock: a first row for the Workspace over the apps row, both on one tall slab. A
 * later style is one more constant here, and {@link #forStyle} picks it.</p>
 *
 * <p>The Search field is no spec: it is a toggle the layout holds beside the design (#96), and
 * the slab carries it under the apps row with any design, as much of it as {@link
 * DockLayoutPolicy.DockInputs#promptFieldReveal} lays out. Nor is the Drawer tile (#97, once
 * the Taskbar design): the layout's toggle puts it in one of the apps row's slots with any design
 * ({@link DrawerTilePolicy}), the row's own business, so the slab draws it as one more icon.</p>
 */
public final class DockSurfaceSpec {

    /**
     * The dock as it always was: dashes in the row's own air, the narrow rim, no shelf. What a layout pass that names no surface gets; no Dock design draws it.
     */
    @NonNull public static final DockSurfaceSpec PLAIN =
        new DockSurfaceSpec("plain", false, 0f, 0f, false, false, false);

    /**
     * Home's Glass tray. Its glass numbers are the program's own constants, so the spec says only
     * that they are on.
     */
    @NonNull public static final DockSurfaceSpec GLASS_TRAY =
        new DockSurfaceSpec("glass_tray", true, GlassRefraction.SOFT_RIM_DP, 1f, true, false, false);

    /**
     * Home's Lens beads: the tray's row geometry (dashes above, equal air) with no slab; each icon
     * on its own round glass bead with a tight, stronger rim band
     * ({@link GlassRefraction#BEAD_SOFT_RIM_DP}), the shelf in miniature (its lip and a small
     * contact shadow under each bead, whatever the dock's Style, since a bead always floats).
     */
    @NonNull public static final DockSurfaceSpec LENS_BEADS =
        new DockSurfaceSpec("lens_beads", true, GlassRefraction.BEAD_SOFT_RIM_DP, 1f, true, true, false);

    /**
     * Home's Wall strip, the expanded dock: the tray's glass (dashes above, equal air, soft rim,
     * shelf, shadow) on one tall slab, a first row for the Workspace ({@link WallStripRowView}: a
     * clock and the terminals as tiles, {@link DockLayout#wallStripBandPx}) over the apps row.
     */
    @NonNull public static final DockSurfaceSpec WALL_STRIP =
        new DockSurfaceSpec("wall_strip", true, GlassRefraction.SOFT_RIM_DP, 1f, true, false, true);

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
    /** A soft contact shadow and a faint glow of light on the wallpaper under a floating slab. */
    public final boolean contactShadow;
    /**
     * No slab: the glass is drawn as one round bead under each icon, and the shelf and the shadow
     * are each bead's own. Where the row cannot be drawn as beads (more icons than the program's
     * beads, another band on the dock's glass) the dock falls back to the slab.
     */
    public final boolean beads;
    /**
     * The slab carries the Wall strip's first row over the apps row, on the same glass; how much
     * of it is laid out is {@link DockLayoutPolicy.DockInputs#wallStripReveal}.
     */
    public final boolean wallStrip;

    private DockSurfaceSpec(@NonNull String name, boolean dashesAboveSlab, float softRimDp,
                            float shelf, boolean contactShadow, boolean beads,
                            boolean wallStrip) {
        this.name = name;
        this.dashesAboveSlab = dashesAboveSlab;
        this.softRimDp = softRimDp;
        this.shelf = shelf;
        this.contactShadow = contactShadow;
        this.beads = beads;
        this.wallStrip = wallStrip;
    }

    /**
     * The spec a Dock design draws with, on whichever page's dock wears it (the page's dress
     * names the design); a design not built yet is the Glass tray. No design is {@link #PLAIN}.
     */
    @NonNull
    public static DockSurfaceSpec forStyle(@Nullable DockStyle style) {
        switch (DockStyle.effectiveStyle(style)) {
            case LENS_BEADS:
                return LENS_BEADS;
            case WALL_STRIP:
                return WALL_STRIP;
            case GLASS_TRAY:
            default:
                return GLASS_TRAY;
        }
    }

    /** Whether the glass program has anything of this spec's to draw. */
    public boolean wearsGlassFeatures() {
        return softRimDp > 0f || shelf > 0f;
    }

    @NonNull
    @Override
    public String toString() {
        return "DockSurfaceSpec{" + name + "}";
    }
}
