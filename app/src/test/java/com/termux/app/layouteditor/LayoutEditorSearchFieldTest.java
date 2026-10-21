package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
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
 * The Search field toggle beside the Dock design picker (#96): offered wherever the picker is, on
 * either half, written to the edited layout alone, independent of the design, and put back by
 * Discard like any pick.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorSearchFieldTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-search-field-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    @Test
    public void theToggleIsOfferedWithThePickerOnEitherHalf() {
        for (boolean homeLayout : new boolean[] {false, true}) {
            places.setHomeLayout(homeLayout);
            for (PaneWallPage page : PaneWallPage.values()) {
                LayoutEditorPlan plan = LayoutEditorPlan.enter(places, page, PORTRAIT);
                assertEquals(page.name(), LayoutEditorPlan.Drop.LIVE,
                    plan.setSearchField(!plan.searchField()));
                plan.revert();
            }
        }
    }

    @Test
    public void theToggleWritesTheEditedLayoutAndLeavesTheDesign() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.setDockStyle(DockStyle.LENS_BEADS);
        assertFalse(plan.searchField());

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.setSearchField(true));
        assertTrue(plan.searchField());
        assertTrue(places.forVariant(LayoutVariant.NORMAL).searchField());
        assertEquals(DockStyle.LENS_BEADS, plan.dockStyle());
        assertEquals("the same state again writes nothing", LayoutEditorPlan.Drop.NONE,
            plan.setSearchField(true));

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.setDockStyle(DockStyle.WALL_STRIP));
        assertTrue("a design picked keeps the field", plan.searchField());
    }

    @Test
    public void onAHomeLayoutTheToggleIsHomesOwn() {
        places.setHomeLayout(true);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        plan.setSearchField(true);
        assertTrue(places.forVariant(LayoutVariant.HOME).searchField());
        assertFalse("the Workspace keeps its own",
            places.forVariant(LayoutVariant.NORMAL).searchField());
    }

    @Test
    public void aToggledFieldIsUnsavedAndDiscardPutsItBack() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        assertFalse(plan.isDirty());
        plan.setSearchField(true);
        assertTrue(plan.isDirty());

        plan.revert();
        assertFalse(places.forVariant(LayoutVariant.NORMAL).searchField());
        assertFalse(plan.isDirty());
    }

    @Test
    public void toggledBackItIsCleanAgain() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.setSearchField(true);
        plan.setSearchField(false);
        assertFalse(plan.isDirty());
    }

    @Test
    public void thePickerNeverWritesThePromptLine() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.setDockStyle(DockStyle.PROMPT_LINE));
        assertEquals(DockStyle.GLASS_TRAY, plan.dockStyle());
        assertFalse(plan.isDirty());
    }
}
