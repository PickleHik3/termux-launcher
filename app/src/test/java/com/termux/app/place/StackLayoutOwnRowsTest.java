package com.termux.app.place;

import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.dock.DockGeometry.Snapshot;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PageRef;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Terminal's rest as a crossing from Home pre-sizes it ({@link StackLayout.RestInput#ownRows})
 * is the room the landing's pass lays out ({@link StackLayout#layout}), so the terminal's grid is
 * resized once, while the page arrives, and not again at the landing. Home's own layout stands no
 * extra-keys row, so a rest read off the half on screen came out that row short: pong, 1080 px
 * wide at 420 dpi, a 670 px keyboard, one 98 px row of extra keys, 32 px text rows, resized
 * 70x65 -> 70x44 on the way and 70x44 -> 70x41 at the landing.
 */
public class StackLayoutOwnRowsTest {

    private static final float DENSITY = 2.625f;
    private static final int WIDTH = 1080;
    /** Pong's in-app keyboard. */
    private static final int KEYBOARD = 670;
    /** One row of extra keys: the pager's 37.5dp at 420 dpi. */
    private static final int EXTRA_KEYS_ROW = Math.round(37.5f * DENSITY);
    /** Pong's text row. */
    private static final int TEXT_ROW = 32;

    /** A window: its pinned apps and its extra keys along the bottom, above the keyboard. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** The same window with its pinned apps standing under the keyboard. */
    private static final PlaceLayout WINDOW_APPS_UNDER = WINDOW
        .withSlot(Element.APPS, new Slot(false, Edge.BOTTOM, 0, true));
    /** Home: its pinned apps alone along the bottom, no extra keys. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private static Half half(PlaceLayout layout, boolean floating) {
        return Half.builder(layout).floating(floating).dockHeightScale(1.95f)
            .design(DockStyle.GLASS_TRAY).build();
    }

    private static Snapshot.Builder device(PlaceLayout window, boolean floating) {
        return Snapshot.builder(half(HOME, floating), half(window, floating))
            .density(DENSITY).widthPx(WIDTH)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .pinnedSlotCount(7)
            .preferencesAvailable(true);
    }

    /** The crossing's rest of the Terminal, read while Home is committed. */
    private static int restFromHome(PlaceLayout window, boolean floating) {
        Snapshot fromHome = device(window, floating).rows(PageRef.HOME).arrangement(HOME).build();
        StackLayout.RestInput in = new StackLayout.RestInput(fromHome, PageRef.TERMINAL)
            .ownRows(EXTRA_KEYS_ROW);
        in.keyboardShown = true;
        in.keyboardHeightPx = KEYBOARD;
        return StackLayout.restReservationPx(in);
    }

    /** The landing's pass on the Terminal, the keyboard up, filled the way the activity fills it. */
    private static int settled(PlaceLayout window, boolean floating) {
        Snapshot onTerminal = device(window, floating).rows(PageRef.TERMINAL).arrangement(window)
            .build();
        StackLayout.Input in = new StackLayout.Input(onTerminal);
        in.toolbarShown = PlaceChromePolicy.dockShown(window);
        in.extraKeysRowEnabled = PlaceChromePolicy.extraKeysRowShown(window);
        in.toolbarHeightPx = in.extraKeysRowEnabled ? EXTRA_KEYS_ROW : 0;
        in.keyboardShown = true;
        in.keyboardHeightPx = KEYBOARD;
        in.flushAllowed = true;
        return StackLayout.layout(in, new DockGeometry()).contentReservationPx;
    }

    @Test
    public void theTerminalsRestFromHomeIsTheRoomItsLandingLaysOut() {
        assertEquals(settled(WINDOW, false), restFromHome(WINDOW, false));
        assertEquals("Floating", settled(WINDOW, true), restFromHome(WINDOW, true));
    }

    @Test
    public void bandsUnderTheKeyboardAreInTheRestToo() {
        assertFalse(EdgeStackPolicy.underKeyboard(WINDOW).contains(Element.APPS));
        assertTrue(EdgeStackPolicy.underKeyboard(WINDOW_APPS_UNDER).contains(Element.APPS));
        assertEquals(settled(WINDOW_APPS_UNDER, false), restFromHome(WINDOW_APPS_UNDER, false));
        assertEquals("Floating", settled(WINDOW_APPS_UNDER, true),
            restFromHome(WINDOW_APPS_UNDER, true));
    }

    @Test
    public void readOffHomeTheRestWasAnExtraKeysRowShort() {
        // What the crossing read before: Home's rows (no extra-keys row) and Home's row height,
        // 0 since Home lays the row out 0 tall.
        Snapshot fromHome = device(WINDOW_APPS_UNDER, false).rows(PageRef.HOME).arrangement(HOME)
            .build();
        StackLayout.RestInput homes = new StackLayout.RestInput(fromHome, PageRef.TERMINAL);
        homes.toolbarShown = PlaceChromePolicy.dockShown(HOME);
        homes.extraKeysRowEnabled = PlaceChromePolicy.extraKeysRowShown(HOME);
        homes.toolbarHeightPx = 0;
        homes.keyboardShown = true;
        homes.keyboardHeightPx = KEYBOARD;
        int shortBy = settled(WINDOW_APPS_UNDER, false) - StackLayout.restReservationPx(homes);
        assertEquals(EXTRA_KEYS_ROW, shortBy);
        // On pong that is three whole text rows and a part (44 rows on the way, 41 at the
        // landing: the part fell in the leftover under the grid).
        assertEquals(3, shortBy / TEXT_ROW);
    }

    @Test
    public void aLayoutWithNoExtraKeysRowTakesNone() {
        assertEquals(0, StackLayout.extraKeysRowPx(HOME, EXTRA_KEYS_ROW));
        assertEquals(EXTRA_KEYS_ROW, StackLayout.extraKeysRowPx(WINDOW, EXTRA_KEYS_ROW));
    }
}
