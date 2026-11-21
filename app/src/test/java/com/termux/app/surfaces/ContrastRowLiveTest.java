package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

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
 * Contrast is previewed live: a change on the Global or the Terminal card rebuilds the terminal
 * palette at once, marks the session dirty, and Discard puts the entry's level and palette back.
 * Both cards' rows are the one stored setting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ContrastRowLiveTest {

    private TermuxAppSharedPreferences preferences;
    private int paletteRefreshes;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("contrast-row-live",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        paletteRefreshes = 0;
    }

    @Test
    public void aContrastChangeRepaintsTheTerminalAndDiscardPutsItBack() {
        SurfaceEditorController editor = open(EditorCard.APPEARANCE);
        int before = paletteRefreshes;

        model(editor).onValue(Row.CONTRAST, 2, false);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(TerminalContrastLevel.HARDER, preferences.getTerminalContrastLevel());
        assertTrue("the palette is rebuilt with the change", paletteRefreshes > before);
        assertTrue("the session is dirty",
            (Boolean) ReflectionHelpers.callInstanceMethod(editor, "isDirty"));

        int changed = paletteRefreshes;
        ReflectionHelpers.callInstanceMethod(editor, "revertToEntry");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(TerminalContrastLevel.DEFAULT, preferences.getTerminalContrastLevel());
        assertTrue("Discard rebuilds the palette at the entry's level",
            paletteRefreshes > changed);
    }

    @Test
    public void theGlobalAndTerminalRowsAreOneSetting() {
        SurfaceEditorController terminal = open(EditorCard.TERMINAL);
        model(terminal).onValue(Row.CONTRAST, 0, false);
        assertEquals(TerminalContrastLevel.SOFTER, preferences.getTerminalContrastLevel());
        // Done stores the draft; the next editor then opens on the store.
        terminal.done();
        SurfaceEditorController global = open(EditorCard.APPEARANCE);
        assertEquals(0, model(global).value(Row.CONTRAST));

        model(global).onValue(Row.CONTRAST, 2, false);
        global.done();
        terminal = open(EditorCard.TERMINAL);
        assertEquals(2, model(terminal).value(Row.CONTRAST));
    }

    @Test
    public void adaptiveLegibilityIsOneSettingOnBothCardsAndDiscardPutsItBack() {
        SurfaceEditorController global = open(EditorCard.APPEARANCE);
        int before = paletteRefreshes;
        model(global).onValue(Row.ADAPTIVE_LEGIBILITY, 1, false);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertTrue(preferences.isAdaptiveLegibility());
        assertTrue("the palette is rebuilt with the change", paletteRefreshes > before);
        assertTrue("the session is dirty",
            (Boolean) ReflectionHelpers.callInstanceMethod(global, "isDirty"));
        ReflectionHelpers.callInstanceMethod(global, "revertToEntry");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertFalse("Discard puts it back", preferences.isAdaptiveLegibility());

        model(global).onValue(Row.ADAPTIVE_LEGIBILITY, 1, false);
        global.done();
        SurfaceEditorController terminal = open(EditorCard.TERMINAL);
        assertEquals(1, model(terminal).value(Row.ADAPTIVE_LEGIBILITY));
    }

    private SurfaceEditorController open(EditorCard card) {
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "refreshTerminalPalette": paletteRefreshes++; return null;
                case "pageOnScreen": return PageKind.TERMINAL;
                default: return null;
            }
        });
        harness.editor.present(card, PageKind.TERMINAL, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return harness.editor;
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }
}
