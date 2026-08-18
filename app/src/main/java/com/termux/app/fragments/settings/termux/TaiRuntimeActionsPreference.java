package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * Row of TAI runtime action cards (stop / unload / logs), matching the TL
 * handoff "Runtime control" design.
 */
@Keep
public class TaiRuntimeActionsPreference extends Preference {

    public interface OnActionClickListener {
        void onStop();
        void onUnload();
        void onLogs();
    }

    private OnActionClickListener mListener;
    private boolean mStopEnabled = false;
    private boolean mUnloadEnabled = false;
    private boolean mLogsEnabled = true;

    public TaiRuntimeActionsPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TaiRuntimeActionsPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_tai_actions);
        setPersistent(false);
        setSelectable(false);
        setIconSpaceReserved(false);
    }

    public void setOnActionClickListener(OnActionClickListener listener) {
        mListener = listener;
    }

    public void setActionStates(boolean stopEnabled, boolean unloadEnabled, boolean logsEnabled) {
        mStopEnabled = stopEnabled;
        mUnloadEnabled = unloadEnabled;
        mLogsEnabled = logsEnabled;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        bindCard(holder, R.id.tai_action_stop, mStopEnabled, () -> {
            if (mListener != null) mListener.onStop();
        });
        bindCard(holder, R.id.tai_action_unload, mUnloadEnabled, () -> {
            if (mListener != null) mListener.onUnload();
        });
        bindCard(holder, R.id.tai_action_logs, mLogsEnabled, () -> {
            if (mListener != null) mListener.onLogs();
        });
    }

    private void bindCard(@NonNull PreferenceViewHolder holder, int id, boolean enabled, @NonNull Runnable action) {
        View card = holder.findViewById(id);
        if (card == null) return;
        card.setAlpha(enabled ? 1f : 0.45f);
        card.setEnabled(enabled);
        card.setClickable(enabled);
        card.setOnClickListener(enabled ? v -> action.run() : null);
    }
}
