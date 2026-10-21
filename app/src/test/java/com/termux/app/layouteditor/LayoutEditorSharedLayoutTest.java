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
        places.setStatusBarEdge(PORTRAIT, Edge.TOP);
        places.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        places.setExtraKeys(PORTRAIT, RowPlacement.BOTTOM);
    }

    private LayoutEditorPlan onHome() {
        return LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
    }

    @Test
    public void openedOnHomeItEditsTheSharedLayout() {
        assertEquals(LayoutVariant.NORMAL, onHome().variant());
        places.setMinimal(true);
        assertEquals("Home ignores minimal", LayoutVariant.NORMAL, onHome().variant());
    }

    @Test
    public void aMoveOnHomeLandsOnEveryWindow() {
        LayoutEditorPlan plan = onHome();
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.APPS_ROW, Edge.TOP));
        assertEquals(Edge.TOP, plan.shownLayout().slot(Element.APPS).edge);
        assertEquals(Edge.TOP,
            places.resolve(PORTRAIT, PaneWallPage.TERMINAL).slot(Element.APPS).edge);
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
    public void homeIsDrawnAndOfferedWithTheBarAndWithoutTheExtraKeys() {
        LayoutEditorPlan plan = onHome();
        assertTrue(plan.shownLayout().slot(Element.EXTRA_KEYS).hidden);
        assertFalse("Home wears the bar (#90)", plan.shownLayout().slot(Element.STATUS).hidden);
        assertFalse("nothing Home does not wear is in its tray",
            plan.trayItems().contains(LayoutEditorPlan.TrayItem.EXTRA_KEYS));
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.drop(Bar.EXTRA_KEYS, Edge.BOTTOM));
        // The bar moved on Home moves on every window: one layout.
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.drop(Bar.STATUS_BAR, Edge.BOTTOM));
        assertEquals(Edge.BOTTOM,
            places.resolve(PORTRAIT, PaneWallPage.TERMINAL).slot(Element.STATUS).edge);
        assertFalse("the windows keep them",
            places.resolve(PORTRAIT, PaneWallPage.TERMINAL).slot(Element.EXTRA_KEYS).hidden);
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

    @Test
    public void theDesignPickedOnHomeIsTheSharedLayouts() {
        LayoutEditorPlan plan = onHome();
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.setDockStyle(DockStyle.LENS_BEADS));
        assertEquals(DockStyle.LENS_BEADS, places.forVariant(LayoutVariant.NORMAL).dockStyle());
        assertEquals(DockStyle.LENS_BEADS, plan.shownLayout().dockStyle);
        assertEquals("the windows' dock wears it too", DockStyle.LENS_BEADS,
            places.resolve(PORTRAIT, PaneWallPage.TERMINAL).dockStyle);

        plan.revert();
        assertEquals(DockStyle.GLASS_TRAY, places.forVariant(LayoutVariant.NORMAL).dockStyle());
    }

    @Test
    public void theHomeFilterStaysOnTheOneLayout() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.showHome(true);
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        assertTrue(plan.shownLayout().slot(Element.EXTRA_KEYS).hidden);
        plan.showHome(false);
        assertFalse(plan.shownLayout().slot(Element.EXTRA_KEYS).hidden);
        assertEquals(1, plan.entries().size());
    }
}
