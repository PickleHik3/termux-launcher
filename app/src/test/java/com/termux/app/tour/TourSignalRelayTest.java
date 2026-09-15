package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void oneMoreSessionThanLastTimeIsTheChaptersNewSession() {
        relay.onSessionsSettled(1, "s1");
        assertTrue(signals.isEmpty());
        relay.onSessionsSettled(2, "s2");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.SESSION_OPENED, signals.get(0));
    }

    @Test
    public void aRebuildThatMovedNothingSaysNothing() {
        relay.onSessionsSettled(1, "s1");
        relay.onSessionsSettled(1, "s1");
        relay.onSessionsSettled(1, "s1");
        assertTrue(signals.isEmpty());
    }

    @Test
    public void closingASessionIsNotAnOpening() {
        relay.onSessionsSettled(2, "s1");
        relay.onSessionsSettled(1, "s1");
        assertTrue(signals.isEmpty());
    }

    @Test
    public void onlyTheSessionTheChapterStartedInCountsAsComingBack() {
        relay.onSessionsSettled(1, "s1");
        relay.markKeyboardChapterHome();
        relay.onSessionsSettled(2, "s2");
        // Walking the ring past a third session is a swipe that has not arrived.
        relay.onSessionsSettled(3, "s3");
        signals.clear();
        relay.onSessionsSettled(3, "s1");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.SESSION_RETURNED, signals.get(0));
    }

    @Test
    public void theChapterHomeIsWhereTheChapterStartedNotWhereTheRunDid() {
        relay.onSessionsSettled(1, "s1");
        relay.onSessionsSettled(2, "s2");
        // The chapter opens on s2; s1 is somebody else's session now.
        relay.markKeyboardChapterHome();
        signals.clear();
        relay.onSessionsSettled(2, "s1");
        assertTrue(signals.isEmpty());
        relay.onSessionsSettled(2, "s2");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.SESSION_RETURNED, signals.get(0));
    }

    @Test
    public void aSessionSwitchBeforeTheChapterStartedClearsNothing() {
        relay.onSessionsSettled(1, "s1");
        relay.onSessionsSettled(2, "s2");
        relay.onSessionsSettled(2, "s1");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.SESSION_OPENED, signals.get(0));
    }

    @Test
    public void theWindowTheChapterStartedOnIsTheOneItAsksToComeBackTo() {
        relay.onActiveWindowSettled("w1");
        relay.markKeyboardChapterHome();
        relay.onActiveWindowSettled("w2");
        assertTrue(signals.isEmpty());
        relay.onActiveWindowSettled("w3");
        assertTrue(signals.isEmpty());
        relay.onActiveWindowSettled("w1");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.WINDOW_RETURNED, signals.get(0));
    }

    @Test
    public void theWindowTheUserNeverLeftIsNotAnArrival() {
        relay.onActiveWindowSettled("w1");
        relay.markKeyboardChapterHome();
        // A pane focus change inside the same window re-settles the same id.
        relay.onActiveWindowSettled("w1");
        relay.onActiveWindowSettled("w1");
        assertTrue(signals.isEmpty());
    }

    @Test
    public void theFirstWindowSeenIsOnlyPriming() {
        relay.markKeyboardChapterHome();
        relay.onActiveWindowSettled("w1");
        assertTrue(signals.isEmpty());
    }

    @Test
    public void aLauncherWithNothingToCountSaysNothing() {
        relay.onSessionsSettled(-1, null);
        relay.onActiveWindowSettled(null);
        assertTrue(signals.isEmpty());
    }

    @Test
    public void theHomePlaceCanBeSetUpFront() {
        // The first settle only says where the wall is — the launcher primes the relay with it
        // when the run is built — and the move after it is the swipe.
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled("TERMINAL");
        relay.onPlaceSettled("DISPLAY");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.PLACE_CHANGED, signals.get(0));
    }

    @Test
    public void everyOtherPlaceIsAChangeAndOnlyHomeIsAReturn() {
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled("TERMINAL");
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
    public void aPrimedDrawerReportsTheVeryFirstOpenTheUserPerforms() {
        // The first device pass lost the drawer card to exactly this: the relay swallows the
        // first call as the one that tells it where the plane rests, so the launcher primes it
        // with the resting state up front and the user's own first pull is an open.
        relay.onDrawerOpenSettled(false, false);
        relay.onDrawerOpenSettled(true, true);
        assertEquals(1, signals.size());
        assertEquals(TourSignals.DRAWER_OPENED, signals.get(0));
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
    public void theActionsAreTheirOwnEdge() {
        relay.onPaneSplit();
        relay.onPaneCornerMenuOpened();
        relay.onPaneControlsDismissed();
        relay.onAppLaunchedFromScrub();
        relay.onPaletteOpened();
        // Six, not five: a scrub launch is both the scrub's own signal and an app launch.
        assertEquals(6, signals.size());
        assertEquals(TourSignals.PANE_SPLIT, signals.get(0));
        assertEquals(TourSignals.PANE_CORNER_MENU, signals.get(1));
        assertEquals(TourSignals.PANE_CONTROLS_DISMISSED, signals.get(2));
        assertEquals(TourSignals.APP_LAUNCHED_FROM_SCRUB, signals.get(3));
        assertEquals(TourSignals.APP_LAUNCHED, signals.get(4));
        assertEquals(TourSignals.PALETTE_OPENED, signals.get(5));
    }

    @Test
    public void thePaletteClosesOnlyAfterAnOpenTheRunActuallySaw() {
        // The funnel this arrives from hands its keyboard slot back on pause, on a configuration
        // change and on destroy too, so a close of a palette that was never open says nothing.
        relay.onPaletteClosed();
        assertTrue(signals.isEmpty());
        relay.onPaletteOpened();
        relay.onPaletteClosed();
        assertEquals(2, signals.size());
        assertEquals(TourSignals.PALETTE_OPENED, signals.get(0));
        assertEquals(TourSignals.PALETTE_CLOSED, signals.get(1));
        // And a second close of the same palette is not another one.
        relay.onPaletteClosed();
        assertEquals(2, signals.size());
    }

    @Test
    public void thePaletteCanBeOpenedAndClosedAsOftenAsTheUserLikes() {
        relay.onPaletteOpened();
        relay.onPaletteClosed();
        relay.onPaletteOpened();
        relay.onPaletteClosed();
        assertEquals(4, signals.size());
        assertEquals(TourSignals.PALETTE_OPENED, signals.get(2));
        assertEquals(TourSignals.PALETTE_CLOSED, signals.get(3));
    }

    @Test
    public void openingAndDismissingThePaneControlsAreTwoSeparateSignalsEveryTime() {
        // The controls can be raised and dropped as often as the user likes, and the card asking
        // for the way out of them has to hear every one of those: there is no state to compare
        // against here, only the pane view's own two calls.
        relay.onPaneCornerMenuOpened();
        relay.onPaneControlsDismissed();
        relay.onPaneCornerMenuOpened();
        relay.onPaneControlsDismissed();
        assertEquals(4, signals.size());
        assertEquals(TourSignals.PANE_CORNER_MENU, signals.get(2));
        assertEquals(TourSignals.PANE_CONTROLS_DISMISSED, signals.get(3));
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
        relay.onPaneControlsDismissed();
        relay.onAppLaunchedFromScrub();
        relay.onPaletteOpened();
        relay.onPaletteClosed();
        assertTrue(signals.isEmpty());
    }

    @Test
    public void thePinnedHomePlaceIsTheOneToComeBackToWhereverTheWallWasFound() {
        // Replay from Settings can find the wall resting on the display; the run is still taught
        // on the terminal, so arriving there is the return and not the change.
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled("DISPLAY");
        assertTrue(signals.isEmpty());
        assertFalse(relay.isOnHomePlace());
        relay.onPlaceSettled("TERMINAL");
        assertEquals(1, signals.size());
        assertEquals(TourSignals.PLACE_RETURNED, signals.get(0));
        assertTrue(relay.isOnHomePlace());
        relay.onPlaceSettled("WIDGETS");
        assertEquals(TourSignals.PLACE_CHANGED, signals.get(1));
        assertFalse(relay.isOnHomePlace());
    }

    @Test
    public void aPlaceSettlingWhereItAlreadyWasIsNotASwipe() {
        // A rotation re-settles the place the wall is already on.
        relay.setHomePlace("TERMINAL");
        relay.onPlaceSettled("TERMINAL");
        relay.onPlaceSettled("DISPLAY");
        relay.onPlaceSettled("DISPLAY");
        relay.onPlaceSettled("DISPLAY");
        assertEquals(1, signals.size());
    }

    @Test
    public void untilTheWallHasSaidWhereItIsTheRunIsAtHome() {
        relay.setHomePlace("TERMINAL");
        assertTrue(relay.isOnHomePlace());
    }

    // Help, the keyboard, and the trip out to an Android app.

    @Test
    public void theFirstHelpStateSeenOnlySaysWhereHelpRests() {
        relay.onHelpShownSettled(false);
        assertTrue(signals.isEmpty());
        relay.onHelpShownSettled(true);
        assertEquals(1, signals.size());
        assertEquals(TourSignals.HELP_OPENED, signals.get(0));
        relay.onHelpShownSettled(false);
        assertEquals(2, signals.size());
        assertEquals(TourSignals.HELP_CLOSED, signals.get(1));
    }

    @Test
    public void helpClosingWithoutHavingBeenSeenOpenIsNotASignal() {
        // The launcher puts help away on a pause, a rotation and a destroy; a close of a help the
        // run never saw open is not the gesture the lesson is asking for.
        relay.onHelpShownSettled(false);
        relay.onHelpShownSettled(false);
        relay.onHelpShownSettled(false);
        assertTrue(signals.isEmpty());
        assertFalse(relay.isHelpShown());
    }

    @Test
    public void helpRestatingWhereItAlreadyIsSaysNothing() {
        relay.onHelpShownSettled(false);
        relay.onHelpShownSettled(true);
        relay.onHelpShownSettled(true);
        relay.onHelpShownSettled(true);
        assertEquals(1, signals.size());
        assertTrue(relay.isHelpShown());
    }

    @Test
    public void theFirstKeyboardStateSeenOnlySaysWhereTheKeyboardRests() {
        relay.onKeyboardShownSettled(true);
        assertTrue(signals.isEmpty());
        assertTrue(relay.isKeyboardShown());
        relay.onKeyboardShownSettled(false);
        assertEquals(1, signals.size());
        assertEquals(TourSignals.KEYBOARD_HIDDEN, signals.get(0));
        relay.onKeyboardShownSettled(true);
        assertEquals(2, signals.size());
        assertEquals(TourSignals.KEYBOARD_SHOWN, signals.get(1));
    }

    @Test
    public void aKeyboardReAppliedWithTheValueItAlreadyHadIsNotATap() {
        // A rotation, a place change and every preference reload re-apply the keyboard.
        relay.onKeyboardShownSettled(true);
        relay.onKeyboardShownSettled(true);
        relay.onKeyboardShownSettled(true);
        assertTrue(signals.isEmpty());
    }

    @Test
    public void anAppLaunchedByTheScrubIsAnAppLaunchedLikeAnyOther() {
        relay.onAppLaunchedFromScrub();
        assertEquals(2, signals.size());
        assertEquals(TourSignals.APP_LAUNCHED_FROM_SCRUB, signals.get(0));
        assertEquals(TourSignals.APP_LAUNCHED, signals.get(1));
    }

    @Test
    public void anAppLaunchedFromTheDrawerIsOneSignalEveryTime() {
        relay.onAppLaunched();
        relay.onAppLaunched();
        assertEquals(2, signals.size());
        assertEquals(TourSignals.APP_LAUNCHED, signals.get(0));
        assertEquals(TourSignals.APP_LAUNCHED, signals.get(1));
    }

    @Test
    public void comingBackToTheLauncherIsItsOwnSignal() {
        relay.onLauncherResumed();
        assertEquals(1, signals.size());
        assertEquals(TourSignals.LAUNCHER_RESUMED, signals.get(0));
    }

    @Test
    public void noneOfTheNewInputsSpeakWithoutSomeoneListening() {
        relay.setTourSignalListener(null);
        relay.onHelpShownSettled(false);
        relay.onHelpShownSettled(true);
        relay.onKeyboardShownSettled(true);
        relay.onKeyboardShownSettled(false);
        relay.onAppLaunched();
        relay.onLauncherResumed();
        assertTrue(signals.isEmpty());
    }
}
