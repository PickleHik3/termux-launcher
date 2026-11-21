package com.termux.app.terminal;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.FieldEditor;
import com.termux.app.terminal.inappkeyboard.FieldKeys;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import juloo.keyboard2.KeyValue;

/**
 * The key-side intake of a focusless editor: no focus, no {@code InputConnection}, and every
 * stroke swallowed while it is up.
 *
 * <p>Typing reaches such a surface by exactly three routes, and a surface that wires fewer looks
 * dead on somebody's keyboard:
 *
 * <ul>
 *   <li>the in-app keyboard, as resolved key values through {@link #interceptKeyValue};
 *   <li>hardware and external keyboards, as key events through {@link #handleKeyDown};
 *   <li>system IMEs, as committed text through {@link #handleCodePoint} — those send no key events
 *       at all for ordinary characters.
 * </ul>
 *
 * <p>The first two are decided here and reduced to a handful of edits; the third differs per
 * editor and stays with it. Everything is swallowed while the editor is active, including strokes
 * it ignores: a key that fell through to the shell would type into the terminal behind it.
 */
public abstract class FocuslessKeyIntake implements TerminalKeyEventHandler.KeyValueInterceptor {

    @NonNull private final FieldEditor mFieldEditor;

    protected FocuslessKeyIntake() {
        this(() -> null);
    }

    protected FocuslessKeyIntake(@NonNull ClipboardText clipboardSource) {
        mFieldEditor = new FieldEditor(clipboardSource);
    }

    /**
     * The text the in-app keyboard's editing keys act on now (copy, cut, paste, select all, undo,
     * redo and their Ctrl chords), or null when there is none and they do nothing.
     */
    @Nullable
    protected FieldEditor.Field editableField() {
        return null;
    }

    /** A Ctrl or Alt chord this editor gives a meaning of its own; it goes to {@link #onText}. */
    protected boolean ownsChord(@NonNull String text, boolean ctrl, boolean alt) {
        return false;
    }

    /** A new edit begins: the last one's undo history and select-all go. */
    protected final void resetFieldEdits() {
        mFieldEditor.reset();
    }

    public abstract boolean isActive();

    /** @return true when the committed character was claimed by the active editor. */
    public abstract boolean handleCodePoint(int codePoint, boolean ctrlDown);

    /** Printable text with the modifiers it arrived under; the editor decides what a chord means. */
    protected abstract void onText(@NonNull String text, boolean ctrl, boolean alt);

    protected abstract void onBackspace();

    /** The Enter or action key. */
    protected abstract void onCommit();

    /** A cursor slider: negative for left, positive for right. */
    protected abstract void onCursor(int delta);

    /** Named keys, which every channel shares. @return true when the key code was claimed. */
    protected abstract boolean handleKeyCode(int keyCode);

    @Override
    public final boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                           boolean shift) {
        if (!isActive()) return false;
        if (FieldKeys.classify(value, ctrl, alt) == FieldKeys.Kind.CHORD) {
            String text = value.getKind() == KeyValue.Kind.Char
                ? String.valueOf(value.getChar()) : value.getString();
            if (ownsChord(text, ctrl, alt)) {
                onText(text, ctrl, alt);
                return true;
            }
        }
        // Edits, chords and tool keys follow the one field rule; what is left is this editor's.
        if (mFieldEditor.handle(editableField(), value, ctrl, alt, shift)) return true;
        switch (value.getKind()) {
            case Char:
                onText(String.valueOf(value.getChar()), ctrl, alt);
                break;
            case String:
                onText(value.getString(), ctrl, alt);
                break;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR: onText(" ", ctrl, alt); break;
                    case BACKSPACE: onBackspace(); break;
                    default: break;
                }
                break;
            case Keyevent:
                handleKeyCode(value.getKeyevent());
                break;
            case Event:
                if (value.getEvent() == KeyValue.Event.ACTION) onCommit();
                break;
            case Slider:
                switch (value.getSlider()) {
                    case Cursor_left: onCursor(-Math.max(1, value.getSliderRepeat())); break;
                    case Cursor_right: onCursor(Math.max(1, value.getSliderRepeat())); break;
                    default: break;
                }
                break;
            default:
                break;
        }
        return true;
    }

    /** @return true when the stroke was claimed by the active editor. */
    public final boolean handleKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (!isActive()) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (handleKeyCode(keyCode)) return true;
        // Ctrl and Alt are reported beside the character rather than folded into it, so a chord
        // like Ctrl+V still names the letter it was struck on.
        int unicode = event.getUnicodeChar(event.getMetaState()
            & ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_MASK | KeyEvent.META_META_MASK));
        if (unicode >= ' ') onText(new String(Character.toChars(unicode)),
            event.isCtrlPressed(), event.isAltPressed());
        return true;
    }
}
