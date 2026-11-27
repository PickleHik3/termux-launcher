package com.termux.app.wayland;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.EditKeyPolicy;
import com.termux.app.x11.X11AsciiKeys;

import juloo.keyboard2.KeyValue;

/**
 * What the launcher's keyboard types into a Linux window: every value becomes plain key presses
 * with Android key codes, which the compositor maps to the window's xkb keys
 * (wayland-host/src/keymap.rs). A printable character goes on the US key that types it, Shift
 * held where the character needs it ({@link X11AsciiKeys}); Ctrl, Alt, Meta and Shift from the
 * keyboard are pressed around the key and released after it, so a window is never left with a
 * modifier down. A character with no US key (emoji, accented letters, other scripts) is dropped
 * here; while the app has a text field focused the controller sends characters as text instead
 * ({@link #typedText}).
 */
public final class LinuxWindowKeys {

    /** Where the presses go: one Android key code down or up. */
    public interface Sink {
        void key(int keyCode, boolean down);
    }

    private LinuxWindowKeys() {}

    /** Key codes the host maps to evdev (see wayland-host/src/keymap.rs); others stay with Android. */
    public static boolean isForwarded(int code) {
        return (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
            || (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT)
            || (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_SPACE)
            || (code >= KeyEvent.KEYCODE_ENTER && code <= KeyEvent.KEYCODE_SLASH)
            || code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_PAGE_DOWN
            || (code >= KeyEvent.KEYCODE_ESCAPE && code <= KeyEvent.KEYCODE_CAPS_LOCK)
            || code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT
            || (code >= KeyEvent.KEYCODE_MOVE_HOME && code <= KeyEvent.KEYCODE_INSERT)
            || (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12);
    }

    /**
     * Types {@code value} with the held modifiers. Values that are the keyboard's own business
     * (layout switches, settings, launcher tools, compose state, macros) send nothing; the caller
     * acts on those itself.
     */
    public static void send(@Nullable KeyValue value, boolean ctrl, boolean alt, boolean shift,
                            boolean meta, @NonNull Sink sink) {
        send(value, ctrl, alt, shift, meta, false, sink);
    }

    /**
     * As {@link #send(KeyValue, boolean, boolean, boolean, boolean, Sink)}, with the focused
     * app's Paste shortcut: {@code pasteWithShift} makes the Paste key Ctrl+Shift+V.
     */
    public static void send(@Nullable KeyValue value, boolean ctrl, boolean alt, boolean shift,
                            boolean meta, boolean pasteWithShift, @NonNull Sink sink) {
        if (value == null) return;
        switch (value.getKind()) {
            case Char:
                sendChar(value.getChar(), ctrl, alt, shift, meta, sink);
                return;
            case String: {
                String text = value.getString();
                if (text == null) return;
                for (int i = 0; i < text.length(); i++)
                    sendChar(text.charAt(i), ctrl, alt, shift, meta, sink);
                return;
            }
            case Keyevent:
                press(value.getKeyevent(), ctrl, alt, shift, meta, sink);
                return;
            case Event:
                // The action key is Enter; the keyboard's other events are not typing.
                if (value.getEvent() == KeyValue.Event.ACTION)
                    press(KeyEvent.KEYCODE_ENTER, ctrl, alt, shift, meta, sink);
                return;
            case Editing:
                sendEditing(value.getEditing(), ctrl, alt, shift, meta, pasteWithShift, sink);
                return;
            case Slider:
                sendSlider(value.getSlider(), value.getSliderRepeat(), ctrl, alt, shift, meta, sink);
                return;
            default:
                // Modifier presses are tracked by the keyboard and arrive with the next value.
        }
    }

    /**
     * The text {@code value} types into a Linux app's text field (zwp_text_input_v3), or null
     * when it is a key rather than text: a chord (Ctrl or Alt held), a control character, an
     * edit key, an arrow. Any character counts, with or without a US key, so accented letters,
     * other scripts and emoji reach the field.
     */
    @Nullable
    public static String typedText(@Nullable KeyValue value, boolean ctrl, boolean alt) {
        if (value == null || ctrl || alt) return null;
        switch (value.getKind()) {
            case Char: {
                char c = value.getChar();
                return Character.isISOControl(c) ? null : String.valueOf(c);
            }
            case String: {
                String text = value.getString();
                if (text == null || text.isEmpty()) return null;
                for (int i = 0; i < text.length(); i++)
                    if (Character.isISOControl(text.charAt(i))) return null;
                return text;
            }
            default:
                return null;
        }
    }

    /** A character on its US key; under a chord Shift is the keyboard's or the character's. */
    private static void sendChar(char c, boolean ctrl, boolean alt, boolean shift, boolean meta,
                                 @NonNull Sink sink) {
        int code = X11AsciiKeys.keyCode(c);
        if (code == KeyEvent.KEYCODE_UNKNOWN) return;
        boolean chord = ctrl || alt || meta;
        // Without a chord the character already carries the keyboard's Shift ('A', '!'); holding
        // Shift again would turn ',' into '<'.
        press(code, ctrl, alt, X11AsciiKeys.needsShift(c) || (chord && shift), meta, sink);
    }

    /** The edit keys are the app's chords ({@link EditKeyPolicy}); undo and redo are Ctrl+Z and Ctrl+Y. */
    private static void sendEditing(@Nullable KeyValue.Editing editing, boolean ctrl, boolean alt,
                                    boolean shift, boolean meta, boolean pasteWithShift,
                                    @NonNull Sink sink) {
        if (editing == null) return;
        EditKeyPolicy.Plan plan = EditKeyPolicy.plan(EditKeyPolicy.Target.WAYLAND_WINDOW, editing,
            shift, pasteWithShift);
        if (plan != null) {
            press(plan.keyCode, true, alt, plan.shift, meta, sink);
            return;
        }
        switch (editing) {
            case SPACE_BAR:
                press(KeyEvent.KEYCODE_SPACE, ctrl, alt, shift, meta, sink);
                return;
            case BACKSPACE:
                press(KeyEvent.KEYCODE_DEL, ctrl, alt, shift, meta, sink);
                return;
            case DELETE_WORD:
                press(KeyEvent.KEYCODE_DEL, true, alt, shift, meta, sink);
                return;
            case FORWARD_DELETE_WORD:
                press(KeyEvent.KEYCODE_FORWARD_DEL, true, alt, shift, meta, sink);
                return;
            case UNDO:
                press(KeyEvent.KEYCODE_Z, true, alt, shift, meta, sink);
                return;
            case REDO:
                press(KeyEvent.KEYCODE_Y, true, alt, shift, meta, sink);
                return;
            default:
                // Plain paste would be text; Android's context-menu entries mean nothing here.
        }
    }

    /**
     * A space-bar swipe: one arrow per tick; a negative repeat is the finger coming back. The
     * selection sliders hold Shift, as a desktop text field extends its selection.
     */
    private static void sendSlider(@Nullable KeyValue.Slider slider, int repeat, boolean ctrl,
                                   boolean alt, boolean shift, boolean meta, @NonNull Sink sink) {
        if (slider == null || repeat == 0) return;
        boolean reverse = repeat < 0;
        int code;
        boolean selecting = false;
        switch (slider) {
            case Cursor_left:
                code = reverse ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case Cursor_right:
                code = reverse ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case Cursor_up:
                code = reverse ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
                break;
            case Cursor_down:
                code = reverse ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            case Selection_cursor_left:
                code = reverse ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                selecting = true;
                break;
            case Selection_cursor_right:
                code = reverse ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                selecting = true;
                break;
            default:
                return;
        }
        int count = Math.abs(repeat);
        for (int i = 0; i < count; i++) press(code, ctrl, alt, shift || selecting, meta, sink);
    }

    /** {@code code} down and up inside the held modifiers; a code the host cannot map is dropped. */
    private static void press(int code, boolean ctrl, boolean alt, boolean shift, boolean meta,
                              @NonNull Sink sink) {
        if (!isForwarded(code)) return;
        if (ctrl) sink.key(KeyEvent.KEYCODE_CTRL_LEFT, true);
        if (alt) sink.key(KeyEvent.KEYCODE_ALT_LEFT, true);
        if (meta) sink.key(KeyEvent.KEYCODE_META_LEFT, true);
        if (shift) sink.key(KeyEvent.KEYCODE_SHIFT_LEFT, true);
        sink.key(code, true);
        sink.key(code, false);
        if (shift) sink.key(KeyEvent.KEYCODE_SHIFT_LEFT, false);
        if (meta) sink.key(KeyEvent.KEYCODE_META_LEFT, false);
        if (alt) sink.key(KeyEvent.KEYCODE_ALT_LEFT, false);
        if (ctrl) sink.key(KeyEvent.KEYCODE_CTRL_LEFT, false);
    }
}
