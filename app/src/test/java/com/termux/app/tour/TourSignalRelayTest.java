package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The two adapters that are wired today.
 *
 * <p>The launcher re-applies both of these states constantly — every place change re-applies the
 * status bar, every rotation re-settles the place — so the relay has to speak only on an edge, or
 * the first card clears itself while the user is still reading it.
 */
public class TourSignalRelayTest {

    private TourSignalRelay relay;
    private List<String> signals;

    @Before
    public void setUp() {
        relay = new TourSignalRelay();
        signals = new ArrayList<>();
        relay.setTourSignalListener(signals::add);
    }

    @Test
    public void theFirstPlaceSeenIsTheOneToComeBackTo() {
        relay.onPlaceSettled("TERMINAL");
        assertTrue(signals.isEmpty());
        relay.onPlaceSettled("WIDGETS");
        relay.onPlaceSettled("TERMINAL");
        assertEquals(2, signals.size());
        assertEquals(TourSignals.PLACE_CHANGED, signals.get(0));
        assertEquals(TourSignals.PLACE_RETURNED, signals.get(1));
    }

    @Test
    public void theHomePlaceCanBeSetUpFront() {
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled("DISPLAY");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.PLACE_CHANGED, signals.get(0));
    }

    @Test
    public void everyOtherPlaceIsAChangeAndOnlyHomeIsAReturn() {
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled("WIDGETS");
        relay.onPlaceSettled("DISPLAY");
        relay.onPlaceSettled("TERMINAL");
        assertEquals(3, signals.size());
        assertEquals(TourSignals.PLACE_CHANGED, signals.get(0));
        assertEquals(TourSignals.PLACE_CHANGED, signals.get(1));
        assertEquals(TourSignals.PLACE_RETURNED, signals.get(2));
    }

    @Test
    public void aPlaceThatIsNotThereYetIsNotASignal() {
        relay.onPlaceSettled(null);
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled(null);
        assertTrue(signals.isEmpty());
    }

    @Test
    public void theStatusBarSpeaksOnlyWhenItActuallyMoves() {
        relay.onStatusBarCollapsedSettled(true);
        assertTrue(signals.isEmpty());
        relay.onStatusBarCollapsedSettled(true);
        assertTrue(signals.isEmpty());
        relay.onStatusBarCollapsedSettled(false);
        relay.onStatusBarCollapsedSettled(false);
        relay.onStatusBarCollapsedSettled(true);
        assertEquals(2, signals.size());
        assertEquals(TourSignals.STATUS_BAR_EXPANDED, signals.get(0));
        assertEquals(TourSignals.STATUS_BAR_COLLAPSED, signals.get(1));
    }

    @Test
    public void aBarThatStartsExpandedReportsItsCollapseFirst() {
        relay.onStatusBarCollapsedSettled(false);
        relay.onStatusBarCollapsedSettled(true);
        assertEquals(1, signals.size());
        assertEquals(TourSignals.STATUS_BAR_COLLAPSED, signals.get(0));
    }

    @Test
    public void nothingIsEmittedWithoutSomeoneListening() {
        relay.setTourSignalListener(null);
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled("WIDGETS");
        relay.onStatusBarCollapsedSettled(true);
        relay.onStatusBarCollapsedSettled(false);
        assertTrue(signals.isEmpty());
    }

    @Test
    public void aWindowCountThatGrowsIsAnOpenAndOneThatShrinksIsAClose() {
        relay.onWindowCountSettled(1);
        assertTrue(signals.isEmpty());
        relay.onWindowCountSettled(2);
        relay.onWindowCountSettled(1);
        assertEquals(2, signals.size());
        assertEquals(TourSignals.WINDOW_OPENED, signals.get(0));
        assertEquals(TourSignals.WINDOW_CLOSED, signals.get(1));
    }

    @Test
    public void aRowRebuiltWithTheSameWindowsSaysNothing() {
        relay.onWindowCountSettled(2);
        relay.onWindowCountSettled(2);
        relay.onWindowCountSettled(2);
        assertTrue(signals.isEmpty());
    }

    @Test
    public void aCountThatIsNotThereYetIsNotASignal() {
        relay.onWindowCountSettled(-1);
        relay.onWindowCountSettled(1);
        relay.onWindowCountSettled(-1);
        assertTrue(signals.isEmpty());
    }

    @Test
    public void everyChipTapIsAChipTapIncludingTheFirstAndTheCurrentOne() {
        // The status bar reports a chip only from its own tap listener, so unlike the count there
        // is no rebuild to filter out — and re-tapping the current chip is how the x is revealed.
        relay.onWindowSelected("a");
        relay.onWindowSelected("a");
        relay.onWindowSelected("b");
        assertEquals(3, signals.size());
        assertEquals(TourSignals.WINDOW_CHIP_SELECTED, signals.get(0));
        assertEquals(TourSignals.WINDOW_CHIP_SELECTED, signals.get(1));
        assertEquals(TourSignals.WINDOW_CHIP_SELECTED, signals.get(2));
    }

    @Test
    public void aWindowThatIsNotThereYetIsNotASignal() {
        relay.onWindowSelected(null);
        assertTrue(signals.isEmpty());
    }

    @Test
    public void theDrawerSpeaksOnlyWhenItActuallyMoves() {
        relay.onDrawerOpenSettled(false, true);
        assertTrue(signals.isEmpty());
        relay.onDrawerOpenSettled(false, true);
        assertTrue(signals.isEmpty());
        relay.onDrawerOpenSettled(true, true);
        relay.onDrawerOpenSettled(true, true);
        relay.onDrawerOpenSettled(false, true);
        assertEquals(2, signals.size());
        assertEquals(TourSignals.DRAWER_OPENED, signals.get(0));
        assertEquals(TourSignals.DRAWER_CLOSED, signals.get(1));
    }

    @Test
    public void aDrawerThatStartsOpenReportsItsCloseFirst() {
        relay.onDrawerOpenSettled(true, true);
        relay.onDrawerOpenSettled(false, true);
        assertEquals(1, signals.size());
        assertEquals(TourSignals.DRAWER_CLOSED, signals.get(0));
    }

    @Test
    public void aDrawerTheLauncherPutAwayItselfIsNotTheUsersSwipe() {
        relay.onDrawerOpenSettled(false, true);
        relay.onDrawerOpenSettled(true, true);
        assertEquals(1, signals.size());
        // HOME, a rotation and a preference reload all close the plane without a finger.
        relay.onDrawerOpenSettled(false, false);
        assertEquals(1, signals.size());
        // And the state is still tracked, so the next real open is still an open.
        relay.onDrawerOpenSettled(true, true);
        assertEquals(2, signals.size());
        assertEquals(TourSignals.DRAWER_OPENED, signals.get(1));
    }

    @Test
    public void theFourActionsAreTheirOwnEdge() {
        relay.onPaneSplit();
        relay.onPaneCornerMenuOpened();
        relay.onAppLaunchedFromScrub();
        relay.onPaletteOpened();
        assertEquals(4, signals.size());
        assertEquals(TourSignals.PANE_SPLIT, signals.get(0));
        assertEquals(TourSignals.PANE_CORNER_MENU, signals.get(1));
        assertEquals(TourSignals.APP_LAUNCHED_FROM_SCRUB, signals.get(2));
        assertEquals(TourSignals.PALETTE_OPENED, signals.get(3));
    }

    @Test
    public void nothingAtAllIsEmittedWithoutSomeoneListening() {
        relay.setTourSignalListener(null);
        relay.onWindowCountSettled(1);
        relay.onWindowCountSettled(2);
        relay.onWindowSelected("a");
        relay.onWindowSelected("b");
        relay.onDrawerOpenSettled(false, true);
        relay.onDrawerOpenSettled(true, true);
        relay.onPaneSplit();
        relay.onPaneCornerMenuOpened();
        relay.onAppLaunchedFromScrub();
        relay.onPaletteOpened();
        assertTrue(signals.isEmpty());
    }
}
