package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import com.termux.app.terminal.inappkeyboard.EditKeyPolicy.Plan;
import com.termux.app.terminal.inappkeyboard.EditKeyPolicy.Target;

import juloo.keyboard2.KeyValue.Editing;

/** Which action Copy, Cut, Paste and Select all do on each target. */
public class EditKeyPolicyTest {

    @Test
    public void aTerminalCopiesAndPastesAsItselfNeverAsControlCodes() {
        Plan copy = EditKeyPolicy.plan(Target.TERMINAL, Editing.COPY, false, false);
        assertFalse(copy.isChord());
        assertEquals(FieldKeys.Edit.COPY, copy.action);
        assertEquals(FieldKeys.Edit.PASTE,
            EditKeyPolicy.plan(Target.TERMINAL, Editing.PASTE, false, true).action);
        assertEquals(FieldKeys.Edit.CUT,
            EditKeyPolicy.plan(Target.TERMINAL, Editing.CUT, false, false).action);
        assertEquals(FieldKeys.Edit.SELECT_ALL,
            EditKeyPolicy.plan(Target.TERMINAL, Editing.SELECT_ALL, false, false).action);
    }

    @Test
    public void aLinuxWindowGetsTheAppsChordUnderEitherDisplay() {
        for (Target t : new Target[] {Target.X11_WINDOW, Target.WAYLAND_WINDOW}) {
            assertChord(EditKeyPolicy.plan(t, Editing.COPY, false, true), KeyEvent.KEYCODE_C, false);
            assertChord(EditKeyPolicy.plan(t, Editing.CUT, false, true), KeyEvent.KEYCODE_X, false);
            assertChord(EditKeyPolicy.plan(t, Editing.SELECT_ALL, false, true), KeyEvent.KEYCODE_A, false);
        }
    }

    @Test
    public void pasteFollowsTheAppsPasteShortcut() {
        for (Target t : new Target[] {Target.X11_WINDOW, Target.WAYLAND_WINDOW}) {
            assertChord(EditKeyPolicy.plan(t, Editing.PASTE, false, false), KeyEvent.KEYCODE_V, false);
            assertChord(EditKeyPolicy.plan(t, Editing.PASTE, false, true), KeyEvent.KEYCODE_V, true);
        }
    }

    @Test
    public void aHeldShiftRidesAlong() {
        assertChord(EditKeyPolicy.plan(Target.WAYLAND_WINDOW, Editing.COPY, true, false),
            KeyEvent.KEYCODE_C, true);
        assertChord(EditKeyPolicy.plan(Target.X11_WINDOW, Editing.PASTE, true, false),
            KeyEvent.KEYCODE_V, true);
    }

    @Test
    public void otherEditingValuesAreNotEditKeys() {
        assertNull(EditKeyPolicy.plan(Target.TERMINAL, Editing.UNDO, false, false));
        assertNull(EditKeyPolicy.plan(Target.X11_WINDOW, Editing.BACKSPACE, false, false));
        assertNull(EditKeyPolicy.plan(Target.TERMINAL, Editing.PASTE_PLAIN, false, false));
    }

    @Test
    public void hardwareCtrlShiftCAndVCopyAndPasteInATerminal() {
        assertEquals(FieldKeys.Edit.COPY,
            EditKeyPolicy.terminalHardwareEdit(KeyEvent.KEYCODE_C, true, true, false));
        assertEquals(FieldKeys.Edit.PASTE,
            EditKeyPolicy.terminalHardwareEdit(KeyEvent.KEYCODE_V, true, true, false));
    }

    @Test
    public void plainCtrlCAndOtherChordsStayWithTheProgram() {
        assertEquals(FieldKeys.Edit.NONE,
            EditKeyPolicy.terminalHardwareEdit(KeyEvent.KEYCODE_C, true, false, false));
        assertEquals(FieldKeys.Edit.NONE,
            EditKeyPolicy.terminalHardwareEdit(KeyEvent.KEYCODE_V, true, false, false));
        assertEquals(FieldKeys.Edit.NONE,
            EditKeyPolicy.terminalHardwareEdit(KeyEvent.KEYCODE_C, false, true, false));
        assertEquals(FieldKeys.Edit.NONE,
            EditKeyPolicy.terminalHardwareEdit(KeyEvent.KEYCODE_C, true, true, true));
        assertEquals(FieldKeys.Edit.NONE,
            EditKeyPolicy.terminalHardwareEdit(KeyEvent.KEYCODE_X, true, true, false));
    }

    private static void assertChord(Plan plan, int keyCode, boolean shift) {
        assertTrue(plan.isChord());
        assertEquals(keyCode, plan.keyCode);
        assertEquals(shift, plan.shift);
    }
}
