package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;

/**
 * The Dock design a page's slab actually wears: the one its layout holds while the layout shows
 * the pinned apps, and {@link #NO_APPS_ROW} where it shows none. Every design is a way of
 * drawing the apps row (Lens beads under its icons, Peek glass cut through them, the Wall strip
 * over them), so a page whose dock is turned off has no design to wear, and its slab, when it
 * still carries the extra keys or the A–Z row, is the plain tray each design already falls back
 * to there. The stored pick is left alone: turning the row back on brings it back.
 *
 * <p>Pure. Every runtime reader of a page's design asks here: the page's slab spec and its rows'
 * geometry, the crossing's two designs, and the settle's compare between pages, so a crossing
 * between a dock-off page and another never sees a design to swap that neither page draws.</p>
 */
public final class WornDockDesign {

    private WornDockDesign() {}

    /**
     * The design a page with no apps row wears: the Glass tray, the slab Lens beads, Peek glass
     * and the Wall strip each draw with no row to dress (soft rim, shelf and contact shadow,
     * no beads, no card, no strip).
     */
    @NonNull public static final DockStyle NO_APPS_ROW = DockStyle.GLASS_TRAY;

    /** The design {@code stored} for a page standing {@code layout}'s rows. */
    @NonNull
    public static DockStyle of(@Nullable DockStyle stored, @NonNull PlaceLayout layout) {
        if (!PlaceChromePolicy.appsShown(layout)) return NO_APPS_ROW;
        return stored != null ? stored : DockStyle.GLASS_TRAY;
    }
}
