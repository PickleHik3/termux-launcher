package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;

/**
 * The TAI main screen's one "Model centre" row: "3 installed · 1 downloading", with a thin
 * progress line under it while anything is on its way. The owning fragment feeds it from
 * {@link com.termux.ai.TaiDownloadHub}; a pure progress tick goes straight to the bound bar so
 * the row is not rebound five times a second (a rebind repaints the list, which flickers).
 */
@Keep
public final class TaiModelCentreRowPreference extends Preference {
    private boolean showProgress;
    private boolean indeterminate;
    private int progress;
    @Nullable private LinearProgressIndicator boundBar;

    public TaiModelCentreRowPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_tai_model_centre_row);
        setIconSpaceReserved(false);
        setPersistent(false);
    }

    public TaiModelCentreRowPreference(@NonNull Context context) {
        this(context, null);
    }

    /** Shows the line when {@code show}; {@code progress} is 0..10000, or below 0 for a sweep. */
    public void setProgress(boolean show, int progress) {
        boolean sweep = progress < 0;
        int value = Math.max(0, Math.min(10000, progress));
        if (show == showProgress && sweep == indeterminate && value == this.progress) return;
        boolean shapeChanged = show != showProgress || sweep != indeterminate;
        showProgress = show;
        indeterminate = sweep;
        this.progress = value;
        if (!shapeChanged && boundBar != null && boundBar.getTag() == this) {
            boundBar.setProgressCompat(value, true);
            return;
        }
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View view = holder.findViewById(R.id.tai_model_centre_progress);
        if (!(view instanceof LinearProgressIndicator)) return;
        LinearProgressIndicator bar = (LinearProgressIndicator) view;
        bar.setTag(this);
        boundBar = bar;
        bar.setIndicatorColor(TaiModelCentreAdapter.color(getContext(), com.termux.shared.R.attr.termuxColorPrimary));
        bar.setTrackColor(TaiModelCentreAdapter.color(getContext(), com.termux.shared.R.attr.termuxColorSurfacePanelHigh));
        if (!showProgress) {
            bar.setVisibility(View.GONE);
            return;
        }
        if (bar.isIndeterminate() != indeterminate) {
            bar.setVisibility(View.INVISIBLE);
            bar.setIndeterminate(indeterminate);
        }
        bar.setVisibility(View.VISIBLE);
        if (!indeterminate) bar.setProgressCompat(progress, false);
    }
}
