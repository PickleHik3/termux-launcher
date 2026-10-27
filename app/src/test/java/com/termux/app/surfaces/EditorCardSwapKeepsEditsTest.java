package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;

import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
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

/**
 * Edits live in the editing session until Done stores them or Discard reverts them: a screen card
 * that follows the page swipe, a card collapsed to peek, or a card raised by the page label
 * never puts a value back, and the bar keeps showing the session dirty.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class EditorCardSwapKeepsEditsTest {

    private TermuxAppSharedPreferences preferences;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("editor-card-swap-keeps-edits",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        places = new PlaceLayoutStore(preferences);
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
        SurfacePresets.applyLook(preferences, SurfacePresets.presets(preferences).get(1));
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE, 40);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE,
            GlassControls.blurForGlass(20));
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.WORKSPACE,
            GlassControls.grainForGlass(20));
    }

    @Test
    public void theWorkspacesGlassOutlivesASwipeToHomeAndBack() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        moveStrip(editor, Row.FROST, 70);
        moveStrip(editor, Row.OPACITY, 65);
        assertWorkspaceKept(editor);

        swipe(editor, WallHalf.HOME);
        assertEquals("the Home card followed the swipe", EditorCard.HOME, editor.shownCard());
        assertWorkspaceKept(editor);

        swipe(editor, WallHalf.WORKSPACE);
        assertEquals(EditorCard.WORKSPACE, editor.shownCard());
        assertWorkspaceKept(editor);
        assertEquals("Frost reads back where it was left", 70, model(editor).value(Row.FROST));
        assertEquals(65, model(editor).value(Row.OPACITY));
    }

    @Test
    public void bothScreensEditsAcrossSwipesAreStoredByDone() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        moveStrip(editor, Row.FROST, 70);
        moveStrip(editor, Row.OPACITY, 65);
        swipe(editor, WallHalf.HOME);
        moveStrip(editor, Row.OPACITY, 33);
        swipe(editor, WallHalf.WORKSPACE);
        // Base is wall-wide: Home's 33 is the wall's opacity, which the Workspace wears too.
        assertWorkspaceKept(editor, 33);
        assertEquals("Home's edit is kept too", 33,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));

        boolean[] done = {false};
        editor.setOnDone(() -> done[0] = true);
        editor.done();
        assertTrue(done[0]);
        assertEquals("Base is wall-wide: Home's 33 stands", 33,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
        assertEquals(GlassControls.blurForGlass(70),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE));
        assertEquals(33, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));
    }

    @Test
    public void collapsingToPeekKeepsTheCardAndItsEdits() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        moveStrip(editor, Row.FROST, 70);
        moveStrip(editor, Row.OPACITY, 65);

        EditorCardSheet sheet = ReflectionHelpers.getField(editor, "mSheet");
        assertTrue("Back above peek is spent on the card", editor.consumeBack());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("the card stays, at peek", EditorCard.WORKSPACE, editor.shownCard());
        assertEquals(EditorCardSheet.DETENT_PEEK, sheet.detent());
        assertWorkspaceKept(editor);

        editor.present(EditorCard.WORKSPACE, PaneWallPage.TERMINAL, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.WORKSPACE, editor.shownCard());
        assertEquals("a door naming the card raises it from peek", EditorCardSheet.DETENT_HALF,
            sheet.detent());
        assertEquals(70, model(editor).value(Row.FROST));
        assertEquals(65, model(editor).value(Row.OPACITY));
        assertWorkspaceKept(editor);
    }

    @Test
    public void thePageLabelRaisesItsCardOverTheEditsKept() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        moveStrip(editor, Row.FROST, 70);
        moveStrip(editor, Row.OPACITY, 65);
        assertTrue(editor.consumeBack());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        EditorBar bar = ReflectionHelpers.getField(editor, "mBar");
        EditorBar.Listener barListener = ReflectionHelpers.getField(bar, "mListener");
        barListener.onPageLabel();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.WORKSPACE, editor.shownCard());
        assertWorkspaceKept(editor);
        assertEquals(70, model(editor).value(Row.FROST));
    }

    // ------------------------------------------------------------------------------- the checks

    private void assertWorkspaceKept(SurfaceEditorController editor) {
        assertWorkspaceKept(editor, 65);
    }

    private void assertWorkspaceKept(SurfaceEditorController editor, int opacity) {
        assertEquals(opacity,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
        assertEquals(GlassControls.blurForGlass(70),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE));
        assertEquals(GlassControls.grainForGlass(70),
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.WORKSPACE));
        assertTrue("the session is dirty",
            (Boolean) ReflectionHelpers.callInstanceMethod(editor, "isDirty"));
        EditorBar bar = ReflectionHelpers.getField(editor, "mBar");
        assertNotNull(bar);
        View undo = ReflectionHelpers.getField(bar, "mUndo");
        assertTrue("the bar shows the session dirty", undo.isEnabled());
    }

    // ------------------------------------------------------------------------------- the drive

    private SurfaceEditorController open(EditorCard card, PaneWallPage place) {
        LayoutEditorController layout = layoutEditor();
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "layoutEditor": return layout;
                case "pageOnScreen": return place;
                default: return null;
            }
        });
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return harness.editor;
    }

    /** A strip dragged to {@code value} and let go, as a finger does it. */
    private static void moveStrip(SurfaceEditorController editor, Row row, int value) {
        model(editor).onValue(row, value / 2, true);
        model(editor).onValue(row, value, true);
        model(editor).onReleased(row);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    /** The preview swiped to {@code half}: the page swipe's own path. */
    private static void swipe(SurfaceEditorController editor, WallHalf half) {
        ReflectionHelpers.callInstanceMethod(editor, "switchHalf",
            ClassParameter.from(WallHalf.class, half),
            ClassParameter.from(boolean.class, true),
            ClassParameter.from(boolean.class, true));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    /** A live layout session, so a half's slide runs the place's own restatement too. */
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
