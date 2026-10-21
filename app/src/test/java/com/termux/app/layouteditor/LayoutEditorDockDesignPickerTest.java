package com.termux.app.layouteditor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.LayoutCanvasView.Block;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
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
 * The Dock design picker shows wherever the dock is selected (#93): the pinned apps row, or the
 * A–Z row on a dock that shows the index alone; never for a block that is not the dock's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorDockDesignPickerTest {

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-dock-design-picker-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    @Test
    public void thePickerShowsForEitherRowOfTheDockOnEveryPage() {
        for (PaneWallPage page : PaneWallPage.values()) {
            LayoutEditorPlan plan = LayoutEditorPlan.enter(places, page, PlaceOrientation.PORTRAIT);
            assertTrue(page.name(), LayoutEditorController.showsDockStyles(plan, Block.APPS_ROW));
            // Beside the apps the A-Z row keeps its own tools.
            assertFalse(page.name(),
                LayoutEditorController.showsDockStyles(plan, Block.ALPHABETS_ROW));
            // A dock of the A-Z row alone: that row is the dock.
            // Dropped in the tray (a null edge): the apps row is put away.
            plan.drop(MiniatureDragPolicy.Bar.APPS_ROW, null);
            assertTrue(page.name() + ": a dock of the A-Z row alone",
                LayoutEditorController.showsDockStyles(plan, Block.ALPHABETS_ROW));
            plan.revert();
            for (Block other : new Block[] {Block.STATUS_BAR, Block.EXTRA_KEYS, Block.CANVAS,
                    Block.KEYBOARD}) {
                assertFalse(page.name() + " " + other,
                    LayoutEditorController.showsDockStyles(plan, other));
            }
            assertFalse(LayoutEditorController.showsDockStyles(plan, null));
        }
        assertFalse(LayoutEditorController.showsDockStyles(null, Block.APPS_ROW));
    }
}
