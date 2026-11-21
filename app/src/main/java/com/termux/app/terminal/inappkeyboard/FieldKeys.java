package com.termux.app.terminal.inappkeyboard;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

import juloo.keyboard2.KeyValue;

/**
 * What a key of the in-app keyboard means while a launcher text field has it, rather than the
 * terminal: the one rule every field interceptor (drawer search, palette, find, rename, folder
 * rename, the dictation editor, the Assistant field, the sheets) follows.
 *
 * <ul>
 *   <li>{@link Kind#KEYBOARD_OWNED}: the keyboard acting on itself (settings, the clipboard pane,
 *       layout switches, change input method, caps lock, hide, voice typing, compose state, macro
 *       expansion). {@link TerminalKeyEventHandler} hands these to the host before any interceptor
 *       sees them, so a field never needs to know about them and can never swallow them.
 *   <li>{@link Kind#EDITING}: copy, cut, paste, select all, undo and redo, which act on the field's
 *       own text ({@link FieldEditor}).
 *   <li>{@link Kind#CHORD}: a Ctrl or Alt chord on a character. Ctrl+A/C/X/V/Z/Y and Ctrl+Shift+Z
 *       are the editing actions ({@link #chordEdit}); every other chord does nothing. The keyboard
 *       turns a character struck under Ctrl or Alt into its key's key event
 *       ({@code KeyModifier.turn_into_keyevent}), so a chord on a letter arrives as that key event
 *       ({@link #chordChar}), not as the character.
 *   <li>{@link Kind#TOOL}: a {@code tool:} key (palette, window switching, places). Ignored while a
 *       field has the keyboard.
 *   <li>{@link Kind#CONTENT}: everything else (characters, space, backspace, named keys, the
 *       action key, sliders), which each field handles as it always has.
 * </ul>
 *
 * <p>Nothing classified here ever reaches the shell while a field has the keyboard: the field's
 * interceptor claims every value but the keyboard-owned ones.
 */
public final class FieldKeys {

    public enum Kind { KEYBOARD_OWNED, EDITING, CHORD, TOOL, CONTENT }

    /** The editing action a key or chord asks of the field; {@link #NONE} when it asks nothing. */
    public enum Edit { NONE, COPY, CUT, PASTE, PASTE_PLAIN, SELECT_ALL, UNDO, REDO }

    private FieldKeys() {}

    /**
     * Whether a value acts on the keyboard itself and goes to the host whatever has the keyboard.
     * These are the values {@code X11KeyboardBridge.isLauncherSide} already passes on, less the
     * {@code tool:} keys, which a text field ignores.
     */
    public static boolean isKeyboardOwned(@NonNull KeyValue value) {
        switch (value.getKind()) {
            case Event:
                return isKeyboardOwned(value.getEvent());
            // A macro is expanded by the handler and each key it yields comes back through the
            // interceptor; the compose values only move the keyboard's compose indicator.
            case Macro:
            case Compose_pending:
            case Placeholder:
                return true;
            default:
                return false;
        }
    }

    /** Every event is listed, so one added upstream has to be placed here on purpose. */
    public static boolean isKeyboardOwned(@NonNull KeyValue.Event event) {
        switch (event) {
            case ACTION:
                // Enter for the field: commit, submit, run.
                return false;
            case CONFIG:
            case SWITCH_TEXT:
            case SWITCH_NUMERIC:
            case SWITCH_EMOJI:
            case SWITCH_BACK_EMOJI:
            case SWITCH_CLIPBOARD:
            case SWITCH_BACK_CLIPBOARD:
            case CHANGE_METHOD_PICKER:
            case CHANGE_METHOD_PREV:
            case CHANGE_METHOD_NEXT:
            case SWITCH_FORWARD:
            case SWITCH_BACKWARD:
            case SWITCH_GREEKMATH:
            case CAPS_LOCK:
            case SWITCH_VOICE_TYPING:
            case SWITCH_VOICE_TYPING_CHOOSER:
            case HIDE_SELF:
                return true;
            default:
                throw new IllegalArgumentException("Unclassified keyboard event " + event);
        }
    }

    @NonNull
    public static Kind classify(@NonNull KeyValue value, boolean ctrl, boolean alt) {
        if (isKeyboardOwned(value)) return Kind.KEYBOARD_OWNED;
        switch (value.getKind()) {
            case Launcher_tool:
                return Kind.TOOL;
            case Editing:
                return editFor(value.getEditing()) == Edit.NONE ? Kind.CONTENT : Kind.EDITING;
            case Char:
            case String:
                return ctrl || alt ? Kind.CHORD : Kind.CONTENT;
            case Keyevent:
                return (ctrl || alt) && chordChar(value) != 0 ? Kind.CHORD : Kind.CONTENT;
            default:
                return Kind.CONTENT;
        }
    }

