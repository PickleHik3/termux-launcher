package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * When a keyboard floats over the place it is opened on, and what that leaves the content — the two
 * questions the accessory stack asks before it decides where the content root ends.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class KeyboardOverlayPolicyTest {

    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("keyboard-overlay-policy-test",
            Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    private boolean overlays(PlaceLayoutStore store, PaneWallPage place,
                             PlaceOrientation orientation) {
        return KeyboardOverlayPolicy.overlays(place, store.resolve(place, orientation));
    }

    private static PlaceLayout layout(KeyboardMode mode) {
        return new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, RowPlacement.BOTTOM, mode, 4, 5);
    }

    // ------------------------------------------------------------------ when it applies

    @Test
    public void outOfTheBoxOnlyTheDisplayInLandscapeFloatsTheKeyboard() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                boolean expected = place == PaneWallPage.DISPLAY
                    && orientation == PlaceOrientation.LANDSCAPE;
                assertEquals(place + " " + orientation, expected,
                    overlays(store, place, orientation));
            }
        }
    }

    @Test
    public void theDisplayTakesTheStoredModeInEitherOrientation() {
        PlaceLayoutStore store = store();
        store.setKeyboardMode(PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT,
            KeyboardMode.OVERLAY);
        store.setKeyboardMode(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE,
            KeyboardMode.RESIZE);
        assertTrue(overlays(store, PaneWallPage.DISPLAY, PlaceOrientation.PORTRAIT));
        assertFalse(overlays(store, PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void noOtherPlaceFloatsTheKeyboardEvenWithAnOverlayStored() {
        PlaceLayoutStore store = store();
        for (PaneWallPage place : PaneWallPage.values()) {
            if (place == PaneWallPage.DISPLAY) continue;
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                store.setKeyboardMode(place, orientation, KeyboardMode.OVERLAY);
                assertFalse(place + " " + orientation, overlays(store, place, orientation));
            }
        }
    }

    @Test
    public void theDisplayResizesWhenItIsNotThePlaceOnScreen() {
        assertFalse(KeyboardOverlayPolicy.overlays(PaneWallPage.TERMINAL,
            layout(KeyboardMode.OVERLAY)));
        assertTrue(KeyboardOverlayPolicy.overlays(PaneWallPage.DISPLAY,
            layout(KeyboardMode.OVERLAY)));
        assertFalse(KeyboardOverlayPolicy.overlays(PaneWallPage.DISPLAY,
            layout(KeyboardMode.RESIZE)));
    }

    // ------------------------------------------------------------------ what it leaves the content

    @Test
    public void aClosedKeyboardOverlapsNothingInEitherMode() {
        assertEquals(0, KeyboardOverlayPolicy.contentOverlapPx(true, false, 700));
        assertEquals(0, KeyboardOverlayPolicy.contentOverlapPx(false, false, 700));
    }

    @Test
    public void anOpenKeyboardOverlapsItsOwnHeightOnlyWhenItFloats() {
        assertEquals(700, KeyboardOverlayPolicy.contentOverlapPx(true, true, 700));
        assertEquals(0, KeyboardOverlayPolicy.contentOverlapPx(false, true, 700));
        assertEquals(0, KeyboardOverlayPolicy.contentOverlapPx(true, true, -5));
    }

    /** The point of the whole phase: the display does not move as the keyboard comes and goes. */
    @Test
    public void aFloatingKeyboardOfAnyHeightLeavesTheContentWhereItWas() {
        int available = 2000;
        int dockPx = 180;
        int marginPx = 24;
        int closed = contentHeight(true, false, 0, available, dockPx, marginPx);
        for (int keyboardPx : new int[] {1, 250, 700, 1200}) {
            assertEquals("keyboard " + keyboardPx, closed,
                contentHeight(true, true, keyboardPx, available, dockPx, marginPx));
        }
    }

    @Test
    public void aResizingKeyboardTakesItsHeightFromTheContent() {
        int available = 2000;
        int dockPx = 180;
        int marginPx = 24;
        int closed = contentHeight(false, false, 0, available, dockPx, marginPx);
        assertEquals(closed - 700, contentHeight(false, true, 700, available, dockPx, marginPx));
        assertEquals(closed - 250, contentHeight(false, true, 250, available, dockPx, marginPx));
    }

    /** The dock rows keep taking their own room from the content, floating keyboard or not. */
    @Test
    public void theDockRowsStillTakeTheirRoomWhileTheKeyboardFloats()  {
        int available = 2000;
        int marginPx = 0;
        int withRows = contentHeight(true, true, 700, available, 180, marginPx);
        int withoutRows = contentHeight(true, true, 700, available, 0, marginPx);
        assertEquals(180, withoutRows - withRows);
    }

    @Test
    public void theContentNeverGoesNegative() {
        assertEquals(0, KeyboardOverlayPolicy.contentHeightPx(100, 400));
        assertEquals(0, KeyboardOverlayPolicy.contentReservationPx(100, 0, 400));
    }

    private static int contentHeight(boolean overlays, boolean keyboardShown, int keyboardPx,
                                     int availablePx, int dockPx, int marginPx) {
        int overlapPx = KeyboardOverlayPolicy.contentOverlapPx(overlays, keyboardShown, keyboardPx);
        int stackPx = dockPx + (keyboardShown ? keyboardPx : 0);
        return KeyboardOverlayPolicy.contentHeightPx(availablePx,
            KeyboardOverlayPolicy.contentReservationPx(stackPx, marginPx, overlapPx));
    }
}
