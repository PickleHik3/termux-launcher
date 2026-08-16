package com.termux.app.launcher.folder;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.terminal.CommandPaletteSoftKeyDecision;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import juloo.keyboard2.KeyValue;

/** Three-channel rename intake. The host owns interceptor installation/restoration. */
public final class FolderRenameController implements TerminalKeyEventHandler.KeyValueInterceptor {
    public interface Host {
        @NonNull LauncherConfigRepository.MutationResult commit(long revision,
                                                                 @NonNull String folderId,
                                                                 @NonNull String title);
        void onDraftChanged(@NonNull FolderRenameModel model);
        /** Called exactly once for commit, cancel, dismiss, pause, deletion and stale rejection. */
        void onRenameEnded(boolean committed);
    }

    @Nullable private Host host;
    @Nullable private FolderRenameModel model;
    @Nullable private String folderId;
    private long revision;
    private boolean active;

    public boolean begin(long revision, @NonNull String folderId, @NonNull String title,
                         @NonNull Host host) {
        if (active) cancel();
        this.revision = revision;
        this.folderId = folderId;
        this.host = host;
        this.model = new FolderRenameModel(title);
        active = true;
        host.onDraftChanged(model);
        return true;
    }

    public boolean isActive() { return active; }
    @Nullable public FolderRenameModel model() { return model; }

    public void cancel() { finish(false); }
    public void onPopupDismissed() { finish(false); }
    public void onActivityPaused() { finish(false); }
    public void onFolderDeleted(@NonNull String id) {
        if (active && id.equals(folderId)) finish(false);
    }

    public void commit() {
        if (!active || host == null || model == null || folderId == null) return;
        Host current = host;
        LauncherConfigRepository.MutationResult result = current.commit(revision, folderId,
            model.committedTitle());
        // Stale and missing are terminal outcomes too: the interceptor must never leak.
        finish(result == LauncherConfigRepository.MutationResult.APPLIED
            || result == LauncherConfigRepository.MutationResult.NO_OP);
    }

    private void finish(boolean committed) {
        if (!active) return;
        Host current = host;
        active = false;
        host = null;
        model = null;
        folderId = null;
        revision = 0L;
        if (current != null) current.onRenameEnded(committed);
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
        return true;
    }

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
    private void changed() { if (host != null && model != null) host.onDraftChanged(model); }
}
