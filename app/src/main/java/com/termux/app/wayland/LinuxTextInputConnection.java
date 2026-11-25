package com.termux.app.wayland;

import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;

import androidx.annotation.NonNull;

/**
 * The phone keyboard's connection to a Linux app's text field: its typed text becomes the
 * field's commit_string, its composing text the preedit, its deletions delete_surrounding_text,
 * and its key events go to the window as keys. Holds no text of its own (the app has it), so
 * the keyboard reads nothing back.
 */
final class LinuxTextInputConnection extends BaseInputConnection {

    /** Where the keyboard's edits go: the focused window's field. */
    interface Sink {
        void commit(@NonNull String text);

        /** Composing text (empty clears it), with the cursor as a UTF-16 index into it. */
        void preedit(@NonNull String text, int cursor);

        /** UTF-16 code units before and after the cursor. */
        void delete(int before, int after);

        /** One Android key code down or up. */
        void key(int keyCode, boolean down);
    }

    @NonNull private final Sink sink;
    /** The composing text the app shows as preedit; empty for none. */
    @NonNull private String composing = "";

    LinuxTextInputConnection(@NonNull View view, @NonNull Sink sink) {
        super(view, false);
        this.sink = sink;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        composing = "";
        String s = text == null ? "" : text.toString();
        // commit_string replaces the preedit; an empty commit only clears it.
        if (s.isEmpty()) sink.preedit("", -1);
        else sink.commit(s);
        return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        composing = text == null ? "" : text.toString();
        sink.preedit(composing, composing.length());
        return true;
    }

    /** The composing text stays as it is: it becomes the field's text. */
    @Override
    public boolean finishComposingText() {
        if (composing.isEmpty()) return true;
        String done = composing;
        composing = "";
        sink.commit(done);
        return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (beforeLength > 0 || afterLength > 0) sink.delete(Math.max(0, beforeLength), Math.max(0, afterLength));
        return true;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return true;
        if (!LinuxWindowKeys.isForwarded(event.getKeyCode())) return true;
        sink.key(event.getKeyCode(), action == KeyEvent.ACTION_DOWN);
        return true;
    }

    /** The keyboard's action key (Done, Go, Search) is Enter to the app. */
    @Override
    public boolean performEditorAction(int actionCode) {
        sink.key(KeyEvent.KEYCODE_ENTER, true);
        sink.key(KeyEvent.KEYCODE_ENTER, false);
        return true;
    }
}
