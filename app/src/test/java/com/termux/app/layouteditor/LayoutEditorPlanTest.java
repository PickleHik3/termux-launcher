package com.termux.app.layouteditor;

import com.termux.app.place.LayoutVariant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.editorshell.EditorShellMetrics;
import com.termux.app.fragments.settings.PlaceMiniatureView;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Layout editor's decisions, without a window: which orientation the canvas shows and which
 * one a drop writes, what a drop, a handle, a tray chip or a type chip does to the live place,
 * when the session is dirty, and what the revert puts back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorPlanTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    /** Landscape viewports in pixels, the shapes a phone and a window of one actually take. */
    private static final int[][] LANDSCAPE_VIEWPORTS = {
        {960, 540}, {1024, 600}, {1300, 600}, {1280, 720}, {1600, 720}, {1920, 1080},
        {2340, 1080}, {2400, 1080}};

    private SharedPreferences prefs;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("layout-editor-plan-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    private LayoutEditorPlan enterOnTerminalInPortrait() {
        return LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
    }

    @Test
    public void theEditorOpensOnThePhonesOwnOrientationAndTheLivePlaceFollows() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(PaneWallPage.TERMINAL, plan.place());
        assertEquals(PORTRAIT, plan.shownOrientation());
        assertEquals(PORTRAIT, plan.deviceOrientation());
        assertTrue("what is shown is what the phone is in", plan.liveFollows());
    }

    @Test
    public void theToggleMovesWhatIsShownAndWhatADropWritesButNotThePhone() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        plan.showOrientation(LANDSCAPE);
        assertEquals(LANDSCAPE, plan.shownOrientation());
        assertEquals("the phone did not turn", PORTRAIT, plan.deviceOrientation());
        assertFalse("so the live place stays where it is", plan.liveFollows());

        // The miniature draws the orientation on the toggle, whatever the phone is in.
        assertEquals(places.resolve(LANDSCAPE), plan.shownLayout());
    }

    @Test
    public void aDropInTheShownOrientationWritesThatOrientationAlone() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.STATUS_BAR, Edge.BOTTOM));
        assertEquals("bottom", prefs.getString("layout.portrait.status_bar", null));
        assertNull("landscape untouched",
            prefs.getString("layout.landscape.status_bar", null));

        plan.showOrientation(LANDSCAPE);
        assertEquals("the phone is still in portrait, so only the miniature moves",
            LayoutEditorPlan.Drop.MINIATURE, plan.drop(Bar.APPS_ROW, Edge.RIGHT));
        assertEquals("right", prefs.getString("layout.landscape.apps_row", null));
        assertNull("portrait untouched", prefs.getString("layout.portrait.apps_row", null));
    }

    @Test
    public void aDropInTheTrayHidesTheBarForTheShownOrientation() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.EXTRA_KEYS, null));
        assertEquals("hidden", prefs.getString("layout.portrait.extra_keys", null));
        assertEquals(RowPlacement.HIDDEN, places.extraKeys(PORTRAIT));
    }

    @Test
    public void theStatusBarDroppedInTheTrayIsHiddenAndTheRevertBringsItBack() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        // Every bar stands on every edge, and every bar may be put away — the status bar too,
        // since the wall's paging is the border drag and nothing rides the bar's swipe.
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.STATUS_BAR, null));
        assertEquals("hidden", prefs.getString("layout.portrait.status_bar", null));
        assertTrue(places.resolve(PORTRAIT).slot(
            com.termux.app.place.Element.STATUS).hidden);
        assertFalse("the other orientation keeps its bar",
            places.resolve(LANDSCAPE).slot(com.termux.app.place.Element.STATUS).hidden);
        assertTrue("a hidden status bar is something to lose", plan.isDirty());

        plan.revert();
        assertFalse(places.resolve(PORTRAIT).slot(
            com.termux.app.place.Element.STATUS).hidden);
        assertFalse(plan.isDirty());

        assertEquals("a row on the top edge is a placement like any other",
            LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.APPS_ROW, Edge.TOP));
        assertEquals("top", prefs.getString("layout.portrait.apps_row", null));
    }

    /** What stands along the bottom of the Terminal in portrait, outermost first. */
    private List<String> bottomStack() {
        List<String> names = new ArrayList<>();
        for (com.termux.app.place.Element element : EdgeStackPolicy.stack(
            places.resolve(PORTRAIT), Edge.BOTTOM))
            names.add(element.name());
        return names;
    }

    @Test
    public void aReOrderIsAnUnsavedChangeAndTheRevertPutsTheStackBack() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertEquals("the bottom as it ships, outermost first",
            Arrays.asList("EXTRA_KEYS", "AZ", "APPS"), bottomStack());
        assertFalse(plan.isDirty());

        // The pinned apps dropped against the screen edge: same edge, new position.
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.APPS_ROW, Edge.BOTTOM, 0));
        assertEquals(Arrays.asList("APPS", "EXTRA_KEYS", "AZ"), bottomStack());
        assertTrue("moving a bar within its edge is a change like any other", plan.isDirty());

        plan.revert();
        assertFalse(plan.isDirty());
        assertEquals("the stack is back the way the editor found it",
            Arrays.asList("EXTRA_KEYS", "AZ", "APPS"), bottomStack());
    }

    @Test
    public void aBarDroppedBackIntoItsOwnGapChangesNothing() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        // The extra keys are already the outermost band of the bottom.
        plan.drop(Bar.EXTRA_KEYS, Edge.BOTTOM, 0);
        assertFalse("it landed where it already stood", plan.isDirty());
    }

    @Test
    public void turningThePhoneMovesBothWhatIsShownAndWhatTheLivePlaceFollows() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        plan.showOrientation(LANDSCAPE);
        assertFalse(plan.liveFollows());

        // The editor shows what the user is looking at, so a rotation takes the miniature with it.
        plan.onDeviceOrientationChanged(LANDSCAPE);
        assertEquals(LANDSCAPE, plan.deviceOrientation());
        assertEquals(LANDSCAPE, plan.shownOrientation());
        assertTrue(plan.liveFollows());
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.STATUS_BAR, Edge.LEFT));
        assertEquals("left", prefs.getString("layout.landscape.status_bar", null));
    }

    @Test
    public void aSessionIsDirtyOnceABarHasMovedAndCleanAgainAfterTheRevert() {
        places.setStatusBarEdge(PORTRAIT, Edge.TOP);
        places.setAppsRow(LANDSCAPE, RowPlacement.BOTTOM);
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertFalse("nothing moved yet", plan.isDirty());

        plan.drop(Bar.STATUS_BAR, Edge.BOTTOM);
        assertTrue(plan.isDirty());

        plan.showOrientation(LANDSCAPE);
        plan.drop(Bar.APPS_ROW, Edge.LEFT);
        assertTrue(plan.isDirty());

        plan.revert();
        assertFalse("every bar is back where the editor found it", plan.isDirty());
        assertEquals(Edge.TOP, places.statusBarEdge(PORTRAIT));
        assertEquals(RowPlacement.BOTTOM, places.appsRow(LANDSCAPE));
    }

    @Test
    public void aDropThatChangesNothingIsNotAnUnsavedChange() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        Edge resting = places.statusBarEdge(PORTRAIT);

        plan.drop(Bar.STATUS_BAR, resting);
        assertFalse("the bar landed where it already stood", plan.isDirty());
    }

    @Test
    public void theRevertLeavesTheSessionOpenOnWhatItWasShowing() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        plan.showOrientation(LANDSCAPE);
        plan.drop(Bar.EXTRA_KEYS, null);

        plan.revert();
        assertEquals("the toggle does not move", LANDSCAPE, plan.shownOrientation());
        assertFalse(plan.isDirty());
    }

    @Test
    public void aSecondDoorMovesTheEditorToThatPlaceAndDiscardStillCoversTheFirst() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        plan.drop(Bar.EXTRA_KEYS, null);

        plan.showPlace(PaneWallPage.WIDGETS);
        assertEquals(PaneWallPage.WIDGETS, plan.place());
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.STATUS_BAR, Edge.BOTTOM));
        assertEquals("bottom", prefs.getString("layout.portrait.status_bar", null));

        plan.revert();
        assertFalse(plan.isDirty());
        assertEquals("the place the editor opened on is back too",
            RowPlacement.BOTTOM, places.extraKeys(PORTRAIT));
    }

    // ----------------------------------------------------------------------- the notice slot

    @Test
    public void theSideStatusNoticeIsSilentWhileTheStatusBarStandsOnTopOrBottom() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertFalse("the shipped default is the top edge", plan.warnsSideStatusBar());

        plan.drop(Bar.STATUS_BAR, Edge.BOTTOM);
        assertFalse(plan.warnsSideStatusBar());
    }

    @Test
    public void theSideStatusNoticeFiresOnceTheStatusBarStandsOnASide() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        plan.drop(Bar.STATUS_BAR, Edge.LEFT);
        assertTrue(plan.warnsSideStatusBar());

        plan.showOrientation(LANDSCAPE);
        assertFalse("the other orientation is untouched", plan.warnsSideStatusBar());

        plan.drop(Bar.STATUS_BAR, Edge.RIGHT);
        assertTrue(plan.warnsSideStatusBar());
    }

    // ------------------------------------------------------- the handles, the tray, the chips

    /**
     * The keyboard's switch is one value for both orientations, so putting the keyboard away in
     * the tray lands on the live place whichever orientation the canvas shows.
     */
    @Test
    public void theKeyboardPutAwayFollowsLiveOnEitherOrientation() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        plan.showOrientation(LANDSCAPE);

        assertEquals("shared by both orientations",
            LayoutEditorPlan.Drop.LIVE, plan.setKeyboardShown(false));
        assertFalse(places.isKeyboardShown());
        assertTrue("as unsaved as a moved bar", plan.isDirty());
        assertTrue(plan.trayItems().contains(LayoutEditorPlan.TrayItem.KEYBOARD));

        assertEquals(LayoutEditorPlan.Drop.LIVE,
            plan.restore(LayoutEditorPlan.TrayItem.KEYBOARD));
        assertTrue(places.isKeyboardShown());
        assertFalse(plan.trayItems().contains(LayoutEditorPlan.TrayItem.KEYBOARD));
        assertFalse(plan.isDirty());
    }

    /** A tray chip brings a hidden bar back to the edge it was put away from. */
    @Test
    public void aTrayChipBringsABarBackToTheEdgeItLeft() {
        places.setSlot(PORTRAIT, com.termux.app.place.Element.EXTRA_KEYS,
            com.termux.app.place.Slot.on(Edge.TOP, com.termux.app.place.Element.EXTRA_KEYS));
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.EXTRA_KEYS, null));
        assertTrue(plan.trayItems().contains(LayoutEditorPlan.TrayItem.EXTRA_KEYS));

        assertEquals(LayoutEditorPlan.Drop.LIVE,
            plan.restore(LayoutEditorPlan.TrayItem.EXTRA_KEYS));
        assertFalse(plan.trayItems().contains(LayoutEditorPlan.TrayItem.EXTRA_KEYS));
        assertEquals("back on the top edge, not folded to the bottom", Edge.TOP,
            plan.shownLayout().slot(com.termux.app.place.Element.EXTRA_KEYS).edge);
        assertFalse("the bar is where it started", plan.isDirty());
        assertEquals("a chip for a bar on the phone does nothing", LayoutEditorPlan.Drop.NONE,
            plan.restore(LayoutEditorPlan.TrayItem.STATUS_BAR));
    }

    /** A type chip writes the orientation on the toggle alone. */
    @Test
    public void aTypeChipWritesTheOrientationOnTheToggleAlone() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.setKeyboardForm(KeyboardForm.FLOATING));
        assertEquals(KeyboardForm.FLOATING, places.keyboardForm(PORTRAIT));
        assertNull("landscape untouched",
            prefs.getString("layout.landscape.keyboard_form", null));
        assertEquals("the chip already checked writes nothing", LayoutEditorPlan.Drop.NONE,
            plan.setKeyboardForm(KeyboardForm.FLOATING));

        plan.showOrientation(LANDSCAPE);
        assertEquals("read again for the orientation now shown",
            KeyboardForm.DOCKED, plan.keyboardForm());
        assertEquals(LayoutEditorPlan.Drop.MINIATURE, plan.setKeyboardForm(KeyboardForm.SPLIT));
        assertEquals(KeyboardForm.SPLIT, places.keyboardForm(LANDSCAPE));
        assertEquals("portrait keeps what it was given",
            KeyboardForm.FLOATING, places.keyboardForm(PORTRAIT));
    }

    /** The grid's corner handle writes both counts, for the orientation on the toggle. */
    @Test
    public void theGridHandleWritesTheOrientationOnTheToggleAlone() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, LANDSCAPE);

        plan.setWidgetGrid(6, 3);
        assertEquals(6, places.widgetColumns(LANDSCAPE));
        assertEquals(3, places.widgetRows(LANDSCAPE));
        assertNull("portrait untouched",
            prefs.getString("layout.portrait.widget_columns", null));
        assertTrue(plan.isDirty());
    }

    @Test
    public void aSizeHandleWritesTheOrientationOnTheToggleAlone() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        plan.setKeyboardHeightScale(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
            places.keyboardHeightScale(PORTRAIT), 0.001f);
        assertNull("landscape untouched",
            prefs.getString("layout.landscape.keyboard_height", null));

        plan.showOrientation(LANDSCAPE);
        assertEquals(LayoutEditorPlan.Drop.MINIATURE, plan.setDockHeightScale(
            TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT));
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT,
            places.dockHeightScale(LANDSCAPE), 0.001f);
        assertNull("portrait's dock untouched",
            prefs.getString("layout.portrait.dock_height", null));

        plan.setKeyboardChinDp(24);
        assertEquals(24, places.keyboardChinDp(LANDSCAPE));
        assertEquals(24, plan.keyboardChinDp());
    }

    @Test
    public void aSizeThatMovesIsAnUnsavedChangeAndTheRevertPutsAllThreeBack() {
        places.setDockHeightScale(PORTRAIT, 1.5f);
        places.setKeyboardHeightScale(PORTRAIT, 1.2f);
        places.setKeyboardChinDp(PORTRAIT, 12);
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertFalse("nothing dragged yet", plan.isDirty());

        plan.setDockHeightScale(TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT);
        assertTrue("a size is as unsaved as a moved bar", plan.isDirty());
        plan.setKeyboardHeightScale(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE);
        plan.setKeyboardChinDp(48);

        plan.revert();
        assertFalse(plan.isDirty());
        assertEquals(1.5f, places.dockHeightScale(PORTRAIT), 0.001f);
        assertEquals(1.2f, places.keyboardHeightScale(PORTRAIT), 0.001f);
        assertEquals(12, places.keyboardChinDp(PORTRAIT));
    }

    @Test
    public void aSizeDraggedBackToWhereItStoodIsNotAnUnsavedChange() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        plan.setKeyboardChinDp(plan.keyboardChinDp());
        assertFalse("the handle landed where it already stood", plan.isDirty());
    }

    @Test
    public void thePortraitCanvasIsTwoFifthsOfTheScreenAndTheLandscapeOneAsWideAsTheCard() {
        int reserved = 60;
        float portraitAspect = 9f / 19.5f;
        float landscapeAspect = 19.5f / 9f;
        int column = 933;

        int resting = LayoutEditorPlan.restingCardHeightPx(1080, 2400, 2300);
        int portrait = LayoutEditorPlan.miniatureHeightPx(PORTRAIT, 2400, portraitAspect,
            reserved, column, 400, resting);
        assertEquals("42% of the screen, plus the room the tray keeps",
            Math.round(0.42f * 2400) + reserved, portrait);
        // A card whose chrome has grown — a large font scale, both notices showing — does not take
        // the fifth of the screen the sheet is leaving the live place. The picture gives way.
        assertEquals("the sheet keeps its height and the frame takes what is left",
            resting - 1600, LayoutEditorPlan.miniatureHeightPx(PORTRAIT, 2400, portraitAspect,
                reserved, column, 1600, resting));

        // The landscape layout shown on a portrait phone: a frame as wide as the card, and no
        // taller than that width allows — the height a screen-wide frame asked for beyond it was
        // dead air above and below the picture.
        assertEquals("as wide as the card's column",
            Math.round(column / landscapeAspect) + reserved,
            LayoutEditorPlan.miniatureHeightPx(LANDSCAPE, 2400, landscapeAspect, reserved,
                column, 400, resting));

        // The landscape viewport is that phone turned: 2400 wide, 1080 tall. A frame as wide as
        // the column is then taller than the card, so it stops at what the chrome and the rows'
        // peek leave it.
        int landscapeRest = LayoutEditorPlan.restingCardHeightPx(2400, 1080, 1000);
        int onACard = LayoutEditorPlan.miniatureHeightPx(LANDSCAPE, 1080, landscapeAspect,
            reserved, 2300, 400, landscapeRest);
        assertEquals("only what is left once the chrome and the rows' peek have theirs",
            landscapeRest - 400, onACard);
        assertTrue("which is less than the frame asked for",
            onACard < Math.round(2300 / landscapeAspect) + reserved);
    }

    @Test
    public void everyLandscapeViewportKeepsTheFrameInsideTheCardAndTheRowsInView() {
        // The two the landscape review reproduced on. The card's chrome and the rows' peek are dp,
        // so only the density moves them; the font scale moves what stands inside the rows, which
        // is the list's business and not this sum's.
        for (float density : new float[]{180f / 160f, 260f / 160f}) {
            int chooser = Math.round(density * EditorShellMetrics.CHOOSER_DP);
            int padding = Math.round(density * 16f);
            // No rows stand under the canvas any more, so nothing peeks under it.
            int peek = 0;
            int reserved = Math.round(density * 48f);
            float aspect = PlaceMiniatureView.frameAspect(LANDSCAPE);
            for (int[] viewport : LANDSCAPE_VIEWPORTS) {
                int rest = LayoutEditorPlan.restingCardHeightPx(viewport[0], viewport[1],
                    viewport[1] - Math.round(density * 42f));
                int column = Math.min(EditorShellMetrics.contentWidthPx(viewport[0], density),
                    EditorShellMetrics.px(EditorShellMetrics.ROW_MAX_INNER_DP, density));
                // The worst case: both notices showing, which is the most chrome there is.
                int chrome = LayoutEditorController.cardChromePx(rest, chooser, padding, 2,
                    density);
                int height = LayoutEditorPlan.miniatureHeightPx(LANDSCAPE, viewport[1], aspect,
                    reserved, column, chrome + peek, rest);
                String where = viewport[0] + "x" + viewport[1] + " at " + density + "x";
                int frame = height - reserved;
                assertTrue("the frame is no wider than the card at " + where,
                    frame * aspect <= column + aspect);
                assertTrue("and leaves the chrome and the rows' peek their room at " + where,
                    frame <= Math.max(0, rest - chrome - peek - reserved));
            }
        }
    }

    @Test
    public void theOtherOrientationIsSaidOnlyWhileTheToggleHasLeftTheOneThePhoneIsIn() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertFalse("the editor opens on what the phone is in", plan.warnsOtherOrientation());

        plan.showOrientation(LANDSCAPE);
        assertFalse(plan.liveFollows());
        assertTrue("the toggle has left the phone's own orientation",
            plan.warnsOtherOrientation());

        plan.onDeviceOrientationChanged(LANDSCAPE);
        assertTrue(plan.liveFollows());
        assertFalse("the phone turned to the orientation on the toggle",
            plan.warnsOtherOrientation());
    }

    // ------------------------------------------------------------------------------- the sheet

    /** pong: 1080x2412 at 420dpi, which is 411 x 919 dp. */
    private static final float PONG_DENSITY = 2.625f;
    private static final int PONG_WIDTH_PX = 1080;
    private static final int PONG_HEIGHT_PX = 2412;

    private static int pongPx(float dp) {
        return Math.round(dp * PONG_DENSITY);
    }

    /** pong's host under its status inset, less the sheet's top air and its bottom margin. */
    private static int pongExpanded() {
        int hostPx = PONG_HEIGHT_PX - pongPx(24f);
        return LayoutEditorPlan.expandedCardBudgetPx(hostPx - pongPx(
            LayoutEditorController.SHEET_TOP_AIR_DP + LayoutEditorController.SHEET_BOTTOM_MARGIN_DP));
    }

    @Test
    public void theRestingSheetLeavesTheLivePlaceTheTopOfPongsScreenAndShowsTheFirstRows() {
        // Worked the way LayoutEditorController.applyCanvasHeight works it out, on the Terminal
        // place in portrait: nothing is warned about, so there are no notice lines.
        int rest = LayoutEditorPlan.restingCardHeightPx(PONG_WIDTH_PX, PONG_HEIGHT_PX,
            pongExpanded());
        int chooser = pongPx(EditorShellMetrics.CHOOSER_DP);
        int padding = pongPx(16f);
        // No rows stand under the canvas any more, so nothing peeks under it.
        int peek = 0;
        int reserved = pongPx(48f);
        int chrome = LayoutEditorController.cardChromePx(pongExpanded(), chooser, padding, 0,
            PONG_DENSITY);
        int column = EditorShellMetrics.contentWidthPx(PONG_WIDTH_PX, PONG_DENSITY);
        int miniature = LayoutEditorPlan.miniatureHeightPx(PORTRAIT, PONG_HEIGHT_PX,
            PlaceMiniatureView.frameAspect(PORTRAIT), reserved, column, chrome + peek, rest);

        assertEquals("the sheet rests in four fifths of the screen",
            Math.round(0.80f * PONG_HEIGHT_PX), rest);
        assertEquals("with the picture at its full two fifths",
            Math.round(0.42f * PONG_HEIGHT_PX) + reserved, miniature);
        assertTrue("and the first rows in view under it before anything scrolls",
            rest - chrome - miniature >= peek);

        // What the user sees above the card: the live place, with its status strip and chip row.
        int cardTop = PONG_HEIGHT_PX - rest
            - pongPx(LayoutEditorController.SHEET_BOTTOM_MARGIN_DP);
        assertTrue("about a fifth of the screen of live place above the sheet, not a sliver: "
                + (cardTop / PONG_DENSITY) + "dp",
            cardTop >= pongPx(150f));
    }

    @Test
    public void theSheetPullsUpToTheRoomUnderTheTopInset() {
        int rest = LayoutEditorPlan.restingCardHeightPx(PONG_WIDTH_PX, PONG_HEIGHT_PX,
            pongExpanded());
        int expanded = pongExpanded();
        assertEquals("all the way up is the room the host gave, exactly",
            PONG_HEIGHT_PX - pongPx(24f) - pongPx(18f), expanded);
        assertTrue("the pull is worth having on a portrait screen: over an inch of list",
            expanded - rest >= pongPx(96f));
        assertEquals("a host with less room than the resting card leaves the card that tall",
            100, LayoutEditorPlan.restingCardHeightPx(PONG_WIDTH_PX, PONG_HEIGHT_PX, 100));
        assertEquals("and no room is no room", 0, LayoutEditorPlan.expandedCardBudgetPx(-40));
    }

    @Test
    public void aLandscapeScreenHasNoHeightToGiveAwayAndKeepsTheWholeRoom() {
        assertEquals("a portrait screen keeps a fifth of itself for the place behind the sheet",
            Math.round(0.80f * 2400), LayoutEditorPlan.restingCardHeightPx(1080, 2400, 2300));
        assertEquals("a landscape screen is already the short edge, so the card rests at the "
                + "room it has and a pull has no travel",
            1000, LayoutEditorPlan.restingCardHeightPx(2400, 1080, 1000));
        assertEquals(1080, LayoutEditorPlan.cardBudgetPx(2400, 1080));
    }

    // ------------------------------------------------------------------------- the pane sizing

    @Test
    public void aMiniatureInItsOwnPaneTakesWhicheverOfTheTwoRunsOutFirst() {
        float aspect = PlaceMiniatureView.frameAspect(PORTRAIT);
        int reserved = 54;
        // A tall pane: the width runs out first, so the frame is as wide as the pane.
        int wide = LayoutEditorPlan.miniatureHeightInPanePx(aspect, reserved, 200, 4000);
        assertEquals(Math.round(200 / aspect) + reserved, wide);
        // A short pane: the height runs out first, and the frame never overflows it.
        int shortPane = LayoutEditorPlan.miniatureHeightInPanePx(aspect, reserved, 200, 300);
        assertEquals(300, shortPane);
        assertTrue(shortPane < wide);
    }

    @Test
    public void thePaneFrameNeverExceedsThePaneAtAnyReferenceSize() {
        for (float density : new float[]{180f / 160f, 260f / 160f}) {
            float aspect = PlaceMiniatureView.frameAspect(PORTRAIT);
            int reserved = Math.round(density * 48f);
            for (int paneWidth = 100; paneWidth <= 900; paneWidth += 13) {
                for (int paneHeight = 120; paneHeight <= 900; paneHeight += 31) {
                    int height = LayoutEditorPlan.miniatureHeightInPanePx(aspect, reserved,
                        paneWidth, paneHeight);
                    assertTrue(paneWidth + "x" + paneHeight + " overflowed its pane",
                        height <= Math.max(paneHeight, reserved));
                    assertTrue("and still leaves a frame", height >= reserved);
                }
            }
        }
    }

    @Test
    public void anEditorOpenedInMinimalModeEditsTheMinimalLayout() {
        places.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        places.setMinimal(true);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        assertEquals(LayoutVariant.MINIMAL, plan.variant());
        // The miniature draws the minimal layout: the seed, with everything put away.
        assertTrue(plan.shownLayout().slot(com.termux.app.place.Element.APPS).hidden);
        assertFalse(plan.isDirty());

        places.forVariant(LayoutVariant.MINIMAL).setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        assertTrue(plan.isDirty());
        assertFalse(plan.shownLayout().slot(com.termux.app.place.Element.APPS).hidden);
        // Toggling minimal mode under the open editor does not move what it edits.
        places.setMinimal(false);
        assertFalse(plan.shownLayout().slot(com.termux.app.place.Element.APPS).hidden);

        plan.revert();
        assertFalse(plan.isDirty());
        assertTrue(plan.shownLayout().slot(com.termux.app.place.Element.APPS).hidden);
        // The normal layout was never in it.
        assertFalse(places.forVariant(LayoutVariant.NORMAL).resolve(PORTRAIT)
            .slot(com.termux.app.place.Element.APPS).hidden);
    }

    @Test
    public void anEditorOpenedOutsideMinimalModeEditsTheNormalLayoutAndLeavesMinimalBe() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        places.forVariant(LayoutVariant.MINIMAL).setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        assertFalse("the minimal layout is not this session's to lose", plan.isDirty());
    }
}
