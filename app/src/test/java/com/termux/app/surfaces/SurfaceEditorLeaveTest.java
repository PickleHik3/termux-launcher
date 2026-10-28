package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AlertDialog;

import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceArrangeSnapshot;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.util.ArrayList;

/**
 * Leaving the editor (AGENTS.md, "the Appearance editor commits only on Done"): Back takes a card
 * above peek to peek first; at peek, any difference from the state at open asks the unsaved-changes question;
 * Discard puts everything back as it was at open, both halves and every layout; Done keeps.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SurfaceEditorLeaveTest {

    private TermuxAppSharedPreferences preferences;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("surface-editor-leave",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        SurfacePresets.applyLook(preferences, SurfacePresets.presets(preferences).get(1));
        places = new PlaceLayoutStore(preferences);
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
        ShadowDialog.reset();
    }

    @Test
    public void backTakesTheCardToPeekThenAWorkspaceChangeAsks() {
        SurfaceEditorController editor = open(EditorCard.DOCK, PaneWallPage.TERMINAL, null);
        assertEquals(WallHalf.WORKSPACE, editor.screen());
        model(editor).onValue(Row.OPACITY, 21, false);
        model(editor).onReleased(Row.OPACITY);

        assertTrue("Back with a card above peek is spent on the card", editor.consumeBack());
        assertEquals("the card stays, at peek", EditorCard.DOCK, editor.shownCard());
        EditorCardSheet sheet = org.robolectric.util.ReflectionHelpers.getField(editor, "mSheet");
        assertEquals(EditorCardSheet.DETENT_PEEK, sheet.detent());
        assertFalse("at peek Back is the surface's to leave by", editor.consumeBack());

        boolean[] left = {false};
        editor.requestLeave(() -> left[0] = true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertNotNull("the unsaved-changes question", unsavedDialog());
        assertFalse("nothing leaves before the answer", left[0]);
    }

    @Test
    public void discardPutsBothHalvesBackAsTheyWereAtOpen() {
        String atOpen = AppearanceSnapshot.signatureOf(preferences);
        SurfaceEditorController editor = open(EditorCard.DOCK, PaneWallPage.TERMINAL, null);
        model(editor).onValue(Row.OPACITY, 21, false);
        model(editor).onReleased(Row.OPACITY);
        model(editor).onValue(Row.TINT, 73, false);
        model(editor).onReleased(Row.TINT);
        editor.present(EditorCard.DOCK, PaneWallPage.WIDGETS, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(WallHalf.HOME, editor.screen());
        model(editor).onValue(Row.OPACITY, 73, false);
        model(editor).onReleased(Row.OPACITY);
        assertNotEquals(atOpen, AppearanceSnapshot.signatureOf(preferences));

        boolean[] left = {false};
        editor.requestLeave(() -> left[0] = true);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        click(DialogInterface.BUTTON_NEGATIVE);

        assertTrue(left[0]);
        assertEquals("both halves back as they were", atOpen,
            AppearanceSnapshot.signatureOf(preferences));
    }

    @Test
    public void aHiddenDockComesBackOnDiscard() {
        LayoutEditorController layout = layoutEditor();
        SurfaceEditorController editor = open(EditorCard.DOCK, PaneWallPage.TERMINAL, layout);
        String normal = PlaceArrangeSnapshot.capture(places, LayoutVariant.NORMAL).signature();
        String home = PlaceArrangeSnapshot.capture(places, LayoutVariant.HOME).signature();
        assertTrue(layout.isShown(Element.APPS));

        model(editor).onValue(Row.VISIBLE, 0, false);
        assertFalse("the dock is put away, live", layout.isShown(Element.APPS));
        assertEquals("the card reads what the frame draws", 0, model(editor).value(Row.VISIBLE));

        editor.requestLeave(() -> { });
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        click(DialogInterface.BUTTON_NEGATIVE);

        assertEquals(normal, PlaceArrangeSnapshot.capture(places, LayoutVariant.NORMAL).signature());
        assertEquals(home, PlaceArrangeSnapshot.capture(places, LayoutVariant.HOME).signature());
    }

    @Test
    public void doneKeepsAndAsksNothing() {
        SurfaceEditorController editor = open(EditorCard.DOCK, PaneWallPage.TERMINAL, null);
        model(editor).onValue(Row.OPACITY, 21, false);
        model(editor).onReleased(Row.OPACITY);
        boolean[] done = {false};
        editor.setOnDone(() -> done[0] = true);

        editor.done();

        assertTrue(done[0]);
        assertNull(unsavedDialog());
        assertEquals(21, preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE));
    }

    @Test
    public void nothingChangedLeavesWithoutAsking() {
        SurfaceEditorController editor = open(EditorCard.DOCK, PaneWallPage.TERMINAL, null);
        boolean[] left = {false};
        editor.requestLeave(() -> left[0] = true);
        assertTrue(left[0]);
        assertNull(unsavedDialog());
    }

    // ------------------------------------------------------------------------------- the drive

    private SurfaceEditorController open(EditorCard card, PaneWallPage place,
                                         LayoutEditorController layout) {
        EditorTestHarness harness = new EditorTestHarness(preferences,
            (method, args) -> "layoutEditor".equals(method) ? layout : null);
        harness.editor.present(card, place, null);
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

    private static Dialog unsavedDialog() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        return dialog != null && dialog.isShowing() ? dialog : null;
    }

    private static void click(int which) {
        Dialog dialog = unsavedDialog();
        assertNotNull("the unsaved-changes question", dialog);
        ((AlertDialog) dialog).getButton(which).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }
}
