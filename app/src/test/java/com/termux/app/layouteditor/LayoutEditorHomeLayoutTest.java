package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.fragments.settings.LayoutChooserModel;
import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

/**
 * The Layout editor opened on a Home page edits the Home layout and offers only its elements —
 * the pinned apps, the A–Z index and the keyboard, whose chip is always on — while opened on a
 * window it edits the Workspace layout with the full set, as before (ADR 0008).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorHomeLayoutTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private TermuxAppSharedPreferences launcher;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-home-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
        places = new PlaceLayoutStore(launcher);
    }

    @Test
    public void openedOnAHomePageItEditsTheHomeLayout() {
        assertEquals(LayoutVariant.HOME,
            LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT).variant());
        assertEquals(LayoutVariant.NORMAL,
            LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT).variant());
        places.setMinimal(true);
        assertEquals("minimal mode is the Workspace's", LayoutVariant.HOME,
            LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT).variant());
        assertEquals(LayoutVariant.MINIMAL,
            LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT).variant());
    }

    @Test
    public void theHomeTrayHoldsOnlyTheDockRowsAndTheKeyboard() {
        places.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        places.setAzRowShown(PORTRAIT, true);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        // Fresh from its seed: the status bar and the extra keys are put away, and so is the
        // keyboard, but only the keyboard is the Home layout's to bring back.
        assertEquals(Arrays.asList(LayoutEditorPlan.TrayItem.KEYBOARD), plan.trayItems());

        plan.drop(Bar.APPS_ROW, null);
        plan.drop(Bar.AZ_INDEX, null);
        List<LayoutEditorPlan.TrayItem> tray = plan.trayItems();
        assertEquals(Arrays.asList(LayoutEditorPlan.TrayItem.PINNED_APPS,
            LayoutEditorPlan.TrayItem.AZ_INDEX, LayoutEditorPlan.TrayItem.KEYBOARD), tray);
        assertEquals(LayoutEditorPlan.Drop.NONE,
            plan.restore(LayoutEditorPlan.TrayItem.STATUS_BAR));
        assertEquals(LayoutEditorPlan.Drop.NONE,
            plan.restore(LayoutEditorPlan.TrayItem.EXTRA_KEYS));
        assertTrue(plan.shownLayout().slot(Element.STATUS).hidden);
    }

    @Test
    public void aWindowsTrayStillOffersTheFullSet() {
        places.setStatusBarEdge(PORTRAIT, Edge.TOP);
        places.setExtraKeys(PORTRAIT, RowPlacement.BOTTOM);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.drop(Bar.STATUS_BAR, null);
        plan.drop(Bar.EXTRA_KEYS, null);
        assertTrue(plan.trayItems().contains(LayoutEditorPlan.TrayItem.STATUS_BAR));
        assertTrue(plan.trayItems().contains(LayoutEditorPlan.TrayItem.EXTRA_KEYS));
        assertEquals(LayoutEditorPlan.Drop.LIVE,
            plan.restore(LayoutEditorPlan.TrayItem.STATUS_BAR));
        assertFalse(plan.shownLayout().slot(Element.STATUS).hidden);
    }

    @Test
    public void theStatusBarAndTheExtraKeysStandNowhereOnTheHomeLayout() {
        PlaceLayoutStore home = places.forVariant(LayoutVariant.HOME);
        assertFalse(LayoutChooserModel.applyDrop(home, PORTRAIT, Bar.STATUS_BAR, Edge.TOP));
        assertFalse(LayoutChooserModel.applyDrop(home, PORTRAIT, Bar.EXTRA_KEYS, Edge.BOTTOM));
        assertTrue(LayoutChooserModel.applyDrop(home, PORTRAIT, Bar.APPS_ROW, Edge.TOP));
        assertEquals(Edge.TOP, home.resolve(PORTRAIT).slot(Element.APPS).edge);
        assertTrue("a window's layout still takes them",
            LayoutChooserModel.applyDrop(places, PORTRAIT, Bar.STATUS_BAR, Edge.BOTTOM));
    }

    @Test
    public void theHomeKeyboardChipIsAlwaysOn() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        assertFalse(places.isHomeKeyboardAlwaysOn());
        assertEquals(LayoutEditorPlan.Drop.LIVE,
            plan.restore(LayoutEditorPlan.TrayItem.KEYBOARD));
        assertTrue(places.isHomeKeyboardAlwaysOn());
        assertFalse("the Workspace keyboard switch is untouched", launcher.isKeyboardTurnedOff());
        assertTrue(plan.isDirty());

        plan.revert();
        assertFalse("Discard puts always on away again", places.isHomeKeyboardAlwaysOn());
        assertFalse(plan.isDirty());
    }

    /**
     * Always on turned on and the editor opened again on Home: the keyboard is no longer in the
     * tray because it stands on the canvas (the shown layout carries it), and putting it away
     * brings its chip back and turns always on off.
     */
    @Test
    public void reopenedWithAlwaysOnTheKeyboardStandsOnTheCanvasAndCanBePutAway() {
        LayoutEditorPlan first = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        first.restore(LayoutEditorPlan.TrayItem.KEYBOARD);
        assertTrue(places.isHomeKeyboardAlwaysOn());

        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        assertTrue("the canvas is handed the keyboard", plan.shownLayout().keyboardShown);
        assertFalse(plan.trayItems().contains(LayoutEditorPlan.TrayItem.KEYBOARD));

        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.setKeyboardShown(false));
        assertFalse(places.isHomeKeyboardAlwaysOn());
        assertTrue(plan.trayItems().contains(LayoutEditorPlan.TrayItem.KEYBOARD));
        assertFalse(plan.shownLayout().keyboardShown);
    }

    @Test
    public void editsOnHomeLeaveTheWorkspaceLayoutAlone() {
        places.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        plan.drop(Bar.APPS_ROW, Edge.TOP);
        assertEquals(Edge.TOP, plan.shownLayout().slot(Element.APPS).edge);
        Slot workspace = places.forVariant(LayoutVariant.NORMAL).slot(PORTRAIT, Element.APPS);
        assertEquals(Edge.BOTTOM, workspace.edge);
    }

    /** An install that never picked one, or holds an id this build cannot read, is the glass tray. */
    @Test
    public void anAbsentOrUnknownDockStyleReadsAsTheGlassTray() {
        assertEquals(DockStyle.GLASS_TRAY, places.dockStyle());
        prefs().edit().putString("layout.home.dock_style", "ask_bar").commit();
        assertEquals(DockStyle.GLASS_TRAY, places.dockStyle());
        assertEquals(DockStyle.GLASS_TRAY,
            places.resolve(PORTRAIT, LayoutVariant.HOME).dockStyle);
    }

    @Test
    public void theDockStyleIsHomesOneValueForBothOrientations() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        assertTrue(plan.offersDockStyle());
        plan.showOrientation(PlaceOrientation.LANDSCAPE);
        assertEquals("live whichever orientation the toggle shows", LayoutEditorPlan.Drop.LIVE,
            plan.setDockStyle(DockStyle.TASKBAR));
        assertEquals("taskbar", prefs().getString("layout.home.dock_style", null));
        assertEquals(DockStyle.TASKBAR, places.dockStyle());
        assertEquals(DockStyle.TASKBAR, places.forVariant(LayoutVariant.NORMAL).dockStyle());
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.setDockStyle(DockStyle.TASKBAR));
    }

    /** The Taskbar's Home tile starts at the row's start and is stored next to the dock style. */
    @Test
    public void theTaskbarHomeTileIsHomesOneStoredPosition() {
        assertEquals(com.termux.app.dock.TaskbarHomeTilePolicy.Position.START,
            places.taskbarHomeTile());
        places.forVariant(LayoutVariant.NORMAL).setTaskbarHomeTile(
            com.termux.app.dock.TaskbarHomeTilePolicy.Position.END);
        assertEquals("end", prefs().getString("layout.home.taskbar_home_tile", null));
        assertEquals(com.termux.app.dock.TaskbarHomeTilePolicy.Position.END,
            places.taskbarHomeTile());
        prefs().edit().putString("layout.home.taskbar_home_tile", "sideways").commit();
        assertEquals(com.termux.app.dock.TaskbarHomeTilePolicy.Position.START,
            places.taskbarHomeTile());
    }

    /**
     * The Home layout carries the style the dock draws: one picked before it is built resolves to
     * the glass tray, so the dock it reads never goes blank.
     */
    @Test
    public void theHomeLayoutCarriesTheStyleTheDockDraws() {
        places.forVariant(LayoutVariant.HOME).setDockStyle(DockStyle.WALL_STRIP);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertEquals(DockStyle.effectiveStyle(DockStyle.WALL_STRIP),
                places.resolve(orientation, LayoutVariant.HOME).dockStyle);
            assertEquals("the Workspace's dock has no style", DockStyle.GLASS_TRAY,
                places.resolve(orientation, LayoutVariant.NORMAL).dockStyle);
        }
    }

    @Test
    public void theWorkspaceLayoutOffersNoDockStyle() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        assertFalse(plan.offersDockStyle());
        assertEquals(LayoutEditorPlan.Drop.NONE, plan.setDockStyle(DockStyle.LENS_BEADS));
        assertFalse(prefs().contains("layout.home.dock_style"));
        assertFalse(plan.isDirty());
    }

    @Test
    public void aPickedDockStyleIsUnsavedAndDiscardPutsItBack() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        plan.setDockStyle(DockStyle.PROMPT_LINE);
        assertTrue(plan.isDirty());

        plan.revert();
        assertEquals(DockStyle.GLASS_TRAY, places.dockStyle());
        assertFalse(plan.isDirty());
    }

    @Test
    public void theHomeFilterCarriesTheDockStyleChoice() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        plan.showHome(true);
        assertTrue(plan.offersDockStyle());
        plan.setDockStyle(DockStyle.LENS_BEADS);
        plan.showHome(false);
        assertFalse(plan.offersDockStyle());
        assertTrue("the Home layout's pick is still unsaved", plan.isDirty());
        plan.revert();
        assertEquals(DockStyle.GLASS_TRAY, places.dockStyle());
    }

    private SharedPreferences prefs() {
        return RuntimeEnvironment.getApplication()
            .getSharedPreferences("layout-editor-home-test", Context.MODE_PRIVATE);
    }
}
