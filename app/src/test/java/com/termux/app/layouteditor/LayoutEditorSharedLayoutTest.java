package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
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
 * The Layout editor opened on a Home page while Home wears the one shared layout (ADR 0009, the
 * Home layout off): it edits the Workspace's layout, normal or minimal, so a move there lands on
 * every window too, while offering only what Home wears. Home's keyboard chip is still always on,
 * and the design picked there is the shared layout's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorSharedLayoutTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private TermuxAppSharedPreferences launcher;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-shared-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
        places = new PlaceLayoutStore(launcher);
        // Home on the shared layout: what a store before version 14 held, which the plan still
        // handles until Layout mode is retired.
        com.termux.app.place.KindFixtures.onHomePage(places);
        places.setStatusBarEdge(PORTRAIT, Edge.TOP);
        places.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        places.setExtraKeys(PORTRAIT, RowPlacement.BOTTOM);
    }

    private LayoutEditorPlan onHome() {
        return LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
    }

    @Test
    public void theDocksHeightOnHomeIsTheSharedOne() {
        LayoutEditorPlan plan = onHome();
        float target = places.dockHeightScale(PORTRAIT) > 1f ? 0.9f : 1.2f;
        plan.setDockHeightScale(target);
        assertEquals(target, places.forVariant(LayoutVariant.NORMAL).dockHeightScale(PORTRAIT),
            0.001f);
    }

    @Test
    public void homesKeyboardChipIsStillAlwaysOn() {
        LayoutEditorPlan plan = onHome();
        assertTrue(plan.trayItems().contains(LayoutEditorPlan.TrayItem.KEYBOARD));
        assertEquals(LayoutEditorPlan.Drop.LIVE,
            plan.restore(LayoutEditorPlan.TrayItem.KEYBOARD));
        assertTrue(places.isHomeKeyboardAlwaysOn());
        assertTrue(plan.shownLayout().keyboardShown);
        assertFalse("the Workspace keyboard switch is untouched", launcher.isKeyboardTurnedOff());
        assertTrue(plan.isDirty());

        plan.revert();
        assertFalse("Discard puts always on away again", places.isHomeKeyboardAlwaysOn());
        assertFalse(plan.isDirty());
    }

}
