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
}
