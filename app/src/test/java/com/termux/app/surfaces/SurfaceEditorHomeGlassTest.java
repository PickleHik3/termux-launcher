package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/**
 * The Home card's glass strip: Frost, Opacity and Tint are the Home half's Base, which Home's
 * widgets, status bar and dock wear while they follow it. It reads the values a phone already
 * holds (pong's Home: opacity 92, no blur, no grain, no tint), writes Home alone, and Reset Home
 * puts it back on the Look Home wore when the editor opened.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SurfaceEditorHomeGlassTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("surface-editor-home-glass",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        SurfacePresets.applyLook(preferences, SurfacePresets.presets(preferences).get(1));
        // Pong's Home half, and a Workspace that looks otherwise.
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME, 92);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.HOME, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, WallHalf.HOME, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE, 40);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE,
            GlassControls.blurForGlass(60));
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.WORKSPACE,
            GlassControls.grainForGlass(60));
        // The Workspace's Frost was left at 60: Home's pair is not that position's.
        preferences.setSurfaceGlassPosition(null, 60);
        for (SurfaceSlot slot : new SurfaceSlot[] {SurfaceSlot.DOCK, SurfaceSlot.STATUS}) {
            for (SurfaceProperty property : SurfaceProperty.values())
                preferences.setSurfaceInheriting(slot, property, WallHalf.HOME, true);
        }
    }

    @Test
    public void theStripReadsTheValuesHomeHolds() {
        SurfaceEditorController editor = open(PaneWallPage.WIDGETS);
        assertEquals(92, model(editor).value(Row.OPACITY));
        assertEquals(0, model(editor).value(Row.TINT));
        assertEquals("Frost stands where Home's own pair puts it, not at the Workspace's 60",
            GlassControls.glassFor(0, 0), model(editor).value(Row.FROST));
    }

    @Test
    public void theStripWritesHomesBaseAndHomesChromeFollowsIt() {
        SurfaceEditorController editor = open(PaneWallPage.WIDGETS);

        model(editor).onValue(Row.OPACITY, 70, false);
        model(editor).onReleased(Row.OPACITY);
        model(editor).onValue(Row.TINT, 25, false);
        model(editor).onReleased(Row.TINT);
        model(editor).onValue(Row.FROST, 40, false);
        model(editor).onReleased(Row.FROST);

        assertEquals(70, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));
        assertEquals(25, preferences.getSurfaceBaseValue(SurfaceProperty.TINT, WallHalf.HOME));
        assertEquals(GlassControls.blurForGlass(40),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME));
        assertEquals(GlassControls.grainForGlass(40),
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.HOME));
        assertEquals("the dock on Home follows", 70,
            preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, WallHalf.HOME));
        assertEquals("the status bar on Home follows", 70, preferences.getSurfaceValue(
            SurfaceSlot.STATUS, SurfaceProperty.OPACITY, WallHalf.HOME));
        assertEquals("the Workspace keeps its glass", 40,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
        assertEquals(GlassControls.blurForGlass(60),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE));
        assertEquals("Frost reads back where it was left", 40, model(editor).value(Row.FROST));
    }

    @Test
    public void theStripWritesHomeWhicheverHalfTheFrameShows() {
        SurfaceEditorController editor = open(PaneWallPage.WIDGETS);
        assertEquals(WallHalf.HOME, EditorRows.halfFor(EditorCard.HOME, Row.OPACITY,
            WallHalf.WORKSPACE));
        model(editor).onValue(Row.OPACITY, 55, false);
        assertEquals(55, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));
        assertEquals(40,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
    }

    @Test
    public void aDockGivenItsOwnHomeValueKeepsIt() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, WallHalf.HOME,
            30);
        SurfaceEditorController editor = open(PaneWallPage.WIDGETS);
        model(editor).onValue(Row.OPACITY, 70, false);
        assertEquals(30,
            preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, WallHalf.HOME));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            WallHalf.HOME));
    }

    /**
     * Reset puts Home's glass back as it ships, not as the editor found it: Undo is what takes
     * the card back to the open, and a reset that restored the open state did nothing for a card
     * opened on the glass it was meant to clear (EditorCardReset).
     */
    @Test
    public void resetHomePutsTheGlassBackAsItShips() {
        SurfaceEditorController editor = open(PaneWallPage.WIDGETS);
        model(editor).onValue(Row.OPACITY, 50, false);
        model(editor).onValue(Row.FROST, 70, false);
        model(editor).onReleased(Row.FROST);

        ReflectionHelpers.callInstanceMethod(editor, "resetCard");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN,
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.HOME));
        assertEquals(GlassControls.glassFor(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN), model(editor).value(Row.FROST));
        assertEquals("the Workspace is untouched", 40,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
    }

    // ------------------------------------------------------------------------------- the drive

    private SurfaceEditorController open(PaneWallPage place) {
        EditorTestHarness harness = new EditorTestHarness(preferences, null);
        harness.editor.present(EditorCard.HOME, place, null);
        assertEquals("the card is up when present returns", EditorCard.HOME,
            harness.editor.shownCard());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.HOME, harness.editor.shownCard());
        return harness.editor;
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }
}
