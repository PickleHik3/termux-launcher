package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

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

    /**
     * The stats pill is always on Home along the top or the bottom, the windows pill joins it with
     * the floats; a side bar comes only for the floats; with nothing to show there is no bar.
     */
    @Test
    public void homeWearsThePillsBandAlongTheTopOrBottomAndASideBarOnlyForFloats() {
        for (PlaceLayout.Edge edge : new PlaceLayout.Edge[] {PlaceLayout.Edge.TOP,
                PlaceLayout.Edge.BOTTOM}) {
            assertTrue(HomeChromePolicy.homeStatusBarShown(edge, false, true));
            assertTrue(HomeChromePolicy.homeStatusBarShown(edge, true, true));
            assertTrue("the windows pill alone", HomeChromePolicy.homeStatusBarShown(edge, true,
                false));
            assertFalse("nothing to show", HomeChromePolicy.homeStatusBarShown(edge, false,
                false));
        }
        for (PlaceLayout.Edge edge : new PlaceLayout.Edge[] {PlaceLayout.Edge.LEFT,
                PlaceLayout.Edge.RIGHT}) {
            assertFalse(HomeChromePolicy.homeStatusBarShown(edge, false, true));
            assertTrue(HomeChromePolicy.homeStatusBarShown(edge, true, true));
            assertTrue(HomeChromePolicy.homeStatusBarShown(edge, true, false));
        }
    }

    @Test
    public void homeHasTheStatusBarOnlyWhileItWearsItAndThenCompact() {
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

    @Test
    public void onlyABarThatIsNotThePillsBandOverlaysAHomePage() {
        assertFalse("the pills' band takes its room", HomeChromePolicy.statusOverlays(HOME, true));
        assertTrue(HomeChromePolicy.statusOverlays(HOME, false));
        assertFalse(HomeChromePolicy.statusOverlays(WINDOW, true));
        assertFalse(HomeChromePolicy.statusOverlays(WINDOW, false));
    }

    /** Nothing on the grid lies under a pill: the grid starts below the pills' band. */
    @Test
    public void theGridGivesThePillsBandItsRoom() {
        PlaceLayout home = new PlaceLayout(PlaceLayout.Edge.TOP, PlaceLayout.RowPlacement.BOTTOM,
            true, PlaceLayout.Edge.BOTTOM, PlaceLayout.RowPlacement.HIDDEN,
            PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5)
            .withSlot(Element.STATUS, Slot.hiddenFrom(PlaceLayout.Edge.TOP, 0));
        EdgeStackPolicy.Metrics metrics = EdgeStackPolicy.Metrics.builder()
            .status(120, 200).apps(150, 90).az(40, 60).extraKeys(80, 100).build();
        PlaceLayout withBar = HomeChromePolicy.withFloatStatusBar(home, PlaceLayout.Edge.TOP, true);
        boolean overlays = HomeChromePolicy.statusOverlays(HOME, true);
        int gridTopWithout = EdgeStackPolicy.contentInsets(home, metrics).top
            - EdgeStackPolicy.topOverlapPx(home, metrics, overlays);
        int gridTopWith = EdgeStackPolicy.contentInsets(withBar, metrics).top
            - EdgeStackPolicy.topOverlapPx(withBar, metrics, overlays);
        assertEquals("the band", gridTopWithout + 120, gridTopWith);
        // The floats' room starts at the bar's edge, under the whole top stack.
        assertEquals(120, EdgeStackPolicy.stackThicknessPx(withBar, PlaceLayout.Edge.TOP, metrics));
    }

    /**
     * Landscape on Home with the dock a rail on the left and the bar on the top: the rail starts at
     * the bar's edge whether the bar is the pills' band (taking its row, as on the Workspace) or
     * overlays the page (the canvas reaching up under it, the rail inset back down), so the rail's
     * first icon never covers the bar (devcheck w2-d-1s).
     */
    @Test
    public void aSideDockOnHomeStartsBelowTheBar() {
        PlaceLayout home = new PlaceLayout(PlaceLayout.Edge.TOP, PlaceLayout.RowPlacement.LEFT,
            true, PlaceLayout.Edge.BOTTOM, PlaceLayout.RowPlacement.HIDDEN,
            PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5)
            .withSlot(Element.STATUS, Slot.hiddenFrom(PlaceLayout.Edge.TOP, 0));
        EdgeStackPolicy.Metrics metrics = EdgeStackPolicy.Metrics.builder()
            .status(120, 200).apps(150, 90).az(40, 60).extraKeys(80, 100).build();
        PlaceLayout withBar = HomeChromePolicy.withFloatStatusBar(home, PlaceLayout.Edge.TOP, true);
        assertEquals(PlaceLayout.Edge.LEFT, EdgeStackPolicy.edgeOf(withBar, Element.APPS));
        for (boolean pillsBand : new boolean[] {true, false}) {
            boolean overlays = HomeChromePolicy.statusOverlays(HOME, pillsBand);
            int overlap = EdgeStackPolicy.topOverlapPx(withBar, metrics, overlays);
            assertEquals(pillsBand ? 0 : 120, overlap);
            int railTop = EdgeStackPolicy.stackThicknessPx(withBar, PlaceLayout.Edge.TOP, metrics)
                - overlap
                + EdgeStackPolicy.sideStackTopInsetPx(withBar, PlaceLayout.Edge.LEFT, metrics,
                    overlays);
            assertEquals("the rail starts at the bar's edge", 120, railTop);
            assertEquals("an empty side keeps no inset", 0,
                EdgeStackPolicy.sideStackTopInsetPx(withBar, PlaceLayout.Edge.RIGHT, metrics,
                    overlays));
            assertEquals("no bar, no inset", 0,
                EdgeStackPolicy.sideStackTopInsetPx(home, PlaceLayout.Edge.LEFT, metrics,
                    overlays));
        }
        assertEquals("a window's bar takes its row, so its rail needs none", 0,
            EdgeStackPolicy.sideStackTopInsetPx(withBar, PlaceLayout.Edge.LEFT, metrics,
                HomeChromePolicy.statusOverlays(WINDOW, false)));
    }

    /**
     * Device check 3, finding 1: the Keyboard chip brought out in the Layout editor on Home turns
     * always on on while the editor's own keyboard is up, so the arrangement pass leaves it; the
     * editor's end must not then take it down, or Home stays without it until a cold launch.
     */
    @Test
    public void theEditorsEndLeavesTheKeyboardUpOnHomeOnlyWhileAlwaysOnIsOn() {
        assertTrue(HomeChromePolicy.editorEndKeepsKeyboard(HOME, true));
        assertFalse(HomeChromePolicy.editorEndKeepsKeyboard(HOME, false));
        assertFalse(HomeChromePolicy.editorEndKeepsKeyboard(WINDOW, true));
        assertFalse(HomeChromePolicy.editorEndKeepsKeyboard(WINDOW, false));
        assertFalse(HomeChromePolicy.editorEndKeepsKeyboard(PaneWallPage.DISPLAY, true));
    }

    private static final WallHalfShape WORKSPACE_SHAPE = WallHalfShape.of(LayoutStyle.DOCKED, 24, 12);
    private static final WallHalfShape HOME_SHAPE = WallHalfShape.of(LayoutStyle.FLOATING, 32, 20);

    /** Slice 2: Style, Corners and Margin are Home's own on a Home page, the Workspace's on a window. */
    @Test
    public void aHomePageWearsHomesShapeAndAWindowTheWorkspacesWhateverMinimalModeSays() {
        assertTrue(HomeChromePolicy.wearsHomeShape(HOME));
        assertFalse(HomeChromePolicy.wearsHomeShape(WINDOW));
        assertFalse(HomeChromePolicy.wearsHomeShape(PaneWallPage.DISPLAY));
        assertEquals(HOME_SHAPE, HomeChromePolicy.shape(HOME, WORKSPACE_SHAPE, HOME_SHAPE));
        assertEquals(WORKSPACE_SHAPE, HomeChromePolicy.shape(WINDOW, WORKSPACE_SHAPE, HOME_SHAPE));
        assertEquals(WORKSPACE_SHAPE,
            HomeChromePolicy.shape(PaneWallPage.DISPLAY, WORKSPACE_SHAPE, HOME_SHAPE));
    }

    @Test
    public void onlyACrossingBetweenHalvesThatDifferRedressesTheChrome() {
        assertTrue(HomeChromePolicy.shapeChanges(HOME, WINDOW, WORKSPACE_SHAPE, HOME_SHAPE));
        assertTrue(HomeChromePolicy.shapeChanges(WINDOW, HOME, WORKSPACE_SHAPE, HOME_SHAPE));
        assertFalse("window to window", HomeChromePolicy.shapeChanges(WINDOW,
            PaneWallPage.DISPLAY, WORKSPACE_SHAPE, HOME_SHAPE));
        assertFalse("home to home", HomeChromePolicy.shapeChanges(HOME, HOME, WORKSPACE_SHAPE,
            HOME_SHAPE));
        WallHalfShape seeded = WallHalfShape.of(LayoutStyle.DOCKED, 24, 12);
        assertFalse("a Home that still follows the Workspace changes nothing",
            HomeChromePolicy.shapeChanges(HOME, WINDOW, WORKSPACE_SHAPE, seeded));
        assertTrue("one value apart is enough", HomeChromePolicy.shapeChanges(HOME, WINDOW,
            WORKSPACE_SHAPE, WallHalfShape.of(LayoutStyle.DOCKED, 24, 13)));
    }

    @Test
    public void aRememberedLayoutPlaceComesBackOnlyOnItsOwnHalf() {
        // Left over Home, brought back from a window: the window's own, not Home's.
        assertEquals(null, HomeChromePolicy.restoredEditorPlace(HOME, WINDOW));
        // Left over a window, brought back on Home: Home's.
        assertEquals(null, HomeChromePolicy.restoredEditorPlace(WINDOW, HOME));
        assertEquals(HOME, HomeChromePolicy.restoredEditorPlace(HOME, HOME));
        // Between two windows the remembered one stands, as before.
        assertEquals(PaneWallPage.DISPLAY,
            HomeChromePolicy.restoredEditorPlace(PaneWallPage.DISPLAY, WINDOW));
        assertEquals(null, HomeChromePolicy.restoredEditorPlace(null, WINDOW));
    }
}
