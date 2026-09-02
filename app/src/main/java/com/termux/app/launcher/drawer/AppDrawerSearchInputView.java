package com.termux.app.launcher.drawer;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import com.termux.R;

/**
 * The drawer's real text field, for the Android-keyboard search.
 *
 * <p>{@link AppDrawerSearchPillView} paints the query and never takes focus, which is what lets
 * the built-in keyboard's key stream and the terminal's input connection drive the search. An
 * Android keyboard, though, only offers its suggestions, autocorrection and swipe typing to a
 * focused text field with a text input type — so when the user asks for it, this field takes
 * focus instead of the terminal and the pill keeps painting what it holds.
 *
 * <p>It sits exactly under the pill and draws nothing: alpha zero, no background, no cursor. The
 * pill stays the touch target and the caret, and the two are kept in step by
 * {@link AppDrawerContentView}: every edit here is reported as the whole query, and every query
 * the search settles on is written back here when they differ.
 */
public final class AppDrawerSearchInputView extends AppCompatEditText {

    /** An edit made by the keyboard: the whole text and where the caret landed. */
    public interface Listener {

        void onQueryEdited(@NonNull String text, int caret);

        /** The keyboard's search action, or a hardware Enter. */
        void onSearchAction();

        /** A hardware Esc. */
        void onDismissAction();
    }

    @Nullable private Listener mListener;
    private boolean mSyncing;

    public AppDrawerSearchInputView(@NonNull Context context) {
        super(context);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            | InputType.TYPE_TEXT_VARIATION_NORMAL);
        setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_FULLSCREEN
            | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setSingleLine(true);
        setBackground(null);
        setCursorVisible(false);
        setAlpha(0f);
        // Focusable so the keyboard has a target, but never by a touch: the pill above it is the
        // thing that is tapped, and it asks for the keyboard through the drawer instead.
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(false);
        setLongClickable(false);
        setContentDescription(context.getString(R.string.app_drawer_search_hint));
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override public void afterTextChanged(Editable s) {
                report();
            }
        });
        setOnEditorActionListener((v, actionId, event) -> {
            // A hardware Enter arrives as IME_NULL with its key event; the keyboard's own action
            // arrives as the action alone. Both mean the first result.
            boolean hardwareEnter = actionId == EditorInfo.IME_NULL && event != null
                && event.getAction() == KeyEvent.ACTION_DOWN;
            if (!hardwareEnter && actionId != EditorInfo.IME_ACTION_SEARCH
                && actionId != EditorInfo.IME_ACTION_DONE && actionId != EditorInfo.IME_ACTION_GO) {
                return false;
            }
            Listener listener = mListener;
            if (listener != null) listener.onSearchAction();
            return true;
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // With focus here the terminal's key channel never sees this stroke, so Esc is answered
        // in place; everything else is the field's to edit with.
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            Listener listener = mListener;
            if (listener != null) listener.onDismissAction();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * The search's answer, written back without echoing: a query cleared from the pill or emptied
     * on close has to leave the field just as empty, or the next keystroke would append to text the
     * user can no longer see.
     */
    public void mirror(@NonNull String query, int caret) {
        Editable current = getText();
        if (current != null && query.contentEquals(current)) {
            int clamped = Math.max(0, Math.min(caret, current.length()));
            if (getSelectionEnd() != clamped) setSelection(clamped);
            return;
        }
        mSyncing = true;
        try {
            setText(query);
            setSelection(Math.max(0, Math.min(caret, query.length())));
        } finally {
            mSyncing = false;
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        // A caret moved by the keyboard's own arrows or a long-press: the pill's caret follows.
        report();
    }

    private void report() {
        if (mSyncing) return;
        Listener listener = mListener;
        Editable text = getText();
        if (listener == null || text == null) return;
        listener.onQueryEdited(text.toString(), Math.max(0, getSelectionEnd()));
    }
}
