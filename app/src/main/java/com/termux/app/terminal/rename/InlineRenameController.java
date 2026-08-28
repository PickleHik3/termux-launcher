package com.termux.app.terminal.rename;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.CommandPaletteSoftKeyDecision;
import com.termux.app.terminal.FocuslessKeyIntake;

/**
 * The {@link FocuslessKeyIntake} of an inline rename.
 *
 * <p>The host owns installing and restoring the in-app keyboard's interceptor slot; this class only
 * decides what a value means. Every terminating path funnels through one {@code finish}, so the
 * interceptor can never leak past the end of a rename.
 */
public final class InlineRenameController extends FocuslessKeyIntake {

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

    @Override public boolean isActive() { return active; }

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

    /** Chords are not text: a key struck with Ctrl or Alt held is swallowed rather than inserted. */
    @Override
    protected void onText(@NonNull String text, boolean ctrl, boolean alt) {
        if (!ctrl && !alt) insert(text);
    }

    @Override protected void onBackspace() { backspace(); }
    @Override protected void onCommit() { commit(); }
    @Override protected void onCursor(int delta) { move(delta); }

    @Override
    protected boolean handleKeyCode(int keyCode) {
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
