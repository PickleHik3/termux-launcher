package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.dock.DrawerTilePolicy;
import com.termux.app.place.LayoutVariant;
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
 * The Drawer tile toggle beside the Dock design picker and the Search field (#97): offered
 * wherever the picker is, on either half, written to the edited layout alone, independent of the
 * design, the field and the tile's place in the row, and put back by Discard like any pick.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorDrawerTileTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-drawer-tile-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    @Test
    public void theToggleIsOfferedWithThePickerOnEitherHalf() {
        for (boolean homeLayout : new boolean[] {false, true}) {
            places.setHomeLayout(homeLayout);
            for (PaneWallPage page : PaneWallPage.values()) {
                LayoutEditorPlan plan = LayoutEditorPlan.enter(places, page, PORTRAIT);
                assertEquals(page.name(), plan.offersDockStyle(), plan.offersDrawerTile());
                assertTrue(page.name(), plan.offersDrawerTile());
            }
        }
    }

    @Test
    public void theToggleWritesTheEditedLayoutAndLeavesTheDesignAndTheField() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.setDockStyle(DockStyle.LENS_BEADS);
        plan.setSearchField(true);
        assertFalse(plan.drawerTile());

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.setDrawerTile(true));
        assertTrue(plan.drawerTile());
        assertTrue(places.forVariant(LayoutVariant.NORMAL).drawerTile());
        assertEquals(DockStyle.LENS_BEADS, plan.dockStyle());
        assertTrue("the field stays", plan.searchField());
        assertEquals("the same state again writes nothing", LayoutEditorPlan.Drop.NONE,
            plan.setDrawerTile(true));

        for (DockStyle style : DockStyle.CHOICES) {
            plan.setDockStyle(style);
            assertTrue("a design picked keeps the tile: " + style, plan.drawerTile());
        }
    }

    @Test
    public void theTileKeepsItsPlaceWhileItIsOff() {
        PlaceLayoutStore normal = places.forVariant(LayoutVariant.NORMAL);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.setDrawerTile(true);
        normal.setDrawerTilePosition(DrawerTilePolicy.Position.MIDDLE);
        plan.setDrawerTile(false);
        plan.setDrawerTile(true);
        assertEquals(DrawerTilePolicy.Position.MIDDLE, normal.drawerTilePosition());
    }

    @Test
    public void onAHomeLayoutTheToggleIsHomesOwn() {
        places.setHomeLayout(true);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        plan.setDrawerTile(true);
        assertTrue(places.forVariant(LayoutVariant.HOME).drawerTile());
        assertFalse("the Workspace keeps its own",
            places.forVariant(LayoutVariant.NORMAL).drawerTile());
    }

    @Test
    public void aToggledTileIsUnsavedAndDiscardPutsItBack() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        assertFalse(plan.isDirty());
        plan.setDrawerTile(true);
        assertTrue(plan.isDirty());

        plan.revert();
        assertFalse(places.forVariant(LayoutVariant.NORMAL).drawerTile());
        assertFalse(plan.isDirty());
    }

    @Test
    public void discardPutsBackATileThatWasOn() {
        places.forVariant(LayoutVariant.NORMAL).setDrawerTile(true);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.setDrawerTile(false);
        assertTrue(plan.isDirty());
        plan.revert();
        assertTrue(places.forVariant(LayoutVariant.NORMAL).drawerTile());
        assertFalse(plan.isDirty());
    }

    @Test
    public void toggledBackItIsCleanAgain() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.setDrawerTile(true);
        plan.setDrawerTile(false);
        assertFalse(plan.isDirty());
    }

    @Test
    public void thePickerNeverWritesTheTaskbar() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.setDockStyle(DockStyle.TASKBAR));
        assertEquals(DockStyle.GLASS_TRAY, plan.dockStyle());
        assertFalse(plan.drawerTile());
        assertFalse(plan.isDirty());
    }
}
