package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.GlassRefraction;

/**
 * What a dock style asks of the dock's slab: where its page dashes stand, and which of the glass's
 * dock-surface features it wears. One value, so a style is a choice of spec and the geometry
 * ({@link DockLayoutPolicy}), the glass program ({@link GlassRefraction.Program#setDockSurface})
 * and the shelf under the slab each read the part
 * that is theirs.
 *
 * <p>{@link #PLAIN} is the dock exactly as it was, which a layout pass that names no surface
 * keeps; every page's dock, Home's and the Workspace's alike, wears its Dock design's spec (#93).
 * {@link #GLASS_TRAY} is the default design
 * (project-docs/active/home-dock-styles-2026-10-20.md): one slab, equal air above and below the
 * icons, the dashes floating just above the slab, and the shared glass work every later style
 * builds on. {@link #LENS_BEADS} keeps the tray's geometry but draws no slab: each icon sits on
 * a round bead of glass ({@link LensBeadGeometry}). {@link #WALL_STRIP} is the tray made
 * the expanded dock: a first row for the Workspace over the apps row, both on one tall slab.
 * {@link #PEEK_GLASS} keeps the tray's row but cuts the slab to a card whose top edge runs
 * through the icons' centres and whose bottom runs on past the screen's edge
 * ({@link PeekGlassGeometry}), the icons' reflections fading down it. A later style is one more
 * constant here, and {@link #forStyle} picks it.</p>
 *
 * <p>The Search field is no spec: it is a toggle the layout holds beside the design (#96), and
 * the slab carries it under the apps row with any design, as much of it as {@link
 * DockLayoutPolicy.DockInputs#promptFieldReveal} lays out. Nor is the Drawer tile (#97, once
 * the Taskbar design): the layout's toggle puts it in one of the apps row's slots with any design
 * ({@link DrawerTilePolicy}), the row's own business, so the slab draws it as one more icon.</p>
 */
public final class DockSurfaceSpec {

    /**
     * The dock as it always was: dashes in the row's own air, the narrow rim, no shelf. What a
     * layout pass that names no surface gets; no Dock design draws it.
     */
    @NonNull public static final DockSurfaceSpec PLAIN =
        new DockSurfaceSpec("plain", false, 0f, 0f, false, false, false, false);

    /**
     * Home's Glass tray. Its glass numbers are the program's own constants, so the spec says only
     * that they are on.
     */
    @NonNull public static final DockSurfaceSpec GLASS_TRAY =
        new DockSurfaceSpec("glass_tray", true, GlassRefraction.SOFT_RIM_DP, 1f, true, false,
            false, false);

    /**
     * Home's Lens beads: the tray's row geometry (dashes above, equal air) with no slab; each icon
     * on its own round glass bead with a tight, stronger rim band
     * ({@link GlassRefraction#BEAD_SOFT_RIM_DP}), the shelf in miniature (its lip and a small
     * contact shadow under each bead, whatever the dock's Style, since a bead always floats).
     */
    @NonNull public static final DockSurfaceSpec LENS_BEADS =
        new DockSurfaceSpec("lens_beads", true, GlassRefraction.BEAD_SOFT_RIM_DP, 1f, true, true,
            false, false);

    /**
     * Home's Wall strip, the expanded dock: the tray's glass (dashes above, equal air, soft rim,
     * shelf, shadow) on one tall slab, a first row for the Workspace ({@link WallStripRowView}: a
     * clock and the terminals as tiles, {@link DockLayout#wallStripBandPx}) over the apps row.
     */
    @NonNull public static final DockSurfaceSpec WALL_STRIP =
        new DockSurfaceSpec("wall_strip", true, GlassRefraction.SOFT_RIM_DP, 1f, true, false, true,
            false);

    /**
     * Home's Peek glass: the tray's row geometry (dashes above, equal air), the slab cut to a card
     * that rises from the screen's bottom edge with its top edge through the icons' centres
     * ({@link PeekGlassGeometry}), the tray's soft rim on its top and sides. The card's lower
     * edge is never seen (it runs on under the gesture pill), so it wears no shelf and casts no
     * shadow. The card is the same under Floating: while it stands, the dock is placed flush as
     * it is Docked ({@link DockPlacement}). Where the card cannot be drawn (another band or the
     * keyboard on the dock's glass, the Search field under the row) the dock draws the tray's
     * slab ({@link #forCard}), placed as the Style places it.
     */
    @NonNull public static final DockSurfaceSpec PEEK_GLASS =
        new DockSurfaceSpec("peek_glass", true, GlassRefraction.SOFT_RIM_DP, 0f, false, false,
            false, true);

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
    /**
     * The slab is cut to the Peek glass card: its top edge through the icons' centres, inset from
     * the screen's sides, running on past the bottom edge ({@link PeekGlassGeometry}).
     */
    public final boolean peek;

    private DockSurfaceSpec(@NonNull String name, boolean dashesAboveSlab, float softRimDp,
                            float shelf, boolean contactShadow, boolean beads,
                            boolean wallStrip, boolean peek) {
        this.name = name;
        this.dashesAboveSlab = dashesAboveSlab;
        this.softRimDp = softRimDp;
        this.shelf = shelf;
        this.contactShadow = contactShadow;
        this.beads = beads;
        this.wallStrip = wallStrip;
        this.peek = peek;
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
            case PEEK_GLASS:
                return PEEK_GLASS;
            case GLASS_TRAY:
            default:
                return GLASS_TRAY;
        }
    }

    /**
     * The spec the dock's slab draws with under this design, for the Peek card standing now
     * ({@code card}, null while none does): a Peek design with no card is the tray's slab, shelf
     * and shadow included. Every other design, and Peek with its card standing, is itself.
     */
    @NonNull
    public DockSurfaceSpec forCard(@Nullable PeekGlassGeometry.Card card) {
        if (!peek) return this;
        return card == null ? GLASS_TRAY : this;
    }

    /**
     * The soft rim band the strip under the gesture pill wears, dp: the card's own under Peek glass
     * while the card stands ({@code cardStanding}), because there the strip is the card's foot, inset
     * as the card is, and its sides are the card's sides run on past the seam. Worn with the narrow
     * ring instead, those sides bent the picture differently from the card right above them, and
     * where the wallpaper beside the card is bright, the frost it bleeds in showed through the strip
     * as a lighter patch starting at the seam. 0 everywhere else, where the strip spans the screen
     * and its sides are seams.
     */
    public float underPillSoftRimDp(boolean cardStanding) {
        return peek && cardStanding ? softRimDp : 0f;
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
