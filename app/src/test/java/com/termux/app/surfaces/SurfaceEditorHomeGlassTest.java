package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
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

/**
 * The Home and Workspace cards' glass strips (appearance-cards-overhaul §1): Home writes the
 * all-glass cells of Home pages, the Workspace those of Terminal and Display pages together;
 * neither touches Base, which Customization writes. Reset clears the card's cells alone.
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
        // Base is wall-wide since the halves became pages: the Workspace's numbers first, then
        // Home's, which are the wall's.
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.terminalPageId(), 40);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.terminalPageId(),
            GlassControls.blurForGlass(60));
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.terminalPageId(),
            GlassControls.grainForGlass(60));
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL, 92);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, TermuxAppSharedPreferences.PAGE_WALL, 0);
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
    public void theStripReadsHomesCellOverBase() {
        grid().write(LookGrid.Property.OPACITY, null, HOME, 33);
        SurfaceEditorController editor = open(EditorCard.HOME, PaneWallPage.WIDGETS);
        assertEquals(33, model(editor).value(Row.OPACITY));
    }

    @Test
    public void homeWritesHomePagesAndLeavesBase() {
        SurfaceEditorController editor = open(EditorCard.HOME, PaneWallPage.WIDGETS);
        model(editor).onValue(Row.OPACITY, 55, false);
        assertEquals(55, grid().value(LookGrid.Property.OPACITY, null, HOME));
        assertEquals("Base is Customization's", 92,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL));
        assertFalse(grid().has(LookGrid.Property.OPACITY, null, LookGrid.Kind.TERMINAL));
        assertFalse(grid().has(LookGrid.Property.OPACITY, null, LookGrid.Kind.DISPLAY));
    }

    @Test
    public void workspaceWritesTerminalAndDisplayPages() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        model(editor).onValue(Row.OPACITY, 44, false);
        model(editor).onValue(Row.FROST, 70, false);
        model(editor).onReleased(Row.FROST);
        for (LookGrid.Kind kind : new LookGrid.Kind[] {LookGrid.Kind.TERMINAL, LookGrid.Kind.DISPLAY}) {
            assertEquals(kind.key, 44, grid().value(LookGrid.Property.OPACITY, null, kind));
            assertTrue(kind.key, grid().has(LookGrid.Property.FROST, null, kind));
            assertTrue(kind.key, grid().has(LookGrid.Property.GRAIN, null, kind));
        }
        assertFalse(grid().has(LookGrid.Property.OPACITY, null, HOME));
        assertEquals(92,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL));
    }

    @Test
    public void customizationWritesBaseAndResetsTheDocksOwn() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL, 30);
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PaneWallPage.WIDGETS);
        model(editor).onValue(Row.OPACITY, 61, false);
        assertEquals(61, grid().value(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE));
        // Last change wins (developer, 2026-11-04): the dock follows Base again.
        assertFalse("the dock's own value went",
            grid().has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, LookGrid.NO_PAGE));
        assertEquals(61, grid().value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK,
            LookGrid.NO_PAGE));
        for (LookGrid.Kind kind : LookGrid.Kind.values())
            assertFalse(kind.key, grid().has(LookGrid.Property.OPACITY, null, kind));
    }

    @Test
    public void aDocksOwnValueIsLeftByHomesWrite() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL,
            30);
        SurfaceEditorController editor = open(EditorCard.HOME, PaneWallPage.WIDGETS);
        model(editor).onValue(Row.OPACITY, 70, false);
        assertEquals(30, grid().value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, LookGrid.NO_PAGE));
    }

    @Test
    public void resetHomeClearsHomePagesCellsAlone() {
        SurfaceEditorController editor = open(EditorCard.HOME, PaneWallPage.WIDGETS);
        model(editor).onValue(Row.OPACITY, 50, false);
        model(editor).onValue(Row.FROST, 70, false);
        model(editor).onReleased(Row.FROST);
        assertTrue(grid().has(LookGrid.Property.OPACITY, null, HOME));

        ReflectionHelpers.callInstanceMethod(editor, "resetCard");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        for (LookGrid.Property property : new LookGrid.Property[] {LookGrid.Property.OPACITY,
                LookGrid.Property.FROST, LookGrid.Property.GRAIN})
            assertFalse(property.key, grid().has(property, null, HOME));
        assertEquals("Base stays", 92,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals("Home follows Base again", 92, model(editor).value(Row.OPACITY));
    }

    // ------------------------------------------------------------------------------- the drive

    private SurfaceEditorController open(EditorCard card, PaneWallPage place) {
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

    /** The grid's page kinds are process-wide: none leaks into the next test class. */
    @org.junit.After
    public void resetLookGridPageKinds() {
        com.termux.shared.termux.settings.preferences.LookGrid.install(null, null);
    }
}
