package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * What a page of the wall wears by its kind (ADR 0008): the Home layout and overlays on a Home
 * page, the Workspace layout and the whole chrome on a window, decided in one policy.
 */
public class HomeChromePolicyTest {

    private static final PaneWallPage HOME = PaneWallPage.WIDGETS;
    private static final PaneWallPage WINDOW = PaneWallPage.TERMINAL;

    @Test
    public void aHomePageIsArrangedByTheHomeLayoutWhateverMinimalModeSays() {
        assertEquals(LayoutVariant.HOME, HomeChromePolicy.variant(HOME, false));
        assertEquals(LayoutVariant.HOME, HomeChromePolicy.variant(HOME, true));
        assertEquals(LayoutVariant.NORMAL, HomeChromePolicy.variant(WINDOW, false));
        assertEquals(LayoutVariant.MINIMAL, HomeChromePolicy.variant(WINDOW, true));
        assertEquals(LayoutVariant.MINIMAL,
            HomeChromePolicy.variant(PaneWallPage.DISPLAY, true));
    }

    @Test
    public void minimalModeIsRefusedOnAHomePage() {
        assertFalse(HomeChromePolicy.minimalModeApplies(HOME));
        assertTrue(HomeChromePolicy.minimalModeApplies(WINDOW));
        assertTrue(HomeChromePolicy.minimalModeApplies(PaneWallPage.DISPLAY));
    }

    @Test
    public void homeHasTheStatusBarOnlyWhileAHomeFloatExistsAndThenCompact() {
        assertFalse(HomeChromePolicy.statusShownAtRest(HOME, false));
        assertTrue(HomeChromePolicy.statusShownAtRest(HOME, true));
        assertTrue(HomeChromePolicy.statusShownAtRest(WINDOW, false));
        assertTrue(HomeChromePolicy.statusShownAtRest(WINDOW, true));
        assertTrue(HomeChromePolicy.statusForcedCompact(HOME));
        assertFalse(HomeChromePolicy.statusForcedCompact(WINDOW));
    }

    @Test
    public void aHomePageWearsNoBordersAndNoStatusBarAndItsDockFloats() {
        assertFalse(HomeChromePolicy.bordersShown(HOME));
        assertFalse(HomeChromePolicy.statusShownAtRest(HOME));
        assertTrue(HomeChromePolicy.dockOverlays(HOME));
        assertTrue(HomeChromePolicy.bordersShown(WINDOW));
        assertTrue(HomeChromePolicy.statusShownAtRest(WINDOW));
        assertFalse(HomeChromePolicy.dockOverlays(WINDOW));
    }

    @Test
    public void homeHasNoKeyboardMemoryAndIsUpOnlyWhileAlwaysOn() {
        assertFalse("remembered open means nothing on Home",
            HomeChromePolicy.keyboardOnEnter(HOME, true, false, false));
        assertTrue(HomeChromePolicy.keyboardOnEnter(HOME, false, false, true));
        assertTrue("minimal mode is the Workspace's",
            HomeChromePolicy.keyboardOnEnter(HOME, false, true, true));
        // A window keeps its memory, and minimal mode still puts it away.
        assertTrue(HomeChromePolicy.keyboardOnEnter(WINDOW, true, false, false));
        assertFalse(HomeChromePolicy.keyboardOnEnter(WINDOW, true, true, true));
        assertFalse(HomeChromePolicy.keyboardOnEnter(WINDOW, false, false, true));
    }

    @Test
    public void alwaysOnTakesTheWholeRowsTheKeyboardCovers() {
        // A 1000 px grid of 5 rows: 200 px a row.
        assertEquals(2, HomeChromePolicy.keyboardRows(400, 1000, 5));
        assertEquals(3, HomeChromePolicy.keyboardRows(401, 1000, 5));
        assertEquals(1, HomeChromePolicy.keyboardRows(10, 1000, 5));
        assertEquals("never the whole grid", 4, HomeChromePolicy.keyboardRows(5000, 1000, 5));
        assertEquals("nothing measured yet", 0, HomeChromePolicy.keyboardRows(400, 0, 5));
        assertEquals(0, HomeChromePolicy.keyboardRows(0, 1000, 5));
        assertEquals("a one-row grid has none to give", 0,
            HomeChromePolicy.keyboardRows(400, 1000, 1));
    }

