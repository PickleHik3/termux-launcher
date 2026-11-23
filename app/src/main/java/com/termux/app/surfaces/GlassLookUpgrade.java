package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.GlassMaterial;
import com.termux.shared.termux.settings.preferences.LookGrid;

/**
 * The upgrade to Frost, Depth and Shine, as a pure function over the look grid: each stored value
 * becomes the nearest value of the new model, once, and nothing is reset.
 *
 * <ul>
 *   <li>Frost is today's Blur, the same stops; Opacity, Tint, Corners and Grain are untouched.</li>
 *   <li>Depth keeps its stored thickness (dp); only the slider reads it at a new position
 *       ({@link GlassMaterial#depthForBend}). A surface's own Depth is dropped: it is app-wide.</li>
 *   <li>Shine is the Rim stop and the Edge together ({@link GlassMaterial#shineForRimAndEdge}):
 *       Rim Off is a low Shine, Hairline the default, Gradient a higher one, and an Edge past the
 *       glint raises it, never lowers it. The Rim and Edge cells go, every surface's Edge too.</li>
 *   <li>A look that is one of the old shipped Looks at its own edge (Clear, Mist, Tint, Solid)
 *       takes the new Look's thickness and Shine, so its stop stays that Look: Clear is Clear,
 *       Mist is Frosted, Tint is Tinted, Solid is Solid. Any other look is Custom with its values
 *       mapped.</li>
 * </ul>
 */
public final class GlassLookUpgrade {

    private GlassLookUpgrade() {}

    /** What an old Look, recognised, becomes: its new thickness and Shine. */
    static final class Mapped {
        public final int bendDp;
        public final int shine;
        /** The old Look's preset id, or null when the look is Custom. */
        @Nullable public final String lookId;

        Mapped(int bendDp, int shine, @Nullable String lookId) {
            this.bendDp = bendDp;
            this.shine = shine;
            this.lookId = lookId;
        }
    }

    /**
     * The new thickness and Shine of a look whose Global cells are {@code frostStop},
     * {@code opacity}, {@code tint}, {@code bendDp}, {@code rim} and {@code edge}.
     */
    @NonNull
    static Mapped map(int frostStop, int opacity, int tint, int bendDp, int rim, int edge) {
        int shine = GlassMaterial.shineForRimAndEdge(rim, edge);
        // Clear: Clear's face on the deepest old edge (27, glint 40, hairline).
        if (frostStop == FrostStops.CLEAR && opacity == 10 && tint == 0 && bendDp == 27
                && rim == LookGrid.RIM_HAIRLINE && edge == 40)
            return new Mapped(GlassMaterial.REALISTIC_BEND_DP, GlassMaterial.SHINE_DEFAULT, "minimal");
        // Mist: 60 of Obsidian ink, or 35 on light glass; a gradient rim and a shallower edge (24).
        if (frostStop == FrostStops.MIST && (opacity == 60 || opacity == 35) && tint == 0
                && bendDp == 24 && rim == LookGrid.RIM_GRADIENT && edge == 0)
            return new Mapped(GlassMaterial.REALISTIC_BEND_DP, GlassMaterial.SHINE_DEFAULT, "frost");
        // Tint: Material colour at 46, the thinnest edge (21).
        if (frostStop == FrostStops.TINT && opacity == 46 && bendDp == 21
                && rim == LookGrid.RIM_HAIRLINE && edge == 0)
            return new Mapped(21, GlassMaterial.SHINE_DEFAULT, "stock");
        // Solid: opaque, no blur, no edge.
        if (frostStop == FrostStops.SOLID && opacity == 92 && bendDp == 0
                && rim == LookGrid.RIM_HAIRLINE && edge == 0)
            return new Mapped(0, 30, "solid");
        return new Mapped(bendDp, shine, null);
    }

    /**
     * Brings {@code grid} to the new model: writes Depth and Shine where the look moves, drops the
     * Rim and Edge cells and each surface's own Depth. Reads before it deletes.
     */
    public static void run(@NonNull LookGrid grid) {
        boolean touched = grid.has(LookGrid.Property.RIM, null) || grid.has(LookGrid.Property.EDGE, null)
            || grid.has(LookGrid.Property.DEPTH, null);
        if (touched) {
            Mapped mapped = map(grid.value(LookGrid.Property.FROST, null),
                grid.value(LookGrid.Property.OPACITY, null),
                grid.value(LookGrid.Property.TINT, null),
                grid.value(LookGrid.Property.DEPTH, null),
                grid.value(LookGrid.Property.RIM, null),
                grid.value(LookGrid.Property.EDGE, null));
            if (!grid.has(LookGrid.Property.SHINE, null))
                grid.write(LookGrid.Property.SHINE, null, mapped.shine);
            if (mapped.lookId != null || grid.has(LookGrid.Property.DEPTH, null))
                grid.write(LookGrid.Property.DEPTH, null, mapped.bendDp);
        }
        grid.clear(LookGrid.Property.RIM, null);
        grid.clear(LookGrid.Property.EDGE, null);
        for (LookGrid.Element element : LookGrid.Element.values()) {
            grid.clear(LookGrid.Property.EDGE, element);
            grid.clear(LookGrid.Property.DEPTH, element);
        }
    }
}
