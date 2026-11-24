package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.page.PageKind;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

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
import android.app.Application;

/**
 * The frame shows the half its half line names: sliding to the Terminal brings the live wall to
 * the Terminal (the Terminal card is then the terminal's tap), sliding back brings Home; and a
 * card's Visible switch reads the state the frame draws.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceEditorHalvesTest {

    private TermuxAppSharedPreferences preferences;
    private PlaceLayoutStore places;
    private final List<PageKind> wallMoves = new ArrayList<>();
    private boolean keyboardShown = true;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("surface-editor-halves",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        places = com.termux.app.place.KindFixtures.dockOnTheTerminal(new PlaceLayoutStore(preferences));
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
    }

    @Test
    public void slidingTheHalvesMovesTheLiveWallBothWays() {
        SurfaceEditorController editor = open(PageKind.HOME);
        assertEquals(PageKind.HOME, editor.screen());
        wallMoves.clear();

        switchHalf(editor, PageKind.TERMINAL);
        assertEquals(PageKind.TERMINAL, editor.screen());
        assertEquals("the wall slides to the Terminal", PageKind.TERMINAL, last());

        switchHalf(editor, PageKind.HOME);
        assertEquals(PageKind.HOME, last());
    }

    @Test
    public void theTerminalCardStandsOnTheTerminal() {
        SurfaceEditorController editor = open(PageKind.HOME);
        switchHalf(editor, PageKind.TERMINAL);
        editor.present(EditorCard.TERMINAL, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.TERMINAL, editor.shownCard());
    }

    @Test
    public void theSearchFieldAndDrawerIconChipsWriteTheDocksValues() {
        LayoutEditorController layout = layoutEditor();
        SurfaceEditorController editor = open(PageKind.TERMINAL, layout);
        editor.present(EditorCard.TERMINAL, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        EditorCardSheet.Model model = ReflectionHelpers.getField(editor, "mModel");
        boolean search = layout.searchField();
        assertEquals(search ? 1 : 0, model.value(Row.SEARCH_FIELD));
        model.onValue(Row.SEARCH_FIELD, search ? 0 : 1, false);
        assertEquals("the chip writes the key the Dock card's tile wrote", !search,
            layout.searchField());
        boolean drawer = layout.drawerTile();
        model.onValue(Row.DRAWER_ICON, drawer ? 0 : 1, false);
        assertEquals(!drawer, layout.drawerTile());
    }

    // ------------------------------------------------------------------------------- the drive

    private PageKind last() {
        return wallMoves.isEmpty() ? null : wallMoves.get(wallMoves.size() - 1);
    }

    /** A door to {@code half}'s page while the editor is up (the swipe retired, ADR 0015 §6). */
    private static void switchHalf(SurfaceEditorController editor, PageKind half) {
        ReflectionHelpers.callInstanceMethod(editor, "showPage",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(PageKind.class,
                half == PageKind.HOME ? PageKind.HOME : PageKind.TERMINAL),
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, true));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private SurfaceEditorController open(PageKind place) {
        return open(place, layoutEditor());
    }

    private SurfaceEditorController open(PageKind place, LayoutEditorController layout) {
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
                    case "placeOnScreen": return PageKind.TERMINAL;
                    case "placeOrientation": return PlaceOrientation.PORTRAIT;
                    case "extraKeySlots": return new ArrayList<>();
                    case "extraKeyCount":
                    case "measuredDockHeightDp":
                    case "measuredKeyboardHeightDp":
                        return -1;
                    case "holdPaneWallOnPlace":
                        if ((Boolean) args[1]) wallMoves.add((PageKind) args[0]);
                        return null;
                    case "slidePaneWallToPlace":
                        wallMoves.add((PageKind) args[0]);
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
