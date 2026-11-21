package com.termux.app.terminal.find;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.ClipboardText;
import com.termux.app.terminal.FocuslessKeyIntake;
import com.termux.app.terminal.inappkeyboard.FieldEditor;

import java.util.List;

/**
 * The {@link FocuslessKeyIntake} of a find session.
 *
 * <p>The controller decides what a key means; {@link TerminalFindModel} decides what it does and
 * the host paints the result.</p>
 */
public final class TerminalFindController extends FocuslessKeyIntake {

    public interface Host {
        /** Repaint the bar and the transcript overlay for the model's new state. */
        void onFindChanged(@NonNull TerminalFindModel model);

        /** Called exactly once per session, for yank, escape, dismissal and pause alike. */
        void onFindEnded(@Nullable String yankedText);
    }

    public TerminalFindController() {
        super();
    }

    public TerminalFindController(@NonNull ClipboardText clipboardSource) {
        super(clipboardSource);
    }

    @Nullable private Host host;
    @Nullable private TerminalFindModel model;
    private boolean active;

    public boolean begin(@NonNull List<TerminalFindModel.Line> snapshot, @NonNull Host host) {
        if (active) end(null);
        this.host = host;
        this.model = new TerminalFindModel(snapshot);
        active = true;
        resetFieldEdits();
        host.onFindChanged(model);
        return true;
    }

    /** The query while it is typed; once committed there is no text to edit, only the copy mode. */
    private final FieldEditor.Field queryField = new FieldEditor.Field() {
        @Nullable @Override public String fieldText() { return model == null ? null : model.query(); }

        @Override public void replaceFieldText(@NonNull String text) {
            TerminalFindModel current = model;
            if (current != null) settle(current.replaceQuery(text));
        }

        @Override public void insertFieldText(@NonNull String text) {
            TerminalFindModel current = model;
            if (current != null) settle(current.typeText(text));
        }
    };

    @Nullable
    @Override
    protected FieldEditor.Field editableField() {
        TerminalFindModel current = model;
        return current != null && current.mode() == TerminalFindModel.Mode.TYPING ? queryField : null;
    }

    /**
     * Find's own chords: every chord once the query is committed (the copy mode's commands), and
     * Ctrl+V while typing, which starts a block selection on what is matched.
     */
    @Override
    protected boolean ownsChord(@NonNull String text, boolean ctrl, boolean alt) {
        TerminalFindModel current = model;
        if (current == null) return false;
        if (current.mode() != TerminalFindModel.Mode.TYPING) return true;
        return ctrl && !alt && (text.equals("v") || text.equals("V"));
    }

    @Override public boolean isActive() { return active; }

    @Nullable public TerminalFindModel model() { return model; }

    public void cancel() { end(null); }

    private void end(@Nullable String yankedText) {
        if (!active) return;
        Host current = host;
        active = false;
        host = null;
        model = null;
        if (current != null) current.onFindEnded(yankedText);
    }

    /** Applies one model result: repaint, or finish the session. */
    private void settle(@NonNull TerminalFindModel.Result result) {
        TerminalFindModel current = model;
        switch (result) {
            case CLOSED:
                end(null);
                break;
            case YANKED:
                end(current == null ? null : current.yankedText());
                break;
            case HANDLED:
                if (host != null && current != null) host.onFindChanged(current);
                break;
            default:
                break;
        }
    }

    // ----------------------------------------------------------------------------- IME channel

    @Override
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        if (!active) return false;
        if (codePoint == '\n' || codePoint == '\r') {
            settle(commit());
            return true;
        }
        if (codePoint == '\b') {
            settle(backspace());
            return true;
        }
        if (codePoint == 0x1b) {
            settle(escape());
            return true;
        }
        if (codePoint >= ' ') onText(new String(Character.toChars(codePoint)), ctrlDown, false);
        return true;
    }

    // ------------------------------------------------------------------------------------ intake

    @Override
    protected void onText(@NonNull String text, boolean ctrl, boolean alt) {
        TerminalFindModel current = model;
        if (current == null || text.isEmpty()) return;
        if (current.mode() == TerminalFindModel.Mode.TYPING) {
            // Ctrl+V is the one stroke that leaves the query: it starts a block selection on what
            // is already matched, which is the whole point of typing a query first.
            if (ctrl && (text.charAt(0) == 'v' || text.charAt(0) == 'V')) {
                settle(current.commitQuery());
                settle(current.command('v', true));
                return;
            }
            if (ctrl || alt) return;
            settle(current.typeText(text));
            return;
        }
        settle(current.command(text.charAt(0), ctrl));
    }

    @Override protected void onBackspace() { settle(backspace()); }
    @Override protected void onCommit() { settle(commit()); }

    /** A slider only nudges the copy-mode cursor; while the query is typed it has nothing to move. */
    @Override
    protected void onCursor(int delta) {
        TerminalFindModel current = model;
        if (current == null) return;
        if (current.mode() == TerminalFindModel.Mode.TYPING) return;
        settle(current.command(delta < 0 ? 'h' : 'l', false));
    }

    private TerminalFindModel.Result backspace() {
        TerminalFindModel current = model;
        return current == null ? TerminalFindModel.Result.IGNORED : current.backspace();
    }

    private TerminalFindModel.Result commit() {
        TerminalFindModel current = model;
        return current == null ? TerminalFindModel.Result.IGNORED : current.commitQuery();
    }

    private TerminalFindModel.Result escape() {
        TerminalFindModel current = model;
        return current == null ? TerminalFindModel.Result.CLOSED : current.escape();
    }

    /**
     * Named keys, which every channel shares. Arrows walk matches while the query is being typed —
     * n and N are still characters there — and move the copy-mode cursor once it is committed.
     */
    @Override
    protected boolean handleKeyCode(int keyCode) {
        TerminalFindModel current = model;
        if (current == null) return false;
        boolean typing = current.mode() == TerminalFindModel.Mode.TYPING;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                settle(typing ? current.step(-1) : current.command('k', false));
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                settle(typing ? current.step(1) : current.command('j', false));
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                settle(typing ? current.step(-1) : current.command('h', false));
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                settle(typing ? current.step(1) : current.command('l', false));
                return true;
            case KeyEvent.KEYCODE_DEL:
                settle(backspace());
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                settle(typing ? current.commitQuery() : current.yank());
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                settle(escape());
                return true;
            default:
                return false;
        }
    }
}
