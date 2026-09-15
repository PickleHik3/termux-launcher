package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
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
 * The Layout editor's decisions, without a window: which orientation the miniature shows and which
 * one a drop writes, what a drop does to the live place, when the session is dirty, and what the
 * revert puts back.
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

        // A row has no top position, and the status bar is never hidden.
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.drop(Bar.APPS_ROW, Edge.TOP));
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.drop(Bar.STATUS_BAR, null));
        assertNull(prefs.getString("place.terminal.portrait.apps_row", null));
        assertNull(prefs.getString("place.terminal.portrait.status_bar", null));
        assertFalse("nothing was written, so there is nothing to lose", plan.isDirty());
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
