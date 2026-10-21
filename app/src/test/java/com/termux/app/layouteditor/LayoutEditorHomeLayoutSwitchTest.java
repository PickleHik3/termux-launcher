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
 * The Layout editor's Home layout switch ("One layout for every screen" / "Home has its own
 * layout"): it shows the stored state, and turning it moves the session's Home page between the
 * shared layout and Home's own copy without the editor closing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorHomeLayoutSwitchTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-home-switch-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
        places.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
    }

    private LayoutEditorPlan open(PaneWallPage place) {
        return LayoutEditorPlan.enter(places, place, PORTRAIT, (List<PlaceArrangeSnapshot>) null);
    }

    @Test
    public void showsTheStoredState() {
        assertFalse(open(PaneWallPage.WIDGETS).hasHomeLayout());
        places.setHomeLayout(true);
        assertTrue(open(PaneWallPage.WIDGETS).hasHomeLayout());
    }

    @Test
    public void turnedOnAHomePageEditsHomesOwnLayoutOnly() {
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        plan.setHomeLayout(true);
        assertTrue(places.hasHomeLayout());
        assertEquals(LayoutVariant.HOME, plan.variant());

        plan.drop(Bar.APPS_ROW, Edge.TOP);
        assertEquals(Edge.TOP, places.forVariant(LayoutVariant.HOME)
            .slot(PORTRAIT, Element.APPS).edge);
        assertEquals(Edge.BOTTOM, places.forVariant(LayoutVariant.NORMAL)
            .slot(PORTRAIT, Element.APPS).edge);
    }

    @Test
    public void turnedOffHomeGoesBackToTheSharedLayout() {
        places.setHomeLayout(true);
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        assertEquals(LayoutVariant.HOME, plan.variant());
        plan.setHomeLayout(false);
        assertFalse(places.hasHomeLayout());
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        assertEquals(PaneWallPage.WIDGETS, plan.place());

        plan.drop(Bar.APPS_ROW, Edge.TOP);
        assertEquals(Edge.TOP, places.forVariant(LayoutVariant.NORMAL)
            .slot(PORTRAIT, Element.APPS).edge);
    }

    @Test
    public void aWindowPageKeepsTheWorkspaceLayoutEitherWay() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        plan.setHomeLayout(true);
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        plan.setHomeLayout(false);
        assertEquals(LayoutVariant.NORMAL, plan.variant());
    }
}
