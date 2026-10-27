package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

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
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/**
 * The editor's cards through the controller: a card writes the half the frame shows, Reset puts
 * the surface back on that half's Look, Copy makes the other half look the same, one dirty state
 * covers both halves, and Undo puts both back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SurfaceEditorCardsTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("surface-editor-cards",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        SurfacePresets.applyLook(preferences, SurfacePresets.presets(preferences).get(1));
    }

    @Test
    public void aDockSliderOnHomeWritesHomeAlone() {
        SurfaceEditorController editor = open(EditorCard.DOCK, PaneWallPage.WIDGETS);
        assertEquals(WallHalf.HOME, editor.half());
        int workspace = opacity(WallHalf.WORKSPACE);

        model(editor).onValue(Row.OPACITY, 23, false);
        model(editor).onReleased(Row.OPACITY);

        assertEquals(23, opacity(WallHalf.HOME));
        // Home's value is the wall's: the Workspace's dock wears it unless it holds its own.
        assertEquals("the Workspace's dock follows the wall", 23, opacity(WallHalf.WORKSPACE));
        assertTrue("the dot: Home's own value", model(editor).ownValue(Row.OPACITY));
    }

    @Test
    public void theTerminalsSliderWritesTheOneTerminal() {
        SurfaceEditorController editor = open(EditorCard.TERMINAL, null);
        model(editor).onValue(Row.TINT, 61, false);
        model(editor).onReleased(Row.TINT);
        assertEquals(61, preferences.getSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.TINT,
            WallHalf.WORKSPACE));
    }

    @Test
    public void aSurfacesOwnValueLeavesTheHalfAtCustom() {
        SurfaceEditorController editor = open(EditorCard.DOCK, null);
        assertEquals("Mist at open", 1, model(editor).value(Row.LOOK));
        model(editor).onValue(Row.OPACITY, 7, false);
        model(editor).onReleased(Row.OPACITY);
        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));
    }

    @Test
    public void resetPutsTheSurfaceBackOnTheHalfsLook() {
        SurfaceEditorController editor = open(EditorCard.STATUS, PaneWallPage.WIDGETS);
        model(editor).onValue(Row.TINT, 80, false);
        model(editor).onReleased(Row.TINT);
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.TINT,
            WallHalf.HOME));

        ReflectionHelpers.callInstanceMethod(editor, "resetCard");

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.TINT,
            WallHalf.HOME));
        assertFalse(model(editor).ownValue(Row.TINT));
    }

    @Test
    public void oneDirtyStateCoversBothHalvesAndUndoPutsBothBack() {
        String atOpen = AppearanceSnapshot.signatureOf(preferences);
        SurfaceEditorController editor = open(EditorCard.DOCK, PaneWallPage.WIDGETS);
        assertFalse(isDirty(editor));

        model(editor).onValue(Row.OPACITY, 15, false);
        model(editor).onReleased(Row.OPACITY);
        assertTrue("a Home write is unsaved", isDirty(editor));
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
            WallHalf.WORKSPACE, 9);

        editor.undo();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(atOpen, AppearanceSnapshot.signatureOf(preferences));
        assertFalse(isDirty(editor));
    }

    @Test
    public void aVisitThatChangedNoLookKeepsTheSavedCustom() {
        preferences.setSurfaceCustomPreset("{\"surface_material\":\"frost\"}");
        String saved = preferences.getSurfaceCustomPreset();
        SurfaceEditorController editor = open(EditorCard.KEYBOARD, null);

        editor.done();

        assertEquals(saved, preferences.getSurfaceCustomPreset());
    }

    @Test
    public void theTerminalsCardIsNotOfferedOnHome() {
        SurfaceEditorController editor = open(EditorCard.TERMINAL, PaneWallPage.WIDGETS);
        assertEquals("the terminal stands on the Workspace alone: Home's card stands at peek",
            EditorCard.HOME, editor.shownCard());
        editor.present(EditorCard.HOME, null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.HOME, editor.shownCard());
    }

    @Test
    public void frostOnASurfaceWritesItsOwnBlurAndGrainOnTheHalf() {
        SurfaceEditorController editor = open(EditorCard.STATUS, PaneWallPage.WIDGETS);
        int workspaceBlur = preferences.getSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            WallHalf.WORKSPACE);

        model(editor).onValue(Row.FROST, 40, false);
        model(editor).onReleased(Row.FROST);

        assertEquals(GlassControls.blurForGlass(40), preferences.getSurfaceValue(
            SurfaceSlot.STATUS, SurfaceProperty.BLUR, WallHalf.HOME));
        assertEquals(GlassControls.grainForGlass(40), preferences.getSurfaceValue(
            SurfaceSlot.STATUS, SurfaceProperty.GRAIN, WallHalf.HOME));
        assertEquals("the Workspace's status bar wears the wall's value",
            GlassControls.blurForGlass(40),
            preferences.getSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
                WallHalf.WORKSPACE));
        assertEquals("Frost reads back where it was left", 40, model(editor).value(Row.FROST));
        assertTrue("the dot: Home's own frost", model(editor).ownValue(Row.FROST));
    }

    @Test
    public void allSurfacesStandsAtTheCustomStopAndWritesTheHalfsSharedGlass() {
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, null);
        assertEquals(WallHalf.WORKSPACE, editor.half());
        EditorCardSheet.Model model = model(editor);
        assertFalse("a Look stop shows no strip", model.shown(Row.FROST));

        model.onValue(Row.LOOK, AppearanceLooks.CUSTOM_STOP, false);
        model.onReleased(Row.LOOK);
        assertEquals("Custom holds though nothing changed yet", AppearanceLooks.CUSTOM_STOP,
            model.value(Row.LOOK));
        assertTrue(model.shown(Row.FROST));
        assertTrue(model.shown(Row.TEXT_SIZE));

        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE, 9);
        int home = preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME);
        model.onValue(Row.OPACITY, 33, false);
        model.onReleased(Row.OPACITY);
        assertEquals(33, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE));
        assertEquals("every surface on the half wears it", 33, opacity(WallHalf.WORKSPACE));
        assertEquals("Base is wall-wide, so Home wears it too", 33,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));

        model.onValue(Row.FROST, 75, false);
        model.onReleased(Row.FROST);
        assertEquals(GlassControls.blurForGlass(75),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE));
        assertEquals(GlassControls.grainForGlass(75),
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.WORKSPACE));
        assertEquals(75, model.value(Row.FROST));

        model.onValue(Row.LOOK, 1, false);
        model.onReleased(Row.LOOK);
        assertFalse("back on a Look, the strip goes", model.shown(Row.FROST));
    }

    @Test
    public void openingSlidingAndAThemeChangeNeverWriteHome() {
        // Home on its own look (Tint), the Workspace on Mist: unequal halves.
        SurfacePresets.applyLook(preferences, SurfacePresets.presets(preferences).get(2),
            WallHalf.HOME);
        java.util.Map<String, Object> home = homeKeys();
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PaneWallPage.WIDGETS);
        assertEquals(WallHalf.HOME, editor.half());

        ReflectionHelpers.callInstanceMethod(editor, "switchHalf",
            ReflectionHelpers.ClassParameter.from(WallHalf.class, WallHalf.WORKSPACE),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        ReflectionHelpers.callInstanceMethod(editor, "switchHalf",
            ReflectionHelpers.ClassParameter.from(WallHalf.class, WallHalf.HOME),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        model(editor).onValue(Row.THEME, 2, false);
        model(editor).onReleased(Row.THEME);
        editor.done();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(home, homeKeys());
    }

    @Test
    public void customOnTheWorkspaceNeverWritesHome() {
        java.util.List<SurfacePresets.Preset> looks = SurfacePresets.presets(preferences);
        // A saved Custom that names Home's own look on Solid's numbers, as pong's did.
        SurfacePresets.applyLook(preferences, looks.get(3), WallHalf.HOME);
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.applyLook(preferences, looks.get(2), WallHalf.HOME);
        java.util.Map<String, Object> home = homeKeys();
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, null);
        assertEquals(WallHalf.WORKSPACE, editor.half());

        model(editor).onValue(Row.LOOK, AppearanceLooks.CUSTOM_STOP, false);
        model(editor).onReleased(Row.LOOK);

        assertEquals("the Workspace's Custom leaves Home as it stood", home, homeKeys());
        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));
    }

    @Test
    public void theGlassWidgetsSwitchAppliesToEveryWidgetAndUndoGivesTheirChoicesBack() {
        java.util.List<String> calls = new java.util.ArrayList<>();
        java.util.Map<Integer, String> held = java.util.Collections.singletonMap(-1, "on");
        Object[] restored = {null};
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            if (method.equals("clearWidgetGlassChoices")) {
                calls.add("clear");
                return held;
            }
            if (method.equals("restoreWidgetGlassChoices")) {
                calls.add("restore");
                restored[0] = args[0];
            }
            return null;
        });
        SurfaceEditorController editor = harness.editor;
        editor.present(EditorCard.HOME, PaneWallPage.WIDGETS, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.HOME, editor.shownCard());
        assertTrue("the row says it applies to every widget",
            model(editor).summary(Row.GLASS_WIDGETS) != null);

        model(editor).onValue(Row.GLASS_WIDGETS, 0, false);
        model(editor).onValue(Row.GLASS_WIDGETS, 1, false);
        assertEquals(java.util.Arrays.asList("clear", "clear"), calls);

        editor.undo();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(java.util.Arrays.asList("clear", "clear", "restore"), calls);
        assertEquals("each widget's choice as it first stood", held, restored[0]);
    }

    // ------------------------------------------------------------------------------- the drive

    private SurfaceEditorController open(EditorCard card, PaneWallPage place) {
        EditorTestHarness harness = new EditorTestHarness(preferences, null);
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        return harness.editor;
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }

    private static boolean isDirty(SurfaceEditorController editor) {
        return ReflectionHelpers.callInstanceMethod(editor, "isDirty");
    }

    /** Every key the store holds for Home's half alone, with its value. */
    private java.util.Map<String, Object> homeKeys() {
        java.util.Map<String, Object> out = new java.util.TreeMap<>();
        String suffix = TermuxAppSharedPreferences.surfaceHalfKey("", WallHalf.HOME);
        for (java.util.Map.Entry<String, ?> entry
                : preferences.getSharedPreferences().getAll().entrySet()) {
            if (entry.getKey().endsWith(suffix))
                out.put(entry.getKey(), entry.getValue());
        }
        assertFalse("Home holds its own look", out.isEmpty());
        return out;
    }

    private int opacity(WallHalf half) {
        return preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, half);
    }
}
