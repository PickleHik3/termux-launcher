package com.termux.app.terminal.rename;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.CommandPaletteSoftKeyDecision;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import juloo.keyboard2.KeyValue;

/**
 * Three-channel intake for an inline rename, with no focus and no {@code InputConnection}.
 *
 * <p>Typing reaches a focusless surface by exactly three routes, and a surface that wires fewer
 * looks dead on somebody's keyboard:
 *
 * <ul>
 *   <li>the in-app keyboard, as resolved key values through {@link #interceptKeyValue};
 *   <li>hardware and external keyboards, as key events through {@link #handleKeyDown};
 *   <li>system IMEs, as committed text through {@link #handleCodePoint} — those send no key events
 *       at all for ordinary characters.
 * </ul>
 *
 * <p>The host owns installing and restoring the in-app keyboard's interceptor slot; this class only
 * decides what a value means. Every terminating path funnels through one {@code finish}, so the
 * interceptor can never leak past the end of a rename.
 */
public final class InlineRenameController implements TerminalKeyEventHandler.KeyValueInterceptor {

    public interface Host {
        /** Redraw the editor for the new draft. */
        void onDraftChanged(@NonNull InlineRenameModel model);

        /** Called exactly once per rename, for commit, cancel, dismissal and pause alike. */
        void onRenameEnded(boolean committed, @Nullable String committedName);
    }

    @Nullable private Host host;
    @Nullable private InlineRenameModel model;
    private boolean active;

    public boolean begin(@Nullable String initial, int maxCodePoints, @NonNull Host host) {
        if (active) cancel();
        this.host = host;
        this.model = new InlineRenameModel(initial, maxCodePoints);
        active = true;
        host.onDraftChanged(model);
        return true;
    }

    public boolean isActive() { return active; }

    @Nullable public InlineRenameModel model() { return model; }

    public void cancel() { finish(false); }

    public void commit() {
        if (!active || model == null) return;
        finish(true);
    }

    /** Replaces the draft from outside the keyboard channels, e.g. with a suggested name. */
    public void setDraft(@Nullable String value) {
        if (!active || model == null) return;
        model.replaceAll(value);
        changed();
    }

    private void finish(boolean committed) {
        if (!active) return;
        Host current = host;
        String name = committed && model != null ? model.committedName() : null;
        active = false;
        host = null;
        model = null;
        if (current != null) current.onRenameEnded(committed, name);
    }

    @Override
    public boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                     boolean shift) {
        if (!active) return false;
        switch (value.getKind()) {
            case Char:
                if (!ctrl && !alt) insert(String.valueOf(value.getChar()));
                break;
            case String:
                if (!ctrl && !alt) insert(value.getString());
                break;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR: insert(" "); break;
                    case BACKSPACE: backspace(); break;
                    default: break;
                }
                break;
            case Keyevent:
                handleKeyCode(value.getKeyevent());
                break;
            case Event:
                if (value.getEvent() == KeyValue.Event.ACTION) commit();
                break;
            case Slider:
                switch (value.getSlider()) {
                    case Cursor_left: move(-Math.max(1, value.getSliderRepeat())); break;
                    case Cursor_right: move(Math.max(1, value.getSliderRepeat())); break;
                    default: break;
                }
                break;
            default:
                break;
        }
        // Everything is swallowed while a rename is up, including strokes this editor ignores: a key
        // that fell through to the shell would type into the terminal behind the chip.
        return true;
    }

    /** @return true when the stroke was claimed by the active rename. */
    public boolean handleKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (!active) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (handleKeyCode(keyCode)) return true;
        if (!event.isCtrlPressed() && !event.isAltPressed()) {
            int unicode = event.getUnicodeChar();
            if (unicode >= ' ') insert(new String(Character.toChars(unicode)));
        }
        return true;
    }

    /** @return true when the committed character was claimed by the active rename. */
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        CommandPaletteSoftKeyDecision.Action action =
            CommandPaletteSoftKeyDecision.decide(active, false, codePoint, ctrlDown);
        switch (action) {
            case IGNORE: return false;
            case APPEND: insert(new String(Character.toChars(codePoint))); return true;
            case COMMIT: commit(); return true;
            case BACKSPACE: backspace(); return true;
            case COLLAPSE: cancel(); return true;
            case SWALLOW:
            default: return true;
        }
    }

    private boolean handleKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: move(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1); return true;
            case KeyEvent.KEYCODE_MOVE_HOME: toStart(); return true;
            case KeyEvent.KEYCODE_MOVE_END: toEnd(); return true;
            case KeyEvent.KEYCODE_DEL: backspace(); return true;
            case KeyEvent.KEYCODE_FORWARD_DEL: delete(); return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: commit(); return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK: cancel(); return true;
            default: return false;
        }
    }

    private void insert(@NonNull String value) {
        if (model == null) return;
        model.insert(value);
        changed();
    }

    private void backspace() { if (model != null) { model.backspace(); changed(); } }
    private void delete() { if (model != null) { model.delete(); changed(); } }
    private void move(int delta) { if (model != null) { model.moveCaret(delta); changed(); } }
    private void toStart() { if (model != null) { model.moveCaretToStart(); changed(); } }
    private void toEnd() { if (model != null) { model.moveCaretToEnd(); changed(); } }
    private void changed() { if (host != null && model != null) host.onDraftChanged(model); }
}
