package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceArrangeSnapshot;
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

import java.util.List;

/**
 * The Layout editor's Home filter: beside the orientation toggle, it moves the session between a
 * Home page and its window, and with it the layout every write lands on (ADR 0008); the
 * orientation stays, and Undo and the unsaved question cover both layouts.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorHomeFilterTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-home-filter-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
        places.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        places.setAppsRow(LANDSCAPE, RowPlacement.BOTTOM);
        // The filter moves between two layouts only while Home holds one of its own; on the
        // shared layout see LayoutEditorSharedLayoutTest.
        places.setHomeLayout(true);
    }

    private PlaceLayoutStore home() {
        return places.forVariant(LayoutVariant.HOME);
    }

    private PlaceLayoutStore workspace() {
        return places.forVariant(LayoutVariant.NORMAL);
    }

    /** A session the editor's own door opens: the relaunch-aware entry, with nothing carried. */
    private LayoutEditorPlan open(PaneWallPage place) {
        return LayoutEditorPlan.enter(places, place, PORTRAIT, (List<PlaceArrangeSnapshot>) null);
    }

    @Test
    public void openedOnHomeTheDoorsEntryEditsTheHomeLayout() {
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        assertEquals(LayoutVariant.HOME, plan.variant());
        assertTrue(plan.showsHome());
        assertEquals(LayoutVariant.NORMAL, open(PaneWallPage.TERMINAL).variant());
        assertFalse(open(PaneWallPage.TERMINAL).showsHome());
    }

    @Test
    public void turnedOnItShowsHomeAndWritesTheHomeLayoutOnly() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        assertEquals(PaneWallPage.WIDGETS, plan.showHome(true));
        assertTrue(plan.showsHome());
        assertEquals(PaneWallPage.WIDGETS, plan.place());
        assertEquals(LayoutVariant.HOME, plan.variant());

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.APPS_ROW, Edge.TOP));
        assertEquals(Edge.TOP, home().slot(PORTRAIT, Element.APPS).edge);
        assertEquals("the Workspace's layout is left alone",
            Edge.BOTTOM, workspace().slot(PORTRAIT, Element.APPS).edge);
        // What Home offers (no status bar to place) is its dress's: DressResolverTest.
    }

    @Test
    public void homesSizesAreTheSharedOnes() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        float workspaceDock = workspace().dockHeightScale(PORTRAIT);
        plan.showHome(true);
        float target = workspaceDock > 1f ? 0.9f : 1.2f;
        plan.setDockHeightScale(target);
        assertEquals(target, home().dockHeightScale(PORTRAIT), 0.001f);
        assertEquals("linked (#84): the window's follows", target,
            workspace().dockHeightScale(PORTRAIT), 0.001f);

        plan.setWidgetGrid(3, 4);
        assertEquals(3, plan.shownLayout().widgetColumns);
        assertEquals(4, plan.shownLayout().widgetRows);
    }

    @Test
    public void theOrientationStaysAcrossTheFilter() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        plan.showOrientation(LANDSCAPE);
        plan.showHome(true);
        assertEquals(LANDSCAPE, plan.shownOrientation());
        plan.drop(Bar.APPS_ROW, Edge.TOP);
        assertEquals(Edge.TOP, home().slot(LANDSCAPE, Element.APPS).edge);
        assertEquals(Edge.BOTTOM, home().slot(PORTRAIT, Element.APPS).edge);
        plan.showHome(false);
        assertEquals(LANDSCAPE, plan.shownOrientation());
    }

    @Test
    public void turnedOffItGoesBackToTheWindowItWasOpenedOver() {
        LayoutEditorPlan plan = open(PaneWallPage.DISPLAY);
        plan.showHome(true);
        assertEquals(PaneWallPage.DISPLAY, plan.showHome(false));
        assertEquals(LayoutVariant.NORMAL, plan.variant());

        LayoutEditorPlan fromHome = open(PaneWallPage.WIDGETS);
        assertEquals("opened over Home, off is the terminal",
            PaneWallPage.TERMINAL, fromHome.showHome(false));
        assertFalse(fromHome.showsHome());
    }

    @Test
    public void inMinimalModeOffIsTheMinimalLayout() {
        places.setMinimal(true);
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        plan.showHome(false);
        assertEquals(LayoutVariant.MINIMAL, plan.variant());
    }

    @Test
    public void undoAndTheUnsavedQuestionCoverBothLayouts() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        plan.drop(Bar.APPS_ROW, Edge.TOP);
        plan.showHome(true);
        plan.drop(Bar.APPS_ROW, Edge.TOP);
        plan.showHome(false);
        plan.drop(Bar.APPS_ROW, Edge.BOTTOM);
        assertTrue("Home's move is still unsaved with the window's put back", plan.isDirty());

        plan.revert();
        assertFalse(plan.isDirty());
        assertEquals(Edge.BOTTOM, workspace().slot(PORTRAIT, Element.APPS).edge);
        assertEquals(Edge.BOTTOM, home().slot(PORTRAIT, Element.APPS).edge);
    }

    @Test
    public void aRelaunchCarriesBothLayoutsArrangementsAtOpen() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        plan.drop(Bar.APPS_ROW, Edge.TOP);
        plan.showHome(true);
        plan.drop(Bar.APPS_ROW, Edge.TOP);
        List<PlaceArrangeSnapshot> carried = plan.entries();
        assertEquals(2, carried.size());

        LayoutEditorPlan next = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT,
            carried);
        assertTrue(next.isDirty());
        next.revert();
        assertFalse(next.isDirty());
        assertEquals(Edge.BOTTOM, workspace().slot(PORTRAIT, Element.APPS).edge);
        assertEquals(Edge.BOTTOM, home().slot(PORTRAIT, Element.APPS).edge);
    }
}
