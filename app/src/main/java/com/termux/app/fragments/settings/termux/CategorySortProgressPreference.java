package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The "re-run categorization" row while it is a running job: title, a phase line, and a determinate
 * bar.
 *
 * <p>The bar is part of the row rather than a dialog because the run survives leaving Settings — a
 * dialog would have to be dismissed and the progress would vanish with it — and because the same
 * numbers already drive the foreground notification.
 */
public final class CategorySortProgressPreference extends Preference {

    private int mPercent;
    private boolean mShowProgress;
    private boolean mIndeterminate;

    public CategorySortProgressPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_category_sort_progress);
        setPersistent(false);
    }

    /**
     * @param percent 0..100, ignored while {@code showProgress} is false
     * @param showProgress whether a run is in flight; an idle row is exactly the old plain row
     * @param indeterminate for the phases with no numerator yet (reading apps, loading the model)
     */
    public void setProgress(int percent, boolean showProgress, boolean indeterminate) {
        int clamped = Math.max(0, Math.min(100, percent));
        if (mPercent == clamped && mShowProgress == showProgress
            && mIndeterminate == indeterminate) return;
        mPercent = clamped;
        mShowProgress = showProgress;
        mIndeterminate = indeterminate;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View view = holder.findViewById(R.id.category_sort_progress);
        if (!(view instanceof ProgressBar)) return;
        ProgressBar bar = (ProgressBar) view;
        bar.setVisibility(mShowProgress ? View.VISIBLE : View.GONE);
        if (!mShowProgress) return;
        int accent = MaterialColors.getColor(getContext(),
            com.google.android.material.R.attr.colorPrimary, 0xFF8AB4F8);
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setIndeterminateTintList(ColorStateList.valueOf(accent));
        bar.setIndeterminate(mIndeterminate);
        if (mIndeterminate) return;
        // Never animated: the ticks arrive from a poll, and an animation between two poll values
        // reads as the bar lagging the number in the line above it.
        bar.setProgress(mPercent, false);
    }
}
