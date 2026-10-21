package com.termux.app.terminal.inappkeyboard.voice;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.ClipboardText;
import com.termux.app.terminal.CommandPaletteSoftKeyDecision;
import com.termux.app.terminal.FocuslessKeyIntake;

import java.util.Collections;
import java.util.List;

/**
 * The {@link FocuslessKeyIntake} of the dictation card's editor: what each key does to the
 * {@link VoiceEditModel}. The card's text is never focused and holds no {@code InputConnection};
 * the in-app keyboard reaches this through its interceptor slot, a hardware keyboard and a system
 * IME through the activity's overlay channel.
 *
 * <p>Every way out keeps the text: Enter, Back and Escape here, a tap outside the text, and the
 * card's own buttons through {@link #end}. Only {@link #abandon} does not, and it is for the card
 * going away with its text (×, a swipe). All of them funnel through one {@code finish}, so
 * {@link Host#onEditEnded} runs exactly once per edit and the slot can never outlive it.
 */
public final class VoiceEditController extends FocuslessKeyIntake {

    public interface Host {
        /** Redraw the text for the new draft and caret. */
        void onDraftChanged(@NonNull VoiceEditModel model);

        /**
         * The UTF-16 offset one laid-out line above ({@code direction} -1) or below (+1) the caret
         * at {@code caretUtf16}, at the same horizontal position; -1 when there is no line there.
         * Lines here are the view's, wrapped ones included, so only the view can answer.
         */
        int offsetOnAdjacentLine(int caretUtf16, int direction);

        /** Called exactly once per edit: the text as edited, or {@code null} when abandoned. */
        void onEditEnded(@Nullable String text);
    }

    @Nullable private Host host;
    @Nullable private VoiceEditModel model;
    private boolean active;

    public VoiceEditController() {
        super();
    }

    public VoiceEditController(@NonNull ClipboardText clipboardSource) {
        super(clipboardSource);
    }

    /** Opens the editor on {@code text}, the caret at UTF-16 {@code caretUtf16}; an edit already open ends first, kept. */
    public void begin(@NonNull String text, int caretUtf16, @NonNull Host host) {
        begin(text, caretUtf16, Collections.emptyList(), host);
    }

    /** As {@link #begin(String, int, Host)}, with the cleanup's {@code marks} on {@code text} ({@link VoiceEditModel#cleanupMarks}). */
    public void begin(@NonNull String text, int caretUtf16, @NonNull List<VoiceEditModel.Mark> marks,
                      @NonNull Host host) {
        if (active) end();
        this.host = host;
        this.model = new VoiceEditModel(text, caretUtf16, marks);
        active = true;
        host.onDraftChanged(model);
    }

    @Override public boolean isActive() { return active; }

    @Nullable public VoiceEditModel model() { return model; }

    /** A tap inside the text while editing: the caret goes there. */
    public void moveCaretTo(int caretUtf16) {
        if (model == null) return;
        model.setCaretUtf16(caretUtf16);
        changed();
    }

    /** A phrase that finished transcribing while the editor is open joins the end of the draft. */
    public void appendPhrase(@NonNull String piece) {
        if (model == null) return;
        model.appendPhrase(piece);
        changed();
    }

    /** Ends the edit, keeping the text. */
    public void end() { finish(true); }

    /** Ends the edit without keeping it: the text is being discarded with the card. */
    public void abandon() { finish(false); }

    private void finish(boolean keep) {
        if (!active) return;
        Host current = host;
        String text = keep && model != null ? model.text() : null;
        active = false;
        host = null;
        model = null;
        if (current != null) current.onEditEnded(text);
    }

    @Override
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        switch (CommandPaletteSoftKeyDecision.decide(active, false, codePoint, ctrlDown)) {
            case IGNORE: return false;
            case APPEND: insert(new String(Character.toChars(codePoint))); return true;
            case COMMIT:
            case COLLAPSE: end(); return true;
            case BACKSPACE: backspace(); return true;
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
    @Override protected void onCommit() { end(); }
    @Override protected void onCursor(int delta) { move(delta); }

    @Override
    protected boolean handleKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: move(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: line(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: line(1); return true;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (model != null) { model.moveCaretToLineStart(); changed(); }
                return true;
            case KeyEvent.KEYCODE_MOVE_END:
                if (model != null) { model.moveCaretToLineEnd(); changed(); }
                return true;
            case KeyEvent.KEYCODE_DEL: backspace(); return true;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                if (model != null) { model.delete(); changed(); }
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                end();
                return true;
            default:
                return false;
        }
    }

    private void insert(@NonNull String value) {
        if (model == null) return;
        model.insert(value);
        changed();
    }

    private void backspace() {
        if (model == null) return;
        model.backspace();
        changed();
    }

    private void move(int delta) {
        if (model == null) return;
        model.moveCaret(delta);
        changed();
    }

    private void line(int direction) {
        VoiceEditModel current = model;
        Host owner = host;
        if (current == null || owner == null) return;
        int offset = owner.offsetOnAdjacentLine(current.caretUtf16(), direction);
        if (offset < 0) return;
        current.setCaretUtf16(offset);
        changed();
    }

    private void changed() {
        if (host != null && model != null) host.onDraftChanged(model);
    }
}
