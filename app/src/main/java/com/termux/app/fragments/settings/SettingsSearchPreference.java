package com.termux.app.fragments.settings;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * The search field: on the Settings root, and on the search screen any other screen opens. What a
 * query shows stays with the owner ({@link SettingsSearchResults}); this row only reports the query
 * as it changes and when the keyboard's search key submits it.
 */
@Keep
public final class SettingsSearchPreference extends Preference {

    public interface OnQueryChangedListener {
        void onQueryChanged(@NonNull String query);
    }

    /** The keyboard's search (or Enter) key on a query: open the first result. */
    public interface OnQuerySubmitListener {
        void onQuerySubmit(@NonNull String query);
    }

    private String mQuery = "";
    private OnQueryChangedListener mListener;
    @Nullable private OnQuerySubmitListener mSubmitListener;
    @Nullable private CharSequence mHint;
    private boolean mUserFocused;
    /** Set by {@link #requestFocusOnBind}: the next bind brings the keyboard up, once. */
    private boolean mShowImeOnNextBind;

    public SettingsSearchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_settings_search);
        setSelectable(false);
        setPersistent(false);
        setIconSpaceReserved(false);
    }

    public void setOnQueryChangedListener(@Nullable OnQueryChangedListener listener) {
        mListener = listener;
    }

    public void setOnQuerySubmitListener(@Nullable OnQuerySubmitListener listener) {
        mSubmitListener = listener;
    }

    /** Exposed for tests driving a submit without a bound view. */
    @Nullable
    public OnQuerySubmitListener getOnQuerySubmitListener() {
        return mSubmitListener;
    }

    /** The field's placeholder, such as "Search Home"; null keeps the layout's own. */
    public void setHint(@Nullable CharSequence hint) {
        mHint = hint;
        notifyChanged();
    }

    /**
     * Opens with the field focused and the keyboard up: the search screen is opened to type in,
     * unlike the root, where the field waits for a tap.
     */
    public void requestFocusOnBind() {
        mUserFocused = true;
        mShowImeOnNextBind = true;
    }

    /** Exposed for tests driving a query without a bound view. */
    @Nullable
    public OnQueryChangedListener getOnQueryChangedListener() {
        return mListener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // This row takes focus below (to keep the IME shut on entry), and the preference row's
        // default selectable background paints its focused state as a light full-width band
        // behind the pill. The pill drawable is the only chrome this row should show.
        holder.itemView.setBackground(null);
        EditText input = (EditText) holder.findViewById(R.id.settings_search_input);
        if (input == null) return;
        Object oldWatcher = input.getTag(R.id.settings_search_input);
        if (oldWatcher instanceof TextWatcher) input.removeTextChangedListener((TextWatcher) oldWatcher);
        if (mHint != null && !mHint.equals(input.getHint())) input.setHint(mHint);
        input.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN;
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                && actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE && !enter) return false;
            if (mSubmitListener != null) mSubmitListener.onQuerySubmit(mQuery);
            return true;
        });
        if (!mQuery.contentEquals(input.getText())) {
            input.setText(mQuery);
            input.setSelection(input.length());
        }
        // Every keystroke filters the list, and the list binds this row again. The first bind
        // keeps the IME shut by parking focus on the row; a bind while the reader is typing must
        // do the opposite and hand the field its focus and caret back, or the keyboard drops
        // mid-word. A tap into the field does not always arrive as a click, so focus itself is
        // what marks the reader's interest.
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) mUserFocused = true;
        });
        if (mUserFocused || !mQuery.isEmpty()) {
            mUserFocused = true;
            if (!input.hasFocus()) input.requestFocus();
            input.setSelection(input.length());
            // Only the search screen's first bind asks for the keyboard; a rebind (Back to the
            // screen, a results refresh) leaves it as the reader left it.
            if (mShowImeOnNextBind) {
                mShowImeOnNextBind = false;
                input.post(() -> {
                    android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
                        input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (ime != null && input.hasFocus()) ime.showSoftInput(input, 0);
                });
            }
        } else {
            input.clearFocus();
            holder.itemView.setFocusableInTouchMode(true);
            holder.itemView.requestFocus();
        }
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                mQuery = value == null ? "" : value.toString();
                if (mListener != null) mListener.onQueryChanged(mQuery);
            }
            @Override public void afterTextChanged(Editable editable) {}
        };
        input.addTextChangedListener(watcher);
        input.setTag(R.id.settings_search_input, watcher);
    }
}
