package com.termux.app.terminal.inappkeyboard;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import juloo.keyboard2.KeyValue;

/**
 * Which action the keyboard's Copy, Cut, Paste and Select all keys do on each target. The keys
 * mean the action, not a chord: a terminal copies its selection and pastes as a terminal does
 * (so Ctrl+C is never sent for Copy), and a Linux window
 * under either display gets the app's own chord, Paste following the app's Paste shortcut.
 *
 * <p>A text field edits as Android does and is decided by {@link FieldKeys}, not here.
 *
 * <p>Pure: the views ask {@link #plan} and dispatch the answer.
 */
public final class EditKeyPolicy {

    /** Where the key lands. */
    public enum Target { TERMINAL, X11_WINDOW, WAYLAND_WINDOW }

    /** What to do: an {@link #action} on the target itself, or the app's chord on a window. */
    public static final class Plan {
        /** The action for a terminal; {@link FieldKeys.Edit#NONE} for a chord. */
        @NonNull public final FieldKeys.Edit action;
        /** The Android key code pressed with Ctrl for a chord; 0 for an action. */
        public final int keyCode;
        /** Whether Shift is held around the chord (Ctrl+Shift+V). */
        public final boolean shift;

        private Plan(@NonNull FieldKeys.Edit action, int keyCode, boolean shift) {
            this.action = action;
            this.keyCode = keyCode;
            this.shift = shift;
        }

        /** Whether the plan is Ctrl (+Shift) and {@link #keyCode}. */
        public boolean isChord() {
            return keyCode != 0;
        }
    }

    private EditKeyPolicy() {}

    /**
     * The plan for {@code editing} on {@code target}, or null when it is not one of the four edit
     * keys (the caller keeps its own handling of undo, redo, paste as plain text and the rest).
     *
     * @param shiftHeld the keyboard's Shift, which rides along on a chord (Shift+Copy is
     *                  Ctrl+Shift+C, the copy chord of Linux terminals)
     * @param pasteWithShift whether the app's Paste shortcut is Ctrl+Shift+V
     */
    @Nullable
    public static Plan plan(@NonNull Target target, @NonNull KeyValue.Editing editing,
                            boolean shiftHeld, boolean pasteWithShift) {
        FieldKeys.Edit edit = FieldKeys.editFor(editing);
        int keyCode;
        switch (edit) {
            case COPY: keyCode = KeyEvent.KEYCODE_C; break;
            case CUT: keyCode = KeyEvent.KEYCODE_X; break;
            case PASTE: keyCode = KeyEvent.KEYCODE_V; break;
            case SELECT_ALL: keyCode = KeyEvent.KEYCODE_A; break;
            default: return null;
        }
        switch (target) {
            case X11_WINDOW:
            case WAYLAND_WINDOW:
                return new Plan(FieldKeys.Edit.NONE, keyCode,
                    shiftHeld || (edit == FieldKeys.Edit.PASTE && pasteWithShift));
            default:
                return new Plan(edit, 0, false);
        }
    }

    /**
     * What a hardware key does in a terminal: Ctrl+Shift+C copies and Ctrl+Shift+V pastes, as
     * Linux terminals do. Plain Ctrl+C and every other chord are {@link FieldKeys.Edit#NONE} and
     * reach the program.
     */
    @NonNull
    public static FieldKeys.Edit terminalHardwareEdit(int keyCode, boolean ctrl, boolean shift,
                                                      boolean alt) {
        if (!ctrl || !shift || alt) return FieldKeys.Edit.NONE;
        if (keyCode == KeyEvent.KEYCODE_C) return FieldKeys.Edit.COPY;
        if (keyCode == KeyEvent.KEYCODE_V) return FieldKeys.Edit.PASTE;
        return FieldKeys.Edit.NONE;
    }
}
