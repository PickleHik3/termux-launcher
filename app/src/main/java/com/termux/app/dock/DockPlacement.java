package com.termux.app.dock;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout;

/**
 * Where the dock's glass is placed: as the Floating card (in from the screen's sides, a gap under
 * it, its own rounded outline, nothing under the gesture pill) or flush with the screen's bottom
 * edge as it is Docked (no gap, the strip under the pill continuing it). The half's Style decides
 * it, except that the Peek glass card ({@link DockSurfaceSpec#PEEK_GLASS}) always rises from the
 * screen's bottom edge: while it stands, the dock is placed as it is Docked whatever the Style.
 * Every other surface (the panes, the status bar, the keyboard, the extra keys) keeps the Style,
 * and so do the dock's rows, which are measured in it ({@link DockLayout#capsule}), so the icons
 * keep their size when the card gives way. Where the card cannot stand, the dock is the Style's
 * own tray again.
 *
 * <p>Pure, and read by everything that places the dock: the margin under the stack
 * ({@code StackLayout}) and the dock's own dressing in the activity. Whether the card stands is
 * worked out from facts the placement never moves (the design, the layout, the keyboard, the IME,
 * the Search field), so the answer never depends on itself.</p>
 */
public final class DockPlacement {

    private DockPlacement() {}

    /**
     * Whether the Peek glass card can stand: the dock wears Peek glass ({@code peekWorn}) and is
     * shown, with the apps row alone on its glass along the bottom
     * ({@link LensBeadGeometry#rowWearsBeads}: no other band, no joined Docked frame it stands
     * down on), no keyboard up under it, not lifted over the system keyboard, and no Search field
     * under the row. {@code sheetStandsDownOnFrame} refuses the card on its own, whatever else
     * holds, so a caller for whom it is dear to ask may ask it last.
     */
    public static boolean peekCardStands(boolean peekWorn, boolean dockShown,
                                         @NonNull PlaceLayout layout,
                                         boolean sheetStandsDownOnFrame, boolean keyboardShown,
                                         boolean imeLifted, boolean searchFieldShown) {
        if (!peekWorn || !dockShown) return false;
        if (keyboardShown || imeLifted || searchFieldShown) return false;
        return LensBeadGeometry.rowWearsBeads(layout, sheetStandsDownOnFrame, false);
    }

    /**
     * Whether the dock is placed as the Floating card: the Floating Style ({@code floatingStyle}),
     * unless the Peek card stands ({@code peekCardStands}), which rises from the screen's edge.
     * Docked, the dock is always flush.
     */
    public static boolean floats(boolean floatingStyle, boolean peekCardStands) {
        return floatingStyle && !peekCardStands;
    }
}
