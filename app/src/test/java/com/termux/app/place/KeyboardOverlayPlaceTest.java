package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * The keyboard mode the front window decides: the display's stored Overlay or Resize applies only
 * while a display window is the one in front, whatever place was last recorded.
 */
public class KeyboardOverlayPlaceTest {

    private static PlaceLayout layout(KeyboardMode mode) {
        return new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM, mode, KeyboardForm.DOCKED, 4, 5);
    }

    @Test
    public void aDisplayInFrontReadsTheStoredChoice() {
        assertEquals(true, KeyboardOverlayPolicy.overlays(PaneWallPage.DISPLAY, true,
            layout(KeyboardMode.OVERLAY)));
        assertEquals(false, KeyboardOverlayPolicy.overlays(PaneWallPage.DISPLAY, true,
            layout(KeyboardMode.RESIZE)));
    }

    @Test
    public void aTerminalWindowInFrontResizesWhateverTheDisplayChoiceIs() {
        for (KeyboardMode mode : KeyboardMode.values()) {
            assertEquals(false, KeyboardOverlayPolicy.overlays(PaneWallPage.TERMINAL, false,
                layout(mode)));
        }
    }

    @Test
    public void aDisplayPlaceLeftUnrecordedFallsBackToTheTerminal() {
        assertEquals(PaneWallPage.TERMINAL,
            KeyboardOverlayPolicy.keyboardPlace(PaneWallPage.DISPLAY, false));
        assertEquals(false, KeyboardOverlayPolicy.overlays(PaneWallPage.DISPLAY, false,
            layout(KeyboardMode.OVERLAY)));
    }

    @Test
    public void otherPlacesAreNotDerivedFromTheDisplay() {
        assertEquals(PaneWallPage.TERMINAL,
            KeyboardOverlayPolicy.keyboardPlace(PaneWallPage.TERMINAL, true));
        assertEquals(true, KeyboardOverlayPolicy.overlays(PaneWallPage.WIDGETS, false,
            layout(KeyboardMode.RESIZE)));
    }
}