    /** The editing action a value asks for: an edit key, or a mapped Ctrl chord. */
    @NonNull
    public static Edit editFor(@NonNull KeyValue value, boolean ctrl, boolean alt, boolean shift) {
        switch (value.getKind()) {
            case Editing:
                return editFor(value.getEditing());
            case Char:
                return chordEdit(value.getChar(), ctrl, alt, shift);
            case String: {
                String text = value.getString();
                return text.length() == 1 ? chordEdit(text.charAt(0), ctrl, alt, shift) : Edit.NONE;
            }
            case Keyevent: {
                char c = chordChar(value);
                return c != 0 ? chordEdit(c, ctrl, alt, shift) : Edit.NONE;
            }
            default:
                return Edit.NONE;
        }
    }

    /** The text of the key a chord was struck on, whichever form the chord arrived in. */
    @NonNull
    public static String chordText(@NonNull KeyValue value) {
        switch (value.getKind()) {
            case Char:
                return String.valueOf(value.getChar());
            case Keyevent: {
                char c = chordChar(value);
                return c != 0 ? String.valueOf(c) : value.getString();
            }
            default:
                return value.getString();
        }
    }

    /**
     * The character whose key a key event is, when the keyboard made it from a character under
     * Ctrl or Alt: the inverse of {@code KeyModifier.turn_into_keyevent}, less the space bar,
     * which a field types as content. 0 for any other value (arrows, Tab, Enter, Delete, …).
     */
    public static char chordChar(@NonNull KeyValue value) {
        if (value.getKind() != KeyValue.Kind.Keyevent) return 0;
        int code = value.getKeyevent();
        if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z)
            return (char) ('a' + (code - KeyEvent.KEYCODE_A));
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
            return (char) ('0' + (code - KeyEvent.KEYCODE_0));
        switch (code) {
            case KeyEvent.KEYCODE_GRAVE: return '`';
            case KeyEvent.KEYCODE_MINUS: return '-';
            case KeyEvent.KEYCODE_EQUALS: return '=';
            case KeyEvent.KEYCODE_LEFT_BRACKET: return '[';
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return ']';
            case KeyEvent.KEYCODE_BACKSLASH: return '\\';
            case KeyEvent.KEYCODE_SEMICOLON: return ';';
            case KeyEvent.KEYCODE_APOSTROPHE: return '\'';
            case KeyEvent.KEYCODE_SLASH: return '/';
            case KeyEvent.KEYCODE_AT: return '@';
            case KeyEvent.KEYCODE_PLUS: return '+';
            case KeyEvent.KEYCODE_COMMA: return ',';
            case KeyEvent.KEYCODE_PERIOD: return '.';
            case KeyEvent.KEYCODE_STAR: return '*';
            case KeyEvent.KEYCODE_POUND: return '#';
            case KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN: return '(';
            case KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN: return ')';
            default: return 0;
        }
    }

    /** The keyboard's edit keys; the others (space, backspace, word deletes, …) are content. */
    @NonNull
    public static Edit editFor(@NonNull KeyValue.Editing editing) {
        switch (editing) {
            case COPY: return Edit.COPY;
            case CUT: return Edit.CUT;
            case PASTE: return Edit.PASTE;
            case PASTE_PLAIN: return Edit.PASTE_PLAIN;
            case SELECT_ALL: return Edit.SELECT_ALL;
            case UNDO: return Edit.UNDO;
            case REDO: return Edit.REDO;
            default: return Edit.NONE;
        }
    }

    /**
     * The standard editing shortcuts, Ctrl alone: A select all, C copy, X cut, V paste, Z undo,
     * Y and Shift+Z redo. Any Alt, and any other letter, is {@link Edit#NONE}.
     */
    @NonNull
    public static Edit chordEdit(char c, boolean ctrl, boolean alt, boolean shift) {
        if (!ctrl || alt) return Edit.NONE;
        switch (Character.toLowerCase(c)) {
            case 'a': return Edit.SELECT_ALL;
            case 'c': return Edit.COPY;
            case 'x': return Edit.CUT;
            case 'v': return Edit.PASTE;
            case 'z': return shift ? Edit.REDO : Edit.UNDO;
            case 'y': return Edit.REDO;
            default: return Edit.NONE;
        }
    }
}
