package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import android.app.Application;

/**
 * The header pill's Home and Workspace slide the live wall to their page inside the mini UI, as a
 * swipe would, and Customization wears the same halo as the page cards.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceEditorPillTest {

    private TermuxAppSharedPreferences preferences;
    private PlaceLayoutStore places;
    private final List<PageKind> wallMoves = new ArrayList<>();

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("surface-editor-pill",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        places = com.termux.app.place.KindFixtures.dockOnTheTerminal(
            new PlaceLayoutStore(preferences));
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
    }

    @Test
    public void workspaceOnThePillSlidesTheWallToTheWorkspaceAndHomeBack() {
        SurfaceEditorController editor = open(PageKind.HOME);
        assertEquals(PageKind.HOME, editor.screen());
        wallMoves.clear();

        pill(editor, EditorCard.TERMINAL);
        assertEquals("the wall slides to the Workspace", PageKind.TERMINAL, last());
        assertEquals(PageKind.TERMINAL, editor.screen());
        assertEquals(EditorCard.TERMINAL, editor.shownCard());

        pill(editor, EditorCard.HOME);
        assertEquals("the wall slides back to Home", PageKind.HOME, last());
        assertEquals(PageKind.HOME, editor.screen());
        assertEquals(EditorCard.HOME, editor.shownCard());
    }

    @Test
    public void homeOnThePillFromAWorkspaceEntrySlidesToHome() {
        SurfaceEditorController editor = open(PageKind.TERMINAL);
        wallMoves.clear();
        pill(editor, EditorCard.HOME);
        assertEquals(PageKind.HOME, last());
        assertEquals(EditorCard.HOME, editor.shownCard());
    }

    @Test
    public void customizationStaysOnTheShownPage() {
        SurfaceEditorController editor = open(PageKind.HOME);
        wallMoves.clear();
        pill(editor, EditorCard.APPEARANCE);
        assertEquals(EditorCard.APPEARANCE, editor.shownCard());
        assertEquals(PageKind.HOME, editor.screen());
        assertTrue("the wall stays where it stands",
            wallMoves.isEmpty() || last() == PageKind.HOME);
    }

    @Test
    public void thePillAsksForThePageTheCardStandsOn() {
        assertEquals(PageKind.TERMINAL,
            SurfaceEditorController.pillPlace(EditorCard.TERMINAL, PageKind.HOME));
        assertEquals(PageKind.HOME,
            SurfaceEditorController.pillPlace(EditorCard.HOME, PageKind.TERMINAL));
        assertNull(SurfaceEditorController.pillPlace(EditorCard.HOME, PageKind.HOME));
        assertNull(SurfaceEditorController.pillPlace(EditorCard.TERMINAL, PageKind.TERMINAL));
        assertNull(SurfaceEditorController.pillPlace(EditorCard.APPEARANCE, PageKind.HOME));
    }

    @Test
    public void customizationWearsTheHalo() {
        assertTrue(SelectionRingShape.wearsHalo(EditorCard.APPEARANCE));
    }

    // ------------------------------------------------------------------------------- the drive

    private PageKind last() {
        return wallMoves.isEmpty() ? null : wallMoves.get(wallMoves.size() - 1);
    }

    /** A tap on the pill's {@code card}, as the sheet reports it. */
    private static void pill(SurfaceEditorController editor, EditorCard card) {
        EditorCardSheet sheet = ReflectionHelpers.getField(editor, "mSheet");
        assertTrue("the editor has its card sheet", sheet != null);
        EditorCardSheet.Listener listener = ReflectionHelpers.getField(sheet, "mListener");
        listener.onOpenCard(card);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private SurfaceEditorController open(PageKind place) {
        LayoutEditorController layout = layoutEditor();
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "layoutEditor": return layout;
                case "pageOnScreen": return place;
                case "isInAppKeyboardShown": return true;
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
}
