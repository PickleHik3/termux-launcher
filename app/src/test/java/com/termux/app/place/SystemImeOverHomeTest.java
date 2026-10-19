package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * The system keyboard over Home: the window resizing for it pads the root by the keyboard, and a
 * Home page's content reaches past that take, so the widget grid keeps its height and the
 * keyboard, with the dock riding on it, covers it the way the in-app keyboard does. Floats and
 * terminal widgets keep their room and meet the stack's top instead.
 *
 * <p>The layout is modelled the way the content root is laid out: the root's inner bottom is the
 * window's bottom less the inset it fits, the stack stands on that less its margin, and the
 * content column ends at the stack's top plus the overlap handed back to it.
 */
public class SystemImeOverHomeTest {

    private static final int WINDOW_BOTTOM = 2400;
    private static final int NAV_BAR = 48;
    /** The system keyboard's inset, the navigation bar under it included. */
    private static final int IME = 900;
    private static final int DOCK = 200;
    private static final int STACK_MARGIN = 16;

    @Test
    public void theTakeIsWhatTheRootFitsPastTheBars() {
        assertEquals("keyboard down: the root fits the bars alone", 0,
            KeyboardOverlayPolicy.systemImeTakePx(NAV_BAR, NAV_BAR, 0));
        assertEquals("a window resized for the keyboard", IME - NAV_BAR,
            KeyboardOverlayPolicy.systemImeTakePx(IME, NAV_BAR, 0));
        assertEquals("a keyboard the insets missed, answered by the root's margin", 300,
            KeyboardOverlayPolicy.systemImeTakePx(NAV_BAR, NAV_BAR, 300));
        assertEquals("bars hidden: the whole keyboard", IME,
            KeyboardOverlayPolicy.systemImeTakePx(IME, 0, 0));
        assertEquals("never negative", 0, KeyboardOverlayPolicy.systemImeTakePx(0, NAV_BAR, -4));
    }

    @Test
    public void homesContentKeepsItsBottomWithTheSystemKeyboardUp() {
        int down = contentBottom(true, NAV_BAR);
        int up = contentBottom(true, IME);
        assertEquals("the grid is not squeezed: the keyboard covers it", down, up);
        assertEquals("and reaches the root's bottom at rest, under the dock",
            WINDOW_BOTTOM - NAV_BAR, down);
    }

    @Test
    public void theWorkspaceStillGivesTheSystemKeyboardItsRoom() {
        int up = contentBottom(false, IME);
        assertEquals("the terminal ends on the dock, above the keyboard",
            WINDOW_BOTTOM - IME - STACK_MARGIN - DOCK, up);
        assertEquals("no reach past the take off Home",
            KeyboardOverlayPolicy.overlapPx(false, DOCK, STACK_MARGIN, 0),
            KeyboardOverlayPolicy.overlapPx(false, DOCK, STACK_MARGIN, 0, IME - NAV_BAR));
    }

    @Test
    public void floatsKeepTheirRoomAndMeetTheStackTheKeyboardPushedUp() {
        int restStackBottom = WINDOW_BOTTOM - NAV_BAR - STACK_MARGIN;
        int push = KeyboardOverlayPolicy.systemImeTakePx(IME, NAV_BAR, 0);
        int pushedStackBottom = restStackBottom - push;

        assertEquals("the dock's top at rest, wherever the keyboard pushed it",
            KeyboardOverlayPolicy.homeDockRestTopPx(restStackBottom, DOCK, 0),
            KeyboardOverlayPolicy.homeDockRestTopPx(pushedStackBottom, DOCK, push));
        assertEquals("the keyboard's top is the dock riding on it",
            pushedStackBottom - DOCK,
            KeyboardOverlayPolicy.homeKeyboardTopPx(false, push, pushedStackBottom, DOCK));
        assertEquals("nothing up: no keyboard top", -1,
            KeyboardOverlayPolicy.homeKeyboardTopPx(false, 0, restStackBottom, DOCK));
        int inAppStack = DOCK + 700;
        assertEquals("the in-app keyboard, in the stack, as before",
            restStackBottom - inAppStack,
            KeyboardOverlayPolicy.homeKeyboardTopPx(true, 0, restStackBottom, inAppStack));
    }

    /** Where the content column ends, the root fitting {@code rootFitBottomPx} at its bottom. */
    private static int contentBottom(boolean home, int rootFitBottomPx) {
        int rootInnerBottom = WINDOW_BOTTOM - rootFitBottomPx;
        int stackTop = rootInnerBottom - STACK_MARGIN - DOCK;
        int take = KeyboardOverlayPolicy.systemImeTakePx(rootFitBottomPx, NAV_BAR, 0);
        boolean dockOverlays = HomeChromePolicy.dockOverlays(
            home ? PaneWallPage.WIDGETS : PaneWallPage.TERMINAL);
        int overlap = KeyboardOverlayPolicy.overlapPx(dockOverlays, DOCK, STACK_MARGIN, 0,
            dockOverlays ? take : 0);
        return stackTop + overlap;
    }
}
