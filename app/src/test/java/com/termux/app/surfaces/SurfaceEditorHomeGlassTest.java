package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.LookGrid;
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
import android.app.Application;

/**
 * The Global card's glass strips write Global, the one look every page wears (ADR 0031); the Home
 * and Workspace cards have no glass of their own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
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
        // Global is wall-wide since the halves became pages: the Workspace's numbers first, then
        // Home's, which are the wall's.
        preferences.setSurfaceGlobalValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.terminalPageId(), 40);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.terminalPageId(),
            GlassControls.blurForGlass(60));
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.terminalPageId(),
            GlassControls.grainForGlass(60));
        preferences.setSurfaceGlobalValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL, 92);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL, 0);
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL, 0);
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, TermuxAppSharedPreferences.PAGE_WALL, 0);
        // The Workspace's Frost was left at 60: Home's pair is not that position's.
        preferences.setSurfaceGlassPosition(null, 60);
        for (SurfaceSlot slot : new SurfaceSlot[] {SurfaceSlot.DOCK, SurfaceSlot.STATUS}) {
            for (SurfaceProperty property : SurfaceProperty.values())
                preferences.setSurfaceInheriting(slot, property, TermuxAppSharedPreferences.PAGE_WALL, true);
        }
    }

    private static final LookGrid.Kind HOME = LookGrid.Kind.HOME;

    private LookGrid grid() {
        return preferences.lookGrid();
    }

    @Test
    public void customizationWritesGlobalAndLeavesTheDocksOwn() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL, 30);
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PageKind.HOME);
        model(editor).onValue(Row.OPACITY, 61, false);
        assertEquals(61, grid().value(LookGrid.Property.OPACITY, null));
        // Own stays (appearance-one-look spec §1): only the dock's arrow puts it back.
        assertTrue("the dock's own value stays",
            grid().has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertEquals(30, grid().value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
    }

    // ------------------------------------------------------------------------------- the drive

    private SurfaceEditorController open(EditorCard card, PageKind place) {
        EditorTestHarness harness = new EditorTestHarness(preferences, null);
        harness.editor.present(card, place, null);
        assertEquals("the card is up when present returns", card,
            harness.editor.shownCard());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return harness.editor;
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }

}
