package com.termux.app.assistant;

import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Selection;
import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.termux.app.terminal.ClipboardText;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import juloo.keyboard2.KeyValue;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The launcher keyboard's keys, typed into the page's focused field (the composer, or the Chats
 * search). The field never opens the
 * system keyboard; while the Assistant place owns the keyboard (TypingClaim.Owner.ASSISTANT)
 * every key lands here, and what the field has no use for is swallowed so nothing reaches the
 * terminal hidden behind the page. Enter sends, as the Send button does; Shift+Enter is a
 * newline; while an answer streams Enter does nothing (Stop stays a button).
 */
final class AssistantFieldKeys implements TerminalKeyEventHandler.KeyValueInterceptor {

    private final Supplier<EditText> target;
    private final Consumer<EditText> onEnter;
    private final ClipboardText clipboard;

    /**
     * @param target  the field the keys edit now: whichever of the page's fields has focus, or null
     * @param onEnter what Enter does for that field (the composer sends; the search does nothing)
     */
    AssistantFieldKeys(@NonNull Supplier<EditText> target, @NonNull Consumer<EditText> onEnter,
                       @NonNull ClipboardText clipboard) {
        this.target = target;
        this.onEnter = onEnter;
        this.clipboard = clipboard;
    }

    @Override
    public boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                     boolean shift) {
        // Nothing focused: still swallowed, so nothing reaches the terminal behind the page.
        if (target.get() == null) return true;
        switch (value.getKind()) {
            case Char:
                if (!ctrl && !alt) insert(String.valueOf(value.getChar()));
                return true;
            case String:
                if (!ctrl && !alt) insert(value.getString());
                return true;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR: insert(" "); break;
                    case BACKSPACE: backspace(); break;
                    case PASTE:
                    case PASTE_PLAIN: paste(); break;
                    case SELECT_ALL: Selection.selectAll(editable()); break;
                    default: break;
                }
                return true;
            case Keyevent:
                handleKeyCode(value.getKeyevent(), shift);
                return true;
            case Event:
                if (value.getEvent() == KeyValue.Event.ACTION) enter(shift);
                return true;
            case Slider:
                switch (value.getSlider()) {
                    case Cursor_left: moveCaret(-Math.max(1, value.getSliderRepeat())); break;
                    case Cursor_right: moveCaret(Math.max(1, value.getSliderRepeat())); break;
                    default: break;
                }
                return true;
            default:
                return true;
        }
    }

    private void handleKeyCode(int keyCode, boolean shift) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: moveCaret(-1); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveCaret(1); break;
            case KeyEvent.KEYCODE_DPAD_UP: moveLine(-1); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: moveLine(1); break;
            case KeyEvent.KEYCODE_MOVE_HOME: moveEdge(true); break;
            case KeyEvent.KEYCODE_MOVE_END: moveEdge(false); break;
            case KeyEvent.KEYCODE_DEL: backspace(); break;
            case KeyEvent.KEYCODE_FORWARD_DEL: forwardDelete(); break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: enter(shift); break;
            default: break;
        }
    }

    private void enter(boolean shift) {
        EditText field = target.get();
        if (field == null) return;
        boolean multiLine = (field.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (shift) {
            if (multiLine) insert("\n");
        } else {
            onEnter.accept(field);
        }
    }

    @NonNull
    private Editable editable() {
        EditText field = target.get();
        return field != null ? field.getText() : new android.text.SpannableStringBuilder();
    }

    private static int low(@NonNull Editable e) {
        return Math.min(Math.max(0, Selection.getSelectionStart(e)),
            Math.max(0, Selection.getSelectionEnd(e)));
    }

    private static int high(@NonNull Editable e) {
        return Math.max(Math.max(0, Selection.getSelectionStart(e)),
            Math.max(0, Selection.getSelectionEnd(e)));
    }

    private void insert(@NonNull String text) {
        if (text.isEmpty()) return;
        Editable e = editable();
        int start = low(e);
        e.replace(start, high(e), text);
        Selection.setSelection(e, Math.min(e.length(), start + text.length()));
    }

    private void backspace() {
        Editable e = editable();
        int start = low(e);
        int end = high(e);
        if (start == end) {
            if (start == 0) return;
            start -= Character.charCount(Character.codePointBefore(e, start));
        }
        e.delete(start, end);
        Selection.setSelection(e, start);
    }

    private void forwardDelete() {
        Editable e = editable();
        int start = low(e);
        int end = high(e);
        if (start == end) {
            if (end >= e.length()) return;
            end += Character.charCount(Character.codePointAt(e, end));
        }
        e.delete(start, end);
        Selection.setSelection(e, start);
    }

    private void paste() {
        String text = clipboard.read();
        if (text == null || text.isEmpty()) return;
        insert(text.replace("\r", ""));
    }

    /** Steps by code points, so a surrogate pair is never split; a selection collapses to its edge first. */
    private void moveCaret(int steps) {
        Editable e = editable();
        int pos;
        if (low(e) != high(e)) {
            pos = steps < 0 ? low(e) : high(e);
            steps += steps < 0 ? 1 : -1;
        } else {
            pos = low(e);
        }
        for (; steps < 0 && pos > 0; steps++) {
            pos -= Character.charCount(Character.codePointBefore(e, pos));
        }
        for (; steps > 0 && pos < e.length(); steps--) {
            pos += Character.charCount(Character.codePointAt(e, pos));
        }
        Selection.setSelection(e, pos);
    }

    private void moveLine(int dir) {
        EditText field = target.get();
        if (field == null) return;
        Editable e = field.getText();
        Layout layout = field.getLayout();
        int pos = high(e);
        if (layout == null) {
            Selection.setSelection(e, dir < 0 ? 0 : e.length());
            return;
        }
        int target = layout.getLineForOffset(pos) + dir;
        if (target < 0) {
            Selection.setSelection(e, 0);
        } else if (target >= layout.getLineCount()) {
            Selection.setSelection(e, e.length());
        } else {
            Selection.setSelection(e, layout.getOffsetForHorizontal(target,
                layout.getPrimaryHorizontal(pos)));
        }
    }

    private void moveEdge(boolean home) {
        EditText field = target.get();
        if (field == null) return;
        Editable e = field.getText();
        Layout layout = field.getLayout();
        int pos = high(e);
        if (layout == null) {
            Selection.setSelection(e, home ? 0 : e.length());
            return;
        }
        int line = layout.getLineForOffset(pos);
        int edge = home ? layout.getLineStart(line) : layout.getLineEnd(line);
        // A hard line break ends the line before its newline; a soft wrap ends where the next begins.
        if (!home && edge > 0 && edge <= e.length() && e.charAt(edge - 1) == '\n') edge--;
        Selection.setSelection(e, Math.min(e.length(), edge));
    }
}
