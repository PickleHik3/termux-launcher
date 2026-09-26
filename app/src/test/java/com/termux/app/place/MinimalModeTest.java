package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * Minimal mode (CONTEXT.md) as a state taken off the shared arrangement: what it puts away, what
 * it leaves, what the slide away from it lays out again, and what it does to the keyboard a place
 * remembers.
 */
public class MinimalModeTest {

    /** The portrait shape that shipped: everything along the bottom, the bar along the top. */
    private static PlaceLayout portrait() {
        return new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    /** A landscape shape: the apps in a rail on the left, the extra keys along the bottom. */
    private static PlaceLayout landscape() {
        return new PlaceLayout(Edge.TOP, RowPlacement.LEFT, true, Edge.TOP,
            RowPlacement.BOTTOM, KeyboardMode.OVERLAY, KeyboardForm.DOCKED, 6, 3);
    }

    @Test
    public void onlyTheTerminalAndTheDisplayCanBeMinimal() {
        assertTrue(MinimalMode.available(PaneWallPage.TERMINAL));
        assertTrue(MinimalMode.available(PaneWallPage.DISPLAY));
        assertFalse("Home has no pane to give the screen to",
            MinimalMode.available(PaneWallPage.WIDGETS));
    }

    @Test
    public void minimalPutsEverythingButTheStatusBarAwayWhereItStood() {
        for (PlaceLayout layout : new PlaceLayout[] {portrait(), landscape()}) {
            PlaceLayout minimal = MinimalMode.apply(layout);
            assertFalse(minimal.slot(Element.STATUS).hidden);
            assertEquals(layout.slot(Element.STATUS), minimal.slot(Element.STATUS));
            for (Element element : new Element[] {Element.APPS, Element.AZ, Element.EXTRA_KEYS}) {
                assertTrue(element.name(), minimal.slot(element).hidden);
                assertFalse(element.name(), EdgeStackPolicy.isShown(minimal, element));
                // Put away, not moved: turning the mode off finds each on the edge it left.
                assertEquals(element.name(), layout.slot(element).edge, minimal.slot(element).edge);
                assertEquals(element.name(), layout.slot(element).order,
                    minimal.slot(element).order);
            }
            assertFalse(PlaceChromePolicy.dockShown(minimal));
            // The keyboard's own type and the grid are not the mode's business.
            assertEquals(layout.keyboardForm, minimal.keyboardForm);
            assertEquals(layout.keyboardMode, minimal.keyboardMode);
            assertEquals(layout.widgetColumns, minimal.widgetColumns);
        }
    }

    @Test
    public void theSlideAwayLaysOnlyTheBottomRowsOutAgain() {
        PlaceLayout portrait = MinimalMode.bottomOnly(portrait());
        // Everything in portrait stands in the accessory stack, so the whole dock rises.
        assertEquals(portrait(), portrait);

        PlaceLayout landscape = MinimalMode.bottomOnly(landscape());
        // The rail on the side and the index along the top would take their room from the pane
        // as they were laid out, so they wait for settle; the bottom keys come back for the slide.
        assertTrue(landscape.slot(Element.APPS).hidden);
        assertTrue(landscape.slot(Element.AZ).hidden);
        assertFalse(landscape.slot(Element.EXTRA_KEYS).hidden);
        assertFalse(landscape.slot(Element.STATUS).hidden);
    }

    @Test
    public void aMinimalPlaceComesBackWithItsKeyboardDownWithoutForgettingIt() {
        assertTrue(MinimalMode.keyboardOnEnter(true, false));
        assertFalse(MinimalMode.keyboardOnEnter(true, true));
        assertFalse(MinimalMode.keyboardOnEnter(false, false));
        assertFalse(MinimalMode.keyboardOnEnter(false, true));
    }

    @Test
    public void theStripIsThinButNeverNothing() {
        assertEquals(12, MinimalMode.stripThicknessPx(1f));
        assertEquals(36, MinimalMode.stripThicknessPx(3f));
        assertEquals(1, MinimalMode.stripThicknessPx(0f));
    }
}
