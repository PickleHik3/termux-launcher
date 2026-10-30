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
        assertEquals(PaneWallPage.WIDGETS, editor.screen());
        int workspace = opacity(PaneWallPage.TERMINAL);

        model(editor).onValue(Row.OPACITY, 23, false);
        model(editor).onReleased(Row.OPACITY);

        assertEquals(23, opacity(PaneWallPage.WIDGETS));
        // Home's value is the wall's: the Workspace's dock wears it unless it holds its own.
        assertEquals("the Workspace's dock follows the wall", 23, opacity(PaneWallPage.TERMINAL));
        assertTrue("the dot: Home's own value", model(editor).ownValue(Row.OPACITY));
    }

    @Test
    public void theTerminalsSliderWritesTheOneTerminal() {
        SurfaceEditorController editor = open(EditorCard.TERMINAL, null);
        model(editor).onValue(Row.TINT, 61, false);
        model(editor).onReleased(Row.TINT);
        assertEquals(61, preferences.getSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.TINT,
            TermuxAppSharedPreferences.terminalPageId()));
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
            TermuxAppSharedPreferences.PAGE_WALL));

        ReflectionHelpers.callInstanceMethod(editor, "resetCard");

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.TINT,
            TermuxAppSharedPreferences.PAGE_WALL));
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
            TermuxAppSharedPreferences.terminalPageId(), 9);

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
            TermuxAppSharedPreferences.terminalPageId());

        model(editor).onValue(Row.FROST, 40, false);
        model(editor).onReleased(Row.FROST);

        assertEquals(GlassControls.blurForGlass(40), preferences.getSurfaceValue(
            SurfaceSlot.STATUS, SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(GlassControls.grainForGlass(40), preferences.getSurfaceValue(
            SurfaceSlot.STATUS, SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals("the Workspace's status bar wears the wall's value",
            GlassControls.blurForGlass(40),
            preferences.getSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
                TermuxAppSharedPreferences.terminalPageId()));
        assertEquals("Frost reads back where it was left", 40, model(editor).value(Row.FROST));
        assertTrue("the dot: Home's own frost", model(editor).ownValue(Row.FROST));
    }

    @Test
    public void allSurfacesStandsAtTheCustomStopAndWritesBase() {
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, null);
        assertEquals(PaneWallPage.TERMINAL, editor.screen());
        EditorCardSheet.Model model = model(editor);
        assertFalse("a Look stop shows no strip", model.shown(Row.FROST));

        model.onValue(Row.LOOK, AppearanceLooks.CUSTOM_STOP, false);
        model.onReleased(Row.LOOK);
        assertEquals("Custom holds though nothing changed yet", AppearanceLooks.CUSTOM_STOP,
            model.value(Row.LOOK));
        assertTrue(model.shown(Row.FROST));
        assertTrue(model.shown(Row.TEXT_SIZE));

        // Global writes Base, which every page wears (ADR 0015 §6): no half of its own.
        model.onValue(Row.OPACITY, 33, false);
        model.onReleased(Row.OPACITY);
        assertEquals("Global writes Base", 33,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals("the dock wears it on every page", 33, opacity(PaneWallPage.WIDGETS));
        assertEquals(33, opacity(PaneWallPage.TERMINAL));

        model.onValue(Row.FROST, 75, false);
        model.onReleased(Row.FROST);
        assertEquals(GlassControls.blurForGlass(75),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(GlassControls.grainForGlass(75),
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(75, model.value(Row.FROST));

        model.onValue(Row.LOOK, 1, false);
        model.onReleased(Row.LOOK);
        assertFalse("back on a Look, the strip goes", model.shown(Row.FROST));
    }

    @Test
    public void openingAndAThemeChangeNeverWriteHome() {
        // Home on its own look (Tint), the Workspace on Mist: unequal halves.
        SurfacePresets.applyLook(preferences, SurfacePresets.presets(preferences).get(2),
            PaneWallPage.WIDGETS);
        java.util.Map<String, Object> home = homeKeys();
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PaneWallPage.WIDGETS);
        assertEquals(PaneWallPage.WIDGETS, editor.screen());

        model(editor).onValue(Row.THEME, 2, false);
        model(editor).onReleased(Row.THEME);
        editor.done();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(home, homeKeys());
    }

    @Test
    public void customUnderGlobalBringsTheSavedCustomToTheWall() {
        java.util.List<SurfacePresets.Preset> looks = SurfacePresets.presets(preferences);
        // A saved Custom on Solid's numbers; the wall on another Look. Global is the wall, so
        // Custom there brings the saved numbers back on it (the page side is EditorScreenLookTest).
        SurfacePresets.applyLook(preferences, looks.get(3), PaneWallPage.WIDGETS);
        int saved = opacity(PaneWallPage.WIDGETS);
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.applyLook(preferences, looks.get(2), PaneWallPage.WIDGETS);
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, null);
        assertEquals(PaneWallPage.TERMINAL, editor.screen());

        model(editor).onValue(Row.LOOK, AppearanceLooks.CUSTOM_STOP, false);
        model(editor).onReleased(Row.LOOK);

        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));
        assertEquals("the saved Custom's dock is the wall's", saved, opacity(PaneWallPage.WIDGETS));
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
        // Home is the wall now: its own look is the plain per-half keys (Base, the surfaces'
        // cells and links, the material point), which a Workspace edit must leave alone.
        java.util.Map<String, ?> all = preferences.getSharedPreferences().getAll();
        for (String key : AppearanceSnapshot.halfAbleKeys()) {
            if (all.containsKey(key))
                out.put(key, all.get(key));
        }
        assertFalse("Home holds its own look", out.isEmpty());
        return out;
    }

    private int opacity(PaneWallPage half) {
        return preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, SurfacePresets.pageOf(half));
    }
}
