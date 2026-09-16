package com.termux.app.layouteditor;

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
import com.termux.app.place.KeyboardOnEnter;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.PlaceArrangeModel;
import com.termux.app.place.PlaceArrangeModel.Element;
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
 * The Layout editor's decisions, without a window: which orientation the miniature shows and which
 * one a drop writes, which rows stand beneath it on each place, what a drop or a pick does to the
 * live place, when the session is dirty, and what the revert puts back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorPlanTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

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

    /** Every row's label, in the order the card stands them in. */
    private static List<String> labels(LayoutEditorPlan plan) {
        List<String> names = new ArrayList<>();
        for (LayoutEditorPlan.Row row : plan.rows())
            names.add(RuntimeEnvironment.getApplication().getString(row.group.labelRes));
        return names;
    }

    /** The one row a label names, so a test can pick on it. */
    private static LayoutEditorPlan.Row row(LayoutEditorPlan plan, String label) {
        for (LayoutEditorPlan.Row row : plan.rows()) {
            if (label.equals(RuntimeEnvironment.getApplication().getString(row.group.labelRes)))
                return row;
        }
        throw new AssertionError("no row labelled " + label + " in " + labels(plan));
    }

    /** The one row a label names under one heading: two headings both offer a Height. */
    private static LayoutEditorPlan.Row row(LayoutEditorPlan plan, Element element, String label) {
        for (LayoutEditorPlan.Row row : plan.rows()) {
            if (row.element == element
                && label.equals(RuntimeEnvironment.getApplication().getString(row.group.labelRes)))
                return row;
        }
        throw new AssertionError("no " + element + " row labelled " + label + " in " + labels(plan));
    }

    /** The size row under one heading, for a test to drag. */
    private static PlaceArrangeModel.Size size(LayoutEditorPlan plan, Element element,
                                               String label) {
        LayoutEditorPlan.Row row = row(plan, element, label);
        assertTrue(label + " is a size row", row.group instanceof PlaceArrangeModel.Size);
        return (PlaceArrangeModel.Size) row.group;
    }

    private static void pick(LayoutEditorPlan plan, String label, String value) {
        LayoutEditorPlan.Row row = row(plan, label);
        assertTrue(label + " is a pill row", row.group instanceof PlaceArrangeModel.Pills);
        ((PlaceArrangeModel.Pills) row.group).writer.write(value);
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
        assertEquals(places.resolve(PaneWallPage.TERMINAL, LANDSCAPE), plan.shownLayout());
    }

    @Test
    public void aDropInTheShownOrientationWritesThatOrientationAlone() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.STATUS_BAR, Edge.BOTTOM));
        assertEquals("bottom", prefs.getString("place.terminal.portrait.status_bar", null));
        assertNull("landscape untouched",
            prefs.getString("place.terminal.landscape.status_bar", null));

        plan.showOrientation(LANDSCAPE);
        assertEquals("the phone is still in portrait, so only the miniature moves",
            LayoutEditorPlan.Drop.MINIATURE, plan.drop(Bar.APPS_ROW, Edge.RIGHT));
        assertEquals("right", prefs.getString("place.terminal.landscape.apps_row", null));
        assertNull("portrait untouched", prefs.getString("place.terminal.portrait.apps_row", null));
    }

    @Test
    public void aDropInTheTrayHidesTheBarForTheShownOrientation() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.EXTRA_KEYS, null));
        assertEquals("hidden", prefs.getString("place.terminal.portrait.extra_keys", null));
        assertEquals(RowPlacement.HIDDEN, places.extraKeys(PaneWallPage.TERMINAL, PORTRAIT));
    }

    @Test
    public void aBarDroppedWhereItCannotStandWritesNothing() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        // Every bar stands on every edge now; the status bar never hides, and that is the one
        // drop left that cannot be made.
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.drop(Bar.STATUS_BAR, null));
        assertNull(prefs.getString("place.terminal.portrait.status_bar", null));
        assertFalse("nothing was written, so there is nothing to lose", plan.isDirty());

        assertEquals("a row on the top edge is a placement like any other",
            LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.APPS_ROW, Edge.TOP));
        assertEquals("top", prefs.getString("place.terminal.portrait.apps_row", null));
    }

    /** What stands along the bottom of the Terminal in portrait, outermost first. */
    private List<String> bottomStack() {
        List<String> names = new ArrayList<>();
        for (com.termux.app.place.Element element : EdgeStackPolicy.stack(
            places.resolve(PaneWallPage.TERMINAL, PORTRAIT), Edge.BOTTOM))
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
        assertEquals("left", prefs.getString("place.terminal.landscape.status_bar", null));
    }

    @Test
    public void aSessionIsDirtyOnceABarHasMovedAndCleanAgainAfterTheRevert() {
        places.setStatusBarEdge(PaneWallPage.TERMINAL, PORTRAIT, Edge.TOP);
        places.setAppsRow(PaneWallPage.TERMINAL, LANDSCAPE, RowPlacement.BOTTOM);
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertFalse("nothing moved yet", plan.isDirty());

        plan.drop(Bar.STATUS_BAR, Edge.BOTTOM);
        assertTrue(plan.isDirty());

        plan.showOrientation(LANDSCAPE);
        plan.drop(Bar.APPS_ROW, Edge.LEFT);
        assertTrue(plan.isDirty());

        plan.revert();
        assertFalse("every bar is back where the editor found it", plan.isDirty());
        assertEquals(Edge.TOP, places.statusBarEdge(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(RowPlacement.BOTTOM, places.appsRow(PaneWallPage.TERMINAL, LANDSCAPE));
    }

    @Test
    public void aDropThatChangesNothingIsNotAnUnsavedChange() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        Edge resting = places.statusBarEdge(PaneWallPage.TERMINAL, PORTRAIT);

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
        assertEquals("bottom", prefs.getString("place.home.portrait.status_bar", null));

        plan.revert();
        assertFalse(plan.isDirty());
        assertEquals("the place the editor opened on is back too",
            RowPlacement.BOTTOM, places.extraKeys(PaneWallPage.TERMINAL, PORTRAIT));
    }

    // ------------------------------------------------------------ the rows beneath the miniature

    @Test
    public void homeOffersTheKeyboardsFormAndOnEnterAndTheGridsTwoCounts() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);

        assertEquals(labels(plan), Arrays.asList("Height", "Type", "On enter", "Height",
            "Bottom padding", "Grid columns", "Grid rows"));
        assertTrue("the grid's counts are counters",
            row(plan, "Grid columns").group instanceof PlaceArrangeModel.Counter);
    }

    @Test
    public void theTerminalHasNoGridToCountAndNoKeyboardModeToPick() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        assertEquals(labels(plan),
            Arrays.asList("Height", "Type", "On enter", "Height", "Bottom padding"));
    }

    @Test
    public void onlyTheDisplayOffersTheKeyboardMode() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.DISPLAY, PORTRAIT);

        assertEquals(labels(plan), Arrays.asList("Height", "Type", "On enter", "Keyboard mode",
            "Height", "Bottom padding"));
    }

    @Test
    public void aPickOnARowWritesTheOrientationOnTheToggleAlone() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        pick(plan, "Type", "floating");
        assertEquals(KeyboardForm.FLOATING, places.keyboardForm(PaneWallPage.TERMINAL, PORTRAIT));
        assertNull("landscape untouched",
            prefs.getString("place.terminal.landscape.keyboard_form", null));

        plan.showOrientation(LANDSCAPE);
        assertEquals("the row re-reads itself for the orientation now shown",
            "docked", ((PlaceArrangeModel.Pills) row(plan, "Type").group).selected);
        pick(plan, "Type", "split");
        assertEquals(KeyboardForm.SPLIT, places.keyboardForm(PaneWallPage.TERMINAL, LANDSCAPE));
        assertEquals("portrait keeps what it was given",
            KeyboardForm.FLOATING, places.keyboardForm(PaneWallPage.TERMINAL, PORTRAIT));
    }

    @Test
    public void theOnEnterRowIsThePlacesOwnAndNotAnOrientationsOf() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        pick(plan, "On enter", "closed");
        assertEquals(KeyboardOnEnter.CLOSED, places.keyboardOnEnter(PaneWallPage.TERMINAL));
        assertEquals("one key for the place, not one per orientation",
            "closed", prefs.getString("place.terminal.keyboard_on_enter", null));

        plan.showOrientation(LANDSCAPE);
        assertEquals("so the other orientation shows the same answer",
            "closed", ((PlaceArrangeModel.Pills) row(plan, "On enter").group).selected);
    }

    @Test
    public void theDisplaysKeyboardModeIsPerOrientationLikeItsForm() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.DISPLAY, LANDSCAPE);

        pick(plan, "Keyboard mode", "overlay");
        assertEquals(KeyboardMode.OVERLAY, places.keyboardMode(PaneWallPage.DISPLAY, LANDSCAPE));
        assertNull("portrait untouched",
            prefs.getString("place.display.portrait.keyboard_mode", null));
    }

    @Test
    public void aGridCountWritesTheOrientationOnTheToggleAlone() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, LANDSCAPE);

        ((PlaceArrangeModel.Counter) row(plan, "Grid columns").group).writer.write(6);
        assertEquals(6, places.widgetColumns(PaneWallPage.WIDGETS, LANDSCAPE));
        assertNull("portrait untouched",
            prefs.getString("place.home.portrait.widget_columns", null));
    }

    @Test
    public void aRowPickIsAnUnsavedChangeAndTheRevertPutsItBack() {
        places.setKeyboardForm(PaneWallPage.TERMINAL, PORTRAIT, KeyboardForm.DOCKED);
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertFalse("nothing picked yet", plan.isDirty());

        pick(plan, "Type", "split");
        assertTrue("a row is as unsaved as a moved bar", plan.isDirty());

        pick(plan, "On enter", "open");
        assertTrue(plan.isDirty());

        plan.revert();
        assertFalse(plan.isDirty());
        assertEquals(KeyboardForm.DOCKED, places.keyboardForm(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(KeyboardOnEnter.AS_LEFT, places.keyboardOnEnter(PaneWallPage.TERMINAL));
    }

    @Test
    public void theRowsFollowTheSecondDoorToItsPlace() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertEquals(5, plan.rows().size());

        plan.showPlace(PaneWallPage.WIDGETS);
        assertEquals("home's grid counts join the card", 7, plan.rows().size());
    }


    // ----------------------------------------------------------------------- the three sizes

    @Test
    public void everyPlaceOffersTheDocksHeightAndTheKeyboardsHeightAndChin() {
        for (PaneWallPage place : PaneWallPage.values()) {
            LayoutEditorPlan plan = LayoutEditorPlan.enter(places, place, PORTRAIT);

            PlaceArrangeModel.Size dock = size(plan, Element.PINNED_APPS, "Height");
            assertEquals("a scale is a hundred steps of its own range",
                100, dock.max - dock.min);
            assertEquals(PlaceArrangeModel.Unit.PERCENT, dock.unit);

            assertEquals(PlaceArrangeModel.Unit.PERCENT,
                size(plan, Element.KEYBOARD, "Height").unit);

            PlaceArrangeModel.Size chin = size(plan, Element.KEYBOARD, "Bottom padding");
            assertEquals("the chin is counted in dp, from nothing to its ceiling",
                PlaceArrangeModel.Unit.DP, chin.unit);
            assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING, chin.min);
            assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING, chin.max);
        }
    }

    @Test
    public void aSizeDragWritesTheOrientationOnTheToggleAlone() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        size(plan, Element.KEYBOARD, "Height").writer.write(100);
        assertEquals("the track's top is the scale's ceiling",
            TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
            places.keyboardHeightScale(PaneWallPage.TERMINAL, PORTRAIT), 0.001f);
        assertNull("landscape untouched",
            prefs.getString("place.terminal.landscape.keyboard_height", null));

        plan.showOrientation(LANDSCAPE);
        assertNotEquals("the row re-reads itself for the orientation now shown",
            100, size(plan, Element.KEYBOARD, "Height").value);
        size(plan, Element.KEYBOARD, "Height").writer.write(50);
        assertNotEquals("the two orientations stand at their own heights",
            places.keyboardHeightScale(PaneWallPage.TERMINAL, PORTRAIT),
            places.keyboardHeightScale(PaneWallPage.TERMINAL, LANDSCAPE), 0.001f);
        assertEquals("portrait keeps what it was given",
            TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
            places.keyboardHeightScale(PaneWallPage.TERMINAL, PORTRAIT), 0.001f);
    }

    @Test
    public void theDocksHeightAndTheChinAreThePlacesAndTheOrientationsToo() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.DISPLAY, LANDSCAPE);

        size(plan, Element.PINNED_APPS, "Height").writer.write(0);
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT,
            places.dockHeightScale(PaneWallPage.DISPLAY, LANDSCAPE), 0.001f);
        assertNull("portrait untouched",
            prefs.getString("place.display.portrait.dock_height", null));

        size(plan, Element.KEYBOARD, "Bottom padding").writer.write(24);
        assertEquals(24, places.keyboardChinDp(PaneWallPage.DISPLAY, LANDSCAPE));
        assertNull("portrait untouched",
            prefs.getString("place.display.portrait.keyboard_chin", null));
        assertEquals("and no other place moved with it", 0,
            places.keyboardChinDp(PaneWallPage.TERMINAL, LANDSCAPE));
    }

    @Test
    public void aSizeThatMovesIsAnUnsavedChangeAndTheRevertPutsAllThreeBack() {
        places.setDockHeightScale(PaneWallPage.TERMINAL, PORTRAIT, 1.5f);
        places.setKeyboardHeightScale(PaneWallPage.TERMINAL, PORTRAIT, 1.2f);
        places.setKeyboardChinDp(PaneWallPage.TERMINAL, PORTRAIT, 12);
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        assertFalse("nothing dragged yet", plan.isDirty());

        size(plan, Element.PINNED_APPS, "Height").writer.write(0);
        assertTrue("a size is as unsaved as a moved bar", plan.isDirty());
        size(plan, Element.KEYBOARD, "Height").writer.write(100);
        size(plan, Element.KEYBOARD, "Bottom padding").writer.write(48);

        plan.revert();
        assertFalse(plan.isDirty());
        assertEquals(1.5f, places.dockHeightScale(PaneWallPage.TERMINAL, PORTRAIT), 0.001f);
        assertEquals(1.2f, places.keyboardHeightScale(PaneWallPage.TERMINAL, PORTRAIT), 0.001f);
        assertEquals(12, places.keyboardChinDp(PaneWallPage.TERMINAL, PORTRAIT));
    }

    @Test
    public void aSizeDraggedBackToWhereItStoodIsNotAnUnsavedChange() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();
        int resting = size(plan, Element.KEYBOARD, "Bottom padding").value;

        size(plan, Element.KEYBOARD, "Bottom padding").writer.write(resting);
        assertFalse("the thumb landed where it already stood", plan.isDirty());
    }

    @Test
    public void aSizeRowReadsBackTheStepItWasDraggedTo() {
        LayoutEditorPlan plan = enterOnTerminalInPortrait();

        size(plan, Element.PINNED_APPS, "Height").writer.write(37);
        assertEquals("the thumb stays where the finger left it",
            37, size(plan, Element.PINNED_APPS, "Height").value);
    }

    @Test
    public void theRowsSectionKeepsWhateverTheCanvasLeavesAndNeverLessThanItsFloor() {
        assertEquals("the room left under the canvas",
            400, LayoutEditorPlan.rowsHeightCapPx(2400, 1800, 200, 120));
        assertEquals("a canvas that took it all still leaves a list to scroll in",
            120, LayoutEditorPlan.rowsHeightCapPx(2400, 2300, 200, 120));
    }

    @Test
    public void thePortraitCanvasIsAboutHalfTheScreenAndTheLandscapeOneFillsTheWidth() {
        int screenWidth = 1080;
        int screenHeight = 2400;
        int reserved = 60;
        float portraitAspect = 9f / 19.5f;
        float landscapeAspect = 19.5f / 9f;

        int portrait = LayoutEditorPlan.miniatureHeightPx(PORTRAIT, screenWidth, screenHeight,
            portraitAspect, reserved);
        assertEquals("about 55% of the screen, plus the room the tray keeps",
            Math.round(0.55f * screenHeight) + reserved, portrait);

        int landscape = LayoutEditorPlan.miniatureHeightPx(LANDSCAPE, screenWidth, screenHeight,
            landscapeAspect, reserved);
        assertEquals("as tall as a full-width landscape phone is",
            Math.round(screenWidth / landscapeAspect) + reserved, landscape);
        assertTrue("and never taller than the portrait canvas", landscape < portrait);
    }
}
