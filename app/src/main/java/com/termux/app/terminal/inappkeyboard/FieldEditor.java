package com.termux.app.terminal.inappkeyboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.ClipboardText;
import com.termux.app.terminal.PasteText;

import java.util.ArrayDeque;

import juloo.keyboard2.KeyValue;

/**
 * The editing half of {@link FieldKeys} for a focusless field: one that draws its own text and
 * caret and has no {@code EditText} behind it (drawer search, palette, find, rename, folder
 * rename, the dictation editor, the sheets). Its interceptor offers every value here first;
 * whatever this does not claim is the field's own content to handle as it always has.
 *
 * <p>These fields have a caret but no selection they could draw, so the edits act on the whole
 * field: copy and cut take all of its text, select all marks all of it for the next stroke (the
 * next character, paste or delete replaces it, a caret move drops it), and undo and redo step
 * through the text as it stood before each change.
 */
public final class FieldEditor {

    /** A focusless field's text, as the editing actions need it. */
    public interface Field {
        /** The field's whole text, or null when it cannot be read (paste still works). */
        @Nullable String fieldText();

        /** Replaces the whole text, caret at its end; the field redraws as after typing. */
        void replaceFieldText(@NonNull String text);

        /** Inserts at the caret exactly as typing would, under the field's own limits. */
        void insertFieldText(@NonNull String text);

        /** Clipboard text made fit for the field; one line unless the field says otherwise. */
        @NonNull
        default String pasteable(@NonNull String clip) {
            return PasteText.sanitizeSingleLine(clip);
        }
    }

    static final int HISTORY_LIMIT = 64;

    @NonNull private final ClipboardText mClipboard;
    private final ArrayDeque<String> mUndo = new ArrayDeque<>();
    private final ArrayDeque<String> mRedo = new ArrayDeque<>();
    /** The text as last seen; null until the field is first seen after {@link #reset}. */
    @Nullable private String mLast;
    private boolean mAllSelected;

    public FieldEditor(@NonNull ClipboardText clipboard) {
        mClipboard = clipboard;
    }

    /** A new edit begins (a rename, a find, the palette opening): history and selection go. */
    public void reset() {
        mUndo.clear();
        mRedo.clear();
        mLast = null;
        mAllSelected = false;
    }

    /** Whether select all is in force: what the next character, paste or delete replaces. */
    public boolean isAllSelected() {
        return mAllSelected;
    }

    /**
     * Offer one in-app keyboard value to the field's editing rule.
     *
     * @return true when the value was spent here (an edit, a chord, a tool key), false when it is
     *         the field's own content to handle
     */
    public boolean handle(@Nullable Field field, @NonNull KeyValue value, boolean ctrl, boolean alt,
                          boolean shift) {
        switch (FieldKeys.classify(value, ctrl, alt)) {
            case KEYBOARD_OWNED:
            case TOOL:
                // Keyboard-owned values never get here from the handler; a tool key is ignored
                // while a field has the keyboard.
                return true;
            case EDITING:
            case CHORD:
                apply(field, FieldKeys.editFor(value, ctrl, alt, shift));
                return true;
            case CONTENT:
            default:
                break;
        }
        if (field == null) return false;
        observe(field);
        if (mAllSelected) {
            mAllSelected = false;
            Effect effect = effectOf(value);
            if (effect != Effect.MOVES) {
                field.replaceFieldText("");
                return effect == Effect.DELETES;
            }
        }
        return false;
    }

    /** Runs one editing action on the field. Null field or {@link FieldKeys.Edit#NONE}: nothing. */
    public void apply(@Nullable Field field, @NonNull FieldKeys.Edit edit) {
        if (field == null || edit == FieldKeys.Edit.NONE) return;
        observe(field);
        String text = field.fieldText();
        switch (edit) {
            case COPY:
                if (text != null && !text.isEmpty()) mClipboard.write(text);
                break;
            case CUT:
                mAllSelected = false;
                if (text == null || text.isEmpty()) break;
                mClipboard.write(text);
                field.replaceFieldText("");
                break;
            case PASTE:
            case PASTE_PLAIN: {
                String clip = mClipboard.read();
                if (clip == null || clip.isEmpty()) break;
                String pasted = field.pasteable(clip);
                if (pasted.isEmpty()) break;
                if (mAllSelected) field.replaceFieldText("");
                mAllSelected = false;
                field.insertFieldText(pasted);
                break;
            }
            case SELECT_ALL:
                mAllSelected = text != null && !text.isEmpty();
                break;
            case UNDO:
                mAllSelected = false;
                step(field, mUndo, mRedo);
                break;
            case REDO:
                mAllSelected = false;
                step(field, mRedo, mUndo);
                break;
            default:
                break;
        }
        observe(field);
    }

    /** Notes a change made since the last look, so undo can return to the text before it. */
    private void observe(@NonNull Field field) {
        String now = field.fieldText();
        if (now == null) return;
        if (mLast != null && !now.equals(mLast)) {
            push(mUndo, mLast);
            mRedo.clear();
        }
        mLast = now;
    }

    private void step(@NonNull Field field, @NonNull ArrayDeque<String> from,
                      @NonNull ArrayDeque<String> to) {
        if (from.isEmpty() || mLast == null) return;
        String target = from.pop();
        push(to, mLast);
        mLast = target;
        field.replaceFieldText(target);
        // The field may refuse part of it (a length limit); what it holds now is the truth.
        String now = field.fieldText();
        if (now != null) mLast = now;
    }

    private static void push(@NonNull ArrayDeque<String> stack, @NonNull String text) {
        stack.push(text);
        while (stack.size() > HISTORY_LIMIT) stack.removeLast();
    }

    private enum Effect { REPLACES, DELETES, MOVES }

    /** What a content value does to a whole-field selection: replace it, delete it, or drop it. */
    @NonNull
    private static Effect effectOf(@NonNull KeyValue value) {
        switch (value.getKind()) {
            case Char:
            case String:
                return Effect.REPLACES;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR:
                        return Effect.REPLACES;
                    case BACKSPACE:
                    case DELETE_WORD:
                    case FORWARD_DELETE_WORD:
                        return Effect.DELETES;
                    default:
                        return Effect.MOVES;
                }
            case Keyevent:
                switch (value.getKeyevent()) {
                    case android.view.KeyEvent.KEYCODE_DEL:
                    case android.view.KeyEvent.KEYCODE_FORWARD_DEL:
                        return Effect.DELETES;
                    default:
                        return Effect.MOVES;
                }
            default:
                return Effect.MOVES;
        }
    }
}
