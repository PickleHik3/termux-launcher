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

/** Search field used on Settings home. Filtering stays in the owning fragment. */
@Keep
public final class SettingsSearchPreference extends Preference {

    public interface OnQueryChangedListener {
        void onQueryChanged(@NonNull String query);
    }

    private String mQuery = "";
    private OnQueryChangedListener mListener;
    private boolean mUserFocused;

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
        if (!mQuery.contentEquals(input.getText())) {
            input.setText(mQuery);
            input.setSelection(input.length());
        }
        input.setOnClickListener(view -> mUserFocused = true);
        if (!mUserFocused) {
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
