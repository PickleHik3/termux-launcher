package com.termux.app.processlimit;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * The Settings root's place for the process-limit card. The row is always there and takes no
 * height while there is no card: the root's search hides and restores rows by the visibility it
 * found, so a row that came and went by itself would come back wrong after a search.
 */
@Keep
public final class ProcessLimitNoticePreference extends Preference {

    public ProcessLimitNoticePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProcessLimitNoticePreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_process_limit_notice);
        setPersistent(false);
        setSelectable(false);
        setIconSpaceReserved(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        if (holder.itemView instanceof ProcessLimitNoticeView)
            ((ProcessLimitNoticeView) holder.itemView).refreshIfShown();
    }
}
