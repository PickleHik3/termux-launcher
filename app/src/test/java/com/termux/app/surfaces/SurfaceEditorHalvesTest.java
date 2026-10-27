package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.cards.ElementRows.Item;
import com.termux.app.surfaces.cards.ElementsList;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * The frame shows the half its half line names: sliding to the Workspace brings the live wall to
 * the Workspace (the Terminal card is then the terminal's tap), sliding back brings Home; and a
 * card's Visible switch reads the state the frame draws.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SurfaceEditorHalvesTest {

    private TermuxAppSharedPreferences preferences;
    private PlaceLayoutStore places;
    private final List<PaneWallPage> wallMoves = new ArrayList<>();
    private boolean keyboardShown = true;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("surface-editor-halves",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        places = new PlaceLayoutStore(preferences);
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
    }

    @Test
    public void slidingTheHalvesMovesTheLiveWallBothWays() {
        SurfaceEditorController editor = open(PaneWallPage.WIDGETS);
        assertEquals(WallHalf.HOME, editor.half());
        wallMoves.clear();

        switchHalf(editor, WallHalf.WORKSPACE);
        assertEquals(WallHalf.WORKSPACE, editor.half());
        assertEquals("the wall slides to the Workspace", PaneWallPage.TERMINAL, last());

        switchHalf(editor, WallHalf.HOME);
        assertEquals(PaneWallPage.WIDGETS, last());
    }

    @Test
    public void theTerminalCardStandsOnTheWorkspace() {
        SurfaceEditorController editor = open(PaneWallPage.WIDGETS);
        switchHalf(editor, WallHalf.WORKSPACE);
        editor.present(EditorCard.TERMINAL, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.TERMINAL, editor.shownCard());
    }

    @Test
    public void theKeyboardsVisibleSwitchReadsWhatTheFrameDraws() {
        keyboardShown = true;
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL);
        editor.present(EditorCard.KEYBOARD, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("the frame draws the keyboard: Visible is on", 1,
            model(editor).value(Row.VISIBLE));

        keyboardShown = false;
        model(editor).onValue(Row.VISIBLE, 0, false);
        assertEquals(0, model(editor).value(Row.VISIBLE));
    }

    @Test
    public void aDockHiddenOnItsCardComesBackFromTheElementsList() {
        LayoutEditorController layout = layoutEditor();
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, layout);
        editor.present(EditorCard.DOCK, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        model(editor).onValue(Row.VISIBLE, 0, false);
        assertFalse(layout.isShown(Element.APPS));

        editor.present(EditorCard.WORKSPACE, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        ElementsList.Host elements = elements(editor);
        assertFalse("the Elements list reads the dock hidden", elements.shown(Item.APPS_ROW));
        elements.setShown(Item.APPS_ROW, true);
        assertTrue("its eye brings the dock back", layout.isShown(Element.APPS));

        editor.present(EditorCard.DOCK, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("the Dock card's eye agrees", 1, model(editor).value(Row.VISIBLE));
    }

    @Test
    public void theElementsListAndTheStatusCardsEyeWriteOneValue() {
        LayoutEditorController layout = layoutEditor();
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, layout);
        assertEquals("the screen's card stands at peek", EditorCard.WORKSPACE, editor.shownCard());
        ElementsList.Host elements = elements(editor);
        assertTrue(elements.shown(Item.STATUS_BAR));
        elements.setShown(Item.STATUS_BAR, false);
        assertFalse(layout.isShown(Element.STATUS));

        editor.present(EditorCard.STATUS, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("the Status bar card's eye reads it hidden", 0,
            model(editor).value(Row.VISIBLE));
        model(editor).onValue(Row.VISIBLE, 1, false);
        assertTrue(layout.isShown(Element.STATUS));
        assertTrue("and the list reads it back", elements.shown(Item.STATUS_BAR));
    }

    @Test
    public void theDocksTilesAndTheElementsListWriteOneValue() {
        LayoutEditorController layout = layoutEditor();
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, layout);
        ElementsList.Host elements = elements(editor);
        boolean search = layout.searchField();
        assertEquals(search, elements.shown(Item.SEARCH_FIELD));
        elements.setShown(Item.SEARCH_FIELD, !search);
        assertEquals("the Dock card's Search field tile reads the same key", !search,
            layout.searchField());
        boolean drawer = layout.drawerTile();
        elements.setShown(Item.DRAWER_ICON, !drawer);
        assertEquals(!drawer, layout.drawerTile());
    }

    @Test
    public void theSlidersGlyphOpensTheElementsCard() {
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL);
        ElementsList.Host elements = elements(editor);
        elements.open(Item.TERMINAL);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.TERMINAL, editor.shownCard());
        editor.present(EditorCard.WORKSPACE, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        elements(editor).open(Item.SEARCH_FIELD);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("the Search field's settings are the Dock card's Options", EditorCard.DOCK,
            editor.shownCard());
    }

    private static ElementsList.Host elements(SurfaceEditorController editor) {
        ElementsList list = ReflectionHelpers.getField(editor, "mElements");
        assertTrue("the screen card holds the Elements list", list != null);
        return ReflectionHelpers.getField(list, "mHost");
    }

    // ------------------------------------------------------------------------------- the drive

    private PaneWallPage last() {
        return wallMoves.isEmpty() ? null : wallMoves.get(wallMoves.size() - 1);
    }

    private static void switchHalf(SurfaceEditorController editor, WallHalf half) {
        ReflectionHelpers.callInstanceMethod(editor, "switchHalf",
            ClassParameter.from(WallHalf.class, half), ClassParameter.from(boolean.class, true));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private SurfaceEditorController open(PaneWallPage place) {
        return open(place, layoutEditor());
    }

    private SurfaceEditorController open(PaneWallPage place, LayoutEditorController layout) {
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "layoutEditor": return layout;
                case "pageOnScreen": return place;
                case "isInAppKeyboardShown": return keyboardShown;
                case "inAppKeyboard": return null;
                default: return null;
            }
        });
        harness.editor.present(null, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        return harness.editor;
    }

    private LayoutEditorController layoutEditor() {
        Context context = RuntimeEnvironment.getApplication();
        LayoutEditorController.Host host = (LayoutEditorController.Host) Proxy.newProxyInstance(
            LayoutEditorController.Host.class.getClassLoader(),
            new Class<?>[] {LayoutEditorController.Host.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "context": return context;
                    case "places": return places;
                    case "placeOnScreen": return PaneWallPage.TERMINAL;
                    case "placeOrientation": return PlaceOrientation.PORTRAIT;
                    case "extraKeySlots": return new ArrayList<>();
                    case "extraKeyCount":
                    case "measuredDockHeightDp":
                    case "measuredKeyboardHeightDp":
                        return -1;
                    case "holdPaneWallOnPlace":
                        if ((Boolean) args[1]) wallMoves.add((PaneWallPage) args[0]);
                        return null;
                    case "slidePaneWallToPlace":
                        wallMoves.add((PaneWallPage) args[0]);
                        return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: break;
                }
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == float.class) return 0f;
                return null;
            });
        return new LayoutEditorController(host);
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }
}
