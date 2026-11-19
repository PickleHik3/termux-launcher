package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/**
 * Own stays (appearance-one-look spec §1, §2): a surface's own Opacity and Blur survive every
 * change on Global, a preset included, and only the reset arrows put them back on Global.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class OwnStaysTest {

    private TermuxAppSharedPreferences prefs;
    private EditorTestHarness harness;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("own-stays", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        prefs = new TermuxAppSharedPreferences(context, store, null);
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
        harness = new EditorTestHarness(prefs, null);
    }

    private EditorCardSheet.Model open(EditorCard card) {
        harness.editor.present(card, card == EditorCard.HOME ? PageKind.HOME : null, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return ReflectionHelpers.getField(harness.editor, "mModel");
    }

    private LookGrid grid() {
        return prefs.lookGrid();
    }

    /** The dock's own Opacity and the keyboard's own Blur, set on their cards. */
    private void giveTheDockAndKeyboardTheirOwn() {
        EditorCardSheet.Model dock = open(EditorCard.DOCK);
        dock.onValue(Row.OPACITY, 80, false);
        dock.onReleased(Row.OPACITY);
        EditorCardSheet.Model keyboard = open(EditorCard.KEYBOARD);
        keyboard.onValue(Row.FROST, 1, false);
        keyboard.onReleased(Row.FROST);
        assertTrue(grid().holdsOwn(Property.OPACITY, Element.DOCK));
        assertTrue(grid().holdsOwn(Property.FROST, Element.KEYBOARD));
    }

    @Test
    public void globalsSlidersAndPresetsLeaveAnOwnValue() {
        giveTheDockAndKeyboardTheirOwn();
        int dockOpacity = grid().value(Property.OPACITY, Element.DOCK);
        int keyboardFrost = grid().value(Property.FROST, Element.KEYBOARD);
        EditorCardSheet.Model global = open(EditorCard.APPEARANCE);
        global.onValue(Row.OPACITY, 20, false);
        global.onReleased(Row.OPACITY);
        global.onValue(Row.FROST, 3, false);
        global.onReleased(Row.FROST);
        for (int stop = 0; stop < AppearanceLooks.CUSTOM_STOP; stop++) {
            global.onValue(Row.LOOK, stop, false);
            global.onReleased(Row.LOOK);
        }
        assertTrue(grid().holdsOwn(Property.OPACITY, Element.DOCK));
        assertEquals(dockOpacity, grid().value(Property.OPACITY, Element.DOCK));
        assertTrue(grid().holdsOwn(Property.FROST, Element.KEYBOARD));
        assertEquals(keyboardFrost, grid().value(Property.FROST, Element.KEYBOARD));
    }

    @Test
    public void theRowArrowPutsOneValueBackOnGlobal() {
        giveTheDockAndKeyboardTheirOwn();
        EditorCardSheet.Model dock = open(EditorCard.DOCK);
        assertTrue(dock.ownValue(Row.OPACITY));
        assertFalse(dock.ownValue(Row.FROST));
        dock.resetRow(Row.OPACITY);
        assertFalse(grid().holdsOwn(Property.OPACITY, Element.DOCK));
        assertFalse(dock.ownValue(Row.OPACITY));
        assertTrue("another surface's own stays", grid().holdsOwn(Property.FROST,
            Element.KEYBOARD));
    }

    @Test
    public void theCardArrowPutsTheWholeSurfaceBackOnGlobal() {
        giveTheDockAndKeyboardTheirOwn();
        EditorCardSheet.Model keyboard = open(EditorCard.KEYBOARD);
        assertTrue(keyboard.cardOwnsAny());
        keyboard.resetCard();
        assertFalse(keyboard.cardOwnsAny());
        assertFalse(grid().holdsOwn(Property.FROST, Element.KEYBOARD));
        assertFalse(grid().holdsOwn(Property.GRAIN, Element.KEYBOARD));
        assertTrue(grid().holdsOwn(Property.OPACITY, Element.DOCK));
        assertTrue("Global's arrow stands while any surface owns a value",
            open(EditorCard.APPEARANCE).cardOwnsAny());
    }

    @Test
    public void theTerminalAndGlobalShowNoRowArrow() {
        EditorCardSheet.Model terminal = open(EditorCard.TERMINAL);
        terminal.onValue(Row.OPACITY, 70, false);
        terminal.onReleased(Row.OPACITY);
        assertFalse(terminal.ownValue(Row.OPACITY));
        assertFalse(terminal.cardOwnsAny());
        assertNull(terminal.rowState(Row.OPACITY));
        EditorCardSheet.Model global = open(EditorCard.APPEARANCE);
        assertFalse(global.ownValue(Row.OPACITY));
        assertNull(global.rowState(Row.OPACITY));
    }

    @Test
    public void aScreenReaderHearsTheRowsState() {
        giveTheDockAndKeyboardTheirOwn();
        EditorCardSheet.Model dock = open(EditorCard.DOCK);
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(context.getString(com.termux.R.string.editor_row_state_own,
            context.getString(Row.OPACITY.labelRes)), dock.rowState(Row.OPACITY).toString());
        assertEquals(context.getString(com.termux.R.string.editor_row_state_global,
            context.getString(Row.FROST.labelRes)), dock.rowState(Row.FROST).toString());
    }
}
