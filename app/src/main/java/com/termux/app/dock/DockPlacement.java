package com.termux.app.dock;

import androidx.annotation.NonNull;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;

import java.util.List;

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
     * shown, with the apps row on its glass along the bottom ({@link #peekRowStands}: no other
     * band but the A–Z row under the icons, no joined Docked frame it stands down on), no keyboard up under it, not lifted over the system keyboard, and no Search field
     * under the row. {@code sheetStandsDownOnFrame} refuses the card on its own, whatever else
     * holds, so a caller for whom it is dear to ask may ask it last.
     */
    public static boolean peekCardStands(boolean peekWorn, boolean dockShown,
                                         @NonNull PlaceLayout layout,
                                         boolean sheetStandsDownOnFrame, boolean keyboardShown,
                                         boolean imeLifted, boolean searchFieldShown) {
        return peekCardStands(peekWorn, dockShown, layout, sheetStandsDownOnFrame, keyboardShown,
            imeLifted, searchFieldShown, false);
    }

    /**
     * {@link #peekCardStands} on a Home page or not ({@code homePage}): on Home the in-app
     * keyboard no longer refuses the card, which then stands on the keyboard instead of the
     * screen's edge (ADR 0018 §4, D11: Home's dock over Home's keyboard). Everywhere else it does.
     */
    public static boolean peekCardStands(boolean peekWorn, boolean dockShown,
                                         @NonNull PlaceLayout layout,
                                         boolean sheetStandsDownOnFrame, boolean keyboardShown,
                                         boolean imeLifted, boolean searchFieldShown,
                                         boolean homePage) {
        if (!peekWorn || !dockShown) return false;
        if ((keyboardShown && !homePage) || imeLifted || searchFieldShown) return false;
        return peekRowStands(layout, sheetStandsDownOnFrame);
    }

    /**
     * {@link #peekCardStands} for the dock's rows as they stand on {@code rowsPage}: the page whose
     * rows the stack stands, which is the committed page at rest and, mid-crossing, the other
     * half's from the frame the stack stands its rows (SeamDress#stackPage). Home's keyboard does
     * not refuse the card, so it is read off this page, never off the committed one: the frame a
     * crossing stands Home's rows (out of sight, at a dip's swap or a pictured dip's first frame)
     * wears the answer the settle on Home commits, and the dock does not change look as it lands.
     * The one answer the travel and the settle both read, and the one Stack layout places by.
     */
    public static boolean peekCardStandsOn(@NonNull PaneWallPage rowsPage, boolean peekWorn,
                                           boolean dockShown, @NonNull PlaceLayout layout,
                                           boolean sheetStandsDownOnFrame, boolean keyboardShown,
                                           boolean imeLifted, boolean searchFieldShown) {
        return peekCardStands(peekWorn, dockShown, layout, sheetStandsDownOnFrame, keyboardShown,
            imeLifted, searchFieldShown, PaneWallPolicy.isHomePlace(rowsPage));
    }

    /**
     * Whether the dock's rows stand where the Peek card can be cut, before the keyboard, the IME
     * and the Search field are asked: the apps row shown and lying along the bottom (no rail), the
     * Docked frame not standing in for the dock's sheet, and no other band on the dock's glass
     * but the A–Z row standing under the icons (nearer the screen's edge), which then rides on
     * the card. The card's top is the icons' centre line, so an A–Z row over the icons would
     * stand outside it on glass of its own; any other band (the extra keys, the status bar on the
     * plank) keeps the tray's slab. Not {@link LensBeadGeometry#rowWearsBeads}, which is right
     * for Lens beads: they would take the A–Z row's glass, the card does not.
     */
    public static boolean peekRowStands(@NonNull PlaceLayout layout,
                                        boolean sheetStandsDownOnFrame) {
        if (sheetStandsDownOnFrame || !PlaceChromePolicy.appsShown(layout)
            || PlaceChromePolicy.appsRailShown(layout)
            || PlaceChromePolicy.appsEdge(layout) != PlaceLayout.Edge.BOTTOM) return false;
        List<Element> bands = AccessoryStackLayoutPolicy.plankBands(
            EdgeStackPolicy.stack(layout, PlaceLayout.Edge.BOTTOM));
        int apps = bands.indexOf(Element.APPS);
        if (apps < 0) return false;
        for (int i = 0; i < bands.size(); i++) {
            Element band = bands.get(i);
            if (band == Element.APPS) continue;
            // Outermost first: an index under the row's is a band nearer the screen's edge.
            if (band == Element.AZ && i < apps) continue;
            return false;
        }
        return true;
    }

    /**
     * Whether the card, standing ({@link #peekRowStands}), has the A–Z row on it under the
     * icons: the letters then end the reflections' fade
     * ({@link PeekGlassGeometry#compute(float[], int, float, int, int, int, float, float, float)}).
     */
    public static boolean peekCardCarriesAz(@NonNull PlaceLayout layout,
                                            boolean sheetStandsDownOnFrame) {
        if (!peekRowStands(layout, sheetStandsDownOnFrame)) return false;
        return LensBeadGeometry.azRowWearsBeads(layout);
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