    /** The dock and the keyboard take no room from the grid on Home, up or down. */
    @Test
    public void onAHomePageTheChromeTakesNoRoomWithTheKeyboardUpOrDown() {
        int dock = 120;
        int keyboard = 600;
        int margin = 8;
        assertEquals(0, KeyboardOverlayPolicy.restReservationPx(true, dock, 0, false, true,
            keyboard, margin));
        assertEquals(0, KeyboardOverlayPolicy.restReservationPx(true, dock, 0, true, true,
            keyboard, margin));
        // A window's dock takes its room; its keyboard takes room too unless it floats.
        assertEquals(dock + margin, KeyboardOverlayPolicy.restReservationPx(false, dock, 0, false,
            false, keyboard, margin));
        assertEquals(dock + keyboard + margin, KeyboardOverlayPolicy.restReservationPx(false,
            dock, 0, true, false, keyboard, margin));
        assertEquals("the old signature is a window's", dock + margin,
            KeyboardOverlayPolicy.restReservationPx(dock, 0, true, true, keyboard, margin));
    }

    /** The geometry pass's overlap: the whole stack on Home, the keyboard's share elsewhere. */
    @Test
    public void theContentReachesUnderTheWholeStackOnAHomePage() {
        int stack = 720;
        int margin = 8;
        int overlap = KeyboardOverlayPolicy.overlapPx(true, stack, margin, 600);
        assertEquals(stack + margin, overlap);
        assertEquals("nothing is reserved", 0,
            KeyboardOverlayPolicy.contentReservationPx(stack, margin, overlap));
        assertEquals("with the keyboard down too", 0, KeyboardOverlayPolicy.contentReservationPx(
            120, margin, KeyboardOverlayPolicy.overlapPx(true, 120, margin, 0)));
        assertEquals(600, KeyboardOverlayPolicy.overlapPx(false, stack, margin, 600));
        assertEquals(0, KeyboardOverlayPolicy.overlapPx(false, stack, margin, 0));
    }

    /**
     * The dock rides the keyboard as a transform: the stack is laid out once with the keyboard in
     * it, and while the keyboard is part way up the whole stack, dock included, is slid down by
     * the share of the keyboard not yet revealed. Nothing is laid out per frame.
     */
    @Test
    public void theDockRidesTheKeyboardsRevealAsATranslation() {
        PlaceChromeTravel.Rest down = new PlaceChromeTravel.Rest(false, false, false);
        PlaceChromeTravel.Rest up = new PlaceChromeTravel.Rest(true, false, false);
        int keyboard = 600;
        int dock = 128;
        assertEquals(keyboard, PlaceChromeTravel.stackTranslationPx(
            PlaceChromeTravel.rest(HOME, down), keyboard, dock), 0f);
        assertEquals(0f, PlaceChromeTravel.stackTranslationPx(
            PlaceChromeTravel.rest(HOME, up), keyboard, dock), 0f);
    }

    /** The window's status bar fades on the way to Home, and arrives with the window. */
    @Test
    public void theStatusBarFadesOutTowardHomeAndArrivesWithTheWindow() {
        List<PaneWallPage> row = Arrays.asList(HOME, WINDOW);
        PlaceChromeTravel.States states = page -> new PlaceChromeTravel.Rest(false, false,
            HomeChromePolicy.statusShownAtRest(page));
        // Resting on the window, the wall half way back toward Home.
        PlaceChromeTravel.Frame halfway = PlaceChromeTravel.at(row, WINDOW, 500f, 1000, states);
        assertEquals(0.5f, halfway.statusReveal, 1e-4f);
        assertEquals("the dock is on both halves", 1f, halfway.dockReveal, 0f);
        PlaceChromeTravel.Frame home = PlaceChromeTravel.at(row, HOME, 0f, 1000, states);
        assertEquals(0f, home.statusReveal, 0f);
        PlaceChromeTravel.Frame window = PlaceChromeTravel.at(row, WINDOW, 0f, 1000, states);
        assertEquals(1f, window.statusReveal, 0f);
    }
}
