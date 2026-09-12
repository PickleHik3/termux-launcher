package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceArrangeModel.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which arrangement controls lead which card. The rule is where the bar stands: the dock band's
 * card carries the bars standing on it, and the canvas — the one surface every place always has —
 * carries the ones that have left it, so a hidden bar is never a one-way door.
 */
public class SurfaceEditorPlaceSectionTest {

    private static PlaceLayout layout(RowPlacement appsRow, boolean azShown, Edge azEdge,
                                      RowPlacement extraKeys) {
        return new PlaceLayout(Edge.TOP, appsRow, azShown, azEdge, extraKeys, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
    }

    private static List<Element> elements(SurfaceSlot slot, PaneWallPage place, PlaceLayout layout) {
        return SurfaceEditorPlaceSection.elementsFor(slot, place, layout);
    }

    @Test
    public void theStatusBarsCardCarriesItsOwnEdgeAndNothingElse() {
        assertEquals(Arrays.asList(Element.STATUS_BAR), elements(SurfaceSlot.STATUS,
            PaneWallPage.TERMINAL, layout(RowPlacement.BOTTOM, true, Edge.BOTTOM,
                RowPlacement.BOTTOM)));
    }

    @Test
    public void theKeyboardsCardCarriesTheKeyboard() {
        assertEquals(Arrays.asList(Element.KEYBOARD), elements(SurfaceSlot.KEYBOARD,
            PaneWallPage.DISPLAY, layout(RowPlacement.BOTTOM, true, Edge.BOTTOM,
                RowPlacement.BOTTOM)));
    }

    @Test
    public void theDockCardCarriesItsThreeBars() {
        assertEquals(Arrays.asList(Element.PINNED_APPS, Element.AZ_INDEX, Element.EXTRA_KEYS),
            elements(SurfaceSlot.DOCK, PaneWallPage.TERMINAL,
                layout(RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.BOTTOM)));
    }

    /**
     * A bar keeps its card whatever it is set to: hidden, railed or standing on an edge of its own,
     * its control is still where the user last saw it. Only the terminal's own grid is elsewhere.
     */
    @Test
    public void changingABarNeverMovesItToAnotherCard() {
        PlaceLayout[] arrangements = {
            layout(RowPlacement.LEFT, true, Edge.BOTTOM, RowPlacement.BOTTOM),   // apps railed
            layout(RowPlacement.HIDDEN, true, Edge.BOTTOM, RowPlacement.BOTTOM), // apps hidden
            layout(RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.HIDDEN), // keys hidden
            layout(RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.RIGHT),  // keys in a column
            layout(RowPlacement.BOTTOM, true, Edge.TOP, RowPlacement.BOTTOM),    // index on top
            layout(RowPlacement.BOTTOM, false, Edge.BOTTOM, RowPlacement.BOTTOM), // index off
            layout(RowPlacement.HIDDEN, false, Edge.BOTTOM, RowPlacement.BOTTOM), // keys alone
        };
        for (PlaceLayout arrangement : arrangements) {
            assertEquals(arrangement.toString(),
                Arrays.asList(Element.PINNED_APPS, Element.AZ_INDEX, Element.EXTRA_KEYS),
                elements(SurfaceSlot.DOCK, PaneWallPage.TERMINAL, arrangement));
            assertTrue(arrangement + " leaked a dock bar onto the terminal card",
                elements(SurfaceSlot.CANVAS, PaneWallPage.TERMINAL, arrangement).isEmpty());
        }
    }

    @Test
    public void anEmptyBandHasNoCardSoItsBarsAreReachedFromTheCanvas() {
        PlaceLayout bare = layout(RowPlacement.HIDDEN, false, Edge.BOTTOM, RowPlacement.HIDDEN);
        PlaceLayout railedOnly = layout(RowPlacement.LEFT, true, Edge.TOP, RowPlacement.RIGHT);

        for (PlaceLayout arrangement : new PlaceLayout[] {bare, railedOnly}) {
            assertTrue("with nothing on the band there is no dock card at all",
                elements(SurfaceSlot.DOCK, PaneWallPage.TERMINAL, arrangement).isEmpty());
            assertEquals(Arrays.asList(Element.PINNED_APPS, Element.AZ_INDEX, Element.EXTRA_KEYS),
                elements(SurfaceSlot.CANVAS, PaneWallPage.TERMINAL, arrangement));
        }
    }

    @Test
    public void theHomeCanvasLeadsWithItsWidgetGrid() {
        PlaceLayout portrait = layout(RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.BOTTOM);

        assertEquals(Arrays.asList(Element.WIDGET_GRID),
            elements(SurfaceSlot.CANVAS, PaneWallPage.WIDGETS, portrait));
        assertTrue("every other place's canvas has no grid",
            elements(SurfaceSlot.CANVAS, PaneWallPage.TERMINAL, portrait).isEmpty());
    }

    @Test
    public void theSharedLayerHasNoPlaceSection() {
        assertTrue(elements(null, PaneWallPage.TERMINAL,
            layout(RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.BOTTOM)).isEmpty());
    }

    /** Every bar is reachable from exactly one card, whatever the arrangement. */
    @Test
    public void everyBarIsOnOneCardAndOnlyOne() {
        for (RowPlacement appsRow : RowPlacement.values()) {
            for (RowPlacement extraKeys : RowPlacement.values()) {
                for (boolean azShown : new boolean[] {true, false}) {
                    for (Edge azEdge : Edge.values()) {
                        PlaceLayout layout = layout(appsRow, azShown, azEdge, extraKeys);
                        Set<Element> seen = new HashSet<>();
                        for (SurfaceSlot slot : SurfaceSlot.values()) {
                            for (Element element
                                    : elements(slot, PaneWallPage.WIDGETS, layout)) {
                                assertTrue(layout + " offers " + element + " twice",
                                    seen.add(element));
                            }
                        }
                        assertEquals(layout + " misses a bar", 6, seen.size());
                    }
                }
            }
        }
    }
}
